package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.max.network.MaxPush
import com.mrboomdev.scrollix.data.max.network.MaxRpc
import com.mrboomdev.scrollix.data.max.network.array
import com.mrboomdev.scrollix.data.max.network.asLongOrNull
import com.mrboomdev.scrollix.data.max.network.long
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.max.network.objects
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun MaxSession.startPushLoop(active: MaxRpc) {
    pushJob?.cancel()
    pushJob = scope.launch {
        active.pushes.collect { push -> runCatching { handlePush(push) } }
    }
}

internal fun MaxSession.handlePush(push: MaxPush) {
    val payload = push.payload ?: return

    when (push.opcode) {
        129 -> {
            val chatId = payload.long("chatId") ?: return
            showTyping(chatId, payload.long("userId"))
        }

        130 -> {
            val chatId = payload.long("chatId") ?: return
            val mark = payload.long("mark") ?: return

            histories.value[chatId.toString()]?.mutate { message ->
                if (message.isOutgoing && message.timestamp <= mark) {
                    message.copy(isRead = true)
                } else {
                    message
                }
            }

            emitDialogs()
        }

        140, 142 -> {
            val chatId = payload.long("chatId") ?: return
            val ids = payload.array("messageIds")
                ?.mapNotNull { it.asLongOrNull() }
                ?: listOfNotNull(payload.long("messageId"))

            if (ids.isNotEmpty()) {
                histories.value[chatId.toString()]?.remove(ids.toSet())
            }
        }

        156 -> {
            val chatId = payload.long("chatId") ?: return
            val messageId = payload.long("messageId") ?: return
            val info = payload.obj("reactionInfo") ?: return
            updateReactions(chatId, messageId, mapReactions(info))
        }
    }

    val chatId = payload.long("chatId")
    val messageObject = payload.obj("message")

    if (chatId != null && messageObject != null) {
        val message = parseMessage(chatId, messageObject)
        if (message.id == 0L) return

        messageTimes[message.id] = message.time

        // A partial push (for example a payload that only carries an id)
        // must not replace a complete message. Edits carry their new text
        // or attachments, so requiring one of those keeps media intact.
        val hasContent = messageObject.containsKey("text") ||
            messageObject.containsKey("attaches")

        if (hasContent) {
            histories.value[chatId.toString()]
                ?.merge(listOf(toChatMessage(chatId, message)))
        }

        val chat = chats[chatId]
        if (chat != null) {
            val incoming = message.senderId != null && message.senderId != meId
            chats[chatId] = chat.copy(
                lastMessage = message,
                lastEventTime = maxOf(chat.lastEventTime, message.time),
                unread = if (incoming) chat.unread + 1 else chat.unread
            )
        }

        emitDialogs()
    }
}

internal fun MaxSession.showTyping(chatId: Long, userId: Long?) {
    val name = userId?.let { users[it]?.name } ?: "Someone"

    typing.update { it + (chatId to "$name is typing…") }
    emitDialogs()

    typingJobs[chatId]?.cancel()
    typingJobs[chatId] = scope.launch {
        delay(5_000)
        typing.update { it - chatId }
        typingJobs.remove(chatId)
        emitDialogs()
    }
}

internal fun MaxSession.updateReactions(
    chatId: Long,
    messageId: Long,
    reactions: List<MessageReactionInfo>
) {
    histories.value[chatId.toString()]?.mutate { message ->
        if (message.messageId == messageId) {
            message.copy(reactions = reactions)
        } else {
            message
        }
    }
}

internal fun MaxSession.applySentMessage(chatId: Long, message: MaxMessage) {
    messageTimes[message.id] = message.time
    histories.value[chatId.toString()]?.merge(listOf(toChatMessage(chatId, message)))

    chats[chatId]?.let { chat ->
        chats[chatId] = chat.copy(
            lastMessage = message,
            lastEventTime = maxOf(chat.lastEventTime, message.time),
            unread = 0
        )
    }

    emitDialogs()
}

