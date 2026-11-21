package com.browserhub.app.browser

import com.browserhub.app.ai.BrowserAiEngine
import com.browserhub.app.data.model.ReaderFont
import com.browserhub.app.data.model.ReaderPaper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class TranslateLanguage(
    val code: String,
    val badgeCode: String,
    val nativeName: String,
    val badgeColorHex: Long
) {
    BENGALI("bn", "BN", "বাংলা", 0xFF059669),
    HINDI("hi", "HI", "हिन्दी", 0xFFD97706),
    SPANISH("es", "ES", "Español", 0xFFDC2626),
    FRENCH("fr", "FR", "Français", 0xFF2563EB),
    GERMAN("de", "DE", "Deutsch", 0xFF475569),
    JAPANESE("ja", "JA", "日本語", 0xFFDB2777),
    CHINESE("zh-CN", "ZH", "简体中文", 0xFFDC2626),
    ARABIC("ar", "AR", "العربية", 0xFF15803D),
    RUSSIAN("ru", "RU", "Русский", 0xFF1D4ED8),
    PORTUGUESE("pt", "PT", "Português", 0xFF16A34A),
    KOREAN("ko", "KO", "한국어", 0xFF9333EA),
    TURKISH("tr", "TR", "Türkçe", 0xFFEA580C),
    INDONESIAN("id", "ID", "Bahasa Indonesia", 0xFFB91C1C),
    ITALIAN("it", "IT", "Italiano", 0xFF059669)
}

