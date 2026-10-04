package com.example.ui.browser

import android.net.Uri
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.db.BrowserTab
import com.example.ui.BrowserViewModel
import com.example.ui.WebViewAction
import kotlinx.coroutines.flow.collectLatest

@Composable
fun WebContainer(
    tab: BrowserTab,
    viewModel: BrowserViewModel,
    onFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    modifier: Modifier = Modifier
) {
    val findQuery by viewModel.findQuery.collectAsState()
    val isFindActive by viewModel.findInPageActive.collectAsState()

    // Find in Page query updates
    LaunchedEffect(tab.id, isFindActive, findQuery) {
        val webView = TabWebViewManager.getWebView(tab.id) ?: return@LaunchedEffect
        if (isFindActive && findQuery.isNotBlank()) {
            webView.findAllAsync(findQuery)
        } else {
            webView.clearMatches()
        }
    }

    // Handle WebView navigation actions (Back, Forward, Reload, Stop)
    LaunchedEffect(tab.id) {
        viewModel.webViewActions.collectLatest { action ->
            val webView = TabWebViewManager.getWebView(tab.id) ?: return@collectLatest
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

    // Embed persistent WebView into Compose via FrameLayout container
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val webView = TabWebViewManager.getOrCreateWebView(ctx, tab, viewModel, onFileChooser)
                (webView.parent as? ViewGroup)?.removeView(webView)
                addView(webView)
            }
        },
        update = { frameLayout ->
            val webView = TabWebViewManager.getOrCreateWebView(frameLayout.context, tab, viewModel, onFileChooser)
            if (webView.parent != frameLayout) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                frameLayout.removeAllViews()
                frameLayout.addView(webView)
            }
            TabWebViewManager.updateTabSettings(webView, tab, viewModel.config.value)
        },
        onReset = { frameLayout ->
            frameLayout.removeAllViews()
        },
        modifier = modifier.fillMaxSize()
    )
}
