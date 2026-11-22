package com.mrboomdev.scrollix.data.api

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaxCaptchaTest {

    @Test
    fun readsCaptchaLinkFromSessionResponse() {
        val response = buildJsonObject {
            put("link", "https://id.vk.ru/not_robot_captcha?domain=web.max.ru&session_token=abc&variant=popup")
        }

        assertEquals(
            "https://id.vk.ru/not_robot_captcha?domain=web.max.ru&session_token=abc&variant=popup",
            parseCaptchaLink(response)
        )
    }

    @Test
    fun ignoresResponseWithoutLink() {
        assertNull(parseCaptchaLink(buildJsonObject { put("something", "else") }))
        assertNull(parseCaptchaLink(null))
    }

    @Test
    fun ignoresNonHttpLink() {
        assertNull(parseCaptchaLink(buildJsonObject { put("link", "not a url") }))
    }
}
