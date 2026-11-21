package com.browserhub.app.privacy

import java.net.URI
import java.util.Locale

enum class ShieldBlockKind {
    NONE,
    AD,
    TRACKER
}

data class ShieldDecision(
    val kind: ShieldBlockKind,
    val estimatedKbSaved: Int
)

object ShieldsEngine {
    /**
     * Fallback Clean Android Mobile Chrome User-Agent without WebView's "; wv)" token.
     * Prefer deriveCleanMobileChromeUa(WebSettings.getDefaultUserAgent(context)) at runtime
     * so Chromium version matches the device's real V8/Blink/TLS stack cleanly.
     */
    const val CLEAN_MOBILE_CHROME_UA: String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"

    fun deriveCleanMobileChromeUa(systemDefaultUa: String?): String {
        val raw = systemDefaultUa?.trim().orEmpty()
        if (raw.isEmpty()) return CLEAN_MOBILE_CHROME_UA
        return raw
            .replace("; wv)", ")")
            .replace("; wv;", ";")
            .replace("Version/4.0 ", "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private val captchaAllowHosts = setOf(
        "challenges.cloudflare.com",
        "turnstile.cloudflare.com",
        "hcaptcha.com",
        "newassets.hcaptcha.com",
        "recaptcha.net",
        "www.recaptcha.net"
    )

    private val trustedMediaAndAuthHosts = setOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "googlevideo.com",
        "google.com",
        "accounts.google.com",
        "gstatic.com",
        "ggpht.com",
        "ytimg.com"
    ) + captchaAllowHosts

    fun isTrustedGoogleOrCaptchaHost(url: String): Boolean {
        val host = extractNormalizedHost(url)
        if (host.isEmpty()) return false
        return matchesHostSet(host, trustedMediaAndAuthHosts)
    }

    /**
     * Prevents Chromium WebView User-Agent vs Sec-CH-UA header mismatch on Google/YouTube/CAPTCHA
     * hosts (returning null keeps stock system WebView UA aligned with Sec-CH-UA: "Android WebView"),
     * while stripping "; wv" cleanly on general websites.
     */
    fun resolveEffectiveUserAgent(
        url: String,
        isDesktopMode: Boolean,
        noCaptchaShield: Boolean,
        shieldsEnabled: Boolean,
        systemDefaultUa: String?,
        storeDesktopUa: String?,
        desktopUserAgent: String
    ): String? {
        if (!storeDesktopUa.isNullOrBlank()) return storeDesktopUa
        if (isDesktopMode) return desktopUserAgent
        if (isTrustedGoogleOrCaptchaHost(url)) return null
        return if (shieldsEnabled && noCaptchaShield) {
            deriveCleanMobileChromeUa(systemDefaultUa)
        } else null
    }

    fun isYouTubeHost(url: String): Boolean {
        val host = extractNormalizedHost(url)
        if (host.isEmpty()) return false
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host == "youtube-nocookie.com"
    }

    fun isYouTubeAdRequest(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        val host = extractNormalizedHost(url)
        if (host.contains("youtube.com")) {
            return lower.contains("/pagead/") ||
                lower.contains("/api/stats/ads") ||
                lower.contains("/ptracking") ||
                lower.contains("/get_midroll_") ||
                lower.contains("/youtubei/v1/player/ad_break") ||
                lower.contains("/pcs/activeview") ||
                lower.contains("ad_type=") ||
                lower.contains("&oad=")
        }
        if (host.contains("googlevideo.com") && lower.contains("/videoplayback")) {
            return lower.contains("&oad=") ||
                lower.contains("adformat=") ||
                lower.contains("source=yt_ad")
        }
        return false
    }

