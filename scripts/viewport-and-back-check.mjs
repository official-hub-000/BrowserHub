#!/usr/bin/env node
/**
 * Guards the two things that made the app feel broken on a real phone:
 *
 *   1. "the app is not fitting in my mobile screen"
 *      - the shell used a static `height: 100%`, which does not follow the
 *        visible viewport when Android chrome collapses or the keyboard opens,
 *        so the bottom bar ended up off-screen;
 *      - the native window painted under the status/navigation bars.
 *
 *   2. "the back button is not working"
 *      - the JS handler was only registered if the native bridge was visible at
 *        mount, so if it was not ready the hardware Back button found nothing
 *        and the platform exited the app instead of navigating.
 *
 * Run: node scripts/viewport-and-back-check.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const problems = [];

/* ------------------------------ screen fit -------------------------------- */

const css = fs.readFileSync(path.join(ROOT, 'src/index.css'), 'utf8');
const rootBlock = css.slice(css.indexOf('html,'), css.indexOf('}', css.indexOf('html,')));

if (!/height:\s*100dvh/.test(rootBlock)) {
  problems.push('src/index.css: root uses a static height — use 100dvh so the shell fits the screen');
}
if (!/max-height:\s*100dvh/.test(rootBlock)) {
  problems.push('src/index.css: root has no max-height: 100dvh (content can push the bar off-screen)');
}
if (!/overflow:\s*hidden/.test(rootBlock)) {
  problems.push('src/index.css: root does not clip overflow');
}

const activity = fs.readFileSync(
  path.join(ROOT, 'android/standalone/src/com/browserhub/app/MainActivity.java'),
  'utf8'
);

if (!/setDecorFitsSystemWindows\(true\)/.test(activity)) {
  problems.push('MainActivity.java: does not reserve system bar space (content draws under the status bar)');
}
if (!/setOnApplyWindowInsetsListener/.test(activity)) {
  problems.push('MainActivity.java: no window insets handling (notch/system bars can cover the UI)');
}
if (/#05070D/i.test(activity)) {
  problems.push('MainActivity.java: still paints the old navy #05070D instead of #000000');
}

const themes = fs.readFileSync(path.join(ROOT, 'android/standalone/res/values/themes.xml'), 'utf8');
if (/#05070D/i.test(themes)) {
  problems.push('themes.xml: still paints the old navy #05070D');
}

/* -------------------------------- back ----------------------------------- */

const app = fs.readFileSync(path.join(ROOT, 'src/App.tsx'), 'utf8');

// The handler must be registered unconditionally. Guarding the whole effect on
// the bridge is what left Back unregistered.
const backIdx = app.indexOf('window.__onAndroidBackPressed =');
if (backIdx < 0) {
  problems.push('App.tsx: no hardware-back handler');
} else {
  // Look at the effect that owns the handler and make sure it does not bail out.
  const effectStart = app.lastIndexOf('useEffect(', backIdx);
  const between = app.slice(effectStart, backIdx);
  if (/hasJniBridge\(\)[^;]*return;/.test(between)) {
    problems.push(
      'App.tsx: the back handler is still registered behind a bridge check — it will be missing when the bridge is late'
    );
  }
}

if (!/moveTaskToBack|onBackPressed/.test(activity)) {
  problems.push('MainActivity.java: no native back handling');
}
if (!/browserWebView\.canGoBack\(\)/.test(activity)) {
  problems.push('MainActivity.java: back does not consult real page history');
}

/* ------------------- internal pages are history entries ------------------ */

// A page reachable only through the menu still has to be exitable with Back.
const viewport = fs.readFileSync(path.join(ROOT, 'src/components/WebViewport.tsx'), 'utf8');
const routes = [...viewport.matchAll(/tab\.url === '(browserhub:\/\/[a-z]+)'/g)].map((m) => m[1]);
if (!routes.includes('browserhub://games')) {
  problems.push('WebViewport.tsx: browserhub://games is not routed, so the Games hub would 404');
}
if (routes.length < 3) {
  problems.push(`only ${routes.length} internal routes found — expected at least start, games, benchmark`);
}
// Every internal route must be a pushed history entry, not a swap.
if (!/historyIndex \+ 1|history\.slice\(0, .*historyIndex/.test(app)) {
  problems.push('App.tsx: navigation does not push history, so Back cannot leave an internal page');
}

/* ------------------------------- reported -------------------------------- */

if (problems.length) {
  console.error(`❌ ${problems.length} device-layout/back violation(s):\n`);
  for (const p of problems) console.error(`  ${p}`);
  process.exit(1);
}

console.log('shell fits the visible screen, system bars reserved, hardware Back always routed');
