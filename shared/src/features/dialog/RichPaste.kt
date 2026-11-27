package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageTextBlock
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.platform.clipboard.ClipboardRichText

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

private val HtmlTokenRegex = Regex("""<!--.*?-->|<[^>]*>|[^<]+""", RegexOption.DOT_MATCHES_ALL)
private val HtmlTagNameRegex = Regex("""</?\s*([a-zA-Z][a-zA-Z0-9]*)""")
private val HtmlHrefRegex = Regex("""href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
private val HtmlEntityRegex = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

private val BlockHtmlTags = setOf(
    "p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6",
    "blockquote", "pre", "tr", "table", "section", "article", "header", "footer"
)

private val SkippedHtmlTags = setOf("script", "style", "head", "title", "svg", "math")

/** Converts copied HTML into text plus ranges, or null for unstyled text. */
internal fun parseHtmlPaste(html: String): ConvertedPaste? {
    val output = StringBuilder()
    val formatting = mutableListOf<MessageTextFormat>()

    var bold = 0
    var italic = 0
    var underline = 0
    var strikethrough = 0
    var code = 0
    var quote = 0
    var preformatted = 0
    var skipped = 0
    var link: String? = null

    fun breakLine() {
        while (output.isNotEmpty() && output.last() == ' ') {
            output.setLength(output.length - 1)
        }

        if (output.isNotEmpty() && output.last() != '\n') {
            output.append('\n')
        }
    }

    fun appendText(value: String) {
        if (value.isEmpty()) return

        val start = output.length
        output.append(value)
        val end = output.length

        if (bold > 0) formatting.add(MessageTextFormat(start, end, bold = true))
        if (italic > 0) formatting.add(MessageTextFormat(start, end, italic = true))
        if (underline > 0) formatting.add(MessageTextFormat(start, end, underline = true))
        if (strikethrough > 0) formatting.add(MessageTextFormat(start, end, strikethrough = true))
        if (code > 0) formatting.add(MessageTextFormat(start, end, monospace = true))
        if (quote > 0) formatting.add(MessageTextFormat(start, end, block = MessageTextBlock.Quote))
        if (preformatted > 0) {
            formatting.add(MessageTextFormat(start, end, block = MessageTextBlock.Code))
        }

        link?.takeIf { url -> url.isNotBlank() }?.let { url ->
            formatting.add(MessageTextFormat(start, end, url = url))
        }
    }

    for (token in HtmlTokenRegex.findAll(html)) {
        val value = token.value

        if (value.startsWith("<!--")) continue

        if (value.startsWith("<")) {
            val name = HtmlTagNameRegex.find(value)
                ?.groupValues
                ?.getOrNull(1)
                ?.lowercase()
                ?: continue

            val closing = value.startsWith("</")
            val selfClosing = value.endsWith("/>") || name in setOf("br", "hr", "img", "wbr")

            if (name in SkippedHtmlTags) {
                if (!selfClosing) {
                    skipped = if (closing) (skipped - 1).coerceAtLeast(0) else skipped + 1
                }

                continue
            }

            if (skipped > 0) continue

            when (name) {
                "br", "hr" -> breakLine()

                "b", "strong" -> bold = if (closing) (bold - 1).coerceAtLeast(0) else bold + 1
                "i", "em" -> italic = if (closing) (italic - 1).coerceAtLeast(0) else italic + 1
                "u", "ins" -> underline = if (closing) {
                    (underline - 1).coerceAtLeast(0)
                } else {
                    underline + 1
                }

                "s", "strike", "del" -> strikethrough = if (closing) {
                    (strikethrough - 1).coerceAtLeast(0)
                } else {
                    strikethrough + 1
                }

                "code", "tt" -> code = if (closing) (code - 1).coerceAtLeast(0) else code + 1
                "pre" -> {
                    breakLine()
                    preformatted = if (closing) (preformatted - 1).coerceAtLeast(0) else preformatted + 1
                    if (closing) breakLine()
                }

                "blockquote" -> {
                    breakLine()
                    quote = if (closing) (quote - 1).coerceAtLeast(0) else quote + 1
                    if (closing) breakLine()
                }

                "a" -> {
                    link = if (closing) {
                        null
                    } else {
                        HtmlHrefRegex.find(value)?.groupValues?.drop(1)
                            ?.firstOrNull { group -> group.isNotBlank() }
                            ?.trim()
                    }
                }

                in BlockHtmlTags -> breakLine()
            }

            continue
        }

        if (skipped > 0) continue

        val decoded = unescapeHtml(value)

        val text = if (preformatted > 0) {
            decoded
        } else {
            collapseHtmlWhitespace(decoded, output)
        }

        appendText(text)
    }

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

private fun unescapeHtml(value: String): String {
    if ('&' !in value) return value

    return HtmlEntityRegex.replace(value) { match ->
        val entity = match.groupValues[1]

        when (entity.lowercase()) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> decodeHtmlCodePoint(entity) ?: match.value
        }
    }
}

private fun decodeHtmlCodePoint(entity: String): String? {
    if (!entity.startsWith("#")) return null

    val number = entity.removePrefix("#")
    val codePoint = if (number.startsWith("x", ignoreCase = true)) {
        number.substring(1).toIntOrNull(16)
    } else {
        number.toIntOrNull()
    } ?: return null

    if (codePoint !in 0..0x10FFFF) return null

    if (codePoint <= 0xFFFF) return codePoint.toChar().toString()

    // Supplementary planes need a surrogate pair, which no common stdlib
    // helper builds for us.
    val adjusted = codePoint - 0x10000
    val high = 0xD800 + (adjusted shr 10)
    val low = 0xDC00 + (adjusted and 0x3FF)

    return charArrayOf(high.toChar(), low.toChar()).concatToString()
}
