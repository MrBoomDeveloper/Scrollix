package com.mrboomdev.scrollix.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TelegramMessageLinkTest {
    @Test
    fun `private links resolve through the internal supergroup id`() {
        val link = assertNotNull(
            parseTelegramMessageLink("https://t.me/c/1234567890/42?thread=7")
        )

        assertEquals("-1001234567890", link.providerDialogId)
        assertNull(link.username)
        assertEquals(42L, link.messageId)
    }

    @Test
    fun `private links without a message still open the chat`() {
        val link = assertNotNull(parseTelegramMessageLink("https://t.me/c/1234567890"))

        assertEquals("-1001234567890", link.providerDialogId)
        assertNull(link.messageId)
    }

    @Test
    fun `public message links keep the message id`() {
        val link = assertNotNull(parseTelegramMessageLink("https://t.me/news_channel/77"))

        assertEquals("news_channel", link.username)
        assertEquals(77L, link.messageId)
    }

    @Test
    fun `topic links use the last id as the message`() {
        val link = assertNotNull(parseTelegramMessageLink("https://t.me/news_channel/55/77"))

        assertEquals("news_channel", link.username)
        assertEquals(77L, link.messageId)
    }

    @Test
    fun `profile links and the telegram me host are accepted`() {
        val profile = assertNotNull(parseTelegramMessageLink("http://telegram.me/news_channel"))

        assertEquals("news_channel", profile.username)
        assertNull(profile.messageId)

        val message = assertNotNull(
            parseTelegramMessageLink("https://telegram.me/news_channel/9")
        )

        assertEquals(9L, message.messageId)
    }

    @Test
    fun `invites sticker sets and tg links are not message links`() {
        assertNull(parseTelegramMessageLink("https://t.me/+AbCdEfGh"))
        assertNull(parseTelegramMessageLink("https://t.me/joinchat/AbCdEfGh"))
        assertNull(parseTelegramMessageLink("https://t.me/addstickers/cats"))
        assertNull(parseTelegramMessageLink("https://t.me/addemoji/cats"))
        assertNull(parseTelegramMessageLink("tg://resolve?domain=news_channel"))
        assertNull(parseTelegramMessageLink("https://example.com/c/1/2"))
    }

    @Test
    fun `a private link without a numeric id is not classified`() {
        assertNull(parseTelegramMessageLink("https://t.me/c/notanumber/42"))
        assertNull(parseTelegramMessageLink("https://t.me/c/0/42"))
    }

    @Test
    fun `internal ids map to tidlib chat ids`() {
        assertEquals("-1001234567890", supergroupIdToDialogId(1234567890L))
        assertEquals("-1000000000001", supergroupIdToDialogId(1L))
    }
}
