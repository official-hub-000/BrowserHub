/**
 * Page context extraction for the AI assistant.
 *
 * IMPORTANT: BrowserHub never fabricates page content. Everything the AI receives is
 * either (a) real text extracted from the Android WebView via the native bridge,
 * (b) real text passed up from a fetched live API result, or (c) nothing at all.
 */

export type PageKind = 'home' | 'search' | 'internal' | 'article' | 'unknown';

const SEARCH_HOSTS = [
  'google.',
  'bing.com',
  'duckduckgo.com',
  'search.brave.com',
  'startpage.com',
  'search.yahoo.',
  'ecosia.org',
  'yandex.',
  'baidu.com',
  'wikipedia.org/w/index.php?search',
  'github.com/search',
];

export function classifyPageUrl(url: string): PageKind {
  if (!url) return 'unknown';
  if (url === 'browserhub://start') return 'home';
  if (url.startsWith('browserhub://')) return 'internal';
  if (url.startsWith('gh-search:')) return 'internal';

  const lower = url.toLowerCase();
  if (SEARCH_HOSTS.some((h) => lower.includes(h)) && /[?&](q|query|search|p)=/.test(lower)) {
    return 'search';
  }
  if (lower.includes('google.com/search') || lower.includes('bing.com/search')) return 'search';
  if (/^https?:\/\//.test(lower)) return 'article';
  return 'unknown';
}

/** Extracts the visible query from a search-engine result URL (for honest AI context). */
export function extractQueryFromUrl(url: string): string {
  try {
    const u = new URL(url);
    return (
      u.searchParams.get('q') ||
      u.searchParams.get('query') ||
      u.searchParams.get('p') ||
      u.searchParams.get('search') ||
      ''
    );
  } catch {
    return '';
  }
}

export interface PageAiContext {
  kind: PageKind;
  title: string;
  /** Clean article-ish body text. Empty for search pages & home. */
  text: string;
  /** Detected search query when kind === 'search'. */
  searchQuery: string;
  /** Human readable note about what context the AI actually has. */
  contextNote: string;
}

function cleanWhitespace(raw: string): string {
  return raw
    .replace(/\\n/g, '\n')
    .replace(/\\"/g, '"')
    .replace(/\\\//g, '/')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

const CHROME_PHRASES = [
  /^(open menu|menu|search settings|settings|sign in|log in|all results|images|videos|news|maps)$/i,
  /^(skip to (main )?content|accept (all )?cookies|manage (preferences|cookies)|privacy policy|terms of service)$/i,
  /^(share|tweet|email|print|save|follow us|subscribe|advertisement|sponsored)$/i,
  /^(previous|next|back to top|home|about|contact|careers|blog|help|feedback)$/i,
  /^(\d+\s*results?|about\s+\d+.*results?)$/i,
];

/**
 * Removes navigation chrome, filter bars, cookie banners and one-word menu links from
 * extracted page text. Keeps real sentences and article paragraphs.
 */
export function sanitizeExtractedPageContent(raw: string): string {
  if (!raw) return '';

  let text = cleanWhitespace(raw)
    .replace(/All\s+Results\s+[\w.]*[^\n]{0,160}?(?:Date|Safe\s*search|Region)/gi, '\n')
    .replace(/\b(Open menu|Ask Duck\.ai|Safe search|Search Settings|Skip to (?:main )?content)\b/gi, '\n')
    .replace(/\bAll\s+Images\s+Videos\s+News\s+Maps\b/gi, '\n');

  const lines = text
    .split(/\n+/)
    .map((l) => l.replace(/\s+/g, ' ').trim())
    .filter((line) => {
      if (!line) return false;
      if (CHROME_PHRASES.some((re) => re.test(line))) return false;
      if (line.length < 40) return false;
      const words = line.split(' ').length;
      if (words < 6) return false;
      // Drop lines that are mostly pipe-separated nav links.
      if ((line.match(/\s\|\s/g) || []).length >= 3) return false;
      if ((line.match(/›/g) || []).length >= 2) return false;
      return true;
    });

  const joined = lines.join('\n');
  if (joined.length >= 80) return joined;

  const fallback = cleanWhitespace(raw);
  return fallback.length > 0 && fallback.split(' ').length >= 12 ? fallback : '';
}

/**
 * Builds the exact context handed to the AI engine.
 * Never invents content — an empty `text` means the AI has no page body to read.
 */
export function getPageContextForAi(
  url: string,
  liveText?: string
): PageAiContext {
  const kind = classifyPageUrl(url);
  const searchQuery = kind === 'search' ? extractQueryFromUrl(url) : '';
  const cleaned = sanitizeExtractedPageContent(liveText || '');

  if (kind === 'home') {
    return {
      kind,
      title: 'BrowserHub Home Screen',
      text: '',
      searchQuery: '',
      contextNote: 'No webpage is open — the user is on the BrowserHub Home screen.',
    };
  }

  if (kind === 'search') {
    return {
      kind,
      title: searchQuery ? `Search results for "${searchQuery}"` : 'Search results page',
      text: '',
      searchQuery,
      contextNote: `The open page is a SEARCH RESULTS page${
        searchQuery ? ` for "${searchQuery}"` : ''
      }. Search result listings contain navigation menus and short snippets only — never quote them as article content.`,
    };
  }

  if (kind === 'internal') {
    return {
      kind,
      title: url.replace('browserhub://', 'BrowserHub '),
      text: '',
      searchQuery: '',
      contextNote: 'The user is on an internal BrowserHub page (not a website).',
    };
  }

  return {
    kind,
    title: url.replace(/^https?:\/\//, '').split('/')[0],
    text: cleaned,
    searchQuery: '',
    contextNote: cleaned
      ? 'Real visible text extracted from the open webpage.'
      : 'No readable page text could be extracted yet (the page may still be loading, or it is app-like/JS-only).',
  };
}
