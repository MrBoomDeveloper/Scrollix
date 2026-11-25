package com.mrboomdev.scrollix.data.media

import com.mrboomdev.scrollix.features.home.key

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

data class WebmAnimation(
    val frames: List<ImageBitmap>,
    val frameRate: Float,
    val width: Int,
    val height: Int
)

private data class WebmAnimationKey(
    val customEmojiId: Long,
    val widthPx: Int,
    val heightPx: Int
)

private const val MaxWebmAnimations = 12
internal const val MaxAnimationFrames = 120
private const val MaxAnimationDimension = 320

private val webmAnimations = mutableMapOf<WebmAnimationKey, WebmAnimation>()
private val webmAnimationsMutex = Mutex()

internal fun animationFrameSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
    val width = widthPx.coerceAtLeast(1)
    val height = heightPx.coerceAtLeast(1)
    val largest = maxOf(width, height)

    if (largest <= MaxAnimationDimension) return width to height

    val scale = MaxAnimationDimension.toFloat() / largest

    return (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
}

internal suspend fun loadWebmAnimation(
    customEmojiId: Long,
    bytes: ByteArray,
    widthPx: Int,
    heightPx: Int
): WebmAnimation? {
    val (width, height) = animationFrameSize(widthPx, heightPx)
    val key = WebmAnimationKey(customEmojiId, width, height)

    webmAnimationsMutex.withLock {
        webmAnimations[key]
    }?.let { return it }

    val animation = decodeWebmAnimation(bytes, width, height) ?: return null

    return webmAnimationsMutex.withLock {
        val cached = webmAnimations[key]

        if (cached != null) {
            cached
        } else {
            webmAnimations[key] = animation

            while (webmAnimations.size > MaxWebmAnimations) {
                val oldest = webmAnimations.keys.firstOrNull() ?: break
                webmAnimations.remove(oldest)
            }

            animation
        }
    }
}

internal expect suspend fun decodeWebmAnimation(
    bytes: ByteArray,
    widthPx: Int,
    heightPx: Int
): WebmAnimation?
