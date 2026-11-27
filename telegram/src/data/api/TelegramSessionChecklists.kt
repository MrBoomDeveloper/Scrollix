package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.MessageChecklistInfo
import com.mrboomdev.scrollix.data.model.MessageChecklistTaskInfo
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputChecklist
import dev.g000sha256.tdl.dto.InputChecklistTask
import dev.g000sha256.tdl.dto.InputMessageChecklist
import dev.g000sha256.tdl.dto.InputMessageReplyToMessage
import dev.g000sha256.tdl.dto.MessageChecklist
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageSenderUser
import kotlinx.coroutines.CancellationException

/** Maps a checklist message, or null when [content] is anything else. */
internal fun mapChecklist(content: MessageContent): MessageChecklistInfo? {
    val list = (content as? MessageChecklist)?.list ?: return null

    return MessageChecklistInfo(
        title = list.title.text,
        customEmojis = customEmojiSpans(list.title),
        canMarkTasksAsDone = list.canMarkTasksAsDone,
        canAddTasks = list.canAddTasks,
        tasks = list.tasks.map { task ->
            MessageChecklistTaskInfo(
                id = task.id,
                text = task.text.text,
                // Completion is stamped with who did it and when; either one
                // means the task is done.
                isDone = task.completionDate != 0 || task.completedBy != null,
                completedById = (task.completedBy as? MessageSenderUser)?.userId,
                customEmojis = customEmojiSpans(task.text)
            )
        }
    )
}

/**
 * The checklist plus the names of whoever completed a task. Names are not part
 * of the message, so they are resolved through the user cache and requested
 * when still unknown.
 */
internal fun TelegramSession.mapChecklistWithNames(content: MessageContent): MessageChecklistInfo? {
    val checklist = mapChecklist(content) ?: return null
    val users = _users.value

    checklist.tasks.mapNotNull { task -> task.completedById }.forEach(::requestUser)

    return checklist.copy(
        tasks = checklist.tasks.map { task ->
            task.copy(
                completedByName = task.completedById
                    ?.let { userId -> users[userId]?.let(::userDisplayName) }
                    ?.takeIf { name -> name.isNotBlank() }
            )
        }
    )
}

/**
 * Marks [taskIds] done or not done. TDLib reports the change back as an
 * `updateMessageContent`, which refreshes the cached message.
 */
internal suspend fun TelegramSession.setChecklistTasksDoneImpl(
    providerDialogId: String,
    messageId: Long,
    taskIds: List<Int>,
    done: Boolean
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    if (taskIds.isEmpty()) return Result.success(Unit)

    val ids = taskIds.toIntArray()

    return try {
        when (val result = client.markChecklistTasksAsDone(
            chatId = chatId,
            messageId = messageId,
            markedAsDoneTaskIds = if (done) ids else intArrayOf(),
            markedAsNotDoneTaskIds = if (done) intArrayOf() else ids
        )) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

/** Sends a freshly composed checklist as a message of its own. */
internal suspend fun TelegramSession.sendChecklistImpl(
    providerDialogId: String,
    title: String,
    tasks: List<String>,
    othersCanAddTasks: Boolean,
    othersCanMarkTasksAsDone: Boolean,
    replyToMessageId: Long?
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val cleanTitle = title.trim()
    val cleanTasks = tasks.map(String::trim).filter(String::isNotEmpty)

    if (cleanTitle.isEmpty()) {
        return Result.failure(IllegalArgumentException("Checklist title is empty"))
    }

    if (cleanTasks.isEmpty()) {
        return Result.failure(IllegalArgumentException("A checklist needs at least one task"))
    }

    val checklist = InputChecklist(
        title = FormattedText(cleanTitle, emptyArray()),
        tasks = cleanTasks.map { text ->
            InputChecklistTask(id = 0, text = FormattedText(text, emptyArray()))
        }.toTypedArray(),
        othersCanAddTasks = othersCanAddTasks,
        othersCanMarkTasksAsDone = othersCanMarkTasksAsDone
    )

    return try {
        when (val result = client.sendMessage(
            chatId = chatId,
            replyTo = replyToMessageId?.let { messageId ->
                InputMessageReplyToMessage(
                    messageId = messageId,
                    quote = null,
                    checklistTaskId = 0,
                    pollOptionId = ""
                )
            },
            inputMessageContent = InputMessageChecklist(checklist)
        )) {
            is TdlResult.Success -> {
                upsertMessage(result.result)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

/** Appends tasks to a checklist that is already on the server. */
internal suspend fun TelegramSession.addChecklistTasksImpl(
    providerDialogId: String,
    messageId: Long,
    tasks: List<String>
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val cleanTasks = tasks.map(String::trim).filter(String::isNotEmpty)

    if (cleanTasks.isEmpty()) {
        return Result.failure(IllegalArgumentException("No tasks to add"))
    }

    return try {
        when (val result = client.addChecklistTasks(
            chatId = chatId,
            messageId = messageId,
            tasks = cleanTasks.map { text ->
                InputChecklistTask(id = 0, text = FormattedText(text, emptyArray()))
            }.toTypedArray()
        )) {
            is TdlResult.Success -> {
                // The updated list arrives with updateMessageContent, but a
                // refresh makes it appear without waiting for the update.
                refreshMessage(chatId, messageId)
                Result.success(Unit)
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}
