package com.browserhub.app.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The direct route: YouTube's own player API.
 *
 * This is the path an app like SnapTube takes, and it is the shortest one that
 * exists. Instead of loading a web page, watching what its player requests, and
 * unscrambling signatures, this asks the player service itself — the same service
 * the official Android app talks to — for the stream list of one video.
 *
 *   POST https://www.youtube.com/youtubei/v1/player?key=…
 *   {
 *     "context": { "client": { "clientName": "ANDROID", "clientVersion": "…", … } },
 *     "videoId": "…"
 *   }
 *
 * The reply is the canonical player response. The reason this route is better
 * than scraping is not cleverness, it is arithmetic: the server does the signing.
 * A native client has no JavaScript engine, so the service cannot hand it a
 * scrambled signature — the URLs in this response arrive already signed and are
 * ready to fetch. No signature emulation, no page hooks, no guessing.
 *
 * Why a fallback chain rather than one client: not every client is offered every
 * stream. A client that returns only separate video and audio tracks is still
 * useful (the two are joined), but a client that answers with a bot check is not.
 * Trying the next client in the chain after a refusal is what makes this reliable
 * across videos rather than across the videos someone happened to test.
 *
 * Honest scope, because it matters: this resolves public videos. A rental, a
 * members-only video, a private video or an age check that requires sign-in is
 * reported with YouTube's own reason — no client identity changes that, because
 * the refusal comes from the account, not the endpoint.
 */
public final class YouTubePlayerApi {

    /** Where the player endpoint lives. */
    public static final String ENDPOINT = "https://www.youtube.com/youtubei/v1/player";

    /**
     * The client identities used, in order.
     *
     * These are the public client identifiers the YouTube apps themselves send.
     * ANDROID_VR is first because it is the one that reliably returns combined
     * picture-and-sound streams for ordinary videos, which saves a merge step; iOS
     * follows because it tends to answer without a signature requirement either.
     */
    public static final Client[] CLIENTS = new Client[] {
            new Client("ANDROID_VR", "1.60.19",
                    "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; "
                            + "en_US; Quest 3 Build/SQ3A.220605.009.A1) gzip",
                    "28", "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"),
            new Client("IOS", "19.45.4",
                    "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                    "5", "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"),
            new Client("ANDROID", "19.44.38",
                    "com.google.android.youtube/19.44.38 (Linux; U; Android 14; en_US; "
                            + "Pixel 8 Pro Build/AP1A.240505.004) gzip",
                    "3", "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"),
    };

    /** One client identity: who we claim the request is from. */
    public static final class Client {
        public final String name;
        public final String version;
        public final String userAgent;
        /** Client id number, sent in the context. */
        public final String id;
        public final String key;

        Client(String name, String version, String userAgent, String id, String key) {
            this.name = name;
            this.version = version;
            this.userAgent = userAgent;
            this.id = id;
            this.key = key;
        }
    }

    private YouTubePlayerApi() {}

    /**
     * Resolves a video through the player API.
     *
     * Tries each client in turn and returns the first response that carries a
     * usable stream. The result is the same type the page parser produces, so the
     * rest of the download path does not care which route answered.
     *
     * @param videoId an 11-character video id
     */
    public static YouTubeExtractor.Result resolve(String videoId, String errorPrefix) {
        YouTubeExtractor.Result result = new YouTubeExtractor.Result();
        result.videoId = videoId;
        if (videoId == null || videoId.length() < 5) {
            result.error = "that does not look like a video address";
            return result;
        }

        List<String> failures = new ArrayList<>();
        for (Client client : CLIENTS) {
            try {
                YouTubeExtractor.Result attempt = attempt(client, videoId);
                if (attempt.ok) {
                    return attempt;
                }
                // Keep the most specific refusal, since that is what the user needs
                // to read: "This video is private" beats "no stream".
                if (attempt.error != null && attempt.error.length() > 0) {
                    failures.add(attempt.error);
                }
            } catch (IOException e) {
                failures.add(friendly(e));
            } catch (Throwable t) {
                failures.add("the player service could not be reached");
            }
        }

        result.error = pickReason(failures);
        return result;
    }

    /** One call with one client identity. */
    static YouTubeExtractor.Result attempt(Client client, String videoId) throws IOException {
        String body = requestBody(client, videoId);
        Map<String, String> headers = headers(client);

        String url = ENDPOINT + "?key=" + client.key + "&prettyPrint=false";
        String reply = MediaMerger.postJson(url, body.getBytes("UTF-8"), headers);

        YouTubeExtractor.Result result = YouTubeExtractor.parsePlayerResponse(reply);
        if (result.videoId == null || result.videoId.length() == 0) {
            result.videoId = videoId;
        }
        return result;
    }

    /**
     * The request body.
     *
     * Built by hand rather than with a JSON library so the exact bytes sent to
     * YouTube are visible here, which is the only way to debug a refusal that
     * depends on them.
     */
    public static String requestBody(Client client, String videoId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"context\":{\"client\":{");
        sb.append("\"clientName\":\"").append(client.name).append("\",");
        sb.append("\"clientVersion\":\"").append(escape(client.version)).append("\",");
        sb.append("\"clientId\":\"").append(client.id).append("\",");
        sb.append("\"hl\":\"en\",\"gl\":\"US\",");
        // The Android and iOS clients require their platform reported, and VR also
        // needs the device/OS fields or it answers as an unknown client.
        sb.append("\"userAgent\":\"").append(escape(client.userAgent)).append("\"");
        if (!"IOS".equals(client.name)) {
            sb.append(",\"androidSdkVersion\":34,\"osName\":\"Android\",\"osVersion\":\"14\"");
        } else {
            sb.append(",\"deviceMake\":\"Apple\",\"deviceModel\":\"iPhone16,2\",")
              .append("\"osName\":\"iPhone\",\"osVersion\":\"18.1.0.22B83\"");
        }
        sb.append("},\"user\":{\"lockedSafetyMode\":false},");
        sb.append("\"request\":{\"useSsl\":true}}");
        sb.append(",\"videoId\":\"").append(escape(videoId)).append("\"");
        // contentCheckOk/racyCheckOk are what stop an age gate being applied to a
        // request that carries no signed-in account.
        sb.append(",\"contentCheckOk\":true,\"racyCheckOk\":true}");
        return sb.toString();
    }

