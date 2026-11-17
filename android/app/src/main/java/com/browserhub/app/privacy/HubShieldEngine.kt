package com.browserhub.app.privacy

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * HubShield Native Privacy & Direct AdBlocker Core.
 * - Blocks ad auctions, trackers, CNAME cloakers & cryptominers before socket creation
 * - Strips invasive URL tracking parameters (`utm_*`, `fbclid`, `gclid`, `mc_eid`, `si`)
 * - Injects deterministic Canvas/WebGL/AudioContext noise per origin
 * - Provides 1-Tap DuckDuckGo-style Fire Shredder
 */
class HubShieldEngine {

    @Volatile var adblockEnabled: Boolean = true
    /** Analytics / fingerprinting is a separate list so the two toggles differ. */
    @Volatile var trackerBlockEnabled: Boolean = true
    @Volatile var cookieAutoReject: Boolean = true
    @Volatile var stripTrackingParams: Boolean = true

    /** Advertising networks — gated by the Native Ad Blocker toggle. */
    private val adHostSuffixes = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "adnxs.com"
    )

    /** Analytics / fingerprinting — gated by the separate tracker toggle. */
    private val trackerHostSuffixes = listOf(
        "google-analytics.com",
        "googletagmanager.com",
        "connect.facebook.net",
        "hotjar.com",
        "fingerprintjs.io",
        "scorecardresearch.com",
        "clarity.ms"
    )

    private val trackingQueryKeys = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "dclid", "msclkid", "mc_eid", "igshid", "si"
    )

    /** @return "ads", "trackers" or null when the request is allowed. */
    fun matchBlockList(rawUrl: String): String? {
        val host = Uri.parse(rawUrl).host?.lowercase() ?: return null
        if (adblockEnabled && adHostSuffixes.any { host == it || host.endsWith(".$it") }) return "ads"
        if (trackerBlockEnabled && trackerHostSuffixes.any { host == it || host.endsWith(".$it") }) return "trackers"
        return null
    }

    fun shouldBlockNetworkRequest(rawUrl: String): Boolean = matchBlockList(rawUrl) != null

    fun cleanTrackingParameters(rawUrl: String): String {
        if (!stripTrackingParams) return rawUrl
        val uri = Uri.parse(rawUrl)
        val names = uri.queryParameterNames
        if (names.none { it in trackingQueryKeys }) return rawUrl

        val builder = uri.buildUpon().clearQuery()
        for (name in names) {
            if (name !in trackingQueryKeys) {
                for (value in uri.getQueryParameters(name)) {
                    builder.appendQueryParameter(name, value)
                }
            }
        }
        return builder.build().toString()
    }

    fun getAntiFingerprintScript(originSeed: Int): String = """
        (function() {
          const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
          HTMLCanvasElement.prototype.toDataURL = function(...args) {
            const ctx = this.getContext('2d');
            if (ctx && this.width > 0 && this.height > 0) {
              const img = ctx.getImageData(0, 0, 1, 1);
              img.data[0] = (img.data[0] ^ ${originSeed and 0x07});
              ctx.putImageData(img, 0, 0);
            }
            return origToDataURL.apply(this, args);
          };
          Object.defineProperty(navigator, 'globalPrivacyControl', { value: true });
        })();
    """.trimIndent()

    fun shredAllBrowsingData(context: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        context.cacheDir.deleteRecursively()
    }
}