    private val adHosts = setOf(
        // Mainstream ad networks
        "doubleclick.net",
        "g.doubleclick.net",
        "googleads.g.doubleclick.net",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "adservice.google.com",
        "amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "taboola.com",
        "trc.taboola.com",
        "outbrain.com",
        "widgets.outbrain.com",
        "criteo.com",
        "static.criteo.net",
        "adnxs.com",
        "ib.adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "ads.yahoo.com",
        "adroll.com",
        "mediavine.com",
        "moatads.com",
        "revcontent.com",
        "mgid.com",
        "adsafeprotected.com",
        "2mdn.net",
        "an.facebook.com",
        // Aggressive popunder, redirect, 18+ / streaming ad & push spam networks
        "popads.net",
        "popcash.net",
        "popunder.net",
        "propellerads.com",
        "propellerclick.com",
        "adsterra.com",
        "effectivegatecpm.com",
        "profitablegatecpm.com",
        "highperformanceformat.com",
        "exoclick.com",
        "syndication.exoclick.com",
        "tsyndicate.com",
        "realsrv.com",
        "magsrv.com",
        "juicyads.com",
        "trafficjunky.com",
        "trafficjunky.net",
        "plugrush.com",
        "ero-advertising.com",
        "trafficfactory.biz",
        "hilltopads.net",
        "clickadu.com",
        "galaksion.com",
        "monetag.com",
        "admaven.com",
        "adskeeper.com",
        "adsco.re",
        "a-ads.com",
        "evadav.com",
        "richpush.co",
        "notix.io",
        "propush.me",
        "onesignal.com",
        "cdn.onesignal.com",
        "pushwoosh.com",
        "izooto.com",
        "gravitec.net",
        "pushcrew.com",
        "subscribers.com"
    )

