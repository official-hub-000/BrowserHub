import { answerFromWikipedia, isFactualQuestion } from './wikipediaAnswer';
import {
  getPageContextForAi,
  sanitizeExtractedPageContent,
  type PageAiContext,
} from './pageTextExtractor';

import { getCachedAnswer, rememberAnswer, singleFlight } from './aiCache';

export { sanitizeExtractedPageContent };
export type { PageAiContext };

export interface LocalAiInput {
  mode: string;
  title?: string;
  url?: string;
  content?: string;
  query?: string;
  history?: Array<{ role: 'user' | 'assistant'; content: string }>;
  /** Human-readable name of the engine selected in the address bar. */
  searchEngineName?: string;
}

export interface LocalAiOutput {
  ok: true;
  model: string;
  engineLabel: string;
  zeroApiKey: true;
  zeroTelemetry: true;
  inferenceMs: number;
  responseText: string;
  bullets: string[];
  /** True when no live model could be reached and this is a limited local reply. */
  offlineFallback?: boolean;
}

const STOPWORDS = new Set([
  'the', 'and', 'for', 'that', 'this', 'with', 'from', 'your', 'have', 'are',
  'was', 'were', 'will', 'would', 'could', 'should', 'into', 'about', 'there',
  'their', 'they', 'them', 'then', 'than', 'what', 'when', 'where', 'which',
  'while', 'who', 'whom', 'whose', 'been', 'being', 'also', 'more', 'most',
  'some', 'such', 'only', 'other', 'over', 'under', 'after', 'before', 'between',
  'through', 'during', 'without', 'within', 'along', 'across', 'behind', 'beyond',
  'using', 'used', 'uses', 'make', 'made', 'does', 'doing', 'done', 'just', 'very',
  'can', 'may', 'might', 'must', 'shall', 'here', 'there', 'each', 'both', 'any',
  'how', 'why', 'did', 'does', 'its', 'it\'s', 'you', 'your', 'our', 'ours',
]);

function splitSentences(text: string): string[] {
  return text
    .split(/(?<=[.!?])\s+|\n+/)
    .map((s) => s.replace(/\s+/g, ' ').trim())
    .filter((s) => s.length >= 40 && s.length <= 460);
}

