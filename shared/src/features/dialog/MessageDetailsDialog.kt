package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.ui.components.savedMediaFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One label/value line of the details sheet. */
internal data class MessageDetailRow(
    val label: String,
    val value: String
)

/**
 * Everything the details sheet lists, in display order. Dates go through
 * [formatDate] so the builder stays pure and testable.
 */
internal fun messageDetailsRows(
    message: ChatMessage,
    permalink: String? = null,
    formatDate: (Long) -> String = ::formatMessageDateTime
): List<MessageDetailRow> = buildList {
    (message.senderName ?: if (message.isOutgoing) "You" else null)?.let { sender ->
        add(MessageDetailRow("From", sender))
    }

    message.forwardedFromName?.let { origin ->
        add(MessageDetailRow("Forwarded from", origin))
    }

    add(MessageDetailRow("Sent", formatDate(message.timestamp)))

    if (message.editDate > 0) {
        add(MessageDetailRow("Edited", formatDate(message.editDate)))
    }

    mediaDetails(message)?.let { media -> add(MessageDetailRow("Media", media)) }

    if (message.viewCount > 0) {
        add(MessageDetailRow("Views", message.viewCount.toString()))
    }

    if (message.shareCount > 0) {
        add(MessageDetailRow("Shares", message.shareCount.toString()))
    }

    if (message.replyCount > 0) {
        add(MessageDetailRow("Replies", message.replyCount.toString()))
    }

    permalink?.takeIf { it.isNotBlank() }?.let { link ->
        add(MessageDetailRow("Link", link))
    }

    // A queued row has a synthetic negative id that means nothing to the user.
    if (message.messageId > 0) {
        add(MessageDetailRow("Message id", message.messageId.toString()))
    }
}

private fun mediaDetails(message: ChatMessage): String? {
    val media = message.allMedia
    if (media.isEmpty()) return null

    if (media.size > 1) {
        val kinds = media.map { item -> mediaKindLabel(item.kind) }.distinct()
        return "${media.size} items · ${kinds.joinToString(", ")}"
    }

    val item = media.first()

    return buildList {
        add(mediaKindLabel(item.kind))
        item.fileName?.let(::add)
        if (item.width > 0 && item.height > 0) add("${item.width}×${item.height}")
        if (item.size > 0) add(formatFileSize(item.size))
        if (item.durationSeconds > 0) add(formatMediaDuration(item.durationSeconds))
    }.joinToString(" · ")
}

private fun mediaKindLabel(kind: MessageMediaKind): String = when (kind) {
    MessageMediaKind.Photo -> "Photo"
    MessageMediaKind.Video -> "Video"
    MessageMediaKind.File -> "File"
    MessageMediaKind.Sticker -> "Sticker"
    MessageMediaKind.VideoNote -> "Video message"
    MessageMediaKind.VoiceNote -> "Voice message"
    MessageMediaKind.Audio -> "Audio"
    MessageMediaKind.Animation -> "Animation"
}

private fun formatMediaDuration(seconds: Int): String {
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/**
 * The message's metadata: when it was sent and last edited, where it came from,
 * its media and its permalink. The permalink is resolved through the session,
 * because only the provider can build one.
 */
@Composable
internal fun MessageDetailsDialog(
    message: ChatMessage,
    session: AccountSession?,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var permalink by remember(message.messageId) { mutableStateOf<String?>(null) }
    var isSaving by remember(message.messageId) { mutableStateOf(false) }
    var saveError by remember(message.messageId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message.messageId, session) {
        permalink = if (session?.supportsMessageLinks == true && message.messageId > 0) {
            session.messageLink(message.providerDialogId, message.messageId).getOrNull()
        } else {
            null
        }
    }

    val saveableMedia = message.allMedia.filter { media ->
        (media.fileId ?: media.previewFileId) != null
    }

    // Saving goes through the same session byte loader the media viewer uses,
    // so cached files are written straight to the device. Albums are saved as
    // a set; the dialog only closes once at least one file was written.
    fun saveMedia() {
        val currentSession = session ?: return
        if (isSaving || saveableMedia.isEmpty()) return

        isSaving = true
        saveError = null

        scope.launch {
            var saved = 0

            for (media in saveableMedia) {
                val fileId = media.fileId ?: media.previewFileId ?: continue
                val bytes = currentSession.loadMediaBytes(
                    fileId = fileId,
                    uniqueId = media.fileUniqueId ?: media.previewUniqueId
                ) ?: continue

                val path = withContext(Dispatchers.IO) {
                    saveFileToDevice(savedMediaFileName(media), bytes)
                }

                if (path != null) saved++
            }

            if (saved == 0) {
                saveError = "Could not save the media"
            } else {
                onDismiss()
            }

            isSaving = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Message details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                saveError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                messageDetailsRows(message, permalink).forEach { row ->
                    Column {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        SelectionContainer {
                            Text(
                                text = row.value,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val link = permalink

                if (link != null) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(link))
                            onDismiss()
                        }
                    ) {
                        Text("Copy link")
                    }
                }

                if (session != null && saveableMedia.isNotEmpty()) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = { saveMedia() }
                    ) {
                        Text(if (isSaving) "Saving…" else "Save media")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
