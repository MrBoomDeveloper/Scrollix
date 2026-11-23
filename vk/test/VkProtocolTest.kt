package com.mrboomdev.scrollix.data.vk.network

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VkProtocolTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `peer ids encode the three VK conversation kinds`() {
        assertEquals(1234L, userPeerId(1234))
        assertEquals(-1234L, groupPeerId(1234))
        assertEquals(2_000_000_042L, chatPeerId(42))

        assertEquals(1234L, peerUserId(1234))
        assertEquals(-1234L, groupPeerId(1234))
        assertEquals(1234L, peerGroupId(-1234))
        assertEquals(42L, peerChatId(2_000_000_042L))

        assertNull(peerUserId(-5))
        assertNull(peerGroupId(5))
        assertNull(peerChatId(5))
    }

    @Test
    fun `form encoding matches application x-www-form-urlencoded`() {
        assertEquals("a=1&b=hello+world", encodeForm(mapOf("a" to "1", "b" to "hello world")))
        assertEquals("q=%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", encodeForm(mapOf("q" to "привет")))
        assertEquals("msg=a%26b%3Dc", encodeForm(mapOf("msg" to "a&b=c")))
    }

    @Test
    fun `long poll page keeps ts and updates`() {
        val page = parseLongPollPage(
            """{"ts":"1234","updates":[[4,55,1,42,1700000000,"hi"],[6,1]]}""",
            json
        )

        assertEquals("1234", page?.ts)
        assertNull(page?.failed)
        assertEquals(2, page?.updates?.size)
        assertEquals(4L, page?.updates?.first()?.first()?.asLongOrNull())
    }

    @Test
    fun `long poll failure carries the failed code`() {
        val page = parseLongPollPage("""{"failed":2}""", json)

        assertEquals(2, page?.failed)
        assertNull(page?.ts)
        assertTrue(page?.updates?.isEmpty() == true)
    }

    @Test
    fun `long poll url uses the returned server and key`() {
        val url = longPollUrl("lp.vk.com/wh123", "abc", "999", waitSeconds = 25)

        assertEquals(
            "https://lp.vk.com/wh123?act=a_check&key=abc&ts=999&wait=25&mode=2&version=3",
            url
        )
    }
}
