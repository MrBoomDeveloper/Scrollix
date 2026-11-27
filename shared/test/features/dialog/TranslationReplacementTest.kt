package com.mrboomdev.scrollix.features.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationReplacementTest {
    @Test
    fun `a shown translation replaces the original text`() {
        assertEquals(
            "Hello",
            messageTranslationToShow(translation = "Hello", isTranslationHidden = false)
        )
    }

    @Test
    fun `a hidden translation leaves the original in place`() {
        assertNull(
            messageTranslationToShow(translation = "Hello", isTranslationHidden = true)
        )
    }

    @Test
    fun `no translation or a blank one keeps the original`() {
        assertNull(messageTranslationToShow(translation = null, isTranslationHidden = false))
        assertNull(messageTranslationToShow(translation = "   ", isTranslationHidden = false))
    }
}
