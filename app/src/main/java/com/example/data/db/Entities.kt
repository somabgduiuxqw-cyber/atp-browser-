package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browser_tabs")
data class BrowserTab(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val favicon: String? = null,
    val incognito: Boolean = false,
    val background: Boolean = false,
    val keepAliveMode: String = "None", // None, Standard, Audio, Aggressive, RestoreOnly
    val lastAccessed: Long = System.currentTimeMillis(),
    val isSuspended: Boolean = false,
    val desktopMode: Boolean = false
)

@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folderId: Long = 0, // 0 for root
    val favicon: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_settings")
data class SiteSetting(
    @PrimaryKey val domain: String,
    val javaScript: Boolean = true,
    val desktopMode: Boolean = false,
    val autoplay: String = "BLOCK", // ALLOW, INTERACTION, BLOCK
    val popupPolicy: String = "BLOCK", // ALLOW, BLOCK, ASK
    val adBlocking: Boolean = true,
    val trackingProtection: Boolean = true,
    val keepAlive: Boolean = false,
    val dnsMode: String = "Global", // Global, System
    val cameraPermission: String = "Ask", // Ask, Allow, Block
    val micPermission: String = "Ask",
    val locationPermission: String = "Ask",
    val notificationPermission: String = "Ask"
)

@Entity(tableName = "background_sites")
data class BackgroundSite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val mode: String = "Standard", // Standard, Audio, Aggressive, RestoreOnly
    val status: String = "Running", // Running, Suspended, Restoring, Stopped, Error
    val autoStart: Boolean = true,
    val isHighPriority: Boolean = false,
    val lastActive: Long = System.currentTimeMillis(),
    val audioState: String = "Idle" // Idle, Playing, Paused
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val filename: String,
    val temporaryPath: String,
    val finalPath: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: String = "Pending", // Pending, Downloading, Paused, Completed, Failed, Cancelled
    val etag: String? = null,
    val lastModified: String? = null,
    val speed: String = "0 KB/s",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "security_events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val eventType: String, // Blocked Tracker, Blocked Ad, Suspicious Navigation, Malware Warning, Download Warning, Certificate Warning
    val timestamp: Long = System.currentTimeMillis(),
    val details: String
)

@Entity(tableName = "dns_profiles")
data class DnsProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryServer: String,
    val secondaryServer: String = "",
    val protocol: String = "Plain", // Plain, DoH, DoT, System
    val enabled: Boolean = false
)

@Entity(tableName = "web_apps")
data class WebApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val icon: String? = null,
    val displayMode: String = "Standalone" // Browser, Standalone, Fullscreen
)
