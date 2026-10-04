package com.example.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import com.example.data.db.BrowserTab
import com.example.data.db.SecurityEvent
import com.example.data.preferences.BrowserConfig
import com.example.data.security.AdBlockManager
import com.example.ui.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * TabWebViewManager manages persistent Chromium WebView instances across tabs
 * to prevent reload thrashing, state loss, and memory leaks.
 */
object TabWebViewManager {
    private const val TAG = "TabWebViewManager"

    // Active WebView instances mapped by tab ID
    private val webViewMap = ConcurrentHashMap<Long, WebView>()
    // Tracking access time for LRU tab suspension
    private val lastAccessMap = ConcurrentHashMap<Long, Long>()

    // Transparent 1x1 PNG bytes for cleanly blocking image advertisements
    private val TRANSPARENT_1X1_PNG: ByteArray by lazy {
        android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
            android.util.Base64.DEFAULT
        )
    }

    fun getWebView(tabId: Long): WebView? {
        return webViewMap[tabId]
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(
        context: Context,
        tab: BrowserTab,
        viewModel: BrowserViewModel,
        onFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit
    ): WebView {
        lastAccessMap[tab.id] = System.currentTimeMillis()

        webViewMap[tab.id]?.let { existing ->
            updateTabSettings(existing, tab, viewModel.config.value)
            return existing
        }

        // Apply performance mode limits before creating new WebView
        pruneExcessWebViews(tab.id, viewModel.config.value.performanceMode)

        val webView = WebView(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Hardware acceleration
            val hwEnabled = viewModel.config.value.hardwareAcceleration
            if (hwEnabled) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }

            settings.apply {
                javaScriptEnabled = viewModel.config.value.javaScriptDefault
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            // Cookie handling
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            if (tab.incognito) {
                cookieManager.setAcceptThirdPartyCookies(this, false)
            } else {
                cookieManager.setAcceptThirdPartyCookies(this, true)
            }

            val redirectHistory = mutableListOf<String>()
            val scope = CoroutineScope(Dispatchers.Main)

            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val urlStr = uri.toString()
                    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: ""

                    // Check redirect loop
                    redirectHistory.add(urlStr)
                    if (redirectHistory.size > 12) {
                        redirectHistory.clear()
                        scope.launch(Dispatchers.IO) {
                            try {
                                viewModel.db.insertSecurityEvent(
                                    SecurityEvent(
                                        domain = uri.host ?: "unknown",
                                        eventType = "Suspicious Navigation",
                                        details = "Redirect loop limit exceeded"
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error logging security event", e)
                            }
                        }
                        return true
                    }

                    // External schemes: mailto, tel, geo, sms, intent
                    if (scheme != "http" && scheme != "https" && scheme != "about") {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.w(TAG, "No handler for scheme $scheme")
                        }
                        return true
                    }

                    return false
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    // NEVER block the main page frame
                    if (request?.isForMainFrame == true) {
                        return super.shouldInterceptRequest(view, request)
                    }

                    val reqUrl = request?.url?.toString() ?: return null
                    val pageHost = try { Uri.parse(view?.url ?: "").host } catch (e: Exception) { null }

                    val decision = AdBlockManager.shouldBlock(reqUrl, pageHost)
                    if (decision is AdBlockManager.BlockDecision.Blocked) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                viewModel.db.insertSecurityEvent(
                                    SecurityEvent(
                                        domain = pageHost ?: "Unknown",
                                        eventType = if (decision.reason.contains("Tracker")) "Blocked Tracker" else "Blocked Ad",
                                        details = "${decision.reason}: ${decision.pattern}"
                                    )
                                )
                            } catch (e: Exception) {
                                // DB protection
                            }
                        }

                        val lower = reqUrl.lowercase(Locale.ROOT)
                        return when {
                            // Google AdSense defuser: provide stub to avoid JavaScript TypeErrors
                            lower.contains("adsbygoogle") -> {
                                val script = "window.adsbygoogle = window.adsbygoogle || []; window.adsbygoogle.loaded = true;"
                                WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(script.toByteArray()))
                            }
                            // Google Analytics / GTag defuser
                            lower.contains("google-analytics.com") || lower.contains("analytics.js") || lower.contains("gtag/js") || lower.contains("/ga.js") -> {
                                val script = "window.ga = window.ga || function(){}; window.gtag = window.gtag || function(){}; window.dataLayer = window.dataLayer || [];"
                                WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(script.toByteArray()))
                            }
                            // Facebook Pixel defuser
                            lower.contains("fbevents.js") || lower.contains("facebook.com/tr") -> {
                                val script = "window.fbq = window.fbq || function(){};"
                                WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(script.toByteArray()))
                            }
                            // Amazon Ads defuser
                            lower.contains("apstag.js") -> {
                                val script = "window.apstag = window.apstag || {init:function(){},fetchBids:function(){}};"
                                WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(script.toByteArray()))
                            }
                            // DoubleClick / GPT defuser
                            lower.contains("gpt.js") -> {
                                val script = "window.googletag = window.googletag || {cmd:[],defineSlot:function(){return {addService:function(){}}},pubads:function(){return {enableSingleRequest:function(){},enableServices:function(){}}},enableServices:function(){}};"
                                WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(script.toByteArray()))
                            }
                            // Other JavaScript files
                            lower.endsWith(".js") || lower.contains("javascript") -> {
                                val stub = "/* blocked by ATP Browser */"
                                WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(stub.toByteArray()))
                            }
                            // Images: return 1x1 transparent PNG
                            lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".jpg") ||
                            lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.contains("banner") -> {
                                WebResourceResponse("image/png", "UTF-8", ByteArrayInputStream(TRANSPARENT_1X1_PNG))
                            }
                            // CSS
                            lower.endsWith(".css") -> {
                                WebResourceResponse("text/css", "UTF-8", ByteArrayInputStream("".toByteArray()))
                            }
                            else -> {
                                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                        }
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let { viewModel.onPageStarted(it) }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    redirectHistory.clear()
                    url?.let {
                        viewModel.onPageFinished(
                            it,
                            view?.title,
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false
                        )
                    }

                    // Cosmetic Ad Blocker Injection (Hides ad containers without breaking site layout)
                    if (viewModel.config.value.adBlockingEnabled) {
                        val cosmeticJs = """
                            (function() {
                                var css = '.adsbygoogle, [id^="google_ads"], [id^="div-gpt-ad"], .ad-banner, .advertisement, [class*="sponsored"], [class*="ad-container"], .ad-box, .banner-ad, [data-ad], #ad-container, div[class*="ad-box"] { display: none !important; visibility: hidden !important; height: 0 !important; max-height: 0 !important; }';
                                var style = document.createElement('style');
                                style.type = 'text/css';
                                style.id = 'atp-cosmetic-adblock';
                                if (!document.getElementById('atp-cosmetic-adblock')) {
                                    style.appendChild(document.createTextNode(css));
                                    (document.head || document.documentElement).appendChild(style);
                                }
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(cosmeticJs, null)
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    // CRITICAL: Never bypass TLS certificate errors. Never silently weaken WebView security.
                    handler?.cancel()
                    val errUrl = error?.url ?: "Unknown URL"
                    scope.launch(Dispatchers.IO) {
                        try {
                            viewModel.db.insertSecurityEvent(
                                SecurityEvent(
                                    domain = try { Uri.parse(errUrl).host ?: errUrl } catch (e: Exception) { errUrl },
                                    eventType = "Certificate Warning",
                                    details = "Blocked connection with invalid SSL Certificate (Error code: ${error?.primaryError})"
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error recording SSL event", e)
                        }
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    // CRITICAL STABILITY: Handle render process termination gracefully instead of crashing the entire Android application
                    Log.w(TAG, "Render process gone for tab ${tab.id}. Did crash: ${detail?.didCrash()}")
                    destroyTab(tab.id)
                    // Inform viewModel to recover active tab cleanly
                    scope.launch {
                        viewModel.onTabProcessCrashed(tab.id)
                    }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    viewModel.onProgressChanged(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        viewModel.onPageFinished(
                            view?.url ?: "",
                            title,
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false
                        )
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        viewModel.addConsoleLog(it.message(), it.messageLevel(), it.sourceId(), it.lineNumber())
                    }
                    return super.onConsoleMessage(consoleMessage)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    onFileChooser(filePathCallback, fileChooserParams)
                    return true
                }

                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                    callback?.invoke(origin, true, false)
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                    if (isUserGesture) {
                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                        val newWebView = WebView(context.applicationContext)
                        newWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                val newUrl = req?.url?.toString() ?: return false
                                viewModel.createNewTab(newUrl, tab.incognito)
                                return true
                            }
                        }
                        transport?.webView = newWebView
                        resultMsg?.sendToTarget()
                        return true
                    }
                    return false
                }
            }

            setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                viewModel.updateFindMatches(activeMatchOrdinal + 1, numberOfMatches)
            }

            setDownloadListener { url, _, contentDisposition, _, _ ->
                val filename = URLUtil.guessFileName(url, contentDisposition, null)
                viewModel.requestDownload(url, filename)
            }

            updateTabSettings(this, tab, viewModel.config.value)

            if (tab.url.isNotBlank() && tab.url != "about:home") {
                loadUrl(tab.url)
            }
        }

        webViewMap[tab.id] = webView
        return webView
    }

    fun updateTabSettings(webView: WebView, tab: BrowserTab, config: BrowserConfig) {
        val targetUa = if (tab.desktopMode) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        } else if (config.customUserAgent.isNotBlank()) {
            config.customUserAgent
        } else {
            null // Default mobile WebView UA
        }

        if (webView.settings.userAgentString != targetUa) {
            webView.settings.userAgentString = targetUa
        }

        if (webView.settings.javaScriptEnabled != config.javaScriptDefault) {
            webView.settings.javaScriptEnabled = config.javaScriptDefault
        }
    }

    fun loadUrl(tabId: Long, url: String) {
        if (url.isBlank() || url == "about:home") return
        webViewMap[tabId]?.loadUrl(url)
    }

    fun destroyTab(tabId: Long) {
        webViewMap.remove(tabId)?.let { webView ->
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying WebView for tab $tabId", e)
            }
        }
        lastAccessMap.remove(tabId)
    }

    fun destroyAll() {
        for (id in webViewMap.keys()) {
            destroyTab(id)
        }
    }

    fun pauseTab(tabId: Long) {
        webViewMap[tabId]?.onPause()
    }

    fun resumeTab(tabId: Long) {
        lastAccessMap[tabId] = System.currentTimeMillis()
        webViewMap[tabId]?.onResume()
    }

    private fun pruneExcessWebViews(currentTabId: Long, performanceMode: String) {
        val maxActive = when (performanceMode) {
            "Performance" -> 10
            "Battery Saver" -> 3
            else -> 6 // Balanced
        }

        if (webViewMap.size < maxActive) return

        // Find least recently used tab that is not current tab
        val candidate = lastAccessMap.entries
            .filter { it.key != currentTabId }
            .minByOrNull { it.value }

        candidate?.let {
            Log.d(TAG, "Suspending LRU WebView for tab ${it.key} due to limit ($maxActive)")
            destroyTab(it.key)
        }
    }
}
