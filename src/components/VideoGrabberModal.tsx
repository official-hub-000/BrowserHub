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
    () => items.map((m) => assessMedia(m, page, pageUrl, nativeDownloader)),
    [items, page, pageUrl, nativeDownloader]
  );

  const summary = summarise(items);
  const actionable = assessments.filter((a) => a.actionable);

  const handleDownload = (media: CapturedMedia) => {
    const filename = suggestedFilename(media, pageTitle);
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

          {!scanning && items.length === 0 && (
            <div className="rounded-xl border border-slate-800 bg-[#060608] px-3.5 py-4 text-center">
              <ScanLine size={22} className="mx-auto text-slate-600 mb-2" />
              <div className="text-xs font-semibold text-slate-300">No video on this page yet</div>
              <p className="text-[11px] text-slate-500 leading-relaxed mt-1.5">
                Play the video for a second, then tap scan again. BrowserHub only finds media the page
                has actually requested — it cannot reach into a site it has not loaded.
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

              <p
                className={`text-[11px] leading-relaxed ${
                  a.actionable ? 'text-emerald-200/80' : 'text-amber-200/80'
                }`}
              >
                {a.reason}
              </p>

              {a.actionable && (
                <button
                  onClick={() => handleDownload(a.media)}
                  className={`w-full flex items-center justify-center gap-2 py-2 rounded-lg text-xs font-semibold cursor-pointer ${
                    downloaded[a.media.url]
                      ? 'bg-emerald-500/20 border border-emerald-500/40 text-emerald-300'
                      : 'bg-cyan-500/20 border border-cyan-500/40 text-cyan-200 hover:bg-cyan-500/30'
                  }`}
                >
                  <Download size={14} />
                  {downloaded[a.media.url] ? 'Downloading to Movies/BrowserHub' : 'Download video'}
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

          <div className="rounded-xl border border-slate-800 bg-[#060608] px-3.5 py-3">
            <div className="text-[10px] font-bold text-slate-300 mb-1.5">What this can and cannot do</div>
            <ul className="text-[10px] text-slate-500 leading-relaxed space-y-1">
              <li>Finds video files the page actually requests while you watch.</li>
              <li>Downloads them straight to Movies/BrowserHub, with session cookies.</li>
              <li>Refuses stream playlists, which need segment merging this build lacks.</li>
              <li>Refuses DRM video, which cannot be decrypted by any browser app.</li>
              <li>No ads, no interstitials, no unlock timers, no account.</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};
