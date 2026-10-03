package com.example.data.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class DnsTestResult(
    val server: String,
    val protocol: String,
    val isReachable: Boolean,
    val responseTimeMs: Long,
    val resolvedIp: String?,
    val error: String? = null
)

object DnsManager {
    // Current active mode: "System" or "Custom"
    var currentMode: String = "System"
    var activeProfileName: String? = null
    var primaryServer: String = ""
    var secondaryServer: String = ""
    var protocol: String = "Plain" // System, Plain, DoH, DoT

    fun isCustomDnsEnabled(): Boolean = currentMode == "Custom" && primaryServer.isNotBlank()

    suspend fun testDnsServer(server: String, protocol: String): DnsTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            when (protocol) {
                "DoH" -> {
                    // Test DNS-over-HTTPS endpoint by querying a standard hostname (e.g. atp-check or example.com)
                    val url = if (server.startsWith("http")) server else "https://$server/dns-query?name=example.com&type=A"
                    val client = OkHttpClient.Builder()
                        .connectTimeout(4, TimeUnit.SECONDS)
                        .readTimeout(4, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "application/dns-json")
                        .build()
                    val response = client.newCall(request).execute()
                    val duration = System.currentTimeMillis() - startTime
                    if (response.isSuccessful) {
                        DnsTestResult(
                            server = server,
                            protocol = protocol,
                            isReachable = true,
                            responseTimeMs = duration,
                            resolvedIp = "DoH Response HTTP ${response.code}"
                        )
                    } else {
                        DnsTestResult(
                            server = server,
                            protocol = protocol,
                            isReachable = false,
                            responseTimeMs = duration,
                            resolvedIp = null,
                            error = "HTTP error code: ${response.code}"
                        )
                    }
                }
                "DoT" -> {
                    // Test port 853 for DoT reachability
                    val socket = Socket()
                    socket.connect(InetSocketAddress(server, 853), 4000)
                    socket.close()
                    val duration = System.currentTimeMillis() - startTime
                    DnsTestResult(
                        server = server,
                        protocol = protocol,
                        isReachable = true,
                        responseTimeMs = duration,
                        resolvedIp = "Port 853 reachable"
                    )
                }
                else -> {
                    // Plain DNS: Port 53 reachability
                    val socket = Socket()
                    socket.connect(InetSocketAddress(server, 53), 4000)
                    socket.close()
                    val duration = System.currentTimeMillis() - startTime
                    DnsTestResult(
                        server = server,
                        protocol = protocol,
                        isReachable = true,
                        responseTimeMs = duration,
                        resolvedIp = "Port 53 reachable"
                    )
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            // Fallback ping test if direct socket fails
            try {
                val addr = InetAddress.getByName(server)
                val pingSuccess = addr.isReachable(3000)
                DnsTestResult(
                    server = server,
                    protocol = protocol,
                    isReachable = pingSuccess,
                    responseTimeMs = duration,
                    resolvedIp = addr.hostAddress,
                    error = if (!pingSuccess) e.message ?: "Server unreachable" else null
                )
            } catch (ex: Exception) {
                DnsTestResult(
                    server = server,
                    protocol = protocol,
                    isReachable = false,
                    responseTimeMs = duration,
                    resolvedIp = null,
                    error = e.message ?: "Connection timed out"
                )
            }
        }
    }
}
