package com.mrboomdev.scrollix.data.vk.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * VK returns most ids as JSON numbers, but a few endpoints (and every value
 * that came back from Long Poll) use strings. All ids go through this
 * accessor so both forms work.
 */
/** Content of a JSON primitive, regardless of whether it is a string or number. */
internal fun JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null

    return primitive.content.ifBlank { null }
}

internal fun JsonElement?.asLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null

    return primitive.content.toLongOrNull()
        ?: primitive.content.toDoubleOrNull()?.toLong()
}

internal fun JsonElement?.long(key: String): Long? =
    (this as? JsonObject)?.get(key).asLongOrNull()

internal fun JsonElement?.int(key: String): Int? = long(key)?.toInt()

internal fun JsonElement?.double(key: String): Double? {
    val primitive = (this as? JsonObject)?.get(key) as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null

    return primitive.content.toDoubleOrNull()
}

internal fun JsonElement?.string(key: String): String? {
    val primitive = (this as? JsonObject)?.get(key) as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null

    return primitive.content.ifBlank { null }
}

internal fun JsonElement?.bool(key: String): Boolean? {
    val primitive = (this as? JsonObject)?.get(key) as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null

    return when (primitive.content) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}

internal fun JsonElement?.obj(key: String): JsonObject? =
    (this as? JsonObject)?.get(key) as? JsonObject

internal fun JsonElement?.array(key: String): JsonArray? =
    (this as? JsonObject)?.get(key) as? JsonArray

internal fun JsonArray?.objects(): List<JsonObject> =
    this?.mapNotNull { element -> element as? JsonObject } ?: emptyList()

/**
 * Percent-encodes a form body. VK's API accepts `application/x-www-form-urlencoded`
 * for every method, which keeps uploads and calls on one code path.
 */
internal fun encodeForm(params: Map<String, String>): String = params.entries
    .joinToString("&") { (key, value) -> "${percentEncode(key)}=${percentEncode(value)}" }

internal fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val char = byte.toInt() and 0xFF
        val symbol = char.toChar()

        // Only ASCII survives unencoded: `Char.isLetterOrDigit` is true for
        // Cyrillic, which must be sent as UTF-8 percent escapes.
        val isAscii = symbol in 'a'..'z' || symbol in 'A'..'Z' || symbol in '0'..'9'

        if (isAscii || symbol in "-_.~*" || symbol == ' ') {
            append(if (symbol == ' ') '+' else symbol)
        } else {
            append('%')
            append(HexDigits[char shr 4])
            append(HexDigits[char and 0x0F])
        }
    }
}

private const val HexDigits = "0123456789ABCDEF"

/**
 * VK addresses a conversation by a *peer id*: a positive user id for a direct
 * chat, the negated group id for a community, and `2_000_000_000 + chat_id`
 * for a multi-user chat. The provider dialog id is that peer id as a string.
 */
internal fun userPeerId(userId: Long): Long = userId

internal fun groupPeerId(groupId: Long): Long = -groupId

internal fun chatPeerId(chatId: Long): Long = 2_000_000_000L + chatId

internal fun parsePeerId(raw: String): Long? = raw.toLongOrNull()

internal fun peerUserId(peerId: Long): Long? = peerId.takeIf { it > 0 }

internal fun peerGroupId(peerId: Long): Long? = (-peerId).takeIf { peerId < 0 }

internal fun peerChatId(peerId: Long): Long? =
    (peerId - 2_000_000_000L).takeIf { it > 0 }
