package com.browserhub.app;

import android.os.Build;
import android.webkit.JavascriptInterface;

public class BrowserHubBridge {
    private final MainActivity activity;

    public BrowserHubBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getDeviceArchitecture() {
        String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                ? Build.SUPPORTED_ABIS[0]
                : "arm64-v8a";
        return "Android " + Build.VERSION.RELEASE + " (" + abi + " • Native Release)";
    }

    @JavascriptInterface
    public boolean isPlayStoreFlavor() {
        return false;
    }

    @JavascriptInterface
    public void setVpnState(boolean enabled, String nodeId, String protocol, String dohUrl) {
        activity.handleVpnToggle(enabled, nodeId);
    }

    @JavascriptInterface
    public void setIncognitoLock(boolean locked) {
        activity.setIncognitoScreenshotLock(locked);
    }

    @JavascriptInterface
    public boolean shredSessionData() {
        activity.shredAllBrowsingData();
        return true;
    }

    @JavascriptInterface
    public String getActivePageText() {
        return activity.getExtractedPageText();
    }

    @JavascriptInterface
    public void syncWebViewport(
            String url,
            float topCssPx,
            float bottomCssPx,
            float winHeightCss,
            boolean isDesktop,
            boolean adblockOn,
            boolean darkReaderOn,
            boolean cookieKillerOn,
            String customCss,
            int zoomPct,
            String findQuery,
            boolean visible,
            boolean trackerBlockOn,
            boolean antiFingerprintOn,
            boolean httpsUpgradeOn,
            boolean keepMediaAlive,
            boolean customCssActive
    ) {
        activity.syncNativeBrowserViewport(
                url, topCssPx, bottomCssPx, winHeightCss,
                isDesktop, adblockOn, darkReaderOn, cookieKillerOn,
                customCss, zoomPct, findQuery, visible,
                trackerBlockOn, antiFingerprintOn, httpsUpgradeOn,
                keepMediaAlive, customCssActive
        );
    }

    /**
     * Hands a deep link (tel:, mailto:, market:, intent:) to the OS.
     *
     * A WebView cannot render these — attempting a load produces
     * net::ERR_NAME_NOT_RESOLVED, which is exactly the error screen reported.
     * Returns false when nothing on the device can handle the URI.
     */
    @JavascriptInterface
    public boolean openExternalUrl(String url) {
        return activity.openExternalUrl(url);
    }

    /**
     * Injects a sideloaded WebExtension's content scripts into the live page.
     *
     * `css` and `js` are already matched to this URL by the shell's runtime, so
     * the native side only has to apply them. Kept separate from
     * setUserScripts so a package's code can be cleared independently of a
     * user-authored script.
     */
    @JavascriptInterface
    public void applyExtensionRuntime(String css, String js) {
        activity.applyExtensionRuntime(css, js);
    }

    /**
     * Real haptic feedback.
     *
     * This is the single biggest reason a WebView-based browser feels like a web
     * page instead of a native app: every tap is silent and still. `strength`:
     * 0 = light tick (selection), 1 = keyboard tap (buttons), 2 = confirm.
     */
    @JavascriptInterface
    public void haptic(int strength) {
        activity.performHaptic(strength);
    }

    /** Reliable clipboard write for Android WebView (execCommand often fails). */
    @JavascriptInterface
    public void copyToClipboard(final String text) {
        activity.copyToClipboard(text);
    }

    @JavascriptInterface
    public void setUserScripts(String code) {
        activity.applyUserScripts(code);
    }

    @JavascriptInterface
    public void setPageTranslation(String langCode, String dictionaryJson) {
        activity.applyPageTranslation(langCode, dictionaryJson);
    }

    @JavascriptInterface
    public void setWebViewportVisible(boolean visible) {
        activity.setBrowserVisibleOnly(visible);
    }

    @JavascriptInterface
    public boolean nativeGoBack() {
        return activity.goBackBrowser();
    }

    @JavascriptInterface
    public boolean nativeGoForward() {
        return activity.goForwardBrowser();
    }

    @JavascriptInterface
    public void nativeReload() {
        activity.reloadBrowser();
    }

    /**
     * Zero-key AI, non-blocking: starts the free public LLM call on a native worker
     * thread so the WebView JS thread never freezes and CORS never applies.
     * The page polls {@link #pollAiResult(String)} until a JSON envelope is ready.
     */
    @JavascriptInterface
    public void startAiRequest(String requestId, String prompt, String systemPrompt) {
        com.browserhub.app.ai.NativeAiClient.start(requestId, prompt, systemPrompt);
    }

    @JavascriptInterface
    public String pollAiResult(String requestId) {
        return com.browserhub.app.ai.NativeAiClient.poll(requestId);
    }

    // ------------------------------------------------------------------
    //  Video capture: what the page is playing right now.
    // ------------------------------------------------------------------

    /**
     * Installs the media hooks in the real page and returns what it has already
     * seen. Returning the current list in the same call means the first open of
     * the sheet is never empty just because the hooks landed a moment ago.
     */
    @JavascriptInterface
    public String collectPageMedia() {
        return activity.collectPageMedia();
    }

    /** Last capture set, for immediate paint while a fresh scan runs. */
    @JavascriptInterface
    public String getPageMedia() {
        return activity.getLastPageMedia();
    }

    /** Downloads a captured media URL through the platform (no page CORS). */
    @JavascriptInterface
    public void downloadPageMedia(String url, String filename) {
        activity.downloadPageMedia(url, filename);
    }

    /** True when the platform downloader is available (Android build only). */
    @JavascriptInterface
    public boolean hasNativeDownloader() {
        return true;
    }
}
