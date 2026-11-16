import {
  getPageContextForAi,
  sanitizeExtractedPageContent,
  type PageAiContext,
} from './pageTextExtractor';

export { sanitizeExtractedPageContent };
export type { PageAiContext };

export interface LocalAiInput {
  mode: string;
  title?: string;
  url?: string;
  content?: string;
  query?: string;
  history?: Array<{ role: 'user' | 'assistant'; content: string }>;
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
      // No live model, no page context. Be explicit instead of inventing an answer.
      responseText =
        `I couldn't reach a live AI model just now, so I can't reliably answer **"${query}"** from general knowledge offline.\n\n` +
        `What works right now:\n` +
        `- **Search it** — type the question in the address bar; BrowserHub opens it with the engine you selected.\n` +
        `- **Open a result**, then tap **Summarize** — I'll extract the real answer from that page on-device.\n` +
        `- **Math and drafting** still work offline (e.g. **"18% of 2450"** or **"draft an email about a late delivery"**).`;
      bullets = ['Retry once you are online for full conversational answers'];
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
async function askViaNativeBridge(prompt: string): Promise<string | null> {
  const bridge = typeof window !== 'undefined' ? window.BrowserHubNative : undefined;
  if (!bridge?.startAiRequest || !bridge?.pollAiResult) return null;

  const requestId = `ai-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  try {
    bridge.startAiRequest(requestId, prompt, SYSTEM_PROMPT);
  } catch {
    return null;
  }

  const deadline = Date.now() + 22000;
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
      return null;
    } catch {
      return null;
    }
  }
  return null;
}

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

  // Attempt 0 — native worker thread inside the APK (no CORS, never blocks the UI).
  const nativeReply = await askViaNativeBridge(userPrompt);
  if (nativeReply) return liveReply(nativeReply);

  // Attempt 1 — direct public endpoint (works where CORS allows).
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 8000);
    const res = await fetch('https://text.pollinations.ai/openai', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      signal: controller.signal,
      body: JSON.stringify({
        model: 'openai',
        messages: [
          { role: 'system', content: SYSTEM_PROMPT },
          ...priorMsgs,
          { role: 'user', content: userPrompt },
        ],
      }),
    });
    clearTimeout(timer);
    if (res.ok) {
      const data: any = await res.json();
      const reply: string | undefined = data?.choices?.[0]?.message?.content?.trim();
      if (reply && reply.length > 2) return liveReply(reply);
    }
  } catch {
    // network / CORS blocked — try the same-origin proxy next
  }

  // Attempt 2 — same-origin proxy (used by the Android shell build & web preview).
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 12000);
    const res = await fetch('./api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      signal: controller.signal,
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
    clearTimeout(timer);
    if (res.ok) {
      const data: any = await res.json();
      const reply: string | undefined = data?.responseText?.trim();
      if (data?.ok && reply && reply.length > 2) return liveReply(reply);
    }
  } catch {
    // offline — fall through to the honest local engine
  }

  return runLocalAiClient(input);
}
