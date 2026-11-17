#!/usr/bin/env node
/**
 * Reproduces the device AI failure in a simulation and asserts it is fixed.
 *
 * The reported bug: "Who is Cristiano Ronaldo" was answered by the live model,
 * then "Who is goat?" — seconds later — fell through to an apology. Root cause:
 * the free model endpoint is anonymously rate limited to about one request per
 * 15 s, and rotating models does not help because they all share that endpoint.
 * On top of that the worst-case path stacked to ~36 s of spinner.
 *
 * This check mocks that exact endpoint behaviour (one success, then 429 until
 * the window passes) and asserts:
 *   1. a factual question asked while throttled is still answered, from
 *      Wikipedia, and is labelled with its source;
 *   2. the answer is not invented — it comes from the article extract;
 *   3. a repeated question is served from cache without a new network call;
 *   4. slang is explained locally instead of being sent to an encyclopedia;
 *   5. the whole flow stays fast (no 36 s stall).
 *
 * Run: npx tsx scripts/ai-throttle-simulation.mts
 */
import assert from 'node:assert';

const mem: Record<string, string> = {};
(globalThis as any).localStorage = {
  getItem: (k: string) => mem[k] ?? null,
  setItem: (k: string, v: string) => {
    mem[k] = v;
  },
  removeItem: (k: string) => {
    delete mem[k];
  },
};

let modelCalls = 0;
let throttled = false;

const ARTICLES: Record<string, any> = {
  cristiano_ronaldo: {
    title: 'Cristiano Ronaldo',
    type: 'standard',
    extract:
      'Cristiano Ronaldo dos Santos Aveiro is a Portuguese professional footballer who plays as a forward for Al Nassr and the Portugal national team. He is widely regarded as one of the greatest players of all time.',
    content_urls: { desktop: { page: 'https://en.wikipedia.org/wiki/Cristiano_Ronaldo' } },
  },
  goat: {
    title: 'Goat',
    type: 'standard',
    extract:
      'The goat or domestic goat is a species of cloven-hoofed ruminant mammal. It is one of the oldest domesticated species, kept for milk, meat, fur and skins across the world.',
    content_urls: { desktop: { page: 'https://en.wikipedia.org/wiki/Goat' } },
  },
};

(globalThis as any).fetch = async (url: string) => {
  const u = String(url);

  // The throttled free LLM endpoint: exactly one anonymous success, then 429.
  if (u.includes('text.pollinations.ai')) {
    modelCalls += 1;
    if (throttled) return { ok: false, status: 429, json: async () => ({}) } as any;
    throttled = true;
    return {
      ok: true,
      status: 200,
      json: async () => ({
        choices: [{ message: { content: 'A live model reply that must not be invented here.' } }],
      }),
    } as any;
  }

  if (u.includes('wikipedia.org/api/rest_v1/page/summary/')) {
    const raw = decodeURIComponent(u.split('/summary/')[1] || '').split('?')[0];
    const key = raw.replace(/\s+/g, '_').toLowerCase();
    const hit = ARTICLES[key];
    if (hit) return { ok: true, status: 200, json: async () => hit } as any;
    return { ok: false, status: 404, json: async () => ({}) } as any;
  }

  // Wikipedia search fallback: no results for these fixtures.
  return { ok: true, status: 200, json: async () => ({ query: { search: [] } }) } as any;
};

const { runSmartHybridAi } = await import('../src/utils/localAiEngine');

async function ask(query: string) {
  const started = Date.now();
  const out = await runSmartHybridAi({
    mode: 'chat',
    query,
    url: 'browserhub://start',
    title: 'New Tab',
    content: '',
  } as any);
  return { out, ms: Date.now() - started };
}

// 1. The model answers the first question and then throttles.
const first = await ask('Who is Cristiano Ronaldo?');
assert.ok(first.out.responseText.length > 10, 'first question should be answered');

// 2. The device failure case: a second factual question while throttled.
const second = await ask('Who is the animal goat?');
assert.match(
  second.out.engineLabel,
  /Wikipedia/,
  `second question must be answered from Wikipedia, got "${second.out.engineLabel}"`
);
assert.match(second.out.responseText, /domestic goat|cloven-hoofed/i, 'answer must be the real article text');
assert.ok(
  !/couldn't reach a live AI model/i.test(second.out.responseText),
  'second question must not fall through to the apology'
);
assert.ok(
  second.out.bullets?.some((b) => /Source:/i.test(b)),
  'a Wikipedia answer must state its source'
);

// 3. Repeating the first question must not hit the network again.
const before = modelCalls;
const repeat = await ask('Who is Cristiano Ronaldo?');
assert.match(repeat.out.engineLabel, /saved/, 'repeat should be served from cache');
assert.equal(modelCalls, before, 'a cached answer must not make a new model call');

// 4. Slang stays local: it must not be shipped to an encyclopedia as a name.
const slang = await ask('Who is goat?');
assert.ok(
  /greatest of all time/i.test(slang.out.responseText),
  'slang must be explained as slang, not looked up as a person'
);

// 5. No stall: the throttled path must be fast, not the old ~36 s stack.
assert.ok(second.ms < 3000, `throttled answer took ${second.ms} ms; expected under 3 s`);
assert.ok(repeat.ms < 1000, `cached answer took ${repeat.ms} ms`);

console.log(
  `AI throttle simulation passed (2nd question ${second.ms} ms, repeat ${repeat.ms} ms, ` +
    `${modelCalls} model call${modelCalls === 1 ? '' : 's'})`
);
