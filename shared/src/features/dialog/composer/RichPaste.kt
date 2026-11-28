package com.mrboomdev.scrollix.features.dialog.composer

import com.mrboomdev.scrollix.data.model.MessageTextBlock
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.platform.clipboard.ClipboardRichText

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * A pasted chunk after its markup was converted: [text] is what should land in
 * the composer and [formatting] describes it, with offsets starting at zero.
 */
internal data class ConvertedPaste(
    val text: String,
    val formatting: List<MessageTextFormat>
)

/** The range of the editor text a paste replaces, and its converted form. */
internal data class PasteReplacement(
    val start: Int,
    val end: Int,
    val paste: ConvertedPaste
)

/** Pastes longer than this are inserted verbatim to keep the composer usable. */
private const val MaxConvertedPasteLength = 100_000

/**
 * Turns an insertion into a converted paste when it carries markup. Markdown is
 * tried on the inserted text, then the clipboard's HTML. A plain insertion
 * returns null, so normal typing and pasting are untouched.
 */
internal fun conversionForPaste(
    oldText: String,
    newText: String,
    /** Read lazily, because most insertions are ordinary typing. */
    clipboard: () -> ClipboardRichText?
): PasteReplacement? {
    if (newText.length <= oldText.length) return null

    var prefix = 0
    while (
        prefix < oldText.length &&
        prefix < newText.length &&
        oldText[prefix] == newText[prefix]
    ) {
        prefix++
    }

    var suffix = 0
    while (
        suffix < oldText.length - prefix &&
        suffix < newText.length - prefix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) {
        suffix++
    }

    val end = newText.length - suffix
    val inserted = newText.substring(prefix, end)

    if (inserted.length < 3 || inserted.length > MaxConvertedPasteLength) return null

    // Markdown is tried first: it needs no clipboard access, so ordinary
    // multi-character input never touches the system clipboard.
    val converted = parseMarkdownPaste(inserted) ?: run {
        val richClipboard = clipboard() ?: return null
        val html = richClipboard.html ?: return null

        if (normalizeClipboardText(richClipboard.plain) != normalizeClipboardText(inserted)) {
            return null
        }

        parseHtmlPaste(html) ?: return null
    }

    if (converted.formatting.isEmpty() && converted.text == inserted) return null

    return PasteReplacement(start = prefix, end = end, paste = converted)
}

private fun normalizeClipboardText(value: String): String {
    return value.replace("\r\n", "\n").trim()
}

// ---------------------------------------------------------------- markdown --

private class MarkdownRule(
    val open: String,
    val close: String,
    val format: (Int, Int) -> MessageTextFormat
)

private val MarkdownRules = listOf(
    MarkdownRule("**", "**") { start, end -> MessageTextFormat(start, end, bold = true) },
    MarkdownRule("__", "__") { start, end -> MessageTextFormat(start, end, bold = true) },
    MarkdownRule("~~", "~~") { start, end -> MessageTextFormat(start, end, strikethrough = true) },
    MarkdownRule("||", "||") { start, end -> MessageTextFormat(start, end, spoiler = true) },
    MarkdownRule("`", "`") { start, end -> MessageTextFormat(start, end, monospace = true) },
    MarkdownRule("*", "*") { start, end -> MessageTextFormat(start, end, italic = true) },
    MarkdownRule("_", "_") { start, end -> MessageTextFormat(start, end, italic = true) }
)

