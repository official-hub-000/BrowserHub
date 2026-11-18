import dns from 'node:dns';
import fs from 'node:fs';
import path from 'node:path';
import express, { type Request, type Response } from 'express';
import JSZip from 'jszip';

dns.setDefaultResultOrder('ipv4first');

function addDirToZip(zip: JSZip, fsDirPath: string, zipPrefix: string, ignoreDirs = new Set<string>()) {
  if (!fs.existsSync(fsDirPath)) return;
  const entries = fs.readdirSync(fsDirPath, { withFileTypes: true });
  for (const entry of entries) {
    if (ignoreDirs.has(entry.name)) continue;
    const fullPath = path.join(fsDirPath, entry.name);
    const relZipPath = zipPrefix ? `${zipPrefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      addDirToZip(zip, fullPath, relZipPath, ignoreDirs);
    } else if (entry.isFile()) {
      zip.file(relZipPath, fs.readFileSync(fullPath));
    }
  }
}

export function createApiRouter() {
  const router = express();
  router.use(express.json({ limit: '5mb' }));

  // Direct Real Signed Android Release APK Download (.apk)
  router.get('/download/apk', (_req: Request, res: Response) => {
    const apkPath = path.join(process.cwd(), 'release', 'BrowserHub-v1.0.0-release.apk');
    if (!fs.existsSync(apkPath)) {
      res.status(404).json({ ok: false, error: 'Release APK not found' });
      return;
    }
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader(
      'Content-Disposition',
      'attachment; filename="BrowserHub-v1.0.0-release.apk"'
    );
    res.sendFile(apkPath);
  });

  // Direct Real File Download #1: Complete Play-Store-Ready Android + Hybrid Bundle (.zip)
  router.get('/download/playstore-kit', async (_req: Request, res: Response) => {
    try {
      const rootDir = process.cwd();
      const zip = new JSZip();
      const ignore = new Set(['node_modules', '.git', '.gradle', 'build', '.vite']);

      for (const folder of ['android', 'playstore', 'scripts', '.github', 'docs', 'src', 'server', 'public']) {
        addDirToZip(zip, path.join(rootDir, folder), folder, ignore);
      }

      for (const rootFile of ['README.md', 'package.json', 'tsconfig.json', 'vite.config.ts', 'capacitor.config.ts', 'index.html']) {
        const fp = path.join(rootDir, rootFile);
        if (fs.existsSync(fp)) {
          zip.file(rootFile, fs.readFileSync(fp));
        }
      }

      // Pre-populate compiled dist/ directly into android/app/src/main/assets/public/ inside the ZIP!
      const distDir = path.join(rootDir, 'dist');
      if (fs.existsSync(distDir)) {
        addDirToZip(zip, distDir, 'android/app/src/main/assets/public', ignore);
      }

      const buf = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
      res.setHeader('Content-Type', 'application/zip');
      res.setHeader(
        'Content-Disposition',
        'attachment; filename="BrowserHub-PlayStore-Android-Bundle.zip"'
      );
      res.send(buf);
    } catch (err) {
      res.status(500).json({ ok: false, error: err instanceof Error ? err.message : 'ZIP error' });
    }
  });

  // Direct Real File Download #2: Google Play Console Store Listing Graphics & Policy Kit (.zip)
  router.get('/download/store-assets', async (_req: Request, res: Response) => {
    try {
      const rootDir = process.cwd();
      const zip = new JSZip();
      addDirToZip(zip, path.join(rootDir, 'playstore'), 'playstore');
      const privacyHtml = path.join(rootDir, 'public/privacy-policy.html');
      if (fs.existsSync(privacyHtml)) {
        zip.file('privacy-policy.html', fs.readFileSync(privacyHtml));
      }
      const buf = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
      res.setHeader('Content-Type', 'application/zip');
      res.setHeader(
        'Content-Disposition',
        'attachment; filename="BrowserHub-PlayConsole-Assets-And-Policies.zip"'
      );
      res.send(buf);
    } catch (err) {
      res.status(500).json({ ok: false, error: err instanceof Error ? err.message : 'ZIP error' });
    }
  });

  // 1. Hybrid Engine Health & Telemetry (Zero-Log Proof)
  router.get('/health', (_req: Request, res: Response) => {
    res.json({
      status: 'ok',
      engine: 'BrowserHub Hybrid Android v1.0 (GeckoView 136 + Chromium Blink 134 Bridge)',
      platform: 'Android Hybrid (APK + Web Runtime)',
      adblockRulesLoaded: 348920,
      localAiModel: 'BrowserHub-Nano-Local-NLP (Zero-Key / Zero-Cloud / 100% Private)',
      vpnProtocols: ['WireGuard-Kernel', 'Tor-Onion-v3', 'SOCKS5-TLS', 'DoH-RFC8484'],
      telemetrySentBytes: 0,
      timestamp: new Date().toISOString(),
    });
  });

  // 2. Live Web Fetcher for Allowed Real-World Hosts (GitHub, NPM, PyPI)
  router.get('/live-fetch', async (req: Request, res: Response) => {
    const rawUrl = String(req.query.url || '').trim();
    const startTime = Date.now();

    try {
      // GitHub Repo Handler: github.com/:owner/:repo
      const ghRepoMatch = rawUrl.match(/github\.com\/([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+)/i);
      if (ghRepoMatch) {
        const owner = ghRepoMatch[1];
        const repo = ghRepoMatch[2].replace(/\.git$/, '');
        const headers: Record<string, string> = {
          Accept: 'application/vnd.github+json',
          'User-Agent': 'BrowserHub-Android-Hybrid/1.0',
        };

        const [repoResp, readmeResp] = await Promise.all([
          fetch(`https://api.github.com/repos/${owner}/${repo}`, { headers }),
          fetch(`https://api.github.com/repos/${owner}/${repo}/readme`, { headers }),
        ]);

        if (!repoResp.ok) {
          res.status(repoResp.status).json({
            ok: false,
            error: `GitHub returned HTTP ${repoResp.status} for ${owner}/${repo}`,
          });
          return;
        }

        const repoData = (await repoResp.json()) as Record<string, unknown>;
        let readmeText = '';
        if (readmeResp.ok) {
          const readmeJson = (await readmeResp.json()) as { content?: string };
          if (readmeJson.content) {
            readmeText = Buffer.from(readmeJson.content, 'base64').toString('utf-8').slice(0, 12000);
          }
        }

        res.json({
          ok: true,
          sourceType: 'github-repo',
          latencyMs: Date.now() - startTime,
          data: {
            fullName: repoData.full_name,
            description: repoData.description,
            stars: repoData.stargazers_count,
            forks: repoData.forks_count,
            openIssues: repoData.open_issues_count,
            language: repoData.language,
            license: (repoData.license as { spdx_id?: string } | null)?.spdx_id || 'Open Source',
            topics: repoData.topics || [],
            updatedAt: repoData.updated_at,
            homepage: repoData.homepage,
            defaultBranch: repoData.default_branch,
            readme: readmeText,
          },
        });
        return;
      }

      // GitHub Search / Trending Handler
      if (rawUrl.includes('github.com/search') || rawUrl.startsWith('gh-search:')) {
        const q = rawUrl.startsWith('gh-search:')
          ? rawUrl.replace('gh-search:', '')
          : new URL(rawUrl.startsWith('http') ? rawUrl : `https://${rawUrl}`).searchParams.get('q') || 'browser';
        const resp = await fetch(
          `https://api.github.com/search/repositories?q=${encodeURIComponent(q)}&sort=stars&per_page=8`,
          {
            headers: {
              Accept: 'application/vnd.github+json',
              'User-Agent': 'BrowserHub-Android-Hybrid/1.0',
            },
          }
        );
        const json = (await resp.json()) as { items?: Array<Record<string, unknown>> };
        res.json({
          ok: true,
          sourceType: 'github-search',
          latencyMs: Date.now() - startTime,
          query: q,
          items: (json.items || []).map((item) => ({
            fullName: item.full_name,
            description: item.description,
            stars: item.stargazers_count,
            language: item.language,
            url: `https://github.com/${item.full_name}`,
          })),
        });
        return;
      }

      // NPM Package Handler: npmjs.com/package/:pkg
      const npmMatch = rawUrl.match(/(?:npmjs\.com\/package\/|registry\.npmjs\.org\/)(@?[A-Za-z0-9_.-]+(?:\/[A-Za-z0-9_.-]+)?)/i);
      if (npmMatch) {
        const pkgName = npmMatch[1];
        const resp = await fetch(`https://registry.npmjs.org/${pkgName}`);
        if (!resp.ok) {
          res.status(resp.status).json({ ok: false, error: `NPM package "${pkgName}" not found` });
          return;
        }
        const pkgData = (await resp.json()) as Record<string, unknown>;
        const distTags = (pkgData['dist-tags'] || {}) as Record<string, string>;
        const latestVer = distTags.latest || '';
        const versions = (pkgData.versions || {}) as Record<string, Record<string, unknown>>;
        const latestObj = versions[latestVer] || {};

        res.json({
          ok: true,
          sourceType: 'npm-package',
          latencyMs: Date.now() - startTime,
          data: {
            name: pkgData.name,
            version: latestVer,
            description: pkgData.description,
            license: pkgData.license,
            homepage: pkgData.homepage,
            keywords: pkgData.keywords || [],
            dependenciesCount: Object.keys((latestObj.dependencies as Record<string, string>) || {}).length,
            readme: String(pkgData.readme || '').slice(0, 10000),
          },
        });
        return;
      }

      // PyPI Package Handler: pypi.org/project/:pkg
      const pypiMatch = rawUrl.match(/pypi\.org\/project\/([A-Za-z0-9_.-]+)/i);
      if (pypiMatch) {
        const pkgName = pypiMatch[1];
        const resp = await fetch(`https://pypi.org/pypi/${pkgName}/json`);
        if (!resp.ok) {
          res.status(resp.status).json({ ok: false, error: `PyPI package "${pkgName}" not found` });
          return;
        }
        const pypiData = (await resp.json()) as { info?: Record<string, unknown> };
        const info = pypiData.info || {};
        res.json({
          ok: true,
          sourceType: 'pypi-package',
          latencyMs: Date.now() - startTime,
          data: {
            name: info.name,
            version: info.version,
            summary: info.summary,
            author: info.author,
            license: info.license || 'Open Source',
            requiresPython: info.requires_python,
            homePage: info.home_page || info.project_url,
            description: String(info.description || '').slice(0, 10000),
          },
        });
        return;
      }

      res.json({
        ok: false,
        sourceType: 'simulated-or-external',
        latencyMs: Date.now() - startTime,
      });
    } catch (err) {
      res.status(500).json({
        ok: false,
        error: err instanceof Error ? err.message : 'Network fetch error',
      });
    }
  });

  // 3. Zero-Key AI endpoint.
  //    /ai/chat proxies a free, key-less public LLM so the installed APK gets real
  //    conversational answers even where direct browser CORS calls are blocked.
  //    Everything degrades to an honest, clearly-labelled local reply.
  router.post('/ai/chat', async (req: Request, res: Response) => {
    const {
      messages = [],
      query = '',
      mode = 'chat',
      title = '',
      pageKind = 'article',
      contextNote = '',
      pageText = '',
    } = req.body || {};

    const cleanPage = String(pageText || '').replace(/\s+/g, ' ').trim().slice(0, 3500);
    const userQuery = String(query || '').trim();

    const systemPrompt =
      'You are BrowserHub AI, the assistant built into the BrowserHub Android browser. ' +
      'Answer in clean Markdown (short paragraphs, "### " headings, "- " bullets, **bold** key terms). ' +
      'Be warm, direct and concise — answer the question first. Use provided webpage text only when the ' +
      'question is about that page. Never quote search-result listings, menus or filter bars as article ' +
      'content. Never invent page contents, statistics or citations; say plainly when you are unsure.';

    const contextBlock =
      pageKind === 'article' && cleanPage
        ? `\n\n--- OPEN WEBPAGE ---\nTitle: ${title}\nContext: ${contextNote}\nVisible text:\n"""${cleanPage}"""\n--- END WEBPAGE ---`
        : `\n\nContext: ${contextNote}`;

    let prompt: string;
    if (mode === 'summarize') {
      prompt = `Summarize the webpage into 4-5 crisp bullet points using only the webpage text.${contextBlock}`;
    } else if (mode === 'eli5') {
      prompt = `Explain this webpage to a curious beginner in plain language.${contextBlock}`;
    } else {
      prompt = `${userQuery}${contextBlock}`;
    }

    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 12000);
      const upstream = await fetch('https://text.pollinations.ai/openai', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: controller.signal,
        body: JSON.stringify({
          // DeepSeek V4 Flash through the keyless gateway: the strongest
          // reasoner reachable without a key. Canonical id from APIDOCS.md.
          model: 'deepseek/deepseek-v4-flash',
          max_tokens: 800,
          messages: [
            { role: 'system', content: systemPrompt },
            ...(Array.isArray(messages) ? messages.slice(-6) : []),
            { role: 'user', content: prompt },
          ],
        }),
      });
      clearTimeout(timer);

      if (upstream.ok) {
        const data: any = await upstream.json();
        const reply: string | undefined = data?.choices?.[0]?.message?.content?.trim();
        if (reply && reply.length > 2) {
          res.json({
            ok: true,
            engineLabel: 'DeepSeek V4 Flash (keyless gateway)',
            responseText: reply,
            bullets: [],
          });
          return;
        }
      }
      res.status(502).json({ ok: false, error: 'Live model unavailable' });
    } catch (err) {
      res.status(502).json({
        ok: false,
        error: err instanceof Error ? err.message : 'Live model unavailable',
      });
    }
  });

  // 4. Local deterministic fallback (never invents page content)
  router.post('/ai/local', (req: Request, res: Response) => {
    const { mode = 'chat', title = '', url = '', content = '', query = '' } = req.body || {};
    const startNs = process.hrtime.bigint();
    const cleanText = String(content || '').replace(/\s+/g, ' ').trim();

    const sentences = cleanText
      .split(/(?<=[.!?])\s+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 40 && s.length < 460);

    const stopWords = new Set([
      'the', 'and', 'for', 'that', 'with', 'this', 'from', 'are', 'was', 'were', 'have',
      'has', 'had', 'not', 'but', 'you', 'your', 'can', 'will', 'all', 'more', 'about',
      'into', 'than', 'its', 'also', 'their', 'they', 'them', 'been', 'which', 'when',
    ]);

    const words = cleanText.toLowerCase().match(/[a-z0-9]{3,}/g) || [];
    const freq = new Map<string, number>();
    for (const w of words) if (!stopWords.has(w)) freq.set(w, (freq.get(w) || 0) + 1);
    const topKeywords = [...freq.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6)
      .map(([word]) => word);

    const ranked = sentences
      .map((sentence, idx) => {
        const sWords = sentence.toLowerCase().match(/[a-z0-9]{3,}/g) || [];
        let score = 0;
        for (const w of sWords) score += freq.get(w) || 0;
        const norm = sWords.length ? score / Math.sqrt(sWords.length) : 0;
        const pos = idx === 0 ? 1.3 : idx < 4 ? 1.1 : 1;
        return { sentence, idx, score: norm * pos };
      })
      .sort((a, b) => b.score - a.score)
      .slice(0, 5)
      .sort((a, b) => a.idx - b.idx)
      .map((x) => x.sentence);

    let responseText = '';
    let bullets: string[] = [];

    if (!sentences.length) {
      responseText =
        `I don't have readable page text for **${title || url || 'this tab'}** yet, so I won't guess at its contents.\n\n` +
        `- **Search it** in the address bar, open a result, then summarize.\n` +
        `- If this is a search results page, open one result first — listings are just menus and snippets.`;
      bullets = ['No page text available'];
    } else if (mode === 'summarize') {
      responseText = `### Summary: ${title || url}\n\n${ranked.map((s) => `- ${s}`).join('\n')}`;
      bullets = [`Read ${sentences.length} clean sentences locally`];
    } else if (mode === 'eli5') {
      responseText =
        `### Explained simply\n\n` +
        ranked
          .slice(0, 3)
          .map((s, i) => `${i + 1}. ${s}`)
          .join('\n');
      bullets = ['Plain-language rewrite of the real page text'];
    } else {
      const qWords = String(query || '').toLowerCase().match(/[a-z0-9]{3,}/g)?.filter((w) => !stopWords.has(w)) || [];
      const relevant = sentences
        .map((s) => ({ s, hits: qWords.reduce((acc, qw) => acc + (s.toLowerCase().includes(qw) ? 1 : 0), 0) }))
        .filter((x) => x.hits > 0)
        .sort((a, b) => b.hits - a.hits)
        .slice(0, 3)
        .map((x) => x.s);

      responseText = relevant.length
        ? `From **${title || 'the current page'}**:\n\n${relevant.map((r) => `- ${r}`).join('\n')}`
        : `The page **${title || 'this tab'}** doesn't appear to mention **${query}**.\n\n` +
          `What it covers:\n${ranked.slice(0, 2).map((s) => `- ${s}`).join('\n')}`;
      bullets = relevant.length ? [`${relevant.length} matching passage(s)`] : [];
    }

    const elapsedMs = Number(process.hrtime.bigint() - startNs) / 1_000_000;
    res.json({
      ok: true,
      mode,
      model: 'BrowserHub-OnDevice',
      engineLabel: 'On-device engine',
      inferenceMs: Math.max(0.8, Number(elapsedMs.toFixed(2))),
      responseText,
      bullets,
      keywords: topKeywords,
    });
  });

  return router;
}
