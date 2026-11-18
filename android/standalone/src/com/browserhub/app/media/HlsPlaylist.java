package com.browserhub.app.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal, strict HLS (RFC 8216) playlist parser.
 *
 * Scope is deliberate: this exists to turn a stream the user is already watching
 * into one file, so it implements what real sites actually serve — master
 * playlists with variant streams, media playlists with EXTINF/EXT-X-BYTERANGE/
 * EXT-X-MAP/EXT-X-KEY, and AES-128 segment encryption. It does not try to be a
 * general HLS stack.
 *
 * Two design rules:
 *   1. Never guess. An unsupported tag (SAMPLE-AES, DRM) makes the item
 *      unplayable-by-us and is reported, not silently skipped, because silently
 *      skipping would produce a file that looks fine and plays garbage.
 *   2. Everything is relative-URL resolved against the playlist that declared
 *      it, which is where naive implementations break.
 */
public final class HlsPlaylist {

    /** One variant stream from a master playlist. */
    public static final class Variant {
        public final String uri;
        public final long bandwidth;
        public final String resolution;
        public final String codecs;

        Variant(String uri, long bandwidth, String resolution, String codecs) {
            this.uri = uri;
            this.bandwidth = bandwidth;
            this.resolution = resolution;
            this.codecs = codecs;
        }
    }

    /** One media segment. */
    public static final class Segment {
        public final String uri;
        public final double duration;
        public final long rangeStart;
        public final long rangeLength;
        /** Byte range of the EXT-X-MAP init section, or -1 when not present. */
        public final long mapStart;
        public final long mapLength;

        Segment(String uri, double duration, long rangeStart, long rangeLength,
                long mapStart, long mapLength) {
            this.uri = uri;
            this.duration = duration;
            this.rangeStart = rangeStart;
            this.rangeLength = rangeLength;
            this.mapStart = mapStart;
            this.mapLength = mapLength;
        }
    }

    public boolean isMaster;
    public final List<Variant> variants = new ArrayList<>();
    public final List<Segment> segments = new ArrayList<>();
    /** Resolved first segment key URI, or null when unencrypted. */
    public String keyUri;
    public String keyMethod;
    public String keyIv;
    public String mapUri;
    /** Byte range of the init section, when EXT-X-MAP declared one. */
    public long mapStart = -1;
    public long mapLength = -1;
    public boolean encrypted;
    public boolean sampleAes;
    public boolean ended;
    public double totalDuration;
    public String error;

    private HlsPlaylist() {}

    /** True when this looks like an HLS playlist at all. */
    public static boolean looksLikeHls(String text) {
        if (text == null) return false;
        String head = text.length() > 4096 ? text.substring(0, 4096) : text;
        return head.contains("#EXTM3U") || head.contains("#EXTINF") || head.contains("#EXT-X-STREAM-INF");
    }

