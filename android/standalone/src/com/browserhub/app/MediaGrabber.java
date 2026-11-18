package com.browserhub.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Downloads a media file that a page is already playing.
 *
 * Why this is native rather than a fetch() in the shell: the shell runs on a
 * virtual origin and the media lives on somebody else's CDN, so a browser fetch
 * is blocked by that CDN's cross-origin policy. HttpURLConnection is not bound by
 * page CORS rules, and it can replay the session cookies the page itself used —
 * which is what makes a signed, logged-in-only URL actually resolve. That is the
 * whole reason a browser downloader can work where a web app cannot.
 *
 * It is a plain in-app downloader. There is no ad SDK anywhere in this app, so
 * there is no interstitial, no "watch this to unlock", and no upsell screen on
 * this path — see CLAIMS in MediaCaptureCheck for the gate that proves it.
 */
public final class MediaGrabber {

    public interface ProgressListener {
        void onProgress(int percent, long bytes, long total);
    }

    private MediaGrabber() {}

    /**
     * Where the download is written while it is in flight.
     *
     * This must be app-private. Under scoped storage (Android 11+) a direct
     * write into the public Movies folder is refused with EACCES, so staging a
     * `.part` file there would make every download fail on a modern device. The
     * finished file is published to the media store, or copied into the public
     * folder only on the older versions that allow it.
     */
    private static File stagingDir(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "media");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Public folder used only on Android 9 and below. */
    private static File legacyPublicDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "BrowserHub");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Runs a download on a worker thread and reports progress on the UI thread.
     * Returns immediately; the file appears in Movies/BrowserHub.
     */
    public static void download(
            final Context ctx,
            final String rawUrl,
            final String rawName,
            final String userAgent,
            final ProgressListener listener) {

        final Handler ui = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream in = null;
                OutputStream out = null;
                File temp = null;
                try {
                    URL url = new URL(rawUrl);
                    String protocol = url.getProtocol();
                    // Only plain web protocols: never file://, content:// or javascript:.
                    if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                        throw new IllegalArgumentException("Unsupported protocol: " + protocol);
                    }

                    String filename = sanitise(rawName);

                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(30000);
                    conn.setRequestMethod("GET");
                    if (userAgent != null && userAgent.length() > 0) {
                        conn.setRequestProperty("User-Agent", userAgent);
                    }
                    conn.setRequestProperty("Accept", "*/*");

                    // Replay the page's cookies: most signed media URLs need the session.
                    try {
                        CookieManager cm = CookieManager.getInstance();
                        String cookie = cm.getCookie(url.getProtocol() + "://" + url.getHost() + "/");
                        if (cookie != null && cookie.length() > 0) {
                            conn.setRequestProperty("Cookie", cookie);
                        }
                    } catch (Throwable ignored) {
                        // Cookie replay is a bonus, never a requirement.
                    }

                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code >= 400) {
                        throw new IllegalStateException("Server said HTTP " + code);
                    }

                    long total = conn.getContentLengthLong();

                    // An HTML body means we were handed a page, not a video. Say so
                    // instead of writing a broken .mp4 the user only discovers later.
                    String contentType = conn.getContentType();
                    if (contentType != null && contentType.toLowerCase().startsWith("text/html")) {
                        throw new IllegalStateException(
                                "That link returned a web page, not a video file");
                    }

                    temp = new File(stagingDir(ctx), filename + ".part");
                    in = conn.getInputStream();
                    out = new FileOutputStream(temp);

                    byte[] buf = new byte[65536];
                    long written = 0L;
                    int lastPercent = -1;
                    int read;
                    while ((read = in.read(buf)) > 0) {
                        out.write(buf, 0, read);
                        written += read;
                        if (listener != null && total > 0) {
                            int percent = (int) ((written * 100) / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int p = percent;
                                final long w = written;
                                final long t = total;
                                ui.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        listener.onProgress(p, w, t);
                                    }
                                });
                            }
                        }
                    }
                    out.flush();
                    out.close();
                    out = null;
                    in.close();
                    in = null;

                    if (written <= 0) {
                        throw new IllegalStateException("The server sent an empty file");
                    }

                    /*
                     * Publish the finished file.
                     *
                     * Android 10+ goes through MediaStore, which needs no storage
                     * permission and makes the file visible to the gallery.
                     * Older versions copy into the public Movies folder, which is
                     * why WRITE_EXTERNAL_STORAGE is declared with maxSdkVersion 28.
                     */
                    boolean placed;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        placed = publishToMediaStore(ctx, temp, filename, contentType);
                    } else {
                        File target = new File(legacyPublicDir(), filename);
                        placed = temp.renameTo(target);
                    }
                    if (!placed) {
                        throw new IllegalStateException("Could not finish the file");
                    }
                    if (temp.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        temp.delete();
                    }

                    final String doneName = filename;
                    final long size = written;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ctx,
                                    "Saved " + doneName + " (" + megabytes(size) + ")",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Throwable t) {
                    if (temp != null && temp.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        temp.delete();
                    }
                    final String message = readableFailure(t);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    try { if (out != null) out.close(); } catch (Throwable ignored) {}
                    try { if (in != null) in.close(); } catch (Throwable ignored) {}
                    if (conn != null) conn.disconnect();
                }
            }
        }, "BrowserHubMediaGrab").start();
    }

    /**
     * Registers the finished file with MediaStore so it survives the app being
     * uninstalled and shows up in the gallery. Video and audio go to their own
     * collections; anything else lands in Downloads.
     */
    private static boolean publishToMediaStore(Context ctx, File file, String name, String mime) {
        try {
            String resolved = mime != null && mime.length() > 0 ? mime : guessMime(name);
            boolean isAudio = resolved.startsWith("audio/");
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, resolved);
            values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    isAudio
                            ? Environment.DIRECTORY_MUSIC + "/BrowserHub"
                            : Environment.DIRECTORY_MOVIES + "/BrowserHub");

            Uri uri = ctx.getContentResolver().insert(
                    isAudio
                            ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values);
            if (uri == null) return false;

            OutputStream os = ctx.getContentResolver().openOutputStream(uri);
            if (os == null) return false;
            InputStream is = new java.io.FileInputStream(file);
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            is.close();
            os.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "video/mp4";
        String ext = name.substring(dot + 1).toLowerCase();
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "video/mp4";
    }

    /** Keeps the user's title but makes it a legal filename on every device. */
    static String sanitise(String raw) {
        String base = raw == null || raw.trim().length() == 0 ? "video" : raw.trim();
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ");
        base = base.replaceAll("\\s+", " ").trim();
        if (base.length() > 90) {
            base = base.substring(0, 90).trim();
        }
        if (base.length() == 0) base = "video";
        if (base.startsWith(".")) base = "video" + base;
        return base;
    }

    private static String megabytes(long bytes) {
        if (bytes < 1024L * 1024L) {
            return Math.max(1L, bytes / 1024L) + " KB";
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** A failure the user can act on, never a stack trace and never a lie. */
    static String readableFailure(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) msg = "";
        String lower = msg.toLowerCase();
        if (lower.contains("403") || lower.contains("forbidden")) {
            return "Download refused: this link needs your session or has expired";
        }
        if (lower.contains("404")) {
            return "Download failed: the video is no longer at that address";
        }
        if (lower.contains("http")) {
            return "Download failed: " + msg;
        }
        if (lower.contains("unknownhost") || lower.contains("unable to resolve")) {
            return "Download failed: no connection to the video host";
        }
        if (lower.contains("empty file")) {
            return "Download failed: the server sent nothing";
        }
        if (lower.contains("web page")) {
            return "That link is a web page, not a video. Pick another entry.";
        }
        return "Download failed: " + (msg.length() > 0 ? msg : t.getClass().getSimpleName());
    }

    /** Status of a running download, exposed to the shell for the UI. */
    public static final Map<String, long[]> STATS = new HashMap<String, long[]>();
}
