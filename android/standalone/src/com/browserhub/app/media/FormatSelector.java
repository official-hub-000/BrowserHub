package com.browserhub.app.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Format selection, written to yt-dlp's rules.
 *
 * This is the one piece of the reference implementation worth copying exactly,
 * because it is the part that decides whether a download produces a playable
 * file. yt-dlp's model, which the app being compared to adopts wholesale:
 *
 *   vcodec / acodec are strings, and the literal value "none" means "absent".
 *   A format can be video-only, audio-only, or combined — and combined formats
 *   beat every other consideration, because they need no second download.
 *
 * The reference implementation's download invocation is the whole idea:
 *
 *     -f "&lt;video&gt;+bestaudio" --merge-output-format mp4
 *
 * That is: pick the best picture, pick the best sound, download both, merge. It
 * works on every site yt-dlp supports, which is why the app does not need a
 * per-site parser. This class does the selection half; {@link TrackWriter} and
 * {@link AacTranscoder} do the joining half in-process.
 *
 * Every rule here is a rule that, when missing, produces a file that plays
 * silently, plays nothing, or is labelled one quality and contains another.
 */
public final class FormatSelector {

    private FormatSelector() {}

    /** Whether a codec field means "this track is absent". */
    public static boolean isNone(String codec) {
        return codec == null || codec.length() == 0
                || "none".equals(codec.toLowerCase(Locale.US))
                || "null".equals(codec.toLowerCase(Locale.US));
    }

    /** A format carrying both picture and sound. */
    public static boolean isCombined(YouTubeExtractor.Stream s) {
        return s != null && !isNone(s.videoCodec) && !isNone(s.audioCodec);
    }

    /** Picture only: needs sound from somewhere else. */
    public static boolean isVideoOnly(YouTubeExtractor.Stream s) {
        return s != null && !isNone(s.videoCodec) && isNone(s.audioCodec);
    }

    /** Sound only. */
    public static boolean isAudioOnly(YouTubeExtractor.Stream s) {
        return s != null && isNone(s.videoCodec) && !isNone(s.audioCodec);
    }

    /**
     * The best picture that already has sound, or null.
     *
     * This is the preferred outcome and the reason it is checked first: one
     * download, one file, no merging, nothing to go wrong halfway.
     */
    public static YouTubeExtractor.Stream bestCombined(List<YouTubeExtractor.Stream> formats) {
        YouTubeExtractor.Stream best = null;
        for (YouTubeExtractor.Stream s : formats) {
            if (s == null || s.url == null || s.url.length() == 0) continue;
            if (!isCombined(s)) continue;
            if (best == null || higher(s, best)) best = s;
        }
        return best;
    }

    /** The best picture-only track, or null. */
    public static YouTubeExtractor.Stream bestVideoOnly(List<YouTubeExtractor.Stream> formats) {
        YouTubeExtractor.Stream best = null;
        for (YouTubeExtractor.Stream s : formats) {
            if (s == null || s.url == null || s.url.length() == 0) continue;
            if (!isVideoOnly(s)) continue;
            if (best == null || higher(s, best)) best = s;
        }
        return best;
    }

    /**
     * The best sound track, or null.
     *
     * Preference order matters and is not just bitrate: an AAC track is preferred
     * over an Opus track at the same bitrate, because AAC can be written into an
     * MP4 without re-encoding, and a re-encode is both slow and lossy. Opus wins
     * only when it is the only option, and then it needs conversion.
     */
    public static YouTubeExtractor.Stream bestAudio(List<YouTubeExtractor.Stream> formats) {
        YouTubeExtractor.Stream best = null;
        for (YouTubeExtractor.Stream s : formats) {
            if (s == null || s.url == null || s.url.length() == 0) continue;
            if (!isAudioOnly(s)) continue;
            if (best == null || betterAudio(s, best)) best = s;
        }
        return best;
    }

    /**
     * Chooses what to download, as a pair.
     *
     * Mirrors `-f "bestvideo+bestaudio"` with yt-dlp's own escalation: if a
     * combined format exists at all it is used, because that is one request
     * instead of two and no merge that can fail.
     */
    public static Plan plan(List<YouTubeExtractor.Stream> formats) {
        Plan plan = new Plan();
        if (formats == null || formats.isEmpty()) return plan;

        plan.combined = bestCombined(formats);
        if (plan.combined != null) {
            plan.mode = Plan.MODE_SINGLE;
            return plan;
        }

        plan.video = bestVideoOnly(formats);
        plan.audio = bestAudio(formats);
        if (plan.video != null && plan.audio != null) {
            plan.mode = Plan.MODE_MERGE;
        } else if (plan.video != null) {
            // Picture with no sound anywhere: still the only thing offered.
            plan.mode = Plan.MODE_SINGLE;
        } else if (plan.audio != null) {
            plan.mode = Plan.MODE_AUDIO;
        }
        return plan;
    }