    /** The headers that make the request look like the client it claims to be. */
    public static Map<String, String> headers(Client client) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", client.userAgent);
        h.put("X-YouTube-Client-Name", client.id);
        h.put("X-YouTube-Client-Version", client.version);
        h.put("Origin", "https://www.youtube.com");
        h.put("Accept-Language", "en-US,en;q=0.9");
        h.put("Content-Type", "application/json");
        return h;
    }

    /** Chooses the most useful message out of several failed attempts. */
    public static String pickReason(List<String> failures) {
        if (failures == null || failures.isEmpty()) {
            return "YouTube did not return this video. It may be private, removed or region locked.";
        }
        // A specific refusal is more useful than a generic one, and the same strings
        // come back from every client when the refusal is about the video itself.
        for (String f : failures) {
            String lower = f.toLowerCase(Locale.US);
            if (lower.contains("private") || lower.contains("removed")
                    || lower.contains("not available") || lower.contains("country")
                    || lower.contains("sign in") || lower.contains("members")
                    || lower.contains("age")) {
                return f;
            }
        }
        return failures.get(failures.size() - 1);
    }

    /** Turns a transport error into something worth reading. */
    public static String friendly(IOException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("HTTP 403")) {
            return "YouTube refused the request (403). Retrying usually works.";
        }
        if (message.contains("HTTP 429")) {
            return "YouTube is rate limiting this device. Wait a minute and try again.";
        }
        return "could not reach YouTube: " + message;
    }

    /** Answers whether the direct route is worth trying for a URL shape. */
    public static boolean handles(String url) {
        return YouTubeExtractor.handles(url);
    }

    static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Whether a result has a stream that needs no further work.
     *
     * A combined file is the goal; separate tracks are acceptable but cost a merge
     * and can fail on a device with little free space.
     */
    public static boolean hasCombined(YouTubeExtractor.Result result) {
        return result != null && result.progressive != null;
    }
}