function rankSentences(sentences: string[], query = ''): string[] {
  if (sentences.length === 0) return [];
  const freq: Record<string, number> = {};
  const queryTerms = query
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter((w) => w.length > 2 && !STOPWORDS.has(w));

  for (const s of sentences) {
    for (const w of s.toLowerCase().split(/[^a-z0-9]+/)) {
      if (w.length > 3 && !STOPWORDS.has(w)) freq[w] = (freq[w] || 0) + 1;
    }
  }

  return sentences
    .map((sentence, idx) => {
      const words = sentence.toLowerCase().split(/[^a-z0-9]+/);
      let score = 0;
      for (const w of words) if (freq[w]) score += freq[w];
      for (const qt of queryTerms) if (sentence.toLowerCase().includes(qt)) score += 24;
      if (idx === 0) score *= 1.25;
      else if (idx < 3) score *= 1.12;
      return { sentence, score: score / Math.max(words.length, 8), idx };
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, 5)
    .sort((a, b) => a.idx - b.idx)
    .map((x) => x.sentence);
}

function tryEvaluateMath(expr: string): string | null {
  const normalized = expr
    .trim()
    .replace(/^(what\s+is|what's|calculate|compute|solve|how\s+much\s+is)\s+/i, '')
    .replace(/[?=]+$/, '')
    .trim();

  const pctMatch = /^(\d+(?:\.\d+)?)\s*%\s*(?:of)\s*(\d+(?:\.\d+)?)$/i.exec(normalized);
  if (pctMatch) {
    const ans = (parseFloat(pctMatch[1]) / 100) * parseFloat(pctMatch[2]);
    return `${pctMatch[1]}% of ${pctMatch[2]} = ${Number(ans.toFixed(6))}`;
  }

  if (/^[\d\s+\-*/().%^]+$/.test(normalized) && /[+\-*/%^]/.test(normalized) && /\d/.test(normalized)) {
    try {
      const jsExpr = normalized.replace(/\^/g, '**');
      const val = Function(`"use strict"; return (${jsExpr});`)();
      if (typeof val === 'number' && Number.isFinite(val)) {
        return `${normalized} = ${Number(val.toFixed(8))}`;
      }
    } catch {
      return null;
    }
  }
  return null;
}

const GREETING_RE =
  /^(hi|hey|hello|yo|sup|hola|salam|assalamu alaikum|namaste|good morning|good afternoon|good evening|good night|hey there|hi there|hello there|howdy)[\s!.,]*$/i;

const THANKS_RE = /^(thanks|thank you|thx|ty|awesome|great|cool|nice|perfect|got it|ok|okay)[\s!.,]*$/i;

const CAPABILITY_RE =
  /^(who are you|what are you|what can you do|what do you do|help|how do you work|are you (working|online)|what is browserhub ai)[\s!?.]*$/i;

/**
 * Handles everything that does NOT require live facts:
 * greetings, identity, thanks, arithmetic and structured drafting.
 */
function handleNonFactualQuery(
  query: string,
  ctx: PageAiContext
): { text: string; bullets: string[] } | null {
  const q = query.trim();
  const lower = q.toLowerCase().replace(/[!?.]+$/g, '').trim();
  if (!q) return null;

  if (GREETING_RE.test(lower)) {
    const pageHint =
      ctx.kind === 'article' && ctx.text
        ? `I can also read the page you have open right now.`
        : ctx.kind === 'search'
        ? `I can summarize any result — just open one and ask again.`
        : `Open any website and I can summarize or answer questions about it.`;
    return {
      text: `Hey! I'm **BrowserHub AI** — your built-in assistant.\n\nAsk me anything, or try one of these:\n- **"Summarize this page"** while reading an article\n- **"Explain this simply"** for a plain-language breakdown\n- **"Draft a polite follow-up email"** for writing help\n\n${pageHint}`,
      bullets: ['No API key, no account, no subscription'],
    };
  }

  if (THANKS_RE.test(lower)) {
    return {
      text: `You're welcome! Ask me anytime — I'm right here in the toolbar.`,
      bullets: [],
    };
  }

  if (CAPABILITY_RE.test(lower)) {
    return {
      text: `I'm **BrowserHub AI**, built into your browser with no API key required.\n\n### What I can do\n1. **Answer questions** — general knowledge, explanations, coding and writing.\n2. **Summarize the page you're reading** — real article text only, with menus and ads stripped.\n3. **Explain simply** — rewrite dense material in plain language.\n4. **Privacy check** — inspect the current connection and URL for tracking parameters.\n5. **Draft text** — emails, messages and notes.`,
      bullets: ['Works with the search engine you picked in the address bar'],
    };
  }

  const mathAns = tryEvaluateMath(q);
  if (mathAns) {
    return { text: `**${mathAns}**`, bullets: ['Computed instantly on-device'] };
  }

  if (/^(write|draft|compose)\s+(an?\s+)?(email|message|letter|note|reply|text|sms)\b/i.test(lower)) {
    const topic =
      q
        .replace(/^(write|draft|compose)\s+(an?\s+)?(email|message|letter|note|reply|text|sms)\s*/i, '')
        .replace(/^(about|for|to|regarding|on)\s+/i, '')
        .trim() || 'the topic';
    return {
      text: `Here's a clean draft about **${topic}**:\n\n**Subject:** Regarding ${topic}\n\nHi [Name],\n\nI hope you're doing well. I'm reaching out about ${topic}. Could you let me know your thoughts, or suggest a convenient time to discuss the next steps?\n\nThanks so much,\n[Your name]`,
      bullets: ['Tap Copy to reuse or edit this draft'],
    };
  }

  return null;
}

/**
 * Offline query understanding. Classifies what is being asked so the fallback
 * can say something specific ("this is a who-is question about a person")
 * instead of a generic apology. No network, no invented facts.
 */
function describeQuery(query: string): string | null {
  const q = query.trim().replace(/[?!.]+$/, '');
  if (!q) return null;

  const isWho = /^who\s+(is|was|are|were)\b/i.test(q);
  const isWhat = /^what\s+(is|was|are|were)\b/i.test(q);
  const isWhere = /^where\s+(is|was|are)\b/i.test(q);
  const isWhen = /^when\s+(did|was|is|were)\b/i.test(q);
  const isHowTo = /^how\s+(do|does|can|to|should)\b/i.test(q);
  const isWhy = /^why\s+(is|does|do|did|are)\b/i.test(q);
  const isDefinition = /\b(meaning|definition|stand for|acronym)\b/i.test(q);

  // Extract the subject after the interrogative so we can echo it precisely.
  const subject = q
    .replace(/^(who|what|where|when|how|why)\s+(is|was|are|were|do|does|did|can|should|to)\s+/i, '')
    .replace(/^(who|what|where|when|how|why)\s+/i, '')
    .trim();

  // "goat" and friends are ambiguous acronyms; naming that is more useful than
  // a wrong answer, and it is honest about the ambiguity.
  if (/^(goat|sick|fire|lit|cap|drip|ratio|bet)$/i.test(subject)) {
    const expansions: Record<string, string> = {
      goat: 'greatest of all time',
      sick: 'extremely good',
      fire: 'excellent',
      lit: 'exciting',
      cap: 'a lie (as in "no cap")',
      drip: 'style / outfit',
      ratio: 'a reply with more likes than the original post',
      bet: 'agreement ("you bet")',
    };
    const full = expansions[subject.toLowerCase()];
    if (full) {
      return `"${subject}" is internet slang for "${full}". If you meant the animal, say "the animal ${subject}" and I'll take it that way.`;
    }
  }

  if (isDefinition && subject) {
    return `This is a definition question about "${subject}" — that needs a live model or a source page, so I won't guess.`;
  }
  if (isWho && subject) {
    return `This is a who-is question about "${subject}" — the kind of thing a live model or a Wikipedia page answers accurately, and the kind of thing I refuse to invent.`;
  }
  if (isWhat && subject) {
    return `This is a what-is question about "${subject}".`;
  }
  if (isWhere && subject) return `This is a where-is question about "${subject}".`;
  if (isWhen) return 'This is a when question about dates or events.';
  if (isHowTo) return 'This is a how-to question — a result page usually has the steps.';
  if (isWhy) return 'This is a why question that needs reasoning over sources.';
  return null;
}

function noPageMessage(requestLabel: string, ctx: PageAiContext): string {
  if (ctx.kind === 'home') {
    return `I can't ${requestLabel} yet — you're on the **BrowserHub Home screen**, so no website is open.\n\nUse the search bar to open any article, docs page or wiki entry, then ask again and I'll read the real page text.`;
  }
  if (ctx.kind === 'search') {
    return `I can't ${requestLabel} from a **search results page** — those listings are just navigation menus and short snippets, not article content.\n\nOpen one of the results, then ask me again.`;
  }
  return `I can't ${requestLabel} yet because **no readable text** has been extracted from this page. It may still be loading, or it could be an app-style page with little text.\n\nTry reloading the page, or open a different one.`;
}

/**
 * Fully local, deterministic engine. No network. Never invents page content.
 */
export function runLocalAiClient(input: LocalAiInput): LocalAiOutput {
  const start = performance.now();
  lastAttempts = [];
  const mode = input.mode || 'chat';
  const query = (input.query || '').trim();
  const ctx = getPageContextForAi(input.url || 'browserhub://start', input.content);
  const sentences = ctx.text ? splitSentences(ctx.text) : [];
  const hasPageText = sentences.length > 0;
  const pageTitle = input.title && input.title !== input.url ? input.title : ctx.title;

  let responseText = '';
  let bullets: string[] = [];

  const nonFactual = query ? handleNonFactualQuery(query, ctx) : null;
  if (nonFactual) {
    responseText = nonFactual.text;
    bullets = nonFactual.bullets;
  } else if (mode === 'summarize') {
    if (!hasPageText) {
      responseText = noPageMessage('summarize the page', ctx);
    } else {
      const top = rankSentences(sentences);
      responseText = `### Summary: ${pageTitle}\n\n${top
        .slice(0, 5)
        .map((s) => `- ${s}`)
        .join('\n')}`;
      bullets = [`Read ${sentences.length} clean sentences — navigation and ads removed`];
    }
  } else if (mode === 'eli5') {
    if (!hasPageText) {
      responseText = noPageMessage('simplify the page', ctx);
    } else {
      const top = rankSentences(sentences).slice(0, 3);
      responseText = `### Explained simply: ${pageTitle}\n\n${top
        .map((s, i) => `${i + 1}. ${s}`)
        .join('\n')}\n\nIn short: this page is mainly about **${pageTitle}**, and the points above are the parts that matter most.`;
      bullets = ['Plain-language rewrite of the real page text'];
    }
  } else if (mode === 'security_audit') {
    const url = input.url || '';
    const isHttps = url.startsWith('https://');
    const isInternal = url.startsWith('browserhub://');
    const tracking = /[?&](utm_[a-z]+|fbclid|gclid|msclkid|mc_cid|igshid)=/i.test(url);
    responseText =
      `### Page privacy check\n\n` +
      (ctx.kind === 'home'
        ? `- **Where you are:** BrowserHub Home screen (internal page)\n- **Tracking:** none — this page never leaves your device\n- **Shields:** active\n- **AI data:** replies are generated without an API key; page text never leaves the device unless a live AI model is reached`
        : `- **Page:** ${pageTitle}\n- **Connection:** ${
            isInternal ? 'internal browser page' : isHttps ? 'secure HTTPS' : '⚠ unencrypted HTTP — avoid entering passwords'
          }\n- **Tracking parameters in URL:** ${tracking ? 'found (BrowserHub strips these on navigation)' : 'none detected'}\n- **Ad & tracker blocking:** ${url ? 'enabled per your Shields settings' : 'n/a'}`);
    bullets = ['Checked locally — no page data uploaded'];
  } else if (mode === 'browse_for_me' || mode === 'chat') {
    if (hasPageText && query) {
      const qWords = query
        .toLowerCase()
        .split(/[^a-z0-9]+/)
        .filter((w) => w.length > 2 && !STOPWORDS.has(w));
      const matching = sentences.filter((s) => {
        const sl = s.toLowerCase();
        return qWords.some((w) => sl.includes(w));
      });

      if (matching.length > 0) {
        const best = rankSentences(matching, query).slice(0, 3);
        responseText = `From **${pageTitle}**:\n\n${best.map((s) => `- ${s}`).join('\n')}`;
        bullets = [`${matching.length} matching passage(s) found in the page text`];
      } else {
        const top = rankSentences(sentences).slice(0, 2);
        responseText =
          `The page **${pageTitle}** doesn't appear to mention **"${query}"**.\n\n` +
          (top.length
            ? `What it does cover:\n${top.map((s) => `- ${s}`).join('\n')}`
            : 'It has very little readable text.');
        bullets = [];
      }
    } else {
      // No live model, no page context. Never invent an answer — but still do
      // something genuinely useful with what the browser already knows.
      const hint = describeQuery(query);
      // Prefer this request's own evidence; fall back to the session-level
      // signal only when nothing here said anything useful.
      const sawNetworkError = lastAttempts.some((a) => /network error|Load failed|Failed to fetch|HTTP 5\d\d/i.test(a));
      const sawThrottle = lastAttempts.some((a) => /rate limited|HTTP 429/i.test(a));
      const offline = sawNetworkError || (!sawThrottle && isOfflineDevice());
      // Say which failure this is. Telling someone to "ask again in a moment"
      // when their phone plainly has no connection is the kind of dishonest
      // advice this app is not allowed to give.
      const reason = offline
        ? 'This device could not reach the internet, so no live model was available.'
        : 'The free live model is busy or rate limited right now.';
      responseText =
        `${reason} I won't guess at **"${query}"**.\n\n` +
        (hint ? `**${hint}**\n\n` : '') +
        `Three things that work right now:\n` +
        `1. **Search it** — type it in the address bar and BrowserHub opens it with **${input.searchEngineName || 'the engine in your address bar'}**.\n` +
        `2. **Open any result and tap Summarize** — I read the real page text on-device and answer from it.\n` +
        (offline
          ? `3. **Turn on mobile data or Wi-Fi and ask again** — the search and page-reading above already work without it.\n\n`
          : `3. **Ask again in about 15 seconds** — the free model accepts roughly one question per 15 seconds.\n\n`) +
        `What I can always do on-device: exact math (**"18% of 2450"**), email and message drafts, page summaries, page Q&A, and a privacy check of the URL you're on.`;
      bullets = [
        'Tap Summarize after opening a page for an on-device answer from its real text',
        ...(lastAttempts.length ? [`Why: ${lastAttempts.slice(-2).join(' · ')}`] : []),
      ];
    }
  } else {
    responseText = hasPageText
      ? `I have the page **${pageTitle}** open and ready — tell me what you'd like to know about it.`
      : `Ask me a question, or open a page and I'll read it for you.`;
  }

  return {
    ok: true,
    model: 'BrowserHub-OnDevice',
    engineLabel: 'On-device engine',
    zeroApiKey: true,
    zeroTelemetry: true,
    inferenceMs: Math.max(1, Math.round((performance.now() - start) * 10) / 10),
    responseText,
    bullets,
    offlineFallback: true,
  };
}


/**
 * Uses the Android shell's worker-thread AI client when available.
 * Returns null when the bridge is missing, the request fails, or it takes too long.
 */
async function askViaNativeBridge(prompt: string, timeoutMs: number): Promise<string | null> {
  const bridge = typeof window !== 'undefined' ? window.BrowserHubNative : undefined;
  // No bridge at all is normal in the browser preview; say so only in the
  // diagnostic channel, never in the chat.
  if (!bridge?.startAiRequest || !bridge?.pollAiResult) {
    noteAiAttempt('native', 'no native bridge (web build)');
    return null;
  }

  const requestId = `ai-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  try {
    bridge.startAiRequest(requestId, prompt, SYSTEM_PROMPT);
  } catch (err) {
    noteAiAttempt('native', `start threw: ${(err as Error)?.message || 'unknown'}`);
    return null;
  }

  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 500));
    let raw: string | null = null;
    try {
      raw = bridge.pollAiResult(requestId);
    } catch {
      return null;
    }
    if (!raw) continue;
    try {
      const parsed = JSON.parse(raw);
      if (parsed?.ok && typeof parsed.text === 'string' && parsed.text.trim().length > 2) {
        return parsed.text.trim();
      }
      noteAiAttempt('native', `failed: ${parsed?.error || 'empty reply'}`);
      return null;
    } catch (err) {
      noteAiAttempt('native', `bad envelope: ${(err as Error)?.message || 'unknown'}`);
      return null;
    }
  }
  noteAiAttempt('native', `timed out after ${timeoutMs} ms`);
  return null;
}


/**
 * Runs a live-AI request with a hard timeout and swallows every failure mode
 * (blocked network, CORS, malformed body) so callers can fall through cleanly.
 */
async function fetchLive(
  request: (signal: AbortSignal) => Promise<string | null>,
  timeoutMs: number
): Promise<string | null> {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const reply = await request(controller.signal);
      const trimmed = typeof reply === 'string' ? reply.trim() : '';
      return trimmed.length > 2 ? trimmed : null;
    } finally {
      clearTimeout(timer);
    }
  } catch {
    return null;
  }
}

/**
 * Remembers when the free model endpoint throttled us.
 *
 * Pollinations limits anonymous callers to roughly one request every 15 s.
 * Without this guard the next question spends its whole budget re-learning
 * that, which is why asking twice quickly produced "answered, then apologised".
 */
/**
 * Why the last live attempt failed, in plain words.
 *
 * The user could not tell "the model is rate limited" from "this phone cannot
 * reach the internet", and they all looked identical: an apology. Recording the
 * reason lets the UI say which one it is, and lets the fallback stop offering
 * "ask again in a moment" when the device is genuinely offline.
 */
export type AiAttemptKind = 'native' | 'direct' | 'proxy' | 'wiki';
let lastAttempts: string[] = [];
let networkUnreachable = false;

function noteAiAttempt(kind: AiAttemptKind, reason: string) {
  lastAttempts.push(`${kind}: ${reason}`);
  if (lastAttempts.length > 6) lastAttempts = lastAttempts.slice(-6);
}

export function getLastAiAttempts(): string[] {
  return [...lastAttempts];
}

/** True when the device could not reach the network at all (not throttling). */
export function isOfflineDevice(): boolean {
  return networkUnreachable;
}

function noteNetworkFailure(err: unknown) {
  const name = (err as Error)?.name || '';
  // AbortError is our own timeout: the network may be fine, just slow.
  if (name === 'AbortError') return;
  networkUnreachable = true;
}

const RATE_LIMIT_WINDOW_MS = 15000;
let rateLimitedUntil = 0;

function markModelRateLimited() {
  rateLimitedUntil = Date.now() + RATE_LIMIT_WINDOW_MS;
}

function markModelAnswered() {
  rateLimitedUntil = Date.now() + RATE_LIMIT_WINDOW_MS;
}

function isModelRateLimited(): boolean {
  return Date.now() < rateLimitedUntil;
}

/** Small stable key for the fact cache, so repeats reuse the same lookup. */
function extractCacheSubject(query: string): string {
  return query.trim().toLowerCase().replace(/\s+/g, ' ').slice(0, 80);
}

/** Free, key-less models tried in order until one answers. */
const LIVE_MODELS = ['openai', 'mistral', 'llama', 'openai-fast'];

const SYSTEM_PROMPT = `You are BrowserHub AI, the assistant built into the BrowserHub Android browser.
Rules:
- Reply in clean Markdown: short paragraphs, "### " headings, "- " bullets, **bold** for key terms. Never output raw HTML.
- Be warm, direct and concise. Answer the user's actual question first.
- If webpage text is provided, use it only when the question is about that page; otherwise answer normally from your own knowledge.
- If the user is on a search results page, never quote the result listing, menus or filter bars as if they were article content.
- Never invent page contents, statistics, or citations. If you are unsure, say so plainly.
- Keep answers under ~250 words unless asked for detail.`;

/**
 * Hybrid zero-key runner:
 * 1. Try a free, key-less public LLM endpoint when the device is online.
 * 2. If that is unreachable, fall back to the honest local engine above.
 */
export async function runSmartHybridAi(input: LocalAiInput): Promise<LocalAiOutput> {
  const start = performance.now();
  lastAttempts = [];
  const mode = input.mode || 'chat';
  const query = (input.query || '').trim();
  const ctx = getPageContextForAi(input.url || 'browserhub://start', input.content);
  const pageTitle = input.title && input.title !== input.url ? input.title : ctx.title;

  // Instant, network-free answers: greetings, math, drafting, page privacy audit.
  const instant = query ? handleNonFactualQuery(query, ctx) : null;
  if (instant) {
    return {
      ok: true,
      model: 'BrowserHub-Instant',
      engineLabel: 'Instant local reply',
      zeroApiKey: true,
      zeroTelemetry: true,
      inferenceMs: 1,
      responseText: instant.text,
      bullets: instant.bullets,
    };
  }
  if (mode === 'security_audit') {
    return runLocalAiClient(input);
  }

  // Same question, same page → reuse a real previous answer instead of asking
  // the network again. This is what makes re-tapping a prompt instant.
  const cacheKey = [mode, query, ctx.kind, ctx.text.slice(0, 160)].join('|');
  const cached = getCachedAnswer(cacheKey);
  // A saved *model* answer is authoritative — reuse it immediately. A saved
  // encyclopedia answer is only a stand-in, so it must not stop us asking the
  // live model that has since come back online.
  const cachedIsModelAnswer = !!cached && !/wikipedia/i.test(cached.engineLabel || '');
  if (cached && cachedIsModelAnswer) {
    return {
      ok: true,
      model: cached.model,
      engineLabel: `${cached.engineLabel} (saved)`,
      zeroApiKey: true,
      zeroTelemetry: true,
      inferenceMs: 1,
      responseText: cached.text,
      bullets: ['Answered from a saved reply — no new request was made'],
    };
  }

  const hasPageText = ctx.text.length >= 60;
  const pageBlock = hasPageText
    ? `\n\n--- OPEN WEBPAGE ---\nTitle: ${pageTitle}\nURL: ${input.url}\nContext: ${ctx.contextNote}\nVisible text:\n"""${ctx.text.slice(0, 3500)}"""\n--- END WEBPAGE ---`
    : `\n\nContext: ${ctx.contextNote}`;

  let userPrompt: string;
  if (mode === 'summarize') {
    if (!hasPageText) return runLocalAiClient(input);
    userPrompt = `Summarize this webpage into 4-5 crisp bullet points. Only use the text inside the webpage block.${pageBlock}`;
  } else if (mode === 'eli5') {
    if (!hasPageText) return runLocalAiClient(input);
    userPrompt = `Explain this webpage to a curious beginner in plain language, 3-4 short paragraphs.${pageBlock}`;
  } else if (mode === 'browse_for_me') {
    userPrompt = `${query || pageTitle}${pageBlock}`;
  } else {
    userPrompt = `${query}${pageBlock}`;
  }

  const priorMsgs = (input.history || [])
    .slice(-6)
    .map((m) => ({ role: m.role, content: m.content }));

  const liveReply = (reply: string): LocalAiOutput => ({
    ok: true,
    model: 'BrowserHub-Live-LLM',
    engineLabel: 'Live AI (no key needed)',
    zeroApiKey: true,
    zeroTelemetry: true,
    inferenceMs: Math.round(performance.now() - start),
    responseText: reply,
    bullets: hasPageText ? ['Answered using the clean text of this page'] : [],
  });

  // Live attempts run with a shared budget so the drawer never looks frozen.
  const persist = (out: LocalAiOutput): LocalAiOutput => {
    if (out.responseText.trim()) {
      rememberAnswer(cacheKey, {
        text: out.responseText,
        engineLabel: out.engineLabel,
        model: out.model,
        savedAt: Date.now(),
      });
    }
    return out;
  };

  // The free model endpoint is anonymously rate limited to about one request
  // every 15 s. Asking again inside that window is guaranteed to fail, so we
  // skip straight to the fact layer instead of making the user wait.
  const rateLimited = isModelRateLimited();

  // Wikipedia has its own limits and needs no key, so it answers factual
  // questions independently of (and concurrently with) the live model. This is
  // what keeps the second question working when the model says 429.
  const factPromise = isFactualQuestion(query)
    ? singleFlight(`wiki:${extractCacheSubject(query)}`, () => answerFromWikipedia(query, 4500))
    : Promise.resolve(null);

  // Native worker thread (APK, no CORS, off the JS thread) gets first refusal.
  const nativeReply = rateLimited
    ? null
    : await singleFlight(`native:${cacheKey}`, () => askViaNativeBridge(userPrompt, 9000));
  if (nativeReply) {
    markModelAnswered();
    return persist(liveReply(nativeReply));
  }

  // Direct public endpoints. Several models are tried in order because a
  // single free endpoint can be rate-limited or briefly down — that is exactly
  // how "Who is Ronaldo?" answered and "Who is goat?" failed seconds later.
  const directReply = rateLimited ? null : await fetchLive(async (signal) => {
    for (const model of LIVE_MODELS) {
      try {
        const res = await fetch('https://text.pollinations.ai/openai', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          signal,
          body: JSON.stringify({
            model,
            messages: [
              { role: 'system', content: SYSTEM_PROMPT },
              ...priorMsgs,
              { role: 'user', content: userPrompt },
            ],
          }),
        });
        if (res.status === 429) {
          markModelRateLimited();
          noteAiAttempt('direct', 'rate limited (HTTP 429)');
          return null;
        }
        if (!res.ok) {
          noteAiAttempt('direct', `${model}: HTTP ${res.status}`);
          continue;
        }
        const data: any = await res.json();
        const text = data?.choices?.[0]?.message?.content;
        if (typeof text === 'string' && text.trim().length > 2) return text.trim();
      } catch (err) {
        noteNetworkFailure(err);
        // Abort means the budget is gone — stop trying further models.
        if ((err as Error)?.name === 'AbortError') {
          noteAiAttempt('direct', 'timed out');
          break;
        }
        noteAiAttempt('direct', `${model}: ${(err as Error)?.message || 'network error'}`);
      }
    }
    return null;
  }, 7000);
  if (directReply) {
    markModelAnswered();
    return persist(liveReply(directReply));
  }

  // Skip the proxy when we are throttled: it forwards to the same endpoint, so
  // it would fail too — waiting 3.5 s to confirm that is pure latency for the
  // user. A hard overall deadline also stops stacked timeouts.
  const outOfBudget = performance.now() - start > 14000;
  if (rateLimited) noteAiAttempt('proxy', 'skipped (model rate limited)');
  const proxyReply = rateLimited || outOfBudget ? null : await fetchLive(async (signal) => {
    const res = await fetch('./api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      signal,
      body: JSON.stringify({
        messages: priorMsgs,
        query: mode === 'chat' || mode === 'browse_for_me' ? query : '',
        mode,
        title: pageTitle,
        pageKind: ctx.kind,
        contextNote: ctx.contextNote,
        pageText: hasPageText ? ctx.text : '',
      }),
    });
    if (!res.ok) {
      noteAiAttempt('proxy', `HTTP ${res.status}`);
      return null;
    }
    const data: any = await res.json();
    if (!data?.ok) noteAiAttempt('proxy', data?.error || 'no answer in response');
    return data?.ok ? data.responseText ?? null : null;
  }, 3500);
  if (proxyReply) {
    markModelAnswered();
    return persist(liveReply(proxyReply));
  }

  const fact = await factPromise;
  const savedFallback = cached && !cachedIsModelAnswer ? cached : null;

  if (fact) {
    const out: LocalAiOutput = {
      ok: true,
      model: 'BrowserHub-Wikipedia',
      engineLabel: fact.sourceLabel,
      zeroApiKey: true,
      zeroTelemetry: true,
      inferenceMs: Math.round(performance.now() - start),
      responseText: fact.text,
      bullets: [
        `Source: ${fact.sourceLabel}`,
        'Live AI was unavailable, so this is the real article extract — not a generated guess',
      ],
    };
    return persist(out);
  }

  if (savedFallback) {
    return {
      ok: true,
      model: savedFallback.model,
      engineLabel: `${savedFallback.engineLabel} (saved)`,
      zeroApiKey: true,
      zeroTelemetry: true,
      inferenceMs: 1,
      responseText: savedFallback.text,
      bullets: ['Answered from a saved reply — no new request was made'],
    };
  }

  return runLocalAiClient(input);
}
