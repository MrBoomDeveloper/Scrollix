package com.mrboomdev.scrollix.features.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.brand_awareness_icon
import com.mrboomdev.scrollix.ui.icons.group_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.keep_icon
import com.mrboomdev.scrollix.ui.icons.smart_toy_icon

private const val TAB_ALL = 0
private const val TAB_DIRECT = 1
private const val TAB_GROUPS = 2
private const val TAB_CHANNELS = 3
private const val TAB_BOTS = 4

@Composable
fun HomeScreen(
    onOpenAccountsScreen: () -> Unit,
    onOpenDialog: (AccountDialogEntry) -> Unit
) {
    var selectedTab by remember { mutableStateOf(TAB_ALL) }
    val accountState by ApiManager.accountState.collectAsState()
    val entries by ApiManager.dialogs.collectAsState()

    val visibleEntries = remember(entries, selectedTab) {
        entries.filter { entry ->
            when (selectedTab) {
                TAB_DIRECT -> entry.dialog.kind == DialogKind.Direct ||
                    entry.dialog.kind == DialogKind.Secret

                TAB_GROUPS -> entry.dialog.kind == DialogKind.Group
                TAB_CHANNELS -> entry.dialog.kind == DialogKind.Channel
                TAB_BOTS -> entry.dialog.kind == DialogKind.Bot
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 8.dp, 8.dp, 0.dp)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(IconButtonDefaults.smallContainerSize())
                    )

                    Text(
                        text = "Scrollix",
                        style = MaterialTheme.typography.titleLarge
                    )

                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        onClick = onOpenAccountsScreen
                    ) {
                        Icon(
                            imageVector = account_circle_icon,
                            contentDescription = "Accounts"
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HomeTab(
                        selected = selectedTab == TAB_ALL,
                        label = "All",
                        icon = home_icon,
                        onClick = { selectedTab = TAB_ALL }
                    )

                    HomeTab(
                        selected = selectedTab == TAB_DIRECT,
                        label = "DM",
                        icon = account_circle_icon,
                        onClick = { selectedTab = TAB_DIRECT }
                    )

                    HomeTab(
                        selected = selectedTab == TAB_GROUPS,
                        label = "Groups",
                        icon = group_icon,
                        onClick = { selectedTab = TAB_GROUPS }
                    )

                    HomeTab(
                        selected = selectedTab == TAB_CHANNELS,
                        label = "Channels",
                        icon = brand_awareness_icon,
                        onClick = { selectedTab = TAB_CHANNELS }
                    )

                    HomeTab(
                        selected = selectedTab == TAB_BOTS,
                        label = "Bots",
                        icon = smart_toy_icon,
                        onClick = { selectedTab = TAB_BOTS }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = visibleEntries,
                    key = { entry ->
                        "${entry.account.id}:${entry.dialog.providerDialogId}"
                    }
                ) { entry ->
                    DialogItem(
                        entry = entry,
                        onClick = { onOpenDialog(entry) }
                    )
                }

                if (visibleEntries.isEmpty()) {
                    item {
                        EmptyState(
                            hasAccounts = accountState.accounts.isNotEmpty(),
                            onOpenAccountsScreen = onOpenAccountsScreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = colors,
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
private fun DialogItem(entry: AccountDialogEntry, onClick: () -> Unit) {
    val dialog = entry.dialog

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(8.dp)
        ) {
            AvatarImage(
                avatar = dialog.avatar ?: Avatar.fromName(dialog.title),
                size = 48.dp
            )

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

                    if (entry.showAccountBadge) {
                        AccountBadge(entry)
                    }
                }

                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    text = dialog.preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    text = formatTime(dialog.timestamp)
                )

                if (dialog.unreadCount > 0) {
                    Text(
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        text = dialog.unreadCount.toString()
                    )
                } else if (dialog.isPinned) {
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
}

@Composable
private fun AccountBadge(entry: AccountDialogEntry) {
    val label = entry.account.nickname?.let { "@$it" }
        ?: entry.account.displayName

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(
            avatar = entry.account.avatar
                ?: Avatar.fromName(entry.account.displayName),
            size = 16.dp
        )

        Text(
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyState(
    hasAccounts: Boolean,
    onOpenAccountsScreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            style = MaterialTheme.typography.titleMedium,
            text = if (hasAccounts) "No dialogs yet" else "No accounts yet"
        )

        Text(
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
            text = if (hasAccounts) {
                "Dialogs will appear here after the accounts finish loading."
            } else {
                "Add a Telegram account to see its dialogs here."
            }
        )

        Button(onClick = onOpenAccountsScreen) {
            Text(if (hasAccounts) "Manage accounts" else "Add account")
        }
    }
}
