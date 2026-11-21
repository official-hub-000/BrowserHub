package com.browserhub.app.data.model

import androidx.compose.ui.graphics.Color
import java.net.URI
import java.util.UUID

enum class SearchEngine(
    val displayName: String,
    val shortName: String,
    val searchUrlPrefix: String,
    val homeUrl: String,
    val badgeLetter: String,
    val brandColorHex: Long
) {
    GOOGLE(
        displayName = "Google",
        shortName = "Google",
        searchUrlPrefix = "https://www.google.com/search?q=",
        homeUrl = "https://www.google.com",
        badgeLetter = "G",
        brandColorHex = 0xFF4285F4
    ),
    DUCKDUCKGO(
        displayName = "DuckDuckGo",
        shortName = "DuckDuckGo",
        searchUrlPrefix = "https://duckduckgo.com/?q=",
        homeUrl = "https://duckduckgo.com",
        badgeLetter = "D",
        brandColorHex = 0xFFDE5833
    ),
    BRAVE(
        displayName = "Brave Search",
        shortName = "Brave",
        searchUrlPrefix = "https://search.brave.com/search?q=",
        homeUrl = "https://search.brave.com",
        badgeLetter = "B",
        brandColorHex = 0xFFFB542B
    ),
    BING(
        displayName = "Microsoft Bing",
        shortName = "Bing",
        searchUrlPrefix = "https://www.bing.com/search?q=",
        homeUrl = "https://www.bing.com",
        badgeLetter = "Bi",
        brandColorHex = 0xFF008373
    ),
    STARTPAGE(
        displayName = "Startpage (Anonymous Google)",
        shortName = "Startpage",
        searchUrlPrefix = "https://www.startpage.com/do/dsearch?query=",
        homeUrl = "https://www.startpage.com",
        badgeLetter = "S",
        brandColorHex = 0xFF6573FF
    ),
    WIKIPEDIA(
        displayName = "Wikipedia",
        shortName = "Wiki",
        searchUrlPrefix = "https://en.wikipedia.org/wiki/Special:Search?search=",
        homeUrl = "https://en.wikipedia.org",
        badgeLetter = "W",
        brandColorHex = 0xFF64748B
    ),
    GITHUB(
        displayName = "GitHub Repositories",
        shortName = "GitHub",
        searchUrlPrefix = "https://github.com/search?q=",
        homeUrl = "https://github.com",
        badgeLetter = "Gh",
        brandColorHex = 0xFF334155
    );

    val brandColor: Color
        get() = Color(brandColorHex)
}

enum class StudioTheme(
    val title: String,
    val badge: String,
    val dotColorHex: Long
) {
    AMOLED_BLACK("AMOLED Pitch Black (0% OLED Power)", "OLED Battery Saver", 0xFF38BDF8),
    CYBER_CYAN("BrowserHub Cyber Cyan", "Default Flagship", 0xFF00D4FF),
    MULLVAD_EMERALD("Mullvad Emerald Privacy", "Stealth Ops", 0xFF10B981),
    ARC_VIOLET("Arc Nebula Violet", "AI Studio", 0xFFA855F7),
    NORDIC_FROST("Nordic Arctic Frost", "Eye Comfort", 0xFF93C5FD),
    VIVALDI_CRIMSON("Vivaldi Power Crimson", "Power Pro", 0xFFF43F5E),
    WARM_DUSK("Warm Dusk Amber", "Night Filter", 0xFFF59E0B);

    val dotColor: Color
        get() = Color(dotColorHex)
}

enum class AddressBarPosition(val label: String) {
    TOP("TOP"),
    BOTTOM("BOTTOM")
}

enum class ReaderPaper(val label: String, val bgHex: String, val fgHex: String, val mutedHex: String) {
    AMOLED("Amoled", "#000000", "#E2E8F0", "#94A3B8"),
    SEPIA("Sepia", "#F4ECD8", "#3B2F23", "#6B5A45"),
    SLATE("Slate", "#0F172A", "#E2E8F0", "#94A3B8")
}

