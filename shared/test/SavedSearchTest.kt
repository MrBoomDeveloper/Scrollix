package com.mrboomdev.scrollix.data.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedSearchTest {
    @Test
    fun `a new query goes to the front`() {
        assertEquals(
            listOf("new", "old"),
            withSavedQuery(listOf("old"), "new")
        )
    }

    @Test
    fun `a saved query moves to the front instead of repeating`() {
        val saved = withSavedQuery(listOf("first", "Second", "third"), "  second  ")

        assertEquals(listOf("second", "first", "third"), saved)
    }

    @Test
    fun `saving a blank query changes nothing`() {
        val existing = listOf("keep")

        assertEquals(existing, withSavedQuery(existing, "   "))
        assertEquals(existing, withSavedQuery(existing, ""))
    }

    @Test
    fun `the list is capped from the oldest end`() {
        val saved = withSavedQuery(
            saved = listOf("a", "b", "c"),
            query = "new",
            limit = 3
        )

        assertEquals(listOf("new", "a", "b"), saved)
    }

    @Test
    fun `removing ignores case and surrounding spaces`() {
        val saved = withoutSavedQuery(listOf("Photos", "links"), "photos")

        assertEquals(listOf("links"), saved)
    }

    @Test
    fun `removing an unknown query keeps the list`() {
        val existing = listOf("one", "two")

        assertEquals(existing, withoutSavedQuery(existing, "three"))
        assertTrue(withoutSavedQuery(existing, "  ").isNotEmpty())
    }
}
