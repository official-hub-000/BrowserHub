package com.browserhub.app.media;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloads one file over several connections at once.
 *
 * This is the technique every fast downloader uses, and it is why a link that
 * crawls at a couple of hundred bytes a second in a single connection can be
 * perfectly healthy: a single TCP connection over mobile data is usually limited
 * by round-trip time and per-connection throttling, not by bandwidth. Four
 * connections fetching different byte ranges use the same link four times over.
 *
 * The rules, learned from the reference implementation examined alongside this:
 *
 *  - Range support must be probed before anything else. If the server ignores
 *    Range, every connection would return the whole file and the result would be
 *    four copies interleaved into corruption. The probe sends `Range: bytes=0-0`
 *    and checks for a 206 plus a usable `Content-Range`.
 *  - The file is pre-allocated to its full length and each connection writes at
 *    its own offset, so no buffering or reordering is needed: position is decided
 *    by the range, not by arrival order.
 *  - Chunk boundaries must cover the file exactly, with the last chunk taking the
 *    remainder, or the tail of the video is silently missing.
 *  - A chunk that fails is retried, and if it still fails the whole download is
 *    reported as failed rather than leaving zero-filled holes.
 *
 * When the server does not support ranges, or the size is unknown, this delegates
 * to the single-connection path rather than giving up: slower is correct, and
 * broken is not.
 */
public final class RangeDownloader {

    /** Enough to bypass per-connection limits without opening a socket storm. */
    public static final int DEFAULT_THREADS = 4;
    private static final int MIN_CHUNK_SIZE = 512 * 1024;
    private static final int CONNECT_TIMEOUT = 20000;
    private static final int READ_TIMEOUT = 30000;
    private static final int ATTEMPTS = 3;

    private RangeDownloader() {}

    /** What a probe learned about the server. */
    static final class Probe {
        boolean ranges;
        long length = -1;
        String userAgent;
    }

    /**
     * Downloads {@code url} into {@code out}, using parallel ranges when possible.
     *
     * @param userAgent the identity the url was issued to; googlevideo compares it
     * @return true only when every byte was written
     */
    public static boolean fetch(String url, File out, String userAgent, String cookie,
                                DownloadQueue.Job job) {
        Probe probe = probe(url, userAgent, cookie);
        if (!probe.ranges || probe.length <= 0) {
            // No ranges: one connection is the only correct option.
            return MediaGrabberDirect.fetch(url, out, userAgent, cookie, job);
        }

        int threads = threadsFor(probe.length);
        if (threads <= 1) {
            return MediaGrabberDirect.fetch(url, out, userAgent, cookie, job);
        }

        job.totalBytes = probe.length;
        job.message = "Downloading over " + threads + " connections";

        try {
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            if (out.exists() && !out.delete()) return false;

            RandomAccessFile raf = new RandomAccessFile(out, "rw");
            FileChannel channel;
            try {
                // Reserve the full length so writes can land at any offset. Without
                // this, a chunk that finishes first would extend the file to its own
                // end only, and later chunks would write past it correctly but the
                // file would be the wrong size until the very last byte landed.
                raf.setLength(probe.length);
                channel = raf.getChannel();
            } finally {
                raf.close();
            }

            try {
                List<long[]> ranges = rangesFor(probe.length, threads);
                AtomicLong done = new AtomicLong(0);
                AtomicBoolean cancelled = new AtomicBoolean(false);
                AtomicBoolean failed = new AtomicBoolean(false);
                List<Thread> workers = new ArrayList<>();

                for (long[] range : ranges) {
                    final long start = range[0];
                    final long end = range[1];
                    Thread worker = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (cancelled.get() || failed.get()) return;
                            boolean ok = downloadChunk(url, start, end, channel,
                                    userAgent, cookie, done, job, cancelled);
                            if (!ok) failed.set(true);
                        }
                    }, "bh-chunk-" + start);
                    workers.add(worker);
                    worker.start();
                }

                // Report progress from the main thread while the workers run, so the
                // notification updates without any worker touching the job.
                for (Thread worker : workers) {
                    while (worker.isAlive()) {
                        try {
                            worker.join(250);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (job.cancel.get()) {
                            cancelled.set(true);
                        }
                        long written = done.get();
                        job.bytes = written;
                        job.percent = (int) Math.min(100, (written * 100) / probe.length);
                        job.message = readable(written) + " of " + readable(probe.length);
                        if (!worker.isAlive()) break;
                    }
                }

                if (job.cancel.get() || cancelled.get()) {
                    job.status = "cancelled";
                    job.message = "Cancelled";
                    out.delete();
                    return false;
                }
                if (failed.get() || done.get() != probe.length) {
                    job.status = "failed";
                    job.message = "The download did not complete — some parts failed";
                    out.delete();
                    return false;
                }

                job.bytes = probe.length;
                job.percent = 100;
                return true;
            } finally {
                try { channel.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            job.status = "failed";
            job.message = "The download could not be started: "
                    + (t.getMessage() != null ? t.getMessage() : "unknown error");
            out.delete();
            return false;
        }
    }

