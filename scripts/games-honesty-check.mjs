#!/usr/bin/env node
/**
 * Keeps the Games hub honest.
 *
 * The request was "let users play GTA V, PES, Call of Duty in the browser".
 * Those games have no web build and cannot run in a browser — so the only
 * truthful thing a games hub can do is link to the official stores and to real
 * cloud-streaming services, and say so. This check fails the build if:
 *
 *   1. a console title is listed as playable,
 *   2. an entry claims a game that has no web version,
 *   3. the hub loses its explicit "not playable in a browser" notice,
 *   4. games are offered without a capability check,
 *   5. the gamepad section stops being honest about WebView limitations.
 *
 * Run: node scripts/games-honesty-check.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const problems = [];

const gamesFile = path.join(ROOT, 'src/data/games.ts');
const hubFile = path.join(ROOT, 'src/components/GamesHub.tsx');
const probeFile = path.join(ROOT, 'src/utils/capabilityProbe.ts');
const padFile = path.join(ROOT, 'src/utils/gamepadBridge.ts');

for (const f of [gamesFile, hubFile, probeFile, padFile]) {
  if (!fs.existsSync(f)) problems.push(`${path.relative(ROOT, f)}: missing`);
}

if (!problems.length) {
  const games = fs.readFileSync(gamesFile, 'utf8');
  const hub = fs.readFileSync(hubFile, 'utf8');
  const probe = fs.readFileSync(probeFile, 'utf8');
  const pad = fs.readFileSync(padFile, 'utf8');

  /* --- 1. console titles must never appear in the playable list ---------- */
  const playableBlock = games.slice(
    games.indexOf('export const WEB_GAMES'),
    games.indexOf('export interface ConsoleLink')
  );
  for (const title of ['GTA', 'Grand Theft Auto', 'Call of Duty', 'FIFA', 'EA SPORTS FC', 'PES', 'eFootball']) {
    if (new RegExp(title, 'i').test(playableBlock)) {
      problems.push(
        `src/data/games.ts: "${title}" is in the playable list — it has no browser build`
      );
    }
  }

  /* --- 2. the console section must stay honest -------------------------- */
  if (!/not playable in a browser|no browser version|No browser build/i.test(games + hub)) {
    problems.push(
      'src/data/games.ts + GamesHub.tsx: the "not playable in a browser" notice is gone'
    );
  }
  if (!/CONSOLE_LINKS/.test(games) || !/CONSOLE_LINKS/.test(hub)) {
    problems.push('console titles must be routed through CONSOLE_LINKS (official stores only)');
  }

  /* --- 3. entries need real urls ---------------------------------------- */
  const urls = [...games.matchAll(/url:\s*'([^']+)'/g)].map((m) => m[1]);
  if (urls.length < 10) problems.push(`expected at least 10 real game links, found ${urls.length}`);
  for (const url of urls) {
    if (!/^https:\/\//.test(url)) problems.push(`insecure or non-http game url: ${url}`);
  }

  /* --- 4. every game declares its requirements and weight --------------- */
  const entries = playableBlock.split('{').length - 1;
  const needCount = (playableBlock.match(/needs:\s*\[/g) ?? []).length;
  const weightCount = (playableBlock.match(/weight:\s*'/g) ?? []).length;
  if (needCount === 0) problems.push('games declare no `needs` — capability checks are impossible');
  if (weightCount === 0) problems.push('games declare no `weight` — performance warnings are impossible');

  /* --- 5. the UI must use the probe, not guesses ------------------------ */
  if (!/checkGameFit/.test(hub)) {
    problems.push('GamesHub.tsx does not call checkGameFit — compatibility would be a guess');
  }
  if (!/probeCapabilities/.test(probe) || !/WebGL/i.test(probe)) {
    problems.push('capabilityProbe.ts no longer tests WebGL');
  }

  /* --- 6. gamepad claims must stay qualified ---------------------------- */
  if (!/WebView/.test(pad) || !/not passing|platform limit|outside the page/i.test(pad)) {
    problems.push(
      'gamepadBridge.ts: must state that Android WebView may not expose pads (no silent over-claim)'
    );
  }
  if (!/emulateKeysFromGamepad/.test(pad)) {
    problems.push('gamepadBridge.ts: keyboard translation is the only fallback and it is missing');
  }

  process.stderr.write(`· ${urls.length} game links, ${entries} entries with declared needs\n`);
}

if (problems.length) {
  console.error(`❌ ${problems.length} games-hub honesty violation(s):\n`);
  for (const p of problems) console.error(`  ${p}`);
  process.exit(1);
}

console.log('games hub is honest: no fake playability, capability-checked, gamepad limits stated');
