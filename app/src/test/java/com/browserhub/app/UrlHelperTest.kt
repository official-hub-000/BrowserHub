package com.browserhub.app

import com.browserhub.app.data.model.SearchEngine
import com.browserhub.app.data.model.UrlHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlHelperTest {

    @Test
    fun normalizeInput_preservesExplicitHttps() {
        val result = UrlHelper.normalizeInputToUrl("https://github.com", SearchEngine.GOOGLE)
        assertEquals("https://github.com", result)
    }

    @Test
    fun normalizeInput_prependsHttpsForBareDomain() {
        val result = UrlHelper.normalizeInputToUrl("developer.android.com/compose", SearchEngine.GOOGLE)
        assertEquals("https://developer.android.com/compose", result)
    }

    @Test
    fun normalizeInput_usesSelectedSearchEngineForQueries() {
        val result = UrlHelper.normalizeInputToUrl("jetpack compose webview", SearchEngine.DUCKDUCKGO)
        assertTrue(result.startsWith("https://duckduckgo.com/?q="))
    }

    @Test
    fun extractHost_stripsSchemeAndWww() {
        assertEquals("github.com", UrlHelper.extractHost("https://www.github.com/official-hub-000"))
        assertTrue(UrlHelper.isSecureUrl("https://github.com"))
        assertFalse(UrlHelper.isSecureUrl("http://localhost:3000"))
    }
}
