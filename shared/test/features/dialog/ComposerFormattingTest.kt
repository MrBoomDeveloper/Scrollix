package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageTextBlock
import com.mrboomdev.scrollix.data.model.MessageTextFormat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerFormattingTest {
    @Test
    fun `toggle applies and removes one style`() {
        val selection = TextRange(0, 4)
        val formatted = emptyList<MessageTextFormat>().toggleFormatting(
            selection = selection,
            style = ComposerTextFormat.Bold
        )

        assertEquals(1, formatted.size)
        assertTrue(formatted.single().bold)
        assertEquals(0, formatted.single().start)
        assertEquals(4, formatted.single().end)

        val removed = formatted.toggleFormatting(
            selection = selection,
            style = ComposerTextFormat.Bold
        )

        assertTrue(removed.isEmpty())
    }

    @Test
    fun `toggle inside an existing span splits it`() {
        val existing = listOf(MessageTextFormat(start = 0, end = 10, bold = true))

        val result = existing.toggleFormatting(
            selection = TextRange(3, 6),
            style = ComposerTextFormat.Bold
        )

        assertEquals(2, result.size)
        assertEquals(TextRange(0, 3), TextRange(result[0].start, result[0].end))
        assertEquals(TextRange(6, 10), TextRange(result[1].start, result[1].end))
        assertTrue(result.all { span -> span.bold })
    }

    @Test
    fun `toggle only covers the missing part`() {
        val existing = listOf(MessageTextFormat(start = 0, end = 5, italic = true))

        val partial = existing.toggleFormatting(
            selection = TextRange(0, 10),
            style = ComposerTextFormat.Italic
        )

        // A half-styled selection gains the style on the missing half only.
        assertEquals(1, partial.size)
        assertEquals(0, partial.single().start)
        assertEquals(10, partial.single().end)
        assertTrue(partial.single().italic)
        assertFalse(partial.single().bold)
    }

    @Test
    fun `toggle quote uses a block style`() {
        val result = emptyList<MessageTextFormat>().toggleFormatting(
            selection = TextRange(2, 8),
            style = ComposerTextFormat.Quote
        )

        assertEquals(MessageTextBlock.Quote, result.single().block)
        assertTrue(result.isStyleActive(TextRange(2, 8), ComposerTextFormat.Quote))
    }

    @Test
    fun `link applies to the selection and can be removed`() {
        val linked = emptyList<MessageTextFormat>().applyLink(
            selection = TextRange(1, 5),
            url = " https://example.com "
        )

        assertEquals("https://example.com", linked.single().url)
        assertEquals("https://example.com", linked.linkAt(TextRange(1, 5)))

        val unlinked = linked.applyLink(selection = TextRange(1, 5), url = "")

        assertTrue(unlinked.isEmpty())
    }

    @Test
    fun `typing inside a formatted range keeps the style`() {
        val formatting = listOf(MessageTextFormat(start = 2, end = 5, bold = true))

        val result = formatting.adjustFormatting(
            oldText = "abcdef",
            newText = "abcXdef"
        )

        assertEquals(TextRange(2, 6), TextRange(result.single().start, result.single().end))
    }

    @Test
    fun `typing after a formatted range does not extend it`() {
        val formatting = listOf(MessageTextFormat(start = 2, end = 6, bold = true))

        val result = formatting.adjustFormatting(
            oldText = "hello world",
            newText = "hello there world"
        )

        assertEquals(TextRange(2, 6), TextRange(result.single().start, result.single().end))
    }

    @Test
    fun `typing before a formatted range shifts it`() {
        val formatting = listOf(MessageTextFormat(start = 2, end = 6, bold = true))

        val result = formatting.adjustFormatting(
            oldText = "hello",
            newText = "say hello"
        )

        assertEquals(TextRange(6, 10), TextRange(result.single().start, result.single().end))
    }

    @Test
    fun `deleting inside a formatted range shrinks it`() {
        val formatting = listOf(MessageTextFormat(start = 0, end = 10, bold = true))

        val result = formatting.adjustFormatting(
            oldText = "0123456789",
            newText = "012789"
        )

        assertEquals(TextRange(0, 6), TextRange(result.single().start, result.single().end))
    }

    @Test
    fun `style activity follows the caret and the whole selection`() {
        val formatting = listOf(MessageTextFormat(start = 3, end = 8, italic = true))

        assertTrue(formatting.isStyleActive(TextRange(5), ComposerTextFormat.Italic))
        assertFalse(formatting.isStyleActive(TextRange(1), ComposerTextFormat.Italic))
        assertTrue(formatting.isStyleActive(TextRange(3, 8), ComposerTextFormat.Italic))
        assertFalse(formatting.isStyleActive(TextRange(2, 8), ComposerTextFormat.Italic))
    }

    @Test
    fun `composer text carries the formatting ranges`() {
        val formatting = listOf(
            MessageTextFormat(start = 0, end = 4, bold = true),
            MessageTextFormat(start = 5, end = 9, monospace = true)
        )

        val text = formatting.toComposerText("bold code", linkColor = Color.Blue)

        assertEquals("bold code", text.text)
        assertEquals(2, text.spanStyles.size)
        assertEquals(TextRange(0, 4), text.spanStyles[0].item.let { span ->
            TextRange(text.spanStyles[0].start, text.spanStyles[0].end)
        })
        assertEquals(
            TextRange(5, 9),
            TextRange(text.spanStyles[1].start, text.spanStyles[1].end)
        )
    }

    @Test
    fun `hidden spoiler is drawn as a block`() {
        val style = MessageTextFormat(start = 0, end = 3, spoiler = true)
            .spanStyle(spoilerHidden = true)

        assertEquals(Color.Transparent, style.color)
    }
}
