package com.mrboomdev.scrollix.data.max.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Minimal MessagePack bridge for the MAX protocol.
 *
 * The session code works with [JsonElement] payloads, while the wire format is
 * MessagePack. Only the types the protocol uses are supported; binary values
 * are converted to hex strings and the web client's big-integer extension
 * (type 1) is decoded as a number.
 */
internal fun encodeMsgPack(value: JsonElement): ByteArray {
    val sink = ByteSink()
    sink.writeElement(value)
    return sink.toByteArray()
}

internal fun decodeMsgPack(bytes: ByteArray): JsonElement {
    return MsgPackReader(bytes).readValue()
}

private class ByteSink {
    private var buffer = ByteArray(256)
    private var size = 0

    fun write(byte: Int) {
        if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        buffer[size++] = byte.toByte()
    }

    fun write(bytes: ByteArray) {
        if (size + bytes.size > buffer.size) {
            buffer = buffer.copyOf((size + bytes.size).coerceAtLeast(buffer.size * 2))
        }
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun writeShort(value: Int) {
        write(value ushr 8)
        write(value)
    }

    fun writeInt(value: Int) {
        write(value ushr 24)
        write(value ushr 16)
        write(value ushr 8)
        write(value)
    }

    fun writeLong(value: Long) {
        for (shift in 56 downTo 0 step 8) write((value ushr shift).toInt())
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    fun writeElement(element: JsonElement) {
        when (element) {
            is JsonNull -> write(0xC0)

            is JsonObject -> {
                val count = element.size
                when {
                    count < 16 -> write(0x80 or count)
                    count <= 0xFFFF -> {
                        write(0xDE)
                        writeShort(count)
                    }

                    else -> {
                        write(0xDF)
                        writeInt(count)
                    }
                }

                element.forEach { (key, value) ->
                    writeString(key)
                    writeElement(value)
                }
            }

            is JsonArray -> {
                val count = element.size
                when {
                    count < 16 -> write(0x90 or count)
                    count <= 0xFFFF -> {
                        write(0xDC)
                        writeShort(count)
                    }

                    else -> {
                        write(0xDD)
                        writeInt(count)
                    }
                }

                element.forEach(::writeElement)
            }

            is JsonPrimitive -> when {
                element.isString -> writeString(element.content)
                element.booleanOrNull != null -> write(if (element.booleanOrNull == true) 0xC3 else 0xC2)
                element.content.contains('.') ||
                    element.content.contains('e', ignoreCase = true) -> writeDouble(element.content.toDoubleOrNull() ?: 0.0)

                else -> {
                    val long = element.longOrNull
                    if (long != null) writeInteger(long) else writeDouble(element.doubleOrNull ?: 0.0)
                }
            }
        }
    }

    fun writeString(value: String) {
        val bytes = value.encodeToByteArray()
        when {
            bytes.size < 32 -> write(0xA0 or bytes.size)
            bytes.size <= 0xFF -> {
                write(0xD9)
                write(bytes.size)
            }

            bytes.size <= 0xFFFF -> {
                write(0xDA)
                writeShort(bytes.size)
            }

            else -> {
                write(0xDB)
                writeInt(bytes.size)
            }
        }

        write(bytes)
    }

    fun writeInteger(value: Long) {
        when {
            value in 0..0x7F -> write(value.toInt())
            value in -32..-1 -> write(0x100 + value.toInt())
            value in 0..0xFF -> {
                write(0xCC)
                write(value.toInt())
            }

            value in 0..0xFFFF -> {
                write(0xCD)
                writeShort(value.toInt())
            }

            value in 0..0xFFFFFFFFL -> {
                write(0xCE)
                writeInt(value.toInt())
            }

            value >= 0 -> {
                write(0xCF)
                writeLong(value)
            }

            value >= -0x80 -> {
                write(0xD0)
                write(value.toInt())
            }

            value >= -0x8000 -> {
                write(0xD1)
                writeShort(value.toInt())
            }

            value >= Int.MIN_VALUE -> {
                write(0xD2)
                writeInt(value.toInt())
            }

            else -> {
                write(0xD3)
                writeLong(value)
            }
        }
    }

    fun writeDouble(value: Double) {
        write(0xCB)
        writeLong(value.toRawBits())
    }
}

private class MsgPackReader(private val bytes: ByteArray) {
    private var index = 0

    fun readValue(): JsonElement {
        // Dispatch needs the unsigned byte: every type marker at or above 0x80
        // would otherwise arrive negative and decode as a fixint.
        val type = readByte() and 0xFF
        return when {
            type <= 0x7F -> JsonPrimitive(type)
            type >= 0xE0 -> JsonPrimitive(type - 0x100)

            type in 0x80..0x8F -> readMap(type and 0x0F)
            type in 0x90..0x9F -> readArray(type and 0x0F)
            type in 0xA0..0xBF -> JsonPrimitive(readString(type and 0x1F))

            type == 0xC0 -> JsonNull
            type == 0xC2 -> JsonPrimitive(false)
            type == 0xC3 -> JsonPrimitive(true)

            type == 0xC4 -> JsonPrimitive(readBinary(readByte().toInt() and 0xFF))
            type == 0xC5 -> JsonPrimitive(readBinary(readShort()))
            type == 0xC6 -> JsonPrimitive(readBinary(readInt()))

            type == 0xCA -> JsonPrimitive(readFloat().toDouble())
            type == 0xCB -> JsonPrimitive(readDouble())

            type == 0xCC -> JsonPrimitive(readByte().toLong() and 0xFF)
            type == 0xCD -> JsonPrimitive(readShort().toLong())
            type == 0xCE -> JsonPrimitive(readInt().toLong() and 0xFFFFFFFFL)
            type == 0xCF -> {
                val value = readLong()
                if (value >= 0) JsonPrimitive(value) else JsonPrimitive(value.toULong().toString())
            }

            type == 0xD0 -> JsonPrimitive(readByte().toLong())
            type == 0xD1 -> JsonPrimitive(readShort().toShort().toLong())
            type == 0xD2 -> JsonPrimitive(readInt().toLong())
            type == 0xD3 -> JsonPrimitive(readLong())

            type == 0xD9 -> JsonPrimitive(readString(readByte().toInt() and 0xFF))
            type == 0xDA -> JsonPrimitive(readString(readShort()))
            type == 0xDB -> JsonPrimitive(readString(readInt()))

            type == 0xDC -> readArray(readShort())
            type == 0xDD -> readArray(readInt())
            type == 0xDE -> readMap(readShort())
            type == 0xDF -> readMap(readInt())

            type in 0xD4..0xD8 -> readExtension((type and 0x07) + 1)
            type == 0xC7 -> readExtension(readByte().toInt() and 0xFF)
            type == 0xC8 -> readExtension(readShort())
            type == 0xC9 -> readExtension(readInt())

            else -> JsonNull
        }
    }

    private fun readMap(size: Int): JsonObject {
        val map = LinkedHashMap<String, JsonElement>(size)

        repeat(size) {
            val key = readValue()
            val name = when (key) {
                is JsonPrimitive -> key.content
                else -> key.toString()
            }
            map[name] = readValue()
        }

        return JsonObject(map)
    }

    private fun readArray(size: Int): JsonArray {
        val values = ArrayList<JsonElement>(size)
        repeat(size) { values += readValue() }
        return JsonArray(values)
    }

    /**
     * The web client registers extension type 1 for numbers that do not fit a
     * double; the extension body is a nested MessagePack integer.
     */
    private fun readExtension(size: Int): JsonElement {
        val type = readByte().toInt() and 0xFF
        val start = index

        index += size
        if (index > bytes.size) index = bytes.size

        if (type != 1) return JsonNull

        val nested = try {
            val reader = MsgPackReader(bytes.copyOfRange(start, start + size))
            reader.readValue()
        } catch (_: Exception) {
            JsonNull
        }

        return nested
    }

    private fun readString(size: Int): String {
        val end = (index + size).coerceAtMost(bytes.size)
        val value = bytes.decodeToString(index, end)
        index = end
        return value
    }

    private fun readBinary(size: Int): String {
        val end = (index + size).coerceAtMost(bytes.size)
        val builder = StringBuilder((end - index) * 2)

        for (position in index until end) {
            val value = bytes[position].toInt() and 0xFF
            builder.append(HexDigits[value ushr 4])
            builder.append(HexDigits[value and 0x0F])
        }

        index = end
        return builder.toString()
    }

    private fun readByte(): Int {
        if (index >= bytes.size) return 0
        return bytes[index++].toInt()
    }

    private fun readShort(): Int {
        val high = readByte() and 0xFF
        val low = readByte() and 0xFF
        return (high shl 8) or low
    }

    private fun readInt(): Int {
        var value = 0
        repeat(4) { value = (value shl 8) or (readByte() and 0xFF) }
        return value
    }

    private fun readLong(): Long {
        var value = 0L
        repeat(8) { value = (value shl 8) or (readByte().toLong() and 0xFF) }
        return value
    }

    private fun readFloat(): Float = Float.fromBits(readInt())

    private fun readDouble(): Double = Double.fromBits(readLong())

    private companion object {
        const val HexDigits = "0123456789abcdef"
    }
}
