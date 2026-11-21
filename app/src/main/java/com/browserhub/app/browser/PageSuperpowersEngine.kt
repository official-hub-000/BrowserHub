package com.browserhub.app.browser

import com.browserhub.app.data.model.PageXRayReport
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Unique browser power-user scripts injected cleanly into the active WebView:
 * - Page X-Ray live DOM & privacy scanner
 * - Universal AMOLED Force Dark Web CSS inverter (preserves true-color images/video)
 * - Teleprompter smooth requestAnimationFrame auto-scroller
 * - Universal HTML5 <video>/<audio> speed controller (0.5x to 3.0x)
 * - Copy & right-click protection unlocker
 */
object PageSuperpowersEngine {

    const val INSPECT_PAGE_XRAY_JS: String = """
        (function() {
            try {
                var host = (location.hostname || '').toLowerCase();
                var text = (document.body && document.body.innerText) ? document.body.innerText : '';
                var words = text.trim() ? text.trim().split(/\s+/).length : 0;
                var scripts = Array.prototype.slice.call(document.querySelectorAll('script[src]'));
                var thirdParty = 0;
                scripts.forEach(function(s) {
                    try {
                        var h = new URL(s.src, location.href).hostname.toLowerCase();
                        if (h && host && h.indexOf(host) === -1 && host.indexOf(h) === -1) {
                            thirdParty++;
                        }
                    } catch(e) {}
                });
                return JSON.stringify({
                    url: location.href || '',
                    title: document.title || host || 'Untitled',
                    wordCount: words,
                    linkCount: document.querySelectorAll('a[href]').length,
                    imageCount: document.querySelectorAll('img').length,
                    scriptCount: document.querySelectorAll('script').length,
                    thirdPartyScriptCount: thirdParty,
                    formCount: document.querySelectorAll('form, input').length,
                    hasCookies: (document.cookie || '').length > 0
                });
            } catch(e) {
                return '{}';
            }
        })();
    """

    fun parseXRayReport(rawJson: String, fallbackUrl: String): PageXRayReport {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: JSONObject()
        val words = obj.optInt("wordCount", 0)
        val thirdParty = obj.optInt("thirdPartyScriptCount", 0)
        val hasCookies = obj.optBoolean("hasCookies", false)
        val readingMin = max(1, (words / 210.0).roundToInt())
        val riskLabel = when {
            thirdParty >= 10 || (thirdParty >= 6 && hasCookies) -> "Elevated"
            thirdParty >= 3 || hasCookies -> "Moderate"
            else -> "Low"
        }
        return PageXRayReport(
            url = obj.optString("url").ifBlank { fallbackUrl },
            title = obj.optString("title").ifBlank { "Page X-Ray" },
            wordCount = words,
            readingMinutes = readingMin,
            linkCount = obj.optInt("linkCount", 0),
            imageCount = obj.optInt("imageCount", 0),
            scriptCount = obj.optInt("scriptCount", 0),
            thirdPartyScriptCount = thirdParty,
            formCount = obj.optInt("formCount", 0),
            hasCookies = hasCookies,
            privacyRiskLabel = riskLabel
        )
    }

    fun buildForceDarkWebJs(enabled: Boolean): String = if (enabled) {
        """
        (function() {
            try {
                var existing = document.getElementById('bh-force-dark-style');
                if (existing) return;
                var st = document.createElement('style');
                st.id = 'bh-force-dark-style';
                st.textContent = [
                    'html { filter: invert(0.92) hue-rotate(180deg) !important; background: #0b0b0b !important; }',
                    'img, video, picture, canvas, svg, iframe, [style*="background-image"] {',
                    '  filter: invert(1) hue-rotate(180deg) !important;',
                    '}'
                ].join('\n');
                (document.head || document.documentElement).appendChild(st);
            } catch(e) {}
        })();
        """.trimIndent()
    } else {
        """
        (function() {
            var st = document.getElementById('bh-force-dark-style');
            if (st) st.remove();
        })();
        """.trimIndent()
    }

    fun buildAutoScrollJs(speedLevel: Int): String {
        val pxPerFrame = when (speedLevel.coerceIn(0, 3)) {
            1 -> 0.85
            2 -> 1.85
            3 -> 3.4
            else -> 0.0
        }
        return if (pxPerFrame <= 0.0) {
            """
            (function() {
                window.__bhAutoScrollSpeed = 0;
                if (window.__bhAutoScrollRaf) {
                    cancelAnimationFrame(window.__bhAutoScrollRaf);
                    window.__bhAutoScrollRaf = null;
                }
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                window.__bhAutoScrollSpeed = $pxPerFrame;
                if (window.__bhAutoScrollRunning) return;
                window.__bhAutoScrollRunning = true;
                var tick = function() {
                    if (!window.__bhAutoScrollSpeed || window.__bhAutoScrollSpeed <= 0) {
                        window.__bhAutoScrollRunning = false;
                        return;
                    }
                    window.scrollBy(0, window.__bhAutoScrollSpeed);
                    window.__bhAutoScrollRaf = requestAnimationFrame(tick);
                };
                window.__bhAutoScrollRaf = requestAnimationFrame(tick);
            })();
            """.trimIndent()
        }
    }

    fun buildVideoSpeedJs(speed: Float): String {
        val safeSpeed = speed.coerceIn(0.5f, 3.0f)
        return """
        (function() {
            window.__bhVideoSpeed = $safeSpeed;
            var applySpeed = function() {
                document.querySelectorAll('video, audio').forEach(function(m) {
                    try {
                        if (Math.abs(m.playbackRate - window.__bhVideoSpeed) > 0.01) {
                            m.playbackRate = window.__bhVideoSpeed;
                        }
                    } catch(e) {}
                });
            };
            applySpeed();
            if (!window.__bhVideoSpeedTimer) {
                window.__bhVideoSpeedTimer = setInterval(applySpeed, 900);
            }
        })();
        """.trimIndent()
    }

    fun buildCopyUnlockJs(enabled: Boolean): String = if (enabled) {
        """
        (function() {
            try {
                var st = document.getElementById('bh-copy-unlock-style');
                if (!st) {
                    st = document.createElement('style');
                    st.id = 'bh-copy-unlock-style';
                    st.textContent = '*, *::before, *::after { -webkit-user-select: text !important; user-select: text !important; -webkit-touch-callout: default !important; }';
                    (document.head || document.documentElement).appendChild(st);
                }
                if (!window.__bhCopyUnlocked) {
                    window.__bhCopyUnlocked = true;
                    ['contextmenu', 'selectstart', 'copy', 'cut', 'dragstart'].forEach(function(evName) {
                        document.addEventListener(evName, function(e) { e.stopPropagation(); }, true);
                    });
                    if (document.body) {
                        document.body.oncontextmenu = null;
                        document.body.onselectstart = null;
                        document.body.oncopy = null;
                    }
                }
            } catch(e) {}
        })();
        """.trimIndent()
    } else {
        """
        (function() {
            var st = document.getElementById('bh-copy-unlock-style');
            if (st) st.remove();
        })();
        """.trimIndent()
    }
}
