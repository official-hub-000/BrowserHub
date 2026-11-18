import com.browserhub.app.media.Aes128Cbc;
import com.browserhub.app.media.HlsPlaylist;
import com.browserhub.app.media.MediaMerger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Random;

/**
 * End-to-end proof that an HLS stream becomes one correct file.
 *
 * A real local HTTP server serves a master playlist, a media playlist and its
 * segments — some encrypted with AES-128 — and the test then asserts the merged
 * output is byte-for-byte the concatenation of the source segments. That is the
 * only way to prove a merger works: parsing a manifest correctly still tells you
 * nothing about whether the bytes land in the right order, decrypted, once.
 *
 * Needs no Android device and no network.
 */
public final class MediaMergeTest {

    private static int passed = 0;
    private static final java.util.List<String> failures = new java.util.ArrayList<>();
    private static com.sun.net.httpserver.HttpServer server;
    private static int port;

    public static void main(String[] args) throws Exception {
        startServer();
        try {
            testPlainMerge();
            testEncryptedMerge();
            testMasterResolution();
            testResume();
            testByteRangeSegments();
            testHonestFailures();
        } finally {
            if (server != null) server.stop(0);
        }

        System.out.println();
        if (!failures.isEmpty()) {
            System.err.println("media merge: " + failures.size() + " failure(s)");
            for (String f : failures) System.err.println("  - " + f);
            System.exit(1);
        }
        System.out.println("media merge: " + passed + " checks pass — streams join into correct files");
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
            byte[] body = Routes.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            int status = 200;
            java.util.Map<String, byte[]> routes = Routes.byName;
            // Honour a Range request the way a CDN would.
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (range != null && range.startsWith("bytes=")) {
                String spec = range.substring("bytes=".length());
                long start = 0;
                long end;
                if (spec.contains("-")) {
                    String[] parts = spec.split("-");
                    start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
                    end = parts.length > 1 && !parts[1].isEmpty()
                            ? Long.parseLong(parts[1])
                            : body.length - 1;
                } else {
                    end = Long.parseLong(spec) - 1;
                }
                int len = (int) Math.min(end - start + 1, body.length - start);
                byte[] slice = new byte[len];
                System.arraycopy(body, (int) start, slice, 0, len);
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

    /** Content served by the fake CDN. */
    private static final class Routes {
        static final java.util.Map<String, byte[]> byName = new java.util.HashMap<>();

        static byte[] get(String path) {
            return byName.get(path);
        }

        static void put(String path, byte[] body) {
            byName.put(path, body);
        }

        static void put(String path, String body) {
            byName.put(path, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static File tempOut(String name) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "bh-merge-test-" + System.nanoTime());
        dir.mkdirs();
        return new File(dir, name);
    }

    private static byte[] randomBytes(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static boolean sameBytes(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    // ------------------------------------------------------------ plain HLS
    private static void testPlainMerge() throws Exception {
        byte[] s0 = randomBytes(4000, 11);
        byte[] s1 = randomBytes(4000, 12);
        byte[] s2 = randomBytes(2000, 13);
        Routes.put("/plain/seg0.ts", s0);
        Routes.put("/plain/seg1.ts", s1);
        Routes.put("/plain/seg2.ts", s2);
        Routes.put("/plain/index.m3u8",
                "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXTINF:4.0,\nseg0.ts\n"
                + "#EXTINF:4.0,\nseg1.ts\n"
                + "#EXTINF:2.0,\nseg2.ts\n"
                + "#EXT-X-ENDLIST\n");

        File out = tempOut("plain.ts");
        MediaMerger.Result r = MediaMerger.merge(url("/plain/index.m3u8"), out, 0, "test-agent", null, null);

        check("plain merge reports success", r.ok);
        check("plain merge counts every segment", r.segments == 3);
        check("plain merge wrote a file", r.file != null && r.file.exists());
        check("plain merged bytes equal the sum of segments", r.bytes == s0.length + s1.length + s2.length);

        byte[] written = java.nio.file.Files.readAllBytes(out.toPath());
        byte[] expected = new byte[s0.length + s1.length + s2.length];
        System.arraycopy(s0, 0, expected, 0, s0.length);
        System.arraycopy(s1, 0, expected, s0.length, s1.length);
        System.arraycopy(s2, 0, expected, s0.length + s1.length, s2.length);
        check("plain merged output is byte-identical and in order", sameBytes(expected, written));
        check("no .part file is left behind",
                !new File(out.getParentFile(), out.getName() + ".part").exists());
    }

    // -------------------------------------------------------- encrypted HLS
    private static void testEncryptedMerge() throws Exception {
        byte[] key = new byte[16];
        for (int i = 0; i < 16; i++) key[i] = (byte) (i * 7 + 1);
        Routes.put("/enc/key.bin", key);

        byte[] p0 = randomBytes(1600, 21); // exactly 100 blocks, no padding needed
        byte[] p1 = randomBytes(1600, 22);
        Routes.put("/enc/seg0.ts", encrypt(p0, key, Aes128Cbc.sequenceIv(0)));
        Routes.put("/enc/seg1.ts", encrypt(p1, key, Aes128Cbc.sequenceIv(1)));
        Routes.put("/enc/index.m3u8",
                "#EXTM3U\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n"
                + "#EXTINF:4.0,\nseg0.ts\n"
                + "#EXTINF:4.0,\nseg1.ts\n"
                + "#EXT-X-ENDLIST\n");

        File out = tempOut("enc.ts");
        MediaMerger.Result r = MediaMerger.merge(url("/enc/index.m3u8"), out, 0, "test-agent", null, null);

        check("encrypted merge succeeds", r.ok);
        byte[] written = java.nio.file.Files.readAllBytes(out.toPath());
        byte[] expected = new byte[p0.length + p1.length];
        System.arraycopy(p0, 0, expected, 0, p0.length);
        System.arraycopy(p1, 0, expected, p0.length, p1.length);
        check("AES-128 segments decrypt to the original bytes", sameBytes(expected, written));
        check("decrypted output is not the ciphertext", !sameBytes(Routes.get("/enc/seg0.ts"), written));
    }

    // ------------------------------------------------------- master playlist
    private static void testMasterResolution() throws Exception {
        byte[] low = randomBytes(500, 31);
        byte[] high = randomBytes(1500, 32);
        Routes.put("/m/360/seg0.ts", low);
        Routes.put("/m/1080/seg0.ts", high);
        Routes.put("/m/360/index.m3u8", "#EXTM3U\n#EXTINF:4.0,\nseg0.ts\n#EXT-X-ENDLIST\n");
        Routes.put("/m/1080/index.m3u8", "#EXTM3U\n#EXTINF:4.0,\nseg0.ts\n#EXT-X-ENDLIST\n");
        Routes.put("/m/master.m3u8",
                "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=640x360\n360/index.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080\n1080/index.m3u8\n");

        File out = tempOut("master.ts");
        MediaMerger.Result r = MediaMerger.merge(url("/m/master.m3u8"), out, 0, "ua", null, null);
        check("master playlist resolves to a variant", r.ok);
        check("master picks the highest quality, not the first",
                r.ok && r.bytes == high.length);
    }

    // --------------------------------------------------------------- resume
    private static void testResume() throws Exception {
        byte[] s0 = randomBytes(3000, 41);
        Routes.put("/res/seg0.ts", s0);
        Routes.put("/res/seg1.ts", randomBytes(3000, 42));
        Routes.put("/res/index.m3u8",
                "#EXTM3U\n#EXTINF:4.0,\nseg0.ts\n#EXTINF:4.0,\nseg1.ts\n#EXT-X-ENDLIST\n");

        File out = tempOut("res.ts");
        // Simulate an interrupted run: the first segment is on disk and the
        // journal says exactly one segment completed.
        File part = new File(out.getParentFile(), out.getName() + ".part");
        try (FileOutputStream fos = new FileOutputStream(part)) {
            fos.write(s0);
        }
        try (FileOutputStream fos = new FileOutputStream(
                new File(out.getParentFile(), out.getName() + ".segments"))) {
            fos.write("1".getBytes("US-ASCII"));
        }
        long before = part.length();
        check("staged part file exists before resuming", before == s0.length);

        // A full run must not corrupt or duplicate the already-written prefix.
        MediaMerger.Result r = MediaMerger.merge(url("/res/index.m3u8"), out, 0, "ua", null, null);
        check("resumed merge completes", r.ok);
        byte[] written = java.nio.file.Files.readAllBytes(out.toPath());
        byte[] prefix = new byte[Math.min(s0.length, written.length)];
        System.arraycopy(written, 0, prefix, 0, prefix.length);
        check("resume keeps the already-downloaded prefix intact", sameBytes(s0, prefix));
        check("resume does not duplicate the first segment",
                written.length >= s0.length && !isPrefixRepeated(written, s0));
    }

    private static boolean isPrefixRepeated(byte[] all, byte[] seg) {
        if (all.length < seg.length * 2) return false;
        byte[] second = new byte[seg.length];
        System.arraycopy(all, seg.length, second, 0, seg.length);
        return MessageDigest.isEqual(seg, second);
    }

    // -------------------------------------------------- byte-range segments
    private static void testByteRangeSegments() throws Exception {
        byte[] a = randomBytes(1000, 51);
        byte[] b = randomBytes(1200, 52);
        byte[] all = new byte[2200];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        Routes.put("/br/all.ts", all);
        Routes.put("/br/index.m3u8",
                "#EXTM3U\n"
                + "#EXTINF:4.0,\n#EXT-X-BYTERANGE:1000@0\nall.ts\n"
                + "#EXTINF:4.0,\n#EXT-X-BYTERANGE:1200@1000\nall.ts\n"
                + "#EXT-X-ENDLIST\n");

        File out = tempOut("br.ts");
        MediaMerger.Result r = MediaMerger.merge(url("/br/index.m3u8"), out, 0, "ua", null, null);
        check("byte-range merge succeeds", r.ok);
        byte[] written = java.nio.file.Files.readAllBytes(out.toPath());
        check("byte-range segments are fetched exactly", sameBytes(all, written));
    }

    // ----------------------------------------------------- honest failures
    private static void testHonestFailures() throws Exception {
        // A page that is not a playlist must be refused, not saved as a video.
        Routes.put("/bad/page.m3u8", "<html>please enable javascript</html>");
        File out = tempOut("bad.ts");
        MediaMerger.Result r = MediaMerger.merge(url("/bad/page.m3u8"), out, 0, "ua", null, null);
        check("an HTML page is refused", !r.ok);
        check("the refusal explains itself", r.error != null && r.error.length() > 0);
        check("no file is produced from a refused stream", !out.exists());

        // A missing segment must fail rather than silently skip.
        Routes.put("/gap/index.m3u8", "#EXTM3U\n#EXTINF:4.0,\nnope.ts\n#EXT-X-ENDLIST\n");
        MediaMerger.Result gap = MediaMerger.merge(url("/gap/index.m3u8"), tempOut("gap.ts"), 0, "ua", null, null);
        check("a missing segment fails the job instead of emitting a short file", !gap.ok);

        // SAMPLE-AES must be named, never attempted.
        byte[] seg = randomBytes(1600, 61);
        Routes.put("/samp/seg0.ts", seg);
        Routes.put("/samp/index.m3u8",
                "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"k\"\n#EXTINF:4.0,\nseg0.ts\n#EXT-X-ENDLIST\n");
        MediaMerger.Result samp = MediaMerger.merge(url("/samp/index.m3u8"), tempOut("samp.ts"), 0, "ua", null, null);
        check("SAMPLE-AES is refused with a reason", !samp.ok && samp.error.toLowerCase().contains("sample-aes"));

        // Cancellation must stop and leave no finished file.
        File cancelOut = tempOut("cancel.ts");
        MediaMerger.Result cancel = MediaMerger.merge(
                url("/plain/index.m3u8"), cancelOut, 0, "ua", null,
                new MediaMerger.Listener() {
                    public void onProgress(long d, long t, int a, int b) {}
                    public void onStage(String m) {}
                    public boolean shouldCancel() { return true; }
                });
        check("cancellation aborts the job", !cancel.ok);
        check("a cancelled job produces no finished file", !cancelOut.exists());
    }

    // ------------------------------------------------------------------ util
    private static byte[] encrypt(byte[] plain, byte[] key, byte[] iv) throws Exception {
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(key, "AES"),
                new javax.crypto.spec.IvParameterSpec(iv));
        return c.doFinal(plain);
    }
}
