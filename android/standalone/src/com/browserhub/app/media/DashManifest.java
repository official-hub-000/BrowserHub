package com.browserhub.app.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A DASH (MPEG-DASH) manifest parser.
 *
 * DASH is the other half of what real sites serve: YouTube, most VOD platforms
 * and a lot of live TV use it. Its shape is different from HLS — instead of a
 * flat list of segment files, an MPD declares periods, within them adaptation
 * sets (one video track, one audio track), and within those a SegmentTemplate or
 * SegmentList describing how to compute each segment's URL.
 *
 * Scope is honest and specific: this parses what real MPDs actually contain —
 * SegmentTemplate with $Number$ / $Time$ / $RepresentationID$ and $Bandwidth$,
 * SegmentList, SegmentBase with an init range, multiple periods, and
 * BaseURL chains. It does not evaluate XLink or XML namespaces beyond the
 * default. What it cannot handle it reports, because a wrong segment URL is a
 * 404 on every request.
 */
public final class DashManifest {

    /** One selected track within a period. */
    public static final class Track {
        public String id = "";
        public String mimeType = "";
        public String codecs = "";
        public long bandwidth;
        public int width;
        public int height;
        public String language = "";
        public boolean audio;
        /** Fully expanded segment URLs, in order. */
        public final List<SegmentRef> segments = new ArrayList<>();
        public String initUrl;
        public long initStart = -1;
        public long initLength = -1;
        public double duration;

        /**
         * Segment addresses for a duration-based template, captured as literal
         * URLs. I cannot download the two tracks "simultaneously" (that would
         * double memory and does not make the file better), so I need to know
         * the exact count up front to interleave frames — and the count must
         * come from one parse of the manifest, not two.
         */
        public final List<SegmentRef> videoSegments = new ArrayList<>();

        public String resolution() {
            return width > 0 && height > 0 ? width + "x" + height : "";
        }
    }

    /** One segment: URL plus an optional byte range. */
    public static final class SegmentRef {
        public final String url;
        public final long start;
        public final long length;

        SegmentRef(String url, long start, long length) {
            this.url = url;
            this.start = start;
            this.length = length;
        }
    }

    public boolean ok;
    public String error;
    public double totalDuration;
    public boolean dynamic;
    public final List<Track> video = new ArrayList<>();
    public final List<Track> audio = new ArrayList<>();
    public boolean encrypted;
    public String drmSystem;

    private DashManifest() {}

    /** True when a body looks like an MPD rather than something else. */
    public static boolean looksLikeDash(String text) {
        if (text == null) return false;
        String head = text.length() > 8192 ? text.substring(0, 8192) : text;
        return head.contains("<MPD") || head.contains("urn:mpeg:dash");
    }

