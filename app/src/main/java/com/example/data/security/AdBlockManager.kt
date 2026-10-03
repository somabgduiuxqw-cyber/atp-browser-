package com.example.data.security

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object AdBlockManager {

    // Known ad & tracking domain substrings and hosts
    private val KNOWN_AD_DOMAINS = hashSetOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "criteo.com",
        "outbrain.com",
        "taboola.com",
        "pubmatic.com",
        "rubiconproject.com",
        "amazon-adsystem.com",
        "adcolony.com",
        "unityads.unity3d.com",
        "vungle.com",
        "applvn.com",
        "popcash.net",
        "popads.net",
        "exoclick.com",
        "trafficjunky.com",
        "admob.com",
        "pagead2.googlesyndication.com"
    )

    private val KNOWN_TRACKER_DOMAINS = hashSetOf(
        "google-analytics.com",
        "analytics.google.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "scorecardresearch.com",
        "quantserve.com",
        "facebook.com/tr",
        "connect.facebook.net/en_US/fbevents.js",
        "ads-twitter.com",
        "statcounter.com",
        "clarity.ms",
        "newrelic.com",
        "sentry.io",
        "coinhive.com",
        "coin-hive.com",
        "cryptoloot.pro",
        "webminepool.com"
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
        }

        // Check Ad Blocking
        if (isGlobalAdBlockingEnabled) {
            for (ad in KNOWN_AD_DOMAINS) {
                if (host.contains(ad) || fullUrl.contains(ad)) {
                    recordBlock(pageDomain ?: host, isTracker = false)
                    return BlockDecision.Blocked("Known Ad Domain Blocked", ad)
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
