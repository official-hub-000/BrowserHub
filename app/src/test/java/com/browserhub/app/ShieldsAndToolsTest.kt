package com.browserhub.app

import com.browserhub.app.ai.AiActionTagParser
import com.browserhub.app.ai.AiQuickNavigationAction
import com.browserhub.app.ai.AiWavEncoder
import com.browserhub.app.ai.BrowserAiEngine
import com.browserhub.app.ai.BrowserLiveContextSnapshot
import com.browserhub.app.browser.PageSuperpowersEngine
import com.browserhub.app.browser.PageToolsEngine
import com.browserhub.app.browser.TranslateLanguage
import com.browserhub.app.data.model.ReaderFont
import com.browserhub.app.data.model.ReaderPaper
import com.browserhub.app.data.model.SearchEngine
import com.browserhub.app.data.model.StudioTheme
import com.browserhub.app.data.model.UrlHelper
import com.browserhub.app.downloads.DownloadAndMediaEngine
import com.browserhub.app.downloads.MediaKind
import com.browserhub.app.extensions.ExtensionStoreDirectory
import com.browserhub.app.games.GamesCatalog
import com.browserhub.app.privacy.ShieldBlockKind
import com.browserhub.app.privacy.ShieldsEngine
import com.browserhub.app.privacy.ShortlinkBypassEngine
import com.browserhub.app.privacy.YouTubeStreamResolver
import com.browserhub.app.vpn.VirtualRegion
import com.browserhub.app.vpn.VpnProxyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ShieldsAndToolsTest {

    @Test
    fun shieldsEngine_blocksAdsAndTrackersAccurately() {
        val adDecision = ShieldsEngine.evaluateRequest(
            url = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            nativeAdBlocker = true,
            analyticsTrackerShield = true
        )
        assertEquals(ShieldBlockKind.AD, adDecision.kind)
        assertTrue(adDecision.estimatedKbSaved > 0)

        val trackerDecision = ShieldsEngine.evaluateRequest(
            url = "https://www.google-analytics.com/analytics.js",
            nativeAdBlocker = true,
            analyticsTrackerShield = true
        )
        assertEquals(ShieldBlockKind.TRACKER, trackerDecision.kind)

        val cleanDecision = ShieldsEngine.evaluateRequest(
            url = "https://developer.android.com/jetpack/compose",
            nativeAdBlocker = true,
            analyticsTrackerShield = true
        )
        assertEquals(ShieldBlockKind.NONE, cleanDecision.kind)
    }

    @Test
    fun shieldsEngine_blocksPopups18PlusAdsYouTubeAdsAndProtectsCaptcha() {
        // Dynamic Clean UA strips "; wv)" & "Version/4.0 " while preserving native Chrome version
        val sampleSystemWvUa = "Mozilla/5.0 (Linux; Android 15; Pixel 9; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/135.0.7049.38 Mobile Safari/537.36"
        val cleanUa = ShieldsEngine.deriveCleanMobileChromeUa(sampleSystemWvUa)
        assertFalse(cleanUa.contains("; wv"))
        assertFalse(cleanUa.contains("Version/4.0"))
        assertTrue(cleanUa.contains("Chrome/135.0.7049.38"))

        // Trusted Google/YouTube/CAPTCHA hosts preserve system UA matching Sec-CH-UA (preventing BotGuard mismatch)
        assertNull(
            ShieldsEngine.resolveEffectiveUserAgent(
                url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
                isDesktopMode = false,
                noCaptchaShield = true,
                shieldsEnabled = true,
                systemDefaultUa = sampleSystemWvUa,
                storeDesktopUa = null,
                desktopUserAgent = ExtensionStoreDirectory.PC_CHROME_UA
            )
        )
        assertNotNull(
            ShieldsEngine.resolveEffectiveUserAgent(
                url = "https://example.com/",
                isDesktopMode = false,
                noCaptchaShield = true,
                shieldsEnabled = true,
                systemDefaultUa = sampleSystemWvUa,
                storeDesktopUa = null,
                desktopUserAgent = ExtensionStoreDirectory.PC_CHROME_UA
            )
        )

        // YouTube ad endpoints are blocked while clean watch/playback requests stay untouched
        assertTrue(ShieldsEngine.isYouTubeAdRequest("https://www.youtube.com/youtubei/v1/player/ad_break"))
        assertTrue(ShieldsEngine.isYouTubeAdRequest("https://m.youtube.com/api/stats/ads?ns=yt"))
        assertFalse(ShieldsEngine.isYouTubeAdRequest("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(
            ShieldBlockKind.AD,
            ShieldsEngine.evaluateRequest(
                url = "https://m.youtube.com/pagead/viewthroughconversion/123/",
                nativeAdBlocker = true,
                analyticsTrackerShield = true
            ).kind
        )

        // 18+ & pop-under/push-spam networks are blocked
        assertEquals(
            ShieldBlockKind.AD,
            ShieldsEngine.evaluateRequest(
                url = "https://syndication.exoclick.com/ads-v2.js",
                nativeAdBlocker = true,
                analyticsTrackerShield = true,
                popupNotificationBlocker = true
            ).kind
        )
        assertTrue(ShieldsEngine.isBlockedAdRedirect("https://cdn.popads.net/pop.js"))
        assertEquals(
            ShieldBlockKind.AD,
            ShieldsEngine.evaluateRequest(
                url = "https://example-stream.test/scripts/popunder.js",
                nativeAdBlocker = false,
                analyticsTrackerShield = false,
                popupNotificationBlocker = true
            ).kind
        )

        // CAPTCHA verification infrastructure is never blocked so challenges pass smoothly
        assertEquals(
            ShieldBlockKind.NONE,
            ShieldsEngine.evaluateRequest(
                url = "https://challenges.cloudflare.com/turnstile/v0/api.js",
                nativeAdBlocker = true,
                analyticsTrackerShield = true,
                popupNotificationBlocker = true
            ).kind
        )
    }

    @Test
    fun youTubeStreamResolver_extractsVideoIdsAndBuildsMirrorFreeBypassJs() {
        assertEquals("dQw4w9WgXcQ", YouTubeStreamResolver.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=5s"))
        assertEquals("dQw4w9WgXcQ", YouTubeStreamResolver.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("abcDEF12345", YouTubeStreamResolver.extractVideoId("https://www.youtube.com/shorts/abcDEF12345"))
        val js = YouTubeStreamResolver.buildInPageBypassPlayerJs(
            videoId = "dQw4w9WgXcQ",
            streamUrl = "https://cdn.example.com/stream.mp4",
            title = "Demo",
            isHlsStream = false
        )
        assertTrue(js.contains("bh-yt-bypass-player"))
        assertTrue(js.contains("BrowserHub Ad-Free Player"))
        assertTrue(js.contains("Native HD"))
        assertTrue(js.contains("Direct HD"))
        assertTrue(js.contains("Clean Embed"))
        assertTrue(js.contains("Reload Stream"))
        assertFalse(js.contains("Mirror 1"))
        assertFalse(js.contains("invidious", ignoreCase = true))
        assertFalse(js.contains("piped", ignoreCase = true))

        // HLS streams must NOT be fed into HTML5 <video src=...> in Android WebView; they route to Native HD
        val hlsJs = YouTubeStreamResolver.buildInPageBypassPlayerJs(
            videoId = "dQw4w9WgXcQ",
            streamUrl = "https://manifest.googlevideo.com/api/manifest/hls_variant/index.m3u8",
            title = "Demo HLS",
            isHlsStream = true
        )
        assertFalse(hlsJs.contains("index.m3u8"))
        assertTrue(hlsJs.contains("resolvedIsHls = true"))

        val fakeStreamUrl = "https://rr1---sn.googlevideo.com/videoplayback?itag=18&expire=999"
        YouTubeStreamResolver.registerStreamUserAgent(fakeStreamUrl, "CustomVR/1.0")
        assertEquals("CustomVR/1.0", YouTubeStreamResolver.lookupStreamUserAgent("$fakeStreamUrl&range=0-65535"))

        assertTrue(ShieldsEngine.YOUTUBE_AD_AND_BOT_SHIELD_JS.contains("enforcement-message-view-model"))
        assertTrue(ShieldsEngine.YOUTUBE_AD_AND_BOT_SHIELD_JS.contains("ytm-feed-nudge-renderer"))
        assertTrue(ShieldsEngine.YOUTUBE_AD_AND_BOT_SHIELD_JS.contains("window.fetch"))
        assertTrue(ShieldsEngine.AD_SKIPPER_JS.contains("#movie_player.ad-showing"))

        val fakeWatchHtml = """
            <script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"OK"},"videoDetails":{"title":"HTML Direct"},"streamingData":{"formats":[{"url":"https://rr2---sn.googlevideo.com/videoplayback?itag=18","mimeType":"video/mp4","height":360}]}};</script>
        """.trimIndent()
        val parsedHtml = YouTubeStreamResolver.extractDirectStreamsFromHtml(fakeWatchHtml, "dQw4w9WgXcQ")
        assertNotNull(parsedHtml)
        assertEquals("HTML Direct", parsedHtml?.title)
        assertEquals("https://rr2---sn.googlevideo.com/videoplayback?itag=18", parsedHtml?.streamUrl)
    }

    @Test
    fun shieldsEngine_stripsTrackingParamsAndUpgradesHttps() {
        val dirty = "http://example.com/article?id=42&utm_source=x&fbclid=123&gclid=abc#section"
        val cleaned = ShieldsEngine.sanitizeNavigationUrl(
            rawUrl = dirty,
            stripTrackingParameters = true,
            upgradeHttps = true
        )
        assertEquals("https://example.com/article?id=42#section", cleaned)

        val local = ShieldsEngine.sanitizeNavigationUrl(
            rawUrl = "http://localhost:8080/test",
            stripTrackingParameters = true,
            upgradeHttps = true
        )
        assertEquals("http://localhost:8080/test", local)
    }

    @Test
    fun shortlinkBypassEngine_coversAdLinksSocialUnlocksPastersShortenersAndResolvers() {
        val bypassableSamples = listOf(
            "https://linkvertise.com/12345/demo",
            "https://bstshrt.com/abc",
            "https://boostylink.com/abc",
            "https://bstlar.com/abc",
            "https://mavenlinks.com/abc",
            "https://vplink.in/abc",
            "https://cuty.io/abc",
            "https://cety.io/abc",
            "https://exe.io/abc",
            "https://lockr.to/abc",
            "https://shrinkme.click/abc",
            "https://lootlabs.gg/s?abc",
            "https://lootlinks.com/s?abc",
            "https://work.ink/123/test",
            "https://paste.work.ink/xyz",
            "https://paster.so/abc",
            "https://paster.gg/abc",
            "https://boost.ink/abc",
            "https://adfoc.us/12345",
            "https://mendationforc.info/abc",
            "https://rekonise.com/unlock",
            "https://rkns.link/unlock",
            "https://mboost.me/a/123",
            "https://bst.gg/abc",
            "https://booo.st/abc",
            "https://social-unlock.com/abc",
            "https://socialwolvez.com/app/l/abc",
            "https://sub2get.com/link?l=123",
            "https://sub2unlock.com/abc",
            "https://sub2unlock.net/abc",
            "https://sub2unlock.me/abc",
            "https://sub4unlock.io/abc",
            "https://subfinal.com/abc",
            "https://unlocknow.net/abc",
            "https://ytsubme.com/abc",
            "https://linkunlocker.com/abc",
            "https://link2unlock.com/abc",
            "https://justpaste.it/abc",
            "https://paste-drop.com/abc",
            "https://pastebin.com/abc",
            "https://pastecanyon.com/abc",
            "https://pastehill.com/abc",
            "https://pastemode.com/abc",
            "https://rentry.org/abc",
            "https://rentry.xyz/abc",
            "https://anotepad.com/notes/abc",
            "https://pastelink.net/abc",
            "https://controlc.com/abc",
            "https://hastebin.com/abc",
            "https://pastefy.app/abc",
            "https://telegra.ph/abc",
            "https://gist.github.com/user/abc",
            "https://bit.ly/abc",
            "https://cl.gy/abc",
            "https://goo.gl/abc",
            "https://is.gd/abc",
            "https://rebrand.ly/abc",
            "https://shorter.me/abc",
            "https://t.co/abc",
            "https://t.ly/abc",
            "https://tiny.cc/abc",
            "https://tinylink.onl/abc",
            "https://tinyurl.com/abc",
            "https://v.gd/abc",
            "https://www.google.com/url?q=https://example.org/clean",
            "https://justpaste.it/redirect/abc?to=https://example.org/clean"
        )
        bypassableSamples.forEach { sample ->
            assertTrue("Expected bypass detection for $sample", ShortlinkBypassEngine.isShortlinkOrWrapperUrl(sample))
        }
        assertFalse(ShortlinkBypassEngine.isShortlinkOrWrapperUrl("https://developer.android.com/"))

        val targetUrl = "https://github.com/official-hub-000/BrowserHub"
        val encodedTarget = Base64.getEncoder().encodeToString(targetUrl.toByteArray(Charsets.UTF_8))

        // BaseResolver via query parameter & fragment & path
        assertEquals(targetUrl, ShortlinkBypassEngine.extractEmbeddedQueryTarget("https://link-to.net/dynamic/?r=$encodedTarget"))
        assertEquals(targetUrl, ShortlinkBypassEngine.extractBase64PathOrFragmentTarget("https://random-wrapper.example/go/$encodedTarget"))
        assertEquals(targetUrl, ShortlinkBypassEngine.extractBase64PathOrFragmentTarget("https://random-wrapper.example/#$encodedTarget"))

        // Google URL unwrap
        assertEquals(targetUrl, ShortlinkBypassEngine.unwrapNestedTargets("https://www.google.com/url?q=$targetUrl&sa=D"))

        val metaHtml = """<html><head><meta http-equiv="refresh" content="0;url=https://example.org/final-file.zip"></head></html>"""
        assertEquals(
            "https://example.org/final-file.zip",
            ShortlinkBypassEngine.extractDestinationFromHtml(metaHtml, "https://gplinks.co/step1")
        )

        val adfocHtml = """<script>var click_url = "https://example.org/real-download";</script>"""
        assertEquals(
            "https://example.org/real-download",
            ShortlinkBypassEngine.extractDestinationFromHtml(adfocHtml, "https://adfoc.us/123")
        )

        val atobHtml = """<script>var go = atob('$encodedTarget');</script>"""
        assertEquals(
            targetUrl,
            ShortlinkBypassEngine.extractDestinationFromHtml(atobHtml, "https://shrinkme.io/x")
        )
    }

    @Test
    fun downloadAndMediaEngine_extractsVideoAudioAndPhotoItems() {
        assertEquals(MediaKind.VIDEO, DownloadAndMediaEngine.classifyMediaKind("https://cdn.example.com/clip.mp4"))
        assertEquals(MediaKind.AUDIO, DownloadAndMediaEngine.classifyMediaKind("https://cdn.example.com/track.mp3"))
        assertEquals(MediaKind.PHOTO, DownloadAndMediaEngine.classifyMediaKind("https://cdn.example.com/cover.jpg"))

        val html = """
            <html>
            <head>
                <title>Demo Media Page</title>
                <meta property="og:video" content="https://cdn.example.com/video/sample.mp4">
                <meta property="og:audio" content="https://cdn.example.com/audio/podcast.mp3">
                <meta property="og:image" content="https://cdn.example.com/images/poster.jpg">
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val items = DownloadAndMediaEngine.parseHtmlMediaItems(html, "https://example.com/watch")
        assertTrue(items.any { it.kind == MediaKind.VIDEO && it.url == "https://cdn.example.com/video/sample.mp4" })
        assertTrue(items.any { it.kind == MediaKind.AUDIO && it.url == "https://cdn.example.com/audio/podcast.mp3" })
        assertTrue(items.any { it.kind == MediaKind.PHOTO && it.url == "https://cdn.example.com/images/poster.jpg" })
    }

    @Test
    fun gamesCatalog_has18PlayableGamesWithValidHtml() {
        assertEquals(18, GamesCatalog.games.size)
        GamesCatalog.games.forEach { game ->
            val html = GamesCatalog.buildPlayableGameHtml(game)
            assertTrue(html.contains("<!DOCTYPE html>"))
            assertTrue(html.contains(game.title))
        }
    }

    @Test
    fun pageTools_buildsReaderHtmlAnd14TranslateLanguages() {
        assertEquals(14, TranslateLanguage.entries.size)
        val json = """{"title":"Sample Article","blocks":[{"tag":"p","text":"Hello world content block."}]}"""
        val html = PageToolsEngine.buildReaderHtml(
            rawJson = json,
            sourceUrl = "https://example.com",
            paper = ReaderPaper.SEPIA,
            font = ReaderFont.SERIF,
            zoomPercent = 120
        )
        assertTrue(html.contains("Sample Article"))
        assertTrue(html.contains("#F4ECD8"))
        assertTrue(html.contains("Georgia"))
    }

    @Test
    fun searchEnginesAndThemes_matchFlagshipCatalog() {
        assertEquals(7, SearchEngine.entries.size)
        assertEquals(7, StudioTheme.entries.size)

        val ghQuery = UrlHelper.normalizeInputToUrl("jetpack compose", SearchEngine.GITHUB)
        assertTrue(ghQuery.startsWith("https://github.com/search?q="))
    }

    @Test
    fun extensionStoreDirectory_detectsChromeFirefoxEdgeOperaUrls() {
        val chromeMatch = ExtensionStoreDirectory.detectStoreExtension(
            "https://chromewebstore.google.com/detail/ublock-origin/cjpalhdlnbpafiamejdnhcphjbkeiagm"
        )
        assertNotNull(chromeMatch)
        assertEquals("cjpalhdlnbpafiamejdnhcphjbkeiagm", chromeMatch?.extensionId)

        val amoMatch = ExtensionStoreDirectory.detectStoreExtension(
            "https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/"
        )
        assertNotNull(amoMatch)
        assertEquals("ublock-origin", amoMatch?.extensionId)

        val markJs = ExtensionStoreDirectory.buildMarkInstalledJs(
            extensionId = "ublock-origin",
            extensionName = "uBlock Origin",
            installedIds = setOf("ublock-origin")
        )
        assertFalse(markJs.isBlank())
        assertTrue(markJs.contains("Added to BrowserHub"))
    }

    @Test
    fun shortlinkBypassEngine_ignoresPasterHomepagesAndDetectsNewTldAliases() {
        // Bare paster homepages must never auto-bypass
        assertFalse(ShortlinkBypassEngine.isShortlinkOrWrapperUrl("https://pastebin.com/"))
        assertFalse(ShortlinkBypassEngine.isShortlinkOrWrapperUrl("https://gist.github.com"))
        assertFalse(ShortlinkBypassEngine.isShortlinkOrWrapperUrl("https://rentry.org/"))
        assertFalse(ShortlinkBypassEngine.isShortlinkOrWrapperUrl("https://telegra.ph/"))

        // Specific paste paths & TLD aliases should still trigger bypass
        val newTldSamples = listOf(
            "https://pastebin.com/abc123",
            "https://gist.github.com/octocat/12345",
            "https://bstshrt.io/xyz",
            "https://bstshrt.net/xyz",
            "https://boostylink.net/xyz",
            "https://mavenlinks.net/xyz",
            "https://cutty.io/xyz",
            "https://exee.io/xyz",
            "https://shrinkme.site/xyz",
            "https://quickgate.net/xyz",
            "https://linkgec.net/xyz",
            "https://pasteva.net/xyz",
            "https://pasteview.net/xyz",
            "https://vaultlinks.net/xyz",
            "https://hastebin.de/xyz"
        )
        newTldSamples.forEach { url ->
            assertTrue("Expected bypass detection for $url", ShortlinkBypassEngine.isShortlinkOrWrapperUrl(url))
        }
    }

    @Test
    fun vpnProxyParser_andLocationMaskJs_workAccurately() {
        val socks = VpnProxyParser.parse("socks5://127.0.0.1:9050")
        assertNotNull(socks)
        assertEquals("socks5", socks?.scheme)
        assertEquals("127.0.0.1", socks?.host)
        assertEquals(9050, socks?.port)

        val http = VpnProxyParser.parse("http://proxy.example.com:8080")
        assertNotNull(http)
        assertEquals("http", http?.scheme)
        assertEquals("proxy.example.com", http?.host)
        assertEquals(8080, http?.port)

        val bare = VpnProxyParser.parse("10.0.0.2:3128")
        assertNotNull(bare)
        assertEquals("http://10.0.0.2:3128", bare?.proxyConfigUrl)

        assertNull(VpnProxyParser.parse(""))
        assertNull(VpnProxyParser.parse("not-a-valid-proxy"))
        assertNull(VpnProxyParser.parse("ftp://127.0.0.1:21"))
        assertNull(VpnProxyParser.parse("socks5://127.0.0.1:99999"))

        val ch = VirtualRegion.SWITZERLAND
        val maskJs = ShieldsEngine.buildLocationMaskJs(
            latitude = ch.latitude,
            longitude = ch.longitude,
            timezoneId = ch.timezoneId,
            tzOffsetMinutesWest = ch.tzOffsetMinutesWest
        )
        assertTrue(maskJs.contains("Europe/Zurich"))
        assertTrue(maskJs.contains("47.3769"))
        assertTrue(maskJs.contains("-60"))
    }

    @Test
    fun youTubeStreamResolver_parsesNonDrmInnerTubeAndRejectsDrm() {
        val cleanJson = """
            {
              "playabilityStatus": { "status": "OK" },
              "videoDetails": { "title": "Open Source Demo" },
              "streamingData": {
                "formats": [
                  {
                    "url": "https://rr1---sn.googlevideo.com/videoplayback?itag=18",
                    "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
                    "height": 360
                  },
                  {
                    "url": "https://rr1---sn.googlevideo.com/videoplayback?itag=22",
                    "mimeType": "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"",
                    "height": 720
                  }
                ],
                "adaptiveFormats": [
                  {
                    "url": "https://rr1---sn.googlevideo.com/audioplayback?itag=140",
                    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                    "bitrate": 128000
                  }
                ]
              }
            }
        """.trimIndent()

        val resolved = YouTubeStreamResolver.parseInnerTubeResponse(cleanJson, "dQw4w9WgXcQ")
        assertNotNull(resolved)
        assertEquals("Open Source Demo", resolved?.title)
        assertEquals("https://rr1---sn.googlevideo.com/videoplayback?itag=22", resolved?.streamUrl)
        assertEquals("https://rr1---sn.googlevideo.com/audioplayback?itag=140", resolved?.audioUrl)
        assertFalse(resolved?.isHls == true)

        val drmJson = """
            {
              "playabilityStatus": { "status": "OK" },
              "videoDetails": { "title": "Protected Movie" },
              "streamingData": {
                "drmFamilies": ["WIDEVINE"],
                "formats": [
                  {
                    "url": "https://rr1---sn.googlevideo.com/videoplayback?itag=22",
                    "mimeType": "video/mp4",
                    "height": 720
                  }
                ]
              }
            }
        """.trimIndent()
        assertNull(YouTubeStreamResolver.parseInnerTubeResponse(drmJson, "drm12345"))
    }

    @Test
    fun browserAiEngine_systemMemoryCoversAllSubsystemsAndParsesActionTags() {
        val memory = BrowserAiEngine.BROWSERHUB_SYSTEM_MEMORY
        assertTrue(memory.contains("VPN"))
        assertTrue(memory.contains("VirtualRegion"))
        assertTrue(memory.contains("Extensions Hub"))
        assertTrue(memory.contains("Shortlink"))
        assertTrue(memory.contains("Media Grabber"))
        assertTrue(memory.contains("Gemini 3.1 Flash TTS"))

        val rawResponse = """
            Tap **Open VPN & Location Shield** below to turn on Cloudflare 1.1.1.1 and mask your timezone.
            [[OPEN:VPN]] [[OPEN:EXTENSIONS]] [[OPEN:UNKNOWN_TAG]]
        """.trimIndent()
        val parsed = AiActionTagParser.parse(rawResponse)
        assertFalse(parsed.cleanText.contains("[[OPEN:"))
        assertEquals(2, parsed.actions.size)
        assertEquals(AiQuickNavigationAction.OPEN_VPN, parsed.actions[0])
        assertEquals(AiQuickNavigationAction.OPEN_EXTENSIONS, parsed.actions[1])

        val snapshot = BrowserLiveContextSnapshot(
            activeTabTitle = "GitHub",
            activeTabUrl = "https://github.com/official-hub-000/BrowserHub",
            vpnConnected = true,
            installedExtensionCount = 3
        )
        assertEquals(3, snapshot.installedExtensionCount)
        assertTrue(snapshot.vpnConnected)
    }

    @Test
    fun aiWavEncoder_writesValidRiffHeaderAndEstimatesDuration() {
        val sampleRate = 24_000
        val oneSecondPcm = ByteArray(sampleRate * 2) { 0x1A }
        val wav = AiWavEncoder.pcm16MonoToWavBytes(oneSecondPcm, sampleRate = sampleRate)
        assertEquals(44 + oneSecondPcm.size, wav.size)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(wav.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(wav.copyOfRange(36, 40), Charsets.US_ASCII))
        assertEquals(1, AiWavEncoder.estimateDurationSeconds(oneSecondPcm.size, sampleRate = sampleRate))
    }

    @Test
    fun pageSuperpowersEngine_parsesXRayAndBuildsScriptsCleanly() {
        val rawJson = """
            {
              "url": "https://example.org/deep-dive",
              "title": "Deep Dive Article",
              "wordCount": 840,
              "linkCount": 24,
              "imageCount": 6,
              "scriptCount": 14,
              "thirdPartyScriptCount": 8,
              "formCount": 1,
              "hasCookies": true
            }
        """.trimIndent()
        val report = PageSuperpowersEngine.parseXRayReport(rawJson, "https://fallback.test")
        assertEquals("Deep Dive Article", report.title)
        assertEquals(840, report.wordCount)
        assertEquals(4, report.readingMinutes)
        assertEquals(8, report.thirdPartyScriptCount)
        assertTrue(report.hasCookies)
        assertEquals("Elevated", report.privacyRiskLabel)

        val darkOnJs = PageSuperpowersEngine.buildForceDarkWebJs(true)
        assertTrue(darkOnJs.contains("bh-force-dark-style"))
        assertTrue(darkOnJs.contains("invert(0.92)"))

        val scrollJs = PageSuperpowersEngine.buildAutoScrollJs(2)
        assertTrue(scrollJs.contains("requestAnimationFrame"))
        assertTrue(PageSuperpowersEngine.buildVideoSpeedJs(2.5f).contains("2.5"))
        assertTrue(PageSuperpowersEngine.buildCopyUnlockJs(true).contains("user-select: text"))
    }
}
