// The download path has to work on a modern Android device, not just compile.
//
// Two specific traps are guarded here, both of which produce a download that
// looks implemented and fails 100% of the time on a real phone:
//   1. Writing a partial file straight into public storage. Android 11+ refuses
//      that with EACCES under scoped storage.
//   2. Registering a finished file with MediaStore but never declaring the
//      legacy permission for the versions that still copy into Movies/.
import fs from 'node:fs';

const grabber = fs.readFileSync('android/standalone/src/com/browserhub/app/MediaGrabber.java', 'utf8');
const manifest = fs.readFileSync('android/standalone/AndroidManifest.xml', 'utf8');
const bridge = fs.readFileSync('android/standalone/src/com/browserhub/app/BrowserHubBridge.java', 'utf8');
const scripts = fs.readFileSync('android/standalone/src/com/browserhub/app/MediaCaptureScripts.java', 'utf8');

const bad = [];
const ok = [];

/** Strips comments so a rule can never be satisfied by prose alone. */
/**
 * Strips comments from Java source without touching string literals.
 *
 * A regex-based stripper is not good enough here: `/*` and `//` both occur
 * inside string literals in this file, and stripping them truncated real code.
 * This walks the input once, tracking whether it is inside a string, a char
 * literal, an escaped character, a line comment or a block comment.
 */
const code = (src) => {
  let out = '';
  let i = 0;
  const n = src.length;
  while (i < n) {
    const c = src[i];
    const next = src[i + 1];
    if (c === '"' || c === "'") {
      const quote = c;
      out += c;
      i += 1;
      while (i < n) {
        out += src[i];
        if (src[i] === '\\') {
          if (i + 1 < n) out += src[i + 1];
          i += 2;
          continue;
        }
        if (src[i] === quote) {
          i += 1;
          break;
        }
        i += 1;
      }
      continue;
    }
    if (c === '/' && next === '/') {
      while (i < n && src[i] !== '\n') i += 1;
      continue;
    }
    if (c === '/' && next === '*') {
      i += 2;
      while (i + 1 < n && !(src[i] === '*' && src[i + 1] === '/')) i += 1;
      i += 2;
      continue;
    }
    out += c;
    i += 1;
  }
  return out;
};

const grabberCode = code(grabber);

// 1. Staging must be app-private.
if (/getCacheDir\(\)/.test(grabberCode)) ok.push('partial downloads stage in app cache');
else bad.push('MediaGrabber does not stage downloads in app-private storage');

// Any `.part` staging that is not rooted in getCacheDir() is a real bug: on
// Android 11+ the write is refused outright.
{
  const partSites = [...grabberCode.matchAll(/new File\(([^;]*?)\.part"?\)/g)].map((m) => m[1]);
  const publicPart = partSites.filter(
    (root) => !/stagingDir|getCacheDir/.test(root)
  );
  if (publicPart.length) {
    bad.push(`a .part file is staged outside app-private storage: ${publicPart[0].slice(0, 60)}`);
  } else {
    ok.push('no .part file is ever staged in public storage');
  }
}

// 2. Modern path publishes through MediaStore.
if (/MediaStore\.Video\.Media\.EXTERNAL_CONTENT_URI/.test(grabberCode)) ok.push('publishes to MediaStore');
else bad.push('finished video is not published to MediaStore');

if (/MediaStore\.Audio\.Media\.EXTERNAL_CONTENT_URI/.test(grabberCode)) ok.push('audio goes to the Music collection');
else bad.push('audio downloads are not placed in the Music collection');

// 3. Legacy permission must exist AND be capped, or modern devices get asked
//    for storage access they do not need.
if (/WRITE_EXTERNAL_STORAGE/.test(manifest)) {
  if (/WRITE_EXTERNAL_STORAGE[\s\S]{0,80}?maxSdkVersion="28"/.test(manifest)) {
    ok.push('legacy write permission capped at API 28');
  } else {
    bad.push('WRITE_EXTERNAL_STORAGE is declared without maxSdkVersion, so modern devices are asked for it');
  }
} else {
  bad.push('WRITE_EXTERNAL_STORAGE is missing for Android 9 and below');
}

// 4. Only web protocols: a page could otherwise hand us file:// or javascript:.
if (/!"http"\.equalsIgnoreCase\(protocol\)/.test(grabberCode) && /!"https"\.equalsIgnoreCase\(protocol\)/.test(grabberCode)) {
  ok.push('only http/https URLs are fetched');
} else {
  bad.push('MediaGrabber does not restrict itself to http/https');
}

// 5. An HTML response must be refused, not saved as a broken .mp4.
if (/text\/html/.test(grabberCode)) ok.push('an HTML body is refused instead of saved as video');
else bad.push('an HTML response would be written out as a broken video file');

// 6. The page hooks must be wrapped so they can never break a site.
if (/catch\(e\)\{\}/.test(scripts) && /__bhCaptureOn/.test(scripts)) {
  ok.push('page hooks are guarded and idempotent');
} else {
  bad.push('page hooks are not guarded against breaking the page');
}

// 6b. The emitted page scripts must be valid JavaScript.
//
// This is checked properly by video-capture-test.mts, which extracts the real
// strings out of this file and executes them against a stub page. A weaker second
// check lives here so a broken script fails the release even if that test is ever
// run in isolation.
{
  const extract = (name) => {
    const at = scripts.indexOf(name + ' =');
    if (at < 0) return '';
    const rest = scripts.slice(at);
    const term = rest.indexOf('";\n');
    const seg = term >= 0 ? rest.slice(0, term + 1) : rest;
    return [...seg.matchAll(/"((?:[^"\\]|\\.)*)"/g)]
      .map((m) => m[1])
      .join('')
      .replace(/\\\\/g, '\\');
  };
  const problems = [];
  for (const name of ['HOOK', 'READBACK']) {
    const js = extract(name);
    if (!js) {
      problems.push(`${name} could not be extracted`);
      continue;
    }
    try {
      new Function(`return ${js}`);
    } catch (e) {
      problems.push(`${name} is not valid JavaScript (${e.message})`);
    }
  }
  if (problems.length) problems.forEach((p) => bad.push(p));
  else ok.push('both injected page scripts are valid JavaScript');
}

// 7. The bridge must actually expose the download to the shell.
for (const m of ['collectPageMedia', 'getPageMedia', 'downloadPageMedia', 'hasNativeDownloader']) {
  if (!bridge.includes(m)) bad.push(`bridge does not expose ${m}`);
}
if (!bad.length) ok.push('shell bridge exposes capture + download');

if (bad.length) {
  ok.forEach((o) => console.log(`  ok  ${o}`));
  bad.forEach((b) => console.error(`  FAIL ${b}`));
  process.exit(1);
}
ok.forEach((o) => console.log(`  ok  ${o}`));
console.log('download path is scoped-storage safe on Android 10+ and complete on older devices');
