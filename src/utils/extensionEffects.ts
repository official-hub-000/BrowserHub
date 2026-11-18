import type { BrowserExtension } from '../types/browser';
import { buildInjection as buildRuntimeInjection } from './webextensionRuntime';

/**
 * Which extension behaviours are genuinely executed by this build.
 *
 * Everything listed here maps to code that changes a real page or a real
 * request: `ublock` / `privacy-badger` filter requests natively, `dark-reader`
 * and `cookie-killer` inject CSS, `clear-urls` rewrites URLs before navigation,
 * `custom-css` / `custom-script` inject author-supplied content, and `bg-play`
 * keeps media playing (the native WebView never suspends it on tab switch).
 *
 * Anything not listed is a store listing / packaging preview only: those cards
 * say so instead of pretending to run MV3 code inside Android WebView.
 */
export const ENFORCED_EFFECT_KEYS = new Set([
  'ublock',
  'privacy-badger',
  'dark-reader',
  'cookie-killer',
  'clear-urls',
  'custom-css',
  'custom-script',
  'bg-play',
  // Sideloaded WebExtensions execute their own content scripts.
  'webextension',
]);

export function isExtensionEnforced(ext: Pick<BrowserExtension, 'effectKey'>): boolean {
  return Boolean(ext.effectKey && ENFORCED_EFFECT_KEYS.has(ext.effectKey));
}

/**
 * Background Play (PiP/media) is enforced by the shell itself: the native
 * WebView is never paused or blanked when you switch tabs, and media keeps its
 * audio focus. Pages only need to be told they may keep playing.
 */
export function isBackgroundPlayEnabled(extensions: BrowserExtension[]): boolean {
  return extensions.some((e) => e.enabled && e.effectKey === 'bg-play');
}

/** True when the user has an enabled Stylus-style custom CSS extension. */
export function hasCustomCss(extensions: BrowserExtension[]): boolean {
  return extensions.some((e) => e.enabled && e.effectKey === 'custom-css' && Boolean(e.customCss));
}

/** True when the user has an enabled Userscript extension with code. */
export function hasUserScript(extensions: BrowserExtension[]): boolean {
  return extensions.some((e) => e.enabled && e.effectKey === 'custom-script' && Boolean(e.customJs));
}

/** Re-sends the author's custom JS/CSS so an edit takes effect immediately. */
export function collectInjectedAssets(extensions: BrowserExtension[]): {
  css: string;
  js: string;
} {
  const active = extensions.filter((e) => e.enabled);
  return {
    css: active
      .filter((e) => e.effectKey === 'custom-css' || e.effectKey === 'dark-reader')
      .map((e) => e.customCss || '')
      .filter(Boolean)
      .join('\n'),
    js: active
      .filter((e) => e.effectKey === 'custom-script')
      .map((e) => e.customJs || '')
      .filter(Boolean)
      .join('\n;\n'),
  };
}

/**
 * Combines every enabled sideloaded WebExtension that applies to this page.
 *
 * Each package's own content scripts are matched against the URL by the runtime,
 * so an extension scoped to `*://*.youtube.com/*` never runs on another site.
 * Returns empty strings when nothing applies, which the caller treats as a no-op.
 */
export function buildExtensionInjection(
  extensions: BrowserExtension[],
  pageUrl: string
): { css: string; js: string } {
  let css = '';
  let js = '';
  for (const ext of extensions) {
    if (!ext.enabled || ext.effectKey !== 'webextension' || !ext.runtime) continue;
    const injection = buildRuntimeInjection(ext.runtime, pageUrl);
    if (!injection) continue;
    css += `\n/* --- ${ext.name} --- */\n${injection.css}`;
    js += `\n/* --- ${ext.name} --- */\n${injection.js}`;
  }
  return { css, js };
}
