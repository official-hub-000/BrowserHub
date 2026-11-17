/**
 * Extension catalogue.
 *
 * The request was to install "any extension, like Chrome, Edge or Firefox".
 * Android WebView has no extension engine at all — there is no `chrome.runtime`,
 * no content-script injection, and `.crx`/`.xpi` archives cannot be executed in
 * it. That is a platform fact, not a missing setting.
 *
 * So this catalogue is organised around what actually happens when you tap
 * Install:
 *
 *   runs-here   — BrowserHub has its own native implementation. It really works.
 *   importable  — the real package can be imported and inspected, and any CSS or
 *                 userscript part is applied. The rest cannot execute.
 *   store-link  — the real listing opens in a tab; the code cannot be installed.
 *
 * Every entry carries an explicit `works` status so nothing is oversold.
 */

import type { BrowserExtension } from '../types/browser';

export type ExtensionSupport = 'runs-here' | 'importable' | 'store-link';

export interface CatalogueEntry extends Omit<BrowserExtension, 'enabled' | 'allowIncognito'> {
  support: ExtensionSupport;
  /** The plain-English truth about this entry on Android WebView. */
  works: string;
  /** Official listing, opened in a tab for imports and store links. */
  storeUrl?: string;
  /** What BrowserHub actually does instead, when it runs here. */
  implementation?: string;
}