    /** What the downloader should do, decided before any bytes are fetched. */
    public static final class Plan {
        public static final int MODE_NONE = 0;
        /** One file, picture and sound together. */
        public static final int MODE_SINGLE = 1;
        /** Two tracks that must be joined. */
        public static final int MODE_MERGE = 2;
        /** Sound only. */
        public static final int MODE_AUDIO = 3;

        public int mode = MODE_NONE;
        public YouTubeExtractor.Stream combined;
        public YouTubeExtractor.Stream video;
        public YouTubeExtractor.Stream audio;

        public boolean ok() {
            return mode != MODE_NONE;
        }

        /** The streams to fetch, in order. */
        public List<YouTubeExtractor.Stream> streams() {
            List<YouTubeExtractor.Stream> out = new ArrayList<>();
            if (mode == MODE_SINGLE && combined != null) out.add(combined);
            if (mode == MODE_MERGE) {
                if (video != null) out.add(video);
                if (audio != null) out.add(audio);
            }
            if (mode == MODE_AUDIO && audio != null) out.add(audio);
            return out;
        }
    }

    /**
     * Higher is better: resolution first, then frame rate, then bitrate.
     *
     * Resolution before bitrate because a 1080p stream at a lower bitrate is
     * still more picture than a 720p stream at a higher one, and users read the
     * label, not the number.
     */
    static boolean higher(YouTubeExtractor.Stream a, YouTubeExtractor.Stream b) {
        int ah = heightOf(a), bh = heightOf(b);
        if (ah != bh) return ah > bh;
        if (a.fps != b.fps) return a.fps > b.fps;
        if (a.bitrate != b.bitrate) return a.bitrate > b.bitrate;
        // A url needing no signature work is worth choosing when all else ties.
        return b.needsSignature && !a.needsSignature;
    }

    /**
     * Sound choice: an MP4-compatible track first, then the highest bitrate.
     *
     * This is deliberately not "highest bitrate wins". Opus is usually offered at a
     * higher bitrate than AAC, but MP4 cannot carry Opus without converting it, and
     * converting means decoding and re-encoding audio — slow, and lossy for no
     * audible gain on a phone speaker. A 128 kbps AAC track stored directly is a
     * better result than a 160 kbps Opus track that has to be re-encoded to become
     * playable in the same file as the picture.
     *
     * When neither is AAC, or both are, bitrate decides.
     */
    static boolean betterAudio(YouTubeExtractor.Stream a, YouTubeExtractor.Stream b) {
        boolean aFits = fitsMp4(a), bFits = fitsMp4(b);
        if (aFits != bFits) return aFits;
        long aRate = a.bitrate > 0 ? a.bitrate : (long) a.sampleRate * 2L;
        long bRate = b.bitrate > 0 ? b.bitrate : (long) b.sampleRate * 2L;
        return aRate > bRate;
    }

    /** True when a track can be stored in MP4 without decoding and re-encoding. */
    public static boolean fitsMp4(YouTubeExtractor.Stream s) {
        return isAac(s);
    }

    /** True when a track's codec can be written into an MP4 without re-encoding. */
    public static boolean isAac(YouTubeExtractor.Stream s) {
        if (s == null) return false;
        String c = s.audioCodec == null ? "" : s.audioCodec.toLowerCase(Locale.US);
        String m = s.mimeType == null ? "" : s.mimeType.toLowerCase(Locale.US);
        return c.contains("mp4a") || c.contains("aac") || m.contains("mp4a");
    }

    /** True when the track is Opus, which MP4 cannot carry as-is. */
    public static boolean isOpus(YouTubeExtractor.Stream s) {
        if (s == null) return false;
        String c = s.audioCodec == null ? "" : s.audioCodec.toLowerCase(Locale.US);
        String m = s.mimeType == null ? "" : s.mimeType.toLowerCase(Locale.US);
        return c.contains("opus") || m.contains("opus");
    }

    /** Resolution from the numeric height, falling back to the label. */
    static int heightOf(YouTubeExtractor.Stream s) {
        if (s == null) return 0;
        if (s.height > 0) return s.height;
        String label = s.qualityLabel;
        if (label == null || label.length() == 0) return 0;
        int end = 0;
        while (end < label.length() && Character.isDigit(label.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(label.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The file extension the result should carry.
     *
     * Always mp4 when a merge happened, because that is what the joiner writes —
     * naming the file after the source container would be a lie about the bytes.
     */
    public static String outputExtension(Plan plan) {
        if (plan == null || !plan.ok()) return ".mp4";
        if (plan.mode == Plan.MODE_AUDIO) {
            return isAac(plan.audio) ? ".m4a" : ".m4a";
        }
        return ".mp4";
    }
}
