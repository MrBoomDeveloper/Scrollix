package com.mrboomdev.scrollix.features.accounts

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.provider.ProviderRegistry
import com.mrboomdev.scrollix.features.settings.SettingsDataUsageContent
import com.mrboomdev.scrollix.features.settings.SettingsNavRow
import com.mrboomdev.scrollix.features.settings.SettingsNotificationsContent
import com.mrboomdev.scrollix.features.settings.SettingsPrivacyContent
import com.mrboomdev.scrollix.features.settings.SettingsSessionsContent
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.ConfirmDeleteDialog
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.data_usage_icon
import com.mrboomdev.scrollix.ui.icons.delete_icon
import com.mrboomdev.scrollix.ui.icons.devices_icon
import com.mrboomdev.scrollix.ui.icons.info_icon
import com.mrboomdev.scrollix.ui.icons.notifications_icon
import com.mrboomdev.scrollix.ui.icons.visibility_icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Everything that manages one account lives here: its profile, server-side
 * sessions, privacy, notifications and per-account data usage. The destructive
 * action is a single button at the bottom, behind a confirmation.
 *
 * Wide layouts show the section list and the content side by side; a phone
 * walks the list first and opens one section on top of it.
 */
@Composable
internal fun AccountManageScreen(
    accountId: String,
    onBack: () -> Unit,
    onOpenOwnProfile: (String) -> Unit,
    viewModel: AccountsScreenViewModel = viewModel { AccountsScreenViewModel() }
) {
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val connectionStates by remember { ApiManager.connectionStates }.collectAsState(
        initial = emptyMap()
    )

    val account = remember(accountState.accounts, accountId) {
        accountState.accounts.firstOrNull { record -> record.id == accountId }
    }

    var confirmRemove by remember { mutableStateOf(false) }
    var selected by rememberSaveable(accountId) {
        mutableStateOf(ManageSection.Overview.name)
    }
    var openedOnPhone by rememberSaveable(accountId) { mutableStateOf(false) }

    if (account == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Account not found", style = MaterialTheme.typography.titleMedium)

            TextButton(onClick = onBack) {
                Text("Back")
            }
        }

        return
    }

    val section = ManageSection.entries.firstOrNull { it.name == selected }
        ?: ManageSection.Overview
    val isRemoving = account.id in viewModel.removingIds
    val isConnected = connectionStates[account.id]
    val authState = sessionStates[account.id]

    val details: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge
            )

            when (section) {
                ManageSection.Overview -> AccountOverview(
                    account = account,
                    authState = authState,
                    isConnected = isConnected,
                    isActive = accountState.activeAccountId == account.id,
                    onOpenOwnProfile = { onOpenOwnProfile(account.id) }
                )

                ManageSection.Sessions -> SettingsSessionsContent(account.id)
                ManageSection.Privacy -> SettingsPrivacyContent(account.id)
                ManageSection.Notifications -> SettingsNotificationsContent(account.id)
                ManageSection.DataUsage -> SettingsDataUsageContent(account.id)
            }

            viewModel.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    val removeFooter: @Composable () -> Unit = {
        RemoveAccountFooter(
            account = account,
            isRemoving = isRemoving,
            onRemove = { confirmRemove = true }
        )
    }

    if (isTwoPaneLayout()) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = arrow_back_icon,
                            contentDescription = "Back"
                        )
                    }

                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ManageSection.entries.forEach { entry ->
                    SettingsNavRow(
                        icon = entry.icon,
                        title = entry.title,
                        subtitle = entry.description,
                        selected = entry == section,
                        onClick = { selected = entry.name }
                    )
                }
            }

            VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f)) {
                    details()
                }

                removeFooter()
            }
        }
    } else if (!openedOnPhone) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = arrow_back_icon,
                        contentDescription = "Back"
                    )
                }

                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ManageSection.entries.forEach { entry ->
                SettingsNavRow(
                    icon = entry.icon,
                    title = entry.title,
                    subtitle = entry.description,
                    selected = false,
                    onClick = {
                        selected = entry.name
                        openedOnPhone = true
                    }
                )
            }

            viewModel.error?.let { message ->
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            removeFooter()
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openedOnPhone = false }) {
                    Icon(
                        imageVector = arrow_back_icon,
                        contentDescription = "Back"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            Box(Modifier.weight(1f)) {
                details()
            }

            removeFooter()
        }
    }

    // Removing an account is irreversible on this device, so it always goes
    // through the shared destructive-action confirmation.
    if (confirmRemove) {
        ConfirmDeleteDialog(
            title = "Remove ${account.displayName}?",
            message = "The account is signed out on this device and its local data, " +
                "cached messages and queued sends are deleted. The account itself " +
                "is not deleted from the provider.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmRemove = false
                viewModel.removeAccount(account.id)
                onBack()
            },
            onDismiss = { confirmRemove = false }
        )
    }
}

@Composable
private fun AccountOverview(
    account: AccountRecord,
    authState: AuthState?,
    isConnected: Boolean?,
    isActive: Boolean,
    onOpenOwnProfile: () -> Unit
) {
    val providerName = ProviderRegistry.get(account.providerId)?.displayName
        ?: account.providerId

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                avatar = account.avatar ?: Avatar.fromName(account.displayName),
                size = 56.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = buildString {
                        append(providerName)
                        account.nickname?.let { nickname -> append(" · @").append(nickname) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = accountStatusText(authState, isConnected),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        authState is AuthState.Ready && isConnected == false ->
                            MaterialTheme.colorScheme.error

                        authState is AuthState.Ready -> MaterialTheme.colorScheme.primary
                        authState is AuthState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )

                if (isActive) {
                    Text(
                        text = "Used by default for settings pickers and the QR scanner",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = authState is AuthState.Ready,
        onClick = onOpenOwnProfile
    ) {
        Text("View profile")
    }
}

@Composable
private fun RemoveAccountFooter(
    account: AccountRecord,
    isRemoving: Boolean,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        Text(
            text = "Remove account",
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = "Signs ${account.displayName} out on this device and deletes its " +
                "local data, cache and queued sends. The account stays on the " +
                "provider's servers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        OutlinedButton(
            enabled = !isRemoving,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            onClick = onRemove
        ) {
            if (isRemoving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = delete_icon,
                    contentDescription = null
                )

                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = "Remove account"
                )
            }
        }
    }
}

private fun accountStatusText(authState: AuthState?, isConnected: Boolean?): String {
    return when {
        authState is AuthState.Ready && isConnected == false ->
            "Offline — messages stay queued"

        authState is AuthState.Ready -> "Connected"
        authState is AuthState.Challenge -> authState.challenge.title
        authState is AuthState.Error -> authState.message
        authState is AuthState.Closed -> "Disconnected"
        else -> "Connecting..."
    }
}

/** Sections of the account management screen. */
private enum class ManageSection(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    Overview("Overview", "Profile, status and the default account", info_icon),
    Sessions("Sessions", "Devices signed in to this account", devices_icon),
    Privacy("Privacy", "Who can see your activity", visibility_icon),
    Notifications("Notifications", "Mutes and per-chat exceptions", notifications_icon),
    DataUsage("Data and network", "Traffic on this device and data saver", data_usage_icon)
}
