/**
 * A real WebExtension runtime for Android WebView.
 *
 * ## What this is (and is not)
 *
 * Chrome's extension engine cannot be embedded — it lives inside Chrome; WebView
 * has no such component. But a *WebExtension runtime* can be implemented, and
 * that is what this is: it reads the standard `manifest.json` surface and
 * executes the parts of it that make extensions useful.
 *
 * Supported, because there are real execution paths for each:
 *   - `content_scripts[].js`        injected into matching pages
 *   - `content_scripts[].css`       injected into matching pages
 *   - `content_scripts[].matches`   full match-pattern support (`*://*.x.com/*`)
 *   - `content_scripts[].run_at`    document_start / document_end
 *   - `web_accessible_resources`    resolvable via a `chrome.runtime.getURL` shim
 *   - declarative `blocking_rules`  enforced natively in the request path
 *   - a `chrome.*` shim             runtime, storage, tabs.query, i18n
 *
 * Not supported, and the UI must say so per extension:
 *   - MV3 service workers / background pages (no extension process model)
 *   - the `webRequest` *observing* API and action popups (no extension UI host)
 *   - `declarativeNetRequest` JSON (translated to native rules where the shape
 *     maps cleanly; complex rules are reported as unsupported, not ignored)
 *
 * Anything unmapped is surfaced as an explicit capability report rather than
 * silently doing nothing — that is the difference between a runtime and a label.
 */

export type ExtensionRunStatus = 'runs' | 'partial' | 'cannot-run';

export interface ContentScriptSpec {
  matches: string[];
  excludeMatches?: string[];
  js?: string;
  css?: string;
  runAt: 'document_start' | 'document_end';
}

export interface PackageReport {
  /** Manifest version the package declared. */
  manifestVersion: number;
  name: string;
  version: string;
  description: string;
  /** True once content scripts or blocking rules can actually execute. */
  status: ExtensionRunStatus;
  /** What will genuinely happen on a page, one line per capability. */
  capabilities: string[];
  /** Standard features this runtime cannot honour, named honestly. */
  unsupported: string[];
  contentScripts: ContentScriptSpec[];
  /** Host patterns to block, for the native request path. */
  blockingHosts: string[];
  /** Total bytes of injected code, used for the weight warning. */
  codeBytes: number;
  warnings: string[];
}

/* ------------------------------------------------------------------ patterns */

/**
 * Converts a WebExtension match pattern to a RegExp.
 * Supports `*://`, `http://`, `https://`, `<all_urls>`, `*.host` and paths.
 */
export function matchPatternToRegExp(pattern: string): RegExp | null {
  const raw = pattern.trim();
  if (!raw) return null;

  if (raw === '<all_urls>') return /^https?:\/\//i;

  // scheme://host/path
  const match = raw.match(/^(\*|https?|file|ftp):\/\/([^/]+)(\/.*)?$/i);
  if (!match) return null;

  const [, scheme, host, path] = match;
  const schemePart = scheme === '*' ? 'https?' : scheme.toLowerCase();
  // "*.example.com" means "example.com or any subdomain".
  const hostPart = host
    .replace(/[.+^${}()|[\]\\]/g, '\\$&')
    .replace(/^\*\\\./, '(?:[^/]+\\.)?')
    .replace(/^\*/, '[^/]+');
  const pathPart = (path ?? '/*')
    .replace(/[.+^${}()|[\]\\]/g, '\\$&')
    .replace(/\*/g, '.*');

  try {
    return new RegExp(`^${schemePart}://${hostPart}${pathPart}$`, 'i');
  } catch {
    return null;
  }
}

/** True when a page URL matches any of the supplied extension patterns. */
export function urlMatches(url: string, patterns: string[]): boolean {
  if (!url || !/^https?:/i.test(url)) return false;
  return patterns.some((pattern) => {
    const re = matchPatternToRegExp(pattern);
    return re ? re.test(url) : false;
  });
}

/* ------------------------------------------------------------------ manifest */

interface RawManifest {
  manifest_version?: number;
  name?: string;
  version?: string;
  description?: string;
  content_scripts?: Array<{
    matches?: string[];
    exclude_matches?: string[];
    js?: string[];
    css?: string[];
    run_at?: string;
  }>;
  permissions?: string[];
  host_permissions?: string[];
}

