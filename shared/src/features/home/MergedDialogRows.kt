package com.mrboomdev.scrollix.features.home

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.check_icon
import com.mrboomdev.scrollix.ui.icons.done_all_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon
import com.mrboomdev.scrollix.onSecondaryClick

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One row for a chat that several accounts share. Tapping it expands the row
 * in place instead of opening a chooser popup: every account's copy is listed
 * underneath with its own avatar, the account badge, the time and the last
 * message, and tapping one opens that dialog. Tapping the summary again folds
 * the row back.
 *
 * While open the whole block is tinted, so the copies read as one group.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MergedDialogItem(
    entries: List<AccountDialogEntry>,
    isSelected: Boolean,
    onOpenDialog: (AccountDialogEntry) -> Unit
) {
    val latest = remember(entries) {
        entries.maxByOrNull { entry -> entry.dialog.timestamp } ?: entries.first()
    }
    val dialog = latest.dialog
    val session = remember(dialog.accountId) { ApiManager.session(dialog.accountId) }
    var expanded by remember(entries) { mutableStateOf(false) }

    val groupTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { expanded = !expanded }
            )
            .onSecondaryClick { expanded = !expanded },
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            expanded -> groupTint
            else -> Color.Transparent
        }
    ) {
        Column {
            Row(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.padding(end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dialog.isSavedMessages) {
                        SavedMessagesAvatar(size = 48.dp)
                    } else {
                        AvatarImage(
                            avatar = dialog.avatar ?: Avatar.fromName(dialog.title),
                            size = 48.dp
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        entries.take(4).forEach { entry ->
                            AvatarImage(
                                avatar = entry.account.avatar
                                    ?: Avatar.fromName(entry.account.displayName),
                                size = 16.dp
                            )
                        }

                        if (entries.size > 4) {
                            Text(
                                text = "+${entries.size - 4}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
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
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = formatTime(dialog.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    val unread = entries.sumOf { entry -> entry.dialog.unreadCount }

                    if (unread > 0) {
                        UnreadCountBadge(count = unread, selected = isSelected)
                    }

                    // The chevron points down while folded and up once the
                    // copies are listed, like the group expanders in Telegram.
                    Icon(
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (expanded) 90f else -90f),
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = arrow_back_icon,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(groupTint)
                        .padding(bottom = 4.dp)
                ) {
                    entries.forEach { entry ->
                        MergedEntryRow(
                            entry = entry,
                            onClick = { onOpenDialog(entry) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * One account's copy of a merged chat. The contact avatar carries the account
 * badge exactly like a regular chat row, so the copies are told apart by whose
 * account they belong to.
 */
@Composable
private fun MergedEntryRow(
    entry: AccountDialogEntry,
    onClick: () -> Unit
) {
    val dialog = entry.dialog
    val session = remember(dialog.accountId) { ApiManager.session(dialog.accountId) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                if (dialog.isSavedMessages) {
                    SavedMessagesAvatar(size = 40.dp)
                } else {
                    AvatarImage(
                        avatar = dialog.avatar ?: Avatar.fromName(dialog.title),
                        size = 40.dp
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(1.5.dp)
                ) {
                    AvatarImage(
                        avatar = entry.account.avatar
                            ?: Avatar.fromName(entry.account.displayName),
                        size = 14.dp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = dialog.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dialog.lastMessageIsOutgoing) {
                        Text(
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            text = "You:",
                            maxLines = 1
                        )
                    }

                    dialog.lastMessageMedia?.let { media ->
                        DialogMediaPreview(
                            media = media,
                            session = session,
                            size = 16.dp
                        )
                    }

                    DialogPreviewText(
                        modifier = Modifier.weight(1f),
                        dialog = dialog,
                        session = session
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dialog.lastMessageIsOutgoing && !dialog.isSavedMessages) {
                        Icon(
                            modifier = Modifier.size(13.dp),
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
                            contentDescription = null
                        )
                    }

                    Text(
                        text = formatTime(dialog.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (dialog.unreadCount > 0) {
                    UnreadCountBadge(count = dialog.unreadCount, selected = false)
                }
            }
        }
    }
}
