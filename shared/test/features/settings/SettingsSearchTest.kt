package com.mrboomdev.scrollix.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSearchTest {
    @Test
    fun `an empty query shows the category list instead of rows`() {
        assertTrue(searchSettings("").isEmpty())
        assertTrue(searchSettings("   \t  ").isEmpty())
    }

    @Test
    fun `finds a single switch inside a section`() {
        val results = searchSettings("crash reports")

        assertEquals(
            listOf("Save crash reports"),
            results.map { entry -> entry.title }
        )
        assertEquals(SettingsCategory.Diagnostics, results.single().category)
    }

    @Test
    fun `finds rows of sections that were never opened`() {
        // Nothing about these rows is loaded until the screen is composed.
        assertEquals(
            listOf("Media cache limit"),
            searchSettings("media cache limit").map { entry -> entry.title }
        )
        assertEquals(
            "Double-tap reaction",
            searchSettings("double tap reaction").first().title
        )
    }

    @Test
    fun `a section name without a matching row falls back to the section`() {
        val results = searchSettings("storage")

        // "Storage profile" is a real row in About, so it matches too.
        assertEquals(
            listOf("Storage"),
            results.filter { entry -> entry.category == SettingsCategory.Storage }
                .map { entry -> entry.title }
        )
    }

    @Test
    fun `search is case and whitespace insensitive`() {
        assertEquals(
            listOf("Media cache limit"),
            searchSettings("  MEDIA CACHE LIMIT ").map { entry -> entry.title }
        )
    }

    @Test
    fun `every token has to match so a second word narrows the result`() {
        assertTrue(searchSettings("cache").isNotEmpty())
        assertTrue(searchSettings("cache wallpaper").isEmpty())
    }

    @Test
    fun `an unknown query matches nothing`() {
        assertTrue(searchSettings("telepathy").isEmpty())
    }

    @Test
    fun `accounts and offline are no longer settings sections`() {
        val names = SettingsCategory.entries.map { category -> category.name }

        assertFalse("Accounts" in names)
        assertFalse("Offline" in names)
        // Per-account data usage lives on Accounts → Manage too.
        assertFalse("DataUsage" in names)
        assertTrue(searchSettings("auto-download").isEmpty())
        assertTrue(searchSettings("log out").isEmpty())
    }

    @Test
    fun `the index covers every section`() {
        SettingsCategory.entries.forEach { category ->
            assertTrue(
                SettingsEntries.any { entry -> entry.category == category },
                "no settings rows indexed for $category"
            )
        }
    }
}
