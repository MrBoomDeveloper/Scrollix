package com.mrboomdev.scrollix.data.model

/** Server-side notification scope, mirroring TDLib's notification settings. */
enum class NotificationScope(val title: String) {
    PrivateChats("Private chats"),
    Groups("Groups"),
    Channels("Channels")
}

/**
 * The scope-wide mute state of one account. Telegram stores these settings on
 * the server, so they apply to every device signed in to the account.
 */
data class NotificationSettings(
    val mutedScopes: Set<NotificationScope> = emptySet()
) {
    fun isMuted(scope: NotificationScope): Boolean = scope in mutedScopes

    val allMuted: Boolean
        get() = mutedScopes.containsAll(NotificationScope.entries)
}
