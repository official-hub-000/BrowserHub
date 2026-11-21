package com.browserhub.app.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class AiEngineOption(
    val displayName: String,
    val badge: String
) {
    AUTO("Auto", "Fast Gemini"),
    GEMINI_FLASH_LITE("Gemini Flash Lite", "Ultra Fast"),
    DEEPSEEK_FLASH("DeepSeek V4 Flash", "Open"),
    POLLINATIONS("Pollinations", "No key"),
    DEEPINFRA("DeepInfra (open)", "Open")
}

enum class AiQuickNavigationAction(
    val tagCode: String,
    val buttonLabel: String
) {
    OPEN_VPN("VPN", "Open VPN & Location Shield"),
    OPEN_EXTENSIONS("EXTENSIONS", "Open Extensions Hub"),
    OPEN_SHIELDS("SHIELDS", "Open AdBlock Shields"),
    OPEN_MEDIA_GRABBER("MEDIA_GRABBER", "Open Media Grabber"),
    OPEN_STUDIO("STUDIO", "Open Themes & Studio"),
    OPEN_TRANSLATOR("TRANSLATOR", "Open Page Translator"),
    OPEN_TABS("TABS", "Open Tab Vault"),
    OPEN_DEVTOOLS("DEVTOOLS", "Toggle Web DevTools"),
    OPEN_GAMES("GAMES", "Open Games Hub"),
    OPEN_BENCHMARK("BENCHMARK", "Open Speed Benchmark"),
    OPEN_DIAGNOSTICS("DIAGNOSTICS", "Open About & Diagnostics"),
    OPEN_BOOKMARKS("BOOKMARKS", "Open Bookmarks"),
    OPEN_HISTORY("HISTORY", "Open History"),
    TOGGLE_READER("READER", "Toggle Reader Mode"),
    TOGGLE_ZAPPER("ZAPPER", "Toggle Element Zapper");

    companion object {
        fun fromTagCode(code: String): AiQuickNavigationAction? {
            val normalized = code.trim().uppercase(Locale.US)
            return entries.firstOrNull { it.tagCode == normalized }
        }
    }
}

data class ParsedAiMessage(
    val cleanText: String,
    val actions: List<AiQuickNavigationAction>
)

