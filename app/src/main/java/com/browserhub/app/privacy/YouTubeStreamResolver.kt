package com.browserhub.app.privacy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ResolvedYouTubeStream(
    val videoId: String,
    val title: String,
    val streamUrl: String,
    val audioUrl: String? = null,
    val isHls: Boolean = false,
    val sourceName: String = "Ad-Free Direct Stream",
    val userAgent: String = ShieldsEngine.CLEAN_MOBILE_CHROME_UA
)

/**
 * Direct, mirror-free YouTube stream & embed resolver.
 * Resolves all non-DRM direct streams concurrently, prioritizing progressive MP4 for
 * HTML5 <video> (with exact User-Agent header forwarding via WebView shouldInterceptRequest)
 * and HLS HD streams for the built-in native hardware VideoView/MediaPlayer.
 */
object YouTubeStreamResolver {

    private val registeredStreamUserAgents = ConcurrentHashMap<String, String>()

    fun registerStreamUserAgent(streamUrl: String, userAgent: String) {
        if (streamUrl.isBlank() || userAgent.isBlank()) return
        registeredStreamUserAgents[streamUrl] = userAgent
        // Keep cache bounded so memory stays tiny
        if (registeredStreamUserAgents.size > 48) {
            val firstKey = registeredStreamUserAgents.keys.firstOrNull()
            if (firstKey != null) registeredStreamUserAgents.remove(firstKey)
        }
    }

    fun lookupStreamUserAgent(requestUrl: String): String? {
        if (requestUrl.isBlank()) return null
        registeredStreamUserAgents[requestUrl]?.let { return it }
        val baseKey = stripRangeParam(requestUrl)
        return registeredStreamUserAgents.entries.firstOrNull { (url, _) ->
            stripRangeParam(url) == baseKey
        }?.value
    }

