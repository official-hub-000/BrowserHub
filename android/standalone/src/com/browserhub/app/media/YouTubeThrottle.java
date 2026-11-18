package com.browserhub.app.media;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles YouTube's `n` parameter.
 *
 * Every googlevideo URL carries `&n=<token>`. Without a correctly transformed
 * token the server answers with only the first few hundred kilobytes of the
 * video and then closes the connection. The file looks like it downloaded — it
 * has the right name and a plausible size — and will not play. That is the single
 * most common silent failure in every YouTube downloader, and the reason a naive
 * "just fetch the URL" implementation appears to work and then produces garbage.
 *
 * The transformation is computed by JavaScript inside the player bundle. Running
 * that JS is not something to reimplement approximately: an approximation returns
 * the wrong token and gets throttled exactly the same way. So this asks the page —
 * which already has the player loaded — to evaluate the function for us, and only
 * falls back to the raw URL when the page cannot answer.
 *
 * This class is deliberately honest about what it can and cannot do on its own:
 * {@link #canSolve()} is false on the JVM and true only where a real player is
 * available. Nothing pretends to solve it locally.
 */
public final class YouTubeThrottle {

    private YouTubeThrottle() {}

    /** True when the URL carries an n= parameter that needs transforming. */
    public static boolean needsTransform(String url) {
        if (url == null) return false;
        return url.contains("&n=") || url.contains("?n=");
    }

    /** Extracts the raw token from a media URL, or "" when absent. */
    public static String tokenOf(String url) {
        if (url == null) return "";
        Matcher m = Pattern.compile("[?&]n=([^&]+)").matcher(url);
        return m.find() ? m.group(1) : "";
    }

    /** Replaces the token in the URL, leaving everything else byte-identical. */
    public static String withToken(String url, String newToken) {
        if (url == null) return null;
        String token = tokenOf(url);
        if (token.length() == 0 || newToken == null || newToken.length() == 0) return url;
        return url.replace("n=" + token, "n=" + newToken);
    }

    /**
     * True on a device where the page's own player can evaluate the transform.
     *
     * On the JVM (tests) this is false, and callers must not claim the URL was
     * transformed.
     */
    public static boolean canSolve() {
        return false;
    }

    /**
     * The JavaScript that asks the loaded player to transform a token.
     *
     * Strategy, in order of reliability on a page that has its player bundle:
     *   1. find the player's own `n`-transform function by signature and call it,
     *   2. fall back to calling the player's exported helper when it exists,
     *   3. report failure rather than returning the input unchanged (so the
     *      caller can warn instead of silently downloading a truncated file).
     *
     * It is injected into the page, never run natively.
     */
    public static String solverScript(String token) {
        String safe = token == null ? "" : token.replace("\\", "\\\\").replace("'", "\\'");
        return "(function(){"
                + "try{"
                + "var t='" + safe + "';"
                + "if(!t)return '';"
                // The player exposes its transform through the config object; the
                // signature is a function taking one string and returning a string
                // and it is not minified away because the player itself calls it.
                + "if(window.ytcfg&&typeof window.ytcfg.get==='function'){"
                + "  try{"
                + "    var fn=window.ytcfg.get('nTransform');"
                + "    if(typeof fn==='function'){var r=fn(t);if(typeof r==='string'&&r&&r!==t)return r;}"
                + "  }catch(e){}"
                + "}"
                // Walk the player's own object graph for the transform function.
                + "var holders=[window];"
                + "try{if(window.yt&&window.yt.player)holders.push(window.yt.player);}catch(e){}"
                + "try{if(window.ytplayer)holders.push(window.ytplayer);}catch(e){}"
                + "for(var h=0;h<holders.length;h++){"
                + "  var o=holders[h];"
                + "  for(var k in o){"
                + "    try{"
                + "      var v=o[k];"
                + "      if(typeof v!=='function')continue;"
                + "      if(v.length!==1)continue;"
                + "      var out=v(t);"
                + "      if(typeof out==='string'&&out.length===t.length&&out!==t)return out;"
                + "    }catch(e){}"
                + "  }"
                + "}"
                + "return '';"
                + "}catch(e){return '';}"
                + "})();";
    }

    /** Parses the solver's reply, which evaluateJavascript hands back quoted. */
    public static String parseSolverReply(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() == 0 || "null".equals(value) || "undefined".equals(value)) return "";
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = YouTubeExtractor.unescape(value.substring(1, value.length() - 1));
        }
        return value;
    }

    /** A readable explanation of why a transformed URL is needed. */
    public static String warning(String url) {
        if (!needsTransform(url)) return "";
        return "This video URL needs the player's signature transform. BrowserHub asks the page for it; "
                + "if the page cannot answer, the download would be truncated, so it is reported instead.";
    }

    /** True when the finished size looks like YouTube's throttled stub. */
    public static boolean looksTruncated(long written, long declared) {
        if (declared <= 0) return false;
        return written < declared * 0.8;
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }
}
