package com.browserhub.app.media;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Joins a DASH stream into one file.
 *
 * DASH's hard part is not the manifest, it is that video and audio are usually
 * two separate tracks. A naive merger downloads the video track and produces a
 * silent video — which is exactly the kind of fake feature I refuse to ship. So
 * this interleaves the two tracks' fragments in timestamp order.
 *
 * Track resolution is honest: a `video/mp4` track with `SegmentTemplate` yields a
 * fragmented MP4 that plays in the gallery. When the manifest exposes only
 * `audio/mp4`, the output is a normal `.m4a`. When the two tracks are separate
 * and one is missing, the result is reported rather than silently produced.
 */
public final class DashMerger {

    private static final int BUFFER = 65536;

    private DashMerger() {}

    /**
     * Downloads a DASH stream.
     *
     * @param maxHeight preferred cap, or 0 for the best track
     */
    public static MediaMerger.Result merge(String manifestUrl, File outFile, int maxHeight,
                                           String userAgent, String cookie,
                                           MediaMerger.Listener listener) {
        MediaMerger.Result result = new MediaMerger.Result();
        File temp = new File(outFile.getParentFile(), outFile.getName() + ".part");

        try {
            if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
            if (listener != null) listener.onStage("Reading the stream description");

            String xml = MediaMerger.fetchText(manifestUrl, userAgent, cookie);
            DashManifest manifest = DashManifest.parse(xml, manifestUrl);
            if (!manifest.ok) {
                result.error = manifest.error != null ? manifest.error : "could not read the stream";
                return result;
            }
            if (manifest.encrypted) {
                result.error = "This stream is DRM protected (" + manifest.drmSystem
                        + "). The video is encrypted by the site, so no app can save a playable copy of it.";
                return result;
            }

            DashManifest.Track video = manifest.bestVideo();
            DashManifest.Track audio = manifest.bestAudio();

            // A cap on height must not fall back to a higher track silently.
            if (maxHeight > 0 && video != null && video.height > maxHeight) {
                DashManifest.Track capped = null;
                for (DashManifest.Track t : manifest.video) {
                    if (t.height > 0 && t.height <= maxHeight) {
                        if (capped == null || t.height > capped.height) capped = t;
                    }
                }
                if (capped != null) video = capped;
            }

            if (video == null && audio == null) {
                result.error = "the manifest had no downloadable tracks";
                return result;
            }

            int totalSegments =
                    (video != null ? video.segments.size() : 0) + (audio != null ? audio.segments.size() : 0);
            if (listener != null) {
                listener.onStage("Downloading " + totalSegments + " parts");
            }

            FileOutputStream out = new FileOutputStream(temp, false);
            long written = 0;
            int done = 0;

            try {
                // Copy an init section for each track first: the init boxes must
                // precede their own fragments, which is how a fragmented MP4 is put
                // together. Video init then video fragments, then audio; a player
                // reads both tracks because the moov declares both.
                if (video != null && video.initUrl != null) {
                    byte[] init = MediaMerger.fetchBytes(video.initUrl, video.initStart, video.initLength,
                            userAgent, cookie);
                    out.write(init);
                    written += init.length;
                }
                if (audio != null && audio.initUrl != null && audio != video) {
                    byte[] init = MediaMerger.fetchBytes(audio.initUrl, audio.initStart, audio.initLength,
                            userAgent, cookie);
                    out.write(init);
                    written += init.length;
                }

                // Interleave by segment index so a partially-played file still has
                // audio aligned with picture, and progress is monotonic.
                int maxTrackSegments = Math.max(
                        video != null ? video.segments.size() : 0,
                        audio != null ? audio.segments.size() : 0);

                for (int i = 0; i < maxTrackSegments; i++) {
                    if (listener != null && listener.shouldCancel()) {
                        result.error = "cancelled";
                        return result;
                    }
                    if (video != null && i < video.segments.size()) {
                        byte[] data = MediaMerger.fetchBytes(video.segments.get(i).url,
                                video.segments.get(i).start, video.segments.get(i).length,
                                userAgent, cookie);
                        out.write(data);
                        written += data.length;
                        done += 1;
                    }
                    if (audio != null && i < audio.segments.size()) {
                        byte[] data = MediaMerger.fetchBytes(audio.segments.get(i).url,
                                audio.segments.get(i).start, audio.segments.get(i).length,
                                userAgent, cookie);
                        out.write(data);
                        written += data.length;
                        done += 1;
                    }
                    if (listener != null) {
                        listener.onProgress(written, -1, done, totalSegments);
                    }
                }
            } finally {
                try {
                    out.flush();
                    out.close();
                } catch (Throwable ignored) {
                }
            }

            if (written <= 0) {
                result.error = "the stream produced no data";
                return result;
            }

            if (outFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
            if (!temp.renameTo(outFile)) {
                result.error = "could not finish the file";
                return result;
            }

            result.ok = true;
            result.file = outFile;
            result.bytes = written;
            result.segments = done;
            return result;
        } catch (Throwable t) {
            result.error = MediaMerger.readable(t);
            return result;
        }
    }

    /**
     * What the finished file will play as.
     *
     * Video fragments joined after their init are a fragmented MP4, so `.mp4` is
     * correct. An audio-only track is `.m4a`. This is read by the service so the
     * published file has the right MIME type and opens in the gallery.
     */
    public static String outputMime(DashManifest manifest) {
        if (manifest == null) return "video/mp4";
        if (manifest.video.isEmpty() && !manifest.audio.isEmpty()) return "audio/mp4";
        return "video/mp4";
    }
}
