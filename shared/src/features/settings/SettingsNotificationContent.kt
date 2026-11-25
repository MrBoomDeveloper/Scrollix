package com.mrboomdev.scrollix.features.settings

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.NotificationException
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.NotificationSettings
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.notifications_icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun SettingsNotificationsContent() {
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
internal fun AccountNotificationSettings(accountId: String, displayName: String) {
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
        icon = notifications_icon,
        title = "All notifications from this account",
        description = "Mute private chats, groups and channels at once.",
        checked = loaded != null && !allMuted,
        onCheckedChange = { enabled -> setAll(!enabled) }
    )

    NotificationScope.entries.forEach { notificationScope ->
        SettingsSwitch(
            icon = notificationScope.icon,
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
internal fun NotificationExceptionsList(
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
