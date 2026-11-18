package com.browserhub.app.media;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The download engine's state: a list of jobs that survive the sheet being
 * closed, progress the UI can poll, and filesystem publishing for finished work.
 *
 * Kept free of Android UI types apart from Context so the download logic can be
 * exercised on the JVM — which is how the merge path is tested at all.
 */
public final class DownloadQueue {

    public static final String KIND_FILE = "file";
    public static final String KIND_HLS = "hls";
    public static final String KIND_DASH = "dash";
    public static final String KIND_YOUTUBE = "youtube";

    /** One download. Fields are read by the UI thread and written by a worker. */
    public static final class Job {
        public final String id;
        public final String url;
        public final String filename;
        public final String kind;
        /**
         * URLs the page was observed requesting, for YouTube only.
         *
         * The player signs its own media URLs before requesting them, so its own
         * requests are the shortest path to a working address. Passed explicitly
         * rather than read from a global so the job is reproducible from its
         * fields alone.
         */
        public java.util.List<String> captured = new java.util.ArrayList<>();

        /** Mutable: YouTube only reports the real title after the player data is read. */
        public volatile String title;
        public volatile String status = "queued"; // queued|running|done|failed|cancelled
        public volatile int percent;
        public volatile long bytes;
        public volatile long totalBytes = -1;
        public volatile String message = "";
        public volatile boolean resumable;
        public final AtomicBoolean cancel = new AtomicBoolean(false);

        Job(String id, String url, String filename, String kind, String title) {
            this.id = id;
            this.url = url;
            this.filename = filename;
            this.kind = kind;
            this.title = title;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("url", url);
                o.put("filename", filename);
                o.put("kind", kind);
                o.put("title", title);
                o.put("status", status);
                o.put("percent", percent);
                o.put("bytes", bytes);
                o.put("totalBytes", totalBytes);
                o.put("message", message);
                o.put("resumable", resumable);
            } catch (Throwable ignored) {
            }
            return o;
        }
    }

    private static final List<Job> JOBS = Collections.synchronizedList(new ArrayList<Job>());
    private static int counter = 0;

    private DownloadQueue() {}

    public static Job enqueue(String url, String filename, String kind, String title) {
        String id;
        synchronized (DownloadQueue.class) {
            counter += 1;
            id = "dl-" + System.currentTimeMillis() + "-" + counter;
        }
        Job job = new Job(id, url, filename, kind, title);
        JOBS.add(0, job);
        return job;
    }

    public static Job find(String id) {
        synchronized (JOBS) {
            for (Job j : JOBS) {
                if (j.id.equals(id)) return j;
            }
        }
        return null;
    }

    public static List<Job> all() {
        synchronized (JOBS) {
            return new ArrayList<>(JOBS);
        }
    }

    /** Everything the sheet needs, as JSON, so it paints in one round trip. */
    public static String toJson() {
        JSONArray arr = new JSONArray();
        for (Job j : all()) arr.put(j.toJson());
        JSONObject root = new JSONObject();
        try {
            root.put("jobs", arr);
        } catch (Throwable ignored) {
        }
        return root.toString();
    }

    public static boolean cancel(String id) {
        Job j = find(id);
        if (j == null) return false;
        j.cancel.set(true);
        if ("queued".equals(j.status)) {
            j.status = "cancelled";
            j.message = "Cancelled";
            return true;
        }
        return true;
    }

    public static void clearFinished() {
        synchronized (JOBS) {
            for (int i = JOBS.size() - 1; i >= 0; i--) {
                String s = JOBS.get(i).status;
                if ("done".equals(s) || "failed".equals(s) || "cancelled".equals(s)) {
                    JOBS.remove(i);
                }
            }
        }
    }

    /** A title turned into a legal filename with a sane extension. */
    public static String filenameFor(String title, String kind) {
        String base = title == null || title.trim().length() == 0 ? "video" : title.trim();
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        if (base.length() > 80) base = base.substring(0, 80).trim();
        if (base.length() == 0) base = "video";
        String ext = KIND_HLS.equals(kind) ? ".ts"
                : KIND_DASH.equals(kind) ? ".mp4"
                : KIND_YOUTUBE.equals(kind) ? ".mp4"
                : guessExt(base);
        if (!base.toLowerCase(Locale.US).endsWith(ext)) base = base + ext;
        return base;
    }

    private static String guessExt(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m4v")
                || lower.endsWith(".mkv") || lower.endsWith(".mov")) {
            return "";
        }
        if (lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".aac")) {
            return "";
        }
        return ".mp4";
    }

    /**
     * Human-readable speed and remaining time from a progress sample.
     * Returns "" when there is not enough information, rather than inventing a
     * number — an estimated time that is obviously wrong erodes trust in all the
     * other numbers on screen.
     */
    public static String describeSpeed(long deltaBytes, long deltaMs, long done, long total) {
        if (deltaMs <= 0 || deltaBytes <= 0) return "";
        double bytesPerSec = (deltaBytes * 1000.0) / deltaMs;
        String speed = String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0));
        if (total <= 0 || done >= total) return speed;
        long remaining = total - done;
        long seconds = (long) (remaining / bytesPerSec);
        if (seconds <= 0 || seconds > 6 * 3600) return speed;
        if (seconds >= 60) {
            return speed + " · " + (seconds / 60) + "m " + (seconds % 60) + "s left";
        }
        return speed + " · " + seconds + "s left";
    }

    /** Deletes a finished file the user no longer wants. */
    public static boolean deleteFile(File file) {
        return file != null && file.exists() && file.delete();
    }
}
