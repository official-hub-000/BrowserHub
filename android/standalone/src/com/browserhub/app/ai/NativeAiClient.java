package com.browserhub.app.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zero-key LLM client for the Android shell.
 *
 * The request runs on a native worker thread; the WebView polls for the result so
 * the JS thread is never blocked (a blocking @JavascriptInterface call would freeze
 * the whole UI for the length of the network round-trip).
 *
 * Result envelopes are JSON strings: {"ok":true,"text":"…"} / {"ok":false,"error":"…"}.
 */
public final class NativeAiClient {
    /**
     * Free, key-less models tried in order. A single hardcoded model meant one
     * rate-limited model broke every question after it — the exact "answered
     * 'Who is Ronaldo' then failed 'Who is goat'" behaviour.
     */
    /**
     * Model aliases for the Pollinations gateway. DeepSeek first: it is the
     * strongest reasoner reachable without a key, and the canonical id is the
     * one from Pollinations' own APIDOCS.md.
     */
    private static final String[] MODELS = {"openai", "mistral", "llama", "openai-fast"};

    /** The model that answers alongside each provider, when it needs one. */
    private static final String DEEPSEEK_MODEL = "deepseek/deepseek-v4-flash";

    /**
     * Providers that send exactly one model of their own choosing, so the
     * shared MODELS list must not be looped over for them.
     */
    private static final java.util.Set<String> PINNED_PROVIDERS =
            new java.util.HashSet<String>(java.util.Arrays.asList("deepseek", "deepinfra"));

    /** The single attempt a pinned provider gets. */
    private static final String[] ONLY_ITS_OWN_MODEL = {""};
    private static final ConcurrentHashMap<String, String> RESULTS = new ConcurrentHashMap<String, String>();

    private NativeAiClient() {}

