package com.mrboomdev.scrollix.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrboomdev.scrollix.data.AppMode
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.NotificationException
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.NotificationSettings
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.ReactionPreference
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.DefaultQuickReactions
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.EmojiCatalog
import com.mrboomdev.scrollix.ui.components.EmojiPicker
import com.mrboomdev.scrollix.ui.components.ReorderDialog
import com.mrboomdev.scrollix.ui.components.ReorderItem
import com.mrboomdev.scrollix.ui.components.orderedStickerPacks
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.getPlatform
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.link_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mrboomdev.scrollix.isTwoPaneLayout

private enum class SettingsCategory(
    val title: String,
    val description: String
) {
    ChatList("Chat list", "Folders, filters and chat sorting"),
    Notifications("Notifications", "Mutes, alerts and double-tap reaction"),
    Backup("Backup", "Export or import your settings"),
    Storage("Storage", "Cached data and disk usage"),
    Accounts("Accounts", "Connected messaging accounts"),
    About("About", "Application information")
}

@Composable
fun SettingsScreen(onOpenAccountsScreen: () -> Unit) {
    val isTwoPane = isTwoPaneLayout()
    val settings by AppSettingsStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val filePicker = rememberFilePicker(
        onImagesPicked = { picked ->
            picked.firstOrNull()?.let { file ->
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        readFileBytes(file.path)
                    }

                    if (bytes != null) {
                        AppSettingsStore.saveWallpaper(bytes)
                    }
                }
            }
        },
        onFilesPicked = {}
    )

    if (isTwoPane) {
        var selected by remember { mutableStateOf(SettingsCategory.ChatList) }

        Row(Modifier.fillMaxSize()) {
            SettingsCategories(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                selected = selected,
                onSelect = { selected = it }
            )

            VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            SettingsCategoryContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                category = selected,
                settings = settings,
                onPickWallpaper = { filePicker.pickImages() },
                onOpenAccountsScreen = onOpenAccountsScreen
            )
        }

        return
    }

    var openedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    val category = openedCategory

    if (category == null) {
        SettingsCategories(
            modifier = Modifier.fillMaxSize(),
            selected = null,
            onSelect = { openedCategory = it }
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openedCategory = null }) {
                    Icon(
                        imageVector = arrow_back_icon,
                        contentDescription = "Back"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            SettingsCategoryContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                category = category,
                settings = settings,
                onPickWallpaper = { filePicker.pickImages() },
                onOpenAccountsScreen = onOpenAccountsScreen
            )
        }
    }
}

