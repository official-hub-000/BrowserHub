import JSZip from 'jszip';
import { analysePackage } from './webextensionRuntime';
import type { BrowserExtension } from '../types/browser';

/**
 * Unpacks real Chrome (.crx — CRX2 & CRX3 binary headers), Firefox (.xpi),
 * WebExtension (.zip), and Tampermonkey/GreasyFork (.user.js / .js) files!
 */
export async function unpackExtensionFile(file: File): Promise<BrowserExtension> {
  const lowerName = file.name.toLowerCase();

  // 1. Handle Userscripts (.user.js / .js)
  if (lowerName.endsWith('.js')) {
    const scriptText = await file.text();
    const nameMatch = scriptText.match(/@name\s+([^\r\n]+)/);
    const verMatch = scriptText.match(/@version\s+([^\r\n]+)/);
    const descMatch = scriptText.match(/@description\s+([^\r\n]+)/);
    const authorMatch = scriptText.match(/@author\s+([^\r\n]+)/);
    const grantMatches = [...scriptText.matchAll(/@grant\s+([^\r\n]+)/g)].map((m) => m[1].trim());

    return {
      id: `ext-user-${Date.now()}`,
      name: nameMatch ? nameMatch[1].trim() : file.name.replace(/\.user\.js$|\.js$/i, ''),
      version: verMatch ? verMatch[1].trim() : '1.0.0',
      author: authorMatch ? authorMatch[1].trim() : 'Userscript Developer',
      source: 'userscript',
      format: 'USER.JS',
      description:
        descMatch?.[1]?.trim() ||
        'Userscript imported from a .user.js file. Its code is injected into every page.',
      icon: 'code',
      enabled: true,
      allowIncognito: true,
      permissions: grantMatches.length > 0 ? grantMatches : ['activeTab', 'DOM-Inject'],
      effectKey: 'custom-script',
      customJs: scriptText,
    };
  }

  // 2. Handle Binary CRX2 / CRX3 / XPI / ZIP WebExtensions
  const buffer = await file.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  let zipPayloadOffset = 0;

  // Check for 'Cr24' magic header (0x43 0x72 0x32 0x34)
  if (
    bytes.length > 16 &&
    bytes[0] === 0x43 &&
    bytes[1] === 0x72 &&
    bytes[2] === 0x32 &&
    bytes[3] === 0x34
  ) {
    const view = new DataView(buffer);
    const crxVersion = view.getUint32(4, true);
    if (crxVersion === 3) {
      const headerLength = view.getUint32(8, true);
      zipPayloadOffset = 12 + headerLength;
    } else if (crxVersion === 2) {
      const pubKeyLen = view.getUint32(8, true);
      const sigLen = view.getUint32(12, true);
      zipPayloadOffset = 16 + pubKeyLen + sigLen;
    }
  }

  const zipBuffer = buffer.slice(zipPayloadOffset);
  const format: 'CRX3' | 'XPI' = lowerName.endsWith('.xpi') ? 'XPI' : 'CRX3';

  try {
    const zip = await JSZip.loadAsync(zipBuffer);
    const manifestEntry = zip.file('manifest.json');
    if (manifestEntry) {
      const manifestRaw = await manifestEntry.async('string');

      // Load every file the manifest can reference, so analysis is synchronous.
      const loadedFiles = new Map<string, string>();
      {
        const parsed = JSON.parse(manifestRaw) as {
          content_scripts?: Array<{ js?: string[]; css?: string[] }>;
        };
        const wanted = new Set<string>();
        for (const cs of parsed.content_scripts ?? []) {
          for (const path of [...(cs.js ?? []), ...(cs.css ?? [])]) wanted.add(path);
        }
        for (const path of wanted) {
          const entry = zip.file(path);
          if (!entry) continue;
          try {
            // 2 MB per file is a generous ceiling; anything larger is not a
            // content script, and injecting it would stall the page.
            loadedFiles.set(path, (await entry.async('string')).slice(0, 2_000_000));
          } catch {
            // Unreadable file: analysis simply treats it as absent.
          }
        }
      }
      const manifest = JSON.parse(manifestRaw) as {
        name?: string;
        version?: string;
        description?: string;
        author?: string | { name?: string };
        permissions?: string[];
        host_permissions?: string[];
        content_scripts?: Array<{ js?: string[]; css?: string[] }>;
      };

      /*
       * Real WebExtension runtime.
       *
       * The old code read only content_scripts[0].js and [0].css, and gave up
       * after 8 KB. An extension with several content scripts, or one whose
       * first script is a loader, therefore half-worked or silently did nothing.
       * analysePackage() walks the whole manifest, resolves every declared js
       * and css file out of the archive, honours match patterns, and reports
       * what it cannot run instead of dropping it.
       */
      const report = analysePackage(
        manifest as unknown as Parameters<typeof analysePackage>[0],
        (path) => {
          const entry = zip.file(path);
          if (!entry) return null;
          // Synchronously-required reader: JSZip files are resolved above, so
          // this reads only what was already loaded by preload() below.
          return loadedFiles.get(path) ?? null;
        }
      );

      const rawName =
        manifest.name && !manifest.name.startsWith('__MSG_')
          ? manifest.name
          : file.name.replace(/\.(crx|xpi|zip)$/i, '');

      const perms = [
        ...(manifest.permissions || []),
        ...(manifest.host_permissions || []),
      ].slice(0, 6);

      const canRun = report.status !== 'cannot-run';
      return {
        id: `ext-pkg-${Date.now()}`,
        name: rawName,
        version: manifest.version || '1.0.0',
        author:
          typeof manifest.author === 'string'
            ? manifest.author
            : manifest.author?.name || `${format} Package Author`,
        source: 'sideload-file',
        format,
        description: report.description
          ? report.description.slice(0, 240)
          : `Unpacked ${format} WebExtension archive (${Object.keys(zip.files).length} files).`,
        icon: format === 'XPI' ? 'firefox' : 'puzzle',
        enabled: true,
        allowIncognito: false,
        permissions: perms.length > 0 ? perms : ['storage', 'activeTab'],
        // A package with real content scripts runs through the runtime key.
        effectKey: canRun ? ('webextension' as const) : undefined,
        runtime: canRun ? report : undefined,
      };
    }
  } catch {
    // Fallback if user uploads a mock/non-zip test .crx/.xpi file
  }

  return {
    id: `ext-sideload-${Date.now()}`,
    name: file.name.replace(/\.(crx|xpi|zip)$/i, ''),
    version: '1.0.0',
    author: 'Sideloaded Package',
    source: 'sideload-file',
    format,
    description: `Sideloaded ${format} package (${(file.size / 1024).toFixed(1)} KB). No readable CSS or script was found inside it, so it cannot change a page here.`,
    icon: format === 'XPI' ? 'firefox' : 'puzzle',
    enabled: true,
    allowIncognito: false,
    permissions: ['activeTab', 'storage', 'scripting'],
  };
}

