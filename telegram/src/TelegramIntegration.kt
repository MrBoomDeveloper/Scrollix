package com.mrboomdev.scrollix.data.telegram

import com.mrboomdev.scrollix.data.AppMode
import com.mrboomdev.scrollix.data.api.TelegramProvider
import com.mrboomdev.scrollix.data.provider.ProviderRegistry
import com.mrboomdev.scrollix.generated.BuildConfig


/**
 * Registers the Telegram provider with the shared provider registry.
 *
 * The app entry points call this before composing the UI, so the shared
 * module never has to depend on a concrete messenger provider.
 */
object TelegramIntegration {
    fun init() {
        // Entry points call this first, so the mode is known before any
        // storage, cache database or provider is created.
        AppMode.setTestServerMode(BuildConfig.TEST_SERVER_MODE)
        ProviderRegistry.register(TelegramProvider)
    }
}
