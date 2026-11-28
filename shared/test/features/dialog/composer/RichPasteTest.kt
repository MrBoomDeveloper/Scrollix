package com.mrboomdev.scrollix.features.dialog.composer

import com.mrboomdev.scrollix.data.model.MessageTextBlock
import com.mrboomdev.scrollix.platform.clipboard.ClipboardRichText

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichPasteTest {
    @Test
    fun `markdown bold and italic are converted`() {
        val paste = assertNotNull(parseMarkdownPaste("say **bold** and *italic*"))

        assertEquals("say bold and italic", paste.text)
        assertTrue(paste.formatting.any { span ->
            span.bold && TextRange(span.start, span.end) == TextRange(4, 8)
        })
        assertTrue(paste.formatting.any { span ->
            span.italic && TextRange(span.start, span.end) == TextRange(13, 19)
        })
    }

    @Test
    fun `markdown code spoiler and strike are converted`() {
        val paste = assertNotNull(parseMarkdownPaste("`code` ||hidden|| ~~gone~~"))

        assertEquals("code hidden gone", paste.text)
        assertTrue(paste.formatting.any { span -> span.monospace })
        assertTrue(paste.formatting.any { span -> span.spoiler })
        assertTrue(paste.formatting.any { span -> span.strikethrough })
    }

    @Test
    fun `markdown link keeps the label as the text`() {
        val paste = assertNotNull(parseMarkdownPaste("see [the site](https://example.com)"))

        assertEquals("see the site", paste.text)
        assertEquals("https://example.com", paste.formatting.single().url)
        assertEquals(TextRange(4, 12), TextRange(
            paste.formatting.single().start,
            paste.formatting.single().end
        ))
    }

    @Test
    fun `markdown quote and fenced code become blocks`() {
        val quote = assertNotNull(parseMarkdownPaste("> first\n> second"))

        assertEquals("first\nsecond", quote.text)
        assertTrue(quote.formatting.any { span -> span.block == MessageTextBlock.Quote })

        val code = assertNotNull(parseMarkdownPaste("```kotlin\nval x = 1\nval y = 2\n```"))

        assertEquals("val x = 1\nval y = 2", code.text)
        assertTrue(code.formatting.all { span -> span.block == MessageTextBlock.Code })
    }

    @Test
    fun `plain and identifier text is not converted`() {
        assertNull(parseMarkdownPaste("just some text"))
        assertNull(parseMarkdownPaste("snake_case_name"))
        assertNull(parseMarkdownPaste("2*3*4"))
    }

    @Test
    fun `html styles become ranges`() {
        val paste = assertNotNull(
            parseHtmlPaste("<b>bold</b> and <i>it</i> and <u>under</u>")
        )

        assertEquals("bold and it and under", paste.text)
        assertTrue(paste.formatting.any { span -> span.bold && span.end == 4 })
        assertTrue(paste.formatting.any { span -> span.italic })
        assertTrue(paste.formatting.any { span -> span.underline })
    }

    @Test
    fun `html blocks and links are converted`() {
        val paste = assertNotNull(
            parseHtmlPaste("<p>One</p><p>Two <a href=\"https://example.com\">site</a></p>")
        )

        assertEquals("One\nTwo site", paste.text)
        assertEquals("https://example.com", paste.formatting.single { span -> span.url != null }.url)
    }

    @Test
    fun `html entities and skipped scripts are handled`() {
        val paste = assertNotNull(
            parseHtmlPaste("<script>alert(1)</script><div>a &amp; b<br>c</div>")
        )

        assertEquals("a & b\nc", paste.text)
        assertTrue(paste.formatting.isEmpty())
    }

    @Test
    fun `html numeric entities decode including emoji`() {
        val paste = assertNotNull(parseHtmlPaste("<p>&#65;&#x1F600;</p>"))

        assertEquals("A\uD83D\uDE00", paste.text)
    }

    @Test
    fun `html nested formatting and links keep their ranges`() {
        val paste = assertNotNull(
            parseHtmlPaste("<p><b>bold <a href=\"https://example.com\">link</a></b></p>")
        )

        assertEquals("bold link", paste.text)
        assertTrue(paste.formatting.any { span ->
            span.bold && TextRange(span.start, span.end) == TextRange(0, 9)
        })
        assertTrue(paste.formatting.any { span ->
            span.url == "https://example.com" && TextRange(span.start, span.end) == TextRange(5, 9)
        })
    }

    @Test
    fun `html pre keeps its whitespace`() {
        val paste = assertNotNull(parseHtmlPaste("<pre>line1\n  line2</pre>"))

        assertEquals("line1\n  line2", paste.text)
        assertEquals(MessageTextBlock.Code, paste.formatting.single().block)
    }

    @Test
    fun `plain html text is not converted`() {
        assertNull(parseHtmlPaste("hello"))
    }

    @Test
    fun `paste conversion takes the inserted range`() {
        val replacement = assertNotNull(
            conversionForPaste(
                oldText = "hello world",
                newText = "hello **there** world",
                clipboard = { null }
            )
        )

        // The inserted chunk includes the separating space, which survives.
        assertEquals(TextRange(6, 16), TextRange(replacement.start, replacement.end))
        assertEquals("there ", replacement.paste.text)
        assertEquals(0, replacement.paste.formatting.single().start)
        assertEquals(5, replacement.paste.formatting.single().end)
        assertTrue(replacement.paste.formatting.single().bold)
    }

    @Test
    fun `clipboard html is used when the plain text matches`() {
        val replacement = assertNotNull(
            conversionForPaste(
                oldText = "",
                newText = "bold text",
                clipboard = {
                    ClipboardRichText(
                        plain = "bold text",
                        html = "<b>bold</b> text"
                    )
                }
            )
        )

        assertEquals("bold text", replacement.paste.text)
        assertEquals(TextRange(0, 4), TextRange(
            replacement.paste.formatting.single().start,
            replacement.paste.formatting.single().end
        ))
    }

    @Test
    fun `plain insertion is left alone`() {
        assertNull(
            conversionForPaste(
                oldText = "hello ",
                newText = "hello world",
                clipboard = { null }
            )
        )

        assertNull(
            conversionForPaste(
                oldText = "",
                newText = "hi",
                clipboard = { null }
            )
        )
    }
}
