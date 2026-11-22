package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import kotlinx.coroutines.CoroutineScope

data class ProviderCapabilities(
    val multipleAccounts: Boolean = true,
    val dialogs: Boolean = true,
    val oauth: Boolean = false
)

interface MessengerProvider {
    val id: String
    val displayName: String
    val capabilities: ProviderCapabilities

    fun createSession(
        account: AccountRecord,
        storage: PlatformStorage,
        scope: CoroutineScope
    ): AccountSession
}
