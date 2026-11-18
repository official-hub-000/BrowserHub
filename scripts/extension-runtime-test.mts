/**
 * Proves the WebExtension runtime actually executes extension code.
 *
 * The user's request was "add a extension engine to add any extension". Chrome's
 * engine cannot be embedded, but a real WebExtension runtime can be built, and
 * this test is what separates that claim from a label on a card:
 *
 *  - it builds a genuine MV3-style package (manifest + content script + CSS),
 *  - analyses it exactly as the app does,
 *  - matches it against real URLs,
 *  - and then EXECUTES the produced JavaScript in a DOM to prove it changes
 *    the page.
 *
 * Run: npx tsx scripts/extension-runtime-test.mts
 */
import assert from 'node:assert/strict';
import {
  analysePackage,
  matchPatternToRegExp,
  urlMatches,
  buildInjection,
  describeRuntime,
} from '../src/utils/webextensionRuntime';

let checks = 0;
const check = (label: string, fn: () => void) => {
  try {
    fn();
    checks += 1;
  } catch (err) {
    console.error(`FAILED: ${label}`);
    throw err;
  }
};

/* ------------------------------------------------------------------ patterns */

check('match patterns', () => {
  const cases: Array<[string, string, boolean]> = [
    ['*://*.youtube.com/*', 'https://www.youtube.com/watch?v=1', true],
    ['*://*.youtube.com/*', 'https://youtube.com/', true],
    ['*://*.youtube.com/*', 'https://notyoutube.com/', false],
    ['*://*.youtube.com/*', 'https://evil.com/?x=youtube.com', false],
    ['https://github.com/*', 'https://github.com/a/b', true],
    ['https://github.com/*', 'http://github.com/a/b', false],
    ['<all_urls>', 'https://anything.example/x', true],
    ['*://*.example.com/path/*', 'https://a.example.com/path/deep', true],
    ['*://*.example.com/path/*', 'https://a.example.com/other', false],
  ];
  for (const [pattern, url, expected] of cases) {
    const re = matchPatternToRegExp(pattern);
    assert.ok(re, `pattern did not compile: ${pattern}`);
    assert.equal(
      re!.test(url),
      expected,
      `${pattern} vs ${url} should be ${expected}`
    );
    assert.equal(urlMatches(url, [pattern]), expected);
  }
});

/* --------------------------------------------------------------- a real package */

const CONTENT_CSS = `.bh-demo-badge { position: fixed; inset: auto auto 8px 8px; }`;
const CONTENT_JS = `
  var badge = document.createElement('div');
  badge.className = 'bh-demo-badge';
  badge.id = 'bh-demo-badge';
  badge.textContent = 'extension running';
  document.body.appendChild(badge);
  if (window.chrome && window.chrome.storage) {
    window.chrome.storage.local.set({ ran: true });
  }
`;

const FILES: Record<string, string> = {
  'manifest.json': JSON.stringify({
    manifest_version: 3,
    name: 'Demo Highlighter',
    version: '2.1.0',
    description: 'Adds a badge to matching pages.',
    permissions: ['storage', 'tabs'],
    host_permissions: ['*://*.example.com/*'],
    content_scripts: [
      {
        matches: ['*://*.example.com/*'],
        exclude_matches: ['*://private.example.com/*'],
        js: ['content.js'],
        css: ['content.css'],
        run_at: 'document_end',
      },
    ],
    background: { service_worker: 'sw.js' },
  }),
  'content.js': CONTENT_JS,
  'content.css': CONTENT_CSS,
};

const report = analysePackage(JSON.parse(FILES['manifest.json']), (path) => FILES[path] ?? null);

check('manifest is understood', () => {
  assert.equal(report.name, 'Demo Highlighter');
  assert.equal(report.version, '2.1.0');
  assert.equal(report.manifestVersion, 3);
  assert.equal(report.contentScripts.length, 1);
  assert.ok(report.contentScripts[0].js?.includes('bh-demo-badge'), 'content script was not read');
  assert.ok(report.contentScripts[0].css?.includes('bh-demo-badge'), 'stylesheet was not read');
});

check('status reflects what can run', () => {
  // Content scripts + host rules run, but a service worker cannot.
  assert.equal(report.status, 'partial');
  assert.ok(
    report.unsupported.some((u) => /service worker/i.test(u)),
    'the unsupported background worker must be reported, not hidden'
  );
  assert.ok(report.capabilities.length > 0, 'capabilities must be described');
});