enum class ReaderFont(val label: String, val cssFamily: String) {
    SANS("Sans", "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"),
    SERIF("Serif", "Georgia, 'Times New Roman', serif"),
    MONO("Mono", "'JetBrains Mono', ui-monospace, SFMono-Regular, monospace")
}

enum class TabWorkspace(val label: String) {
    ALL("All"),
    MAIN("Main"),
    WORK("Work"),
    RESEARCH("Research"),
    MEDIA("Media")
}

data class ShieldsConfig(
    val shieldsEnabled: Boolean = true,
    val nativeAdBlocker: Boolean = true,
    val analyticsTrackerShield: Boolean = true,
    val popupNotificationBlocker: Boolean = true,
    val autoSkipAds: Boolean = true,
    val noCaptchaShield: Boolean = true,
    val autoBypassShortlinks: Boolean = true,
    val cookieWallKiller: Boolean = true,
    val canvasHardwareSpoofing: Boolean = true,
    val trackingParamStripper: Boolean = true,
    val httpsOnlyUpgrades: Boolean = true,
    val elementZapperActive: Boolean = false
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val errorMessage: String? = null,
    val isOnHub: Boolean = true,
    val workspace: TabWorkspace = TabWorkspace.MAIN,
    val isPinned: Boolean = false,
    val isSleeping: Boolean = false,
    val estimatedRamMb: Int = 28,
    val adsBlocked: Int = 0,
    val trackersStopped: Int = 0,
    val kbStripped: Int = 0,
    val isReaderMode: Boolean = false,
    val lastActiveAtMs: Long = System.currentTimeMillis()
)

data class PageSuperpowerState(
    val forceDarkWeb: Boolean = false,
    val autoScrollSpeed: Int = 0,
    val videoSpeed: Float = 1.0f,
    val copyUnlockEnabled: Boolean = false,
    val nightTintEnabled: Boolean = false
)

data class PageXRayReport(
    val url: String = "",
    val title: String = "",
    val wordCount: Int = 0,
    val readingMinutes: Int = 1,
    val linkCount: Int = 0,
    val imageCount: Int = 0,
    val scriptCount: Int = 0,
    val thirdPartyScriptCount: Int = 0,
    val formCount: Int = 0,
    val hasCookies: Boolean = false,
    val privacyRiskLabel: String = "Low"
)

data class BrowserSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val studioTheme: StudioTheme = StudioTheme.AMOLED_BLACK,
    val addressBarPosition: AddressBarPosition = AddressBarPosition.TOP,
    val comfortableUi: Boolean = true,
    val desktopDevToolsEnabled: Boolean = false,
    val readerPaper: ReaderPaper = ReaderPaper.SLATE,
    val readerFont: ReaderFont = ReaderFont.SANS,
    val pageTextZoom: Int = 100,
    val javaScriptEnabled: Boolean = true,
    val blockPopups: Boolean = true,
    val defaultDesktopMode: Boolean = false
)

object UrlHelper {
    private val domainRegex = Regex(
        pattern = "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?$"
    )
    private val localhostRegex = Regex(
        pattern = "^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$"
    )

    fun normalizeInputToUrl(rawInput: String, searchEngine: SearchEngine): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.equals("browserhub://start", ignoreCase = true)) {
            return "browserhub://start"
        }

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        if (!trimmed.contains(" ") && (domainRegex.matches(trimmed) || localhostRegex.matches(trimmed))) {
            return if (localhostRegex.matches(trimmed)) {
                "http://$trimmed"
            } else {
                "https://$trimmed"
            }
        }

        val encoded = java.net.URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        return "${searchEngine.searchUrlPrefix}$encoded"
    }

    fun normalizeShortcutUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun extractHost(url: String): String {
        if (url.isBlank()) return ""
        if (url.startsWith("browserhub://")) return url
        return try {
            val host = URI(url).host.orEmpty()
            host.removePrefix("www.")
        } catch (_: Exception) {
            url.removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .removePrefix("www.")
        }
    }

    fun isSecureUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true) || url.startsWith("browserhub://")
    }
}
