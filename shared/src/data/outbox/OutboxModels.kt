package com.mrboomdev.scrollix.data.outbox

import com.mrboomdev.scrollix.data.model.CustomEmojiSpan

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
    val customEmojis: List<CustomEmojiSpan> = emptyList(),
    val createdAt: Long,
    val attempts: Int = 0,
    val lastAttemptAt: Long = 0,
    val lastError: String? = null,
    val state: OutboxEntryState = OutboxEntryState.Queued,
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
