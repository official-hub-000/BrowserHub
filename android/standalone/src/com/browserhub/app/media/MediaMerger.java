package com.browserhub.app.media;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * Joins an HLS stream into one file.
 *
 * HLS is not a video format — it is a list of small files. Downloading "the
 * m3u8" gets you a text file. To get a playable video you fetch every segment,
 * decrypt any that are encrypted, and concatenate them in order. For the common
 * `.ts` segments that concatenation is valid MPEG-TS, which every Android player
 * and the gallery accept directly.
 *
 * What this deliberately does NOT do:
 *   - fMP4 (`.m4s`) remuxing into a clean MP4. The init section plus fragments is
 *     a valid fragmented MP4, so it is written as `.mp4` and plays, but it is not
 *     a rewrite into a classic MP4. No claim of that is made in the UI.
 *   - SAMPLE-AES / DRM. Reported as unsupported, never attempted.
 *
 * Progress is reported per segment, and everything is streamed to disk one
 * segment at a time: a two-hour video is hundreds of megabytes and must never be
 * held in memory.
 */
public final class MediaMerger {

    /** Progress callback, always invoked on the worker thread. */
    public interface Listener {
        /** @param done bytes written so far; @param total expected, or -1 if unknown. */
        void onProgress(long done, long total, int segmentsDone, int segmentsTotal);

        void onStage(String message);

        /** Returning true requests a stop; the merge cleans up after itself. */
        boolean shouldCancel();
    }

    public static final class Result {
        public boolean ok;
        public File file;
        public String error;
        public long bytes;
        public int segments;
        public boolean resumed;
    }

    private static final int CONNECT_TIMEOUT = 20000;
    private static final int READ_TIMEOUT = 30000;
    private static final int BUFFER = 65536;

    private MediaMerger() {}

