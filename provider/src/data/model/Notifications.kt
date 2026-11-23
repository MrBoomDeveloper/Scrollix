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

/**
 * One chat that does not follow its [scope]'s notification defaults. Telegram
 * keeps these overrides per chat on the server; the settings screen lists them
 * under the matching category so they can be reset.
 */
data class NotificationException(
    val providerDialogId: String,
    val title: String,
    val avatar: Avatar? = null,
    val isMuted: Boolean = false,
    val scope: NotificationScope? = null
)
