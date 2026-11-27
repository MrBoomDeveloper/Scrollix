package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.MessageChecklist
import dev.g000sha256.tdl.dto.MessageContent
import kotlinx.coroutines.CancellationException
import com.mrboomdev.scrollix.data.model.MessageChecklistInfo
import com.mrboomdev.scrollix.data.model.MessageChecklistTaskInfo

/** Maps a checklist message, or null when [content] is anything else. */
internal fun mapChecklist(content: MessageContent): MessageChecklistInfo? {
    val list = (content as? MessageChecklist)?.list ?: return null

    return MessageChecklistInfo(
        title = list.title.text,
        customEmojis = customEmojiSpans(list.title),
        canMarkTasksAsDone = list.canMarkTasksAsDone,
        tasks = list.tasks.map { task ->
            MessageChecklistTaskInfo(
                id = task.id,
                text = task.text.text,
                // Completion is stamped with who did it and when; either one
                // means the task is done.
                isDone = task.completionDate != 0 || task.completedBy != null,
                customEmojis = customEmojiSpans(task.text)
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
