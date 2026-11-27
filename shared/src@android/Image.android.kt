package com.mrboomdev.scrollix.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The largest side a decoded image may keep. A camera photo is 12+ MP; decoding
 * it at full size needs ~48 MB and fails with an OOM on a phone, which left
 * attachment previews and chat thumbnails blank. A phone screen never shows
 * more than this, so the bitmap is sampled down at decode time.
 */
private const val MaxDecodeDimension = 2048

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null

    return runCatching {
        // The bounds pass reads only the header, so the sample size is known
        // before any pixels are allocated.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }.getOrNull()
}

/**
 * Power-of-two downsample factor that keeps both sides within
 * [MaxDecodeDimension]. 1 means the image is decoded as is.
 */
internal fun sampleSizeFor(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1

    val largest = maxOf(width, height)
    if (largest <= MaxDecodeDimension) return 1

    var sample = 1

    while (largest / (sample * 2) >= MaxDecodeDimension) {
        sample *= 2
    }

    return sample
}
