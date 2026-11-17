#!/usr/bin/env node
/**
 * Enforces the two visual contracts the user asked for on device:
 *
 *   1. Pure AMOLED black — the app canvas is #000000 and no panel, bubble or
 *      gradient carries a navy/slate tint. Slate-900 (#0f172a) and friends are
 *      exactly the "dark blue shell" that was rejected.
 *   2. Motion — tab switches and every overlay (sheet, dialog, drawer, popover)
 *      animate, and the animations are disabled for prefers-reduced-motion.
 *
 * Run: node scripts/amoled-motion-check.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const SRC = path.join(ROOT, 'src');
const problems = [];
const notes = [];

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (/\.(tsx?|css)$/.test(e.name)) out.push(full);
  }
  return out;
}

const files = walk(SRC);
const source = new Map(files.map((f) => [f, fs.readFileSync(f, 'utf8')]));

/* ---------------------------------------------------------------- AMOLED --- */

const css = source.get(path.join(SRC, 'index.css')) ?? '';

// The root canvas must be literally #000000.
for (const selector of ['html,\nbody,\n#root', 'html, body, #root']) {
  if (css.includes(selector)) {
    const block = css.slice(css.indexOf(selector), css.indexOf('}', css.indexOf(selector)));
    if (!/background-color:\s*#000000/i.test(block) && !/background:\s*#000000/i.test(block)) {
      problems.push({
        file: 'src/index.css',
        detail: 'root canvas is not background-color: #000000',
      });
    }
    break;
  }
}

// Tailwind palette surfaces that are visibly blue-grey rather than black.
const bannedUtilities = /\b(?:bg|from|via|to)-(?:slate|gray|zinc|neutral|stone)-(?:700|800|900|950)\b/;
// A *solid* indigo/purple/navy fill at 950 reads as a dark-blue band on an
// AMOLED screen. Translucent accent chips (e.g. /20 tints on a black panel) are
// deliberate accents and stay allowed.
const bannedNavyFill = /\b(?:bg|from|via|to)-(?:indigo|blue|navy)-(?:800|900|950)\b(?!\/[123]\d)/;
// Hex colours that are dark *but blue-tinted* (navy family) rather than neutral.
const bannedHex = /#(?:0[0-9a-f])?(?:0[1-9a-f]|1[0-9a-f])(?:1[0-9a-f]|2[0-9a-f])(?:[2-9a-f][0-9a-f])/i;

const allowedHex = new Set([
  '#000000', // canvas
  '#040405', '#050506', '#060608', '#08080b', '#0b0b0e', '#121216', // neutral surfaces
]);

