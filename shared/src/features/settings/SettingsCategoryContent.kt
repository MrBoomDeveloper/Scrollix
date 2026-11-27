package com.mrboomdev.scrollix.features.settings

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DefaultQuickReactions
import com.mrboomdev.scrollix.features.home.key
import com.mrboomdev.scrollix.ui.icons.bug_report_icon
import com.mrboomdev.scrollix.ui.icons.folder_icon
import com.mrboomdev.scrollix.ui.icons.history_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.image_icon
import com.mrboomdev.scrollix.ui.icons.merge_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon
import com.mrboomdev.scrollix.ui.icons.tab_icon
import com.mrboomdev.scrollix.ui.icons.touch_app_icon
import com.mrboomdev.scrollix.ui.icons.view_list_icon

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsCategoryContent(
    modifier: Modifier = Modifier,
    category: SettingsCategory,
    settings: AppSettings,
    onPickWallpaper: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    highlightTitle: String? = null,
    onHighlightDismiss: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val rowOffsets = remember { mutableMapOf<String, Int>() }

    // A search result opens this section with the row title it matched. Rows
    // register their offset while composing, so the section scrolls to that
    // row and tints it briefly instead of dropping the user at the top.
    LaunchedEffect(category, highlightTitle) {
        if (highlightTitle == null) return@LaunchedEffect

        withFrameNanos { }

        val offset = rowOffsets.entries
            .filter { (title, _) -> title.matchesSettingsQuery(highlightTitle) }
            .maxByOrNull { (title, _) -> title.length }
            ?.value

        if (offset != null) {
            scrollState.animateScrollTo((offset - 24).coerceAtLeast(0))
        }

        delay(2_500L)
        onHighlightDismiss()
    }

    CompositionLocalProvider(
        LocalSettingsHighlightTitle provides highlightTitle,
        LocalSettingsRowOffsets provides rowOffsets
    ) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleLarge
        )

        when (category) {
            SettingsCategory.ChatList -> {
                SettingsSectionTitle(icon = image_icon, title = "Chat wallpaper")

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
                    icon = tab_icon,
                    title = "Show the \"All\" tab",
                    description = "Add a chip that displays every chat at once.",
                    checked = settings.showAllChatsTab,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(showAllChatsTab = value) }
                    }
                )

                SettingsSwitch(
                    icon = view_list_icon,
                    title = "Single row filters",
                    description = "Lay out folder chips in one scrollable row.",
                    checked = settings.singleRowFilters,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(singleRowFilters = value) }
                    }
                )

                SettingsSwitch(
                    icon = merge_icon,
                    title = "Merge duplicate chats",
                    description = "Show the same chat from several accounts as one " +
                        "row with all account avatars and pick the account on tap.",
                    checked = settings.mergeDuplicateDialogs,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(mergeDuplicateDialogs = value) }
                    }
                )

                SettingsSwitch(
                    icon = folder_icon,
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
                                        SettingsRowGroup(title = "Reorder sticker packs") {
    Button(onClick = { reorderKind = StickerPackKind.Sticker }) {
                            Text("Reorder sticker packs")
                        }
                    }

                                        SettingsRowGroup(title = "Reorder emoji packs") {
    Button(onClick = { reorderKind = StickerPackKind.CustomEmoji }) {
                            Text("Reorder emoji packs")
                        }
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
                    icon = history_icon,
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
                    icon = mood_icon,
                    title = "System emoji",
                    description = "Show the standard emoji categories in the picker.",
                    checked = settings.showSystemEmojis,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(showSystemEmojis = value) }
                    }
                )

                var emojiEditorOpen by remember { mutableStateOf(false) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SettingsRowGroup(title = "Choose emoji") {
    Button(
                            onClick = { emojiEditorOpen = true },
                            enabled = settings.showSystemEmojis
                        ) {
                            Text("Choose emoji")
                        }
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

                SettingsRowGroup(title = "Quick reactions") {
                    Text(
                        text = "Quick reactions",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

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

                SettingsSectionTitle(icon = home_icon, title = "Default home tab")

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

            SettingsCategory.Accessibility -> {
                Text(
                    text = "Motion is only decoration here: turning it off does not " +
                        "disable any feature.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                SettingsSwitch(
                    icon = touch_app_icon,
                    title = "Reduce motion",
                    description = "New messages appear without sliding in and profile " +
                        "avatars stop flying between screens.",
                    checked = settings.reduceMotion,
                    onCheckedChange = { enabled ->
                        AppSettingsStore.update { current ->
                            current.copy(reduceMotion = enabled)
                        }
                    }
                )
            }

            SettingsCategory.Notifications -> SettingsNotificationsContent()

            SettingsCategory.Backup -> SettingsBackupContent()

            SettingsCategory.Storage -> SettingsStorageContent()

            SettingsCategory.Privacy -> SettingsPrivacyContent()

            SettingsCategory.Sessions -> SettingsSessionsContent()

            SettingsCategory.DataUsage -> SettingsDataUsageContent()

            SettingsCategory.Diagnostics -> {
                Text(
                    text = "Scrollix records recent warnings and errors in memory so a bug " +
                        "report can include them. Nothing is uploaded automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                SettingsSwitch(
                    icon = bug_report_icon,
                    title = "Save crash reports",
                    description = "When Scrollix crashes, a report with the last log lines " +
                        "is saved on this device. Nothing is uploaded automatically.",
                    checked = settings.crashReportsEnabled,
                    onCheckedChange = { enabled ->
                        AppSettingsStore.update { current ->
                            current.copy(crashReportsEnabled = enabled)
                        }
                    }
                )

                Button(onClick = onOpenDiagnostics) {
                    Text("Open diagnostics")
                }

                Text(
                    text = "The log is kept in memory and a rotating copy is saved " +
                        "on this device. Nothing is uploaded automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            SettingsCategory.About -> SettingsAboutContent()
        }
    }
    }
}
