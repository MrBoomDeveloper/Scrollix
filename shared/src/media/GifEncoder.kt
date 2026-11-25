package com.mrboomdev.scrollix.media

import com.mrboomdev.scrollix.features.home.key

/**
 * Minimal GIF89a encoder for prerendered sticker animations.
 *
 * MAX has no sticker format and displays animated images as photos, so an
 * animated sticker has to become a raster animation first. Neither platform
 * ships an animated-image encoder, hence this writer: one global 216-colour
 * cube palette, a single transparent index and "restore to background" frames,
 * which keeps sticker transparency and stops earlier frames from smearing.
 */
internal object GifEncoder {
    private const val TransparentIndex = 0
    private const val PaletteEntries = 256
    private const val AlphaThreshold = 128
    private const val MaxCodeSize = 12
    private const val MaxCode = 4096

    private val palette = ByteArray(PaletteEntries * 3).also { table ->
        // Slot 0 is the transparent colour; 1..216 are a 6x6x6 colour cube.
        for (red in 0..5) {
            for (green in 0..5) {
                for (blue in 0..5) {
                    val index = 1 + red * 36 + green * 6 + blue
                    table[index * 3] = (red * 51).toByte()
                    table[index * 3 + 1] = (green * 51).toByte()
                    table[index * 3 + 2] = (blue * 51).toByte()
                }
            }
        }
    }

    /**
     * @param frames ARGB pixels, row by row, each `width * height` long.
     * @param delayCentis how long every frame is shown, in 1/100 seconds.
     */
    fun encode(
        frames: List<IntArray>,
        width: Int,
        height: Int,
        delayCentis: Int
    ): ByteArray {
        require(width > 0 && height > 0) { "GIF size must be positive" }
        require(frames.isNotEmpty()) { "GIF needs at least one frame" }
        require(frames.all { frame -> frame.size == width * height }) {
            "Every GIF frame must match its size"
        }

        val out = Sink()

        out.ascii("GIF89a")
        out.short(width)
        out.short(height)
        // Global palette, 8-bit colour resolution, 256 entries.
        out.byte(0xF7)
        out.byte(0)
        out.byte(0)
        out.bytes(palette, palette.size)

        // NETSCAPE2.0 application extension: loop forever.
        out.byte(0x21)
        out.byte(0xFF)
        out.byte(11)
        out.ascii("NETSCAPE2.0")
        out.byte(3)
        out.byte(1)
        out.short(0)
        out.byte(0)

        for (frame in frames) {
            val indices = IntArray(frame.size) { position -> quantize(frame[position]) }

            // Graphic control: disposal 2 (restore to background) so the
            // transparent areas of the previous frame do not linger.
            out.byte(0x21)
            out.byte(0xF9)
            out.byte(4)
            out.byte(0x09)
            out.short(delayCentis.coerceIn(0, 0xFFFF))
            out.byte(TransparentIndex)
            out.byte(0)

            out.byte(0x2C)
            out.short(0)
            out.short(0)
            out.short(width)
            out.short(height)
            out.byte(0)

            out.byte(8)
            // writeLzw terminates the image data with its own zero block.
            writeLzw(indices, out)
        }

        out.byte(0x3B)
        return out.toByteArray()
    }

    private fun quantize(argb: Int): Int {
        val alpha = (argb ushr 24) and 0xFF
        if (alpha < AlphaThreshold) return TransparentIndex

        val red = ((argb ushr 16 and 0xFF) * 5 + 127) / 255
        val green = ((argb ushr 8 and 0xFF) * 5 + 127) / 255
        val blue = ((argb and 0xFF) * 5 + 127) / 255

        return 1 + red * 36 + green * 6 + blue
    }

    private fun writeLzw(indices: IntArray, out: Sink) {
        val clearCode = 1 shl 8
        val endCode = clearCode + 1

        val writer = CodeWriter(out)
        val dictionary = HashMap<Int, Int>()
        var nextCode = endCode + 1
        var codeSize = 9

        writer.write(clearCode, codeSize)
        var prefix = indices[0]

        for (position in 1 until indices.size) {
            val symbol = indices[position]
            val key = (prefix shl 8) or symbol
            val existing = dictionary[key]

            if (existing != null) {
                prefix = existing
                continue
            }

            writer.write(prefix, codeSize)

            if (nextCode < MaxCode) {
                dictionary[key] = nextCode
                nextCode++

                if (nextCode == (1 shl codeSize) && codeSize < MaxCodeSize) {
                    codeSize++
                }
            } else {
                writer.write(clearCode, codeSize)
                dictionary.clear()
                nextCode = endCode + 1
                codeSize = 9
            }

            prefix = symbol
        }

        writer.write(prefix, codeSize)
        writer.write(endCode, codeSize)
        writer.flush()
    }

    /** Packs codes LSB-first and writes them as GIF data sub-blocks. */
    private class CodeWriter(private val out: Sink) {
        private val block = ByteArray(255)
        private var blockSize = 0
        private var bitBuffer = 0
        private var bitCount = 0

        fun write(code: Int, size: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += size

            while (bitCount >= 8) {
                writeByte(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        fun flush() {
            if (bitCount > 0) {
                writeByte(bitBuffer and 0xFF)
                bitBuffer = 0
                bitCount = 0
            }

            flushBlock()
            out.byte(0)
        }

        private fun writeByte(value: Int) {
            block[blockSize++] = value.toByte()

            if (blockSize == block.size) flushBlock()
        }

        private fun flushBlock() {
            if (blockSize == 0) return

            out.byte(blockSize)
            out.bytes(block, blockSize)
            blockSize = 0
        }
    }

    private class Sink {
        private var buffer = ByteArray(4096)
        private var size = 0

        fun byte(value: Int) {
            ensure(1)
            buffer[size++] = value.toByte()
        }

        fun short(value: Int) {
            byte(value and 0xFF)
            byte((value ushr 8) and 0xFF)
        }

        fun ascii(value: String) {
            for (character in value) byte(character.code)
        }

        fun bytes(values: ByteArray, count: Int = values.size) {
            ensure(count)
            values.copyInto(buffer, size, 0, count)
            size += count
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)

        private fun ensure(count: Int) {
            if (size + count <= buffer.size) return

            buffer = buffer.copyOf((size + count).coerceAtLeast(buffer.size * 2))
        }
    }
}
