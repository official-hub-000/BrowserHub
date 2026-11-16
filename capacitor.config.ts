import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.browserhub.hybrid.android',
  appName: 'BrowserHub',
  webDir: 'dist',
  android: {
    allowMixedContent: false,
    captureInput: true,
    webContentsDebuggingEnabled: false,
    backgroundColor: '#05070d',
    buildOptions: {
      keystoreAlias: 'browserhub-release',
      releaseType: 'APK',
    },
  },
  plugins: {
    BrowserHubNative: {
      defaultEngine: 'hybrid-gecko-chromium',
      enableHardwareAcceleration: true,
      nativeAdBlockLists: [
        'uBlock-Origin-Core',
        'EasyList',
        'EasyPrivacy',
        'Fanboy-Annoyances',
        'PeterLowe-Trackers',
        'ClearURLs-Params',
      ],
      vpnServiceMode: 'wireguard-doh-tor',
      localAiRuntime: 'on-device-npu-gguf-zero-key',
    },
  },
};

export default config;
