package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.MessageTextBlock
import com.mrboomdev.scrollix.data.model.MessageTextFormat

import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeBlockQuote
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeItalic
import dev.g000sha256.tdl.dto.TextEntityTypeSpoiler
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextFormatMappingTest {
    @Test
    fun `incoming entities become formatting ranges`() {
        val text = FormattedText(
            text = "hello world",
            entities = arrayOf(
                TextEntity(0, 5, TextEntityTypeBold()),
                TextEntity(6, 5, TextEntityTypeItalic()),
                TextEntity(0, 11, TextEntityTypeBlockQuote()),
                TextEntity(6, 5, TextEntityTypeTextUrl(url = "https://example.com")),
                TextEntity(0, 5, TextEntityTypeSpoiler())
            )
        )

        val formatting = textFormats(text)

        assertEquals(5, formatting.size)
        assertTrue(formatting.any { span -> span.bold && span.start == 0 && span.end == 5 })
        assertTrue(formatting.any { span -> span.italic && span.start == 6 && span.end == 11 })
        assertTrue(formatting.any { span -> span.block == MessageTextBlock.Quote })
        assertTrue(formatting.any { span -> span.url == "https://example.com" })
        assertTrue(formatting.any { span -> span.spoiler && span.end == 5 })
    }

    @Test
    fun `outgoing entities move to the trimmed text`() {
        val entities = formattingEntities(
            text = "  bold text  ",
            formatting = listOf(MessageTextFormat(start = 2, end = 6, bold = true))
        )

        assertEquals(1, entities.size)
        assertEquals(0, entities.single().offset)
        assertEquals(4, entities.single().length)
        assertTrue(entities.single().type is TextEntityTypeBold)
    }

    @Test
    fun `one range can produce several entities`() {
        val entities = formattingEntities(
            text = "code",
            formatting = listOf(
                MessageTextFormat(
                    start = 0,
                    end = 4,
                    bold = true,
                    block = MessageTextBlock.Code,
                    url = "https://example.com"
                )
            )
        )

        assertEquals(3, entities.size)
        assertTrue(entities.any { entity -> entity.type is TextEntityTypeBold })
        assertTrue(entities.any { entity -> entity.type is TextEntityTypeTextUrl })
        assertTrue(
            entities.filter { entity -> entity.type !is TextEntityTypeBold }
                .all { entity -> entity.offset == 0 && entity.length == 4 }
        )
    }

    @Test
    fun `draft entities keep the offsets of the stored text`() {
        val entities = formattingEntities(
            text = "  bold  ",
            formatting = listOf(MessageTextFormat(start = 2, end = 6, bold = true)),
            trim = false
        )

        assertEquals(2, entities.single().offset)
        assertEquals(4, entities.single().length)
    }

    @Test
    fun `ranges outside the sent text are dropped`() {
        val entities = formattingEntities(
            text = "text",
            formatting = listOf(
                MessageTextFormat(start = 4, end = 10, bold = true),
                MessageTextFormat(start = 0, end = 2, url = "  ")
            )
        )

        assertTrue(entities.isEmpty())
    }
}
