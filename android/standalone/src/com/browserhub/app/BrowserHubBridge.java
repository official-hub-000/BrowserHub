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
}
