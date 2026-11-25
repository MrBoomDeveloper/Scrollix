package com.mrboomdev.scrollix.data.max

import com.mrboomdev.scrollix.data.AppMode
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
        // MAX has no public test server, so test-server builds never register
        // it: it cannot be selected or contacted in that mode.
        if (!AppMode.isProviderEnabled(MaxProvider.id)) return

        ProviderRegistry.register(MaxProvider)
    }
}