    public static void start(final String requestId, final String userPrompt, final String systemPrompt) {
        if (requestId == null) return;
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            RESULTS.put(requestId, error("empty prompt"));
            return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                RESULTS.put(requestId, call(userPrompt, systemPrompt));
            }
        }, "BrowserHub-Ai");
        worker.setDaemon(true);
        worker.start();
    }

    /** Returns the JSON envelope once ready, otherwise null while still pending. */
    public static String poll(String requestId) {
        if (requestId == null) return null;
        String value = RESULTS.get(requestId);
        if (value != null) RESULTS.remove(requestId);
        return value;
    }

    /**
     * Independent key-less chat services. Rotating model names on ONE gateway is
     * still one quota — the "always the same apology" bug. Each endpoint below
     * has its own limits, so when one is saturated another can still answer.
     */
    private static final String[][] PROVIDERS = {
            // DeepSeek through the keyless gateway. Its own api.deepseek.com
            // needs a paid key, which would break the zero-key promise.
            {"https://text.pollinations.ai/openai", "deepseek"},
            {"https://text.pollinations.ai/openai", "pollinations"},
            {"https://api.deepinfra.com/v1/openai/chat/completions", "deepinfra"},
            {"https://ai.hackclub.com/chat/completions", "hackclub"},
    };

    /**
     * Total wall-clock budget. The JS layer polls for 16 s and gives up at 18 s,
     * so this must stay under that: work that outlives the waiter is wasted, and
     * the user is told "no model answered" while one was still trying.
     */
    private static final long TOTAL_BUDGET_MS = 15000L;

    private static String call(String userPrompt, String systemPrompt) {
        long deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS;
        deadlineAt = deadline;
        StringBuilder why = new StringBuilder();
        int tried = 0;
        for (int p = 0; p < PROVIDERS.length; p++) {
            if (System.currentTimeMillis() >= deadline) break;
            /*
             * Each provider may spend at most a fair share of what is left, so
             * one that hangs cannot consume the budget that the later, working
             * ones needed. Reserving `left / providersRemaining` is what makes
             * "provider 1 and 2 are down" survivable instead of fatal.
             */
            int providersLeft = PROVIDERS.length - p;
            long left = deadline - System.currentTimeMillis();
            providerBudgetAt = Math.max(1500L, left / providersLeft);
            tried++;
            String[] provider = PROVIDERS[p];
            String result = callProvider(provider[0], provider[1], userPrompt, systemPrompt);
            if (result.startsWith("{\"ok\":true")) return result;
            if (why.length() > 0) why.append("; ");
            why.append(provider[1]).append(": ").append(shortReason(result));
        }
        /*
         * Say what actually happened. "No model answered" when three were tried
         * and each returned 429 is a different problem from a dead network, and
         * the user's next action differs.
         */
        return error(tried == 0
                ? "ran out of time before any provider could be tried"
                : why.toString());
    }

    /** Turns an error envelope into the short phrase that matters to a person. */
    private static String shortReason(String errorEnvelope) {
        if (errorEnvelope == null) return "no reply";
        if (errorEnvelope.contains("HTTP 429")) return "rate limited";
        if (errorEnvelope.contains("HTTP 5")) return "server error";
        if (errorEnvelope.contains("HTTP 4")) return "rejected";
        if (errorEnvelope.contains("timed out") || errorEnvelope.contains("SocketTimeout"))
            return "timed out";
        if (errorEnvelope.contains("empty") || errorEnvelope.contains("no choices"))
            return "empty reply";
        return "unreachable";
    }

    private static long deadlineAt = Long.MAX_VALUE;
    /** Ceiling for the provider currently being tried. */
    private static long providerBudgetAt = Long.MAX_VALUE;

    private static String callProvider(
            String endpoint, String providerId, String userPrompt, String systemPrompt) {
        String lastError = "no model answered";
        /*
         * A provider that pins one concrete model must be tried exactly once.
         * The old code looped over MODELS regardless, and because callOnce
         * ignores the loop variable for a pinned provider, that meant the same
         * request was re-sent up to five times — 18 s each, inside a 22 s
         * budget. The JS layer only waits 9 s, so every question on device
         * timed out before a single other provider was ever reached. That is
         * why all models appeared broken at once, not just one.
         */
        String[] models = PINNED_PROVIDERS.contains(providerId) ? ONLY_ITS_OWN_MODEL : MODELS;
        for (int i = 0; i < models.length; i++) {
            String model = models[i];
            if (Thread.currentThread().isInterrupted()) break;
            // Whatever is left, divided between this attempt and the ones after
            // it in this provider, so one slow model cannot starve the rest.
            long providerEnd = Math.min(deadlineAt,
                    System.currentTimeMillis() + (providerBudgetAt == Long.MAX_VALUE
                            ? deadlineAt - System.currentTimeMillis() : providerBudgetAt));
            long left = providerEnd - System.currentTimeMillis();
            if (left < 800) break;
            int share = (int) Math.max(900, left / (models.length - i));
            String result = callOnce(endpoint, providerId, model, userPrompt, systemPrompt, share);
            if (result.startsWith("{\"ok\":true")) return result;
            lastError = result;
            // A 429 or a dead host will not improve by trying another model name
            // on the same gateway — move to the next provider instead.
            if (result.contains("HTTP 429") || result.contains("HTTP 5")) break;
        }
        return lastError;
    }

    private static String callOnce(
            String endpoint, String providerId, String model, String userPrompt, String systemPrompt,
            int attemptBudgetMs) {
        HttpURLConnection conn = null;
        try {
            JSONObject payload = new JSONObject();
            // DeepInfra and Hack Club need a concrete model; Pollinations accepts
            // its own alias names.
            if ("deepseek".equals(providerId)) {
                payload.put("model", DEEPSEEK_MODEL);
                // DeepSeek rambles; a phone reply should stay readable.
                payload.put("max_tokens", 800);
            } else if ("pollinations".equals(providerId)) {
                payload.put("model", model);
            } else if ("deepinfra".equals(providerId)) {
                payload.put("model", "meta-llama/Meta-Llama-3.1-8B-Instruct");
                payload.put("max_tokens", 700);
            }

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt == null || systemPrompt.isEmpty()
                            ? "You are BrowserHub AI. Reply in clean Markdown. Be concise and accurate."
                            : systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
            payload.put("messages", messages);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            /*
             * Sized so several providers fit inside one JS wait. A connect that
             * has not completed in 3 s on mobile data is not going to; a read
             * that takes over 9 s is a stalled gateway, and waiting longer only
             * burns the budget that the next provider could have used.
             */
            /*
             * The attempt gets a slice of the remaining budget rather than a
             * fixed timeout. With a fixed 8 s, only two of four providers could
             * ever be reached inside a 15 s budget, so a dead first and second
             * provider meant the working third and fourth were never tried —
             * which is indistinguishable from "all models are broken".
             */
            int connectMs = Math.max(1200, Math.min(2500, attemptBudgetMs / 3));
            int readMs = Math.max(1500, attemptBudgetMs - connectMs);
            conn.setConnectTimeout(connectMs);
            conn.setReadTimeout(readMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return error("HTTP " + status);

            String raw = readAll(conn.getInputStream());
            JSONArray choices = new JSONObject(raw).optJSONArray("choices");
            if (choices == null || choices.length() == 0) return error("no choices");
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            String text = message != null ? message.optString("content", "") : "";
            if (text.trim().isEmpty()) return error("empty reply");
            return new JSONObject().put("ok", true).put("text", text.trim()).toString();
        } catch (Throwable t) {
            return error(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private static String error(String message) {
        try {
            return new JSONObject().put("ok", false).put("error", message).toString();
        } catch (Throwable t) {
            return "{\"ok\":false,\"error\":\"unknown\"}";
        }
    }
}
