package com.mrboomdev.scrollix.data.cache

import com.mrboomdev.scrollix.data.storage.AndroidAppContext
import com.mrboomdev.scrollix.data.storage.createPlatformStorage

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun createCacheDatabase(): ScrollixCacheDatabase {
    val storage = createPlatformStorage()

    // SQLite can't create the database file inside a missing directory.
    storage.ensureDirectory("cache")

    return Room.databaseBuilder<ScrollixCacheDatabase>(
        context = AndroidAppContext.context,
        name = storage.absolutePath("cache/scrollix-cache.db")
    )
        .setDriver(BundledSQLiteDriver())
        .build()
}
