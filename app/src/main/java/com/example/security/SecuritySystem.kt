package com.example.security

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class PermissionValue {
    ALLOW,
    ASK,
    BLOCK
}

data class DomainPermissions(
    val camera: PermissionValue = PermissionValue.ASK,
    val microphone: PermissionValue = PermissionValue.ASK,
    val location: PermissionValue = PermissionValue.ASK,
    val notifications: PermissionValue = PermissionValue.ASK,
    val popups: PermissionValue = PermissionValue.BLOCK,
    val javascript: PermissionValue = PermissionValue.ALLOW,
    val cookies: PermissionValue = PermissionValue.ALLOW,
    val autoplay: PermissionValue = PermissionValue.ALLOW
)

data class SecurityStatusReport(
    val isHttps: Boolean,
    val isCertificateValid: Boolean,
    val safeBrowsingEnabled: Boolean,
    val trackingMode: String,
    val adShieldEnabled: Boolean,
    val proxyActive: Boolean,
    val proxySource: String?,
    val overallLevel: String // "Secure", "Warning", "Dangerous"
)

object SitePermissionManager {
    private val domainPermissionsMap = ConcurrentHashMap<String, DomainPermissions>()

    fun getPermissions(domain: String): DomainPermissions {
        val clean = domain.lowercase(Locale.ROOT).trim()
        return domainPermissionsMap[clean] ?: DomainPermissions()
    }

    fun setPermission(domain: String, update: (DomainPermissions) -> DomainPermissions) {
        val clean = domain.lowercase(Locale.ROOT).trim()
        val current = getPermissions(clean)
        domainPermissionsMap[clean] = update(current)
    }

    fun getAllConfiguredDomains(): List<String> = domainPermissionsMap.keys().toList().sorted()

    fun resetDomain(domain: String) {
        domainPermissionsMap.remove(domain.lowercase(Locale.ROOT).trim())
    }
}

object SiteDataManager {
    fun clearDataForDomain(context: Context, domain: String, onComplete: () -> Unit) {
        try {
            // Remove cookies for domain
            val cookieManager = CookieManager.getInstance()
            val url = "https://$domain"
            val cookies = cookieManager.getCookie(url)
            if (cookies != null) {
                cookies.split(";").forEach { cookie ->
                    val name = cookie.substringBefore("=").trim()
                    cookieManager.setCookie(url, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                }
                cookieManager.flush()
            }

            // Remove web storage origins matching domain
            WebStorage.getInstance().getOrigins { origins ->
                origins?.forEach { (origin, _) ->
                    val originStr = origin?.toString() ?: ""
                    if (originStr.contains(domain, ignoreCase = true)) {
                        WebStorage.getInstance().deleteOrigin(originStr)
                    }
                }
                onComplete()
            }
        } catch (e: Exception) {
            onComplete()
        }
    }
}

object SafeBrowsingManager {
    var isSafeBrowsingSupported: Boolean = false
        private set

    fun initialize(context: Context) {
        isSafeBrowsingSupported = WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)
        if (isSafeBrowsingSupported) {
            WebViewCompat.startSafeBrowsing(context) { success ->
                // Safe browsing initialized
            }
        }
    }
}
