/**
 * Wikipedia-backed factual answers — the safety net for when the live model is
 * unavailable.
 *
 * Why this exists: the free LLM endpoint BrowserHub uses is anonymously rate
 * limited to roughly one request every 15 seconds. Rotating models does not
 * help, because every model goes through the same throttled endpoint. On a
 * phone, tapping two questions within a few seconds therefore used to produce
 * "answered, then apologised", and the user waits ~36 s to find out.
 *
 * Wikipedia's REST API has no such limit, needs no key, always tells you where
 * the text came from, and is exactly the right source for "who is X" questions.
 * Everything here returns the real article extract with its URL attached — it
 * never invents a fact, and if nothing is found it returns null so the caller
 * can fall through honestly.
 */

export interface FactAnswer {
  text: string;
  sourceUrl: string;
  sourceLabel: string;
}

const WIKI_SUMMARY = 'https://en.wikipedia.org/api/rest_v1/page/summary/';
const WIKI_SEARCH = 'https://en.wikipedia.org/w/api.php';

/** Words that are not part of the subject and confuse article lookup. */
const NOISE = new Set([
  'the', 'a', 'an', 'is', 'was', 'are', 'were', 'who', 'what', 'where', 'when',
  'why', 'how', 'tell', 'me', 'about', 'please', 'explain', 'define', 'definition',
  'meaning', 'of', 'do', 'does', 'did', 'can', 'could', 'you', 'know', 'any',
  'information', 'info', 'give', 'some', 'and',
]);

/** Slang that should never be sent to Wikipedia as a person's name. */
const SLANG = new Set([
  'goat', 'sick', 'fire', 'lit', 'cap', 'drip', 'ratio', 'bet', 'sus', 'based',
  'slay', 'ghost', 'salty', 'yeet', 'flex', 'stan',
]);

/**
 * Pull a Wikipedia-friendly subject out of a conversational question.
 * Returns null when the query is clearly not a factual lookup.
 */
