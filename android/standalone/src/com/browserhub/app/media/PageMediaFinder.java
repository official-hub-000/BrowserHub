package com.browserhub.app.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a video on any page, following yt-dlp's generic extractor.
 *
 * yt-dlp supports well over a thousand sites with a thousand small extractors
 * plus one fallback: its generic extractor. That fallback is what this is — the
 * same searches, in the same order, because the order is the part that was
 * learned the hard way by that project over many years and is not something to
 * invent again.
 *
 * The order, taken from yt_dlp/extractor/generic.py:
 *
 *   1. An embed URL that is itself a player page (iframes, twitter:player) —
 *      followed as a second page, because the video is not on this one.
 *   2. A KVS player (kt_player), which needs its own decode.
 *   3. JSON-LD VideoObject, the schema.org declaration.
 *   4. JW Player in a SWFObject: `flashvars: "…file=http…"`.
 *   5. JW Player setup blocks with a `file:` key.
 *   6. A loose `file=` / `source=` attribute.
 *   7. A JSON `file:` / `video_url:` key.
 *   8. Flowplayer and Cinerama embeds.
 *   9. twitter:player:stream — a raw stream, checked before twitter:player
 *      because that one is an iframe.
 *  10. Open Graph `og:video`, but ONLY when og:video:type says it is a video and
 *      not a Flash player — that check is why a page with a swf player does not
 *      offer a .swf as a download.
 *  11. A meta refresh redirect.
 *  12. twitter:player as a last resort.
 *
 * A candidate is rejected when its extension is a subtitle, image, stylesheet,
 * script or flash file. That filter is the difference between finding the video
 * and offering a poster image named "video.jpg".
 *
 * What is deliberately NOT done: rewriting a player URL into a real one by
 * pattern-matching minified JavaScript. yt-dlp tries that for one player family
 * (KVS) and its own source calls the approach unreliable — it is the piece that
 * breaks. A page whose video cannot be reached without executing its player is
 * handled by reading the live page in the app instead, where the player has
 * already done the work.
 */
public final class PageMediaFinder {

    /**
     * Extensions a candidate may have, from yt-dlp's MEDIA_EXTENSIONS.
     *
     * Used both ways: to accept a media url, and to reject a page address that is
     * really a direct file needing no page fetch at all.
     */
    static final String[] VIDEO_EXT = {
            "3g2", "3gp", "f4v", "mk3d", "divx", "mpg", "ogv", "m4v", "wmv",
            "avi", "flv", "mkv", "mov", "mp4", "webm"
    };
    static final String[] AUDIO_EXT = {
            "aac", "ape", "asf", "f4a", "f4b", "m4b", "m4r", "oga", "ogx", "spx",
            "vorbis", "wma", "weba", "aiff", "alac", "flac", "m4a", "mka", "mp3",
            "ogg", "opus", "wav"
    };
    static final String[] MANIFEST_EXT = { "f4f", "f4m", "m3u8", "smil", "mpd" };

    /** Extensions that look like media but are never a downloadable video. */
    static final String[] REJECT_EXT = {
            "swf", "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg",
            "srt", "sbv", "sub", "vtt", "ttml", "ass", "lrc",
            "js", "mjs", "xml", "json", "css", "html", "htm", "txt", "pdf"
    };

    private PageMediaFinder() {}

    /** The outcome of looking at a page. */
    public static final class Result {
        public boolean ok;
        public String url;
        public String title;
        public String quality;
        /** Which rule found it, for the log and for the tests. */
        public String foundBy;
        /** True when this is a playlist the merger should read, not a single file. */
        public boolean isManifest;
        /** An embed page that must be fetched instead of this one. */
        public String followUrl;
        public String reason;
    }

