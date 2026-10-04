package com.example.extensions

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sandboxed bridge exposed to WebViews as `_ATPBridge`.
 * Exposes strictly controlled APIs with no access to Context or private browser state.
 */
class ExtensionBridge(
    private val storageManager: ExtensionStorageManager,
    private val logCallback: (extensionId: String, message: String) -> Unit,
    private val webViewProvider: () -> WebView?
) {
    @JavascriptInterface
    fun log(extensionId: String, message: String) {
        val sanitized = LogRedactor.redact(message)
        logCallback(extensionId, sanitized)
    }

    @JavascriptInterface
    fun storageGet(extensionId: String, key: String, callbackName: String) {
        val value = storageManager.get(extensionId, key) ?: ""
        val escaped = JSONObject.quote(value)
        runOnMain { webView ->
            webView.evaluateJavascript("window['$callbackName']($escaped);", null)
        }
    }

    @JavascriptInterface
    fun storageSet(extensionId: String, key: String, value: String, callbackName: String) {
        val success = storageManager.set(extensionId, key, value)
        runOnMain { webView ->
            webView.evaluateJavascript("window['$callbackName']($success);", null)
        }
    }

    @JavascriptInterface
    fun storageRemove(extensionId: String, key: String, callbackName: String) {
        val success = storageManager.remove(extensionId, key)
        runOnMain { webView ->
            webView.evaluateJavascript("window['$callbackName']($success);", null)
        }
    }

    @JavascriptInterface
    fun storageClear(extensionId: String, callbackName: String) {
        val success = storageManager.clear(extensionId)
        runOnMain { webView ->
            webView.evaluateJavascript("window['$callbackName']($success);", null)
        }
    }

    @JavascriptInterface
    fun getCurrentTab(callbackName: String) {
        runOnMain { webView ->
            val url = webView.url ?: ""
            val title = webView.title ?: ""
            val json = JSONObject().apply {
                put("url", url)
                put("title", title)
            }.toString()
            val escaped = JSONObject.quote(json)
            webView.evaluateJavascript("window['$callbackName']($escaped);", null)
        }
    }

    private fun runOnMain(action: (WebView) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            webViewProvider()?.let { action(it) }
        }
    }
}

/**
 * Log redactor that removes sensitive tokens, passwords, cookies, and keys.
 */
object LogRedactor {
    private val REDACT_PATTERNS = listOf(
        Regex("""(?i)(password|secret|token|api_?key|auth|access-?key)[\s:=]+([^\s,;]+)"""),
        Regex("""atp-key-user-\d{6}"""),
        Regex("""(?i)(cookie:?)\s*([^\s;]+)""")
    )

    fun redact(message: String): String {
        var result = message
        for (pattern in REDACT_PATTERNS) {
            result = pattern.replace(result) { match ->
                val group1 = match.groups[1]?.value
                if (group1 != null) "$group1 [REDACTED]" else "[REDACTED]"
            }
        }
        return result
    }
}

/**
 * Extension Manager handles installation, file management, match execution, and logs.
 */
class ExtensionManager(private val context: Context) {

    val storageManager = ExtensionStorageManager(context)

    private val _extensions = MutableStateFlow<List<AtpExtension>>(emptyList())
    val extensions: StateFlow<List<AtpExtension>> = _extensions.asStateFlow()

    private val _logs = MutableStateFlow<List<ExtensionLogEntry>>(emptyList())
    val logs: StateFlow<List<ExtensionLogEntry>> = _logs.asStateFlow()

    private val extensionsDir = File(context.filesDir, "extensions")

    init {
        loadInstalledExtensions()
    }

