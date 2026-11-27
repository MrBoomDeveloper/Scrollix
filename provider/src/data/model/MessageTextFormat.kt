package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

/** Block-level formatting that covers whole lines of a message. */
@Serializable
enum class MessageTextBlock {
    /** Rendered as a quote with a left rail. */
    Quote,

    /** Rendered as a preformatted code block. */
    Code
}

/**
 * Formatting of a range of a message's text, with offsets in UTF-16 code units
 * (the same units TDLib uses for text entities, so no conversion is needed).
 *
 * Entries may overlap: a bold word inside a quote is two entries. Every flag is
 * independent so a received entity never has to be merged with another one.
 */
@Serializable
data class MessageTextFormat(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val monospace: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val spoiler: Boolean = false,
    val block: MessageTextBlock? = null,
    /** Target of a link span; the covered text is shown instead of the URL. */
    val url: String? = null
) {
    val isEmpty: Boolean
        get() = start >= end

    /** True when no style is set, so the entry would not change the rendered text. */
    val isPlain: Boolean
        get() = !bold && !italic && !monospace && !underline &&
            !strikethrough && !spoiler && block == null && url == null

    fun covers(position: Int): Boolean {
        return position >= start && position < end
    }

    /**
     * Whether the whole [range] is inside this entry. A collapsed range is
     * checked against the caret, so a toolbar can show the style at the cursor.
     */
    fun covers(range: IntRange): Boolean {
        return if (range.isEmpty()) {
            covers(range.first)
        } else {
            start <= range.first && range.last < end
        }
    }

    /** Whether this entry carries the same single style as [other]. */
    fun sameStyle(other: MessageTextFormat): Boolean {
        return bold == other.bold &&
            italic == other.italic &&
            monospace == other.monospace &&
            underline == other.underline &&
            strikethrough == other.strikethrough &&
            spoiler == other.spoiler &&
            block == other.block &&
            url == other.url
    }
}
