package com.mrboomdev.scrollix.features.settings

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.emoji.EmojiCatalog
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.ReactionPreference
import com.mrboomdev.scrollix.ui.components.EmojiPicker
import com.mrboomdev.scrollix.ui.icons.push_pin_icon
import com.mrboomdev.scrollix.ui.icons.touch_app_icon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun SystemEmojiDialog(
    disabled: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                                        if(isDisabled) {
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
internal fun QuickReactionsDialog(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                                    if(isSelected) {
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
internal fun PinnedReactionsEditor() {
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

    SettingsSectionTitle(icon = push_pin_icon, title = "Pinned reactions")

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
internal fun DoubleTapReactionEditor() {
    val settings by AppSettingsStore.state.collectAsState()
    var emojiPickerOpen by remember { mutableStateOf(false) }

    SettingsSectionTitle(icon = touch_app_icon, title = "Double-tap reaction")

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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
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

internal val HomeTabOptions = listOf(
    "all" to "All",
    "direct" to "DM",
    "groups" to "Groups",
    "channels" to "Channels",
    "bots" to "Bots"
)

internal val ReactionCatalog = listOf(
    "👍", "❤️", "🔥", "🎉", "😁", "😂", "🥰", "😍", "🤩", "😎",
    "🤔", "😮", "😢", "😡", "🙏", "👏", "💯", "✅", "❌", "⭐",
    "👀", "🤝", "💔", "🍾", "🤯", "😴", "🌚", "🤡", "🗿", "❤️‍🔥"
)
