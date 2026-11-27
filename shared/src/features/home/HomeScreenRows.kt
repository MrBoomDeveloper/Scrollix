package com.mrboomdev.scrollix.features.home

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.features.dialog.CustomEmojiMessageText
import com.mrboomdev.scrollix.features.dialog.MainMiniAppDialog
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.ConfirmDeleteDialog
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.notice.NoticeCenter
import com.mrboomdev.scrollix.ui.icons.check_icon
import com.mrboomdev.scrollix.ui.icons.delete_icon
import com.mrboomdev.scrollix.ui.icons.done_all_icon
import com.mrboomdev.scrollix.ui.icons.keep_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.mrboomdev.scrollix.onSecondaryClick

internal fun Dialog.hasUnreadIndicator(): Boolean =
    unreadCount > 0 || isMarkedAsUnread

@Composable
internal fun DialogPreviewText(
    modifier: Modifier = Modifier,
    dialog: Dialog,
    session: AccountSession?
) {
    val emojis = dialog.previewCustomEmojis
    var data by remember(dialog.providerDialogId, emojis) {
        mutableStateOf<Map<Long, ByteArray>>(emptyMap())
    }
    var adaptive by remember(dialog.providerDialogId, emojis) {
        mutableStateOf<Set<Long>>(emptySet())
    }

    LaunchedEffect(session, emojis) {
        val currentSession = session ?: return@LaunchedEffect
        val missing = emojis
            .map { span -> span.emojiId }
            .distinct()
            .filter { id -> id !in data }

        if (missing.isEmpty()) return@LaunchedEffect

        val loaded = missing.mapNotNull { id ->
            currentSession.loadCustomEmoji(id)?.let { asset -> id to asset }
        }.toMap()

        if (loaded.isNotEmpty()) {
            data = data + loaded.mapValues { (_, asset) -> asset.data }
            adaptive = adaptive + loaded
                .filterValues { asset -> asset.needsRepainting }
                .keys
        }
    }

    CustomEmojiMessageText(
        modifier = modifier,
        text = dialog.preview,
        emojis = emojis,
        data = data,
        adaptiveEmojis = adaptive,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun UnreadCountBadge(
    count: Int,
    selected: Boolean
) {
    val textStyle = MaterialTheme.typography.labelSmall
    val textSizeDp = with(LocalDensity.current) { textStyle.fontSize.toDp() }

    Box(
        modifier = Modifier
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                shape = RoundedCornerShape(textSizeDp)
            )
            .padding(vertical = 1.dp, horizontal = 4.dp)
            .widthIn(min = textSizeDp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            text = count.toString()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DialogItem(
    entry: AccountDialogEntry,
    isSelected: Boolean,
    folderId: Int?,
    showSavedMessagesAccountBadge: Boolean,
    onClick: () -> Unit
) {
    val dialog = entry.dialog
    val isPinned = dialog.isPinnedIn(folderId)

    // The same chat can be open on more than one account (and every account
    // has its own "Saved messages"). Such rows carry the account avatar on the
    // corner of the chat avatar so the copies stay distinguishable.
    val showAccountAvatarBadge = entry.showAccountBadge ||
        (dialog.isSavedMessages && showSavedMessagesAccountBadge)
    val scope = rememberCoroutineScope()
    val session = remember(dialog.accountId) { ApiManager.session(dialog.accountId) }
    var menuExpanded by remember(dialog.providerDialogId) { mutableStateOf(false) }
    var confirmDelete by remember(dialog.providerDialogId) { mutableStateOf(false) }
    var showMainWebApp by remember(dialog.providerDialogId) { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
                .onSecondaryClick { menuExpanded = true },
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else Color.Transparent
        ) {
        Row(
            modifier = Modifier.padding(8.dp)
        ) {
            Box {
                if (dialog.isSavedMessages) {
                    SavedMessagesAvatar(
                        modifier = Modifier.padding(end = 2.dp),
                        size = 48.dp
                    )
                } else {
                    AvatarImage(
                        modifier = Modifier.padding(end = 2.dp),
                        avatar = dialog.avatar ?: Avatar.fromName(dialog.title),
                        size = 48.dp
                    )
                }

                if (showAccountAvatarBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(1.5.dp)
                    ) {
                        AvatarImage(
                            avatar = entry.account.avatar
                                ?: Avatar.fromName(entry.account.displayName),
                            size = 15.dp
                        )
                    }
                }

//                if (dialog.statusText == "online" && dialog.kind != DialogKind.Bot) {
//                    // The online dot sits just left of the account badge (with
//                    // a small gap) instead of covering it.
//                    Box(
//                        modifier = Modifier
//                            .align(Alignment.BottomEnd)
//                            .offset(
//                                x = if (showAccountAvatarBadge) (-18).dp else 4.dp,
//                                y = 4.dp
//                            )
//                            .size(16.dp)
//                            .clip(CircleShape)
//                            .background(MaterialTheme.colorScheme.background)
//                            .padding(3.dp)
//                    ) {
//                        Spacer(
//                            modifier = Modifier
//                                .clip(CircleShape)
//                                .background(MaterialTheme.colorScheme.primary)
//                                .fillMaxSize()
//                        )
//                    }
//                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = dialog.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (dialog.isVerified) {
                        Icon(
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                            imageVector = verified_icon,
                            contentDescription = "Verified"
                        )
                    }

                    dialog.emojiStatusId?.let { emojiId ->
                        EmojiStatusIcon(
                            emojiId = emojiId,
                            session = session,
                            size = 16.dp
                        )
                    }

                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val typingText = dialog.typingText

                    if (typingText != null) {
                        Text(
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            text = typingText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        if (dialog.lastMessageIsOutgoing) {
                            Text(
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                text = "You:",
                                maxLines = 1
                            )
                        }

                        dialog.lastMessageMedia?.let { media ->
                            DialogMediaPreview(
                                media = media,
                                session = session,
                                size = 20.dp
                            )
                        }

                        DialogPreviewText(
                            modifier = Modifier.weight(1f),
                            dialog = dialog,
                            session = session
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dialog.lastMessageIsOutgoing && !dialog.isSavedMessages) {
                        Icon(
                            modifier = Modifier.size(14.dp),
                            tint = if (dialog.lastMessageIsRead) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            imageVector = if (dialog.lastMessageIsRead) {
                                done_all_icon
                            } else {
                                check_icon
                            },
                            contentDescription = if (dialog.lastMessageIsRead) {
                                "Read"
                            } else {
                                "Sent"
                            }
                        )
                    }
                    
                    Text(
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        text = formatTime(dialog.timestamp)
                    )
                }

                if (dialog.unreadCount > 0) {
                    val textStyle = MaterialTheme.typography.labelMedium
                    val textSizeDp = with(LocalDensity.current) { textStyle.fontSize.toDp() }
                    
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(textSizeDp))
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                            .widthIn(min = textSizeDp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            style = textStyle,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            text = dialog.unreadCount.toString()
                        )
                    }
                }

                if (dialog.kind == DialogKind.Bot && dialog.hasMainWebApp) {
                    TextButton(
                        modifier = Modifier.heightIn(min = 24.dp),
                        onClick = { showMainWebApp = true }
                    ) {
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (isPinned) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = keep_icon,
                        contentDescription = "Pinned"
                    )
                }
            }
        }
    }

    DropdownMenu(
        expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = keep_icon,
                        contentDescription = null
                    )
                },

                text = {
                    Text(
                        text = if (isPinned) "Unpin chat" else "Pin chat"
                    )
                },

                onClick = {
                    menuExpanded = false

                    scope.launch {
                        ApiManager.setDialogPinned(
                            accountId = entry.account.id,
                            providerDialogId = dialog.providerDialogId,
                            isPinned = !isPinned,
                            folderId = folderId
                        )
                    }
                }
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = delete_icon,
                        contentDescription = null
                    )
                },

                text = {
                    Text(text = "Delete chat")
                },

                onClick = {
                    menuExpanded = false
                    confirmDelete = true
                }
            )
        }
    }

    if (showMainWebApp && session != null) {
        MainMiniAppDialog(
            session = session,
            providerDialogId = dialog.providerDialogId,
            title = dialog.title,
            onDismiss = { showMainWebApp = false }
        )
    }

    if (confirmDelete) {
        val isSharedChat = dialog.kind == DialogKind.Group ||
            dialog.kind == DialogKind.Channel

        ConfirmDeleteDialog(
            title = if (isSharedChat) "Leave chat" else "Delete chat",
            message = if (isSharedChat) {
                "Leave \"${dialog.title}\" and remove it from your chat list?"
            } else {
                "Delete the conversation with \"${dialog.title}\" for both sides?"
            },
            confirmLabel = if (isSharedChat) "Leave" else "Delete",
            onConfirm = {
                // The row disappears at once but the provider call waits for
                // the undo window; Undo brings the row back untouched.
                ApiManager.hideDialog(
                    accountId = entry.account.id,
                    providerDialogId = dialog.providerDialogId
                )

                NoticeCenter.undo(
                    message = if (isSharedChat) {
                        "Left \"${dialog.title}\""
                    } else {
                        "Chat deleted"
                    },
                    undo = {
                        ApiManager.restoreHiddenDialog(
                            accountId = entry.account.id,
                            providerDialogId = dialog.providerDialogId
                        )
                    },
                    commit = {
                        ApiManager.deleteHiddenDialog(
                            accountId = entry.account.id,
                            providerDialogId = dialog.providerDialogId
                        )
                    }
                )
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

internal fun Dialog.isPinnedIn(folderId: Int?): Boolean {
    if (folderId == null) return isPinned

    return folderPositions.any { position ->
        position.folderId == folderId && position.isPinned
    }
}

internal fun Dialog.orderIn(folderId: Int?): Long {
    if (folderId == null) return order

    return folderPositions.firstOrNull { position ->
        position.folderId == folderId
    }?.order ?: 0L
}

internal fun AccountDialogEntry.key(): String = "${account.id}:${dialog.providerDialogId}"

/**
 * Pinned chats stay on top in their provider pin order, and every unpinned
 * chat is mixed across accounts by its last message time. `Dialog.order`
 * itself cannot be compared across providers: TDLib uses a huge int64 sort
 * key while MAX stores the last event time there.
 */
internal fun sortDialogs(
    entries: List<AccountDialogEntry>,
    folderId: Int?
): List<AccountDialogEntry> {
    val pinRanks = entries
        .filter { entry -> entry.dialog.isPinnedIn(folderId) }
        .groupBy { entry -> entry.account.id }
        .flatMap { (_, pinned) ->
            pinned
                .sortedByDescending { entry -> entry.dialog.orderIn(folderId) }
                .mapIndexed { index, entry -> entry.key() to index }
        }
        .toMap()

    return entries.sortedWith(
        compareByDescending<AccountDialogEntry> { entry -> entry.dialog.isPinnedIn(folderId) }
            .thenBy { entry -> pinRanks[entry.key()] ?: 0 }
            .thenByDescending { entry -> entry.dialog.timestamp }
            .thenBy { entry -> entry.dialog.title.lowercase() }
    )
}

