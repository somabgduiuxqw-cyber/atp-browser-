package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

enum class ProxyState {
    DISABLED,
    CHECKING_SUPPORT,
    CONFIGURING,
    TESTING,
    CONNECTED,
    FAILED,
    UNSUPPORTED,
    REMOVING
}

data class ProxyTestResult(
    val success: Boolean,
    val latencyMs: Long = 0L,
    val message: String
)

data class ActiveProxyInfo(
    val endpoint: String = "",
    val bypassRules: List<String> = emptyList(),
    val source: String = "User" // "User", "Extension: <Name>", etc.
)

class ProxyManager(private val context: Context) {

    private val TAG = "ProxyManager"
    private val mainExecutor: Executor = context.mainExecutor

    private val _proxyState = MutableStateFlow(ProxyState.DISABLED)
    val proxyState: StateFlow<ProxyState> = _proxyState.asStateFlow()

    private val _activeProxy = MutableStateFlow<ActiveProxyInfo?>(null)
    val activeProxy: StateFlow<ActiveProxyInfo?> = _activeProxy.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun isProxyFeatureSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    }

    data class ValidationResult(val isValid: Boolean, val errorReason: String? = null)

    fun validateEndpoint(endpoint: String): ValidationResult {
        val trimmed = endpoint.trim()
        if (trimmed.isEmpty()) return ValidationResult(false, "Endpoint cannot be empty")

        // Parse scheme and host:port
        val uri = try {
            if (!trimmed.contains("://")) Uri.parse("http://$trimmed") else Uri.parse(trimmed)
        } catch (e: Exception) {
            return ValidationResult(false, "Malformed endpoint URI")
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https" && scheme != "socks" && scheme != "socks5") {
            return ValidationResult(false, "Unsupported proxy scheme: $scheme (use http, https, or socks)")
        }

        val host = uri.host
        if (host.isNullOrBlank()) return ValidationResult(false, "Hostname is missing")

        val port = uri.port
        if (port != -1 && (port < 1 || port > 65535)) {
            return ValidationResult(false, "Invalid port: $port (must be 1-65535)")
        }

        return ValidationResult(true)
    }

    suspend fun testConnection(endpoint: String): ProxyTestResult = withContext(Dispatchers.IO) {
        val validation = validateEndpoint(endpoint)
        if (!validation.isValid) {
            return@withContext ProxyTestResult(false, 0L, validation.errorReason ?: "Invalid endpoint")
        }

        val uri = if (!endpoint.contains("://")) Uri.parse("http://$endpoint") else Uri.parse(endpoint)
        val host = uri.host ?: return@withContext ProxyTestResult(false, 0L, "Invalid host")
        val port = if (uri.port != -1) uri.port else 8080

        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 6000) // 6 second timeout
            }
            val latency = System.currentTimeMillis() - start
            ProxyTestResult(true, latency, "Successfully connected to $host:$port in ${latency}ms")
        } catch (e: java.net.SocketTimeoutException) {
            ProxyTestResult(false, 0L, "Connection timeout to $host:$port")
        } catch (e: java.net.ConnectException) {
            ProxyTestResult(false, 0L, "Connection refused by $host:$port")
        } catch (e: java.net.UnknownHostException) {
            ProxyTestResult(false, 0L, "Unknown host: $host")
        } catch (e: Exception) {
            ProxyTestResult(false, 0L, "Connection failed: ${e.message ?: "Unknown socket error"}")
        }
    }

    fun applyProxy(endpoint: String, bypassRules: List<String>, source: String = "User", onComplete: (Boolean, String?) -> Unit) {
        if (!isProxyFeatureSupported()) {
            _proxyState.value = ProxyState.UNSUPPORTED
            _lastError.value = "Proxy is unsupported on this device's Android WebView version"
            onComplete(false, "Proxy feature unsupported on this WebView")
            return
        }

        val validation = validateEndpoint(endpoint)
        if (!validation.isValid) {
            _proxyState.value = ProxyState.FAILED
            val err = validation.errorReason ?: "Invalid proxy endpoint"
            _lastError.value = err
            onComplete(false, err)
            return
        }

        _proxyState.value = ProxyState.CONFIGURING
        try {
            val builder = ProxyConfig.Builder()
                .addProxyRule(endpoint.trim())
                .addDirect()

            for (rule in bypassRules) {
                if (rule.isNotBlank()) {
                    builder.addBypassRule(rule.trim())
                }
            }

            val proxyConfig = builder.build()
            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                mainExecutor,
                Runnable {
                    _proxyState.value = ProxyState.CONNECTED
                    _activeProxy.value = ActiveProxyInfo(endpoint.trim(), bypassRules, source)
                    _lastError.value = null
                    Log.i(TAG, "Proxy successfully configured: $endpoint (Source: $source)")
                    onComplete(true, null)
                }
            )
        } catch (e: Exception) {
            _proxyState.value = ProxyState.FAILED
            _lastError.value = "Failed to configure proxy: ${e.message}"
            Log.e(TAG, "Proxy configuration error", e)
            onComplete(false, e.message)
        }
    }

    fun clearProxy(onComplete: (() -> Unit)? = null) {
        if (!isProxyFeatureSupported()) {
            _proxyState.value = ProxyState.DISABLED
            _activeProxy.value = null
            onComplete?.invoke()
            return
        }

        _proxyState.value = ProxyState.REMOVING
        try {
            ProxyController.getInstance().clearProxyOverride(
                mainExecutor,
                Runnable {
                    _proxyState.value = ProxyState.DISABLED
                    _activeProxy.value = null
                    _lastError.value = null
                    Log.i(TAG, "Proxy cleared, restored direct network connection")
                    onComplete?.invoke()
                }
            )
        } catch (e: Exception) {
            _proxyState.value = ProxyState.DISABLED
            _activeProxy.value = null
            onComplete?.invoke()
        }
    }
}
