package com.mrboomdev.scrollix.data.cache

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Schema export is enabled, so Room itself creates the database from the
 * committed schema and validates that [ScrollixCacheDatabase] still matches it.
 * A future schema bump must add a migration test here before the version
 * changes, or this suite fails on the version/identity check.
 */
class CacheDatabaseMigrationTest {
    @Test
    fun `exported schema version matches the database version`() {
        val schemaFile = databaseSchemaDirectory().resolve("$CacheDatabaseVersion.json")
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val database = schema["database"]?.jsonObject

        assertEquals(CacheDatabaseVersion, database?.get("version")?.jsonPrimitive?.int)
        assertTrue(database?.get("identityHash")?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `the current schema creates both cache tables`() = runBlocking {
        val databasePath = Files.createTempFile("scrollix-cache-test", ".db")
        Files.deleteIfExists(databasePath)

        val helper = MigrationTestHelper(
            schemaDirectoryPath = schemaDirectory(),
            databasePath = databasePath,
            driver = BundledSQLiteDriver(),
            databaseClass = ScrollixCacheDatabase::class
        )

        val connection = helper.createDatabase(CacheDatabaseVersion)

        try {
            connection.execSQL(
                "INSERT INTO cached_dialogs (accountId, payload, updatedAt) " +
                    "VALUES ('account', '{}', 1)"
            )
            connection.execSQL(
                "INSERT INTO cached_messages (accountId, providerDialogId, payload, updatedAt) " +
                    "VALUES ('account', '1', '[]', 1)"
            )

            connection.prepare("SELECT COUNT(*) FROM cached_dialogs").use { statement ->
                assertTrue(statement.step())
                assertEquals(1L, statement.getLong(0))
            }

            connection.prepare("SELECT COUNT(*) FROM cached_messages").use { statement ->
                assertTrue(statement.step())
                assertEquals(1L, statement.getLong(0))
            }
        } finally {
            connection.close()
            Files.deleteIfExists(databasePath)
        }
    }

    private fun schemaDirectory(): Path {
        val candidates = listOf(
            Path.of("schemas"),
            Path.of("shared", "schemas"),
            Path.of(System.getProperty("user.dir"), "shared", "schemas")
        )

        return candidates.firstOrNull { candidate -> candidate.exists() }
            ?: error("Exported Room schemas were not found. Build the shared module first.")
    }

    private fun databaseSchemaDirectory(): Path {
        return schemaDirectory().resolve(
            "com.mrboomdev.scrollix.data.cache.ScrollixCacheDatabase"
        )
    }
}