export const EXTENSION_CATALOGUE: CatalogueEntry[] = [
  /* ----------------------------- runs here ------------------------------- */
  {
    id: 'ublock-origin',
    name: 'uBlock Origin',
    version: '1.62.0',
    author: 'Raymond Hill',
    source: 'global-hub',
    format: 'NATIVE-MOD',
    description: 'Wide-spectrum content blocker: network filters, cosmetic filters and scriptlets.',
    icon: 'shield',
    permissions: ['webRequest', 'storage', '<all_urls>'],
    effectKey: 'ublock',
    rating: '4.9 ★',
    usersCount: '40M+',
    support: 'runs-here',
    works: 'Blocking runs in the native WebView request path — filters really stop requests.',
    implementation: 'Native request interception plus cosmetic CSS injection.',
    storeUrl: 'https://chromewebstore.google.com/detail/ublock-origin/cjpalhdlnbpafiamejdnhcphjbkeiagm',
  },
  {
    id: 'privacy-badger',
    name: 'Privacy Badger (EFF)',
    version: '2026.9.1',
    author: 'Electronic Frontier Foundation',
    source: 'firefox-amo',
    format: 'NATIVE-MOD',
    description: 'Automatically learns to block invisible behavioural trackers across domains.',
    icon: 'shield',
    permissions: ['webRequest', 'storage', '<all_urls>'],
    effectKey: 'privacy-badger',
    rating: '4.8 ★',
    usersCount: '3.4M+',
    support: 'runs-here',
    works: 'Tracker blocking is enforced natively; the full heuristic learning model is not.',
    implementation: 'Native tracker-domain blocking with a built-in list.',
    storeUrl: 'https://addons.mozilla.org/firefox/addon/privacy-badger17/',
  },
  {
    id: 'dark-reader',
    name: 'Dark Reader',
    version: '4.9.103',
    author: 'Alexander Shutau',
    source: 'global-hub',
    format: 'NATIVE-MOD',
    description: 'Generates dark themes for any website, with per-site contrast control.',
    icon: 'moon',
    permissions: ['activeTab', 'storage', '<all_urls>'],
    effectKey: 'dark-reader',
    rating: '4.7 ★',
    usersCount: '5M+',
    support: 'runs-here',
    works: 'A real dark stylesheet is injected into every page. The full algorithm is simplified.',
    implementation: 'Injected dark CSS filter layer.',
    storeUrl: 'https://chromewebstore.google.com/detail/dark-reader/eimadpbcbfnmbkopoojfekhnkhdbieeh',
  },
  {
    id: 'stylus-css',
    name: 'Stylus Custom UserCSS',
    version: '1.5.51',
    author: 'OpenStyles',
    source: 'chrome-web-store',
    format: 'NATIVE-MOD',
    description: 'Redesign the web with custom CSS themes for Reddit, GitHub, YouTube, Wikipedia & more.',
    icon: 'css',
    permissions: ['tabs', 'webNavigation', 'storage'],
    effectKey: 'custom-css',
    rating: '4.8 ★',
    usersCount: '1.8M+',
    support: 'runs-here',
    works: 'Ships with a working dark stylesheet injected into every page; edit it in Studio.',
    implementation: 'Author CSS injected into every document.',
    /*
     * BUG THIS FIXES (reported as "extension not working, I can't add any"):
     * hasCustomCss() requires a non-empty `customCss`, so installing this entry
     * with no stylesheet installed an extension that provably did nothing.
     */
    customCss: `/* BrowserHub Stylus — default dark polish. Edit in the Studio tab. */
html { color-scheme: dark !important; }
body { background-color: #0b0b0e !important; }
::-webkit-scrollbar { width: 8px; height: 8px; }
::-webkit-scrollbar-track { background: #08080b; }
::-webkit-scrollbar-thumb { background: #22d3ee55; border-radius: 8px; }
::selection { background: #22d3ee44 !important; }
img, video { border-radius: 6px; }`,
    storeUrl: 'https://chromewebstore.google.com/detail/stylus/clngdbkpkpeebahjckkjfobafhncgmne',
  },
  {
    id: 'singlefile-archive',
    name: 'SingleFile Offline Page Saver',
    version: '1.22.74',
    author: 'Gildas Lormeau',
    source: 'firefox-amo',
    format: 'CRX3',
    description: 'Saves a complete webpage (images, fonts, CSS) into one offline HTML file.',
    icon: 'archive',
    permissions: ['downloads', 'activeTab', 'scripting'],
    rating: '4.9 ★',
    usersCount: '1.2M+',
    support: 'store-link',
    works: 'Listing only. Page archiving is not built into this shell yet, so nothing would be saved.',
    storeUrl: 'https://addons.mozilla.org/firefox/addon/single-file/',
  },
  {
    id: 'cookie-killer',
    name: 'Cookie AutoDelete',
    version: '3.8.4',
    author: 'Cadt',
    source: 'global-hub',
    format: 'NATIVE-MOD',
    description: 'Clears cookies and local storage as soon as a tab is closed.',
    icon: 'cookie',
    permissions: ['cookies', 'storage', 'tabs'],
    effectKey: 'cookie-killer',
    rating: '4.6 ★',
    usersCount: '300K+',
    support: 'runs-here',
    works: 'Cookie and storage clearing runs in the native shell when a tab closes.',
    implementation: 'Native cookie/storage purge on tab close.',
    storeUrl: 'https://chromewebstore.google.com/detail/cookie-autodelete/fhcgjolkccmbidfldfkfkviejdgmohfk',
  },
  {
    id: 'clear-urls',
    name: 'ClearURLs',
    version: '1.27.3',
    author: 'Kevin R. (ClearURLs)',
    source: 'chrome-web-store',
    format: 'NATIVE-MOD',
    description: 'Removes tracking parameters (utm_*, fbclid, gclid) from every link before it loads.',
    icon: 'link',
    permissions: ['webRequest', 'storage', '<all_urls>'],
    effectKey: 'clear-urls',
    rating: '4.7 ★',
    usersCount: '500K+',
    support: 'runs-here',
    works: 'URLs are rewritten before navigation, so trackers never see the parameter.',
    implementation: 'Pre-navigation URL rewriting.',
    storeUrl: 'https://chromewebstore.google.com/detail/clearurls/lckanjgmijmafbedllaakclkaicjfmnk',
  },
  {
    id: 'dark-mode-youtube',
    name: 'Dark Mode + Turn Off the Lights',
    version: '2.4.1',
    author: 'BrowserHub',
    source: 'global-hub',
    format: 'NATIVE-MOD',
    description: 'Dims the page around a video and forces night colours on media sites.',
    icon: 'moon',
    permissions: ['storage', '<all_urls>'],
    effectKey: 'dark-reader',
    rating: '4.5 ★',
    usersCount: '120K+',
    support: 'runs-here',
    works: 'Applies the same native dark CSS layer; cinema dimming is a page style, not a script.',
    implementation: 'Injected page style.',
  },
  {
    id: 'userscript-runner',
    name: 'Userscript Runner (Tampermonkey-style)',
    version: '1.1.0',
    author: 'BrowserHub',
    source: 'userscript',
    format: 'USER.JS',
    description: 'Runs your own Greasemonkey/Tampermonkey .user.js snippets on matching sites.',
    icon: 'code',
    permissions: ['scripting', 'storage'],
    effectKey: 'custom-script',
    rating: '4.4 ★',
    usersCount: '80K+',
    support: 'runs-here',
    works: 'Runs a real userscript in every page. Ships with a small one you can replace in Studio.',
    implementation: 'Author script injected into the document.',
    // Same bug as Stylus above: hasUserScript() needs non-empty `customJs`.
    customJs: `/* BrowserHub userscript example — replace it in the Studio tab.
   GM_* helper APIs are not available; plain DOM code works. */
(function () {
  try {
    document.documentElement.setAttribute('data-browserhub-userscript', 'on');
    document.querySelectorAll('a').forEach(function (a) {
      a.style.touchAction = 'manipulation';
    });
  } catch (e) {
    /* a page without a document is not an error worth surfacing */
  }
})();`,
    storeUrl: 'https://greasyfork.org/en',
  },
  {
    id: 'bg-play',
    name: 'Background Play',
    version: '1.0.4',
    author: 'BrowserHub',
    source: 'global-hub',
    format: 'NATIVE-MOD',
    description: 'Keeps audio and video playing when you switch tabs or minimise the browser.',
    icon: 'play',
    permissions: ['tabs'],
    effectKey: 'bg-play',
    rating: '4.6 ★',
    usersCount: '210K+',
    support: 'runs-here',
    works: 'The native WebView is never suspended on tab switch, so playback genuinely continues.',
    implementation: 'Native media focus retention.',
  },

  /* ---------------------------- importable ------------------------------- */
  {
    id: 'vimium-mobile',
    name: 'Vimium C — Keyboard & Gesture Nav',
    version: '2.11.3',
    author: 'gdh1995',
    source: 'chrome-web-store',
    format: 'CRX3',
    description: 'Power-user spatial link hints, external keyboard shortcuts and an instant tab jumper.',
    icon: 'keyboard',
    permissions: ['tabs', 'bookmarks', '<all_urls>'],
    rating: '4.9 ★',
    usersCount: '950K+',
    support: 'importable',
    works: 'The package imports and its shortcuts are listed, but its MV3 content scripts cannot run in WebView.',
    storeUrl: 'https://chromewebstore.google.com/detail/vimium-c/ihlenndgcmojhcghmfjfneahoeklbjjh',
  },
  {
    id: 'dearrow',
    name: 'DeArrow — Anti-Clickbait Titles',
    version: '1.8.2',
    author: 'Ajay Ramachandran',
    source: 'chrome-web-store',
    format: 'CRX3',
    description: 'Replaces sensationalist ALL-CAPS titles and clickbait thumbnails with community titles.',
    icon: 'target',
    permissions: ['youtube.com/*', 'storage'],
    rating: '4.9 ★',
    usersCount: '620K+',
    support: 'importable',
    works: 'Imports for inspection only — the extension cannot rewrite YouTube titles inside WebView.',
    storeUrl: 'https://chromewebstore.google.com/detail/dearrow/bonqlfgdmjlgdbjdeaodpnjhfjnjhplp',
  },
  {
    id: 'bitwarden-vault',
    name: 'Bitwarden Password Manager',
    version: '2026.10.0',
    author: 'Bitwarden Inc.',
    source: 'global-hub',
    format: 'NATIVE-MOD',
    description: 'Autofill logins from an encrypted vault.',
    icon: 'lock',
    permissions: ['storage', 'activeTab'],
    effectKey: 'bitwarden',
    rating: '4.7 ★',
    usersCount: '1.5M+',
    support: 'importable',
    works: 'The vault UI opens; autofill into page forms is not wired into WebView input yet.',
    storeUrl: 'https://chromewebstore.google.com/detail/bitwarden-free-password-m/nngceckbapebfimnlniiiahkandclblb',
  },

  /* ---------------------------- store links ------------------------------ */
  {
    id: 'sponsorblock',
    name: 'SponsorBlock for YouTube',
    version: '5.9.2',
    author: 'Ajay Ramachandran',
    source: 'chrome-web-store',
    format: 'CRX3',
    description: 'Skips sponsor segments in YouTube videos using community-submitted timestamps.',
    icon: 'skip',
    permissions: ['youtube.com/*', 'storage'],
    effectKey: 'sponsorblock',
    rating: '4.8 ★',
    usersCount: '2M+',
    support: 'store-link',
    works: 'Listing only. WebView cannot run its content script, so it does not skip anything here.',
    storeUrl: 'https://chromewebstore.google.com/detail/sponsorblock-for-youtube/mnjggcdmjocbbbhaepdhchncahnbgone',
  },
  {
    id: 'return-dislike',
    name: 'Return YouTube Dislike',
    version: '3.0.0',
    author: 'RYD',
    source: 'chrome-web-store',
    format: 'CRX3',
    description: 'Restores the dislike counter on YouTube videos.',
    icon: 'thumbs-down',
    permissions: ['youtube.com/*'],
    effectKey: 'return-dislike',
    rating: '4.6 ★',
    usersCount: '1.1M+',
    support: 'store-link',
    works: 'Listing only — the counter needs its content script, which WebView cannot execute.',
    storeUrl: 'https://chromewebstore.google.com/detail/return-youtube-dislike/gebbhagfogifgggkldgodflihgfeippi',
  },
  {
    id: 'bypass-paywall',
    name: 'Bypass Paywalls Clean',
    version: '3.4.2',
    author: 'magnolia1234',
    source: 'chrome-web-store',
    format: 'CRX3',
    description: 'Reads articles on sites that meter content.',
    icon: 'unlock',
    permissions: ['<all_urls>'],
    effectKey: 'bypass-paywall',
    rating: '4.2 ★',
    usersCount: '400K+',
    support: 'store-link',
    works: 'Listing only, and note that it removes sites’ own access controls — use at your own risk.',
    storeUrl: 'https://gitflic.ru/project/magnolia1234/bypass-paywalls-chrome-clean',
  },
  {
    id: 'firefox-multi-account',
    name: 'Firefox Multi-Account Containers',
    version: '8.1.4',
    author: 'Mozilla',
    source: 'firefox-amo',
    format: 'XPI',
    description: 'Keeps work, shopping and personal browsing in isolated cookie jars.',
    icon: 'container',
    permissions: ['cookies', 'tabs', 'storage'],
    rating: '4.6 ★',
    usersCount: '700K+',
    support: 'store-link',
    works: 'Listing only. Container isolation is a Firefox feature with no WebView equivalent; use Incognito tabs for separation.',
    storeUrl: 'https://addons.mozilla.org/firefox/addon/multi-account-containers/',
  },
];

