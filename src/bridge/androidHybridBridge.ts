import { Capacitor, registerPlugin } from '@capacitor/core';
import type { BrowserEngineType } from '../types/browser';

export interface AndroidJniBridge {
  getDeviceArchitecture?: () => string;
  isPlayStoreFlavor?: () => boolean;
  setVpnState?: (enabled: boolean, nodeId: string, protocol: string, dohUrl: string) => void;
  setShieldConfig?: (adblock: boolean, cookieReject: boolean, stripParams: boolean) => void;
  installExtensionPackage?: (sourceType: String, urlOrBase64: String) => string;
  setDesktopMode?: (tabId: string, enabled: boolean) => void;
  setIncognitoLock?: (locked: boolean) => void;
  shredSessionData?: () => boolean;
  getActivePageText?: () => string;
  syncWebViewport?: (
    url: string,
    topCssPx: number,
    bottomCssPx: number,
    winHeightCss: number,
    isDesktop: boolean,
    adblockOn: boolean,
    darkReaderOn: boolean,
    cookieKillerOn: boolean,
    customCss: string,
    zoomPct: number,
    findQuery: string,
    visible: boolean,
    trackerBlockOn: boolean,
    antiFingerprintOn: boolean,
    httpsUpgradeOn: boolean,
    keepMediaAlive: boolean,
    customCssActive: boolean
  ) => void;
  setWebViewportVisible?: (visible: boolean) => void;
  nativeGoBack?: () => boolean;
  nativeGoForward?: () => boolean;
  nativeReload?: () => void;
  /** Starts a zero-key AI request on a native worker thread (no CORS, no UI freeze). */
  startAiRequest?: (requestId: string, prompt: string, systemPrompt: string) => void;
  /** Native clipboard write (WebView's execCommand('copy') is unreliable). */
  copyToClipboard?: (text: string) => void;
  /** Injects the user's enabled Userscript (Violentmonkey-style) into the page. */
  setUserScripts?: (code: string) => void;
  /** Applies (or clears) offline phrase translation inside the real page. */
  setPageTranslation?: (langCode: string, dictionaryJson: string) => void;
  /** Returns the JSON result envelope once ready, or null while pending. */
  pollAiResult?: (requestId: string) => string | null;
  /**
   * Hands a non-web URI (tel:, mailto:, market:, intent:) to the Android OS.
   * Returns false when nothing on the device can handle it.
   */
  openExternalUrl?: (url: string) => boolean;
  /** 0 = light tick, 1 = key tap, 2 = confirm. No-op outside the APK. */
  haptic?: (strength: number) => void;
  /** Applies a sideloaded WebExtension's matched content scripts to the page. */
  applyExtensionRuntime?: (css: string, js: string) => void;
}

declare global {
  interface Window {
    BrowserHubNative?: AndroidJniBridge;
    __onNativeUrlChanged?: (url: string, title: string) => void;
    /** `list` is "ads" or "trackers" so the two shield counters stay separate. */
    __onNativeAdBlocked?: (domain: string, list?: string) => void;
    __onAndroidBackPressed?: () => boolean;
    /** Real match counts coming back from the native Find-in-page engine. */
    __onNativeFindResult?: (activeOrdinal: number, totalMatches: number) => void;
    /** How many text blocks the offline translator actually rewrote. */
    __onNativeTranslateResult?: (langCode: string, blocksChanged: number) => void;
  }
}

export interface BrowserHubCapacitorPlugin {
  setRenderingEngine(options: { engine: BrowserEngineType }): Promise<{ activeEngine: string }>;
  configureVpnTunnel(options: {
    enable: boolean;
    nodeId: string;
    protocol: string;
    dohProvider: string;
    killSwitch: boolean;
  }): Promise<{ connected: boolean; interfaceIp: string }>;
}

const CapacitorNative = registerPlugin<BrowserHubCapacitorPlugin>('BrowserHubNative');

export const AndroidHybridBridge = {
  /**
   * Native haptic feedback.
   *
   * "This app is like a page, not a real native android app" was the fair
   * result of an app where every control was silent and still — no tap ever
   * produced a physical response. This is the cheapest, most convincing native
   * signal there is, and it respects the device's own haptics setting because
   * the native side uses performHapticFeedback.
   */
  haptic(strength: 0 | 1 | 2 = 1): void {
    try {
      window.BrowserHubNative?.haptic?.(strength);
    } catch {
      // Haptics are optional; never let one break an interaction.
    }
  },

  /**
   * Sends a deep link to Android. A WebView cannot render `tel:` or `market:`,
   * and attempting to load one produces a DNS error page, so those have to be
   * handed to the OS explicitly.
   */
  openExternalUrl(url: string): boolean {
    if (!url) return false;
    if (this.hasJniBridge() && window.BrowserHubNative?.openExternalUrl) {
      try {
        return Boolean(window.BrowserHubNative.openExternalUrl(url));
      } catch {
        return false;
      }
    }
    return false;
  },

  hasJniBridge(): boolean {
    return typeof window !== 'undefined' && Boolean(window.BrowserHubNative);
  },

  isNativeAndroid(): boolean {
    return (
      this.hasJniBridge() ||
      (Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android')
    );
  },

  getRuntimeBadge(): string {
    if (this.hasJniBridge() && window.BrowserHubNative?.getDeviceArchitecture) {
      return window.BrowserHubNative.getDeviceArchitecture();
    }
    if (this.isNativeAndroid()) {
      return 'Android Native APK (Dual WebView + Shield Engine)';
    }
    return 'Android Hybrid Live Runtime (Web + Capacitor/JNI Bridge)';
  },

  async switchEngine(engine: BrowserEngineType): Promise<string> {
    if (Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android') {
      const res = await CapacitorNative.setRenderingEngine({ engine });
      return res.activeEngine;
    }
    return engine;
  },

  async syncVpnTunnel(
    enable: boolean,
    nodeId: string,
    protocol: string,
    dohProvider: string,
    killSwitch: boolean
  ): Promise<{ connected: boolean; interfaceIp: string }> {
    if (this.hasJniBridge() && window.BrowserHubNative?.setVpnState) {
      window.BrowserHubNative.setVpnState(enable, nodeId, protocol, dohProvider);
      return {
        connected: enable,
        interfaceIp: enable ? '10.88.0.2/32' : '0.0.0.0',
      };
    }
    if (Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android') {
      return CapacitorNative.configureVpnTunnel({
        enable,
        nodeId,
        protocol,
        dohProvider,
        killSwitch,
      });
    }
    return {
      connected: enable,
      interfaceIp: enable ? '10.88.0.2/32' : '0.0.0.0',
    };
  },
};

export function isPlayStoreCompliantFlavor(): boolean {
  try {
    if (typeof window !== 'undefined' && window.BrowserHubNative?.isPlayStoreFlavor) {
      return Boolean(window.BrowserHubNative.isPlayStoreFlavor());
    }
  } catch {
    // Ignore bridge lookup errors outside Android WebView
  }
  return false;
}
