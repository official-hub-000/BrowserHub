package com.browserhub.app.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Last-resort resolution through a third-party service.
 *
 * This exists for one reason, and it is a network reason rather than a clever one.
 * When a phone's own requests to YouTube are refused — a carrier-level block, a
 * datacentre IP that YouTube treats as a bot, a region where the player API
 * answers with a check — retrying the same request from the same address will
 * keep failing. Services like the one this app is compared to work by asking a
 * server elsewhere to do the lookup, because that server's address has different
 * standing.
 *
 * So the order in the app is deliberately: player API first, watch page second,
 * this only when both of those are refused. It is not the primary route, and it
 * should not become one — a third party that can disappear, rate limit, or answer
 * with a different video is a worse dependency than a direct call.
 *
 * What is sent is a video id. What comes back is a list of media urls and titles,
 * which is exactly what the rest of the pipeline already understands: the files
 * are fetched directly from the source, never proxied through the service.
 *
 * Only http(s) urls are accepted from a reply, because the reply is untrusted
 * input and must not be able to steer the downloader anywhere else.
 */
public final class YouTubeResolverService {

    /**
     * Endpoints tried in order.
     *
     * These are public resolution APIs of the same kind the big download sites run
     * on. Each speaks its own shape, so each has a small parser below rather than a
     * shared one that would fit none of them.
     */
    static final String[] ENDPOINTS = new String[] {
            // Shape: {"links":{"mp4":{"url":"…","size":"…","quality":"…"}},"title":"…"}
            "https://api.vevioz.com/api/button/mp4/VIDEOID",
    };

    private YouTubeResolverService() {}

    /** True when this could answer at all; used to skip the wait when offline shapes fail. */
    public static boolean available() {
        return ENDPOINTS.length > 0;
    }

    /**
     * Asks each service in turn for a video.
     *
     * @return a result shaped exactly like a direct resolution, or a failure
     *         carrying the reason the last one gave
     */
    public static YouTubeExtractor.Result resolve(String videoId) {
        YouTubeExtractor.Result result = new YouTubeExtractor.Result();
        result.videoId = videoId;
        if (videoId == null || videoId.length() < 5) {
            result.error = "that does not look like a video address";
            return result;
        }

        List<String> failures = new ArrayList<>();
        for (String template : ENDPOINTS) {
            String url = template.replace("VIDEOID", videoId);
            try {
                String body = MediaMerger.fetchTextWithHeaders(url, UA, null, NO_REFERER, null);
                if (body == null || body.length() == 0) {
                    failures.add("the resolver returned nothing");
                    continue;
                }
                YouTubeExtractor.Result parsed = parse(htmlUnescape(body), videoId);
                if (parsed.ok) return parsed;
                if (parsed.error != null && parsed.error.length() > 0) failures.add(parsed.error);
            } catch (IOException e) {
                failures.add(describe(e));
            } catch (Throwable t) {
                failures.add("the resolver could not be reached");
            }
        }

        result.error = failures.isEmpty()
                ? "no external resolver is configured"
                : failures.get(failures.size() - 1);
        return result;
    }

    /** A desktop user agent: these services render for browsers, not for apps. */
    static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36";
    static final String NO_REFERER = null;

    /**
     * Extracts whatever media links a reply contains.
     *
     * Deliberately permissive about the container and strict about the contents:
     * any http(s) url that looks like a media file or a googlevideo stream is
     * taken, everything else is ignored. That way a service changing its wrapper
     * does not break this, while nothing unsafe can enter the pipeline.
     */
    public static YouTubeExtractor.Result parse(String body, String videoId) {
        YouTubeExtractor.Result result = new YouTubeExtractor.Result();
        result.videoId = videoId == null ? "" : videoId;
        if (body == null || body.length() == 0) {
            result.error = "the resolver returned nothing";
            return result;
        }

        List<String> urls = new ArrayList<>();
        int at = 0;
        while (true) {
            int start = body.indexOf("http", at);
            if (start < 0) break;
            int end = start;
            // A url runs until a character that cannot be part of one in this
            // context: a quote, whitespace, an angle bracket or a backslash.
            while (end < body.length() && isUrlChar(body.charAt(end))) end++;
            String candidate = body.substring(start, end);
            at = end + 1;

            String clean = htmlUnescape(candidate.replace("\\/", "/"));
            if (!isSafeMediaUrl(clean)) continue;
            if (!urls.contains(clean)) urls.add(clean);
        }

        if (urls.isEmpty()) {
            result.error = "the resolver did not offer a downloadable file for this video";
            return result;
        }

        // Titles are a convenience; the download works without one.
        result.title = tidyTitle(titleOf(body));

        for (String url : urls) {
            YouTubeExtractor.Stream stream = new YouTubeExtractor.Stream();
            stream.url = url;
            stream.mimeType = guessMime(url);
            stream.qualityLabel = qualityOf(url, body);
            stream.contentLength = sizeOf(url, body);
            stream.audioOnly = stream.mimeType.startsWith("audio/");
            stream.videoOnly = false;
            /*
             * A resolver returns finished files, not separate tracks, so these are
             * all progressive by nature. No client identity is attached: these urls
             * are not signed for a client, and claiming to be one would make them
             * more likely to be refused, not less.
             */
            result.all.add(stream);
        }

        // Prefer a video file with sound; keep audio-only only if nothing else came.
        for (YouTubeExtractor.Stream stream : result.all) {
            if (!stream.audioOnly) {
                if (result.progressive == null || betterThan(stream, result.progressive)) {
                    result.progressive = stream;
                }
            } else if (result.bestAudio == null) {
                result.bestAudio = stream;
            }
        }
        if (result.progressive == null && result.bestAudio == null) {
            result.error = "the resolver's links were not playable files";
            return result;
        }
        result.ok = true;
        return result;
    }