data class DevToolsLogEntry(
    val level: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class BenchmarkReport(
    val totalScore: Int,
    val jsOpsMs: Double,
    val domRenderMs: Double,
    val canvas2dMs: Double,
    val wasmAvailable: Boolean,
    val webGlRenderer: String
)

object PageToolsEngine {

    const val EXTRACT_PAGE_TEXT_JS: String = """
        (function() {
            try {
                var article = document.querySelector('article, main, [role="main"]') || document.body;
                return (article && article.innerText ? article.innerText : '').replace(/\s+/g, ' ').trim().slice(0, 6000);
            } catch(e) { return ''; }
        })();
    """

    const val EXTRACT_READER_JSON_JS: String = """
        (function() {
            try {
                var root = document.querySelector('article, main, [role="main"], .post-content, .article-body') || document.body;
                var title = document.title || '';
                var blocks = [];
                var nodes = root.querySelectorAll('h1, h2, h3, p, blockquote, li');
                for (var i = 0; i < nodes.length && blocks.length < 180; i++) {
                    var el = nodes[i];
                    var txt = (el.innerText || '').trim();
                    if (txt.length >= 25 || el.tagName.startsWith('H')) {
                        blocks.push({ tag: el.tagName.toLowerCase(), text: txt });
                    }
                }
                if (blocks.length === 0 && root.innerText) {
                    blocks.push({ tag: 'p', text: root.innerText.trim().slice(0, 8000) });
                }
                return JSON.stringify({ title: title, blocks: blocks });
            } catch(e) {
                return JSON.stringify({ title: document.title || 'Reader', blocks: [] });
            }
        })();
    """

    fun buildReaderHtml(
        rawJson: String,
        sourceUrl: String,
        paper: ReaderPaper,
        font: ReaderFont,
        zoomPercent: Int
    ): String {
        val obj = try { JSONObject(rawJson) } catch (_: Exception) { JSONObject() }
        val title = escapeHtml(obj.optString("title", "Reader View"))
        val blocksArr = obj.optJSONArray("blocks") ?: JSONArray()
        val fontSizePx = (18 * (zoomPercent / 100f)).toInt().coerceIn(12, 32)
        val bodyHtml = buildString {
            for (i in 0 until blocksArr.length()) {
                val b = blocksArr.optJSONObject(i) ?: continue
                val tag = when (b.optString("tag")) {
                    "h1", "h2", "h3", "blockquote", "li", "p" -> b.optString("tag")
                    else -> "p"
                }
                val text = escapeHtml(b.optString("text"))
                if (text.isNotBlank()) {
                    append("<$tag>$text</$tag>")
                }
            }
        }
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <style>
                body {
                  background: ${paper.bgHex};
                  color: ${paper.fgHex};
                  font-family: ${font.cssFamily};
                  font-size: ${fontSizePx}px;
                  line-height: 1.72;
                  max-width: 720px;
                  margin: 0 auto;
                  padding: 24px 20px 64px 20px;
                }
                .bh-meta {
                  color: ${paper.mutedHex};
                  font-size: 12px;
                  margin-bottom: 16px;
                  word-break: break-all;
                }
                h1, h2, h3 { line-height: 1.3; margin-top: 1.3em; }
                blockquote {
                  border-left: 3px solid #00D4FF;
                  padding-left: 14px;
                  margin-left: 0;
                  opacity: 0.9;
                }
              </style>
            </head>
            <body>
              <div class="bh-meta">BrowserHub Reader Mode • ${escapeHtml(sourceUrl)}</div>
              <h1>$title</h1>
              $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }

    const val EXTRACT_TRANSLATABLE_BLOCKS_JS: String = """
        (function() {
            var els = document.querySelectorAll('h1,h2,h3,h4,p,li,blockquote,td,th,a,span');
            var out = [];
            var seen = new Set();
            window.__bhTranslateTargets = [];
            for (var i = 0; i < els.length && out.length < 40; i++) {
                var el = els[i];
                if (el.closest('script,style,noscript,code,pre,#bh-zapper-hud')) continue;
                var hasChildBlock = el.querySelector('p,h1,h2,h3,div,li');
                if (hasChildBlock) continue;
                var txt = (el.innerText || '').trim();
                if (txt.length < 4 || txt.length > 400 || seen.has(el)) continue;
                seen.add(el);
                window.__bhTranslateTargets.push(el);
                out.push(txt);
            }
            return JSON.stringify(out);
        })();
    """

    fun buildApplyTranslationJs(translated: List<String>, bilingualMode: Boolean): String {
        val jsonArr = JSONArray(translated).toString()
        return """
            (function() {
                var targets = window.__bhTranslateTargets || [];
                var items = $jsonArr;
                var bilingual = $bilingualMode;
                for (var i = 0; i < targets.length && i < items.length; i++) {
                    var el = targets[i];
                    var tr = items[i];
                    if (!el || !tr) continue;
                    if (!el.dataset.bhOrigText) {
                        el.dataset.bhOrigText = el.innerText;
                    }
                    if (bilingual) {
                        var oldSub = el.querySelector('.bh-bilingual-tr');
                        if (oldSub) oldSub.remove();
                        el.innerText = el.dataset.bhOrigText;
                        var div = document.createElement('div');
                        div.className = 'bh-bilingual-tr';
                        div.textContent = tr;
                        div.style.cssText = 'margin-top:4px;padding:4px 8px;border-left:2px solid #00D4FF;background:rgba(0,212,255,0.08);color:#7DD3FC;font-size:0.92em;border-radius:4px;';
                        el.appendChild(div);
                    } else {
                        el.innerText = tr;
                    }
                }
                return targets.length;
            })();
        """.trimIndent()
    }

    suspend fun translateStrings(
        texts: List<String>,
        language: TranslateLanguage,
        aiEngine: BrowserAiEngine
    ): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        // Primary: fast Google Translate public gtx batch endpoint (works across all 14 languages)
        val joined = texts.joinToString("\n|||\n")
        val gtx = tryTranslateViaGtx(joined, language.code)
        if (gtx != null) {
            val parts = gtx.split(Regex("\\s*\\|\\|\\|\\s*"))
            if (parts.size == texts.size) return@withContext parts
        }
        // Fallback: individual gtx or Gemini 3.8 Flash batch translation
        val aiRes = aiEngine.translateBatch(texts, language.nativeName)
        if (aiRes != null && aiRes.size == texts.size) return@withContext aiRes

        // Per-item gtx fallback for resilience
        texts.map { item -> tryTranslateViaGtx(item, language.code) ?: item }
    }

    private fun tryTranslateViaGtx(text: String, targetLang: String): String? {
        return try {
            val q = URLEncoder.encode(text, Charsets.UTF_8.name())
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$q")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader().readText()
                val arr = JSONArray(body).getJSONArray(0)
                buildString {
                    for (i in 0 until arr.length()) {
                        append(arr.getJSONArray(i).optString(0))
                    }
                }.trim()
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    const val DEVTOOLS_HOOK_JS: String = """
        (function() {
            if (window.__bhDevToolsHooked) return;
            window.__bhDevToolsHooked = true;
            var send = function(lvl, args) {
                try {
                    var msg = Array.prototype.slice.call(args).map(function(a) {
                        if (typeof a === 'object') {
                            try { return JSON.stringify(a); } catch(e) { return String(a); }
                        }
                        return String(a);
                    }).join(' ');
                    if (window.BrowserHubExtBridge && window.BrowserHubExtBridge.onDevToolsLog) {
                        window.BrowserHubExtBridge.onDevToolsLog(lvl, msg.slice(0, 600));
                    }
                } catch(e) {}
            };
            ['log', 'warn', 'error', 'info'].forEach(function(lvl) {
                var orig = console[lvl];
                console[lvl] = function() {
                    send(lvl.toUpperCase(), arguments);
                    if (orig) return orig.apply(console, arguments);
                };
            });
            window.addEventListener('error', function(ev) {
                send('ERR', [ev.message + ' @ ' + (ev.filename || '') + ':' + (ev.lineno || 0)]);
            });
        })();
    """

    const val BENCHMARK_JS: String = """
        (function() {
            var t0 = performance.now();
            var s = 0;
            for (var i = 0; i < 600000; i++) {
                s += (i * 31) ^ (i >>> 3);
            }
            var jsMs = performance.now() - t0;

            var t1 = performance.now();
            var box = document.createElement('div');
            box.style.cssText = 'position:fixed;left:-9999px;top:0;width:200px;';
            for (var j = 0; j < 400; j++) {
                var row = document.createElement('div');
                row.textContent = 'Node ' + j + ' ' + s;
                box.appendChild(row);
            }
            (document.body || document.documentElement).appendChild(box);
            var h = box.getBoundingClientRect().height;
            box.remove();
            var domMs = performance.now() - t1;

            var t2 = performance.now();
            var cvs = document.createElement('canvas');
            cvs.width = 256; cvs.height = 256;
            var ctx = cvs.getContext('2d');
            if (ctx) {
                for (var k = 0; k < 400; k++) {
                    ctx.fillStyle = 'rgba(' + (k % 255) + ',180,255,0.25)';
                    ctx.fillRect((k * 7) % 220, (k * 11) % 220, 32, 32);
                }
            }
            var c2dMs = performance.now() - t2;

            var webgl = 'Unavailable';
            try {
                var gl = cvs.getContext('webgl2') || cvs.getContext('webgl');
                if (gl) webgl = gl.getParameter(gl.RENDERER) || 'WebGL Supported';
            } catch(e) {}

            var wasm = (typeof WebAssembly === 'object' && typeof WebAssembly.instantiate === 'function');
            var rawScore = Math.round(10000 / Math.max(1, (jsMs + domMs + c2dMs) / 3));
            return JSON.stringify({
                score: Math.max(120, Math.min(9999, rawScore)),
                jsMs: Number(jsMs.toFixed(2)),
                domMs: Number(domMs.toFixed(2)),
                canvasMs: Number(c2dMs.toFixed(2)),
                wasm: wasm,
                webgl: String(webgl),
                touchHeight: h
            });
        })();
    """

    private fun escapeHtml(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
