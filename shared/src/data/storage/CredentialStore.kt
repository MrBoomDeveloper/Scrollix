package com.mrboomdev.scrollix.data.storage

import com.mrboomdev.scrollix.features.home.key

interface CredentialStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun delete(key: String)
}
