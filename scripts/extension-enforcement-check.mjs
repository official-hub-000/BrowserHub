// Every extension card claims an effectKey. Either that effect is implemented
// and mapped to a real code path, or the card must be labelled as a preview.
import fs from 'node:fs';

const presets = fs.readFileSync('src/data/presets.ts', 'utf8');
const effects = fs.readFileSync('src/utils/extensionEffects.ts', 'utf8');
const modal = fs.readFileSync('src/components/ExtensionsModal.tsx', 'utf8');
const app = fs.readFileSync('src/App.tsx', 'utf8');
const viewport = fs.readFileSync('src/components/WebViewport.tsx', 'utf8');
const nativeJava = fs.readFileSync('android/standalone/src/com/browserhub/app/MainActivity.java', 'utf8');

const keys = [...presets.matchAll(/effectKey: '([\w-]+)'/g)].map((m) => m[1]);
const enforced = [...(effects.match(/ENFORCED_EFFECT_KEYS[\s\S]*?\]\)/) || [''])[0].matchAll(/'([\w-]+)'/g)].map(
  (m) => m[1]
);

const effectsHelper = fs.readFileSync('src/utils/extensionEffects.ts', 'utf8');

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
};
const wiring = {
  'custom-css': ['hasCustomCss', '__bh_user_css'],
  'custom-script': ['hasUserScript', 'applyUserScripts'],
  'bg-play': ['isBackgroundPlayEnabled', '__bhBgPlayOn'],
};

const bad = [];
for (const key of enforced) {
  if (!keys.includes(key)) bad.push(`enforced key '${key}' is not offered by any extension`);
  const sources = consumer[key] || [];
  const needles = [key, ...(wiring[key] || [])];
  if (sources.length === 0) bad.push(`'${key}' has no consumer mapped`);
  else if (!needles.some((n) => sources.some((src) => src.includes(n)))) {
    bad.push(`'${key}' is listed as enforced but no code reads it`);
  }
}
for (const key of keys) {
  if (!enforced.includes(key) && !modal.includes('PREVIEW')) {
    bad.push(`'${key}' is neither enforced nor labelled a preview`);
  }
}
if (!modal.includes('isExtensionEnforced')) bad.push('store cards do not show the RUNS HERE / PREVIEW badge');

if (bad.length) {
  bad.forEach((b) => console.error(`  FAIL ${b}`));
  process.exit(1);
}
console.log(`  ${enforced.length} enforced effects, ${keys.length - enforced.length} labelled previews`);
