package com.mrboomdev.scrollix.data.cache

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

/**
 * Local cache of the data that should be on screen right after a restart,
 * before TDLib has replayed its updates. The payloads are the serialized model
 * objects, so the schema stays small and migrations only touch the columns.
 */
@Entity(tableName = "cached_dialogs")
internal data class CachedDialogsEntity(
    @PrimaryKey val accountId: String,
    val payload: String,
    val updatedAt: Long
)

@Entity(tableName = "cached_messages", primaryKeys = ["accountId", "providerDialogId"])
internal data class CachedMessagesEntity(
    val accountId: String,
    val providerDialogId: String,
    val payload: String,
    val updatedAt: Long
)

@Dao
internal interface CacheDao {
    @Query("SELECT payload FROM cached_dialogs WHERE accountId = :accountId")
    suspend fun dialogs(accountId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDialogs(entity: CachedDialogsEntity)

    @Query("DELETE FROM cached_dialogs WHERE accountId = :accountId")
    suspend fun deleteDialogs(accountId: String)

    @Query("SELECT COALESCE(SUM(LENGTH(payload)), 0) FROM cached_dialogs")
    suspend fun dialogsSize(): Long

    @Query("DELETE FROM cached_dialogs")
    suspend fun clearDialogs()

    @Query(
        "SELECT payload FROM cached_messages " +
            "WHERE accountId = :accountId AND providerDialogId = :providerDialogId"
    )
    suspend fun messages(accountId: String, providerDialogId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMessages(entity: CachedMessagesEntity)

    @Query("DELETE FROM cached_messages WHERE accountId = :accountId")
    suspend fun deleteMessages(accountId: String)

    @Query(
        "DELETE FROM cached_messages " +
            "WHERE accountId = :accountId AND providerDialogId = :providerDialogId"
    )
    suspend fun deleteMessages(accountId: String, providerDialogId: String)

    @Query("SELECT COALESCE(SUM(LENGTH(payload)), 0) FROM cached_messages")
    suspend fun messagesSize(): Long

    @Query("DELETE FROM cached_messages")
    suspend fun clearMessages()
}

/**
 * Schema version of the cache database. Bumping it requires an explicit Room
 * migration (or a deliberate destructive fallback); the cache is rebuildable,
 * but losing it on upgrade must be a decision, not a side effect. Payload
 * changes are part of the schema too, because rows store serialized models.
 */
internal const val CacheDatabaseVersion = 1

@Database(
    entities = [CachedDialogsEntity::class, CachedMessagesEntity::class],
    version = CacheDatabaseVersion,
    exportSchema = true
)
@ConstructedBy(ScrollixCacheDatabaseConstructor::class)
internal abstract class ScrollixCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object ScrollixCacheDatabaseConstructor :
    RoomDatabaseConstructor<ScrollixCacheDatabase> {
    override fun initialize(): ScrollixCacheDatabase
}

internal expect fun createCacheDatabase(): ScrollixCacheDatabase

/** One database per process, shared by every account through the account column. */
internal object CacheDatabaseProvider {
    val database: ScrollixCacheDatabase by lazy { createCacheDatabase() }
}