    private val trackerHosts = setOf(
        "google-analytics.com",
        "www.google-analytics.com",
        "ssl.google-analytics.com",
        "analytics.google.com",
        "googletagmanager.com",
        "www.googletagmanager.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "analytics.twitter.com",
        "ads-api.twitter.com",
        "analytics.tiktok.com",
        "bat.bing.com",
        "scorecardresearch.com",
        "sb.scorecardresearch.com",
        "quantserve.com",
        "pixel.quantserve.com",
        "demdex.net",
        "omtrdc.net",
        "hotjar.com",
        "static.hotjar.com",
        "mixpanel.com",
        "api.mixpanel.com",
        "segment.io",
        "api.segment.io",
        "cdn.segment.com",
        "amplitude.com",
        "api.amplitude.com",
        "branch.io",
        "app.link",
        "appsflyer.com",
        "adjust.com",
        "sentry.io",
        "browser-intake-datadoghq.com",
        "clarity.ms",
        "fullstory.com",
        "heapanalytics.com",
        "nr-data.net"
    )

    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_name", "utm_reader", "utm_viz_id", "utm_pubreferrer",
        "fbclid", "gclid", "gbraid", "wbraid", "dclid", "msclkid",
        "mc_cid", "mc_eid", "igshid", "_hsenc", "_hsmi", "hsctatracking",
        "yclid", "wickedid", "oly_anon_id", "oly_enc_id", "rb_clickid",
        "s_cid", "vero_id", "nr_email_referer", "si", "ref_src", "ref_url"
    )

    fun evaluateRequest(
        url: String,
        nativeAdBlocker: Boolean,
        analyticsTrackerShield: Boolean,
        popupNotificationBlocker: Boolean = true
    ): ShieldDecision {
        val host = extractNormalizedHost(url)
        if (host.isEmpty()) return ShieldDecision(ShieldBlockKind.NONE, 0)

        // Never block CAPTCHA/challenge infrastructure so verification works cleanly
        if (matchesHostSet(host, captchaAllowHosts)) {
            return ShieldDecision(ShieldBlockKind.NONE, 0)
        }

        if (nativeAdBlocker && (isYouTubeAdRequest(url) || matchesHostSet(host, adHosts))) {
            return ShieldDecision(ShieldBlockKind.AD, 28)
        }
        if (popupNotificationBlocker && isSuspiciousAdOrPopupUrl(url)) {
            return ShieldDecision(ShieldBlockKind.AD, 22)
        }
        if (analyticsTrackerShield && matchesHostSet(host, trackerHosts)) {
            return ShieldDecision(ShieldBlockKind.TRACKER, 12)
        }
        return ShieldDecision(ShieldBlockKind.NONE, 0)
    }

    fun isBlockedAdRedirect(url: String): Boolean {
        val host = extractNormalizedHost(url)
        if (host.isEmpty()) return false
        return matchesHostSet(host, adHosts)
    }

    private fun isSuspiciousAdOrPopupUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("/popunder") ||
            lower.contains("/pop.js") ||
            lower.contains("/pu.js") ||
            lower.contains("/inpagepush") ||
            lower.contains("vast.xml") ||
            lower.contains("/pagead/aclk") ||
            lower.contains("/ads/vast")
    }

    private fun extractNormalizedHost(url: String): String {
        return try {
            URI(url).host.orEmpty().trim().trimEnd('.').lowercase(Locale.US)
        } catch (_: Exception) {
            ""
        }
    }

    private fun matchesHostSet(host: String, set: Set<String>): Boolean {
        if (host in set) return true
        return set.any { blocked -> host.endsWith(".$blocked") }
    }

    fun sanitizeNavigationUrl(
        rawUrl: String,
        stripTrackingParameters: Boolean,
        upgradeHttps: Boolean
    ): String {
        var result = rawUrl.trim()
        if (result.isEmpty()) return result
        if (upgradeHttps) {
            result = upgradeToHttps(result)
        }
        if (stripTrackingParameters) {
            result = stripTrackingParams(result)
        }
        return result
    }

    fun upgradeToHttps(rawUrl: String): String {
        if (!rawUrl.startsWith("http://", ignoreCase = true)) return rawUrl
        return try {
            val uri = URI(rawUrl)
            val host = uri.host.orEmpty().lowercase(Locale.US)
            if (host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.")) {
                rawUrl
            } else {
                "https://" + rawUrl.substring(7)
            }
        } catch (_: Exception) {
            rawUrl
        }
    }

    fun stripTrackingParams(rawUrl: String): String {
        if (!rawUrl.contains("?")) return rawUrl
        return try {
            val idx = rawUrl.indexOf('?')
            val hashIdx = rawUrl.indexOf('#', startIndex = idx)
            val base = rawUrl.substring(0, idx)
            val query = if (hashIdx >= 0) rawUrl.substring(idx + 1, hashIdx) else rawUrl.substring(idx + 1)
            val fragment = if (hashIdx >= 0) rawUrl.substring(hashIdx) else ""
            if (query.isBlank()) return rawUrl

            val kept = query.split('&').filter { pair ->
                val key = pair.substringBefore('=').lowercase(Locale.US)
                key.isNotEmpty() && key !in trackingParams && !key.startsWith("utm_")
            }
            if (kept.isEmpty()) {
                base + fragment
            } else {
                "$base?${kept.joinToString("&")}$fragment"
            }
        } catch (_: Exception) {
            rawUrl
        }
    }

    const val COOKIE_WALL_KILLER_JS: String = """
        (function() {
            var selectors = [
                '[id*="onetrust" i]', '[class*="onetrust" i]',
                '[id*="cookie" i][class*="banner" i]', '[id*="cookie" i][class*="consent" i]',
                '[class*="cookie-banner" i]', '[class*="cookie-consent" i]',
                '[id*="CybotCookiebot" i]', '#didomi-host', '.fc-consent-root',
                '[aria-label*="cookie" i][role="dialog"]'
            ];
            var hidden = 0;
            selectors.forEach(function(sel) {
                try {
                    document.querySelectorAll(sel).forEach(function(el) {
                        var st = window.getComputedStyle(el);
                        if (st && (st.position === 'fixed' || st.position === 'sticky' || el.getAttribute('role') === 'dialog')) {
                            el.style.setProperty('display', 'none', 'important');
                            hidden++;
                        }
                    });
                } catch(e) {}
            });
            if (document.body && window.getComputedStyle(document.body).overflow === 'hidden') {
                document.body.style.overflow = 'auto';
            }
            if (document.documentElement && window.getComputedStyle(document.documentElement).overflow === 'hidden') {
                document.documentElement.style.overflow = 'auto';
            }
            return hidden;
        })();
    """

    /**
     * Neutralizes WebView automation flags cleanly without injecting mismatched desktop
     * plugins on mobile User-Agents (which would trigger Google/YouTube BotGuard).
     */
    const val NO_CAPTCHA_SHIELD_JS: String = """
        (function() {
            if (window.__bhNoCaptchaApplied) return;
            window.__bhNoCaptchaApplied = true;
            try {
                if (navigator.webdriver) {
                    Object.defineProperty(Navigator.prototype, 'webdriver', {
                        get: function() { return false; },
                        configurable: true
                    });
                }
            } catch(e) {}
        })();
    """

    /**
     * Strips YouTube adPlacements/playerAds/adSlots from player payloads, hides sponsored
     * feed/banner/masthead cards & "Sign in to confirm you're not a bot" cards, extracts
     * videoId even on SPA/m.youtube.com cards, and triggers BrowserHub's Ad-Free Player.
     */
    const val YOUTUBE_AD_AND_BOT_SHIELD_JS: String = """
        (function() {
            var extractDomVideoId = function(fallbackId) {
                try {
                    if (fallbackId && /^[A-Za-z0-9_-]{8,16}$/.test(fallbackId)) {
                        return fallbackId;
                    }
                    if (/[?&]v=([A-Za-z0-9_-]{8,16})/.test(location.search)) {
                        return RegExp.$1;
                    }
                    if (/\/(shorts|embed|live)\/([A-Za-z0-9_-]{8,16})/.test(location.pathname)) {
                        return RegExp.$2;
                    }
                    if (window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.videoDetails && window.ytInitialPlayerResponse.videoDetails.videoId) {
                        return window.ytInitialPlayerResponse.videoDetails.videoId;
                    }
                    var canon = document.querySelector('link[rel="canonical"]');
                    if (canon && canon.href && /[?&]v=([A-Za-z0-9_-]{8,16})/.test(canon.href)) {
                        return RegExp.$1;
                    }
                    var vidAttrEl = document.querySelector('[video-id], [data-video-id]');
                    if (vidAttrEl) {
                        var vAttr = vidAttrEl.getAttribute('video-id') || vidAttrEl.getAttribute('data-video-id');
                        if (vAttr && /^[A-Za-z0-9_-]{8,16}$/.test(vAttr)) return vAttr;
                    }
                } catch(e) {}
                return '';
            };

            var notifyBotWall = function(reasonTag, hintVideoId) {
                try {
                    var vid = extractDomVideoId(hintVideoId);
                    var key = (location.href || '') + '|' + vid;
                    if (window.__bhLastBotKey === key) return;
                    window.__bhLastBotKey = key;
                    var targetUrl = vid ? ('https://m.youtube.com/watch?v=' + vid) : (location.href || 'https://m.youtube.com/');
                    if (window.BrowserHubExtBridge && window.BrowserHubExtBridge.onYouTubeBotWallDetected) {
                        window.BrowserHubExtBridge.onYouTubeBotWallDetected(targetUrl);
                    }
                } catch(e) {}
            };

            var normalizePhraseText = function(txt) {
                return (txt || '')
                    .replace(/[\u2018\u2019]/g, "'")
                    .replace(/\s+/g, ' ')
                    .toLowerCase();
            };

            var isBotPhrase = function(txt) {
                var lower = normalizePhraseText(txt);
                return lower.indexOf("not a bot") !== -1 ||
                    lower.indexOf("confirm you're not a bot") !== -1 ||
                    lower.indexOf("protect our community") !== -1 ||
                    lower.indexOf("sign in to confirm") !== -1 ||
                    lower.indexOf("bot_guard") !== -1;
            };

            if (!window.__bhYtAdShieldInstalled) {
                window.__bhYtAdShieldInstalled = true;
                try {
                    var style = document.createElement('style');
                    style.id = 'bh-yt-ad-hide-css';
                    style.textContent = [
                        'ytd-ad-slot-renderer, ytd-banner-promo-renderer, ytd-display-ad-renderer,',
                        'ytd-promoted-sparkles-web-renderer, ytd-action-companion-ad-renderer,',
                        'ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer,',
                        'ytm-ad-slot-renderer, #masthead-ad, #player-ads,',
                        '.ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-image-overlay,',
                        '.ytp-ad-text-overlay, ad-slot-renderer,',
                        'enforcement-message-view-model, ytm-enforcement-message-view-model,',
                        'yt-enforcement-message-view-model, ytd-enforcement-message-view-model,',
                        'ytm-feed-nudge-renderer, yt-feed-nudge-renderer,',
                        'ytm-player-error-message-renderer, yt-player-error-message-renderer,',
                        'ytd-player-error-message-renderer, ytm-playability-error-supported-renderers,',
                        'yt-playability-error-supported-renderers,',
                        'ytm-mealbar-promo-renderer, ytm-upsell-dialog-renderer, yt-confirm-dialog-renderer,',
                        '.ytp-error, #error-screen {',
                        '  display: none !important;',
                        '  height: 0 !important;',
                        '  min-height: 0 !important;',
                        '  margin: 0 !important;',
                        '  padding: 0 !important;',
                        '  visibility: hidden !important;',
                        '}'
                    ].join(' ');
                    (document.head || document.documentElement).appendChild(style);
                } catch(e) {}

                var scrubAdsAndCheckBot = function(obj) {
                    if (!obj || typeof obj !== 'object') return obj;
                    try {
                        delete obj.adPlacements;
                        delete obj.playerAds;
                        delete obj.adSlots;
                        delete obj.adBreakHeartbeatParams;
                        if (obj.playerResponse && typeof obj.playerResponse === 'object') {
                            delete obj.playerResponse.adPlacements;
                            delete obj.playerResponse.playerAds;
                            delete obj.playerResponse.adSlots;
                        }
                        var ps = obj.playabilityStatus || (obj.playerResponse && obj.playerResponse.playabilityStatus);
                        var vd = obj.videoDetails || (obj.playerResponse && obj.playerResponse.videoDetails);
                        var statusStr = ps && ps.status ? String(ps.status) : '';
                        var reasonStr = ps ? JSON.stringify(ps) : '';
                        if (statusStr === 'LOGIN_REQUIRED' || isBotPhrase(reasonStr)) {
                            var hintId = (vd && vd.videoId) ? String(vd.videoId) : '';
                            setTimeout(function() { notifyBotWall('json_login_required', hintId); }, 40);
                        }
                    } catch(e) {}
                    return obj;
                };

                try {
                    if (window.ytInitialPlayerResponse) scrubAdsAndCheckBot(window.ytInitialPlayerResponse);
                    var origParse = JSON.parse;
                    JSON.parse = function(text, reviver) {
                        var res = origParse.apply(this, arguments);
                        if (res && typeof res === 'object' && (res.adPlacements || res.playerAds || res.adSlots || res.playabilityStatus || res.playerResponse)) {
                            scrubAdsAndCheckBot(res);
                        }
                        return res;
                    };
                } catch(e) {}

                try {
                    var origJson = Response.prototype.json;
                    Response.prototype.json = function() {
                        return origJson.apply(this, arguments).then(function(data) {
                            return scrubAdsAndCheckBot(data);
                        });
                    };
                } catch(e) {}

                try {
                    if (typeof window.fetch === 'function') {
                        var origFetch = window.fetch;
                        window.fetch = function(input) {
                            var reqUrl = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                            return origFetch.apply(this, arguments).then(function(resp) {
                                try {
                                    if (resp && resp.ok && /youtubei\/v1\/(player|next|browse|reel)/i.test(reqUrl)) {
                                        resp.clone().json().then(function(payload) {
                                            scrubAdsAndCheckBot(payload);
                                        }).catch(function() {});
                                    }
                                } catch(e) {}
                                return resp;
                            });
                        };
                    }
                } catch(e) {}

                try {
                    var origXhrSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.send = function() {
                        try {
                            this.addEventListener('load', function() {
                                try {
                                    if (this.responseType === '' || this.responseType === 'text') {
                                        var txt = this.responseText || '';
                                        if (txt.indexOf('LOGIN_REQUIRED') !== -1 || txt.indexOf('adPlacements') !== -1) {
                                            scrubAdsAndCheckBot(JSON.parse(txt));
                                        }
                                    }
                                } catch(e) {}
                            });
                        } catch(e) {}
                        return origXhrSend.apply(this, arguments);
                    };
                } catch(e) {}
            }

            var hideCompactBotElement = function(el) {
                try {
                    if (!el || el.id === 'bh-yt-bypass-player' || el.closest && el.closest('#bh-yt-bypass-player')) return;
                    var wrapper = el.closest && el.closest(
                        'enforcement-message-view-model, ytm-enforcement-message-view-model, yt-enforcement-message-view-model, ' +
                        'ytd-enforcement-message-view-model, ytm-feed-nudge-renderer, yt-feed-nudge-renderer, ' +
                        'ytm-rich-section-renderer, ytd-rich-section-renderer, ' +
                        'ytm-player-error-message-renderer, yt-player-error-message-renderer, ytd-player-error-message-renderer, ' +
                        'ytm-playability-error-supported-renderers, tp-yt-paper-dialog, yt-confirm-dialog-renderer'
                    );
                    var target = wrapper || el;
                    if (target.querySelector && target.querySelector('ytm-video-with-context-renderer, ytm-rich-item-renderer, ytd-rich-item-renderer, ytd-video-renderer, #bh-yt-bypass-player')) {
                        target = el;
                    }
                    target.style.setProperty('display', 'none', 'important');
                    target.style.setProperty('height', '0', 'important');
                    target.style.setProperty('min-height', '0', 'important');
                    target.style.setProperty('overflow', 'hidden', 'important');
                    target.style.setProperty('visibility', 'hidden', 'important');
                } catch(e) {}
            };

            var checkBotAndAds = function() {
                try {
                    if (window.ytInitialPlayerResponse) {
                        delete window.ytInitialPlayerResponse.adPlacements;
                        delete window.ytInitialPlayerResponse.playerAds;
                        delete window.ytInitialPlayerResponse.adSlots;
                    }
                    if (window.__bhLastBotHref && window.__bhLastBotHref !== location.href) {
                        window.__bhLastBotKey = '';
                    }
                    window.__bhLastBotHref = location.href;

                    // 1. Hide explicit known bot-gate & enforcement renderers
                    var botContainers = document.querySelectorAll(
                        'enforcement-message-view-model, ytm-enforcement-message-view-model, yt-enforcement-message-view-model, ' +
                        'ytd-enforcement-message-view-model, ytm-player-error-message-renderer, yt-player-error-message-renderer, ' +
                        'ytd-player-error-message-renderer, ytm-playability-error-supported-renderers, ytm-feed-nudge-renderer, ' +
                        'yt-feed-nudge-renderer, yt-confirm-dialog-renderer, tp-yt-paper-dialog, ' +
                        '.ytp-error, #error-screen, .error-container, #player-error-message-container'
                    );
                    var foundBotWall = false;
                    botContainers.forEach(function(el) {
                        var t = el.innerText || el.textContent || '';
                        if (isBotPhrase(t) || el.tagName.toLowerCase().indexOf('enforcement') !== -1 || el.tagName.toLowerCase().indexOf('nudge') !== -1) {
                            if (isBotPhrase(t)) foundBotWall = true;
                            hideCompactBotElement(el);
                        }
                    });

                    // 2. Active structural sweep: catch dynamically obfuscated YouTube bot cards regardless of tag name
                    var compactCandidates = document.querySelectorAll('ytm-item-section-renderer, ytm-rich-section-renderer, ytd-rich-section-renderer, ytd-item-section-renderer, div, section');
                    for (var i = 0; i < compactCandidates.length; i++) {
                        var cand = compactCandidates[i];
                        if (!cand || cand.style.display === 'none') continue;
                        if (cand.id === 'bh-yt-bypass-player' || (cand.closest && cand.closest('#bh-yt-bypass-player'))) continue;
                        if (cand.querySelector && cand.querySelector('ytm-video-with-context-renderer, ytm-rich-item-renderer, ytm-compact-video-renderer, ytd-rich-item-renderer, ytd-video-renderer, #bh-yt-bypass-player')) continue;
                        var txt = cand.innerText || '';
                        if (txt.length > 0 && txt.length < 600 && isBotPhrase(txt)) {
                            foundBotWall = true;
                            hideCompactBotElement(cand);
                        }
                    }

                    // 3. Remove overlay modal backdrops & unlock scroll if YouTube froze the viewport
                    document.querySelectorAll('tp-yt-iron-overlay-backdrop').forEach(function(b) {
                        b.style.setProperty('display', 'none', 'important');
                    });
                    if (document.body && window.getComputedStyle(document.body).overflow === 'hidden') {
                        document.body.style.overflow = 'auto';
                    }

                    // 4. Keep active bypass player visible if Polymer updated container styles
                    var activePlayer = document.getElementById('bh-yt-bypass-player');
                    if (activePlayer) {
                        activePlayer.style.setProperty('display', 'block', 'important');
                    }

                    if (!foundBotWall && document.body) {
                        var bodySlice = (document.body.innerText || '').slice(0, 4500);
                        if (isBotPhrase(bodySlice)) {
                            foundBotWall = true;
                        }
                    }

                    if (foundBotWall) {
                        notifyBotWall('dom', '');
                    }
                } catch(e) {}
            };

            checkBotAndAds();
            if (!window.__bhYtWatcher) {
                window.__bhYtWatcher = true;
                setInterval(checkBotAndAds, 450);
                try {
                    var obs = new MutationObserver(function() { checkBotAndAds(); });
                    obs.observe(document.documentElement, { childList: true, subtree: true });
                } catch(e) {}
            }
        })();
    """

    /**
     * Blocks Web Push notification spam, pop-under window.open hijacks, exit traps,
     * and transparent full-screen click-hijack overlays (common on 18+ and streaming sites).
     */
    const val POPUP_NOTIFICATION_KILLER_JS: String = """
        (function() {
            if (!window.__bhPopupKillerInstalled) {
                window.__bhPopupKillerInstalled = true;
                try {
                    // Kill Notification & Push spam
                    if (window.Notification) {
                        Object.defineProperty(Notification, 'permission', { get: function() { return 'denied'; }, configurable: true });
                        Notification.requestPermission = function(cb) {
                            if (typeof cb === 'function') try { cb('denied'); } catch(e) {}
                            return Promise.resolve('denied');
                        };
                    }
                    if (window.PushManager && PushManager.prototype) {
                        PushManager.prototype.subscribe = function() {
                            return Promise.reject(new DOMException('Notifications blocked by BrowserHub', 'NotAllowedError'));
                        };
                    }
                    // Block popunder / spam window.open
                    var origOpen = window.open;
                    window.open = function(url) {
                        try {
                            if (window.BrowserHubExtBridge && window.BrowserHubExtBridge.onDevToolsLog) {
                                window.BrowserHubExtBridge.onDevToolsLog('SHIELD', 'Blocked popup: ' + String(url || 'about:blank').slice(0, 120));
                            }
                        } catch(e) {}
                        return { closed: false, focus: function(){}, blur: function(){}, close: function(){}, opener: null, location: { href: url || '' } };
                    };
                    // Neutralize exit-intent popups
                    Object.defineProperty(window, 'onbeforeunload', { set: function() {}, get: function() { return null; }, configurable: true });
                } catch(e) {}
            }
            // Strip transparent fullscreen click-jack overlays and floating ad layers
            try {
                var vw = window.innerWidth || 360;
                var vh = window.innerHeight || 640;
                var els = document.querySelectorAll('div, a, section, aside, ins');
                for (var i = 0; i < els.length; i++) {
                    var el = els[i];
                    if (el.id && el.id.indexOf('bh-') === 0) continue;
                    if (el.closest && el.closest('video, #movie_player, .html5-video-player, ytm-player, .video-js, .plyr, #bh-yt-bypass-player')) continue;
                    var st = window.getComputedStyle(el);
                    if (!st) continue;
                    var isOverlay = (st.position === 'fixed' || st.position === 'absolute');
                    if (!isOverlay) continue;
                    var z = parseInt(st.zIndex, 10) || 0;
                    var r = el.getBoundingClientRect();
                    var coversScreen = (r.width >= vw * 0.72 && r.height >= vh * 0.65);
                    var isInvisibleTrap = coversScreen && z >= 99 && (parseFloat(st.opacity) < 0.08 || (st.backgroundColor === 'rgba(0, 0, 0, 0)' && el.children.length === 0));
                    var isFloatingAd = z >= 9999 && /pop|ad[-_]?overlay|ad[-_]?modal|interstitial|exoclick|juicy/i.test((el.id || '') + ' ' + (el.className || ''));
                    if (isInvisibleTrap || isFloatingAd) {
                        el.style.setProperty('pointer-events', 'none', 'important');
                        el.style.setProperty('display', 'none', 'important');
                    }
                }
            } catch(e) {}
        })();
    """

    /**
     * Direct Ad-Skipper: skips pre-roll player ads, hides player overlay banners,
     * and fast-forwards active video ad segments without affecting normal playback.
     */
    const val AD_SKIPPER_JS: String = """
        (function() {
            var skipPass = function() {
                try {
                    var skipSelectors = [
                        '.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.ytp-skip-ad-button',
                        '.vast-skip-button', '.vjs-skip-ad-button',
                        '[class*="skip-ad" i]', '[id*="skip-ad" i]',
                        'button[aria-label*="Skip Ad" i]', 'button[aria-label*="Skip" i].ytp-ad-skip-button-slot'
                    ];
                    skipSelectors.forEach(function(sel) {
                        document.querySelectorAll(sel).forEach(function(btn) {
                            if (btn && typeof btn.click === 'function') {
                                btn.click();
                            }
                        });
                    });
                    // Hide static player ad banners
                    document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-image-overlay, .ytp-ad-text-overlay, [class*="ad-banner-overlay" i]').forEach(function(el) {
                        el.style.setProperty('display', 'none', 'important');
                    });
                    // Fast-forward active ad video ONLY when the player container itself is in ad-showing state
                    var adPlayer = document.querySelector('#movie_player.ad-showing, #movie_player.ad-interrupting, .html5-video-player.ad-showing, .html5-video-player.ad-interrupting');
                    if (adPlayer) {
                        adPlayer.querySelectorAll('video').forEach(function(v) {
                            if (!v.closest('#bh-yt-bypass-player') && isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.1) {
                                v.muted = true;
                                v.playbackRate = 16.0;
                                v.currentTime = v.duration;
                            }
                        });
                    }
                } catch(e) {}
            };
            skipPass();
            if (!window.__bhAdSkipperObserver) {
                window.__bhAdSkipperObserver = true;
                setInterval(skipPass, 650);
            }
        })();
    """

    const val CANVAS_HARDWARE_SPOOF_JS: String = """
        (function() {
            if (window.__bhSpoofApplied) return;
            window.__bhSpoofApplied = true;
            try {
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 4; }, configurable: true });
                Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 4; }, configurable: true });
            } catch(e) {}
            try {
                var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function(sx, sy, sw, sh) {
                    var data = origGetImageData.apply(this, arguments);
                    if (data && data.data && data.data.length > 4) {
                        data.data[0] = data.data[0] ^ 1;
                    }
                    return data;
                };
                var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = function() {
                    try {
                        var ctx = this.getContext('2d');
                        if (ctx && this.width > 0 && this.height > 0) {
                            ctx.fillStyle = 'rgba(0,0,0,0.004)';
                            ctx.fillRect(0, 0, 1, 1);
                        }
                    } catch(e) {}
                    return origToDataURL.apply(this, arguments);
                };
            } catch(e) {}
        })();
    """

    const val ELEMENT_ZAPPER_ACTIVATE_JS: String = """
        (function() {
            if (window.__bhZapperActive) return;
            window.__bhZapperActive = true;
            var banner = document.getElementById('bh-zapper-hud');
            if (!banner) {
                banner = document.createElement('div');
                banner.id = 'bh-zapper-hud';
                banner.textContent = 'uBlock Element Zapper ON — Tap any box/banner to vaporize';
                banner.style.cssText = 'position:fixed;top:10px;left:50%;transform:translateX(-50%);z-index:2147483647;background:#7f1d1d;color:#fecdd3;border:1px solid #fb7185;padding:8px 14px;border-radius:999px;font:600 12px/1.2 system-ui,sans-serif;box-shadow:0 8px 24px rgba(0,0,0,0.5);pointer-events:none;';
                (document.body || document.documentElement).appendChild(banner);
            }
            window.__bhZapperHandler = function(ev) {
                if (!window.__bhZapperActive) return;
                var target = ev.target;
                if (!target || target === document.body || target === document.documentElement || target.id === 'bh-zapper-hud') return;
                ev.preventDefault();
                ev.stopPropagation();
                target.style.setProperty('display', 'none', 'important');
                if (window.BrowserHubExtBridge && window.BrowserHubExtBridge.onElementZapped) {
                    window.BrowserHubExtBridge.onElementZapped(target.tagName || 'ELEMENT');
                }
            };
            document.addEventListener('click', window.__bhZapperHandler, true);
        })();
    """

    const val ELEMENT_ZAPPER_DEACTIVATE_JS: String = """
        (function() {
            window.__bhZapperActive = false;
            var banner = document.getElementById('bh-zapper-hud');
            if (banner) banner.remove();
            if (window.__bhZapperHandler) {
                document.removeEventListener('click', window.__bhZapperHandler, true);
            }
        })();
    """

    /**
     * Overrides HTML5 navigator.geolocation and Intl.DateTimeFormat timezone/offset
     * to match the user's selected VirtualRegion without affecting core DOM stability.
     */
    fun buildLocationMaskJs(
        latitude: Double,
        longitude: Double,
        timezoneId: String,
        tzOffsetMinutesWest: Int
    ): String {
        val safeTz = org.json.JSONObject.quote(timezoneId)
        return """
            (function() {
                try {
                    if (window.__bhLocRegion === $safeTz) return;
                    window.__bhLocRegion = $safeTz;
                    var fakePos = function() {
                        return {
                            coords: {
                                latitude: $latitude,
                                longitude: $longitude,
                                accuracy: 18,
                                altitude: null,
                                altitudeAccuracy: null,
                                heading: null,
                                speed: null
                            },
                            timestamp: Date.now()
                        };
                    };
                    if (navigator.geolocation) {
                        navigator.geolocation.getCurrentPosition = function(success) {
                            if (typeof success === 'function') {
                                setTimeout(function() { success(fakePos()); }, 12);
                            }
                        };
                        navigator.geolocation.watchPosition = function(success) {
                            if (typeof success === 'function') {
                                setTimeout(function() { success(fakePos()); }, 12);
                            }
                            return 1;
                        };
                        navigator.geolocation.clearWatch = function() {};
                    }
                    if ($safeTz && window.Intl && Intl.DateTimeFormat) {
                        var origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
                        Intl.DateTimeFormat.prototype.resolvedOptions = function() {
                            var opts = origResolved.apply(this, arguments);
                            opts.timeZone = $safeTz;
                            return opts;
                        };
                        Date.prototype.getTimezoneOffset = function() { return $tzOffsetMinutesWest; };
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