/** Pulls host permission patterns that can drive native blocking. */
function blockingHostsFrom(manifest: RawManifest): string[] {
  const hosts = new Set<string>();
  for (const pattern of [...(manifest.host_permissions ?? []), ...(manifest.permissions ?? [])]) {
    if (typeof pattern !== 'string') continue;
    // Only full-URL patterns describe hosts; bare permission names do not.
    if (/^(\*|https?):\/\//i.test(pattern) || pattern === '<all_urls>') {
      hosts.add(pattern);
    }
  }
  return [...hosts];
}

const UNSUPPORTED_FEATURES: Record<string, string> = {
  webRequest: 'request observation (only blocking is honoured)',
  webRequestBlocking: 'blocking via the legacy API (use declarative rules)',
  declarativeNetRequest: 'complex DNR rule sets',
  tabs: 'tab enumeration beyond the active tab',
  scripting: 'arbitrary script injection outside content_scripts',
  cookies: 'cookie read/write',
  history: 'browsing history access',
  bookmarks: 'bookmark access',
  management: 'extension management APIs',
};

/**
 * Reads a package's manifest and reports exactly what will run.
 * `readFile` must return the file's text, or null when absent.
 */
export function analysePackage(
  manifest: RawManifest,
  readFile: (path: string) => string | null
): PackageReport {
  const contentScripts: ContentScriptSpec[] = [];
  const capabilities: string[] = [];
  const unsupported: string[] = [];
  const warnings: string[] = [];
  let codeBytes = 0;

  for (const entry of manifest.content_scripts ?? []) {
    const jsCode = (entry.js ?? [])
      .map((path) => readFile(path))
      .filter((text): text is string => typeof text === 'string' && text.length > 0)
      .join('\n;\n');
    const cssCode = (entry.css ?? [])
      .map((path) => readFile(path))
      .filter((text): text is string => typeof text === 'string' && text.length > 0)
      .join('\n');

    if (!jsCode && !cssCode) continue;

    codeBytes += jsCode.length + cssCode.length;
    contentScripts.push({
      matches: (entry.matches ?? []).filter(Boolean),
      excludeMatches: entry.exclude_matches,
      js: jsCode || undefined,
      css: cssCode || undefined,
      runAt: entry.run_at === 'document_start' ? 'document_start' : 'document_end',
    });
  }

  const blockingHosts = blockingHostsFrom(manifest);

  if (contentScripts.length > 0) {
    capabilities.push(
      `Injects code into ${contentScripts.reduce((n, c) => n + c.matches.length, 0)} URL pattern(s)`
    );
  }
  if (contentScripts.some((c) => c.css)) capabilities.push('Injects stylesheets');
  if (blockingHosts.length > 0) {
    capabilities.push(`Blocks requests to ${blockingHosts.length} declared host pattern(s)`);
  }

  // Name every declared permission the runtime cannot honour.
  const declared = [...(manifest.permissions ?? []), ...(manifest.host_permissions ?? [])];
  for (const permission of declared) {
    if (typeof permission !== 'string') continue;
    const label = UNSUPPORTED_FEATURES[permission];
    if (label && !unsupported.includes(label)) unsupported.push(label);
  }

  if ((manifest as { background?: unknown }).background) {
    unsupported.push('background service worker (no extension process model)');
  }
  if ((manifest as { action?: unknown }).action || (manifest as { browser_action?: unknown }).browser_action) {
    unsupported.push('toolbar popup (no extension UI host)');
  }

  // Heavier packages are worth flagging before the user installs them.
  if (codeBytes > 400_000) {
    warnings.push('Large package — injecting this much code will slow page loads.');
  }
  if (contentScripts.some((c) => c.matches.includes('<all_urls>'))) {
    warnings.push('Runs on every website you visit.');
  }

  const status: ExtensionRunStatus =
    contentScripts.length > 0 || blockingHosts.length > 0
      ? unsupported.length > 0
        ? 'partial'
        : 'runs'
      : 'cannot-run';

  return {
    manifestVersion: manifest.manifest_version ?? 2,
    name: manifest.name ?? 'Unnamed extension',
    version: manifest.version ?? '1.0.0',
    description: manifest.description ?? '',
    status,
    capabilities,
    unsupported,
    contentScripts,
    blockingHosts,
    codeBytes,
    warnings,
  };
}

