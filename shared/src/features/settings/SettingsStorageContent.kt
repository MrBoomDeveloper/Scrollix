package com.mrboomdev.scrollix.features.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.SettingsImportPreview
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.MediaCacheLimitOptionsMb
import com.mrboomdev.scrollix.data.storage.UnlimitedMediaCacheBytes
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.ui.components.ReorderDialog
import com.mrboomdev.scrollix.ui.components.ReorderItem
import com.mrboomdev.scrollix.ui.components.orderedStickerPacks
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.icons.storage_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsStorageContent() {
    val scope = rememberCoroutineScope()
    val settings by AppSettingsStore.state.collectAsState()
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

    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

    SettingsSectionTitle(icon = storage_icon, title = "Media cache limit")

    Text(
        text = "When cached stickers, custom emoji and avatars pass this size, the " +
            "oldest entries are removed automatically. Dialogs and cached messages " +
            "are never evicted.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MediaCacheLimitOptionsMb.forEach { megabytes ->
            val bytes = megabytes * 1024L * 1024L
            val isSelected = settings.mediaCacheLimitBytes == bytes

            if (isSelected) {
                Button(onClick = {}) {
                    Text("$megabytes MB")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        AppSettingsStore.update { current ->
                            current.copy(mediaCacheLimitBytes = bytes)
                        }

                        scope.launch {
                            withContext(Dispatchers.IO) {
                                CacheStore.enforceMediaLimit(bytes)
                            }
                            refreshKey++
                        }
                    }
                ) {
                    Text("$megabytes MB")
                }
            }
        }

        val unlimited = settings.mediaCacheLimitBytes == UnlimitedMediaCacheBytes

        if (unlimited) {
            Button(onClick = {}) {
                Text("Unlimited")
            }
        } else {
            OutlinedButton(
                onClick = {
                    AppSettingsStore.update { current ->
                        current.copy(mediaCacheLimitBytes = UnlimitedMediaCacheBytes)
                    }
                }
            ) {
                Text("Unlimited")
            }
        }
    }

    Text(
        text = "Media cache: ${formatCacheSize(sizes[CacheStore.Category.Media] ?: 0)}" +
            if (settings.mediaCacheLimitBytes == UnlimitedMediaCacheBytes) {
                " (no limit)"
            } else {
                " of ${formatCacheSize(settings.mediaCacheLimitBytes)}"
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

    KeepMediaSection(onChanged = { refreshKey++ })

    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

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
internal fun PackOrderDialog(
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

internal fun formatCacheSize(bytes: Long): String = formatDataSize(bytes)

@Composable
internal fun SettingsBackupContent() {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingRestore?>(null) }

    val filePicker = rememberFilePicker(
        onImagesPicked = {},
        onFilesPicked = { picked ->
            picked.firstOrNull()?.let { file ->
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        readFileBytes(file.path)?.decodeToString()
                    }

                    if (text == null) {
                        message = "Could not read ${file.name}."
                        return@launch
                    }

                    // Nothing is written before the preview is confirmed.
                    when (val preview = AppSettingsStore.previewImport(text)) {
                        is SettingsImportPreview.Refused -> message = preview.reason
                        is SettingsImportPreview.Ready -> {
                            message = null
                            pending = PendingRestore(
                                fileName = file.name,
                                preview = preview
                            )
                        }
                    }
                }
            }
        }
    )

    Text(
        text = "The backup keeps your chat list preferences, reactions, emoji " +
            "settings, storage limits and the data saver profile. Wallpapers and " +
            "accounts are not included. Restoring shows what would change first.",
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

    pending?.let { restore ->
        RestorePreviewDialog(
            fileName = restore.fileName,
            preview = restore.preview,
            onDismiss = { pending = null },
            onConfirm = {
                AppSettingsStore.applyImport(restore.preview.settings)
                pending = null
                message = "Restored settings from ${restore.fileName}."
            }
        )
    }
}

/** A parsed backup waiting for the user to confirm the preview. */
internal data class PendingRestore(
    val fileName: String,
    val preview: SettingsImportPreview.Ready
)

@Composable
internal fun RestorePreviewDialog(
    fileName: String,
    preview: SettingsImportPreview.Ready,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from $fileName?") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (preview.changes.isEmpty()) {
                    Text(
                        "Every setting in the file already has this value on this " +
                            "device. Restoring changes nothing."
                    )
                } else {
                    Text(
                        text = "${preview.changes.size} setting(s) would change:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    preview.changes.take(12).forEach { change ->
                        Text(
                            text = change,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (preview.changes.size > 12) {
                        Text(
                            text = "and ${preview.changes.size - 12} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = "Wallpapers and accounts are not part of a backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
