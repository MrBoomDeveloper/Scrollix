package com.mrboomdev.scrollix.data.outbox

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.data.model.OutgoingAttachment
import com.mrboomdev.scrollix.data.storage.DataUsageStore
import com.mrboomdev.scrollix.data.storage.createPlatformStorage
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.data.storage.randomId

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import com.mrboomdev.scrollix.data.model.asSessionException

/**
 * Owns the persisted outgoing queue. A send is attempted immediately, and
 * retryable failures (offline, timeout, flood wait) stay queued and are retried
 * with backoff until the provider accepts them. Queued rows are rendered by
 * their negative `localMessageId`, so they survive opening another chat and a
 * process restart.
 *
 * The queue carries text sends, file sends, edits and deletions. Only text and
 * file sends render a pending row; edits and deletions are silent until they
 * reach the server.
 *
 * Delivery is at-least-once: a request that timed out after the server stored
 * the message can be sent twice.
 */
object OutboxManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy { OutboxStore(createPlatformStorage()) }

    private val jobs = mutableMapOf<String, Job>()
    private val jobsMutex = Mutex()
    private val attemptMutex = Mutex()

    private var started = false

    val entries: StateFlow<List<OutboxEntry>> by lazy {
        store.state
            .map { state -> state.entries }
            .stateIn(scope, SharingStarted.Eagerly, store.state.value.entries)
    }

    /** Recovers interrupted sends and schedules every queued entry once. */
    fun start() {
        if (started) return
        started = true

        scope.launch {
            store.recoverInterrupted()

            store.state.value.entries
                .filter { entry -> entry.state == OutboxEntryState.Queued }
                .forEach { entry -> schedule(entry.id, delayMillis = 0L) }
        }
    }

    /** Queues a plain text message. */
    suspend fun enqueue(
        providerId: String,
        accountId: String,
        providerDialogId: String,
        text: String,
        replyToMessageId: Long? = null,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList(),
        scheduling: MessageScheduling? = null,
        silent: Boolean = false
    ): OutboxEntry = enqueueText(
        providerId = providerId,
        accountId = accountId,
        providerDialogId = providerDialogId,
        text = text,
        replyToMessageId = replyToMessageId,
        customEmojis = customEmojis,
        formatting = formatting,
        scheduling = scheduling,
        silent = silent
    )

    suspend fun enqueueText(
        providerId: String,
        accountId: String,
        providerDialogId: String,
        text: String,
        replyToMessageId: Long? = null,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList(),
        scheduling: MessageScheduling? = null,
        silent: Boolean = false
    ): OutboxEntry = enqueueEntry(
        providerId = providerId,
        accountId = accountId,
        providerDialogId = providerDialogId,
        kind = OutboxEntryKind.Text,
        text = text,
        replyToMessageId = replyToMessageId,
        customEmojis = customEmojis,
        formatting = formatting,
        scheduling = scheduling,
        silent = silent
    )

    /** Queues one or more files with an optional caption. */
    suspend fun enqueueFiles(
        providerId: String,
        accountId: String,
        providerDialogId: String,
        attachments: List<OutboxAttachment>,
        caption: String = "",
        replyToMessageId: Long? = null,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList(),
        scheduling: MessageScheduling? = null,
        silent: Boolean = false
    ): OutboxEntry = enqueueEntry(
        providerId = providerId,
        accountId = accountId,
        providerDialogId = providerDialogId,
        kind = OutboxEntryKind.Files,
        text = caption,
        attachments = attachments,
        replyToMessageId = replyToMessageId,
        customEmojis = customEmojis,
        formatting = formatting,
        scheduling = scheduling,
        silent = silent
    )

    /** Queues an edit of an already delivered message. */
    suspend fun enqueueEdit(
        providerId: String,
        accountId: String,
        providerDialogId: String,
        messageId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList(),
        isCaptionEdit: Boolean = false
    ): OutboxEntry = enqueueEntry(
        providerId = providerId,
        accountId = accountId,
        providerDialogId = providerDialogId,
        kind = OutboxEntryKind.Edit,
        text = text,
        targetMessageIds = listOf(messageId),
        customEmojis = customEmojis,
        formatting = formatting,
        isCaptionEdit = isCaptionEdit
    )

    /** Queues a deletion of one or more delivered messages. */
    suspend fun enqueueDelete(
        providerId: String,
        accountId: String,
        providerDialogId: String,
        messageIds: List<Long>
    ): OutboxEntry = enqueueEntry(
        providerId = providerId,
        accountId = accountId,
        providerDialogId = providerDialogId,
        kind = OutboxEntryKind.Delete,
        targetMessageIds = messageIds
    )

    /**
     * Attempts one delivery. Returns success when the entry is gone or safely
     * queued for a retry, and failure only for a permanent provider error (the
     * entry is then kept as [OutboxEntryState.Failed]).
     */
    suspend fun sendNow(entryId: String): Result<Unit> = attemptMutex.withLock {
        // A retry may be scheduled from inside this attempt; the current job
        // must not make the new schedule look like a duplicate.
        jobsMutex.withLock { jobs.remove(entryId) }

        val entry = store.find(entryId) ?: return@withLock Result.success(Unit)
        val attempted = store.update(entryId) { current ->
            current.copy(
                state = OutboxEntryState.Sending,
                attempts = current.attempts + 1,
                lastAttemptAt = currentTimeMillis(),
                lastError = null
            )
        } ?: return@withLock Result.success(Unit)

        val session = ApiManager.session(entry.accountId)

        if (session == null || session.authState.value !is AuthState.Ready) {
            requeue(attempted)
            return@withLock Result.success(Unit)
        }

        val result = when (entry.kind) {
            OutboxEntryKind.Text -> session.sendMessage(
                providerDialogId = entry.providerDialogId,
                text = entry.text,
                replyToMessageId = entry.replyToMessageId,
                customEmojis = entry.customEmojis,
                formatting = entry.formatting,
                scheduling = entry.scheduling,
                sendSilently = entry.silent
            )

            OutboxEntryKind.Files -> session.sendFiles(
                providerDialogId = entry.providerDialogId,
                attachments = entry.attachments.map { attachment ->
                    OutgoingAttachment(
                        path = attachment.path,
                        asPhoto = attachment.asPhoto
                    )
                },
                caption = entry.text,
                replyToMessageId = entry.replyToMessageId,
                customEmojis = entry.customEmojis,
                formatting = entry.formatting,
                scheduling = entry.scheduling,
                sendSilently = entry.silent
            )

            OutboxEntryKind.Edit -> {
                val messageId = entry.targetMessageIds.firstOrNull()

                if (messageId == null) {
                    Result.failure(IllegalArgumentException("Edit has no target message"))
                } else if (entry.isCaptionEdit) {
                    session.editMessageCaption(
                        providerDialogId = entry.providerDialogId,
                        messageId = messageId,
                        caption = entry.text,
                        customEmojis = entry.customEmojis,
                        formatting = entry.formatting
                    )
                } else {
                    session.editMessage(
                        providerDialogId = entry.providerDialogId,
                        messageId = messageId,
                        text = entry.text,
                        customEmojis = entry.customEmojis,
                        formatting = entry.formatting
                    )
                }
            }

            OutboxEntryKind.Delete -> {
                if (entry.targetMessageIds.isEmpty()) {
                    Result.failure(IllegalArgumentException("Deletion has no target messages"))
                } else {
                    session.deleteMessages(
                        providerDialogId = entry.providerDialogId,
                        messageIds = entry.targetMessageIds.toLongArray()
                    )
                }
            }
        }

        if (result.isSuccess) {
            // Only text is measurable here; attachment sizes are not tracked by
            // the queue. The statistics screen presents the number as approximate.
            if (entry.text.isNotEmpty()) {
                DataUsageStore.record(
                    accountId = entry.accountId,
                    sentBytes = entry.text.encodeToByteArray().size.toLong()
                )
            }

            store.remove(entryId)
            return@withLock Result.success(Unit)
        }

        val error = (result.exceptionOrNull() ?: IllegalStateException("Message was not sent"))
            .asSessionException()

        // Only failures are recorded: a delivered entry leaves the queue, so
        // its history would be discarded immediately anyway.
        val history = appendOutboxAttempt(
            history = attempted.attemptHistory,
            attempt = OutboxAttempt(
                atMillis = attempted.lastAttemptAt,
                attempt = attempted.attempts,
                succeeded = false,
                error = error.message
            )
        )

        if (error.retryable) {
            requeue(
                attempted.copy(
                    lastError = error.message,
                    attemptHistory = history
                )
            )
            Result.success(Unit)
        } else {
            store.update(entryId) { current ->
                current.copy(
                    state = OutboxEntryState.Failed,
                    lastError = error.message,
                    attemptHistory = history
                )
            }
            Result.failure(error)
        }
    }

    /** Retries a failed entry from the UI, restarting its backoff. */
    fun retryByLocalMessageId(localMessageId: Long) {
        val entry = store.findByLocalMessageId(localMessageId) ?: return

        scope.launch {
            jobsMutex.withLock { jobs.remove(entry.id)?.cancel() }
            store.update(entry.id) { current ->
                current.copy(
                    state = OutboxEntryState.Queued,
                    attempts = 0,
                    lastError = null
                )
            }
            schedule(entry.id, delayMillis = 0L)
        }
    }

    fun cancelByLocalMessageId(localMessageId: Long) {
        val entry = store.findByLocalMessageId(localMessageId) ?: return

        scope.launch {
            jobsMutex.withLock { jobs.remove(entry.id)?.cancel() }
            store.remove(entry.id)
        }
    }

    /**
     * Requeues every entry that is waiting or has failed and restarts its
     * backoff, so the next attempt happens immediately. Entries already being
     * sent are left alone: a second attempt would duplicate the message.
     */
    fun retryAll() {
        scope.launch {
            store.state.value.entries
                .filter { entry -> entry.state != OutboxEntryState.Sending }
                .forEach { entry ->
                    jobsMutex.withLock { jobs.remove(entry.id)?.cancel() }
                    store.update(entry.id) { current ->
                        current.copy(
                            state = OutboxEntryState.Queued,
                            attempts = 0,
                            lastError = null
                        )
                    }
                    schedule(entry.id, delayMillis = 0L)
                }
        }
    }

    /**
     * Drops every queued entry. A send already in flight is cancelled too, so
     * the queue is empty when this returns; at-least-once delivery still
     * applies to a request that reached the server first.
     */
    fun discardAll() {
        scope.launch {
            jobsMutex.withLock {
                jobs.values.forEach { job -> job.cancel() }
                jobs.clear()
            }

            store.state.value.entries.forEach { entry -> store.remove(entry.id) }
        }
    }

    suspend fun removeAccount(accountId: String) {
        store.removeAccount(accountId)
    }

    /**
     * Drops queued work of accounts that are no longer on this device. Called at
     * startup: an entry whose account is gone can never be delivered, and
     * retrying it forever would keep the queue and its attachments alive.
     */
    suspend fun pruneUnknownAccounts(knownAccountIds: Set<String>) {
        val stale = store.state.value.entries.filter { entry ->
            entry.accountId !in knownAccountIds
        }

        if (stale.isEmpty()) return

        jobsMutex.withLock {
            stale.forEach { entry -> jobs.remove(entry.id)?.cancel() }
        }

        store.pruneUnknownAccounts(knownAccountIds)
    }

    /** Cancels retry jobs so a shutdown cannot resurrect app sessions. */
    suspend fun stop() {
        jobsMutex.withLock {
            jobs.values.forEach { job -> job.cancel() }
            jobs.clear()
        }
        started = false
    }

    private suspend fun enqueueEntry(
        providerId: String,
        accountId: String,
        providerDialogId: String,
        kind: OutboxEntryKind,
        text: String = "",
        attachments: List<OutboxAttachment> = emptyList(),
        targetMessageIds: List<Long> = emptyList(),
        replyToMessageId: Long? = null,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList(),
        scheduling: MessageScheduling? = null,
        silent: Boolean = false,
        isCaptionEdit: Boolean = false
    ): OutboxEntry {
        val entry = OutboxEntry(
            id = randomId(),
            localMessageId = nextLocalMessageId(),
            providerId = providerId,
            accountId = accountId,
            providerDialogId = providerDialogId,
            kind = kind,
            text = text,
            attachments = attachments,
            targetMessageIds = targetMessageIds,
            replyToMessageId = replyToMessageId,
            customEmojis = customEmojis,
            formatting = formatting,
            scheduling = scheduling,
            silent = silent,
            isCaptionEdit = isCaptionEdit,
            createdAt = currentTimeMillis()
        )

        store.add(entry)
        return entry
    }

    private suspend fun requeue(entry: OutboxEntry) {
        store.update(entry.id) { current ->
            current.copy(
                state = OutboxEntryState.Queued,
                lastError = entry.lastError,
                attemptHistory = entry.attemptHistory
            )
        }

        schedule(entry.id, outboxRetryDelayMillis(entry.attempts))
    }

    private suspend fun schedule(entryId: String, delayMillis: Long) {
        jobsMutex.withLock {
            if (jobs[entryId]?.isActive == true) return

            val job = scope.launch(start = CoroutineStart.LAZY) {
                if (delayMillis > 0L) delay(delayMillis)

                try {
                    sendNow(entryId)
                } finally {
                    val self = coroutineContext[Job]
                    jobsMutex.withLock {
                        if (jobs[entryId] === self) jobs.remove(entryId)
                    }
                }
            }

            jobs[entryId] = job
            job.start()
        }
    }

    private fun nextLocalMessageId(): Long {
        val used = store.state.value.entries
            .mapTo(HashSet()) { entry -> entry.localMessageId }

        while (true) {
            val candidate = -Random.nextLong(1L, Long.MAX_VALUE)
            if (candidate !in used) return candidate
        }
    }
}
