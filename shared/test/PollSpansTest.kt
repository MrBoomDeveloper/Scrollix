package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class PollSpansTest {
    @Test
    fun `insertion before a span shifts it right`() {
        val spans = listOf(CustomEmojiSpan(start = 3, end = 4, emojiId = 7))

        assertEquals(
            listOf(CustomEmojiSpan(start = 5, end = 6, emojiId = 7)),
            adjustCustomEmojiSpans(spans, oldText = "abcXdef", newText = "ZZabcXdef")
        )
    }

    @Test
    fun `deletion before a span shifts it left`() {
        val spans = listOf(CustomEmojiSpan(start = 3, end = 4, emojiId = 7))

        assertEquals(
            listOf(CustomEmojiSpan(start = 2, end = 3, emojiId = 7)),
            adjustCustomEmojiSpans(spans, oldText = "abcXdef", newText = "acXdef")
        )
    }

    @Test
    fun `deleting the emoji drops its span`() {
        val spans = listOf(CustomEmojiSpan(start = 3, end = 4, emojiId = 7))

        assertEquals(
            emptyList(),
            adjustCustomEmojiSpans(spans, oldText = "abcXdef", newText = "abcdef")
        )
    }
}
