package com.example.network

import android.content.Context
import com.example.data.dns.DnsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkDiagnosticsInfo(
    val proxyState: ProxyState,
    val activeProxyEndpoint: String?,
    val proxyFeatureSupported: Boolean,
    val dnsMode: String,
    val dnsActiveServer: String?,
    val httpsOnlyMode: Boolean
)

/**
 * Centralized coordinator for browser networking subsystems.
 */
class NetworkManager(private val context: Context) {

    val proxyManager = ProxyManager(context)

    fun getDiagnostics(httpsOnly: Boolean): NetworkDiagnosticsInfo {
        return NetworkDiagnosticsInfo(
            proxyState = proxyManager.proxyState.value,
            activeProxyEndpoint = proxyManager.activeProxy.value?.endpoint,
            proxyFeatureSupported = proxyManager.isProxyFeatureSupported(),
            dnsMode = DnsManager.currentMode,
            dnsActiveServer = DnsManager.primaryServer.takeIf { it.isNotBlank() },
            httpsOnlyMode = httpsOnly
        )
    }
}
