package com.mrboomdev.scrollix.features.dialog

import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationTest {
    @Test
    fun `region and script are dropped`() {
        assertEquals("ru", translationLanguageCode("ru-RU"))
        assertEquals("zh", translationLanguageCode("zh-Hans-CN"))
    }

    @Test
    fun `underscores and casing are normalized`() {
        assertEquals("pt", translationLanguageCode(" pt_BR "))
    }

    @Test
    fun `unknown locales fall back to english`() {
        assertEquals("en", translationLanguageCode(null))
        assertEquals("en", translationLanguageCode(""))
        assertEquals("en", translationLanguageCode("123"))
        assertEquals("en", translationLanguageCode("x"))
    }
}
