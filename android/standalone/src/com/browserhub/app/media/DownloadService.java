package com.browserhub.app.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.webkit.CookieManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Keeps a stream merge alive while the user is doing something else.
 *
 * Merging a long video is minutes of work; a plain thread inside the activity
 * would be killed the moment the app went to the background, leaving a half
 * written file. A foreground service with a progress notification is the only
 * correct place for it, and it is also what makes the notification honest: if it
 * says 42% then something is really downloading.
 */
public class DownloadService extends Service {

    public static final String ACTION_START = "com.browserhub.app.media.START";
    public static final String ACTION_CANCEL = "com.browserhub.app.media.CANCEL";
    public static final String EXTRA_JOB_ID = "job_id";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_USER_AGENT = "ua";

    private static final String CHANNEL_ID = "browserhub_downloads";
    private static final int NOTIFICATION_ID = 4211;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_CANCEL.equals(intent.getAction())) {
            String id = intent.getStringExtra(EXTRA_JOB_ID);
            if (id != null) DownloadQueue.cancel(id);
            return START_NOT_STICKY;
        }

        final String jobId = intent.getStringExtra(EXTRA_JOB_ID);
        final String url = intent.getStringExtra(EXTRA_URL);
        final String filename = intent.getStringExtra(EXTRA_FILENAME);
        final String kind = intent.getStringExtra(EXTRA_KIND);
        final String title = intent.getStringExtra(EXTRA_TITLE);
        final String userAgent = intent.getStringExtra(EXTRA_USER_AGENT);
        if (jobId == null || url == null || filename == null) return START_NOT_STICKY;

        DownloadQueue.Job job = DownloadQueue.find(jobId);
        if (job == null) job = DownloadQueue.enqueue(url, filename, kind, title);
        final DownloadQueue.Job running = job;
        running.status = "running";

        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification(title != null ? title : filename, 0, true));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runJob(running, url, filename, kind, title, userAgent);
                } finally {
                    stopForeground(true);
                    stopSelf();
                }
            }
        }, "BrowserHubDownload").start();

        return START_NOT_STICKY;
    }

    private void runJob(DownloadQueue.Job job, String url, String filename, String kind,
                        String title, String userAgent) {
        String cookie = cookieFor(url);
        File out = new File(getCacheDir(), "media/" + filename);
        out.getParentFile().mkdirs();

        long lastShown = 0L;

        if (DownloadQueue.KIND_YOUTUBE.equals(kind)) {
            /*
             * YouTube needs its own path: the media URL is not in the page's
             * network traffic, it is inside the player's JSON. Resolve that first,
             * then hand the result to the same merger the other streams use.
             */
            job.message = "Reading the video information";
            publishProgress(job, false);

            YouTubeExtractor.Result info = YouTubeExtractor.resolve(url, userAgent, cookie);

            /*
             * Complete any scrambled stream with the URLs the page already fetched
             * for itself. This is tried BEFORE asking the page to transform a
             * signature, because the capture costs nothing: those requests already
             * happened, and they carry a signature the player computed itself.
             */
            if (info.all != null && info.scrambledCount > 0 && job.captured != null
                    && job.captured.size() > 0) {
                YouTubeExtractor.applyCaptures(info, job.captured);
            }

            if (!info.ok) {
                job.status = "failed";
                job.message = info.error != null ? info.error : "YouTube did not offer this video";
                return;
            }

            // Whatever is still scrambled needs the page's own JavaScript. This
            // runs on the UI thread against the live player, which is where the
            // transform function actually exists.
            if (info.scrambledCount > 0 && pendingSignatures(info)) {
                job.message = "Asking the player for the video address";
                publishProgress(job, false);
                resolveSignatures(info);
            }

            if (info.bestVideo == null && info.progressive == null) {
                job.status = "failed";
                job.message = "YouTube did not offer a playable stream for this video";
                return;
            }
            if (info.title != null && info.title.length() > 0) {
                job.title = info.title;
            }

            if (info.progressive != null) {
                // One file with picture and sound: nothing to join.
                job.message = "Downloading " + info.progressive.qualityLabel;
                publishProgress(job, false);
                boolean ok = MediaGrabberDirect.fetch(
                        info.progressive.url, info.progressive, out, userAgent, cookie, job);
                if (job.cancel.get()) {
                    job.status = "cancelled";
                    job.message = "Cancelled";
                    out.delete();
                    return;
                }
                if (!ok) {
                    if (job.message == null || job.message.length() == 0) {
                        job.message = "Download failed";
                    }
                    out.delete();
                    return;
                }
                finish(job, out, filename, "video/mp4", title);
                return;
            }

            /*
             * No combined file offered, so both tracks are needed. This is the
             * `-f "<video>+bestaudio" --merge-output-format mp4` step: fetch the
             * picture and the sound, then join them with the platform muxer.
             *
             * The two are downloaded to separate files and joined as real tracks,
             * not concatenated. Concatenating produced a file that opened and played
             * nothing, because a container's sample tables describe one file's
             * layout and do not survive being pasted next to another's.
             */
            FormatSelector.Plan plan = FormatSelector.plan(info.all);
            if (plan.mode != FormatSelector.Plan.MODE_MERGE) {
                job.status = "failed";
                job.message = "YouTube did not offer both a picture and a sound track";
                return;
            }

            File videoPart = new File(getCacheDir(), "media/part-" + job.id + "-v.mp4");
            File audioPart = new File(getCacheDir(), "media/part-" + job.id + "-a.m4a");
            job.message = "Downloading picture and sound";
            publishProgress(job, false);

            boolean haveVideo = MediaGrabberDirect.fetch(
                    plan.video.url, plan.video, videoPart, userAgent, cookie, job);
            if (!haveVideo || job.cancel.get()) {
                videoPart.delete();
                audioPart.delete();
                if (!job.cancel.get()) {
                    job.status = "failed";
                    if (job.message == null || job.message.length() == 0) {
                        job.message = "The picture track could not be downloaded";
                    }
                } else {
                    job.status = "cancelled";
                    job.message = "Cancelled";
                }
                out.delete();
                return;
            }

            boolean haveAudio = MediaGrabberDirect.fetch(
                    plan.audio.url, plan.audio, audioPart, userAgent, cookie, job);
            if (!haveAudio || job.cancel.get()) {
                videoPart.delete();
                audioPart.delete();
                if (!job.cancel.get()) {
                    job.status = "failed";
                    job.message = "The sound track could not be downloaded";
                } else {
                    job.status = "cancelled";
                    job.message = "Cancelled";
                }
                out.delete();
                return;
            }

            job.message = "Joining picture and sound";
            publishProgress(job, false);

            java.util.List<File> parts = new java.util.ArrayList<>();
            parts.add(videoPart);
            parts.add(audioPart);

            boolean joined;
            try {
                joined = TrackWriter.assemble(plan, parts, out);
            } catch (Throwable t) {
                joined = false;
            }
            videoPart.delete();
            audioPart.delete();

            if (job.cancel.get()) {
                job.status = "cancelled";
                job.message = "Cancelled";
                out.delete();
                return;
            }
            if (!joined) {
                job.status = "failed";
                job.message = "The two tracks downloaded but could not be joined into one file";
                out.delete();
                return;
            }
            finish(job, out, filename, "video/mp4", title);
            return;
        }

        if (DownloadQueue.KIND_DASH.equals(kind)) {
            MediaMerger.Result result = DashMerger.merge(url, out, 0, userAgent, cookie,
                    new MediaMerger.Listener() {
                        @Override
                        public void onProgress(long done, long total, int segmentsDone, int segmentsTotal) {
                            job.bytes = done;
                            job.totalBytes = total;
                            job.percent = progressPercent(done, total, segmentsDone, segmentsTotal);
                            job.message = segmentsDone + " of " + segmentsTotal + " parts";
                            publishProgress(job, false);
                        }

                        @Override
                        public void onStage(String message) {
                            job.message = message;
                            publishProgress(job, false);
                        }

                        @Override
                        public boolean shouldCancel() {
                            return job.cancel.get();
                        }
                    });

            if (job.cancel.get()) {
                job.status = "cancelled";
                job.message = "Cancelled";
                MediaMerger.discard(out);
                return;
            }
            if (!result.ok) {
                job.status = "failed";
                job.message = result.error != null ? result.error : "Download failed";
                MediaMerger.discard(out);
                return;
            }
            finish(job, result.file, filename, "video/mp4", title);
            return;
        }

        if (DownloadQueue.KIND_HLS.equals(kind)) {
            MediaMerger.Result result = MediaMerger.merge(url, out, 0, userAgent, cookie,
                    new MediaMerger.Listener() {
                        @Override
                        public void onProgress(long done, long total, int segmentsDone, int segmentsTotal) {
                            job.bytes = done;
                            job.totalBytes = total;
                            job.percent = progressPercent(done, total, segmentsDone, segmentsTotal);
                            job.message = segmentsDone + " of " + segmentsTotal + " parts";
                            publishProgress(job, false);
                        }

                        @Override
                        public void onStage(String message) {
                            job.message = message;
                            publishProgress(job, false);
                        }

                        @Override
                        public boolean shouldCancel() {
                            return job.cancel.get();
                        }
                    });

            if (job.cancel.get()) {
                job.status = "cancelled";
                job.message = "Cancelled";
                MediaMerger.discard(out);
                return;
            }
            if (!result.ok) {
                job.status = "failed";
                job.message = result.error != null ? result.error : "Download failed";
                MediaMerger.discard(out);
                return;
            }
            finish(job, result.file, filename, "video/mp4", title);
            return;
        }

        // A direct file: stream it and report progress as it lands.
        boolean ok = MediaGrabberDirect.fetch(url, out, userAgent, cookie, job);
        if (job.cancel.get()) {
            job.status = "cancelled";
            job.message = "Cancelled";
            out.delete();
            return;
        }
        if (!ok) {
            if (!"failed".equals(job.status)) job.status = "failed";
            if (job.message == null || job.message.length() == 0) job.message = "Download failed";
            out.delete();
            return;
        }
        finish(job, out, filename, guessMime(filename), title);
    }

    private static int progressPercent(long done, long total, int segDone, int segTotal) {
        if (total > 0) return (int) Math.min(100, (done * 100) / total);
        if (segTotal > 0) return (int) Math.min(100, (segDone * 100) / segTotal);
        return 0;
    }

    /** True when any stream that would actually be downloaded still lacks a signature. */
    private static boolean pendingSignatures(YouTubeExtractor.Result info) {
        YouTubeExtractor.Stream[] wanted = new YouTubeExtractor.Stream[] {
                info.progressive, info.bestVideo, info.bestAudio
        };
        for (YouTubeExtractor.Stream s : wanted) {
            if (s != null && s.needsSignature) return true;
        }
        return false;
    }

    /**
     * Asks the page to sign the streams that are still scrambled.
     *
     * The transform lives in the player's own JavaScript, so it is called there
     * rather than reimplemented here: the player already contains it, and a copy
     * would be wrong the next time YouTube changes it. The call must happen on the
     * UI thread because that is the only thread with access to the live page.
     */
    private void resolveSignatures(YouTubeExtractor.Result info) {
        YouTubeExtractor.Stream[] wanted = new YouTubeExtractor.Stream[] {
                info.progressive, info.bestVideo, info.bestAudio
        };
        for (YouTubeExtractor.Stream stream : wanted) {
            if (stream == null || !stream.needsSignature) continue;
            String signature = SignatureSolver.Holder.solve(stream.cipherSignature);
            if (signature != null && signature.length() > 0) {
                stream.url = YouTubeExtractor.urlWithSignature(stream, signature);
                stream.needsSignature = false;
            }
        }
    }

    private void finish(DownloadQueue.Job job, File file, String filename, String mime, String title) {
        if (file == null || !file.exists()) {
            job.status = "failed";
            job.message = "The file was not written";
            return;
        }
        job.bytes = file.length();
        job.percent = 100;
        job.status = "done";
        job.message = readableSize(file.length());

        // Publish so the video is visible to the gallery and other apps.
        try {
            publish(file, filename, mime, title);
        } catch (Throwable t) {
            job.message = readableSize(file.length());
        }
        publishProgress(job, true);
        file.delete();
    }

    private void publish(File file, String filename, String mime, String title) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    mime.startsWith("audio/")
                            ? Environment.DIRECTORY_MUSIC + "/BrowserHub"
                            : Environment.DIRECTORY_MOVIES + "/BrowserHub");
            Uri uri = getContentResolver().insert(
                    mime.startsWith("audio/")
                            ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values);
            if (uri == null) throw new IllegalStateException("media store refused the file");
            OutputStream os = getContentResolver().openOutputStream(uri);
            InputStream is = new FileInputStream(file);
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            is.close();
            os.close();
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES), "BrowserHub");
            dir.mkdirs();
            File target = new File(dir, filename);
            if (!file.renameTo(target)) throw new IllegalStateException("could not move the file");
        }
    }

    private String cookieFor(String url) {
        try {
            String scheme = Resolver.scheme(url);
            String host = Resolver.authority(url);
            if (scheme == null || host == null) return null;
            return CookieManager.getInstance().getCookie(scheme + "://" + host + "/");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String guessMime(String filename) {
        String lower = filename.toLowerCase(Locale.US);
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".m4a") || lower.endsWith(".aac")) return "audio/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".ts")) return "video/mp2t";
        return "video/mp4";
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(1, bytes / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ------------------------------------------------------------ notification
    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Download progress");
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String title, int percent, boolean indeterminate) {
        // Built from the package name rather than a class reference so the engine
        // stays free of any dependency on the UI layer — which is what lets the
        // merge path be compiled and executed as a standalone JVM test.
        Intent open = new Intent(Intent.ACTION_MAIN);
        open.setClassName(getPackageName(), getPackageName() + ".MainActivity");
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setContentTitle(title != null ? title : "Downloading")
                .setContentIntent(pi)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true);

        if (indeterminate) {
            b.setProgress(100, 0, true);
            b.setContentText("Starting…");
        } else {
            b.setProgress(100, percent, false);
            b.setContentText(percent + "%");
        }
        return b.build();
    }

    private void publishProgress(DownloadQueue.Job job, boolean finished) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        String title = job.title != null && job.title.length() > 0 ? job.title : job.filename;
        Notification n = buildNotification(title, job.percent, false);
        nm.notify(NOTIFICATION_ID, n);
    }

    /** Entry point used by the bridge. */
    public static void start(Context ctx, DownloadQueue.Job job, String kind, String userAgent) {
        Intent i = new Intent(ctx, DownloadService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_JOB_ID, job.id);
        i.putExtra(EXTRA_URL, job.url);
        i.putExtra(EXTRA_FILENAME, job.filename);
        i.putExtra(EXTRA_KIND, kind);
        i.putExtra(EXTRA_TITLE, job.title);
        i.putExtra(EXTRA_USER_AGENT, userAgent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }
}
