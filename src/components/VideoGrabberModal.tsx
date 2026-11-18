import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { X, Download, Film, RefreshCw, AlertTriangle, ShieldAlert, ScanLine } from 'lucide-react';
import { AndroidHybridBridge } from '../bridge/androidHybridBridge';
import {
  assessMedia,
  parseCaptures,
  suggestedFilename,
  summarise,
  type CapturedMedia,
  type MediaAssessment,
  type PageConstraints,
} from '../utils/videoCapture';
import {
  EMPTY_PROBE,
  describeProbe,
  formatBytes,
  formatDuration,
  isHomePage,
  isYouTubeWatchUrl,
  normaliseVideoUrl,
  parseDownloads,
  parseProbe,
  streamRefusal,
  type DownloadJob,
  type StreamProbe,
} from '../utils/downloadQueue';

interface VideoGrabberModalProps {
  pageUrl: string;
  pageTitle: string;
  onClose: () => void;
}

/**
 * The video downloader sheet.
 *
 * It lists what the page is *actually* playing — captured from the page's own
 * network requests — and then tells the truth about each one: a direct file is
 * offered with a download button, while a stream playlist or DRM-protected video
 * is explained instead of being offered and failing later.
 *
 * There is no ad anywhere in this path. No interstitial, no "watch to unlock",
 * no rewarded prompt, no upsell. That is a code property, not a promise: there is
 * no ad SDK in this app and `claims-check` enforces that.
 */
