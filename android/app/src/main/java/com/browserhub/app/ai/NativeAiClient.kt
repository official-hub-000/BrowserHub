package com.browserhub.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Zero-key LLM client for the Android shell.
 *
 * The request runs on a native worker thread and the WebView polls for the result,
 * so the JavaScript thread never blocks (a blocking @JavascriptInterface call would
 * freeze the whole UI for the duration of the network round-trip).
 *
 * Envelopes are JSON strings: {"ok":true,"text":"…"} or {"ok":false,"error":"…"}.
 */
object NativeAiClient {
    private const val ENDPOINT = "https://text.pollinations.ai/openai"

    /**
     * Free, key-less models tried in order. One hardcoded model meant a single
     * rate-limited model broke every question after it — the exact
     * "answered 'Who is Ronaldo', then failed 'Who is goat'" behaviour.
     */
    private val MODELS = listOf("openai", "mistral", "llama", "openai-fast")
    private const val FALLBACK_SYSTEM =
        "You are BrowserHub AI. Reply in clean Markdown. Be concise, accurate and friendly."

    private val results = ConcurrentHashMap<String, String>()

    fun start(requestId: String?, userPrompt: String?, systemPrompt: String?) {
        if (requestId.isNullOrEmpty()) return
        if (userPrompt.isNullOrBlank()) {
            results[requestId] = error("empty prompt")
            return
        }
        Thread({
            results[requestId] = call(userPrompt, systemPrompt)
        }, "BrowserHub-Ai").apply { isDaemon = true }.start()
    }

    /** Returns the JSON envelope once ready, otherwise null while still pending. */
    fun poll(requestId: String?): String? {
        if (requestId.isNullOrEmpty()) return null
        return results.remove(requestId)
    }

    private fun call(userPrompt: String, systemPrompt: String?): String {
        val deadline = System.currentTimeMillis() + 20_000L
        var lastError = "no model answered"
        for (model in MODELS) {
            if (System.currentTimeMillis() >= deadline) break
            val result = callOnce(model, userPrompt, systemPrompt)
            if (result.startsWith("{\"ok\":true")) return result
            lastError = result
        }
        return error(lastError)
    }

    private fun callOnce(model: String, userPrompt: String, systemPrompt: String?): String {
        var conn: HttpURLConnection? = null
        return try {
            val payload = JSONObject().put("model", model)
            val messages = JSONArray()
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", if (systemPrompt.isNullOrBlank()) FALLBACK_SYSTEM else systemPrompt)
            )
            messages.put(JSONObject().put("role", "user").put("content", userPrompt))
            payload.put("messages", messages)

            val body = payload.toString().toByteArray(StandardCharsets.UTF_8)
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(body) }

            val status = conn.responseCode
            if (status !in 200..299) {
                error("HTTP $status")
            } else {
                val raw = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
                val choices = JSONObject(raw).optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    error("no choices")
                } else {
                    val message = choices.getJSONObject(0).optJSONObject("message")
                    val text = message?.optString("content", "") ?: ""
                    if (text.isBlank()) error("empty reply")
                    else JSONObject().put("ok", true).put("text", text.trim()).toString()
                }
            }
        } catch (t: Throwable) {
            error(t.message ?: t.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    private fun error(message: String): String =
        try {
            JSONObject().put("ok", false).put("error", message).toString()
        } catch (t: Throwable) {
            "{\"ok\":false,\"error\":\"unknown\"}"
        }
}
