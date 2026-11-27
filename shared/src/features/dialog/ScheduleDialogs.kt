package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.ui.components.ConfirmDeleteDialog
import com.mrboomdev.scrollix.ui.icons.delete_icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/**
 * Picks when a message should be sent. The same dialog reschedules an existing
 * scheduled message, which is why it can also offer "Send now".
 */
@Composable
internal fun ScheduleMessageDialog(
    title: String,
    onDismiss: () -> Unit,
    onSchedule: (MessageScheduling) -> Unit,
    onSendNow: (() -> Unit)? = null
) {
    var showCustom by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val nowMillis = remember { currentTimeMillis() }
    val presets = remember(nowMillis) { schedulePresets(nowMillis) }
    val zone = remember { TimeZone.currentSystemDefault() }

    val defaultMoment = remember(nowMillis) {
        Instant.fromEpochMilliseconds(nowMillis + 1.hours.inWholeMilliseconds)
            .toLocalDateTime(zone)
    }

    var date by rememberSaveable {
        mutableStateOf(
            "${defaultMoment.year}-" +
                "${defaultMoment.monthNumber.toString().padStart(2, '0')}-" +
                defaultMoment.dayOfMonth.toString().padStart(2, '0')
        )
    }

    var time by rememberSaveable {
        mutableStateOf(
            "${defaultMoment.hour.toString().padStart(2, '0')}:" +
                defaultMoment.minute.toString().padStart(2, '0')
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                presets.forEach { preset ->
                    ScheduleOptionRow(
                        label = preset.label,
                        onClick = { onSchedule(preset.scheduling) }
                    )
                }

                ScheduleOptionRow(
                    label = "Custom date and time",
                    onClick = { showCustom = !showCustom }
                )

                if (showCustom) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1.4f),
                            value = date,
                            onValueChange = { value -> date = value },
                            singleLine = true,
                            label = { Text("Date") },
                            placeholder = { Text("YYYY-MM-DD") }
                        )

                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = time,
                            onValueChange = { value -> time = value },
                            singleLine = true,
                            label = { Text("Time") },
                            placeholder = { Text("HH:MM") }
                        )
                    }

                    error?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    TextButton(
                        onClick = {
                            val millis = parseScheduleDateTime(date, time, zone)

                            when {
                                millis == null -> {
                                    error = "Use YYYY-MM-DD and HH:MM"
                                }

                                millis <= currentTimeMillis() -> {
                                    error = "Pick a time in the future"
                                }

                                else -> {
                                    error = null
                                    onSchedule(MessageScheduling.At(millis))
                                }
                            }
                        }
                    ) {
                        Text("Schedule")
                    }
                }

                onSendNow?.let { sendNow ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    ScheduleOptionRow(
                        label = "Send now",
                        onClick = sendNow
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ScheduleOptionRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * The chat's schedule queue. Scheduled messages are not part of the history,
 * so this list is the only place they can be re-timed, sent immediately or
 * deleted before they ever reach the other side.
 */
@Composable
internal fun ScheduledMessagesDialog(
    session: AccountSession,
    providerDialogId: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var messages by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rescheduling by remember { mutableStateOf<ChatMessage?>(null) }
    var deleting by remember { mutableStateOf<ChatMessage?>(null) }

    fun reload() {
        scope.launch {
            session.loadScheduledMessages(providerDialogId)
                .onSuccess { loaded -> messages = loaded }
                .onFailure { failure ->
                    error = failure.message ?: "Failed to load scheduled messages"
                }
        }
    }

    fun reschedule(message: ChatMessage, scheduling: MessageScheduling?) {
        scope.launch {
            busy = true
            error = null

            session.rescheduleMessage(providerDialogId, message.messageId, scheduling)
                .onSuccess {
                    rescheduling = null
                    reload()
                }
                .onFailure { failure ->
                    error = failure.message ?: "Failed to reschedule the message"
                }

            busy = false
        }
    }

    fun delete(message: ChatMessage) {
        scope.launch {
            busy = true
            error = null

            session.deleteMessages(
                providerDialogId = providerDialogId,
                messageIds = longArrayOf(message.messageId),
                forEveryone = false
            ).onSuccess {
                messages = messages.orEmpty().filterNot { entry ->
                    entry.messageId == message.messageId
                }
            }.onFailure { failure ->
                error = failure.message ?: "Failed to delete the message"
            }

            busy = false
        }
    }

    LaunchedEffect(providerDialogId) {
        reload()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Scheduled messages") },
        text = {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .heightIn(max = 480.dp)
            ) {
                error?.let { message ->
                    Text(
                        modifier = Modifier.padding(bottom = 6.dp),
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                val current = messages

                when {
                    current == null -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(Modifier.width(24.dp))
                    }

                    current.isEmpty() -> Text(
                        text = "No scheduled messages",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> LazyColumn {
                        items(current, key = { message -> message.rowId }) { message ->
                            ScheduledMessageRow(
                                message = message,
                                enabled = !busy,
                                onSendNow = { reschedule(message, null) },
                                onReschedule = { rescheduling = message },
                                onDelete = { deleting = message }
                            )

                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    deleting?.let { message ->
        ConfirmDeleteDialog(
            title = "Delete the scheduled message?",
            message = "It is removed before it is sent, so the other side never " +
                "sees it. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = null
                delete(message)
            },
            onDismiss = { deleting = null }
        )
    }

    rescheduling?.let { message ->
        ScheduleMessageDialog(
            title = "Reschedule message",
            onDismiss = { rescheduling = null },
            onSchedule = { scheduling -> reschedule(message, scheduling) },
            onSendNow = { reschedule(message, null) }
        )
    }
}

@Composable
private fun ScheduledMessageRow(
    message: ChatMessage,
    enabled: Boolean,
    onSendNow: () -> Unit,
    onReschedule: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = scheduledMessagePreview(message),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = message.scheduling?.scheduleLabel() ?: "Scheduled",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            IconButton(enabled = enabled, onClick = onDelete) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = delete_icon,
                    contentDescription = "Delete scheduled message"
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = enabled, onClick = onSendNow) {
                Text("Send now")
            }

            TextButton(enabled = enabled, onClick = onReschedule) {
                Text("Reschedule")
            }
        }
    }
}

private fun scheduledMessagePreview(message: ChatMessage): String {
    if (message.text.isNotBlank()) return message.text

    val checklistTitle = message.checklist?.title?.ifBlank { "Checklist" }

    return when {
        message.poll != null -> "Poll"
        checklistTitle != null -> checklistTitle
        message.media != null -> "Attachment"
        message.location != null -> "Location"
        else -> "Message"
    }
}
