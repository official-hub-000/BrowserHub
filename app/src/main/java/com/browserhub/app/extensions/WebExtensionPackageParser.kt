package com.browserhub.app.extensions

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

object WebExtensionPackageParser {

    /**
     * Extracts .zip, .xpi (Firefox), .crx (Chrome/Edge CRX2 & CRX3), or .nex/.oex (Opera)
     * into [destDir] safely, protecting against zip-slip path traversal.
     */
    fun extractPackageBytes(rawBytes: ByteArray, destDir: File) {
        if (rawBytes.size < 4) {
            throw IllegalArgumentException("Extension package is empty or truncated.")
        }

        val zipOffset = findZipPayloadOffset(rawBytes)
        if (zipOffset < 0 || zipOffset >= rawBytes.size) {
            throw IllegalArgumentException("Unsupported package format (expected .crx, .xpi, .nex, or .zip).")
        }

        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        val canonicalDest = destDir.canonicalPath
        ZipInputStream(ByteArrayInputStream(rawBytes, zipOffset, rawBytes.size - zipOffset)).use { zis ->
            var entry = zis.nextEntry
            var fileCount = 0
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(destDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(canonicalDest + File.separator)) {
                        throw SecurityException("Blocked unsafe zip entry path: ${entry.name}")
                    }
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        zis.copyTo(out)
                    }
                    fileCount++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            if (fileCount == 0) {
                throw IllegalArgumentException("Extension archive contained no files.")
            }
        }
    }

    fun readAllBytesLimited(input: InputStream, maxBytes: Int = 35 * 1024 * 1024): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Extension package exceeds 35 MB limit.")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * Parses CRX2, CRX3, NEX, XPI, or ZIP headers to return the byte offset where PKZIP starts.
     */
    internal fun findZipPayloadOffset(bytes: ByteArray): Int {
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
        ) {
            return 0
        }

        if (bytes.size >= 12 &&
            bytes[0] == 0x43.toByte() &&
            bytes[1] == 0x72.toByte() &&
            bytes[2] == 0x32.toByte() &&
            bytes[3] == 0x34.toByte()
        ) {
            val version = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (version == 3 && bytes.size >= 12) {
                val headerSize = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val offset = 12 + headerSize
                if (offset in 12 until bytes.size) return offset
            } else if (version == 2 && bytes.size >= 16) {
                val pubKeyLen = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val sigLen = ByteBuffer.wrap(bytes, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val offset = 16 + pubKeyLen + sigLen
                if (offset in 16 until bytes.size) return offset
            }
        }

        val limit = minOf(bytes.size - 4, 131072)
        for (i in 0..limit) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x03.toByte() &&
                bytes[i + 3] == 0x04.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    fun parseManifest(extDir: File): ParsedManifest {
        val manifestFile = File(extDir, "manifest.json")
        if (!manifestFile.exists()) {
            throw IllegalArgumentException("manifest.json not found in extension package.")
        }
        val raw = manifestFile.readText()
        val json = JSONObject(raw)

        val manifestVersion = json.optInt("manifest_version", 2)
        val rawName = json.optString("name", "Unnamed Extension")
        val resolvedName = resolveLocalizedString(extDir, json, rawName)
        val version = json.optString("version", "1.0.0")
        val rawDesc = json.optString("description", "")
        val resolvedDesc = resolveLocalizedString(extDir, json, rawDesc)

        val actionObj = json.optJSONObject("action")
            ?: json.optJSONObject("browser_action")
            ?: json.optJSONObject("page_action")
        val popupPath = actionObj?.optString("default_popup")?.takeIf { it.isNotBlank() }

        val optionsUiObj = json.optJSONObject("options_ui")
        val optionsPath = optionsUiObj?.optString("page")?.takeIf { it.isNotBlank() }
            ?: json.optString("options_page").takeIf { it.isNotBlank() }

        val permissions = buildList {
            val perms = json.optJSONArray("permissions")
            if (perms != null) {
                for (i in 0 until perms.length()) {
                    val p = perms.optString(i)
                    if (p.isNotBlank()) add(p)
                }
            }
            val hostPerms = json.optJSONArray("host_permissions")
            if (hostPerms != null) {
                for (i in 0 until hostPerms.length()) {
                    val p = hostPerms.optString(i)
                    if (p.isNotBlank()) add(p)
                }
            }
        }

        val contentScripts = buildList {
            val csArray = json.optJSONArray("content_scripts")
            if (csArray != null) {
                for (i in 0 until csArray.length()) {
                    val obj = csArray.optJSONObject(i) ?: continue
                    add(
                        ContentScriptRule(
                            matches = obj.optJSONArray("matches").toStringList(),
                            excludeMatches = obj.optJSONArray("exclude_matches").toStringList(),
                            jsFiles = obj.optJSONArray("js").toStringList(),
                            cssFiles = obj.optJSONArray("css").toStringList(),
                            runAt = obj.optString("run_at", "document_idle"),
                            allFrames = obj.optBoolean("all_frames", false)
                        )
                    )
                }
            }
        }

        val bgObj = json.optJSONObject("background")
        val backgroundScripts = buildList {
            val worker = bgObj?.optString("service_worker")?.takeIf { it.isNotBlank() }
            if (worker != null) add(worker)
            addAll(bgObj?.optJSONArray("scripts").toStringList())
        }

        return ParsedManifest(
            name = resolvedName,
            version = version,
            description = resolvedDesc,
            manifestVersion = manifestVersion,
            popupPath = popupPath,
            optionsPath = optionsPath,
            permissions = permissions,
            contentScripts = contentScripts,
            backgroundScripts = backgroundScripts
        )
    }

    /**
     * Converts a `.user.js` (Tampermonkey / Violentmonkey / GreasyFork) script
     * into a standard Manifest V3 extension directory on disk.
     */
    fun unpackUserScriptToDirectory(scriptText: String, destDir: File) {
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()

        var name = "UserScript Extension"
        var version = "1.0.0"
        var description = "Installed UserScript extension"
        var runAt = "document_idle"
        val matches = mutableListOf<String>()
        val excludes = mutableListOf<String>()

        val metaBlockRegex = Regex("// ==UserScript==([\\s\\S]*?)// ==/UserScript==")
        val metaBlock = metaBlockRegex.find(scriptText)?.groupValues?.getOrNull(1).orEmpty()
        metaBlock.lines().forEach { line ->
            val trimmed = line.trim().removePrefix("//").trim()
            if (trimmed.startsWith("@name ")) {
                name = trimmed.removePrefix("@name ").trim()
            } else if (trimmed.startsWith("@version ")) {
                version = trimmed.removePrefix("@version ").trim()
            } else if (trimmed.startsWith("@description ")) {
                description = trimmed.removePrefix("@description ").trim()
            } else if (trimmed.startsWith("@match ")) {
                matches.add(trimmed.removePrefix("@match ").trim())
            } else if (trimmed.startsWith("@include ")) {
                val inc = trimmed.removePrefix("@include ").trim()
                matches.add(if (inc == "*") "<all_urls>" else inc)
            } else if (trimmed.startsWith("@exclude ")) {
                excludes.add(trimmed.removePrefix("@exclude ").trim())
            } else if (trimmed.startsWith("@run-at ")) {
                val rawRunAt = trimmed.removePrefix("@run-at ").trim()
                runAt = when (rawRunAt) {
                    "document-start" -> "document_start"
                    "document-end" -> "document_end"
                    else -> "document_idle"
                }
            }
        }
        if (matches.isEmpty()) matches.add("<all_urls>")

        val manifestJson = JSONObject().apply {
            put("manifest_version", 3)
            put("name", name)
            put("version", version)
            put("description", description)
            put("permissions", JSONArray(listOf("storage")))
            put(
                "content_scripts",
                JSONArray().put(
                    JSONObject().apply {
                        put("matches", JSONArray(matches))
                        if (excludes.isNotEmpty()) put("exclude_matches", JSONArray(excludes))
                        put("js", JSONArray(listOf("userscript.js")))
                        put("run_at", runAt)
                    }
                )
            )
        }

        val gmWrapper = """
            // Greasemonkey / Tampermonkey compatibility layer
            var GM_info = { script: { name: ${JSONObject.quote(name)}, version: ${JSONObject.quote(version)} } };
            function GM_getValue(k, defVal) {
                try {
                    var raw = window.BrowserHubExtBridge ? window.BrowserHubExtBridge.storageGet(chrome.runtime.id, JSON.stringify([k])) : '{}';
                    var obj = JSON.parse(raw);
                    return (k in obj) ? obj[k] : defVal;
                } catch (e) { return defVal; }
            }
            function GM_setValue(k, val) {
                var o = {}; o[k] = val;
                if (chrome && chrome.storage && chrome.storage.local) chrome.storage.local.set(o);
            }
            function GM_deleteValue(k) {
                if (chrome && chrome.storage && chrome.storage.local) chrome.storage.local.remove([k]);
            }
            function GM_addStyle(css) {
                var s = document.createElement('style');
                s.textContent = css;
                (document.head || document.documentElement).appendChild(s);
            }
            $scriptText
        """.trimIndent()

        File(destDir, "manifest.json").writeText(manifestJson.toString(2))
        File(destDir, "userscript.js").writeText(gmWrapper)
    }

    fun loadLocaleMessagesJson(extDir: File): String {
        val manifestFile = File(extDir, "manifest.json")
        val defaultLocale = try {
            if (manifestFile.exists()) JSONObject(manifestFile.readText()).optString("default_locale", "en") else "en"
        } catch (_: Exception) {
            "en"
        }
        val candidates = listOf(
            File(extDir, "_locales/$defaultLocale/messages.json"),
            File(extDir, "_locales/en/messages.json"),
            File(extDir, "_locales/en_US/messages.json")
        )
        for (file in candidates) {
            if (file.exists()) {
                return try {
                    JSONObject(file.readText()).toString()
                } catch (_: Exception) {
                    "{}"
                }
            }
        }
        return "{}"
    }

    private fun resolveLocalizedString(extDir: File, manifest: JSONObject, raw: String): String {
        if (!raw.startsWith("__MSG_") || !raw.endsWith("__")) return raw
        val key = raw.removePrefix("__MSG_").removeSuffix("__")
        val defaultLocale = manifest.optString("default_locale", "en")
        val candidates = listOf(
            File(extDir, "_locales/$defaultLocale/messages.json"),
            File(extDir, "_locales/en/messages.json"),
            File(extDir, "_locales/en_US/messages.json")
        )
        for (file in candidates) {
            if (file.exists()) {
                try {
                    val msgJson = JSONObject(file.readText())
                    val entry = msgJson.optJSONObject(key)
                    val message = entry?.optString("message")
                    if (!message.isNullOrBlank()) return message
                } catch (_: Exception) {
                    // Fallback to next locale file
                }
            }
        }
        return key.replace("_", " ")
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optString(i)
                if (item.isNotBlank()) add(item)
            }
        }
    }
}

