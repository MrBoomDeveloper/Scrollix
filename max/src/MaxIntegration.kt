package com.mrboomdev.scrollix.data.max

import com.mrboomdev.scrollix.data.api.MaxProvider
import com.mrboomdev.scrollix.data.provider.ProviderRegistry

/**
 * Registers the MAX provider with the shared provider registry.
 *
 * The shared module never depends on a concrete messenger, so every app
 * entry point calls this before composing the UI.
 */
object MaxIntegration {
    fun init() {
        ProviderRegistry.register(MaxProvider)
    }
}
