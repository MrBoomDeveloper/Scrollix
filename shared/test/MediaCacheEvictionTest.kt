package com.mrboomdev.scrollix.data.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaCacheEvictionTest {
    private fun entry(path: String, size: Long, modifiedAt: Long) =
        MediaCacheEntry(relativePath = path, sizeBytes = size, lastModifiedAt = modifiedAt)

    @Test
    fun `an unlimited budget evicts nothing`() {
        val entries = listOf(
            entry("a", 100, 1),
            entry("b", 100, 2)
        )

        assertTrue(selectMediaForEviction(entries, limitBytes = 0L).isEmpty())
    }

    @Test
    fun `a cache that already fits is left alone`() {
        val entries = listOf(
            entry("a", 40, 1),
            entry("b", 40, 2)
        )

        assertTrue(selectMediaForEviction(entries, limitBytes = 100L).isEmpty())
        assertTrue(selectMediaForEviction(entries, limitBytes = 80L).isEmpty())
    }

    @Test
    fun `oldest entries are evicted until the budget fits`() {
        val entries = listOf(
            entry("newest", 40, 300),
            entry("oldest", 40, 100),
            entry("middle", 40, 200)
        )

        assertEquals(
            listOf("oldest"),
            selectMediaForEviction(entries, limitBytes = 100L).map { it.relativePath }
        )

        assertEquals(
            listOf("oldest", "middle"),
            selectMediaForEviction(entries, limitBytes = 50L).map { it.relativePath }
        )
    }

    @Test
    fun `equal timestamps evict in a stable path order`() {
        val entries = listOf(
            entry("b", 10, 5),
            entry("a", 10, 5)
        )

        assertEquals(
            listOf("a"),
            selectMediaForEviction(entries, limitBytes = 10L).map { it.relativePath }
        )
    }

    @Test
    fun `protected media is never evicted even when it is the oldest`() {
        val entries = listOf(
            entry("kept", 40, 100),
            entry("old", 40, 200),
            entry("new", 40, 300)
        )

        assertEquals(
            listOf("old"),
            selectMediaForEviction(
                entries = entries,
                limitBytes = 100L,
                protectedPaths = setOf("kept")
            ).map { it.relativePath }
        )
    }

    @Test
    fun `a cache made only of protected entries stays above the budget`() {
        val entries = listOf(
            entry("kept-a", 80, 100),
            entry("kept-b", 80, 200)
        )

        assertTrue(
            selectMediaForEviction(
                entries = entries,
                limitBytes = 50L,
                protectedPaths = setOf("kept-a", "kept-b")
            ).isEmpty()
        )
    }

    @Test
    fun `zero sized entries do not block eviction`() {
        val entries = listOf(
            entry("empty", 0, 1),
            entry("large", 100, 2)
        )

        assertEquals(
            listOf("empty", "large"),
            selectMediaForEviction(entries, limitBytes = 50L).map { it.relativePath }
        )
    }
}
