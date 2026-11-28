package com.mrboomdev.scrollix.data.outbox

import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageReplyQuote
import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.model.MessageTextFormat

import kotlinx.serialization.Serializable

/** Delivery state shown on the pending row of a queued message. */
@Serializable
enum class OutboxEntryState {
    Queued,
    Sending,
    Failed
}

/** What one queued entry asks the provider to do. */
@Serializable
enum class OutboxEntryKind {
    Text,
    Files,
    Edit,
    Delete
}

/** One outgoing attachment. The path is a local file and is not copied. */
@Serializable
data class OutboxAttachment(
    val path: String,
    val asPhoto: Boolean
)

/**
 * One failed delivery attempt. Kept on the entry so Diagnostics can show why a
 * message did not go out and how often it was retried.
 */
@Serializable
data class OutboxAttempt(
    val atMillis: Long,
    /** 1-based attempt number, matching [OutboxEntry.attempts] at the time. */
    val attempt: Int,
    val succeeded: Boolean = false,
    val error: String? = null
)

/** A long offline stretch must not grow the queue file without bound. */
internal const val MaxOutboxAttemptHistory = 20

/** Appends an attempt, dropping the oldest ones past [limit]. */
internal fun appendOutboxAttempt(
    history: List<OutboxAttempt>,
    attempt: OutboxAttempt,
    limit: Int = MaxOutboxAttemptHistory
): List<OutboxAttempt> = (history + attempt).takeLast(limit)

/**
 * One operation waiting to reach the provider. The same queue carries text
 * sends, file sends, edits and deletions, so all of them survive a restart and
 * share retry backoff. Only [OutboxEntryKind.Text] and [OutboxEntryKind.Files]
 * render a pending row.
 */
@Serializable
data class OutboxEntry(
    val id: String,
    /** Negative synthetic id used by the chat list while the message is queued. */
    val localMessageId: Long,
    val providerId: String,
    val accountId: String,
    val providerDialogId: String,
    val kind: OutboxEntryKind = OutboxEntryKind.Text,
    /** Message text, or the caption of a file send, or the new text of an edit. */
    val text: String = "",
    val attachments: List<OutboxAttachment> = emptyList(),
    /** Message ids an edit or a deletion targets. */
    val targetMessageIds: List<Long> = emptyList(),
    val replyToMessageId: Long? = null,
    /** Chosen passage of the replied-to message, when the reply quotes one. */
    val replyQuote: MessageReplyQuote? = null,
    val customEmojis: List<CustomEmojiSpan> = emptyList(),
    /** Rich text ranges of [text]; empty when the message is plain. */
    val formatting: List<MessageTextFormat> = emptyList(),
    /** Non-null when the queued send itself is scheduled for later. */
    val scheduling: MessageScheduling? = null,
    /** True when the send must not notify the recipient. */
    val silent: Boolean = false,
    /**
     * True when an [OutboxEntryKind.Edit] replaces a media caption instead of
     * plain text, which providers edit through a different call.
     */
    val isCaptionEdit: Boolean = false,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastAttemptAt: Long = 0,
    val lastError: String? = null,
    val state: OutboxEntryState = OutboxEntryState.Queued,
    /** True while the user paused the send; a paused entry is never retried. */
    val paused: Boolean = false,
    /** Upload fraction when it was paused, so the row shows where it stopped. */
    val progress: Float? = null,
    /** Failed attempts, oldest first; a succeeded entry leaves the queue. */
    val attemptHistory: List<OutboxAttempt> = emptyList()
) {
    /** Text shown on the pending row; file sends have no text of their own. */
    val pendingText: String
        get() = text.ifBlank {
            when {
                kind != OutboxEntryKind.Files -> ""
                attachments.isEmpty() -> ""
                attachments.all { attachment -> attachment.asPhoto } -> "Photo"
                attachments.size == 1 -> "File"
                else -> "${attachments.size} files"
            }
        }
}

@Serializable
data class OutboxState(
    /** Schema version; files written before versioning decode as 0. */
    val version: Int = 0,
    val entries: List<OutboxEntry> = emptyList()
)

/**
 * Overall upload fraction of a file send: the slowest attachment, because the
 * album is only complete when every one of its parts is.
 */
internal fun outboxAttachmentProgress(
    attachments: List<OutboxAttachment>,
    progressByPath: Map<String, Float>
): Float? = attachments
    .mapNotNull { attachment -> progressByPath[attachment.path] }
    .minOrNull()

/**
 * A picked photo is sent as a photo only while compression is allowed. With it
 * off the provider receives a document, which Telegram does not re-encode.
 */
internal fun photoKeepsPhotoForm(asPhoto: Boolean, compressPhotos: Boolean): Boolean =
    asPhoto && compressPhotos

/** True while this entry is a file send that still needs bytes. */
fun OutboxEntry.isUploadActive(): Boolean =
    kind == OutboxEntryKind.Files &&
        !paused &&
        state != OutboxEntryState.Failed

/**
 * Aggregate fraction of every active file send, for the background
 * notification. An entry without a reported fraction counts as zero, so the
 * bar never reaches completion before the bytes do.
 */
fun outboxOverallUploadProgress(
    entries: List<OutboxEntry>,
    progressByPath: Map<String, Float>
): Float {
    val active = entries.filter { entry -> entry.isUploadActive() }
    if (active.isEmpty()) return 0f

    return active
        .map { entry -> outboxAttachmentProgress(entry.attachments, progressByPath) ?: 0f }
        .average()
        .toFloat()
}

/** One value describing the whole outgoing file queue, for the notification. */
data class UploadSummary(
    val files: Int = 0,
    val progress: Float = 0f,
    val active: Boolean = false
)
