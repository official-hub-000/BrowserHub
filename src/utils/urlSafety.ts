/**
 * URL safety — what may be handed to a WebView, and what must be translated
 * into something a WebView can actually render.
 *
 * Bug this fixes (reported from a real device):
 *   "The web page at https://intent//play.google.com/... could not be loaded
 *    because: net::ERR_NAME_NOT_RESOLVED"
 *
 * A page on play.google.com used an `intent://…#Intent;scheme=https;…` link.
 * That is an Android deep-link URI, not a web URL. Nothing resolves it as a
 * hostname, so the WebView showed a DNS error. The same happened to
 * `market://`, `tel:`, `mailto:` and every other app scheme.
 *
 * The fix is to recognise these *before* navigation and turn them into either a
 * real https URL or a clear instruction — never to load them as a page.
 */

export type UrlIntent =
  | { kind: 'web'; url: string }
  | { kind: 'internal'; url: string }
  | { kind: 'app-scheme'; scheme: string; url: string; webUrl: string | null; note: string }
  | { kind: 'search'; query: string }
  | { kind: 'unsafe'; reason: string };

/** Schemes that a WebView can render or that this app handles itself. */
const WEB_SCHEMES = new Set(['http:', 'https:']);

/** App deep-link schemes and the honest thing to tell the user. */
const APP_SCHEME_NOTES: Record<string, string> = {
  intent: 'Android app link',
  market: 'Play Store app link',
  'play.google.com': 'Play Store app link',
  tel: 'phone call',
  sms: 'text message',
  mailto: 'email',
  geo: 'map location',
  whatsapp: 'WhatsApp',
  upi: 'payment app',
  content: 'local file',
  file: 'local file',
  about: 'browser-internal page',
  chrome: 'browser-internal page',
  data: 'inline data',
  blob: 'inline data',
  javascript: 'script',
};

/**
 * Parses an `intent://` URI into its embedded https fallback.
 *
 *   intent://play.google.com/store/apps/details?id=x#Intent;scheme=https;…;end
 *   -> https://play.google.com/store/apps/details?id=x
 */
export function intentUriToWebUrl(raw: string): string | null {
  const trimmed = raw.trim();
  if (!/^intent:/i.test(trimmed)) return null;

  // Everything between "intent://" and "#Intent;" is the target.
  const withoutScheme = trimmed.replace(/^intent:\/\//i, '').replace(/^intent:/i, '');
  const hashIndex = withoutScheme.indexOf('#Intent;');
  const body = hashIndex >= 0 ? withoutScheme.slice(0, hashIndex) : withoutScheme.split('#')[0];
  if (!body) return null;

  // Read the scheme the link asked for, defaulting to https because that is
  // what these links target in practice and https is always safe to try.
  const meta = hashIndex >= 0 ? withoutScheme.slice(hashIndex) : '';
  const schemeMatch = meta.match(/scheme=([^;]+)/i);
  const scheme = (schemeMatch?.[1] ?? 'https').toLowerCase().trim();

  // A non-web scheme (e.g. "market") cannot be rendered, so fall back to https
  // which resolves to the same destination for Play Store links.
  const usableScheme = WEB_SCHEMES.has(`${scheme}:`) ? scheme : 'https';
  return `${usableScheme}://${body.replace(/^\/+/, '')}`;
}

/** Extracts a web URL from a `market://` Play Store link. */
function marketUriToWebUrl(raw: string): string | null {
  const match = raw.match(/^market:\/\/details\?id=([\w.]+)/i);
  if (!match) return null;
  return `https://play.google.com/store/apps/details?id=${match[1]}`;
}

/**
 * Decides what to do with whatever the user typed, pasted, or tapped.
 * This is the single gate every navigation must pass through.
 */
export function classifyUrl(input: string): UrlIntent {
  const raw = (input ?? '').trim();
  if (!raw) return { kind: 'search', query: '' };

  // Internal pages are ours.
  if (/^browserhub:\/\//i.test(raw)) return { kind: 'internal', url: raw };

  // Android deep links first — these are the reported failure.
  if (/^intent:/i.test(raw)) {
    const webUrl = intentUriToWebUrl(raw);
    if (webUrl) {
      return {
        kind: 'app-scheme',
        scheme: 'intent',
        url: raw,
        webUrl,
        note: 'That was an Android app link, not a web address. Opened the web version instead.',
      };
    }
    return {
      kind: 'unsafe',
      reason: 'That is an Android app link with no web address inside it, so it cannot be opened here.',
    };
  }

  if (/^market:\/\//i.test(raw)) {
    const webUrl = marketUriToWebUrl(raw);
    if (webUrl) {
      return {
        kind: 'app-scheme',
        scheme: 'market',
        url: raw,
        webUrl,
        note: 'That was a Play Store app link. Opened the store page in a tab.',
      };
    }
    return { kind: 'unsafe', reason: 'That Play Store link has no app id, so there is no page to open.' };
  }

  // Any other explicit scheme.
  const schemeMatch = raw.match(/^([a-z][a-z0-9+.-]*):/i);
  if (schemeMatch) {
    const scheme = schemeMatch[1].toLowerCase();
    const withColon = `${scheme}:`;
    if (WEB_SCHEMES.has(withColon)) {
      return { kind: 'web', url: raw };
    }
    const label = APP_SCHEME_NOTES[scheme] ?? `${scheme} link`;
    return {
      kind: 'app-scheme',
      scheme,
      url: raw,
      webUrl: null,
      note:
        `That is a ${label}, which lives in another app — a browser cannot open it. ` +
        `Search for it instead, or open the app directly.`,
    };
  }

  // No scheme: a domain or a search.
  const looksLikeDomain = !raw.includes(' ') && /\.[a-z]{2,}(\/|$|\?)/i.test(raw);
  if (looksLikeDomain) return { kind: 'web', url: `https://${raw}` };

  return { kind: 'search', query: raw };
}

/**
 * True only for URLs a native WebView may be pointed at. Anything else must be
 * handled in the shell — this is what stops an `intent://` from reaching
 * `browserWebView.loadUrl()` and producing a DNS error page.
 */
export function isWebLoadable(url: string): boolean {
  const trimmed = (url ?? '').trim();
  if (!trimmed) return false;
  if (!/^https?:\/\//i.test(trimmed)) return false;
  try {
    const parsed = new URL(trimmed);
    // A WebView needs a real host; "intent//play.google.com" parses with host
    // "intent" and path "//play.google.com", which is exactly the bad case.
    if (!parsed.hostname || !parsed.hostname.includes('.')) return false;
    return WEB_SCHEMES.has(parsed.protocol);
  } catch {
    return false;
  }
}

/** Schemes that should hand off to the operating system instead of the WebView. */
export function isExternalAppScheme(url: string): boolean {
  const match = (url ?? '').trim().match(/^([a-z][a-z0-9+.-]*):/i);
  if (!match) return false;
  const scheme = `${match[1].toLowerCase()}:`;
  return !WEB_SCHEMES.has(scheme) && !scheme.startsWith('browserhub:');
}
