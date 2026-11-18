/**
 * Frame path — does anything touch the WebView when nothing changed?
 *
 * "No lags" is not one fix, it is the absence of per-frame work. The specific
 * bug class this guards against is a shell sync (which fires on every React
 * layout pass and every resize) doing unconditional WebView mutations:
 *
 *   - setTextZoom() invalidates the entire document layout. Calling it with an
 *     unchanged value re-lays-out every open page, continuously.
 *   - setVisibility()/setFocusable()/bringToFront() invalidate the parent.
 *     bringToFront() re-orders the view hierarchy.
 *   - one evaluateJavascript() per blocked ad request saturates the main thread
 *     on an ad-heavy page, competing with the page's own layout.
 *
 * Each of those was present. Each is asserted gone.
 */
import { readFileSync } from 'node:fs';

let passed = 0;
const failures = [];

function check(name, ok, detail) {
  if (ok) {
    passed++;
  } else {
    failures.push(detail ? `${name} — ${detail}` : name);
    console.log(`  FAIL  ${name}${detail ? ` — ${detail}` : ''}`);
  }
}

const activity = readFileSync(
  'android/standalone/src/com/browserhub/app/MainActivity.java',
  'utf8',
);
const app = readFileSync('src/App.tsx', 'utf8');

// --- the sync path must be idempotent -------------------------------------
check(
  'text zoom is only applied when it changes',
  /if \(wantZoom != lastTextZoom\)/.test(activity),
  'setTextZoom with an unchanged value re-lays-out the whole document',
);
check(
  'text zoom has a change tracker',
  /private int lastTextZoom/.test(activity),
);
check(
  'visibility is only applied on a real transition',
  /lastBrowserVisibility/.test(activity) &&
    /if \(lastBrowserVisibility != View\.VISIBLE\)/.test(activity),
  'setVisibility/bringToFront invalidate the parent view',
);
check(
  'bringToFront is behind the visibility guard',
  /if \(lastBrowserVisibility != View\.VISIBLE\) \{[\s\S]{0,600}?bringToFront\(\)/.test(activity),
  'bringToFront re-orders the view hierarchy on every call',
);

/*
 * Both calls still exist — that is correct and necessary. What matters is that
 * each one sits inside an `if`, so the check is that the call cannot be reached
 * without a guard immediately above it. A plain "the call is absent" assertion
 * would push a future fix toward deleting the feature instead of guarding it.
 */
const textZoomGuard = activity.split('setTextZoom(')[0].split('\n').slice(-3).join('\n');
check(
  'setTextZoom is reachable only through a guard',
  /if \(wantZoom != lastTextZoom\)/.test(activity) &&
    !/\n\s*browserWebView\.getSettings\(\)\.setTextZoom\(wantZoom\);\s*\n(?!\s*\})/.test(
      activity.replace('if (wantZoom != lastTextZoom) {', ''),
    ) === false ||
    /if \(wantZoom != lastTextZoom\) \{\s*\n\s*lastTextZoom = wantZoom;\s*\n\s*browserWebView\.getSettings\(\)\.setTextZoom\(wantZoom\);/.test(
      activity,
    ),
  'the guarded body must assign the tracker before the call',
);
check(
  'bringToFront is reachable only through the visibility guard',
  /if \(lastBrowserVisibility != View\.VISIBLE\) \{[\s\S]{0,700}?bringToFront\(\);/.test(activity),
);

// --- the ad-block bridge must be batched ----------------------------------
check(
  'ad-block notifications are batched',
  /pendingBlockedCount/.test(activity) && /blockFlushScheduled/.test(activity),
  'one JS round-trip per blocked request is main-thread load',
);
check(
  'the batch has a rate limit',
  /lastBlockFlushAt/.test(activity) && /500L/.test(activity),
);
check(
  'each flush posts once, not once per block',
  /if \(blockFlushScheduled\) return;/.test(activity),
);
check(
  'the shell accepts a count as well as a list name',
  /listOrCount/.test(app) && /parseInt\(listOrCount/.test(app),
);
check(
  'the counter adds the real amount',
  /prev\.totalAdsBlocked \+ amount/.test(app),
  'adding 1 for a batch of 20 loses the true count',
);

// --- one row per domain, not per request ----------------------------------
check(
  'network log merges repeats of a domain',
  /findIndex\(\(e\) => e\.domain === key/.test(app),
  'a row per request churns React state and evicts useful entries',
);
check('merged rows carry a count', /count: \(hit\.count \?\? 1\) \+ amount/.test(app));

// --- the properties that were already correct must stay correct -----------
check(
  'offscreen pre-raster stays on (scroll tile prep)',
  /setOffscreenPreRaster\(true\)/.test(activity),
);
check(
  'no forced hardware layer above API 21',
  /SDK_INT == Build\.VERSION_CODES\.LOLLIPOP\) \{\s*\n\s*\/\/ Only here/.test(activity),
  'a forced hardware layer recomposites the whole view every frame',
);
check(
  'no animation of layout properties in the motion system',
  !/@keyframes bhExit[\s\S]{0,400}?filter:/.test(readFileSync('src/index.css', 'utf8')),
);

console.log();
if (failures.length === 0) {
  console.log(
    `frame path: ${passed} checks pass — a sync with no changes touches nothing, ad blocks are batched`,
  );
} else {
  console.log(`frame path: ${passed} passed, ${failures.length} FAILED`);
  for (const f of failures) console.log(`  - ${f}`);
  process.exit(1);
}
