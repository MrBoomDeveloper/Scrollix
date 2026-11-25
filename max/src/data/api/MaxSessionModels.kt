package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.max.network.array
import com.mrboomdev.scrollix.data.max.network.bool
import com.mrboomdev.scrollix.data.max.network.int
import com.mrboomdev.scrollix.data.max.network.long
import com.mrboomdev.scrollix.data.max.network.obj
import com.mrboomdev.scrollix.data.max.network.objects
import com.mrboomdev.scrollix.data.max.network.string
import com.mrboomdev.scrollix.data.model.MessageLocationInfo
import com.mrboomdev.scrollix.data.model.MessagePollInfo
import com.mrboomdev.scrollix.data.model.MessagePollOptionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal val MentionUsernameRegex = Regex("""@([A-Za-z0-9_]{2,64})""")

internal data class MaxVideoRef(
    val chatId: Long,
    val messageId: Long,
    val videoId: Long,
    val token: String?
)

internal data class MaxPollOption(
    val id: String,
    val text: String,
    val votes: Int,
    val isChosen: Boolean
)

internal data class MaxPoll(
    val id: Long,
    val question: String,
    val options: List<MaxPollOption>,
    val totalVoterCount: Int,
    val isAnonymous: Boolean,
    val allowsMultipleAnswers: Boolean,
    val isClosed: Boolean
) {
    fun toPollInfo(): MessagePollInfo {
        val total = totalVoterCount.coerceAtLeast(options.sumOf { it.votes })

        return MessagePollInfo(
            id = id,
            question = question,
            options = options.map { option ->
                MessagePollOptionInfo(
                    id = option.id,
                    text = option.text,
                    voterCount = option.votes,
                    votePercentage = if (total > 0) option.votes * 100 / total else 0,
                    isChosen = option.isChosen
                )
            },
            totalVoterCount = total,
            isClosed = isClosed,
            isAnonymous = isAnonymous,
            allowsMultipleAnswers = allowsMultipleAnswers,
            canSeeResults = true
        )
    }
}

internal data class MaxAttach(
    val type: String,
    val url: String?,
    val remoteId: String?,
    val fileName: String?,
    val mimeType: String?,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationSeconds: Int,
    val isGif: Boolean = false,
    /** Static thumbnail URL of a video/GIF attachment. */
    val thumbnailUrl: String? = null,
    /** Server id and token of a video/GIF whose bytes need a URL request. */
    val videoId: Long? = null,
    val videoToken: String? = null,
    /** Decoded `previewData` payload (a base64 data URI) for the poster. */
    val previewBytes: ByteArray? = null,
    val stickerId: Long? = null,
    val setId: Long? = null,
    val lottieUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val livePeriodSeconds: Int = 0,
    val poll: MaxPoll? = null
) {
    val previewLabel: String
        get() = when (type.uppercase()) {
            "PHOTO" -> "Photo"
            "VIDEO" -> "Video"
            "AUDIO", "UNSUPPORTED" -> "Voice message"
            "FILE" -> fileName ?: "File"
            "STICKER" -> "Sticker"
            "GIF" -> "GIF"
            "CALL" -> "Call"
            "CONTROL" -> ""
            "POLL" -> "Poll"
            "LOCATION" -> "Location"
            else -> type.lowercase().replaceFirstChar(Char::uppercase)
        }

    fun toLocationInfo(): MessageLocationInfo? {
        val latitude = latitude ?: return null
        val longitude = longitude ?: return null

        return MessageLocationInfo(
            latitude = latitude,
            longitude = longitude,
            isLive = livePeriodSeconds > 0,
            expiresInSeconds = livePeriodSeconds
        )
    }

    fun toPollInfo(): MessagePollInfo? = poll?.toPollInfo()
}

internal data class MaxMessage(
    val id: Long,
    val senderId: Long?,
    val text: String,
    val time: Long,
    val attaches: List<MaxAttach>,
    val reactions: List<MessageReactionInfo>,
    val views: Int,
    val forwardedFromName: String?,
    val replyTo: MessageReply?,
    val isService: Boolean
)

/** An image payload MAX accepts as a photo, with its upload metadata. */
internal data class MaxImageUpload(
    val extension: String,
    val contentType: String
)

/** Where an upload request wants the bytes, with the ids the attach needs. */
internal data class MaxUploadTarget(
    val url: String,
    val token: String?,
    val id: Long? = null,
    /** `FILE_UPLOAD` ids are UUID strings, not numbers. */
    val fileKey: String? = null
)

/** A photo prepared by `IMAGE_UPLOAD_URL` (80), ready to be attached. */
internal data class MaxPhotoUpload(
    val token: String?,
    val photoId: Long?
)

internal data class MaxChat(
    val id: Long,
    val type: String,
    val title: String?,
    val description: String?,
    val status: String?,
    val ownerId: Long,
    val adminIds: Set<Long> = emptySet(),
    val participantIds: List<Long>,
    val participantsCount: Int?,
    val pictureUrl: String?,
    val lastMessage: MaxMessage?,
    val unread: Int,
    val lastEventTime: Long,
    val pinnedMessageId: Long?,
    val isVerified: Boolean = false,
    /** Channels may forbid copying their posts to other chats. */
    val canCopyMessages: Boolean = true,
    /** The chat lets every member pin (channel `ALL_CAN_PIN_MESSAGE`). */
    val allCanPinMessages: Boolean = false
)

internal data class StoredMaxSession(
    val token: String,
    val deviceId: String
)

/** Result of AUTH_REQUEST (17), the first step of the SMS sign-in. */
internal data class MaxCodeRequest(
    val trackId: String,
    val codeLength: Int,
    val blockingDurationSeconds: Int
)

