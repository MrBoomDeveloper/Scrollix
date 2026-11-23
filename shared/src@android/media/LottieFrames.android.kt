package com.mrboomdev.scrollix.media

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import kotlin.math.roundToInt

/**
 * Android has no headless Lottie renderer in Compose, so TGS frames are drawn
 * with the Lottie library into software bitmaps.
 */
internal actual fun renderLottieFrames(
    json: String,
    width: Int,
    height: Int,
    maxFrames: Int
): StickerFrames? {
    val composition = runCatching {
        LottieCompositionFactory.fromJsonStringSync(json, null).value
    }.getOrNull() ?: return null

    val totalFrames = composition.durationFrames.toInt().takeIf { it > 0 } ?: 1
    val count = totalFrames.coerceIn(1, maxFrames)

    val drawable = LottieDrawable().apply {
        this.composition = composition
        setBounds(0, 0, width, height)
    }

    val frames = ArrayList<ImageBitmap>(count)

    for (index in 0 until count) {
        // A looping sticker's last frame repeats the first one, so the
        // sampled progress stops just before 1.
        drawable.setProgress(index / count.toFloat())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))
        frames += bitmap.asImageBitmap()
    }

    val delay = (composition.duration.toFloat() / count / 10f).roundToInt()
    return StickerFrames(frames, delay.coerceAtLeast(2))
}
