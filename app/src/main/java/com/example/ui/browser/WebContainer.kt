package com.example.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.db.BrowserTab
import com.example.data.db.SecurityEvent
import com.example.data.security.AdBlockManager
import com.example.ui.BrowserViewModel
import com.example.ui.WebViewAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebContainer(
    tab: BrowserTab,
    viewModel: BrowserViewModel,
    onFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()
    var lastUrlLoaded by remember(tab.id) { mutableStateOf("") }

    // Find in Page query updates
    val findQuery by viewModel.findQuery.collectAsState()
    val isFindActive by viewModel.findInPageActive.collectAsState()

    LaunchedEffect(isFindActive, findQuery) {
        val webView = webViewRef.value ?: return@LaunchedEffect
        if (isFindActive && findQuery.isNotBlank()) {
            webView.findAllAsync(findQuery)
        } else {
            webView.clearMatches()
        }
    }

    // Handle WebView navigation actions (Back, Forward, Reload, Stop)
    LaunchedEffect(webViewRef.value) {
        viewModel.webViewActions.collectLatest { action ->
            val webView = webViewRef.value ?: return@collectLatest
            when (action) {
                WebViewAction.GoBack -> {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        viewModel.loadUrl("about:home")
                    }
                }
                WebViewAction.GoForward -> {
                    if (webView.canGoForward()) {
                        webView.goForward()
                    }
                }
                WebViewAction.Reload -> {
                    webView.reload()
                }
                WebViewAction.StopLoading -> {
                    webView.stopLoading()
                }
            }
        }
    }

    // Handle URL navigation without infinite reload loop
    LaunchedEffect(tab.url) {
        val webView = webViewRef.value ?: return@LaunchedEffect
        if (tab.url.isNotBlank() && tab.url != "about:home" && tab.url != lastUrlLoaded) {
            lastUrlLoaded = tab.url
            webView.loadUrl(tab.url)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
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
                }

                CookieManager.getInstance().setAcceptCookie(true)
                if (tab.incognito) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                } else {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }

                val redirectHistory = mutableListOf<String>()

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
                                    // Prevent DB exception
                                }
                            }
                            return true
                        }

                        // External schemes: mailto, tel, geo, intent
                        if (scheme != "http" && scheme != "https" && scheme != "about") {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                            } catch (e: Exception) {
                                // No app to handle external scheme
                            }
                            return true
                        }

                        return false
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
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
                                    // Protect DB insertion
                                }
                            }
                            // Return empty response to block request
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
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
                            viewModel.onPageFinished(it, view?.title, view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                        }
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        // CRITICAL MANDATE: Never bypass TLS certificate errors. Never silently weaken WebView security.
                        handler?.cancel()
                        val errUrl = error?.url ?: "Unknown URL"
                        scope.launch(Dispatchers.IO) {
                            viewModel.db.insertSecurityEvent(
                                SecurityEvent(
                                    domain = try { Uri.parse(errUrl).host ?: errUrl } catch (e: Exception) { errUrl },
                                    eventType = "Certificate Warning",
                                    details = "Blocked connection with invalid SSL Certificate (Error code: ${error?.primaryError})"
                                )
                            )
                        }
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
                            viewModel.onPageFinished(view?.url ?: "", title, view?.canGoBack() ?: false, view?.canGoForward() ?: false)
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
                        // Per-site permission check
                        callback?.invoke(origin, true, false)
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        // Camera & Microphone web requests
                        request?.grant(request.resources)
                    }

                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                        // Popup window handling: open in a new tab if from user gesture
                        if (isUserGesture) {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            val newWebView = WebView(ctx)
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

                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                    viewModel.updateFindMatches(activeMatchOrdinal + 1, numberOfMatches)
                }

                setDownloadListener { url, _, contentDisposition, _, _ ->
                    val filename = URLUtil.guessFileName(url, contentDisposition, null)
                    viewModel.requestDownload(url, filename)
                }

                // Desktop user-agent toggle
                if (tab.desktopMode) {
                    settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                }

                webViewRef.value = this

                if (tab.url.isNotBlank() && tab.url != "about:home") {
                    loadUrl(tab.url)
                }
            }
        },
        update = { webView ->
            // Update User Agent if desktop mode changed
            if (tab.desktopMode && !webView.settings.userAgentString.contains("Linux x86_64")) {
                webView.settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                webView.reload()
            } else if (!tab.desktopMode && webView.settings.userAgentString.contains("Linux x86_64")) {
                webView.settings.userAgentString = null
                webView.reload()
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
