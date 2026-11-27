package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import kotlinx.coroutines.CancellationException

/**
 * The chat's auto-delete timer, in seconds; zero means it is off. TDLib stores
 * this server-side per chat, so it applies to every client of the account.
 */
internal suspend fun TelegramSession.loadMessageAutoDeleteTimeImpl(
    providerDialogId: String
): Result<Int> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> Result.success(result.result.messageAutoDeleteTime)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.setMessageAutoDeleteTimeImpl(
    providerDialogId: String,
    seconds: Int
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val result = client.setChatMessageAutoDeleteTime(
            chatId = chatId,
            messageAutoDeleteTime = seconds.coerceAtLeast(0)
        )

        when (result) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}
