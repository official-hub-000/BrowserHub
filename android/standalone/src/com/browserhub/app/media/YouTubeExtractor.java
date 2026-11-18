package com.browserhub.app.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a YouTube watch URL into directly downloadable media URLs.
 *
 * Why this has to exist as its own path: on a watch page the video is not a
 * request the page makes. The page loads a player script, that script calls an
 * internal API, and the media arrives inside a JSON blob. Watching the network
 * for `*.mp4` finds nothing on a perfectly working page — which is exactly what
 * a plain "grab what the page requested" approach hits on YouTube.
 *
 * So this reads the same information the player reads:
 *   1. fetch the watch page,
 *   2. pull `ytInitialPlayerResponse` out of it (the canonical metadata blob),
 *   3. read streamingData.formats (progressive, audio+video in one file) and
 *      streamingData.adaptiveFormats (separate video and audio),
 *   4. prefer a progressive MP4 that already contains sound, because that is the
 *      only shape that plays on a phone without a remux step.
 *
 * When YouTube returns only adaptive tracks, both are downloaded and interleaved
 * by the DASH merger — the same engine that already joins separate tracks.
 *
 * What it will not do: pretend. A rental, a members-only video, an age-gated or
 * bot-checked response is reported with the reason the page gave.
 */
public final class YouTubeExtractor {

    /** A resumable media URL plus its declared shape. */
    public static final class Stream {
        public String url;
        public String mimeType;
        public String qualityLabel;
        public long contentLength;
        public long bitrate;
        public boolean audioOnly;
        public boolean videoOnly;
        public String codecs;
    }

    public static final class Result {
        public boolean ok;
        public String error;
        public String title;
        public String videoId;
        public int durationSeconds;
        /** Progressive track with picture and sound, when YouTube offers one. */
        public Stream progressive;
        public Stream bestVideo;
        public Stream bestAudio;
        public boolean isLive;
    }

    private YouTubeExtractor() {}

