package com.example.ui.browser.tabs

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebView
import com.example.data.db.BrowserTab

class TabSession(
    val tabId: Long,
    var url: String,
    var title: String,
    val incognito: Boolean = false,
    var desktopMode: Boolean = false
) {
    var webView: WebView? = null
    var loadingProgress: Int = 0
    var canGoBack: Boolean = false
    var canGoForward: Boolean = false
    var isSecure: Boolean = true
    var isCrashed: Boolean = false
    var hasError: Boolean = false
    var errorMessage: String? = null
    var favicon: Bitmap? = null
    var lastAccessed: Long = System.currentTimeMillis()
    var savedState: Bundle? = null

    fun saveWebViewState() {
        val wv = webView ?: return
        val bundle = Bundle()
        wv.saveState(bundle)
        savedState = bundle
    }

    fun restoreWebViewState() {
        val wv = webView ?: return
        val bundle = savedState ?: return
        wv.restoreState(bundle)
    }

    fun destroy() {
        val wv = webView ?: return
        try {
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.webChromeClient = null
            wv.clearHistory()
            wv.removeAllViews()
            wv.destroy()
        } catch (e: Exception) {
            // Ignore destruction cleanup errors
        } finally {
            webView = null
        }
    }
}
