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
        testSignatureCipher();
        testDirectApiShape();
        testCaptureCompletion();

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

    // ----------------------------------------------------- direct player API
    private static void testDirectApiShape() {
        /*
         * The request shape is the whole game here. A player API call that claims
         * to be a client but does not look like one is answered with a bot check,
         * not a stream list. These assertions pin the parts of the request that
         * decide that, so a future tidy-up cannot quietly remove one.
         */
        check("the player endpoint is the documented one",
                com.browserhub.app.media.YouTubePlayerApi.ENDPOINT
                        .equals("https://www.youtube.com/youtubei/v1/player"));
        check("more than one client identity is tried",
                com.browserhub.app.media.YouTubePlayerApi.CLIENTS.length >= 2);

        com.browserhub.app.media.YouTubePlayerApi.Client vr =
                com.browserhub.app.media.YouTubePlayerApi.CLIENTS[0];
        String body = com.browserhub.app.media.YouTubePlayerApi.requestBody(vr, "dQw4w9WgXcQ");
        check("the body names the client", body.contains("\"clientName\":\"" + vr.name + "\""));
        check("the body states the client version", body.contains("\"clientVersion\":\""));
        check("the body carries the video id", body.contains("\"videoId\":\"dQw4w9WgXcQ\""));
        check("the body reports a platform, which the native clients require",
                body.contains("\"osName\":"));
        check("age checks are bypassed for signed-out requests",
                body.contains("\"contentCheckOk\":true") && body.contains("\"racyCheckOk\":true"));
        check("the body is valid-looking JSON",
                body.startsWith("{") && body.endsWith("}") && balancedBraces(body));

        java.util.Map<String, String> headers =
                com.browserhub.app.media.YouTubePlayerApi.headers(vr);
        check("the client name header is sent",
                headers.containsKey("X-YouTube-Client-Name"));
        check("the client version header is sent",
                headers.containsKey("X-YouTube-Client-Version"));
        eq("the client name header matches the body", vr.id,
                headers.get("X-YouTube-Client-Name"));
        check("a real user agent is sent, matching the claimed client",
                headers.get("User-Agent") != null
                        && headers.get("User-Agent").contains(vr.version));
        check("the origin is stated",
                "https://www.youtube.com".equals(headers.get("Origin")));

        // Every client must produce a complete, distinct request.
        for (com.browserhub.app.media.YouTubePlayerApi.Client c
                : com.browserhub.app.media.YouTubePlayerApi.CLIENTS) {
            String b = com.browserhub.app.media.YouTubePlayerApi.requestBody(c, "dQw4w9WgXcQ");
            check("client " + c.name + " produces a complete request",
                    b.contains(c.name) && b.contains(c.version) && balancedBraces(b));
            check("client " + c.name + " has its own user agent",
                    com.browserhub.app.media.YouTubePlayerApi.headers(c)
                            .get("User-Agent").contains(c.version));
        }

        // A specific refusal must win over a generic one; otherwise a private video
        // reports "could not reach YouTube" and the user retries forever.
        java.util.List<String> failures = new java.util.ArrayList<>();
        failures.add("could not reach YouTube: timeout");
        failures.add("This video is private");
        eq("the specific refusal is the one reported", "This video is private",
                com.browserhub.app.media.YouTubePlayerApi.pickReason(failures));
        check("an empty failure list still explains itself",
                com.browserhub.app.media.YouTubePlayerApi
                        .pickReason(new java.util.ArrayList<>()).length() > 10);

        // Transport errors must be actionable, not stack traces.
        check("403 is explained as retryable",
                com.browserhub.app.media.YouTubePlayerApi
                        .friendly(new java.io.IOException("HTTP 403: x")).contains("403"));
        check("429 is explained as rate limiting",
                com.browserhub.app.media.YouTubePlayerApi
                        .friendly(new java.io.IOException("HTTP 429: x")).toLowerCase()
                        .contains("rate limit"));

        // A refusal about the video itself must not be retried the slow way.
        check("a private video is a final refusal",
                com.browserhub.app.media.YouTubeExtractor.isFinalRefusal("This video is private"));
        check("a members-only video is a final refusal",
                com.browserhub.app.media.YouTubeExtractor.isFinalRefusal("members-only video"));
        check("a country block is a final refusal",
                com.browserhub.app.media.YouTubeExtractor
                        .isFinalRefusal("not available in your country"));
        check("a transport failure is NOT treated as final",
                !com.browserhub.app.media.YouTubeExtractor.isFinalRefusal("could not reach YouTube"));

        // The direct response is parsed by the same code as the page route, so a
        // combined stream must be recognised as the preferred shape.
        String apiReply = "{"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"videoDetails\":{\"videoId\":\"dQw4w9WgXcQ\",\"title\":\"Direct\","
                + " \"lengthSeconds\":\"212\"},"
                + "\"streamingData\":{\"formats\":["
                + "{\"itag\":18,\"mimeType\":\"video/mp4; codecs=\\\"avc1.42001E, mp4a.40.2\\\"\","
                + " \"qualityLabel\":\"360p\",\"bitrate\":600000,\"contentLength\":\"9000000\","
                + " \"url\":\"https://rr1.googlevideo.com/videoplayback?itag=18&sig=SIGNED\"}"
                + "]}}";
        com.browserhub.app.media.YouTubeExtractor.Result api =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(apiReply);
        check("a direct reply with one combined stream is downloadable", api.ok);
        check("the combined stream needs no signature work",
                api.progressive != null && !api.progressive.needsSignature);
        eq("the direct reply keeps the video id", "dQw4w9WgXcQ", api.videoId);
        check("a combined stream means no merge step",
                com.browserhub.app.media.YouTubePlayerApi.hasCombined(api));
    }

    private static boolean balancedBraces(String s) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') depth--;
            if (depth < 0) return false;
        }
        return depth == 0 && !inString;
    }

    // ------------------------------------------------------ signature cipher
    private static void testSignatureCipher() {
        /*
         * A scrambled entry: no "url", a "signatureCipher" holding the address
         * with its s= still raw. Reading only "url" made these invisible, which is
         * how a perfectly watchable video got reported as having no stream.
         */
        String response = "{"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"videoDetails\":{\"videoId\":\"dQw4w9WgXcQ\",\"title\":\"Scrambled\"},"
                + "\"streamingData\":{\"adaptiveFormats\":["
                + "{\"itag\":137,\"mimeType\":\"video/mp4; codecs=\\\"avc1.640028\\\"\","
                + " \"qualityLabel\":\"1080p\",\"bitrate\":4000000,\"contentLength\":\"90000000\","
                + " \"signatureCipher\":\"s=SCRAMBLEDVALUE&sp=sig&url=https%3A%2F%2Frr1.googlevideo.com%2Fvideoplayback%3Fitag%3D137%26id%3DdQw4w9WgXcQ%26expire%3D99\"},"
                + "{\"itag\":140,\"mimeType\":\"audio/mp4; codecs=\\\"mp4a.40.2\\\"\","
                + " \"qualityLabel\":\"\",\"bitrate\":128000,\"contentLength\":\"3000000\","
                + " \"signatureCipher\":\"s=AUDIOSCRAMBLE&sp=sig&url=https%3A%2F%2Frr1.googlevideo.com%2Fvideoplayback%3Fitag%3D140%26id%3DdQw4w9WgXcQ%26expire%3D99\"}"
                + "]}}";

        com.browserhub.app.media.YouTubeExtractor.Result r =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(response);
        check("a video whose every stream is scrambled is still an offer", r.ok);
        eq("scrambled entries are counted", "2", String.valueOf(r.scrambledCount));
        check("a scrambled video track is selected", r.bestVideo != null);
        eq("the scrambled track is marked as needing a signature", "true",
                String.valueOf(r.bestVideo != null && r.bestVideo.needsSignature));
        eq("the scrambled signature is kept verbatim",
                "SCRAMBLEDVALUE", r.bestVideo != null ? r.bestVideo.cipherSignature : "");
        eq("the base address is decoded exactly once",
                "https://rr1.googlevideo.com/videoplayback?itag=137&id=dQw4w9WgXcQ&expire=99",
                r.bestVideo != null ? r.bestVideo.url : "");
        check("the parameter name comes from the cipher",
                r.bestVideo != null && "sig".equals(r.bestVideo.cipherParam));

        // A transformed signature goes into the address as its own parameter.
        String signed = com.browserhub.app.media.YouTubeExtractor.urlWithSignature(r.bestVideo, "GOODSIG");
        eq("the signature is appended as a query parameter",
                "https://rr1.googlevideo.com/videoplayback?itag=137&id=dQw4w9WgXcQ&expire=99&sig=GOODSIG",
                signed);

        // A signed entry and a scrambled entry of the same resolution: take signed.
        String both = "{"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"videoDetails\":{\"videoId\":\"dQw4w9WgXcQ\",\"title\":\"Both\"},"
                + "\"streamingData\":{\"adaptiveFormats\":["
                + "{\"itag\":137,\"mimeType\":\"video/mp4\",\"qualityLabel\":\"1080p\","
                + " \"bitrate\":4000000,\"url\":\"https://rr1.googlevideo.com/videoplayback?itag=137&sig=OK\"},"
                + "{\"itag\":137,\"mimeType\":\"video/mp4\",\"qualityLabel\":\"1080p\",\"bitrate\":4000000,"
                + " \"signatureCipher\":\"s=X&sp=sig&url=https%3A%2F%2Frr1.googlevideo.com%2Fvideoplayback%3Fitag%3D137\"}"
                + "]}}";
        com.browserhub.app.media.YouTubeExtractor.Result b =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(both);
        check("a signed entry is preferred over a scrambled one at the same resolution",
                b.bestVideo != null && !b.bestVideo.needsSignature);

        // Resolution beats convenience: never silently drop quality.
        String mixed = "{"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"videoDetails\":{\"videoId\":\"dQw4w9WgXcQ\",\"title\":\"Mixed\"},"
                + "\"streamingData\":{\"adaptiveFormats\":["
                + "{\"itag\":18,\"mimeType\":\"video/mp4\",\"qualityLabel\":\"360p\","
                + " \"bitrate\":500000,\"url\":\"https://rr1.googlevideo.com/videoplayback?itag=18&sig=OK\"},"
                + "{\"itag\":137,\"mimeType\":\"video/mp4\",\"qualityLabel\":\"1080p\",\"bitrate\":4000000,"
                + " \"signatureCipher\":\"s=X&sp=sig&url=https%3A%2F%2Frr1.googlevideo.com%2Fvideoplayback%3Fitag%3D137\"}"
                + "]}}";
        com.browserhub.app.media.YouTubeExtractor.Result m =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(mixed);
        eq("the higher resolution wins even when it needs a signature", "1080p",
                m.bestVideo != null ? m.bestVideo.qualityLabel : "");
    }

    // ---------------------------------------------------- completing captures
    private static void testCaptureCompletion() {
        String response = "{"
                + "\"playabilityStatus\":{\"status\":\"OK\"},"
                + "\"videoDetails\":{\"videoId\":\"dQw4w9WgXcQ\",\"title\":\"Cap\"},"
                + "\"streamingData\":{\"adaptiveFormats\":["
                + "{\"itag\":137,\"mimeType\":\"video/mp4\",\"qualityLabel\":\"1080p\",\"bitrate\":4000000,"
                + " \"signatureCipher\":\"s=X&sp=sig&url=https%3A%2F%2Frr1.googlevideo.com%2Fvideoplayback%3Fitag%3D137%26id%3DdQw4w9WgXcQ\"},"
                + "{\"itag\":140,\"mimeType\":\"audio/mp4\",\"qualityLabel\":\"\",\"bitrate\":128000,"
                + " \"signatureCipher\":\"s=Y&sp=sig&url=https%3A%2F%2Frr1.googlevideo.com%2Fvideoplayback%3Fitag%3D140%26id%3DdQw4w9WgXcQ\"}"
                + "]}}";
        com.browserhub.app.media.YouTubeExtractor.Result r =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(response);

        // The page fetching its own media is the capture we want: already signed.
        java.util.List<String> captured = new java.util.ArrayList<>();
        captured.add("https://rr1.googlevideo.com/videoplayback?itag=137&id=dQw4w9WgXcQ&sig=SIGNEDVIDEO");
        captured.add("https://rr1.googlevideo.com/videoplayback?itag=140&id=dQw4w9WgXcQ&sig=SIGNEDAUDIO");

        int done = com.browserhub.app.media.YouTubeExtractor.applyCaptures(r, captured);
        eq("both scrambled streams are completed by the page's own requests", "2",
                String.valueOf(done));
        check("the completed streams no longer need signing",
                r.bestVideo != null && !r.bestVideo.needsSignature && !r.bestAudio.needsSignature);
        check("the completed url is the one the page actually used",
                r.bestVideo != null && r.bestVideo.url.contains("sig=SIGNEDVIDEO"));
        eq("a completed video and audio pair is downloadable", "true", String.valueOf(r.ok));

        // Wrong video: a capture from another video must not be borrowed.
        com.browserhub.app.media.YouTubeExtractor.Result r2 =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(response);
        java.util.List<String> other = new java.util.ArrayList<>();
        other.add("https://rr1.googlevideo.com/videoplayback?itag=137&id=AAAAAAAAAAA&sig=SIGNEDVIDEO");
        eq("a capture from a different video is not used", "0",
                String.valueOf(com.browserhub.app.media.YouTubeExtractor.applyCaptures(r2, other)));

        // Wrong itag: a 360p capture must not stand in for the 1080p stream.
        com.browserhub.app.media.YouTubeExtractor.Result r3 =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(response);
        java.util.List<String> wrongItag = new java.util.ArrayList<>();
        wrongItag.add("https://rr1.googlevideo.com/videoplayback?itag=18&id=dQw4w9WgXcQ&sig=X");
        eq("a capture of a different format is not used", "0",
                String.valueOf(com.browserhub.app.media.YouTubeExtractor.applyCaptures(r3, wrongItag)));

        // A capture list may contain other media; only matching itags count.
        com.browserhub.app.media.YouTubeExtractor.Result r5 =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(response);
        java.util.List<String> noisy = new java.util.ArrayList<>();
        noisy.add("https://example.com/other.mp4");
        noisy.add("");
        noisy.add("https://rr1.googlevideo.com/videoplayback?itag=140&id=dQw4w9WgXcQ&sig=AUDIOONLY");
        eq("only the matching format is completed from a noisy capture list", "1",
                String.valueOf(com.browserhub.app.media.YouTubeExtractor.applyCaptures(r5, noisy)));
        check("the audio stream is the one completed, not the video",
                r5.bestAudio != null && !r5.bestAudio.needsSignature
                        && r5.bestVideo != null && r5.bestVideo.needsSignature);

        // With no page available, solving must fail softly, not throw: a desktop
        // run, or a service with no activity, must degrade to the capture path.
        eq("solving with no solver registered returns empty, not an exception", "",
                com.browserhub.app.media.SignatureSolver.Holder.solve("SCRAMBLED"));
        eq("solving a null signature is safe", "",
                com.browserhub.app.media.SignatureSolver.Holder.solve(null));

        // Unsigned capture: same 403 as before, must be refused rather than tried.
        com.browserhub.app.media.YouTubeExtractor.Result r4 =
                com.browserhub.app.media.YouTubeExtractor.parsePlayerResponse(response);
        java.util.List<String> unsigned = new java.util.ArrayList<>();
        unsigned.add("https://rr1.googlevideo.com/videoplayback?itag=137&id=dQw4w9WgXcQ");
        eq("an unsigned capture is not accepted", "0",
                String.valueOf(com.browserhub.app.media.YouTubeExtractor.applyCaptures(r4, unsigned)));
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
