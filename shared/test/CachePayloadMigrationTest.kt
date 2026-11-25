package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.MessageSendState

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachePayloadMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `legacy unsupported text becomes a service row and avatars are dropped`() {
        val message = message(
            text = "This message is not supported by Scrollix yet",
            senderAvatar = Avatar(initials = "A", colorSeed = 1, image = byteArrayOf(1, 2))
        )

        val migrated = migrateCachedMessage(message)

        assertTrue(migrated.isService)
        assertNull(migrated.senderAvatar?.image)
    }

    @Test
    fun `a normal cached message keeps its text and service flag`() {
        val message = message(text = "Hello")

        assertEquals(message, migrateCachedMessage(message))
    }

    @Test
    fun `older message payloads decode with the current defaults`() {
        val decoded = json.decodeFromString<ChatMessage>(
            """
            {
              "providerId":"telegram",
              "accountId":"a",
              "providerDialogId":"1",
              "messageId":5,
              "senderId":2,
              "text":"hi",
              "timestamp":10,
              "isOutgoing":false
            }
            """.trimIndent()
        )

        assertEquals(MessageSendState.Sent, decoded.sendState)
        assertEquals(emptyList(), decoded.mediaItems)
        assertEquals(emptyList(), decoded.keyboard)
        assertTrue(decoded.canBeForwarded)
    }

    @Test
    fun `older dialog payloads decode with the current defaults`() {
        val decoded = json.decodeFromString<Dialog>(
            """
            {
              "providerId":"telegram",
              "accountId":"a",
              "providerDialogId":"1",
              "identity":{"providerId":"telegram","remoteId":"1"},
              "kind":"Direct",
              "title":"Alice",
              "preview":"hi",
              "timestamp":10
            }
            """.trimIndent()
        )

        assertEquals(false, decoded.hasMainWebApp)
        assertEquals(false, decoded.isForum)
        assertTrue(decoded.canSendMessages)
        assertEquals(emptySet<Int>(), decoded.folderIds)
    }

    private fun message(
        text: String,
        senderAvatar: Avatar? = null
    ): ChatMessage {
        return ChatMessage(
            providerId = "telegram",
            accountId = "a",
            providerDialogId = "1",
            messageId = 1,
            senderId = 2,
            senderAvatar = senderAvatar,
            text = text,
            timestamp = 1,
            isOutgoing = false
        )
    }
}
