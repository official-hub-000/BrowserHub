/**
 * Regression proof for the "always same / can't connect to live chat" report.
 *
 * Root cause found: `markModelAnswered()` set the same `rateLimitedUntil` value
 * as a 429, so ONE successful reply disabled the live model for the next 15 s.
 * The assistant worked once, then stayed on the apology for every follow-up.
 *
 * This test stubs fetch and asserts:
 *   1. a successful answer does not block the next question,
 *   2. a 429 on one provider does not stop a different provider answering,
 *   3. the whole chain still returns text (never the apology) when any
 *      provider is healthy.
 *
 * Run: npx tsx scripts/live-chat-recovery-test.mts
 */
import assert from 'node:assert/strict';

interface Call {
  url: string;
}

const calls: Call[] = [];
let pollinationsAlways429 = false;
let othersHealthy = true;

const originalFetch = globalThis.fetch;

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

globalThis.fetch = (async (input: any) => {
  const url = typeof input === 'string' ? input : input?.url ?? String(input);
  calls.push({ url });

  // Wikipedia resolves only for the article it actually has. Answering every
  // query would hide a broken chat path behind the encyclopedic fallback.
  if (url.includes('wikipedia.org')) {
    if (!/ronaldo/i.test(decodeURIComponent(url))) {
      return new Response('not found', { status: 404 });
    }
    return jsonResponse({
      title: 'Cristiano Ronaldo',
      extract:
        'Cristiano Ronaldo is a Portuguese professional footballer who plays as a forward. He is widely regarded as one of the greatest players of all time.',
      content_urls: { desktop: { page: 'https://en.wikipedia.org/wiki/Cristiano_Ronaldo' } },
    });
  }

  if (url.includes('text.pollinations.ai')) {
    if (pollinationsAlways429) {
      return new Response('rate limited', { status: 429 });
    }
    return jsonResponse({ choices: [{ message: { content: 'Pollinations answer.' } }] });
  }

  if (url.includes('deepinfra.com') || url.includes('hackclub.com')) {
    if (!othersHealthy) return new Response('down', { status: 503 });
    return jsonResponse({ choices: [{ message: { content: 'Independent provider answer.' } }] });
  }

  // Proxy and anything else.
  return new Response('not found', { status: 404 });
}) as typeof fetch;

const { runSmartHybridAi } = await import('../src/utils/localAiEngine');

const ask = (q: string) =>
  runSmartHybridAi({
    query: q,
    mode: 'chat',
    pageTitle: '',
    livePageText: '',
    searchEngineName: 'DuckDuckGo',
    history: [],
  } as any);

/* ---- 1. Two questions in a row must BOTH reach a live model -------------- */
calls.length = 0;
const first = await ask('What is a browser engine?');
const firstCalls = calls.length;
const second = await ask('What is a rendering pipeline?');
const secondCalls = calls.length - firstCalls;

assert.ok(first.responseText.length > 0, 'first question must answer');
assert.ok(second.responseText.length > 0, 'second question must answer');
assert.ok(
  secondCalls > 0,
  'the second question made no network attempt at all — success wrongly blocked the next ask'
);
assert.ok(
  !/busy or rate limited/i.test(second.responseText),
  `second question fell through to the apology despite a healthy provider:\n${second.responseText}`
);

/* ---- 2. One provider throttled must not silence the others -------------- */
pollinationsAlways429 = true;
calls.length = 0;
// A distinct query: reusing step 1's text would be served from the answer cache
// and would prove nothing about provider independence.
const throttled = await ask('Explain how HTTP caching works');
assert.ok(
  !/busy or rate limited/i.test(throttled.responseText),
  `all providers reported down even though independent ones were healthy:\n${throttled.responseText}`
);
const { getLastAiAttempts } = await import('../src/utils/localAiEngine');
console.error('  attempts:', getLastAiAttempts().join(' | '));
assert.ok(
  /Independent provider answer/.test(throttled.responseText),
  `pollinations was throttled and no OTHER provider answered — they are not independent:\n` +
    `got: ${throttled.responseText.slice(0, 200)}\n attempts: ${getLastAiAttempts().join(' | ')}`
);

/* ---- 3. Nothing healthy: a factual question still gets a real answer ---- */
pollinationsAlways429 = true;
othersHealthy = false;
const wikiOnly = await ask('Who is Cristiano Ronaldo?');
assert.ok(
  /footballer|Portuguese/i.test(wikiOnly.responseText),
  `a factual question must still be answered from the encyclopedia:\n${wikiOnly.responseText}`
);
assert.ok(
  !/Live AI was unavailable/i.test(wikiOnly.responseText),
  'the answer text still leads with the excuse instead of the facts'
);

globalThis.fetch = originalFetch;

console.log('live chat recovers: success does not self-block, providers are independent, facts survive');
