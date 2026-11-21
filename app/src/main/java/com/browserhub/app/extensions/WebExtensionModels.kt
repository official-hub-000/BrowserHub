package com.browserhub.app.extensions

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class ExtensionSourceBrowser(val label: String, val badgeCode: String) {
    CHROME("Chrome", "CRX"),
    FIREFOX("Firefox", "XPI"),
    EDGE("Edge", "EDGE"),
    OPERA("Opera", "NEX"),
    USERSCRIPT("UserScript", "JS"),
    LOCAL("Package", "ZIP")
}

data class ContentScriptRule(
    val matches: List<String>,
    val excludeMatches: List<String> = emptyList(),
    val jsFiles: List<String> = emptyList(),
    val cssFiles: List<String> = emptyList(),
    val runAt: String = "document_idle",
    val allFrames: Boolean = false
)

data class ParsedManifest(
    val name: String,
    val version: String,
    val description: String,
    val manifestVersion: Int,
    val popupPath: String?,
    val optionsPath: String?,
    val permissions: List<String>,
    val contentScripts: List<ContentScriptRule>,
    val backgroundScripts: List<String>
)

data class ExtensionStoreLink(
    val id: String,
    val name: String,
    val subtitle: String,
    val storeUrl: String,
    val sourceBrowser: ExtensionSourceBrowser
)

data class StoreExtensionMatch(
    val sourceBrowser: ExtensionSourceBrowser,
    val extensionId: String,
    val titleHint: String,
    val downloadUrl: String,
    val fallbackApiUrl: String? = null
)

object ExtensionStoreDirectory {
    const val PC_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val PC_EDGE_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
    const val PC_FIREFOX_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
    const val PC_OPERA_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 OPR/114.0.0.0"

    val stores: List<ExtensionStoreLink> = listOf(
        ExtensionStoreLink(
            id = "chrome_store",
            name = "Chrome Web Store",
            subtitle = "PC Chrome mode • Click Add to Chrome",
            storeUrl = "https://chromewebstore.google.com/",
            sourceBrowser = ExtensionSourceBrowser.CHROME
        ),
        ExtensionStoreLink(
            id = "firefox_amo",
            name = "Firefox Add-ons",
            subtitle = "PC Firefox mode • Click Add to Firefox",
            storeUrl = "https://addons.mozilla.org/en-US/firefox/extensions/",
            sourceBrowser = ExtensionSourceBrowser.FIREFOX
        ),
        ExtensionStoreLink(
            id = "edge_addons",
            name = "Microsoft Edge Add-ons",
            subtitle = "PC Edge mode • Click Get to install",
            storeUrl = "https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home",
            sourceBrowser = ExtensionSourceBrowser.EDGE
        ),
        ExtensionStoreLink(
            id = "opera_addons",
            name = "Opera Add-ons",
            subtitle = "PC Opera mode • Click Add to Opera",
            storeUrl = "https://addons.opera.com/en/extensions/",
            sourceBrowser = ExtensionSourceBrowser.OPERA
        ),
        ExtensionStoreLink(
            id = "greasyfork",
            name = "Greasy Fork Scripts",
            subtitle = "Install .user.js browser extensions",
            storeUrl = "https://greasyfork.org/en/scripts",
            sourceBrowser = ExtensionSourceBrowser.USERSCRIPT
        )
    )

    private val chromeIdRegex = Regex("([a-p]{32})")
    private val edgeIdRegex = Regex("/detail/(?:[^/]+/)?([a-p]{32})")
    private val firefoxSlugRegex = Regex("/addon/([^/?#]+)")
    private val operaSlugRegex = Regex("/extensions/details/([^/?#]+)")

    fun getStoreDesktopUserAgent(url: String): String? {
        if (url.isBlank()) return null
        val lower = url.lowercase(Locale.US)
        return when {
            lower.contains("chromewebstore.google.com") || lower.contains("chrome.google.com/webstore") -> PC_CHROME_UA
            lower.contains("microsoftedge.microsoft.com/addons") -> PC_EDGE_UA
            lower.contains("addons.mozilla.org") -> PC_FIREFOX_UA
            lower.contains("addons.opera.com") -> PC_OPERA_UA
            else -> null
        }
    }

    fun normalizeExtensionId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    fun isExtensionInstalled(match: StoreExtensionMatch, installedIds: Set<String>): Boolean {
        return match.extensionId in installedIds || normalizeExtensionId(match.extensionId) in installedIds
    }

    fun isExtensionStoreHost(url: String): Boolean = getStoreDesktopUserAgent(url) != null ||
        url.lowercase(Locale.US).contains("greasyfork.org")

