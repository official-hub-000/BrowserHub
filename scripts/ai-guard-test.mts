/**
 * Locks in the AI behaviour the user reported as broken.
 * Run: npx tsx scripts/ai-guard-test.mts
 */
import { runLocalAiClient } from '../src/utils/localAiEngine';
import { getPageContextForAi } from '../src/utils/pageTextExtractor';

let failures = 0;
function check(name: string, condition: boolean, detail?: string) {
  if (condition) {
    console.log('OK   ' + name);
  } else {
    failures++;
    console.log('FAIL ' + name + (detail ? ' — ' + detail : ''));
  }
}

const ddg = `All Results DuckDuckGo Open menu Ask Duck.ai All Images Videos News Maps Search Settings Private Region Safe search Date HEY https://www.hey.com HEY — A delightfully fresh take on email + calendar, from 37signals Hey everyone— I'm Jason, CEO here at 37signals.`;

const greeting = runLocalAiClient({
  mode: 'chat', query: 'Hey', url: 'https://duckduckgo.com/?q=Hey', title: 'Hey at DuckDuckGo', content: ddg,
});
check('greeting does not echo page chrome', !/All Results|Open menu|DuckDuckGo Open/.test(greeting.responseText));
check('greeting has balanced bold markers', (greeting.responseText.match(/\*\*/g) || []).length % 2 === 0);
check('greeting mentions BrowserHub AI', greeting.responseText.includes('BrowserHub AI'));

const summarizeOnSearch = runLocalAiClient({
  mode: 'summarize', url: 'https://duckduckgo.com/?q=Hey', content: ddg,
});
check('search page is refused for summarize', /search results page/i.test(summarizeOnSearch.responseText));
check('search page never quoted', !/37signals/.test(summarizeOnSearch.responseText));

const offlineQuestion = runLocalAiClient({
  mode: 'chat', query: 'What is quantum computing?', url: 'browserhub://start', content: '',
});
// The fallback must always state plainly that no live model answered, and it
// must say which kind of failure it was rather than a vague apology.
check(
  'offline admits it cannot answer',
  /(couldn't reach a live AI model|could not reach the internet|rate limited)/i.test(
    offlineQuestion.responseText
  )
);
check(
  'offline names the specific reason',
  /(could not reach the internet|rate limited)/i.test(offlineQuestion.responseText)
);
check('offline offers next steps', /search/i.test(offlineQuestion.responseText));

const math = runLocalAiClient({ mode: 'chat', query: '18% of 2450', url: 'browserhub://start' });
check('math is exact', math.responseText.includes('441'));

const ctx = getPageContextForAi('https://duckduckgo.com/?q=Hey', ddg);
check('search context has no body text', ctx.text === '' && ctx.kind === 'search');
check('search context exposes the query', ctx.searchQuery === 'Hey');

const article = runLocalAiClient({
  mode: 'summarize',
  url: 'https://example.com/privacy',
  title: 'Privacy Guide',
  content:
    'Privacy Guide. Modern browsers must protect users from pervasive tracking across the web. BrowserHub blocks advertising and analytics scripts before they are executed, which reduces page weight substantially. Encrypted DNS prevents your internet provider from observing the domains you visit.',
});
check('article summary returns real sentences', /pervasive tracking/.test(article.responseText));
check('article summary has no navigation words', !/Open menu|Safe search/.test(article.responseText));

console.log(failures === 0 ? '\nAll AI guard checks passed.' : `\n${failures} AI guard check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
