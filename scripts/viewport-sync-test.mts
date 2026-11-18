/**
 * Proof that the viewport sync is coalesced without dropping real changes.
 *
 * This is the measurable half of "butter smooth". The native viewport call is a
 * JNI crossing that re-lays-out the WebView hosting the page; the shell used to
 * fire it on every animation frame, every 900 ms for the whole session, and once
 * per React render. The gate must let through every distinct state exactly once
 * and reject exact repeats — if it ever rejects a genuine change the native
 * WebView ends up the wrong size and steals taps, which is a regression, so
 * each of those cases is asserted individually below.
 */
import { makeViewportGate, viewportPayload, type ViewportSyncInputs } from '../src/utils/viewportSync';

let passed = 0;
const failures: string[] = [];

function check(name: string, condition: boolean, detail = '') {
  if (condition) {
    passed += 1;
    console.log(`  ✓ ${name}`);
  } else {
    failures.push(`${name}${detail ? ` — ${detail}` : ''}`);
    console.log(`  ✗ ${name}${detail ? ` — ${detail}` : ''}`);
  }
}

/** A realistic "idle browser on a page" payload. */
const base: ViewportSyncInputs = {
  url: 'https://example.com/article',
  topCss: 60,
  bottomCss: 64,
  height: 904,
  isDesktop: false,
  adblockOn: true,
  darkReaderOn: false,
  cookieKillerOn: true,
  customCss: '',
  zoom: 1,
  findQuery: '',
  showNativeWeb: true,
  blockTrackers: true,
  antiFingerprinting: true,
  httpsEverywhere: true,
  backgroundPlay: false,
  hasCustomCss: false,
};

console.log('viewport sync gate');

// 1. First call always goes through: the native side has no state yet.
{
  const gate = makeViewportGate();
  check('first sync is always allowed', gate.shouldSync(base) === true);
}

// 2. The actual bug: repeated identical calls must not cross the JNI boundary.
{
  const gate = makeViewportGate();
  gate.shouldSync(base);
  const repeats = [1, 2, 3, 4, 5].map(() => gate.shouldSync({ ...base }));
  check(
    'five identical repeats are rejected (was one JNI call per frame)',
    repeats.every((allowed) => allowed === false),
    `got ${JSON.stringify(repeats)}`
  );
}

// 3. A simulated stream must not trigger a sync per token.
{
  const gate = makeViewportGate();
  gate.shouldSync(base);
  let crossings = 0;
  for (let i = 0; i < 120; i += 1) {
    // 120 renders, nothing about the viewport changed.
    if (gate.shouldSync({ ...base })) crossings += 1;
  }
  check(
    '120 re-renders cause 0 extra syncs',
    crossings === 0,
    `${crossings} syncs`
  );
}

// 4. Every field that reaches the native call must break the dedupe alone.
const perturbations: Array<[string, Partial<ViewportSyncInputs>]> = [
  ['url', { url: 'https://example.com/next' }],
  ['topCss', { topCss: 72 }],
  ['bottomCss', { bottomCss: 0 }],
  ['height', { height: 812 }],
  ['isDesktop', { isDesktop: true }],
  ['adblockOn', { adblockOn: false }],
  ['darkReaderOn', { darkReaderOn: true }],
  ['cookieKillerOn', { cookieKillerOn: false }],
  ['customCss', { customCss: 'a{color:red}' }],
  ['zoom', { zoom: 1.25 }],
  ['findQuery', { findQuery: 'privacy' }],
  ['showNativeWeb', { showNativeWeb: false }],
  ['blockTrackers', { blockTrackers: false }],
  ['antiFingerprinting', { antiFingerprinting: false }],
  ['httpsEverywhere', { httpsEverywhere: false }],
  ['backgroundPlay', { backgroundPlay: true }],
  ['hasCustomCss', { hasCustomCss: true }],
];

{
  const missed: string[] = [];
  for (const [field, patch] of perturbations) {
    const gate = makeViewportGate();
    gate.shouldSync({ ...base });
    if (gate.shouldSync({ ...base, ...patch }) !== true) missed.push(field);
  }
  check(
    `all ${perturbations.length} native arguments force a sync when changed`,
    missed.length === 0,
    missed.length ? `swallowed: ${missed.join(', ')}` : ''
  );
}

// 5. Returning to a previous value is a real change, not a repeat of the last.
{
  const gate = makeViewportGate();
  gate.shouldSync({ ...base });
  gate.shouldSync({ ...base, zoom: 2 });
  check('changing a value back is forwarded again', gate.shouldSync({ ...base }) === true);
}

// 6. Separator safety: naive concatenation would collide here.
{
  const a = viewportPayload({ ...base, url: 'ab', findQuery: 'c' });
  const b = viewportPayload({ ...base, url: 'a', findQuery: 'bc' });
  check('adjacent fields cannot collide (separator present)', a !== b);
}

// 7. A reset forces the next call through, for a reload or shell restart.
{
  const gate = makeViewportGate();
  gate.shouldSync(base);
  gate.reset();
  check('reset forces the next sync', gate.shouldSync({ ...base }) === true);
}

// 8. After a rejected call the gate still tracks the last *sent* payload.
{
  const gate = makeViewportGate();
  gate.shouldSync({ ...base });
  gate.shouldSync({ ...base, zoom: 1.5 });
  check('a change is sent at most once', gate.shouldSync({ ...base, zoom: 1.5 }) === false);
}

console.log('');
if (failures.length) {
  console.error(`viewport sync: ${failures.length} failure(s)`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(`viewport sync: ${passed} checks pass — the native layout call fires once per real change`);
