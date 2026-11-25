package com.mrboomdev.scrollix.data.vk

import kotlinx.serialization.json.JsonObject
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.vk.network.array
import com.mrboomdev.scrollix.data.vk.network.bool
import com.mrboomdev.scrollix.data.vk.network.int
import com.mrboomdev.scrollix.data.vk.network.long
import com.mrboomdev.scrollix.data.vk.network.mediaIdForUrl
import com.mrboomdev.scrollix.data.vk.network.obj
import com.mrboomdev.scrollix.data.vk.network.objects
import com.mrboomdev.scrollix.data.vk.network.peerGroupId
import com.mrboomdev.scrollix.data.vk.network.peerUserId
import com.mrboomdev.scrollix.data.vk.network.string

internal fun VkSession.mapDialog(conversation: JsonObject): Dialog? {
    val peer = conversation.obj("peer") ?: return null
    val peerId = peer.long("id") ?: return null
    val type = peer.string("type") ?: when {
        peerId > 2_000_000_000L -> "chat"
        peerId < 0 -> "group"
        else -> "user"
    }

    val settings = conversation.obj("chat_settings")
    val last = conversation.obj("last_message")
    val user = users[peerUserId(peerId) ?: 0]
    val group = groups[peerGroupId(peerId) ?: 0]

    val title = when (type) {
        "user" -> user?.name
        "group" -> group?.name
        else -> settings.string("title") ?: group?.name
    } ?: "VK chat"

    val avatar = when (type) {
        "user" -> user?.avatar()
        "group" -> group?.avatar()
        else -> settings.obj("photo")?.string("photo_100")
            ?.let { url -> Avatar(initials = title.take(1), colorSeed = title.hashCode(), url = url) }
    } ?: Avatar.fromName(title)

    val timestamp = (last?.long("date") ?: 0L) * 1000

    return Dialog(
        providerId = VkProviderId,
        accountId = accountId,
        providerDialogId = peerId.toString(),
        identity = DialogIdentity(VkProviderId, peerId.toString()),
        kind = when (type) {
            "group" -> DialogKind.Group
            "chat" -> DialogKind.Group
            else -> DialogKind.Direct
        },
        title = title,
        statusText = user?.status,
        preview = conversationPreview(last),
        lastMessageIsOutgoing = last?.bool("out") ?: false,
        timestamp = timestamp,
        unreadCount = conversation.int("unread_count") ?: 0,
        memberCount = settings.int("members_count") ?: group?.membersCount,
        pinnedMessageId = settings.obj("pinned_message").long("id"),
        canSendMessages = conversation.obj("can_write")?.bool("allowed") ?: true,
        isPinned = conversation.bool("is_pinned") ?: false,
        isMarkedAsUnread = conversation.bool("is_marked_unread") ?: false,
        order = timestamp,
        avatar = avatar
    )
}

internal fun VkSession.conversationPreview(last: JsonObject?): String {
    if (last == null) return ""

    val text = last.string("text").orEmpty()
    if (text.isNotBlank()) return text

    val attachment = last.array("attachments").objects().firstOrNull() ?: return ""
    return attachmentLabel(attachment.string("type"))
}

// ------------------------------------------------------------- history --

internal fun VkSession.indexExtended(root: JsonObject) {
    root.array("profiles").objects().forEach { profile ->
        val id = profile.long("id") ?: return@forEach
        users[id] = VkUser(
            id = id,
            name = listOfNotNull(
                profile.string("first_name"),
                profile.string("last_name")
            ).joinToString(" ").ifBlank { "VK user" },
            photoUrl = profile.string("photo_100") ?: profile.string("photo_200"),
            status = profile.string("status")
        )
    }

    root.array("groups").objects().forEach { group ->
        val id = group.long("id") ?: return@forEach
        groups[id] = VkGroup(
            id = id,
            name = group.string("name") ?: "VK community",
            photoUrl = group.string("photo_100") ?: group.string("photo_200"),
            membersCount = group.int("members_count")
        )
    }
}

internal fun VkSession.mapMessage(peerId: Long, raw: JsonObject): ChatMessage? {
    val messageId = raw.long("id") ?: return null
    val fromId = raw.long("from_id") ?: 0L
    val date = raw.long("date") ?: 0L
    val action = raw.obj("action")

    val senderUser = users[peerUserId(fromId) ?: 0]
    val senderGroup = groups[peerGroupId(fromId) ?: 0]
    val senderName = senderUser?.name ?: senderGroup?.name
    val senderAvatar = senderUser?.avatar() ?: senderGroup?.avatar()

    val attachments = raw.array("attachments").objects()
    val media = attachments.firstOrNull()?.let(::mapAttachment)

    val reply = raw.obj("reply_message")?.let { reply ->
        MessageReply(
            messageId = reply.long("id") ?: 0L,
            senderName = users[reply.long("from_id") ?: 0]?.name,
            text = reply.string("text").orEmpty(),
            media = reply.array("attachments").objects().firstOrNull()
                ?.let(::mapAttachment)
        )
    }

    val forwarded = raw.array("fwd_messages").objects().firstOrNull()

    return ChatMessage(
        providerId = VkProviderId,
        accountId = accountId,
        providerDialogId = peerId.toString(),
        messageId = messageId,
        senderName = senderName,
        senderAvatar = senderAvatar,
        senderId = fromId,
        forwardedFromName = forwarded?.let { message ->
            users[message.long("from_id") ?: 0]?.name
        },
        text = action?.let(::actionText) ?: raw.string("text").orEmpty(),
        timestamp = date * 1000,
        isOutgoing = raw.bool("out") ?: false,
        isService = action != null,
        media = media,
        replyTo = reply,
        canBeDeleted = true,
        canBeDeletedForEveryone = raw.bool("out") ?: false,
        canBeForwarded = true,
        sendState = MessageSendState.Sent,
        isRead = raw.bool("read_state") ?: true
    )
}

