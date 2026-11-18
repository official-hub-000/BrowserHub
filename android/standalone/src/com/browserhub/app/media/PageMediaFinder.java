package com.browserhub.app.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a video on an ordinary web page, given only its address.
 *
 * This is the other half of the paste-a-link feature. YouTube has its own
 * resolver because its media lives in a JSON blob that needs a specific key; most
 * other sites put the same information in one of a handful of standard places,
 * and this reads all of them:
 *
 *   1. Open Graph:  <meta property="og:video" content="…"> — the site's own
 *      statement of what its video is, which is what social cards use.
 *   2. Twitter card: <meta name="twitter:player:stream" content="…">
 *   3. A player config: "file":"…mp4" / "video_url":"…" / "contentUrl":"…"
 *   4. A plain media reference anywhere in the markup, as a last resort.
 *
 * The order matters: the earlier a source appears, the more likely it is to be
 * the video the page is about rather than an advertisement or a preview clip.
 * That is why an og:video tag beats a URL found in a script, even though both
 * are "a video on the page".
 *
 * The result is a direct file address, which the existing download path already
 * knows how to fetch. Nothing here downloads anything.
 */
public final class PageMediaFinder {

    /** Media file extensions worth accepting from a page. */
    private static final String[] MEDIA_EXT = {
            ".mp4", ".webm", ".m4v", ".mov", ".mkv", ".m3u8", ".mpd", ".m4a", ".mp3"
    };

    private PageMediaFinder() {}

    /** The outcome of looking at a page. */
    public static final class Result {
        public boolean ok;
        public String url;
        public String title;
        public String quality;
        public String reason;
    }

    /** True when this looks like a page to fetch rather than a stream to read. */
    public static boolean isPageUrl(String url) {
        if (url == null || url.length() < 8) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        // A direct media address needs no page fetch.
        for (String ext : MEDIA_EXT) {
            if (lower.contains(ext) && !lower.contains(ext + "?")) return false;
        }
        return !lower.contains("/videoplayback");
    }

    /**
     * Fetches {@code pageUrl} and looks for a video on it.
     *
     * @return a direct media address, or a reason nothing was found
     */
    public static Result find(String pageUrl, String userAgent, String cookie) {
        Result result = new Result();
        String html;
        try {
            html = MediaMerger.fetchTextWithHeaders(pageUrl, userAgent, cookie, null, null);
        } catch (IOException e) {
            result.reason = describe(e);
            return result;
        } catch (Throwable t) {
            result.reason = "that link could not be opened";
            return result;
        }
        if (html == null || html.length() < 40) {
            result.reason = "that link returned an empty page";
            return result;
        }
        return parse(html, pageUrl);
    }

    /**
     * Looks for a video in already-fetched markup.
     *
     * Separate from {@link #find} so the searching rules can be tested against
     * captured pages without a network.
     */
    public static Result parse(String html, String pageUrl) {
        Result result = new Result();
        if (html == null) {
            result.reason = "the page was empty";
            return result;
        }

        result.title = titleOf(html);
        result.quality = qualityOf(html);

        // 1. The site's own declaration.
        String openGraph = metaContent(html, "og:video:secure_url");
        if (openGraph.length() == 0) openGraph = metaContent(html, "og:video:url");
        if (openGraph.length() == 0) openGraph = metaContent(html, "og:video");
        String fromOg = absolute(openGraph, pageUrl);
        if (isUsableMedia(fromOg)) {
            result.ok = true;
            result.url = fromOg;
            return result;
        }

        // 2. A Twitter player stream, which some sites use instead.
        String twitter = absolute(metaContent(html, "twitter:player:stream"), pageUrl);
        if (isUsableMedia(twitter)) {
            result.ok = true;
            result.url = twitter;
            return result;
        }

        // 3. A player configuration key. Order is by how specific the key is.
        String[] keys = {
                "contentUrl", "video_url", "videoUrl", "file", "src", "hls", "dash",
                "playlist", "streamUrl", "mediaUrl"
        };
        for (String key : keys) {
            String value = jsonStringField(html, key);
            String candidate = absolute(jsonUnescape(value), pageUrl);
            if (isUsableMedia(candidate)) {
                result.ok = true;
                result.url = candidate;
                return result;
            }
        }

        // 4. Any media reference in the markup, preferring the earliest, which is
        //    usually the page's own video rather than a promoted one in a sidebar.
        String loose = firstMediaUrl(html);
        String absoluteLoose = absolute(loose, pageUrl);
        if (isUsableMedia(absoluteLoose)) {
            result.ok = true;
            result.url = absoluteLoose;
            return result;
        }

        result.reason = "no video was found on that page — it may load the player with a script "
                + "after opening, in which case play it in the browser and use the page scan instead";
        return result;
    }

