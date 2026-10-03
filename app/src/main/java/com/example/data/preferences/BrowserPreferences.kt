package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SearchEngine(val title: String, val searchUrlTemplate: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    CUSTOM("Custom", "%s")
}

data class BrowserConfig(
    val searchEngine: String = "DuckDuckGo",
    val customSearchTemplate: String = "https://duckduckgo.com/?q=%s",
    val homePageType: String = "ATP_HOME", // ATP_HOME, BLANK, CUSTOM
    val customHomePageUrl: String = "https://duckduckgo.com",
    val startupBehavior: String = "RESTORE_PREVIOUS", // NEW_TAB, HOME_PAGE, RESTORE_PREVIOUS, RESTORE_WITH_BACKGROUND
    val addressBarPosition: String = "TOP", // TOP, BOTTOM
    val alwaysShowFullUrl: Boolean = false,
    val showSecurityIndicator: Boolean = true,
    val tabLayoutMode: String = "GRID", // GRID, LIST
    val autoSuspendMinutes: Int = 30, // 0 for OFF
    val tabAutoCleanupDays: Int = 0, // 0 for Never
    val performanceMode: String = "Balanced", // Balanced, Performance, Battery Saver
    val adBlockingEnabled: Boolean = true,
    val trackingProtectionEnabled: Boolean = true,
    val httpsOnlyMode: Boolean = true,
    val developerModeEnabled: Boolean = false,
    val customUserAgent: String = "",
    val desktopSiteDefault: Boolean = false,
    val javaScriptDefault: Boolean = true,
    val hardwareAcceleration: Boolean = true,
    val searchSuggestionsEnabled: Boolean = true,
    val historySuggestionsEnabled: Boolean = true,
    val bookmarkSuggestionsEnabled: Boolean = true
)

class BrowserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("atp_browser_prefs", Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<BrowserConfig> = _configFlow.asStateFlow()

    private fun loadConfig(): BrowserConfig {
        return BrowserConfig(
            searchEngine = prefs.getString("searchEngine", "DuckDuckGo") ?: "DuckDuckGo",
            customSearchTemplate = prefs.getString("customSearchTemplate", "https://duckduckgo.com/?q=%s") ?: "https://duckduckgo.com/?q=%s",
            homePageType = prefs.getString("homePageType", "ATP_HOME") ?: "ATP_HOME",
            customHomePageUrl = prefs.getString("customHomePageUrl", "https://duckduckgo.com") ?: "https://duckduckgo.com",
            startupBehavior = prefs.getString("startupBehavior", "RESTORE_PREVIOUS") ?: "RESTORE_PREVIOUS",
            addressBarPosition = prefs.getString("addressBarPosition", "TOP") ?: "TOP",
            alwaysShowFullUrl = prefs.getBoolean("alwaysShowFullUrl", false),
            showSecurityIndicator = prefs.getBoolean("showSecurityIndicator", true),
            tabLayoutMode = prefs.getString("tabLayoutMode", "GRID") ?: "GRID",
            autoSuspendMinutes = prefs.getInt("autoSuspendMinutes", 30),
            tabAutoCleanupDays = prefs.getInt("tabAutoCleanupDays", 0),
            performanceMode = prefs.getString("performanceMode", "Balanced") ?: "Balanced",
            adBlockingEnabled = prefs.getBoolean("adBlockingEnabled", true),
            trackingProtectionEnabled = prefs.getBoolean("trackingProtectionEnabled", true),
            httpsOnlyMode = prefs.getBoolean("httpsOnlyMode", true),
            developerModeEnabled = prefs.getBoolean("developerModeEnabled", false),
            customUserAgent = prefs.getString("customUserAgent", "") ?: "",
            desktopSiteDefault = prefs.getBoolean("desktopSiteDefault", false),
            javaScriptDefault = prefs.getBoolean("javaScriptDefault", true),
            hardwareAcceleration = prefs.getBoolean("hardwareAcceleration", true),
            searchSuggestionsEnabled = prefs.getBoolean("searchSuggestionsEnabled", true),
            historySuggestionsEnabled = prefs.getBoolean("historySuggestionsEnabled", true),
            bookmarkSuggestionsEnabled = prefs.getBoolean("bookmarkSuggestionsEnabled", true)
        )
    }

    fun updateConfig(block: (BrowserConfig) -> BrowserConfig) {
        val current = _configFlow.value
        val updated = block(current)
        prefs.edit().apply {
            putString("searchEngine", updated.searchEngine)
            putString("customSearchTemplate", updated.customSearchTemplate)
            putString("homePageType", updated.homePageType)
            putString("customHomePageUrl", updated.customHomePageUrl)
            putString("startupBehavior", updated.startupBehavior)
            putString("addressBarPosition", updated.addressBarPosition)
            putBoolean("alwaysShowFullUrl", updated.alwaysShowFullUrl)
            putBoolean("showSecurityIndicator", updated.showSecurityIndicator)
            putString("tabLayoutMode", updated.tabLayoutMode)
            putInt("autoSuspendMinutes", updated.autoSuspendMinutes)
            putInt("tabAutoCleanupDays", updated.tabAutoCleanupDays)
            putString("performanceMode", updated.performanceMode)
            putBoolean("adBlockingEnabled", updated.adBlockingEnabled)
            putBoolean("trackingProtectionEnabled", updated.trackingProtectionEnabled)
            putBoolean("httpsOnlyMode", updated.httpsOnlyMode)
            putBoolean("developerModeEnabled", updated.developerModeEnabled)
            putString("customUserAgent", updated.customUserAgent)
            putBoolean("desktopSiteDefault", updated.desktopSiteDefault)
            putBoolean("javaScriptDefault", updated.javaScriptDefault)
            putBoolean("hardwareAcceleration", updated.hardwareAcceleration)
            putBoolean("searchSuggestionsEnabled", updated.searchSuggestionsEnabled)
            putBoolean("historySuggestionsEnabled", updated.historySuggestionsEnabled)
            putBoolean("bookmarkSuggestionsEnabled", updated.bookmarkSuggestionsEnabled)
            apply()
        }
        _configFlow.value = updated
    }

    fun resetPreferences() {
        prefs.edit().clear().apply()
        _configFlow.value = loadConfig()
    }
}
