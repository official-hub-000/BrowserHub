package com.browserhub.app.bridge

import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import com.browserhub.app.BuildConfig
import com.browserhub.app.MainActivity
import com.browserhub.app.ai.LocalNeuralEngine
import com.browserhub.app.ai.NativeAiClient
import com.browserhub.app.engine.HybridBrowserEngine
import com.browserhub.app.extensions.UniversalExtensionManager
import com.browserhub.app.privacy.HubShieldEngine
import com.browserhub.app.vpn.HubVpnService

class BrowserHubJavascriptBridge(
    private val activity: MainActivity,
    private val engine: HybridBrowserEngine,
    private val shield: HubShieldEngine,
    private val extensions: UniversalExtensionManager,
    private val neural: LocalNeuralEngine
) {

    @JavascriptInterface
    fun getDeviceArchitecture(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val channel = if (BuildConfig.PLAY_STORE_COMPLIANT) "PlayStore-AAB" else "Universal-APK"
        return "Android ${Build.VERSION.RELEASE} ($abi • $channel)"
    }

    @JavascriptInterface
    fun isPlayStoreFlavor(): Boolean = BuildConfig.PLAY_STORE_COMPLIANT

    @JavascriptInterface
    fun setVpnState(enabled: Boolean, nodeId: String, protocol: String, dohUrl: String) {
        if (enabled) {
            activity.requestConsentAndStartVpn(nodeId, protocol, dohUrl)
        } else {
            val intent = Intent(activity, HubVpnService::class.java).apply {
                action = HubVpnService.ACTION_DISCONNECT
            }
            activity.startService(intent)
        }
    }

    @JavascriptInterface
    fun setIncognitoLock(locked: Boolean) {
        activity.setIncognitoScreenshotLock(locked)
    }

    @JavascriptInterface
    fun setShieldConfig(adblock: Boolean, cookieReject: Boolean, stripParams: Boolean) {
        shield.adblockEnabled = adblock
        shield.cookieAutoReject = cookieReject
        shield.stripTrackingParams = stripParams
    }

    @JavascriptInterface
    fun installExtensionPackage(sourceType: String, urlOrBase64: String): String {
        return extensions.installFromSource(sourceType, urlOrBase64)
    }

    @JavascriptInterface
    fun setDesktopMode(tabId: String, enabled: Boolean) {
        engine.setDesktopViewport(tabId, enabled)
    }

    @JavascriptInterface
    fun getActivePageText(): String = activity.extractedPageText

    @JavascriptInterface
    fun syncWebViewport(
        url: String,
        topCssPx: Float,
        bottomCssPx: Float,
        winHeightCss: Float,
        isDesktop: Boolean,
        adblockOn: Boolean,
        darkReaderOn: Boolean,
        cookieKillerOn: Boolean,
        customCss: String,
        zoomPct: Int,
        findQuery: String,
        visible: Boolean
    ) {
        activity.syncNativeBrowserViewport(
            url, topCssPx, bottomCssPx, winHeightCss,
            isDesktop, adblockOn, darkReaderOn, cookieKillerOn,
            customCss, zoomPct, findQuery, visible
        )
    }

    @JavascriptInterface
    fun setWebViewportVisible(visible: Boolean) {
        activity.setBrowserVisibleOnly(visible)
    }

    @JavascriptInterface
    fun nativeGoBack(): Boolean = activity.goBackBrowser()

    @JavascriptInterface
    fun nativeGoForward(): Boolean = activity.goForwardBrowser()

    @JavascriptInterface
    fun nativeReload() = activity.reloadBrowser()

    @JavascriptInterface
    fun shredSessionData(): Boolean {
        shield.shredAllBrowsingData(activity.applicationContext)
        engine.clearAllContainerStorage()
        activity.shredBrowserWebView()
        return true
    }

    @JavascriptInterface
    fun runLocalInference(prompt: String, pageText: String): String {
        return neural.inferZeroKeyLocal(prompt, pageText)
    }

    /**
     * Zero-key live AI. Runs on a native worker thread so the WebView JS thread never
     * freezes and CORS is bypassed; the page polls pollAiResult() until ready.
     */
    @JavascriptInterface
    fun startAiRequest(requestId: String, prompt: String, systemPrompt: String) {
        NativeAiClient.start(requestId, prompt, systemPrompt)
    }

    @JavascriptInterface
    fun pollAiResult(requestId: String): String? = NativeAiClient.poll(requestId)
}
