package com.fieldscanner.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `exactly the six approved languages are supported`() {
        assertEquals(
            listOf("ru", "pl", "be", "en", "uk", "he"),
            AppLanguage.values().map { it.code }
        )
    }

    @Test
    fun `default is russian`() {
        assertSame(AppLanguage.RUSSIAN, AppLanguage.DEFAULT)
    }

    @Test
    fun `fromCode round-trips every member and is case-insensitive`() {
        AppLanguage.values().forEach { language ->
            assertSame(language, AppLanguage.fromCode(language.code))
            assertSame(language, AppLanguage.fromCode(language.code.uppercase()))
        }
    }

    @Test
    fun `fromCode returns null for unknown or missing codes`() {
        assertNull(AppLanguage.fromCode(null))
        assertNull(AppLanguage.fromCode(""))
        assertNull(AppLanguage.fromCode("de"))
    }

    @Test
    fun `locale carries the region subtag and hebrew is right-to-left`() {
        val english = AppLanguage.ENGLISH.locale()
        assertEquals("en", english.language)
        assertEquals("US", english.country)

        // The JVM Locale constructor remaps "he" to the legacy "iw"; only assert the region.
        assertEquals("IL", AppLanguage.HEBREW.locale().country)
        assertEquals("he-IL", AppLanguage.HEBREW.bcp47)
        assertTrue(AppLanguage.HEBREW.isRtl)
    }

    @Test
    fun `prompt descriptor pairs the endonym with the bcp47 tag`() {
        assertEquals("English (en-US)", AppLanguage.ENGLISH.promptDescriptor)
    }
}
