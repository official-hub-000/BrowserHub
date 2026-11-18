/**
 * The download queue, as the UI consumes it.
 *
 * Everything here is about not lying on screen. A job that says "42%" must have
 * a real 42; a job in an unknown state must not render as if it were
 * downloading; a probe that has not finished must say "checking" rather than
 * inventing a resolution.
 */
import {
  EMPTY_PROBE,
  describeProbe,
  formatBytes,
  formatDuration,
  hasLiveJobs,
  parseDownloads,
  parseProbe,
  streamRefusal,
  type DownloadJob,
} from '../src/utils/downloadQueue';

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

console.log('download queue');

// ------------------------------------------------------------------- parsing
{
  const raw = JSON.stringify({
    jobs: [
      {
        id: 'dl-1',
        url: 'https://c/v/master.m3u8',
        filename: 'Big Buck Bunny.ts',
        kind: 'hls',
        title: 'Big Buck Bunny',
        status: 'running',
        percent: 42,
        bytes: 1048576,
        totalBytes: 4194304,
        message: '120 of 280 parts',
        resumable: true,
      },
      {
        id: 'dl-2',
        url: 'https://c/v/clip.mp4',
        filename: 'clip.mp4',
        kind: 'file',
        title: 'clip',
        status: 'done',
        percent: 100,
        bytes: 2097152,
        totalBytes: 2097152,
        message: '2.0 MB',
        resumable: false,
      },
    ],
  });
  const jobs = parseDownloads(raw);
  check('parses both jobs', jobs.length === 2);
  check('keeps the running state', jobs[0].status === 'running');
  check('keeps the real percentage', jobs[0].percent === 42);
  check('keeps the byte counter', jobs[0].bytes === 1048576);
  check('keeps the part message', jobs[0].message === '120 of 280 parts');
  check('keeps the kind so the UI can label a stream', jobs[0].kind === 'hls');
  check('done jobs report done', jobs[1].status === 'done');
}

// Malformed input must not reach the UI as a broken job.
for (const bad of ['', 'not json', '{}', '{"jobs":null}', '{"jobs":[{}]}']) {
  check(`malformed payload is ignored: ${bad.slice(0, 16) || '(empty)'}`, parseDownloads(bad).length === 0);
}

// An unknown status must not be rendered as if it were running.
{
  const jobs = parseDownloads('{"jobs":[{"id":"x","status":"weird"}]}');
  check('unknown status falls back to queued, never to running', jobs[0].status === 'queued');
}

// Percentages are clamped: a bridge glitch must not draw a 400% bar.
{
  const jobs = parseDownloads('{"jobs":[{"id":"x","percent":400,"status":"running"}]}');
  check('percent is clamped to 100', jobs[0].percent === 100);
  const neg = parseDownloads('{"jobs":[{"id":"x","percent":-5,"status":"running"}]}');
  check('negative percent is clamped to 0', neg[0].percent === 0);
  const nan = parseDownloads('{"jobs":[{"id":"x","percent":"abc","status":"running"}]}');
  check('a non-numeric percent becomes 0', nan[0].percent === 0);
}

// -------------------------------------------------------- live-job detection
{
  const running = parseDownloads('{"jobs":[{"id":"a","status":"running"}]}');
  const done = parseDownloads('{"jobs":[{"id":"a","status":"done"}]}');
  const failed = parseDownloads('{"jobs":[{"id":"a","status":"failed"}]}');
  check('running counts as live', hasLiveJobs(running));
  check('done is not live', !hasLiveJobs(done));
  check('failed is not live', !hasLiveJobs(failed));
}

// ------------------------------------------------------------------- probes
{
  const p = parseProbe(
    JSON.stringify({
      ok: true,
      parts: 280,
      encrypted: true,
      seconds: 596,
      bestResolution: '1920x1080',
    })
  );
  check('probe parses', p !== null && p.ok === true);
  check('probe keeps the part count', p?.parts === 280);
  check('probe keeps the resolution', p?.bestResolution === '1920x1080');
  check('probe keeps the duration', p?.seconds === 596);
  check('probe keeps encryption', p?.encrypted === true);

  check('an unfinished probe is null, not a guess', parseProbe('') === null);
  check('a garbage probe is null', parseProbe('{{{') === null);
  check('a probe without ok is rejected', parseProbe('{"parts":3}') === null);
}

// The description must come only from real probe data.
{
  check('unfinished probe says it is checking', /checking/i.test(describeProbe(null)));
  const failed = parseProbe(JSON.stringify({ ok: false, reason: 'HTTP 403' }));
  check('a failed probe shows its reason', describeProbe(failed).includes('HTTP 403'));
  const good = parseProbe(
    JSON.stringify({ ok: true, parts: 280, seconds: 596, bestResolution: '1920x1080' })
  );
  const text = describeProbe(good);
  check('the description carries real numbers', text.includes('280') && text.includes('1920x1080'));
  check('the description never invents a size', !/\d+(\.\d+)? ?MB/.test(text));
}

// ------------------------------------------------------- refusal decisions
{
  const drm = parseProbe(JSON.stringify({ ok: true, sampleAes: true }));
  check('SAMPLE-AES is refused before download', streamRefusal(drm) !== null);
  check('the refusal names DRM', /DRM|SAMPLE-AES/i.test(streamRefusal(drm) ?? ''));

  const clean = parseProbe(JSON.stringify({ ok: true, parts: 10 }));
  check('a clean stream is not refused', streamRefusal(clean) === null);

  check('no probe yet is not a refusal', streamRefusal(null) === null);
  check('an unreadable stream IS refused', streamRefusal(parseProbe('{"ok":false,"reason":"x"}')) !== null);
  check('EMPTY_PROBE is not offered as ready', EMPTY_PROBE.ok === false);
}

// -------------------------------------------------------------- formatting
{
  check('kB formatting', formatBytes(2048) === '2 KB');
  check('MB formatting', formatBytes(2 * 1024 * 1024) === '2.0 MB');
  check('GB formatting', formatBytes(3 * 1024 * 1024 * 1024) === '3.00 GB');
  check('zero bytes shows nothing rather than "0 KB"', formatBytes(0) === '');

  check('duration under an hour', formatDuration(220) === '3m 40s');
  check('duration over an hour', formatDuration(4320) === '1h 12m');
  check('unknown duration shows nothing', formatDuration(undefined) === '');
}

// A done job must never be described as still downloading.
{
  const jobs: DownloadJob[] = parseDownloads(
    '{"jobs":[{"id":"a","status":"done","percent":100,"bytes":500}]}'
  );
  const j = jobs[0];
  check('a done job is terminal', j.status === 'done');
  check('a done job keeps its byte count', formatBytes(j.bytes) === '1 KB');
}

console.log('');
if (failures.length) {
  console.error(`download queue: ${failures.length} failure(s)`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(`download queue: ${passed} checks pass — progress shown is progress that happened`);
