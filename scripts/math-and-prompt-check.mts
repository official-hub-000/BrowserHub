#!/usr/bin/env node
/**
 * Guards the two defects behind "it's not working" and "the answer is wrong".
 *
 * 1. ARITHMETIC. The user asked "Solve 1270+23-10/80×18%100" and the app
 *    refused it while advertising "exact math". Cause: the evaluator handed a
 *    string to Function(), and `×` was not in the allowed character set. A
 *    phone keyboard produces `×` and `÷`, so this had to be a real parser.
 *
 * 2. PROMPT CONTAMINATION. Every chat question used to be appended with the
 *    full visible text of whatever page was open, so a general question got
 *    answered from unrelated page content. Page text is now attached only when
 *    the question is actually about the page.
 *
 * Run: npx tsx scripts/math-and-prompt-check.mts
 */
import assert from 'node:assert';
import { evaluateArithmetic } from '../src/utils/mathEngine';

/* ------------------------------- arithmetic ------------------------------- */

const exact: Array<[string, string]> = [
  ['Solve 1270+23-10/80×18%100', '1290.75'],
  ['1270+23-10/80×18%100', '1290.75'],
  ['1270+23-10/80*18%100', '1290.75'],
  ['18% of 2450', '441'],
  ['15% of 80', '12'],
  ['2+2', '4'],
  ['(2+3)×4', '20'],
  ['2^10', '1024'],
  ['100÷4', '25'],
  ['7/2', '3.5'],
  ['1,000+500', '1500'],
  ['10 − 3', '7'],
  ['-5+12', '7'],
  ['what is 12*12', '144'],
  ['50%', '0.5'],
];

for (const [input, expected] of exact) {
  const got = evaluateArithmetic(input);
  assert.ok(got, `expected "${input}" to evaluate, got null`);
  assert.equal(got.value, expected, `"${input}" should be ${expected}, got ${got.value}`);
}

// The unicode multiplication sign must produce a *readable* expression, not a
// mangled one, and must show its working.
const worked = evaluateArithmetic('Solve 1270+23-10/80×18%100');
assert.ok(worked);
assert.ok(!worked.display.includes('×18'), `display should normalise ×, got "${worked.display}"`);
assert.ok(worked.steps.length >= 3, 'a multi-step expression must show its working');

// Division by zero and non-arithmetic text must fail cleanly, not return junk.
assert.equal(evaluateArithmetic('5/0'), null, 'division by zero must not return a number');
assert.equal(evaluateArithmetic('who is Cristiano Ronaldo'), null, 'text must not be treated as math');
assert.equal(evaluateArithmetic('1234'), null, 'a bare number is not a calculation');

/* --------------------------- prompt contamination -------------------------- */

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

let lastPrompt = '';
(globalThis as any).fetch = async (url: string, init?: any) => {
  if (String(url).includes('text.pollinations.ai')) {
    lastPrompt = JSON.parse(init.body).messages.slice(-1)[0].content;
    return { ok: true, status: 200, json: async () => ({ choices: [{ message: { content: 'ok' } }] }) } as any;
  }
  return { ok: false, status: 404, json: async () => ({}) } as any;
};

const { runSmartHybridAi } = await import('../src/utils/localAiEngine');

const PAGE =
  'Breaking News: The city council approved a new budget. Residents complained about parking. '.repeat(3);

const general = await runSmartHybridAi({
  mode: 'chat',
  query: 'What is the capital of Japan?',
  url: 'https://news.example/story',
  title: 'City Council News',
  content: PAGE,
} as any);
assert.ok(
  !lastPrompt.includes('OPEN WEBPAGE'),
  'a general question must NOT carry the page text (that is what made answers wrong)'
);
assert.ok(
  !/city council|parking/i.test(lastPrompt),
  'unrelated page content must not reach the model for a general question'
);
assert.ok(general.responseText.length > 0);

lastPrompt = '';
await runSmartHybridAi({
  mode: 'chat',
  query: 'Summarize this page',
  url: 'https://news.example/story',
  title: 'City Council News',
  content: PAGE,
} as any);
assert.ok(lastPrompt.includes('OPEN WEBPAGE'), 'a page question MUST still carry the page text');

// An explicit page reference is also enough.
lastPrompt = '';
await runSmartHybridAi({
  mode: 'chat',
  query: 'What does this article say about parking?',
  url: 'https://news.example/story',
  title: 'City Council News',
  content: PAGE,
} as any);
assert.ok(lastPrompt.includes('OPEN WEBPAGE'), 'a question about "this article" must carry the page');

/* ---------------------- no dynamic-code evaluation ------------------------ */
// The old evaluator used Function(); it is both a security smell and the exact
// reason `×` failed. Arithmetic must go through the parser only.
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

function walk(dir: string, out: string[] = []): string[] {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (/\.tsx?$/.test(e.name)) out.push(full);
  }
  return out;
}

for (const file of walk('src')) {
  const src = readFileSync(file, 'utf8');
  assert.ok(
    !/Function\(`/.test(src) && !/\beval\s*\(/.test(src),
    `${file}: dynamic code evaluation is not allowed for arithmetic`
  );
}

console.log(
  `math + prompt checks passed (${exact.length} expressions exact, page text attached only when relevant)`
);
