package com.mrboomdev.scrollix.features.settings

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.BusinessHoursInterval
import com.mrboomdev.scrollix.data.model.BusinessQuickReply
import com.mrboomdev.scrollix.data.model.BusinessSettings
import com.mrboomdev.scrollix.ui.components.ConfirmDeleteDialog
import com.mrboomdev.scrollix.ui.icons.reply_icon
import com.mrboomdev.scrollix.ui.icons.schedule_icon
import com.mrboomdev.scrollix.ui.notice.AppNotice
import com.mrboomdev.scrollix.ui.notice.NoticeCenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

/**
 * Telegram Business features of the active account: the greeting and away
 * messages it sends for the business, its opening hours and its quick reply
 * shortcuts. Everything is stored on the server, so the editor reads the
 * account's full profile before showing what is currently set.
 */
@Composable
fun SettingsBusinessContent(accountId: String? = null) {
    val accountState by ApiManager.accountState.collectAsState()
    val scope = rememberCoroutineScope()

    val selectedAccountId = accountId
        ?: accountState.activeAccountId
        ?: accountState.accounts.firstOrNull()?.id

    val session = selectedAccountId?.let { id -> ApiManager.session(id) }

    // Providers without business features do not show the editor at all.
    if (session != null && !session.supportsBusinessSettings) return

    var settings by remember(selectedAccountId) { mutableStateOf<BusinessSettings?>(null) }
    var isLoading by remember(selectedAccountId) { mutableStateOf(true) }
    var greeting by remember(selectedAccountId) { mutableStateOf("") }
    var away by remember(selectedAccountId) { mutableStateOf("") }
    var awayOutsideHours by remember(selectedAccountId) { mutableStateOf(false) }
    var hours by remember(selectedAccountId) {
        mutableStateOf<List<BusinessHoursInterval>>(emptyList())
    }
    var timeZone by remember(selectedAccountId) { mutableStateOf("") }
    var newReplyName by remember(selectedAccountId) { mutableStateOf("") }
    var newReplyText by remember(selectedAccountId) { mutableStateOf("") }
    var pendingDelete by remember(selectedAccountId) {
        mutableStateOf<BusinessQuickReply?>(null)
    }
    var isSaving by remember(selectedAccountId) { mutableStateOf(false) }

    val quickReplies by session?.businessQuickReplies?.collectAsState()
        ?: remember { mutableStateOf(emptyList<BusinessQuickReply>()) }

    fun reload() {
        val active = session ?: return

        scope.launch {
            isLoading = true

            active.loadBusinessSettings()
                .onSuccess { loaded ->
                    settings = loaded
                    greeting = loaded.greeting.orEmpty()
                    away = loaded.away.orEmpty()
                    awayOutsideHours = loaded.awayOutsideHours
                    hours = loaded.openingHours
                    timeZone = loaded.timeZoneId
                }
                .onFailure { failure ->
                    NoticeCenter.error(failure.message ?: "Business settings are unavailable")
                }

            isLoading = false
        }
    }

    LaunchedEffect(selectedAccountId) { reload() }

    fun submit(action: suspend () -> Result<Unit>) {
        scope.launch {
            isSaving = true

            action()
                .onSuccess { reload() }
                .onFailure { failure ->
                    NoticeCenter.error(failure.message ?: "The setting was not saved")
                }

            isSaving = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val loaded = settings

        when {
            isLoading -> Text(
                text = "Loading business settings...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            loaded == null || !loaded.isAvailable -> Text(
                text = "This account has no Telegram Business subscription. " +
                    "Greeting and away messages, opening hours and quick replies " +
                    "need one; nothing else in the app is affected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            else -> {
                SettingsSectionTitle(icon = reply_icon, title = "Greeting message")

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = greeting,
                    onValueChange = { greeting = it },
                    label = { Text("Text sent to a new chat") },
                    minLines = 2,
                    enabled = !isSaving
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = greeting.isNotBlank() && !isSaving,
                        onClick = { submit { session?.setBusinessGreeting(greeting) ?: fail() } }
                    ) {
                        Text("Save greeting")
                    }

                    if (loaded.greeting != null) {
                        TextButton(
                            enabled = !isSaving,
                            onClick = {
                                greeting = ""
                                submit { session?.setBusinessGreeting(null) ?: fail() }
                            }
                        ) {
                            Text("Turn off")
                        }
                    }
                }

                HorizontalDivider()

                SettingsSectionTitle(icon = schedule_icon, title = "Away message")

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = away,
                    onValueChange = { away = it },
                    label = { Text("Text sent while you are away") },
                    minLines = 2,
                    enabled = !isSaving
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "Only outside opening hours",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    androidx.compose.material3.Switch(
                        checked = awayOutsideHours,
                        onCheckedChange = { awayOutsideHours = it }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = away.isNotBlank() && !isSaving,
                        onClick = {
                            submit {
                                session?.setBusinessAway(away, awayOutsideHours) ?: fail()
                            }
                        }
                    ) {
                        Text("Save away message")
                    }

                    if (loaded.away != null) {
                        TextButton(
                            enabled = !isSaving,
                            onClick = {
                                away = ""
                                submit { session?.setBusinessAway(null) ?: fail() }
                            }
                        ) {
                            Text("Turn off")
                        }
                    }
                }

                HorizontalDivider()

                SettingsSectionTitle(icon = schedule_icon, title = "Opening hours")

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = timeZone,
                    onValueChange = { timeZone = it },
                    label = { Text("Time zone, for example Europe/Berlin") },
                    singleLine = true,
                    enabled = !isSaving
                )

                if (hours.isEmpty()) {
                    Text(
                        text = "No hours set. Add the first interval as HH:MM, for " +
                            "example 09:00 and 18:00.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                hours.forEachIndexed { index, interval ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = interval.label,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        TextButton(
                            enabled = !isSaving,
                            onClick = { hours = hours.toMutableList().also { it.removeAt(index) } }
                        ) {
                            Text("Remove")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = {
                            val last = hours.lastOrNull()
                            val nextStart = last?.endMinute?.takeIf { it < 24 * 60 }
                                ?: 9 * 60

                            hours = hours + BusinessHoursInterval(
                                startMinute = nextStart,
                                endMinute = (nextStart + 60).coerceAtMost(24 * 60)
                            )
                        }
                    ) {
                        Text("Add interval")
                    }

                    Button(
                        enabled = timeZone.isNotBlank() && !isSaving,
                        onClick = {
                            submit {
                                session?.setBusinessOpeningHours(
                                    timeZoneId = timeZone.trim(),
                                    intervals = hours
                                ) ?: fail()
                            }
                        }
                    ) {
                        Text("Save hours")
                    }
                }

                HorizontalDivider()

                SettingsSectionTitle(icon = reply_icon, title = "Quick replies")

                if (quickReplies.isEmpty()) {
                    Text(
                        text = "No quick replies yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                quickReplies.forEach { reply ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reply.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (reply.preview.isNotBlank()) {
                                Text(
                                    text = reply.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        TextButton(
                            enabled = !isSaving,
                            onClick = { pendingDelete = reply }
                        ) {
                            Text("Remove")
                        }
                    }
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newReplyName,
                    onValueChange = { newReplyName = it },
                    label = { Text("Shortcut name") },
                    singleLine = true,
                    enabled = !isSaving
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newReplyText,
                    onValueChange = { newReplyText = it },
                    label = { Text("Message") },
                    minLines = 2,
                    enabled = !isSaving
                )

                Button(
                    enabled = newReplyName.isNotBlank() && newReplyText.isNotBlank() && !isSaving,
                    onClick = {
                        submit {
                            val result = session?.createBusinessQuickReply(
                                name = newReplyName.trim(),
                                text = newReplyText.trim()
                            ) ?: fail()

                            if (result.isSuccess) {
                                newReplyName = ""
                                newReplyText = ""
                            }

                            result
                        }
                    }
                ) {
                    Text("Add quick reply")
                }
            }
        }
    }

    pendingDelete?.let { reply ->
        ConfirmDeleteDialog(
            title = "Delete quick reply?",
            message = "\"${reply.name}\" and its ${reply.messageCount} message(s) " +
                "will be deleted from your account.",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                submit { session?.deleteBusinessQuickReply(reply.id) ?: fail() }
            }
        )
    }
}

private fun fail(): Result<Unit> = Result.failure(IllegalStateException("No account is signed in"))
