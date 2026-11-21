package com.browserhub.app

import com.browserhub.app.extensions.ExtensionSourceBrowser
import com.browserhub.app.extensions.ExtensionStoreDirectory
import com.browserhub.app.extensions.MatchPattern
import com.browserhub.app.extensions.WebExtensionPackageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WebExtensionEngineTest {

    private fun buildZipBytes(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun wrapAsCrx3(zipBytes: ByteArray): ByteArray {
        val header = byteArrayOf(0x0A, 0x00, 0x00, 0x00)
        val prefix = ByteBuffer.allocate(12 + header.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("Cr24".toByteArray(Charsets.US_ASCII))
            .putInt(3)
            .putInt(header.size)
            .put(header)
            .array()
        return prefix + zipBytes
    }

    @Test
    fun extractAndParseCrx3ExtensionPackage() {
        val zip = buildZipBytes(
            mapOf(
                "manifest.json" to """
                    {
                      "manifest_version": 3,
                      "name": "Sample Chrome Ext",
                      "version": "2.1.0",
                      "description": "Tests CRX3 parsing",
                      "permissions": ["storage"],
                      "action": { "default_popup": "popup.html" },
                      "options_ui": { "page": "options.html" },
                      "content_scripts": [
                        {
                          "matches": ["https://*.example.com/*"],
                          "js": ["content.js"],
                          "run_at": "document_end"
                        }
                      ]
                    }
                """.trimIndent(),
                "content.js" to "console.log('active');",
                "popup.html" to "<html><body>Hi</body></html>",
                "options.html" to "<html><body>Options</body></html>"
            )
        )
        val crx3 = wrapAsCrx3(zip)
        val tempDir: File = Files.createTempDirectory("ext_test").toFile()
        try {
            WebExtensionPackageParser.extractPackageBytes(crx3, tempDir)
            val parsed = WebExtensionPackageParser.parseManifest(tempDir)
            assertEquals("Sample Chrome Ext", parsed.name)
            assertEquals("2.1.0", parsed.version)
            assertEquals(3, parsed.manifestVersion)
            assertEquals("popup.html", parsed.popupPath)
            assertEquals("options.html", parsed.optionsPath)
            assertEquals(1, parsed.contentScripts.size)
            assertEquals("document_end", parsed.contentScripts.first().runAt)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun unpackUserScript_createsValidMv3Extension() {
        val script = """
            // ==UserScript==
            // @name         GitHub Enhancer
            // @version      1.4.2
            // @description  Enhances GitHub repo pages
            // @match        https://github.com/*
            // @run-at       document-end
            // ==/UserScript==
            console.log('enhanced');
        """.trimIndent()
        val tempDir: File = Files.createTempDirectory("us_test").toFile()
        try {
            WebExtensionPackageParser.unpackUserScriptToDirectory(script, tempDir)
            val parsed = WebExtensionPackageParser.parseManifest(tempDir)
            assertEquals("GitHub Enhancer", parsed.name)
            assertEquals("1.4.2", parsed.version)
            assertEquals("document_end", parsed.contentScripts.first().runAt)
            assertEquals(listOf("https://github.com/*"), parsed.contentScripts.first().matches)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun matchPattern_matchesWildcardsAndExclusions() {
        assertTrue(MatchPattern.matchesAny("https://docs.github.com/en/get-started", listOf("*://*.github.com/*")))
        assertTrue(MatchPattern.matchesAny("https://github.com/explore", listOf("*://*.github.com/*")))
        assertFalse(
            MatchPattern.matchesAny(
                "https://github.com/login",
                listOf("<all_urls>"),
                excludeMatches = listOf("https://github.com/login*")
            )
        )
    }

    @Test
    fun storeDetector_recognizesAllFourBrowserStoresAndPcUserAgents() {
        val chromeUrl = "https://chromewebstore.google.com/detail/dark-reader/eimadpbcbfnmbkopoojfekhnkhdbieeh"
        val chrome = ExtensionStoreDirectory.detectStoreExtension(chromeUrl)
        assertNotNull(chrome)
        assertEquals(ExtensionSourceBrowser.CHROME, chrome?.sourceBrowser)
        assertEquals("eimadpbcbfnmbkopoojfekhnkhdbieeh", chrome?.extensionId)
        assertEquals(ExtensionStoreDirectory.PC_CHROME_UA, ExtensionStoreDirectory.getStoreDesktopUserAgent(chromeUrl))

        val firefoxUrl = "https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/"
        val firefox = ExtensionStoreDirectory.detectStoreExtension(firefoxUrl)
        assertNotNull(firefox)
        assertEquals(ExtensionSourceBrowser.FIREFOX, firefox?.sourceBrowser)
        assertTrue(firefox?.downloadUrl?.endsWith(".xpi") == true)
        assertEquals(ExtensionStoreDirectory.PC_FIREFOX_UA, ExtensionStoreDirectory.getStoreDesktopUserAgent(firefoxUrl))

        val edgeUrl = "https://microsoftedge.microsoft.com/addons/detail/dark-reader/ifoakfbpdcdoeenechcleahebpibofpc"
        val edge = ExtensionStoreDirectory.detectStoreExtension(edgeUrl)
        assertNotNull(edge)
        assertEquals(ExtensionSourceBrowser.EDGE, edge?.sourceBrowser)
        assertEquals(ExtensionStoreDirectory.PC_EDGE_UA, ExtensionStoreDirectory.getStoreDesktopUserAgent(edgeUrl))

        val operaUrl = "https://addons.opera.com/en/extensions/details/dark-reader/"
        val opera = ExtensionStoreDirectory.detectStoreExtension(operaUrl)
        assertNotNull(opera)
        assertEquals(ExtensionSourceBrowser.OPERA, opera?.sourceBrowser)
        assertEquals(ExtensionStoreDirectory.PC_OPERA_UA, ExtensionStoreDirectory.getStoreDesktopUserAgent(operaUrl))

        assertNull(ExtensionStoreDirectory.getStoreDesktopUserAgent("https://example.com"))
    }

    @Test
    fun storeInstallShim_embedsInstalledIdsAndProvidesLiveUpdateHooks() {
        val chromeMatch = ExtensionStoreDirectory.detectStoreExtension(
            "https://chromewebstore.google.com/detail/dark-reader/eimadpbcbfnmbkopoojfekhnkhdbieeh"
        )!!
        val installed = setOf("eimadpbcbfnmbkopoojfekhnkhdbieeh", "ublock-origin")
        assertTrue(ExtensionStoreDirectory.isExtensionInstalled(chromeMatch, installed))
        assertFalse(ExtensionStoreDirectory.isExtensionInstalled(chromeMatch, emptySet()))

        val shimJs = ExtensionStoreDirectory.buildStorePageInstallShimJs(installed)
        assertTrue(shimJs.contains("eimadpbcbfnmbkopoojfekhnkhdbieeh"))
        assertTrue(shimJs.contains("Added to BrowserHub"))
        assertTrue(shimJs.contains("navigator, 'mozAddonManager'"))
        assertTrue(shimJs.contains("window.chrome.management"))

        val markJs = ExtensionStoreDirectory.buildMarkInstalledJs(
            extensionId = "ublock-origin",
            extensionName = "uBlock Origin",
            installedIds = installed
        )
        assertTrue(markJs.contains("window.__browserHubMarkInstalled(\"ublock-origin\", \"uBlock Origin\")"))
    }
}
