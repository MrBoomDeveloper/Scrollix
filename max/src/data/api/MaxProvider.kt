package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.MessengerProvider
import com.mrboomdev.scrollix.data.provider.ProviderCapabilities
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import kotlinx.coroutines.CoroutineScope

data object MaxProvider : MessengerProvider {
    override val id: String = "max"
    override val displayName: String = "MAX"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        multipleAccounts = true,
        dialogs = true,
        oauth = false
    )

    override fun createSession(
        account: AccountRecord,
        storage: PlatformStorage,
        scope: CoroutineScope
    ): AccountSession {
        return MaxSession(
            account = account,
            storage = storage,
            parentScope = scope
        )
    }
}