/** Converts Markdown-plain text into text plus ranges, or null for plain text. */
internal fun parseMarkdownPaste(text: String): ConvertedPaste? {
    val output = StringBuilder()
    val formatting = mutableListOf<MessageTextFormat>()
    val lines = text.split('\n')

    var index = 0

    while (index < lines.size) {
        val line = lines[index]

        if (line.trimStart().startsWith("```")) {
            var closing = index + 1
            val body = mutableListOf<String>()

            while (closing < lines.size && !lines[closing].trim().startsWith("```")) {
                body.add(lines[closing])
                closing++
            }

            if (closing < lines.size) {
                val start = output.length
                body.forEachIndexed { bodyIndex, bodyLine ->
                    if (bodyIndex > 0) output.append('\n')
                    output.append(bodyLine)
                }

                if (output.length > start) {
                    formatting.add(
                        MessageTextFormat(start, output.length, block = MessageTextBlock.Code)
                    )
                }

                index = closing + 1

                if (index < lines.size) {
                    output.append('\n')
                }

                continue
            }
        }

        if (line.trimStart().startsWith(">")) {
            var closing = index
            val quoteStart = output.length

            while (closing < lines.size && lines[closing].trimStart().startsWith(">")) {
                if (closing > index) output.append('\n')

                val content = lines[closing]
                    .trimStart()
                    .removePrefix(">")
                    .removePrefix(" ")

                appendInlineMarkdown(content, output, formatting)
                closing++
            }

            if (output.length > quoteStart) {
                formatting.add(
                    MessageTextFormat(quoteStart, output.length, block = MessageTextBlock.Quote)
                )
            }

            index = closing

            if (index < lines.size) {
                output.append('\n')
            }

            continue
        }

        appendInlineMarkdown(line, output, formatting)
        index++

        if (index < lines.size) {
            output.append('\n')
        }
    }

    val converted = output.toString()
    if (formatting.isEmpty()) return null

    return ConvertedPaste(text = converted, formatting = formatting.mergedFormatting())
}

private fun appendInlineMarkdown(
    text: String,
    output: StringBuilder,
    formatting: MutableList<MessageTextFormat>,
    depth: Int = 0
) {
    var index = 0

    while (index < text.length) {
        // A backslash escapes the next character, so "\*" stays a literal star.
        if (text[index] == '\\' && index + 1 < text.length) {
            output.append(text[index + 1])
            index += 2
            continue
        }

        if (text[index] == '[') {
            val consumed = appendMarkdownLink(text, index, output, formatting, depth)

            if (consumed > 0) {
                index += consumed
                continue
            }
        }

        val rule = MarkdownRules.firstOrNull { candidate ->
            text.startsWith(candidate.open, index) &&
                isRuleFlanked(text, index, candidate)
        }

        if (rule != null && depth < 8) {
            val contentStart = index + rule.open.length
            val closeIndex = text.indexOf(rule.close, contentStart)

            if (closeIndex > contentStart) {
                val innerStart = output.length

                appendInlineMarkdown(
                    text = text.substring(contentStart, closeIndex),
                    output = output,
                    formatting = formatting,
                    depth = depth + 1
                )

                val innerEnd = output.length

                if (innerEnd > innerStart) {
                    formatting.add(rule.format(innerStart, innerEnd))
                    index = closeIndex + rule.close.length
                    continue
                }
            }
        }

        output.append(text[index])
        index++
    }
}

/** Consumes a `[label](url)` link; returns the characters consumed, or 0. */
private fun appendMarkdownLink(
    text: String,
    index: Int,
    output: StringBuilder,
    formatting: MutableList<MessageTextFormat>,
    depth: Int
): Int {
    val labelEnd = text.indexOf(']', index + 1)
    if (labelEnd <= index + 1 || text.getOrNull(labelEnd + 1) != '(') return 0

    val urlEnd = text.indexOf(')', labelEnd + 2)
    if (urlEnd <= labelEnd + 2) return 0

    val url = text.substring(labelEnd + 2, urlEnd).trim()
    if (url.isEmpty()) return 0

    val start = output.length

    appendInlineMarkdown(
        text = text.substring(index + 1, labelEnd),
        output = output,
        formatting = formatting,
        depth = depth + 1
    )

    val end = output.length
    if (end <= start) return 0

    formatting.add(MessageTextFormat(start, end, url = url))
    return urlEnd + 1 - index
}

/**
 * Inline markers must touch their content and stand apart from words, so
 * `snake_case` or `2*3*4` are not converted by accident.
 */
private fun isRuleFlanked(text: String, index: Int, rule: MarkdownRule): Boolean {
    if (rule.open.length > 1) return true

    val before = text.getOrNull(index - 1)
    val first = text.getOrNull(index + rule.open.length)
    val closeIndex = text.indexOf(rule.close, index + rule.open.length)

    if (closeIndex <= index + rule.open.length) return false

    val last = text.getOrNull(closeIndex - 1)
    val after = text.getOrNull(closeIndex + 1)

    if (first == null || first.isWhitespace()) return false
    if (last == null || last.isWhitespace()) return false
    if (before != null && before.isLetterOrDigit()) return false
    if (after != null && after.isLetterOrDigit()) return false

    return true
}

// -------------------------------------------------------------------- html --

private val BlockHtmlTags = setOf(
    "p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6",
    "blockquote", "pre", "tr", "table", "section", "article", "header", "footer"
)

