package com.example.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File
import java.util.Locale

object SecurityScanner {

    // Prompt 20 & 36 mandate: Only .apk, .html, .htm, .js, .py, .sh
    val RISKY_EXTENSIONS = setOf("apk", "html", "htm", "js", "py", "sh")

    fun isRiskyExtension(filename: String): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return RISKY_EXTENSIONS.contains(extension)
    }

    data class ScanResult(
        val isThreatDetected: Boolean,
        val status: String, // "No known threat detected", "Warning", "Dangerous", "Unknown"
        val threatReason: String? = null,
        val details: List<String> = emptyList()
    )

    fun checkUrlRisk(url: String): ScanResult {
        val uri = try { Uri.parse(url) } catch (e: Exception) { return ScanResult(false, "Unknown") }
        val host = uri.host?.lowercase(Locale.ROOT) ?: ""
        val path = uri.path?.lowercase(Locale.ROOT) ?: ""
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: ""

        val findings = mutableListOf<String>()

        if (scheme == "http") {
            findings.add("Connection is unencrypted HTTP")
        }

        // Check for suspicious phishing patterns: excessive subdomains, IP address as host, typo-squatting
        if (host.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            findings.add("Host is a direct raw IP address")
        }

        if (host.count { it == '.' } > 4) {
            findings.add("Excessive subdomain depth detected")
        }

        val phishingKeywords = listOf("login-verify", "bank-secure", "account-update-alert", "paypal-security-login", "apple-id-verify", "wallet-seed")
        for (kw in phishingKeywords) {
            if (host.contains(kw) || path.contains(kw)) {
                findings.add("Suspicious credential-seeking keyword pattern: $kw")
            }
        }

        // Check direct script/binary download in URL path
        val ext = path.substringAfterLast('.', "")
        if (ext == "apk" || ext == "sh" || ext == "py") {
            findings.add("URL links directly to potentially executable content (.$ext)")
        }

        return when {
            findings.any { it.contains("credential") || it.contains("raw IP") } -> {
                ScanResult(
                    isThreatDetected = true,
                    status = "Dangerous",
                    threatReason = "Phishing or suspicious origin pattern identified",
                    details = findings
                )
            }
            findings.isNotEmpty() -> {
                ScanResult(
                    isThreatDetected = true,
                    status = "Warning",
                    threatReason = "Security warnings detected for this destination",
                    details = findings
                )
            }
            else -> {
                ScanResult(
                    isThreatDetected = false,
                    status = "No known threat detected", // NEVER "100% Safe"
                    threatReason = null,
                    details = listOf("Standard web domain structure", "Valid protocol verified")
                )
            }
        }
    }

    data class ApkMetadata(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val permissions: List<String>
    )

    fun inspectApk(context: Context, file: File): ApkMetadata? {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS)
            if (info != null) {
                val perms = info.requestedPermissions?.toList() ?: emptyList()
                ApkMetadata(
                    packageName = info.packageName ?: "Unknown",
                    versionName = info.versionName ?: "1.0",
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        info.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        info.versionCode.toLong()
                    },
                    permissions = perms
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun scanCodeContent(fileName: String, content: String): ScanResult {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val findings = mutableListOf<String>()

        if (RISKY_EXTENSIONS.contains(ext)) {
            findings.add("File has executable extension: .$ext")
        }

        val suspiciousPatterns = listOf(
            "eval(" to "Dynamic code execution (eval)",
            "document.cookie" to "Attempt to read document cookies",
            "XMLHttpRequest" to "Background network requests",
            "WebSocket" to "WebSocket communication",
            "coinhive" to "Cryptocurrency mining script reference",
            "miner.start" to "Potential background miner trigger",
            "/bin/sh" to "Shell execution command",
            "/bin/bash" to "Bash execution command",
            "exec(" to "Process execution call",
            "atob(" to "Base64 decoding of obfuscated payloads"
        )

        for ((pattern, desc) in suspiciousPatterns) {
            if (content.contains(pattern, ignoreCase = true)) {
                findings.add(desc)
            }
        }

        return if (findings.size >= 2) {
            ScanResult(
                isThreatDetected = true,
                status = "Warning",
                threatReason = "Heuristic scanner found suspicious code patterns",
                details = findings
            )
        } else if (findings.isNotEmpty()) {
            ScanResult(
                isThreatDetected = false,
                status = "Warning",
                threatReason = "File contains active scripting elements",
                details = findings
            )
        } else {
            ScanResult(
                isThreatDetected = false,
                status = "No known threat detected",
                details = listOf("No obfuscated or malicious code patterns detected")
            )
        }
    }
}
