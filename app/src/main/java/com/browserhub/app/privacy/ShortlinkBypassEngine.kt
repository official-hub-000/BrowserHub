package com.browserhub.app.privacy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

data class BypassResult(
    val originalUrl: String,
    val resolvedUrl: String,
    val method: String,
    val hops: Int
) {
    val didBypass: Boolean
        get() = resolvedUrl.isNotBlank() && resolvedUrl != originalUrl
}

object ShortlinkBypassEngine {

    private val adLinkAndLockerHosts = setOf(
        // Linkvertise family
        "linkvertise.com", "link-to.net", "link-center.net", "link-target.net",
        "direct-link.net", "file-link.net", "up-to-down.net", "linkvertise.net",
        "linkvertise.download",
        // Ad-Maven / Lootlabs / Lootlinks
        "admaven.com", "ad-maven.com", "lootlinks.com", "lootlinks.co",
        "loot-link.com", "lootlabs.gg", "lootdest.com", "lootdest.org",
        "mendationforc.info",
        // Work.ink & Paster.so / Paster.gg
        "work.ink", "workink.net", "workink.co", "r.work.ink", "paste.work.ink",
        "paster.so", "paster.gg", "pasteso.com",
        // Cuty / Boost / AdFoc / ShrinkMe / Exe / Lockr / GPLinks / Ouo / Shorte.st
        "cuty.io", "cety.io", "cutty.app", "cutty.io", "cutyion.com",
        "exe.io", "exey.io", "exee.io", "lockr.to",
        "shrinkme.click", "shrinkme.io", "shrinkme.site", "shrinkearn.com",
        "quickgate.org", "quickgate.io", "quickgate.net", "quickgate.com",
        "linkgec.com", "linkgec.net",
        "bstshrt.com", "bstshrt.net", "bstshrt.io",
        "boostylink.com", "boostylink.net",
        "bstlar.com", "mavenlinks.com", "mavenlinks.net", "vplink.in",
        "adfoc.us", "boost.ink", "boostfusedgt.com", "boostfusedgt.net", "leasurepartment.xyz",
        "letsboost.net", "letsboost.com",
        "shorte.st", "sh.st", "festyy.com", "gestyy.com", "ceesty.com",
        "gplinks.co", "gplinks.in", "get2short.com", "gplink.co",
        "adf.ly", "j.gs", "q.gs", "ouo.io", "ouo.press",
        "bc.vc", "za.gl", "uii.io", "fc.lc", "fc-lc.com",
        "clicksfly.com", "clk.sh", "droplink.co", "v2links.com",
        "shareus.io", "modijiurl.com", "rocklinks.net", "try2link.com",
        "linkrex.net", "payurl.me", "tny.so", "zirof.com",
        "dragonslayer.gg", "dragonslayer.cc", "dragonslayer.net", "dragonslayer.xyz",
        "egirls.wtf", "empebau.eu",
        "linkdirect.net", "vaultlinks.com", "vaultlinks.net",
        "leakutopia.com", "leakutopia.net", "leakslinks.com", "leakslinks.net",
        "goldpaster.com", "goldpaster.net", "leaked.tools"
    )

    private val socialUnlockHosts = setOf(
        "rekonise.com", "rkns.link", "mboost.me", "bst.gg", "booo.st",
        "social-unlock.com", "socialwolvez.com",
        "sub2get.com", "sub2unlock.com", "sub2unlock.net", "sub2unlock.me",
        "sub4unlock.io", "sub4unlock.com", "subfinal.com", "sub1s.com",
        "unlocknow.net", "ytsubme.com", "linkunlocker.com", "link2unlock.com"
    )

    private val pasterHosts = setOf(
        "justpaste.it", "paste-drop.com", "pastedrop.com", "pastebin.com",
        "pastecanyon.com", "pastehill.com", "pastemode.com",
        "rentry.org", "rentry.co", "rentry.xyz",
        "anotepad.com", "pastelua.com", "pastelua.net", "pastelink.net",
        "pastesite.org", "pastesite.com",
        "ecodevs.org", "ecodevs.com", "controlc.com",
        "privatebin.net", "privatebin.org",
        "razpaste.com", "hastebin.com", "hastebin.de",
        "n0paste.com", "n0paste.net", "pasteflash.com", "pasteflash.net",
        "pastefy.app", "pasteva.com", "pasteva.net",
        "pasteview.com", "pasteview.net", "telegra.ph",
        "thpaste.com", "thpaste.net", "gist.github.com"
    )

