package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.MessageReportOption
import com.mrboomdev.scrollix.data.model.MessageReportResult
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.ReportChatResultMessagesRequired
import dev.g000sha256.tdl.dto.ReportChatResultOk
import dev.g000sha256.tdl.dto.ReportChatResultOptionRequired
import dev.g000sha256.tdl.dto.ReportChatResultTextRequired
import kotlinx.coroutines.CancellationException

/**
 * Reports messages through TDLib. The first call (no option) is also how the
 * provider states what it needs, so the UI can show the reasons Telegram
 * itself offers instead of a hard-coded list that may not match.
 */
internal suspend fun TelegramSession.reportMessagesImpl(
    providerDialogId: String,
    messageIds: LongArray,
    optionId: String?,
    comment: String
): Result<MessageReportResult> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(
            com.mrboomdev.scrollix.data.model.SessionException(
                com.mrboomdev.scrollix.data.model.SessionErrorCode.InvalidRequest,
                "Invalid Telegram chat id"
            )
        )

    if (messageIds.isEmpty()) {
        return Result.failure(
            com.mrboomdev.scrollix.data.model.SessionException(
                com.mrboomdev.scrollix.data.model.SessionErrorCode.InvalidRequest,
                "No message to report"
            )
        )
    }

    return try {
        when (
            val result = client.reportChat(
                chatId,
                optionId.hexToBytes(),
                messageIds,
                comment
            )
        ) {
            is TdlResult.Success -> Result.success(
                when (val value = result.result) {
                    is ReportChatResultOk -> MessageReportResult.Reported

                    is ReportChatResultOptionRequired -> MessageReportResult.OptionsRequired(
                        title = value.title,
                        options = value.options.orEmpty().map { option ->
                            MessageReportOption(
                                id = option.id.toHexString(),
                                title = option.text
                            )
                        }
                    )

                    is ReportChatResultTextRequired -> MessageReportResult.CommentRequired(
                        optionId = value.optionId.toHexString(),
                        isOptional = value.isOptional
                    )

                    is ReportChatResultMessagesRequired -> MessageReportResult.MessagesRequired
                }
            )

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        AppLog.error("reporting", "Failed to report a message", exception)
        Result.failure(exception)
    }
}

private fun ByteArray?.toHexString(): String {
    return this?.joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }.orEmpty()
}

private fun String?.hexToBytes(): ByteArray {
    val text = this.orEmpty()
    if (text.isEmpty() || text.length % 2 != 0) return ByteArray(0)

    return ByteArray(text.length / 2) { index ->
        text.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
