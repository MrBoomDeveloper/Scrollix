package com.mrboomdev.scrollix.data.provider

object ProviderRegistry {
    private val providers = linkedMapOf<String, MessengerProvider>()

    fun register(provider: MessengerProvider) {
        providers[provider.id] = provider
    }

    fun get(id: String): MessengerProvider? = providers[id]

    fun all(): List<MessengerProvider> = providers.values.toList()
}
