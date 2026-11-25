package com.mrboomdev.scrollix.data.vk

import kotlinx.coroutines.CoroutineScope
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.MessengerProvider
import com.mrboomdev.scrollix.data.provider.ProviderCapabilities
import com.mrboomdev.scrollix.data.storage.PlatformStorage

/** Provider id used by [Dialog.providerId] and the account store. */
const val VkProviderId: String = "vk"

data object VkProvider : MessengerProvider {
    override val id: String = VkProviderId
    override val displayName: String = "VK"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        multipleAccounts = true,
        dialogs = true,
        oauth = true
    )

    override fun createSession(
        account: AccountRecord,
        storage: PlatformStorage,
        scope: CoroutineScope
    ): AccountSession = VkSession(
        account = account,
        storage = storage,
        parentScope = scope
    )
}
