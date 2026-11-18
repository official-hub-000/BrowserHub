import com.browserhub.app.media.DashManifest;
import com.browserhub.app.media.DashMerger;
import com.browserhub.app.media.MediaMerger;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Random;

/**
 * Real DASH manifests, real HTTP, real bytes.
 *
 * The important assertion is the one naive mergers fail: a DASH stream where
 * picture and sound are separate tracks must produce a file containing BOTH.
 * Downloading only the video track yields a silent video, which is the single
 * most common fake "download" in this category.
 */
public final class DashMergeTest {

    private static int passed = 0;
    private static final java.util.List<String> failures = new java.util.ArrayList<>();
    private static com.sun.net.httpserver.HttpServer server;
    private static int port;
    private static final java.util.Map<String, byte[]> ROUTES = new java.util.HashMap<>();

    public static void main(String[] args) throws Exception {
        startServer();
        try {
            testTemplateWithSeparateTracks();
            testSegmentTimeline();
            testSegmentList();
            testSegmentBase();
            testDrmRefused();
            testMissingManifest();
        } finally {
            if (server != null) server.stop(0);
        }

        System.out.println();
        if (!failures.isEmpty()) {
            System.err.println("dash merge: " + failures.size() + " failure(s)");
            for (String f : failures) System.err.println("  - " + f);
            System.exit(1);
        }
        // ------------------------------------------------ parallel range download
        /*
         * These decide whether a fast download is correct. A range set that does not
         * cover the file exactly leaves a zero-filled hole at the end, which plays as
         * a video that stops early — the kind of bug nobody reports as "corruption".
         */
        java.util.List<long[]> r4 = com.browserhub.app.media.RangeDownloader
                .rangesFor(1000, 4);
        checkEq("four ranges are produced", "4", String.valueOf(r4.size()));
        checkEq("the first starts at zero", "0", String.valueOf(r4.get(0)[0]));
        checkEq("the last ends at the final byte", "999", String.valueOf(r4.get(3)[1]));
        boolean contiguous = true;
        for (int i = 1; i < r4.size(); i++) {
            if (r4.get(i)[0] != r4.get(i - 1)[1] + 1) contiguous = false;
        }
        check("the ranges are contiguous with no gap and no overlap", contiguous);

        // An odd length must not lose its tail.
        java.util.List<long[]> odd = com.browserhub.app.media.RangeDownloader
                .rangesFor(1001, 4);
        long covered = 0;
        for (long[] range : odd) covered += range[1] - range[0] + 1;
        checkEq("an odd length is covered exactly, with no tail lost", "1001",
                String.valueOf(covered));

        // Sizes that do not divide evenly are the normal case, not the edge case.
        for (long size : new long[] { 1, 2, 7, 1023, 1024, 1025, 999999 }) {
            java.util.List<long[]> ranges = com.browserhub.app.media.RangeDownloader
                    .rangesFor(size, 4);
            long sum = 0;
            for (long[] range : ranges) sum += range[1] - range[0] + 1;
            check("size " + size + " is covered exactly", sum == size);
        }

        // A small file must not be split into connections that each do nothing.
        checkEq("a tiny file uses one connection", "1",
                String.valueOf(com.browserhub.app.media.RangeDownloader.threadsFor(1000)));
        checkEq("a large file uses four", "4",
                String.valueOf(com.browserhub.app.media.RangeDownloader.threadsFor(50L * 1024 * 1024)));
        checkEq("an unknown size uses one", "1",
                String.valueOf(com.browserhub.app.media.RangeDownloader.threadsFor(0)));

        // Content-Range is the only trustworthy source of the total size.
        checkEq("the total is read from Content-Range", "12345",
                String.valueOf(com.browserhub.app.media.RangeDownloader
                        .totalFromContentRange("bytes 0-0/12345")));
        checkEq("an unknown total is not guessed", "0",
                String.valueOf(com.browserhub.app.media.RangeDownloader
                        .totalFromContentRange("bytes 0-0/*")));
        checkEq("a missing header is not guessed", "0",
                String.valueOf(com.browserhub.app.media.RangeDownloader
                        .totalFromContentRange(null)));

        System.out.println("dash merge: " + passed + " checks pass — separate tracks are joined into one file");
    }

