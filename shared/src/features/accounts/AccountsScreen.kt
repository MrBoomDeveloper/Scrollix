package com.mrboomdev.scrollix.features.accounts

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.outbox.OutboxEntry
import com.mrboomdev.scrollix.data.outbox.OutboxEntryKind
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.data.provider.ProviderRegistry
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.EmptyState
import com.mrboomdev.scrollix.ui.icons.ProviderLogoIcon
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.add_icon
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.send_icon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextOverflow
import com.mrboomdev.scrollix.add
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountsScreen(
    onBack: () -> Unit,
    onOpenLogin: (String) -> Unit,
    onOpenOwnProfile: (String) -> Unit = {},
    onManageAccount: (String) -> Unit = {},
    viewModel: AccountsScreenViewModel = viewModel { AccountsScreenViewModel() }
) {
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val dialogsLoading by ApiManager.dialogsLoading.collectAsState()
    val connectionStates by remember {
        ApiManager.connectionStates
    }.collectAsState(initial = emptyMap())
    val queue by OutboxManager.entries.collectAsState()
    val accountNames = remember(accountState.accounts) {
        accountState.accounts.associate { account -> account.id to account.displayName }
    }
    val confirmDiscard = viewModel.confirmDiscard
    val error = viewModel.error
    val isAdding = viewModel.isAdding
    val providerPickerOpen = viewModel.providerPickerOpen

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("Accounts")
                },
                
                actions = {
                    IconButton(
                        onClick = { viewModel.openProviderPicker() },
                        enabled = !isAdding
                    ) {
                        if (isAdding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                tint = MaterialTheme.colorScheme.primary,
                                imageVector = add_icon,
                                contentDescription = "Add account"
                            )
                        }
                    }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = contentPadding.add(8.dp)
        ) {
            error?.let { message ->
                item(
                    key = "error",
                    contentType = "error"
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(
                items = accountState.accounts,
                key = { it.id }
            ) { account ->
                AccountRow(
                    account = account,
                    authState = sessionStates[account.id],
                    isConnected = connectionStates[account.id],
                    isActivating = false,
                    isDialogsLoading = account.id in dialogsLoading,
                    onOpenLogin = onOpenLogin,
                    onOpenOwnProfile = { onOpenOwnProfile(account.id) },
                    onManage = { onManageAccount(account.id) }
                )
            }

            if (accountState.accounts.isEmpty()) {
                item {
                    EmptyState(
                        modifier = Modifier.fillMaxWidth(),
                        title = "No accounts yet",
                        message = "Add a Telegram, MAX or VK account to see its " +
                            "dialogs here.",
                        icon = account_circle_icon
                    )
                }
            }

            item(key = "outgoing-queue") {
                OutgoingQueueCard(
                    entries = queue,
                    accountNames = accountNames,
                    onRetryAll = { viewModel.retryAll() },
                    onDiscardAll = { viewModel.requestDiscardAll() }
                )
            }
        }
    }

    if (providerPickerOpen) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { viewModel.closeProviderPicker() },
            title = { Text("Add account") },
            
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderRegistry.all().forEach { provider ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Transparent,
                            onClick = {
                                viewModel.closeProviderPicker()
                                viewModel.addAccount(provider.id, onOpenLogin)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProviderLogoIcon(
                                    providerId = provider.id,
                                    size = 20.dp
                                )

                                Text(provider.displayName)
                            }
                        }
                    }
                }
            },
            
            confirmButton = {
                TextButton(onClick = { viewModel.closeProviderPicker() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { viewModel.dismissDiscardAll() },
            title = { Text("Discard the queue?") },
            text = {
                Text(
                    "${queue.size} queued operation(s) are removed and will " +
                        "not be sent. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.discardAll() }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDiscardAll() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AccountRow(
    account: AccountRecord,
    authState: AuthState?,
    isConnected: Boolean?,
    isActivating: Boolean,
    isDialogsLoading: Boolean,
    onOpenLogin: (String) -> Unit,
    onOpenOwnProfile: () -> Unit,
    onManage: () -> Unit
) {
    val layoutInfo = currentAdaptiveLayoutInfo()
    val actionsBottom = layoutInfo.size < AdaptiveLayoutSize.MEDIUM
    
    val providerName = ProviderRegistry.get(account.providerId)?.displayName
        ?: account.providerId
    val isBusy = isActivating
    
    @Composable
    fun actions() {
        Row(
            modifier = if(actionsBottom) Modifier.fillMaxWidth() else Modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val actionModifier = if(actionsBottom) {
                Modifier.width(0.dp).weight(1f)
            } else Modifier
            
            OutlinedButton(
                modifier = actionModifier,
                enabled = !isBusy && authState is AuthState.Ready,
                onClick = onOpenOwnProfile
            ) {
                Text("View profile")
            }
            
            OutlinedButton(
                modifier = actionModifier,
                enabled = !isBusy,
                onClick = onManage
            ) {
                Text("Manage")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        enabled = !isBusy,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = {
            when (authState) {
                is AuthState.Ready -> onOpenOwnProfile()
                is AuthState.Error, is AuthState.Closed -> Unit
                else -> onOpenLogin(account.id)
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarImage(
                    avatar = account.avatar ?: Avatar.fromName(account.displayName),
                    size = 48.dp
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ProviderLogoIcon(
                            providerId = account.providerId,
                            size = 12.dp
                        )

                        Text(
                            text = buildString {
                                append(providerName)
                                account.nickname?.let { append(" · @").append(it) }
                            },
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    AccountStatus(
                        authState = authState,
                        isConnected = isConnected,
                        isActivating = isActivating,
                        isDialogsLoading = isDialogsLoading
                    )
                }

                if(!actionsBottom) {
                    actions()
                }
            }

            if(actionsBottom) {
                actions()
            }
        }
    }
}

@Composable
private fun AccountStatus(
    authState: AuthState?,
    isConnected: Boolean?,
    isActivating: Boolean,
    isDialogsLoading: Boolean
) {
    val isLoading = isActivating ||
        isDialogsLoading ||
        authState == null ||
        authState is AuthState.Initializing

    val isOffline = authState is AuthState.Ready && isConnected == false

    val text = when {
        isActivating -> "Activating..."
        isDialogsLoading -> "Loading chats..."
        isOffline -> "Offline — messages stay queued"
        else -> authState.statusText()
    }

    val color = when {
        isActivating || isDialogsLoading -> MaterialTheme.colorScheme.secondary
        isOffline -> MaterialTheme.colorScheme.error
        authState is AuthState.Error -> MaterialTheme.colorScheme.error
        authState is AuthState.Ready -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = color
            )
        }

        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * The queue used to live in Settings → Offline. It sits next to the accounts
 * now: the same rows show which account is disconnected, and the queue is what
 * keeps its messages waiting, so both belong on one screen.
 */
@Composable
private fun OutgoingQueueCard(
    entries: List<OutboxEntry>,
    accountNames: Map<String, String>,
    onRetryAll: () -> Unit,
    onDiscardAll: () -> Unit
) {
    val sending = entries.count { entry -> entry.state == OutboxEntryState.Sending }
    val failed = entries.count { entry -> entry.state == OutboxEntryState.Failed }
    val waiting = entries.size - sending - failed

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = send_icon,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null
                )

                Text(
                    text = "Outgoing queue",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = when {
                    entries.isEmpty() ->
                        "Nothing is waiting. Messages go out immediately while a " +
                            "connection is available."

                    else -> buildString {
                        append(waiting)
                        append(if (waiting == 1) " message waiting" else " messages waiting")

                        if (sending > 0) {
                            append(", ")
                            append(sending)
                            append(if (sending == 1) " message sending" else " messages sending")
                        }

                        if (failed > 0) {
                            append(", ")
                            append(failed)
                            append(" failed")
                        }

                        append('.')
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            // Every waiting row is listed with its account, so a message
            // queued in another account is not hidden behind a summary.
            for (entry in entries.reversed()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = entry.pendingText.ifBlank { entry.kind.queueLabel },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = entry.state.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (entry.state == OutboxEntryState.Failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }

                    Text(
                        text = buildString {
                            append(accountNames[entry.accountId] ?: entry.accountId)
                            append(" · ")
                            append(entry.kind.queueLabel)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    entry.lastError?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetryAll,
                    enabled = entries.any { entry -> entry.state != OutboxEntryState.Sending }
                ) {
                    Text("Retry all")
                }

                OutlinedButton(
                    onClick = onDiscardAll,
                    enabled = entries.isNotEmpty()
                ) {
                    Text("Discard all")
                }
            }

            Text(
                text = "Discarding removes the queued operations from this device. " +
                    "A message that already reached the server is not deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private val OutboxEntryState.label: String
    get() = when (this) {
        OutboxEntryState.Queued -> "Waiting"
        OutboxEntryState.Sending -> "Sending"
        OutboxEntryState.Failed -> "Failed"
    }

private val OutboxEntryKind.queueLabel: String
    get() = when (this) {
        OutboxEntryKind.Text -> "Message"
        OutboxEntryKind.Files -> "Files"
        OutboxEntryKind.Edit -> "Edit"
        OutboxEntryKind.Delete -> "Deletion"
    }

private fun AuthState?.statusText(): String {
    return when (this) {
        is AuthState.Ready -> "Connected"
        is AuthState.Challenge -> challenge.title
        is AuthState.Error -> message
        is AuthState.Closed -> "Disconnected"
        is AuthState.Initializing, null -> "Connecting..."
    }
}
