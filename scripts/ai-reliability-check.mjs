#!/usr/bin/env node
/**
 * Guards AI reliability in **both native shells**.
 *
 * Device transcript that started this: "Who is Cristiano Ronaldo" was answered
 * by the live model, then "Who is goat?" — the same class of question, seconds
 * later — fell through to an apology. Root cause: both native clients pinned a
 * single free model (`"model": "openai"`), so one rate-limited model broke every
 * question after it.
 *
 * This check fails the build if a shell goes back to:
 *   - naming a single model instead of a list,
 *   - never iterating that list,
 *   - having no overall deadline (the drawer would hang),
 *   - or carrying unbalanced braces (there is no kotlinc in this environment).
 *
 * Run: node scripts/ai-reliability-check.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');

const shells = {
  'android/standalone/src/com/browserhub/app/ai/NativeAiClient.java': 'gradle-free APK shell',
  'android/app/src/main/java/com/browserhub/app/ai/NativeAiClient.kt': 'Play Store shell',
};

/** Remove string literals and line comments so braces can be counted safely. */
function stripLiterals(line) {
  let out = '';
  let i = 0;
  while (i < line.length) {
    const c = line[i];
    if (c === '/' && line[i + 1] === '/') break;
    if (c === '"' || c === "'") {
      const quote = c;
      i += 1;
      while (i < line.length) {
        if (line[i] === '\\') {
          i += 2;
          continue;
        }
        if (line[i] === quote) {
          i += 1;
          break;
        }
        i += 1;
      }
      out += '""';
      continue;
    }
    out += c;
    i += 1;
  }
  return out;
}

/** Engine-side rotation, which is the part the user actually feels. */
const jsEngine = fs.readFileSync(path.join(ROOT, 'src/utils/localAiEngine.ts'), 'utf8');
const problems = [];

if (!/LIVE_MODELS\s*=\s*\[/.test(jsEngine)) {
  problems.push('src/utils/localAiEngine.ts: LIVE_MODELS is no longer a list');
}
if (!/for \(const model of LIVE_MODELS\)/.test(jsEngine)) {
  problems.push('src/utils/localAiEngine.ts: the model list is never iterated');
}
if (!/getCachedAnswer|cacheKey/.test(jsEngine)) {
  problems.push('src/utils/localAiEngine.ts: no answer cache (repeat questions re-hit the network)');
}
if (!/singleFlight/.test(jsEngine)) {
  problems.push('src/utils/localAiEngine.ts: no single-flight guard for duplicate taps');
}

for (const [file, label] of Object.entries(shells)) {
  const abs = path.join(ROOT, file);
  if (!fs.existsSync(abs)) {
    problems.push(`${file}: missing`);
    continue;
  }
  const src = fs.readFileSync(abs, 'utf8');

  if (!/MODELS\s*=/.test(src)) {
    problems.push(`${file}: no model list — single-model pinning is back (${label})`);
  }
  const iterates = /for\s*\(\s*(?:String\s+)?model\s*:\s*MODELS\s*\)/.test(src) ||
    /for\s*\(\s*model\s+in\s+MODELS\s*\)/.test(src);
  if (!iterates) problems.push(`${file}: MODELS is defined but never iterated (${label})`);

  if (!/callOnce/.test(src)) {
    problems.push(`${file}: no per-model attempt function (${label})`);
  }
  if (!/deadline/.test(src)) {
    problems.push(`${file}: no overall deadline — a slow model can hang the drawer (${label})`);
  }

  // A pinned model in the payload would bypass rotation entirely.
  if (/put\(\s*"model"\s*,\s*"openai"\s*\)/.test(src)) {
    problems.push(`${file}: request still pins model "openai" (${label})`);
  }

  let depth = 0;
  let lineNo = 0;
  for (const raw of src.split('\n')) {
    lineNo += 1;
    const code = stripLiterals(raw);
    depth += (code.match(/\{/g) ?? []).length - (code.match(/\}/g) ?? []).length;
    if (depth < 0) {
      problems.push(`${file}:${lineNo}: closing brace with no opener (${label})`);
      break;
    }
  }
  if (depth !== 0) {
    problems.push(`${file}: unbalanced braces (depth ${depth}) (${label})`);
  }
}

if (problems.length) {
  console.error('❌ AI reliability regression:\n');
  for (const p of problems) console.error(`  ${p}`);
  process.exit(1);
}

console.log('AI rotates models with a bounded deadline in the engine and both native shells');
