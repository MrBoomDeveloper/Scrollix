package com.mrboomdev.scrollix.data.vk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.OutgoingAttachment
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.vk.network.VkApi
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.vk.network.array
import com.mrboomdev.scrollix.data.vk.network.asLongOrNull
import com.mrboomdev.scrollix.data.vk.network.bool
import com.mrboomdev.scrollix.data.vk.network.int
import com.mrboomdev.scrollix.data.vk.network.long
import com.mrboomdev.scrollix.data.vk.network.longPollUrl
import com.mrboomdev.scrollix.data.vk.network.obj
import com.mrboomdev.scrollix.data.vk.network.objects
import com.mrboomdev.scrollix.data.vk.network.parseLongPollPage
import com.mrboomdev.scrollix.data.vk.network.string
import com.mrboomdev.scrollix.data.vk.network.vkHttpRequest
import com.mrboomdev.scrollix.data.vk.network.vkReadFileBytes

internal suspend fun VkSession.uploadAttachment(
    client: VkApi,
    attachment: OutgoingAttachment
): String? {
    val bytes = vkReadFileBytes(attachment.path) ?: return null
    val fileName = attachment.path.substringAfterLast('/').ifBlank { "file" }
    val isImage = attachment.path.substringAfterLast('.', "")
        .lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp")

    return if (attachment.asPhoto && isImage) {
        uploadPhoto(client, fileName, bytes)
    } else {
        uploadDocument(client, fileName, bytes)
    }
}

internal suspend fun VkSession.uploadPhoto(
    client: VkApi,
    fileName: String,
    bytes: ByteArray
): String? {
    val server = client.callObject("photos.getMessagesUploadServer")
    val uploadUrl = server.string("upload_url") ?: return null

    val uploaded = client.upload(
        uploadUrl = uploadUrl,
        fieldName = "photo",
        fileName = fileName,
        bytes = bytes,
        contentType = "image/jpeg"
    ) ?: return null

    val serverId = uploaded.string("server") ?: return null
    val photo = uploaded.string("photo") ?: return null
    val hash = uploaded.string("hash") ?: return null

    val saved = client.callArray(
        "photos.saveMessagesPhoto",
        mapOf("server" to serverId, "photo" to photo, "hash" to hash)
    ).firstOrNull() ?: return null

    val id = saved.long("id") ?: return null
    val ownerId = saved.long("owner_id") ?: return null
    val accessKey = saved.string("access_key")

    return buildString {
        append("photo").append(ownerId).append('_').append(id)
        if (accessKey != null) append('_').append(accessKey)
    }
}

internal suspend fun VkSession.uploadDocument(
    client: VkApi,
    fileName: String,
    bytes: ByteArray
): String? {
    val server = client.callObject(
        "docs.getMessagesUploadServer",
        mapOf("type" to "doc")
    )
    val uploadUrl = server.string("upload_url") ?: return null

    val uploaded = client.upload(
        uploadUrl = uploadUrl,
        fieldName = "file",
        fileName = fileName,
        bytes = bytes,
        contentType = "application/octet-stream"
    ) ?: return null

    val file = uploaded.string("file") ?: return null

    val saved = client.callObject("docs.save", mapOf("file" to file))
    val doc = saved.obj("doc")
        ?: saved.array("docs").objects().firstOrNull()
        ?: return null

    val id = doc.long("id") ?: return null
    val ownerId = doc.long("owner_id") ?: return null
    val accessKey = doc.string("access_key")

    return buildString {
        append("doc").append(ownerId).append('_').append(id)
        if (accessKey != null) append('_').append(accessKey)
    }
}

// ------------------------------------------------------------- messages --

internal suspend fun VkSession.resolveUserProfile(userId: Long): UserProfileDetails {
    val response = api?.callObject(
        "users.get",
        mapOf(
            "user_ids" to userId.toString(),
            "fields" to "photo_200,status,online,last_seen,bdate,about,domain,contacts"
        )
    )

    indexExtended(response ?: JsonObject(emptyMap()))

    val user = response?.array("items")?.objects()?.firstOrNull()
        ?: throw SessionException(SessionErrorCode.NotFound, "VK user not found")

    val name = listOfNotNull(
        user.string("first_name"),
        user.string("last_name")
    ).joinToString(" ").ifBlank { "VK user" }

    users[userId] = VkUser(
        id = userId,
        name = name,
        photoUrl = user.string("photo_200") ?: user.string("photo_100"),
        status = user.string("status")
    )

    return UserProfileDetails(
        userId = userId,
        displayName = name,
        avatar = users[userId]?.avatar(),
        username = user.string("domain"),
        bio = user.string("about"),
        birthdate = user.string("bdate"),
        status = user.string("status"),
        isOnline = user.bool("online") ?: false,
        kind = DialogKind.Direct
    )
}

