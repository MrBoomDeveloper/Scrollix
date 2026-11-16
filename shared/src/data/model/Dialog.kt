package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DialogIdentity(
    val providerId: String,
    val remoteId: String
)

@Serializable
enum class DialogKind(
    val isPeer: Boolean
) {
    Direct(isPeer = true),
    Group(isPeer = false),
    Channel(isPeer = true),
    Bot(isPeer = true),
    Secret(isPeer = true)
}

@Serializable
data class Dialog(
    val providerId: String,
    val accountId: String,
    val providerDialogId: String,
    val identity: DialogIdentity,
    val kind: DialogKind,
    val title: String,
    val preview: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMarkedAsUnread: Boolean = false,
    val order: Long = 0,
    val folderIds: Set<Int> = emptySet(),
    val avatar: Avatar? = null
)

data class AccountDialogEntry(
    val account: AccountRecord,
    val dialog: Dialog,
    val showAccountBadge: Boolean = false
)