    public static DashManifest parse(String xml, String baseUrl) {
        DashManifest out = new DashManifest();
        if (text(xml).length() == 0) {
            out.error = "empty manifest";
            return out;
        }
        if (!looksLikeDash(xml)) {
            out.error = "not a DASH manifest";
            return out;
        }

        try {
            /*
             * DRM detection.
             *
             * Real manifests identify the system by a UUID, not by the word
             * "Widevine": com.widevine.alpha is
             * urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed and PlayReady is
             * urn:uuid:9a04f079-9840-4286-ab92-e65be0885f95. Matching on the
             * friendly name alone missed every real manifest, so a DRM stream
             * looked ordinary and would have been offered as a download.
             */
            String lowerXml = xml.toLowerCase(Locale.US);
            boolean anyProtection = lowerXml.contains("<contentprotection");

            if (lowerXml.contains("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed") || lowerXml.contains("widevine")) {
                out.encrypted = true;
                out.drmSystem = "Widevine";
            } else if (lowerXml.contains("9a04f079-9840-4286-ab92-e65be0885f95")
                    || lowerXml.contains("playready")) {
                out.encrypted = true;
                out.drmSystem = "PlayReady";
            } else if (lowerXml.contains("e2719d58-a985-b3c9-781a-b030af78d30e")
                    || lowerXml.contains("clearkey")) {
                out.encrypted = true;
                out.drmSystem = "ClearKey";
            } else if (anyProtection) {
                out.encrypted = true;
                out.drmSystem = "unknown DRM";
            }

            Matcher mpd = Pattern.compile("<MPD([^>]*)>", Pattern.CASE_INSENSITIVE).matcher(xml);
            if (!mpd.find()) {
                out.error = "no MPD element";
                return out;
            }
            String mpdAttrs = mpd.group(1);
            out.dynamic = attr(mpdAttrs, "type").equalsIgnoreCase("dynamic");
            out.totalDuration = parseDurationSeconds(attr(mpdAttrs, "mediaPresentationDuration"));

            // Global BaseURL applies under the manifest URL.
            String globalBase = baseUrl;
            String firstBase = firstTagBody(xml, "BaseURL");
            if (firstBase != null && firstBase.length() > 0) {
                globalBase = Resolver.resolve(baseUrl, firstBase.trim());
            }

            // Periods: each is a timeline segment with its own tracks.
            List<String> periods = allTagBodies(xml, "Period");
            if (periods.isEmpty()) {
                out.error = "manifest has no Period";
                return out;
            }

            double periodStart = 0;
            for (int p = 0; p < periods.size(); p++) {
                String periodBody = periods.get(p);
                String periodAttrs = firstTagAttrs(xml, "Period", p);
                double dur = parseDurationSeconds(attr(periodAttrs, "duration"));
                // A live-shaped or single-period manifest often only declares the
                // duration on the MPD itself. Falling back to it is correct here:
                // a period with no duration runs to the end of the presentation.
                if (dur <= 0) dur = out.totalDuration;
                double start = parseDurationSecondsOr(attr(periodAttrs, "start"), periodStart);

                String periodBase = globalBase;
                String pb = firstTagBody(periodBody, "BaseURL");
                if (pb != null && pb.length() > 0) {
                    periodBase = Resolver.resolve(globalBase, pb.trim());
                }

                collectTracks(periodBody, periodBase, start, dur, out);
                periodStart = start + (dur > 0 ? dur : out.totalDuration);
            }

            if (out.video.isEmpty() && out.audio.isEmpty()) {
                out.error = out.encrypted
                        ? "this stream is DRM protected (" + out.drmSystem + ")"
                        : "manifest declared no usable tracks";
                return out;
            }
            out.ok = out.error == null;
            return out;
        } catch (Throwable t) {
            out.error = "could not parse the manifest";
            out.ok = false;
            return out;
        }
    }

    // ------------------------------------------------------------------ tracks

