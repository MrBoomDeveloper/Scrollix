package com.mrboomdev.scrollix.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

internal actual suspend fun decodeWebmAnimation(
    bytes: ByteArray,
    widthPx: Int,
    heightPx: Int
): WebmAnimation? {
    val (width, height) = animationFrameSize(widthPx, heightPx)

    return withContext(Dispatchers.IO) {
        decodeWebmAnimationBlocking(
            bytes = bytes,
            widthPx = width,
            heightPx = height
        )
    }
}

private suspend fun decodeWebmAnimationBlocking(
    bytes: ByteArray,
    widthPx: Int,
    heightPx: Int
): WebmAnimation? {
    if (bytes.isEmpty()) return null

    silenceFfmpegLogs()

    var grabber: FFmpegFrameGrabber? = null

    return try {
        val currentGrabber = FFmpegFrameGrabber(ByteArrayInputStream(bytes))
        grabber = currentGrabber
        currentGrabber.videoCodecName = when {
            bytes.containsAscii("V_VP8") -> "libvpx"
            bytes.containsAscii("V_VP9") -> "libvpx-vp9"
            else -> null
        }
        currentGrabber.pixelFormat = AV_PIX_FMT_RGBA
        currentGrabber.imageWidth = widthPx
        currentGrabber.imageHeight = heightPx
        currentGrabber.start()

        val frameRate = currentGrabber.frameRate
            .toFloat()
            .takeIf { it.isFinite() && it > 0f }
            ?: 30f
        val frames = mutableListOf<ImageBitmap>()
        val rowBytes = widthPx * 4

        while (frames.size < MaxAnimationFrames) {
            currentCoroutineContext().ensureActive()

            val frame = currentGrabber.grabImage() ?: break
            val buffer = frame.image?.getOrNull(0) as? ByteBuffer ?: break
            val stride = frame.imageStride.takeIf { it >= rowBytes } ?: rowBytes
            val pixels = ByteArray(widthPx * heightPx * 4)

            for (y in 0 until heightPx) {
                val source = buffer.duplicate()
                source.position(y * stride)
                source.get(pixels, y * rowBytes, rowBytes)
            }

            frames += pixels.premultiply().toImageBitmap(widthPx, heightPx, rowBytes)
        }

        if (frames.isEmpty()) {
            null
        } else {
            WebmAnimation(
                frames = frames,
                frameRate = frameRate,
                width = widthPx,
                height = heightPx
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Failed to decode animation: ${exception.message}")
        null
    } catch (exception: LinkageError) {
        println("Failed to load animation decoder: ${exception.message}")
        null
    } finally {
        runCatching { grabber?.close() }
    }
}

private fun ByteArray.premultiply(): ByteArray {
    for (index in indices step 4) {
        val alpha = this[index + 3].toInt() and 0xFF

        if (alpha == 255) continue

        this[index] = ((this[index].toInt() and 0xFF) * alpha / 255).toByte()
        this[index + 1] = ((this[index + 1].toInt() and 0xFF) * alpha / 255).toByte()
        this[index + 2] = ((this[index + 2].toInt() and 0xFF) * alpha / 255).toByte()
    }

    return this
}

private fun ByteArray.toImageBitmap(
    width: Int,
    height: Int,
    rowBytes: Int
): ImageBitmap {
    val imageInfo = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.RGBA_8888,
        alphaType = ColorAlphaType.PREMUL
    )

    return Image.makeRaster(imageInfo, this, rowBytes).toComposeImageBitmap()
}

private fun ByteArray.containsAscii(value: String): Boolean {
    val target = value.encodeToByteArray()
    if (target.isEmpty() || target.size > size) return false

    for (start in 0..size - target.size) {
        var matches = true

        for (index in target.indices) {
            if (this[start + index] != target[index]) {
                matches = false
                break
            }
        }

        if (matches) return true
    }

    return false
}
