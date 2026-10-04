package com.example.ui

import android.app.Application
import android.net.Uri
import android.webkit.ConsoleMessage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AtpApplication
import com.example.data.db.*
import com.example.data.dns.DnsManager
import com.example.data.dns.DnsTestResult
import com.example.data.download.DownloadManager
import com.example.data.preferences.BrowserConfig
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.SearchEngine
import com.example.data.security.AdBlockManager
import com.example.data.security.SecurityScanner
import com.example.data.service.BackgroundSitesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Stack

data class ConsoleLogEntry(
    val message: String,
    val level: ConsoleMessage.MessageLevel,
    val sourceId: String,
    val lineNumber: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class RiskyPrompt(
    val url: String,
    val reason: String,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
)

data class RiskyFilePrompt(
    val filename: String,
    val reason: String,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
)

sealed class WebViewAction {
    object GoBack : WebViewAction()
    object GoForward : WebViewAction()
    object Reload : WebViewAction()
    object StopLoading : WebViewAction()
}

enum class ScreenState {
    BROWSER,
    TABS_TRAY,
    BACKGROUND_SITES,
    SECURITY_CENTER,
    DNS_SETTINGS,
    DOWNLOADS,
    HISTORY_BOOKMARKS,
    SETTINGS,
    DEVELOPER_MODE,
    ACCESS_KEY,
    EXTENSIONS,
    EXTENSION_EDITOR,
    PROXY_SETTINGS,
    PRIVACY_DASHBOARD,
    SITE_PERMISSIONS
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AtpApplication
    val db: BrowserDao = app.database.browserDao()
    val preferences: BrowserPreferences = app.preferences

    val config: StateFlow<BrowserConfig> = preferences.configFlow

    // Extensions & Network Managers
    val extensionManager = com.example.extensions.ExtensionManager(application)
    val networkManager = com.example.network.NetworkManager(application)

    // Editing Extension ID for Extension Editor
    val editingExtensionId = MutableStateFlow<String?>(null)

    // Renderer Crash Recovery
    private val _rendererCrashedTabId = MutableStateFlow<Long?>(null)
    val rendererCrashedTabId: StateFlow<Long?> = _rendererCrashedTabId.asStateFlow()

    // Proxy testing states
    private val _proxyTestResult = MutableStateFlow<com.example.network.ProxyTestResult?>(null)
    val proxyTestResult: StateFlow<com.example.network.ProxyTestResult?> = _proxyTestResult.asStateFlow()

    private val _isTestingProxy = MutableStateFlow(false)
    val isTestingProxy: StateFlow<Boolean> = _isTestingProxy.asStateFlow()

    // Navigation Screens
    private val _currentScreen = MutableStateFlow(ScreenState.BROWSER)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    // Tabs
    val tabs: StateFlow<List<BrowserTab>> = db.getAllTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTabId = MutableStateFlow<Long?>(null)
    val activeTabId: StateFlow<Long?> = _activeTabId.asStateFlow()

    private val closedTabsStack = Stack<BrowserTab>()

    // Current Web Page State
    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _currentTitle = MutableStateFlow("New Tab")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _isSecureHttps = MutableStateFlow(true)
    val isSecureHttps: StateFlow<Boolean> = _isSecureHttps.asStateFlow()

    // Find in Page
    private val _findInPageActive = MutableStateFlow(false)
    val findInPageActive: StateFlow<Boolean> = _findInPageActive.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    private val _findMatches = MutableStateFlow(Pair(0, 0)) // current, total
    val findMatches: StateFlow<Pair<Int, Int>> = _findMatches.asStateFlow()

    // Dialog Prompts
    private val _pendingRiskyPrompt = MutableStateFlow<RiskyPrompt?>(null)
    val pendingRiskyPrompt: StateFlow<RiskyPrompt?> = _pendingRiskyPrompt.asStateFlow()

    private val _pendingRiskyFilePrompt = MutableStateFlow<RiskyFilePrompt?>(null)
    val pendingRiskyFilePrompt: StateFlow<RiskyFilePrompt?> = _pendingRiskyFilePrompt.asStateFlow()

    // Console logs for Developer Mode
    private val _consoleLogs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLogEntry>> = _consoleLogs.asStateFlow()

    // Page Source
    private val _pageSource = MutableStateFlow<String?>(null)
    val pageSource: StateFlow<String?> = _pageSource.asStateFlow()

    // DNS Test
    private val _dnsTestResult = MutableStateFlow<DnsTestResult?>(null)
    val dnsTestResult: StateFlow<DnsTestResult?> = _dnsTestResult.asStateFlow()

    private val _isTestingDns = MutableStateFlow(false)
    val isTestingDns: StateFlow<Boolean> = _isTestingDns.asStateFlow()

    private val _webViewActions = MutableSharedFlow<WebViewAction>(extraBufferCapacity = 1)
    val webViewActions: SharedFlow<WebViewAction> = _webViewActions.asSharedFlow()

    fun triggerGoBack() {
        _webViewActions.tryEmit(WebViewAction.GoBack)
    }

    fun triggerGoForward() {
        _webViewActions.tryEmit(WebViewAction.GoForward)
    }

    fun triggerReload() {
        _webViewActions.tryEmit(WebViewAction.Reload)
    }

    fun triggerStopLoading() {
        _webViewActions.tryEmit(WebViewAction.StopLoading)
    }

    // Search Suggestions
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    init {
        viewModelScope.launch {
            // Restore session tabs or create initial tab
            val existing = db.getAllTabs().first()
            if (existing.isEmpty()) {
                val initTab = BrowserTab(
                    url = "about:home",
                    title = "ATP Browser",
                    incognito = false
                )
                val id = db.insertTab(initTab)
                _activeTabId.value = id
                _currentUrl.value = "about:home"
                _currentTitle.value = "ATP Browser"
            } else {
                val startup = config.value.startupBehavior
                when (startup) {
                    "NEW_TAB" -> {
                        val newTab = BrowserTab(url = "about:home", title = "New Tab")
                        val id = db.insertTab(newTab)
                        _activeTabId.value = id
                        _currentUrl.value = "about:home"
                    }
                    "HOME_PAGE" -> {
                        val homeUrl = if (config.value.homePageType == "CUSTOM") config.value.customHomePageUrl else "about:home"
                        val newTab = BrowserTab(url = homeUrl, title = "Home")
                        val id = db.insertTab(newTab)
                        _activeTabId.value = id
                        _currentUrl.value = homeUrl
                    }
                    else -> {
                        // Restore previous tabs
                        val firstTab = existing.first()
                        _activeTabId.value = firstTab.id
                        _currentUrl.value = firstTab.url
                        _currentTitle.value = firstTab.title
                    }
                }
            }

            // Sync active DNS profile if enabled
            val activeDns = db.getActiveDnsProfile()
            if (activeDns != null && activeDns.enabled) {
                DnsManager.currentMode = "Custom"
                DnsManager.activeProfileName = activeDns.name
                DnsManager.primaryServer = activeDns.primaryServer
                DnsManager.secondaryServer = activeDns.secondaryServer
                DnsManager.protocol = activeDns.protocol
            } else {
                DnsManager.currentMode = "System"
            }
        }
    }

    fun navigateToScreen(screen: ScreenState) {
        _currentScreen.value = screen
    }

    fun setScreen(screen: ScreenState) = navigateToScreen(screen)

    fun navigateBackToBrowser() {
        _currentScreen.value = ScreenState.BROWSER
    }

    // --- Tab Management ---
    fun createNewTab(url: String = "about:home", incognito: Boolean = false) {
        viewModelScope.launch {
            val title = if (url == "about:home") "ATP Browser" else url
            val tab = BrowserTab(url = url, title = title, incognito = incognito)
            val id = db.insertTab(tab)
            _activeTabId.value = id
            _currentUrl.value = url
            _currentTitle.value = title
            _currentScreen.value = ScreenState.BROWSER
        }
    }

    fun switchTab(tabId: Long) {
        viewModelScope.launch {
            val tab = db.getTabById(tabId) ?: return@launch
            _activeTabId.value = tabId
            _currentUrl.value = tab.url
            _currentTitle.value = tab.title
            db.updateTab(tab.copy(lastAccessed = System.currentTimeMillis(), isSuspended = false))
            com.example.ui.browser.TabWebViewManager.resumeTab(tabId)
            val webView = com.example.ui.browser.TabWebViewManager.getWebView(tabId)
            _canGoBack.value = webView?.canGoBack() ?: false
            _canGoForward.value = webView?.canGoForward() ?: false
            _currentScreen.value = ScreenState.BROWSER
        }
    }

    fun closeTab(tabId: Long) {
        viewModelScope.launch {
            com.example.ui.browser.TabWebViewManager.destroyTab(tabId)
            val tabToClose = db.getTabById(tabId)
            if (tabToClose != null && !tabToClose.incognito) {
                closedTabsStack.push(tabToClose)
            }
            db.deleteTabById(tabId)

            val remaining = db.getAllTabs().first()
            if (remaining.isEmpty()) {
                createNewTab("about:home", false)
            } else if (_activeTabId.value == tabId) {
                val next = remaining.first()
                _activeTabId.value = next.id
                _currentUrl.value = next.url
                _currentTitle.value = next.title
                com.example.ui.browser.TabWebViewManager.resumeTab(next.id)
            }
        }
    }

    fun closeOtherTabs(tabId: Long, isIncognito: Boolean) {
        viewModelScope.launch {
            val all = db.getAllTabs().first()
            for (t in all) {
                if (t.id != tabId && t.incognito == isIncognito) {
                    com.example.ui.browser.TabWebViewManager.destroyTab(t.id)
                }
            }
            db.deleteOtherTabs(tabId, isIncognito)
        }
    }

    fun closeTabsToRight(tabId: Long, isIncognito: Boolean) {
        viewModelScope.launch {
            val all = db.getAllTabs().first().filter { it.incognito == isIncognito }
            val index = all.indexOfFirst { it.id == tabId }
            if (index != -1 && index < all.size - 1) {
                val toRight = all.subList(index + 1, all.size)
                for (t in toRight) {
                    com.example.ui.browser.TabWebViewManager.destroyTab(t.id)
                }
            }
            db.deleteTabsToRight(tabId, isIncognito)
        }
    }

    fun duplicateTab(tabId: Long) {
        viewModelScope.launch {
            val tab = db.getTabById(tabId) ?: return@launch
            val duplicated = tab.copy(id = 0, lastAccessed = System.currentTimeMillis())
            val newId = db.insertTab(duplicated)
            _activeTabId.value = newId
            _currentUrl.value = duplicated.url
            _currentTitle.value = duplicated.title
        }
    }

    fun restoreLastClosedTab() {
        if (closedTabsStack.isNotEmpty()) {
            val tab = closedTabsStack.pop()
            viewModelScope.launch {
                val id = db.insertTab(tab.copy(id = 0, lastAccessed = System.currentTimeMillis()))
                _activeTabId.value = id
                _currentUrl.value = tab.url
                _currentTitle.value = tab.title
            }
        }
    }

    fun toggleDesktopMode() {
        val tabId = _activeTabId.value ?: return
        viewModelScope.launch {
            val tab = db.getTabById(tabId) ?: return@launch
            val updated = !tab.desktopMode
            db.updateTab(tab.copy(desktopMode = updated))
        }
    }

    // --- URL Navigation & Search ---
    fun onUrlSubmitted(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        val targetUrl = resolveInputToUrl(trimmed)

        // Security check
        val scan = SecurityScanner.checkUrlRisk(targetUrl)
        if (scan.isThreatDetected && scan.status == "Dangerous") {
            _pendingRiskyPrompt.value = RiskyPrompt(
                url = targetUrl,
                reason = scan.threatReason ?: "This destination may contain security risks.",
                onConfirm = {
                    _pendingRiskyPrompt.value = null
                    loadUrl(targetUrl)
                },
                onCancel = {
                    _pendingRiskyPrompt.value = null
                }
            )
        } else {
            loadUrl(targetUrl)
        }
    }

    fun loadUrl(url: String) {
        _currentUrl.value = url
        val currentId = _activeTabId.value ?: return
        com.example.ui.browser.TabWebViewManager.loadUrl(currentId, url)
        viewModelScope.launch {
            val tab = db.getTabById(currentId) ?: return@launch
            db.updateTab(tab.copy(url = url, lastAccessed = System.currentTimeMillis(), isSuspended = false))

            // Save history if not incognito
            if (!tab.incognito && !url.startsWith("about:")) {
                db.insertHistory(HistoryEntry(url = url, title = _currentTitle.value))
            }
        }
    }

    fun onTabProcessCrashed(tabId: Long) {
        _rendererCrashedTabId.value = tabId
    }

    fun recoverCrashedTab(tabId: Long, reload: Boolean) {
        _rendererCrashedTabId.value = null
        if (reload) {
            viewModelScope.launch {
                val tab = db.getTabById(tabId) ?: return@launch
                if (tab.url.isNotBlank() && tab.url != "about:home") {
                    loadUrl(tab.url)
                }
            }
        } else {
            closeTab(tabId)
        }
    }

    // --- Shield & Protection ---
    fun isShieldDisabledForCurrentSite(): Boolean {
        val host = try { Uri.parse(_currentUrl.value).host } catch (e: Exception) { null } ?: return false
        return com.example.protection.SiteExceptionManager.isShieldDisabled(host)
    }

    fun toggleShieldForCurrentSite() {
        val host = try { Uri.parse(_currentUrl.value).host } catch (e: Exception) { null } ?: return
        if (com.example.protection.SiteExceptionManager.isShieldDisabled(host)) {
            com.example.protection.SiteExceptionManager.enableShieldForSite(host)
        } else {
            com.example.protection.SiteExceptionManager.disableShieldForSite(host)
        }
    }

    // --- Proxy Actions ---
    fun testProxy(endpoint: String) {
        viewModelScope.launch {
            _isTestingProxy.value = true
            _proxyTestResult.value = networkManager.proxyManager.testConnection(endpoint)
            _isTestingProxy.value = false
        }
    }

    fun applyUserProxy(endpoint: String, bypassRules: List<String>, onResult: (Boolean, String?) -> Unit) {
        networkManager.proxyManager.applyProxy(endpoint, bypassRules, "User", onResult)
    }

    fun clearProxy(onComplete: (() -> Unit)? = null) {
        networkManager.proxyManager.clearProxy(onComplete)
    }

    // --- Extensions Navigation ---
    fun openExtensionEditor(extensionId: String? = null) {
        editingExtensionId.value = extensionId
        _currentScreen.value = ScreenState.EXTENSION_EDITOR
    }

    fun setPerformanceMode(mode: String) {
        preferences.updateConfig { it.copy(performanceMode = mode) }
    }

    private fun resolveInputToUrl(input: String): String {
        // Special internal schemes
        if (input == "about:home" || input == "about:blank") return input

        // Check if looks like a valid URL
        val hasSpace = input.contains(" ")
        val hasScheme = input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)
        val hasDomainDot = input.contains(".") && !input.endsWith(".") && !input.startsWith(".")

        return if (!hasSpace && (hasScheme || hasDomainDot)) {
            if (!hasScheme) "https://$input" else input
        } else {
            // Build search engine query
            val engine = config.value.searchEngine
            val template = when (engine) {
                "Google" -> "https://www.google.com/search?q=%s"
                "Bing" -> "https://www.bing.com/search?q=%s"
                "Brave Search" -> "https://search.brave.com/search?q=%s"
                "Custom" -> config.value.customSearchTemplate
                else -> "https://duckduckgo.com/?q=%s" // DuckDuckGo default
            }
            val encoded = Uri.encode(input)
            template.replace("%s", encoded)
        }
    }

    fun updateSuggestions(query: String) {
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableListOf<String>()
            if (config.value.historySuggestionsEnabled) {
                val hist = db.getHistorySuggestions(query)
                results.addAll(hist.map { it.url })
            }
            if (config.value.bookmarkSuggestionsEnabled) {
                val bms = db.getBookmarkSuggestions(query)
                results.addAll(bms.map { it.url })
            }
            _suggestions.value = results.distinct().take(6)
        }
    }

    // --- Web State Callback ---
    fun onPageStarted(url: String) {
        _currentUrl.value = url
        _loadingProgress.value = 10
        _isSecureHttps.value = url.startsWith("https://", ignoreCase = true)
    }

    fun onPageFinished(url: String, title: String?, canBack: Boolean, canForward: Boolean) {
        _currentUrl.value = url
        if (!title.isNullOrBlank()) {
            _currentTitle.value = title
        }
        _canGoBack.value = canBack
        _canGoForward.value = canForward
        _loadingProgress.value = 0
        _isSecureHttps.value = url.startsWith("https://", ignoreCase = true)

        val currentId = _activeTabId.value ?: return
        viewModelScope.launch {
            val tab = db.getTabById(currentId) ?: return@launch
            db.updateTab(tab.copy(url = url, title = title ?: tab.title))
            if (!tab.incognito && !url.startsWith("about:")) {
                db.insertHistory(HistoryEntry(url = url, title = title ?: url))
            }
        }
    }

    fun onProgressChanged(newProgress: Int) {
        _loadingProgress.value = if (newProgress >= 100) 0 else newProgress
    }

    // --- Keep Alive / Background Sites ---
    fun toggleKeepAliveForCurrentSite(mode: String = "Standard") {
        val url = _currentUrl.value
        val title = _currentTitle.value
        if (url.isBlank() || url.startsWith("about:")) return

        viewModelScope.launch {
            val existing = db.getBackgroundSiteByUrl(url)
            if (existing != null) {
                db.deleteBackgroundSite(existing)
            } else {
                db.insertBackgroundSite(
                    BackgroundSite(
                        url = url,
                        title = title,
                        mode = mode,
                        status = "Running"
                    )
                )
                // Start Foreground Service
                BackgroundSitesService.start(getApplication())
            }
        }
    }

    fun updateBackgroundSiteStatus(siteId: Long, status: String) {
        viewModelScope.launch {
            val site = db.getBackgroundSiteById(siteId) ?: return@launch
            db.updateBackgroundSite(site.copy(status = status, lastActive = System.currentTimeMillis()))
        }
    }

    fun removeBackgroundSite(siteId: Long) {
        viewModelScope.launch {
            db.deleteBackgroundSiteById(siteId)
        }
    }

    // --- Bookmarks ---
    fun toggleBookmarkCurrentPage() {
        val url = _currentUrl.value
        val title = _currentTitle.value
        if (url.isBlank() || url.startsWith("about:")) return

        viewModelScope.launch {
            val bookmarks = db.getAllBookmarks().first()
            val existing = bookmarks.find { it.url == url }
            if (existing != null) {
                db.deleteBookmark(existing)
            } else {
                db.insertBookmark(Bookmark(title = title, url = url))
            }
        }
    }

    // --- Resumable Downloads ---
    fun requestDownload(url: String, suggestedFilename: String? = null) {
        val filename = suggestedFilename ?: url.substringAfterLast('/').substringBefore('?')
        if (SecurityScanner.isRiskyExtension(filename)) {
            _pendingRiskyFilePrompt.value = RiskyFilePrompt(
                filename = filename,
                reason = "This file can contain executable or active code (.${filename.substringAfterLast('.')}).",
                onConfirm = {
                    _pendingRiskyFilePrompt.value = null
                    DownloadManager.startDownload(getApplication(), viewModelScope, url, filename)
                    navigateToScreen(ScreenState.DOWNLOADS)
                },
                onCancel = {
                    _pendingRiskyFilePrompt.value = null
                }
            )
        } else {
            DownloadManager.startDownload(getApplication(), viewModelScope, url, filename)
            navigateToScreen(ScreenState.DOWNLOADS)
        }
    }

    // --- Find In Page ---
    fun openFindInPage() {
        _findInPageActive.value = true
    }

    fun closeFindInPage() {
        _findInPageActive.value = false
        _findQuery.value = ""
        _findMatches.value = Pair(0, 0)
    }

    fun setFindQuery(query: String) {
        _findQuery.value = query
    }

    fun updateFindMatches(current: Int, total: Int) {
        _findMatches.value = Pair(current, total)
    }

    // --- Developer Mode ---
    fun addConsoleLog(message: String, level: ConsoleMessage.MessageLevel, sourceId: String, lineNumber: Int) {
        val entry = ConsoleLogEntry(message, level, sourceId, lineNumber)
        _consoleLogs.value = (_consoleLogs.value + entry).takeLast(200)
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
    }

    fun setPageSource(source: String) {
        _pageSource.value = source
    }

    // --- DNS Management ---
    fun testDns(server: String, protocol: String) {
        viewModelScope.launch {
            _isTestingDns.value = true
            val result = DnsManager.testDnsServer(server, protocol)
            _dnsTestResult.value = result
            _isTestingDns.value = false
        }
    }

    fun enableCustomDns(profile: DnsProfile) {
        viewModelScope.launch {
            db.disableAllDnsProfiles()
            db.enableDnsProfile(profile.id)
            DnsManager.currentMode = "Custom"
            DnsManager.activeProfileName = profile.name
            DnsManager.primaryServer = profile.primaryServer
            DnsManager.secondaryServer = profile.secondaryServer
            DnsManager.protocol = profile.protocol
        }
    }

    fun disableCustomDns() {
        viewModelScope.launch {
            db.disableAllDnsProfiles()
            DnsManager.currentMode = "System"
            DnsManager.activeProfileName = null
            DnsManager.primaryServer = ""
            DnsManager.secondaryServer = ""
        }
    }

    // --- Clear Browsing Data ---
    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearPartialDownloads: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (clearHistory) {
                db.clearAllHistory()
            }
            if (clearCookies) {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
            }
            if (clearCache) {
                com.example.ui.browser.TabWebViewManager.destroyAll()
                android.webkit.WebStorage.getInstance().deleteAllData()
            }
            if (clearPartialDownloads) {
                DownloadManager.clearDownloadCache(getApplication())
            }
        }
    }
}