/**
 * AUTH_REQUEST (17) answers with `trackId`, `codeLength` and
 * `blockingDuration`; the track id becomes the `token` of the follow-up
 * AUTH (18) request. Alternate flows call the same value `token`, so both
 * keys are accepted.
 */
internal fun parseCaptchaLink(response: JsonElement?): String? {
    val root = response as? JsonObject ?: return null
    return root.string("link")?.takeIf { it.startsWith("http") }
}

/**
 * MAX contacts carry the avatar as `baseUrl`/`baseRawUrl` and the phone as a
 * bare international number (`{country}{subscriber}`); the older
 * `picture`/`phones` shapes are kept as a fallback.
 */
internal fun parseMaxUser(objectValue: JsonObject): MaxUser? {
    val id = objectValue.long("id") ?: return null

    val phone = objectValue.long("phone")
        ?.takeIf { it > 0L }
        ?.let { number -> formatPhoneNumber("+$number") }
        ?: objectValue.array("phones").objects().firstOrNull()?.string("number")

    return MaxUser(
        id = id,
        name = maxContactName(objectValue),
        pictureUrl = objectValue.string("baseRawUrl")
            ?: objectValue.string("baseUrl")
            ?: objectValue.obj("picture")?.string("url"),
        username = maxUsernameOf(objectValue),
        phone = phone,
        description = objectValue.string("about") ?: objectValue.string("description"),
        isVerified = maxVerifiedFlag(objectValue),
        isServiceAccount = objectValue.bool("isServiceAccount") == true
    )
}

/**
 * MAX exposes the handle as `username` on some payloads and only as a profile
 * `link` (`https://max.ru/u/<handle>`) on others. Showing the raw link where
 * the UI prints `@handle` reads wrong, so the handle is extracted.
 */
internal fun maxUsernameOf(objectValue: JsonObject): String? {
    val raw = objectValue.string("username") ?: objectValue.string("link") ?: return null

    return normalizeMaxUsername(raw)
}

internal fun normalizeMaxUsername(raw: String): String? {
    val trimmed = raw.trim().removePrefix("@").trim()
    if (trimmed.isEmpty()) return null

    if (!trimmed.startsWith("http", ignoreCase = true)) return trimmed

    return trimmed.substringAfterLast('/')
        .substringBefore('?')
        .takeIf { it.isNotBlank() }
}

/** MAX flags official channels and accounts with `isVerified`/`verified`. */
internal fun maxVerifiedFlag(objectValue: JsonObject): Boolean {
    return objectValue.bool("isVerified") == true ||
        objectValue.bool("verified") == true ||
        objectValue.bool("is_verified") == true
}

/**
 * Names come as `{type: ONEME|CUSTOM, name?, firstName?, lastName?}` entries.
 * The display name joins the first and last name with a space, preferring the
 * ONEME entry; an explicit `name` wins when the server filled it in.
 */
internal fun maxContactName(objectValue: JsonObject): String {
    val names = objectValue.array("names").objects()
    val preferred = names.firstOrNull { it.string("type") == "ONEME" }
        ?: names.firstOrNull()

    // `name` is often only the first name, so the first and last name are
    // joined first and `name` is just a fallback.
    fun fullNameOf(entry: JsonObject?): String? {
        entry ?: return null

        return listOfNotNull(
            entry.string("firstName"),
            entry.string("lastName")
        ).joinToString(" ").ifBlank { entry.string("name") }
    }

    return fullNameOf(preferred)
        ?: objectValue.string("name")
        ?: objectValue.string("title")
        ?: fullNameOf(objectValue)
        ?: names.firstNotNullOfOrNull(::fullNameOf)
        ?: "MAX user"
}

/** What CODE_ENTER (18) replied: a session, an extra password step, or nothing usable. */
internal sealed interface MaxCodeEntryResult {
    data class LoggedIn(val token: String) : MaxCodeEntryResult
    data class PasswordRequired(val trackId: String, val hint: String?) : MaxCodeEntryResult
    data class RegistrationRequired(val token: String) : MaxCodeEntryResult
    data class Rejected(val message: String?) : MaxCodeEntryResult
}

/**
 * CODE_ENTER answers with `tokenAttrs.LOGIN.token` for a finished sign-in. A
 * protected account instead gets a `passwordChallenge` that has to be solved
 * with `PASSWORD_CHECK` (115), and an unknown number gets a `REGISTER` token
 * for the profile-creation step. Reading only the login token made protected
 * accounts look like the code was rejected.
 */
internal fun parseCodeEntry(response: JsonElement?): MaxCodeEntryResult {
    val root = response as? JsonObject ?: return MaxCodeEntryResult.Rejected(null)
    val tokenAttrs = root.obj("tokenAttrs")

    tokenAttrs?.obj("LOGIN")?.string("token")?.let { token ->
        return MaxCodeEntryResult.LoggedIn(token)
    }

    val challenge = root.obj("passwordChallenge")
    val trackId = challenge?.string("trackId")

    if (trackId != null) {
        return MaxCodeEntryResult.PasswordRequired(trackId, challenge.string("hint"))
    }

    tokenAttrs?.obj("REGISTER")?.string("token")?.let { token ->
        return MaxCodeEntryResult.RegistrationRequired(token)
    }

    return MaxCodeEntryResult.Rejected(root.string("localizedMessage"))
}

internal fun parseMaxCodeRequest(response: JsonElement?): MaxCodeRequest? {
    val root = response as? JsonObject ?: return null
    val trackId = root.string("trackId") ?: root.string("token") ?: return null

    return MaxCodeRequest(
        trackId = trackId,
        codeLength = (root.int("codeLength") ?: 6).coerceAtLeast(1),
        blockingDurationSeconds = (root.int("blockingDuration") ?: 0).coerceAtLeast(0)
    )
}