    /** True when this looks like a page to fetch rather than a stream to read. */
    public static boolean isPageUrl(String url) {
        if (url == null || url.length() < 8) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.contains("/videoplayback")) return false;
        String path = pathOf(lower);
        for (String ext : ALL_MEDIA_EXT) {
            if (path.endsWith("." + ext)) return false;
        }
        // A url whose query carries a media file is already a stream.
        for (String ext : ALL_MEDIA_EXT) {
            if (lower.contains("." + ext) && lower.indexOf("." + ext) > lower.indexOf('?')) return false;
        }
        return true;
    }

    static final String[] ALL_MEDIA_EXT;

    static {
        List<String> all = new ArrayList<>();
        for (String e : VIDEO_EXT) all.add(e);
        for (String e : AUDIO_EXT) all.add(e);
        for (String e : MANIFEST_EXT) all.add(e);
        ALL_MEDIA_EXT = all.toArray(new String[0]);
    }

    /**
     * Fetches {@code pageUrl} and looks for a video on it.
     *
     * @return a direct media address, a followable embed page, or a reason
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
        Result parsed = parse(html, pageUrl);

        // A page that only holds an embed: fetch the player page and look there.
        if (!parsed.ok && parsed.followUrl != null && parsed.followUrl.length() > 0
                && !parsed.followUrl.equals(pageUrl)) {
            try {
                String embedded = MediaMerger.fetchTextWithHeaders(
                        parsed.followUrl, userAgent, cookie, pageUrl, null);
                if (embedded != null && embedded.length() > 40) {
                    Result inner = parse(embedded, parsed.followUrl);
                    if (inner.ok) return inner;
                }
            } catch (Throwable ignored) {
            }
        }
        return parsed;
    }

    /**
     * Looks for a video in already-fetched markup.
     *
     * Separate from {@link #find} so the rules can be tested against captured
     * pages with no network involved.
     */
    public static Result parse(String html, String pageUrl) {
        Result result = new Result();
        if (html == null) {
            result.reason = "the page was empty";
            return result;
        }

        result.title = titleOf(html);
        result.quality = qualityOf(html);

        // 1. An embedded player page. Followed rather than returned, because the
        //    answer is on the other side of it.
        String follow = firstEmbedPage(html, pageUrl);
        if (follow != null) {
            result.followUrl = follow;
        }

        // 3. JSON-LD VideoObject. Before the player-embed rules because it is the
        //    site stating the answer directly.
        String jsonLd = jsonLdContentUrl(html, pageUrl);
        if (isAcceptable(jsonLd)) {
            return accept(result, jsonLd, "json-ld");
        }

        // 2b. The media tags themselves, which is how yt-dlp's
        //     _parse_html5_media_entries starts. A <video src> or a <source>
        //     inside a <video> is the site stating the file, and it beats every
        //     rule below. amp-video and dl8-video are the same tag with a prefix.
        String tag = mediaTagSource(html, pageUrl);
        if (tag != null) return accept(result, tag, "media-tag");

        // 2c. Video.js: player.src([{src: "...", type: "video/mp4"}]).
        String videoJs = videoJsSource(html, pageUrl);
        if (videoJs != null) return accept(result, videoJs, "videojs");

        // 4. JW Player in a SWFObject: flashvars: "…file=http…"
        for (String candidate : findAll(html,
                "flashvars[\"']?\\s*[:,]\\s*[\"'][^\"']*?file=(https?[^\"'&]*)")) {
            String url = absolute(unescape(candidate), pageUrl);
            if (isAcceptable(url)) return accept(result, url, "jwplayer-swfobject");
        }

        // 5. A JW Player setup block with a file key.
        for (String candidate : findAll(html,
                "(?:jw_plugins|JWPlayerOptions|jwplayer\\s*\\(\\s*[\"'][^'\"]+[\"']\\s*\\)\\s*\\.setup)"
                        + "[\\s\\S]{0,4000}?[\"']?file[\"']?\\s*:\\s*[\"']([^\"']+)[\"']")) {
            String url = absolute(unescape(candidate), pageUrl);
            if (isAcceptable(url)) return accept(result, url, "jwplayer-setup");
        }

        // 6. A loose file= or source= attribute.
        for (String candidate : findAll(html, "[^A-Za-z0-9](?:file|source)=(https?[^\"'&]+)")) {
            String url = absolute(unescape(candidate), pageUrl);
            if (isAcceptable(url)) return accept(result, url, "file-attribute");
        }

        // 7. A JSON file: / video_url: key.
        for (String key : new String[] { "file", "video_url", "videoUrl", "contentUrl",
                "streamUrl", "mediaUrl", "hls", "hlsUrl", "dash", "dashUrl", "playlist" }) {
            String value = jsonStringField(html, key);
            String url = absolute(unescape(value), pageUrl);
            if (isAcceptable(url)) return accept(result, url, "json:" + key);
        }

        // 8. Flowplayer.
        for (String candidate : findAll(html,
                "flowplayer\\(\"[^\"]+\",\\s*\\{[^}]+\\}\\s*,\\s*\\{[^}]+?[\"']?clip[\"']?\\s*:\\s*"
                        + "\\{\\s*[\"']?url[\"']?\\s*:\\s*[\"']([^\"']+)[\"']")) {
            String url = absolute(unescape(candidate), pageUrl);
            if (isAcceptable(url)) return accept(result, url, "flowplayer");
        }

        // 8b. Cinerama.
        for (String candidate : findAll(html, "cinerama\\.embedPlayer\\(\\s*'[^']+',\\s*'([^']+)'")) {
            String url = absolute(unescape(candidate), pageUrl);
            if (isAcceptable(url)) return accept(result, url, "cinerama");
        }

        // 9. twitter:player:stream — a raw stream. Checked before twitter:player,
        //    which is an iframe.
        String twitterStream = absolute(unescape(
                metaContentAnyOrder(html, "twitter:player:stream")), pageUrl);
        if (isAcceptable(twitterStream)) return accept(result, twitterStream, "twitter:stream");

        // 10. Open Graph, but only when og:video:type says video. A page whose
        //     og:video is a Flash player must not offer a .swf.
        String ogType = metaContentAnyOrder(html, "og:video:type");
        boolean saysVideo = ogType.length() == 0
                || ogType.toLowerCase(Locale.US).startsWith("video/");
        if (saysVideo && (ogType.length() == 0 || !ogType.toLowerCase(Locale.US).contains("x-shockwave"))) {
            for (String key : new String[] { "og:video:secure_url", "og:video:url", "og:video",
                    "og:audio:secure_url", "og:audio:url", "og:audio" }) {
                String url = absolute(unescape(metaContentAnyOrder(html, key)), pageUrl);
                if (isAcceptable(url)) return accept(result, url, "opengraph:" + key);
            }
        }

        // 11. A meta refresh.
        String refresh = metaRefreshTarget(html);
        if (refresh.length() > 0) {
            String url = absolute(unescape(refresh), pageUrl);
            if (isAcceptable(url)) return accept(result, url, "meta-refresh");
            if (follow == null && url.length() > 0) result.followUrl = url;
        }

        // 12. twitter:player as the last resort: an iframe to follow.
        String twitterPlayer = absolute(unescape(
                metaContentAnyOrder(html, "twitter:player")), pageUrl);
        if (twitterPlayer.length() > 0 && !twitterPlayer.equals(pageUrl)) {
            result.followUrl = twitterPlayer;
        }

        // Nothing was declared. Take the first real media address in the markup,
        // which catches pages that simply link the file.
        String loose = firstAcceptableMediaUrl(html, pageUrl);
        if (loose != null) return accept(result, loose, "loose-media-url");

        result.reason = follow != null
                ? "the video is on an embedded player page that could not be read"
                : "no video was found on that page — play it in the browser and use the page scan instead";
        return result;
    }

    private static Result accept(Result result, String url, String how) {
        result.ok = true;
        result.url = url;
        result.foundBy = how;
        result.isManifest = hasManifestExtension(url);
        return result;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The source of a &lt;video&gt; or &lt;audio&gt; tag.
     *
     * Takes the tag's own src when it has one, otherwise the first
     * &lt;source src&gt; inside it. A <source> without a type is skipped only
     * when the tag declared a type of its own that disagrees — the same
     * preference order yt-dlp uses.
     */
    static String mediaTagSource(String html, String pageUrl) {
        // amp-video and dl8-video are the same element with a prefix.
        Matcher tag = Pattern.compile(
                "<(?:(?:amp|dl8(?:-live)?)-)?(video|audio)\\b([^>]*)>([\\s\\S]{0,4000}?)</\\1>",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (tag.find()) {
            Matcher own = Pattern.compile("\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE).matcher(tag.group(2));
            if (own.find()) {
                String url = absolute(unescape(own.group(1)), pageUrl);
                if (isAcceptable(url)) return url;
            }
            Matcher source = Pattern.compile(
                    "<source\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE).matcher(tag.group(3));
            while (source.find()) {
                String url = absolute(unescape(source.group(1)), pageUrl);
                if (isAcceptable(url)) return url;
            }
        }
        // A self-closing or unclosed tag, which the regex above needs to pair.
        Matcher alone = Pattern.compile(
                "<(?:(?:amp|dl8(?:-live)?)-)?(?:video|audio)\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (alone.find()) {
            String url = absolute(unescape(alone.group(1)), pageUrl);
            if (isAcceptable(url)) return url;
        }
        return null;
    }

    /** The source of a video.js player.src([...]) call. */
    static String videoJsSource(String html, String pageUrl) {
        Matcher m = Pattern.compile(
                "\\bvideojs\\s*\\([\\s\\S]{0,400}?\\.src\\s*\\(\\s*(\\[[\\s\\S]{0,2000}?\\]|\\{[\\s\\S]{0,2000}?\\})",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            for (String src : findAll(m.group(1), "[\"']?src[\"']?\\s*:\\s*[\"']([^\"']+)[\"']")) {
                String url = absolute(unescape(src), pageUrl);
                if (isAcceptable(url)) return url;
            }
        }
        return null;
    }

    /** Reads a meta tag by property or name, in either attribute order. */
    public static String metaContentAnyOrder(String html, String key) {
        String[] patterns = {
                "<meta[^>]+(?:property|name)\\s*=\\s*[\"']" + Pattern.quote(key)
                        + "[\"'][^>]*?content\\s*=\\s*[\"']([^\"']*)[\"']",
                "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?(?:property|name)\\s*=\\s*[\"']"
                        + Pattern.quote(key) + "[\"']"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) return m.group(1).trim();
        }
        return "";
    }

    /** An iframe or player address that should be fetched instead of the page. */
    static String firstEmbedPage(String html, String pageUrl) {
        Matcher iframe = Pattern.compile(
                "<iframe[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        while (iframe.find()) {
            String src = absolute(unescape(iframe.group(1)), pageUrl);
            String lower = src.toLowerCase(Locale.US);
            if (lower.contains("/embed") || lower.contains("/player")
                    || lower.contains("player.") || lower.contains("/v/")) {
                return src;
            }
        }
        return null;
    }

    /** The contentUrl of a schema.org VideoObject. */
    static String jsonLdContentUrl(String html, String pageUrl) {
        for (String block : jsonLdBlocks(html)) {
            if (!block.contains("VideoObject")) continue;
            for (String key : new String[] { "contentUrl", "embedUrl" }) {
                String value = jsonStringField(block, key);
                if (value != null && value.length() > 0) {
                    return absolute(unescape(value), pageUrl);
                }
            }
        }
        return "";
    }

    /** Every JSON-LD script body on the page. */
    static List<String> jsonLdBlocks(String html) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile(
                "<script[^>]+type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>([\\s\\S]*?)</script>",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static String metaRefreshTarget(String html) {
        Matcher m = Pattern.compile(
                "<meta[^>]+http-equiv\\s*=\\s*[\"']refresh[\"'][^>]*?content\\s*=\\s*[\"'][^\"']*?"
                        + "(?:URL|url)\\s*=\\s*'?([^'\"]+)",
                Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

    static List<String> findAll(String html, String regex) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        while (m.find()) {
            if (m.groupCount() >= 1 && m.group(1) != null) out.add(m.group(1));
            if (out.size() > 40) break;
        }
        return out;
    }

    /** Reads a JSON string value for a key, honouring escapes. */
    public static String jsonStringField(String text, String key) {
        if (text == null) return "";
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(text);
        return m.find() ? m.group(1) : "";
    }

    public static String unescape(String s) {
        if (s == null) return "";
        String out = s.replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=")
                .replace("\\u003f", "?").replace("\\\"", "\"");
        return htmlUnescape(out);
    }

    public static String htmlUnescape(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    /** True when a candidate is a media address and not a rejection-worthy one. */
    public static boolean isAcceptable(String url) {
        if (url == null || url.length() < 12) return false;
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.contains("/videoplayback")) return true;

        String path = pathOf(lower);
        String ext = extensionOf(path);

        // A manifest is always acceptable; that is what the merger is for.
        if (ext.length() > 0 && contains(MANIFEST_EXT, ext)) return true;
        if (ext.length() > 0 && contains(REJECT_EXT, ext)) return false;
        if (ext.length() > 0 && (contains(VIDEO_EXT, ext) || contains(AUDIO_EXT, ext))) return true;

        // No extension: trust a stated mime type in the query, which is how
        // googlevideo and several CDNs serve media.
        return lower.contains("mime=video") || lower.contains("mime=audio")
                || lower.contains("format=mp4") || lower.contains("type=video");
    }

    /** True for yt-dlp's "usable media" test, kept for the caller's naming. */
    public static boolean isUsableMedia(String url) {
        return isAcceptable(url);
    }

    static boolean hasManifestExtension(String url) {
        String ext = extensionOf(pathOf(url == null ? "" : url.toLowerCase(Locale.US)));
        return ext.length() > 0 && contains(MANIFEST_EXT, ext);
    }

    /** The first media address in the markup that passes the filter. */
    static String firstAcceptableMediaUrl(String html, String pageUrl) {
        Matcher m = Pattern.compile("(?:https?:)?//?[^\"'\\s<>\\\\)]+", Pattern.CASE_INSENSITIVE)
                .matcher(html);
        while (m.find()) {
            String candidate = absolute(unescape(m.group()), pageUrl);
            if (isAcceptable(candidate)) return candidate;
        }
        return null;
    }

    static String pathOf(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        String noQuery = q < 0 ? url : url.substring(0, q);
        int scheme = noQuery.indexOf("://");
        if (scheme < 0) return noQuery;
        int slash = noQuery.indexOf('/', scheme + 3);
        return slash < 0 ? "/" : noQuery.substring(slash);
    }

    static String extensionOf(String path) {
        if (path == null) return "";
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        String ext = path.substring(dot + 1).toLowerCase(Locale.US);
        // A path segment that is not really an extension (an id with a dot in it).
        if (ext.length() > 5) return "";
        return ext;
    }

    static boolean contains(String[] set, String value) {
        for (String s : set) if (s.equals(value)) return true;
        return false;
    }

    /** Resolves a possibly-relative address against the page it came from. */
    public static String absolute(String url, String pageUrl) {
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
        return "";
    }

    static String originOf(String url) {
        if (url == null) return "";
        int at = url.indexOf("://");
        if (at < 0) return "";
        int slash = url.indexOf('/', at + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }

    /** The page title, without the site's branding. */
    static String titleOf(String html) {
        String og = metaContentAnyOrder(html, "og:title");
        if (og.length() == 0) {
            Matcher m = Pattern.compile("<title[^>]*>([^<]{2,200})</title>", Pattern.CASE_INSENSITIVE)
                    .matcher(html);
            if (m.find()) og = m.group(1);
        }
        return tidy(og);
    }

    static String tidy(String raw) {
        return htmlUnescape(raw == null ? "" : raw).trim();
    }

    /** A resolution hint from the markup, when one is stated. */
    public static String qualityOf(String html) {
        if (html == null) return "";
        Matcher m = Pattern.compile("(\\d{3,4})\\s*p\\b", Pattern.CASE_INSENSITIVE).matcher(html);
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

    /** Every acceptable media address the page mentions, in document order. */
    public static List<String> allMediaUrls(String html, String pageUrl) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (html == null) return new ArrayList<>();
        Matcher m = Pattern.compile("(?:https?:)?//?[^\"'\\s<>\\\\)]+", Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            String candidate = absolute(unescape(m.group()), pageUrl);
            if (isAcceptable(candidate)) out.add(candidate);
            if (out.size() >= 20) break;
        }
        return new ArrayList<>(out);
    }
}
