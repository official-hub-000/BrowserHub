/**
 * Small, dependency-free caches that make "ask it again" work like a user
 * expects inside a browser assistant:
 *
 *  - an in-memory LRU of successful AI replies (session)
 *  - a persisted mirror in localStorage so the same question still answers
 *    instantly after the app is killed and relaunched
 *  - a single-flight map so asking the same thing twice while the first
 *    request is still in the air joins it instead of firing a second call
 *
 * Only answers that came from a real model are cached; offline fallbacks are
 * never stored, otherwise a temporary network blip would poison the cache.
 */

const MEMORY_LIMIT = 40;
const STORAGE_KEY = 'browserhub.ai.answers.v1';
const STORAGE_LIMIT = 60;

export interface CachedAnswer {
  text: string;
  engineLabel: string;
  model: string;
  savedAt: number;
}

const memory = new Map<string, CachedAnswer>();
const inFlight = new Map<string, Promise<unknown>>();

function normalize(key: string): string {
  return key
    .toLowerCase()
    // Treat every separator the same and collapse whitespace everywhere, so
    // "CHAT|  Who is Ronaldo  |home|" and "chat|who is ronaldo|home|" agree.
    .replace(/\s+/g, ' ')
    .replace(/\s*\|\s*/g, '|')
    .replace(/\|+$/, '')
    .replace(/[?!.]+$/g, '')
    .trim();
}

function readStorage(): Record<string, CachedAnswer> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeStorage(map: Record<string, CachedAnswer>) {
  try {
    const entries = Object.entries(map)
      .sort((a, b) => b[1].savedAt - a[1].savedAt)
      .slice(0, STORAGE_LIMIT);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(Object.fromEntries(entries)));
  } catch {
    // Storage disabled or full: the in-memory cache still works this session.
  }
}

export function getCachedAnswer(key: string): CachedAnswer | null {
  const k = normalize(key);
  if (!k) return null;
  const hit = memory.get(k);
  if (hit) return hit;
  const stored = readStorage()[k];
  if (stored && typeof stored.text === 'string' && stored.text.trim()) {
    memory.set(k, stored);
    return stored;
  }
  return null;
}

export function rememberAnswer(key: string, answer: CachedAnswer) {
  const k = normalize(key);
  if (!k || !answer.text.trim()) return;
  memory.delete(k);
  memory.set(k, answer);
  while (memory.size > MEMORY_LIMIT) {
    const oldest = memory.keys().next().value;
    if (oldest === undefined) break;
    memory.delete(oldest);
  }
  const stored = readStorage();
  stored[k] = answer;
  writeStorage(stored);
}

/** Runs `task` once per key; concurrent callers await the same promise. */
export function singleFlight<T>(key: string, task: () => Promise<T>): Promise<T> {
  const k = normalize(key);
  const existing = inFlight.get(k);
  if (existing) return existing as Promise<T>;
  const p = task().finally(() => inFlight.delete(k));
  inFlight.set(k, p as Promise<unknown>);
  return p;
}

export function clearAiCache() {
  memory.clear();
  inFlight.clear();
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function aiCacheSize(): number {
  return Object.keys(readStorage()).length;
}

/**
 * Clipboard write that actually works inside Android WebView.
 * navigator.clipboard is frequently unavailable there (non-secure context,
 * no user-activation tracking), so we fall back to execCommand and finally to
 * the native bridge. Returns true only when something really copied.
 */
export async function copyText(text: string): Promise<boolean> {
  if (!text) return false;

  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // fall through
  }

  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.top = '-1000px';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    ta.setSelectionRange(0, text.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    if (ok) return true;
  } catch {
    // fall through
  }

  try {
    const bridge = (window as any).BrowserHubNative;
    if (bridge?.copyToClipboard) {
      bridge.copyToClipboard(text);
      return true;
    }
  } catch {
    // ignore
  }
  return false;
}