object AiActionTagParser {
    private val tagRegex = Regex("""\[\[OPEN:([A-Z0-9_]+)]]""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): ParsedAiMessage {
        if (rawText.isBlank()) return ParsedAiMessage("", emptyList())
        val foundActions = linkedSetOf<AiQuickNavigationAction>()
        tagRegex.findAll(rawText).forEach { match ->
            val code = match.groupValues.getOrNull(1).orEmpty()
            AiQuickNavigationAction.fromTagCode(code)?.let { foundActions.add(it) }
        }
        val cleaned = tagRegex.replace(rawText, "")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .trim()
        return ParsedAiMessage(
            cleanText = cleaned,
            actions = foundActions.toList()
        )
    }
}

data class BrowserLiveContextSnapshot(
    val activeTabTitle: String = "New Tab",
    val activeTabUrl: String = "",
    val isOnHubHome: Boolean = true,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isReaderMode: Boolean = false,
    val openTabsCount: Int = 1,
    val searchEngineName: String = "Google",
    val themeName: String = "Midnight Obsidian",
    val addressBarPosition: String = "Top",
    val shieldsEnabled: Boolean = true,
    val adsBlockedTotal: Int = 0,
    val trackersStoppedTotal: Int = 0,
    val autoBypassShortlinks: Boolean = true,
    val noCaptchaShield: Boolean = true,
    val vpnConnected: Boolean = false,
    val vpnProviderName: String = "Cloudflare 1.1.1.1",
    val virtualRegionLabel: String = "Zurich, Switzerland",
    val proxyExitModeLabel: String = "DoH Tunnel",
    val activeProxyEndpoint: String? = null,
    val installedExtensionCount: Int = 0,
    val installedExtensionNames: List<String> = emptyList(),
    val detectedMediaCount: Int = 0,
    val activeDownloadsCount: Int = 0
)

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val engineUsed: String = "",
    val isError: Boolean = false,
    val suggestedActions: List<AiQuickNavigationAction> = emptyList()
)

data class AiAttemptLog(
    val timestampMs: Long,
    val provider: String,
    val outcome: String,
    val detail: String,
    val latencyMs: Long
)

sealed interface AiGenerationEvent {
    data class Chunk(val accumulatedText: String, val provider: String) : AiGenerationEvent
    data class Completed(
        val finalText: String,
        val provider: String,
        val actions: List<AiQuickNavigationAction> = emptyList()
    ) : AiGenerationEvent
    data class Failed(val errorMessage: String, val provider: String) : AiGenerationEvent
}

class BrowserAiEngine {
    companion object {
        private const val TAG = "BrowserAiEngine"
        private const val GEMINI_TIMEOUT_MS = 35_000L
        private const val FIRST_TOKEN_RACE_MS = 5_000L

        /**
         * Authoritative system manual for BrowserHub injected into Gemini's systemInstruction
         * so Gemini can help users troubleshoot, discover, and launch any built-in BrowserHub capability.
         */
        val BROWSERHUB_SYSTEM_MEMORY: String = """
You are BrowserHub AI, the built-in assistant for the BrowserHub Android browser app.
You run inside BrowserHub with zero user login, zero subscription, and zero manual API key required.
You have complete memory of every feature, system function, setting, and troubleshooting tip in BrowserHub:

1. NAVIGATION, HUB HOME & DISPLAY
- Hub Home (`Home` icon in bottom bar or `Control Menu -> Home`): Quick search bar, search-engine selector chips, Quick-Access Speed Dial pins (tap `+ Pin Site` or Menu), recent history, and quick-launch shortcuts.
- Omnibox & Search Engines: Supports 7 engines (`Google`, `DuckDuckGo`, `Brave`, `Bing`, `Startpage`, `Wikipedia`, `GitHub`). Change via top-left search pill or Control Menu section 1.
- Reading & Layout Tools (`Menu -> 2. Page & Reading Tools`):
  * `Desktop Site` toggle (PC Chrome User-Agent).
  * `Reader Mode` toggle (clean distraction-free layout with 4 paper themes: Ink, Sepia, Slate, Cream and customizable serif/sans/mono fonts inside `Customization Studio`).
  * `Page Translator` (`Menu -> Translate Page`): Translates web pages into 14 languages with optional `Bilingual Side-by-Side Mode`.
  * `Find in Page`, `Page Text Zoom` (50%–200%), `Split Screen` (dual-tab side-by-side/stacked browsing), and Top/Bottom `Address Bar` placement (`Menu -> Address Bar` or `Customization Studio`).

2. TAB VAULT & PRIVACY WIPING
- `Tab Vault` (tab count icon in bottom bar): Manage standard & `Incognito` tabs, filter by workspaces (`ALL`, `WORK`, `MEDIA`, `PRIVATE`), `Pin` important tabs, `Sleep` background tabs to save RAM, or tap `Fire Wipe Data` (`Menu` or `Tab Vault`) to instantly wipe cache, cookies, WebStorage, and browsing history.

3. SHIELDS, ADBLOCK, ANTI-CAPTCHA & YOUTUBE SHIELD
- Open via shield badge in top bar or `Shields` bottom nav or `Menu -> AdBlock Shields`:
  * `AdBlocker` + `Analytics & Tracker Shield` + `Cookie Wall Killer` + `Tracking Parameter Stripper` (strips `utm_`, `fbclid`, `gclid`) + `HTTPS-Only Upgrades` + `Canvas/Hardware Spoofer`.
  * `Popup & 18+ Ad/Notification Killer`: Blocks pop-under scripts, unsolicited new windows, beforeunload traps, and adult/gambling redirect domains.
  * `No-CAPTCHA Clean Shield`: Strips WebView's `; wv`, `Version/4.0`, and `X-Requested-With` package header so sites treat BrowserHub like standard mobile Chrome without endless bot challenges.
  * `Element Zapper` (`Shields` or `Menu -> Element Zapper`): Tap any annoying overlay or banner on the page to hide it immediately.
  * `YouTube Ad-Free Shield`: Strips `adPlacements`/`playerAds`/`adSlots`, removes `enforcement-message-view-model` / sign-in bot banners, keeps `Sec-CH-UA` consistent on Google/YouTube hosts, auto-skips pre-roll ads, and shows a 1-tap `Ad-Free Player` banner above YouTube `/watch` & `/shorts` pages (using direct non-DRM InnerTube + live WebView cookie fetch + `youtube-nocookie` playback — zero mirrors, zero Google login, zero YouTube Premium needed).

4. 90+ SHORTLINK, AD-LINK, SOCIAL UNLOCK & PASTER BYPASS
- `ShortlinkBypassEngine`: Automatically (when enabled in Shields) or on-demand (1-tap `Bypass` banner or `Menu -> Bypass Link`) skips countdowns and ad walls on 90+ services including Linkvertise, Lootlabs/Lootlinks/Ad-Maven, Work.ink, Cuty.io/Cety.io/Cutty, Exe.io, ShrinkMe, Bstlar/BstShrt/BoostyLink/Boost.ink, AdFoc.us, Rekonise, Mboost, Sub2Unlock/Sub4Unlock/Sub2Get/Socialwolvez, Pastebin, Justpaste.it, Rentry, Hastebin, Telegraph, GitHub Gist, Bit.ly, TinyURL, Rebrand.ly, Is.gd, V.gd, and Google URL redirects.
- Note: Bare paster homepages (like `pastebin.com/`) stay open normally so users can browse or paste; only specific paste/shortlink item URLs trigger bypass.

5. CROSS-BROWSER EXTENSIONS HUB (`Menu -> Extensions Hub` or Hub shortcut)
- Supports direct store installation from **Chrome Web Store**, **Firefox Add-ons (AMO)**, **Microsoft Edge Add-ons**, and **Opera Addons** using automatic PC Store Mode + 1-tap `Install` store banner (and syncs `Added to BrowserHub ✓` badge directly onto the store page).
- Also supports importing local `.crx`, `.xpi`, `.nex`, or `.zip` packages, 1-tap Starter WebExtensions (including Dark Reader, Enhancer scripts, etc.), and **Extension Studio** to write custom JS/CSS userscripts (`document_start`, `document_end`, `document_idle`). Extensions can be toggled per-tab or allowed in Incognito.

6. BUILT-IN PRIVACY VPN, LOCATION SHIELD & EXIT PROXY (`Shields / Menu -> Built-in VPN`)
- Free, unlimited, no login, no subscription:
  * **On-Device Encrypted DoH VPN TUN (`10.111.0.2/32`)**: Encrypts all DNS queries via RFC 8484 DNS-over-HTTPS (`Cloudflare 1.1.1.1`, `AdGuard Privacy DNS`, or `Quad9 9.9.9.9`) and drops tracker hostnames at DNS level + WebRTC IP Leak Guard + 3rd-Party Cookie Blocker.
  * **Browser Location & Timezone Mask (`VirtualRegion`)**: Horizontal country selector (`Zurich CH`, `Frankfurt DE`, `Amsterdam NL`, `London UK`, `New York US`, `Singapore SG`, `Tokyo JP`, or `Off`). When VPN is connected, hardware GPS permission prompts are denied and `navigator.geolocation`, `Intl.DateTimeFormat`, and `Date.prototype.getTimezoneOffset` are spoofed to the chosen city.
  * **Public IP Exit Route (`ProxyExitMode`)**: Important technical honesty — local DoH VPN encrypts DNS & masks browser GPS/timezone while keeping full-speed ISP WAN IP. To actually change external public IP & country in WebView (`ProxyController`), users can choose **Orbot / Tor Exit Proxy (`127.0.0.1:8118` or `9050`)** or **Custom SOCKS5 / HTTP Exit Proxy** (`socks5://host:port` or `http://host:port`) and verify anytime with the **Check Live IP** (`ipinfo.io/json`) button inside VPN settings.

7. MEDIA GRABBER & 16-THREAD DOWNLOADER (`Menu -> Media Grabber`)
- SnapTube-style extractor: Sniffs `<video>`, `<audio>`, `<img>`, HLS `.m3u8`, MP4, MP3, plus direct YouTube non-DRM video/audio streams from the active page or any pasted URL. Includes `Rescan Page`, `Save Photos` batch saver, and 16-thread accelerated downloading (also accessible in `Customization Studio -> Downloads`).

8. BUILT-IN AI ASSISTANT & CREATIVE STUDIO (`Ask AI` center button in bottom bar)
- Four tabs inside `BrowserHub AI`:
  * **Chat**: Unlimited keyless AI powered by Gemini 3.5 Flash-Lite & Gemini 3.8 Flash (via Firebase AI Logic + App Check) with open fallback engines (`DeepSeek V4 Flash`, `Pollinations`, `DeepInfra`). Reads only the active open page when asked.
  * **Image (4K)**: Generates high-resolution images (`4K`, `2K`, `1K`, and `1:1`, `16:9`, `9:16`, `4:3`, `3:4` aspect ratios) via `Gemini 3 Pro Image`, `Gemini Nano Banana 2.1`, or free keyless `Flux Ultra`, with 1-tap save to device Pictures/Downloads.
  * **Audio**: Generates studio-quality 24kHz WAV speech/narration via `Gemini 3.1 Flash TTS` (`gemini-3.1-flash-tts-preview`, 8 voice personas, verbatim or AI-scripted) with instant playback and save to Downloads.
  * **Video**: Produces HD `.mp4` motion video reels (Gemini 3.8 Flash multi-scene storyboard + AI keyframe images + Gemini TTS voiceover + native Android `MediaCodec` H.264/MP4 encoding) with in-app preview & save to Downloads.

9. DEVELOPER, DIAGNOSTICS, BENCHMARK & GAMES
- `Web DevTools` (`Menu -> Web DevTools`): Live JS console, network logs, and JS evaluation bar.
- `Games Hub` (`Menu -> Games Hub`): 18 playable offline HTML5 mini-games.
- `Speed Benchmark` (`Menu -> Speed Benchmark`): Benchmarks rendering, JS compute, and network responsiveness.
- `About & Diagnostics` (`Menu -> About & Diagnostics`): Displays WebView engine version, AI attempt telemetry, and system self-checks.
- `Customization Studio` (`Menu -> Themes & Downloads`): 7 OLED/Studio themes, Comfortable UI density, Reader Paper/Font defaults, and Downloads queue.

HELP & ACTION RULES:
- Give concise, accurate, step-by-step help tailored to the user's LIVE BrowserHub state provided in context.
- Never invent non-existent menus or features, and never claim local DoH mode hides carrier WAN IP without Orbot/Custom Proxy.
- When helpful to open a BrowserHub screen or toggle a tool directly, append AT MOST ONE action tag at the very end of your message from this exact list:
  [[OPEN:VPN]], [[OPEN:EXTENSIONS]], [[OPEN:SHIELDS]], [[OPEN:MEDIA_GRABBER]], [[OPEN:STUDIO]], [[OPEN:TRANSLATOR]], [[OPEN:TABS]], [[OPEN:DEVTOOLS]], [[OPEN:GAMES]], [[OPEN:BENCHMARK]], [[OPEN:DIAGNOSTICS]], [[OPEN:BOOKMARKS]], [[OPEN:HISTORY]], [[OPEN:READER]], [[OPEN:ZAPPER]]
        """.trimIndent()
    }

    private val fastGeminiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content { text(BROWSERHUB_SYSTEM_MEMORY) }
        )
    }

    private val proGeminiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(BROWSERHUB_SYSTEM_MEMORY) }
        )
    }

    fun streamPrompt(
        userPrompt: String,
        pageTitle: String,
        pageUrl: String,
        pageExcerpt: String,
        liveContext: BrowserLiveContextSnapshot,
        engineOption: AiEngineOption,
        history: List<AiChatMessage>,
        onAttemptRecorded: (AiAttemptLog) -> Unit
    ): Flow<AiGenerationEvent> = channelFlow {
        val fullPrompt = buildContextualPrompt(
            userPrompt = userPrompt,
            pageTitle = pageTitle,
            pageUrl = pageUrl,
            pageExcerpt = pageExcerpt,
            liveContext = liveContext,
            history = history,
            includeSystemMemoryHeader = false
        )
        val fallbackPrompt = buildContextualPrompt(
            userPrompt = userPrompt,
            pageTitle = pageTitle,
            pageUrl = pageUrl,
            pageExcerpt = pageExcerpt,
            liveContext = liveContext,
            history = history,
            includeSystemMemoryHeader = true
        )
        val isShortChat = pageUrl.isBlank() && userPrompt.length < 180

        when (engineOption) {
            AiEngineOption.AUTO -> {
                val settled = AtomicBoolean(false)
                val firstTokenArrived = AtomicBoolean(false)
                val modelId = if (isShortChat) "gemini-3.5-flash-lite" else "gemini-3.8-flash"
                val providerLabel = if (isShortChat) "Gemini 3.5 Flash-Lite" else "Gemini 3.8 Flash"

                coroutineScope {
                    var fallbackJob: kotlinx.coroutines.Job? = null
                    val geminiJob = launch(Dispatchers.IO) {
                        val startedAt = System.currentTimeMillis()
                        val acc = StringBuilder()
                        try {
                            val activeModel = if (isShortChat) fastGeminiModel else proGeminiModel
                            withTimeout(GEMINI_TIMEOUT_MS) {
                                activeModel.generateContentStream(content { text(fullPrompt) })
                                    .collect { chunk ->
                                        val piece = chunk.text.orEmpty()
                                        if (piece.isNotEmpty() && !settled.get()) {
                                            if (firstTokenArrived.compareAndSet(false, true)) {
                                                fallbackJob?.cancel()
                                            }
                                            acc.append(piece)
                                            val parsedChunk = AiActionTagParser.parse(acc.toString())
                                            trySend(AiGenerationEvent.Chunk(parsedChunk.cleanText, providerLabel))
                                        }
                                    }
                            }
                            val latency = System.currentTimeMillis() - startedAt
                            val parsedFinal = AiActionTagParser.parse(acc.toString())
                            if (parsedFinal.cleanText.isNotEmpty() && settled.compareAndSet(false, true)) {
                                fallbackJob?.cancel()
                                onAttemptRecorded(
                                    AiAttemptLog(System.currentTimeMillis(), "Firebase AI ($modelId)", "OK", "${parsedFinal.cleanText.length} chars", latency)
                                )
                                trySend(
                                    AiGenerationEvent.Completed(
                                        finalText = parsedFinal.cleanText,
                                        provider = providerLabel,
                                        actions = parsedFinal.actions
                                    )
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val latency = System.currentTimeMillis() - startedAt
                            Log.w(TAG, "Gemini $modelId failed (${latency}ms), handing off to fast fallback", e)
                            onAttemptRecorded(
                                AiAttemptLog(
                                    System.currentTimeMillis(),
                                    "Firebase AI ($modelId)",
                                    if (e is TimeoutCancellationException) "TIMEOUT" else "FALLBACK",
                                    e.localizedMessage ?: "Fallback triggered",
                                    latency
                                )
                            )
                            if (!firstTokenArrived.get() && !settled.get() && fallbackJob == null) {
                                fallbackJob = launch(Dispatchers.IO) {
                                    runFastFallbackIntoChannel(
                                        prompt = fallbackPrompt,
                                        modelName = "openai-fast",
                                        displayName = "Fast Cloud AI",
                                        settled = settled,
                                        onAttemptRecorded = onAttemptRecorded,
                                        sendEvent = { ev -> trySend(ev) }
                                    )
                                }
                            }
                        }
                    }

                    val watchdogJob = launch(Dispatchers.IO) {
                        delay(FIRST_TOKEN_RACE_MS)
                        if (!firstTokenArrived.get() && !settled.get() && fallbackJob == null) {
                            fallbackJob = launch(Dispatchers.IO) {
                                runFastFallbackIntoChannel(
                                    prompt = fallbackPrompt,
                                    modelName = "openai-fast",
                                    displayName = "Fast Cloud AI",
                                    settled = settled,
                                    onAttemptRecorded = onAttemptRecorded,
                                    sendEvent = { ev -> trySend(ev) },
                                    onWinner = { geminiJob.cancel() }
                                )
                            }
                        }
                    }

                    geminiJob.join()
                    watchdogJob.cancel()
                    fallbackJob?.join()
                    if (!settled.get()) {
                        runFastFallbackIntoChannel(
                            prompt = fallbackPrompt,
                            modelName = "openai",
                            displayName = "Fast Cloud AI",
                            settled = settled,
                            onAttemptRecorded = onAttemptRecorded,
                            sendEvent = { ev -> trySend(ev) },
                            emitFailureOnError = true
                        )
                    }
                }
            }

            AiEngineOption.GEMINI_FLASH_LITE -> {
                val startedAt = System.currentTimeMillis()
                val acc = StringBuilder()
                try {
                    withTimeout(GEMINI_TIMEOUT_MS) {
                        fastGeminiModel.generateContentStream(content { text(fullPrompt) })
                            .collect { chunk ->
                                val piece = chunk.text.orEmpty()
                                if (piece.isNotEmpty()) {
                                    acc.append(piece)
                                    val parsedChunk = AiActionTagParser.parse(acc.toString())
                                    send(AiGenerationEvent.Chunk(parsedChunk.cleanText, "Gemini 3.5 Flash-Lite"))
                                }
                            }
                    }
                    val parsedFinal = AiActionTagParser.parse(acc.toString())
                    val latency = System.currentTimeMillis() - startedAt
                    if (parsedFinal.cleanText.isNotEmpty()) {
                        onAttemptRecorded(AiAttemptLog(System.currentTimeMillis(), "Firebase AI (gemini-3.5-flash-lite)", "OK", "${parsedFinal.cleanText.length} chars", latency))
                        send(AiGenerationEvent.Completed(parsedFinal.cleanText, "Gemini 3.5 Flash-Lite", parsedFinal.actions))
                    } else {
                        val fallback = runPollinationsPostOrGet(fallbackPrompt, "openai-fast", onAttemptRecorded)
                        if (fallback.isSuccess) {
                            val parsed = AiActionTagParser.parse(fallback.getOrThrow())
                            send(AiGenerationEvent.Completed(parsed.cleanText, "Fast Cloud AI", parsed.actions))
                        } else {
                            send(AiGenerationEvent.Failed("Empty response from AI.", "Gemini 3.5 Flash-Lite"))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onAttemptRecorded(
                        AiAttemptLog(System.currentTimeMillis(), "Firebase AI (gemini-3.5-flash-lite)", "FALLBACK", e.localizedMessage ?: "Error", System.currentTimeMillis() - startedAt)
                    )
                    val fallback = runPollinationsPostOrGet(fallbackPrompt, "openai-fast", onAttemptRecorded)
                    if (fallback.isSuccess) {
                        val parsed = AiActionTagParser.parse(fallback.getOrThrow())
                        send(AiGenerationEvent.Completed(parsed.cleanText, "Fast Cloud AI", parsed.actions))
                    } else {
                        send(AiGenerationEvent.Failed(fallback.exceptionOrNull()?.message ?: e.localizedMessage ?: "AI failed", "Gemini Flash Lite"))
                    }
                }
            }

            AiEngineOption.POLLINATIONS -> {
                val res = runPollinationsPostOrGet(fallbackPrompt, "openai-fast", onAttemptRecorded)
                if (res.isSuccess) {
                    val parsed = AiActionTagParser.parse(res.getOrThrow())
                    send(AiGenerationEvent.Completed(parsed.cleanText, "Pollinations", parsed.actions))
                } else {
                    send(AiGenerationEvent.Failed(res.exceptionOrNull()?.message ?: "Pollinations request failed.", "Pollinations"))
                }
            }

            AiEngineOption.DEEPSEEK_FLASH -> {
                val res = runPollinationsPostOrGet(fallbackPrompt, "deepseek", onAttemptRecorded)
                if (res.isSuccess) {
                    val parsed = AiActionTagParser.parse(res.getOrThrow())
                    send(AiGenerationEvent.Completed(parsed.cleanText, "DeepSeek V4 Flash", parsed.actions))
                } else {
                    val alt = runPollinationsPostOrGet(fallbackPrompt, "openai-fast", onAttemptRecorded)
                    if (alt.isSuccess) {
                        val parsed = AiActionTagParser.parse(alt.getOrThrow())
                        send(AiGenerationEvent.Completed(parsed.cleanText, "Fast Cloud AI", parsed.actions))
                    } else {
                        send(AiGenerationEvent.Failed(res.exceptionOrNull()?.message ?: "DeepSeek unavailable", "DeepSeek V4 Flash"))
                    }
                }
            }

            AiEngineOption.DEEPINFRA -> {
                val res = runPollinationsPostOrGet(fallbackPrompt, "llama", onAttemptRecorded)
                if (res.isSuccess) {
                    val parsed = AiActionTagParser.parse(res.getOrThrow())
                    send(AiGenerationEvent.Completed(parsed.cleanText, "DeepInfra (open)", parsed.actions))
                } else {
                    send(AiGenerationEvent.Failed(res.exceptionOrNull()?.message ?: "DeepInfra open endpoint failed.", "DeepInfra"))
                }
            }
        }
    }

    private suspend fun runFastFallbackIntoChannel(
        prompt: String,
        modelName: String,
        displayName: String,
        settled: AtomicBoolean,
        onAttemptRecorded: (AiAttemptLog) -> Unit,
        sendEvent: (AiGenerationEvent) -> Unit,
        onWinner: () -> Unit = {},
        emitFailureOnError: Boolean = false
    ) {
        val res = runPollinationsPostOrGet(prompt, modelName, onAttemptRecorded)
        if (res.isSuccess) {
            if (settled.compareAndSet(false, true)) {
                onWinner()
                val parsed = AiActionTagParser.parse(res.getOrThrow())
                sendEvent(AiGenerationEvent.Completed(parsed.cleanText, displayName, parsed.actions))
            }
        } else if (emitFailureOnError && settled.compareAndSet(false, true)) {
            sendEvent(
                AiGenerationEvent.Failed(
                    res.exceptionOrNull()?.message ?: "Unable to reach AI service. Check network connection.",
                    displayName
                )
            )
        }
    }

    suspend fun translateBatch(
        texts: List<String>,
        targetLanguageName: String
    ): List<String>? {
        if (texts.isEmpty()) return emptyList()
        return try {
            val numbered = texts.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")
            val prompt = """
                Translate each numbered line below into $targetLanguageName.
                Return ONLY the translated lines in the exact same numbered order (e.g. "1. ..."), one per line, with no extra commentary.

                $numbered
            """.trimIndent()
            val response = withTimeout(GEMINI_TIMEOUT_MS) {
                fastGeminiModel.generateContent(prompt)
            }
            val raw = response.text ?: return null
            val lines = raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line -> line.replaceFirst(Regex("^\\d+\\.\\s*"), "") }
            if (lines.size >= texts.size) lines.take(texts.size) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini translation fallback failed", e)
            null
        }
    }

    private suspend fun runPollinationsPostOrGet(
        prompt: String,
        modelName: String,
        onAttemptRecorded: (AiAttemptLog) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            val payload = JSONObject().apply {
                put("model", modelName)
                put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject().apply {
                                put("role", "system")
                                put("content", BROWSERHUB_SYSTEM_MEMORY.take(3600))
                            }
                        )
                        .put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", prompt.take(4800))
                            }
                        )
                )
                put("private", true)
            }.toString()

            val postConn = (URL("https://text.pollinations.ai/openai").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                postConn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                if (postConn.responseCode in 200..299) {
                    val raw = postConn.inputStream.bufferedReader().readText()
                    val text = extractOpenAiChoiceText(raw)
                    if (!text.isNullOrBlank()) {
                        val latency = System.currentTimeMillis() - startedAt
                        onAttemptRecorded(AiAttemptLog(System.currentTimeMillis(), "Fast AI ($modelName)", "OK", "${text.length} chars", latency))
                        return@withContext Result.success(text)
                    }
                }
            } finally {
                postConn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall through cleanly to simple GET
        }

        try {
            val encodedPrompt = URLEncoder.encode(prompt.take(3000), Charsets.UTF_8.name())
            val url = URL("https://text.pollinations.ai/$encodedPrompt?model=$modelName")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 15_000
                setRequestProperty("Accept", "text/plain")
            }
            try {
                val code = conn.responseCode
                val body = if (code in 200..299) {
                    conn.inputStream.bufferedReader().readText().trim()
                } else {
                    throw IllegalStateException("HTTP $code")
                }
                val latency = System.currentTimeMillis() - startedAt
                if (body.isBlank()) throw IllegalStateException("Empty response")
                onAttemptRecorded(AiAttemptLog(System.currentTimeMillis(), "Pollinations ($modelName)", "OK", "${body.length} chars", latency))
                Result.success(body)
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startedAt
            onAttemptRecorded(
                AiAttemptLog(System.currentTimeMillis(), "Pollinations ($modelName)", "ERROR", e.localizedMessage ?: "Network failed", latency)
            )
            Result.failure(e)
        }
    }

    private fun extractOpenAiChoiceText(rawJson: String): String? {
        return try {
            val obj = JSONObject(rawJson)
            val choices = obj.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val msg = choices.getJSONObject(0).optJSONObject("message") ?: return null
            msg.optString("content").trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            rawJson.trim().takeIf { !it.startsWith("{") && it.isNotEmpty() }
        }
    }

    private fun buildContextualPrompt(
        userPrompt: String,
        pageTitle: String,
        pageUrl: String,
        pageExcerpt: String,
        liveContext: BrowserLiveContextSnapshot,
        history: List<AiChatMessage>,
        includeSystemMemoryHeader: Boolean
    ): String {
        val sb = StringBuilder()
        if (includeSystemMemoryHeader) {
            sb.appendLine("You are BrowserHub AI, built into the BrowserHub Android browser.")
        }
        sb.appendLine("[Live BrowserHub State]")
        sb.appendLine(
            "Tabs: ${liveContext.openTabsCount} | Incognito: ${liveContext.isIncognito} | Search: ${liveContext.searchEngineName} | Theme: ${liveContext.themeName} | Bar: ${liveContext.addressBarPosition}"
        )
        sb.appendLine(
            "Shields: ${if (liveContext.shieldsEnabled) "UP" else "DOWN"} (Blocked ${liveContext.adsBlockedTotal} ads, ${liveContext.trackersStoppedTotal} trackers) | No-Captcha: ${liveContext.noCaptchaShield} | Auto-Bypass: ${liveContext.autoBypassShortlinks}"
        )
        sb.appendLine(
            "VPN: ${if (liveContext.vpnConnected) "CONNECTED (${liveContext.vpnProviderName})" else "DISCONNECTED"} | Location Mask: ${liveContext.virtualRegionLabel} | Exit Route: ${liveContext.proxyExitModeLabel}${liveContext.activeProxyEndpoint?.let { " ($it)" } ?: ""}"
        )
        sb.appendLine(
            "Extensions Installed (${liveContext.installedExtensionCount}): ${if (liveContext.installedExtensionNames.isEmpty()) "None" else liveContext.installedExtensionNames.take(8).joinToString(", ")}"
        )
        sb.appendLine(
            "Active Page: ${if (liveContext.isOnHubHome || pageUrl.isBlank()) "Hub Home" else "$pageTitle ($pageUrl)"} | Desktop: ${liveContext.isDesktopMode} | Reader: ${liveContext.isReaderMode} | Sniffed Media: ${liveContext.detectedMediaCount} | Downloads: ${liveContext.activeDownloadsCount}"
        )

        if (!liveContext.isOnHubHome && pageUrl.isNotBlank() && !pageUrl.startsWith("browserhub://") && pageExcerpt.isNotBlank()) {
            sb.appendLine("Visible Page Text:")
            sb.appendLine(pageExcerpt.take(3200))
        }

        val recent = history.takeLast(6)
        if (recent.isNotEmpty()) {
            sb.appendLine("Recent conversation:")
            recent.forEach { msg ->
                sb.appendLine("${if (msg.isUser) "User" else "Assistant"}: ${msg.text.take(400)}")
            }
        }
        sb.appendLine("User: $userPrompt")
        return sb.toString()
    }
}
