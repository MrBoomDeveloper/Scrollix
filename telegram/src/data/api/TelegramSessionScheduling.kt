package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.MessageSendOptions
import dev.g000sha256.tdl.dto.MessageSchedulingState
import dev.g000sha256.tdl.dto.MessageSchedulingStateSendAtDate
import dev.g000sha256.tdl.dto.MessageSchedulingStateSendWhenOnline
import kotlinx.coroutines.CancellationException
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageScheduling

/** Maps the shared scheduling model to TDLib's send options. */
internal fun MessageScheduling?.toTdlibSchedulingState(): MessageSchedulingState? {
    return when (this) {
        null -> null

        is MessageScheduling.At -> MessageSchedulingStateSendAtDate(
            sendDate = (sendDateMillis / 1000L).toInt(),
            repeatPeriod = 0
        )

        MessageScheduling.WhenOnline -> MessageSchedulingStateSendWhenOnline()
    }
}

/**
 * Null when the message should be sent right away with a notification, as
 * TDLib expects. Options are needed for a scheduled send and for a silent one.
 */
internal fun MessageScheduling?.sendOptions(silent: Boolean = false): MessageSendOptions? {
    val state = toTdlibSchedulingState()
    if (state == null && !silent) return null

    return MessageSendOptions(
        suggestedPostInfo = null,
        disableNotification = silent,
        fromBackground = false,
        protectContent = false,
        allowPaidBroadcast = false,
        paidMessageStarCount = 0,
        updateOrderOfInstalledStickerSets = false,
        schedulingState = state,
        effectId = 0,
        sendingId = 0,
        onlyPreview = false
    )
}

internal suspend fun TelegramSession.loadScheduledMessagesImpl(
    providerDialogId: String
): Result<List<ChatMessage>> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getChatScheduledMessages(chatId)) {
            is TdlResult.Success -> Result.success(
                result.result.messages
                    .filterNotNull()
                    .map(::mapMessage)
                    // Soonest first; "when online" has no date, so it follows
                    // the dated messages.
                    .sortedWith(
                        compareBy(
                            { message ->
                                message.scheduling is MessageScheduling.WhenOnline
                            },
                            { message ->
                                (message.scheduling as? MessageScheduling.At)
                                    ?.sendDateMillis
                                    ?: Long.MAX_VALUE
                            }
                        )
                    )
            )

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.rescheduleMessageImpl(
    providerDialogId: String,
    messageId: Long,
    scheduling: MessageScheduling?
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.editMessageSchedulingState(
            chatId = chatId,
            messageId = messageId,
            schedulingState = scheduling.toTdlibSchedulingState()
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