    private fun stripRangeParam(url: String): String {
        return url.replace(Regex("[&?]range=[^&]*"), "")
    }

    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(trimmed)) return trimmed
        return try {
            val uri = URI(trimmed)
            val host = uri.host.orEmpty().lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
            if (host != "youtube.com" && host != "youtu.be" && host != "youtube-nocookie.com") {
                return null
            }
            if (host == "youtu.be") {
                return uri.path.orEmpty().trim('/').substringBefore('/').takeIf { it.length in 8..16 }
            }
            val query = uri.rawQuery.orEmpty()
            val vParam = query.split('&')
                .firstOrNull { it.startsWith("v=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.substringBefore('&')
                ?.trim()
            if (!vParam.isNullOrBlank() && vParam.length in 8..16) {
                return vParam
            }
            val path = uri.path.orEmpty().trim('/')
            val segments = path.split('/')
            if (segments.size >= 2 && segments[0] in setOf("shorts", "embed", "live", "v")) {
                return segments[1].takeIf { it.length in 8..16 }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun buildNoCookieEmbedUrl(videoId: String): String {
        return "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&rel=0&playsinline=1&modestbranding=1"
    }

    suspend fun resolveAllPlayableStreams(videoId: String): List<ResolvedYouTubeStream> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()

        val visitorData = runCatching { fetchYouTubeVisitorData() }.getOrNull()

        val candidates: List<suspend () -> ResolvedYouTubeStream?> = listOf(
            suspend { fetchFromInnerTubeAndroidVr(videoId, visitorData) },
            suspend { fetchFromInnerTubeAndroidEmbedded(videoId, visitorData) },
            suspend { fetchFromInnerTubeAndroidTestsuite(videoId, visitorData) },
            suspend { fetchFromInnerTubeWebEmbedded(videoId, visitorData) },
            suspend { fetchFromInnerTubeTvEmbedded(videoId, visitorData) },
            suspend { fetchFromInnerTubeIos(videoId, visitorData) },
            suspend { fetchFromYouTubeEmbedAndWatchHtml(videoId) }
        )

        val rawResults = coroutineScope {
            candidates.map { fn ->
                async {
                    withTimeoutOrNull(4_500L) {
                        try {
                            fn()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        // Deduplicate & order: Progressive MP4 streams first (playable in BOTH WebView & Native),
        // followed by HLS .m3u8 HD streams (playable natively in Android VideoView/MediaPlayer).
        val ordered = rawResults
            .distinctBy { it.streamUrl }
            .sortedWith(compareBy<ResolvedYouTubeStream> { it.isHls }.thenBy {
                when {
                    it.sourceName.contains("VR") -> 0
                    it.sourceName.contains("Embedded") -> 1
                    it.sourceName.contains("Page") -> 2
                    it.sourceName.contains("HLS") -> 3
                    else -> 4
                }
            })

        ordered.forEach { stream ->
            registerStreamUserAgent(stream.streamUrl, stream.userAgent)
        }
        ordered
    }

    suspend fun resolvePlayableStream(videoId: String): ResolvedYouTubeStream? {
        return resolveAllPlayableStreams(videoId).firstOrNull()
    }

    private fun fetchYouTubeVisitorData(): String? {
        val body = JSONObject().put(
            "context",
            JSONObject().put(
                "client",
                JSONObject().apply {
                    put("clientName", "MWEB")
                    put("clientVersion", "2.20250305.01.00")
                    put("hl", "en")
                    put("gl", "US")
                }
            )
        ).toString()
        val raw = postJson(
            url = "https://m.youtube.com/youtubei/v1/visitor_id?prettyPrint=false",
            jsonBody = body,
            userAgent = ShieldsEngine.CLEAN_MOBILE_CHROME_UA
        ) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return json.optJSONObject("responseContext")
            ?.optString("visitorData")
            ?.takeIf { it.isNotBlank() }
    }

    private fun fetchFromInnerTubeWebEmbedded(
        videoId: String,
        visitorData: String?
    ): ResolvedYouTubeStream? {
        val ua = ShieldsEngine.CLEAN_MOBILE_CHROME_UA
        val client = JSONObject().apply {
            put("clientName", "WEB_EMBEDDED_PLAYER")
            put("clientVersion", "1.20250305.01.00")
            put("hl", "en")
            put("gl", "US")
            if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().apply {
                put("client", client)
                put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/"))
            })
        }.toString()
        val raw = postJson(
            url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            jsonBody = body,
            userAgent = ua,
            visitorData = visitorData,
            clientNameHeader = "56",
            clientVersionHeader = "1.20250305.01.00"
        ) ?: return null
        return parseInnerTubeResponse(raw, videoId, "Web-Embedded MP4", ua)
    }

    private fun fetchFromInnerTubeAndroidEmbedded(
        videoId: String,
        visitorData: String?
    ): ResolvedYouTubeStream? {
        val ua = "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"
        val client = JSONObject().apply {
            put("clientName", "ANDROID_EMBEDDED_PLAYER")
            put("clientVersion", "19.09.37")
            put("androidSdkVersion", 30)
            put("hl", "en")
            put("gl", "US")
            if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().apply {
                put("client", client)
                put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/"))
            })
        }.toString()
        val raw = postJson(
            url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            jsonBody = body,
            userAgent = ua,
            visitorData = visitorData,
            clientNameHeader = "55",
            clientVersionHeader = "19.09.37"
        ) ?: return null
        return parseInnerTubeResponse(raw, videoId, "Android Embedded MP4", ua)
    }

    private fun fetchFromInnerTubeTvEmbedded(
        videoId: String,
        visitorData: String?
    ): ResolvedYouTubeStream? {
        val ua = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.125 TV Safari/537.36"
        val client = JSONObject().apply {
            put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
            put("clientVersion", "2.0")
            put("hl", "en")
            put("gl", "US")
            if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().apply {
                put("client", client)
                put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/"))
            })
        }.toString()
        val raw = postJson(
            url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            jsonBody = body,
            userAgent = ua,
            visitorData = visitorData,
            clientNameHeader = "85",
            clientVersionHeader = "2.0"
        ) ?: return null
        return parseInnerTubeResponse(raw, videoId, "Direct TV MP4", ua)
    }

    private fun fetchFromInnerTubeAndroidVr(
        videoId: String,
        visitorData: String?
    ): ResolvedYouTubeStream? {
        val ua = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        val client = JSONObject().apply {
            put("clientName", "ANDROID_VR")
            put("clientVersion", "1.60.19")
            put("deviceMake", "Oculus")
            put("deviceModel", "Quest 3")
            put("osName", "Android")
            put("osVersion", "12L")
            put("androidSdkVersion", 32)
            put("hl", "en")
            put("gl", "US")
            if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().put("client", client))
        }.toString()
        val raw = postJson(
            url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            jsonBody = body,
            userAgent = ua,
            visitorData = visitorData,
            clientNameHeader = "28",
            clientVersionHeader = "1.60.19"
        ) ?: return null
        return parseInnerTubeResponse(raw, videoId, "Direct VR MP4", ua)
    }

    private fun fetchFromInnerTubeAndroidTestsuite(
        videoId: String,
        visitorData: String?
    ): ResolvedYouTubeStream? {
        val ua = "com.google.android.youtube/1.9 (Linux; U; Android 11) gzip"
        val client = JSONObject().apply {
            put("clientName", "ANDROID_TESTSUITE")
            put("clientVersion", "1.9")
            put("androidSdkVersion", 30)
            put("hl", "en")
            put("gl", "US")
            if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().put("client", client))
        }.toString()
        val raw = postJson(
            url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            jsonBody = body,
            userAgent = ua,
            visitorData = visitorData,
            clientNameHeader = "30",
            clientVersionHeader = "1.9"
        ) ?: return null
        return parseInnerTubeResponse(raw, videoId, "Direct Suite MP4", ua)
    }

    private fun fetchFromInnerTubeIos(
        videoId: String,
        visitorData: String?
    ): ResolvedYouTubeStream? {
        val ua = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X; en_US)"
        val client = JSONObject().apply {
            put("clientName", "IOS")
            put("clientVersion", "20.10.4")
            put("deviceModel", "iPhone16,2")
            put("osName", "iPhone")
            put("osVersion", "18.3.2.22D82")
            put("hl", "en")
            put("gl", "US")
            if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().put("client", client))
        }.toString()
        val raw = postJson(
            url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            jsonBody = body,
            userAgent = ua,
            visitorData = visitorData,
            clientNameHeader = "5",
            clientVersionHeader = "20.10.4"
        ) ?: return null
        return parseInnerTubeResponse(raw, videoId, "iOS HLS HD", ua)
    }

    private fun fetchFromYouTubeEmbedAndWatchHtml(videoId: String): ResolvedYouTubeStream? {
        val urls = listOf(
            "https://www.youtube.com/embed/$videoId",
            "https://m.youtube.com/watch?v=$videoId&bpctr=9999999999&has_verified=1"
        )
        for (target in urls) {
            val html = fetchText(target) ?: continue
            val parsed = extractDirectStreamsFromHtml(html, videoId)
            if (parsed != null) return parsed
        }
        return null
    }

    internal fun extractDirectStreamsFromHtml(html: String, videoId: String): ResolvedYouTubeStream? {
        val markers = listOf(
            "var ytInitialPlayerResponse = ",
            "ytInitialPlayerResponse = ",
            "\"playerResponse\":"
        )
        for (marker in markers) {
            val idx = html.indexOf(marker)
            if (idx < 0) continue
            val braceStart = html.indexOf('{', idx + marker.length)
            if (braceStart < 0) continue
            val jsonSlice = extractBalancedJsonObject(html, braceStart) ?: continue
            val parsed = parseInnerTubeResponse(jsonSlice, videoId, "Direct Page MP4", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun extractBalancedJsonObject(source: String, startIndex: Int): String? {
        if (startIndex !in source.indices || source[startIndex] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        val limit = minOf(source.length, startIndex + 250_000)
        for (i in startIndex until limit) {
            val ch = source[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        return source.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    internal fun parseInnerTubeResponse(
        rawJson: String,
        videoId: String,
        sourceLabel: String = "Ad-Free Direct Stream",
        userAgent: String = ShieldsEngine.CLEAN_MOBILE_CHROME_UA
    ): ResolvedYouTubeStream? {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val playability = obj.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        if (!status.equals("OK", ignoreCase = true)) return null

        val streamingData = obj.optJSONObject("streamingData") ?: return null
        if (streamingData.has("drmFamilies") || streamingData.has("licenseInfos")) return null

        val title = obj.optJSONObject("videoDetails")?.optString("title").orEmpty().ifBlank { "YouTube Video" }
        val hlsManifest = streamingData.optString("hlsManifestUrl").takeIf { it.startsWith("https://") }

        var bestMuxedUrl: String? = null
        var bestHeight = -1
        val formats = streamingData.optJSONArray("formats")
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val directUrl = f.optString("url")
                if (!directUrl.startsWith("https://")) continue
                val mime = f.optString("mimeType").lowercase(Locale.US)
                val height = f.optInt("height", 360)
                if (mime.contains("mp4") && height >= bestHeight) {
                    bestHeight = height
                    bestMuxedUrl = directUrl
                }
            }
        }

        var bestAudioUrl: String? = null
        var bestBitrate = -1
        val adaptive = streamingData.optJSONArray("adaptiveFormats")
        if (adaptive != null) {
            for (i in 0 until adaptive.length()) {
                val a = adaptive.optJSONObject(i) ?: continue
                val directUrl = a.optString("url")
                if (!directUrl.startsWith("https://")) continue
                val mime = a.optString("mimeType").lowercase(Locale.US)
                val bitrate = a.optInt("bitrate", 0)
                if (mime.startsWith("audio/") && bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    bestAudioUrl = directUrl
                }
            }
        }

        val chosen = bestMuxedUrl ?: hlsManifest ?: return null
        return ResolvedYouTubeStream(
            videoId = videoId,
            title = title,
            streamUrl = chosen,
            audioUrl = bestAudioUrl,
            isHls = chosen == hlsManifest && bestMuxedUrl == null,
            sourceName = sourceLabel,
            userAgent = userAgent
        )
    }

    private fun postJson(
        url: String,
        jsonBody: String,
        userAgent: String,
        visitorData: String? = null,
        clientNameHeader: String? = null,
        clientVersionHeader: String? = null
    ): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 4_000
            readTimeout = 4_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("Referer", "https://www.youtube.com/")
            if (!clientNameHeader.isNullOrBlank()) {
                setRequestProperty("X-YouTube-Client-Name", clientNameHeader)
            }
            if (!clientVersionHeader.isNullOrBlank()) {
                setRequestProperty("X-YouTube-Client-Version", clientVersionHeader)
            }
            if (!visitorData.isNullOrBlank()) {
                setRequestProperty("X-Goog-Visitor-Id", visitorData)
            }
        }
        return try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchText(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Cookie", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg; CONSENT=YES+cb.20230531-04-p0.en+FX+908")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Mounts an in-page Ad-Free player cleanly above Polymer templates (un-hiding collapsed
     * #player-container-id wrappers), playing progressive MP4 through our UA-forwarding
     * WebView range proxy or launching the Native HD VideoView Player for HLS/fallback.
     */
    fun buildInPageBypassPlayerJs(
        videoId: String,
        streamUrl: String?,
        title: String?,
        isHlsStream: Boolean = false,
        forceReplace: Boolean = true,
        noCookieUrl: String = buildNoCookieEmbedUrl(videoId)
    ): String {
        val safeWebStream = if (isHlsStream) "" else streamUrl.orEmpty()
        val escapedStream = JSONObject.quote(safeWebStream)
        val escapedNoCookie = JSONObject.quote(noCookieUrl)
        val escapedTitle = JSONObject.quote(title ?: "Ad-Free Playback Active")
        val escapedVid = JSONObject.quote(videoId)
        val replaceLiteral = if (forceReplace) "true" else "false"
        val isHlsLiteral = if (isHlsStream) "true" else "false"

        return """
            (function() {
                try {
                    var stream = $escapedStream;
                    var noCookie = $escapedNoCookie;
                    var titleText = $escapedTitle;
                    var vid = $escapedVid;
                    var forceReplace = $replaceLiteral;
                    var resolvedIsHls = $isHlsLiteral;

                    // Pause & mute underlying blocked/ad YouTube player elements
                    document.querySelectorAll('video').forEach(function(v) {
                        if (!v.closest('#bh-yt-bypass-player')) {
                            try { v.pause(); v.muted = true; } catch(e) {}
                        }
                    });

                    // Hide YouTube bot-wall / enforcement / nudge cards across desktop & mobile
                    var errorSelectors = [
                        'enforcement-message-view-model',
                        'ytm-enforcement-message-view-model',
                        'yt-enforcement-message-view-model',
                        'ytd-enforcement-message-view-model',
                        'ytm-player-error-message-renderer',
                        'ytd-player-error-message-renderer',
                        'yt-player-error-message-renderer',
                        'ytm-playability-error-supported-renderers',
                        'ytm-feed-nudge-renderer',
                        'ytm-mealbar-promo-renderer',
                        'ytm-upsell-dialog-renderer',
                        'yt-confirm-dialog-renderer',
                        '.ytp-error',
                        '#error-screen',
                        '.error-container',
                        '#player-error-message-container'
                    ];
                    document.querySelectorAll(errorSelectors.join(',')).forEach(function(el) {
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('height', '0', 'important');
                    });

                    var hostContainer = document.querySelector('#player-container-id, #player, .player-container, ytm-player, #movie_player')
                        || document.body;
                    if (!hostContainer) return;

                    // Un-hide Polymer player container if LOGIN_REQUIRED collapsed or hid it
                    if (hostContainer !== document.body) {
                        hostContainer.removeAttribute('hidden');
                        hostContainer.style.setProperty('display', 'block', 'important');
                        hostContainer.style.setProperty('visibility', 'visible', 'important');
                        hostContainer.style.setProperty('min-height', '220px', 'important');
                        hostContainer.style.setProperty('height', 'auto', 'important');
                        hostContainer.style.setProperty('opacity', '1', 'important');
                    }

                    var existing = document.getElementById('bh-yt-bypass-player');
                    if (existing) {
                        if (!forceReplace && existing.getAttribute('data-vid') === vid) return;
                        existing.remove();
                    }

                    if (hostContainer !== document.body) {
                        for (var c = 0; c < hostContainer.children.length; c++) {
                            var child = hostContainer.children[c];
                            if (child.id !== 'bh-yt-bypass-player') {
                                child.style.setProperty('display', 'none', 'important');
                            }
                        }
                    }

                    var wrap = document.createElement('div');
                    wrap.id = 'bh-yt-bypass-player';
                    wrap.setAttribute('data-vid', vid);
                    wrap.style.cssText = 'position:relative;width:100%;aspect-ratio:16/9;min-height:220px;max-height:60vh;background:#020617;z-index:9999;overflow:hidden;border-bottom:1px solid #1e293b;display:block;';

                    var stage = document.createElement('div');
                    stage.style.cssText = 'width:100%;height:100%;position:relative;min-height:220px;';
                    wrap.appendChild(stage);

                    var openNative = function() {
                        if (window.BrowserHubExtBridge && window.BrowserHubExtBridge.openNativeVideoPlayer) {
                            window.BrowserHubExtBridge.openNativeVideoPlayer('https://m.youtube.com/watch?v=' + encodeURIComponent(vid));
                        }
                    };

                    var loadNoCookieEmbed = function() {
                        stage.innerHTML = '';
                        var f = document.createElement('iframe');
                        f.src = noCookie;
                        f.allow = 'autoplay; encrypted-media; picture-in-picture; fullscreen';
                        f.allowFullscreen = true;
                        f.referrerPolicy = 'strict-origin-when-cross-origin';
                        f.style.cssText = 'width:100%;height:100%;min-height:220px;border:0;background:#000;';
                        stage.appendChild(f);
                    };

                    var pickBestStreamFromJson = function(data) {
                        try {
                            if (!data || !data.playabilityStatus || data.playabilityStatus.status !== 'OK') return '';
                            var sd = data.streamingData;
                            if (!sd || sd.drmFamilies || sd.licenseInfos) return '';
                            var bestUrl = '';
                            var bestH = -1;
                            var fmts = Array.isArray(sd.formats) ? sd.formats : [];
                            for (var i = 0; i < fmts.length; i++) {
                                var f = fmts[i];
                                if (f && f.url && String(f.url).indexOf('https://') === 0) {
                                    var h = Number(f.height || 360);
                                    if (h >= bestH) { bestH = h; bestUrl = f.url; }
                                }
                            }
                            return bestUrl;
                        } catch(e) {}
                        return '';
                    };

                    var fetchLiveWebViewStream = function(onDone) {
                        var apiKey = '';
                        var visitor = '';
                        try {
                            if (window.ytcfg && typeof window.ytcfg.get === 'function') {
                                apiKey = window.ytcfg.get('INNERTUBE_API_KEY') || '';
                                visitor = window.ytcfg.get('VISITOR_DATA') || '';
                            }
                        } catch(e) {}
                        var endpoint = location.origin + '/youtubei/v1/player?prettyPrint=false' + (apiKey ? ('&key=' + encodeURIComponent(apiKey)) : '');
                        var clients = [
                            { clientName: 'WEB_EMBEDDED_PLAYER', clientVersion: '1.20250305.01.00', thirdParty: true },
                            { clientName: 'TVHTML5_SIMPLY_EMBEDDED_PLAYER', clientVersion: '2.0', thirdParty: true }
                        ];
                        var tryIdx = 0;
                        var nextAttempt = function() {
                            if (tryIdx >= clients.length) { onDone(''); return; }
                            var spec = clients[tryIdx++];
                            var ctxObj = {
                                client: { clientName: spec.clientName, clientVersion: spec.clientVersion, hl: 'en', gl: 'US' }
                            };
                            if (visitor) ctxObj.client.visitorData = visitor;
                            if (spec.thirdParty) ctxObj.thirdParty = { embedUrl: 'https://www.youtube.com/' };
                            fetch(endpoint, {
                                method: 'POST',
                                credentials: 'include',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ videoId: vid, contentCheckOk: true, racyCheckOk: true, context: ctxObj })
                            })
                            .then(function(r) { return r.ok ? r.json() : null; })
                            .then(function(json) {
                                var found = pickBestStreamFromJson(json);
                                if (found) onDone(found); else nextAttempt();
                            })
                            .catch(function() { nextAttempt(); });
                        };
                        nextAttempt();
                    };

                    var showNativeLaunchCard = function(noteText) {
                        stage.innerHTML = '';
                        var box = document.createElement('div');
                        box.style.cssText = 'width:100%;height:100%;min-height:220px;background:linear-gradient(135deg,#020617,#0f172a);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;padding:18px;box-sizing:border-box;text-align:center;';
                        var thumb = document.createElement('img');
                        thumb.src = 'https://i.ytimg.com/vi/' + encodeURIComponent(vid) + '/hqdefault.jpg';
                        thumb.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:0.28;pointer-events:none;';
                        box.appendChild(thumb);
                        var msg = document.createElement('div');
                        msg.textContent = noteText || titleText;
                        msg.style.cssText = 'position:relative;color:#f8fafc;font:700 13px/1.35 system-ui,sans-serif;max-width:300px;text-shadow:0 1px 4px #000;';
                        box.appendChild(msg);
                        var cta = document.createElement('button');
                        cta.type = 'button';
                        cta.textContent = 'Play in Native HD Player';
                        cta.style.cssText = 'position:relative;background:#10b981;color:#022c22;border:none;padding:10px 18px;border-radius:999px;font:700 13px/1.2 system-ui,sans-serif;cursor:pointer;box-shadow:0 4px 16px rgba(16,185,129,0.45);';
                        cta.onclick = openNative;
                        box.appendChild(cta);
                        stage.appendChild(box);
                    };

                    var loadDirect = function(url, allowLiveRetry) {
                        if (!url) {
                            if (allowLiveRetry) {
                                fetchLiveWebViewStream(function(liveUrl) {
                                    if (liveUrl) {
                                        stream = liveUrl;
                                        loadDirect(liveUrl, false);
                                    } else {
                                        loadNoCookieEmbed();
                                    }
                                });
                            } else {
                                showNativeLaunchCard(titleText);
                            }
                            return;
                        }
                        stage.innerHTML = '';
                        var v = document.createElement('video');
                        v.src = url;
                        v.poster = 'https://i.ytimg.com/vi/' + encodeURIComponent(vid) + '/hqdefault.jpg';
                        v.controls = true;
                        v.autoplay = true;
                        v.playsInline = true;
                        v.title = titleText;
                        v.style.cssText = 'width:100%;height:100%;min-height:220px;background:#000;object-fit:contain;display:block;';
                        v.onloadedmetadata = function() {
                            try { v.play(); } catch(e) {}
                        };
                        v.onerror = function() {
                            if (allowLiveRetry) {
                                loadDirect('', true);
                            } else {
                                showNativeLaunchCard('Stream switched to Hardware Player');
                            }
                        };
                        stage.appendChild(v);
                    };

                    if (resolvedIsHls) {
                        showNativeLaunchCard(titleText + ' (HD HLS Stream Ready)');
                    } else if (stream) {
                        loadDirect(stream, true);
                    } else {
                        loadDirect('', true);
                    }

                    // Top bar with Ad-Free status + Native HD / Direct MP4 / Clean Embed controls (zero mirrors)
                    var bar = document.createElement('div');
                    bar.style.cssText = 'position:absolute;top:8px;left:8px;right:8px;display:flex;flex-wrap:wrap;gap:6px;align-items:center;pointer-events:none;z-index:10;';

                    var badge = document.createElement('span');
                    badge.textContent = 'BrowserHub Ad-Free Player';
                    badge.style.cssText = 'background:rgba(6,78,59,0.92);color:#6ee7b7;border:1px solid rgba(16,185,129,0.55);padding:3px 9px;border-radius:999px;font:700 10.5px/1.2 system-ui,sans-serif;pointer-events:auto;';
                    bar.appendChild(badge);

                    var makeBtn = function(label, fn) {
                        var b = document.createElement('button');
                        b.type = 'button';
                        b.textContent = label;
                        b.style.cssText = 'background:rgba(15,23,42,0.88);color:#e2e8f0;border:1px solid rgba(148,163,184,0.35);padding:3px 8px;border-radius:999px;font:600 10px/1.2 system-ui,sans-serif;pointer-events:auto;cursor:pointer;';
                        b.onclick = fn;
                        return b;
                    };

                    bar.appendChild(makeBtn('Native HD', openNative));
                    bar.appendChild(makeBtn('Direct HD', function() { loadDirect(stream, true); }));
                    bar.appendChild(makeBtn('Clean Embed', function() { loadNoCookieEmbed(); }));
                    bar.appendChild(makeBtn('Reload Stream', function() { loadDirect('', true); }));
                    wrap.appendChild(bar);

                    if (hostContainer === document.body) {
                        document.body.insertBefore(wrap, document.body.firstChild);
                    } else {
                        hostContainer.insertBefore(wrap, hostContainer.firstChild);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
