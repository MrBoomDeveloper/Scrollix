package com.mrboomdev.scrollix.data.max.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MaxProtocolCodecTest {

    @Test
    fun msgPackRoundTripsNestedPayload() {
        val source = buildJsonObject {
            put("deviceId", 12345678901L)
            put("verifyCode", "1234")
            put("interactive", true)
            put("ratio", 2.5)
            put("missing", JsonNull)
            put(
                "list",
                buildJsonArray {
                    add(JsonPrimitive(1))
                    add(JsonPrimitive(-7))
                    add(JsonPrimitive("text"))
                    add(JsonNull)
                }
            )
            put(
                "nested",
                buildJsonObject {
                    put("empty", buildJsonArray { })
                    put("flag", false)
                }
            )
        }

        val decoded = decodeMsgPack(encodeMsgPack(source))

        assertEquals(source, decoded)
    }

    @Test
    fun msgPackDecodesIntegerWidthsAndShortStrings() {
        // fixint 42, uint8 200, int16 -300, uint64 2^40, fixstr "hi"
        val bytes = byteArrayOf(
            0x95.toByte(),
            0x2A,
            0xCC.toByte(), 0xC8.toByte(),
            0xD1.toByte(), 0xFE.toByte(), 0xD4.toByte(),
            0xCF.toByte(), 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
            0xA2.toByte(), 'h'.code.toByte(), 'i'.code.toByte()
        )

        val decoded = assertIs<JsonArray>(decodeMsgPack(bytes))

        assertEquals(42, decoded[0].jsonPrimitive.int)
        assertEquals(200, decoded[1].jsonPrimitive.int)
        assertEquals(-300, decoded[2].jsonPrimitive.int)
        assertEquals(1L shl 40, decoded[3].jsonPrimitive.long)
        assertEquals("hi", decoded[4].jsonPrimitive.content)
    }

    @Test
    fun msgPackDecodesNumericMapKeys() {
        // map {7: "chat"} — JS-style objects sometimes use numeric keys.
        val bytes = byteArrayOf(0x81.toByte(), 0x07, 0xA4.toByte(), 'c'.code.toByte(), 'h'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte())

        val decoded = assertIs<JsonObject>(decodeMsgPack(bytes))

        assertEquals("chat", decoded["7"]?.jsonPrimitive?.content)
    }

    @Test
    fun msgPackDecodesBigIntegerExtension() {
        // ext type 1 carrying a nested uint64 integer 2^53.
        val bytes = byteArrayOf(
            0xC7.toByte(), 0x09, 0x01,
            0xCF.toByte(), 0x00, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        val decoded = decodeMsgPack(bytes)

        assertEquals(1L shl 53, decoded.jsonPrimitive.long)
    }

    @Test
    fun msgPackDecodesBooleanAndNull() {
        assertEquals(JsonPrimitive(true), decodeMsgPack(byteArrayOf(0xC3.toByte())))
        assertEquals(JsonPrimitive(false), decodeMsgPack(byteArrayOf(0xC2.toByte())))
        assertEquals(JsonNull, decodeMsgPack(byteArrayOf(0xC0.toByte())))
    }

    @Test
    fun msgPackTreatsBinaryAsHex() {
        val decoded = decodeMsgPack(byteArrayOf(0xC4.toByte(), 0x02, 0xAB.toByte(), 0x01))

        assertEquals("ab01", decoded.jsonPrimitive.content)
    }

    @Test
    fun lz4DecodesLiteralOnlyBlock() {
        // token: 4 literals, no match.
        val block = byteArrayOf(0x40, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte())

        assertEquals("abcd", decompressLz4Block(block, 4).decodeToString())
    }

    @Test
    fun lz4DecodesOverlappingMatch() {
        // 1 literal "a", then a match at offset 1 with length 5 -> "aaaaaa".
        val block = byteArrayOf(0x11, 'a'.code.toByte(), 0x01, 0x00)

        assertEquals("aaaaaa", decompressLz4Block(block, 6).decodeToString())
    }

    @Test
    fun lz4StopsAtExpectedSize() {
        val block = byteArrayOf(0x30, 'x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte())

        assertEquals("xy", decompressLz4Block(block, 2).decodeToString())
    }

    @Test
    fun frameHeaderRoundTripsAndCarriesSeq() {
        val payload = buildJsonObject { put("phone", "+79297575154") }

        val frame = buildMaxFrame(cmd = 0, seq = 321, opcode = 17, payload = payload)

        assertEquals(10, frame[0].toInt())
        assertEquals(0, frame[1].toInt())
        assertEquals(321, (((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)))
        assertEquals(17, (((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)))
        assertEquals(0, frame[6].toInt())

        val parsed = parseMaxFrame(frame)

        assertEquals(0, parsed.cmd)
        assertEquals(321, parsed.seq)
        assertEquals(17, parsed.opcode)
        assertEquals(payload, parsed.payload)
    }

    @Test
    fun frameWithoutPayloadRoundTrips() {
        val parsed = parseMaxFrame(buildMaxFrame(cmd = 0, seq = 0, opcode = 1, payload = null))

        assertEquals(1, parsed.opcode)
        assertNull(parsed.payload)
    }

    @Test
    fun errorFrameKeepsNegativeSeq() {
        val frame = buildMaxFrame(cmd = 3, seq = -5, opcode = 17, payload = null)
        val parsed = parseMaxFrame(frame)

        assertEquals(-5, parsed.seq)
        assertEquals(3, parsed.cmd)
    }

    @Test
    fun frameWithCompressedPayloadIsDecoded() {
        val body = encodeMsgPack(buildJsonObject { put("error", "captcha.validation-failed") })
        val block = ByteArray(body.size + 1)
        block[0] = ((body.size shl 4) and 0xFF).toByte()
        body.copyInto(block, 1)

        // Literal-only block with an explicit length extension for sizes >= 15.
        val literalBlock = if (body.size < 15) {
            block
        } else {
            ByteArray(body.size + 2).also {
                it[0] = 0xF0.toByte()
                it[1] = (body.size - 15).toByte()
                body.copyInto(it, 2)
            }
        }

        val frame = ByteArray(10 + literalBlock.size)
        frame[0] = 10
        frame[1] = 3
        frame[4] = 0
        frame[5] = 17
        frame[6] = 1
        frame[7] = (literalBlock.size shr 16).toByte()
        frame[8] = (literalBlock.size shr 8).toByte()
        frame[9] = literalBlock.size.toByte()
        literalBlock.copyInto(frame, 10)

        val parsed = parseMaxFrame(frame)
        val payload = assertIs<JsonObject>(parsed.payload)

        assertEquals("captcha.validation-failed", payload["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodeDecodePreservesLargeIdAsNumber() {
        val payload = buildJsonObject { put("id", 7_000_000_000_000_000_000L) }

        val decoded = decodeMsgPack(encodeMsgPack(payload))

        assertEquals(payload, decoded)
        assertContentEquals(encodeMsgPack(payload), encodeMsgPack(decoded))
    }
}
