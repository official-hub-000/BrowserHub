/**
 * Regression proof for the reported DNS error screen:
 *
 *   "The web page at https://intent//play.google.com/store/apps/details?id=…
 *    could not be loaded because: net::ERR_NAME_NOT_RESOLVED"
 *
 * An `intent://` Android deep link was handed to the WebView as if it were a
 * web address. "intent" is not a hostname, so DNS failed and the user got an
 * error page instead of the Play Store.
 *
 * Run: npx tsx scripts/url-safety-test.mts
 */
import assert from 'node:assert/strict';
import { classifyUrl, intentUriToWebUrl, isWebLoadable } from '../src/utils/urlSafety';

let checks = 0;
const check = (label: string, fn: () => void) => {
  fn();
  checks += 1;
  void label;
};

/* ---- the exact link from the screenshot ---------------------------------- */
const SCREENSHOT_LINK =
  'intent://play.google.com/store/apps/details?id=com.microsoft.emmx&listing=extensionmarketing#Intent;scheme=https;package=com.android.vending;end';

check('screenshot intent link', () => {
  const result = classifyUrl(SCREENSHOT_LINK);
  assert.equal(result.kind, 'app-scheme', 'an intent:// link must be recognised as an app link');
  assert.ok(result.kind === 'app-scheme');
  assert.equal(
    result.webUrl,
    'https://play.google.com/store/apps/details?id=com.microsoft.emmx&listing=extensionmarketing',
    'the embedded https target must be extracted'
  );
  assert.ok(result.note.length > 10, 'the user must be told what happened');
});

check('intent extraction does not keep the ;end tail', () => {
  const url = intentUriToWebUrl(SCREENSHOT_LINK);
  assert.ok(url);
  assert.ok(!url.includes('#Intent'), 'the intent metadata must not survive');
  assert.ok(!url.includes(';end'), 'the terminator must not survive');
  assert.ok(!url.includes('intent'), 'the raw scheme must not survive');
});

/* ---- the produced URL must actually be loadable -------------------------- */
check('extracted URL is loadable', () => {
  const url = intentUriToWebUrl(SCREENSHOT_LINK)!;
  assert.equal(isWebLoadable(url), true, 'the translated URL must pass the WebView gate');
});

check('the raw broken form is rejected', () => {
  // This is what the browser ended up loading.
  assert.equal(
    isWebLoadable('https://intent//play.google.com/store/apps/details?id=x'),
    false,
    'a URL whose host is "intent" must never reach the WebView'
  );
});

/* ---- other deep links ---------------------------------------------------- */
check('market link', () => {
  const result = classifyUrl('market://details?id=com.microsoft.emmx');
  assert.equal(result.kind, 'app-scheme');
  assert.ok(result.kind === 'app-scheme');
  assert.equal(result.webUrl, 'https://play.google.com/store/apps/details?id=com.microsoft.emmx');
});

check('tel link has no web fallback', () => {
  const result = classifyUrl('tel:+8801234567890');
  assert.equal(result.kind, 'app-scheme');
  assert.ok(result.kind === 'app-scheme');
  assert.equal(result.webUrl, null, 'a phone number cannot become a web page');
  assert.match(result.note, /another app|cannot open/i);
});

check('mailto link is not loaded as a page', () => {
  const result = classifyUrl('mailto:someone@example.com');
  assert.equal(result.kind, 'app-scheme');
});

check('javascript scheme is refused', () => {
  const result = classifyUrl('javascript:alert(1)');
  assert.notEqual(result.kind, 'web', 'a javascript: URI must never be navigated to');
});

/* ---- ordinary input still works ----------------------------------------- */
check('plain https', () => {
  const result = classifyUrl('https://addons.mozilla.org/android/');
  assert.deepEqual(result, { kind: 'web', url: 'https://addons.mozilla.org/android/' });
});

check('bare domain', () => {
  const result = classifyUrl('chromewebstore.google.com');
  assert.deepEqual(result, { kind: 'web', url: 'https://chromewebstore.google.com' });
});

check('ordinary words become a search', () => {
  const result = classifyUrl('best android browser');
  assert.equal(result.kind, 'search');
});

check('internal pages survive', () => {
  const result = classifyUrl('browserhub://games');
  assert.deepEqual(result, { kind: 'internal', url: 'browserhub://games' });
});

/* ---- the gate itself ---------------------------------------------------- */
check('empty and junk are not loadable', () => {
  for (const junk of ['', '   ', 'not a url', 'about:blank', 'data:text/html,hi', 'file:///etc/passwd']) {
    assert.equal(isWebLoadable(junk), false, `"${junk}" must not be loadable`);
  }
});

check('real URLs are loadable', () => {
  for (const good of [
    'https://example.com',
    'http://example.com/path?a=1',
    'https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home',
  ]) {
    assert.equal(isWebLoadable(good), true, `"${good}" must be loadable`);
  }
});

/**
 * The property that matters: for ANY input, whatever the classifier says is a
 * web url must pass the gate, and anything else must not be handed to a WebView.
 */
check('classifier output is always consistent with the gate', () => {
  const samples = [
    SCREENSHOT_LINK,
    'market://details?id=x',
    'tel:123',
    'https://example.com',
    'example.com',
    'some words',
    'browserhub://start',
    'javascript:void(0)',
    'intent://only.a.host#Intent;end',
  ];
  for (const sample of samples) {
    const result = classifyUrl(sample);
    if (result.kind === 'web') {
      assert.equal(isWebLoadable(result.url), true, `classifier returned a non-loadable web url for "${sample}"`);
    }
    if (result.kind === 'app-scheme' && result.webUrl) {
      assert.equal(
        isWebLoadable(result.webUrl),
        true,
        `the fallback for "${sample}" is not loadable`
      );
    }
  }
});

console.log(`url safety: ${checks} checks pass — intent:// can no longer reach the WebView`);
