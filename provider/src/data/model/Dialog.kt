package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
data class ChatWallpaper(
    val fileId: Int? = null,
    /**
     * Durable content id of the wallpaper file. Runtime file ids are only
     * valid for a single run, so cached wallpapers are looked up by this.
     */
    val fileUniqueId: String? = null,
    val thumbnailFileId: Int? = null,
    val thumbnailUniqueId: String? = null,
    val colors: List<Int> = emptyList(),
    val gradientAngle: Int = 0,
    val isPattern: Boolean = false,
    val intensity: Int = 100,
    val darkThemeDimming: Int = 0
)

@Serializable
data class DialogFolderPosition(
    val folderId: Int,
    val order: Long,
    val isPinned: Boolean = false
)

@Serializable
data class Dialog(
    val providerId: String,
    val accountId: String,
    val providerDialogId: String,
    val identity: DialogIdentity,
    val kind: DialogKind,
    val title: String,
    val statusText: String? = null,
    /** Transient chat action hint (typing, sending a photo…), cleared by the session. */
    val typingText: String? = null,
    val emojiStatusId: Long? = null,
    val preview: String,
    @Transient val previewCustomEmojis: List<CustomEmojiSpan> = emptyList(),
    @Transient val lastMessageMedia: MessageMedia? = null,
    val lastMessageIsOutgoing: Boolean = false,
    val lastMessageIsRead: Boolean = false,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val memberCount: Int? = null,
    val pinnedMessageId: Long? = null,
    val canSendMessages: Boolean = true,
    val isPinned: Boolean = false,
    val isMarkedAsUnread: Boolean = false,
    val isSavedMessages: Boolean = false,
    val order: Long = 0,
    val folderIds: Set<Int> = emptySet(),
    val folderPositions: List<DialogFolderPosition> = emptyList(),
    val wallpaper: ChatWallpaper? = null,
    val avatar: Avatar? = null
)

data class AccountDialogEntry(
    val account: AccountRecord,
    val dialog: Dialog,
    val showAccountBadge: Boolean = false
)
