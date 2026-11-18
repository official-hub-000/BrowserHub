/**
 * Video capture — does it find real media, and does it refuse to over-promise?
 *
 * Two failure modes matter equally here:
 *   - missing a real video (the app looks broken), and
 *   - offering a download that cannot work (the app lies, which is exactly what
 *     every "video downloader" on the Play Store is accused of).
 *
 * Both are asserted below.
 */
import {
  classifyMediaUrl,
  assessMedia,
  dedupeMedia,
  parseCaptures,
  suggestedFilename,
  looksSigned,
  summarise,
  buildCaptureScript,
  buildCaptureReadbackScript,
  type CapturedMedia,
} from '../src/utils/videoCapture';

let passed = 0;
const failures: string[] = [];
function check(name: string, ok: boolean, detail = '') {
  if (ok) {
    passed += 1;
    console.log(`  ok  ${name}`);
  } else {
    failures.push(`${name}${detail ? ` — ${detail}` : ''}`);
    console.log(`  FAIL ${name}${detail ? ` — ${detail}` : ''}`);
  }
}

const page = (over: Partial<{ usesMse: boolean; drm: boolean }> = {}) => ({
  usesMse: false,
  drm: false,
  crossoriginBlocked: false,
  ...over,
});

console.log('video capture');

// ---------------------------------------------------------------- detection
const detected: Array<[string, string, string]> = [
  ['direct mp4', 'https://cdn.example.com/clip.mp4', 'video/mp4'],
  ['mp4 with query', 'https://cdn.example.com/v/clip.mp4?token=abc', ''],
  ['webm', 'https://cdn.example.com/v/b.webm', ''],
  ['hls manifest', 'https://cdn.example.com/master.m3u8', 'application/vnd.apple.mpegurl'],
  ['dash manifest', 'https://cdn.example.com/stream.mpd', 'application/dash+xml'],
  ['mpegurl mime only', 'https://cdn.example.com/play?id=7', 'application/x-mpegurl'],
  ['audio track', 'https://cdn.example.com/a.m4a', 'audio/mp4'],
  ['fragment', 'https://cdn.example.com/seg/12.ts', 'video/mp2t'],
];
for (const [name, url, mime] of detected) {
  check(`detects ${name}`, classifyMediaUrl(url, mime) !== null);
}

// Page assets must never be offered as video.
const rejected = [
  'https://example.com/style.css',
  'https://example.com/app.js',
  'https://example.com/photo.jpg',
  'https://example.com/font.woff2',
  'https://example.com/page.html',
  'blob:https://example.com/19e1-2',
  'data:video/mp4;base64,AAAA',
  'not a url',
];
for (const url of rejected) {
  check(`ignores ${url.slice(0, 42)}`, classifyMediaUrl(url, '') === null);
}

// A video MIME on a URL with no extension must still be caught.
check(
  'catches extensionless URL by Content-Type',
  classifyMediaUrl('https://cdn.example.com/stream/4821', 'video/mp4') !== null
);

// ------------------------------------------------------------- signed links
check('flags an expiring signature', looksSigned('https://c.example.com/v.mp4?expire=1700000000&sig=abc'));
check('does not flag a plain url', !looksSigned('https://c.example.com/v.mp4'));

// ------------------------------------------------------------ dedupe + sort
{
  const list: CapturedMedia[] = [
    { url: 'https://c/a.mp4', kind: 'file', mime: '', bytes: 100, source: 'network', label: 'A', signed: false },
    { url: 'https://c/a.mp4', kind: 'file', mime: 'video/mp4', bytes: 900, source: 'network', label: 'A', signed: false },
    { url: 'https://c/big.mp4', kind: 'file', mime: '', bytes: 50_000, source: 'network', label: 'B', signed: false },
  ];
  const out = dedupeMedia(list);
  check('dedupes by url', out.length === 2, `${out.length} items`);
  check('keeps the larger known size', out.find((i) => i.url === 'https://c/a.mp4')?.bytes === 900);
  check('largest file sorts first (the real video)', out[0].url === 'https://c/big.mp4');
}

