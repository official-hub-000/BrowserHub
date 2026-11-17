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
    private static final String ENDPOINT = "https://text.pollinations.ai/openai";
    /**
     * Free, key-less models tried in order. A single hardcoded model meant one
     * rate-limited model broke every question after it — the exact "answered
     * 'Who is Ronaldo' then failed 'Who is goat'" behaviour.
     */
    private static final String[] MODELS = {"openai", "mistral", "llama", "openai-fast"};
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

    private static String call(String userPrompt, String systemPrompt) {
        String lastError = "no model answered";
        long deadline = System.currentTimeMillis() + 20000L;
        for (String model : MODELS) {
            if (System.currentTimeMillis() >= deadline) break;
            String result = callOnce(model, userPrompt, systemPrompt);
            if (result.startsWith("{\"ok\":true")) return result;
            lastError = result;
        }
        return error(lastError);
    }

    private static String callOnce(String model, String userPrompt, String systemPrompt) {
        HttpURLConnection conn = null;
        try {
            JSONObject payload = new JSONObject();
            payload.put("model", model);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt == null || systemPrompt.isEmpty()
                            ? "You are BrowserHub AI. Reply in clean Markdown. Be concise and accurate."
                            : systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
            payload.put("messages", messages);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(12000);
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
