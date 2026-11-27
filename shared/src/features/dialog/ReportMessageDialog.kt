package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageReportOption
import com.mrboomdev.scrollix.data.model.MessageReportResult
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.notice.AppNotice
import com.mrboomdev.scrollix.ui.notice.NoticeCenter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface ReportUiState {
    data object Loading : ReportUiState
    data class Choice(
        val title: String,
        val options: List<MessageReportOption>
    ) : ReportUiState

    data class Comment(
        val optionId: String,
        val isOptional: Boolean
    ) : ReportUiState

    data class Failure(val message: String) : ReportUiState
}

/**
 * Reports one message. The provider decides what it needs: the first call only
 * asks for requirements, and the reason list comes from the provider itself, so
 * a new TDLib reason does not need a client update.
 */
@Composable
internal fun ReportMessageDialog(
    session: AccountSession,
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf<ReportUiState>(ReportUiState.Loading) }
    var selectedOption by remember { mutableStateOf<MessageReportOption?>(null) }
    var comment by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun applyResult(result: MessageReportResult) {
        when (result) {
            MessageReportResult.Reported -> {
                NoticeCenter.post(AppNotice("Report sent"))
                onDismiss()
            }

            is MessageReportResult.OptionsRequired -> {
                state = ReportUiState.Choice(result.title, result.options)
            }

            is MessageReportResult.CommentRequired -> {
                state = ReportUiState.Comment(
                    optionId = result.optionId,
                    isOptional = result.isOptional
                )
            }

            MessageReportResult.MessagesRequired -> {
                state = ReportUiState.Failure("The provider did not accept this message")
            }
        }
    }

    fun send(optionId: String?, text: String) {
        if (isSending) return
        isSending = true
        sendError = null

        scope.launch {
            session.reportMessage(
                providerDialogId = message.providerDialogId,
                messageIds = message.allMessageIds.toLongArray(),
                optionId = optionId,
                comment = text
            ).onSuccess { result ->
                isSending = false
                applyResult(result)
            }.onFailure { failure ->
                isSending = false
                sendError = failure.message ?: "Could not send the report"
            }
        }
    }

    LaunchedEffect(message.messageId) {
        session.reportMessage(
            providerDialogId = message.providerDialogId,
            messageIds = message.allMessageIds.toLongArray()
        ).onSuccess { result ->
            applyResult(result)
        }.onFailure { failure ->
            state = ReportUiState.Failure(
                failure.message ?: "Could not start the report"
            )
        }
    }

    val current = state

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = {
            if (!isSending) onDismiss()
        },
        title = {
            Text(
                when (current) {
                    is ReportUiState.Choice -> current.title
                    else -> "Report message"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (current) {
                    ReportUiState.Loading -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }

                    is ReportUiState.Failure -> Text(
                        text = current.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    is ReportUiState.Choice -> {
                        if (current.options.isEmpty()) {
                            Text("The provider did not offer a reason list.")
                        }

                        current.options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedOption = option }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedOption == option,
                                    onClick = { selectedOption = option }
                                )

                                Text(text = option.title)
                            }
                        }
                    }

                    is ReportUiState.Comment -> {
                        Text(
                            text = if (current.isOptional) {
                                "Add a comment (optional)."
                            } else {
                                "Describe what is wrong with this message."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = comment,
                            onValueChange = { comment = it },
                            label = { Text("Comment") }
                        )
                    }
                }

                sendError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            when (current) {
                is ReportUiState.Choice -> TextButton(
                    enabled = selectedOption != null && !isSending,
                    onClick = { send(selectedOption?.id, "") }
                ) {
                    Text(if (isSending) "Reporting…" else "Report")
                }

                is ReportUiState.Comment -> TextButton(
                    enabled = !isSending &&
                        (comment.isNotBlank() || current.isOptional),
                    onClick = { send(current.optionId, comment) }
                ) {
                    Text(if (isSending) "Sending…" else "Send")
                }

                is ReportUiState.Failure -> TextButton(onClick = onDismiss) {
                    Text("Close")
                }

                ReportUiState.Loading -> Unit
            }
        },
        dismissButton = {
            when (current) {
                ReportUiState.Loading, is ReportUiState.Failure -> Unit

                else -> TextButton(
                    enabled = !isSending,
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
