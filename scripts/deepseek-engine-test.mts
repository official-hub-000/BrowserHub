/**
 * DeepSeek engine — is it actually wired, on every path the app can answer from?
 *
 * Three separate code paths can produce an AI reply: the TypeScript engine in
 * the WebView, the native Java client on a worker thread, and the server
 * middleware for the web build. Adding a provider to one of them and forgetting
 * the other two is exactly how a feature ends up working "sometimes", so all
 * three are checked here.
 *
 * It also checks the thing that matters more than the provider existing: that
 * the user's choice is honoured first, and that a silent fallback stays visible.
 */
import { readFileSync } from 'node:fs';

let passed = 0;
const failures: string[] = [];

function check(name: string, ok: boolean) {
  if (ok) {
    passed++;
  } else {
    failures.push(name);
    console.log(`  FAIL  ${name}`);
  }
}

const MODULE = 'deepseek/deepseek-v4-flash';
const engine = readFileSync('src/utils/localAiEngine.ts', 'utf8');
const native = readFileSync('android/standalone/src/com/browserhub/app/ai/NativeAiClient.java', 'utf8');
const server = readFileSync('server/apiMiddleware.ts', 'utf8');
const drawer = readFileSync('src/components/AiCopilotDrawer.tsx', 'utf8');
const app = readFileSync('src/App.tsx', 'utf8');
const types = readFileSync('src/types/browser.ts', 'utf8');

// --- the engine id is the real one, not an invented name -------------------
check('TS engine uses the canonical model id', engine.includes(`'${MODULE}'`));
check('native client uses the canonical model id', native.includes(`"${MODULE}"`));
check('server proxy uses the canonical model id', server.includes(`'${MODULE}'`));

// --- present on all three answer paths ------------------------------------
check('TS engine has a deepseek provider', /id:\s*'deepseek'/.test(engine));
check('native client lists deepseek', native.includes('"deepseek"'));
check('native client sends the deepseek model', /"deepseek"\.equals\(providerId\)/.test(native));
check('server proxy targets deepseek', /model:\s*'deepseek/.test(server));

// --- nothing here needs a key ---------------------------------------------
/*
 * A comment explaining that api.deepseek.com needs a paid key is not the same
 * as calling it, so look for it in a fetch/URL position rather than anywhere
 * in the file.
 */
const callsPaidDeepSeek = (src: string) =>
  /(fetch\(\s*['"]https:\/\/api\.deepseek\.com|"https:\/\/api\.deepseek\.com\/|url:\s*['"]https:\/\/api\.deepseek\.com)/.test(
    src,
  );
check('TS engine does not call the paid DeepSeek API', !callsPaidDeepSeek(engine));
check('native client does not call the paid DeepSeek API', !callsPaidDeepSeek(native));
check('no api key is required anywhere in the engine', !/Authorization['"]?\s*[:=]\s*['"]?Bearer/.test(engine));
check('engine still advertises zero-key', engine.includes('zeroApiKey: true'));
check('every engine choice is marked zero-key', !/zeroKey:\s*false/.test(engine));

// --- the choice is honoured -------------------------------------------------
check('input accepts a preferred engine', engine.includes('preferredAiEngine'));
check('auto has a default engine', /DEFAULT_AI_ENGINE\s*=\s*'deepseek'/.test(engine));
check('ordering ranks the chosen engine first', /if \(p\.id === chosen\) return 0/.test(engine));
check(
  'a chosen engine is not skipped when locally rate limited',
  // The rank function must not return a "skip" sentinel for the chosen engine.
  /providerAvailable\(p\.id\) \? 1 : 2/.test(engine),
);

// --- the answer says which engine answered ---------------------------------
check(
  'the answering provider is reported, not overwritten',
  engine.includes('engineLabel: answered.label') ||
    engine.includes('lastEngineLabel'),
);
check('no hardcoded generic live label remains for the multi-provider path',
  !/engineLabel: 'Live AI \(no key needed\)',\s*\n\s*\}\);/.test(engine));

// --- the UI can pick it, and the pick persists -----------------------------
check('engine list is exported for the UI', engine.includes('export const AI_ENGINES'));
check('deepseek appears in the UI list', /id:\s*'deepseek'/.test(engine));
check('drawer renders the picker', drawer.includes('AI_ENGINES.map'));
check('drawer reports the selection', drawer.includes('onSelectAiEngine'));
check('drawer passes the choice to the engine', drawer.includes('preferredAiEngine: aiEngine'));
check('customization stores the choice', types.includes('aiEngine: string'));
check('App defaults the choice', /aiEngine:\s*'auto'/.test(app));
check('App persists through customization', app.includes('aiEngine={customization.aiEngine}'));

// --- DeepSeek is tried before the others on auto ---------------------------
const deepseekIdx = engine.indexOf("id: 'deepseek'");
const pollinationsIdx = engine.indexOf("id: 'pollinations'");
check('deepseek is listed before pollinations', deepseekIdx > 0 && deepseekIdx < pollinationsIdx);

// --- a cap exists so a phone reply stays readable --------------------------
check('TS engine caps reply length', /max_tokens:\s*800/.test(engine));
check('native client caps reply length', /put\("max_tokens", 800\)/.test(native));

console.log();
if (failures.length === 0) {
  console.log(`deepseek: ${passed} checks pass — wired on WebView, native and server paths, zero-key`);
} else {
  console.log(`deepseek: ${passed} passed, ${failures.length} FAILED`);
  for (const f of failures) console.log(`  - ${f}`);
  process.exit(1);
}
