package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryCall
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryFile
import dev.g000sha256.tdl.dto.NetworkType
import dev.g000sha256.tdl.dto.NetworkTypeMobile
import dev.g000sha256.tdl.dto.NetworkTypeMobileRoaming
import dev.g000sha256.tdl.dto.NetworkTypeOther
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import com.mrboomdev.scrollix.data.model.ActiveSessionInfo
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatTopic
import com.mrboomdev.scrollix.data.model.NotificationException
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.NotificationSettings
import com.mrboomdev.scrollix.data.model.DataUsagePreset
import com.mrboomdev.scrollix.data.model.DataUsageSummary

internal suspend fun TelegramSession.loadNotificationSettingsImpl(): Result<NotificationSettings> {
    return try {
        val muted = NotificationScope.entries.filter { scope ->
            val settings = when (val result = client.getScopeNotificationSettings(
                scope = scope.toTdlibScope()
            )) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> return Result.failure(result.toSessionException())
            }

            settings.muteFor > 0
        }.toSet()

        Result.success(NotificationSettings(mutedScopes = muted))
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.setScopeMutedImpl(scope: NotificationScope, muted: Boolean): Result<Unit> {
    return try {
        val tdlibScope = scope.toTdlibScope()
        val current = when (val result = client.getScopeNotificationSettings(tdlibScope)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val updated = ScopeNotificationSettings(
            muteFor = if (muted) Int.MAX_VALUE else 0,
            soundId = current.soundId,
            showPreview = current.showPreview,
            useDefaultMuteStories = current.useDefaultMuteStories,
            muteStories = current.muteStories,
            storySoundId = current.storySoundId,
            showStoryPoster = current.showStoryPoster,
            disablePinnedMessageNotifications =
                current.disablePinnedMessageNotifications,
            disableMentionNotifications = current.disableMentionNotifications
        )

        when (val result = client.setScopeNotificationSettings(
            scope = tdlibScope,
            notificationSettings = updated
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

internal suspend fun TelegramSession.setDialogMutedImpl(providerDialogId: String, muted: Boolean): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val chat = when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val current = chat.notificationSettings
        val settings = ChatNotificationSettings(
            useDefaultMuteFor = false,
            muteFor = if (muted) Int.MAX_VALUE else 0,
            useDefaultSound = current.useDefaultSound,
            soundId = current.soundId,
            useDefaultShowPreview = current.useDefaultShowPreview,
            showPreview = current.showPreview,
            useDefaultMuteStories = current.useDefaultMuteStories,
            muteStories = current.muteStories,
            useDefaultStorySound = current.useDefaultStorySound,
            storySoundId = current.storySoundId,
            useDefaultShowStoryPoster = current.useDefaultShowStoryPoster,
            showStoryPoster = current.showStoryPoster,
            useDefaultDisablePinnedMessageNotifications =
                current.useDefaultDisablePinnedMessageNotifications,
            disablePinnedMessageNotifications =
                current.disablePinnedMessageNotifications,
            useDefaultDisableMentionNotifications =
                current.useDefaultDisableMentionNotifications,
            disableMentionNotifications = current.disableMentionNotifications
        )

        when (val result = client.setChatNotificationSettings(
            chatId = chatId,
            notificationSettings = settings
        )) {
            is TdlResult.Success -> {
                when (val refreshed = client.getChat(chatId)) {
                    is TdlResult.Success -> upsertChat(refreshed.result)
                    is TdlResult.Failure -> Unit
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

internal suspend fun TelegramSession.loadNotificationExceptionsImpl(scope: NotificationScope): Result<List<NotificationException>> {
    return try {
        val chatIds = when (val result = client.getChatNotificationSettingsExceptions(
            scope = scope.toTdlibScope(),
            compareSound = false
        )) {
            is TdlResult.Success -> result.result.chatIds
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        val cached = _dialogs.value.associateBy { dialog ->
            dialog.providerDialogId
        }

        // These are the chats that override the category defaults. The
        // list is bounded so opening settings stays one quick round trip.
        val exceptions = chatIds.take(50).mapNotNull { chatId ->
            val chat = when (val result = client.getChat(chatId)) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> return@mapNotNull null
            }

            val dialog = cached[chatId.toString()]
            val settings = chat.notificationSettings

            NotificationException(
                providerDialogId = chatId.toString(),
                title = chat.title.ifBlank { dialog?.title ?: "Chat" },
                avatar = dialog?.avatar ?: Avatar.fromName(chat.title),
                isMuted = !settings.useDefaultMuteFor && settings.muteFor > 0,
                scope = scope
            )
        }

        Result.success(exceptions)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.resetDialogNotificationSettingsImpl(providerDialogId: String): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val chat = when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        // Every "use default" flag makes the chat follow its scope again,
        // which is what removes it from the exceptions list.
        val current = chat.notificationSettings
        val settings = ChatNotificationSettings(
            useDefaultMuteFor = true,
            muteFor = current.muteFor,
            useDefaultSound = true,
            soundId = current.soundId,
            useDefaultShowPreview = true,
            showPreview = current.showPreview,
            useDefaultMuteStories = true,
            muteStories = current.muteStories,
            useDefaultStorySound = true,
            storySoundId = current.storySoundId,
            useDefaultShowStoryPoster = true,
            showStoryPoster = current.showStoryPoster,
            useDefaultDisablePinnedMessageNotifications = true,
            disablePinnedMessageNotifications = current.disablePinnedMessageNotifications,
            useDefaultDisableMentionNotifications = true,
            disableMentionNotifications = current.disableMentionNotifications
        )

        when (val result = client.setChatNotificationSettings(chatId, settings)) {
            is TdlResult.Success -> {
                when (val refreshed = client.getChat(chatId)) {
                    is TdlResult.Success -> upsertChat(refreshed.result)
                    is TdlResult.Failure -> Unit
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

internal suspend fun TelegramSession.loadTopicsImpl(providerDialogId: String, query: String): Result<List<ChatTopic>> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val topics = when (val result = client.getForumTopics(
            chatId = chatId,
            query = query.trim(),
            offsetDate = 0,
            offsetMessageId = 0,
            offsetForumTopicId = 0,
            limit = 100
        )) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return Result.failure(result.toSessionException())
        }

        Result.success(
            topics.topics.map { topic ->
                ChatTopic(
                    id = topic.info.forumTopicId.toLong(),
                    title = topic.info.name.ifBlank {
                        if (topic.info.isGeneral) "General" else "Topic"
                    },
                    unreadCount = topic.unreadCount,
                    isClosed = topic.info.isClosed,
                    lastMessageText = topic.lastMessage?.let(::messagePreview).orEmpty(),
                    lastMessageTimestamp = (topic.lastMessage?.date ?: 0).toLong() * 1000L,
                    iconCustomEmojiId = topic.info.icon?.customEmojiId?.takeIf { it != 0L },
                    isGeneral = topic.info.isGeneral,
                    isPinned = topic.isPinned,
                    // TDLib resolves muteFor even when the default is
                    // inherited, so a positive value means muted.
                    isMuted = topic.notificationSettings.muteFor > 0
                )
            }
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.loadTopicMessagesImpl(providerDialogId: String, topicId: Long, limit: Int): Result<List<ChatMessage>> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        val result = when (val response = client.getForumTopicHistory(
            chatId = chatId,
            forumTopicId = topicId.toInt(),
            fromMessageId = 0,
            offset = 0,
            limit = limit
        )) {
            is TdlResult.Success -> response.result
            is TdlResult.Failure -> return Result.failure(Exception(response.message))
        }

        // TDLib returns newest first; the chat UI renders oldest first.
        Result.success(
            result.messages.filterNotNull().map(::mapMessage).asReversed()
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.sendTopicMessageImpl(providerDialogId: String, topicId: Long, text: String, customEmojis: List<CustomEmojiSpan>): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return Result.failure(IllegalArgumentException("Message is empty"))
    }

    return try {
        when (val result = client.sendMessage(
            chatId = chatId,
            topicId = MessageTopicForum(topicId.toInt()),
            inputMessageContent = InputMessageText(
                text = FormattedText(
                    text = trimmed,
                    entities = customEmojiEntities(text, customEmojis)
                ),
                linkPreviewOptions = null,
                clearDraft = true
            )
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

internal suspend fun TelegramSession.isDialogMutedImpl(providerDialogId: String): Boolean? {
    val chatId = providerDialogId.toLongOrNull() ?: return null

    return try {
        when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> {
                val settings = result.result.notificationSettings
                !settings.useDefaultMuteFor && settings.muteFor > 0
            }

            is TdlResult.Failure -> null
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        null
    }
}

internal suspend fun TelegramSession.loadDataUsageImpl(): Result<DataUsageSummary> {
    if (!ready) return notSignedIn()

    return when (val result = client.getNetworkStatistics(onlyCurrent = false)) {
        is TdlResult.Success -> {
            var sentBytes = 0L
            var receivedBytes = 0L
            var callSeconds = 0.0

            result.result.entries.forEach { entry ->
                when (entry) {
                    is NetworkStatisticsEntryFile -> {
                        sentBytes += entry.sentBytes
                        receivedBytes += entry.receivedBytes
                    }

                    is NetworkStatisticsEntryCall -> {
                        sentBytes += entry.sentBytes
                        receivedBytes += entry.receivedBytes
                        callSeconds += entry.duration
                    }
                }
            }

            Result.success(
                DataUsageSummary(
                    sinceMillis = result.result.sinceDate.toLong() * 1000L,
                    sentBytes = sentBytes,
                    receivedBytes = receivedBytes,
                    callsDurationSeconds = callSeconds.toLong()
                )
            )
        }

        is TdlResult.Failure -> Result.failure(result.toSessionException())
    }
}

internal suspend fun TelegramSession.resetDataUsageStatisticsImpl(): Result<Unit> {
    if (!ready) return notSignedIn()

    return when (val result = client.resetNetworkStatistics()) {
        is TdlResult.Success -> Result.success(Unit)
        is TdlResult.Failure -> Result.failure(result.toSessionException())
    }
}

internal suspend fun TelegramSession.applyDataUsagePresetImpl(preset: DataUsagePreset): Result<Unit> {
    if (!ready) return notSignedIn()

    val presets = when (val result = client.getAutoDownloadSettingsPresets()) {
        is TdlResult.Success -> result.result
        is TdlResult.Failure -> return Result.failure(result.toSessionException())
    }

    val settings = when (preset) {
        DataUsagePreset.Low -> presets.low
        DataUsagePreset.Medium -> presets.medium
        DataUsagePreset.High -> presets.high
    }

    // Applying one profile to every network type is what makes the choice
    // behave like Telegram's "use less data" switch instead of changing
    // only the network the user happens to be on right now.
    val networkTypes: List<NetworkType> = listOf(
        NetworkTypeMobile(),
        NetworkTypeMobileRoaming(),
        NetworkTypeWiFi(),
        NetworkTypeOther()
    )

    networkTypes.forEach { networkType ->
        when (val result = client.setAutoDownloadSettings(settings, networkType)) {
            is TdlResult.Success -> Unit
            is TdlResult.Failure -> return Result.failure(
                result.toSessionException()
            )
        }
    }

    return Result.success(Unit)
}

internal suspend fun TelegramSession.loadActiveSessionsImpl(): Result<List<ActiveSessionInfo>> {
    if (!ready) return notSignedIn()

    return when (val result = client.getActiveSessions()) {
        is TdlResult.Success -> Result.success(
            result.result.sessions
                .map { session -> session.toActiveSessionInfo() }
                .sortedByDescending { session -> session.isCurrent }
        )

        is TdlResult.Failure -> Result.failure(result.toSessionException())
    }
}

internal suspend fun TelegramSession.terminateSessionImpl(sessionId: Long): Result<Unit> {
    if (!ready) return notSignedIn()

    return when (val result = client.terminateSession(sessionId)) {
        is TdlResult.Success -> Result.success(Unit)
        is TdlResult.Failure -> Result.failure(result.toSessionException())
    }
}

internal suspend fun TelegramSession.terminateOtherSessionsImpl(): Result<Unit> {
    if (!ready) return notSignedIn()

    return when (val result = client.terminateAllOtherSessions()) {
        is TdlResult.Success -> Result.success(Unit)
        is TdlResult.Failure -> Result.failure(result.toSessionException())
    }
}
