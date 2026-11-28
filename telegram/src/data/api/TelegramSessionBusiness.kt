package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import com.mrboomdev.scrollix.data.model.BusinessHoursInterval
import com.mrboomdev.scrollix.data.model.BusinessQuickReply
import com.mrboomdev.scrollix.data.model.BusinessSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Name of the shortcut this app keeps the greeting message in. */
internal const val GreetingShortcutName = "Scrollix greeting"

/** Name of the shortcut this app keeps the away message in. */
internal const val AwayShortcutName = "Scrollix away"

/**
 * Reads the business settings of the account. TDLib has no dedicated getter,
 * but the full profile of the current account carries them. The texts of the
 * greeting and away messages are the first messages of their shortcuts, which
 * arrive through `updateQuickReplyShortcut`.
 */
internal suspend fun TelegramSession.loadBusinessSettingsImpl(): Result<BusinessSettings> {
    val userId = currentUserId
        ?: return Result.failure(IllegalStateException("Sign in to Telegram first"))

    return try {
        // Shortcut contents are update-driven; asking again makes sure the
        // cached copy is not older than this read.
        runCatching { client.loadQuickReplyShortcuts() }

        val features = when (val result = client.getBusinessFeatures()) {
            is TdlResult.Success -> result.result.features
            is TdlResult.Failure -> emptyArray()
        }

        val full = when (val result = client.getUserFullInfo(userId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val business = full.businessInfo
        val shortcuts = _businessQuickReplies.value
        val byId = shortcuts.associateBy { shortcut -> shortcut.id }

        val greetingId = business?.greetingMessageSettings?.shortcutId ?: 0
        val awayId = business?.awayMessageSettings?.shortcutId ?: 0

        Result.success(
            BusinessSettings(
                isAvailable = features.isNotEmpty() || business != null,
                greeting = greetingId.takeIf { it != 0 }
                    ?.let { id -> byId[id]?.preview?.takeIf { it.isNotBlank() } },
                away = awayId.takeIf { it != 0 }
                    ?.let { id -> byId[id]?.preview?.takeIf { it.isNotBlank() } },
                awayOutsideHours = business?.awayMessageSettings?.schedule is
                    BusinessAwayMessageScheduleOutsideOfOpeningHours,
                timeZoneId = business?.openingHours?.timeZoneId.orEmpty(),
                openingHours = business?.openingHours?.openingHours.orEmpty().map { interval ->
                    BusinessHoursInterval(
                        startMinute = interval.startMinute,
                        endMinute = interval.endMinute
                    )
                }
            )
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.setBusinessGreetingImpl(text: String?): Result<Unit> {
    val value = text?.trim()

    if (value.isNullOrEmpty()) {
        return runRequest { client.setBusinessGreetingMessageSettings(null) }
    }

    return setBusinessShortcut(name = GreetingShortcutName, text = value) { shortcutId ->
        client.setBusinessGreetingMessageSettings(
            greetingMessageSettings = BusinessGreetingMessageSettings(
                shortcutId = shortcutId,
                recipients = businessRecipients(),
                inactivityDays = 7
            )
        )
    }
}

internal suspend fun TelegramSession.setBusinessAwayImpl(
    text: String?,
    outsideOpeningHours: Boolean
): Result<Unit> {
    val value = text?.trim()

    if (value.isNullOrEmpty()) {
        return runRequest { client.setBusinessAwayMessageSettings(null) }
    }

    return setBusinessShortcut(name = AwayShortcutName, text = value) { shortcutId ->
        client.setBusinessAwayMessageSettings(
            awayMessageSettings = BusinessAwayMessageSettings(
                shortcutId = shortcutId,
                recipients = businessRecipients(),
                schedule = if (outsideOpeningHours) {
                    BusinessAwayMessageScheduleOutsideOfOpeningHours()
                } else {
                    BusinessAwayMessageScheduleAlways()
                },
                offlineOnly = true
            )
        )
    }
}

internal suspend fun TelegramSession.setBusinessOpeningHoursImpl(
    timeZoneId: String,
    intervals: List<BusinessHoursInterval>
): Result<Unit> {
    if (intervals.isEmpty()) {
        return runRequest { client.setBusinessOpeningHours(null) }
    }

    return runRequest {
        client.setBusinessOpeningHours(
            openingHours = BusinessOpeningHours(
                timeZoneId = timeZoneId,
                openingHours = intervals.map { interval ->
                    BusinessOpeningHoursInterval(
                        startMinute = interval.startMinute,
                        endMinute = interval.endMinute
                    )
                }.toTypedArray()
            )
        )
    }
}

internal suspend fun TelegramSession.createBusinessQuickReplyImpl(
    name: String,
    text: String
): Result<Unit> {
    val shortcutName = name.trim()
    val content = text.trim()

    if (shortcutName.isEmpty() || content.isEmpty()) {
        return Result.failure(IllegalArgumentException("A quick reply needs a name and a text"))
    }

    return runRequest {
        client.addQuickReplyShortcutMessage(
            shortcutName = shortcutName,
            replyToMessageId = 0,
            inputMessageContent = textContent(content)
        )
    }
}

internal suspend fun TelegramSession.deleteBusinessQuickReplyImpl(
    shortcutId: Int
): Result<Unit> {
    val result = runRequest { client.deleteQuickReplyShortcut(shortcutId) }

    if (result.isSuccess) {
        _businessQuickReplies.value = _businessQuickReplies.value
            .filterNot { shortcut -> shortcut.id == shortcutId }
    }

    return result
}

internal suspend fun TelegramSession.sendBusinessQuickReplyImpl(
    providerDialogId: String,
    shortcutId: Int
): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.sendQuickReplyShortcutMessages(
            chatId = chatId,
            shortcutId = shortcutId,
            sendingId = 0
        )) {
            is TdlResult.Success -> {
                result.result.messages.forEach { message ->
                    message?.let(::upsertMessage)
                }

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

/**
 * Keeps one shortcut in the published list. The first message is what the
 * greeting editor and the picker show, so it is read here.
 */
internal fun TelegramSession.upsertBusinessQuickReply(shortcut: QuickReplyShortcut) {
    val mapped = BusinessQuickReply(
        id = shortcut.id,
        name = shortcut.name,
        preview = shortcutText(shortcut.firstMessage?.content),
        messageCount = shortcut.messageCount
    )

    _businessQuickReplies.value = _businessQuickReplies.value
        .filterNot { existing -> existing.id == mapped.id }
        .plus(mapped)
        .sortedBy { existing -> existing.id }
}

/**
 * Writes [text] into the shortcut named [name] and then applies the settings
 * that point at it. An existing shortcut is edited in place, so saving twice
 * does not leave a second shortcut behind.
 */
private suspend fun TelegramSession.setBusinessShortcut(
    name: String,
    text: String,
    apply: suspend (shortcutId: Int) -> TdlResult<Ok>
): Result<Unit> {
    return try {
        val existing = _businessQuickReplies.value
            .firstOrNull { shortcut -> shortcut.name == name }

        if (existing == null) {
            client.addQuickReplyShortcutMessage(
                shortcutName = name,
                replyToMessageId = 0,
                inputMessageContent = textContent(text)
            )
        } else {
            when (val loaded = client.loadQuickReplyShortcutMessages(existing.id)) {
                is TdlResult.Success -> Unit
                is TdlResult.Failure -> return Result.failure(loaded.toSessionException())
            }

            // The messages themselves arrive through an update, so the edit
            // asks for the shortcut contents and edits what is known.
            val firstId = currentShortcutMessageId(existing.id)

            if (firstId != null) {
                client.editQuickReplyMessage(
                    shortcutId = existing.id,
                    messageId = firstId,
                    inputMessageContent = textContent(text)
                )
            } else {
                client.addQuickReplyShortcutMessage(
                    shortcutName = name,
                    replyToMessageId = 0,
                    inputMessageContent = textContent(text)
                )
            }
        }

        val shortcutId = awaitShortcutId(name)
            ?: return Result.failure(IllegalStateException("The shortcut was not created"))

        runRequest { apply(shortcutId) }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

/** Waits for the update that reports the shortcut the message created. */
private suspend fun TelegramSession.awaitShortcutId(name: String): Int? {
    return withTimeoutOrNull(2_000) {
        _businessQuickReplies.first { list ->
            list.any { shortcut -> shortcut.name == name }
        }
    }?.firstOrNull { shortcut -> shortcut.name == name }?.id
}

/** Message ids of a shortcut are update-driven like its contents. */
private suspend fun TelegramSession.currentShortcutMessageId(shortcutId: Int): Long? {
    val update = withTimeoutOrNull(1_000) {
        quickReplyMessageIds.first { ids -> ids.containsKey(shortcutId) }
    }

    return update?.get(shortcutId)?.firstOrNull()
}

/** The greeting and away messages reach every private chat by default. */
private fun businessRecipients() = BusinessRecipients(
    chatIds = longArrayOf(),
    excludedChatIds = longArrayOf(),
    selectExistingChats = true,
    selectNewChats = true,
    selectContacts = false,
    selectNonContacts = false,
    excludeSelected = false
)

private fun TelegramSession.textContent(text: String) = InputMessageText(
    text = FormattedText(text = text, entities = emptyArray()),
    linkPreviewOptions = null,
    clearDraft = false
)

internal fun shortcutText(content: MessageContent?): String {
    return when (content) {
        is MessageText -> content.text.text
        else -> ""
    }.lineSequence().firstOrNull().orEmpty()
}

private suspend fun runRequest(request: suspend () -> TdlResult<*>): Result<Unit> {
    return try {
        when (val result = request()) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}
