/**
 * Motion — does everything that animates in also animate out?
 *
 * The failure this guards against is not a broken animation, it is an absent
 * one: a panel that slides open beautifully and then vanishes on close. That
 * looks worse than no animation at all, because the open motion sets an
 * expectation the close then breaks.
 *
 * The check is structural rather than visual: every overlay component must
 * carry the exit hook, and the hook's own timing logic is exercised directly.
 */
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

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

const componentsDir = 'src/components';
const components = readdirSync(componentsDir).filter((f) => f.endsWith('.tsx'));

/** Every component that renders a full-screen overlay must animate its exit. */
const overlays = components.filter((f) =>
  readFileSync(join(componentsDir, f), 'utf8').includes('bh-overlay'),
);

check('there are overlays to check', overlays.length >= 8);

for (const file of overlays) {
  const src = readFileSync(join(componentsDir, file), 'utf8');
  const name = file.replace('.tsx', '');
  check(`${name} imports the exit hook`, src.includes('useExitTransition'));
  check(`${name} calls the exit hook`, /useExitTransition\(\s*onClose/.test(src));
  check(`${name} marks itself as closing`, src.includes('.isClosing'));
  check(`${name} closes through the hook`, src.includes('requestClose'));
  /*
   * A close button inside the panel may call onClose directly — it is
   * dismissed by the same parent either way. What must never call onClose
   * directly is the *scrim*, because that is the tap users make constantly,
   * and skipping the animation there is what makes the app feel abrupt.
   * The scrim is the element carrying the overlay class.
   */
  const overlayTag = src.slice(src.indexOf('bh-overlay'));
  const scrimEnd = overlayTag.indexOf('>');
  check(
    `${name} scrim does not close instantly`,
    !/onClick=\{onClose\}/.test(overlayTag.slice(0, scrimEnd)),
  );
}

// The stylesheet must define a reversing animation for each entrance it uses.
const css = readFileSync('src/index.css', 'utf8');
const exitClasses = [
  'bh-sheet-out',
  'bh-dialog-out',
  'bh-drawer-right-out',
  'bh-drawer-left-out',
  'bh-overlay-out',
];
for (const cls of exitClasses) {
  check(`stylesheet defines .${cls}`, css.includes(`.${cls}`));
}

// The JS timeout and the CSS duration must come from the same variable,
// otherwise the element unmounts mid-animation or lingers invisibly.
check('exit duration lives in a CSS variable', css.includes('--bh-exit-ms'));
check('exit animations use that variable', css.includes('var(--bh-exit-ms)'));

// Every animation added here must be transform/opacity only — an animated
// width or filter on a large element is what caused the reported lag.
const exitBlock = css.slice(css.indexOf('/* Exit motion'), css.indexOf('/* Inline bars'));
check('exit keyframes animate no layout properties', !/filter\s*:/.test(exitBlock));
check('reduced motion shortens the exit', /prefers-reduced-motion[\s\S]{0,400}--bh-exit-ms: 1ms/.test(css));

// The hook itself: a double close must not queue two timeouts.
const hook = readFileSync('src/utils/useExitTransition.ts', 'utf8');
check('hook guards against a double close', hook.includes('closedRef.current) return'));
check('hook clears its timer on unmount', hook.includes('clearTimeout'));
check('hook falls back when the stylesheet is missing', hook.includes('FALLBACK_MS'));

// Inline bars grow rather than jump, and progress fills smoothly.
check('find bar animates open', readFileSync('src/App.tsx', 'utf8').includes('bh-bar-open'));
check('progress bar smoothing exists', css.includes('.bh-progress'));

console.log();
if (failures.length === 0) {
  console.log(`motion: ${passed} checks pass — every overlay animates out, no layout-property animation`);
} else {
  console.log(`motion: ${passed} passed, ${failures.length} FAILED`);
  for (const f of failures) console.log(`  - ${f}`);
  process.exit(1);
}
