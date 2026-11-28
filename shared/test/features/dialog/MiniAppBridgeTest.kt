package com.mrboomdev.scrollix.features.dialog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MiniAppBridgeTest {
    @Test
    fun shareMessageKeepsThePreparedIdAndCallbackToken() {
        val request = parseMiniAppShareRequest(
            """{"msg_id":"prepared-1","callback_id":"share_3"}"""
        )

        assertEquals("prepared-1", request?.messageId)
        assertEquals("share_3", request?.callbackId)
    }

    @Test
    fun shareMessageWithoutATokenCanStillBeHandled() {
        val request = parseMiniAppShareRequest("""{"msg_id":"prepared-1"}""")

        assertEquals("prepared-1", request?.messageId)
        assertEquals("", request?.callbackId)
    }

    @Test
    fun shareMessageWithoutAnIdIsIgnored() {
        assertNull(parseMiniAppShareRequest("""{"callback_id":"share_3"}"""))
        assertNull(parseMiniAppShareRequest("""{"msg_id":""}"""))
        assertNull(parseMiniAppShareRequest("not json"))
    }
}