    /** Equality with the actual value in the failure text, so a failure says why. */
    private static void checkEq(String name, String expected, String actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed += 1;
            System.out.println("  ok  " + name);
        } else {
            failures.add(name);
            System.out.println("  FAIL " + name + " (expected " + expected + ", got " + actual + ")");
        }
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

    // ----------------------------------------------------------- test server
    private static void startServer() throws Exception {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = ROUTES.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            String range = exchange.getRequestHeaders().getFirst("Range");
            int status = 200;
            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.substring(6).split("-");
                int start = Integer.parseInt(parts[0]);
                int end = parts.length > 1 && !parts[1].isEmpty()
                        ? Integer.parseInt(parts[1]) : body.length - 1;
                int len = Math.min(end - start + 1, body.length - start);
                byte[] slice = new byte[len];
                System.arraycopy(body, start, slice, 0, len);
                body = slice;
                status = 206;
            }
            exchange.sendResponseHeaders(status, body.length);
            OutputStream os = exchange.getResponseBody();
            os.write(body);
            os.close();
        });
        server.start();
    }

    private static String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static byte[] bytes(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static File tempOut(String name) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "bh-dash-" + System.nanoTime());
        dir.mkdirs();
        return new File(dir, name);
    }

    // ------------------------- SegmentTemplate, video and audio split apart
    private static void testTemplateWithSeparateTracks() throws Exception {
        byte[] vInit = bytes(200, 1);
        byte[] aInit = bytes(150, 2);
        byte[] v0 = bytes(800, 10);
        byte[] v1 = bytes(820, 11);
        byte[] a0 = bytes(300, 20);
        byte[] a1 = bytes(310, 21);

        // Numbering with a padded template: $Number%03d$.
        ROUTES.put("/t/v/init.mp4", vInit);
        ROUTES.put("/t/v/seg-001.m4s", v0);
        ROUTES.put("/t/v/seg-002.m4s", v1);
        ROUTES.put("/t/a/init.mp4", aInit);
        ROUTES.put("/t/a/seg-001.m4s", a0);
        ROUTES.put("/t/a/seg-002.m4s", a1);

        String mpd =
                "<?xml version=\"1.0\"?>"
                + "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" "
                + "mediaPresentationDuration=\"PT8S\" minBufferTime=\"PT2S\">"
                + "<Period id=\"1\" start=\"PT0S\" duration=\"PT8S\">"
                + "<AdaptationSet mimeType=\"video/mp4\">"
                + "<SegmentTemplate media=\"v/seg-$Number%03d$.m4s\" initialization=\"v/init.mp4\" "
                + "startNumber=\"1\" timescale=\"1000\" duration=\"4000\"/>"
                + "<Representation id=\"v1\" bandwidth=\"2000000\" width=\"1920\" height=\"1080\" codecs=\"avc1.640028\"/>"
                + "<Representation id=\"v2\" bandwidth=\"800000\" width=\"1280\" height=\"720\" codecs=\"avc1.4d401f\"/>"
                + "</AdaptationSet>"
                + "<AdaptationSet mimeType=\"audio/mp4\" lang=\"en\">"
                + "<SegmentTemplate media=\"a/seg-$Number%03d$.m4s\" initialization=\"a/init.mp4\" "
                + "startNumber=\"1\" timescale=\"1000\" duration=\"4000\"/>"
                + "<Representation id=\"a1\" bandwidth=\"128000\" codecs=\"mp4a.40.2\"/>"
                + "</AdaptationSet>"
                + "</Period></MPD>";
        ROUTES.put("/t/manifest.mpd", mpd.getBytes("UTF-8"));

        DashManifest parsed = DashManifest.parse(mpd, url("/t/manifest.mpd"));
        check("template manifest parses", parsed.ok);
        check("two video representations found", parsed.video.size() == 2);
        check("audio track found", parsed.audio.size() == 1);
        check("best video is 1080p, not the first listed", parsed.bestVideo().height == 1080);
        check("padded numbering expanded to 001",
                parsed.bestVideo().segments.get(0).url.endsWith("/t/v/seg-001.m4s"));
        check("second segment numbered 002",
                parsed.bestVideo().segments.get(1).url.endsWith("/t/v/seg-002.m4s"));
        check("init section resolved", parsed.bestVideo().initUrl.endsWith("/t/v/init.mp4"));
        check("audio init resolved separately",
                !parsed.bestAudio().initUrl.equals(parsed.bestVideo().initUrl));

        File out = tempOut("template.mp4");
        MediaMerger.Result r = DashMerger.merge(url("/t/manifest.mpd"), out, 0, "ua", null, null);
        check("dash merge succeeds" + (r.ok ? "" : " (" + r.error + ")"), r.ok);
        if (!r.ok) return;
        check("dash merge counted every part", r.segments == 4);

        byte[] written = java.nio.file.Files.readAllBytes(out.toPath());

        // The whole point: the video track alone would be 200+800+820 bytes.
        int videoOnly = vInit.length + v0.length + v1.length;
        check("output contains the audio track as well as the video", written.length == videoOnly + aInit.length + a0.length + a1.length);

        boolean hasVideoInit = contains(written, vInit);
        boolean hasAudioInit = contains(written, aInit);
        boolean hasV0 = contains(written, v0);
        boolean hasV1 = contains(written, v1);
        boolean hasA0 = contains(written, a0);
        boolean hasA1 = contains(written, a1);
        check("video init present", hasVideoInit);
        check("audio init present", hasAudioInit);
        check("both video fragments present", hasV0 && hasV1);
        check("both audio fragments present", hasA0 && hasA1);

        // Init sections must precede fragments or a player cannot decode.
        check("video init precedes the first video fragment",
                indexOf(written, vInit) < indexOf(written, v0));
        check("audio init precedes the first audio fragment",
                indexOf(written, aInit) < indexOf(written, a0));
    }

    // ------------------------------------------------------- SegmentTimeline
    private static void testSegmentTimeline() throws Exception {
        byte[] init = bytes(100, 30);
        byte[] s1 = bytes(400, 31);
        byte[] s2 = bytes(420, 32);
        byte[] s3 = bytes(440, 33);
        ROUTES.put("/tl/init.mp4", init);
        ROUTES.put("/tl/t1000.m4s", s1);
        ROUTES.put("/tl/t5000.m4s", s2);
        ROUTES.put("/tl/t9000.m4s", s3);

        String mpd =
                "<MPD mediaPresentationDuration=\"PT12S\"><Period duration=\"PT12S\">"
                + "<AdaptationSet mimeType=\"video/mp4\">"
                + "<SegmentTemplate media=\"t$Time$.m4s\" initialization=\"init.mp4\" timescale=\"1000\">"
                + "<SegmentTimeline><S t=\"1000\" d=\"4000\"/><S d=\"4000\"/><S d=\"4000\"/></SegmentTimeline>"
                + "</SegmentTemplate>"
                + "<Representation id=\"v\" bandwidth=\"1000000\" width=\"1280\" height=\"720\"/>"
                + "</AdaptationSet></Period></MPD>";
        ROUTES.put("/tl/manifest.mpd", mpd.getBytes("UTF-8"));

        DashManifest parsed = DashManifest.parse(mpd, url("/tl/manifest.mpd"));
        check("timeline manifest parses", parsed.ok);
        check("timeline produced three segments", parsed.bestVideo().segments.size() == 3);
        check("$Time$ uses the declared t", parsed.bestVideo().segments.get(0).url.endsWith("t1000.m4s"));
        check("implicit continuation time", parsed.bestVideo().segments.get(1).url.endsWith("t5000.m4s"));
        check("second implicit continuation", parsed.bestVideo().segments.get(2).url.endsWith("t9000.m4s"));

        File out = tempOut("timeline.mp4");
        MediaMerger.Result r = DashMerger.merge(url("/tl/manifest.mpd"), out, 0, "ua", null, null);
        check("timeline merge succeeds" + (r.ok ? "" : " (" + r.error + ")"), r.ok);
        if (!r.ok) return;
        byte[] written = java.nio.file.Files.readAllBytes(out.toPath());
        check("timeline output is init then all three fragments",
                contains(written, init) && contains(written, s1) && contains(written, s2) && contains(written, s3));
    }

    // ----------------------------------------------------------- SegmentList
    private static void testSegmentList() throws Exception {
        byte[] s1 = bytes(300, 41);
        byte[] s2 = bytes(320, 42);
        ROUTES.put("/sl/one.m4s", s1);
        ROUTES.put("/sl/two.m4s", s2);

        String mpd =
                "<MPD mediaPresentationDuration=\"PT4S\"><Period duration=\"PT4S\">"
                + "<AdaptationSet mimeType=\"video/mp4\">"
                + "<Representation id=\"v\" bandwidth=\"900000\" width=\"854\" height=\"480\">"
                + "<SegmentList><SegmentURL media=\"one.m4s\"/><SegmentURL media=\"two.m4s\"/></SegmentList>"
                + "</Representation></AdaptationSet></Period></MPD>";
        ROUTES.put("/sl/manifest.mpd", mpd.getBytes("UTF-8"));

        DashManifest parsed = DashManifest.parse(mpd, url("/sl/manifest.mpd"));
        check("segment list parses", parsed.ok);
        check("segment list produced two segments", parsed.bestVideo().segments.size() == 2);

        File out = tempOut("segmentlist.mp4");
        MediaMerger.Result r = DashMerger.merge(url("/sl/manifest.mpd"), out, 0, "ua", null, null);
        check("segment list merge succeeds" + (r.ok ? "" : " (" + r.error + ")"), r.ok);
        if (!r.ok) return;
        byte[] written = java.nio.file.Files.readAllBytes(out.toPath());
        check("segment list bytes are exact", equals(concat(s1, s2), written));
    }

    // ---------------------------------------------------------- SegmentBase
    private static void testSegmentBase() throws Exception {
        byte[] whole = bytes(900, 51);
        ROUTES.put("/sb/movie.mp4", whole);
        String mpd =
                "<MPD mediaPresentationDuration=\"PT10S\"><Period duration=\"PT10S\">"
                + "<AdaptationSet mimeType=\"video/mp4\">"
                + "<Representation id=\"v\" bandwidth=\"1200000\" width=\"1920\" height=\"1080\">"
                + "<BaseURL>movie.mp4</BaseURL><SegmentBase indexRange=\"0-100\"/>"
                + "</Representation></AdaptationSet></Period></MPD>";
        ROUTES.put("/sb/manifest.mpd", mpd.getBytes("UTF-8"));

        File out = tempOut("segmentbase.mp4");
        MediaMerger.Result r = DashMerger.merge(url("/sb/manifest.mpd"), out, 0, "ua", null, null);
        check("single-file dash succeeds" + (r.ok ? "" : " (" + r.error + ")"), r.ok);
        if (!r.ok) return;
        byte[] written = java.nio.file.Files.readAllBytes(out.toPath());
        check("the whole file is saved, not a fragment", equals(whole, written));
    }

    // --------------------------------------------------------- DRM refusal
    private static void testDrmRefused() throws Exception {
        String mpd =
                "<MPD mediaPresentationDuration=\"PT10S\"><Period duration=\"PT10S\">"
                + "<AdaptationSet mimeType=\"video/mp4\">"
                + "<ContentProtection schemeIdUri=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\">"
                + "<cenc:pssh>AAAA</cenc:pssh></ContentProtection>"
                + "<SegmentTemplate media=\"v/$Number$.m4s\" duration=\"4000\"/>"
                + "<Representation id=\"v\" bandwidth=\"1000000\" width=\"1920\" height=\"1080\"/>"
                + "</AdaptationSet></Period></MPD>";
        ROUTES.put("/drm/manifest.mpd", mpd.getBytes("UTF-8"));

        DashManifest parsed = DashManifest.parse(mpd, url("/drm/manifest.mpd"));
        check("widevine is detected from the scheme id", parsed.encrypted);
        check("the DRM system is named", "Widevine".equals(parsed.drmSystem));

        File out = tempOut("drm.mp4");
        MediaMerger.Result r = DashMerger.merge(url("/drm/manifest.mpd"), out, 0, "ua", null, null);
        check("a DRM stream is refused", !r.ok);
        check("no file is produced for a DRM stream", !out.exists());
    }

    private static void testMissingManifest() throws Exception {
        File out = tempOut("missing.mp4");
        MediaMerger.Result r = DashMerger.merge(url("/nope/manifest.mpd"), out, 0, "ua", null, null);
        check("a missing manifest fails honestly", !r.ok && r.error != null);

        DashManifest notDash = DashManifest.parse("<html>hi</html>", url("/x.mpd"));
        check("HTML is not mistaken for a manifest", !notDash.ok);
        check("the parse failure has a reason", notDash.error != null);
    }

    // ----------------------------------------------------------------- utils
    private static boolean contains(byte[] haystack, byte[] needle) {
        return indexOf(haystack, needle) >= 0;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) return -1;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static boolean equals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
