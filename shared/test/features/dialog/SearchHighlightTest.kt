package com.mrboomdev.scrollix.features.dialog

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchHighlightTest {
    @Test
    fun `a blank query marks nothing`() {
        assertEquals(emptyList(), searchHighlightRanges("hello", ""))
        assertEquals(emptyList(), searchHighlightRanges("hello", "   "))
        assertEquals(emptyList(), searchHighlightRanges("", "hello"))
    }

    @Test
    fun `matches are case insensitive`() {
        assertEquals(
            listOf(0..4),
            searchHighlightRanges("Hello world", "hello")
        )
    }

    @Test
    fun `every occurrence is marked`() {
        assertEquals(
            listOf(0..2, 8..10, 16..18),
            searchHighlightRanges("foo bar foo baz foo", "foo")
        )
    }

    @Test
    fun `tokens are matched independently and merged when adjacent`() {
        assertEquals(
            listOf(0..2, 8..12),
            searchHighlightRanges("tom and jerry", "tom jerry")
        )
        assertEquals(
            listOf(0..5),
            searchHighlightRanges("tomtom", "tom tom")
        )
    }

    @Test
    fun `regex characters are searched literally`() {
        assertEquals(
            listOf(7..7, 9..10),
            searchHighlightRanges("cost: 5$ (net)", "$ (n")
        )
    }

    @Test
    fun `a token longer than the text is ignored`() {
        assertEquals(emptyList(), searchHighlightRanges("abc", "abcdef"))
    }
}