    /** A media url, and nothing else: no javascript:, no data:, no file paths. */
    public static boolean isSafeMediaUrl(String url) {
        if (url == null || url.length() < 12) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        // Reject anything carrying credentials or a scheme-relative trick.
        if (lower.contains("@") && lower.indexOf("@") < lower.indexOf("/", 8)) return false;
        return lower.contains(".mp4") || lower.contains(".m4a") || lower.contains(".webm")
                || lower.contains("/videoplayback") || lower.contains(".mp3")
                || lower.contains("mime=video") || lower.contains("mime=audio")
                || lower.contains(".mkv") || lower.contains(".3gp");
    }

    static boolean isUrlChar(char c) {
        return c > ' ' && c != '"' && c != '\'' && c != '<' && c != '>' && c != '\\'
                && c != ')' && c != ']' && c != ',' && c != '}';
    }

    static String guessMime(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m4a") || lower.contains("mime=audio")) return "audio/mp4";
        if (lower.contains(".webm") && lower.contains("audio")) return "audio/webm";
        if (lower.contains(".mp3")) return "audio/mpeg";
        return "video/mp4";
    }

    /** Pulls a quality label out of whatever shape the reply uses. */
    static String qualityOf(String url, String body) {
        String lower = body.toLowerCase(Locale.US);
        int at = body.indexOf(url);
        if (at > 0) {
            String nearby = body.substring(Math.max(0, at - 400), Math.min(body.length(), at + 400));
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d{3,4})p").matcher(nearby);
            if (m.find()) return m.group(0);
        }
        if (lower.contains("1080")) return "1080p";
        if (lower.contains("720")) return "720p";
        if (lower.contains("480")) return "480p";
        if (lower.contains("360")) return "360p";
        return "";
    }

    static long sizeOf(String url, String body) {
        int at = body.indexOf(url);
        if (at > 0) {
            String nearby = body.substring(Math.max(0, at - 400), Math.min(body.length(), at + 400));
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+)\\s*(MB|GB)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(nearby);
            if (m.find()) {
                double value = Double.parseDouble(m.group(1));
                double mult = "GB".equalsIgnoreCase(m.group(2)) ? 1073741824.0 : 1048576.0;
                return (long) (value * mult);
            }
        }
        return 0L;
    }

    /** The page title, without the service's own branding bolted on. */
    static String titleOf(String body) {
        java.util.regex.Matcher og = java.util.regex.Pattern
                .compile("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']{3,150})[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (og.find()) return og.group(1);
        java.util.regex.Matcher t = java.util.regex.Pattern
                .compile("<title[^>]*>([^<]{3,150})</title>",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (t.find()) return t.group(1);
        return "";
    }

    static String tidyTitle(String raw) {
        if (raw == null) return "";
        String out = htmlUnescape(raw).trim();
        // Services append their own name; strip the usual separators.
        out = out.replaceAll("\\s*[|\\-–—]\\s*(Save|Download|YouTube).*$", "").trim();
        return out;
    }

    public static String htmlUnescape(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&#x2F;", "/").replace("&nbsp;", " ");
    }

    static boolean betterThan(YouTubeExtractor.Stream a, YouTubeExtractor.Stream b) {
        int ah = heightOf(a.qualityLabel);
        int bh = heightOf(b.qualityLabel);
        if (ah != bh) return ah > bh;
        return a.contentLength > b.contentLength;
    }

    static int heightOf(String label) {
        if (label == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{3,4})").matcher(label);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public static String describe(IOException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("403")) return "the resolver refused the request";
        if (message.contains("404")) return "the resolver does not know this video";
        if (message.contains("429")) return "the resolver is rate limiting this device";
        return "could not reach the resolver: " + message;
    }
}
