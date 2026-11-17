#!/usr/bin/env node
/**
 * Keeps the extension store honest.
 *
 * The request was "a store where the user can download any extension like
 * Chrome, Edge or Firefox". Android WebView has no extension engine, so that
 * exact thing is impossible; what is possible is native equivalents plus an
 * explicit per-extension status. This check enforces the second half:
 *
 *   1. every entry marked `runs-here` must map to a real enforced effect,
 *   2. every entry must carry a plain-English `works` line,
 *   3. every entry must be visibly sorted into a status group,
 *   4. the UI must render the status, not just store it,
 *   5. the flat "everything is installable" list must not come back.
 *
 * Run: node scripts/extensions-honesty-check.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const problems = [];

const cataloguePath = path.join(ROOT, 'src/data/extensionCatalogue.ts');
const effectsPath = path.join(ROOT, 'src/utils/extensionEffects.ts');
const modalPath = path.join(ROOT, 'src/components/ExtensionsModal.tsx');

for (const f of [cataloguePath, effectsPath, modalPath]) {
  if (!fs.existsSync(f)) problems.push(`${path.relative(ROOT, f)}: missing`);
}

if (problems.length === 0) {
  const catalogue = fs.readFileSync(cataloguePath, 'utf8');
  const effects = fs.readFileSync(effectsPath, 'utf8');
  const modal = fs.readFileSync(modalPath, 'utf8');

  const enforcedBlock = effects.slice(
    effects.indexOf('ENFORCED_EFFECT_KEYS'),
    effects.indexOf(']);', effects.indexOf('ENFORCED_EFFECT_KEYS'))
  );
  const enforced = new Set([...enforcedBlock.matchAll(/'([a-z-]+)'/g)].map((m) => m[1]));

  /* Split the catalogue into its entries so each one can be checked. */
  const body = catalogue.slice(catalogue.indexOf('EXTENSION_CATALOGUE'), catalogue.indexOf('export const RUNS_HERE'));
  const entries = body.split(/\n  \{\n/).slice(1);

  let runsHere = 0;
  let listed = 0;

  for (const raw of entries) {
    const id = raw.match(/id:\s*'([^']+)'/)?.[1] ?? '(unnamed)';
    const support = raw.match(/support:\s*'([a-z-]+)'/)?.[1];
    const effectKey = raw.match(/effectKey:\s*'([a-z-]+)'/)?.[1];
    const works = raw.match(/works:\s*'((?:[^'\\]|\\.)*)'/)?.[1];

    if (!support) {
      problems.push(`${id}: no support status (would render as unlabelled)`);
      continue;
    }
    if (!works || works.trim().length < 12) {
      problems.push(`${id}: no plain-English "works" line saying what really happens`);
    }

    if (support === 'runs-here') {
      runsHere += 1;
      if (!effectKey) {
        problems.push(`${id}: marked runs-here but has no effectKey — nothing would happen`);
      } else if (!enforced.has(effectKey)) {
        problems.push(
          `${id}: claims to run here via "${effectKey}", but that effect is not in ENFORCED_EFFECT_KEYS`
        );
      }
    } else {
      listed += 1;
      if (!/storeUrl:/.test(raw)) {
        problems.push(`${id}: not runnable here, so it needs an official storeUrl to be useful`);
      }
      for (const url of raw.matchAll(/storeUrl:\s*'([^']+)'/g)) {
        if (!/^https:\/\//.test(url[1])) problems.push(`${id}: insecure store url ${url[1]}`);
      }
    }
  }

  /*
   * Keys alone prove nothing. Walk the source for a consumer of each effect so
   * a "WORKING" badge cannot survive after the code behind it is deleted.
   */
  const srcFiles = [];
  const walk = (dir) => {
    for (const name of fs.readdirSync(dir)) {
      const full = path.join(dir, name);
      const stat = fs.statSync(full);
      if (stat.isDirectory()) walk(full);
      else if (/\.(ts|tsx)$/.test(name)) srcFiles.push(full);
    }
  };
  walk(path.join(ROOT, 'src'));

  const consumersOf = (key) => {
    const consumers = [];
    for (const file of srcFiles) {
      const rel = path.relative(ROOT, file);
      if (rel.includes('extensionCatalogue') || rel.includes('extensionEffects')) continue;
      if (rel.includes('types/browser')) continue;
      if (new RegExp(`'${key}'`).test(fs.readFileSync(file, 'utf8'))) consumers.push(rel);
    }
    return consumers;
  };

  for (const raw of entries) {
    const support = raw.match(/support:\s*'([a-z-]+)'/)?.[1];
    const effectKey = raw.match(/effectKey:\s*'([a-z-]+)'/)?.[1];
    if (support !== 'runs-here' || !effectKey) continue;
    // bg-play is read through its accessor, not the raw key.
    const found =
      consumersOf(effectKey).length > 0 ||
      (effectKey === 'bg-play' && /isBackgroundPlayEnabled/.test(fs.readFileSync(path.join(ROOT, 'src/App.tsx'), 'utf8')));
    if (!found) {
      problems.push(
        `${raw.match(/id:\s*'([^']+)'/)?.[1]}: claims WORKING, but no source file consumes "${effectKey}"`
      );
    }
  }

  /*
   * A "WORKING" entry whose effect needs author content must SHIP that content.
   * Installing Stylus with no stylesheet produced an extension that provably did
   * nothing — exactly the "I can't add any of them / it doesn't work" report.
   */
  for (const raw of entries) {
    const id = raw.match(/id:\s*'([^']+)'/)?.[1] ?? '(unnamed)';
    const support = raw.match(/support:\s*'([a-z-]+)'/)?.[1];
    const effectKey = raw.match(/effectKey:\s*'([a-z-]+)'/)?.[1];
    if (support !== 'runs-here') continue;
    if (effectKey === 'custom-css' && !/customCss:\s*`[^`]{20,}`/.test(raw)) {
      problems.push(`${id}: runs-here custom-css with no shipped stylesheet — installing it does nothing`);
    }
    if (effectKey === 'custom-script' && !/customJs:\s*`[^`]{20,}`/.test(raw)) {
      problems.push(`${id}: runs-here custom-script with no shipped script — installing it does nothing`);
    }
  }

  if (runsHere === 0) problems.push('no extension actually runs — the "working" group would be empty');
  if (listed === 0) problems.push('catalogue has no listings, so the honest "not working" group is missing');

  /* The status must be rendered, not only stored. */
  for (const marker of ['SUPPORT_BADGE', 'CATALOGUE_GROUPS', 'item.works', 'Not working here']) {
    if (!modal.includes(marker)) {
      problems.push(`ExtensionsModal.tsx: does not render "${marker}" — the status would be invisible`);
    }
  }

  /* The old flat list of universal installs must not return. */
  if (/DISCOVER_WORLD_EXTENSIONS/.test(modal)) {
    problems.push(
      'ExtensionsModal.tsx: still uses the flat DISCOVER_WORLD_EXTENSIONS list, which has no per-entry status'
    );
  }

  /*
   * Imports must not create inert entries: a userscript has to carry real code
   * (or the custom-script effect is a no-op) and a .crx/.xpi must not be added.
   * Comments are stripped first — a comment about the old bug is not the bug.
   */
  const unpacker = fs
    .readFileSync(path.join(ROOT, 'src/utils/extensionUnpacker.ts'), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
  if (/icon:[^\n]*[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u.test(unpacker)) {
    problems.push('extensionUnpacker.ts: emoji icon in an imported extension (the UI is vector-only)');
  }
  if (/effectKey:\s*undefined/.test(unpacker)) {
    problems.push('extensionUnpacker.ts: an import path sets effectKey undefined — the entry would do nothing');
  }
  if (!/customJs:\s*`/.test(unpacker)) {
    problems.push('extensionUnpacker.ts: imported userscripts carry no code, so they cannot run');
  }
  if (!/cannot run its code|cannot execute/.test(modal)) {
    problems.push('ExtensionsModal.tsx: importing a .crx/.xpi does not tell the user it cannot run');
  }

  /* No silent over-claim in the store copy. */
  if (!/no extension engine|cannot execute|no extension API/i.test(modal)) {
    problems.push('ExtensionsModal.tsx: lost the explanation of why browser add-ons cannot run here');
  }

  process.stderr.write(
    `· ${runsHere} working, ${listed} listed-only, ${enforced.size} enforced effects\n`
  );
}

if (problems.length) {
  console.error(`❌ ${problems.length} extension-store honesty violation(s):\n`);
  for (const p of problems) console.error(`  ${p}`);
  process.exit(1);
}

console.log('extension store is honest: every entry states whether it really works');
