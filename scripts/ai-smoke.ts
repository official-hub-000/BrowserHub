import { runLocalAiClient, runSmartHybridAi } from '../src/utils/localAiEngine';
import { getPageContextForAi } from '../src/utils/pageTextExtractor';

const ddgDump = `All Results DuckDuckGo Open menu Ask Duck.ai All Images Videos News Maps Search Settings Private Region Safe search Date HEY https://www.hey.com HEY — A delightfully fresh take on email + calendar, from 37signals Hey everyone— I'm Jason, CEO here at 37signals.`;

const cases = [
  { label: 'Hey on DDG search page', input: { mode: 'chat', query: 'Hey', url: 'https://duckduckgo.com/?q=Hey', content: ddgDump, title: 'Hey at DuckDuckGo' } },
  { label: 'Hello on home', input: { mode: 'chat', query: 'Hello', url: 'browserhub://start', content: '' } },
  { label: 'Summarize on search page', input: { mode: 'summarize', url: 'https://duckduckgo.com/?q=Hey', content: ddgDump } },
  { label: 'What is quantum computing (offline)', input: { mode: 'chat', query: 'What is quantum computing?', url: 'browserhub://start', content: '' } },
  { label: 'Math', input: { mode: 'chat', query: '18% of 2450', url: 'browserhub://start', content: '' } },
  { label: 'Draft email', input: { mode: 'chat', query: 'draft an email about a late delivery', url: 'browserhub://start', content: '' } },
  { label: 'Summarize real article', input: { mode: 'summarize', url: 'https://example.com/privacy-guide', title: 'Privacy Guide', content: `Privacy Guide. Modern browsers must protect users from pervasive tracking across the web. BrowserHub blocks advertising and analytics scripts before they are executed, which reduces page weight substantially. Encrypted DNS prevents your internet provider from observing the domains you visit. Reader mode strips navigation, sidebars and cookie banners so long articles stay readable on a small screen.` } },
];

for (const c of cases) {
  const out = runLocalAiClient(c.input as any);
  console.log('\n=== ' + c.label);
  console.log(out.responseText);
  if (out.bullets.length) console.log('   bullets:', out.bullets.join(' | '));
}

const ctx = getPageContextForAi('https://duckduckgo.com/?q=Hey', ddgDump);
console.log('\nCTX for DDG:', { kind: ctx.kind, text: ctx.text, note: ctx.contextNote });
