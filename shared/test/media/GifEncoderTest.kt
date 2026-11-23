package com.mrboomdev.scrollix.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GifEncoderTest {

    @Test
    fun writesLoopingAnimatedGif() {
        val frame = intArrayOf(
            0xFFFF0000.toInt(),
            0x00000000,
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt()
        )

        val bytes = GifEncoder.encode(listOf(frame, frame), width = 2, height = 2, delayCentis = 7)

        assertEquals("GIF89a", bytes.decodeToString(0, 6))
        assertEquals(2, bytes.u16(6))
        assertEquals(2, bytes.u16(8))
        assertEquals(0xF7, bytes[10].toInt() and 0xFF)
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
        assertTrue(bytes.containsAscii("NETSCAPE2.0"))

        val frames = parseFrames(bytes)
        assertEquals(2, frames.size)

        frames.forEach { parsed ->
            assertEquals(2, parsed.width)
            assertEquals(2, parsed.height)
            assertEquals(7, parsed.delay)
            assertTrue(parsed.transparent)
            assertEquals(0, parsed.transparentIndex)
            assertEquals(8, parsed.minCodeSize)
        }
    }

    @Test
    fun quantizesColoursAndDropsTransparentPixels() {
        val frame = intArrayOf(
            0xFFFF0000.toInt(),
            0x00000000,
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt()
        )

        val bytes = GifEncoder.encode(listOf(frame), width = 2, height = 2, delayCentis = 5)
        val parsed = parseFrames(bytes).single()

        // Slot 0 is transparent; 1..216 are a 6x6x6 cube of 0/51/.../255.
        val expected = listOf(
            1 + 5 * 36,     // pure red
            0,              // transparent
            1 + 5 * 6,      // pure green
            1 + 5           // pure blue
        )

        assertContentEquals(expected, decodeIndices(parsed).take(4))

        val palette = parsed.palette
        assertEquals(0xFF, palette[(1 + 5 * 36) * 3].toInt() and 0xFF)
        assertEquals(0, palette[(1 + 5 * 36) * 3 + 1].toInt() and 0xFF)
        assertEquals(0xFF, palette[(1 + 5 * 6) * 3 + 1].toInt() and 0xFF)
        assertEquals(0xFF, palette[(1 + 5) * 3 + 2].toInt() and 0xFF)
    }

    private data class ParsedFrame(
        val width: Int,
        val height: Int,
        val delay: Int,
        val transparent: Boolean,
        val transparentIndex: Int,
        val minCodeSize: Int,
        val palette: ByteArray,
        val data: ByteArray
    )

    private fun parseFrames(bytes: ByteArray): List<ParsedFrame> {
        val width = bytes.u16(6)
        val height = bytes.u16(8)
        val packed = bytes[10].toInt() and 0xFF

        var position = 13
        // The size bits store N where the table has 2^(N+1) entries.
        val paletteSize = if (packed and 0x80 != 0) {
            3 shl ((packed and 0x07) + 1)
        } else {
            0
        }
        val palette = bytes.copyOfRange(position, position + paletteSize)
        position += paletteSize

        val frames = mutableListOf<ParsedFrame>()
        var delay = 0
        var transparent = false
        var transparentIndex = 0

        while (position < bytes.size) {
            when (val marker = bytes[position].toInt() and 0xFF) {
                0x3B -> return frames

                0x21 -> {
                    val label = bytes[position + 1].toInt() and 0xFF
                    position += 2

                    if (label == 0xF9) {
                        val blockSize = bytes[position].toInt() and 0xFF
                        val flags = bytes[position + 1].toInt() and 0xFF
                        delay = bytes.u16(position + 2)
                        transparentIndex = bytes[position + 4].toInt() and 0xFF
                        transparent = flags and 0x01 != 0
                        position += blockSize + 1
                    }

                    position = skipBlocks(bytes, position)
                }

                0x2C -> {
                    val left = bytes.u16(position + 1)
                    val top = bytes.u16(position + 3)
                    val frameWidth = bytes.u16(position + 5)
                    val frameHeight = bytes.u16(position + 7)
                    val framePacked = bytes[position + 9].toInt() and 0xFF
                    position += 10

                    if (framePacked and 0x80 != 0) {
                        position += 3 shl ((framePacked and 0x07) + 1)
                    }

                    val minCodeSize = bytes[position].toInt() and 0xFF
                    position++

                    val start = position
                    val data = mutableListOf<Byte>()
                    while (true) {
                        val blockSize = bytes[position].toInt() and 0xFF
                        position++

                        if (blockSize == 0) break

                        for (offset in 0 until blockSize) data += bytes[position + offset]
                        position += blockSize
                    }

                    // The image data is a run of sub-blocks between start and
                    // the terminator; the frame size comes from the descriptor.
                    frames += ParsedFrame(
                        width = frameWidth,
                        height = frameHeight,
                        delay = delay,
                        transparent = transparent,
                        transparentIndex = transparentIndex,
                        minCodeSize = minCodeSize,
                        palette = palette,
                        data = data.toByteArray()
                    )

                    check(start < position)
                }

                else -> error("Unexpected GIF marker 0x${marker.toString(16)}")
            }
        }

        error("GIF has no trailer (size ${width}x$height)")
    }

    private fun skipBlocks(bytes: ByteArray, from: Int): Int {
        var position = from

        while (true) {
            val size = bytes[position].toInt() and 0xFF
            position++

            if (size == 0) return position

            position += size
        }
    }

    private fun decodeIndices(frame: ParsedFrame): List<Int> {
        val clear = 1 shl frame.minCodeSize
        val end = clear + 1
        var codeSize = frame.minCodeSize + 1

        var table = mutableListOf<IntArray>()
        fun resetTable() {
            table = MutableList(clear) { value -> intArrayOf(value) }
            table.add(IntArray(0))
            table.add(IntArray(0))
            codeSize = frame.minCodeSize + 1
        }

        resetTable()

        var previous: IntArray? = null
        val output = mutableListOf<Int>()
        val reader = BitReader(frame.data)

        while (true) {
            val code = reader.read(codeSize) ?: break

            when (code) {
                clear -> {
                    resetTable()
                    previous = null
                }

                end -> break

                else -> {
                    val entry = if (code < table.size) {
                        table[code]
                    } else {
                        val last = previous ?: error("Invalid GIF code $code")
                        last + last[0]
                    }

                    output += entry.toList()

                    previous?.let { last ->
                        if (table.size < 4096) {
                            table.add(last + entry[0])

                            if (table.size == (1 shl codeSize) && codeSize < 12) {
                                codeSize++
                            }
                        }
                    }

                    previous = entry
                }
            }
        }

        return output
    }

    private class BitReader(private val data: ByteArray) {
        private var bit = 0

        fun read(size: Int): Int? {
            if (bit + size > data.size * 8) return null

            var value = 0
            for (offset in 0 until size) {
                val position = bit + offset
                val current = data[position / 8].toInt() and 0xFF
                value = value or (((current shr (position % 8)) and 1) shl offset)
            }

            bit += size
            return value
        }
    }

    private fun ByteArray.u16(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        val target = value.encodeToByteArray()

        for (start in 0..(size - target.size)) {
            if (target.indices.all { index -> this[start + index] == target[index] }) return true
        }

        return false
    }
}
