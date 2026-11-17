package com.mrboomdev.scrollix.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WebmAnimation(
    val frames: List<ImageBitmap>,
    val frameRate: Float
)

private data class WebmAnimationKey(
    val customEmojiId: Long,
    val sizePx: Int
)

private const val MaxWebmAnimations = 12

private val webmAnimations = mutableMapOf<WebmAnimationKey, WebmAnimation>()
private val webmAnimationsMutex = Mutex()

internal suspend fun loadWebmAnimation(
    customEmojiId: Long,
    bytes: ByteArray,
    sizePx: Int
): WebmAnimation? {
    val key = WebmAnimationKey(customEmojiId, sizePx)

    webmAnimationsMutex.withLock {
        webmAnimations[key]
    }?.let { return it }

    val animation = decodeWebmAnimation(bytes, sizePx) ?: return null

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
    sizePx: Int
): WebmAnimation?
