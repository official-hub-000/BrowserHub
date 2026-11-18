package com.browserhub.app.media;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Streams a single media file to disk with real progress.
 *
 * Separate from the merge path because these are genuinely different jobs: one
 * copies bytes from a stable URL, the other assembles hundreds of requests. Both
 * report progress and both honour cancellation; neither buffers a whole video in
 * memory, which matters because a phone download is routinely hundreds of
 * megabytes.
 */
public final class MediaGrabberDirect {

    private static final int CONNECT_TIMEOUT = 20000;
    private static final int READ_TIMEOUT = 30000;

    private MediaGrabberDirect() {}

    /**
     * Downloads {@code url} into {@code out}.
     *
     * @return true when a complete file was written. On failure the job carries
     *         a sentence the user can act on.
     */
    public static boolean fetch(String url, File out, String userAgent, String cookie,
                                DownloadQueue.Job job) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream os = null;
        try {
            String scheme = Resolver.scheme(url);
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                fail(job, "That link is not a web address");
                return false;
            }
            if (out.getParentFile() != null) out.getParentFile().mkdirs();

            conn = (HttpURLConnection) new URL(url).openConnection();
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

            int code = conn.getResponseCode();
            if (code >= 400) {
                fail(job, code == 403
                        ? "This link refused the request. It may have expired."
                        : code == 404
                        ? "The video is no longer at that address."
                        : "The server said HTTP " + code);
                return false;
            }

            String contentType = conn.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.US).startsWith("text/html")) {
                fail(job, "That link is a web page, not a video");
                return false;
            }

            long total = conn.getContentLengthLong();
            job.totalBytes = total;

            in = conn.getInputStream();
            os = new FileOutputStream(out);

            byte[] buf = new byte[65536];
            long written = 0;
            int read;
            while ((read = in.read(buf)) > 0) {
                if (job.cancel.get()) {
                    return false;
                }
                os.write(buf, 0, read);
                written += read;
                job.bytes = written;
                if (total > 0) {
                    job.percent = (int) Math.min(100, (written * 100) / total);
                }
                job.message = readable(written);
            }
            os.flush();
            os.close();
            os = null;
            in.close();
            in = null;

            if (written <= 0) {
                fail(job, "The server sent an empty file");
                return false;
            }
            return true;
        } catch (Throwable t) {
            fail(job, MediaMerger.readable(t));
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (Throwable ignored) {}
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static void fail(DownloadQueue.Job job, String message) {
        job.status = "failed";
        job.message = message;
    }

    private static String readable(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(1, bytes / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** True when the URL is a stream index rather than a file. */
    public static boolean isStream(String url) {
        return Resolver.isPlaylist(url);
    }

    /** Detects a manifest by content when the URL has no extension. */
    public static boolean looksLikeManifest(String contentType, String firstBytes) {
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.US);
            if (ct.contains("mpegurl") || ct.contains("dash+xml")) return true;
        }
        return firstBytes != null && firstBytes.contains("#EXTM3U");
    }

    static IOException io(String message) {
        return new IOException(message);
    }
}
