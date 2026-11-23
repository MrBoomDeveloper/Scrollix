package com.mrboomdev.scrollix.data.vk

import com.mrboomdev.scrollix.data.provider.ProviderRegistry

/**
 * Registers the VK provider with the shared provider registry.
 *
 * The shared module never depends on a concrete messenger, so every app entry
 * point calls this before composing the UI.
 */
object VkIntegration {
    fun init() {
        ProviderRegistry.register(VkProvider)
    }
}