    private val urlShortenerHosts = setOf(
        "bit.ly", "bitly.com", "cl.gy", "goo.gl", "is.gd", "rebrand.ly",
        "rkns.link", "shorter.me", "t.co", "t.ly", "tiny.cc", "tinylink.onl",
        "tinyurl.com", "v.gd", "ow.ly", "buff.ly", "cutt.ly", "rb.gy",
        "shorturl.at", "s.id", "lnkd.in", "amzn.to", "fb.me", "trib.al",
        "dlvr.it", "short.io", "shorturl.gg", "x.co", "ity.im", "q.gs", "po.st"
    )

    private val nonPasterBypassableHosts: Set<String> =
        adLinkAndLockerHosts + socialUnlockHosts + urlShortenerHosts

    private val destinationParamKeys = listOf(
        "r", "u", "url", "dest", "destination", "target", "redirect", "redirect_to",
        "redir", "link", "q", "href", "next", "continue", "goto", "to", "out", "away",
        "s", "data", "payload", "encoded", "b64", "callback", "return", "returnUrl",
        "return_url", "ref", "l", "v"
    )

    fun isShortlinkOrWrapperUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val host = extractHost(url)
        if (host.isEmpty()) return false
        if (matchesHostSet(host, nonPasterBypassableHosts)) return true

        val parsedUri = runCatching { URI(url) }.getOrNull()
        val path = parsedUri?.path.orEmpty().trim('/')
        // Only treat paster sites as bypassable when pointing to a specific paste slug, not bare homepage
        if (matchesHostSet(host, pasterHosts) && (path.isNotEmpty() || !parsedUri?.rawQuery.isNullOrBlank())) {
            return true
        }

