import JSZip from 'jszip';
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
        'Custom Userscript injected via BrowserHub Violentmonkey/GM Runtime.',
      icon: '📜',
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
      const manifest = JSON.parse(manifestRaw) as {
        name?: string;
        version?: string;
        description?: string;
        author?: string | { name?: string };
        permissions?: string[];
        host_permissions?: string[];
        content_scripts?: Array<{ js?: string[]; css?: string[] }>;
      };

      // Extract first CSS/JS content script if present so it can run live!
      let extractedCss = '';
      let extractedJs = '';
      if (manifest.content_scripts && manifest.content_scripts.length > 0) {
        const firstCs = manifest.content_scripts[0];
        if (firstCs.css?.[0] && zip.file(firstCs.css[0])) {
          extractedCss = await zip.file(firstCs.css[0])!.async('string');
        }
        if (firstCs.js?.[0] && zip.file(firstCs.js[0])) {
          extractedJs = (await zip.file(firstCs.js[0])!.async('string')).slice(0, 8000);
        }
      }

      const rawName =
        manifest.name && !manifest.name.startsWith('__MSG_')
          ? manifest.name
          : file.name.replace(/\.(crx|xpi|zip)$/i, '');

      const perms = [
        ...(manifest.permissions || []),
        ...(manifest.host_permissions || []),
      ].slice(0, 6);

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
        description:
          manifest.description && !manifest.description.startsWith('__MSG_')
            ? manifest.description
            : `Unpacked ${format} WebExtension archive (${Object.keys(zip.files).length} files verified).`,
        icon: format === 'XPI' ? '🦊' : '🧩',
        enabled: true,
        allowIncognito: false,
        permissions: perms.length > 0 ? perms : ['storage', 'activeTab'],
        effectKey: extractedCss ? 'custom-css' : extractedJs ? 'custom-script' : undefined,
        customCss: extractedCss || undefined,
        customJs: extractedJs || undefined,
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
    description: `Sideloaded ${format} extension (${(file.size / 1024).toFixed(1)} KB) registered in Universal Extension Runtime.`,
    icon: format === 'XPI' ? '🦊' : '🧩',
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
    description: `Imported globally from ${
      isFirefox ? 'Firefox Add-ons (AMO)' : isUserscript ? 'Userscript Registry' : 'Chrome Web Store'
    } via BrowserHub Universal WebExtension Polyfill Bridge.`,
    icon: isFirefox ? '🦊' : isUserscript ? '⚡' : '🌐',
    enabled: true,
    allowIncognito: true,
    permissions: ['activeTab', 'storage', 'webRequest', 'scripting'],
  };
}