private val SkippedHtmlTags = setOf("script", "style", "head", "title", "svg", "math")

/** The inline styles that apply to a subtree while the document is walked. */
private data class HtmlStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val quote: Boolean = false,
    val preformatted: Boolean = false,
    val link: String? = null
)

/** Converts copied HTML into text plus ranges, or null for unstyled text. */
internal fun parseHtmlPaste(html: String): ConvertedPaste? {
    val output = StringBuilder()
    val formatting = mutableListOf<MessageTextFormat>()

    fun breakLine() {
        while (output.isNotEmpty() && output.last() == ' ') {
            output.setLength(output.length - 1)
        }

        if (output.isNotEmpty() && output.last() != '\n') {
            output.append('\n')
        }
    }

    fun appendText(value: String, style: HtmlStyle) {
        if (value.isEmpty()) return

        val start = output.length
        output.append(value)
        val end = output.length

        if (style.bold) formatting.add(MessageTextFormat(start, end, bold = true))
        if (style.italic) formatting.add(MessageTextFormat(start, end, italic = true))
        if (style.underline) formatting.add(MessageTextFormat(start, end, underline = true))
        if (style.strikethrough) {
            formatting.add(MessageTextFormat(start, end, strikethrough = true))
        }
        if (style.code) formatting.add(MessageTextFormat(start, end, monospace = true))
        if (style.quote) {
            formatting.add(MessageTextFormat(start, end, block = MessageTextBlock.Quote))
        }
        if (style.preformatted) {
            formatting.add(MessageTextFormat(start, end, block = MessageTextBlock.Code))
        }

        style.link?.takeIf { url -> url.isNotBlank() }?.let { url ->
            formatting.add(MessageTextFormat(start, end, url = url))
        }
    }

    fun walk(node: Node, style: HtmlStyle) {
        when (node) {
            is TextNode -> {
                val value = if (style.preformatted) {
                    node.getWholeText()
                } else {
                    collapseHtmlWhitespace(node.getWholeText(), output)
                }

                appendText(value, style)
            }

            is Element -> {
                val name = node.tagName().lowercase()

                if (name in SkippedHtmlTags) return
                if (name == "br" || name == "hr") {
                    breakLine()
                    return
                }

                val block = name in BlockHtmlTags
                if (block) breakLine()

                val next = when (name) {
                    "b", "strong" -> style.copy(bold = true)
                    "i", "em" -> style.copy(italic = true)
                    "u", "ins" -> style.copy(underline = true)
                    "s", "strike", "del" -> style.copy(strikethrough = true)
                    "code", "tt" -> style.copy(code = true)
                    "pre" -> style.copy(preformatted = true)
                    "blockquote" -> style.copy(quote = true)
                    "a" -> style.copy(
                        link = node.attr("href").trim().takeIf { href -> href.isNotEmpty() }
                    )

                    else -> style
                }

                node.childNodes().forEach { child -> walk(child, next) }

                if (block) breakLine()
            }
        }
    }

    val document = Ksoup.parse(html)
    document.body().childNodes().forEach { node -> walk(node, HtmlStyle()) }

    val raw = output.toString()
    val leading = raw.indexOfFirst { character -> !character.isWhitespace() }
    if (leading < 0) return null

    val trailing = raw.indexOfLast { character -> !character.isWhitespace() }
    val plain = raw.substring(leading, trailing + 1)

    val shifted = formatting.mapNotNull { format ->
        val start = maxOf(format.start, leading)
        val end = minOf(format.end, trailing + 1)

        if (start >= end) {
            null
        } else {
            format.copy(start = start - leading, end = end - leading)
        }
    }.mergedFormatting()

    if (shifted.isEmpty() && plain == html) return null

    return ConvertedPaste(text = plain, formatting = shifted)
}

/** Collapses HTML whitespace, which browsers treat as one space per run. */
private fun collapseHtmlWhitespace(value: String, output: StringBuilder): String {
    if (value.isEmpty()) return value

    var lastWasSpace = output.isEmpty() || output.last() == ' ' || output.last() == '\n'
    val builder = StringBuilder()

    for (character in value) {
        if (character.isWhitespace()) {
            if (!lastWasSpace) {
                builder.append(' ')
                lastWasSpace = true
            }
        } else {
            builder.append(character)
            lastWasSpace = false
        }
    }

    return builder.toString()
}