// --------------------------------------------------- honest verdicts, part 1
{
  // Without a merger, a playlist must not be offered as a video file.
  const hls = classifyMediaUrl('https://cdn.example.com/master.m3u8', '')!;
  const a = assessMedia(hls, page(), 'https://cdn.example.com/watch', true);
  check('a playlist is not offered when there is no merger', a.actionable === false);
  check('playlist verdict is needs-merge', a.verdict === 'needs-merge');
  check('playlist reason says saving it is not a video', /not a video file/i.test(a.reason));

  // With the native merger present, the same item becomes a real download.
  const merged = assessMedia(hls, page(), 'https://cdn.example.com/watch', true, {
    canMergeStreams: true,
  });
  check('a playlist IS offered when the merger exists', merged.actionable === true);
  check('mergeable verdict is named', merged.verdict === 'mergeable');
  check('the merge reason explains joining parts', /join/i.test(merged.reason));
  check(
    'the merger is not offered in the web build (it is native only)',
    assessMedia(hls, page(), 'https://cdn.example.com/watch', false, { canMergeStreams: true })
      .actionable === false
  );

  // DASH is still refused: joining it needs a remuxer this build does not have.
  const dash = classifyMediaUrl('https://cdn.example.com/stream.mpd', '')!;
  const dashAssessed = assessMedia(dash, page(), 'https://cdn.example.com/watch', true, {
    canMergeStreams: true,
  });
  check('DASH is refused even with the merger', dashAssessed.actionable === false);
  check('DASH reason names the missing remuxer', /remux/i.test(dashAssessed.reason));
}
{
  const mp4 = classifyMediaUrl('https://cdn.example.com/clip.mp4', '')!;
  const a = assessMedia(mp4, page({ drm: true }), 'https://cdn.example.com/watch', true);
  check('DRM content is refused', a.actionable === false && a.verdict === 'drm-protected');
  check('DRM reason names Widevine and says it cannot be removed', /widevine/i.test(a.reason) && /cannot/i.test(a.reason));
}
{
  // The web build: cross-origin media genuinely cannot be read.
  const mp4 = classifyMediaUrl('https://media.other-host.net/clip.mp4', '')!;
  const a = assessMedia(mp4, page(), 'https://www.site.com/watch', false);
  check('web build refuses cross-origin media instead of failing at tap time', a.actionable === false);
  check('cross-origin verdict is named', a.verdict === 'cross-origin-blocked');
}
{
  // The Android build goes through the platform, so the same URL is fine.
  const mp4 = classifyMediaUrl('https://media.other-host.net/clip.mp4', '')!;
  const a = assessMedia(mp4, page(), 'https://www.site.com/watch', true);
  check('android build offers the same cross-origin video', a.actionable === true);
  check('android verdict is ready', a.verdict === 'ready');
}
{
  const mp4 = classifyMediaUrl('https://www.site.com/v/clip.mp4', '')!;
  const a = assessMedia(mp4, page(), 'https://www.site.com/watch', false);
  check('same-origin video works even in the web build', a.actionable === true);
}