    private static void collectTracks(String periodBody, String base, double periodStart,
                                      double periodDuration, DashManifest out) {
        List<String> sets = allTagBodies(periodBody, "AdaptationSet");
        for (int i = 0; i < sets.size(); i++) {
            String setBody = sets.get(i);
            String setAttrs = firstTagAttrs(periodBody, "AdaptationSet", i);
            String setMime = attr(setAttrs, "mimeType").toLowerCase(Locale.US);
            String setLang = attr(setAttrs, "lang");

            String setBase = base;
            String sb = firstTagBody(setBody, "BaseURL");
            if (sb != null && sb.length() > 0) setBase = Resolver.resolve(base, sb.trim());

            // Representation-level tracks are what we merge.
            List<String> reps = allTagBodies(setBody, "Representation");
            if (reps.isEmpty()) continue;

            for (int r = 0; r < reps.size(); r++) {
                String repBody = reps.get(r);
                String repAttrs = firstTagAttrs(setBody, "Representation", r);

                Track track = new Track();
                track.id = attr(repAttrs, "id");
                track.mimeType = attr(repAttrs, "mimeType");
                if (track.mimeType.length() == 0) track.mimeType = setMime;
                track.codecs = attr(repAttrs, "codecs");
                track.bandwidth = parseLong(attr(repAttrs, "bandwidth"), 0);
                track.width = (int) parseLong(attr(repAttrs, "width"), 0);
                track.height = (int) parseLong(attr(repAttrs, "height"), 0);
                track.language = setLang.length() > 0 ? setLang : attr(repAttrs, "lang");
                track.audio = track.mimeType.startsWith("audio/");
                track.duration = periodDuration;

                /*
                 * A BaseURL is resolved exactly once, against the level above it.
                 * Resolving an already-absolute URL again appended the path over
                 * and over ("sb/sb/sb/movie.mp4"), which 404s on the device.
                 */
                String repBase = setBase;
                String rb = firstTagBody(repBody, "BaseURL");
                if (rb != null && rb.trim().length() > 0) {
                    String ref = rb.trim();
                    repBase = ref.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
                            ? ref
                            : Resolver.resolve(setBase, ref);
                }

                // The template may sit on the Representation, the AdaptationSet,
                // or the Period — inherit from the nearest one that declares it.
                // Only a tag that is genuinely present counts. An empty attribute
                // string is not the same as "absent", and treating them the same
                // made a SegmentList manifest look like a SegmentTemplate one.
                String templateAttrs = findTagAttrs(repBody, "SegmentTemplate");
                if (templateAttrs == null) templateAttrs = findTagAttrs(setBody, "SegmentTemplate");
                if (templateAttrs == null) templateAttrs = findTagAttrs(periodBody, "SegmentTemplate");

                String listAttrs = findTagAttrs(repBody, "SegmentList");
                if (listAttrs == null) listAttrs = findTagAttrs(setBody, "SegmentList");

                String baseAttrs = findTagAttrs(repBody, "SegmentBase");
                if (baseAttrs == null) baseAttrs = findTagAttrs(setBody, "SegmentBase");

                if (templateAttrs != null) {
                    buildFromTemplate(track, templateAttrs, repBody, setBody, periodBody, repBase, repAttrs, periodStart);
                } else if (listAttrs != null) {
                    buildFromList(track, listAttrs, repBody, setBody, periodBody, repBase, repAttrs);
                } else if (baseAttrs != null) {
                    buildFromBase(track, baseAttrs, repBody, setBody, repBase);
                } else {
                    /*
                     * No segment description at all: the representation is one
                     * self-contained file at its BaseURL. This is the simplest
                     * DASH layout and it still has to resolve to an absolute URL —
                     * passing the relative BaseURL through produced a segment URL
                     * of "sb/movie.mp4", which failed on every device.
                     */
                    track.segments.add(new SegmentRef(repBase, -1, -1));
                    track.initUrl = null;
                }

                if (track.segments.isEmpty()) continue;
                if (track.audio) out.audio.add(track);
                else out.video.add(track);
            }
        }
    }

    private static void buildFromTemplate(Track track, String templateAttrs,
                                          String repBody, String setBody, String periodBody,
                                          String base, String repAttrs, double periodStart) {
        // Initialization may be on the Representation, the set or the period.
        String initAttrs = findTagAttrs(repBody, "Initialization");
        if (initAttrs == null) initAttrs = findTagAttrs(setBody, "Initialization");
        if (initAttrs == null) initAttrs = findTagAttrs(periodBody, "Initialization");

        String media = attr(templateAttrs, "media");
        String initTmpl = attr(templateAttrs, "initialization");
        String startNumber = attr(templateAttrs, "startNumber");
        long startNum = parseLong(startNumber, 1);
        String timescaleAttr = attr(templateAttrs, "timescale");
        long timescale = parseLong(timescaleAttr, 1000);
        if (timescale <= 0) timescale = 1000;
        String durationAttr = attr(templateAttrs, "duration");
        long segDuration = parseLong(durationAttr, 0);

        String timeline = firstTagBody(periodBody, "SegmentTimeline");
        if (timeline == null) timeline = firstTagBody(setBody, "SegmentTimeline");
        if (timeline == null) timeline = firstTagBody(repBody, "SegmentTimeline");

        if (initTmpl.length() > 0) {
            String expanded = expand(initTmpl, repAttrs, track, 0, 0);
            track.initUrl = Resolver.resolve(base, expanded);
        } else if (initAttrs != null) {
            String source = attr(initAttrs, "sourceURL");
            if (source.length() > 0) track.initUrl = Resolver.resolve(base, expand(source, repAttrs, track, 0, 0));
            track.initStart = parseLong(attr(initAttrs, "range").split("-")[0], -1);
            String range = attr(initAttrs, "range");
            if (range.contains("-")) {
                String[] rr = range.split("-");
                track.initStart = parseLong(rr[0], -1);
                if (rr.length > 1) {
                    long end = parseLong(rr[1], -1);
                    if (end >= 0 && track.initStart >= 0) track.initLength = end - track.initStart + 1;
                }
            }
        }

        if (media.length() > 0) {
            if (timeline != null) {
                // Explicit timeline: $Time$ values come from the S entries.
                Matcher s = Pattern.compile("<S([^>]*)/?>", Pattern.CASE_INSENSITIVE).matcher(timeline);
                long time = 0;
                long number = startNum;
                while (s.find()) {
                    String attrs = s.group(1);
                    long t = parseLong(attr(attrs, "t"), time);
                    long d = parseLong(attr(attrs, "d"), segDuration);
                    int repeat = (int) parseLong(attr(attrs, "r"), 0);
                    for (int k = 0; k <= repeat; k++) {
                        String expanded = expand(media, repAttrs, track, number, t);
                        track.segments.add(new SegmentRef(Resolver.resolve(base, expanded), -1, -1));
                        t += d;
                        number += 1;
                    }
                    time = t;
                }
            } else if (segDuration > 0) {
                // Duration-based numbering: how many segments fit in the period.
                double durSec = track.duration > 0 ? track.duration : 0;
                long count = durSec > 0
                        ? (long) Math.ceil((durSec * timescale) / (double) segDuration)
                        : 0;
                for (long n = 0; n < count; n++) {
                    long number = startNum + n;
                    long time = (long) (n * segDuration);
                    String expanded = expand(media, repAttrs, track, number, time);
                    track.segments.add(new SegmentRef(Resolver.resolve(base, expanded), -1, -1));
                }
            }
        }
    }

