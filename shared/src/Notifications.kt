package com.mrboomdev.scrollix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Extras a platform entry point reads from a message notification tap. */
object NotificationExtras {
    const val AccountId = "com.mrboomdev.scrollix.notification.accountId"
    const val ProviderDialogId = "com.mrboomdev.scrollix.notification.providerDialogId"
}

/** A chat the user asked to open from outside Compose. */
data class ChatOpenRequest(
    val accountId: String,
    val providerDialogId: String
)

/**
 * Bridge between a notification tap and Compose, mirroring [DeepLinkHandler].
 * The platform entry point publishes the chat from the intent extras and
 * [App] opens it.
 */
object ChatOpenHandler {
    private val _pending = MutableStateFlow<ChatOpenRequest?>(null)
    val pending: StateFlow<ChatOpenRequest?> = _pending.asStateFlow()

    fun publish(accountId: String?, providerDialogId: String?) {
        if (accountId.isNullOrBlank() || providerDialogId.isNullOrBlank()) return
        _pending.value = ChatOpenRequest(accountId, providerDialogId)
    }

    fun consume() {
        _pending.value = null
    }
}
