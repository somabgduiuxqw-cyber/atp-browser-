package com.example.protection

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class TrackingProtectionMode {
    STANDARD,
    STRICT,
    OFF
}

data class BlockedRequestEntry(
    val domain: String,
    val url: String,
    val resourceType: String, // Script, Image, Ad Network, Tracker, etc.
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SiteExceptionManager {
    private val disabledShieldDomains = ConcurrentHashMap.newKeySet<String>()

    fun isShieldDisabled(domain: String): Boolean {
        val clean = domain.lowercase(Locale.ROOT).trim()
        return disabledShieldDomains.any { clean == it || clean.endsWith(".$it") }
    }

    fun disableShieldForSite(domain: String) {
        disabledShieldDomains.add(domain.lowercase(Locale.ROOT).trim())
    }

    fun enableShieldForSite(domain: String) {
        disabledShieldDomains.remove(domain.lowercase(Locale.ROOT).trim())
    }

    fun getAllExceptions(): List<String> = disabledShieldDomains.toList().sorted()

    fun clearExceptions() {
        disabledShieldDomains.clear()
    }
}

object BlockStatsManager {
    private val _totalAdsBlocked = MutableStateFlow(0)
    val totalAdsBlocked: StateFlow<Int> = _totalAdsBlocked.asStateFlow()

    private val _totalTrackersBlocked = MutableStateFlow(0)
    val totalTrackersBlocked: StateFlow<Int> = _totalTrackersBlocked.asStateFlow()

    private val _totalRequestsBlocked = MutableStateFlow(0)
    val totalRequestsBlocked: StateFlow<Int> = _totalRequestsBlocked.asStateFlow()

    // Per-site counter
    private val perSiteStats = ConcurrentHashMap<String, AtomicInteger>()

    // Blocked requests log
    private val _blockedLog = MutableStateFlow<List<BlockedRequestEntry>>(emptyList())
    val blockedLog: StateFlow<List<BlockedRequestEntry>> = _blockedLog.asStateFlow()

    fun recordBlock(domain: String, url: String, resourceType: String, reason: String, isTracker: Boolean) {
        _totalRequestsBlocked.value += 1
        if (isTracker) {
            _totalTrackersBlocked.value += 1
        } else {
            _totalAdsBlocked.value += 1
        }

        perSiteStats.computeIfAbsent(domain.lowercase(Locale.ROOT)) { AtomicInteger(0) }.incrementAndGet()

        val entry = BlockedRequestEntry(domain, url, resourceType, reason)
        _blockedLog.value = (_blockedLog.value + entry).takeLast(250)
    }

    fun getSiteBlockedCount(domain: String): Int {
        return perSiteStats[domain.lowercase(Locale.ROOT)]?.get() ?: 0
    }

    fun resetStats() {
        _totalAdsBlocked.value = 0
        _totalTrackersBlocked.value = 0
        _totalRequestsBlocked.value = 0
        perSiteStats.clear()
        _blockedLog.value = emptyList()
    }
}

/**
 * Filter list manager with custom allow/block rules.
 */
object FilterListManager {
    val customBlockList = ConcurrentHashMap.newKeySet<String>()
    val customAllowList = ConcurrentHashMap.newKeySet<String>()

    fun addCustomBlock(rule: String) {
        customBlockList.add(rule.lowercase(Locale.ROOT).trim())
    }

    fun removeCustomBlock(rule: String) {
        customBlockList.remove(rule.lowercase(Locale.ROOT).trim())
    }

    fun addCustomAllow(rule: String) {
        customAllowList.add(rule.lowercase(Locale.ROOT).trim())
    }

    fun removeCustomAllow(rule: String) {
        customAllowList.remove(rule.lowercase(Locale.ROOT).trim())
    }
}

/**
 * Real request evaluation engine for Ads & Trackers.
 */
object AdBlockEngine {

    val TRANSPARENT_1X1_PNG: ByteArray by lazy {
        android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
            android.util.Base64.DEFAULT
        )
    }

    private val KNOWN_AD_DOMAINS = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "pagead2.googlesyndication.com", "securepubads.g.doubleclick.net", "googletagservices.com",
        "admob.com", "2mdn.net", "amazon-adsystem.com", "aax.amazon-adsystem.com", "c.amazon-adsystem.com",
        "taboola.com", "cdn.taboola.com", "outbrain.com", "widgets.outbrain.com", "zemanta.com",
        "mgid.com", "revcontent.com", "zergnet.com", "criteo.com", "criteo.net", "static.criteo.net",
        "adnxs.com", "pubmatic.com", "rubiconproject.com", "openx.net", "smartadserver.com",
        "casalemedia.com", "indexexchange.com", "sovrn.com", "triplelift.com", "yieldmo.com",
        "sharethrough.com", "sonobi.com", "smartclip.net", "teads.tv", "gumgum.com", "infolinks.com",
        "media.net", "bidswitch.net", "bidvertiser.com", "adroll.com", "adpushup.com", "ezoic.com",
        "ezoic.net", "buysellads.com", "adblade.com", "skimresources.com", "viglink.com", "clicksor.com",
        "moatads.com", "popcash.net", "popads.net", "exoclick.com", "trafficjunky.com", "propellerads.com",
        "adsterra.com", "hilltopads.com", "adcolony.com", "unityads.unity3d.com", "vungle.com",
        "applvn.com", "applovin.com", "ironsrc.com", "fyber.com", "tapjoy.com", "chartboost.com",
        "inmobi.com", "an.yandex.ru", "direct.yandex.ru"
    )

    private val AD_PATH_PATTERNS = listOf(
        "/adsbygoogle.js", "/pagead/", "/tag/js/gpt.js", "/prebid.js", "/apstag.js",
        "/outbrain.js", "/ads.js", "/advert.js", "/adframe.js", "/advertising.js",
        "/banner.js", "/ad_banner", "/ad-banner", "banner_728x90", "banner_300x250",
        "banner_160x600", "banner_320x50", "banner_468x60"
    )

    sealed class FilterDecision {
        object Allow : FilterDecision()
        data class Block(val reason: String, val resourceType: String, val isTracker: Boolean) : FilterDecision()
    }

    fun evaluate(
        requestUrl: String,
        pageHost: String?,
        isMainFrame: Boolean,
        adBlockingEnabled: Boolean,
        trackingMode: TrackingProtectionMode
    ): FilterDecision {
        // Never block main frame request
        if (isMainFrame) return FilterDecision.Allow

        // Check if shield disabled for site
        if (pageHost != null && SiteExceptionManager.isShieldDisabled(pageHost)) {
            return FilterDecision.Allow
        }

        val uri = try { Uri.parse(requestUrl) } catch (e: Exception) { return FilterDecision.Allow }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return FilterDecision.Allow
        val path = uri.path?.lowercase(Locale.ROOT) ?: ""
        val full = requestUrl.lowercase(Locale.ROOT)

        // Custom allowlist check
        if (FilterListManager.customAllowList.any { host.contains(it) || (pageHost != null && pageHost.contains(it)) }) {
            return FilterDecision.Allow
        }

        // Custom blocklist check
        for (rule in FilterListManager.customBlockList) {
            if (host.contains(rule) || full.contains(rule)) {
                return FilterDecision.Block("User Custom Rule ($rule)", detectType(path), false)
            }
        }

        // Tracker evaluation
        if (trackingMode != TrackingProtectionMode.OFF) {
            val trackerDecision = TrackerBlockEngine.evaluate(host, path, full, trackingMode)
            if (trackerDecision is FilterDecision.Block) {
                return trackerDecision
            }
        }

        // Ad evaluation
        if (adBlockingEnabled) {
            for (adHost in KNOWN_AD_DOMAINS) {
                if (host.contains(adHost) || full.contains(adHost)) {
                    return FilterDecision.Block("Known Ad Network ($adHost)", detectType(path), false)
                }
            }
            for (adPattern in AD_PATH_PATTERNS) {
                if (path.contains(adPattern) || full.contains(adPattern)) {
                    return FilterDecision.Block("Ad Pattern ($adPattern)", detectType(path), false)
                }
            }
        }

        return FilterDecision.Allow
    }

    private fun detectType(path: String): String {
        return when {
            path.endsWith(".js") -> "Script"
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".gif") || path.endsWith(".webp") -> "Image"
            path.endsWith(".css") -> "Stylesheet"
            else -> "Network Request"
        }
    }
}

