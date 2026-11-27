package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import kotlinx.coroutines.CancellationException

/**
 * Marks every unread chat of this account as read. TDLib has no "read all"
 * call, so each unread chat is read up to its newest message with
 * `viewMessages`. A chat that fails does not stop the rest: a partial clear is
 * better than an untouched list, and the failure is only reported when nothing
 * could be marked.
 */
internal suspend fun TelegramSession.markAllChatsReadImpl(): Result<Unit> {
    val unread = _dialogs.value.filter { dialog ->
        dialog.unreadCount > 0 || dialog.isMarkedAsUnread
    }

    if (unread.isEmpty()) return Result.success(Unit)

    var marked = 0
    var firstFailure: Throwable? = null

    for (dialog in unread) {
        val chatId = dialog.providerDialogId.toLongOrNull() ?: continue

        try {
            val lastMessageId = when (val result = client.getChat(chatId)) {
                is TdlResult.Success -> result.result.lastMessage?.id ?: 0L
                is TdlResult.Failure -> continue
            }

            if (lastMessageId <= 0) continue

            client.viewMessages(
                chatId = chatId,
                messageIds = longArrayOf(lastMessageId),
                forceRead = true
            )

            marked++
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (firstFailure == null) firstFailure = exception
        }
    }

    return if (marked > 0) {
        Result.success(Unit)
    } else {
        Result.failure(
            firstFailure ?: IllegalStateException("No chat could be marked as read")
        )
    }
}
