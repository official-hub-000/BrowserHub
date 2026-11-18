/**
 * The download queue as the shell sees it.
 *
 * The engine lives natively because merging a stream is minutes of work that has
 * to survive the app going to the background. This module only parses the
 * engine's JSON and turns it into something the UI can trust: a job list with a
 * real percentage, a real byte count, and a status that never says "downloading"
 * when nothing is moving.
 */

export type DownloadStatus = 'queued' | 'running' | 'done' | 'failed' | 'cancelled';

export interface DownloadJob {
  id: string;
  url: string;
  filename: string;
  kind: string;
  title: string;
  status: DownloadStatus;
  percent: number;
  bytes: number;
  totalBytes: number;
  message: string;
  resumable: boolean;
}

/** A stream probe: what the playlist actually is, before committing to it. */
export interface StreamProbe {
  ok: boolean;
  reason?: string;
  parts?: number;
  encrypted?: boolean;
  sampleAes?: boolean;
  seconds?: number;
  bestResolution?: string;
  /** Which engine answered: a stream playlist, or YouTube's player data. */
  kind?: string;
  /** The real video title, when the source reported one. */
  title?: string;
  /** "one file" or "picture + sound" — what has to be joined. */
  track?: string;
  /**
   * A media address found inside a page, when the link was a page rather than a
   * file. This is what gets downloaded, not the page address itself.
   */
  direct?: string;
}

export const EMPTY_PROBE: StreamProbe = { ok: false };

/**
 * Parses the engine's queue payload. Returns `[]` for anything malformed so a
 * bridge hiccup shows an empty list rather than crashing the sheet.
 */
export function parseDownloads(raw: string): DownloadJob[] {
  if (!raw) return [];
  let payload: any;
  try {
    payload = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!payload || !Array.isArray(payload.jobs)) return [];

  const jobs: DownloadJob[] = [];
  for (const j of payload.jobs) {
    if (!j || typeof j.id !== 'string') continue;
    const status = String(j.status ?? 'queued') as DownloadStatus;
    jobs.push({
      id: j.id,
      url: String(j.url ?? ''),
      filename: String(j.filename ?? 'video'),
      kind: String(j.kind ?? 'file'),
      title: String(j.title ?? ''),
      status: ['queued', 'running', 'done', 'failed', 'cancelled'].includes(status)
        ? status
        : 'queued',
      percent:
        typeof j.percent === 'number' && isFinite(j.percent)
          ? Math.max(0, Math.min(100, Math.round(j.percent)))
          : 0,
      bytes: typeof j.bytes === 'number' ? j.bytes : 0,
      totalBytes: typeof j.totalBytes === 'number' ? j.totalBytes : -1,
      message: String(j.message ?? ''),
      resumable: Boolean(j.resumable),
    });
  }
  return jobs;
}

/** Parses a probe reply; an unfinished probe (`""`) stays `null`. */
export function parseProbe(raw: string): StreamProbe | null {
  if (!raw) return null;
  try {
    const p = JSON.parse(raw);
    if (!p || typeof p.ok !== 'boolean') return null;
    return {
      ok: p.ok,
      reason: typeof p.reason === 'string' ? p.reason : undefined,
      parts: typeof p.parts === 'number' ? p.parts : undefined,
      encrypted: Boolean(p.encrypted),
      sampleAes: Boolean(p.sampleAes),
      seconds: typeof p.seconds === 'number' ? p.seconds : undefined,
      bestResolution: typeof p.bestResolution === 'string' ? p.bestResolution : undefined,
      kind: typeof p.kind === 'string' ? p.kind : undefined,
      title: typeof p.title === 'string' ? p.title : undefined,
      track: typeof p.track === 'string' ? p.track : undefined,
      direct: typeof p.direct === 'string' && p.direct.length > 0 ? p.direct : undefined,
    };
  } catch {
    return null;
  }
}

