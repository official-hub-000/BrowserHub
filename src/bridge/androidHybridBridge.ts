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
    visible: boolean
  ) => void;
  setWebViewportVisible?: (visible: boolean) => void;
  nativeGoBack?: () => boolean;
  nativeGoForward?: () => boolean;
  nativeReload?: () => void;
}

declare global {
  interface Window {
    BrowserHubNative?: AndroidJniBridge;
    __onNativeUrlChanged?: (url: string, title: string) => void;
    __onNativeAdBlocked?: (domain: string) => void;
    __onAndroidBackPressed?: () => boolean;
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