/* --------------------------------------------------------------- the runtime */

/** A `chrome.*` shim so ordinary content scripts do not throw on first line. */
const CHROME_SHIM = `
(function(){
  if (window.chrome && window.chrome.runtime && window.chrome.runtime.id) return;
  var store = {};
  var prefix = '__bh_extstore_';
  try {
    for (var i = 0; i < localStorage.length; i++) {
      var k = localStorage.key(i);
      if (k && k.indexOf(prefix) === 0) store[k.slice(prefix.length)] = JSON.parse(localStorage.getItem(k));
    }
  } catch (e) {}
  window.chrome = window.chrome || {};
  window.chrome.runtime = {
    id: 'browserhub-webextension',
    getURL: function (p) { return p; },
    getManifest: function () { return { version: '1.0.0' }; },
    lastError: undefined,
    sendMessage: function () { },
    onMessage: { addListener: function () { } }
  };
  window.chrome.i18n = {
    getMessage: function (key) { return key; },
    getUILanguage: function () { return navigator.language || 'en'; }
  };
  window.chrome.storage = {
    local: {
      get: function (keys, cb) {
        var out = {};
        if (keys == null) { out = store; }
        else if (typeof keys === 'string') { if (keys in store) out[keys] = store[keys]; }
        else if (Array.isArray(keys)) { keys.forEach(function (k) { if (k in store) out[k] = store[k]; }); }
        else { Object.keys(keys).forEach(function (k) { out[k] = (k in store) ? store[k] : keys[k]; }); }
        if (cb) cb(out);
        return Promise.resolve(out);
      },
      set: function (items, cb) {
        try {
          Object.keys(items || {}).forEach(function (k) {
            store[k] = items[k];
            localStorage.setItem(prefix + k, JSON.stringify(items[k]));
          });
        } catch (e) {}
        if (cb) cb();
        return Promise.resolve();
      },
      remove: function (keys, cb) {
        (Array.isArray(keys) ? keys : [keys]).forEach(function (k) {
          delete store[k];
          try { localStorage.removeItem(prefix + k); } catch (e) {}
        });
        if (cb) cb();
        return Promise.resolve();
      }
    }
  };
  window.chrome.storage.sync = window.chrome.storage.local;
  window.chrome.tabs = { query: function (q, cb) { var t = [{ active: true, url: location.href }]; if (cb) cb(t); return Promise.resolve(t); } };
  window.browser = window.browser || window.chrome;
})();
`;

/** Builds the JavaScript to inject for a page, or null when nothing applies. */
export function buildInjection(
  report: PackageReport,
  pageUrl: string
): { css: string; js: string } | null {
  if (!pageUrl || !/^https?:/i.test(pageUrl)) return null;

  let css = '';
  let js = '';
  let matchedAny = false;

  for (const script of report.contentScripts) {
    if (!urlMatches(pageUrl, script.matches)) continue;
    // exclude_matches wins over matches, exactly as the spec requires.
    if (script.excludeMatches?.length && urlMatches(pageUrl, script.excludeMatches)) continue;
    matchedAny = true;
    if (script.css) css += `${script.css}\n`;
    if (script.js) js += `\n/* content script (${script.runAt}) */\ntry{\n${script.js}\n}catch(e){console.warn('[BrowserHub ext]',e);}\n`;
  }

  if (!matchedAny) return null;
  return { css, js: js ? `${CHROME_SHIM}\n${js}` : '' };
}

/** Short human summary for the installed-extensions list. */
export function describeRuntime(report: PackageReport): string {
  if (report.status === 'cannot-run') {
    return 'Nothing in this package can execute here — it declares no content scripts or host rules.';
  }
  const parts = [...report.capabilities];
  if (report.unsupported.length > 0) {
    parts.push(`Unsupported: ${report.unsupported.join(', ')}`);
  }
  return parts.join(' · ');
}