object TrackerBlockEngine {

    private val KNOWN_TRACKER_DOMAINS = hashSetOf(
        "google-analytics.com", "analytics.google.com", "googletagmanager.com",
        "hotjar.com", "static.hotjar.com", "script.hotjar.com", "clarity.ms",
        "mixpanel.com", "amplitude.com", "segment.io", "segment.com",
        "scorecardresearch.com", "quantserve.com", "statcounter.com", "chartbeat.com",
        "mouseflow.com", "crazyegg.com", "newrelic.com", "sentry.io", "bugsnag.com",
        "mc.yandex.ru", "connect.facebook.net", "pixel.facebook.com", "ads-twitter.com",
        "static.ads-twitter.com", "analytics.tiktok.com", "snap.licdn.com", "bat.bing.com",
        "coinhive.com", "coin-hive.com", "cryptoloot.pro", "webminepool.com"
    )

    private val STRICT_ADDITIONAL_DOMAINS = hashSetOf(
        "branch.io", "appsflyer.com", "adjust.com", "singular.net", "kochava.com"
    )

    private val TRACKER_PATH_PATTERNS = listOf(
        "/analytics.js", "/gtag/js", "/ga.js", "/fbevents.js", "/uwt.js", "/metrika/watch.js"
    )

    fun evaluate(host: String, path: String, fullUrl: String, mode: TrackingProtectionMode): AdBlockEngine.FilterDecision {
        for (tracker in KNOWN_TRACKER_DOMAINS) {
            if (host.contains(tracker) || fullUrl.contains(tracker)) {
                return AdBlockEngine.FilterDecision.Block("Known Tracker ($tracker)", "Tracker Script", true)
            }
        }

        for (pattern in TRACKER_PATH_PATTERNS) {
            if (path.contains(pattern) || fullUrl.contains(pattern)) {
                return AdBlockEngine.FilterDecision.Block("Tracker Pattern ($pattern)", "Tracker Script", true)
            }
        }

        if (mode == TrackingProtectionMode.STRICT) {
            for (tracker in STRICT_ADDITIONAL_DOMAINS) {
                if (host.contains(tracker) || fullUrl.contains(tracker)) {
                    return AdBlockEngine.FilterDecision.Block("Strict Telemetry ($tracker)", "Telemetry", true)
                }
            }
        }

        return AdBlockEngine.FilterDecision.Allow
    }
}
