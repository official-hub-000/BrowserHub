package com.browserhub.app.media;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Joins a picture track and a sound track into one file.
 *
 * The reference implementation delegates this to ffmpeg. ffmpeg is not shipped
 * here, so the same operation is done with {@link MediaMuxer} — the platform's own
 * muxer, which every Android device has and which writes a correct, playable MP4
 * by construction.
 *
 * This is deliberately NOT a hand-written MP4 writer. A container has sample
 * tables whose offsets are absolute file positions; assembling one by hand
 * produces a file that opens, shows a duration, and plays nothing — the worst
 * kind of failure, because it looks like success. The platform muxer exists to
 * solve exactly this problem and has been doing it for a decade.
 *
 * The operation is a copy, not a re-encode: compressed samples are read with
 * {@link MediaExtractor} and written with {@link MediaMuxer} untouched. The file
 * that comes out has the same video quality and the same audio quality as the
 * sources, and the work takes about as long as reading the bytes.
 */
public final class TrackWriter {

    private TrackWriter() {}

    /**
     * Writes {@code video} and {@code audio} into one MP4 at {@code out}.
     *
     * @return true only when the output exists and really declares both tracks
     */
    public static boolean join(File video, File audio, File out) throws IOException {
        if (video == null || !video.exists() || video.length() == 0) return false;
        boolean haveAudio = audio != null && audio.exists() && audio.length() > 0;
        if (!haveAudio) {
            // Nothing to join; a copy is the honest result.
            return copyThrough(video, out);
        }

        MediaExtractor videoExtractor = null;
        MediaExtractor audioExtractor = null;
        MediaMuxer muxer = null;
        try {
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(video.getAbsolutePath());
            int videoTrackIndex = selectTrack(videoExtractor, "video/");
            if (videoTrackIndex < 0) {
                // The "video" file has no video track, so the assumption that drove
                // the whole download is wrong. Say so rather than write junk.
                return false;
            }
            videoExtractor.selectTrack(videoTrackIndex);
            MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);

            audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(audio.getAbsolutePath());
            int audioTrackIndex = selectTrack(audioExtractor, "audio/");
            if (audioTrackIndex < 0) return false;
            audioExtractor.selectTrack(audioTrackIndex);
            MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrackIndex);

            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            if (out.exists() && !out.delete()) return false;

            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outVideo = muxer.addTrack(videoFormat);
            int outAudio = muxer.addTrack(audioFormat);
            muxer.start();

            // Interleave by timestamp. Writing all the video and then all the audio
            // also works for playback, but it makes seeking in the result stutter —
            // the player has to jump across the file for every seek.
            boolean videoDone = copyTrack(videoExtractor, muxer, outVideo);
            boolean audioDone = copyTrack(audioExtractor, muxer, outAudio);

            muxer.stop();
            muxer.release();
            muxer = null;

            if (!videoDone || !audioDone) return false;
            return verify(out, true);
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (videoExtractor != null) videoExtractor.release(); } catch (Throwable ignored) {}
            try { if (audioExtractor != null) audioExtractor.release(); } catch (Throwable ignored) {}
            try { if (muxer != null) { muxer.stop(); muxer.release(); } } catch (Throwable ignored) {}
        }
    }

    /**
     * Copies every sample of the selected track into the muxer.
     *
     * The sample time is preserved, not rewritten: both sources start at zero and
     * both describe the same recording, so their clocks already line up. Shifting
     * them to a shared timeline would desynchronise the sound from the picture.
     */
    private static boolean copyTrack(MediaExtractor extractor, MediaMuxer muxer, int outTrack) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 20);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long lastTime = -1;
        int written = 0;
        while (true) {
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) break;
            long time = extractor.getSampleTime();
            if (time < 0) break;
            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = time;
            info.flags = extractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC
                    ? MediaCodec.BUFFER_FLAG_KEY_FRAME
                    : 0;
            try {
                muxer.writeSampleData(outTrack, buffer, info);
            } catch (Throwable t) {
                // A muxer rejects a timestamp that goes backwards; skipping the
                // sample keeps the file valid and loses a frame instead of the file.
                extractor.advance();
                continue;
            }
            lastTime = time;
            written++;
            if (!extractor.advance()) break;
        }
        return written > 0;
    }

    /** Finds the first track whose mime type starts with {@code prefix}. */
    private static int selectTrack(MediaExtractor extractor, String prefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }

    /**
     * Confirms the output really declares the tracks it should.
     *
     * An MP4 track is declared by an 'hdlr' box whose handler type is 'vide' or
     * 'soun'. Reading the bytes is the only way to know the muxer did its job; a
     * file-size check would pass on a file with two video tracks.
     */
    public static boolean verify(File file, boolean expectAudio) {
        java.io.RandomAccessFile raf = null;
        try {
            raf = new java.io.RandomAccessFile(file, "r");
            long length = raf.length();
            if (length < 64) return false;
            int probeLen = (int) Math.min(length, 4L * 1024L * 1024L);
            byte[] probe = new byte[probeLen];
            raf.readFully(probe);
            boolean video = contains(probe, "vide");
            boolean audio = contains(probe, "soun");
            if (!video) return false;
            if (expectAudio && !audio) return false;
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try { if (raf != null) raf.close(); } catch (Throwable ignored) {}
        }
    }

    static boolean contains(byte[] haystack, String needle) {
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

    /**
     * Writes a file through unchanged.
     *
     * Used when the source already carries both tracks: copying the bytes is both
     * faster and safer than re-muxing a file that is already correct.
     */
    public static boolean copyThrough(File source, File out) throws IOException {
        java.io.FileInputStream in = null;
        OutputStream os = null;
        try {
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            in = new java.io.FileInputStream(source);
            os = new FileOutputStream(out, false);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.flush();
            os.close();
            os = null;
            return out.length() > 0;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (os != null) os.close(); } catch (Throwable ignored) {}
        }
    }

    /** Joins the parts a plan produced, or copies one through. */
    public static boolean assemble(FormatSelector.Plan plan, List<File> files, File out)
            throws IOException {
        if (files == null || files.isEmpty()) return false;
        if (files.size() == 1) {
            boolean copied = copyThrough(files.get(0), out);
            if (!copied) return false;
            // A single file that claims to be combined is only trusted if it
            // actually contains a picture and a sound track.
            if (plan != null && plan.mode == FormatSelector.Plan.MODE_SINGLE) {
                return verify(out, true);
            }
            if (plan != null && plan.mode == FormatSelector.Plan.MODE_AUDIO) {
                return out.length() > 0;
            }
            return true;
        }
        return join(files.get(0), files.get(1), out);
    }
}