export function extractFactSubject(query: string): string | null {
  const q = query.trim().replace(/[?!.]+$/g, '');
  if (!q) return null;

  // Only factual interrogatives — greetings, drafts and how-to steps are
  // handled by the local engine instead.
  const factual =
    /^(who|what|where)\s+(is|was|are|were)\b/i.test(q) ||
    /^tell\s+me\s+about\b/i.test(q) ||
    /^(define|definition\s+of)\b/i.test(q);
  if (!factual) return null;

  let subject = q
    .replace(/^(who|what|where)\s+(is|was|are|were)\s*/i, '')
    .replace(/^tell\s+me\s+about\s*/i, '')
    .replace(/^(define|definition\s+of)\s*/i, '')
    .replace(/\b(the\s+)?(meaning|definition)\s+of\s+(life|everything)\b.*$/i, '')
    .trim();

  // Drop a trailing clarification ("... in simple words", "... for kids").
  subject = subject.replace(/\s+(in simple (words|terms)|for (kids|beginners|dummies))\s*$/i, '');

  const words = subject.split(/\s+/).filter(Boolean);
  if (!words.length || words.length > 8) return null;

  // "a browser" / "the Eiffel Tower" -> "browser" / "Eiffel Tower". This has to
  // happen before the length checks, or a one-word subject keeps its article
  // and Wikipedia returns nothing.
  if (words.length > 1 && /^(a|an|the)$/i.test(words[0])) words.shift();

  // A single slang token is a question about the word, not an encyclopedia
  // article — the local engine already explains those correctly.
  if (words.length === 1 && SLANG.has(words[0].toLowerCase())) return null;

  // Strip leading articles / interrogatives that survived.
  while (words.length > 1 && NOISE.has(words[0].toLowerCase())) {
    words.shift();
  }
  // "What is the?" / "what is it" leaves a bare stop word — not a lookup.
  if (words.length === 1 && (NOISE.has(words[0].toLowerCase()) || /^(a|an|the|it|this|that)$/i.test(words[0]))) {
    return null;
  }
  const cleaned = words.join(' ').replace(/[^\w\s'().-]/g, '').trim();
  if (cleaned.length < 2) return null;
  return cleaned;
}

/** True when the question is the kind Wikipedia can answer from an extract. */
export function isFactualQuestion(query: string): boolean {
  return extractFactSubject(query) !== null;
}

interface WikiSummary {
  title?: string;
  extract?: string;
  description?: string;
  type?: string;
  content_urls?: { desktop?: { page?: string } };
}

function tidyExtract(extract: string): string {
  // Keep the article's own sentences but cap it to a readable answer.
  const sentences = extract
    .replace(/\s+/g, ' ')
    .split(/(?<=[.!?])\s+/)
    .filter((s) => s.trim().length > 0);
  let out = '';
  for (const s of sentences) {
    if (out.length + s.length > 700 && out.length > 200) break;
    out += (out ? ' ' : '') + s.trim();
  }
  return out.trim();
}

async function fetchSummary(title: string, signal: AbortSignal): Promise<FactAnswer | null> {
  try {
    const res = await fetch(
      `${WIKI_SUMMARY}${encodeURIComponent(title.replace(/\s+/g, '_'))}?redirect=true`,
      { signal, headers: { Accept: 'application/json' } }
    );
    if (!res.ok) return null;
    const data: WikiSummary = await res.json();
    // Disambiguation pages are lists, not answers — searching will do better.
    if (data?.type === 'disambiguation') return null;
    const extract = (data?.extract || '').trim();
    if (extract.length < 40) return null;
    const page = data?.content_urls?.desktop?.page;
    return {
      text: tidyExtract(extract),
      sourceUrl: page || `https://en.wikipedia.org/wiki/${encodeURIComponent(title.replace(/\s+/g, '_'))}`,
      sourceLabel: `Wikipedia — ${data?.title || title}`,
    };
  } catch {
    return null;
  }
}

/** Uses Wikipedia's own search when the literal title does not resolve. */
async function searchTitle(query: string, signal: AbortSignal): Promise<string | null> {
  try {
    const url =
      `${WIKI_SEARCH}?action=query&format=json&origin=*&list=search&srlimit=1&srsearch=` +
      encodeURIComponent(query);
    const res = await fetch(url, { signal, headers: { Accept: 'application/json' } });
    if (!res.ok) return null;
    const data: any = await res.json();
    const hit = data?.query?.search?.[0]?.title;
    return typeof hit === 'string' && hit.trim() ? hit.trim() : null;
  } catch {
    return null;
  }
}

/**
 * Progressively simpler spellings of a subject, most specific first.
 * "the animal goat" -> ["animal goat", "goat"]
 */
export function subjectVariants(subject: string): string[] {
  const out: string[] = [];
  const push = (value: string) => {
    const trimmed = value.trim();
    if (trimmed.length > 1 && !out.includes(trimmed)) out.push(trimmed);
  };

  push(subject);
  const words = subject.split(/\s+/).filter(Boolean);
  // Drop a leading qualifier ("animal goat" -> "goat") and leading articles.
  for (let i = 1; i < words.length; i += 1) push(words.slice(i).join(' '));
  for (let i = words.length - 1; i > 0; i -= 1) push(words.slice(0, i).join(' '));
  return out;
}

/**
 * Answers a factual question from Wikipedia. Returns null when the question is
 * not factual or no article matches — callers must then fall through to the
 * honest "I could not reach a model" reply rather than inventing anything.
 */
export async function answerFromWikipedia(
  query: string,
  timeoutMs = 4500
): Promise<FactAnswer | null> {
  const subject = extractFactSubject(query);
  if (!subject) return null;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    // Try the subject as written, then progressively simpler forms. "the animal
    // goat" is not an article, but "goat" is — a user asking that should still
    // get the real answer instead of an apology.
    const candidates = subjectVariants(subject);
    for (const candidate of candidates) {
      const direct = await fetchSummary(candidate, controller.signal);
      if (direct) return direct;
    }

    for (const candidate of candidates) {
      const found = await searchTitle(candidate, controller.signal);
      if (!found) continue;
      if (found.toLowerCase() === candidate.toLowerCase()) continue;
      const viaSearch = await fetchSummary(found, controller.signal);
      if (viaSearch) return viaSearch;
    }
    return null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}