object MatchPattern {
    fun matchesAny(url: String, matches: List<String>, excludeMatches: List<String> = emptyList()): Boolean {
        if (url.isBlank()) return false
        if (excludeMatches.any { matchesPattern(url, it) }) return false
        return matches.any { matchesPattern(url, it) }
    }

    fun matchesPattern(url: String, pattern: String): Boolean {
        if (pattern == "<all_urls>") {
            return url.startsWith("http://", true) || url.startsWith("https://", true)
        }
        val schemeSep = pattern.indexOf("://")
        if (schemeSep <= 0) return false

        val patScheme = pattern.substring(0, schemeSep)
        val rest = pattern.substring(schemeSep + 3)
        val pathSep = rest.indexOf('/')
        val patHost = if (pathSep >= 0) rest.substring(0, pathSep) else rest
        val patPath = if (pathSep >= 0) rest.substring(pathSep) else "/*"

        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }

        val urlScheme = uri.scheme?.lowercase() ?: return false
        val urlHost = uri.host?.lowercase() ?: return false
        val urlPath = (uri.rawPath ?: "/").ifEmpty { "/" }

        val schemeMatches = when (patScheme.lowercase()) {
            "*" -> urlScheme == "http" || urlScheme == "https"
            else -> urlScheme == patScheme.lowercase()
        }
        if (!schemeMatches) return false

        val hostMatches = when {
            patHost == "*" -> true
            patHost.startsWith("*.") -> {
                val base = patHost.removePrefix("*.").lowercase()
                urlHost == base || urlHost.endsWith(".$base")
            }
            else -> urlHost == patHost.lowercase()
        }
        if (!hostMatches) return false

        val regexStr = "^" + patPath.split("*").joinToString(".*") { Regex.escape(it) } + "$"
        return Regex(regexStr).matches(urlPath)
    }
}