    /** True when this URL is a YouTube watch page or short link. */
    public static boolean handles(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!(lower.contains("youtube.com/watch") || lower.contains("youtu.be/")
                || lower.contains("m.youtube.com/watch") || lower.contains("youtube.com/shorts/"))) {
            return false;
        }
        // Never intercept the mobile web player's own assets.
        return !lower.contains("youtube.com/embed");
    }

    /** Extracts the 11-character video id from any of YouTube's URL shapes. */
    public static String videoId(String url) {
        if (url == null) return "";
        Matcher v = Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})").matcher(url);
        if (v.find()) return v.group(1);
        Matcher be = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})").matcher(url);
        if (be.find()) return be.group(1);
        Matcher s = Pattern.compile("youtube\\.com/shorts/([A-Za-z0-9_-]{11})").matcher(url);
        if (s.find()) return s.group(1);
        Matcher e = Pattern.compile("youtube\\.com/embed/([A-Za-z0-9_-]{11})").matcher(url);
        if (e.find()) return e.group(1);
        return "";
    }

    /**
     * Fetches and parses the player response for a watch URL.
     *
     * @param userAgent a real mobile browser UA; YouTube serves a different (and
     *                  sometimes incomplete) player to obvious non-browser agents.
     */
    public static Result resolve(String watchUrl, String userAgent, String cookie) {
        Result result = new Result();
        String id = videoId(watchUrl);
        result.videoId = id;
        if (id.length() == 0) {
            result.error = "that link is not a YouTube video";
            return result;
        }

        try {
            String pageUrl = "https://www.youtube.com/watch?v=" + id + "&hl=en&has_verified=1&bpctr=9999999999";
            String html = MediaMerger.fetchText(pageUrl, userAgent, cookie);
            if (html == null || html.length() < 1000) {
                result.error = "YouTube did not return the page";
                return result;
            }
            return parsePlayerResponse(html, result);
        } catch (Throwable t) {
            result.error = "could not reach YouTube: " + (t.getMessage() != null ? t.getMessage() : "network error");
            return result;
        }
    }

    /**
     * Parses the player response out of a watch page.
     *
     * Package-visible and separate from the network call so the parsing is
     * testable against captured fixtures without touching the internet.
     */
    public static Result parsePlayerResponse(String html, Result result) {
        String json = extractJsonObject(html, "ytInitialPlayerResponse");
        if (json == null) {
            // YouTube sometimes only exposes the config bootstrap.
            String alt = extractJsonObject(html, "playerResponse");
            if (alt != null) json = alt;
        }
        if (json == null) {
            result.error = "YouTube did not include the player data for this video";
            return result;
        }

        String status = stringField(json, "status");
        if ("LOGIN_REQUIRED".equals(status)) {
            String reason = stringField(json, "reason");
            result.error = reason.length() > 0
                    ? "YouTube requires sign-in for this video: " + reason
                    : "YouTube requires sign-in for this video";
            return result;
        }
        if ("ERROR".equals(status) || "UNPLAYABLE".equals(status)) {
            String reason = stringField(json, "reason");
            result.error = reason.length() > 0 ? reason : "this video is not playable";
            return result;
        }

        // Titles live either as the plain string or as the run-list form.
        String title = stringField(json, "title");
        if (title.length() == 0) title = runsField(json, "title");
        result.title = title;

        String duration = stringField(json, "lengthSeconds");
        if (duration.length() > 0) {
            try {
                result.durationSeconds = Integer.parseInt(duration);
            } catch (NumberFormatException ignored) {
                result.durationSeconds = 0;
            }
        }

        String live = stringField(json, "isLive");
        result.isLive = "true".equalsIgnoreCase(live);
        if (result.isLive) {
            result.error = "this is a live stream, so there is no finished file to save yet";
            return result;
        }

        // The playability block carries the most specific refusal reason.
        String playability = extractJsonObject(json, "playabilityStatus");
        if (playability != null) {
            String reason = stringField(playability, "reason");
            if (reason.length() > 0 && result.error == null) {
                String statusField = stringField(playability, "status");
                if (!"OK".equals(statusField)) {
                    result.error = reason;
                    return result;
                }
            }
        }

        String streaming = extractJsonObject(json, "streamingData");
        if (streaming == null) {
            if (result.error == null) {
                result.error = "this video has no downloadable stream (it may be a rental or premiere)";
            }
            return result;
        }

        List<Stream> formats = parseFormats(extractJsonArray(streaming, "formats"));
        List<Stream> adaptive = parseFormats(extractJsonArray(streaming, "adaptiveFormats"));

        // A progressive format is a complete file: picture and sound together.
        Stream bestProgressive = null;
        for (Stream f : formats) {
            if (!f.mimeType.contains("video/")) continue;
            if (f.url == null || f.url.length() == 0) continue;
            if (bestProgressive == null || f.bitrate > bestProgressive.bitrate) {
                bestProgressive = f;
            }
        }
        result.progressive = bestProgressive;

        // Adaptive tracks: choose the highest video and the highest audio.
        for (Stream f : adaptive) {
            if (f.url == null || f.url.length() == 0) continue;
            if (f.mimeType.startsWith("audio/")) {
                if (result.bestAudio == null || f.bitrate > result.bestAudio.bitrate) {
                    result.bestAudio = f;
                }
            } else if (f.mimeType.startsWith("video/")) {
                if (result.bestVideo == null || f.bitrate > result.bestVideo.bitrate) {
                    result.bestVideo = f;
                }
            }
        }

        if (result.progressive == null && result.bestVideo == null && result.bestAudio == null) {
            result.error = "YouTube did not offer a downloadable stream for this video";
            return result;
        }

        result.ok = true;
        return result;
    }

    // ------------------------------------------------------------------ formats

    private static List<Stream> parseFormats(String arrayJson) {
        List<Stream> out = new ArrayList<>();
        if (arrayJson == null) return out;
        for (String objectJson : splitObjects(arrayJson)) {
            Stream s = new Stream();
            s.url = stringField(objectJson, "url");
            s.mimeType = stringField(objectJson, "mimeType");
            s.qualityLabel = stringField(objectJson, "qualityLabel");
            s.codecs = stringField(objectJson, "codecs");
            s.contentLength = longField(objectJson, "contentLength");
            s.bitrate = longField(objectJson, "bitrate");
            s.audioOnly = s.mimeType.startsWith("audio/");
            s.videoOnly = s.mimeType.startsWith("video/") && s.audioOnly == false && !hasAudioCodec(objectJson);
            if (s.mimeType.length() == 0 && s.url.length() == 0) continue;
            out.add(s);
        }
        return out;
    }

    private static boolean hasAudioCodec(String objectJson) {
        String codecs = stringField(objectJson, "codecs").toLowerCase(Locale.US);
        String mime = stringField(objectJson, "mimeType").toLowerCase(Locale.US);
        return codecs.contains("mp4a") || codecs.contains("opus") || mime.contains("audioquality");
    }

    // ------------------------------------------------------------ mini JSON

    /**
     * Finds `"name":{...}` and returns the object text, honouring nested braces
     * and strings so a brace inside a title cannot end the object early.
     */
    public static String extractJsonObject(String source, String name) {
        if (source == null) return null;
        /*
         * The name appears in two shapes depending on how the page was built:
         *   var ytInitialPlayerResponse = {...};      (a variable assignment)
         *   "ytInitialPlayerResponse":{...}          (inside a larger object)
         * Searching only for the quoted form missed the assignment form, which is
         * the one a watch page actually uses.
         */
        int at = source.indexOf(name);
        if (at < 0) return null;
        int brace = source.indexOf('{', at);
        if (brace < 0) return null;
        // Guard against a name match followed by an unrelated object: require the
        // separator between the name and the brace to be trivial.
        String between = source.substring(at + name.length(), brace).replace("\"", "").trim();
        if (!(between.isEmpty() || between.equals("=") || between.equals(":") || between.equals(":"))) {
            return null;
        }
        return readBalanced(source, brace, '{', '}');
    }

    static String extractJsonArray(String source, String name) {
        if (source == null) return null;
        int at = source.indexOf("\"" + name + "\"");
        if (at < 0) at = source.indexOf(name);
        if (at < 0) return null;
        int bracket = source.indexOf('[', at);
        if (bracket < 0) return null;
        return readBalanced(source, bracket, '[', ']');
    }

    /** Splits a JSON array body into its top-level object strings. */
    static List<String> splitObjects(String arrayBody) {
        List<String> out = new ArrayList<>();
        if (arrayBody == null) return out;
        int i = 0;
        while (i < arrayBody.length()) {
            int start = arrayBody.indexOf('{', i);
            if (start < 0) break;
            String obj = readBalanced(arrayBody, start, '{', '}');
            if (obj == null) break;
            out.add(obj);
            i = start + obj.length();
        }
        return out;
    }

    /** Reads a balanced delimiter pair starting at {@code start}, string-aware. */
    static String readBalanced(String s, int start, char open, char close) {
        if (start >= s.length() || s.charAt(start) != open) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == open) depth += 1;
            else if (c == close) {
                depth -= 1;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        return null;
    }

    /** First string value for a key, at any depth. */
    static String stringField(String json, String key) {
        if (json == null) return "";
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) return "";
        return unescape(m.group(1));
    }

    /** Text of a run-list field such as title:{"runs":[{"text":"…"}]}. */
    static String runsField(String json, String key) {
        if (json == null) return "";
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) return "";
        int brace = json.indexOf('{', at);
        if (brace < 0) return "";
        String obj = readBalanced(json, brace, '{', '}');
        if (obj == null) return "";
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(obj);
        while (m.find()) sb.append(unescape(m.group(1)));
        return sb.toString();
    }

    static long longField(String json, String key) {
        if (json == null) return 0;
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?(\\d+)\"?").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append(n);
                        }
                    }
                    break;
                default: sb.append(n);
            }
        }
        return sb.toString();
    }

    /** The extension a progressive YouTube format will save as. */
    public static String extensionFor(Stream stream) {
        if (stream == null || stream.mimeType == null) return ".mp4";
        String m = stream.mimeType.toLowerCase(Locale.US);
        if (m.contains("webm")) return ".webm";
        if (m.contains("3gpp")) return ".3gp";
        return ".mp4";
    }
}
