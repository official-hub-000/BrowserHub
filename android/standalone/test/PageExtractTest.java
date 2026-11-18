import com.browserhub.app.media.PageMediaFinder;

/**
 * yt-dlp's generic extractor rules, checked against markup from real pages.
 *
 * One test per rule, plus the two things that matter more than any rule: a
 * poster image must never be offered as the video, and a page that declares a
 * Flash player must not produce a .swf download.
 */
public final class PageExtractTest {

    private static int passed = 0;
    private static final java.util.List<String> failures = new java.util.ArrayList<>();

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failures.add(name);
            System.out.println("  FAIL  " + name);
        }
    }

    private static String page(String body) {
        return "<html><head><title>A page</title></head><body>" + body + "</body></html>";
    }

    public static void main(String[] args) {
        testOgVideo();
        testOgFlashIsRefused();
        testJwPlayerFlashvars();
        testJwPlayerSetupBlock();
        testFileAttribute();
        testJsonField();
        testJsonLd();
        testTwitterStreamBeforePlayer();
        testTwitterPlayerIsFollowed();
        testIframeEmbedIsFollowed();
        testPosterImageIsNotTheVideo();
        testFlowplayer();
        testMetaRefresh();
        testLooseLink();
        testNothingFound();
        testExtensionSets();
        testManifestDetection();
        testAbsoluteResolution();
        testQualityHint();
        testUnescaping();
        testEmbeddedThenFound();

        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("PageExtractTest: " + passed + " checks passed");
            return;
        }
        System.out.println("PageExtractTest: " + passed + " passed, " + failures.size() + " FAILED");
        for (String f : failures) System.out.println("  - " + f);
        System.exit(1);
    }

    // 10. Open Graph, when it is actually a video.
    private static void testOgVideo() {
        String html = page("<meta property=\"og:video:type\" content=\"video/mp4\">"
                + "<meta property=\"og:video\" content=\"https://cdn.example.com/movie.mp4\">"
                + "<meta property=\"og:title\" content=\"The Movie (2019)\">");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://watch.example.com/film");
        check("og:video is found", r.ok);
        check("og:video url is exact", "https://cdn.example.com/movie.mp4".equals(r.url));
        check("og:video title is read", "The Movie (2019)".equals(r.title));
        check("og:video rule is named", r.foundBy.startsWith("opengraph"));
        check("og:video is not a manifest", !r.isManifest);
    }

    // The Flash refusal: og:video pointing at a swf is not a download.
    private static void testOgFlashIsRefused() {
        String html = page("<meta property=\"og:video:type\" content=\"application/x-shockwave-flash\">"
                + "<meta property=\"og:video\" content=\"https://cdn.example.com/player.swf\">");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://flash.example.com/v");
        check("flash og:video is refused", !r.ok);
        check("flash page gives a reason", r.reason != null && r.reason.length() > 0);
    }

    // 4. JW Player in a SWFObject.
    private static void testJwPlayerFlashvars() {
        String html = page("<script>var s1=new SWFObject('p.swf','p','640','360','9');"
                + "s1.addVariable('flashvars','file=https://cdn.example.com/ep1.mp4&autostart=true');</script>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://vids.example.com/e/1");
        check("jwplayer flashvars found", r.ok);
        check("jwplayer flashvars url", "https://cdn.example.com/ep1.mp4".equals(r.url));
        check("jwplayer flashvars rule", "jwplayer-swfobject".equals(r.foundBy));
    }

    // 5. A JW Player setup block.
    private static void testJwPlayerSetupBlock() {
        String html = page("<script>jwplayer(\"player\").setup({"
                + "  width: 640, height: 360,"
                + "  file: \"https://cdn.example.com/setup-file.webm\","
                + "  image: \"https://cdn.example.com/poster.jpg\"});</script>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://vids.example.com/e/2");
        check("jwplayer setup found", r.ok);
        check("jwplayer setup url", "https://cdn.example.com/setup-file.webm".equals(r.url));
        check("jwplayer setup skips poster", !r.url.contains("poster"));
    }

    // 6. A loose file= attribute.
    private static void testFileAttribute() {
        String html = page("<object><embed src=\"/swf/p.swf\" "
                + "flashvars=\"&amp;file=https://cdn.example.com/loose.m4v\" /></object>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://vids.example.com/e/3");
        check("file attribute found", r.ok);
        check("file attribute url", "https://cdn.example.com/loose.m4v".equals(r.url));
        check("file attribute escapes &amp;", !r.url.contains("&amp;"));
    }

    // 7. A JSON key.
    private static void testJsonField() {
        String html = page("<script>window.__STATE__={\"hlsUrl\":\"https:\\/\\/cdn.example.com\\/live.m3u8\"};</script>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://live.example.com/w");
        check("json hlsUrl found", r.ok);
        check("json hlsUrl unescaped", "https://cdn.example.com/live.m3u8".equals(r.url));
        check("json hlsUrl is a manifest", r.isManifest);
    }

    // 3. JSON-LD VideoObject.
    private static void testJsonLd() {
        String html = page("<script type=\"application/ld+json\">"
                + "{\"@type\":\"VideoObject\",\"name\":\"Clip\","
                + "\"contentUrl\":\"https://cdn.example.com/ld.mp4\","
                + "\"thumbnailUrl\":\"https://cdn.example.com/ld.jpg\"}</script>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://site.example.com/clip");
        check("json-ld is found", r.ok);
        check("json-ld url", "https://cdn.example.com/ld.mp4".equals(r.url));
        check("json-ld rule", "json-ld".equals(r.foundBy));
        check("json-ld thumbnail is not taken", !r.url.contains(".jpg"));
    }

    // 9. twitter:player:stream beats twitter:player.
    private static void testTwitterStreamBeforePlayer() {
        String html = page("<meta name=\"twitter:player:stream\" content=\"https://cdn.example.com/tw.mp4\">"
                + "<meta name=\"twitter:player\" content=\"https://player.example.com/embed/9\">");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://tw.example.com/s/1");
        check("twitter stream wins", r.ok);
        check("twitter stream url", "https://cdn.example.com/tw.mp4".equals(r.url));
        check("twitter stream not followed", r.followUrl == null);
    }

    // 12. twitter:player alone becomes something to follow.
    private static void testTwitterPlayerIsFollowed() {
        String html = page("<meta name=\"twitter:player\" content=\"https://player.example.com/embed/77\">");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://tw.example.com/s/2");
        check("twitter player not accepted as media", !r.ok);
        check("twitter player is followed",
                "https://player.example.com/embed/77".equals(r.followUrl));
    }

    // 1. An iframe embed page.
    private static void testIframeEmbedIsFollowed() {
        String html = page("<iframe src=\"//embed.example.com/embed/abc\" width=\"640\"></iframe>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://site.example.com/watch");
        check("iframe not taken as media", !r.ok);
        check("iframe is followed", "https://embed.example.com/embed/abc".equals(r.followUrl));
    }

    // The poster-image trap.
    private static void testPosterImageIsNotTheVideo() {
        String html = page("<meta property=\"og:image\" content=\"https://cdn.example.com/video.jpg\">"
                + "<video poster=\"https://cdn.example.com/video-thumb.png\">"
                + "<source src=\"https://cdn.example.com/real.mp4\"></video>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://site.example.com/p");
        check("poster image skipped", r.ok && r.url.endsWith("/real.mp4"));
        check("poster url is not used", r.ok && !r.url.contains("thumb"));
    }

    // 8. Flowplayer.
    private static void testFlowplayer() {
        String html = page("<script>flowplayer(\"p\", \"fp.swf\", {"
                + "clip: {url: \"https://cdn.example.com/flow.mp4\"}});</script>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://site.example.com/f");
        check("flowplayer found", r.ok);
        check("flowplayer url", "https://cdn.example.com/flow.mp4".equals(r.url));
    }

    // 11. A meta refresh.
    private static void testMetaRefresh() {
        String html = page("<meta http-equiv=\"refresh\" "
                + "content=\"0; url=https://cdn.example.com/refresh.mp4\">");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://site.example.com/r");
        check("meta refresh found", r.ok);
        check("meta refresh url", "https://cdn.example.com/refresh.mp4".equals(r.url));
    }

    // The last resort: a page that just links the file.
    private static void testLooseLink() {
        String html = page("<p><a href=\"/files/holiday.mp4\">Download</a></p>");
        PageMediaFinder.Result r = PageMediaFinder.parse(html, "https://site.example.com/holiday");
        check("loose link found", r.ok);
        check("loose link absolute", "https://site.example.com/files/holiday.mp4".equals(r.url));
        check("loose link rule", "loose-media-url".equals(r.foundBy));
    }

    private static void testNothingFound() {
        PageMediaFinder.Result r = PageMediaFinder.parse(
                page("<h1>Nothing here</h1><p>Just words.</p>"), "https://site.example.com/none");
        check("empty page reports nothing", !r.ok);
        check("empty page explains itself", r.reason != null
                && r.reason.contains("play it in the browser"));
    }

    // The extension sets themselves, taken from yt-dlp's MEDIA_EXTENSIONS.
    private static void testExtensionSets() {
        String[] good = { "https://c.example.com/a.mp4", "https://c.example.com/a.webm",
                "https://c.example.com/a.mkv", "https://c.example.com/a.m3u8",
                "https://c.example.com/a.mpd", "https://c.example.com/a.m4a",
                "https://c.example.com/a.mp3", "https://c.example.com/a.flv",
                "https://c.example.com/a.mov", "https://c.example.com/a.ogv" };
        for (String url : good) {
            check("media accepted: " + url, PageMediaFinder.isAcceptable(url));
        }
        String[] bad = { "https://c.example.com/a.swf", "https://c.example.com/a.jpg",
                "https://c.example.com/a.vtt", "https://c.example.com/a.js",
                "https://c.example.com/a.json", "https://c.example.com/a.html" };
        for (String url : bad) {
            check("media refused: " + url, !PageMediaFinder.isAcceptable(url));
        }
        check("mime video accepted",
                PageMediaFinder.isAcceptable("https://rr1.googlevideo.com/videoplayback?mime=video%2Fmp4"));
        check("relative is not media", !PageMediaFinder.isAcceptable("/files/a.mp4"));
    }

    // A page address must not be mistaken for a stream.
    private static void testManifestDetection() {
        check("m3u8 page is not a page", !PageMediaFinder.isPageUrl("https://c.example.com/a.m3u8"));
        check("mpd is not a page", !PageMediaFinder.isPageUrl("https://c.example.com/a.mpd"));
        check("videoplayback is not a page",
                !PageMediaFinder.isPageUrl("https://rr1.googlevideo.com/videoplayback?id=1"));
        check("watch page is a page", PageMediaFinder.isPageUrl("https://www.youtube.com/watch?v=x"));
        check("bare id is a page-ish url", PageMediaFinder.isPageUrl("https://x.com/abc"));
        check("ftp is not a page", !PageMediaFinder.isPageUrl("ftp://x.com/a"));
    }

    private static void testAbsoluteResolution() {
        check("protocol relative", "https://c.example.com/a.mp4".equals(
                PageMediaFinder.absolute("//c.example.com/a.mp4", "https://page.example.com/x")));
        check("root relative", "https://page.example.com/a.mp4".equals(
                PageMediaFinder.absolute("/a.mp4", "https://page.example.com/x")));
        check("no host no guess", "".equals(PageMediaFinder.absolute("/a.mp4", "")));
        check("javascript is refused", "".equals(
                PageMediaFinder.absolute("javascript:void(0)", "https://p.example.com")));
    }

    private static void testQualityHint() {
        check("1080p read", "1080p".equals(PageMediaFinder.qualityOf(
                "Best quality 1080p available")));
        check("720p read", "720p".equals(PageMediaFinder.qualityOf("720P stream")));
        check("absurd height ignored", "".equals(PageMediaFinder.qualityOf("99999p")));
        check("no height", "".equals(PageMediaFinder.qualityOf("no numbers here")));
    }

    private static void testUnescaping() {
        check("slash escape", "https://c.example.com/a.mp4".equals(
                PageMediaFinder.unescape("https:\\/\\/c.example.com\\/a.mp4")));
        check("amp escape", "a&b".equals(PageMediaFinder.unescape("a&amp;b")));
        check("quote escape", "a\"b".equals(PageMediaFinder.unescape("a&quot;b")));
        check("unicode amp", "a&b".equals(PageMediaFinder.unescape("a\\u0026b")));
    }

    // The follow-page path, without network: the inner page is parsed by the
    // same rules, so a player page that declares its file is found.
    private static void testEmbeddedThenFound() {
        String inner = page("<script>jwplayer(\"p\").setup({file:\"https://cdn.example.com/inner.mp4\"});</script>");
        PageMediaFinder.Result r = PageMediaFinder.parse(inner, "https://embed.example.com/embed/abc");
        check("embed page parses", r.ok);
        check("embed page url", "https://cdn.example.com/inner.mp4".equals(r.url));
    }
}
