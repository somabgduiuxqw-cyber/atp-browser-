package com.example.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.webkit.WebView
import com.example.extensions.LogRedactor
import com.example.network.ProxyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class PerformanceMode {
    BALANCED,
    FAST,
    BATTERY_SAVER
}

data class DiagnosticsReport(
    val webViewVersion: String,
    val androidVersion: String,
    val deviceModel: String,
    val memoryUsageMb: String,
    val activeWebViews: Int,
    val suspendedTabs: Int,
    val performanceMode: String,
    val extensionCount: Int,
    val proxyState: String,
    val adsBlocked: Int,
    val trackersBlocked: Int
)

object DiagnosticsCollector {

    fun generateReport(
        context: Context,
        activeWebViews: Int,
        suspendedTabs: Int,
        performanceMode: String,
        extensionCount: Int,
        proxyState: ProxyState,
        adsBlocked: Int,
        trackersBlocked: Int
    ): DiagnosticsReport {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)

        val webViewPackage = try {
            WebView.getCurrentWebViewPackage()?.versionName ?: "Chromium System WebView"
        } catch (e: Exception) {
            "Android System WebView"
        }

        return DiagnosticsReport(
            webViewVersion = webViewPackage,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            memoryUsageMb = "${usedMem}MB / ${maxMem}MB",
            activeWebViews = activeWebViews,
            suspendedTabs = suspendedTabs,
            performanceMode = performanceMode,
            extensionCount = extensionCount,
            proxyState = proxyState.name,
            adsBlocked = adsBlocked,
            trackersBlocked = trackersBlocked
        )
    }

    fun toFormattedString(report: DiagnosticsReport): String {
        val raw = """
=== ATP BROWSER DIAGNOSTICS ===
Device: ${report.deviceModel}
OS: ${report.androidVersion}
WebView: ${report.webViewVersion}
Memory: ${report.memoryUsageMb}
Performance Mode: ${report.performanceMode}
Active Tabs: ${report.activeWebViews}
Suspended Tabs: ${report.suspendedTabs}
Extensions Loaded: ${report.extensionCount}
Proxy Status: ${report.proxyState}
Ads Blocked: ${report.adsBlocked}
Trackers Blocked: ${report.trackersBlocked}
Timestamp: ${System.currentTimeMillis()}
===============================
        """.trimIndent()
        return LogRedactor.redact(raw)
    }
}
