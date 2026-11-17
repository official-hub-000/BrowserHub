// Every search engine must produce a real, reachable URL template, and internal
// typing shortcuts must resolve to real sites instead of mock pages.
import fs from 'node:fs';

const presets = fs.readFileSync('src/data/presets.ts', 'utf8');
const app = fs.readFileSync('src/App.tsx', 'utf8');

const engines = [...presets.matchAll(/buildUrl: \(q\) => `([^`]+)`/g)].map((m) => m[1]);
const bad = [];
if (engines.length < 6) bad.push(`only ${engines.length} search engines wired`);

for (const tpl of engines) {
  const resolved = tpl.replace('${encodeURIComponent(q)}', 'test');
  let url;
  try {
    url = new URL(resolved);
  } catch {
    bad.push(`invalid URL template: ${tpl}`);
    continue;
  }
  if (url.protocol !== 'https:') bad.push(`not https: ${tpl}`);
  if (url.hostname === 'browserhub.local' || url.hostname.includes('localhost')) {
    bad.push(`mock URL: ${tpl}`);
  }
  if (!/[?&](q|query|search)=test/.test(resolved)) bad.push(`no query parameter: ${tpl}`);
}

// The typed shortcut must never be navigated to as if it were a page.
if (/startsWith\('gh-search:'\)/.test(app.split('const isProtocol')[1]?.slice(0, 200) || '')) {
  bad.push('gh-search: is still treated as a navigable URL');
}
if (!/github\.com\/search\?q=/.test(app)) bad.push('gh-search: does not resolve to github.com');

if (bad.length) {
  bad.forEach((b) => console.error(`  FAIL ${b}`));
  process.exit(1);
}
console.log(`  ${engines.length} search engines all produce real https URLs`);
