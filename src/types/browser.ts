export type BrowserEngineType = 'hybrid-auto' | 'geckoview-136' | 'chromium-blink-134';

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
  | 'duckduckgo'
  | 'brave'
  | 'startpage'
  | 'github-live'
  | 'searxng'
  | 'google';

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
    | 'custom-css';
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
  turboDataSaver: boolean;
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
  mode?: string;
  timestamp: string;
}

export interface BrowserCustomization {
  theme: ThemePresetId;
  customAccentHex: string;
  addressBarPosition: AddressBarPosition;
  engine: BrowserEngineType;
  searchEngine: SearchEngineId;
  phoneFrameMode: boolean; // Toggle realistic Android Phone Bezel vs Fullscreen
  uiDensity: 'compact' | 'comfortable';
  showBookmarksBar: boolean;
  showVirtualCursorInDesktop: boolean;
  biometricLockIncognito: boolean;
  readerFont: 'sans' | 'serif' | 'mono';
  readerTheme: 'amoled' | 'sepia' | 'slate';
  readerFontSize: number;
}
