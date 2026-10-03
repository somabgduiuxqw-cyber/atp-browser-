package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {

    // --- Tabs ---
    @Query("SELECT * FROM browser_tabs ORDER BY lastAccessed DESC")
    fun getAllTabs(): Flow<List<BrowserTab>>

    @Query("SELECT * FROM browser_tabs WHERE id = :id LIMIT 1")
    suspend fun getTabById(id: Long): BrowserTab?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: BrowserTab): Long

    @Update
    suspend fun updateTab(tab: BrowserTab)

    @Delete
    suspend fun deleteTab(tab: BrowserTab)

    @Query("DELETE FROM browser_tabs WHERE id = :id")
    suspend fun deleteTabById(id: Long)

    @Query("DELETE FROM browser_tabs WHERE incognito = :isIncognito")
    suspend fun deleteTabsByIncognito(isIncognito: Boolean)

    @Query("DELETE FROM browser_tabs WHERE id != :tabId AND incognito = :isIncognito")
    suspend fun deleteOtherTabs(tabId: Long, isIncognito: Boolean)

    @Query("DELETE FROM browser_tabs WHERE id > :tabId AND incognito = :isIncognito")
    suspend fun deleteTabsToRight(tabId: Long, isIncognito: Boolean)

    @Query("DELETE FROM browser_tabs")
    suspend fun clearAllTabs()

    // --- History ---
    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 50")
    fun searchHistory(query: String): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 10")
    suspend fun getHistorySuggestions(query: String): List<HistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntry): Long

    @Delete
    suspend fun deleteHistory(entry: HistoryEntry)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history_entries WHERE visitedAt >= :fromTimestamp")
    suspend fun deleteHistorySince(fromTimestamp: Long)

    @Query("DELETE FROM history_entries")
    suspend fun clearAllHistory()

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT 10")
    suspend fun getBookmarkSuggestions(query: String): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Update
    suspend fun updateBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAllBookmarks()

    // --- Site Settings ---
    @Query("SELECT * FROM site_settings WHERE domain = :domain LIMIT 1")
    suspend fun getSiteSetting(domain: String): SiteSetting?

    @Query("SELECT * FROM site_settings")
    fun getAllSiteSettings(): Flow<List<SiteSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiteSetting(setting: SiteSetting)

    @Query("DELETE FROM site_settings WHERE domain = :domain")
    suspend fun deleteSiteSetting(domain: String)

    @Query("DELETE FROM site_settings")
    suspend fun clearAllSiteSettings()

    // --- Background Sites ---
    @Query("SELECT * FROM background_sites ORDER BY isHighPriority DESC, lastActive DESC")
    fun getAllBackgroundSites(): Flow<List<BackgroundSite>>

    @Query("SELECT * FROM background_sites WHERE id = :id LIMIT 1")
    suspend fun getBackgroundSiteById(id: Long): BackgroundSite?

    @Query("SELECT * FROM background_sites WHERE url = :url LIMIT 1")
    suspend fun getBackgroundSiteByUrl(url: String): BackgroundSite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackgroundSite(site: BackgroundSite): Long

    @Update
    suspend fun updateBackgroundSite(site: BackgroundSite)

    @Delete
    suspend fun deleteBackgroundSite(site: BackgroundSite)

    @Query("DELETE FROM background_sites WHERE id = :id")
    suspend fun deleteBackgroundSiteById(id: Long)

    @Query("DELETE FROM background_sites WHERE url = :url")
    suspend fun deleteBackgroundSiteByUrl(url: String)

    // --- Downloads ---
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadItem): Long

    @Update
    suspend fun updateDownload(download: DownloadItem)

    @Delete
    suspend fun deleteDownload(download: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()

    // --- Security Events ---
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT 200")
    fun getAllSecurityEvents(): Flow<List<SecurityEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurityEvent(event: SecurityEvent): Long

    @Query("DELETE FROM security_events")
    suspend fun clearAllSecurityEvents()

    // --- DNS Profiles ---
    @Query("SELECT * FROM dns_profiles ORDER BY id ASC")
    fun getAllDnsProfiles(): Flow<List<DnsProfile>>

    @Query("SELECT * FROM dns_profiles WHERE enabled = 1 LIMIT 1")
    suspend fun getActiveDnsProfile(): DnsProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDnsProfile(profile: DnsProfile): Long

    @Update
    suspend fun updateDnsProfile(profile: DnsProfile)

    @Delete
    suspend fun deleteDnsProfile(profile: DnsProfile)

    @Query("UPDATE dns_profiles SET enabled = 0")
    suspend fun disableAllDnsProfiles()

    @Query("UPDATE dns_profiles SET enabled = 1 WHERE id = :id")
    suspend fun enableDnsProfile(id: Long)

    // --- Web Apps ---
    @Query("SELECT * FROM web_apps ORDER BY name ASC")
    fun getAllWebApps(): Flow<List<WebApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebApp(webApp: WebApp): Long

    @Delete
    suspend fun deleteWebApp(webApp: WebApp)
}