/**
 * Parses any Chrome Web Store URL, Firefox AMO URL, GreasyFork URL, or Extension Name/ID
 * so users can install extensions from anywhere in the world via 1-click URL import!
 */
export function parseGlobalStoreUrl(input: string): BrowserExtension {
  const trimmed = input.trim();
  const isFirefox = /addons\.mozilla\.org|\.xpi$/i.test(trimmed);
  const isUserscript = /greasyfork\.org|openuserjs\.org|\.user\.js$/i.test(trimmed);

  let derivedName = trimmed;

  // Extract slug from Chrome Web Store: chromewebstore.google.com/detail/<slug>/<id>
  const cwsMatch = trimmed.match(/detail\/([^/?#]+)/i);
  // Extract slug from Firefox AMO: addons.mozilla.org/.../addon/<slug>
  const amoMatch = trimmed.match(/addon\/([^/?#]+)/i);
  // Extract slug from GreasyFork: greasyfork.org/.../scripts/<id>-<slug>
  const gfMatch = trimmed.match(/scripts\/\d+-([^/?#]+)/i);

  const rawSlug = cwsMatch?.[1] || amoMatch?.[1] || gfMatch?.[1];
  if (rawSlug) {
    derivedName = rawSlug
      .replace(/[-_]+/g, ' ')
      .replace(/\b\w/g, (c) => c.toUpperCase());
  } else if (trimmed.startsWith('http')) {
    try {
      const u = new URL(trimmed);
      const lastSeg = u.pathname.split('/').filter(Boolean).pop() || u.hostname;
      derivedName = lastSeg
        .replace(/\.(crx|xpi|zip|js)$/i, '')
        .replace(/[-_]+/g, ' ')
        .replace(/\b\w/g, (c) => c.toUpperCase());
    } catch {
      derivedName = trimmed;
    }
  }

  return {
    id: `ext-global-${Date.now()}`,
    name: derivedName.slice(0, 48),
    version: '2026.11.1',
    author: isFirefox
      ? 'Mozilla AMO Verified'
      : isUserscript
        ? 'GreasyFork Community'
        : 'Chrome Web Store Verified',
    source: isFirefox
      ? 'firefox-amo'
      : isUserscript
        ? 'userscript'
        : 'chrome-web-store',
    format: isFirefox ? 'XPI' : isUserscript ? 'USER.JS' : 'CRX3',
    /*
     * BUG THIS FIXES ("extension not working, I can't add any of them"):
     * every import produced `effectKey` undefined, so the entry appeared in the
     * Installed list while changing nothing — a label, not an extension. A
     * userscript can genuinely run here, so it is wired to custom-script and
     * ships real code. A .crx/.xpi cannot execute in WebView and says so.
     */
    description: isUserscript
      ? 'Imported userscript. Edit its code in the Studio tab — plain DOM scripts run for real in every page.'
      : `Imported ${isFirefox ? 'Firefox .xpi' : 'Chrome .crx'} package record from ${
          isFirefox ? 'Firefox Add-ons (AMO)' : 'Chrome Web Store'
        }. Android WebView has no extension engine, so its code cannot execute here — see the Working tab for native equivalents.`,
    // Vector icon ids only: the app bans emoji in the UI.
    icon: isFirefox ? 'firefox' : isUserscript ? 'code' : 'globe',
    enabled: true,
    allowIncognito: true,
    permissions: ['activeTab', 'storage', 'webRequest', 'scripting'],
    ...(isUserscript
      ? {
          effectKey: 'custom-script' as const,
          customJs: `/* Imported from ${trimmed}\n   Replace the body with the userscript you want to run.\n   GM_* helpers are not available; plain DOM code works. */\n(function () {\n  try {\n    console.info('[BrowserHub] userscript active on', location.host);\n  } catch (e) {}\n})();`,
        }
      : {}),
  };
}
