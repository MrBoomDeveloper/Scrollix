package com.mrboomdev.scrollix.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.skottie.Animation
import kotlin.math.roundToInt

/**
 * Desktop Compose already ships skia, whose skottie module renders Lottie
 * (TGS) animations headlessly. Frames are sampled instead of rendered one by
 * one: a 60 fps sticker would otherwise produce hundreds of GIF frames.
 */
internal actual fun renderLottieFrames(
    json: String,
    width: Int,
    height: Int,
    maxFrames: Int
): StickerFrames? {
    val animation = runCatching { Animation.makeFromString(json) }.getOrNull()
        ?: return null

    return try {
        val durationMillis = animation.duration * 1000f
        val totalFrames = animation.outPoint.roundToInt().coerceAtLeast(1)
        val count = totalFrames.coerceIn(1, maxFrames)

        val surface = Surface.makeRasterN32Premul(width, height)

        try {
            val frames = ArrayList<ImageBitmap>(count)

            for (index in 0 until count) {
                animation.seekFrame(totalFrames * index / count.toFloat())
                surface.canvas.clear(0x00000000)
                animation.render(
                    surface.canvas,
                    Rect.makeWH(width.toFloat(), height.toFloat())
                )
                frames += surface.makeImageSnapshot().toComposeImageBitmap()
            }

            val delay = (durationMillis / count / 10f).roundToInt()
            StickerFrames(frames, delay.coerceAtLeast(2))
        } finally {
            surface.close()
        }
    } finally {
        animation.close()
    }
}
