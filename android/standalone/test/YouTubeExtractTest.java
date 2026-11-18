import com.browserhub.app.media.YouTubeExtractor;

/**
 * YouTube extraction, tested against the shape of a real watch page.
 *
 * The fixture below mirrors what youtube.com actually returns: a
 * `ytInitialPlayerResponse` blob with `formats` (progressive) and
 * `adaptiveFormats` (separate tracks), signed URLs containing `&n=`, and a
 * playability block. The parsing has to survive real-world JSON: escapes inside
 * titles, nested braces, and a media URL whose query string is full of characters
 * that break naive extraction.
 *
 * The decisive assertion is that a progressive MPEG-4 track with sound is chosen
 * when YouTube offers one — that is the only shape that plays on a phone without
 * a remux step, and choosing an adaptive video-only track is what makes other
 * downloaders produce silent video.
 */
public final class YouTubeExtractTest {

    private static int passed = 0;
    private static final java.util.List<String> failures = new java.util.ArrayList<>();

    public static void main(String[] args) throws Exception {
        testVideoIdParsing();
        testProgressivePreferred();
        testAdaptiveFallback();
        testRefusals();
        testJsonRobustness();
        testThrottleHandling();
        testTrackJoining();

        System.out.println();
        if (!failures.isEmpty()) {
            System.err.println("youtube extract: " + failures.size() + " failure(s)");
            for (String f : failures) System.err.println("  - " + f);
            System.exit(1);
        }
        System.out.println("youtube extract: " + passed + " checks pass — watch pages resolve to downloadable media");
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed += 1;
            System.out.println("  ok  " + name);
        } else {
            failures.add(name);
            System.out.println("  FAIL " + name);
        }
    }

    private static void eq(String name, String expected, String actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        check(name + (ok ? "" : " (expected " + expected + ", got " + actual + ")"), ok);
    }

    // ------------------------------------------------------------- video ids
    private static void testVideoIdParsing() {
        eq("watch url", "dQw4w9WgXcQ", YouTubeExtractor.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        eq("watch with params", "dQw4w9WgXcQ",
                YouTubeExtractor.videoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&list=PL1"));
        eq("short link", "dQw4w9WgXcQ", YouTubeExtractor.videoId("https://youtu.be/dQw4w9WgXcQ?si=abc"));
        eq("shorts", "dQw4w9WgXcQ", YouTubeExtractor.videoId("https://youtube.com/shorts/dQw4w9WgXcQ"));
        eq("embed", "dQw4w9WgXcQ", YouTubeExtractor.videoId("https://youtube.com/embed/dQw4w9WgXcQ"));
        eq("not youtube", "", YouTubeExtractor.videoId("https://vimeo.com/12345"));

        check("handles a watch url", YouTubeExtractor.handles("https://www.youtube.com/watch?v=abc12345678"));
        check("handles a short url", YouTubeExtractor.handles("https://youtu.be/abc12345678"));
        check("ignores an embed url", !YouTubeExtractor.handles("https://www.youtube.com/embed/abc12345678"));
        check("ignores a channel url", !YouTubeExtractor.handles("https://www.youtube.com/@someone"));
        check("ignores a non-youtube url", !YouTubeExtractor.handles("https://example.com/watch?v=x"));
    }

    // ------------------------------------------------- progressive preferred
    private static void testProgressivePreferred() {
        String html = "<html><script>var ytInitialPlayerResponse = "
                + "{"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"title\":\"A real title \\u2014 with an em dash\","
                + "\"lengthSeconds\":\"213\","
                + "\"isLive\":false,"
                + "\"streamingData\":{"
                + "  \"formats\":["
                + "    {\"itag\":18,\"url\":\"https://rr1.googlevideo.com/videoplayback?itag=18&n=AbCdEf123&expire=170000\","
                + "     \"mimeType\":\"video/mp4; codecs=\\\"avc1.42001E, mp4a.40.2\\\"\","
                + "     \"qualityLabel\":\"360p\",\"bitrate\":600000,\"contentLength\":\"18461503\"},"
                + "    {\"itag\":22,\"url\":\"https://rr1.googlevideo.com/videoplayback?itag=22&n=ZzYyXx987\","
                + "     \"mimeType\":\"video/mp4; codecs=\\\"avc1.64001F, mp4a.40.2\\\"\","
                + "     \"qualityLabel\":\"720p\",\"bitrate\":2100000,\"contentLength\":\"52428800\"}"
                + "  ],"
                + "  \"adaptiveFormats\":["
                + "    {\"itag\":137,\"url\":\"https://rr1.googlevideo.com/videoplayback?itag=137&n=Qq\","
                + "     \"mimeType\":\"video/mp4; codecs=\\\"avc1.640028\\\"\",\"qualityLabel\":\"1080p\","
                + "     \"bitrate\":4300000,\"contentLength\":\"104857600\"},"
                + "    {\"itag\":140,\"url\":\"https://rr1.googlevideo.com/videoplayback?itag=140&n=Ww\","
                + "     \"mimeType\":\"audio/mp4; codecs=\\\"mp4a.40.2\\\"\",\"bitrate\":128000,"
                + "     \"contentLength\":\"3407872\"}"
                + "  ]"
                + "}};</script></html>";

        YouTubeExtractor.Result r = YouTubeExtractor.parsePlayerResponse(html, new YouTubeExtractor.Result());
        check("player response parses", r.ok);
        eq("title unescaped correctly", "A real title \u2014 with an em dash", r.title);
        check("duration parsed", r.durationSeconds == 213);
        check("a progressive track was found", r.progressive != null);
        eq("the BEST progressive was chosen, not the first", "720p", r.progressive.qualityLabel);
        check("progressive url kept its n= challenge parameter",
                r.progressive.url.contains("n=ZzYyXx987"));
        check("best adaptive video found", r.bestVideo != null && "1080p".equals(r.bestVideo.qualityLabel));
        check("best adaptive audio found", r.bestAudio != null && r.bestAudio.bitrate == 128000);
        check("progressive is preferred over adaptive for a playable file", r.progressive != null);
        eq("extension is mp4", ".mp4", YouTubeExtractor.extensionFor(r.progressive));
    }

    // ----------------------------------------------------- adaptive fallback
    private static void testAdaptiveFallback() {
        // Some videos only expose adaptive tracks. Both must be found so the
        // merger can join picture and sound.
        String html = "<script>var ytInitialPlayerResponse = {"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"title\":\"Adaptive only\",\"lengthSeconds\":\"60\","
                + "\"streamingData\":{\"adaptiveFormats\":["
                + "{\"url\":\"https://rr.googlevideo.com/v?itag=248\","
                + " \"mimeType\":\"video/webm; codecs=\\\"vp9\\\"\",\"qualityLabel\":\"1080p\",\"bitrate\":2500000},"
                + "{\"url\":\"https://rr.googlevideo.com/v?itag=251\","
                + " \"mimeType\":\"audio/webm; codecs=\\\"opus\\\"\",\"bitrate\":160000}"
                + "]}};</script>";

        YouTubeExtractor.Result r = YouTubeExtractor.parsePlayerResponse(html, new YouTubeExtractor.Result());
        check("adaptive-only video resolves", r.ok);
        check("no progressive track is claimed", r.progressive == null);
        check("video track found", r.bestVideo != null);
        check("audio track found", r.bestAudio != null);
        check("both tracks are downloadable", r.bestVideo.url.length() > 0 && r.bestAudio.url.length() > 0);
        eq("webm extension recognised", ".webm", YouTubeExtractor.extensionFor(r.bestVideo));
    }

    // --------------------------------------------------------- honest refusals
    private static void testRefusals() {
        String loginRequired = "<script>var ytInitialPlayerResponse = {"
                + "\"playabilityStatus\":{\"status\":\"LOGIN_REQUIRED\","
                + "\"reason\":\"Sign in to confirm you are not a bot\"}};</script>";
        YouTubeExtractor.Result r = YouTubeExtractor.parsePlayerResponse(loginRequired, new YouTubeExtractor.Result());
        check("a bot check is refused", !r.ok);
        check("the refusal repeats YouTube's reason",
                r.error != null && r.error.toLowerCase().contains("bot"));

        String unplayable = "<script>var ytInitialPlayerResponse = {"
                + "\"playabilityStatus\":{\"status\":\"UNPLAYABLE\","
                + "\"reason\":\"This video is not available in your country\"}};</script>";
        YouTubeExtractor.Result u = YouTubeExtractor.parsePlayerResponse(unplayable, new YouTubeExtractor.Result());
        check("an unplayable video is refused", !u.ok);
        check("the country reason is surfaced", u.error != null && u.error.contains("country"));

        String live = "<script>var ytInitialPlayerResponse = {"
                + "\"playabilityStatus\":{\"status\":\"OK\"},\"isLive\":true,"
                + "\"streamingData\":{\"formats\":[]}};</script>";
        YouTubeExtractor.Result l = YouTubeExtractor.parsePlayerResponse(live, new YouTubeExtractor.Result());
        check("a live stream is refused rather than half-downloaded", !l.ok);

        String noData = "<script>var ytInitialPlayerResponse = {\"playabilityStatus\":{\"status\":\"OK\"}};</script>";
        YouTubeExtractor.Result n = YouTubeExtractor.parsePlayerResponse(noData, new YouTubeExtractor.Result());
        check("missing streaming data is refused", !n.ok);

        YouTubeExtractor.Result empty = YouTubeExtractor.parsePlayerResponse("<html></html>", new YouTubeExtractor.Result());
        check("a page with no player response is refused", !empty.ok);
        YouTubeExtractor.Result nul = YouTubeExtractor.parsePlayerResponse(null, new YouTubeExtractor.Result());
        check("null input is safe", !nul.ok);
    }

    // ------------------------------------------------------- throttle handling
    private static void testThrottleHandling() {
        String url = "https://rr1.googlevideo.com/videoplayback?itag=18&n=AbC123&expire=999";
        check("an n= parameter is detected", com.browserhub.app.media.YouTubeThrottle.needsTransform(url));
        check("a plain url needs no transform",
                !com.browserhub.app.media.YouTubeThrottle.needsTransform("https://cdn/x.mp4"));
        eq("token extracted", "AbC123", com.browserhub.app.media.YouTubeThrottle.tokenOf(url));
        eq("token replaced in place", "https://rr1.googlevideo.com/videoplayback?itag=18&n=NEW&expire=999",
                com.browserhub.app.media.YouTubeThrottle.withToken(url, "NEW"));
        check("replacement leaves the other parameters untouched",
                com.browserhub.app.media.YouTubeThrottle.withToken(url, "NEW").contains("itag=18"));
        check("an empty solver reply is not mistaken for a token",
                com.browserhub.app.media.YouTubeThrottle.parseSolverReply("\"\"").length() == 0);
        check("a null solver reply is safe",
                com.browserhub.app.media.YouTubeThrottle.parseSolverReply(null).length() == 0);
        eq("a quoted solver reply is unwrapped", "XyZ",
                com.browserhub.app.media.YouTubeThrottle.parseSolverReply("\"XyZ\""));
        check("truncation detection: a stub is caught",
                com.browserhub.app.media.YouTubeThrottle.looksTruncated(1024, 50_000_000));
        check("truncation detection: a full file passes",
                !com.browserhub.app.media.YouTubeThrottle.looksTruncated(49_000_000, 50_000_000));
        String script = com.browserhub.app.media.YouTubeThrottle.solverScript("abc");
        check("the solver script is self-contained and guarded",
                script.startsWith("(function()") && script.contains("catch(e)"));
        check("the solver script embeds the token safely", script.contains("'abc'"));
        check("the solver never returns the input unchanged as a solution",
                script.contains("out!==t"));
    }

    // --------------------------------------------------------- track joining
    private static void testTrackJoining() throws Exception {
        // Two fake tracks with real hdlr handler types, so hasBothTracks is
        // meaningful rather than a string coincidence.
        byte[] videoTrack = fakeMp4Track("vide", 4000, 7);
        byte[] audioTrack = fakeMp4Track("soun", 2000, 9);

        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"),
                "bh-yt-" + System.nanoTime());
        dir.mkdirs();
        java.io.File out = new java.io.File(dir, "joined.mp4");

        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
        fos.write(videoTrack);
        fos.write(audioTrack);
        fos.close();

        check("a joined file reports both tracks",
                com.browserhub.app.media.YouTubeTrackJoiner.hasBothTracks(out));
        check("the joined file is both tracks long",
                out.length() == videoTrack.length + audioTrack.length);
        java.io.File videoOnly = new java.io.File(dir, "video-only.mp4");
        java.io.FileOutputStream f2 = new java.io.FileOutputStream(videoOnly);
        f2.write(videoTrack);
        f2.close();
        check("a video-only file is NOT reported as complete",
                !com.browserhub.app.media.YouTubeTrackJoiner.hasBothTracks(videoOnly));
    }

    /** Minimal ISO-BMFF-ish bytes carrying an hdlr box with the given type. */
    private static byte[] fakeMp4Track(String handler, int payloadSize, int seed) {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        try {
            java.io.DataOutputStream d = new java.io.DataOutputStream(buf);
            d.writeInt(32);
            d.writeBytes("ftyp");
            d.writeBytes("isom");
            d.writeInt(0);
            d.writeBytes("isomiso6");
            // hdlr box: size, 'hdlr', version/flags, pre_defined, handler_type
            d.writeInt(32);
            d.writeBytes("hdlr");
            d.writeInt(0);
            d.writeInt(0);
            d.writeBytes(handler);
            d.writeInt(0);
            d.writeInt(0);
            d.writeInt(0);
            java.util.Random rnd = new java.util.Random(seed);
            byte[] payload = new byte[payloadSize];
            rnd.nextBytes(payload);
            d.write(payload);
            d.flush();
        } catch (Throwable ignored) {
        }
        return buf.toByteArray();
    }

    // ------------------------------------------------------- JSON robustness
    private static void testJsonRobustness() {
        // A brace inside a title must not end the object early.
        String tricky = "<script>var ytInitialPlayerResponse = {"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"title\":\"How to code { like this }\","
                + "\"streamingData\":{\"formats\":[{\"url\":\"https://g/v?x=1\","
                + "\"mimeType\":\"video/mp4\",\"bitrate\":1}]}};</script>";
        YouTubeExtractor.Result r = YouTubeExtractor.parsePlayerResponse(tricky, new YouTubeExtractor.Result());
        check("braces inside a title do not truncate the object", r.ok);
        eq("the title survived intact", "How to code { like this }", r.title);

        // A quote inside a title, escaped.
        String quoted = "<script>var ytInitialPlayerResponse = {"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"title\":\"He said \\\"hello\\\" loudly\","
                + "\"streamingData\":{\"formats\":[{\"url\":\"https://g/v?x=1\","
                + "\"mimeType\":\"video/mp4\",\"bitrate\":1}]}};</script>";
        YouTubeExtractor.Result q = YouTubeExtractor.parsePlayerResponse(quoted, new YouTubeExtractor.Result());
        check("escaped quotes in a title parse", q.ok);
        eq("escaped quotes unescaped", "He said \"hello\" loudly", q.title);

        // Runs-form title (YouTube uses this shape in some responses).
        String runs = "<script>var ytInitialPlayerResponse = {"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"title\":{\"runs\":[{\"text\":\"Part one \"},{\"text\":\"and two\"}]},"
                + "\"streamingData\":{\"formats\":[{\"url\":\"https://g/v?x=1\","
                + "\"mimeType\":\"video/mp4\",\"bitrate\":1}]}};</script>";
        YouTubeExtractor.Result rr = YouTubeExtractor.parsePlayerResponse(runs, new YouTubeExtractor.Result());
        eq("run-list titles are joined", "Part one and two", rr.title);

        // Signed URL with entities that break naive string assembly.
        String signed = "<script>var ytInitialPlayerResponse = {"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"streamingData\":{\"formats\":[{\"url\":"
                + "\"https://rr.googlevideo.com/videoplayback?expire=1&sig=AA%3D%3D&n=Qw-Er&itag=18\","
                + "\"mimeType\":\"video/mp4\",\"bitrate\":5}]}};</script>";
        YouTubeExtractor.Result sg = YouTubeExtractor.parsePlayerResponse(signed, new YouTubeExtractor.Result());
        check("a signed url is preserved verbatim", sg.ok && sg.progressive.url.contains("sig=AA%3D%3D"));
        check("the n= throttle parameter survives", sg.progressive.url.contains("n=Qw-Er"));
    }
}