internal suspend fun VkSession.resolveGroupProfile(groupId: Long): UserProfileDetails {
    val response = api?.call(
        "groups.getById",
        mapOf(
            "group_ids" to groupId.toString(),
            "fields" to "description,members_count,photo_200,status"
        )
    )

    // v5.199 answers with `{"groups": [...]}`; older versions used an
    // `items` array or a bare array, so accept all three shapes.
    val g = when (response) {
        is JsonObject -> response.array("groups")?.objects()?.firstOrNull()
            ?: response.array("items")?.objects()?.firstOrNull()
            ?: response.takeIf { it["id"] != null }

        is JsonArray -> response.objects().firstOrNull()
        else -> null
    } ?: throw SessionException(SessionErrorCode.NotFound, "VK community not found")

    val name = g.string("name") ?: groups[groupId]?.name ?: "VK community"

    return UserProfileDetails(
        displayName = name,
        avatar = Avatar(
            initials = name.take(1),
            colorSeed = name.hashCode(),
            url = g.string("photo_200") ?: g.string("photo_100")
        ),
        status = g.string("status"),
        description = g.string("description"),
        memberCount = g.int("members_count"),
        kind = DialogKind.Group,
        canBlock = false
    )
}

internal fun VkSession.startLongPoll() {
    if (longPollJob?.isActive == true) return

    longPollJob = scope.launch {
        while (!closed) {
            try {
                pollOnce()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                delay(3_000)
            }
        }
    }
}

internal suspend fun VkSession.pollOnce() {
    val client = api ?: return
    val server = client.callObject(
        "messages.getLongPollServer",
        mapOf("need_pts" to "1", "lp_version" to "3")
    )

    var host = server.string("server") ?: return
    var key = server.string("key") ?: return
    var ts = server.string("ts") ?: return

    while (!closed) {
        val body = vkHttpRequest(
            url = longPollUrl(host, key, ts, LongPollWaitSeconds),
            timeoutSeconds = LongPollWaitSeconds + 15
        )

        if (body == null) {
            delay(2_000)
            return
        }

        val page = parseLongPollPage(body, json) ?: return

        if (page.failed != null) {
            val fresh = api?.callObject(
                "messages.getLongPollServer",
                mapOf("need_pts" to "1", "lp_version" to "3")
            ) ?: return

            host = fresh.string("server") ?: return
            key = fresh.string("key") ?: return
            ts = fresh.string("ts") ?: return
            continue
        }

        page.ts?.let { ts = it }
        handleUpdates(page.updates)
    }
}

internal fun VkSession.handleUpdates(updates: List<JsonArray>) {
    var refresh = false

    updates.forEach { update ->
        val code = update.firstOrNull().asLongOrNull()?.toInt()

        when (code) {
            // New or edited message: refresh the list and merge into an
            // already-open history window.
            4, 5 -> {
                refresh = true
                val messageId = update.getOrNull(1).asLongOrNull() ?: return@forEach
                val peerId = update.getOrNull(3).asLongOrNull() ?: return@forEach
                val history = histories.value[peerId.toString()] ?: return@forEach

                scope.launch {
                    runCatching {
                        val current = api?.callObject(
                            "messages.getById",
                            mapOf(
                                "message_ids" to messageId.toString(),
                                "extended" to "1",
                                "fields" to "photo_100"
                            )
                        ) ?: return@launch

                        indexExtended(current)
                        current.array("items").objects()
                            .mapNotNull { item -> mapMessage(peerId, item) }
                            .let(history::merge)
                    }
                }
            }

            // Read receipts and unread counters only affect the chat list.
            6, 7, 80 -> refresh = true

            else -> Unit
        }
    }

    if (refresh) {
        scope.launch { refreshDialogs() }
    }
}

// ---------------------------------------------------------------- misc --

internal suspend fun VkSession.loadUser(client: VkApi, expectedId: Long): VkUser? {
    val response = client.callObject(
        "users.get",
        buildMap {
            if (expectedId > 0) put("user_ids", expectedId.toString())
            put("fields", "photo_100,photo_200,status")
        }
    )

    indexExtended(response)

    return response.array("items").objects().firstOrNull()?.let { item ->
        val id = item.long("id") ?: return@let null
        VkUser(
            id = id,
            name = listOfNotNull(
                item.string("first_name"),
                item.string("last_name")
            ).joinToString(" ").ifBlank { "VK user" },
            photoUrl = item.string("photo_100") ?: item.string("photo_200"),
            status = item.string("status")
        )
    }
}

internal fun VkSession.readStoredSession(): StoredVkSession? {
    val text = storage.readText(sessionPath) ?: return null
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        ?: return null

    val token = root.string("token")?.takeIf(String::isNotBlank) ?: return null
    val userId = root.long("userId") ?: 0L
    return StoredVkSession(token, userId)
}

internal fun VkSession.persistSession(token: String, userId: Long) {
    runCatching {
        storage.writeText(
            sessionPath,
            buildJsonObject {
                put("token", token)
                put("userId", userId)
            }.toString()
        )
    }
}

internal fun VkSession.clearStoredSession() {
    runCatching { storage.delete(sessionPath) }
}
