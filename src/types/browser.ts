/** Kept for bridge compatibility: BrowserHub renders with one Chromium WebView. */
export type BrowserEngineType = 'system-webview';

export type AddressBarPosition = 'bottom' | 'top';

export type ThemePresetId =
  | 'amoled-pitch'
  | 'cyber-cyan'
  | 'emerald-shield'
  | 'arc-violet'
  | 'nord-frost'
  | 'vivaldi-crimson'
  | 'solar-amber';

export type SearchEngineId =
  | 'google'
  | 'duckduckgo'
  | 'brave'
  | 'bing'
  | 'startpage'
  | 'wikipedia'
  | 'github-live'
  | 'searxng';

export interface NetworkLogEntry {
  id: string;
  domain: string;
  url: string;
  type: 'document' | 'script' | 'xhr' | 'ad-banner' | 'tracker' | 'media' | 'stylesheet';
  status: 'allowed' | 'blocked' | 'stripped';
  ruleMatched?: string;
  latencyMs: number;
  sizeKb: number;
  timestamp: string;
}

export interface BrowserTab {
  id: string;
  title: string;
  url: string;
  favicon?: string;
  isIncognito: boolean;
  isPinned: boolean;
  isDesktopMode: boolean;
  isReaderMode: boolean;
  isHibernated: boolean;
  groupId: 'main' | 'work' | 'research' | 'media';
  history: string[];
  historyIndex: number;
  adsBlocked: number;
  trackersBlocked: number;
  bytesSavedKb: number;
  translatedLang: string | null; // null = original, e.g., 'bn', 'es', 'ja'
  bilingualTranslate: boolean;
  zappedSelectors: string[];
  zoomLevel: number;
  memoryMb: number;
}

export interface BrowserExtension {
  id: string;
  name: string;
  version: string;
  author: string;
  source: 'global-hub' | 'chrome-web-store' | 'firefox-amo' | 'sideload-file' | 'userscript';
  format: 'CRX3' | 'XPI' | 'USER.JS' | 'NATIVE-MOD';
  description: string;
  icon: string;
  enabled: boolean;
  allowIncognito: boolean;
  permissions: string[];
  effectKey?:
    | 'ublock'
    | 'dark-reader'
    | 'sponsorblock'
    | 'return-dislike'
    | 'cookie-killer'
    | 'clear-urls'
    | 'bg-play'
    | 'bypass-paywall'
    | 'bitwarden'
    | 'privacy-badger'
    | 'custom-script'
    | 'custom-css'
    /**
     * A sideloaded WebExtension whose own content scripts run through the
     * BrowserHub runtime (see utils/webextensionRuntime.ts).
     */
    | 'webextension';
  /**
   * Present on packages imported through the runtime: exactly what will run on
   * a page, and what cannot. Never inferred — produced by reading the package.
   */
  runtime?: import('../utils/webextensionRuntime').PackageReport;
  customJs?: string;
  customCss?: string;
  rating?: string;
  usersCount?: string;
}

export interface VpnNode {
  id: string;
  country: string;
  city: string;
  flag: string;
  protocol: 'WireGuard' | 'Multi-Hop WG' | 'Tor Onion v3';
  ipMask: string;
  baseLatencyMs: number;
  loadPercent: number;
}

export interface VpnState {
  connected: boolean;
  connecting: boolean;
  selectedNodeId: string;
  protocol: 'WireGuard' | 'Multi-Hop WG' | 'Tor Onion v3';
  dohProvider: 'mullvad' | 'adguard' | 'nextdns' | 'cloudflare' | 'quad9';
  killSwitch: boolean;
  blockWebRtcLeak: boolean;
  splitTunnelBypassLocal: boolean;
  encryptedMb: number;
}

export interface ShieldState {
  enabled: boolean;
  blockAds: boolean;
  blockTrackers: boolean;
  blockCookieBanners: boolean;
  stripTrackingParams: boolean;
  antiFingerprinting: boolean;
  httpsEverywhere: boolean;
  blockCryptominers: boolean;
  totalAdsBlocked: number;
  totalTrackersBlocked: number;
  totalDataSavedMb: number;
  totalTimeSavedSec: number;
}

export interface DownloadItem {
  id: string;
  filename: string;
  url: string;
  sizeMb: number;
  downloadedMb: number;
  threads: number;
  speedMbps: number;
  status: 'downloading' | 'completed' | 'paused';
  category: 'apk' | 'video' | 'archive' | 'document' | 'extension';
}

export interface AiChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  bullets?: string[];
  inferenceMs?: number;
  engineLabel?: string;
  mode?: string;
  timestamp: string;
}

export interface BrowserCustomization {
  theme: ThemePresetId;
  customAccentHex: string;
  addressBarPosition: AddressBarPosition;
  searchEngine: SearchEngineId;
  /** Which AI engine answers. 'auto' rotates, an id pins that engine first. */
  aiEngine: string;
  uiDensity: 'compact' | 'comfortable';
  biometricLockIncognito: boolean;
  readerFont: 'sans' | 'serif' | 'mono';
  readerTheme: 'amoled' | 'sepia' | 'slate';
  readerFontSize: number;
}