    fun loadInstalledExtensions() {
        if (!extensionsDir.exists()) extensionsDir.mkdirs()

        val list = mutableListOf<AtpExtension>()
        extensionsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val configFile = File(dir, "config.js")
                if (configFile.exists()) {
                    val res = ExtensionValidator.parseConfigJs(configFile.readText(), dir.name)
                    if (res.isValid && res.extension != null) {
                        val stateFile = File(dir, "state.json")
                        val isEnabled = if (stateFile.exists()) {
                            try { JSONObject(stateFile.readText()).optBoolean("enabled", false) } catch (e: Exception) { false }
                        } else false

                        val ext = res.extension.copy(
                            isEnabled = isEnabled,
                            storageSize = storageManager.getStorageSize(dir.name)
                        )
                        list.add(ext)
                    }
                }
            }
        }
        _extensions.value = list
    }

    fun addLog(extensionId: String, level: String, message: String) {
        val entry = ExtensionLogEntry(
            extensionId = extensionId,
            level = level,
            message = LogRedactor.redact(message)
        )
        _logs.value = (_logs.value + entry).takeLast(200)
    }

    fun clearLogs(extensionId: String? = null) {
        if (extensionId == null) {
            _logs.value = emptyList()
        } else {
            _logs.value = _logs.value.filter { it.extensionId != extensionId }
        }
    }

    fun createExtension(
        name: String,
        version: String,
        description: String,
        author: String,
        matches: List<String>,
        permissions: List<String>,
        proxyConfig: ExtensionProxyConfig? = null
    ): AtpExtension {
        val id = "ext_" + System.currentTimeMillis() + "_" + (1000..9999).random()
        val dir = File(extensionsDir, id)
        dir.mkdirs()

        val proxySection = if (proxyConfig != null && proxyConfig.enabled) {
            """
    ,
    proxy: {
        enabled: ${proxyConfig.enabled},
        endpoint: "${proxyConfig.endpoint}",
        bypass: [${proxyConfig.bypass.joinToString(", ") { "\"$it\"" }}]
    }
            """.trimIndent()
        } else ""

        val configContent = """
export default {
    name: "$name",
    version: "$version",
    description: "$description",
    author: "$author",

    matches: [
        ${matches.joinToString(",\n        ") { "\"$it\"" }}
    ],

    permissions: [
        ${permissions.joinToString(",\n        ") { "\"$it\"" }}
    ],

    script: "script.js",
    styles: "styles.css"$proxySection
};
        """.trimIndent()

        val scriptContent = """
// ATP Extension: $name
console.log("ATP Extension [$name] loaded on: " + window.location.href);

// Example DOM modification
document.addEventListener("DOMContentLoaded", function() {
    if (window.ATPBrowser) {
        window.ATPBrowser.log("$name initialized");
    }
});
        """.trimIndent()

        val stylesContent = """
/* ATP Extension: $name custom styles */
/* Add custom CSS rules here */
        """.trimIndent()

        val readmeContent = """
# $name
Version: $version
Author: $author

$description
        """.trimIndent()

        File(dir, "config.js").writeText(configContent)
        File(dir, "script.js").writeText(scriptContent)
        File(dir, "styles.css").writeText(stylesContent)
        File(dir, "README.md").writeText(readmeContent)
        File(dir, "state.json").writeText(JSONObject().put("enabled", false).toString())

        loadInstalledExtensions()
        return _extensions.value.first { it.id == id }
    }

    fun updateExtensionFiles(id: String, configJs: String, scriptJs: String, stylesCss: String): Boolean {
        val dir = File(extensionsDir, id)
        if (!dir.exists()) return false

        val validation = ExtensionValidator.parseConfigJs(configJs, id)
        if (!validation.isValid) return false

        File(dir, "config.js").writeText(configJs)
        File(dir, "script.js").writeText(scriptJs)
        File(dir, "styles.css").writeText(stylesCss)

        loadInstalledExtensions()
        return true
    }

    fun getExtensionFiles(id: String): Triple<String, String, String>? {
        val dir = File(extensionsDir, id)
        if (!dir.exists()) return null
        val config = File(dir, "config.js").takeIf { it.exists() }?.readText() ?: ""
        val script = File(dir, "script.js").takeIf { it.exists() }?.readText() ?: ""
        val styles = File(dir, "styles.css").takeIf { it.exists() }?.readText() ?: ""
        return Triple(config, script, styles)
    }

    fun setExtensionEnabled(id: String, enabled: Boolean) {
        val dir = File(extensionsDir, id)
        if (!dir.exists()) return

        val stateFile = File(dir, "state.json")
        val json = if (stateFile.exists()) {
            try { JSONObject(stateFile.readText()) } catch (e: Exception) { JSONObject() }
        } else JSONObject()
        json.put("enabled", enabled)
        stateFile.writeText(json.toString())

        addLog(id, "INFO", if (enabled) "Extension enabled" else "Extension disabled")
        loadInstalledExtensions()
    }

    fun recordExtensionError(id: String, error: String) {
        addLog(id, "ERROR", error)
        val current = _extensions.value.find { it.id == id } ?: return
        val count = current.consecutiveErrors + 1
        if (count >= 3) {
            setExtensionEnabled(id, false)
            addLog(id, "WARN", "Extension automatically disabled due to repeated execution errors ($count)")
        } else {
            _extensions.value = _extensions.value.map {
                if (it.id == id) it.copy(lastError = error, consecutiveErrors = count) else it
            }
        }
    }

    fun resetExtensionError(id: String) {
        _extensions.value = _extensions.value.map {
            if (it.id == id) it.copy(lastError = null, consecutiveErrors = 0) else it
        }
    }

    fun removeExtension(id: String) {
        val dir = File(extensionsDir, id)
        if (dir.exists()) dir.deleteRecursively()
        storageManager.clear(id)
        clearLogs(id)
        loadInstalledExtensions()
    }

    fun clearExtensionData(id: String) {
        storageManager.clear(id)
        loadInstalledExtensions()
    }

    // Matching logic
    fun matchesUrl(pattern: String, url: String): Boolean {
        if (pattern == "<all_urls>") return true
        val uri = try { Uri.parse(url) } catch (e: Exception) { return false }
        val scheme = uri.scheme ?: ""
        val host = uri.host ?: ""
        val path = uri.path ?: "/"

        val pUri = try { Uri.parse(pattern.replace("*://", "http://")) } catch (e: Exception) { return false }
        val targetScheme = if (pattern.startsWith("*://")) "*" else pattern.substringBefore("://")
        if (targetScheme != "*" && targetScheme != scheme) return false

        val patternHost = pattern.substringAfter("://").substringBefore("/")
        val hostMatch = when {
            patternHost == "*" -> true
            patternHost.startsWith("*.") -> {
                val root = patternHost.removePrefix("*.")
                host == root || host.endsWith(".$root")
            }
            else -> host.equals(patternHost, ignoreCase = true)
        }
        if (!hostMatch) return false

        val patternPath = "/" + pattern.substringAfter("://").substringAfter("/", "")
        return when {
            patternPath == "/*" || patternPath == "/" -> true
            patternPath.endsWith("/*") -> {
                val prefix = patternPath.removeSuffix("/*")
                path.startsWith(prefix)
            }
            else -> path == patternPath
        }
    }

    fun getInjectionsForUrl(url: String): List<Pair<AtpExtension, Pair<String, String>>> {
        val results = mutableListOf<Pair<AtpExtension, Pair<String, String>>>()
        for (ext in _extensions.value) {
            if (!ext.isEnabled) continue
            val matches = ext.matches.any { matchesUrl(it, url) }
            if (matches) {
                val dir = File(extensionsDir, ext.id)
                val script = if (ext.permissions.contains("page-script")) {
                    File(dir, ext.scriptFile).takeIf { it.exists() }?.readText() ?: ""
                } else ""

                val styles = if (ext.permissions.contains("page-style")) {
                    File(dir, ext.stylesFile).takeIf { it.exists() }?.readText() ?: ""
                } else ""

                if (script.isNotBlank() || styles.isNotBlank()) {
                    results.add(Pair(ext, Pair(script, styles)))
                }
            }
        }
        return results
    }

    // Export .atpext (ZIP archive)
    fun exportExtension(id: String, outputStream: OutputStream): Boolean {
        val dir = File(extensionsDir, id)
        if (!dir.exists()) return false

        ZipOutputStream(outputStream).use { zos ->
            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.name != "state.json" && file.name != "storage.json") {
                    val relPath = file.relativeTo(dir).path
                    zos.putNextEntry(ZipEntry(relPath))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return true
    }

    // Import .atpext / ZIP
    fun importExtension(inputStream: InputStream): Pair<Boolean, String> {
        val tempId = "ext_temp_" + System.currentTimeMillis()
        val tempDir = File(extensionsDir, tempId)
        tempDir.mkdirs()

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                var fileCount = 0
                while (entry != null) {
                    fileCount++
                    if (fileCount > 50) return Pair(false, "Extension archive exceeds file count limit (max 50 files)")

                    // Path traversal check
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        tempDir.deleteRecursively()
                        return Pair(false, "Security violation: archive contains illegal relative paths ($name)")
                    }

                    val dest = File(tempDir, name)
                    if (entry.isDirectory) {
                        dest.mkdirs()
                    } else {
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { zis.copyTo(it) }
                    }
                    entry = zis.nextEntry
                }
            }

            val configFile = File(tempDir, "config.js")
            if (!configFile.exists()) {
                tempDir.deleteRecursively()
                return Pair(false, "Archive is missing required config.js")
            }

            val finalId = "ext_" + System.currentTimeMillis() + "_" + (1000..9999).random()
            val finalDir = File(extensionsDir, finalId)
            val validation = ExtensionValidator.parseConfigJs(configFile.readText(), finalId)
            if (!validation.isValid) {
                tempDir.deleteRecursively()
                return Pair(false, "Invalid config.js: ${validation.errorMessage}")
            }

            tempDir.renameTo(finalDir)
            File(finalDir, "state.json").writeText(JSONObject().put("enabled", false).toString())
            loadInstalledExtensions()
            return Pair(true, "Extension installed successfully")
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return Pair(false, "Failed to import extension: ${e.message}")
        }
    }
}