    /**
     * Parses a playlist. Never throws: malformed input lands in `error` so the
     * caller can show a reason instead of a stack trace.
     */
    public static HlsPlaylist parse(String text, String baseUrl) {
        HlsPlaylist out = new HlsPlaylist();
        if (text == null || !text.contains("#EXTM3U")) {
            out.error = "not an HLS playlist";
            return out;
        }

        String[] lines = text.split("\r?\n");
        long pendingBandwidth = 0;
        String pendingResolution = null;
        String pendingCodecs = null;
        double pendingDuration = 0;
        long pendingRangeStart = -1;
        long pendingRangeLength = -1;
        long mapStart = -1;
        long mapLength = -1;
        String pendingMapUri = null;

        try {
            for (int i = 0; i < lines.length; i++) {
                String raw = lines[i].trim();
                if (raw.length() == 0) continue;

                if (raw.startsWith("#EXT-X-STREAM-INF:")) {
                    out.isMaster = true;
                    String attrs = raw.substring("#EXT-X-STREAM-INF:".length());
                    pendingBandwidth = longAttr(attrs, "BANDWIDTH", 0);
                    pendingResolution = strAttr(attrs, "RESOLUTION");
                    pendingCodecs = strAttr(attrs, "CODECS");
                    continue;
                }
                if (raw.startsWith("#EXT-X-MEDIA:")) {
                    // Alternate audio renditions. We keep the video variant's own
                    // audio, so these are noted but not merged.
                    out.isMaster = true;
                    continue;
                }
                if (raw.startsWith("#EXTINF:")) {
                    String v = raw.substring("#EXTINF:".length());
                    int comma = v.indexOf(',');
                    if (comma >= 0) v = v.substring(0, comma);
                    try {
                        pendingDuration = Double.parseDouble(v.trim());
                    } catch (NumberFormatException e) {
                        pendingDuration = 0;
                    }
                    continue;
                }
                if (raw.startsWith("#EXT-X-BYTERANGE:")) {
                    String v = raw.substring("#EXT-X-BYTERANGE:".length()).trim();
                    String[] parts = v.split("@");
                    try {
                        pendingRangeLength = Long.parseLong(parts[0].trim());
                        pendingRangeStart = parts.length > 1 ? Long.parseLong(parts[1].trim()) : -1;
                    } catch (NumberFormatException e) {
                        out.error = "malformed byte range";
                        return out;
                    }
                    continue;
                }
                if (raw.startsWith("#EXT-X-MAP:")) {
                    String attrs = raw.substring("#EXT-X-MAP:".length());
                    String uri = strAttr(attrs, "URI");
                    if (uri != null) {
                        pendingMapUri = Resolver.resolve(baseUrl, uri);
                        String range = strAttr(attrs, "BYTERANGE");
                        if (range != null) {
                            String[] parts = range.split("@");
                            try {
                                mapLength = Long.parseLong(parts[0].trim());
                                mapStart = parts.length > 1 ? Long.parseLong(parts[1].trim()) : 0;
                            } catch (NumberFormatException e) {
                                mapStart = -1;
                                mapLength = -1;
                            }
                        }
                    }
                    continue;
                }
                if (raw.startsWith("#EXT-X-KEY:")) {
                    String attrs = raw.substring("#EXT-X-KEY:".length());
                    String method = strAttr(attrs, "METHOD");
                    if (method != null) {
                        out.keyMethod = method.toUpperCase(Locale.US);
                        if (out.keyMethod.contains("SAMPLE-AES")) {
                            out.sampleAes = true;
                        }
                        String uri = strAttr(attrs, "URI");
                        if (uri != null) out.keyUri = Resolver.resolve(baseUrl, uri);
                        String iv = strAttr(attrs, "IV");
                        if (iv != null) out.keyIv = iv;
                        out.encrypted = !"NONE".equals(out.keyMethod);
                    }
                    continue;
                }
                if (raw.startsWith("#EXT-X-ENDLIST")) {
                    out.ended = true;
                    continue;
                }
                if (raw.startsWith("#")) {
                    continue; // Other tags are irrelevant to concatenation.
                }

                // A non-comment line is a URI: a variant when the playlist is a
                // master, otherwise a segment.
                String resolved = Resolver.resolve(baseUrl, raw);
                if (out.isMaster) {
                    out.variants.add(new Variant(resolved, pendingBandwidth,
                            pendingResolution, pendingCodecs));
                    pendingBandwidth = 0;
                    pendingResolution = null;
                    pendingCodecs = null;
                } else {
                    out.segments.add(new Segment(
                            resolved, pendingDuration,
                            pendingRangeStart, pendingRangeLength,
                            pendingMapUri != null ? mapStart : -1,
                            pendingMapUri != null ? mapLength : -1));
                    if (pendingMapUri != null) {
                        out.mapUri = pendingMapUri;
                        out.mapStart = mapStart;
                        out.mapLength = mapLength;
                    }
                    pendingMapUri = null;
                    pendingDuration = 0;
                    pendingRangeStart = -1;
                    pendingRangeLength = -1;
                    out.totalDuration += lastDuration(out);
                }
            }
        } catch (Throwable t) {
            out.error = "could not parse playlist";
            return out;
        }

        if (out.isMaster && out.variants.isEmpty()) out.error = "master playlist had no variants";
        if (!out.isMaster && out.segments.isEmpty()) out.error = "playlist had no segments";
        return out;
    }

    private static double lastDuration(HlsPlaylist p) {
        if (p.segments.isEmpty()) return 0;
        return p.segments.get(p.segments.size() - 1).duration;
    }

    /**
     * Picks the variant with the highest resolution, then the highest bandwidth.
     *
     * This mirrors what a user means by "download it": the best quality the site
     * offers. Snapping to the lowest would be dishonest about a download.
     */
    public Variant bestVariant() {
        if (variants.isEmpty()) return null;
        Variant best = null;
        for (Variant v : variants) {
            if (best == null || rank(v) > rank(best)) best = v;
        }
        return best;
    }

    /** Same pick, but constrained to a maximum height so the file stays sane. */
    public Variant variantAtMost(int maxHeight) {
        if (variants.isEmpty()) return null;
        Variant best = null;
        for (Variant v : variants) {
            int h = height(v.resolution);
            if (h > 0 && h > maxHeight) continue;
            if (best == null || rank(v) > rank(best)) best = v;
        }
        return best != null ? best : bestVariant();
    }

    private static long rank(Variant v) {
        // Resolution outranks bandwidth; tie-break on bandwidth.
        return ((long) height(v.resolution)) * 100000000L + v.bandwidth;
    }

    private static int height(String resolution) {
        if (resolution == null) return 0;
        int x = resolution.indexOf('x');
        if (x < 0) return 0;
        try {
            return Integer.parseInt(resolution.substring(x + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String strAttr(String attrs, String name) {
        Pattern p = Pattern.compile("(?:^|,)" + Pattern.quote(name) + "=(\"([^\"]*)\"|[^,]*)");
        Matcher m = p.matcher(attrs);
        if (!m.find()) return null;
        String v = m.group(2) != null ? m.group(2) : m.group(1);
        return v != null ? v.trim() : null;
    }

    static long longAttr(String attrs, String name, long fallback) {
        String v = strAttr(attrs, name);
        if (v == null) return fallback;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Fetches a playlist body over HTTP with the standard media headers. */
    public static String fetch(String url, int timeoutMs) throws IOException {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Accept", "*/*");
            int code = conn.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code + " for playlist");
            in = conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int n;
            int guard = 0;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (++guard > 512) break; // 8 MB ceiling for a playlist
            }
            return buf.toString("UTF-8");
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            if (conn != null) conn.disconnect();
        }
    }
}
