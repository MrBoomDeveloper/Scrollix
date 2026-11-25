package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the chat header shows under the title, and how it is tinted. */
internal data class DialogHeaderStatus(
    val text: String,
    val accent: Boolean
)

/**
 * Presentation state the chat layout reads: the header line, the forwarding
 * picker, the selection summary and the two banners above the message list.
 * Deriving them here keeps the screen free of branching.
 */
internal fun DialogScreenViewModel.refreshHeaderStatus() {
    val currentDialog = dialog

    if (currentDialog == null || currentDialog.isSavedMessages) {
        headerStatus = null
        return
    }

    val typingText = currentDialog.typingText

    // A stale "online" is misleading while the transport is reconnecting, so the
    // connection state wins over it.
    var text = if (!isConnected) {
        "Connecting..."
    } else {
        typingText ?: currentDialog.statusText
    }

    if (currentDialog.kind == DialogKind.Bot && typingText == null) {
        text = null
    }

    if (text == null) {
        text = when (currentDialog.kind) {
            DialogKind.Direct -> if (currentDialog.isSavedMessages) {
                "Saved Messages"
            } else {
                "Direct message"
            }

            DialogKind.Group -> currentDialog.memberCount?.let { count ->
                "${formatCount(count)} ${if (count == 1) "member" else "members"}"
            } ?: "Group"

            DialogKind.Channel -> currentDialog.memberCount?.let { count ->
                "${formatCount(count)} ${if (count == 1) "subscriber" else "subscribers"}"
            } ?: "Channel"

            DialogKind.Bot -> "Bot"
            DialogKind.Secret -> "Secret chat"
        }
    }

    headerStatus = DialogHeaderStatus(
        text = text,
        accent = !isConnected || typingText != null || text == "online"
    )
}

/** Opens the forwarding picker with fresh targets, query and folder tab. */
internal fun DialogScreenViewModel.openForwardPicker(messageIds: LongArray) {
    forwardingMessageIds = messageIds
    forwardQuery = ""
    forwardFolderKey = "all"
    forwardHideSender = false
    forwardRemoveCaption = false

    // Only chats that accept messages can be a forwarding target, so MAX
    // channels the account cannot post into and read-only chats are not offered.
    forwardTargets = dialogs.filter { target ->
        target.canSendMessages && target.providerDialogId != providerDialogId
    }

    forwardFolderOptions = buildForwardFolderOptions()
    refreshForwardTargets()
}

internal fun DialogScreenViewModel.setForwardQuery(value: String) {
    forwardQuery = value
    refreshForwardTargets()
}

internal fun DialogScreenViewModel.setForwardFolder(key: String) {
    forwardFolderKey = key
    refreshForwardTargets()
}

/**
 * The folder tabs still narrow a search: the query runs first, then the
 * selected folder, so a match in another folder is not offered.
 */
internal fun DialogScreenViewModel.refreshForwardTargets() {
    val useNativeFolders = settings.useNativeFolders

    filteredForwardTargets = filterForwardTargets(forwardTargets, forwardQuery).filter { target ->
        when {
            forwardFolderKey == "all" -> true

            useNativeFolders -> {
                val folder = folders.firstOrNull { it.key == forwardFolderKey }
                    ?: return@filter true

                target.accountId == folder.account.id &&
                    folder.folder.id in target.folderIds
            }

            forwardFolderKey == "direct" ->
                target.kind == DialogKind.Direct || target.kind == DialogKind.Secret

            forwardFolderKey == "groups" -> target.kind == DialogKind.Group
            forwardFolderKey == "channels" -> target.kind == DialogKind.Channel
            forwardFolderKey == "bots" -> target.kind == DialogKind.Bot
            else -> true
        }
    }
}

private fun DialogScreenViewModel.buildForwardFolderOptions(): List<Pair<String, String>> {
    // The picker mirrors the home tabs: native server folders when that setting
    // is on, otherwise the built-in Direct/Groups/Channels/Bots tabs. Without
    // them a long chat list hides most destinations.
    if (!settings.useNativeFolders) {
        return listOf(
            "all" to "All",
            "direct" to "DM",
            "groups" to "Groups",
            "channels" to "Channels",
            "bots" to "Bots"
        )
    }

    return buildList {
        add("all" to "All")

        folders
            .filter { folder ->
                folder.account.id == accountId &&
                    forwardTargets.any { target ->
                        target.accountId == accountId &&
                            folder.folder.id in target.folderIds
                    }
            }
            .forEach { folder -> add(folder.key to folder.folder.name) }
    }
}

/** The messages the selection bar acts on, in list order. */
internal val DialogScreenViewModel.selectedMessages: List<ChatMessage>
    get() = displayMessages.filter { message ->
        message.allMessageIds.any { id -> id in selectedMessageIds }
    }

internal val DialogScreenViewModel.selectedMessageCount: Int
    get() = displayMessages.count { message ->
        message.allMessageIds.any { id -> id in selectedMessageIds }
    }

internal val DialogScreenViewModel.canForwardSelection: Boolean
    get() = selectedMessages.isNotEmpty() && selectedMessages.all { it.canBeForwarded }

internal val DialogScreenViewModel.canDeleteSelection: Boolean
    get() = selectedMessages.isNotEmpty() && selectedMessages.all { message ->
        message.canBeDeleted || dialog?.canDeleteMessages == true
    }

internal val DialogScreenViewModel.selectionCopyText: String
    get() = selectedMessageText(selectedMessages)

/** Only loaded rows can be selected: history is paged, so "all" means the list. */
internal val DialogScreenViewModel.allLoadedSelected: Boolean
    get() = displayMessages.isNotEmpty() && displayMessages.all { message ->
        message.allMessageIds.all { id -> id in selectedMessageIds }
    }

/** The live location this account is still sharing, shown above the chat. */
internal val DialogScreenViewModel.activeLiveLocation: ChatMessage?
    get() = displayMessages.lastOrNull { message ->
        message.isOutgoing && message.location?.isLive == true
    }

/** Preview text of the pinned banner; the message itself may be unloaded. */
internal val DialogScreenViewModel.pinnedPreview: String
    get() {
        val pinnedId = dialog?.pinnedMessageId ?: return "Pinned message"

        val pinnedMessage = messages.firstOrNull { it.messageId == pinnedId }
            ?: loadedPinnedMessage?.takeIf { it.messageId == pinnedId }

        return pinnedMessage?.text?.takeIf { it.isNotBlank() }
            ?: pinnedMessage?.media?.fileName
            ?: "Pinned message"
    }
