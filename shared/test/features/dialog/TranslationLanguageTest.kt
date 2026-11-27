package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationLanguageTest {
    @Test
    fun `an explicit choice wins over the device language`() {
        assertEquals("ru", translationTargetLanguage(configured = "ru", deviceLanguage = "de-DE"))
    }

    @Test
    fun `a missing or malformed choice follows the device language`() {
        assertEquals("de", translationTargetLanguage(configured = null, deviceLanguage = "de-DE"))
        assertEquals("de", translationTargetLanguage("not a code", "de-DE"))
        assertEquals("en", translationTargetLanguage(null, null))
    }

    @Test
    fun `a stored choice is normalized or rejected`() {
        assertEquals("pt", normalizeTranslationLanguage(" PT_br "))
        assertNull(normalizeTranslationLanguage(""))
        assertNull(normalizeTranslationLanguage("qwerty"))
    }

    @Test
    fun `labels use the catalog and fall back to the code`() {
        assertEquals("Русский", translationLanguageLabel("ru"))
        assertEquals("QW", translationLanguageLabel("qw"))
    }

    @Test
    fun `only untranslated messages with text are queued`() {
        val messages = listOf(
            message(id = 1, text = "hello"),
            message(id = 2, text = "service", isService = true),
            message(id = 3, text = "   "),
            message(id = 4, text = "already translated"),
            message(id = 1, text = "duplicate")
        )

        assertEquals(
            listOf(1L),
            messagesToTranslate(messages, translated = setOf(4L)).map { it.messageId }
        )
    }

    @Test
    fun `progress fraction stays in range`() {
        assertEquals(0f, TranslationProgress(done = 0, total = 0).fraction)
        assertEquals(0.5f, TranslationProgress(done = 1, total = 2).fraction)
        assertEquals(1f, TranslationProgress(done = 5, total = 2).fraction)
    }

    private fun message(
        id: Long,
        text: String,
        isService: Boolean = false
    ): ChatMessage = ChatMessage(
        providerId = "telegram",
        accountId = "1",
        providerDialogId = "2",
        messageId = id,
        senderId = 3,
        text = text,
        timestamp = 0,
        isOutgoing = false,
        isService = isService
    )
}
