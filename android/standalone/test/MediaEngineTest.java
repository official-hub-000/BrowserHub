import com.browserhub.app.media.HlsPlaylist;
import com.browserhub.app.media.Resolver;

/**
 * Runs the media engine against real-shaped playlists.
 *
 * These are executed, not merely compiled: the ECJ path in verify-release.sh
 * builds this file and the JVM runs it, so a parser regression fails the release
 * instead of shipping. HLS parsing is arithmetic on someone else's manifest, and
 * one wrong offset means every segment 404s — a failure that looks like "the
 * site blocks downloads" when the bug is local.
 */
public final class MediaEngineTest {

    private static int passed = 0;
    private static final java.util.List<String> failures = new java.util.ArrayList<>();

    public static void main(String[] args) {
        testResolver();
        testMasterPlaylist();
        testMediaPlaylist();
        testByteRanges();
        testEncryption();
        testReporterHonesty();

        System.out.println();
        if (!failures.isEmpty()) {
            System.err.println("media engine: " + failures.size() + " failure(s)");
            for (String f : failures) System.err.println("  - " + f);
            System.exit(1);
        }
        System.out.println("media engine: " + passed + " checks pass");
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

    // ------------------------------------------------------------------ paths
    private static void testResolver() {
        String base = "https://cdn.example.com/hls/1080/index.m3u8?token=abc";
        eq("relative segment", "https://cdn.example.com/hls/1080/seg1.ts",
                Resolver.resolve(base, "seg1.ts"));
        eq("root-relative", "https://cdn.example.com/other/seg.ts",
                Resolver.resolve(base, "/other/seg.ts"));
        eq("protocol-relative", "https://other.cdn.net/seg.ts",
                Resolver.resolve(base, "//other.cdn.net/seg.ts"));
        eq("parent traversal", "https://cdn.example.com/hls/seg.ts",
                Resolver.resolve(base, "../seg.ts"));
        eq("two-level traversal", "https://cdn.example.com/seg.ts",
                Resolver.resolve(base, "../../seg.ts"));
        eq("absolute untouched", "https://x.com/a.ts",
                Resolver.resolve(base, "https://x.com/a.ts"));
        eq("query-only keeps path", "https://cdn.example.com/hls/1080/index.m3u8?b=1",
                Resolver.resolve(base, "?b=1"));
        eq("no path base", "https://cdn.example.com/seg.ts",
                Resolver.resolve("https://cdn.example.com", "seg.ts"));
        eq("duplicate slashes collapsed", "https://cdn.example.com/a/b.ts",
                Resolver.resolve(base, "/a//b.ts"));
        eq("dot segments removed", "https://cdn.example.com/a/b.ts",
                Resolver.resolve(base, "/a/./b.ts"));

        eq("nameOf", "index.m3u8", Resolver.nameOf(base));
        check("isPlaylist", Resolver.isPlaylist(base));
        check("not a playlist", !Resolver.isPlaylist("https://c/x.ts"));
    }

    // ---------------------------------------------------------------- master
    private static void testMasterPlaylist() {
        String master =
                "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS=\"avc1.4d401e,mp4a.40.2\"\n"
                + "low/index.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080\n"
                + "high/index.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720\n"
                + "mid/index.m3u8\n";

        HlsPlaylist p = HlsPlaylist.parse(master, "https://cdn.example.com/hls/master.m3u8");
        check("master detected", p.isMaster);
        check("three variants", p.variants.size() == 3);
        eq("relative variants resolved", "https://cdn.example.com/hls/high/index.m3u8",
                p.variants.get(1).uri);
        eq("best variant is 1080p", "https://cdn.example.com/hls/high/index.m3u8",
                p.bestVariant().uri);
        eq("capped variant picks 720p", "https://cdn.example.com/hls/mid/index.m3u8",
                p.variantAtMost(720).uri);
        eq("cap above everything still gives best", "https://cdn.example.com/hls/high/index.m3u8",
                p.variantAtMost(2160).uri);
        eq("codecs parsed", "avc1.4d401e,mp4a.40.2", p.variants.get(0).codecs);
        check("no error on a valid master", p.error == null);
    }

    // ----------------------------------------------------------------- media
    private static void testMediaPlaylist() {
        String media =
                "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-TARGETDURATION:10\n"
                + "#EXTINF:9.009,\n"
                + "seg0.ts\n"
                + "#EXTINF:9.009,\n"
                + "seg1.ts\n"
                + "#EXTINF:3.003,\n"
                + "seg2.ts\n"
                + "#EXT-X-ENDLIST\n";

        HlsPlaylist p = HlsPlaylist.parse(media, "https://cdn.example.com/v/index.m3u8");
        check("not a master", !p.isMaster);
        check("three segments", p.segments.size() == 3);
        eq("first segment resolved", "https://cdn.example.com/v/seg0.ts", p.segments.get(0).uri);
        check("durations parsed", Math.abs(p.segments.get(0).duration - 9.009) < 0.0001);
        check("total duration summed", Math.abs(p.totalDuration - 21.021) < 0.01);
        check("endlist seen", p.ended);
        check("not encrypted", !p.encrypted);
        check("no error", p.error == null);
    }

    // ------------------------------------------------------------ byte ranges
    private static void testByteRanges() {
        // A single-file stream addressed by byte range — this is how a lot of
        // CM 3.0 content is served, and it is the case naive merge code breaks.
        String ranged =
                "#EXTM3U\n"
                + "#EXT-X-MAP:URI=\"init.mp4\"\n"
                + "#EXTINF:4.0,\n"
                + "#EXT-X-BYTERANGE:1000@0\n"
                + "all.mp4\n"
                + "#EXTINF:4.0,\n"
                + "#EXT-X-BYTERANGE:1200\n"
                + "all.mp4\n";

        HlsPlaylist p = HlsPlaylist.parse(ranged, "https://cdn.example.com/r/index.m3u8");
        check("two ranged segments", p.segments.size() == 2);
        eq("init map resolved", "https://cdn.example.com/r/init.mp4", p.mapUri);
        check("first range offset 0", p.segments.get(0).rangeStart == 0);
        check("first range length 1000", p.segments.get(0).rangeLength == 1000);
        check("second range implicit offset", p.segments.get(1).rangeStart == -1);
        check("second range length 1200", p.segments.get(1).rangeLength == 1200);
        check("map range carried", p.segments.get(0).mapStart == -1);
    }

    // -------------------------------------------------------------- encrypted
    private static void testEncryption() {
        String aes =
                "#EXTM3U\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x0123456789abcdef0123456789abcdef\n"
                + "#EXTINF:4.0,\n"
                + "seg0.ts\n";

        HlsPlaylist p = HlsPlaylist.parse(aes, "https://cdn.example.com/e/index.m3u8");
        check("encryption detected", p.encrypted);
        eq("key resolved relative", "https://cdn.example.com/e/key.bin", p.keyUri);
        eq("method parsed", "AES-128", p.keyMethod);
        eq("iv parsed", "0x0123456789abcdef0123456789abcdef", p.keyIv);
        check("not sample-aes", !p.sampleAes);

        // SAMPLE-AES cannot be decrypted by an app: it must be reported.
        String sampleAes =
                "#EXTM3U\n"
                + "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"\n"
                + "#EXTINF:4.0,\n"
                + "seg0.ts\n";
        HlsPlaylist s = HlsPlaylist.parse(sampleAes, "https://cdn.example.com/e/index.m3u8");
        check("sample-aes flagged", s.sampleAes);
        check("sample-aes still encrypted", s.encrypted);
    }

    // ------------------------------------------------------- honest reporting
    private static void testReporterHonesty() {
        HlsPlaylist garbage = HlsPlaylist.parse("this is not a playlist", "https://c/x.m3u8");
        check("non-playlist reports an error rather than pretending", garbage.error != null);

        HlsPlaylist empty = HlsPlaylist.parse("#EXTM3U\n", "https://c/x.m3u8");
        check("empty playlist reports no segments", empty.error != null);

        HlsPlaylist nullParse = HlsPlaylist.parse(null, "https://c/x.m3u8");
        check("null input is safe", nullParse.error != null);

        HlsPlaylist masterNoVariants = HlsPlaylist.parse(
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n", "https://c/x.m3u8");
        check("master without a URI reports an error", masterNoVariants.error != null);

        check("looksLikeHls accepts a real manifest",
                HlsPlaylist.looksLikeHls("#EXTM3U\n#EXTINF:1,\na.ts"));
        check("looksLikeHls rejects html",
                !HlsPlaylist.looksLikeHls("<html><body>403</body></html>"));
    }
}
