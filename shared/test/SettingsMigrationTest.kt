package com.mrboomdev.scrollix.data.storage

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsMigrationTest {
    @Test
    fun `the legacy all tab becomes direct messages`() {
        val migrated = migrateSettings(AppSettings(defaultHomeTab = "all"))

        assertEquals("direct", migrated.defaultHomeTab)
        assertEquals(CurrentSettingsVersion, migrated.settingsVersion)
    }

    @Test
    fun `an explicitly chosen tab is preserved`() {
        val migrated = migrateSettings(AppSettings(defaultHomeTab = "groups"))

        assertEquals("groups", migrated.defaultHomeTab)
        assertEquals(CurrentSettingsVersion, migrated.settingsVersion)
    }

    @Test
    fun `current settings are returned unchanged`() {
        val current = AppSettings(
            defaultHomeTab = "all",
            settingsVersion = CurrentSettingsVersion
        )

        assertEquals(current, migrateSettings(current))
    }

    @Test
    fun `a file without a version decodes and upgrades`() {
        val legacy = Json { ignoreUnknownKeys = true }
            .decodeFromString<AppSettings>("""{"defaultHomeTab":"all"}""")

        assertEquals(0, legacy.settingsVersion)

        val migrated = migrateSettings(legacy)

        assertEquals("direct", migrated.defaultHomeTab)
        assertEquals(CurrentSettingsVersion, migrated.settingsVersion)
    }
}
