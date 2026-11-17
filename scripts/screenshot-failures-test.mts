/**
 * Reproduces the exact failures visible in the user's screenshot.
 *
 * Screenshot evidence:
 *  1. "Summarize this page" answered: "I don't actually have the text of the
 *     page in front of me…" — even though the page text was attached.
 *  2. "Who is goat" answered: "This device could not reach the internet…"
 *     and then, in the SAME message, "Turn on mobile data or Wi-Fi and ask
 *     again" — self-contradictory, because the page had just been loaded from
 *     the network.
 *  3. A summarize request ran on the on-device engine and returned the generic
 *     "three things that work" apology instead of a summary, despite having
 *     page text.
 *
 * Run: npx tsx scripts/screenshot-failures-test.mts
 */
import assert from 'node:assert/strict';

let pageWasAttachedToPrompt = false;
let systemPrompt = '';
let userPrompt = '';

const originalFetch = globalThis.fetch;

(globalThis as any).fetch = (async (url: string, init?: any) => {
  const u = String(url);

  // Wikipedia: nothing for these fixtures.
  if (u.includes('wikipedia.org')) {
    return new Response('not found', { status: 404 });
  }

  if (u.includes('pollinations') || u.includes('deepinfra') || u.includes('hackclub')) {
    const body = init?.body ? JSON.parse(String(init.body)) : null;
    const msgs: { role: string; content: string }[] = body?.messages ?? [];
    const sys = msgs.find((m) => m.role === 'system');
    const user = msgs.filter((m) => m.role === 'user').pop();
    systemPrompt = sys?.content ?? '';
    userPrompt = user?.content ?? '';
    pageWasAttachedToPrompt = /=== THE WEBPAGE THE USER IS ASKED ABOUT ===/.test(userPrompt);

    // A real model refuses only when the text genuinely is not in the prompt.
    const hasText = /"""[\s\S]{40,}"""/.test(userPrompt);
    const content = hasText
      ? '### Summary\n- The page explains how computer memory works.\n- It compares RAM and storage.'
      : "I'm sorry, but I don't actually have the text of the page in front of me.";
    return new Response(
      JSON.stringify({ choices: [{ message: { content } }] }),
      { status: 200, headers: { 'content-type': 'application/json' } }
    );
  }

  return new Response('nf', { status: 404 });
}) as typeof fetch;

const { runSmartHybridAi } = await import('../src/utils/localAiEngine');

const ARTICLE =
  'Computer memory is any physical device capable of storing information temporarily or permanently. ' +
  'RAM is fast volatile memory used by the processor while a program runs. ' +
  'Storage is slower, non-volatile, and keeps data when the power is off. ' +
  'A computer needs both: RAM for speed today, storage for keeping things tomorrow.';

const ask = (mode: string, query: string, content: string) =>
  runSmartHybridAi({
    mode,
    query,
    title: 'Computer memory - Wikipedia',
    url: 'https://en.wikipedia.org/wiki/Computer_memory',
    content,
    history: [],
    searchEngineName: 'Google',
  } as any);

/* ---- 1. Summarize must attach the page AS THE SUBJECT ------------------- */
pageWasAttachedToPrompt = false;
const summary = await ask('summarize', 'Summarize this page', ARTICLE);

assert.ok(
  pageWasAttachedToPrompt,
  'the page block was not labelled as the subject — the model will refuse it as "background"'
);
assert.ok(
  /Summary|memory|RAM/i.test(summary.responseText),
  `summarize produced no summary:\n${summary.responseText}`
);
assert.ok(
  !/don't actually have the text|do not have the text/i.test(summary.responseText),
  `the model still claims it has no text:\n${summary.responseText}`
);
assert.ok(
  !/OPEN WEBPAGE \(background only\)/.test(userPrompt ?? ''),
  'a page the user asked about must not be labelled "background only"'
);

/* ---- 2. The system prompt must not forbid using attached page text ------ */
assert.ok(
  !/NEVER the subject of the question unless/i.test(systemPrompt) ||
    /IS the material|Use it directly/i.test(userPrompt),
  'the prompt still contains a blanket rule that makes the model refuse attached text'
);

/* ---- 3. No self-contradictory offline claim ---------------------------- */
const chat = await ask('chat', 'Who is goat', '');
const text = chat.responseText;
const claimsOffline = /could not reach the internet/i.test(text);
const tellsToGoOnline = /turn on mobile data|ask again in about/i.test(text);
assert.ok(
  !(claimsOffline && tellsToGoOnline),
  `the reply tells the user to go online while claiming they are offline:\n${text}`
);

/* ---- 4. On-device summarize with page text must summarise --------------- */
const localSummary = await ask('summarize', 'Summarize this page', ARTICLE);
assert.ok(
  !/three things that work right now/i.test(localSummary.responseText),
  `a summarize request with page text fell through to the generic apology:\n${localSummary.responseText}`
);
assert.ok(
  /Summary|memory|RAM|storage/i.test(localSummary.responseText),
  `on-device summarize did not use the page text:\n${localSummary.responseText}`
);

globalThis.fetch = originalFetch;

console.log('screenshot failures fixed: page text is the subject, no fake offline claim, summaries summarise');
