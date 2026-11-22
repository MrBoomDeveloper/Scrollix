package com.mrboomdev.scrollix.data.model

import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeCustomEmoji
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderNameParserTest {
    @Test
    fun `parses custom emoji entities with utf16 offsets`() {
        val text = "Hi 😀!"
        val result = parseFolderCustomEmojis(
            text = text,
            entities = listOf(
                entity(offset = 3, length = 2, customEmojiId = 42)
            )
        )

        assertEquals(
            listOf(
                DialogFolderEmoji(
                    customEmojiId = 42,
                    offset = 3,
                    length = 2,
                    fallback = "😀"
                )
            ),
            result
        )
    }

    @Test
    fun `ignores invalid and overlapping entities`() {
        val text = "AB😀"
        val result = parseFolderCustomEmojis(
            text = text,
            entities = listOf(
                entity(offset = 0, length = 2, customEmojiId = 1),
                entity(offset = 1, length = 3, customEmojiId = 2),
                entity(offset = 10, length = 1, customEmojiId = 3)
            )
        )

        assertEquals(listOf(1L), result.map { it.customEmojiId })
    }

    @Test
    fun `sorts entities by offset`() {
        val text = "ABCD"
        val result = parseFolderCustomEmojis(
            text = text,
            entities = listOf(
                entity(offset = 2, length = 1, customEmojiId = 2),
                entity(offset = 0, length = 1, customEmojiId = 1)
            )
        )

        assertEquals(
            listOf(1L, 2L),
            result.map { it.customEmojiId }
        )
        assertEquals(
            listOf("A", "C"),
            result.map { it.fallback }
        )
    }

    @Test
    fun `ignores non custom emoji entities`() {
        val result = parseFolderCustomEmojis(
            text = "Example",
            entities = listOf(
                TextEntity(
                    offset = 0,
                    length = 7,
                    type = TextEntityTypeTextUrl(url = "https://example.com")
                )
            )
        )

        assertTrue(result.isEmpty())
    }

    private fun entity(
        offset: Int,
        length: Int,
        customEmojiId: Long
    ): TextEntity {
        return TextEntity(
            offset = offset,
            length = length,
            type = TextEntityTypeCustomEmoji(customEmojiId = customEmojiId)
        )
    }
}
