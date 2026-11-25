package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.max.network.maxInvalidChatId
import com.mrboomdev.scrollix.data.max.network.maxNotConnected
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun MaxSession.addMessageReactionImpl(providerDialogId: String, messageId: Long, emoji: String, customEmojiId: Long?): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())
    val active = rpc ?: return Result.failure(maxNotConnected())

    // MAX accepted more than one reaction payload shape across its
    // clients (`reactionType`/`id`, `type`/`emoji`, or a bare emoji).
    // Trying them in turn keeps reactions working across deployments.
    val shapes = listOf(
        buildJsonObject {
            put("chatId", chatId)
            put("messageId", messageId.toString())
            put(
                "reaction",
                buildJsonObject {
                    put("reactionType", "EMOJI")
                    put("id", emoji)
                }
            )
        },
        buildJsonObject {
            put("chatId", chatId)
            put("messageId", messageId.toString())
            put(
                "reaction",
                buildJsonObject {
                    put("type", "EMOJI")
                    put("emoji", emoji)
                }
            )
        },
        buildJsonObject {
            put("chatId", chatId)
            put("messageId", messageId.toString())
            put("reaction", emoji)
        }
    )

    var lastFailure: Throwable? = null

    shapes.forEach { payload ->
        try {
            val response = active.request(opcode = 178, payload = payload)

            response?.obj("reactionInfo")?.let { info ->
                updateReactions(chatId, messageId, mapReactions(info))
            }

            return Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            lastFailure = exception
        }
    }

    return Result.failure(
        (lastFailure ?: Exception("MAX rejected the reaction")).asSessionException()
    )
}

internal suspend fun MaxSession.removeMessageReactionImpl(providerDialogId: String, messageId: Long, emoji: String, customEmojiId: Long?): Result<Unit> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(maxInvalidChatId())
    val active = rpc ?: return Result.failure(maxNotConnected())

    return try {
        active.request(
            opcode = 179,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("messageId", messageId.toString())
            }
        )

        histories.value[providerDialogId]?.mutate { message ->
            if (message.messageId != messageId) {
                message
            } else {
                message.copy(reactions = message.reactions.filterNot { it.emoji == emoji })
            }
        }

        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.asSessionException())
    }
}

internal suspend fun MaxSession.loadAvailableReactionsImpl(providerDialogId: String, messageId: Long): Result<List<MessageReactionOption>> {
    val reactions = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "👏", "💯")
        .map { emoji -> MessageReactionOption(emoji) }

    return Result.success(reactions)
}

// -------------------------------------------------------------- search --
