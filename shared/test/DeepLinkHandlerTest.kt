package com.mrboomdev.scrollix

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.random.Random

class DeepLinkHandlerTest {
    @AfterTest
    fun cleanup() {
        DeepLinkHandler.consume()
    }

    @Test
    fun `re-publishing the same link produces a new request`() {
        DeepLinkHandler.consume()
        DeepLinkHandler.publish("https://t.me/addstickers/cats")

        val first = assertNotNull(DeepLinkHandler.pending.value)
        DeepLinkHandler.publish("https://t.me/addstickers/cats")

        val second = assertNotNull(DeepLinkHandler.pending.value)

        assertNotEquals(first.id, second.id)
        assertEquals(first.url, second.url)
    }

    @Test
    fun `accepts telegram and http links and ignores other schemes`() {
        assertTrue(DeepLinkHandler.isTelegramLink("tg://addstickers?set=cats"))
        assertTrue(DeepLinkHandler.isTelegramLink("https://t.me/addemoji/cats"))
        assertFalse(DeepLinkHandler.isTelegramLink("not a link"))

        DeepLinkHandler.consume()
        DeepLinkHandler.publish("mailto:someone@example.com")
        assertNull(DeepLinkHandler.pending.value)

        DeepLinkHandler.publish("https://example.com/article")
        assertEquals(
            "https://example.com/article",
            DeepLinkHandler.pending.value?.url
        )
    }

    @Test
    fun `browser fallback is not repeated for the same url`() {
        val url = "https://t.me/unhandled-${DeepLinkHandler.hashCode()}-${Random.nextLong()}"

        assertTrue(DeepLinkHandler.allowBrowserFallback(url))
        assertFalse(DeepLinkHandler.allowBrowserFallback(url))
    }
}
