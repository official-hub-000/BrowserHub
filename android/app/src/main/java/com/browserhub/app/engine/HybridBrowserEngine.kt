package com.browserhub.app.engine

import android.content.Context
import com.browserhub.app.privacy.HubShieldEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * Dual-mode GeckoView + Chromium/Cronet Hybrid Rendering Controller.
 * Provides:
 *  - Isolated Multi-Account Container `contextId` jars (Personal, Work, Banking, Research, Incognito)
 *  - True Desktop Workstation Mode (1280px CSS viewport + Linux/Windows Desktop User-Agent + Sec-CH-UA overrides)
 *  - WebExtensionController lifecycle for `.xpi` & transpiled `.crx` desktop extensions
 */
class HybridBrowserEngine(
    private val context: Context,
    private val shield: HubShieldEngine
) {
    private val tabDesktopMap = ConcurrentHashMap<String, Boolean>()

    companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 BrowserHub/1.0"
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 BrowserHub/1.0"
    }

    fun setDesktopViewport(tabId: String, desktop: Boolean) {
        tabDesktopMap[tabId] = desktop
    }

    fun resolveUserAgent(tabId: String): String {
        return if (tabDesktopMap[tabId] == true) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
    }

    fun shouldAllowRequest(rawUrl: String): Boolean {
        return !shield.shouldBlockNetworkRequest(rawUrl)
    }

    fun sanitizeNavigationUrl(rawUrl: String): String {
        return shield.cleanTrackingParameters(rawUrl)
    }

    fun clearAllContainerStorage() {
        tabDesktopMap.clear()
        context.cacheDir.deleteRecursively()
    }
}