check('capability text is honest and specific', () => {
  const text = describeRuntime(report);
  assert.ok(/Injects code/.test(text), text);
  assert.ok(/Unsupported:/.test(text), 'unsupported features must appear in the summary');
});

/* ------------------------------------------------------------- matching + build */

check('injection is scoped to matching pages only', () => {
  const onMatch = buildInjection(report, 'https://www.example.com/page');
  assert.ok(onMatch, 'a matching page must produce an injection');
  assert.ok(onMatch!.js.includes('bh-demo-badge'));
  assert.ok(onMatch!.css.includes('bh-demo-badge'));

  const excluded = buildInjection(report, 'https://private.example.com/page');
  assert.equal(excluded, null, 'exclude_matches must win over matches');

  const other = buildInjection(report, 'https://other.example.org/page');
  assert.equal(other, null, 'a non-matching site must not receive the code');

  const internal = buildInjection(report, 'browserhub://start');
  assert.equal(internal, null, 'internal pages must never be injected');
});

check('the chrome.* shim is prepended', () => {
  const injection = buildInjection(report, 'https://www.example.com/');
  assert.ok(injection!.js.includes('window.chrome'), 'content scripts expect chrome.* to exist');
  assert.ok(injection!.js.includes('chrome.storage'), 'storage shim missing');
});

/* --------------------------------------------------------- execute it for real */

check('the injected script actually runs and mutates the page', () => {
  /*
   * Minimal DOM: the runtime's output is plain DOM code, so a tiny stand-in is
   * enough and avoids pulling a browser into the build. If the produced JS did
   * not execute, the badge element would never appear.
   */
  const appended: any[] = [];
  const body = { appendChild: (el: any) => appended.push(el) };
  const documentElement = { appendChild: () => undefined };
  const created: any[] = [];

  const sandbox = {
    document: {
      body,
      documentElement,
      createElement: (tag: string) => {
        const el: any = { tagName: tag, className: '', id: '', textContent: '' };
        created.push(el);
        return el;
      },
    },
    localStorage: {
      store: {} as Record<string, string>,
      length: 0,
      key: () => null,
      getItem(k: string) {
        return this.store[k] ?? null;
      },
      setItem(k: string, v: string) {
        this.store[k] = v;
      },
      removeItem(k: string) {
        delete this.store[k];
      },
    },
    navigator: { language: 'en' },
    location: { href: 'https://www.example.com/page', host: 'www.example.com' },
    console: { warn: () => undefined, info: () => undefined },
    Promise,
    JSON,
    Object,
    Array,
  } as any;
  sandbox.window = sandbox;

  const injected = buildInjection(report, 'https://www.example.com/page')!;
  // eslint-disable-next-line no-new-func
  const run = new Function(
    'window',
    'document',
    'localStorage',
    'navigator',
    'location',
    'console',
    'Promise',
    'JSON',
    'Object',
    'Array',
    injected.js
  );
  run(
    sandbox.window,
    sandbox.document,
    sandbox.localStorage,
    sandbox.navigator,
    sandbox.location,
    sandbox.console,
    sandbox.Promise,
    sandbox.JSON,
    sandbox.Object,
    sandbox.Array
  );

  const badge = created.find((el) => el.id === 'bh-demo-badge');
  assert.ok(badge, 'the content script did not create its element — it did not execute');
  assert.equal(badge.textContent, 'extension running');
  assert.ok(appended.includes(badge), 'the element was never attached to the page');
});

/* -------------------------------------------------- packages that cannot run */

check('a package with nothing executable says so', () => {
  const empty = analysePackage(
    {
      manifest_version: 3,
      name: 'Paywall Bypass',
      version: '1.0.0',
      permissions: ['declarativeNetRequest'],
      background: { service_worker: 'sw.js' },
    },
    () => null
  );
  assert.equal(empty.status, 'cannot-run');
  assert.match(describeRuntime(empty), /Nothing in this package can execute/i);
});

check('a heavier package is flagged', () => {
  const heavy = analysePackage(
    {
      manifest_version: 2,
      name: 'Heavy',
      version: '1.0.0',
      content_scripts: [{ matches: ['<all_urls>'], js: ['big.js'] }],
    },
    () => 'x'.repeat(500_000)
  );
  assert.ok(heavy.warnings.some((w) => /Large package/.test(w)), 'weight warning missing');
  assert.ok(heavy.warnings.some((w) => /every website/.test(w)), 'all-urls warning missing');
});

console.log(`extension runtime: ${checks} checks pass — packages are parsed, matched and executed`);
