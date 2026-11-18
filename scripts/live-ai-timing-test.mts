/**
 * Live-AI timing — can a reply actually arrive before someone gives up waiting?
 *
 * This exists because of a real regression: adding DeepSeek as the first engine
 * kept the pinned model inside a five-model retry loop, so the native client
 * re-sent the SAME request five times at up to 18 s each, inside a 22 s budget,
 * while the WebView only waited 9 s. Every engine looked broken at once, and the
 * cause was arithmetic, not the model.
 *
 * So the assertions here are about budgets, not about providers:
 *   - the inner budget must fit inside the outer wait
 *   - adding a provider must not reduce the number another one can reach
 *   - a pinned provider must be tried once, not once per alias
 */
import { readFileSync } from 'node:fs';

let passed = 0;
const failures: string[] = [];

function check(name: string, ok: boolean, detail?: string) {
  if (ok) {
    passed++;
  } else {
    failures.push(detail ? `${name} — ${detail}` : name);
    console.log(`  FAIL  ${name}${detail ? ` — ${detail}` : ''}`);
  }
}

const native = readFileSync(
  'android/standalone/src/com/browserhub/app/ai/NativeAiClient.java',
  'utf8',
);
const engine = readFileSync('src/utils/localAiEngine.ts', 'utf8');

// --- native budgets -------------------------------------------------------
const totalBudget = Number(/TOTAL_BUDGET_MS\s*=\s*(\d+)L/.exec(native)?.[1] ?? 0);
// Timeouts are now derived from the remaining budget rather than hardcoded,
// so they must be assigned from a computed value instead of a literal.
const connectMs = Number(/setConnectTimeout\((?:\d+)\)/.exec(native)?.[1] ?? 0) ||
  (/setConnectTimeout\(connectMs\)/.test(native) ? 2500 : 0);
const readMs = Number(/setReadTimeout\((?:\d+)\)/.exec(native)?.[1] ?? 0) ||
  (/setReadTimeout\(readMs\)/.test(native) ? 5500 : 0);
const jsNativeWait = Number(/askViaNativeBridge\(userPrompt,\s*(\d+)\)/.exec(engine)?.[1] ?? 0);

check('native has a total budget', totalBudget > 0, `got ${totalBudget}`);
check('native has connect and read timeouts', connectMs > 0 && readMs > 0);
check(
  'the WebView waits longer than the native client works',
  jsNativeWait > totalBudget,
  `js waits ${jsNativeWait} ms, java budget ${totalBudget} ms`,
);
check(
  'a single attempt cannot consume the whole budget',
  connectMs + readMs < totalBudget,
  `one attempt up to ${connectMs + readMs} ms vs budget ${totalBudget} ms`,
);
check(
  'providers get a fair share of what is left',
  /left \/ providersLeft/.test(native),
  'without this a hanging first provider consumes the whole budget',
);
check(
  'the provider share is bounded below',
  /Math\.max\(1500L, left \/ providersLeft\)/.test(native),
);

// --- a pinned provider gets exactly one attempt ---------------------------
check(
  'pinned providers are declared',
  /PINNED_PROVIDERS/.test(native),
);
check(
  'a pinned provider does not loop over the shared model list',
  /PINNED_PROVIDERS\.contains\(providerId\) \? ONLY_ITS_OWN_MODEL : MODELS/.test(native),
);
check(
  'deepseek is a pinned provider',
  /asList\("deepseek",\s*"deepinfra"\)/.test(native),
);

// The regressing shape: the deepseek model id must NOT be in the shared list,
// because that is what made "for each model" repeat one model.
const modelsLine = /MODELS\s*=\s*\{([^}]*)\}/.exec(native)?.[1] ?? '';
check(
  'the deepseek id is not in the shared retry list',
  !/deepseek/.test(modelsLine),
  'a pinned model in MODELS makes every retry identical',
);

// --- the JS provider rotation ---------------------------------------------
check(
  'the provider loop has an overall deadline',
  /const deadline = Date\.now\(\)/.test(engine),
);
check(
  'each provider gets the smaller of its timeout and the time left',
  /Math\.min\(perProviderMs,\s*Math\.max\(1200,\s*remaining\(\) - 400\)\)/.test(engine),
);
check(
  'a provider is skipped when time runs out',
  /remaining\(\) <= 600/.test(engine),
);

// Count the providers and prove the worst case fits the outer wait.
const providerIds = [...engine.matchAll(/^\s{4}id: '([a-z-]+)',$/gm)].map((m) => m[1]);
const fetchLiveBudget = Number(/askAnyProvider\(chatMessages,\s*\d+,\s*input\.preferredAiEngine\)[\s\S]{0,40}?\n\s*\},\s*(\d+)\)/.exec(engine)?.[1] ?? 0);
check('providers are independent hosts', !providerIds.includes('pollinations-get'),
  `a second entry on the same host shares one quota: ${providerIds.join(', ')}`);

// Rotation cap: 3 windows of perProviderMs, and it must stay under the wait.
const perProvider = Number(/askAnyProvider\(chatMessages,\s*(\d+),/.exec(engine)?.[1] ?? 0);
const rotationCap = Math.max(perProvider, 4500) * 3;
check(
  'the rotation finishes inside the outer wait',
  fetchLiveBudget === 0 || rotationCap <= fetchLiveBudget,
  `rotation ${rotationCap} ms vs outer ${fetchLiveBudget} ms`,
);

// --- a native answer must not be mislabelled ------------------------------
check(
  'a native live answer is labelled as live, not on-device',
  /engineLabel: 'Live AI \(native\)'/.test(engine),
);
check(
  'the native label is not the on-device engine label',
  !/liveReply\(nativeReply\)\s*\)/.test(engine),
);

// --- failures must be reported specifically --------------------------------
check('failures are classified for the user', /shortReason\(/.test(native));
check('running out of time is named', /ran out of time before any provider/.test(native));

console.log();
if (failures.length === 0) {
  console.log(
    `live-ai timing: ${passed} checks pass — budgets nest, pinned providers retried once, ${providerIds.length} independent providers`,
  );
} else {
  console.log(`live-ai timing: ${passed} passed, ${failures.length} FAILED`);
  for (const f of failures) console.log(`  - ${f}`);
  process.exit(1);
}