export const VideoGrabberModal: React.FC<VideoGrabberModalProps> = ({
  pageUrl,
  pageTitle,
  onClose,
}) => {
  const [items, setItems] = useState<CapturedMedia[]>([]);
  const [page, setPage] = useState<PageConstraints>({
    usesMse: false,
    drm: false,
    crossoriginBlocked: false,
  });
  const [scanning, setScanning] = useState(true);
  const [hasNative, setHasNative] = useState(false);
  const [downloaded, setDownloaded] = useState<Record<string, boolean>>({});
  const [probes, setProbes] = useState<Record<string, StreamProbe | null>>({});
  const [jobs, setJobs] = useState<DownloadJob[]>([]);
  /*
   * A link pasted by hand.
   *
   * This is how the app being compared to works, and it is why it downloads from
   * every page: it does not care what the browser is showing, only what link you
   * give it. Without this, opening the sheet on a homepage — or on any site that
   * is not currently playing the video you want — finds nothing, correctly, and
   * the feature looks broken.
   */
  const [link, setLink] = useState('');
  const [linkProbe, setLinkProbe] = useState<StreamProbe | null>(null);
  const [linkBusy, setLinkBusy] = useState(false);
  const [linkSaved, setLinkSaved] = useState(false);
  const [youtubeProbe, setYoutubeProbe] = useState<StreamProbe | null>(null);
  const [youtubeStarted, setYoutubeStarted] = useState(false);

  /**
   * YouTube is handled separately.
   *
   * On a watch page the media is not a network request the page makes — it is
   * inside the player's JSON — so waiting for a captured URL shows "nothing
   * found" on a video that plays perfectly. The page itself is the signal.
   */
  const onYouTube = isYouTubeWatchUrl(pageUrl);

  useEffect(() => {
    if (!onYouTube || !AndroidHybridBridge.hasJniBridge()) return;
    AndroidHybridBridge.probeStream(pageUrl);
    const timer = window.setInterval(() => {
      const parsed = parseProbe(AndroidHybridBridge.getStreamProbe());
      if (parsed) {
        setYoutubeProbe(parsed);
        window.clearInterval(timer);
      }
    }, 400);
    const stop = window.setTimeout(() => window.clearInterval(timer), 25000);
    return () => {
      window.clearInterval(timer);
      window.clearTimeout(stop);
    };
  }, [onYouTube, pageUrl]);

  /**
   * What the stream actually is, asked before the user commits.
   *
   * A playlist can be 4 parts or 4000, can be AES-128 (fine) or SAMPLE-AES (DRM,
   * impossible). Showing the real numbers and refusing the impossible ones up
   * front is the difference between a download and a trick.
   */
  useEffect(() => {
    const streams = items.filter((i) => i.kind === 'hls' || i.kind === 'dash');
    if (!streams.length || !AndroidHybridBridge.hasJniBridge()) return;
    for (const stream of streams) {
      AndroidHybridBridge.probeStream(stream.url);
    }
    const timer = window.setInterval(() => {
      const raw = AndroidHybridBridge.getStreamProbe();
      const parsed = parseProbe(raw);
      if (parsed) {
        setProbes((prev) => {
          const next = { ...prev };
          for (const stream of streams) {
            if (!next[stream.url]) next[stream.url] = parsed;
          }
          return next;
        });
        AndroidHybridBridge.clearFinishedDownloads();
        window.clearInterval(timer);
      }
    }, 400);
    const stop = window.setTimeout(() => window.clearInterval(timer), 20000);
    return () => {
      window.clearInterval(timer);
      window.clearTimeout(stop);
    };
  }, [items]);

  /** Poll the engine while anything is moving, and stop when nothing is. */
  useEffect(() => {
    if (!AndroidHybridBridge.hasJniBridge()) return;
    let active = true;
    const tick = () => {
      if (!active) return;
      const parsed = parseDownloads(AndroidHybridBridge.getDownloads());
      setJobs(parsed);
    };
    tick();
    const timer = window.setInterval(tick, 700);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const nativeDownloader = useMemo(() => AndroidHybridBridge.hasNativeDownloader(), []);

  const scan = useCallback(() => {
    const raw = AndroidHybridBridge.collectPageMedia();
    const parsed = parseCaptures(raw);
    setItems(parsed.items);
    setPage(parsed.page);
    setScanning(false);
  }, []);

  useEffect(() => {
    setHasNative(AndroidHybridBridge.hasJniBridge());
    // Paint whatever was captured during playback immediately, then re-scan.
    const cached = parseCaptures(AndroidHybridBridge.getPageMedia());
    if (cached.items.length) {
      setItems(cached.items);
      setPage(cached.page);
      setScanning(false);
    }
    const timer = window.setTimeout(scan, 60);
    return () => window.clearTimeout(timer);
  }, [scan]);

  const assessments: MediaAssessment[] = useMemo(
    () =>
      items.map((m) =>
        assessMedia(m, page, pageUrl, nativeDownloader, { canMergeStreams: true })
      ),
    [items, page, pageUrl, nativeDownloader]
  );

  const summary = summarise(items);
  const actionable = assessments.filter((a) => a.actionable);

  const handleDownload = (media: CapturedMedia) => {
    const filename = suggestedFilename(media, pageTitle);
    // A stream goes through the merger; a direct file streams straight to disk.
    if (media.kind === 'hls' || media.kind === 'dash') {
      const id = AndroidHybridBridge.startDownload(media.url, filename, media.kind, pageTitle);
      if (id) setDownloaded((prev) => ({ ...prev, [media.url]: true }));
      return;
    }
    const started = AndroidHybridBridge.downloadPageMedia(media.url, filename);
    if (started) {
      setDownloaded((prev) => ({ ...prev, [media.url]: true }));
    }
  };

  const formatSize = (bytes: number) => {
    if (!bytes) return 'size unknown';
    if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  };

  return (
    <div
      className="bh-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
      style={{ backgroundColor: 'rgba(0, 0, 0, 0.88)' }}
      onClick={onClose}
    >
      <div
        className="bh-sheet w-full max-w-md rounded-t-3xl sm:rounded-3xl border border-slate-800 shadow-2xl overflow-hidden flex flex-col max-h-[82vh]"
        style={{ backgroundColor: '#000000' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-5 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-cyan-500/20 border border-cyan-500/40 text-cyan-300 flex items-center justify-center">
              <Film size={18} />
            </div>
            <div>
              <div className="text-sm font-bold text-white">Video Grabber</div>
              <div className="text-[11px] text-slate-400">
                {scanning
                  ? 'Looking at what this page is playing…'
                  : summary.count
                    ? `${summary.count} media item${summary.count === 1 ? '' : 's'} found`
                    : 'Nothing playable found yet'}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <button
              aria-label="Scan again"
              title="Scan again"
              onClick={() => {
                setScanning(true);
                window.setTimeout(scan, 40);
              }}
              className="p-2 rounded-xl hover:bg-[#121216] text-slate-300 cursor-pointer"
            >
              <RefreshCw size={16} className={scanning ? 'animate-spin' : ''} />
            </button>
            <button
              aria-label="Close"
              title="Close"
              onClick={onClose}
              className="p-2 rounded-xl hover:bg-[#121216] text-slate-300 cursor-pointer"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="p-4 space-y-3 overflow-y-auto">
          {!hasNative && (
            <div className="rounded-xl border border-amber-500/40 bg-amber-500/10 px-3.5 py-3 flex gap-2.5">
              <AlertTriangle size={16} className="text-amber-400 shrink-0 mt-0.5" />
              <div className="text-[11px] leading-relaxed text-amber-200/90">
                <span className="font-semibold text-amber-200">Downloads need the Android app.</span>{' '}
                This is the web preview, where the browser's cross-origin rules stop the file from
                being read. Install the APK and this sheet downloads for real.
              </div>
            </div>
          )}

          {/*
            Paste a link. Always visible, on every page, because the video you want
            is often not the page you are on.
          */}
          {hasNative && (
            <div className="rounded-xl border border-slate-800 bg-[#060608] p-3 space-y-2">
              <div className="text-[11px] font-semibold text-slate-300">Download from a link</div>
              <div className="flex gap-2">
                <input
                  value={link}
                  onChange={(e) => {
                    setLink(e.target.value);
                    setLinkProbe(null);
                    setLinkSaved(false);
                  }}
                  placeholder="Paste a video link"
                  inputMode="url"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  className="flex-1 min-w-0 bg-[#0b0b0f] border border-slate-800 rounded-lg px-2.5 py-2 text-[11px] text-white placeholder:text-slate-600 outline-none focus:border-cyan-500/50"
                />
                <button
                  onClick={() => {
                    const trimmed = link.trim();
                    if (!trimmed) return;
                    const url = normaliseVideoUrl(trimmed);
                    if (!url) {
                      setLinkProbe({
                        ok: false,
                        reason: 'That does not look like a link. Paste the address of a video page.',
                      });
                      return;
                    }
                    setLinkBusy(true);
                    setLinkProbe(null);
                    setLinkSaved(false);
                    AndroidHybridBridge.probeStream(url);
                    let tries = 0;
                    const timer = window.setInterval(() => {
                      tries += 1;
                      const parsed = parseProbe(AndroidHybridBridge.getStreamProbe());
                      if (parsed) {
                        window.clearInterval(timer);
                        setLinkBusy(false);
                        setLinkProbe(parsed);
                      } else if (tries > 60) {
                        window.clearInterval(timer);
                        setLinkBusy(false);
                        setLinkProbe({ ok: false, reason: 'The link did not answer. Check it and try again.' });
                      }
                    }, 500);
                  }}
                  className="px-3 py-2 rounded-lg bg-cyan-500/20 border border-cyan-500/40 text-cyan-200 text-[11px] font-semibold cursor-pointer disabled:opacity-40 shrink-0"
                  disabled={linkBusy || !link.trim()}
                >
                  {linkBusy ? 'Checking…' : 'Check'}
                </button>
              </div>

              {linkProbe && (
                <p
                  className={`text-[11px] leading-relaxed ${
                    linkProbe.ok ? 'text-cyan-200/80' : 'text-amber-200/80'
                  }`}
                >
                  {linkProbe.ok ? describeProbe(linkProbe) : linkProbe.reason}
                </p>
              )}

              {linkProbe?.ok && (
                <button
                  onClick={() => {
                    const url =
                      linkProbe.direct?.trim() || normaliseVideoUrl(link.trim());
                    if (!url) return;
                    const title = linkProbe.title?.trim() || 'video';
                    const safe = title.replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim();
                    const kind = linkProbe.kind === 'youtube' ? 'youtube' : linkProbe.kind || 'file';
                    const id = AndroidHybridBridge.startDownload(url, `${safe || 'video'}.mp4`, kind, title);
                    if (id) setLinkSaved(true);
                  }}
                  className={`w-full flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold cursor-pointer ${
                    linkSaved
                      ? 'bg-emerald-500/20 border border-emerald-500/40 text-emerald-300'
                      : 'bg-cyan-500/20 border border-cyan-500/40 text-cyan-200 hover:bg-cyan-500/30'
                  }`}
                >
                  <Download size={14} />
                  {linkSaved ? 'Saved to your downloads' : 'Download this video'}
                </button>
              )}
            </div>
          )}

          {hasNative && page.drm && (
            <div className="rounded-xl border border-rose-500/40 bg-rose-500/10 px-3.5 py-3 flex gap-2.5">
              <ShieldAlert size={16} className="text-rose-400 shrink-0 mt-0.5" />
              <div className="text-[11px] leading-relaxed text-rose-200/90">
                <span className="font-semibold text-rose-200">This page uses DRM.</span> The video is
                encrypted on purpose, so no download can produce a playable file. BrowserHub will not
                offer a broken one.
              </div>
            </div>
          )}

          {/*
            The YouTube card. It is shown whenever the page is a watch page,
            independent of what the network capture found, because on YouTube the
            capture finds nothing by design — the media URL lives in the player's
            own JSON, not in a request the page makes.
          */}
          {onYouTube && (
            <div className="rounded-xl border border-cyan-500/40 bg-cyan-500/5 p-3 space-y-2">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="text-xs font-semibold text-white truncate">
                    {youtubeProbe?.title?.trim() || 'YouTube video'}
                  </div>
                  <div className="text-[10px] text-slate-500 font-mono truncate">
                    {youtubeProbe?.ok
                      ? [
                          youtubeProbe.bestResolution,
                          youtubeProbe.seconds ? formatDuration(youtubeProbe.seconds) : '',
                          youtubeProbe.track,
                        ]
                          .filter(Boolean)
                          .join(' · ')
                      : 'reading the video'}
                  </div>
                </div>
              </div>

              <p
                className={`text-[11px] leading-relaxed ${
                  youtubeProbe && !youtubeProbe.ok ? 'text-amber-200/80' : 'text-cyan-200/80'
                }`}
              >
                {youtubeProbe && !youtubeProbe.ok
                  ? (youtubeProbe.reason ?? 'This video could not be read.')
                  : describeProbe(youtubeProbe)}
              </p>

              {(!youtubeProbe || youtubeProbe.ok) && (
                <button
                  onClick={() => {
                    const title = youtubeProbe?.title?.trim() || pageTitle || 'YouTube video';
                    const safe = title.replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim();
                    const id = AndroidHybridBridge.startDownload(
                      pageUrl,
                      `${safe || 'video'}.mp4`,
                      'youtube',
                      title
                    );
                    if (id) {
                      setYoutubeStarted(true);
                      setDownloaded((prev) => ({ ...prev, [pageUrl]: true }));
                    }
                  }}
                  className={`w-full flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold cursor-pointer ${
                    youtubeStarted
                      ? 'bg-emerald-500/20 border border-emerald-500/40 text-emerald-300'
                      : 'bg-cyan-500/20 border border-cyan-500/40 text-cyan-200 hover:bg-cyan-500/30'
                  }`}
                >
                  <Download size={14} />
                  {youtubeStarted ? 'Saved to your downloads' : 'Download this video'}
                </button>
              )}
            </div>
          )}

          {!scanning && items.length === 0 && !onYouTube && (
            <div className="rounded-xl border border-slate-800 bg-[#060608] px-3.5 py-4 text-center">
              <ScanLine size={22} className="mx-auto text-slate-600 mb-2" />
              <div className="text-xs font-semibold text-slate-300">
                {isHomePage(pageUrl) ? 'Open a video first' : 'No video on this page yet'}
              </div>
              <p className="text-[11px] text-slate-500 leading-relaxed mt-1.5">
                {isHomePage(pageUrl)
                  ? 'This is a site front page, so there is no video to grab yet. Open the video you want, or paste its link above.'
                  : 'Play the video for a second, then tap scan again. BrowserHub finds media the page has actually requested — or paste the video link above to skip that entirely.'}
              </p>
            </div>
          )}

          {assessments.map((a, index) => (
            <div
              key={`${a.media.url}-${index}`}
              className="rounded-xl border border-slate-800 bg-[#060608] p-3 space-y-2"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="text-xs font-semibold text-white truncate">
                    {a.media.label}
                    {a.media.kind !== 'file' && (
                      <span className="ml-1.5 text-[9px] px-1.5 py-0.5 rounded border border-slate-700 bg-[#121216] text-slate-300 font-bold align-middle">
                        {a.media.kind === 'hls' ? 'HLS' : 'DASH'}
                      </span>
                    )}
                  </div>
                  <div className="text-[10px] text-slate-500 font-mono truncate">
                    {formatSize(a.media.bytes)} · {a.media.signed ? 'expiring link' : 'direct link'}
                  </div>
                </div>
              </div>

              {a.verdict === 'mergeable' ? (
                <p className="text-[11px] leading-relaxed text-cyan-200/80">
                  {streamRefusal(probes[a.media.url] ?? null) ?? describeProbe(probes[a.media.url] ?? null)}
                </p>
              ) : (
                <p
                  className={`text-[11px] leading-relaxed ${
                    a.actionable ? 'text-emerald-200/80' : 'text-amber-200/80'
                  }`}
                >
                  {a.reason}
                </p>
              )}

              {a.verdict === 'mergeable' && streamRefusal(probes[a.media.url] ?? null) && (
                <p className="text-[10px] leading-relaxed text-rose-200/80">
                  No download is offered for this one. A file that will not play is not a download.
                </p>
              )}

              {a.actionable && !(a.verdict === 'mergeable' && streamRefusal(probes[a.media.url] ?? null)) && (
                <button
                  onClick={() => handleDownload(a.media)}
                  className={`w-full flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold cursor-pointer ${
                    downloaded[a.media.url]
                      ? 'bg-emerald-500/20 border border-emerald-500/40 text-emerald-300'
                      : 'bg-cyan-500/20 border border-cyan-500/40 text-cyan-200 hover:bg-cyan-500/30'
                  }`}
                >
                  <Download size={14} />
                  {downloaded[a.media.url]
                    ? 'Saved to your downloads'
                    : a.verdict === 'mergeable'
                      ? 'Download full video'
                      : 'Download video'}
                </button>
              )}
            </div>
          ))}

          {actionable.length === 0 && items.length > 0 && (
            <p className="text-[10px] text-slate-500 leading-relaxed px-1">
              Nothing on this page can be saved as a working file right now. A download that produced
              a broken video would be worse than this message.
            </p>
          )}

          {jobs.length > 0 && (
            <div className="rounded-xl border border-slate-800 bg-[#060608] p-3 space-y-2">
              <div className="flex items-center justify-between">
                <div className="text-[10px] font-bold text-slate-300">Downloads</div>
                <button
                  onClick={() => {
                    AndroidHybridBridge.clearFinishedDownloads();
                    setJobs((prev) =>
                      prev.filter((j) => j.status === 'running' || j.status === 'queued')
                    );
                  }}
                  className="text-[10px] text-slate-500 hover:text-slate-300 cursor-pointer"
                >
                  Clear finished
                </button>
              </div>
              {jobs.map((job) => (
                <div key={job.id} className="space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <div className="text-[11px] text-white truncate">{job.filename}</div>
                    <div className="text-[10px] shrink-0 font-mono">
                      {job.status === 'done' && (
                        <span className="text-emerald-400">
                          done{formatBytes(job.bytes) ? ` · ${formatBytes(job.bytes)}` : ''}
                        </span>
                      )}
                      {job.status === 'failed' && <span className="text-rose-400">failed</span>}
                      {job.status === 'cancelled' && <span className="text-slate-500">cancelled</span>}
                      {job.status === 'running' && (
                        <span className="text-cyan-300">
                          {job.percent}%{formatBytes(job.bytes) ? ` · ${formatBytes(job.bytes)}` : ''}
                        </span>
                      )}
                      {job.status === 'queued' && <span className="text-slate-400">queued</span>}
                    </div>
                  </div>
                  {(job.status === 'running' || job.status === 'queued') && (
                    <>
                      <div className="h-1 rounded-full bg-[#121216] overflow-hidden">
                        <div
                          className="h-full bg-cyan-400"
                          style={{ width: `${Math.max(2, job.percent)}%` }}
                        />
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-[10px] text-slate-500">{job.message}</span>
                        <button
                          onClick={() => {
                            AndroidHybridBridge.cancelDownload(job.id);
                          }}
                          className="text-[10px] text-rose-300 hover:text-rose-200 cursor-pointer"
                        >
                          Cancel
                        </button>
                      </div>
                    </>
                  )}
                  {job.status === 'failed' && job.message && (
                    <div className="text-[10px] text-rose-200/80 leading-relaxed">{job.message}</div>
                  )}
                </div>
              ))}
            </div>
          )}

          <div className="rounded-xl border border-slate-800 bg-[#060608] px-3.5 py-3">
            <div className="text-[10px] font-bold text-slate-300 mb-1.5">What this can and cannot do</div>
            <ul className="text-[10px] text-slate-500 leading-relaxed space-y-1">
              <li>Finds video files the page actually requests while you watch.</li>
              <li>Reads YouTube watch pages directly, joining picture and sound.</li>
              <li>Joins HLS and DASH streams into one file, picture and sound together.</li>
              <li>Decrypts AES-128 HLS streams while joining them.</li>
              <li>Keeps downloading in the background, with progress and cancel.</li>
              <li>Refuses DRM (Widevine/PlayReady) video, which is encrypted by the site.</li>
              <li>No ads, no interstitials, no unlock timers, no account.</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};