/** Entries that genuinely change a page or a request on this build. */
export const RUNS_HERE = EXTENSION_CATALOGUE.filter((e) => e.support === 'runs-here');

/** Grouped for the store UI. */
export const CATALOGUE_GROUPS: { id: ExtensionSupport; label: string; hint: string }[] = [
  {
    id: 'runs-here',
    label: 'Working here',
    hint: 'BrowserHub has its own implementation. These really do something.',
  },
  {
    id: 'importable',
    label: 'Import only',
    hint: 'The real package downloads and can be inspected; its scripts cannot execute in WebView.',
  },
  {
    id: 'store-link',
    label: 'Listing only',
    hint: 'Opens the official store page. The extension cannot run in Android WebView.',
  },
];

export const SUPPORT_BADGE: Record<ExtensionSupport, { label: string; tone: 'green' | 'amber' | 'slate' }> = {
  'runs-here': { label: 'WORKING', tone: 'green' },
  importable: { label: 'IMPORT ONLY', tone: 'amber' },
  'store-link': { label: 'NOT WORKING HERE', tone: 'slate' },
};

export function catalogueEntry(id: string): CatalogueEntry | undefined {
  return EXTENSION_CATALOGUE.find((e) => e.id === id);
}

/** Store links a user can paste to reach a real listing. */
export const STORE_DOMAINS = [
  { match: 'chromewebstore.google.com', label: 'Chrome Web Store' },
  { match: 'chrome.google.com/webstore', label: 'Chrome Web Store' },
  { match: 'addons.mozilla.org', label: 'Firefox Add-ons' },
  { match: 'microsoftedge.microsoft.com', label: 'Edge Add-ons' },
  { match: 'greasyfork.org', label: 'Greasy Fork userscripts' },
  { match: 'openuserjs.org', label: 'OpenUserJS' },
] as const;

