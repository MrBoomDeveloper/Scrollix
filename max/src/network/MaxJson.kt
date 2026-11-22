package com.mrboomdev.scrollix.data.max.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * MAX sends message and chat ids either as JSON numbers or as strings,
 * depending on the transport and the endpoint. Every id therefore goes
 * through this accessor.
 */
internal fun JsonElement?.asLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null

    return primitive.content.toLongOrNull()
        ?: primitive.content.toDoubleOrNull()?.toLong()
}

internal fun JsonElement?.long(key: String): Long? {
    return (this as? JsonObject)?.get(key).asLongOrNull()
}

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

    return primitive.content.toBooleanStrictOrNull()
}

internal fun JsonElement?.obj(key: String): JsonObject? {
    return (this as? JsonObject)?.get(key) as? JsonObject
}

internal fun JsonElement?.array(key: String): JsonArray? {
    return (this as? JsonObject)?.get(key) as? JsonArray
}

internal fun JsonArray?.objects(): List<JsonObject> =
    this?.mapNotNull { element -> element as? JsonObject } ?: emptyList()

/** Digits-only phone number in the international `+…` form MAX expects. */
internal fun normalizeMaxPhone(input: String): String {
    val trimmed = input.trim()
    val digits = trimmed.filter(Char::isDigit)
    if (digits.isEmpty()) return trimmed

    val international = when {
        trimmed.startsWith("+") -> digits
        digits.length == 11 && digits.startsWith("8") -> "7" + digits.drop(1)
        digits.length == 11 && digits.startsWith("7") -> digits
        else -> digits
    }

    return "+$international"
}