// --------------------------------------------------------------- filenames
{
  const mp4 = classifyMediaUrl('https://cdn.example.com/x.mp4', '')!;
  check(
    'filename is readable and keeps the extension',
    suggestedFilename(mp4, 'Big Buck Bunny (2008)') === 'Big Buck Bunny (2008).mp4'
  );
  check(
    'filename strips characters Android forbids',
    !/[\\/:*?"<>|]/.test(suggestedFilename(mp4, 'a/b:c*d?e"f<g>h|i'))
  );
  const noExt = classifyMediaUrl('https://cdn.example.com/stream/4821', 'video/webm')!;
  check('extension falls back to the mime type', suggestedFilename(noExt, 'clip').endsWith('.webm'));
}

// ------------------------------------------------------------ parseCaptures
{
  // Exactly what evaluateJavascript returns for a JSON.stringify payload.
  const inner = JSON.stringify({
    items: [
      { url: 'https://cdn.example.com/a.mp4', mime: 'video/mp4', bytes: 2048 },
      { url: 'https://cdn.example.com/master.m3u8', mime: '', bytes: 0 },
      { url: 'https://example.com/tracker.gif', mime: 'image/gif', bytes: 12 },
    ],
    mse: true,
    drm: false,
  });
  const quoted = JSON.stringify(inner); // double-encoded, as the WebView does it
  const parsed = parseCaptures(quoted);
  check('parses the doubly-encoded WebView payload', parsed.items.length === 2, `${parsed.items.length} items`);
  check('reports that the page used MediaSource', parsed.page.usesMse === true);
  check('drops the non-media request', !parsed.items.some((i) => i.url.includes('tracker.gif')));
  check('keeps the manifest', parsed.items.some((i) => i.kind === 'hls'));
}

check('a garbage payload yields an empty result, not a crash', parseCaptures('{{{').items.length === 0);
check('an empty payload yields an empty result', parseCaptures('').items.length === 0);
check('null payload yields an empty result', parseCaptures('null').items.length === 0);

// ---------------------------------------------------------------- summaries
{
  const items = [
    classifyMediaUrl('https://c/a.mp4', '', 1000)!,
    classifyMediaUrl('https://c/b.mp4', '', 0)!,
  ];
  const s = summarise(items);
  check('summary counts items', s.count === 2);
  check('summary adds known bytes only', s.bytes === 1000);
}

// ------------------------------------------------- the injected page scripts
{
  const cap = buildCaptureScript();
  check('capture script guards against double injection', cap.includes('__bhCaptureOn'));
  check('capture script hooks XHR', cap.includes('XMLHttpRequest.prototype.open'));
  check('capture script hooks fetch', cap.includes('window.fetch'));
  check('capture script hooks the media src setter', cap.includes("getOwnPropertyDescriptor(HTMLMediaElement.prototype,'src')"));
  check('capture script notices MediaSource (blob streams)', cap.includes('__bhUsesMse'));
  check('capture script never throws into the page', cap.includes('catch(e){}'));

  const read = buildCaptureReadbackScript();
  check('readback reads video elements', read.includes("querySelectorAll('video')"));
  check('readback reads og:video meta tags', read.includes('og:video'));
  check('readback reports DRM state', read.includes('drm'));
  check('readback is valid JavaScript', (() => {
    try {
      // Parsing only — never executed here.
      new Function(`return ${read}`);
      return true;
    } catch {
      return false;
    }
  })());
}

// The engines must be able to syntax-check the injected strings.
check('capture script itself is valid JavaScript', (() => {
  try {
    new Function(buildCaptureScript().replace(/^\(function\(\)\{/, '').replace(/\}\)\(\);$/, ''));
    return true;
  } catch {
    return false;
  }
})());

console.log('');
if (failures.length) {
  console.error(`video capture: ${failures.length} failure(s)`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(`video capture: ${passed} checks pass — real media is found, impossible downloads are refused`);

// ---------------------------------------------------------------------------
// The Java copy is what actually runs on the device. Executing it here in a
// stub page proves the shipped strings hook XHR/fetch/media for real rather
// than merely looking right — the duplication between TS and Java is exactly
// the kind of thing that silently rots.
// ---------------------------------------------------------------------------
{
  const fs = await import('node:fs');
  const javaSrc = fs.readFileSync(
    'android/standalone/src/com/browserhub/app/MediaCaptureScripts.java',
    'utf8'
  );
  // The constant spans many lines and its string literals contain ';', so this
  // walks forward to the semicolon that actually terminates the declaration.
  const extract = (name: string) => {
    const start = javaSrc.indexOf(name + ' =');
    const rest = javaSrc.slice(start);
    const terminator = rest.indexOf('";\n');
    const seg = terminator >= 0 ? rest.slice(0, terminator + 1) : rest;
    const parts = [...seg.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((m) => m[1]);
    return parts.join('').replace(/\\\\/g, '\\');
  };
  const hookJs = extract('HOOK');
  const readJs = extract('READBACK');

  // A stub page just complete enough to run the hooks.
  const captured: string[] = [];
  const listeners: Record<string, () => void> = {};
  const el = {
    currentSrc: '',
    src: '',
    addEventListener: (n: string, f: () => void) => {
      listeners[n] = f;
    },
  };
  const stubDocument = {
    createElement: () => el,
    // READBACK asks for video elements and og:video meta tags separately.
    querySelectorAll: (sel: string) => (sel.includes('og:video') ? [] : [el]),
  };
  const stubWindow: any = {
    __bhMedia: [],
    addEventListener: () => {},
  };
  const stubMediaProto: any = {};
  const stubMediaProtoCtor = { prototype: stubMediaProto };
  class FakeXHR {
    responseURL = '';
    open() {}
    addEventListener() {}
    getResponseHeader() {
      return null;
    }
  }
  const sandbox: any = {
    window: stubWindow,
    document: stubDocument,
    XMLHttpRequest: FakeXHR,
    URL: { createObjectURL: () => 'blob:x' },
    HTMLMediaElement: { prototype: {} },
    Object,
    String,
    Promise,
    navigator: {},
    add: (u: string) => captured.push(u),
  };
  sandbox.window.document = stubDocument;
  sandbox.window.URL = sandbox.URL;
  sandbox.window.HTMLMediaElement = sandbox.HTMLMediaElement;
  sandbox.window.XMLHttpRequest = FakeXHR;
  sandbox.window.navigator = sandbox.navigator;
  sandbox.window.fetch = undefined;

  // Run the real HOOK string.
  let hookRan = true;
  try {
    new Function('window', 'document', 'XMLHttpRequest', 'URL', 'HTMLMediaElement', 'Promise', 'Object', 'String', 'navigator', hookJs)(
      stubWindow,
      stubDocument,
      FakeXHR,
      sandbox.URL,
      sandbox.HTMLMediaElement,
      Promise,
      Object,
      String,
      sandbox.navigator
    );
  } catch {
    hookRan = false;
  }
  check('the shipped Java HOOK string executes without throwing', hookRan);
  check('HOOK marks the page as captured (idempotence guard)', stubWindow.__bhCaptureOn === true);
  // A real accessor must exist before the hook runs, and the hook must wrap it.
  let viaSetter: string | null = null;
  Object.defineProperty(stubMediaProto, 'src', {
    configurable: true,
    get() {
      return '';
    },
    set(v: string) {
      viaSetter = v;
    },
  });
  // Re-run against a fresh window with that prototype in place.
  const window2: any = { __bhMedia: [] };
  try {
    new Function('window', 'document', 'XMLHttpRequest', 'URL', 'HTMLMediaElement', 'Promise', 'Object', 'String', 'navigator', hookJs)(
      window2, stubDocument, FakeXHR, sandbox.URL, stubMediaProtoCtor, Promise, Object, String, sandbox.navigator
    );
  } catch {
    /* asserted below */
  }
  // Drive the wrapped setter the way a player would.
  let setterRecorded = false;
  try {
    const desc = Object.getOwnPropertyDescriptor(stubMediaProto, 'src');
    desc?.set?.call({}, 'https://cdn.example.com/movie.mp4');
    setterRecorded = window2.__bhMedia.some((m: any) => m.url === 'https://cdn.example.com/movie.mp4');
  } catch {
    setterRecorded = false;
  }
  check('HOOK intercepts a real media src assignment', setterRecorded);
  check('the wrapped setter still passes the value through', viaSetter === 'https://cdn.example.com/movie.mp4');

  // READBACK must produce a parseable envelope from the stub page.
  let readOk = false;
  let envelope = '';
  try {
    // `return` matters: without it the IIFE's value is discarded and every
    // readback would look like a failure even when the code is correct.
    envelope = new Function('window', 'document', 'navigator', `return ${readJs}`)(
      stubWindow,
      stubDocument,
      sandbox.navigator
    );
    const parsed = JSON.parse(envelope);
    readOk = Array.isArray(parsed.items);
  } catch {
    readOk = false;
  }
  check('the shipped Java READBACK string returns a parseable envelope', readOk);
  /*
   * DRM detection regression: the readback used to test a flag that nothing ever
   * set, so it could never report DRM and the app would happily offer a download
   * of an encrypted stream. Asking for a key system must raise the flag.
   */
  let drmFlagged = false;
  try {
    const emeWindow: any = { __bhMedia: [] };
    const emeNavigator: any = {
      requestMediaKeySystemAccess: () => Promise.resolve({}),
    };
    new Function(
      'window',
      'document',
      'XMLHttpRequest',
      'URL',
      'HTMLMediaElement',
      'Promise',
      'Object',
      'String',
      'navigator',
      hookJs
    )(emeWindow, stubDocument, FakeXHR, sandbox.URL, stubMediaProtoCtor, Promise, Object, String, emeNavigator);
    // A page asking for Widevine is exactly what a DRM player does.
    emeNavigator.requestMediaKeySystemAccess('com.widevine.alpha', []);
    const env = new Function('window', 'document', 'navigator', `return ${readJs}`)(
      emeWindow,
      stubDocument,
      emeNavigator
    );
    drmFlagged = JSON.parse(env).drm === true;
  } catch {
    drmFlagged = false;
  }
  check('asking for a key system marks the page as DRM-protected', drmFlagged);

  check('READBACK reports MediaSource usage', (() => {
    try {
      return 'mse' in JSON.parse(envelope);
    } catch {
      return false;
    }
  })());
}

console.log('');
if (failures.length) {
  console.error(`video capture (after Java execution): ${failures.length} failure(s)`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(`video capture: ${passed} checks pass — real media is found, impossible downloads are refused`);