/**
 * The actual storefronts, as browsable destinations.
 *
 * The request was "there's no Firefox/Edge/Chrome extension store to use them".
 * This build cannot *run* those add-ons (Android WebView has no extension
 * engine), but it can absolutely take you to the genuine stores so you can
 * search them, read listings, and download files. These URLs are the real
 * top-level storefronts, not made-up deep links.
 */
export interface StoreFrontCategory {
  label: string;
  url: string;
}

export interface StoreFront {
  id: string;
  name: string;
  icon: string;
  count: string;
  blurb: string;
  url: string;
  categories: StoreFrontCategory[];
}

export const STORE_FRONTS: StoreFront[] = [
  {
    id: 'chrome-web-store',
    name: 'Chrome Web Store',
    icon: 'chrome',
    count: '190,000+ add-ons',
    blurb:
      'Google’s store. Search it, read ratings and changelogs, and download any .crx — but Chrome for Android itself has never supported extensions, and neither does WebView.',
    url: 'https://chromewebstore.google.com/',
    categories: [
      { label: 'Productivity', url: 'https://chromewebstore.google.com/category/extensions/productivity/workflow' },
      { label: 'Privacy', url: 'https://chromewebstore.google.com/category/extensions/productivity/tools' },
      { label: 'Search', url: 'https://chromewebstore.google.com/search/extensions' },
    ],
  },
  {
    id: 'edge-addons',
    name: 'Microsoft Edge Add-ons',
    icon: 'edge',
    count: '20,000+ add-ons',
    blurb:
      'Microsoft’s store. Same model as Chrome’s — the listings are real and browsable, and the packages are Chromium CRX3, which WebView cannot execute.',
    url: 'https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home',
    categories: [
      { label: 'Productivity', url: 'https://microsoftedge.microsoft.com/addons/category/Productivity' },
      { label: 'Privacy', url: 'https://microsoftedge.microsoft.com/addons/category/Privacy' },
      { label: 'Search', url: 'https://microsoftedge.microsoft.com/addons/search' },
    ],
  },
  {
    id: 'firefox-amo',
    name: 'Firefox Add-ons (AMO)',
    icon: 'firefox',
    count: '30,000+ add-ons',
    blurb:
      'Mozilla’s official store, including full theme and userscript support. Packages are .xpi (a signed ZIP) and can be downloaded and opened, but not loaded by WebView.',
    url: 'https://addons.mozilla.org/android/',
    categories: [
      { label: 'Recommended', url: 'https://addons.mozilla.org/firefox/extensions/' },
      { label: 'Themes', url: 'https://addons.mozilla.org/firefox/themes/' },
      { label: 'Search', url: 'https://addons.mozilla.org/firefox/search/' },
    ],
  },
  {
    id: 'greasyfork',
    name: 'Greasy Fork userscripts',
    icon: 'code',
    count: '110,000+ scripts',
    blurb:
      'The largest Tampermonkey/Greasemonkey library. Unlike add-ons, plain userscripts CAN run here — paste one into the Studio tab and it is injected for real.',
    url: 'https://greasyfork.org/en',
    categories: [
      { label: 'Popular', url: 'https://greasyfork.org/en/scripts?sort=total_installs' },
      { label: 'Safety', url: 'https://greasyfork.org/en/scripts?sort=created' },
      { label: 'Search', url: 'https://greasyfork.org/en/scripts' },
    ],
  },
  {
    id: 'openuserjs',
    name: 'OpenUserJS',
    icon: 'code',
    count: '10,000+ scripts',
    blurb:
      'Community userscript mirror. Same story as Greasy Fork: the script text itself runs here through the Studio tab.',
    url: 'https://openuserjs.org/',
    categories: [
      { label: 'Popular', url: 'https://openuserjs.org/scripts?q=&order=installs' },
      { label: 'Libraries', url: 'https://openuserjs.org/libs' },
      { label: 'Search', url: 'https://openuserjs.org/scripts' },
    ],
  },
];
