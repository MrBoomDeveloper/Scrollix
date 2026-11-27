package com.mrboomdev.scrollix.data.storage

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsBackupPreviewTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun encode(settings: AppSettings): String =
        json.encodeToString(AppSettings.serializer(), settings)

    @Test
    fun `an identical backup changes nothing`() {
        val current = AppSettings()
        val preview = previewSettingsBackup(json, encode(current.copy(settingsVersion = 1)), current)

        val ready = assertIs<SettingsImportPreview.Ready>(preview)
        assertTrue(ready.changes.isEmpty())
    }

    @Test
    fun `changed values are listed with their labels`() {
        val current = AppSettings()
        val imported = current.copy(
            settingsVersion = 1,
            mergeDuplicateDialogs = true,
            mediaCacheLimitBytes = 128L * 1024 * 1024
        )

        val ready = assertIs<SettingsImportPreview.Ready>(
            previewSettingsBackup(json, encode(imported), current)
        )

        assertEquals(
            listOf(
                "Merge duplicate chats: false → true",
                "Media cache limit: 512 MB → 128 MB"
            ),
            ready.changes
        )
    }

    @Test
    fun `a backup from a newer build is refused instead of trimmed`() {
        val current = AppSettings()
        val imported = current.copy(settingsVersion = CurrentSettingsVersion + 1)

        val refused = assertIs<SettingsImportPreview.Refused>(
            previewSettingsBackup(json, encode(imported), current)
        )

        assertTrue("newer Scrollix" in refused.reason)
    }

    @Test
    fun `an unreadable file is refused`() {
        assertIs<SettingsImportPreview.Refused>(
            previewSettingsBackup(json, "not json at all", AppSettings())
        )
    }

    @Test
    fun `older files are migrated before the preview`() {
        val legacy = """
            {
              "defaultHomeTab": "all",
              "settingsVersion": 0
            }
        """.trimIndent()

        val ready = assertIs<SettingsImportPreview.Ready>(
            previewSettingsBackup(json, legacy, AppSettings())
        )

        assertEquals("direct", ready.settings.defaultHomeTab)
        assertEquals(CurrentSettingsVersion, ready.settings.settingsVersion)
    }

    @Test
    fun `an unlimited cache limit is described in words`() {
        val imported = AppSettings().copy(
            settingsVersion = 1,
            mediaCacheLimitBytes = UnlimitedMediaCacheBytes
        )

        val ready = assertIs<SettingsImportPreview.Ready>(
            previewSettingsBackup(json, encode(imported), AppSettings())
        )

        assertEquals(
            listOf("Media cache limit: 512 MB → Unlimited"),
            ready.changes
        )
    }

    @Test
    fun `the reduce motion switch is part of the preview`() {
        val imported = AppSettings().copy(settingsVersion = 1, reduceMotion = true)

        val ready = assertIs<SettingsImportPreview.Ready>(
            previewSettingsBackup(json, encode(imported), AppSettings())
        )

        assertEquals(listOf("Reduce motion: false → true"), ready.changes)
    }

    @Test
    fun `the data saver switches are part of the preview`() {
        val imported = AppSettings().copy(
            settingsVersion = 1,
            dataSaving = DataSavingSettings(animations = false, videoNotes = false)
        )

        val ready = assertIs<SettingsImportPreview.Ready>(
            previewSettingsBackup(json, encode(imported), AppSettings())
        )

        assertEquals(
            listOf(
                "Auto-play GIFs: true → false",
                "Auto-play round video notes: true → false"
            ),
            ready.changes
        )
    }
}
