package com.mrboomdev.scrollix.data.media

import com.mrboomdev.scrollix.data.media.MaxAnimationFrames
import com.mrboomdev.scrollix.data.media.WebmAnimation
import com.mrboomdev.scrollix.data.media.animationFrameSize
import com.mrboomdev.scrollix.data.storage.AndroidAppContext

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.mediacache.alphavideo.WebMAlphaPlayerNative
import dev.mediacache.alphavideo.WebMFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

private const val DefaultFrameRate = 12f

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
        ) ?: decodeWithMediaMetadataRetriever(
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

    var playerId = 0L

    return try {
        playerId = WebMAlphaPlayerNative.nativeCreate(
            1,
            16,
            false,
            false,
            1.0,
            "preload",
            0
        )

        if (playerId == 0L) return null
        if (!WebMAlphaPlayerNative.nativeLoadFromBytes(playerId, bytes)) return null

        val totalFrames = WebMAlphaPlayerNative.nativeGetTotalFrames(playerId)
        if (totalFrames <= 0) return null

        val frameRate = WebMAlphaPlayerNative.nativeGetFrameRate(playerId)
            .toFloat()
            .takeIf { it.isFinite() && it > 0f }
            ?: 30f

        val matrix = WebMAlphaPlayerNative.nativeGetYuvToRgb(playerId)
            ?.takeIf { it.size >= 16 }
            ?: DefaultYuvToRgb

        val frames = ArrayList<ImageBitmap>(totalFrames.coerceAtMost(MaxAnimationFrames))

        for (index in 0 until totalFrames.coerceAtMost(MaxAnimationFrames)) {
            currentCoroutineContext().ensureActive()

            val frame = WebMAlphaPlayerNative.nativeGetFrame(playerId, index)
                ?: continue
            frames += frame.toImageBitmap(widthPx, heightPx, matrix)
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
        if (playerId != 0L) {
            runCatching { WebMAlphaPlayerNative.nativeDestroy(playerId) }
        }
    }
}

private fun decodeWithMediaMetadataRetriever(
    bytes: ByteArray,
    widthPx: Int,
    heightPx: Int
): WebmAnimation? {
    if (bytes.isEmpty()) return null

    val context = AndroidAppContext.context
    val file = runCatching {
        File.createTempFile("animation", ".bin", context.cacheDir)
    }.getOrNull() ?: return null

    val retriever = MediaMetadataRetriever()

    return try {
        file.writeBytes(bytes)
        retriever.setDataSource(file.absolutePath)

        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L

        val frameRate = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
        )?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: DefaultFrameRate

        val frameCount = if (durationMs > 0L) {
            (durationMs * frameRate / 1000f)
                .toInt()
                .coerceIn(1, MaxAnimationFrames)
        } else {
            1
        }

        val frames = ArrayList<ImageBitmap>(frameCount)

        for (index in 0 until frameCount) {
            val timeUs = if (durationMs > 0L) {
                index * durationMs * 1000L / frameCount
            } else {
                0L
            }

            val frame = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: continue

            frames += Bitmap.createScaledBitmap(
                frame,
                widthPx,
                heightPx,
                true
            ).asImageBitmap()
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
    } catch (exception: Exception) {
        println("Failed to extract animation frames: ${exception.message}")
        null
    } finally {
        runCatching { retriever.release() }
        runCatching { file.delete() }
    }
}

private fun WebMFrame.toImageBitmap(
    widthPx: Int,
    heightPx: Int,
    matrix: FloatArray
): ImageBitmap {
    val sourceWidth = this.width
    val sourceHeight = this.height
    val yPlane = this.yPlane
    val uPlane = this.uPlane
    val vPlane = this.vPlane
    val alphaPlane: ByteBuffer? = this.aPlane
    val uvWidth = (sourceWidth + 1) / 2
    val uvHeight = (sourceHeight + 1) / 2
    val pixels = IntArray(widthPx * heightPx)

    for (y in 0 until heightPx) {
        val sourceY = (y * sourceHeight / heightPx).coerceIn(0, sourceHeight - 1)
        val chromaY = (sourceY / 2).coerceIn(0, uvHeight - 1)

        for (x in 0 until widthPx) {
            val sourceX = (x * sourceWidth / widthPx).coerceIn(0, sourceWidth - 1)
            val chromaX = (sourceX / 2).coerceIn(0, uvWidth - 1)
            val lumaIndex = sourceY * sourceWidth + sourceX
            val chromaIndex = chromaY * uvWidth + chromaX
            val luma = (yPlane.get(lumaIndex).toInt() and 0xFF) / 255f
            val u = (uPlane.get(chromaIndex).toInt() and 0xFF) / 255f
            val v = (vPlane.get(chromaIndex).toInt() and 0xFF) / 255f
            val alpha = alphaPlane
                ?.let { (it.get(lumaIndex).toInt() and 0xFF) }
                ?: 255

            val red = (
                matrix[0] * luma +
                    matrix[4] * u +
                    matrix[8] * v +
                    matrix[12]
            ).coerceIn(0f, 1f)
            val green = (
                matrix[1] * luma +
                    matrix[5] * u +
                    matrix[9] * v +
                    matrix[13]
            ).coerceIn(0f, 1f)
            val blue = (
                matrix[2] * luma +
                    matrix[6] * u +
                    matrix[10] * v +
                    matrix[14]
            ).coerceIn(0f, 1f)

            pixels[y * widthPx + x] =
                (alpha shl 24) or
                    ((red * 255f).toInt() shl 16) or
                    ((green * 255f).toInt() shl 8) or
                    (blue * 255f).toInt()
        }
    }

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
    return bitmap.asImageBitmap()
}

private val DefaultYuvToRgb: FloatArray = run {
    val kr = 0.299
    val kb = 0.114
    val kg = 1.0 - kr - kb
    val rv = 2 * (1 - kr)
    val bu = 2 * (1 - kb)
    val gu = 2 * kb * (1 - kb) / kg
    val gv = 2 * kr * (1 - kr) / kg
    val ys = 255.0 / 219.0
    val yo = 16.0 / 255.0
    val cs = 255.0 / 224.0
    val co = 128.0 / 255.0

    FloatArray(16).apply {
        this[0] = ys.toFloat()
        this[1] = ys.toFloat()
        this[2] = ys.toFloat()
        this[4] = 0f
        this[5] = (-gu * cs).toFloat()
        this[6] = (bu * cs).toFloat()
        this[8] = (rv * cs).toFloat()
        this[9] = (-gv * cs).toFloat()
        this[10] = 0f
        this[12] = (-ys * yo - rv * cs * co).toFloat()
        this[13] = (-ys * yo + (gu + gv) * cs * co).toFloat()
        this[14] = (-ys * yo - bu * cs * co).toFloat()
        this[15] = 1f
    }
}