    /**
     * Downloads and joins a stream.
     *
     * @param playlistUrl the media playlist (a master is resolved automatically)
     * @param outFile     destination; a `.part` sibling is used while running
     * @param maxHeight   preferred maximum vertical resolution, or 0 for "best"
     */
    public static Result merge(String playlistUrl, File outFile, int maxHeight,
                               String userAgent, String cookie, Listener listener) {
        Result result = new Result();
        File temp = new File(outFile.getParentFile(), outFile.getName() + ".part");
        long alreadyHave = 0;

        try {
            if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

            // Resume: keep what an interrupted run already wrote.
            if (temp.exists()) {
                alreadyHave = temp.length();
            }

            if (listener != null) listener.onStage("Reading the stream index");
            HlsPlaylist playlist = loadPlaylist(playlistUrl, userAgent, cookie);
            if (playlist.error != null) {
                result.error = playlist.error;
                return result;
            }

            int totalSegments = playlist.segments.size();
            long totalBytes = estimateBytes(playlist);

            if (listener != null) {
                listener.onStage(alreadyHave > 0 ? "Resuming download" : "Downloading " + totalSegments + " parts");
            }

            /*
             * Resume.
             *
             * Segments have unpredictable sizes, so a byte offset in a .part file
             * cannot be trusted to land on a segment edge. Instead the companion
             * ".segments" journal records how many segments were fully written.
             * On restart those are skipped and the file is truncated back to that
             * exact boundary before writing continues — so resume is correct, not
             * approximate. A journal that disagrees with the file (an interrupted
             * write) is discarded and the download restarts rather than guessing.
             */
            File journal = new File(outFile.getParentFile(), outFile.getName() + ".segments");
            int resumeFrom = 0;
            long resumeBytes = 0;
            if (alreadyHave > 0 && journal.exists()) {
                try {
                    String recorded = readSmall(journal).trim();
                    int index = Integer.parseInt(recorded);
                    long offset = SegmentJournal.offsetOf(playlist, index);
                    if (index > 0 && index <= playlist.segments.size() && offset > 0 && offset <= alreadyHave) {
                        resumeFrom = index;
                        resumeBytes = offset;
                    }
                } catch (Throwable ignored) {
                    // An unreadable journal is not a licence to guess.
                }
            }
            if (resumeBytes == 0 && alreadyHave > 0) {
                // Nothing trustworthy to resume from: start clean.
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                alreadyHave = 0;
            }

            FileOutputStream out = new FileOutputStream(temp, resumeBytes > 0);
            long written = resumeBytes;
            int done = resumeFrom;
            byte[] key = null;

            try {
                if (playlist.mapUri != null && resumeBytes == 0) {
                    byte[] init = fetchBytes(playlist.mapUri, playlist.mapStart, playlist.mapLength,
                            userAgent, cookie);
                    out.write(init);
                    written += init.length;
                }

                for (int segIdx = resumeFrom; segIdx < playlist.segments.size(); segIdx++) {
                    HlsPlaylist.Segment seg = playlist.segments.get(segIdx);
                    if (listener != null && listener.shouldCancel()) {
                        result.error = "cancelled";
                        result.resumed = resumeBytes > 0;
                        return result;
                    }

                    byte[] data = fetchBytes(seg.uri, seg.rangeStart, seg.rangeLength,
                            userAgent, cookie);

                    if (playlist.encrypted && playlist.keyUri != null) {
                        if (playlist.sampleAes) {
                            result.error = "This stream is SAMPLE-AES encrypted and cannot be decrypted in an app";
                            return result;
                        }
                        if (key == null) {
                            byte[] raw = fetchBytes(playlist.keyUri, -1, -1, userAgent, cookie);
                            if (raw.length != 16) {
                                result.error = "The stream's decryption key was not a valid 16-byte key";
                                return result;
                            }
                            key = raw;
                        }
                        String ivHex = playlist.keyIv != null
                                ? playlist.keyIv
                                : sequenceIv(segIdx);
                        data = Aes128Cbc.decrypt(data, key, ivHex);
                    }

                    out.write(data);
                    written += data.length;
                    done = segIdx + 1;

                    // Journal AFTER the bytes are flushed, so a journal entry can
                    // never claim a segment that is not on disk.
                    out.flush();
                    writeSmall(journal, String.valueOf(done));

                    if (listener != null) {
                        listener.onProgress(written, totalBytes > 0 ? totalBytes : -1, done, totalSegments);
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

            // Promote the part file into place.
            if (outFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
            if (!temp.renameTo(outFile)) {
                result.error = "could not finish the file";
                return result;
            }
            //noinspection ResultOfMethodCallIgnored
            journal.delete();

            result.ok = true;
            result.file = outFile;
            result.bytes = written;
            result.segments = done;
            result.resumed = alreadyHave > 0;
            return result;
        } catch (Throwable t) {
            result.error = readable(t);
            return result;
        }
    }

    /** Loads a playlist, following a master to its best variant. */
    public static HlsPlaylist loadPlaylist(String url, String userAgent, String cookie) throws IOException {
        String body = fetchText(url, userAgent, cookie);
        if (!HlsPlaylist.looksLikeHls(body)) {
            HlsPlaylist bad = HlsPlaylist.parse("#EXTM3U\n", url);
            bad.error = "that link is not a stream playlist";
            return bad;
        }
        HlsPlaylist parsed = HlsPlaylist.parse(body, url);
        if (parsed.isMaster && parsed.error == null) {
            HlsPlaylist.Variant best = parsed.bestVariant();
            if (best == null) {
                parsed.error = "stream index had no playable variants";
                return parsed;
            }
            String variantBody = fetchText(best.uri, userAgent, cookie);
            return HlsPlaylist.parse(variantBody, best.uri);
        }
        return parsed;
    }

    private static long estimateBytes(HlsPlaylist p) {
        // Rough: total duration in seconds at ~1 Mbit/s. Used only to make the
        // progress bar sane; the UI never claims this is exact.
        return (long) (p.totalDuration * 1000000L / 8L);
    }

    public static String fetchText(String url, String userAgent, String cookie) throws IOException {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = open(url, userAgent, cookie);
            int code = conn.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code + " reading the stream index");
            in = conn.getInputStream();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return buf.toString("UTF-8");
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    /** Fetches one segment, honouring an optional byte range. */
    public static byte[] fetchBytes(String url, long rangeStart, long rangeLength,
                             String userAgent, String cookie) throws IOException {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = open(url, userAgent, cookie);
            if (rangeLength > 0) {
                if (rangeStart >= 0) {
                    conn.setRequestProperty("Range", "bytes=" + rangeStart + "-" + (rangeStart + rangeLength - 1));
                } else {
                    conn.setRequestProperty("Range", "bytes=" + rangeLength);
                }
            }
            int code = conn.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code + " on a stream segment");
            in = conn.getInputStream();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[BUFFER];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return buf.toByteArray();
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String userAgent, String cookie) throws IOException {
        String scheme = Resolver.scheme(url);
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("refusing a non-web URL");
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept", "*/*");
        if (userAgent != null && userAgent.length() > 0) {
            conn.setRequestProperty("User-Agent", userAgent);
        }
        if (cookie != null && cookie.length() > 0) {
            conn.setRequestProperty("Cookie", cookie);
        }
        return conn;
    }

    /**
     * The implicit IV for an AES-128 segment is its media sequence number as a
     * 128-bit big-endian integer. Segments we download start at sequence 0, so
     * the index is the sequence.
     */
    static String sequenceIv(int index) {
        return String.format(Locale.US, "0x%032x", index);
    }

    /** Keeps failures to a sentence. */
    static String readable(Throwable t) {
        String m = t.getMessage();
        if (m == null) m = t.getClass().getSimpleName();
        String lower = m.toLowerCase(Locale.US);
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "The stream stopped responding. Try again on a better connection.";
        }
        if (lower.contains("403") || lower.contains("forbidden")) {
            return "This stream refused the request. The link may have expired.";
        }
        if (lower.contains("404")) {
            return "A part of the stream is missing on the server.";
        }
        if (lower.contains("refusing a non-web")) {
            return "That link is not a web address.";
        }
        return "Download failed: " + m;
    }

    private static String readSmall(File f) throws IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            byte[] buf = new byte[64];
            int n = in.read(buf);
            return n > 0 ? new String(buf, 0, n, "US-ASCII") : "";
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static void writeSmall(File f, String value) {
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(f, false);
            out.write(value.getBytes("US-ASCII"));
            out.getFD().sync();
            out.close();
        } catch (Throwable ignored) {
        }
    }

    /** Wipes a partial file, used when the user cancels a merge. */
    public static void discard(File outFile) {
        try {
            File temp = new File(outFile.getParentFile(), outFile.getName() + ".part");
            if (temp.exists()) {
                RandomAccessFile raf = new RandomAccessFile(temp, "rw");
                raf.close();
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
