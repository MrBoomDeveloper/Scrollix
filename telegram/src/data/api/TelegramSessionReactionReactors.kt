package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReactionReactor
import com.mrboomdev.scrollix.data.model.MessageReactionReactorsPage
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update

/**
 * Fetches the complete list of reactors for one reaction. The message itself
 * only carries a recent window (which is why the sheet shows "and N others"),
 * so the rest is read page by page from the provider.
 */
internal suspend fun TelegramSession.loadMessageReactionReactorsImpl(
    providerDialogId: String,
    messageId: Long,
    reaction: MessageReactionOption,
    offset: String,
    limit: Int
): Result<MessageReactionReactorsPage> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(
            SessionException(SessionErrorCode.InvalidRequest, "Invalid Telegram chat id")
        )

    return try {
        when (val result = client.getMessageAddedReactions(
            chatId = chatId,
            messageId = messageId,
            reactionType = reactionType(reaction.emoji, reaction.customEmojiId),
            offset = offset,
            limit = limit.coerceIn(1, LIMIT)
        )) {
            is TdlResult.Success -> {
                val page = result.result
                val reactors = page.reactions.mapNotNull { added ->
                    val sender = added.senderId as? MessageSenderUser ?: return@mapNotNull null
                    val user = resolveReactorUser(sender.userId) ?: return@mapNotNull null

                    MessageReactionReactor(
                        id = sender.userId,
                        name = userDisplayName(user),
                        avatar = userAvatar(user)
                    )
                }

                Result.success(
                    MessageReactionReactorsPage(
                        totalCount = page.totalCount,
                        reactors = reactors,
                        nextOffset = page.nextOffset
                    )
                )
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

private const val LIMIT = 100

/** The cached user, or one loaded on demand so a reactor always has a name. */
private suspend fun TelegramSession.resolveReactorUser(userId: Long): User? {
    _users.value[userId]?.let { return it }

    return when (val result = client.getUser(userId)) {
        is TdlResult.Success -> result.result.also { user ->
            _users.update { it + (userId to user) }
        }

        is TdlResult.Failure -> null
    }
}
