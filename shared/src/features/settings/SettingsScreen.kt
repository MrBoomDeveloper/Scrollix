package com.mrboomdev.scrollix.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.DefaultQuickReactions
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.ui.components.EmojiCatalog
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mrboomdev.scrollix.isTwoPaneLayout

private enum class SettingsCategory(
    val title: String,
    val description: String
) {
    ChatList("Chat list", "Folders, filters and chat sorting"),
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
    Column(modifier) {
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
        modifier = modifier.padding(16.dp),
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
                    title = "Use Telegram folders",
                    description = "Show native server-side chat folders.",
                    checked = settings.useNativeFolders,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(useNativeFolders = value) }
                    }
                )

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

            SettingsCategory.About -> {
                Text(
                    text = "Scrollix",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Telegram client built with Compose Multiplatform.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
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
