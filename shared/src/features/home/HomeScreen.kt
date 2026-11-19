package com.mrboomdev.scrollix.features.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexBoxConfig
import androidx.compose.foundation.layout.FlexBoxScope
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.DialogMediaPreview
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.FolderNameText
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.brand_awareness_icon
import com.mrboomdev.scrollix.ui.icons.check_icon
import com.mrboomdev.scrollix.ui.icons.done_all_icon
import com.mrboomdev.scrollix.ui.icons.group_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.keep_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.settings_icon
import com.mrboomdev.scrollix.ui.icons.smart_toy_icon
import com.mrboomdev.scrollix.isWideLayout
import com.mrboomdev.scrollix.onSecondaryClick
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import kotlinx.coroutines.launch

private const val TAB_ALL = 0
private const val TAB_DIRECT = 1
private const val TAB_GROUPS = 2
private const val TAB_CHANNELS = 3
private const val TAB_BOTS = 4

@Composable
fun HomeScreen(
    onOpenAccountsScreen: () -> Unit,
    onOpenDialog: (AccountDialogEntry) -> Unit,
    selectedDialogAccountId: String? = null,
    selectedDialogProviderDialogId: String? = null
) {
    val layoutInfo = currentAdaptiveLayoutInfo()
    val settings by AppSettingsStore.state.collectAsState()
    val isAllShown = settings.showAllChatsTab
    val isSingleRowFilters = settings.singleRowFilters
    val isUsingNativeFolders = settings.useNativeFolders
    
    var selectedTab by remember { mutableStateOf(if(isAllShown) TAB_ALL else TAB_DIRECT) }
    var selectedNativeFolderKey by remember { mutableStateOf<String?>(null) }
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val loadingDialogs by ApiManager.dialogsLoading.collectAsState()
    val entries by ApiManager.dialogs.collectAsState()
    val folders by ApiManager.folders.collectAsState()

    val selectedNativeFolder = remember(
        selectedNativeFolderKey,
        folders,
        isAllShown
    ) {
        if(selectedNativeFolderKey == null && !isAllShown) {
            return@remember folders.firstOrNull()
        }
        
        folders.firstOrNull {
            it.key == selectedNativeFolderKey
        }
    }
    
    val duplicateFolderNames = remember(folders) {
        folders.groupingBy { it.folder.name }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    }

    val isConnecting = accountState.accounts.any { account ->
        val state = sessionStates[account.id]
        state == null || state is AuthState.Initializing
    }
    val isDialogsLoading = isConnecting || loadingDialogs.isNotEmpty()

    val activeFolderId = if (isUsingNativeFolders) {
        selectedNativeFolder?.folder?.id
    } else {
        null
    }

    val visibleEntries = remember(
        entries,
        selectedTab,
        selectedNativeFolder,
        isUsingNativeFolders
    ) {
        if (isUsingNativeFolders) {
            val folderId = selectedNativeFolder?.folder?.id

            entries
                .filter { entry ->
                    if (folderId == null) {
                        true
                    } else {
                        entry.account.id == selectedNativeFolder.account.id &&
                            folderId in entry.dialog.folderIds
                    }
                }
                .sortedWith(dialogComparator(folderId))
        } else {
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
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxWidth()) {
                if(layoutInfo.size < AdaptiveLayoutSize.EXPANDED) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp, 8.dp, 8.dp, 0.dp)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
//                        Image(
//                            modifier = Modifier.size(IconButtonDefaults.smallContainerSize()),
//                            painter = rememberVectorPainter(home_icon),
//                            contentDescription = null
//                        )

                        Text(
                            modifier = Modifier.padding(start = 4.dp),
                            text = "Scrollix",
                            style = MaterialTheme.typography.titleLarge
                        )
                        
                        Spacer(Modifier.weight(1f))

                        Box {
                            var expand by remember { mutableStateOf(false) }
                            
                            IconButton(
                                onClick = {
                                    expand = !expand
                                }
                            ) {
                                Icon(
                                    tint = MaterialTheme.colorScheme.secondary,
                                    imageVector = more_vertical_icon,
                                    contentDescription = null
                                )
                            }

                            DropdownMenu(
                                expanded = expand,
                                onDismissRequest = { expand = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = settings_icon,
                                            contentDescription = null
                                        )
                                    },
                                    
                                    text = {
                                        Text(
                                            text = "Settings"
                                        )
                                    },
                                    
                                    onClick = {
                                        
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                ) {
                    @Composable
                    fun FlexBoxScope.homeFilterChipsContent() {
                        val chipModifier = Modifier.flex { 
                            grow(1f)
                        }
                        
                        if(isUsingNativeFolders) {
                            if(isAllShown) {
                                HomeTab(
                                    modifier = chipModifier,
                                    selected = selectedNativeFolder == null,
                                    label = "All",
                                    icon = home_icon,
                                    onClick = { selectedNativeFolderKey = null }
                                )   
                            }

                            folders.forEach { folder ->
                                NativeFolderTab(
                                    modifier = chipModifier,
                                    selected = selectedNativeFolder?.key == folder.key,
                                    folder = folder.folder,
                                    account = folder.account,
                                    showAccountIndicator = folder.folder.name in duplicateFolderNames,
                                    onClick = {
                                        selectedNativeFolderKey = folder.key
                                    }
                                )
                            }
                        } else {
                            if(isAllShown) {
                                HomeTab(
                                    modifier = chipModifier,
                                    selected = selectedTab == TAB_ALL,
                                    label = "All",
                                    icon = home_icon,
                                    onClick = { selectedTab = TAB_ALL }
                                )
                            }

                            HomeTab(
                                modifier = chipModifier,
                                selected = selectedTab == TAB_DIRECT,
                                label = "DM",
                                icon = account_circle_icon,
                                onClick = { selectedTab = TAB_DIRECT }
                            )

                            HomeTab(
                                modifier = chipModifier,
                                selected = selectedTab == TAB_GROUPS,
                                label = "Groups",
                                icon = group_icon,
                                onClick = { selectedTab = TAB_GROUPS }
                            )

                            HomeTab(
                                modifier = chipModifier,
                                selected = selectedTab == TAB_CHANNELS,
                                label = "Channels",
                                icon = brand_awareness_icon,
                                onClick = { selectedTab = TAB_CHANNELS }
                            )

                            HomeTab(
                                modifier = chipModifier,
                                selected = selectedTab == TAB_BOTS,
                                label = "Bots",
                                icon = smart_toy_icon,
                                onClick = { selectedTab = TAB_BOTS }
                            )
                        }
                    }

                    FlexBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if(isSingleRowFilters) {
                                Modifier.horizontalScroll(rememberScrollState())
                            } else Modifier)
                            .padding(horizontal = 8.dp)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Horizontal
                                )
                            ),
                        
                        config = {
                            gap(4.dp)
                            
                            if(!isSingleRowFilters) {
                                wrap(FlexWrap.Wrap)
                            }
                        }
                    ) {
                        homeFilterChipsContent()
                    }
                }

                if (isDialogsLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))
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
                        isSelected = entry.account.id == selectedDialogAccountId &&
                            entry.dialog.providerDialogId == selectedDialogProviderDialogId,
                        folderId = activeFolderId,
                        onClick = { onOpenDialog(entry) }
                    )
                }

                if (visibleEntries.isEmpty()) {
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
                                hasAccounts = accountState.accounts.isNotEmpty(),
                                onOpenAccountsScreen = onOpenAccountsScreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: ImageVector,
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
            Text(
                overflow = TextOverflow.Visible,
                softWrap = false,
                text = label
            ) 
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
private fun NativeFolderTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    folder: DialogFolder,
    account: AccountRecord,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialogItem(
    entry: AccountDialogEntry,
    isSelected: Boolean,
    folderId: Int?,
    onClick: () -> Unit
) {
    val dialog = entry.dialog
    val isPinned = dialog.isPinnedIn(folderId)
    val scope = rememberCoroutineScope()
    val session = remember(dialog.accountId) { ApiManager.session(dialog.accountId) }
    var menuExpanded by remember(dialog.providerDialogId) { mutableStateOf(false) }

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
                AvatarImage(
                    modifier = Modifier.padding(end = 2.dp),
                    avatar = dialog.avatar ?: Avatar.fromName(dialog.title),
                    size = 48.dp
                )

                if (dialog.statusText == "online" && dialog.kind != DialogKind.Bot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(3.dp)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .fillMaxSize()
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

                    dialog.emojiStatusId?.let { emojiId ->
                        EmojiStatusIcon(
                            emojiId = emojiId,
                            session = session,
                            size = 16.dp
                        )
                    }

                    if (entry.showAccountBadge) {
                        AccountBadge(entry)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    Text(
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        text = dialog.preview,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    if (dialog.lastMessageIsOutgoing) {
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

private fun Dialog.isPinnedIn(folderId: Int?): Boolean {
    if (folderId == null) return isPinned

    return folderPositions.any { position ->
        position.folderId == folderId && position.isPinned
    }
}

private fun Dialog.orderIn(folderId: Int?): Long {
    if (folderId == null) return order

    return folderPositions.firstOrNull { position ->
        position.folderId == folderId
    }?.order ?: 0L
}

private fun dialogComparator(folderId: Int?): Comparator<AccountDialogEntry> {
    return compareByDescending<AccountDialogEntry> {
        it.dialog.isPinnedIn(folderId)
    }
        .thenByDescending { it.dialog.orderIn(folderId) }
        .thenByDescending { it.dialog.timestamp }
        .thenBy { it.dialog.title.lowercase() }
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
