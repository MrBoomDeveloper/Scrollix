package com.mrboomdev.scrollix.data.vk

import com.mrboomdev.scrollix.data.AppMode
import com.mrboomdev.scrollix.data.provider.ProviderRegistry

/**
 * Registers the VK provider with the shared provider registry.
 *
 * The shared module never depends on a concrete messenger, so every app entry
 * point calls this before composing the UI.
 */
object VkIntegration {
    fun init() {
        // VK has no public test server, so test-server builds never register
        // it: it cannot be selected or contacted in that mode.
        if (!AppMode.isProviderEnabled(VkProvider.id)) return

        ProviderRegistry.register(VkProvider)
    }
}