/** Human size, or "" when the engine has not reported one yet. */
export function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '';
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/** A duration like "1h 12m" or "3m 40s", for a probed stream. */
export function formatDuration(seconds?: number): string {
  if (!seconds || seconds <= 0) return '';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.round(seconds % 60);
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

/**
 * Whether a job should keep being polled. Done, failed and cancelled jobs are
 * terminal, so the UI can stop asking about them rather than polling forever.
 */
export function hasLiveJobs(jobs: DownloadJob[]): boolean {
  return jobs.some((j) => j.status === 'queued' || j.status === 'running');
}

/**
 * One-line truth for a stream, built only from real probe data. When the probe
 * has not finished it says so instead of guessing at quality.
 */
export function describeProbe(probe: StreamProbe | null): string {
  if (!probe) return 'Checking the stream…';
  if (!probe.ok) return probe.reason ?? 'This stream could not be read.';
  if (probe.sampleAes) {
    return 'DRM-protected stream. It cannot be decrypted in an app, so it is not offered.';
  }
  const bits: string[] = [];
  if (probe.bestResolution) bits.push(probe.bestResolution);
  if (probe.parts) bits.push(`${probe.parts} parts`);
  if (probe.seconds) bits.push(formatDuration(probe.seconds));
  if (probe.encrypted) bits.push('AES-128 encrypted');
  if (probe.kind === 'youtube' && probe.track) bits.push(probe.track);
  return bits.length ? `Ready to download: ${bits.join(' · ')}` : 'Ready to download.';
}

/**
 * True when this URL is a YouTube watch page.
 *
 * YouTube gets its own path because the video is not a request the page makes —
 * it arrives inside the player's JSON, so a "grab what the page requested"
 * approach finds nothing on a perfectly working page.
 */
/**
 * Turns whatever was pasted into a real video page address.
 *
 * People paste all of these, and all of them mean the same video:
 *
 *   dQw4w9WgXcQ                        a bare id, copied from a share sheet
 *   youtu.be/dQw4w9WgXcQ               the short link
 *   m.youtube.com/watch?v=dQw4w9WgXcQ  what a phone actually copies
 *   https://youtube.com/shorts/...     a short
 *   a link with &t=42s, &si=…, #t=42   tracking and timestamps bolted on
 *
 * Returning "" means "this is not a video link", which the sheet reports rather
 * than fetching something arbitrary.
 */
export function normaliseVideoUrl(raw: string): string {
  const input = (raw ?? '').trim();
  if (!input) return '';

  // A bare 11-character id is the most common paste from a phone share sheet.
  if (/^[A-Za-z0-9_-]{11}$/.test(input)) {
    return `https://www.youtube.com/watch?v=${input}`;
  }

  let url = input;
  if (!/^https?:\/\//i.test(url)) {
    // "youtu.be/x" and "m.youtube.com/watch?v=x" arrive without a scheme.
    if (/^(www\.|m\.)?(youtube\.com|youtu\.be|youtube-nocookie\.com)\//i.test(url)) {
      url = `https://${url}`;
    } else {
      return '';
    }
  }

  // Strip tracking parameters and timestamps, which are noise to the resolver.
  url = url.replace(/([?&])(si|pp|feature|t|start|end|ab_channel)=[^&]*/gi, '$1');
  url = url.replace(/[?&]+$/, '');
  url = url.replace(/#.*$/, '');

  return url;
}

/**
 * True for a site's front page rather than a page about something.
 *
 * Worth distinguishing, because "no video on this page" is a confusing thing to
 * read on youtube.com: the app is being honest, but the useful sentence is "you
 * are on the homepage". A path with no segments is the reliable signal; a query
 * string only counts when it selects content, which `watch?v=` does.
 */
export function isHomePage(raw: string): boolean {
  const url = (raw ?? '').trim();
  if (!url) return true;
  const match = /^https?:\/\/[^/]+(\/[^?#]*)?(\?[^#]*)?/i.exec(url);
  if (!match) return true;
  const path = (match[1] ?? '').replace(/\/+$/, '');
  if (path.length > 1) return false;
  const query = match[2] ?? '';
  // A query that names a video means this is a watch page, not a front page.
  return !/[?&](v|video_id|watch)=/i.test(query);
}

export function isYouTubeWatchUrl(url: string): boolean {
  if (!url) return false;
  const lower = url.toLowerCase();
  if (lower.includes('/embed')) return false;
  return (
    lower.includes('youtube.com/watch') ||
    lower.includes('youtu.be/') ||
    lower.includes('m.youtube.com/watch') ||
    lower.includes('youtube.com/shorts/')
  );
}

/** Why a stream must not be offered, or null when it can be. */
export function streamRefusal(probe: StreamProbe | null): string | null {
  if (!probe) return null;
  if (!probe.ok) return probe.reason ?? 'This stream could not be read.';
  if (probe.sampleAes) {
    return 'This stream is DRM-protected (SAMPLE-AES). No app-side download can decrypt it.';
  }
  return null;
}
