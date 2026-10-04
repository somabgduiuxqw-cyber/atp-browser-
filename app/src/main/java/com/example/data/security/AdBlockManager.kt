package com.example.data.security

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object AdBlockManager {

    // Major ad serving networks, SSPs, DSPs, and banner providers
    private val KNOWN_AD_DOMAINS = hashSetOf(
        // Google & DoubleClick
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "securepubads.g.doubleclick.net",
        "googletagservices.com",
        "admob.com",
        "2mdn.net",

        // Amazon
        "amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "c.amazon-adsystem.com",

        // Content recommendation & native ads
        "taboola.com",
        "cdn.taboola.com",
        "outbrain.com",
        "widgets.outbrain.com",
        "zemanta.com",
        "mgid.com",
        "revcontent.com",
        "zergnet.com",

        // Retargeting & programmatic networks
        "criteo.com",
        "criteo.net",
        "static.criteo.net",
        "adnxs.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "smartadserver.com",
        "casalemedia.com",
        "indexexchange.com",
        "sovrn.com",
        "triplelift.com",
        "yieldmo.com",
        "sharethrough.com",
        "sonobi.com",
        "smartclip.net",
        "teads.tv",
        "gumgum.com",
        "infolinks.com",
        "media.net",
        "bidswitch.net",
        "bidvertiser.com",
        "adroll.com",
        "adpushup.com",
        "ezoic.com",
        "ezoic.net",
        "buysellads.com",
        "adblade.com",
        "skimresources.com",
        "viglink.com",
        "clicksor.com",
        "moatads.com",

        // Popups, popunders & high-risk networks
        "popcash.net",
        "popads.net",
        "exoclick.com",
        "trafficjunky.com",
        "propellerads.com",
        "adsterra.com",
        "hilltopads.com",

        // Mobile ad SDK networks
        "adcolony.com",
        "unityads.unity3d.com",
        "vungle.com",
        "applvn.com",
        "applovin.com",
        "ironsrc.com",
        "fyber.com",
        "tapjoy.com",
        "chartboost.com",
        "inmobi.com",

        // Yandex ads
        "an.yandex.ru",
        "direct.yandex.ru"
    )

    // Trackers, analytics, heatmaps, and telemetry
    private val KNOWN_TRACKER_DOMAINS = hashSetOf(
        // Google analytics & Tag Manager
        "google-analytics.com",
        "analytics.google.com",
        "googletagmanager.com",

        // Web telemetry & Session recording
        "hotjar.com",
        "static.hotjar.com",
        "script.hotjar.com",
        "clarity.ms",
        "mixpanel.com",
        "amplitude.com",
        "segment.io",
        "segment.com",
        "scorecardresearch.com",
        "quantserve.com",
        "statcounter.com",
        "chartbeat.com",
        "mouseflow.com",
        "crazyegg.com",
        "newrelic.com",
        "sentry.io",
        "bugsnag.com",
        "mc.yandex.ru",

        // Social conversion & tracking pixels
        "connect.facebook.net",
        "pixel.facebook.com",
        "ads-twitter.com",
        "static.ads-twitter.com",
        "analytics.tiktok.com",
        "snap.licdn.com",
        "bat.bing.com",

        // Cryptominers
        "coinhive.com",
        "coin-hive.com",
        "cryptoloot.pro",
        "webminepool.com"
    )

    // Script and URL path patterns tested by AdBlock Tester and typical ad scripts
    private val AD_PATH_PATTERNS = listOf(
        "/adsbygoogle.js",
        "/pagead/",
        "/tag/js/gpt.js",
        "/prebid.js",
        "/apstag.js",
        "/outbrain.js",
        "/ads.js",
        "/advert.js",
        "/adframe.js",
        "/advertising.js",
        "/banner.js",
        "/ad_banner",
        "/ad-banner",
        "banner_728x90",
        "banner_300x250",
        "banner_160x600",
        "banner_320x50",
        "banner_468x60"
    )

    private val TRACKER_PATH_PATTERNS = listOf(
        "/analytics.js",
        "/gtag/js",
        "/ga.js",
        "/fbevents.js",
        "/uwt.js",
        "/metrika/watch.js"
    )

    private val customBlockList = ConcurrentHashMap.newKeySet<String>()
    private val customAllowList = ConcurrentHashMap.newKeySet<String>()

    private val _totalBlockedCount = MutableStateFlow(0)
    val totalBlockedCount: StateFlow<Int> = _totalBlockedCount.asStateFlow()

    private val _totalTrackersBlockedCount = MutableStateFlow(0)
    val totalTrackersBlockedCount: StateFlow<Int> = _totalTrackersBlockedCount.asStateFlow()

    // Per-domain blocked counters
    private val siteBlockedCounters = ConcurrentHashMap<String, AtomicInteger>()

    var isGlobalAdBlockingEnabled = true
    var isGlobalTrackingProtectionEnabled = true
    var lastFilterListUpdate: String = "2026-10-03 04:00"

    fun getSiteBlockedCount(domain: String): Int {
        return siteBlockedCounters[domain]?.get() ?: 0
    }

    fun addCustomBlock(domain: String) {
        customBlockList.add(domain.lowercase(Locale.ROOT).trim())
    }

    fun removeCustomBlock(domain: String) {
        customBlockList.remove(domain.lowercase(Locale.ROOT).trim())
    }

    fun getCustomBlockList(): List<String> = customBlockList.toList().sorted()

    fun addCustomAllow(domain: String) {
        customAllowList.add(domain.lowercase(Locale.ROOT).trim())
    }

    fun removeCustomAllow(domain: String) {
        customAllowList.remove(domain.lowercase(Locale.ROOT).trim())
    }

    fun getCustomAllowList(): List<String> = customAllowList.toList().sorted()

    sealed class BlockDecision {
        object Allowed : BlockDecision()
        data class Blocked(val reason: String, val pattern: String) : BlockDecision()
    }

    fun shouldBlock(requestUrl: String, pageDomain: String? = null): BlockDecision {
        val uri = try { Uri.parse(requestUrl) } catch (e: Exception) { return BlockDecision.Allowed }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return BlockDecision.Allowed
        val path = uri.path?.lowercase(Locale.ROOT) ?: ""
        val fullUrl = requestUrl.lowercase(Locale.ROOT)

        // Check user custom allowlist first
        if (customAllowList.any { host.contains(it) || (pageDomain != null && pageDomain.contains(it)) }) {
            return BlockDecision.Allowed
        }

        // Check user custom blocklist
        for (blocked in customBlockList) {
            if (host.contains(blocked) || fullUrl.contains(blocked)) {
                recordBlock(pageDomain ?: host, isTracker = false)
                return BlockDecision.Blocked("User Custom Blocklist", blocked)
            }
        }

        // Check Tracker Blocking
        if (isGlobalTrackingProtectionEnabled) {
            for (tracker in KNOWN_TRACKER_DOMAINS) {
                if (host.contains(tracker) || fullUrl.contains(tracker)) {
                    recordBlock(pageDomain ?: host, isTracker = true)
                    return BlockDecision.Blocked("Known Tracker Blocked", tracker)
                }
            }

            for (pattern in TRACKER_PATH_PATTERNS) {
                if (path.contains(pattern) || fullUrl.contains(pattern)) {
                    recordBlock(pageDomain ?: host, isTracker = true)
                    return BlockDecision.Blocked("Tracker Script Blocked", pattern)
                }
            }
        }

        // Check Ad Blocking
        if (isGlobalAdBlockingEnabled) {
            for (ad in KNOWN_AD_DOMAINS) {
                if (host.contains(ad) || fullUrl.contains(ad)) {
                    recordBlock(pageDomain ?: host, isTracker = false)
                    return BlockDecision.Blocked("Known Ad Domain Blocked", ad)
                }
            }

            for (pattern in AD_PATH_PATTERNS) {
                if (path.contains(pattern) || fullUrl.contains(pattern)) {
                    recordBlock(pageDomain ?: host, isTracker = false)
                    return BlockDecision.Blocked("Ad Pattern Blocked", pattern)
                }
            }
        }

        return BlockDecision.Allowed
    }

    private fun recordBlock(domain: String, isTracker: Boolean) {
        _totalBlockedCount.value += 1
        if (isTracker) {
            _totalTrackersBlockedCount.value += 1
        }
        siteBlockedCounters.computeIfAbsent(domain) { AtomicInteger(0) }.incrementAndGet()
    }

    fun resetCounters() {
        _totalBlockedCount.value = 0
        _totalTrackersBlockedCount.value = 0
        siteBlockedCounters.clear()
    }
}
