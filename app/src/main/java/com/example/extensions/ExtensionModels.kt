package com.example.extensions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ExtensionProxyConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val bypass: List<String> = emptyList()
)

data class AtpExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val matches: List<String>,
    val permissions: List<String>,
    val scriptFile: String = "script.js",
    val stylesFile: String = "styles.css",
    val proxyConfig: ExtensionProxyConfig? = null,
    val isEnabled: Boolean = false,
    val storageSize: Long = 0L,
    val installTime: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val consecutiveErrors: Int = 0
)

data class ExtensionLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val extensionId: String,
    val level: String,
    val message: String
)

/**
 * Validates extension manifests, scripts, CSS and match patterns.
 */
object ExtensionValidator {

    private val ALLOWED_PERMISSIONS = setOf("storage", "page-script", "page-style", "proxy")

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val extension: AtpExtension? = null
    )

    fun validateMatchPattern(pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == "<all_urls>") return true

        // Supported formats:
        // *://example.com/*
        // https://example.com/*
        // https://*.example.com/*
        // http://example.com/*
        val regex = Regex("""^(\*|https?|file|ftp)://(\*|\*\.[a-zA-Z0-9.-]+|[a-zA-Z0-9.-]+)(:[0-9]+)?(/.*)?$""")
        return regex.matches(trimmed)
    }

    fun parseConfigJs(configContent: String, extensionId: String): ValidationResult {
        try {
            // Support JSON directly or JS "export default { ... }"
            var cleanJson = configContent.trim()
            if (cleanJson.startsWith("export default")) {
                cleanJson = cleanJson.substringAfter("export default").trim()
            }
            if (cleanJson.endsWith(";")) {
                cleanJson = cleanJson.substring(0, cleanJson.length - 1).trim()
            }

            // Convert common JS object literal formatting to valid JSON if needed
            val json = JSONObject(cleanJson)

            val name = json.optString("name", "").trim()
            if (name.isEmpty()) return ValidationResult(false, "Extension name is required in config.js")

            val version = json.optString("version", "1.0.0").trim()
            if (!version.matches(Regex("""^\d+(\.\d+)*$"""))) {
                return ValidationResult(false, "Invalid semantic version: $version")
            }

            val description = json.optString("description", "")
            val author = json.optString("author", "Unknown")

            val matchesArray = json.optJSONArray("matches") ?: JSONArray()
            val matches = mutableListOf<String>()
            for (i in 0 until matchesArray.length()) {
                val p = matchesArray.getString(i)
                if (!validateMatchPattern(p)) {
                    return ValidationResult(false, "Invalid match pattern: $p")
                }
                matches.add(p)
            }

            val permsArray = json.optJSONArray("permissions") ?: JSONArray()
            val permissions = mutableListOf<String>()
            for (i in 0 until permsArray.length()) {
                val perm = permsArray.getString(i)
                if (perm !in ALLOWED_PERMISSIONS) {
                    return ValidationResult(false, "Unsupported permission: $perm")
                }
                permissions.add(perm)
            }

            var proxyConfig: ExtensionProxyConfig? = null
            if (json.has("proxy")) {
                val proxyObj = json.getJSONObject("proxy")
                val bypassArray = proxyObj.optJSONArray("bypass") ?: JSONArray()
                val bypassList = mutableListOf<String>()
                for (b in 0 until bypassArray.length()) {
                    bypassList.add(bypassArray.getString(b))
                }
                proxyConfig = ExtensionProxyConfig(
                    enabled = proxyObj.optBoolean("enabled", false),
                    endpoint = proxyObj.optString("endpoint", "").trim(),
                    bypass = bypassList
                )
            }

            val script = json.optString("script", "script.js")
            val styles = json.optString("styles", "styles.css")

            val ext = AtpExtension(
                id = extensionId,
                name = name,
                version = version,
                description = description,
                author = author,
                matches = matches,
                permissions = permissions,
                scriptFile = script,
                stylesFile = styles,
                proxyConfig = proxyConfig,
                isEnabled = false
            )

            return ValidationResult(true, null, ext)
        } catch (e: Exception) {
            return ValidationResult(false, "Failed to parse config.js: ${e.message}")
        }
    }
}

/**
 * Isolated persistent storage for each extension.
 */
class ExtensionStorageManager(private val context: Context) {

    private fun getStorageFile(extensionId: String): File {
        val dir = File(context.filesDir, "extensions/$extensionId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "storage.json")
    }

    @Synchronized
    fun get(extensionId: String, key: String): String? {
        val file = getStorageFile(extensionId)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            if (json.has(key)) json.getString(key) else null
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun set(extensionId: String, key: String, value: String): Boolean {
        val file = getStorageFile(extensionId)
        return try {
            val json = if (file.exists()) JSONObject(file.readText()) else JSONObject()
            json.put(key, value)
            file.writeText(json.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun remove(extensionId: String, key: String): Boolean {
        val file = getStorageFile(extensionId)
        if (!file.exists()) return true
        return try {
            val json = JSONObject(file.readText())
            json.remove(key)
            file.writeText(json.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun clear(extensionId: String): Boolean {
        val file = getStorageFile(extensionId)
        return if (file.exists()) file.delete() else true
    }

    fun getStorageSize(extensionId: String): Long {
        val file = getStorageFile(extensionId)
        return if (file.exists()) file.length() else 0L
    }
}
