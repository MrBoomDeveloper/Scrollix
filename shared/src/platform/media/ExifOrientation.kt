package com.mrboomdev.scrollix.platform.media

/**
 * EXIF orientation of a JPEG, read from its leading bytes. The values follow
 * the EXIF spec: 1 is upright, 2..4 mirror, 5..8 rotate by a quarter turn.
 * Anything unreadable — no EXIF, a non-JPEG file, a cut-off header — is 1.
 */
fun jpegOrientation(bytes: ByteArray): Int {
    if (bytes.size < 4) return ExifOrientationUpright
    if (u(bytes, 0) != 0xFF || u(bytes, 1) != 0xD8) return ExifOrientationUpright

    var index = 2

    while (index + 4 <= bytes.size) {
        if (u(bytes, index) != 0xFF) return ExifOrientationUpright

        val marker = u(bytes, index + 1)

        // Fill bytes may precede the marker code.
        if (marker == 0xFF) {
            index++
            continue
        }

        // Standalone markers carry no length.
        if (marker == 0x01 || marker in 0xD0..0xD8) {
            index += 2
            continue
        }

        // Start of scan or end of image: the metadata is behind us.
        if (marker == 0xD9 || marker == 0xDA) return ExifOrientationUpright

        val length = (u(bytes, index + 2) shl 8) or u(bytes, index + 3)
        if (length < 2) return ExifOrientationUpright

        val payloadStart = index + 4
        val payloadEnd = index + 2 + length
        if (payloadEnd > bytes.size) return ExifOrientationUpright

        if (marker == 0xE1) {
            orientationFromApp1(bytes, payloadStart, payloadEnd)?.let { return it }
        }

        index = payloadEnd
    }

    return ExifOrientationUpright
}

/** How many quarter turns clockwise the orientation asks for. */
internal fun orientationQuarterTurns(orientation: Int): Int = when (orientation) {
    3, 4 -> 2
    5, 6 -> 1
    7, 8 -> 3
    else -> 0
}

/** True when the picture is mirrored on top of its rotation. */
internal fun orientationMirrors(orientation: Int): Boolean =
    orientation == 2 || orientation == 4 || orientation == 5 || orientation == 7

private fun orientationFromApp1(bytes: ByteArray, start: Int, end: Int): Int? {
    // "Exif\0\0" plus at least an 8-byte TIFF header and an empty IFD.
    if (end - start < 14) return null

    val exif = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
    for (offset in exif.indices) {
        if (u(bytes, start + offset) != (exif[offset].toInt() and 0xFF)) return null
    }

    val tiff = start + 6
    val littleEndian = when {
        u(bytes, tiff) == 0x49 && u(bytes, tiff + 1) == 0x49 -> true
        u(bytes, tiff) == 0x4D && u(bytes, tiff + 1) == 0x4D -> false
        else -> return null
    }

    if ((u16(bytes, tiff + 2, littleEndian) ?: return null) != 42) return null

    val ifdOffset = u32(bytes, tiff + 4, littleEndian) ?: return null
    if (ifdOffset > Int.MAX_VALUE) return null

    val ifd = tiff + ifdOffset.toInt()
    if (ifd < tiff || ifd + 2 > end) return null

    val count = u16(bytes, ifd, littleEndian) ?: return null

    for (entryIndex in 0 until count) {
        val entry = ifd + 2 + entryIndex * 12
        if (entry + 12 > end) return null

        if (u16(bytes, entry, littleEndian) != 0x0112) continue

        // Orientation is a SHORT; a LONG from a broken writer is rejected.
        val type = u16(bytes, entry + 2, littleEndian) ?: return null
        if (type != 3) return null

        return u16(bytes, entry + 8, littleEndian)?.takeIf { it in 1..8 }
    }

    return null
}

internal const val ExifOrientationUpright = 1

private fun u(bytes: ByteArray, index: Int): Int =
    if (index in bytes.indices) bytes[index].toInt() and 0xFF else 0

private fun u16(bytes: ByteArray, index: Int, littleEndian: Boolean): Int? {
    if (index < 0 || index + 1 >= bytes.size) return null

    val first = u(bytes, index)
    val second = u(bytes, index + 1)

    return if (littleEndian) first or (second shl 8) else (first shl 8) or second
}

private fun u32(bytes: ByteArray, index: Int, littleEndian: Boolean): Long? {
    if (index < 0 || index + 3 >= bytes.size) return null

    val a = u(bytes, index)
    val b = u(bytes, index + 1)
    val c = u(bytes, index + 2)
    val d = u(bytes, index + 3)

    return if (littleEndian) {
        (a.toLong() or (b.toLong() shl 8) or (c.toLong() shl 16) or (d.toLong() shl 24))
    } else {
        (a.toLong() shl 24) or (b.toLong() shl 16) or (c.toLong() shl 8) or d.toLong()
    }
}
