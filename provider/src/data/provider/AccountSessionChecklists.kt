package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException

/**
 * Composing a checklist and appending tasks to an existing one. They live next
 * to, but apart from, [AccountSession] so the main contract stays readable.
 */
interface ChecklistAuthoringSession {
    /** Whether this provider can send a checklist and add tasks to one. */
    val supportsChecklistAuthoring: Boolean
        get() = false

    /** Sends a new checklist message. */
    suspend fun sendChecklist(
        providerDialogId: String,
        title: String,
        tasks: List<String>,
        othersCanAddTasks: Boolean = false,
        othersCanMarkTasksAsDone: Boolean = true,
        replyToMessageId: Long? = null
    ): Result<Unit> = Result.failure(
        SessionException(SessionErrorCode.Unsupported, "Composing checklists is not supported")
    )

    /** Appends [tasks] to the checklist in [messageId]. */
    suspend fun addChecklistTasks(
        providerDialogId: String,
        messageId: Long,
        tasks: List<String>
    ): Result<Unit> = Result.failure(
        SessionException(SessionErrorCode.Unsupported, "Adding checklist tasks is not supported")
    )
}
