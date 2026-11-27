package com.mrboomdev.scrollix.features.dialog

/**
 * Character ranges of [text] that match [query], for marking search results.
 *
 * The query is split into whitespace-separated tokens and every occurrence of
 * every token is highlighted, case-insensitively, the same way the provider
 * search treats a multi-word query. Adjacent or overlapping ranges are merged
 * so the mark is continuous. Offsets are plain string indices (not UTF-16
 * entity offsets of `MessageTextFormat`), because they are applied to the text
 * being rendered.
 */
internal fun searchHighlightRanges(text: String, query: String): List<IntRange> {
    if (text.isEmpty()) return emptyList()

    val tokens = query.trim()
        .split(WhitespaceRegex)
        .filter { token -> token.isNotEmpty() }

    if (tokens.isEmpty()) return emptyList()

    val ranges = mutableListOf<IntRange>()

    tokens.forEach { token ->
        var from = 0

        while (from <= text.length - token.length) {
            val index = text.indexOf(token, startIndex = from, ignoreCase = true)
            if (index < 0) break

            ranges += index until (index + token.length)
            from = index + token.length
        }
    }

    if (ranges.isEmpty()) return emptyList()

    return mergeHighlightRanges(ranges)
}

/** Sorts ranges and joins the ones that touch or overlap. */
private fun mergeHighlightRanges(ranges: List<IntRange>): List<IntRange> {
    val merged = mutableListOf<IntRange>()

    ranges.sortedBy { range -> range.first }.forEach { range ->
        val last = merged.lastOrNull()

        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }

    return merged
}

private val WhitespaceRegex = Regex("\\s+")