    /** Reads a meta tag's content by property or name. */
    static String metaContent(String html, String key) {
        // Both attribute orders occur in the wild, so both are tried.
        String[] patterns = {
                "<meta[^>]+(?:property|name)\\s*=\\s*[\"']" + Pattern.quote(key)
                        + "[\"'][^>]*content\\s*=\\s*[\"']([^\"']+)[\"']",
                "<meta[^>]+content\\s*=\\s*[\"']([^\"']+)[\"'][^>]*(?:property|name)\\s*=\\s*[\"']"
                        + Pattern.quote(key) + "[\"']"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) return m.group(1).trim();
        }
        return "";
    }

    /** Reads a JSON string value for a key, honouring escapes. */
    static String jsonStringField(String text, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(text);
        if (!m.find()) return "";
        return m.group(1);
    }

    static String jsonUnescape(String s) {
        if (s == null) return "";
        return s.replace("\\/", "/").replace("\\u0026", "&").replace("\\\"", "\"");
    }

    /** The first plausible media address in the markup. */
    static String firstMediaUrl(String html) {
        Matcher m = Pattern.compile("https?://[^\"'\\s<>\\\\)]+", Pattern.CASE_INSENSITIVE)
                .matcher(html);
        while (m.find()) {
            String candidate = jsonUnescape(m.group());
            String lower = candidate.toLowerCase(Locale.US);
            for (String ext : MEDIA_EXT) {
                if (lower.contains(ext)) return candidate;
            }
        }
        return "";
    }

    /** True when the address is a media file worth handing to the downloader. */
    public static boolean isUsableMedia(String url) {
        if (url == null || url.length() < 12) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.contains("/videoplayback")) return true;
        for (String ext : MEDIA_EXT) {
            if (lower.contains(ext)) return true;
        }
        // A player endpoint that serves a stream without a file extension is only
        // trusted when it says so in the address.
        return lower.contains("mime=video") || lower.contains("format=mp4");
    }

    /** Resolves a possibly-relative address against the page it came from. */
    static String absolute(String url, String pageUrl) {
        if (url == null || url.length() == 0) return "";
        String trimmed = url.trim();
        if (trimmed.startsWith("//")) {
            return (pageUrl != null && pageUrl.startsWith("https://") ? "https:" : "http:") + trimmed;
        }
        if (trimmed.startsWith("/")) {
            String base = originOf(pageUrl);
            return base.length() > 0 ? base + trimmed : "";
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        // A bare relative path is not worth guessing at.
        return "";
    }

    static String originOf(String url) {
        if (url == null) return "";
        int at = url.indexOf("://");
        if (at < 0) return "";
        int slash = url.indexOf('/', at + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }

    /** The page title, cleaned of the site's branding. */
    static String titleOf(String html) {
        String og = metaContent(html, "og:title");
        if (og.length() == 0) {
            Matcher m = Pattern.compile("<title[^>]*>([^<]{2,200})</title>", Pattern.CASE_INSENSITIVE)
                    .matcher(html);
            if (m.find()) og = m.group(1);
        }
        return tidy(og);
    }

    static String tidy(String raw) {
        if (raw == null) return "";
        return raw.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    /** A resolution hint from the markup, when one is stated. */
    public static String qualityOf(String html) {
        Matcher m = Pattern.compile("(\\d{3,4})\\s*p\\b").matcher(html);
        int best = 0;
        while (m.find()) {
            try {
                int h = Integer.parseInt(m.group(1));
                if (h >= 144 && h <= 4320 && h > best) best = h;
            } catch (NumberFormatException ignored) {
            }
        }
        return best > 0 ? best + "p" : "";
    }

    public static String describe(IOException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("403")) return "that site refused the request";
        if (message.contains("404")) return "that link does not exist";
        if (message.contains("429")) return "that site is rate limiting this device";
        return "that link could not be opened: " + message;
    }

    /** Every media address the page mentions, for a picker when several exist. */
    public static List<String> allMediaUrls(String html, String pageUrl) {
        List<String> out = new ArrayList<>();
        if (html == null) return out;
        Matcher m = Pattern.compile("https?://[^\"'\\s<>\\\\)]+", Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            String candidate = absolute(jsonUnescape(m.group()), pageUrl);
            if (isUsableMedia(candidate) && !out.contains(candidate)) out.add(candidate);
            if (out.size() >= 20) break;
        }
        return out;
    }
}
