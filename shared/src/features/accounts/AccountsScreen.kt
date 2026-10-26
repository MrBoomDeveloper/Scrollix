package com.mrboomdev.scrollix.features.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.provider.ProviderRegistry
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.add_icon
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onOpenLogin: (String) -> Unit
) {
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    fun addAccount() {
        scope.launch {
            runCatching {
                ApiManager.addAccount("telegram")
            }.onSuccess { accountId ->
                error = null
                onOpenLogin(accountId)
            }.onFailure { exception ->
                error = exception.message ?: "Failed to add account"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = arrow_back_icon,
                    contentDescription = null
                )
            }

            Text(
                modifier = Modifier.weight(1f),
                text = "Accounts",
                style = MaterialTheme.typography.titleLarge
            )

            IconButton(onClick = ::addAccount) {
                Icon(
                    tint = MaterialTheme.colorScheme.primary,
                    imageVector = add_icon,
                    contentDescription = "Add account"
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        error?.let { message ->
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(
                items = accountState.accounts,
                key = { it.id }
            ) { account ->
                AccountRow(
                    account = account,
                    isActive = account.id == accountState.activeAccountId,
                    authState = sessionStates[account.id],
                    onOpenLogin = onOpenLogin,
                    onRemove = {
                        scope.launch {
                            runCatching {
                                ApiManager.removeAccount(account.id)
                            }.onFailure { exception ->
                                error = exception.message ?: "Failed to remove account"
                            }
                        }
                    },
                    onSetActive = {
                        scope.launch {
                            ApiManager.setActiveAccount(account.id)
                        }
                    }
                )
            }

            if (accountState.accounts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No accounts yet",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Add a Telegram account to see its dialogs here.",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    onClick = ::addAccount
                ) {
                    Text("Add account")
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountRecord,
    isActive: Boolean,
    authState: AuthState?,
    onOpenLogin: (String) -> Unit,
    onRemove: () -> Unit,
    onSetActive: () -> Unit
) {
    val providerName = ProviderRegistry.get(account.providerId)?.displayName
        ?: account.providerId

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = {
            if (authState is AuthState.Ready) {
                onSetActive()
            } else {
                onOpenLogin(account.id)
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                avatar = account.avatar ?: Avatar.fromName(account.displayName),
                size = 48.dp
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )

                    if (isActive) {
                        Text(
                            text = "Active",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text(
                    text = buildString {
                        append(providerName)
                        account.nickname?.let { append(" · @").append(it) }
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )

                Text(
                    text = authState.statusText(),
                    color = when (authState) {
                        is AuthState.Error -> MaterialTheme.colorScheme.error
                        is AuthState.Ready -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            if (authState !is AuthState.Ready) {
                TextButton(onClick = { onOpenLogin(account.id) }) {
                    Text("Login")
                }
            }

            TextButton(onClick = onRemove) {
                Text(
                    text = "Remove",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
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
