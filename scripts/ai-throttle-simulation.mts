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

import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const MODE = process.env.BH_AI_MODE as 'throttled' | 'offline' | 'healthy' | undefined;
if (!MODE) {
  // Module state (rate-limit window, answer cache) is per-process, so each
  // condition must run in its own process or the results leak into each other.
  for (const mode of ['throttled', 'offline', 'healthy'] as const) {
    const res = spawnSync(process.execPath, ['--import', 'tsx', fileURLToPath(import.meta.url)], {
      env: { ...process.env, BH_AI_MODE: mode },
      encoding: 'utf8',
    });
    const line = (res.stdout || '').trim().split('\n').pop() || '';
    if (res.status !== 0) {
      console.error(res.stdout);
      console.error(res.stderr);
      process.exit(1);
    }
    console.log(line);
  }
  console.log('AI resilience verified for throttled, offline and healthy providers');
  process.exit(0);
}

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
  hamlet: {
    title: 'Hamlet',
    type: 'standard',
    extract:
      'The Tragedy of Hamlet, Prince of Denmark is a tragedy written by William Shakespeare sometime between 1599 and 1601. It is regarded as one of the most powerful and influential works in world literature.',
    content_urls: { desktop: { page: 'https://en.wikipedia.org/wiki/Hamlet' } },
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

  // The free LLM endpoint, behaving exactly as the condition requires.
  if (u.includes('text.pollinations.ai')) {
    modelCalls += 1;
    if (MODE === 'offline') throw new TypeError('Load failed');
    if (MODE === 'throttled') return { ok: false, status: 429, json: async () => ({}) } as any;
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

if (MODE === 'healthy') {
  // The live model works: its answer must be used, not a stand-in.
  const live = await ask('Who is Cristiano Ronaldo?');
  assert.match(live.out.engineLabel, /Live AI/, 'a healthy provider must answer directly');
  assert.match(live.out.responseText, /live model reply/i);
  assert.ok(!/wikipedia/i.test(live.out.engineLabel), 'must not prefer a stand-in when live AI works');
  console.log(`healthy provider answered live in ${live.ms} ms`);
  process.exit(0);
}

if (MODE === 'throttled') {
  // The model is rate limited from the very first call.
  const first = await ask('Who is Cristiano Ronaldo?');
  assert.match(first.out.engineLabel, /Wikipedia/, `throttled question must use Wikipedia, got "${first.out.engineLabel}"`);
  assert.match(first.out.responseText, /Portuguese professional footballer/i);
  assert.ok(
    !/couldn't reach a live AI model/i.test(first.out.responseText),
    'must not fall through to the apology while a source is available'
  );

  // A second, differently shaped factual question must also be answered.
  const second = await ask('Who wrote Hamlet?');
  assert.match(second.out.engineLabel, /Wikipedia/, `second factual question must be answered, got "${second.out.engineLabel}"`);

  // A repeat must not hit the (dead) model endpoint again.
  const before = modelCalls;
  const repeat = await ask('Who is Cristiano Ronaldo?');
  assert.equal(modelCalls, before, 'a repeat must not call the throttled endpoint again');
  assert.match(
    repeat.out.engineLabel,
    /Wikipedia/,
    `repeat should still be answered from a source, got "${repeat.out.engineLabel}"`
  );

  // Slang is explained locally, and says why the model was not used.
  const slang = await ask('Who is goat?');
  assert.match(slang.out.responseText, /greatest of all time/i);
  assert.match(slang.out.responseText, /rate limited/i, 'must name the real reason');

  assert.ok(first.ms < 3000, `throttled answer took ${first.ms} ms`);
  console.log(`throttled provider answered from source in ${first.ms} ms`);
  process.exit(0);
}

// MODE === 'offline': the device has no working network at all.
const offlineFact = await ask('Who is Cristiano Ronaldo?');
assert.ok(
  /could not reach the internet|Wikipedia/i.test(offlineFact.out.responseText) ||
    /Wikipedia/.test(offlineFact.out.engineLabel),
  'offline factual question must be answered or explain the connection problem'
);
const offlineChat = await ask('Who is goat?');
assert.match(offlineChat.out.responseText, /could not reach the internet/i, 'must name the connection, not rate limiting');
assert.ok(
  !/ask again in about 15 seconds/i.test(offlineChat.out.responseText),
  'must not tell an offline device to retry in 15 seconds'
);
console.log(`offline device handled honestly in ${offlineChat.ms} ms`);
process.exit(0);


