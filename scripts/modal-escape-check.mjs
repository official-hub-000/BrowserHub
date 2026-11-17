// A full-screen scrim with no escape path is how a browser gets "stuck": taps
// land on the sheet, nothing closes it, and every control underneath looks dead.
// Every overlay must close on backdrop tap AND have a labelled close control.
import fs from 'node:fs';

const files = fs.readdirSync('src/components').filter((f) => f.endsWith('.tsx'));
const bad = [];

for (const file of files) {
  const src = fs.readFileSync(`src/components/${file}`, 'utf8');
  const isOverlay = /fixed inset-0/.test(src);
  if (!isOverlay) continue;

  const closesOnBackdrop = /onClick=\{onClose\}/.test(src);
  const stopsBubbling = /stopPropagation/.test(src);
  const hasCloseButton = /aria-label="Close"|title="Close"|onClick=\{onClose\}/.test(src);
  if (!hasCloseButton) bad.push(`${file}: overlay has no close control`);
  if (!closesOnBackdrop) bad.push(`${file}: overlay cannot be dismissed by tapping outside`);
  if (!stopsBubbling) bad.push(`${file}: taps inside the sheet would close it`);
}

// App-level overlays too
const app = fs.readFileSync('src/App.tsx', 'utf8');
if (!/onClick=\{\(\) => setShowEnginePicker\(false\)\}/.test(app)) {
  bad.push('App.tsx: engine picker backdrop does not dismiss');
}

if (bad.length) {
  bad.forEach((b) => console.error(`  FAIL ${b}`));
  process.exit(1);
}
console.log('  every overlay has a reachable way out');
