package com.mrboomdev.scrollix.features.settings

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.CacheStore
import com.mrboomdev.scrollix.data.storage.keepMediaKey
import com.mrboomdev.scrollix.features.home.key
import com.mrboomdev.scrollix.ui.icons.keep_icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One cached chat offered in the keep-media picker. */
private data class KeepMediaChat(
    val account: AccountRecord,
    val dialog: Dialog
) {
    val key: String get() = keepMediaKey(account.id, dialog.providerDialogId)
}

/** A kept chat resolved to a readable title. */
private data class KeptChat(
    val key: String,
    val title: String,
    val accountName: String
)

/**
 * Storage → Keep media. Kept chats are listed per account and can be chosen or
 * released here, so a chat no longer has to be opened to change its protection.
 * Protecting only affects media referenced by cached messages of that chat.
 */
@Composable
internal fun KeepMediaSection(onChanged: () -> Unit = {}) {
    val settings by AppSettingsStore.state.collectAsState()
    val accountState by ApiManager.accountState.collectAsState()
    val scope = rememberCoroutineScope()
    var pickerOpen by remember { mutableStateOf(false) }
    var resolved by remember { mutableStateOf<Map<String, KeptChat>>(emptyMap()) }
    var loadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(accountState.accounts, settings.keepMediaDialogIds, loadKey) {
        val accounts = accountState.accounts
        val cached = withContext(Dispatchers.IO) {
            accounts.associateWith { account ->
                CacheStore.loadDialogs(account.id)
            }
        }

        resolved = buildMap {
            cached.forEach { (account, dialogs) ->
                dialogs.forEach { dialog ->
                    put(
                        keepMediaKey(account.id, dialog.providerDialogId),
                        KeptChat(
                            key = keepMediaKey(account.id, dialog.providerDialogId),
                            title = dialog.title,
                            accountName = account.displayName
                        )
                    )
                }
            }
        }
    }

    fun setKept(key: String, kept: Boolean) {
        AppSettingsStore.update { current ->
            current.copy(
                keepMediaDialogIds = if (kept) {
                    current.keepMediaDialogIds + key
                } else {
                    current.keepMediaDialogIds - key
                }
            )
        }

        scope.launch {
            withContext(Dispatchers.IO) { CacheStore.refreshMediaProtection() }
            loadKey++
            onChanged()
        }
    }

    SettingsSectionTitle(icon = keep_icon, title = "Keep media")

    Text(
        text = "Media used by cached messages of a kept chat is never removed by " +
            "the cache limit, including photos, albums and voice notes. Kept media " +
            "still counts toward the limit.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { pickerOpen = true },
            enabled = accountState.accounts.isNotEmpty()
        ) {
            Text("Choose chats")
        }

        if (settings.keepMediaDialogIds.isNotEmpty()) {
            TextButton(
                onClick = {
                    AppSettingsStore.update { current ->
                        current.copy(keepMediaDialogIds = emptySet())
                    }

                    scope.launch {
                        withContext(Dispatchers.IO) { CacheStore.refreshMediaProtection() }
                        loadKey++
                        onChanged()
                    }
                }
            ) {
                Text("Release all")
            }
        }
    }

    if (settings.keepMediaDialogIds.isEmpty()) {
        Text(
            text = "No chats are kept. Media from every chat can be removed when the " +
                "limit is reached.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }

    // Resolved titles are shown when the chat is still in the cache; a chat
    // that is not cached any more keeps its entry and shows the raw id.
    val chats = settings.keepMediaDialogIds
        .map { key -> resolved[key] ?: KeptChat(key, key, "") }
        .sortedBy { chat -> chat.accountName + chat.title }

    chats.forEach { chat ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (chat.accountName.isNotBlank()) {
                    Text(
                        text = chat.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            TextButton(onClick = { setKept(chat.key, false) }) {
                Text("Release")
            }
        }
    }

    if (pickerOpen) {
        KeepMediaPickerDialog(
            onDismiss = { pickerOpen = false },
            onToggle = ::setKept
        )
    }
}

@Composable
private fun KeepMediaPickerDialog(
    onDismiss: () -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    val settings by AppSettingsStore.state.collectAsState()
    val accountState by ApiManager.accountState.collectAsState()
    var chats by remember { mutableStateOf<List<KeepMediaChat>?>(null) }

    LaunchedEffect(accountState.accounts) {
        val accounts = accountState.accounts
        val loaded = withContext(Dispatchers.IO) {
            accounts.flatMap { account ->
                CacheStore.loadDialogs(account.id).map { dialog ->
                    KeepMediaChat(account, dialog)
                }
            }
        }

        chats = loaded.sortedBy { chat -> chat.account.displayName + chat.dialog.title }
    }

    val loaded = chats

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text("Keep media") },
        text = {
            when {
                loaded == null -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("Loading chats...")
                }

                loaded.isEmpty() -> Text(
                    "No chats are cached yet. Open a chat once so it appears here."
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(loaded, key = { chat -> chat.key }) { chat ->
                        val checked = chat.key in settings.keepMediaDialogIds

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(chat.key, !checked) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value -> onToggle(chat.key, value) }
                            )

                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = chat.dialog.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = chat.account.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
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
