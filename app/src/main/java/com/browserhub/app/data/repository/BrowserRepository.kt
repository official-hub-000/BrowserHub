package com.browserhub.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.browserhub.app.data.local.BookmarkEntity
import com.browserhub.app.data.local.BrowserDao
import com.browserhub.app.data.local.ExtensionEntity
import com.browserhub.app.data.local.HistoryEntity
import com.browserhub.app.data.model.AddressBarPosition
import com.browserhub.app.data.model.BrowserSettings
import com.browserhub.app.data.model.ReaderFont
import com.browserhub.app.data.model.ReaderPaper
import com.browserhub.app.data.model.SearchEngine
import com.browserhub.app.data.model.ShieldsConfig
import com.browserhub.app.data.model.StudioTheme
import com.browserhub.app.data.model.UrlHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(name = "browser_hub_settings")

class BrowserRepository(
    private val context: Context,
    private val dao: BrowserDao
) {
    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val STUDIO_THEME = stringPreferencesKey("studio_theme")
        val ADDRESS_BAR_POS = stringPreferencesKey("address_bar_pos")
        val COMFORTABLE_UI = booleanPreferencesKey("comfortable_ui")
        val DESKTOP_DEVTOOLS = booleanPreferencesKey("desktop_devtools")
        val READER_PAPER = stringPreferencesKey("reader_paper")
        val READER_FONT = stringPreferencesKey("reader_font")
        val PAGE_TEXT_ZOOM = intPreferencesKey("page_text_zoom")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val BLOCK_POPUPS = booleanPreferencesKey("block_popups")
        val DEFAULT_DESKTOP_MODE = booleanPreferencesKey("default_desktop_mode")
        val SEEDED_QUICK_SITES_V2 = booleanPreferencesKey("seeded_quick_sites_v2")

        // Shields persistence
        val SHIELDS_ENABLED = booleanPreferencesKey("shields_enabled")
        val SHIELD_NATIVE_AD = booleanPreferencesKey("shield_native_ad")
        val SHIELD_ANALYTICS = booleanPreferencesKey("shield_analytics")
        val SHIELD_COOKIE_WALL = booleanPreferencesKey("shield_cookie_wall")
        val SHIELD_CANVAS_SPOOF = booleanPreferencesKey("shield_canvas_spoof")
        val SHIELD_PARAM_STRIP = booleanPreferencesKey("shield_param_strip")
        val SHIELD_HTTPS_ONLY = booleanPreferencesKey("shield_https_only")
        val SHIELD_POPUP_NOTIF = booleanPreferencesKey("shield_popup_notif")
        val SHIELD_AUTO_SKIP_ADS = booleanPreferencesKey("shield_auto_skip_ads")
        val SHIELD_NO_CAPTCHA = booleanPreferencesKey("shield_no_captcha")
        val SHIELD_AUTO_BYPASS_LINKS = booleanPreferencesKey("shield_auto_bypass_links")
    }

    val savedBookmarks: Flow<List<BookmarkEntity>> = dao.observeSavedBookmarks()
    val speedDialSites: Flow<List<BookmarkEntity>> = dao.observeSpeedDialSites()
    val history: Flow<List<HistoryEntity>> = dao.observeHistory()
    val extensions: Flow<List<ExtensionEntity>> = dao.observeExtensions()

    val settings: Flow<BrowserSettings> = context.browserDataStore.data.map { prefs ->
        val engine = prefs[Keys.SEARCH_ENGINE]?.let { name ->
            SearchEngine.entries.firstOrNull { it.name == name }
        } ?: SearchEngine.GOOGLE

        val theme = prefs[Keys.STUDIO_THEME]?.let { name ->
            StudioTheme.entries.firstOrNull { it.name == name }
        } ?: StudioTheme.AMOLED_BLACK

        val barPos = prefs[Keys.ADDRESS_BAR_POS]?.let { name ->
            AddressBarPosition.entries.firstOrNull { it.name == name }
        } ?: AddressBarPosition.TOP

        val paper = prefs[Keys.READER_PAPER]?.let { name ->
            ReaderPaper.entries.firstOrNull { it.name == name }
        } ?: ReaderPaper.SLATE

        val font = prefs[Keys.READER_FONT]?.let { name ->
            ReaderFont.entries.firstOrNull { it.name == name }
        } ?: ReaderFont.SANS

        BrowserSettings(
            searchEngine = engine,
            studioTheme = theme,
            addressBarPosition = barPos,
            comfortableUi = prefs[Keys.COMFORTABLE_UI] ?: true,
            desktopDevToolsEnabled = prefs[Keys.DESKTOP_DEVTOOLS] ?: false,
            readerPaper = paper,
            readerFont = font,
            pageTextZoom = (prefs[Keys.PAGE_TEXT_ZOOM] ?: 100).coerceIn(50, 200),
            javaScriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
            blockPopups = prefs[Keys.BLOCK_POPUPS] ?: true,
            defaultDesktopMode = prefs[Keys.DEFAULT_DESKTOP_MODE] ?: false
        )
    }

    val shieldsConfig: Flow<ShieldsConfig> = context.browserDataStore.data.map { prefs ->
        ShieldsConfig(
            shieldsEnabled = prefs[Keys.SHIELDS_ENABLED] ?: true,
            nativeAdBlocker = prefs[Keys.SHIELD_NATIVE_AD] ?: true,
            analyticsTrackerShield = prefs[Keys.SHIELD_ANALYTICS] ?: true,
            popupNotificationBlocker = prefs[Keys.SHIELD_POPUP_NOTIF] ?: true,
            autoSkipAds = prefs[Keys.SHIELD_AUTO_SKIP_ADS] ?: true,
            noCaptchaShield = prefs[Keys.SHIELD_NO_CAPTCHA] ?: true,
            autoBypassShortlinks = prefs[Keys.SHIELD_AUTO_BYPASS_LINKS] ?: true,
            cookieWallKiller = prefs[Keys.SHIELD_COOKIE_WALL] ?: true,
            canvasHardwareSpoofing = prefs[Keys.SHIELD_CANVAS_SPOOF] ?: true,
            trackingParamStripper = prefs[Keys.SHIELD_PARAM_STRIP] ?: true,
            httpsOnlyUpgrades = prefs[Keys.SHIELD_HTTPS_ONLY] ?: true
        )
    }

    fun observeIsBookmarked(url: String): Flow<Boolean> = dao.observeIsBookmarked(url)

    suspend fun ensureDefaultSpeedDialSeeded() {
        var alreadySeeded = false
        context.browserDataStore.edit { prefs ->
            alreadySeeded = prefs[Keys.SEEDED_QUICK_SITES_V2] ?: false
            if (!alreadySeeded) {
                prefs[Keys.SEEDED_QUICK_SITES_V2] = true
            }
        }
        if (!alreadySeeded) {
            val now = System.currentTimeMillis()
            val defaults = listOf(
                BookmarkEntity(title = "Google", url = "https://www.google.com", domain = "google.com", isSpeedDial = true, accentIndex = 0, createdAt = now),
                BookmarkEntity(title = "DuckDuckGo", url = "https://duckduckgo.com", domain = "duckduckgo.com", isSpeedDial = true, accentIndex = 2, createdAt = now + 1),
                BookmarkEntity(title = "YouTube", url = "https://m.youtube.com", domain = "youtube.com", isSpeedDial = true, accentIndex = 4, createdAt = now + 2),
                BookmarkEntity(title = "Wikipedia", url = "https://en.wikipedia.org", domain = "en.wikipedia.org", isSpeedDial = true, accentIndex = 1, createdAt = now + 3),
                BookmarkEntity(title = "GitHub", url = "https://github.com", domain = "github.com", isSpeedDial = true, accentIndex = 0, createdAt = now + 4),
                BookmarkEntity(title = "Reddit", url = "https://www.reddit.com", domain = "reddit.com", isSpeedDial = true, accentIndex = 2, createdAt = now + 5),
                BookmarkEntity(title = "Hacker News", url = "https://news.ycombinator.com", domain = "news.ycombinator.com", isSpeedDial = true, accentIndex = 5, createdAt = now + 6),
                BookmarkEntity(title = "BBC News", url = "https://www.bbc.com/news", domain = "bbc.com", isSpeedDial = true, accentIndex = 4, createdAt = now + 7)
            )
            if (dao.countSpeedDialSites() == 0) {
                dao.insertBookmarks(defaults)
            }
        }
    }

    suspend fun toggleBookmark(title: String, url: String): Boolean {
        val existing = dao.getSavedBookmarkByUrl(url)
        return if (existing != null) {
            dao.deleteSavedBookmarkByUrl(url)
            false
        } else {
            val host = UrlHelper.extractHost(url)
            val safeTitle = title.ifBlank { host.ifBlank { url } }
            dao.insertBookmark(
                BookmarkEntity(
                    title = safeTitle,
                    url = url,
                    domain = host,
                    isSpeedDial = false,
                    accentIndex = abs(host.hashCode()) % 6
                )
            )
            true
        }
    }

    suspend fun addSpeedDialSite(title: String, rawUrl: String) {
        val url = UrlHelper.normalizeShortcutUrl(rawUrl)
        if (url.isBlank()) return
        val host = UrlHelper.extractHost(url)
        val safeTitle = title.trim().ifBlank { host.ifBlank { url } }
        dao.insertBookmark(
            BookmarkEntity(
                title = safeTitle,
                url = url,
                domain = host,
                isSpeedDial = true,
                accentIndex = abs(host.hashCode()) % 6
            )
        )
    }

    suspend fun deleteBookmark(id: Long) {
        dao.deleteBookmarkById(id)
    }

    suspend fun recordHistory(title: String, url: String) {
        if (url.isBlank() || url == "about:blank" || url.startsWith("browserhub://")) return
        val host = UrlHelper.extractHost(url)
        val safeTitle = title.ifBlank { host.ifBlank { url } }
        dao.insertHistory(
            HistoryEntity(
                title = safeTitle,
                url = url,
                domain = host
            )
        )
    }

    suspend fun deleteHistoryItem(id: Long) {
        dao.deleteHistoryById(id)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun setExtensionEnabled(id: String, enabled: Boolean) {
        dao.setExtensionEnabled(id, enabled)
    }

    suspend fun setExtensionAllowInIncognito(id: String, allowed: Boolean) {
        dao.setExtensionAllowInIncognito(id, allowed)
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.browserDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setStudioTheme(theme: StudioTheme) {
        context.browserDataStore.edit { it[Keys.STUDIO_THEME] = theme.name }
    }

    suspend fun setAddressBarPosition(position: AddressBarPosition) {
        context.browserDataStore.edit { it[Keys.ADDRESS_BAR_POS] = position.name }
    }

    suspend fun setComfortableUi(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.COMFORTABLE_UI] = enabled }
    }

    suspend fun setDesktopDevToolsEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.DESKTOP_DEVTOOLS] = enabled }
    }

    suspend fun setReaderPaper(paper: ReaderPaper) {
        context.browserDataStore.edit { it[Keys.READER_PAPER] = paper.name }
    }

    suspend fun setReaderFont(font: ReaderFont) {
        context.browserDataStore.edit { it[Keys.READER_FONT] = font.name }
    }

    suspend fun setPageTextZoom(zoom: Int) {
        context.browserDataStore.edit { it[Keys.PAGE_TEXT_ZOOM] = zoom.coerceIn(50, 200) }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.JAVASCRIPT_ENABLED] = enabled }
    }

    suspend fun setBlockPopups(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.BLOCK_POPUPS] = enabled }
    }

    suspend fun setDefaultDesktopMode(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.DEFAULT_DESKTOP_MODE] = enabled }
    }

    suspend fun updateShields(transform: (ShieldsConfig) -> ShieldsConfig) {
        context.browserDataStore.edit { prefs ->
            val current = ShieldsConfig(
                shieldsEnabled = prefs[Keys.SHIELDS_ENABLED] ?: true,
                nativeAdBlocker = prefs[Keys.SHIELD_NATIVE_AD] ?: true,
                analyticsTrackerShield = prefs[Keys.SHIELD_ANALYTICS] ?: true,
                popupNotificationBlocker = prefs[Keys.SHIELD_POPUP_NOTIF] ?: true,
                autoSkipAds = prefs[Keys.SHIELD_AUTO_SKIP_ADS] ?: true,
                noCaptchaShield = prefs[Keys.SHIELD_NO_CAPTCHA] ?: true,
                autoBypassShortlinks = prefs[Keys.SHIELD_AUTO_BYPASS_LINKS] ?: true,
                cookieWallKiller = prefs[Keys.SHIELD_COOKIE_WALL] ?: true,
                canvasHardwareSpoofing = prefs[Keys.SHIELD_CANVAS_SPOOF] ?: true,
                trackingParamStripper = prefs[Keys.SHIELD_PARAM_STRIP] ?: true,
                httpsOnlyUpgrades = prefs[Keys.SHIELD_HTTPS_ONLY] ?: true
            )
            val next = transform(current)
            prefs[Keys.SHIELDS_ENABLED] = next.shieldsEnabled
            prefs[Keys.SHIELD_NATIVE_AD] = next.nativeAdBlocker
            prefs[Keys.SHIELD_ANALYTICS] = next.analyticsTrackerShield
            prefs[Keys.SHIELD_POPUP_NOTIF] = next.popupNotificationBlocker
            prefs[Keys.SHIELD_AUTO_SKIP_ADS] = next.autoSkipAds
            prefs[Keys.SHIELD_NO_CAPTCHA] = next.noCaptchaShield
            prefs[Keys.SHIELD_AUTO_BYPASS_LINKS] = next.autoBypassShortlinks
            prefs[Keys.SHIELD_COOKIE_WALL] = next.cookieWallKiller
            prefs[Keys.SHIELD_CANVAS_SPOOF] = next.canvasHardwareSpoofing
            prefs[Keys.SHIELD_PARAM_STRIP] = next.trackingParamStripper
            prefs[Keys.SHIELD_HTTPS_ONLY] = next.httpsOnlyUpgrades
        }
    }
}
