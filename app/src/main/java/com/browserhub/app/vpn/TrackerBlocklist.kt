package com.browserhub.app.vpn

import java.net.URI
import java.util.Locale

object TrackerBlocklist {
    private val blockedHosts = setOf(
        "doubleclick.net",
        "google-analytics.com",
        "www.google-analytics.com",
        "ssl.google-analytics.com",
        "googletagmanager.com",
        "www.googletagmanager.com",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "analytics.google.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "an.facebook.com",
        "ads.twitter.com",
        "analytics.twitter.com",
        "ads-api.twitter.com",
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "bat.bing.com",
        "c.bing.com",
        "scorecardresearch.com",
        "sb.scorecardresearch.com",
        "quantserve.com",
        "pixel.quantserve.com",
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
        "demdex.net",
        "omtrdc.net",
        "hotjar.com",
        "static.hotjar.com",
        "mixpanel.com",
        "api.mixpanel.com",
        "segment.io",
        "api.segment.io",
        "amplitude.com",
        "api.amplitude.com",
        "branch.io",
        "app.link",
        "appsflyer.com",
        "adjust.com",
        "sentry.io",
        "browser-intake-datadoghq.com"
    )

    fun isBlockedDomain(domain: String): Boolean {
        val clean = domain.trim().trimEnd('.').lowercase(Locale.US)
        if (clean.isEmpty()) return false
        if (clean in blockedHosts) return true
        return blockedHosts.any { blocked -> clean.endsWith(".$blocked") }
    }

    fun isBlockedUrl(url: String): Boolean {
        return try {
            val host = URI(url).host.orEmpty()
            isBlockedDomain(host)
        } catch (_: Exception) {
            false
        }
    }

    const val WEBRTC_LEAK_GUARD_JS: String = """
        (function() {
            if (window.__browserHubWebRtcGuarded) return;
            window.__browserHubWebRtcGuarded = true;
            var blocked = function() {
                throw new DOMException('Blocked by BrowserHub Privacy Shield', 'NotAllowedError');
            };
            if (window.RTCPeerConnection) window.RTCPeerConnection = blocked;
            if (window.webkitRTCPeerConnection) window.webkitRTCPeerConnection = blocked;
            if (window.mozRTCPeerConnection) window.mozRTCPeerConnection = blocked;
            if (navigator && navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                navigator.mediaDevices.getUserMedia = function() {
                    return Promise.reject(new DOMException('Media capture blocked by Privacy Shield', 'NotAllowedError'));
                };
            }
        })();
    """
}
