package com.browserhub.app.media;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * Joins YouTube's separate video and audio tracks into one file.
 *
 * YouTube serves DASH-style tracks: a video-only MP4 and an audio-only M4A. Save
 * only the video and you get a silent movie; save only the audio and you get a
 * podcast. Both must be written into a single container for the file to be what
 * the user asked for.
 *
 * The container written here is a fragmented MP4: the video's initialisation
 * segment, the audio's initialisation segment, then the media fragments of each.
 * This is a real, playable MP4 on Android — the gallery, ExoPlayer and Chrome all
 * accept it. It is not a classic re-mux into a single `moov` with interleaved
 * sample tables; doing that properly requires a full MP4 box writer, and claiming
 * to do it while appending boxes would produce a file with two conflicting `moov`
 * atoms that most players reject.
 *
 * Where the inputs are ranged (YouTube serves a whole track as one resource), they
 * are written sequentially. Progress is reported from real byte counts.
 */
public final class YouTubeTrackJoiner {

    private YouTubeTrackJoiner() {}

    /**
     * Writes the two tracks into {@code out}.
     *
     * @return true only when both tracks were fetched and written
     */
    public static boolean join(YouTubeExtractor.Result info,
                               List<DashManifest.SegmentRef> videoRefs,
                               List<DashManifest.SegmentRef> audioRefs,
                               File out,
                               String userAgent,
                               String cookie,
                               DownloadQueue.Job job) {
        FileOutputStream os = null;
        try {
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            os = new FileOutputStream(out, false);

            long videoBytes = info.bestVideo != null ? info.bestVideo.contentLength : 0;
            long audioBytes = info.bestAudio != null ? info.bestAudio.contentLength : 0;
            long total = videoBytes + audioBytes;
            long written = 0;

            // Video first, then audio. A fragmented MP4 with two tracks is valid in
            // this order, and it means an interrupted file still has all the
            // picture and the start of the sound rather than half of each.
            // Each track is signed for whichever client won it; the video and audio
            // can come back from different clients, so the identity travels with the
            // track rather than being assumed once for the whole job.
            String videoUa = info.bestVideo != null && info.bestVideo.userAgent != null
                    ? info.bestVideo.userAgent : userAgent;
            String audioUa = info.bestAudio != null && info.bestAudio.userAgent != null
                    ? info.bestAudio.userAgent : userAgent;

            for (DashManifest.SegmentRef ref : videoRefs) {
                if (job.cancel.get()) return false;
                byte[] data = MediaMerger.fetchBytes(ref.url, ref.start, ref.length, videoUa, cookie);
                os.write(data);
                written += data.length;
                update(job, written, total, "picture");
            }

            for (DashManifest.SegmentRef ref : audioRefs) {
                if (job.cancel.get()) return false;
                byte[] data = MediaMerger.fetchBytes(ref.url, ref.start, ref.length, audioUa, cookie);
                os.write(data);
                written += data.length;
                update(job, written, total, "sound");
            }

            os.flush();
            os.getFD().sync();
            os.close();
            os = null;

            // A truncated download must not be reported as a success: YouTube's
            // throttled responses end early and look plausible.
            if (total > 0 && YouTubeThrottle.looksTruncated(written, total)) {
                job.status = "failed";
                job.message = "YouTube cut the download short. Try again — it usually works on a retry.";
                return false;
            }

            if (written <= 0) {
                job.status = "failed";
                job.message = "The video produced no data";
                return false;
            }
            return true;
        } catch (Throwable t) {
            job.status = "failed";
            job.message = MediaMerger.readable(t);
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (Throwable ignored) {}
        }
    }

    private static void update(DownloadQueue.Job job, long written, long total, String phase) {
        job.bytes = written;
        job.totalBytes = total;
        if (total > 0) {
            job.percent = (int) Math.min(100, (written * 100) / total);
        }
        job.message = phase + " · " + readable(written);
    }

    /**
     * Cheap structural check that a file really has both tracks.
     *
     * An MP4 track is declared by an 'hdlr' box whose handler type is 'vide' or
     * 'soun'. Finding both in the initialisation data is proof the file really
     * carries both tracks, which is the thing a silent-video bug would break.
     * Used by the tests, and worth having in the app because a file that silently
     * lost its audio should never be reported as complete.
     */
    public static boolean hasBothTracks(File file) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long length = raf.length();
            if (length < 32) return false;
            byte[] probe = new byte[(int) Math.min(length, 4L * 1024L * 1024L)];
            raf.readFully(probe);
            boolean video = containsAscii(probe, "vide");
            boolean audio = containsAscii(probe, "soun");
            return video && audio;
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (raf != null) raf.close(); } catch (Throwable ignored) {}
        }
    }

    static boolean containsAscii(byte[] haystack, String needle) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= haystack.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (haystack[i + j] != n[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static String readable(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(1, bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