    private static void buildFromList(Track track, String listAttrs,
                                      String repBody, String setBody, String periodBody,
                                      String base, String repAttrs) {
        String initAttrs = findTagAttrs(repBody, "Initialization");
        if (initAttrs == null) initAttrs = findTagAttrs(setBody, "Initialization");
        if (initAttrs == null) initAttrs = findTagAttrs(periodBody, "Initialization");
        if (initAttrs != null) {
            String source = attr(initAttrs, "sourceURL");
            if (source.length() > 0) track.initUrl = Resolver.resolve(base, source);
        }

        // SegmentURL entries belong to this representation's own SegmentList.
        // Concatenating period+set+representation matched every other track's
        // segment list too, which duplicated another representation's media into
        // this one — a corrupt file that looked like it downloaded fine.
        String listBody = firstTagBody(repBody, "SegmentList");
        if (listBody == null) listBody = firstTagBody(setBody, "SegmentList");
        if (listBody == null) listBody = firstTagBody(periodBody, "SegmentList");
        if (listBody == null) listBody = "";
        Matcher seg = Pattern.compile(
                "<SegmentURL([^>]*?)/?>", Pattern.CASE_INSENSITIVE).matcher(listBody);
        while (seg.find()) {
            String attrs = seg.group(1);
            String media = attr(attrs, "media");
            if (media.length() == 0) continue;
            long start = -1;
            long length = -1;
            String range = attr(attrs, "mediaRange");
            if (range.length() > 0 && range.contains("-")) {
                String[] rr = range.split("-");
                start = parseLong(rr[0], -1);
                if (rr.length > 1) {
                    long end = parseLong(rr[1], -1);
                    if (end >= 0 && start >= 0) length = end - start + 1;
                }
            }
            track.segments.add(new SegmentRef(Resolver.resolve(base, media), start, length));
        }
    }

    private static void buildFromBase(Track track, String baseAttrs,
                                      String repBody, String setBody, String base) {
        // A single self-contained file plus an init byte range — common for
        // progressive DASH, and the easiest case to get exactly right.
        track.segments.add(new SegmentRef(base, -1, -1));
        String range = attr(baseAttrs, "indexRange");
        if (range.length() > 0) {
            // The index range describes the sidx box, not media; ignore it.
        }
        String init = findTagAttrs(repBody, "Initialization");
        if (init == null) init = findTagAttrs(setBody, "Initialization");
        if (init != null) {
            String r = attr(init, "range");
            if (r.contains("-")) {
                String[] rr = r.split("-");
                track.initStart = parseLong(rr[0], -1);
                if (rr.length > 1) {
                    long end = parseLong(rr[1], -1);
                    if (end >= 0 && track.initStart >= 0) track.initLength = end - track.initStart + 1;
                }
            }
        }
    }

