package com.mrboomdev.scrollix.features.dialog

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

private val LoadingBlurStyle = HazeBlurStyle {
    blurEnabled(true)
    blurRadius(18.dp)
    noiseFactor(0f)
}

/**
 * Blurs a low-resolution poster while the real image or animation downloads.
 * The effect is applied to the image's own content, so it does not need a
 * separate Haze source elsewhere in the tree.
 */
internal fun Modifier.loadingBlur(active: Boolean): Modifier {
    return if (active) {
        hazeBlur(
            input = HazeInput.Content,
            style = LoadingBlurStyle
        )
    } else {
        this
    }
}
