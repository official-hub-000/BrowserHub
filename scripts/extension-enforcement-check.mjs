// Every extension card claims an effectKey. Either that effect is implemented
// and mapped to a real code path, or the card must be labelled as a preview.
import fs from 'node:fs';

const presets = fs.readFileSync('src/data/presets.ts', 'utf8');
const catalogue = fs.readFileSync('src/data/extensionCatalogue.ts', 'utf8');
const effects = fs.readFileSync('src/utils/extensionEffects.ts', 'utf8');
const modal = fs.readFileSync('src/components/ExtensionsModal.tsx', 'utf8');
const app = fs.readFileSync('src/App.tsx', 'utf8');
const viewport = fs.readFileSync('src/components/WebViewport.tsx', 'utf8');
const nativeJava = fs.readFileSync('android/standalone/src/com/browserhub/app/MainActivity.java', 'utf8');

// Both sources offer extensions: the bundled presets and the store catalogue.
const keys = [
  ...presets.matchAll(/effectKey: '([\w-]+)'/g),
  ...catalogue.matchAll(/effectKey: '([\w-]+)'/g),
].map((m) => m[1]);
const enforced = [...(effects.match(/ENFORCED_EFFECT_KEYS[\s\S]*?\]\)/) || [''])[0].matchAll(/'([\w-]+)'/g)].map(
  (m) => m[1]
);

const effectsHelper = fs.readFileSync('src/utils/extensionEffects.ts', 'utf8');
const runtimeEngine = fs.readFileSync('src/utils/webextensionRuntime.ts', 'utf8');
const unpacker = fs.readFileSync('src/utils/extensionUnpacker.ts', 'utf8');

/*
 * Runtime-assigned keys. These are never hardcoded into a catalogue because
 * they are attached to whatever package the user sideloads — the effect depends
 * on the package's own manifest, not on a preset we shipped. They are still
 * held to the same standard: the key must be produced somewhere real and must
 * reach a code path that executes it.
 */
const runtimeAssigned = new Set(['webextension']);

// Each enforced effect must reach real code. Some are matched by their
// effectKey string, others through a dedicated helper in extensionEffects.ts.
const consumer = {
  ublock: [app, nativeJava],
  'privacy-badger': [app],
  'dark-reader': [app, nativeJava],
  'cookie-killer': [app, nativeJava],
  'clear-urls': [app],
  'custom-css': [effectsHelper + app + nativeJava],
  'custom-script': [effectsHelper + app + nativeJava],
  'bg-play': [effectsHelper + app + nativeJava],
  webextension: [runtimeEngine + unpacker + effectsHelper + app + nativeJava],
};
const wiring = {
  'custom-css': ['hasCustomCss', '__bh_user_css'],
  'custom-script': ['hasUserScript', 'applyUserScripts'],
  'bg-play': ['isBackgroundPlayEnabled', '__bhBgPlayOn'],
};

const bad = [];
for (const key of enforced) {
  if (!keys.includes(key) && !runtimeAssigned.has(key)) {
    bad.push(`enforced key '${key}' is not offered by any extension`);
  }
  if (runtimeAssigned.has(key)) {
    // Must be produced by the unpacker and executed through the runtime engine.
    const produced = unpacker.includes(`'${key}'`);
    // Require a call site, not just an import or a type reference: the import
    // name survives any refactor that stops calling the function.
    const executed =
      /function buildInjection|const buildInjection/.test(runtimeEngine) &&
      /buildExtensionInjection\s*\(/.test(app);
    if (!produced) bad.push(`runtime key '${key}' is never assigned by the unpacker`);
    if (!executed) bad.push(`runtime key '${key}' is assigned but never executed`);
  }
  const sources = consumer[key] || [];
  const needles = [key, ...(wiring[key] || [])];
  if (sources.length === 0) bad.push(`'${key}' has no consumer mapped`);
  else if (!needles.some((n) => sources.some((src) => src.includes(n)))) {
    bad.push(`'${key}' is listed as enforced but no code reads it`);
  }
}
// The catalogue states its status per entry, so a catalogue key is already
// labelled by the card it sits on; the modal must render that status.
const catalogueKeys = new Set([...catalogue.matchAll(/effectKey: '([\w-]+)'/g)].map((m) => m[1]));
const modalLabelsStatus = /Not working here|SUPPORT_BADGE/.test(modal);

for (const key of keys) {
  if (!enforced.includes(key) && !modal.includes('PREVIEW')) {
    if (catalogueKeys.has(key) && modalLabelsStatus) continue;
    bad.push(`'${key}' is neither enforced nor labelled a preview`);
  }
}
if (!modal.includes('isExtensionEnforced')) bad.push('store cards do not show the RUNS HERE / PREVIEW badge');

if (bad.length) {
  bad.forEach((b) => console.error(`  FAIL ${b}`));
  process.exit(1);
}
console.log(`  ${enforced.length} enforced effects, ${keys.length - enforced.length} labelled previews`);