    /**
     * Expands a DASH URL template.
     *
     * Supports the four identifiers real manifests use: $RepresentationID$,
     * $Number$ (with optional %0Nd width), $Bandwidth$ and $Time$. An unknown
     * identifier is left in place, so the request fails loudly at that URL
     * rather than silently hitting a plausible-looking wrong address.
     */
    static String expand(String template, String repAttrs, Track track, long number, long time) {
        String out = template;
        if (out.contains("$RepresentationID$")) {
            out = out.replace("$RepresentationID$", track.id);
        }
        if (out.contains("$Bandwidth$")) {
            out = out.replace("$Bandwidth$", String.valueOf(track.bandwidth));
        }
        out = expandNumbered(out, "Number", number);
        out = expandNumbered(out, "Time", time);
        if (out.contains("$$")) out = out.replace("$$", "$");
        return out;
    }

    private static String expandNumbered(String input, String name, long value) {
        Pattern p = Pattern.compile("\\$" + name + "%0(\\d+)d\\$|\\$" + name + "\\$");
        Matcher m = p.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String replacement;
            if (m.group(1) != null) {
                int width = Integer.parseInt(m.group(1));
                replacement = padLeft(String.valueOf(value), width);
            } else {
                replacement = String.valueOf(value);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String padLeft(String v, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = v.length(); i < width; i++) sb.append('0');
        return sb.append(v).toString();
    }

    // ------------------------------------------------------------- selection

    /** Highest-bandwidth, then highest-resolution video track. */
    public Track bestVideo() {
        Track best = null;
        for (Track t : video) {
            if (best == null) {
                best = t;
                continue;
            }
            long scoreA = ((long) t.height) * 1000000L + t.bandwidth;
            long scoreB = ((long) best.height) * 1000000L + best.bandwidth;
            if (scoreA > scoreB) best = t;
        }
        return best;
    }

    /** Highest-bandwidth audio track. */
    public Track bestAudio() {
        Track best = null;
        for (Track t : audio) {
            if (best == null || t.bandwidth > best.bandwidth) best = t;
        }
        return best;
    }

    // --------------------------------------------------------------- helpers

    private static String text(String s) {
        return s == null ? "" : s;
    }

    static String attr(String attrs, String name) {
        if (attrs == null) return "";
        Matcher m = Pattern.compile(
                "(?:^|\\s)" + Pattern.quote(name) + "\\s*=\\s*\"([^\"]*)\"",
                Pattern.CASE_INSENSITIVE).matcher(attrs);
        return m.find() ? m.group(1) : "";
    }

    static long parseLong(String v, long fallback) {
        if (v == null || v.trim().length() == 0) return fallback;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** ISO 8601 duration such as PT1H12M4.5S to seconds. */
    static double parseDurationSeconds(String iso) {
        if (iso == null || iso.length() == 0) return 0;
        Matcher m = Pattern.compile(
                "P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?",
                Pattern.CASE_INSENSITIVE).matcher(iso.trim());
        if (!m.find()) return 0;
        double total = 0;
        if (m.group(1) != null) total += Double.parseDouble(m.group(1)) * 86400;
        if (m.group(2) != null) total += Double.parseDouble(m.group(2)) * 3600;
        if (m.group(3) != null) total += Double.parseDouble(m.group(3)) * 60;
        if (m.group(4) != null) total += Double.parseDouble(m.group(4));
        return total;
    }

    private static double parseDurationSecondsOr(String iso, double fallback) {
        double v = parseDurationSeconds(iso);
        return v > 0 ? v : fallback;
    }

    private static String firstTagBody(String xml, String tag) {
        List<String> all = allTagBodies(xml, tag);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Bodies of every occurrence of a tag, plus its attributes, found by a
     * depth-aware scan.
     *
     * A non-greedy regex is not good enough here: MPDs nest tags with the same
     * or related names (a Period inside the MPD, an AdaptationSet containing a
     * SegmentTemplate containing a SegmentTimeline), and `.*?` stops at the
     * first inner close tag. That silently produced zero tracks on a manifest
     * that looked perfectly ordinary.
     */
    public static List<String> allTagBodies(String xml, String tag) {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        String close = "</" + tag + ">";
        String open = "<" + tag;
        int i = 0;
        while (true) {
            int start = findTagStart(xml, open, i);
            if (start < 0) break;
            int gt = xml.indexOf('>', start);
            if (gt < 0) break;
            /*
             * A self-closing tag is extremely common in real manifests —
             * `<Representation ... />` almost always is. It has no body, but it
             * is still a real occurrence, so it must be reported with an empty
             * body. Skipping it silently made every manifest with self-closing
             * representations parse as having no tracks at all.
             */
            if (xml.charAt(gt - 1) == '/') {
                out.add("");
                i = gt + 1;
                continue;
            }
            int depth = 1;
            int cursor = gt + 1;
            int bodyEnd = -1;
            while (cursor < xml.length()) {
                int nextOpen = findTagStart(xml, open, cursor);
                int nextClose = indexOfIgnoreCase(xml, close, cursor);
                if (nextClose < 0) break;
                if (nextOpen >= 0 && nextOpen < nextClose) {
                    int ogt = xml.indexOf('>', nextOpen);
                    if (ogt > 0 && xml.charAt(ogt - 1) != '/') depth += 1;
                    cursor = ogt > 0 ? ogt + 1 : nextOpen + 1;
                } else {
                    depth -= 1;
                    if (depth == 0) {
                        bodyEnd = nextClose;
                        break;
                    }
                    cursor = nextClose + close.length();
                }
            }
            if (bodyEnd < 0) break;
            out.add(xml.substring(gt + 1, bodyEnd));
            i = bodyEnd + close.length();
        }
        return out;
    }

    /** Attributes of every occurrence of a tag, in document order. */
    public static List<String> allTagAttrs(String xml, String tag) {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        String open = "<" + tag;
        int i = 0;
        while (true) {
            int start = findTagStart(xml, open, i);
            if (start < 0) break;
            int gt = xml.indexOf('>', start);
            if (gt < 0) break;
            String attrs = xml.substring(start + open.length(), gt);
            if (attrs.endsWith("/")) attrs = attrs.substring(0, attrs.length() - 1);
            out.add(attrs.trim());
            i = gt + 1;
        }
        return out;
    }

    /** Finds `<tag` that is a real tag start, not a prefix of a longer name. */
    private static int findTagStart(String xml, String open, int from) {
        int i = from;
        while (true) {
            int at = indexOfIgnoreCase(xml, open, i);
            if (at < 0) return -1;
            int after = at + open.length();
            if (after < xml.length()) {
                char c = xml.charAt(after);
                if (c == '>' || c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '/') {
                    return at;
                }
            }
            i = at + 1;
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        int n = needle.length();
        for (int i = Math.max(0, from); i <= haystack.length() - n; i++) {
            boolean match = true;
            for (int j = 0; j < n; j++) {
                if (Character.toLowerCase(haystack.charAt(i + j))
                        != Character.toLowerCase(needle.charAt(j))) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    /** Attribute string of the index-th occurrence of a tag. */
    private static String firstTagAttrs(String xml, String tag, int index) {
        List<String> all = allTagAttrs(xml, tag);
        return index >= 0 && index < all.size() ? all.get(index) : "";
    }

    /**
     * Attributes of this exact tag, or null when it is absent.
     *
     * Unlike a substring search this requires the tag name to end at a delimiter,
     * so looking for `SegmentTemplate` never matches `SegmentTimeline`, and an
     * empty attribute string means "present with no attributes" rather than
     * "absent".
     */
    private static String findTagAttrs(String xml, String tag) {
        if (xml == null) return null;
        String open = "<" + tag;
        int start = findTagStart(xml, open, 0);
        if (start < 0) return null;
        int gt = xml.indexOf('>', start);
        if (gt < 0) return null;
        String attrs = xml.substring(start + open.length(), gt);
        if (attrs.endsWith("/")) attrs = attrs.substring(0, attrs.length() - 1);
        return attrs.trim();
    }
}