    fun isExtensionPackageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.endsWith(".xpi") || lower.contains(".xpi?") ||
            lower.endsWith(".crx") || lower.contains(".crx?") ||
            lower.endsWith(".nex") || lower.contains(".nex?") ||
            lower.endsWith(".oex") || lower.contains(".oex?") ||
            lower.endsWith(".user.js") || lower.contains(".user.js?") ||
            lower.contains("clients2.google.com/service/update2/crx") ||
            lower.contains("edge.microsoft.com/extensionwebstorebase/v1/crx") ||
            lower.contains("addons.opera.com/extensions/download/")
    }

    fun detectStoreExtension(url: String): StoreExtensionMatch? {
        if (url.isBlank()) return null
        val lower = url.lowercase(Locale.US)

        if (lower.endsWith(".user.js") || lower.contains(".user.js?")) {
            return StoreExtensionMatch(
                sourceBrowser = ExtensionSourceBrowser.USERSCRIPT,
                extensionId = Uri.parse(url).lastPathSegment ?: "userscript",
                titleHint = "UserScript Extension (.user.js)",
                downloadUrl = url
            )
        }
        if (lower.endsWith(".xpi") || lower.contains(".xpi?")) {
            return StoreExtensionMatch(
                sourceBrowser = ExtensionSourceBrowser.FIREFOX,
                extensionId = Uri.parse(url).lastPathSegment ?: "firefox_ext",
                titleHint = "Firefox Add-on (.xpi)",
                downloadUrl = url
            )
        }
        if (lower.endsWith(".crx") || lower.contains(".crx?")) {
            return StoreExtensionMatch(
                sourceBrowser = ExtensionSourceBrowser.CHROME,
                extensionId = Uri.parse(url).lastPathSegment ?: "chrome_ext",
                titleHint = "Chrome Extension (.crx)",
                downloadUrl = url
            )
        }
        if (lower.endsWith(".nex") || lower.contains(".nex?") ||
            lower.endsWith(".oex") || lower.contains(".oex?")
        ) {
            return StoreExtensionMatch(
                sourceBrowser = ExtensionSourceBrowser.OPERA,
                extensionId = Uri.parse(url).lastPathSegment ?: "opera_ext",
                titleHint = "Opera Extension (.nex)",
                downloadUrl = url
            )
        }

        // Chrome Web Store detail pages
        if (lower.contains("chromewebstore.google.com/detail/") ||
            lower.contains("chrome.google.com/webstore/detail/")
        ) {
            val id = chromeIdRegex.find(lower)?.groupValues?.getOrNull(1) ?: return null
            val crxUrl =
                "https://clients2.google.com/service/update2/crx?response=redirect&prodversion=131.0.6778.86&acceptformat=crx2,crx3&x=id%3D$id%26installsource%3Dondemand%26uc"
            return StoreExtensionMatch(
                sourceBrowser = ExtensionSourceBrowser.CHROME,
                extensionId = id,
                titleHint = "Chrome Extension ($id)",
                downloadUrl = crxUrl
            )
        }

        // Edge Add-ons detail pages
        if (lower.contains("microsoftedge.microsoft.com/addons/detail/")) {
            val id = edgeIdRegex.find(lower)?.groupValues?.getOrNull(1) ?: return null
            val crxUrl =
                "https://edge.microsoft.com/extensionwebstorebase/v1/crx?response=redirect&prod=chromiumcrx&prodchannel=&x=id%3D$id%26installsource%3Dondemand%26uc"
            return StoreExtensionMatch(
                sourceBrowser = ExtensionSourceBrowser.EDGE,
                extensionId = id,
                titleHint = "Edge Extension ($id)",
                downloadUrl = crxUrl
            )
        }

        // Firefox AMO detail pages
        if (lower.contains("addons.mozilla.org/") && lower.contains("/addon/")) {
            val slug = firefoxSlugRegex.find(url)?.groupValues?.getOrNull(1) ?: return null
            val xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/$slug/addon-latest.xpi"
            val apiUrl = "https://addons.mozilla.org/api/v5/addons/addon/$slug/"
            return StoreExtensionMatch(
                sourceBrowser = ExtensionSourceBrowser.FIREFOX,
                extensionId = slug,
                titleHint = slug.replace("-", " "),
                downloadUrl = xpiUrl,
                fallbackApiUrl = apiUrl
            )
        }

        // Opera Add-ons detail pages
        if (lower.contains("addons.opera.com/") && lower.contains("/extensions/details/")) {
            val slug = operaSlugRegex.find(url)?.groupValues?.getOrNull(1) ?: return null
            val nexUrl = "https://addons.opera.com/extensions/download/$slug/"
            return StoreExtensionMatch(
                sourceBrowser = ExtensionSourceBrowser.OPERA,
                extensionId = slug,
                titleHint = slug.replace("-", " "),
                downloadUrl = nexUrl,
                fallbackApiUrl = url
            )
        }

        return null
    }

    fun buildMarkInstalledJs(
        extensionId: String,
        extensionName: String,
        installedIds: Set<String>
    ): String {
        val shim = buildStorePageInstallShimJs(installedIds)
        val safeId = JSONObject.quote(extensionId)
        val safeName = JSONObject.quote(extensionName)
        return """
            $shim
            if (typeof window.__browserHubMarkInstalled === 'function') {
                window.__browserHubMarkInstalled($safeId, $safeName);
            }
        """.trimIndent()
    }

    fun buildSyncStoreStateJs(
        installedIds: Set<String>,
        clearInstalling: Boolean = false
    ): String {
        val shim = buildStorePageInstallShimJs(installedIds)
        return if (clearInstalling) {
            """
                $shim
                if (typeof window.__browserHubResetInstalling === 'function') {
                    window.__browserHubResetInstalling();
                }
            """.trimIndent()
        } else {
            shim
        }
    }

    /**
     * Injected into Chrome Web Store, Firefox AMO, Edge Add-ons, Opera Add-ons, and GreasyFork
     * so clicking the native PC "Add to Chrome" / "Add to Firefox" / "Get" / "Add to Opera"
     * button directly installs the extension into BrowserHub and visibly updates the store UI
     * to "Added to BrowserHub ✓" without losing SPA state.
     */
    fun buildStorePageInstallShimJs(installedIds: Set<String> = emptySet()): String {
        val installedJson = JSONArray(installedIds.toList()).toString()
        return """
            (function() {
                window.__browserHubInstalledIds = $installedJson;

                var normalizeId = function(raw) {
                    return String(raw || '').replace(/[^a-zA-Z0-9_-]/g, '_');
                };

                var isIdInstalled = function(id) {
                    if (!id) return false;
                    var list = window.__browserHubInstalledIds || [];
                    var norm = normalizeId(id);
                    return list.indexOf(id) !== -1 || list.indexOf(norm) !== -1;
                };

                var extractCurrentStoreExtId = function() {
                    var href = window.location.href || '';
                    var mChrome = href.match(/\/([a-p]{32})(?:[/?#]|$)/i);
                    if (mChrome) return mChrome[1].toLowerCase();
                    var mFirefox = href.match(/\/addon\/([^/?#]+)/i);
                    if (mFirefox) return mFirefox[1];
                    var mOpera = href.match(/\/extensions\/details\/([^/?#]+)/i);
                    if (mOpera) return mOpera[1];
                    return '';
                };

                var isCtaText = function(txt, el) {
                    if (!txt) return false;
                    var host = (window.location.hostname || '').toLowerCase();
                    if (txt === 'add to chrome' ||
                        txt === 'add to firefox' ||
                        txt === 'add to opera' ||
                        txt === 'install this script' ||
                        txt === 'installing in browserhub...' ||
                        txt === 'added to browserhub ✓' ||
                        txt.indexOf('add extension') !== -1) {
                        return true;
                    }
                    if (txt === 'get' && (host.indexOf('microsoftedge.microsoft.com') !== -1 || (el.getAttribute('aria-label') || '').toLowerCase().indexOf('get') !== -1)) {
                        return true;
                    }
                    return false;
                };

                var setButtonLabel = function(el, newLabel) {
                    if (!el.dataset.bhOrigText) {
                        el.dataset.bhOrigText = (el.innerText || el.textContent || '').trim();
                    }
                    var spans = el.querySelectorAll('span');
                    var updatedSpan = false;
                    for (var i = 0; i < spans.length; i++) {
                        var sp = spans[i];
                        var spText = (sp.textContent || '').trim();
                        if (sp.children.length === 0 && spText.length > 0 && isCtaText(spText.toLowerCase(), el)) {
                            sp.textContent = newLabel;
                            updatedSpan = true;
                            break;
                        }
                    }
                    if (!updatedSpan) {
                        el.textContent = newLabel;
                    }
                };

                var applyButtonState = function(el, state) {
                    if (state === 'installing') {
                        setButtonLabel(el, 'Installing in BrowserHub...');
                        el.dataset.bhState = 'installing';
                        el.style.opacity = '0.85';
                    } else if (state === 'installed') {
                        setButtonLabel(el, 'Added to BrowserHub ✓');
                        el.dataset.bhState = 'installed';
                        el.style.backgroundColor = '#059669';
                        el.style.color = '#FFFFFF';
                        el.style.borderColor = '#047857';
                        el.style.opacity = '1';
                        el.setAttribute('aria-disabled', 'true');
                    } else {
                        if (el.dataset.bhOrigText) {
                            setButtonLabel(el, el.dataset.bhOrigText);
                        }
                        delete el.dataset.bhState;
                        el.style.backgroundColor = '';
                        el.style.color = '';
                        el.style.borderColor = '';
                        el.style.opacity = '';
                        el.removeAttribute('aria-disabled');
                    }
                };

                window.__browserHubSyncStoreButtons = function(forceInstalled) {
                    var currentExtId = extractCurrentStoreExtId();
                    var installedNow = Boolean(forceInstalled) || isIdInstalled(currentExtId);
                    var candidates = document.querySelectorAll('button, a[role="button"], a.AMInstallButton-button, a.InstallButton, a.install-link');
                    for (var i = 0; i < candidates.length; i++) {
                        var el = candidates[i];
                        var txt = (el.innerText || el.textContent || '').trim().toLowerCase();
                        var curState = el.dataset.bhState || '';
                        if (installedNow && (curState === 'installing' || isCtaText(txt, el))) {
                            if (curState !== 'installed') {
                                applyButtonState(el, 'installed');
                            }
                        } else if (!installedNow && curState === 'installed') {
                            applyButtonState(el, 'default');
                        }
                    }
                };

                window.__browserHubShowStoreToast = function(extName) {
                    try {
                        var existing = document.getElementById('bh-store-installed-toast');
                        if (existing) existing.remove();
                        var pill = document.createElement('div');
                        pill.id = 'bh-store-installed-toast';
                        pill.textContent = '✓ ' + (extName || 'Extension') + ' added to BrowserHub';
                        pill.style.cssText = 'position:fixed;bottom:20px;left:50%;transform:translateX(-50%);z-index:2147483647;background:#059669;color:#ffffff;padding:10px 18px;border-radius:999px;font:600 13px/1.3 system-ui,-apple-system,sans-serif;box-shadow:0 8px 24px rgba(0,0,0,0.35);pointer-events:none;transition:opacity 0.3s ease;';
                        (document.body || document.documentElement).appendChild(pill);
                        setTimeout(function() {
                            pill.style.opacity = '0';
                            setTimeout(function() { if (pill.parentNode) pill.remove(); }, 350);
                        }, 3600);
                    } catch (e) {}
                };

                window.__browserHubMarkInstalled = function(extId, extName) {
                    if (extId && !isIdInstalled(extId)) {
                        window.__browserHubInstalledIds.push(extId);
                    }
                    window.__browserHubSyncStoreButtons(true);
                    window.__browserHubShowStoreToast(extName || 'Extension');
                    try {
                        window.dispatchEvent(new CustomEvent('browserhub-extension-installed', {
                            detail: { id: extId, name: extName }
                        }));
                    } catch (e) {}
                };

                window.__browserHubResetInstalling = function() {
                    var els = document.querySelectorAll('[data-bh-state="installing"]');
                    for (var i = 0; i < els.length; i++) {
                        applyButtonState(els[i], 'default');
                    }
                    window.__browserHubSyncStoreButtons(false);
                };

                if (!window.__browserHubStoreHooked) {
                    window.__browserHubStoreHooked = true;

                    // Polyfill Firefox InstallTrigger & mozAddonManager so AMO enables "Add to Firefox" and detects installed addons
                    if (!window.InstallTrigger) {
                        window.InstallTrigger = {
                            enabled: function() { return true; },
                            install: function(xpiMap) {
                                try {
                                    var keys = Object.keys(xpiMap || {});
                                    if (keys.length > 0 && window.BrowserHubExtBridge) {
                                        var entry = xpiMap[keys[0]];
                                        var xpiUrl = typeof entry === 'string' ? entry : (entry && entry.URL);
                                        if (xpiUrl) {
                                            window.BrowserHubExtBridge.requestStoreInstall(xpiUrl);
                                            return true;
                                        }
                                    }
                                } catch (e) {}
                                if (window.BrowserHubExtBridge) {
                                    window.BrowserHubExtBridge.requestStoreInstall(window.location.href);
                                }
                                return true;
                            }
                        };
                    }

                    try {
                        if (!navigator.mozAddonManager) {
                            Object.defineProperty(navigator, 'mozAddonManager', {
                                configurable: true,
                                value: {
                                    getAddonByID: function(id) {
                                        var currentSlug = extractCurrentStoreExtId();
                                        if (isIdInstalled(id) || isIdInstalled(currentSlug)) {
                                            return Promise.resolve({
                                                id: id,
                                                version: '1.0',
                                                isEnabled: true,
                                                isActive: true
                                            });
                                        }
                                        return Promise.resolve(null);
                                    },
                                    addEventListener: function() {},
                                    removeEventListener: function() {}
                                }
                            });
                        }
                    } catch (e) {}

                    // Polyfill Chrome webstorePrivate & management stubs so Chrome/Edge stores reflect installed extensions
                    if (!window.chrome) window.chrome = {};
                    if (!window.chrome.webstorePrivate) {
                        window.chrome.webstorePrivate = {
                            beginInstallWithManifest3: function(details, cb) {
                                if (window.BrowserHubExtBridge) {
                                    window.BrowserHubExtBridge.requestStoreInstall(window.location.href);
                                }
                                if (typeof cb === 'function') cb('');
                            },
                            completeInstall: function(id, cb) { if (typeof cb === 'function') cb(); },
                            getExtensionStatus: function(id, manifest, cb) {
                                if (typeof cb === 'function') {
                                    cb(isIdInstalled(id) ? 'enabled' : 'installable');
                                }
                            },
                            getStoreLogin: function(cb) { if (typeof cb === 'function') cb(''); },
                            getBrowserLogin: function(cb) { if (typeof cb === 'function') cb({login: ''}); }
                        };
                    }
                    if (!window.chrome.management) {
                        window.chrome.management = {
                            getAll: function(cb) {
                                var all = (window.__browserHubInstalledIds || []).map(function(id) {
                                    return { id: id, enabled: true, installType: 'normal', type: 'extension' };
                                });
                                if (typeof cb === 'function') cb(all);
                                return Promise.resolve(all);
                            },
                            get: function(id, cb) {
                                var info = isIdInstalled(id)
                                    ? { id: id, enabled: true, installType: 'normal', type: 'extension' }
                                    : null;
                                if (typeof cb === 'function') cb(info);
                                return Promise.resolve(info);
                            }
                        };
                    }

                    // Capture clicks on native PC Store install CTA buttons
                    document.addEventListener('click', function(ev) {
                        var target = ev.target;
                        while (target && target !== document.body) {
                            var tag = (target.tagName || '').toLowerCase();
                            if (tag === 'button' || tag === 'a' || target.getAttribute('role') === 'button') {
                                if (target.dataset.bhState === 'installed') {
                                    ev.preventDefault();
                                    ev.stopPropagation();
                                    return;
                                }
                                var txt = (target.innerText || target.textContent || '').trim().toLowerCase();
                                var href = target.getAttribute('href') || '';
                                if (href && (href.indexOf('.xpi') !== -1 || href.indexOf('.crx') !== -1 || href.indexOf('.nex') !== -1 || href.indexOf('.user.js') !== -1)) {
                                    ev.preventDefault();
                                    ev.stopPropagation();
                                    applyButtonState(target, 'installing');
                                    if (window.BrowserHubExtBridge) {
                                        window.BrowserHubExtBridge.requestStoreInstall(new URL(href, window.location.href).href);
                                    }
                                    return;
                                }
                                if (isCtaText(txt, target)) {
                                    ev.preventDefault();
                                    ev.stopPropagation();
                                    applyButtonState(target, 'installing');
                                    if (window.BrowserHubExtBridge) {
                                        window.BrowserHubExtBridge.requestStoreInstall(window.location.href);
                                    }
                                    return;
                                }
                            }
                            target = target.parentElement;
                        }
                    }, true);

                    // Watch SPA DOM mutations (Chrome Web Store, Edge Add-ons, AMO)
                    var syncTimer = null;
                    var scheduleSync = function() {
                        if (syncTimer) clearTimeout(syncTimer);
                        syncTimer = setTimeout(function() {
                            window.__browserHubSyncStoreButtons(false);
                        }, 150);
                    };
                    if (window.MutationObserver && document.documentElement) {
                        var obs = new MutationObserver(scheduleSync);
                        obs.observe(document.documentElement, { childList: true, subtree: true });
                    }
                    window.addEventListener('popstate', scheduleSync);
                    window.addEventListener('hashchange', scheduleSync);
                }

                window.__browserHubSyncStoreButtons(false);
            })();
        """.trimIndent()
    }
}
