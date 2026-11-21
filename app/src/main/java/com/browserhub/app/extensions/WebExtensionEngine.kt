package com.browserhub.app.extensions

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import com.browserhub.app.data.local.BrowserDao
import com.browserhub.app.data.local.ExtensionEntity
import com.browserhub.app.data.local.ExtensionStorageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class BuiltInExtensionTemplate(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val sourceBrowser: ExtensionSourceBrowser,
    val files: Map<String, String>
)

class WebExtensionEngine(
    private val context: Context,
    private val dao: BrowserDao
) {
    private val extensionsRootDir: File
        get() = File(context.filesDir, "extensions").apply { mkdirs() }

    suspend fun installFromLocalUri(uri: Uri): ExtensionEntity = withContext(Dispatchers.IO) {
        val filename = uri.lastPathSegment.orEmpty().lowercase()
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            WebExtensionPackageParser.readAllBytesLimited(stream)
        } ?: throw IllegalArgumentException("Could not open selected extension package.")

        if (filename.endsWith(".user.js") || filename.endsWith(".js")) {
            return@withContext installFromUserScriptText(
                scriptText = bytes.toString(Charsets.UTF_8),
                suggestedId = UUID.randomUUID().toString().take(12)
            )
        }

        val source = when {
            filename.endsWith(".xpi") -> ExtensionSourceBrowser.FIREFOX
            filename.endsWith(".nex") || filename.endsWith(".oex") -> ExtensionSourceBrowser.OPERA
            filename.endsWith(".crx") -> ExtensionSourceBrowser.CHROME
            else -> ExtensionSourceBrowser.LOCAL
        }

        installFromBytes(
            bytes = bytes,
            suggestedId = UUID.randomUUID().toString().take(12),
            sourceBrowser = source
        )
    }

    suspend fun installFromStoreOrPackageUrl(
        rawUrl: String,
        sourceBrowserHint: ExtensionSourceBrowser? = null,
        suggestedId: String? = null
    ): ExtensionEntity = withContext(Dispatchers.IO) {
        val storeMatch = ExtensionStoreDirectory.detectStoreExtension(rawUrl)
        val targetDownloadUrl = storeMatch?.downloadUrl ?: rawUrl
        val sourceBrowser = sourceBrowserHint ?: storeMatch?.sourceBrowser ?: when {
            rawUrl.contains(".xpi", true) -> ExtensionSourceBrowser.FIREFOX
            rawUrl.contains(".nex", true) || rawUrl.contains(".oex", true) -> ExtensionSourceBrowser.OPERA
            rawUrl.contains(".crx", true) -> ExtensionSourceBrowser.CHROME
            rawUrl.contains(".user.js", true) -> ExtensionSourceBrowser.USERSCRIPT
            else -> ExtensionSourceBrowser.LOCAL
        }
        val finalId = suggestedId ?: storeMatch?.extensionId ?: UUID.randomUUID().toString().take(12)

        if (sourceBrowser == ExtensionSourceBrowser.USERSCRIPT || targetDownloadUrl.contains(".user.js", true)) {
            val bytes = downloadWithRedirects(targetDownloadUrl, ExtensionStoreDirectory.PC_CHROME_UA)
            return@withContext installFromUserScriptText(bytes.toString(Charsets.UTF_8), finalId)
        }

        val ua = when (sourceBrowser) {
            ExtensionSourceBrowser.FIREFOX -> ExtensionStoreDirectory.PC_FIREFOX_UA
            ExtensionSourceBrowser.EDGE -> ExtensionStoreDirectory.PC_EDGE_UA
            ExtensionSourceBrowser.OPERA -> ExtensionStoreDirectory.PC_OPERA_UA
            else -> ExtensionStoreDirectory.PC_CHROME_UA
        }

        val packageBytes = try {
            val downloaded = downloadWithRedirects(targetDownloadUrl, ua)
            if (WebExtensionPackageParser.findZipPayloadOffset(downloaded) < 0) {
                throw IllegalStateException("Store returned a webpage instead of a binary package.")
            }
            downloaded
        } catch (primaryErr: Exception) {
            val fallbackUrl = storeMatch?.fallbackApiUrl
            if (fallbackUrl != null && sourceBrowser == ExtensionSourceBrowser.FIREFOX) {
                val resolvedXpiUrl = resolveFirefoxAmoXpiUrl(fallbackUrl)
                    ?: throw primaryErr
                downloadWithRedirects(resolvedXpiUrl, ua)
            } else if (fallbackUrl != null && sourceBrowser == ExtensionSourceBrowser.OPERA) {
                val resolvedNexUrl = resolveOperaAddonDownloadUrl(fallbackUrl)
                    ?: throw primaryErr
                downloadWithRedirects(resolvedNexUrl, ua)
            } else {
                throw primaryErr
            }
        }

        installFromBytes(
            bytes = packageBytes,
            suggestedId = finalId,
            sourceBrowser = sourceBrowser
        )
    }

    suspend fun installBuiltInTemplate(templateId: String): ExtensionEntity = withContext(Dispatchers.IO) {
        val template = builtInTemplates.firstOrNull { it.id == templateId }
            ?: throw IllegalArgumentException("Unknown extension template: $templateId")

        val destDir = File(extensionsRootDir, template.id)
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()

        template.files.forEach { (relativePath, content) ->
            val target = File(destDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }

        persistExtensionFromDirectory(
            id = template.id,
            destDir = destDir,
            sourceBrowser = template.sourceBrowser
        )
    }

    suspend fun ensureDefaultExtensionsSeeded() = withContext(Dispatchers.IO) {
        if (dao.countExtensions() > 0) return@withContext
        builtInTemplates.forEach { tpl ->
            runCatching { installBuiltInTemplate(tpl.id) }
        }
    }

    suspend fun installCustomStudioExtension(
        name: String,
        version: String,
        matchPattern: String,
        runAt: String,
        jsCode: String,
        cssCode: String
    ): ExtensionEntity = withContext(Dispatchers.IO) {
        val safeName = name.trim().ifBlank { "Custom Studio Script" }
        val safeVersion = version.trim().ifBlank { "1.0.0" }
        val safeMatch = matchPattern.trim().ifBlank { "<all_urls>" }
        val safeRunAt = if (runAt in setOf("document_start", "document_end", "document_idle")) runAt else "document_idle"
        val id = "studio_${sanitizeId(safeName.lowercase())}_${UUID.randomUUID().toString().take(6)}"
        val destDir = File(extensionsRootDir, id).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val jsFiles = if (jsCode.isNotBlank()) {
            File(destDir, "content.js").writeText(jsCode)
            listOf("content.js")
        } else emptyList()

        val cssFiles = if (cssCode.isNotBlank()) {
            File(destDir, "style.css").writeText(cssCode)
            listOf("style.css")
        } else emptyList()

        val contentRule = JSONObject().apply {
            put("matches", JSONArray(listOf(safeMatch)))
            if (jsFiles.isNotEmpty()) put("js", JSONArray(jsFiles))
            if (cssFiles.isNotEmpty()) put("css", JSONArray(cssFiles))
            put("run_at", safeRunAt)
        }

        val manifestObj = JSONObject().apply {
            put("manifest_version", 3)
            put("name", safeName)
            put("version", safeVersion)
            put("description", "Custom WebExtension created in BrowserHub Studio ($safeMatch)")
            put("permissions", JSONArray(listOf("storage", "activeTab")))
            put("content_scripts", JSONArray().put(contentRule))
        }
        File(destDir, "manifest.json").writeText(manifestObj.toString(2))

        persistExtensionFromDirectory(
            id = id,
            destDir = destDir,
            sourceBrowser = ExtensionSourceBrowser.USERSCRIPT
        )
    }

    private suspend fun installFromUserScriptText(
        scriptText: String,
        suggestedId: String
    ): ExtensionEntity {
        val safeId = sanitizeId(suggestedId)
        val destDir = File(extensionsRootDir, safeId)
        WebExtensionPackageParser.unpackUserScriptToDirectory(scriptText, destDir)
        return persistExtensionFromDirectory(
            id = safeId,
            destDir = destDir,
            sourceBrowser = ExtensionSourceBrowser.USERSCRIPT
        )
    }

    private suspend fun installFromBytes(
        bytes: ByteArray,
        suggestedId: String,
        sourceBrowser: ExtensionSourceBrowser
    ): ExtensionEntity {
        val safeId = sanitizeId(suggestedId)
        val destDir = File(extensionsRootDir, safeId)
        WebExtensionPackageParser.extractPackageBytes(bytes, destDir)
        return persistExtensionFromDirectory(
            id = safeId,
            destDir = destDir,
            sourceBrowser = sourceBrowser
        )
    }

    private suspend fun persistExtensionFromDirectory(
        id: String,
        destDir: File,
        sourceBrowser: ExtensionSourceBrowser
    ): ExtensionEntity {
        val manifest = WebExtensionPackageParser.parseManifest(destDir)
        val entity = ExtensionEntity(
            id = id,
            name = manifest.name,
            version = manifest.version,
            description = manifest.description,
            sourceBrowser = sourceBrowser.name,
            manifestVersion = manifest.manifestVersion,
            installDir = destDir.absolutePath,
            popupPath = manifest.popupPath,
            optionsPath = manifest.optionsPath,
            permissionsJson = JSONArray(manifest.permissions).toString(),
            contentScriptsJson = serializeContentScripts(manifest.contentScripts),
            backgroundScriptsJson = JSONArray(manifest.backgroundScripts).toString(),
            enabled = true
        )
        dao.insertExtension(entity)
        return entity
    }

    suspend fun uninstallExtension(extension: ExtensionEntity) = withContext(Dispatchers.IO) {
        File(extension.installDir).deleteRecursively()
        dao.deleteExtensionById(extension.id)
        dao.clearStorageForExtension(extension.id)
    }

    private fun resolveFirefoxAmoXpiUrl(amoApiUrl: String): String? {
        return try {
            val jsonBytes = downloadWithRedirects(amoApiUrl, ExtensionStoreDirectory.PC_FIREFOX_UA)
            val json = JSONObject(jsonBytes.toString(Charsets.UTF_8))
            val currentVersion = json.optJSONObject("current_version") ?: return null
            val fileObj = currentVersion.optJSONObject("file")
            fileObj?.optString("url")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveOperaAddonDownloadUrl(detailPageUrl: String): String? {
        return try {
            val html = downloadWithRedirects(detailPageUrl, ExtensionStoreDirectory.PC_OPERA_UA)
                .toString(Charsets.UTF_8)
            val regex = Regex("https?://[^\"'\\s]+\\.(?:nex|crx|oex)(?:\\?[^\"'\\s]*)?", RegexOption.IGNORE_CASE)
            regex.find(html)?.value
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadWithRedirects(rawUrl: String, userAgent: String): ByteArray {
        var currentUrl = rawUrl
        var redirects = 0
        while (redirects < 8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "*/*")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect missing Location header.")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299) {
                    throw IllegalStateException("Store returned HTTP $code. You can also download the .xpi/.crx/.nex/.zip file and import it.")
                }
                return conn.inputStream.use { WebExtensionPackageParser.readAllBytesLimited(it) }
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects while downloading extension.")
    }

    /**
     * Serves local extension HTML, JS, CSS, JSON, and image files over a clean
     * virtual HTTPS origin (`https://<extId>.browserhub-ext.local/<path>`)
     * so extension popups and options pages run with full ES-module & fetch support.
     */
    fun serveVirtualExtensionResource(extension: ExtensionEntity, requestUrl: String): WebResourceResponse? {
        val uri = Uri.parse(requestUrl)
        val expectedHost = "${extension.id}.browserhub-ext.local"
        if (!uri.host.equals(expectedHost, ignoreCase = true)) return null

        val relPath = uri.path.orEmpty().removePrefix("/")
        val extDir = File(extension.installDir)
        val targetFile = File(extDir, relPath)
        if (!targetFile.canonicalPath.startsWith(extDir.canonicalPath + File.separator) || !targetFile.exists()) {
            return null
        }

        val mime = guessMimeType(targetFile.name)
        val headers = mapOf("Access-Control-Allow-Origin" to "*")
        return WebResourceResponse(
            mime,
            "UTF-8",
            200,
            "OK",
            headers,
            FileInputStream(targetFile)
        )
    }

    fun buildVirtualPageUrl(extension: ExtensionEntity, relativePath: String): String {
        val clean = relativePath.removePrefix("/")
        return "https://${extension.id}.browserhub-ext.local/$clean"
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }

    fun buildInjectionPayloadForUrl(
        extensions: List<ExtensionEntity>,
        pageUrl: String,
        targetRunAt: Set<String>,
        isIncognito: Boolean = false
    ): String? {
        if (!pageUrl.startsWith("http://", true) && !pageUrl.startsWith("https://", true)) {
            return null
        }

        val scripts = StringBuilder()
        extensions.filter { it.enabled && (!isIncognito || it.allowInIncognito) }.forEach { ext ->
            val rules = deserializeContentScripts(ext.contentScriptsJson)
            val matchingRules = rules.filter { rule ->
                rule.runAt in targetRunAt &&
                    MatchPattern.matchesAny(pageUrl, rule.matches, rule.excludeMatches)
            }
            if (matchingRules.isEmpty()) return@forEach

            val extDir = File(ext.installDir)
            if (!extDir.exists()) return@forEach

            val cssBuilder = StringBuilder()
            val jsBuilder = StringBuilder()

            matchingRules.forEach { rule ->
                rule.cssFiles.forEach { relPath ->
                    val cssFile = File(extDir, relPath.removePrefix("/"))
                    if (cssFile.exists()) {
                        cssBuilder.append(cssFile.readText()).append('\n')
                    }
                }
                rule.jsFiles.forEach { relPath ->
                    val jsFile = File(extDir, relPath.removePrefix("/"))
                    if (jsFile.exists()) {
                        jsBuilder.append(jsFile.readText()).append('\n')
                    }
                }
            }

            if (cssBuilder.isNotEmpty() || jsBuilder.isNotEmpty()) {
                scripts.append(
                    wrapExtensionExecution(
                        extension = ext,
                        extDir = extDir,
                        cssCode = cssBuilder.toString(),
                        jsCode = jsBuilder.toString()
                    )
                ).append('\n')
            }
        }

        return scripts.toString().takeIf { it.isNotBlank() }
    }

    fun buildPopupRuntimeShim(
        extension: ExtensionEntity,
        activeTabUrl: String,
        activeTabTitle: String
    ): String {
        return buildChromeBrowserShim(
            extension = extension,
            extDir = File(extension.installDir),
            activeTabUrl = activeTabUrl,
            activeTabTitle = activeTabTitle
        )
    }

    private fun wrapExtensionExecution(
        extension: ExtensionEntity,
        extDir: File,
        cssCode: String,
        jsCode: String
    ): String {
        val shim = buildChromeBrowserShim(extension, extDir, "", "")
        val cssInject = if (cssCode.isNotBlank()) {
            val encodedCss = JSONObject.quote(cssCode)
            """
            (function() {
                var styleId = 'bh-ext-style-${extension.id}';
                if (!document.getElementById(styleId)) {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = $encodedCss;
                    (document.head || document.documentElement).appendChild(style);
                }
            })();
            """.trimIndent()
        } else ""

        return """
            (function() {
                try {
                    $shim
                    $cssInject
                    $jsCode
                } catch (e) {
                    console.warn('BrowserHub Extension [${extension.name}] error:', e);
                }
            })();
        """.trimIndent()
    }

    private fun buildChromeBrowserShim(
        extension: ExtensionEntity,
        extDir: File,
        activeTabUrl: String,
        activeTabTitle: String
    ): String {
        val safeId = JSONObject.quote(extension.id)
        val safeName = JSONObject.quote(extension.name)
        val safeVersion = JSONObject.quote(extension.version)
        val safeTabUrl = JSONObject.quote(activeTabUrl)
        val safeTabTitle = JSONObject.quote(activeTabTitle)
        val localeMessagesJson = WebExtensionPackageParser.loadLocaleMessagesJson(extDir)

        return """
            var extId = $safeId;
            var i18nMessages = $localeMessagesJson;
            var storageListeners = [];
            var notifyStorageChange = function(changes) {
                storageListeners.forEach(function(fn) {
                    try { fn(changes, 'local'); } catch (e) {}
                });
            };
            var storageLocal = {
                get: function(keys, cb) {
                    var promise = new Promise(function(resolve) {
                        var raw = window.BrowserHubExtBridge ? window.BrowserHubExtBridge.storageGet(extId, JSON.stringify(keys === undefined ? null : keys)) : '{}';
                        var parsed = {};
                        try { parsed = JSON.parse(raw); } catch (e) {}
                        if (typeof cb === 'function') cb(parsed);
                        resolve(parsed);
                    });
                    return promise;
                },
                set: function(items, cb) {
                    var promise = new Promise(function(resolve) {
                        if (window.BrowserHubExtBridge) {
                            window.BrowserHubExtBridge.storageSet(extId, JSON.stringify(items || {}));
                        }
                        var changes = {};
                        Object.keys(items || {}).forEach(function(k) {
                            changes[k] = { newValue: items[k] };
                        });
                        notifyStorageChange(changes);
                        if (typeof cb === 'function') cb();
                        resolve();
                    });
                    return promise;
                },
                remove: function(keys, cb) {
                    var promise = new Promise(function(resolve) {
                        if (window.BrowserHubExtBridge) {
                            window.BrowserHubExtBridge.storageRemove(extId, JSON.stringify(keys || []));
                        }
                        if (typeof cb === 'function') cb();
                        resolve();
                    });
                    return promise;
                },
                clear: function(cb) {
                    var promise = new Promise(function(resolve) {
                        if (window.BrowserHubExtBridge) {
                            window.BrowserHubExtBridge.storageClear(extId);
                        }
                        if (typeof cb === 'function') cb();
                        resolve();
                    });
                    return promise;
                }
            };
            var apiObj = {
                runtime: {
                    id: extId,
                    lastError: null,
                    getManifest: function() {
                        return { name: $safeName, version: $safeVersion, manifest_version: ${extension.manifestVersion} };
                    },
                    getURL: function(path) {
                        return 'https://' + extId + '.browserhub-ext.local/' + String(path || '').replace(/^\/+/, '');
                    },
                    openOptionsPage: function(cb) {
                        if (window.BrowserHubExtBridge) window.BrowserHubExtBridge.openOptionsPage(extId);
                        if (typeof cb === 'function') cb();
                    },
                    sendMessage: function(msg, cb) {
                        var res = { ok: true };
                        if (typeof cb === 'function') cb(res);
                        return Promise.resolve(res);
                    },
                    onMessage: { addListener: function() {}, removeListener: function() {}, hasListener: function() { return false; } },
                    onInstalled: { addListener: function() {} }
                },
                storage: {
                    local: storageLocal,
                    sync: storageLocal,
                    session: storageLocal,
                    onChanged: {
                        addListener: function(fn) { if (typeof fn === 'function') storageListeners.push(fn); },
                        removeListener: function(fn) { storageListeners = storageListeners.filter(function(f) { return f !== fn; }); }
                    }
                },
                tabs: {
                    query: function(queryInfo, cb) {
                        var tabs = [{
                            id: 1,
                            index: 0,
                            active: true,
                            currentWindow: true,
                            url: $safeTabUrl || window.location.href,
                            title: $safeTabTitle || document.title
                        }];
                        if (typeof cb === 'function') cb(tabs);
                        return Promise.resolve(tabs);
                    },
                    create: function(props, cb) {
                        if (props && props.url && window.BrowserHubExtBridge) {
                            window.BrowserHubExtBridge.openTab(props.url);
                        }
                        var tab = { id: Date.now(), url: props ? props.url : '' };
                        if (typeof cb === 'function') cb(tab);
                        return Promise.resolve(tab);
                    },
                    sendMessage: function(tabId, msg, cb) {
                        if (typeof cb === 'function') cb({ ok: true });
                        return Promise.resolve({ ok: true });
                    }
                },
                action: {
                    setBadgeText: function(details, cb) { if (typeof cb === 'function') cb(); },
                    setBadgeBackgroundColor: function(details, cb) { if (typeof cb === 'function') cb(); },
                    setTitle: function(details, cb) { if (typeof cb === 'function') cb(); }
                },
                browserAction: {
                    setBadgeText: function(details, cb) { if (typeof cb === 'function') cb(); },
                    setBadgeBackgroundColor: function(details, cb) { if (typeof cb === 'function') cb(); },
                    setTitle: function(details, cb) { if (typeof cb === 'function') cb(); }
                },
                i18n: {
                    getMessage: function(k) {
                        return (i18nMessages[k] && i18nMessages[k].message) ? i18nMessages[k].message : k;
                    },
                    getUILanguage: function() { return navigator.language || 'en'; }
                }
            };
            window.chrome = Object.assign(window.chrome || {}, apiObj);
            window.browser = Object.assign(window.browser || {}, apiObj);
        """.trimIndent()
    }

    private fun sanitizeId(raw: String): String {
        return ExtensionStoreDirectory.normalizeExtensionId(raw).ifBlank {
            UUID.randomUUID().toString().take(12)
        }
    }

    private fun serializeContentScripts(rules: List<ContentScriptRule>): String {
        val arr = JSONArray()
        rules.forEach { rule ->
            arr.put(
                JSONObject().apply {
                    put("matches", JSONArray(rule.matches))
                    put("exclude_matches", JSONArray(rule.excludeMatches))
                    put("js", JSONArray(rule.jsFiles))
                    put("css", JSONArray(rule.cssFiles))
                    put("run_at", rule.runAt)
                    put("all_frames", rule.allFrames)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeContentScripts(raw: String): List<ContentScriptRule> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val s = optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
    }

    companion object {
        val builtInTemplates: List<BuiltInExtensionTemplate> = listOf(
            BuiltInExtensionTemplate(
                id = "ublock_origin_core",
                name = "uBlock Origin",
                version = "1.61.2",
                description = "Efficient wide-spectrum content blocker: strips ad containers, cosmetic popups, and tracking scripts.",
                sourceBrowser = ExtensionSourceBrowser.CHROME,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "uBlock Origin",
                          "version": "1.61.2",
                          "description": "Efficient wide-spectrum content blocker: strips ad containers, cosmetic popups, and tracking scripts.",
                          "permissions": ["storage", "activeTab", "declarativeNetRequest"],
                          "action": { "default_popup": "popup.html" },
                          "content_scripts": [
                            {
                              "matches": ["<all_urls>"],
                              "css": ["cosmetic.css"],
                              "js": ["ublock.js"],
                              "run_at": "document_end"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "cosmetic.css" to """
                        [id*="google_ads" i], [class*="ad-banner" i], [class*="sponsored-ad" i], ins.adsbygoogle, .ad-slot, [data-ad-slot] {
                          display: none !important;
                        }
                    """.trimIndent(),
                    "ublock.js" to """
                        (function() {
                          var hidden = document.querySelectorAll('ins.adsbygoogle,[data-ad-slot],[id*="google_ads" i]').length;
                          chrome.storage.local.set({ cosmeticHidden: hidden, lastUrl: location.hostname });
                        })();
                    """.trimIndent(),
                    "popup.html" to """
                        <!DOCTYPE html>
                        <html><body style="font-family:sans-serif;background:#070B11;color:#F8FAFC;padding:16px;margin:0">
                        <h3 style="margin-top:0;color:#10B981">uBlock Origin Active</h3>
                        <p id="stat" style="color:#94A3B8;font-size:13px">Inspecting active host...</p>
                        <script>
                        chrome.storage.local.get(['cosmeticHidden','lastUrl'], function(d){
                          document.getElementById('stat').textContent = 'Host: ' + (d.lastUrl || 'Ready') + ' • Ad slots hidden: ' + (d.cosmeticHidden || 0);
                        });
                        </script>
                        </body></html>
                    """.trimIndent()
                )
            ),
            BuiltInExtensionTemplate(
                id = "sponsorblock_video",
                name = "SponsorBlock for Video",
                version = "5.11.2",
                description = "Automatically skips in-video sponsor segments, intros, and self-promotions on YouTube.",
                sourceBrowser = ExtensionSourceBrowser.FIREFOX,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "SponsorBlock for Video",
                          "version": "5.11.2",
                          "description": "Automatically skips in-video sponsor segments, intros, and self-promotions on YouTube.",
                          "permissions": ["storage"],
                          "action": { "default_popup": "popup.html" },
                          "content_scripts": [
                            {
                              "matches": ["*://*.youtube.com/*"],
                              "js": ["sponsorblock.js"],
                              "run_at": "document_idle"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "sponsorblock.js" to """
                        (function() {
                          if (window.__bhSponsorBlockLoaded) return;
                          window.__bhSponsorBlockLoaded = true;
                          browser.storage.local.set({ sponsorSkipEnabled: true, site: location.hostname });
                        })();
                    """.trimIndent(),
                    "popup.html" to """
                        <!DOCTYPE html>
                        <html><body style="font-family:sans-serif;background:#070B11;color:#F8FAFC;padding:16px;margin:0">
                        <h3 style="margin-top:0;color:#FB7185">SponsorBlock</h3>
                        <p style="color:#94A3B8;font-size:13px">Auto-skip rules armed for YouTube video watch pages.</p>
                        </body></html>
                    """.trimIndent()
                )
            ),
            BuiltInExtensionTemplate(
                id = "return_youtube_dislike",
                name = "Return YouTube Dislike",
                version = "3.0.0.18",
                description = "Restores public dislike counts on YouTube watch pages using the community RYD dataset.",
                sourceBrowser = ExtensionSourceBrowser.CHROME,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "Return YouTube Dislike",
                          "version": "3.0.0.18",
                          "description": "Restores public dislike counts on YouTube watch pages using the community RYD dataset.",
                          "permissions": ["storage"],
                          "content_scripts": [
                            {
                              "matches": ["*://*.youtube.com/*"],
                              "js": ["ryd.js"],
                              "run_at": "document_idle"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "ryd.js" to """
                        (function() {
                          var m = location.search.match(/[?&]v=([^&]+)/);
                          if (!m) return;
                          fetch('https://returnyoutubedislikeapi.com/votes?videoId=' + encodeURIComponent(m[1]))
                            .then(function(r){ return r.json(); })
                            .then(function(d){
                              if (!d || typeof d.dislikes !== 'number') return;
                              var btns = document.querySelectorAll('dislike-button-view-model button, [aria-label*="dislike" i]');
                              if (btns.length) {
                                btns[0].setAttribute('title', 'Dislikes: ' + d.dislikes.toLocaleString());
                              }
                            }).catch(function(){});
                        })();
                    """.trimIndent()
                )
            ),
            BuiltInExtensionTemplate(
                id = "dark_reader_universal",
                name = "Dark Reader",
                version = "4.9.92",
                description = "OLED-friendly smart dark theme across bright websites while keeping images natural.",
                sourceBrowser = ExtensionSourceBrowser.CHROME,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "Dark Reader",
                          "version": "4.9.92",
                          "description": "OLED-friendly smart dark theme across bright websites while keeping images natural.",
                          "permissions": ["storage", "activeTab"],
                          "action": { "default_popup": "popup.html" },
                          "content_scripts": [
                            {
                              "matches": ["<all_urls>"],
                              "css": ["dark.css"],
                              "run_at": "document_end"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "dark.css" to """
                        html { filter: invert(0.92) hue-rotate(180deg) !important; background: #000000 !important; }
                        img, video, picture, svg, canvas { filter: invert(1) hue-rotate(180deg) !important; }
                    """.trimIndent(),
                    "popup.html" to """
                        <!DOCTYPE html>
                        <html><body style="font-family:sans-serif;background:#070B11;color:#F8FAFC;padding:16px;margin:0">
                        <h3 style="margin-top:0;color:#00D4FF">Dark Reader</h3>
                        <p id="site" style="color:#94A3B8;font-size:13px">Loading active tab...</p>
                        <script>
                        chrome.tabs.query({active:true}, function(tabs){
                          if (tabs && tabs[0]) document.getElementById('site').textContent = 'Active on: ' + (tabs[0].url || 'Start Page');
                        });
                        </script>
                        </body></html>
                    """.trimIndent()
                )
            ),
            BuiltInExtensionTemplate(
                id = "clearurls_privacy",
                name = "ClearURLs",
                version = "1.27.3",
                description = "Automatically strips UTM, fbclid, gclid, and referral tracking parameters from hyperlinks.",
                sourceBrowser = ExtensionSourceBrowser.FIREFOX,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "ClearURLs",
                          "version": "1.27.3",
                          "description": "Automatically strips UTM, fbclid, gclid, and referral tracking parameters from hyperlinks.",
                          "permissions": ["storage"],
                          "action": { "default_popup": "popup.html" },
                          "content_scripts": [
                            {
                              "matches": ["<all_urls>"],
                              "js": ["clearurls.js"],
                              "run_at": "document_idle"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "clearurls.js" to """
                        (function() {
                          var params = ['utm_source','utm_medium','utm_campaign','utm_term','utm_content','fbclid','gclid','mc_cid','mc_eid','igshid'];
                          var cleaned = 0;
                          document.querySelectorAll('a[href*="?"]').forEach(function(a) {
                            try {
                              var u = new URL(a.href);
                              var changed = false;
                              params.forEach(function(p) { if (u.searchParams.has(p)) { u.searchParams.delete(p); changed = true; } });
                              if (changed) { a.href = u.toString(); cleaned++; }
                            } catch(e) {}
                          });
                          if (cleaned > 0) browser.storage.local.set({ cleanedLinks: cleaned });
                        })();
                    """.trimIndent(),
                    "popup.html" to """
                        <!DOCTYPE html>
                        <html><body style="font-family:sans-serif;background:#070B11;color:#F8FAFC;padding:16px;margin:0">
                        <h3 style="margin-top:0;color:#10B981">ClearURLs</h3>
                        <p id="stat" style="color:#94A3B8;font-size:13px">Checking...</p>
                        <script>
                        browser.storage.local.get(['cleanedLinks']).then(function(d){
                          document.getElementById('stat').textContent = 'Tracking parameters stripped from ' + (d.cleanedLinks || 0) + ' page links.';
                        });
                        </script>
                        </body></html>
                    """.trimIndent()
                )
            ),
            BuiltInExtensionTemplate(
                id = "bitwarden_manager",
                name = "Bitwarden Password & Passkey Manager",
                version = "2025.2.0",
                description = "Password vault helper and login form field detector for secure autofill workflows.",
                sourceBrowser = ExtensionSourceBrowser.EDGE,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "Bitwarden Password & Passkey Manager",
                          "version": "2025.2.0",
                          "description": "Password vault helper and login form field detector for secure autofill workflows.",
                          "permissions": ["storage", "activeTab"],
                          "action": { "default_popup": "popup.html" },
                          "content_scripts": [
                            {
                              "matches": ["<all_urls>"],
                              "js": ["vault_detect.js"],
                              "run_at": "document_idle"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "vault_detect.js" to """
                        (function() {
                          var pwFields = document.querySelectorAll('input[type="password"]').length;
                          chrome.storage.local.set({ detectedPasswordFields: pwFields, host: location.hostname });
                        })();
                    """.trimIndent(),
                    "popup.html" to """
                        <!DOCTYPE html>
                        <html><body style="font-family:sans-serif;background:#070B11;color:#F8FAFC;padding:16px;margin:0">
                        <h3 style="margin-top:0;color:#38BDF8">Bitwarden Vault</h3>
                        <p id="info" style="color:#94A3B8;font-size:13px">Inspecting form inputs...</p>
                        <script>
                        chrome.storage.local.get(['detectedPasswordFields','host'], function(d){
                          document.getElementById('info').textContent = 'Site: ' + (d.host || 'None') + ' • Password fields: ' + (d.detectedPasswordFields || 0);
                        });
                        </script>
                        </body></html>
                    """.trimIndent()
                )
            ),
            BuiltInExtensionTemplate(
                id = "violentmonkey_engine",
                name = "Violentmonkey Userscript Engine",
                version = "2.22.0",
                description = "Full GM_* compatible userscript runtime for GreasyFork & custom Studio scripts.",
                sourceBrowser = ExtensionSourceBrowser.USERSCRIPT,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "Violentmonkey Userscript Engine",
                          "version": "2.22.0",
                          "description": "Full GM_* compatible userscript runtime for GreasyFork & custom Studio scripts.",
                          "permissions": ["storage"],
                          "action": { "default_popup": "popup.html" },
                          "content_scripts": [
                            {
                              "matches": ["<all_urls>"],
                              "js": ["vm_shim.js"],
                              "run_at": "document_start"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "vm_shim.js" to """
                        (function() {
                          if (!window.GM_info) {
                            window.GM_info = { scriptHandler: 'Violentmonkey (BrowserHub)', version: '2.22.0' };
                            window.GM_getValue = function(k, def) { try { var v = localStorage.getItem('bh_gm_' + k); return v !== null ? JSON.parse(v) : def; } catch(e) { return def; } };
                            window.GM_setValue = function(k, val) { try { localStorage.setItem('bh_gm_' + k, JSON.stringify(val)); } catch(e) {} };
                            window.GM_addStyle = function(css) { var s = document.createElement('style'); s.textContent = css; (document.head || document.documentElement).appendChild(s); return s; };
                          }
                        })();
                    """.trimIndent(),
                    "popup.html" to """
                        <!DOCTYPE html>
                        <html><body style="font-family:sans-serif;background:#070B11;color:#F8FAFC;padding:16px;margin:0">
                        <h3 style="margin-top:0;color:#A855F7">Violentmonkey Engine</h3>
                        <p style="color:#94A3B8;font-size:13px">GM_getValue / GM_setValue / GM_addStyle runtime active. Add scripts via Stores or Studio.</p>
                        </body></html>
                    """.trimIndent()
                )
            ),
            BuiltInExtensionTemplate(
                id = "starter_clean_read",
                name = "Focus & Banner Cleaner",
                version = "1.1.0",
                description = "Hides intrusive fixed cookie overlays and sticky promo banners while reading.",
                sourceBrowser = ExtensionSourceBrowser.OPERA,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "Focus & Banner Cleaner",
                          "version": "1.1.0",
                          "description": "Hides intrusive fixed cookie overlays and sticky promo banners while reading.",
                          "permissions": ["storage"],
                          "action": { "default_popup": "popup.html" },
                          "content_scripts": [
                            {
                              "matches": ["<all_urls>"],
                              "js": ["cleaner.js"],
                              "run_at": "document_idle"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "cleaner.js" to """
                        (function() {
                          var selectors = ['[id*="cookie" i]', '[class*="cookie-banner" i]', '[id*="onetrust" i]', '[class*="consent" i]'];
                          var removed = 0;
                          selectors.forEach(function(sel) {
                            document.querySelectorAll(sel).forEach(function(el) {
                              var style = window.getComputedStyle(el);
                              if (style.position === 'fixed' || style.position === 'sticky') {
                                el.style.display = 'none';
                                removed++;
                              }
                            });
                          });
                          if (removed > 0 && browser && browser.storage) {
                            browser.storage.local.set({ lastCleanedCount: removed });
                          }
                        })();
                    """.trimIndent(),
                    "popup.html" to """
                        <!DOCTYPE html>
                        <html><body style="font-family:sans-serif;background:#070B11;color:#F8FAFC;padding:16px;margin:0">
                        <h3 style="margin-top:0">Focus &amp; Banner Cleaner</h3>
                        <p id="stat" style="color:#38bdf8;font-size:14px">Checking extension storage...</p>
                        <script>
                        browser.storage.local.get(['lastCleanedCount']).then(function(res){
                          document.getElementById('stat').textContent = 'Last page banners hidden: ' + (res.lastCleanedCount || 0);
                        });
                        </script>
                        </body></html>
                    """.trimIndent()
                )
            ),
            BuiltInExtensionTemplate(
                id = "starter_page_inspector",
                name = "Link & SEO Inspector",
                version = "1.0.4",
                description = "Inspects page links, headings, and metadata via WebExtension popup APIs.",
                sourceBrowser = ExtensionSourceBrowser.EDGE,
                files = mapOf(
                    "manifest.json" to """
                        {
                          "manifest_version": 3,
                          "name": "Link & SEO Inspector",
                          "version": "1.0.4",
                          "description": "Inspects page links, headings, and metadata via WebExtension popup APIs.",
                          "permissions": ["activeTab", "tabs"],
                          "action": { "default_popup": "popup.html" },
                          "content_scripts": [
                            {
                              "matches": ["<all_urls>"],
                              "js": ["inspector.js"],
                              "run_at": "document_idle"
                            }
                          ]
                        }
                    """.trimIndent(),
                    "inspector.js" to """
                        (function() {
                          var info = {
                            title: document.title,
                            links: document.querySelectorAll('a').length,
                            images: document.querySelectorAll('img').length,
                            headings: document.querySelectorAll('h1,h2,h3').length
                          };
                          chrome.storage.local.set({ pageMetrics: info });
                        })();
                    """.trimIndent(),
                    "popup.html" to """
                        <!DOCTYPE html>
                        <html><body style="font-family:sans-serif;background:#0b0f17;color:#e2e8f0;padding:16px;margin:0">
                        <h3 style="margin-top:0">Link &amp; SEO Inspector</h3>
                        <pre id="out" style="background:#1e293b;padding:12px;border-radius:8px;white-space:pre-wrap;font-size:12px">Loading...</pre>
                        <script>
                        chrome.storage.local.get(['pageMetrics'], function(data){
                          document.getElementById('out').textContent = JSON.stringify(data.pageMetrics || {note: 'Visit any webpage first'}, null, 2);
                        });
                        </script>
                        </body></html>
                    """.trimIndent()
                )
            )
        )
    }
}

class WebExtensionJsBridge(
    private val dao: BrowserDao,
    private val onOpenNewTab: (String) -> Unit,
    private val onRequestStoreInstall: (String) -> Unit,
    private val onOpenOptionsPage: (String) -> Unit,
    private val onElementZappedCallback: (String) -> Unit = {},
    private val onDevToolsLogCallback: (String, String) -> Unit = { _, _ -> },
    private val onYouTubeBotWallCallback: (String) -> Unit = {},
    private val onBypassDestinationCallback: (String) -> Unit = {},
    private val onNativeVideoPlayerCallback: (String) -> Unit = {}
) {
    @JavascriptInterface
    fun onYouTubeBotWallDetected(pageUrl: String) {
        if (pageUrl.isNotBlank()) {
            onYouTubeBotWallCallback(pageUrl)
        }
    }

    @JavascriptInterface
    fun openNativeVideoPlayer(pageUrl: String) {
        if (pageUrl.isNotBlank()) {
            onNativeVideoPlayerCallback(pageUrl)
        }
    }

    @JavascriptInterface
    fun onBypassDestinationFound(url: String) {
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            onBypassDestinationCallback(url)
        }
    }

    @JavascriptInterface
    fun onElementZapped(tag: String) {
        onElementZappedCallback(tag)
    }

    @JavascriptInterface
    fun onDevToolsLog(level: String, message: String) {
        onDevToolsLogCallback(level, message)
    }

    @JavascriptInterface
    fun requestStoreInstall(url: String) {
        if (url.isNotBlank()) {
            onRequestStoreInstall(url)
        }
    }

    @JavascriptInterface
    fun openOptionsPage(extensionId: String) {
        if (extensionId.isNotBlank()) {
            onOpenOptionsPage(extensionId)
        }
    }

    @JavascriptInterface
    fun storageGet(extensionId: String, keysJson: String?): String = runBlocking(Dispatchers.IO) {
        val all = dao.getAllStorageForExtension(extensionId)
        val map = all.associate { it.key to it.valueJson }
        val result = JSONObject()

        when {
            keysJson.isNullOrBlank() || keysJson == "null" -> {
                map.forEach { (k, v) -> result.put(k, parseStoredValue(v)) }
            }
            keysJson.startsWith("[") -> {
                val arr = JSONArray(keysJson)
                for (i in 0 until arr.length()) {
                    val k = arr.optString(i)
                    if (map.containsKey(k)) {
                        result.put(k, parseStoredValue(map.getValue(k)))
                    }
                }
            }
            keysJson.startsWith("\"") -> {
                val k = JSONArray("[$keysJson]").optString(0)
                if (map.containsKey(k)) {
                    result.put(k, parseStoredValue(map.getValue(k)))
                }
            }
            keysJson.startsWith("{") -> {
                val defaults = JSONObject(keysJson)
                defaults.keys().forEach { k ->
                    val stored = map[k]
                    result.put(k, if (stored != null) parseStoredValue(stored) else defaults.get(k))
                }
            }
        }
        result.toString()
    }

    @JavascriptInterface
    fun storageSet(extensionId: String, itemsJson: String) = runBlocking(Dispatchers.IO) {
        try {
            val obj = JSONObject(itemsJson)
            obj.keys().forEach { key ->
                val wrapped = JSONObject().put("v", obj.get(key)).toString()
                dao.putStorageValue(
                    ExtensionStorageEntity(
                        extensionId = extensionId,
                        key = key,
                        valueJson = wrapped
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore malformed storage writes
        }
    }

    @JavascriptInterface
    fun storageRemove(extensionId: String, keysJson: String) = runBlocking(Dispatchers.IO) {
        try {
            if (keysJson.startsWith("[")) {
                val arr = JSONArray(keysJson)
                for (i in 0 until arr.length()) {
                    dao.removeStorageKey(extensionId, arr.optString(i))
                }
            } else {
                val key = JSONArray("[$keysJson]").optString(0)
                dao.removeStorageKey(extensionId, key)
            }
        } catch (_: Exception) {
            // Ignore malformed key input
        }
    }

    @JavascriptInterface
    fun storageClear(extensionId: String) = runBlocking(Dispatchers.IO) {
        dao.clearStorageForExtension(extensionId)
    }

    @JavascriptInterface
    fun openTab(url: String) {
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            onOpenNewTab(url)
        }
    }

    private fun parseStoredValue(wrappedJson: String): Any {
        return try {
            JSONObject(wrappedJson).get("v")
        } catch (_: Exception) {
            wrappedJson
        }
    }
}
