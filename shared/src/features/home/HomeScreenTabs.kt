package com.mrboomdev.scrollix.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AccountDialogFolder
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.FolderNameText
import com.mrboomdev.scrollix.ui.components.LoadingContent

internal fun HomePage.orderKey(): String = when (this) {
    HomePage.All -> "all"
    is HomePage.Folder -> "folder:$key"
    is HomePage.Tab -> "tab:$index"
}

internal fun HomePage.chipTitle(folders: List<AccountDialogFolder>): String = when (this) {
    HomePage.All -> "All"
    is HomePage.Folder -> folders.firstOrNull { it.key == key }?.folder?.name ?: key
    is HomePage.Tab -> when (index) {
        TAB_DIRECT -> "DM"
        TAB_GROUPS -> "Groups"
        TAB_CHANNELS -> "Channels"
        TAB_BOTS -> "Bots"
        else -> "Tab $index"
    }
}

@Composable
internal fun DialogList(
    entries: List<AccountDialogEntry>,
    folderId: Int?,
    mergeDuplicates: Boolean = false,
    isDialogsLoading: Boolean,
    hasAccounts: Boolean,
    selectedDialogAccountId: String?,
    selectedDialogProviderDialogId: String?,
    onOpenAccountsScreen: () -> Unit,
    onOpenDialog: (AccountDialogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    // Several accounts can each have a "Saved messages" chat. Without a badge
    // the rows are indistinguishable, so every one of them shows its account.
    val showSavedMessagesAccountBadge = remember(entries) {
        entries.count { entry -> entry.dialog.isSavedMessages } > 1
    }

    val rows = remember(entries, mergeDuplicates) {
        if (mergeDuplicates) groupDuplicateDialogs(entries) else entries.map(::listOf)
    }

    LazyColumn(modifier = modifier) {
        items(
            items = rows,
            key = { row ->
                row.joinToString("|") { entry ->
                    "${entry.account.id}:${entry.dialog.providerDialogId}"
                }
            }
        ) { row ->
            val isSelected = row.any { entry ->
                entry.account.id == selectedDialogAccountId &&
                    entry.dialog.providerDialogId == selectedDialogProviderDialogId
            }

            if (row.size == 1) {
                val entry = row.first()

                DialogItem(
                    entry = entry,
                    isSelected = isSelected,
                    folderId = folderId,
                    showSavedMessagesAccountBadge = showSavedMessagesAccountBadge,
                    onClick = { onOpenDialog(entry) }
                )
            } else {
                MergedDialogItem(
                    entries = row,
                    isSelected = isSelected,
                    onOpenDialog = onOpenDialog
                )
            }
        }

        if (entries.isEmpty()) {
            item {
                if (isDialogsLoading) {
                    LoadingContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        text = "Loading dialogs..."
                    )
                } else {
                    EmptyState(
                        hasAccounts = hasAccounts,
                        onOpenAccountsScreen = onOpenAccountsScreen
                    )
                }
            }
        }
    }
}

internal sealed interface HomePage {
    data object All : HomePage
    data class Folder(val key: String) : HomePage
    data class Tab(val index: Int) : HomePage
}

/** Entries and active folder id shown by one pager page. */
internal fun entriesForPage(
    page: HomePage?,
    entries: List<AccountDialogEntry>,
    folders: List<AccountDialogFolder>,
    isUsingNativeFolders: Boolean
): Pair<List<AccountDialogEntry>, Int?> {
    return when (page) {
        is HomePage.Folder -> {
            val folder = folders.firstOrNull { folder -> folder.key == page.key }

            if (folder == null) {
                entries to null
            } else {
                val folderId = folder.folder.id

                entries
                    .filter { entry ->
                        entry.account.id == folder.account.id &&
                            folderId in entry.dialog.folderIds
                    }
                    .let { filtered -> sortDialogs(filtered, folderId) } to folderId
            }
        }

        is HomePage.Tab -> entries
            .filter { entry ->
                when (page.index) {
                    TAB_DIRECT -> entry.dialog.kind == DialogKind.Direct ||
                        entry.dialog.kind == DialogKind.Secret

                    TAB_GROUPS -> entry.dialog.kind == DialogKind.Group
                    TAB_CHANNELS -> entry.dialog.kind == DialogKind.Channel
                    TAB_BOTS -> entry.dialog.kind == DialogKind.Bot
                    else -> true
                }
            }
            .let { filtered -> sortDialogs(filtered, null) } to null

        HomePage.All, null -> sortDialogs(entries, null) to null
    }
}

@Composable
internal fun HomeTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: ImageVector,
    unreadCount: Int = 0,
    onClick: () -> Unit
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),

        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                    text = label
                )

                if (unreadCount > 0) {
                    UnreadCountBadge(
                        count = unreadCount,
                        selected = selected
                    )
                }
            }
        },
        
        leadingIcon = {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = icon,
                contentDescription = null
            )
        }
    )
}

@Composable
internal fun NativeFolderTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    folder: DialogFolder,
    account: AccountRecord,
    unreadCount: Int,
    showAccountIndicator: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,

        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),

        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolderNameText(
                    folder = folder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (unreadCount > 0) {
                    UnreadCountBadge(
                        count = unreadCount,
                        selected = selected
                    )
                }

                if (showAccountIndicator) {
                    AvatarImage(
                        avatar = account.avatar
                            ?: Avatar.fromName(account.displayName),
                        size = 14.dp
                    )
                }
            }
        }
    )
}

/**
 * Rows of the dialog list. With merging enabled, every account copy of the
 * same chat becomes one row. Saved Messages merges per provider, and secret
 * chats merge by their peer (the provider identity already keys them that
 * way); identities without a provider id stay separate.
 */
internal fun groupDuplicateDialogs(
    entries: List<AccountDialogEntry>
): List<List<AccountDialogEntry>> {
    val byIdentity = LinkedHashMap<String, MutableList<AccountDialogEntry>>()
    val rows = mutableListOf<List<AccountDialogEntry>>()

    for (entry in entries) {
        val identity = entry.dialog.identity

        val key = when {
            // Every account has its own Saved Messages chat, but they describe
            // the same idea and merge across accounts of one provider.
            entry.dialog.isSavedMessages -> "saved:${identity.providerId}"

            identity.remoteId.isBlank() -> {
                rows += listOf(entry)
                continue
            }

            else -> "${identity.providerId}:${identity.remoteId}"
        }

        val existing = byIdentity[key]

        if (existing == null) {
            val group = mutableListOf(entry)
            byIdentity[key] = group
            rows += group
        } else {
            existing += entry
        }
    }

    return rows
}
