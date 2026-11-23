package com.mrboomdev.scrollix.media

import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The encoder is hand written, so a JDK decoder reads its output back: this
 * catches structural mistakes that a self-written reader would repeat.
 */
class GifEncoderJvmTest {

    @Test
    fun encodedGifIsReadableByTheJdkDecoder() {
        val frame = intArrayOf(
            0xFFFF0000.toInt(),
            0x00000000,
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt()
        )

        val bytes = GifEncoder.encode(listOf(frame, frame), width = 2, height = 2, delayCentis = 6)
        val file = Files.createTempFile("scrollix-sticker", ".gif").toFile()

        try {
            file.writeBytes(bytes)

            val input = assertNotNull(ImageIO.createImageInputStream(file))
            val readers = ImageIO.getImageReadersByFormatName("gif")
            check(readers.hasNext()) { "The JDK has no GIF decoder" }
            val reader = readers.next()

            try {
                reader.input = input

                assertEquals(2, reader.getNumImages(true))

                val image = reader.read(0)
                assertEquals(2, image.width)
                assertEquals(2, image.height)

                assertEquals(0xFFFF0000.toInt(), image.getRGB(0, 0))
                assertEquals(0, image.getRGB(1, 0) ushr 24)
                assertEquals(0xFF00FF00.toInt(), image.getRGB(0, 1))
                assertEquals(0xFF0000FF.toInt(), image.getRGB(1, 1))
            } finally {
                reader.dispose()
                input.close()
            }
        } finally {
            file.delete()
        }
    }
}