    /**
     * How many connections to use.
     *
     * Enough to help, few enough not to be rude: four is the point where mobile
     * throughput usually stops improving, and a small file does not justify more
     * than one connection at all.
     */
    public static int threadsFor(long length) {
        if (length <= 0) return 1;
        // Each connection should have a meaningful amount to do.
        int bySize = (int) Math.max(1, Math.min(DEFAULT_THREADS, length / MIN_CHUNK_SIZE));
        return bySize;
    }

    /**
     * Byte ranges covering {@code length} exactly.
     *
     * The last chunk absorbs the remainder, because dividing an odd length by the
     * thread count leaves a tail. Leaving the tail unclaimed is how a video ends up
     * missing its final seconds — a failure nobody notices until the playback stops
     * early.
     */
    public static List<long[]> rangesFor(long length, int threads) {
        List<long[]> out = new ArrayList<>();
        if (length <= 0 || threads <= 0) return out;
        long chunk = length / threads;
        long at = 0;
        for (int i = 0; i < threads; i++) {
            long end = (i == threads - 1) ? length - 1 : at + chunk - 1;
            out.add(new long[] { at, end });
            at = end + 1;
        }
        return out;
    }

    /** Asks the server whether it honours Range, and how big the file is. */
    static Probe probe(String url, String userAgent, String cookie) {
        Probe probe = new Probe();
        probe.userAgent = userAgent;
        HttpURLConnection conn = null;
        try {
            conn = open(url, userAgent, cookie);
            conn.setRequestProperty("Range", "bytes=0-0");
            int code = conn.getResponseCode();
            if (code == 206) {
                String contentRange = conn.getHeaderField("Content-Range");
                long total = totalFromContentRange(contentRange);
                if (total > 0) {
                    probe.ranges = true;
                    probe.length = total;
                    return probe;
                }
            }
            // A 200 here means Range was ignored: the whole file would have come
            // back, so parallel downloading would corrupt it.
            probe.ranges = false;
            probe.length = conn.getContentLengthLong();
            return probe;
        } catch (Throwable t) {
            probe.ranges = false;
            return probe;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Reads the total size out of `bytes 0-0/12345`. */
    public static long totalFromContentRange(String header) {
        if (header == null) return 0;
        int slash = header.lastIndexOf('/');
        if (slash < 0 || slash == header.length() - 1) return 0;
        String tail = header.substring(slash + 1).trim();
        if ("*".equals(tail)) return 0;
        try {
            return Long.parseLong(tail);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Fetches one range, writing it at its own offset. Retries before failing. */
    private static boolean downloadChunk(String url, long start, long end, FileChannel channel,
                                         String userAgent, String cookie, AtomicLong done,
                                         DownloadQueue.Job job, AtomicBoolean cancelled) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (cancelled.get() || job.cancel.get()) return false;
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                conn = open(url, userAgent, cookie);
                conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
                int code = conn.getResponseCode();
                if (code != 206 && code != 200) {
                    // 200 on a range request means the server ignored the header, and
                    // writing the whole body at an offset would corrupt the file.
                    if (code == 200) return false;
                    continue;
                }

                in = conn.getInputStream();
                byte[] buf = new byte[64 * 1024];
                long at = start;
                int read;
                while ((read = in.read(buf)) > 0) {
                    if (cancelled.get() || job.cancel.get()) return false;
                    int written = writeFully(channel, buf, read, at);
                    at += written;
                    done.addAndGet(written);
                    if (written < read) return false;
                }
                return at == end + 1;
            } catch (Throwable t) {
                // Fall through to the next attempt.
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) {}
                if (conn != null) conn.disconnect();
            }
        }
        return false;
    }

    private static int writeFully(FileChannel channel, byte[] buf, int length, long position)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(buf, 0, length);
        int total = 0;
        while (buffer.hasRemaining()) {
            int n = channel.write(buffer, position + total);
            if (n <= 0) break;
            total += n;
        }
        return total;
    }

    private static HttpURLConnection open(String url, String userAgent, String cookie)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (userAgent != null && userAgent.length() > 0) {
            conn.setRequestProperty("User-Agent", userAgent);
        }
        if (cookie != null && cookie.length() > 0) {
            conn.setRequestProperty("Cookie", cookie);
        }
        return conn;
    }

    private static String readable(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(0, bytes / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