        val lower = url.lowercase(Locale.US)
        // Google-URL / Justpaste.it Redirect / generic RedirectResolver patterns
        if ((host == "google.com" || host.endsWith(".google.com")) &&
            (lower.contains("/url?") || lower.contains("/amp/"))
        ) {
            return extractEmbeddedQueryTarget(url) != null
        }
        if (extractEmbeddedQueryTarget(url) != null) return true
        if (extractBase64PathOrFragmentTarget(url) != null) return true
        return lower.contains("/redirect?") ||
            lower.contains("/out?") ||
            lower.contains("/away?") ||
            lower.contains("/go?") ||
            lower.contains("/jump?") ||
            lower.contains("/exit?")
    }

    suspend fun bypassUrl(rawUrl: String): BypassResult = withContext(Dispatchers.IO) {
        val cleaned = rawUrl.trim()
        require(cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) {
            "Enter a valid http:// or https:// link to bypass."
        }

        // 1. ParamsResolver + BaseResolver (query params, fragment, base64 path tokens, google-url)
        val unwrappedDirect = unwrapNestedTargets(cleaned)
        if (unwrappedDirect != cleaned && !isShortlinkOrWrapperUrl(unwrappedDirect)) {
            return@withContext BypassResult(
                originalUrl = cleaned,
                resolvedUrl = unwrappedDirect,
                method = "ParamsResolver / BaseResolver",
                hops = 1
            )
        }

        // 2. Service-specific fast API & JSON resolvers
        resolveKnownServiceDirect(unwrappedDirect)?.let { (target, serviceMethod) ->
            val finalTarget = unwrapNestedTargets(target)
            if (finalTarget != unwrappedDirect) {
                return@withContext BypassResult(
                    originalUrl = cleaned,
                    resolvedUrl = finalTarget,
                    method = serviceMethod,
                    hops = 1
                )
            }
        }

        // 3. Multi-hop Location Redirect + HTML/JS/Paster extractor (up to 12 hops)
        var currentUrl = unwrappedDirect
        var hops = 0
        var lastMethod = "Location Redirect"
        val visited = hashSetOf<String>()

        while (hops < 12 && currentUrl !in visited) {
            visited += currentUrl

            // Try dedicated API resolver at each hop too (for chained shortlinks)
            val serviceHop = resolveKnownServiceDirect(currentUrl)
            if (serviceHop != null && serviceHop.first != currentUrl) {
                currentUrl = unwrapNestedTargets(serviceHop.first)
                lastMethod = serviceHop.second
                hops++
                if (!isShortlinkOrWrapperUrl(currentUrl)) {
                    return@withContext BypassResult(
                        originalUrl = cleaned,
                        resolvedUrl = currentUrl,
                        method = lastMethod,
                        hops = hops
                    )
                }
                continue
            }

            val step = fetchNextHop(currentUrl)
            if (step == null || step.first == currentUrl) break
            currentUrl = unwrapNestedTargets(step.first)
            lastMethod = step.second
            hops++
            if (!isShortlinkOrWrapperUrl(currentUrl)) {
                return@withContext BypassResult(
                    originalUrl = cleaned,
                    resolvedUrl = currentUrl,
                    method = if (hops == 1) lastMethod else "$lastMethod ($hops hops)",
                    hops = hops
                )
            }
        }

        BypassResult(
            originalUrl = cleaned,
            resolvedUrl = currentUrl,
            method = if (currentUrl != cleaned) lastMethod else "No external target exposed server-side",
            hops = hops
        )
    }

    fun unwrapNestedTargets(url: String): String {
        var current = url
        repeat(6) {
            val extracted = extractEmbeddedQueryTarget(current)
                ?: extractBase64PathOrFragmentTarget(current)
                ?: return current
            if (extracted == current) return current
            current = extracted
        }
        return current
    }

    fun extractEmbeddedQueryTarget(url: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isBlank()) return null
        val pairs = query.split('&')
        for (key in destinationParamKeys) {
            val match = pairs.firstOrNull { pair ->
                pair.substringBefore('=').equals(key, ignoreCase = true)
            } ?: continue
            val rawVal = match.substringAfter('=', "").trim()
            if (rawVal.isEmpty()) continue
            val decoded = runCatching { URLDecoder.decode(rawVal, Charsets.UTF_8.name()) }.getOrDefault(rawVal).trim()
            if (looksLikeHttpUrl(decoded)) return decoded
            val b64 = decodeBase64UrlIfHttp(decoded) ?: decodeBase64UrlIfHttp(rawVal)
            if (b64 != null) return b64
        }
        // Fallback: inspect ANY query param whose value decodes to an http(s) URL
        for (pair in pairs) {
            val rawVal = pair.substringAfter('=', "").trim()
            if (rawVal.length < 10) continue
            val decoded = runCatching { URLDecoder.decode(rawVal, Charsets.UTF_8.name()) }.getOrDefault(rawVal).trim()
            if (looksLikeHttpUrl(decoded)) return decoded
            val b64 = decodeBase64UrlIfHttp(decoded) ?: decodeBase64UrlIfHttp(rawVal)
            if (b64 != null) return b64
        }
        return null
    }

    fun extractBase64PathOrFragmentTarget(url: String): String? {
        val fragment = url.substringAfter('#', "").trim()
        if (fragment.isNotBlank()) {
            if (looksLikeHttpUrl(fragment)) return fragment
            decodeBase64UrlIfHttp(fragment)?.let { return it }
        }
        val path = try {
            URI(url).path.orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (path.isBlank()) return null
        val segments = path.split('/').filter { it.isNotBlank() }
        for (seg in segments.reversed()) {
            if (seg.length >= 16) {
                decodeBase64UrlIfHttp(seg)?.let { return it }
            }
        }
        return null
    }

    private fun decodeBase64UrlIfHttp(candidate: String): String? {
        val clean = candidate.trim()
        if (clean.length < 10) return null
        val normalized = clean.replace('-', '+').replace('_', '/')
        return try {
            val bytes = decodeBase64Bytes(normalized) ?: return null
            val text = String(bytes, Charsets.UTF_8).trim()
            if (looksLikeHttpUrl(text)) text else null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase64Bytes(input: String): ByteArray? {
        val cleaned = input.filter { !it.isWhitespace() }.trimEnd('=')
        if (cleaned.isEmpty()) return null
        val out = ByteArray((cleaned.length * 3) / 4)
        var outIdx = 0
        var buffer = 0
        var bits = 0
        for (ch in cleaned) {
            val v = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in 'a'..'z' -> ch - 'a' + 26
                in '0'..'9' -> ch - '0' + 52
                '+' -> 62
                '/' -> 63
                else -> return null
            }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[outIdx++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return out.copyOf(outIdx)
    }

    private fun resolveKnownServiceDirect(url: String): Pair<String, String>? {
        val host = extractHost(url)
        if (host.isEmpty()) return null

        // 1. Linkvertise API
        if (host.contains("linkvertise") || host == "link-to.net" || host == "direct-link.net" ||
            host == "link-center.net" || host == "link-target.net" || host == "file-link.net" ||
            host == "up-to-down.net"
        ) {
            tryResolveLinkvertise(url)?.let { return it to "Linkvertise API Resolver" }
        }

        // 2. Rekonise / rkns.link API
        if (host == "rekonise.com" || host == "rkns.link") {
            tryResolveRekonise(url)?.let { return it to "Rekonise Unlock Resolver" }
        }

        // 3. Mboost.me Next.js state
        if (host == "mboost.me") {
            tryResolveMboost(url)?.let { return it to "Mboost.me Resolver" }
        }

        // 4. Work.ink / paste.work.ink / r.work.ink
        if (host == "work.ink" || host.endsWith(".work.ink") || host == "workink.net" || host == "workink.co") {
            tryResolveWorkInk(url)?.let { return it to "Work.ink Resolver" }
        }

        // 5. Paster sites (Rentry, Pastebin, Pastefy, Hastebin, Justpaste.it, etc.)
        if (matchesHostSet(host, pasterHosts)) {
            tryResolvePasterSite(url, host)?.let { return it to "Paster Link Extractor" }
        }

        return null
    }

    private fun tryResolveLinkvertise(url: String): String? {
        val m = Regex("https?://[^/]+/(\\d+/[^/?#]+)").find(url) ?: return null
        val path = m.groupValues[1]
        val apiUrl = "https://publisher.linkvertise.com/api/v1/redirect/link/static/$path"
        val body = fetchSmallText(apiUrl, accept = "application/json") ?: return null
        val match = Regex("\"target\"\\s*:\\s*\"(https?:\\\\?/\\\\?/[^\"]+)\"").find(body)
        return match?.groupValues?.getOrNull(1)?.replace("\\/", "/")
    }

    private fun tryResolveRekonise(url: String): String? {
        val slug = try {
            URI(url).path.orEmpty().trim('/').substringAfterLast('/')
        } catch (_: Exception) {
            ""
        }
        if (slug.isBlank()) return null
        val apiUrl = "https://api.rekonise.com/social-unlocks/$slug"
        val body = fetchSmallText(apiUrl, accept = "application/json") ?: return null
        return runCatching {
            val obj = JSONObject(body)
            obj.optString("url").takeIf { looksLikeHttpUrl(it) }
                ?: obj.optString("target").takeIf { looksLikeHttpUrl(it) }
        }.getOrNull()
    }

    private fun tryResolveMboost(url: String): String? {
        val html = fetchSmallText(url) ?: return null
        val nextDataMatch = Regex(
            "<script[^>]+id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)
        if (!nextDataMatch.isNullOrBlank()) {
            val targetMatch = Regex("\"targeturl\"\\s*:\\s*\"(https?:\\\\?/\\\\?/[^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(nextDataMatch)?.groupValues?.getOrNull(1)?.replace("\\/", "/")
            if (!targetMatch.isNullOrBlank()) return targetMatch
        }
        return extractDestinationFromHtml(html, url)
    }

    private fun tryResolveWorkInk(url: String): String? {
        val slug = try {
            URI(url).path.orEmpty().trim('/').substringAfterLast('/')
        } catch (_: Exception) {
            ""
        }
        if (slug.isNotBlank()) {
            val apiBody = fetchSmallText("https://redirect-api.work.ink/v1/link/$slug", accept = "application/json")
            if (!apiBody.isNullOrBlank()) {
                val jsonTarget = Regex("\"(?:destination|url|target)\"\\s*:\\s*\"(https?:\\\\?/\\\\?/[^\"]+)\"")
                    .find(apiBody)?.groupValues?.getOrNull(1)?.replace("\\/", "/")
                if (!jsonTarget.isNullOrBlank() && extractHost(jsonTarget) != "work.ink") {
                    return jsonTarget
                }
            }
        }
        val html = fetchSmallText(url) ?: return null
        return extractDestinationFromHtml(html, url)
    }

    private fun tryResolvePasterSite(url: String, host: String): String? {
        val path = runCatching { URI(url).path.orEmpty().trim('/') }.getOrDefault("")
        if (path.isBlank()) return null

        val rawUrl = when {
            host == "rentry.org" || host == "rentry.co" || host == "rentry.xyz" -> {
                val base = url.substringBefore('?').trimEnd('/')
                if (base.endsWith("/raw")) base else "$base/raw"
            }
            host == "pastebin.com" && !url.contains("/raw/") -> {
                val id = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
                if (id.isNotBlank()) "https://pastebin.com/raw/$id" else url
            }
            host.startsWith("hastebin.") && !url.contains("/raw/") -> {
                val id = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
                if (id.isNotBlank()) "https://$host/raw/$id" else url
            }
            host == "pastefy.app" -> {
                val id = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
                if (id.isNotBlank()) "https://pastefy.app/api/v2/paste/$id" else url
            }
            else -> url
        }

        val body = fetchSmallText(rawUrl) ?: return null
        val scoped = scopePasteContentIfHtml(body)
        return extractBestExternalTarget(scoped, host)
    }

    private fun scopePasteContentIfHtml(body: String): String {
        if (!body.contains("<html", ignoreCase = true) && !body.contains("<body", ignoreCase = true)) {
            return body
        }
        val tagBlocks = Regex(
            "<(?:textarea|pre|article|main)[^>]*>(.*?)</(?:textarea|pre|article|main)>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(body).map { it.groupValues[1] }.joinToString("\n")
        return if (tagBlocks.isNotBlank()) tagBlocks else body
    }

    private fun extractBestExternalTarget(content: String, baseHost: String): String? {
        val candidates = Regex("https?://[^\"'\\s<>\\\\)\\]]+", RegexOption.IGNORE_CASE)
            .findAll(content)
            .map { it.value.replace("\\/", "/").trimEnd('.', ',', ';', ')') }
            .distinct()
            .toList()

        for (candidate in candidates) {
            val h = extractHost(candidate)
            if (h.isBlank() || h == baseHost || h.endsWith(".$baseHost")) continue
            // Skip common static/CDN/font/schema domains inside HTML pages
            if (h.contains("w3.org") || h.contains("schema.org") || h.contains("googleapis.com") ||
                h.contains("gstatic.com") || h.contains("cloudflare.com") || h.contains("jquery.com") ||
                h.contains("jsdelivr.net") || h.contains("unpkg.com")
            ) {
                continue
            }
            if (candidate.endsWith(".css", true) || candidate.endsWith(".js", true) ||
                candidate.endsWith(".png", true) || candidate.endsWith(".jpg", true) ||
                candidate.endsWith(".svg", true) || candidate.endsWith(".ico", true) ||
                candidate.endsWith(".woff2", true)
            ) {
                continue
            }
            if (ShieldsEngine.evaluateRequest(candidate, nativeAdBlocker = true, analyticsTrackerShield = true).kind != ShieldBlockKind.NONE) {
                continue
            }
            return candidate
        }
        return null
    }

    private fun fetchSmallText(url: String, accept: String = "*/*"): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 6_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
                setRequestProperty("Accept", accept)
            }
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(98_304)
                    val total = StringBuilder()
                    while (total.length < 98_304) {
                        val n = r.read(buf)
                        if (n == -1) break
                        total.append(buf, 0, n)
                    }
                    total.toString()
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchNextHop(url: String): Pair<String, String>? {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 7_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
        } catch (_: Exception) {
            return null
        }

        return try {
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")?.trim()
                if (!loc.isNullOrBlank()) {
                    return URL(URL(url), loc).toString() to "Location Redirect"
                }
            }
            if (code !in 200..299) return null
            val contentType = conn.contentType.orEmpty().lowercase(Locale.US)
            if (!contentType.contains("text/html") && !contentType.contains("application/xhtml") && !contentType.contains("text/plain")) {
                return null
            }
            val html = conn.inputStream.bufferedReader().use { r ->
                val buf = CharArray(98_304)
                val total = StringBuilder()
                while (total.length < 98_304) {
                    val n = r.read(buf)
                    if (n == -1) break
                    total.append(buf, 0, n)
                }
                total.toString()
            }
            if (html.isBlank()) return null
            extractDestinationFromHtml(html, url)?.let { it to "HTML / Script Unwrapper" }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    internal fun extractDestinationFromHtml(html: String, baseUrl: String): String? {
        val baseHost = extractHost(baseUrl)

        // 1. <meta http-equiv="refresh" content="0;url=...">
        val metaRefresh = Regex(
            "<meta[^>]+http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]+content\\s*=\\s*[\"'][^\"']*url=([^\"'>\\s]+)",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (!metaRefresh.isNullOrBlank()) {
            val resolved = resolveRelativeOrNull(baseUrl, metaRefresh)
            if (resolved != null && resolved != baseUrl) return resolved
        }

        // 2. AdFoc.us click_url / general click_url / targeturl / destination JSON keys
        val jsonOrVarTarget = Regex(
            "(?:var\\s+click_url|\"targeturl\"|\"destination\"|\"redirectUrl\"|\"unlockUrl\"|\"finalUrl\"|\"target_url\")\\s*[:=]\\s*[\"'](https?:\\\\?/\\\\?/[^\"'\\s<>]+)[\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)?.replace("\\/", "/")
        if (!jsonOrVarTarget.isNullOrBlank() && extractHost(jsonOrVarTarget) != baseHost) {
            return jsonOrVarTarget
        }

        // 3. JS direct redirect assignments: location.href = "...", window.location.replace("...")
        val jsLocation = Regex(
            "(?:window\\.)?location(?:\\.href|\\.replace|\\s*=)\\s*\\(?\\s*[\"'](https?://[^\"'\\s<>]+)[\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (!jsLocation.isNullOrBlank() && extractHost(jsLocation) != baseHost) {
            return jsLocation
        }

        // 4. Inline atob("...") URLs
        val atobMatches = Regex("atob\\(\\s*[\"']([A-Za-z0-9+/=_-]{12,})[\"']\\s*\\)").findAll(html)
        for (m in atobMatches) {
            val decoded = decodeBase64UrlIfHttp(m.groupValues[1])
            if (decoded != null && extractHost(decoded) != baseHost) return decoded
        }

        // 5. External "get link / continue / skip / destination / unlock" anchors or data-href/data-url
        val dataAttr = Regex(
            "data-(?:href|url|target|link)\\s*=\\s*[\"'](https?://[^\"'\\s]+)[\"']",
            RegexOption.IGNORE_CASE
        ).findAll(html)
        for (m in dataAttr) {
            val href = m.groupValues[1].trim()
            val targetHost = extractHost(href)
            if (targetHost.isNotBlank() && targetHost != baseHost &&
                ShieldsEngine.evaluateRequest(href, nativeAdBlocker = true, analyticsTrackerShield = true).kind == ShieldBlockKind.NONE
            ) {
                return href
            }
        }

        val anchorRegex = Regex(
            "<a[^>]+href\\s*=\\s*[\"'](https?://[^\"'\\s]+)[\"'][^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        for (m in anchorRegex.findAll(html)) {
            val href = m.groupValues[1].trim()
            val text = m.groupValues[2].replace(Regex("<[^>]+>"), " ").trim().lowercase(Locale.US)
            val targetHost = extractHost(href)
            if (targetHost.isBlank() || targetHost == baseHost) continue
            if (ShieldsEngine.evaluateRequest(href, nativeAdBlocker = true, analyticsTrackerShield = true).kind != ShieldBlockKind.NONE) {
                continue
            }
            if (text.contains("get link") || text.contains("continue") || text.contains("proceed") ||
                text.contains("skip") || text.contains("destination") || text.contains("go to link") ||
                text.contains("download") || text.contains("unlock") || text.contains("visit")
            ) {
                return href
            }
        }
        return null
    }

    private fun resolveRelativeOrNull(baseUrl: String, target: String): String? {
        return try {
            URL(URL(baseUrl), target.trim()).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeHttpUrl(value: String): Boolean {
        return value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)
    }

    private fun matchesHostSet(host: String, set: Set<String>): Boolean {
        if (host in set) return true
        return set.any { host.endsWith(".$it") }
    }

    private fun extractHost(url: String): String {
        return try {
            URI(url).host.orEmpty().lowercase(Locale.US).removePrefix("www.")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Injected inside active WebView for countdown, social-unlock, and JS-locker wrappers
     * (Work.ink, AdMaven/Lootlabs, Cuty.io, Bstlar, Linkvertise, Sub2Unlock, Rekonise, Mboost, GPLinks, etc.):
     * - Fast-forwards timers up to 25ms
     * - Reveals & activates social-unlock steps without triggering ad popups
     * - Extracts embedded JSON / atob / data-url / destination button links
     */
    const val SHORTLINK_DOM_BYPASS_JS: String = """
        (function() {
            var currentHost = location.hostname.replace(/^www\./, '');
            var badAds = /doubleclick|googlesyndication|adsterra|popads|exoclick|juicyads|propeller|admaven/i;

            var scanOnce = function() {
                try {
                    if (!window.__bhTimersAccelerated) {
                        window.__bhTimersAccelerated = true;
                        var origTimeout = window.setTimeout;
                        window.setTimeout = function(fn, delay) {
                            return origTimeout.call(window, fn, Math.min(Number(delay) || 0, 25));
                        };
                        var origInterval = window.setInterval;
                        window.setInterval = function(fn, delay) {
                            return origInterval.call(window, fn, Math.min(Number(delay) || 0, 120));
                        };
                    }

                    // 1. Check Next.js / Nuxt / embedded JSON targets
                    try {
                        if (window.__NEXT_DATA__) {
                            var nd = JSON.stringify(window.__NEXT_DATA__);
                            var m1 = nd.match(/"(?:targeturl|destination|targetUrl|unlockUrl|redirectUrl)"\s*:\s*"(https?:[^"]+)"/i);
                            if (m1 && m1[1] && m1[1].indexOf(currentHost) === -1) return m1[1];
                        }
                    } catch(e) {}

                    // 2. Auto-complete social unlock steps & unhide locked buttons
                    var btns = document.querySelectorAll('a, button, input[type="submit"], [role="button"]');
                    for (var i = 0; i < btns.length; i++) {
                        var el = btns[i];
                        if (el.getAttribute('data-bh-clicked')) continue;
                        var txt = (el.innerText || el.value || '').trim().toLowerCase();
                        if (/subscribe|follow|like|join|bell|twitter|youtube|instagram|discord/i.test(txt) &&
                            el.tagName === 'A' && el.getAttribute('href')) {
                            el.setAttribute('data-bh-clicked', '1');
                            el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                        }
                    }

                    var foundUrl = '';
                    for (var j = 0; j < btns.length; j++) {
                        var b = btns[j];
                        var label = (b.innerText || b.value || '').trim().toLowerCase();
                        if (label.match(/get link|continue|proceed|skip|unlock|go to|open link|download|visit|claim|redirect/)) {
                            b.disabled = false;
                            b.removeAttribute('disabled');
                            b.classList.remove('disabled');
                            b.style.display = '';
                            b.style.visibility = 'visible';
                            b.style.pointerEvents = 'auto';
                            var href = b.getAttribute('href') || (b.dataset && (b.dataset.href || b.dataset.url || b.dataset.target)) || '';
                            if (href && /^https?:\/\//i.test(href) && href.indexOf(currentHost) === -1 && !badAds.test(href)) {
                                foundUrl = href;
                                break;
                            }
                        }
                    }

                    if (!foundUrl) {
                        var links = document.querySelectorAll('a[href^="http"], [data-url^="http"], [data-href^="http"]');
                        for (var k = 0; k < links.length; k++) {
                            var h = links[k].href || links[k].getAttribute('data-url') || links[k].getAttribute('data-href') || '';
                            if (h && h.indexOf(currentHost) === -1 && !badAds.test(h)) {
                                var t = (links[k].innerText || '').trim().toLowerCase();
                                if (/link|continue|get|unlock|go|download|destination|paste/i.test(t)) {
                                    foundUrl = h;
                                    break;
                                }
                            }
                        }
                    }
                    return foundUrl;
                } catch(e) { return ''; }
            };

            var immediate = scanOnce();
            if (immediate) return immediate;

            if (!window.__bhBypassWatcherActive) {
                window.__bhBypassWatcherActive = true;
                var ticks = 0;
                var timer = setInterval(function() {
                    ticks++;
                    if (ticks > 42 || window.__bhBypassReported === location.href) {
                        clearInterval(timer);
                        window.__bhBypassWatcherActive = false;
                        return;
                    }
                    var nextUrl = scanOnce();
                    if (nextUrl) {
                        window.__bhBypassReported = location.href;
                        clearInterval(timer);
                        window.__bhBypassWatcherActive = false;
                        if (window.BrowserHubExtBridge && window.BrowserHubExtBridge.onBypassDestinationFound) {
                            window.BrowserHubExtBridge.onBypassDestinationFound(nextUrl);
                        }
                    }
                }, 350);
            }
            return '';
        })();
    """
}
