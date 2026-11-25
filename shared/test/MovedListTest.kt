package com.mrboomdev.scrollix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MovedListTest {
    @Test
    fun `an item moves down and up`() {
        val items = listOf("a", "b", "c")

        assertEquals(listOf("b", "a", "c"), items.moved(0, 1))
        assertEquals(listOf("a", "c", "b"), items.moved(2, 1))
        assertEquals(listOf("c", "a", "b"), items.moved(2, 0))
    }

    @Test
    fun `an unchanged or out-of-range move returns the same list`() {
        val items = listOf("a", "b", "c")

        assertSame(items, items.moved(1, 1))
        assertSame(items, items.moved(-1, 0))
        assertSame(items, items.moved(0, 3))

        val empty = emptyList<String>()
        assertSame(empty, empty.moved(0, 0))
    }
}
