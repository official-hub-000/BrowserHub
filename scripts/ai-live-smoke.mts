import { runSmartHybridAi } from '../src/utils/localAiEngine';

/**
 * Mirrors FormattedAiMessage: strip fenced blocks, resolve unbalanced ** pairs,
 * drop stray single-asterisk emphasis, then remove the remaining markers the
 * renderer would have turned into <strong>.
 */
function renderLikeDrawer(markdown: string): string {
  return markdown
    .split(/(```[\s\S]*?```)/g)
    .map((block) =>
      block.startsWith('```')
        ? block.replace(/^```[a-zA-Z0-9_-]*\n?/, '').replace(/```$/, '')
        : block
            .split('\n')
            .map((line) => {
              let out = line;
              if ((out.match(/\*\*/g) || []).length % 2 === 1) out = out.replace(/\*\*/g, '');
              out = out.replace(/(^|[^*])\*([^\s*][^*\n]*[^\s*])\*(?!\*)/g, '$1$2');
              out = out.replace(/\*\*([^*]+)\*\*/g, '$1');
              // Repeat once so "*a* **b** *c*" style leftovers resolve too.
              out = out.replace(/(^|[^*])\*([^\s*][^*\n]*[^\s*])\*(?!\*)/g, '$1$2');
              out = out.replace(/`([^`]+)`/g, '$1');
              out = out.replace(/^#{1,4}\s+/, '');
              out = out.replace(/^([-•*])\s+/, '');
              return out;
            })
            .join('\n')
    )
    .join('\n');
}

async function main() {
  const cases: Array<{ mode: 'chat' | 'summarize'; query: string; url: string; title: string; content: string }> = [
    { mode: 'chat', query: 'Hey', url: 'https://duckduckgo.com/?q=Hey', title: 'Hey at DuckDuckGo', content: '' },
    { mode: 'chat', query: 'What is 18% of 2450?', url: 'browserhub://start', title: 'New Tab', content: '' },
    {
      mode: 'summarize',
      query: '',
      url: 'https://example.com/article',
      title: 'Privacy in Browsers',
      content:
        'Modern browsers collect telemetry. BrowserHub keeps data on device. Shields block trackers before requests are made. Users can audit every blocked domain in the network log.',
    },
  ];

  let failed = 0;
  for (const c of cases) {
    const r = await runSmartHybridAi(c as never);
    const text = String(r.responseText);
    const label = c.query || c.mode;

    // What matters is what the user SEES. Run the same markdown pipeline the
    // drawer uses and assert no asterisk survives it.
    const rendered = renderLikeDrawer(text);
    const leaked = rendered.includes('*');
    const empty = rendered.trim().length === 0;
    console.log(
      `${leaked || empty ? 'FAIL' : 'OK  '} ${label}: ${rendered.slice(0, 110).replace(/\n/g, ' | ')}`
    );
    if (leaked || empty) failed++;
  }

  // No network in CI: the offline path must say so rather than invent an answer.
  const offline = await runSmartHybridAi({
    mode: 'chat',
    query: 'Explain quantum entanglement',
    url: 'browserhub://start',
    title: 'New Tab',
    content: '',
  } as never);
  const honest =
    /offline|can.t reach|no network|local|on-device|unavailable/i.test(offline.responseText);
  console.log(`${honest ? 'OK  ' : 'FAIL'} offline honesty: ${offline.responseText.slice(0, 110).replace(/\n/g, ' | ')}`);
  if (!honest) failed++;

  if (failed) {
    console.error(`${failed} AI smoke check(s) failed`);
    process.exit(1);
  }
  console.log('AI smoke checks passed');
}

main();
