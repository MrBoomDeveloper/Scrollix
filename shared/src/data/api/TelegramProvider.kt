package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.MessengerProvider
import com.mrboomdev.scrollix.data.provider.ProviderCapabilities
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import kotlinx.coroutines.CoroutineScope

data object TelegramProvider : MessengerProvider {
    override val id: String = "telegram"
    override val displayName: String = "Telegram"
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
        return TelegramAccountSession(
            account = account,
            storage = storage,
            parentScope = scope
        )
    }
}
