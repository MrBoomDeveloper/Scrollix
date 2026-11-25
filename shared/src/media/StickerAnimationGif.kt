package com.mrboomdev.scrollix.media

import com.mrboomdev.scrollix.data.media.gunzip
import com.mrboomdev.scrollix.data.model.MessageStickerFormat

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.mrboomdev.scrollix.data.media.decodeWebmAnimation

/** A rasterized animation: frames plus how long each one is shown. */
internal data class StickerFrames(
    val frames: List<ImageBitmap>,
    val delayCentis: Int
)

private const val MaxGifDimension = 256
private const val MaxGifFrames = 40

/**
 * Prerenders an animated sticker (Telegram TGS or WebM) into a transparent
 * animated GIF.
 *
 * MAX neither accepts Telegram stickers nor encodes animated images, so the
 * frames are rasterized here and packed by [GifEncoder]. Returns null when the
 * sticker cannot be decoded or is static.
 */
suspend fun renderStickerAnimationGif(
    bytes: ByteArray,
    format: MessageStickerFormat,
    width: Int,
    height: Int
): ByteArray? = withContext(Dispatchers.Default) {
    if (bytes.isEmpty() || format == MessageStickerFormat.Webp) return@withContext null

    val (frameWidth, frameHeight) = gifFrameSize(width, height)

    val rendered = when (format) {
        MessageStickerFormat.Tgs -> {
            val json = gunzip(bytes)?.decodeToString() ?: return@withContext null
            renderLottieFrames(json, frameWidth, frameHeight, MaxGifFrames)
        }

        MessageStickerFormat.Webm -> webmFrames(bytes, frameWidth, frameHeight)
        MessageStickerFormat.Webp -> null
    } ?: return@withContext null

    if (rendered.frames.isEmpty()) return@withContext null

    val pixels = rendered.frames.map(ImageBitmap::toArgbPixels)

    GifEncoder.encode(
        frames = pixels,
        width = frameWidth,
        height = frameHeight,
        delayCentis = rendered.delayCentis.coerceIn(2, 100)
    )
}

/** Rasterizes a Lottie (TGS) animation into at most [maxFrames] frames. */
internal expect fun renderLottieFrames(
    json: String,
    width: Int,
    height: Int,
    maxFrames: Int
): StickerFrames?

private suspend fun webmFrames(
    bytes: ByteArray,
    width: Int,
    height: Int
): StickerFrames? {
    val animation = decodeWebmAnimation(bytes, width, height) ?: return null
    if (animation.frames.isEmpty()) return null

    val stride = (animation.frames.size + MaxGifFrames - 1) / MaxGifFrames
    val frames = animation.frames.filterIndexed { index, _ -> index % stride == 0 }
    val fps = animation.frameRate.takeIf { it > 0f } ?: 30f
    val delay = (100f * stride / fps).roundToInt()

    return StickerFrames(frames, delay.coerceAtLeast(2))
}

/** Keeps the sticker's aspect ratio while bounding the GIF size. */
private fun gifFrameSize(width: Int, height: Int): Pair<Int, Int> {
    val safeWidth = width.coerceAtLeast(0)
    val safeHeight = height.coerceAtLeast(0)

    if (safeWidth == 0 || safeHeight == 0) {
        return MaxGifDimension to MaxGifDimension
    }

    val largest = maxOf(safeWidth, safeHeight)
    if (largest <= MaxGifDimension) return safeWidth to safeHeight

    val scale = MaxGifDimension.toFloat() / largest

    return (safeWidth * scale).roundToInt().coerceAtLeast(1) to
        (safeHeight * scale).roundToInt().coerceAtLeast(1)
}

private fun ImageBitmap.toArgbPixels(): IntArray {
    val pixels = toPixelMap()
    val argb = IntArray(width * height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            argb[y * width + x] = pixels[x, y].toArgb()
        }
    }

    return argb
}
