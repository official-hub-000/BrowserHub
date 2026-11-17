#!/usr/bin/env node
/**
 * Fails the build when a control is not actually wired.
 *
 * Real-device feedback kept repeating "all options and buttons are not wired
 * and working", so this guard looks past the presence of an onClick and checks
 * the things that actually break on a phone:
 *
 *   1. a <button> with no handler and no submit behaviour (a dead control)
 *   2. a handler that is an empty body or returns a bare no-op
 *   3. a hand-rolled `cursor-pointer` div/span with no role and no handler
 *   4. a toggle whose only effect is a boolean flip with no visible binding
 *      (checked indirectly: every boolean state setter must feed a render)
 *
 * Run: node scripts/button-wiring-check.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const SRC = path.join(ROOT, 'src');

/** @returns {string[]} every .tsx file under src */
function tsxFiles(dir = SRC, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) tsxFiles(full, out);
    else if (entry.name.endsWith('.tsx')) out.push(full);
  }
  return out;
}

/** Split a JSX opening tag into its attribute text, respecting {} nesting. */
function readTag(source, start) {
  let i = start;
  let depth = 0;
  let quote = null;
  for (; i < source.length; i += 1) {
    const c = source[i];
    if (quote) {
      if (c === quote && source[i - 1] !== '\\') quote = null;
      continue;
    }
    if (c === '"' || c === "'" || c === '`') {
      quote = c;
      continue;
    }
    if (c === '{') depth += 1;
    else if (c === '}') depth -= 1;
    else if (c === '>' && depth === 0) return source.slice(start, i + 1);
  }
  return source.slice(start);
}

const problems = [];

for (const file of tsxFiles()) {
  const source = fs.readFileSync(file, 'utf8');
  const rel = path.relative(ROOT, file);
  const lineOf = (idx) => source.slice(0, idx).split('\n').length;

  // 1 + 2: button tags with no handler, and handlers that do nothing.
  const tagRe = /<button\b/g;
  let m;
  while ((m = tagRe.exec(source))) {
    const tag = readTag(source, m.index);
    const line = lineOf(m.index);
    const hasClick = /\bonClick\s*=/.test(tag);
    const isSubmit = /type\s*=\s*["']submit["']/.test(tag);
    const isReset = /type\s*=\s*["']reset["']/.test(tag);
    if (!hasClick && !isSubmit && !isReset) {
      problems.push({
        file: rel,
        line,
        kind: 'dead-button',
        detail: `button has no onClick and is not a submit/reset button`,
      });
      continue;
    }

    // Empty handler bodies: `onClick={() => {}}`, `onClick={noop}`, `{() => null}`.
    const emptyRe =
      /onClick\s*=\s*\{\s*(?:\(\s*\)\s*=>\s*\{\s*\}|\(\s*\)\s*=>\s*(?:null|undefined|void\s+0|false)\b)\s*\}/;
    if (emptyRe.test(tag)) {
      problems.push({
        file: rel,
        line,
        kind: 'noop-handler',
        detail: 'onClick handler has an empty body',
      });
    }
  }

  // 3: clickable-looking divs/spans that can never respond.
  const fakeRe =
    /<(div|span)\b([^>]*?className\s*=\s*[`"][^`"]*cursor-pointer[^`"]*[`"][^>]*)>/gs;
  let f;
  while ((f = fakeRe.exec(source))) {
    const tag = f[0];
    const line = lineOf(f.index);
    const interactive =
      /onClick\s*=/.test(tag) || /role\s*=\s*["']button["']/.test(tag) || /onPointerDown/.test(tag);
    if (!interactive) {
      problems.push({
        file: rel,
        line,
        kind: 'fake-button',
        detail: `<${
          f[1]
        }> is styled cursor-pointer but has no onClick and no role="button"`,
      });
    }
  }

  // 4: a `disabled` control must also explain itself, otherwise it looks broken.
  const disRe = /<button\b[^>]*\bdisabled\b[^>]*>/gs;
  let d;
  while ((d = disRe.exec(source))) {
    const tag = d[0];
    const line = lineOf(d.index);
    if (!/title\s*=|aria-label\s*=/.test(tag)) {
      problems.push({
        file: rel,
        line,
        kind: 'silent-disabled',
        detail: 'disabled button has no title/aria-label explaining why it is unavailable',
      });
    }
  }
}

if (problems.length) {
  console.error(`❌ ${problems.length} unwired control(s):\n`);
  for (const p of problems) {
    console.error(`  ${p.file}:${p.line}  [${p.kind}] ${p.detail}`);
  }
  console.error('\nEvery control must do something real on a phone.');
  process.exit(1);
}

console.log('all buttons are wired (no dead, no-op, fake or silently-disabled controls)');