for (const [file, text] of source) {
  const rel = path.relative(ROOT, file);
  if (rel.endsWith('index.css')) continue;

  text.split('\n').forEach((line, i) => {
    const trimmed = line.trim();
    // Ignore comments and the deliberate accent tints listed below.
    if (trimmed.startsWith('*') || trimmed.startsWith('//') || trimmed.startsWith('/*')) return;

    if (bannedNavyFill.test(line)) {
      problems.push({
        file: rel,
        line: i + 1,
        detail: `solid navy/indigo fill: ${line.match(bannedNavyFill)[0]}`,
      });
    }

    if (bannedUtilities.test(line)) {
      problems.push({
        file: rel,
        line: i + 1,
        detail: `Tailwind slate/gray surface: ${line.match(bannedUtilities)[0]}`,
      });
    }

    const hexes = line.match(/#[0-9a-f]{6}\b/gi) ?? [];
    for (const hex of hexes) {
      const lower = hex.toLowerCase();
      if (allowedHex.has(lower)) continue;
      // Skip anything that is not being used as a background/surface fill.
      const idx = line.indexOf(hex);
      const context = line.slice(Math.max(0, idx - 42), idx);
      if (!/background|bg-|fill|borderColor|from-|to-/.test(context)) continue;
      if (bannedHex.test(lower)) {
        problems.push({
          file: rel,
          line: i + 1,
          detail: `blue-tinted surface ${hex} (should be neutral black)`,
        });
      }
    }
  });
}

notes.push(`scanned ${files.length} source files for tinted surfaces`);

/* ---------------------------------------------------------------- MOTION --- */

const requiredKeyframes = [
  'bhSheetUp',
  'bhDialogIn',
  'bhDrawerRight',
  'bhDrawerLeft',
  'bhFadeIn',
  'bhViewportIn',
  'bhPopDown',
];
for (const name of requiredKeyframes) {
  if (!css.includes(`@keyframes ${name}`)) {
    problems.push({ file: 'src/index.css', detail: `missing @keyframes ${name}` });
  }
}

const requiredClasses = [
  '.bh-overlay',
  '.bh-sheet',
  '.bh-dialog',
  '.bh-drawer-right',
  '.bh-drawer-left',
  '.bh-viewport-enter',
  '.bh-pop',
];
for (const cls of requiredClasses) {
  // The class must be *defined* with an animation property.
  const idx = css.indexOf(`${cls} {`);
  const altIdx = css.indexOf(`${cls.replace('.', '.')} ,`);
  const at = idx >= 0 ? idx : altIdx;
  if (at < 0) {
    problems.push({ file: 'src/index.css', detail: `missing motion class ${cls}` });
    continue;
  }
  const block = css.slice(at, css.indexOf('}', at));
  if (!/animation:/.test(block)) {
    problems.push({ file: 'src/index.css', detail: `${cls} has no animation` });
  }
}

if (!/prefers-reduced-motion/.test(css)) {
  problems.push({
    file: 'src/index.css',
    detail: 'no prefers-reduced-motion opt-out (animations must be skippable)',
  });
}

// Every overlay surface must carry a motion class, or it pops in with no easing.
const overlayFiles = [
  'components/CustomizeDrawer.tsx',
  'components/AiCopilotDrawer.tsx',
  'components/ExtensionsModal.tsx',
  'components/PlayStoreDownloadModal.tsx',
  'components/ShieldsPanel.tsx',
  'components/TabsDrawer.tsx',
  'components/VpnModal.tsx',
  'components/MenuHubModal.tsx',
  'components/TranslateModal.tsx',
];

// DevTools is a docked panel rather than a full-screen overlay, so it only needs
// an entry animation (checked separately below).

for (const rel of overlayFiles) {
  const file = path.join(SRC, rel);
  if (!source.has(file)) {
    problems.push({ file: `src/${rel}`, detail: 'overlay file missing' });
    continue;
  }
  const text = source.get(file);
  if (!/bh-(overlay|dialog|sheet|drawer-right|drawer-left)/.test(text)) {
    problems.push({
      file: `src/${rel}`,
      detail: 'overlay has no motion class (it will appear instantly)',
    });
  }
  if (!/fixed inset-0/.test(text)) {
    problems.push({ file: `src/${rel}`, detail: 'overlay no longer renders a full-screen scrim' });
  }
}

// The docked DevTools panel must slide in rather than appear instantly.
const devtools = source.get(path.join(SRC, 'components/DevToolsPanel.tsx')) ?? '';
if (!/bh-sheet|bh-pop|bh-viewport-enter/.test(devtools)) {
  problems.push({
    file: 'src/components/DevToolsPanel.tsx',
    detail: 'docked panel has no entry animation',
  });
}

// The viewport animation must not leave a transform behind.
{
  const idx = css.indexOf('.bh-viewport-enter {');
  if (idx >= 0) {
    const block = css.slice(idx, css.indexOf('}', idx));
    if (/\bboth\b/.test(block)) {
      problems.push({
        file: 'src/index.css',
        detail:
          '.bh-viewport-enter uses fill-mode both, leaving a transform that breaks position: fixed children',
      });
    }
  }
}

// The tab viewport must be keyed so switching tabs replays the entry animation.
const app = source.get(path.join(SRC, 'App.tsx')) ?? '';
if (!/className="bh-viewport-enter/.test(app)) {
  problems.push({ file: 'src/App.tsx', detail: 'tab viewport is missing bh-viewport-enter' });
}
if (!/key=\{activeTab\.id\}/.test(app)) {
  problems.push({
    file: 'src/App.tsx',
    detail: 'tab viewport must be keyed by activeTab.id or animations never replay',
  });
}

if (problems.length) {
  console.error(`❌ ${problems.length} visual contract violation(s):\n`);
  for (const p of problems) {
    console.error(`  ${p.file}${p.line ? `:${p.line}` : ''}  ${p.detail}`);
  }
  process.exit(1);
}

for (const n of notes) console.log(`· ${n}`);
console.log('pure AMOLED black + motion contract holds');
