package com.fairswap.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleHelperStateTest {

    @Test
    fun normalizeLanguage_validSupportedLocales_returnsNormalizedCode() {
        assertEquals("en", LocaleHelper.normalizeLanguage("EN"))
        assertEquals("hi", LocaleHelper.normalizeLanguage("HI"))
        assertEquals("hi", LocaleHelper.normalizeLanguage(" hi "))
    }

    @Test
    fun normalizeLanguage_unsupportedOrNullLocales_defaultsToEnglish() {
        assertEquals("en", LocaleHelper.normalizeLanguage("fr"))
        assertEquals("en", LocaleHelper.normalizeLanguage(null))
        assertEquals("en", LocaleHelper.normalizeLanguage(""))
    }

    @Test
    fun isSupportedLanguage_validatesSupportedLocalesOnly() {
        assertTrue(LocaleHelper.isSupportedLanguage("en"))
        assertTrue(LocaleHelper.isSupportedLanguage("hi"))
        assertFalse(LocaleHelper.isSupportedLanguage("es"))
    }
}