@Composable
private fun SettingsCategories(
    modifier: Modifier = Modifier,
    selected: SettingsCategory?,
    onSelect: (SettingsCategory) -> Unit
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = "Settings",
            style = MaterialTheme.typography.titleLarge
        )

        SettingsCategory.entries.forEach { category ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (selected == category) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                onClick = { onSelect(category) }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryContent(
    modifier: Modifier = Modifier,
    category: SettingsCategory,
    settings: AppSettings,
    onPickWallpaper: () -> Unit,
    onOpenAccountsScreen: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleLarge
        )

        when (category) {
            SettingsCategory.ChatList -> {
                Text(
                    text = "Chat wallpaper",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = if (settings.hasCustomWallpaper) {
                        "A custom wallpaper is used in chats without their own."
                    } else {
                        "The default wallpaper is used in chats without their own."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickWallpaper) {
                        Text("Pick wallpaper")
                    }

                    if (settings.hasCustomWallpaper) {
                        Button(onClick = { AppSettingsStore.removeWallpaper() }) {
                            Text("Remove")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

                SettingsSwitch(
                    title = "Show the \"All\" tab",
                    description = "Add a chip that displays every chat at once.",
                    checked = settings.showAllChatsTab,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(showAllChatsTab = value) }
                    }
                )

                SettingsSwitch(
                    title = "Single row filters",
                    description = "Lay out folder chips in one scrollable row.",
                    checked = settings.singleRowFilters,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(singleRowFilters = value) }
                    }
                )

                SettingsSwitch(
                    title = "Merge duplicate chats",
                    description = "Show the same chat from several accounts as one " +
                        "row with all account avatars and pick the account on tap.",
                    checked = settings.mergeDuplicateDialogs,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(mergeDuplicateDialogs = value) }
                    }
                )

                SettingsSwitch(
                    title = "Use Telegram folders",
                    description = "Show native server-side chat folders.",
                    checked = settings.useNativeFolders,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(useNativeFolders = value) }
                    }
                )

                val accountState by ApiManager.accountState.collectAsState()
                var selectedAccountId by remember {
                    mutableStateOf(
                        accountState.activeAccountId
                            ?: accountState.accounts.firstOrNull()?.id
                    )
                }
                val selectedSession = remember(selectedAccountId) {
                    selectedAccountId?.let { accountId -> ApiManager.session(accountId) }
                }
                var reorderKind by remember { mutableStateOf<StickerPackKind?>(null) }

                if (accountState.accounts.size > 1) {
                    Text(
                        text = "Account for pack settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        accountState.accounts.forEach { account ->
                            if (account.id == selectedAccountId) {
                                Button(onClick = {}) { Text(account.displayName) }
                            } else {
                                TextButton(onClick = { selectedAccountId = account.id }) {
                                    Text(account.displayName)
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { reorderKind = StickerPackKind.Sticker }) {
                        Text("Reorder sticker packs")
                    }

                    Button(onClick = { reorderKind = StickerPackKind.CustomEmoji }) {
                        Text("Reorder emoji packs")
                    }
                }

                reorderKind?.let { kind ->
                    PackOrderDialog(
                        kind = kind,
                        session = selectedSession,
                        order = if (kind == StickerPackKind.Sticker) {
                            settings.stickerPackOrders[selectedAccountId].orEmpty()
                        } else {
                            settings.emojiPackOrders[selectedAccountId].orEmpty()
                        },
                        onDismiss = { reorderKind = null },
                        onSave = { orderedIds ->
                            val accountId = selectedAccountId
                                ?: return@PackOrderDialog

                            AppSettingsStore.update { current ->
                                if (kind == StickerPackKind.Sticker) {
                                    current.copy(
                                        stickerPackOrders = current.stickerPackOrders +
                                            (accountId to orderedIds)
                                    )
                                } else {
                                    current.copy(
                                        emojiPackOrders = current.emojiPackOrders +
                                            (accountId to orderedIds)
                                    )
                                }
                            }
                            reorderKind = null
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

                SettingsSwitch(
                    title = "Recent emoji and stickers",
                    description = "Show recently used emoji and stickers at the top of the picker.",
                    checked = settings.showRecentStickersAndEmojis,
                    onCheckedChange = { value ->
                        AppSettingsStore.update {
                            it.copy(showRecentStickersAndEmojis = value)
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

                SettingsSwitch(
                    title = "System emoji",
                    description = "Show the standard emoji categories in the picker.",
                    checked = settings.showSystemEmojis,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(showSystemEmojis = value) }
                    }
                )

                var emojiEditorOpen by remember { mutableStateOf(false) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { emojiEditorOpen = true },
                        enabled = settings.showSystemEmojis
                    ) {
                        Text("Choose emoji")
                    }

                    if (settings.disabledEmojis.isNotEmpty()) {
                        Button(
                            onClick = {
                                AppSettingsStore.update { it.copy(disabledEmojis = emptySet()) }
                            },
                            enabled = settings.showSystemEmojis
                        ) {
                            Text("Enable all")
                        }
                    }
                }

                if (emojiEditorOpen) {
                    SystemEmojiDialog(
                        disabled = settings.disabledEmojis,
                        onToggle = { emoji ->
                            AppSettingsStore.update { current ->
                                val disabled = if (emoji in current.disabledEmojis) {
                                    current.disabledEmojis - emoji
                                } else {
                                    current.disabledEmojis + emoji
                                }

                                current.copy(disabledEmojis = disabled)
                            }
                        },
                        onDismiss = { emojiEditorOpen = false }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

                Text(
                    text = "Quick reactions",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "The emoji shown above a message's action menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = settings.quickReactions.joinToString("  "),
                    style = MaterialTheme.typography.titleMedium
                )

                var reactionsEditorOpen by remember { mutableStateOf(false) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { reactionsEditorOpen = true }) {
                        Text("Choose reactions")
                    }

                    if (settings.quickReactions != DefaultQuickReactions) {
                        Button(
                            onClick = {
                                AppSettingsStore.update { current ->
                                    current.copy(quickReactions = DefaultQuickReactions)
                                }
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                }

                if (reactionsEditorOpen) {
                    QuickReactionsDialog(
                        selected = settings.quickReactions,
                        onToggle = { emoji ->
                            AppSettingsStore.update { current ->
                                val updated = if (emoji in current.quickReactions) {
                                    current.quickReactions - emoji
                                } else {
                                    current.quickReactions + emoji
                                }

                                // Keep at least one emoji so the row never vanishes.
                                if (updated.isEmpty()) current else current.copy(
                                    quickReactions = updated
                                )
                            }
                        },
                        onDismiss = { reactionsEditorOpen = false }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

                PinnedReactionsEditor()

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

                DoubleTapReactionEditor()

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

                Text(
                    text = "Default home tab",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "The tab selected when the chat list opens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeTabOptions.forEach { (key, label) ->
                        if (settings.defaultHomeTab == key) {
                            Button(onClick = {}) {
                                Text(label)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    AppSettingsStore.update { current ->
                                        current.copy(defaultHomeTab = key)
                                    }
                                }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            SettingsCategory.Notifications -> SettingsNotificationsContent()

            SettingsCategory.Backup -> SettingsBackupContent()

            SettingsCategory.Storage -> SettingsStorageContent()

            SettingsCategory.Accounts -> {
                Text(
                    text = "Manage the accounts Scrollix connects to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Button(onClick = onOpenAccountsScreen) {
                    Text("Manage accounts")
                }
            }

            SettingsCategory.About -> SettingsAboutContent()
        }
    }
}

private const val ScrollixGitHubUrl = "https://github.com/MrBoomDeveloper/Scrollix"

private val AboutStack = listOf(
    "Kotlin Multiplatform",
    "Compose Multiplatform",
    "TDLib",
    "Material 3"
)

/**
 * The About page: a branded hero card, a link to the source repository and a
 * few facts about this install. It is laid out with Material 3 cards, tonal
 * surfaces and pill-shaped labels so it feels at home in the rest of Settings.
 */
@Composable
private fun SettingsAboutContent() {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        imageVector = chat_bubble_icon,
                        contentDescription = null
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Scrollix",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Text(
                        text = "A multi-provider messenger",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            Text(
                text = "Scrollix brings Telegram, MAX and VK into one native-feeling app " +
                    "for Android and desktop. One Compose Multiplatform UI, a shared " +
                    "multi-account chat list, server-side folders, topics, media " +
                    "galleries, and settings that stay in sync with your accounts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (label in AboutStack) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { uriHandler.openUri(ScrollixGitHubUrl) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    imageVector = link_icon,
                    contentDescription = null
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = "Source code on GitHub",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = "github.com/MrBoomDeveloper/Scrollix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(onClick = { uriHandler.openUri(ScrollixGitHubUrl) }) {
                Text("Open")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AboutFact(label = "Platform", value = getPlatform().name)

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            AboutFact(
                label = "Edition",
                value = if (AppMode.isTestServerMode) "Test server" else "Production"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            AboutFact(label = "Storage profile", value = AppMode.storageProfileName)
        }
    }

    Text(
        modifier = Modifier.padding(horizontal = 4.dp),
        text = "Contributions, bug reports and feature requests are welcome on GitHub. " +
            "Scrollix is an independent client and is not affiliated with Telegram, " +
            "MAX or VK.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun AboutFact(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsStorageContent() {
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableStateOf(0) }
    var sizes by remember { mutableStateOf<Map<CacheStore.Category, Long>>(emptyMap()) }

    LaunchedEffect(refreshKey) {
        sizes = withContext(Dispatchers.IO) {
            CacheStore.Category.entries.associateWith { category ->
                CacheStore.size(category)
            }
        }
    }

    Text(
        text = "Dialogs, recently opened messages, stickers, custom emoji and " +
            "avatars are kept on this device so they appear instantly after a restart.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    CacheStore.Category.entries.forEach { category ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = formatCacheSize(sizes[category] ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            TextButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { CacheStore.clear(category) }
                        refreshKey++
                    }
                }
            ) {
                Text("Clear")
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Total: ${formatCacheSize(sizes.values.sum())}",
            style = MaterialTheme.typography.bodyLarge
        )

        TextButton(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { CacheStore.clearAll() }
                    refreshKey++
                }
            }
        ) {
            Text("Clear all")
        }
    }
}

@Composable
private fun PackOrderDialog(
    kind: StickerPackKind,
    session: AccountSession?,
    order: List<Long>,
    onDismiss: () -> Unit,
    onSave: (List<Long>) -> Unit
) {
    var items by remember(kind, session) { mutableStateOf<List<ReorderItem>?>(null) }

    LaunchedEffect(kind, session, order) {
        val packs = session?.loadStickerPacks(kind)
            ?.getOrDefault(emptyList())
            .orEmpty()

        items = orderedStickerPacks(packs, order).map { pack ->
            ReorderItem(key = pack.id.toString(), title = pack.title)
        }
    }

    val loadedItems = items

    when {
        loadedItems == null -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Loading packs") },
            text = { CircularProgressIndicator() },
            confirmButton = {}
        )

        loadedItems.isEmpty() -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No packs installed") },
            text = { Text("Install a pack in Telegram to reorder it here.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        )

        else -> ReorderDialog(
            title = if (kind == StickerPackKind.Sticker) {
                "Sticker packs"
            } else {
                "Emoji packs"
            },
            items = loadedItems,
            onDismiss = onDismiss,
            onConfirm = { keys ->
                onSave(keys.mapNotNull { key -> key.toLongOrNull() })
            }
        )
    }
}

private fun formatCacheSize(bytes: Long): String {
    return when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
private fun SettingsBackupContent() {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberFilePicker(
        onImagesPicked = {},
        onFilesPicked = { picked ->
            picked.firstOrNull()?.let { file ->
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        readFileBytes(file.path)?.decodeToString()
                    }

                    message = if (text != null && AppSettingsStore.importJson(text)) {
                        "Restored settings from ${file.name}."
                    } else {
                        "Could not restore settings from ${file.name}."
                    }
                }
            }
        }
    )

    Text(
        text = "The backup keeps your chat list preferences, quick reactions, " +
            "emoji settings and the default home tab. Wallpapers and accounts " +
            "are not included.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                scope.launch {
                    val path = saveFileToDevice(
                        fileName = "scrollix-settings.json",
                        bytes = AppSettingsStore.exportJson().encodeToByteArray()
                    )

                    message = if (path != null) {
                        "Backup saved to $path"
                    } else {
                        "Could not save the backup."
                    }
                }
            }
        ) {
            Text("Back up settings")
        }

        Button(onClick = { filePicker.pickFiles() }) {
            Text("Restore")
        }
    }

    message?.let { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun SystemEmojiDialog(
    disabled: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("System emoji") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tap an emoji to hide it from the picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                LazyVerticalGrid(
                    modifier = Modifier.height(360.dp),
                    columns = GridCells.Adaptive(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    EmojiCatalog.forEach { category ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                modifier = Modifier.padding(top = 6.dp),
                                text = category.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        items(category.emojis) { emoji ->
                            val isDisabled = emoji in disabled

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isDisabled) {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable { onToggle(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 20.sp,
                                    modifier = Modifier.alpha(if (isDisabled) 0.3f else 1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun QuickReactionsDialog(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick reactions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tap an emoji to show or hide it above the message menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                LazyVerticalGrid(
                    modifier = Modifier.height(360.dp),
                    columns = GridCells.Adaptive(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(ReactionCatalog) { emoji ->
                        val isSelected = emoji in selected

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { onToggle(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun PinnedReactionsEditor() {
    val settings by AppSettingsStore.state.collectAsState()
    val accountState by ApiManager.accountState.collectAsState()
    var accountId by remember {
        mutableStateOf(
            accountState.activeAccountId ?: accountState.accounts.firstOrNull()?.id
        )
    }
    val session = remember(accountId) {
        accountId?.let { id -> ApiManager.session(id) }
    }
    var pickerOpen by remember { mutableStateOf(false) }

    if (accountState.accounts.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            accountState.accounts.forEach { account ->
                if (account.id == accountId) {
                    Button(onClick = {}) { Text(account.displayName) }
                } else {
                    TextButton(onClick = { accountId = account.id }) {
                        Text(account.displayName)
                    }
                }
            }
        }
    }

    Text(
        text = "Pinned reactions",
        style = MaterialTheme.typography.titleMedium
    )

    Text(
        text = "These reactions stay one tap away above a message's action menu. " +
            "Custom emoji reactions are supported too.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        settings.pinnedCustomReactions.forEach { preference ->
            OutlinedButton(
                onClick = {
                    AppSettingsStore.update { current ->
                        current.copy(
                            pinnedCustomReactions = current.pinnedCustomReactions
                                .filterNot { it.key == preference.key }
                        )
                    }
                }
            ) {
                Text(preference.emoji.ifBlank { "Custom emoji" })
                Text("  ✕", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }

    Button(
        enabled = session != null,
        onClick = { pickerOpen = true }
    ) {
        Text("Add custom reaction")
    }

    if (pickerOpen && session != null) {
        Dialog(
            onDismissRequest = { pickerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                EmojiPicker(
                    session = session,
                    onEmojiSelected = { emoji ->
                        AppSettingsStore.update { current ->
                            current.copy(
                                pinnedCustomReactions = current.pinnedCustomReactions +
                                    ReactionPreference(emoji = emoji)
                            )
                        }
                        pickerOpen = false
                    },
                    onCustomEmojiSelected = { emojiId, fallback ->
                        AppSettingsStore.update { current ->
                            current.copy(
                                pinnedCustomReactions = current.pinnedCustomReactions +
                                    ReactionPreference(emoji = fallback, customEmojiId = emojiId)
                            )
                        }
                        pickerOpen = false
                    },
                    onStickerSelected = { pickerOpen = false },
                    onAnimationSelected = { pickerOpen = false }
                )
            }
        }
    }
}

@Composable
private fun DoubleTapReactionEditor() {
    val settings by AppSettingsStore.state.collectAsState()
    var emojiPickerOpen by remember { mutableStateOf(false) }

    Text(
        text = "Double-tap reaction",
        style = MaterialTheme.typography.titleMedium
    )

    Text(
        text = "Sent when a message is double-tapped on a phone. " +
            "Without an explicit choice the first pinned reaction is used.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    val pinned = settings.quickReactions.map { emoji -> ReactionPreference(emoji) } +
        settings.pinnedCustomReactions
    val current = settings.doubleTapReaction

    Text(
        text = current?.emoji?.ifBlank { "Custom emoji" }
            ?: pinned.firstOrNull()?.emoji?.ifBlank { "Custom emoji" }
            ?: "Default",
        style = MaterialTheme.typography.titleMedium
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        pinned.forEach { preference ->
            val selected = current?.key == preference.key

            OutlinedButton(
                onClick = {
                    AppSettingsStore.update { it.copy(doubleTapReaction = preference) }
                }
            ) {
                Text(
                    text = preference.emoji.ifBlank { "Custom" },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        TextButton(
            onClick = {
                AppSettingsStore.update { it.copy(doubleTapReaction = null) }
            }
        ) {
            Text("First pinned")
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { emojiPickerOpen = true }) {
            Text("Choose another emoji")
        }

        if (current != null) {
            TextButton(
                onClick = {
                    AppSettingsStore.update { it.copy(doubleTapReaction = null) }
                }
            ) {
                Text("Reset")
            }
        }
    }

    if (emojiPickerOpen) {
        AlertDialog(
            onDismissRequest = { emojiPickerOpen = false },
            title = { Text("Double-tap reaction") },
            text = {
                LazyVerticalGrid(
                    modifier = Modifier.height(360.dp),
                    columns = GridCells.Adaptive(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(ReactionCatalog) { emoji ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    AppSettingsStore.update {
                                        it.copy(
                                            doubleTapReaction = ReactionPreference(emoji = emoji)
                                        )
                                    }
                                    emojiPickerOpen = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { emojiPickerOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsNotificationsContent() {
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val accounts = accountState.accounts.filter { account ->
        sessionStates[account.id] is AuthState.Ready
    }

    Text(
        text = "Notification settings are stored on Telegram's servers and apply " +
            "to every device signed in to the account.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    if (accounts.isEmpty()) {
        Text(
            text = "Sign in to an account to change its notification settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        return
    }

    accounts.forEach { account ->
        AccountNotificationSettings(account.id, account.displayName)
    }
}

@Composable
private fun AccountNotificationSettings(accountId: String, displayName: String) {
    val session = remember(accountId) { ApiManager.session(accountId) }
    val scope = rememberCoroutineScope()
    var settings by remember(accountId) { mutableStateOf<NotificationSettings?>(null) }
    var busy by remember(accountId) { mutableStateOf(false) }
    var refreshKey by remember(accountId) { mutableStateOf(0) }
    var exceptions by remember(accountId) {
        mutableStateOf<Map<NotificationScope, List<NotificationException>>>(emptyMap())
    }
    var loadingExceptions by remember(accountId) {
        mutableStateOf<Set<NotificationScope>>(emptySet())
    }
    var busyExceptionId by remember(accountId) { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId, refreshKey) {
        settings = session?.loadNotificationSettings()?.getOrNull()
    }

    // Per-chat overrides are listed under the category they belong to, so the
    // settings screen shows exactly which chats are excluded from the scope.
    LaunchedEffect(accountId, settings) {
        val currentSession = session ?: return@LaunchedEffect

        NotificationScope.entries.forEach { scopeValue ->
            if (scopeValue in exceptions) return@forEach

            loadingExceptions = loadingExceptions + scopeValue

            currentSession.loadNotificationExceptions(scopeValue).onSuccess { loaded ->
                exceptions = exceptions + (scopeValue to loaded)
            }

            loadingExceptions = loadingExceptions - scopeValue
        }
    }

    fun updateException(
        providerDialogId: String,
        transform: (NotificationException) -> NotificationException?
    ) {
        exceptions = exceptions.mapValues { (_, list) ->
            list.mapNotNull { exception ->
                if (exception.providerDialogId == providerDialogId) {
                    transform(exception)
                } else {
                    exception
                }
            }
        }
    }

    fun toggleException(exception: NotificationException, muted: Boolean) {
        val currentSession = session ?: return
        if (busyExceptionId != null) return

        busyExceptionId = exception.providerDialogId

        scope.launch {
            currentSession.setDialogMuted(exception.providerDialogId, muted)
                .onSuccess {
                    updateException(exception.providerDialogId) { current ->
                        current.copy(isMuted = muted)
                    }
                }

            busyExceptionId = null
        }
    }

    fun resetException(exception: NotificationException) {
        val currentSession = session ?: return
        if (busyExceptionId != null) return

        busyExceptionId = exception.providerDialogId

        scope.launch {
            currentSession.resetDialogNotificationSettings(exception.providerDialogId)
                .onSuccess {
                    // Following the scope defaults again removes the chat from
                    // the exceptions list instead of leaving a muted row.
                    updateException(exception.providerDialogId) { null }
                }

            busyExceptionId = null
        }
    }

    fun setScope(scopeValue: NotificationScope, muted: Boolean) {
        val currentSession = session ?: return
        if (busy) return

        busy = true
        scope.launch {
            currentSession.setScopeMuted(scopeValue, muted).onSuccess {
                settings = settings?.let { current ->
                    current.copy(
                        mutedScopes = if (muted) {
                            current.mutedScopes + scopeValue
                        } else {
                            current.mutedScopes - scopeValue
                        }
                    )
                }
            }
            busy = false
        }
    }

    fun setAll(muted: Boolean) {
        val currentSession = session ?: return
        if (busy) return

        busy = true
        scope.launch {
            NotificationScope.entries.forEach { scopeValue ->
                currentSession.setScopeMuted(scopeValue, muted)
            }
            busy = false
            refreshKey++
        }
    }

    Text(
        modifier = Modifier.padding(top = 8.dp),
        text = displayName,
        style = MaterialTheme.typography.titleMedium
    )

    val loaded = settings
    val allMuted = loaded?.allMuted == true

    SettingsSwitch(
        title = "All notifications from this account",
        description = "Mute private chats, groups and channels at once.",
        checked = loaded != null && !allMuted,
        onCheckedChange = { enabled -> setAll(!enabled) }
    )

    NotificationScope.entries.forEach { notificationScope ->
        SettingsSwitch(
            title = notificationScope.title,
            description = if (loaded == null) {
                "Loading server settings..."
            } else if (loaded.isMuted(notificationScope)) {
                "Muted on the server."
            } else {
                "Notifications are enabled."
            },
            checked = loaded?.isMuted(notificationScope) != true,
            onCheckedChange = { enabled -> setScope(notificationScope, !enabled) }
        )

        NotificationExceptionsList(
            scope = notificationScope,
            exceptions = exceptions[notificationScope].orEmpty(),
            isLoading = notificationScope in loadingExceptions,
            busyId = busyExceptionId,
            onToggleMute = ::toggleException,
            onReset = ::resetException
        )
    }

    if (busy) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp))
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
}

@Composable
private fun NotificationExceptionsList(
    scope: NotificationScope,
    exceptions: List<NotificationException>,
    isLoading: Boolean,
    busyId: String?,
    onToggleMute: (NotificationException, Boolean) -> Unit,
    onReset: (NotificationException) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = "${scope.title} exceptions",
            style = MaterialTheme.typography.titleSmall
        )

        when {
            isLoading && exceptions.isEmpty() -> Text(
                text = "Loading exceptions...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            exceptions.isEmpty() -> Text(
                text = "Every chat follows these settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            else -> exceptions.forEach { exception ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(
                        avatar = exception.avatar
                            ?: Avatar.fromName(exception.title),
                        size = 32.dp
                    )

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = exception.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (exception.isMuted) {
                                "Muted for this chat."
                            } else {
                                "Has its own alert settings."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    TextButton(
                        enabled = busyId == null,
                        onClick = { onReset(exception) }
                    ) {
                        Text("Reset")
                    }

                    Switch(
                        checked = exception.isMuted,
                        enabled = busyId == null,
                        onCheckedChange = { muted -> onToggleMute(exception, muted) }
                    )
                }
            }
        }
    }
}

private val HomeTabOptions = listOf(
    "all" to "All",
    "direct" to "DM",
    "groups" to "Groups",
    "channels" to "Channels",
    "bots" to "Bots"
)

private val ReactionCatalog = listOf(
    "👍", "❤️", "🔥", "🎉", "😁", "😂", "🥰", "😍", "🤩", "😎",
    "🤔", "😮", "😢", "😡", "🙏", "👏", "💯", "✅", "❌", "⭐",
    "👀", "🤝", "💔", "🍾", "🤯", "😴", "🌚", "🤡", "🗿", "❤️‍🔥"
)

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