internal fun MaxSession.applyEditedMessage(chatId: Long, messageId: Long, text: String) {
    histories.value[chatId.toString()]?.mutate { message ->
        if (message.messageId == messageId) message.copy(text = text) else message
    }

    val chat = chats[chatId]
    val last = chat?.lastMessage

    if (chat != null && last?.id == messageId) {
        chats[chatId] = chat.copy(lastMessage = last.copy(text = text))
    }

    emitDialogs()
}

// --------------------------------------------------------------- dialogs --

internal suspend fun MaxSession.refreshDialogs() {
    val active = rpc ?: return

    // The indicator means "the first chat list is still syncing". Later
    // refreshes (reconnects, background syncs) used to flip it on again,
    // which left the list spinning, so only the first load reports.
    val reportLoading = !dialogsLoaded
    if (reportLoading) _isLoadingDialogs.value = true

    try {
        // The web client repeats GET_CHATS with the marker returned by the
        // previous page until it becomes 0. The marker is a server
        // timestamp from LOGIN, not a local clock value.
        var marker = chatMarker
        var pages = 0

        while (marker != 0L && pages < 100) {
            val response = active.request(
                opcode = 53,
                payload = buildJsonObject { put("marker", marker) }
            )

            response?.array("chats")?.objects()?.forEach(::mergeChat)

            val next = response?.long("marker") ?: 0L
            if (next == marker) break

            marker = next
            pages++
        }

        chatMarker = 0L
        fetchMissingChatUsers(active)
        prefetchAvatars()
        refreshOwnProfileAvatar()
        emitDialogs()
        dialogsLoaded = true
    } finally {
        if (reportLoading) _isLoadingDialogs.value = false
    }
}

/**
 * DIALOG chats carry no `title`; their display name is the peer contact.
 * LOGIN only returns a handful of contacts, so peers that are not in that
 * snapshot have to be requested explicitly or the row stays nameless.
 */
internal suspend fun MaxSession.fetchMissingChatUsers(active: MaxRpc) {
    val missing = chats.values
        .asSequence()
        .filter { chat -> chat.type == "DIALOG" && chat.id != 0L }
        .mapNotNull { chat -> chat.participantIds.firstOrNull { id -> id != meId } }
        .filter { id -> id !in users }
        .distinct()
        .take(300)
        .toList()

    if (missing.isEmpty()) return

    for (chunk in missing.chunked(100)) {
        val response = runCatching {
            active.request(
                opcode = 32,
                payload = buildJsonObject {
                    put(
                        "contactIds",
                        buildJsonArray { chunk.forEach { id -> add(JsonPrimitive(id)) } }
                    )
                }
            )
        }.getOrNull() ?: continue

        response.array("contacts")?.objects()?.forEach { contact ->
            parseUser(contact)?.let { user -> users[user.id] = user }
        }
    }
}

internal fun MaxSession.mergeChat(objectValue: JsonObject) {
    val id = objectValue.long("id") ?: return
    val parsed = parseChat(objectValue)
    val previous = chats[id]

    chats[id] = parsed.copy(
        title = parsed.title ?: previous?.title,
        description = parsed.description ?: previous?.description,
        pictureUrl = parsed.pictureUrl ?: previous?.pictureUrl,
        participantsCount = parsed.participantsCount ?: previous?.participantsCount,
        participantIds = parsed.participantIds.ifEmpty { previous?.participantIds.orEmpty() },
        adminIds = parsed.adminIds.ifEmpty { previous?.adminIds.orEmpty() },
        isVerified = parsed.isVerified || previous?.isVerified == true,
        canCopyMessages = parsed.canCopyMessages && previous?.canCopyMessages != false,
        allCanPinMessages = parsed.allCanPinMessages ||
            previous?.allCanPinMessages == true,
        lastMessage = parsed.lastMessage ?: previous?.lastMessage,
        unread = if (objectValue.long("newMessages") != null) parsed.unread else previous?.unread ?: 0,
        lastEventTime = maxOf(parsed.lastEventTime, previous?.lastEventTime ?: 0L)
    )
}
