package com.mrboomdev.scrollix.platform.media

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExifOrientationTest {

    @Test
    fun `a little endian orientation is read`() {
        assertEquals(6, jpegOrientation(jpegWithOrientation(orientation = 6, littleEndian = true)))
    }

    @Test
    fun `a big endian orientation is read`() {
        assertEquals(8, jpegOrientation(jpegWithOrientation(orientation = 8, littleEndian = false)))
    }

    @Test
    fun `a JPEG without EXIF is upright`() {
        assertEquals(1, jpegOrientation(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())))
    }

    @Test
    fun `a non-JPEG file is upright`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        assertEquals(1, jpegOrientation(png))
    }

    @Test
    fun `a cut-off header is upright instead of throwing`() {
        val full = jpegWithOrientation(orientation = 3)
        // The APP1 segment ends right before the two EOI bytes; every shorter
        // prefix cuts the payload, so there is nothing trustworthy to read.
        val segmentEnd = full.size - 2

        for (length in 0 until segmentEnd) {
            assertEquals(1, jpegOrientation(full.copyOf(length)))
        }
    }

    @Test
    fun `an orientation outside the spec is ignored`() {
        assertEquals(1, jpegOrientation(jpegWithOrientation(orientation = 9)))
    }

    @Test
    fun `orientation values map onto quarter turns and mirrors`() {
        assertEquals(0, orientationQuarterTurns(1))
        assertEquals(2, orientationQuarterTurns(3))
        assertEquals(1, orientationQuarterTurns(6))
        assertEquals(3, orientationQuarterTurns(8))

        assertTrue(orientationMirrors(2))
        assertTrue(orientationMirrors(5))
        assertFalse(orientationMirrors(6))
        assertFalse(orientationMirrors(1))
    }
}

/** Builds a minimal JPEG: SOI, one EXIF APP1 segment, EOI. */
private fun jpegWithOrientation(orientation: Int, littleEndian: Boolean = true): ByteArray {
    val tiff = Writer()
        .raw(if (littleEndian) byteArrayOf(0x49, 0x49) else byteArrayOf(0x4D, 0x4D))
        .short(42, littleEndian)
        .int(8, littleEndian)
        .short(1, littleEndian)          // one entry
        .short(0x0112, littleEndian)     // orientation tag
        .short(3, littleEndian)          // SHORT
        .int(1, littleEndian)            // one value
        .short(orientation, littleEndian)
        .short(0, littleEndian)          // the value field is four bytes wide
        .int(0, littleEndian)            // no next IFD
        .build()

    val payload = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) + tiff

    return Writer()
        .byte(0xFF, 0xD8)
        .byte(0xFF, 0xE1)
        .short(payload.size + 2, littleEndian = false)
        .raw(payload)
        .byte(0xFF, 0xD9)
        .build()
}

private class Writer {
    private val out = ByteArrayOutputStream()

    fun byte(vararg values: Int) = apply {
        values.forEach { value -> out.write(value and 0xFF) }
    }

    fun short(value: Int, littleEndian: Boolean) = apply {
        if (littleEndian) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        } else {
            out.write((value shr 8) and 0xFF)
            out.write(value and 0xFF)
        }
    }

    fun int(value: Int, littleEndian: Boolean) = apply {
        if (littleEndian) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        } else {
            out.write((value shr 24) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write(value and 0xFF)
        }
    }

    fun raw(data: ByteArray) = apply { out.write(data) }

    fun build(): ByteArray = out.toByteArray()
}
