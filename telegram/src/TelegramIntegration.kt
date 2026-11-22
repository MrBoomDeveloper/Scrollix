package com.mrboomdev.scrollix.data.telegram

import com.mrboomdev.scrollix.data.api.TelegramProvider
import com.mrboomdev.scrollix.data.provider.ProviderRegistry

/**
 * Registers the Telegram provider with the shared provider registry.
 *
 * The app entry points call this before composing the UI, so the shared
 * module never has to depend on a concrete messenger provider.
 */
object TelegramIntegration {
    fun init() {
        ProviderRegistry.register(TelegramProvider)
    }
}