internal fun VkSession.actionText(action: JsonObject): String {
    val type = action.string("type").orEmpty()
    val memberId = action.long("member_id")
    val member = memberId?.let { id -> users[id]?.name }

    return when (type) {
        "chat_photo_update" -> "Chat photo updated"
        "chat_photo_remove" -> "Chat photo removed"
        "chat_create" -> "Chat created"
        "chat_title_update" -> "Chat renamed to \"${action.string("text").orEmpty()}\""
        "chat_invite_user" -> "${member ?: "A user"} joined the chat"
        "chat_kick_user" -> "${member ?: "A user"} left the chat"
        "chat_pin_message" -> "Message pinned"
        "chat_unpin_message" -> "Message unpinned"
        "chat_invite_user_by_link" -> "Someone joined by link"
        else -> type.replace('_', ' ').replaceFirstChar(Char::uppercase)
    }
}

internal fun VkSession.mapAttachment(attachment: JsonObject): MessageMedia? {
    return when (attachment.string("type")) {
        "photo" -> {
            val sizes = attachment.obj("photo")?.array("sizes").objects().orEmpty()
            val best = sizes.maxByOrNull { size ->
                (size.int("width") ?: 0) * (size.int("height") ?: 0)
            } ?: return null
            val url = best.string("url") ?: return null

            MessageMedia(
                kind = MessageMediaKind.Photo,
                previewFileId = registerMedia(url),
                previewUniqueId = url,
                fileId = registerMedia(url),
                fileUniqueId = url,
                width = best.int("width") ?: 0,
                height = best.int("height") ?: 0
            )
        }

        "sticker" -> {
            val sticker = attachment.obj("sticker") ?: return null
            val images = sticker.array("images").objects()
            val image = images.maxByOrNull { it.int("width") ?: 0 } ?: return null
            val url = image.string("url") ?: return null

            MessageMedia(
                kind = MessageMediaKind.Sticker,
                previewFileId = registerMedia(url),
                previewUniqueId = url,
                fileId = registerMedia(url),
                fileUniqueId = url,
                width = image.int("width") ?: 0,
                height = image.int("height") ?: 0,
                stickerFormat = MessageStickerFormat.Webp,
                stickerEmoji = sticker.string("emoji")
            )
        }

        "doc" -> {
            val doc = attachment.obj("doc") ?: return null
            val url = doc.string("url") ?: return null
            val extension = doc.string("ext").orEmpty()

            MessageMedia(
                kind = if (extension.equals("gif", ignoreCase = true)) {
                    MessageMediaKind.Animation
                } else {
                    MessageMediaKind.File
                },
                previewFileId = registerMedia(url),
                previewUniqueId = url,
                fileId = registerMedia(url),
                fileUniqueId = url,
                fileName = doc.string("title"),
                mimeType = doc.string("ext")?.let { "application/$it" },
                size = doc.long("size") ?: 0
            )
        }

        "audio_message" -> {
            val audio = attachment.obj("audio_message") ?: return null
            val url = audio.string("link_mp3") ?: audio.string("link_ogg") ?: return null

            MessageMedia(
                kind = MessageMediaKind.VoiceNote,
                fileId = registerMedia(url),
                fileUniqueId = url,
                durationSeconds = audio.int("duration") ?: 0
            )
        }

        "video" -> {
            val video = attachment.obj("video") ?: return null
            val url = video.string("photo_800")
                ?: video.string("photo_320")
                ?: video.array("image").objects().lastOrNull()?.string("url")

            if (url == null) return null

            MessageMedia(
                kind = MessageMediaKind.Video,
                previewFileId = registerMedia(url),
                previewUniqueId = url,
                fileId = registerMedia(url),
                fileUniqueId = url,
                durationSeconds = video.int("duration") ?: 0,
                width = video.int("width") ?: 0,
                height = video.int("height") ?: 0
            )
        }

        "audio" -> {
            val audio = attachment.obj("audio") ?: return null
            val url = audio.string("url") ?: return null

            MessageMedia(
                kind = MessageMediaKind.Audio,
                fileId = registerMedia(url),
                fileUniqueId = url,
                fileName = audio.string("title"),
                durationSeconds = audio.int("duration") ?: 0
            )
        }

        else -> null
    }
}

internal fun VkSession.attachmentLabel(type: String?): String = when (type) {
    "photo" -> "[Photo]"
    "video" -> "[Video]"
    "doc" -> "[File]"
    "sticker" -> "[Sticker]"
    "audio_message" -> "[Voice message]"
    "audio" -> "[Audio]"
    else -> "[Attachment]"
}

internal fun VkSession.registerMedia(url: String): Int {
    val id = mediaIdForUrl(url)
    mediaUrls[id] = url
    return id
}

// -------------------------------------------------------------- sending --
