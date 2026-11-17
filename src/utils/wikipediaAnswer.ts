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

/**
 * CORS note (this matters — it is why a Wikipedia lookup can fail silently):
 *
 * Wikimedia's REST API echoes CORS headers for browser callers, but its
 * preflight allow-list does not include `user-agent`. Any request that triggers
 * a preflight can therefore be rejected before it is even sent. The safe form
 * is a "simple request": GET, no custom headers, no explicit Accept. We
 * deliberately pass no headers and let the browser supply defaults.
 */
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
  // handled by the local engine instead. Every shape below is a question that
  // has a single authoritative encyclopedia answer, so a missing live model
  // does not have to mean "no answer".
  // "why is the sky blue" / "how does a car engine work" and long narrative
  // questions like "why did X happen" have no single encyclopedia title, but
  // they almost always name one. Extracting that name is far more useful than
  // refusing to look anything up.
  // Covers both "why is the sky blue" and narrative questions with no auxiliary
  // verb, e.g. "why Cristiano Ronaldo got betrayed by ...".
  const whyMatch = /^why\s+(?:(?:is|are|was|were|did|does|do|has|have)\s+)?(.+)$/i.exec(q);
  // Explanatory only: "how does a car engine work", "how is glass made".
  // Instructional phrasing ("how do I cook rice") belongs to the local engine.
  const howMatch = /^how\s+(?:does|did|is|are|was|were)\s+(?:a|an|the)?\s*(.+)$/i.exec(q);

  const shapes: Array<{ re: RegExp; to: string }> = [
    // who is / what is / where is
    { re: /^(who|what|where)\s+(is|was|are|were)\s*/i, to: '' },
    // who won / who wrote / who invented / who discovered / who built
    { re: /^who\s+(won|wrote|invented|discovered|founded|built|created|painted|directed|composed|killed|signed)\s*/i, to: '' },
    // when did / when was / when is   (keeps the event words as the subject)
    { re: /^when\s+(did|was|is|were)\s*/i, to: '' },
    // how tall is / how old is / how long is / how far is
    { re: /^how\s+(tall|old|long|big|heavy|far|hot|cold|deep|high)\s+(is|was|are|were)\s*/i, to: '' },
    // what causes / what makes
    { re: /^(what|which)\s+(causes|makes|caused|created)\s*/i, to: '' },
    { re: /^tell\s+me\s+about\s*/i, to: '' },
    { re: /^(define|definition\s+of)\s*/i, to: '' },
  ];

  let subject = '';
  for (const shape of shapes) {
    if (shape.re.test(q)) {
      subject = q.replace(shape.re, shape.to);
      break;
    }
  }

  // Fall back to the most encyclopedia-like noun phrase for open questions.
  if (!subject && whyMatch) subject = pickNounPhrase(whyMatch[1]);
  if (!subject && howMatch) subject = pickNounPhrase(howMatch[1]);
  if (!subject) return null;

  subject = subject
    .replace(/\b(the\s+)?(meaning|definition)\s+of\s+(life|everything)\b.*$/i, '')
    .trim();

  // Drop a trailing clarification ("... in simple words", "... for kids").
  subject = subject.replace(/\s+(in simple (words|terms)|for (kids|beginners|dummies))\s*$/i, '');

  // Keep at most 8 words. Truncating is better than refusing: a question that
  // begins with a person's name still resolves from their article.
  const words = subject.split(/\s+/).filter(Boolean).slice(0, 8);
  if (!words.length) return null;

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
      { signal }
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
    // `origin=*` is mandatory for the Action API to return CORS headers.
    const url =
      `${WIKI_SEARCH}?action=query&format=json&origin=*&list=search&srlimit=1&srsearch=` +
      encodeURIComponent(query);
    const res = await fetch(url, { signal });
    if (!res.ok) return null;
    const data: any = await res.json();
    const hit = data?.query?.search?.[0]?.title;
    return typeof hit === 'string' && hit.trim() ? hit.trim() : null;
  } catch {
    return null;
  }
}

/**
 * Picks the part of a long open question most likely to be an article title.
 * "Cristiano Ronaldo got betrayed by man u" -> "Cristiano Ronaldo".
 */
function pickNounPhrase(text: string): string {
  const cleaned = text.replace(/[?,!.]+$/g, '').trim();
  if (!cleaned) return '';
  const words = cleaned.split(/\s+/);

  // Prefer a leading run of capitalised words (proper noun), which is what
  // "who/what is X" style questions about people and places look like.
  const proper: string[] = [];
  for (const w of words) {
    if (/^[A-Z0-9]/.test(w)) proper.push(w.replace(/[^\w'.-]/g, ''));
    else break;
  }
  if (proper.length >= 2) return proper.join(' ').slice(0, 60);

  // Otherwise take the first few content words, dropping filler words but
  // keeping the leading noun so a person or product name survives.
  const filler = new Set(['got','get','gets','by','from','with','his','her','their','it','this','that','so','and','but','for','to','of','in','on','at','very','really','actually','betrayed']);
  const kept = words.filter((w, i) => i < 2 || !filler.has(w.toLowerCase())).slice(0, 4);
  return kept.join(' ').slice(0, 60);
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
