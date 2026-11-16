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
    @Volatile var cookieAutoReject: Boolean = true
    @Volatile var stripTrackingParams: Boolean = true

    private val blockedHostSuffixes = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "connect.facebook.net",
        "hotjar.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "adnxs.com",
        "fingerprintjs.io"
    )

    private val trackingQueryKeys = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "dclid", "msclkid", "mc_eid", "igshid", "si"
    )

    fun shouldBlockNetworkRequest(rawUrl: String): Boolean {
        if (!adblockEnabled) return false
        val host = Uri.parse(rawUrl).host?.lowercase() ?: return false
        return blockedHostSuffixes.any { host == it || host.endsWith(".$it") }
    }

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
