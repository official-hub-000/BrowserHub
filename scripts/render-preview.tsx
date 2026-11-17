/**
 * Renders the app's key surfaces to static HTML with the compiled Tailwind CSS
 * inlined, so a reviewer can open /tmp/preview-*.html in a browser and confirm the
 * layout without a device. Run with: npx tsx scripts/render-preview.tsx
 */
import React from 'react';
import { renderToString } from 'react-dom/server';
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { App } from '../src/App';
import { MenuHubModal } from '../src/components/MenuHubModal';
import { TranslateModal } from '../src/components/TranslateModal';
import { AiCopilotDrawer } from '../src/components/AiCopilotDrawer';
import { GamesHub } from '../src/components/GamesHub';
import { GLOBAL_VPN_NODES } from '../src/data/presets';

if (!(globalThis as any).performance) (globalThis as any).performance = { now: () => Date.now() };

const cssFile = readdirSync('dist/assets').find((f) => f.endsWith('.css'))!;
const css = readFileSync(`dist/assets/${cssFile}`, 'utf8');

const tab: any = {
  id: 't1', title: 'Example Domain', url: 'https://example.com', isIncognito: false,
  isPinned: false, isDesktopMode: false, isReaderMode: false, isHibernated: false,
  groupId: 'main', history: ['browserhub://start', 'https://example.com'], historyIndex: 1,
  adsBlocked: 3, trackersBlocked: 5, bytesSavedKb: 120, translatedLang: null,
  bilingualTranslate: false, zappedSelectors: [], zoomLevel: 100, memoryMb: 30,
};
const shields: any = {
  enabled: true, blockAds: true, blockTrackers: true, blockCookieBanners: true,
  stripTrackingParams: true, antiFingerprinting: true, httpsEverywhere: true,
  blockCryptominers: true, turboDataSaver: false, totalAdsBlocked: 4,
  totalTrackersBlocked: 7, totalDataSavedMb: 2, totalTimeSavedSec: 30,
};
const vpn: any = {
  connected: false, connecting: false, selectedNodeId: GLOBAL_VPN_NODES[0].id,
  protocol: 'WireGuard', dohProvider: 'mullvad', killSwitch: true, blockWebRtcLeak: true,
  splitTunnelBypassLocal: true, encryptedMb: 0,
};
const customization: any = {
  theme: 'cyber-cyan', customAccentHex: '#22d3ee', addressBarPosition: 'top',
  engine: 'hybrid-auto', searchEngine: 'google', phoneFrameMode: false,
  uiDensity: 'comfortable', showBookmarksBar: false, showVirtualCursorInDesktop: true,
  biometricLockIncognito: false, readerFont: 'sans', readerTheme: 'slate', readerFontSize: 17,
};

const surfaces: Record<string, string> = {
  home: renderToString(React.createElement(App)),
  menu: renderToString(
    React.createElement(MenuHubModal, {
      tab, tabsCount: 2, shields, vpn, enabledExtensionsCount: 4, customization,
      zapperMode: false, showDevTools: false, hasSplitView: false, canGoForward: false,
      onClose() {}, onSelectSearchEngine() {}, onNewTab() {}, onGoHome() {}, onReload() {},
      onGoForward() {}, onToggleDesktop() {}, onToggleReader() {}, onOpenTranslate() {},
      onOpenFind() {}, onToggleSplit() {}, onChangeZoom() {}, onOpenShields() {},
      onOpenVpn() {}, onToggleZapper() {}, onFireWipe() {}, onOpenExtensions() {},
      onOpenCustomize() {}, onToggleDevTools() {}, onOpenBenchmark() {}, onOpenAi() {},
      onToggleToolbarPosition() {}, onOpenPlayStore() {},
    } as any)
  ),
  translate: renderToString(
    React.createElement(TranslateModal, {
      activeLang: null, bilingual: false, onSelectLang() {}, onToggleBilingual() {},
      onClose() {},
    })
  ),
  ai: renderToString(
    React.createElement(AiCopilotDrawer, { tab, livePageText: '', initialMode: 'idle', onClose() {} })
  ),
  games: renderToString(React.createElement(GamesHub, { onNavigate() {} })),
};

for (const [name, body] of Object.entries(surfaces)) {
  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=390, initial-scale=1">
<style>${css}</style>
<style>body{margin:0;background:#000000;color:#f1f5f9;font-family:system-ui,sans-serif}.phone{width:390px;height:780px;margin:16px auto;border-radius:24px;overflow:hidden;position:relative;border:1px solid #1a1a1f}#root{height:100%}</style>
</head><body><div class="phone"><div id="root">${body}</div></div></body></html>`;
  writeFileSync(`/tmp/preview-${name}.html`, html);
  console.log(`/tmp/preview-${name}.html  ${body.length} chars`);
}
