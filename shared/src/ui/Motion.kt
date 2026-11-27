package com.mrboomdev.scrollix.ui

import com.mrboomdev.scrollix.data.storage.AppSettingsStore

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset

/**
 * Motion tokens shared by every screen. [reduceMotion] collapses the spatial
 * part to an instant change and keeps a short fade, so the app still reads as
 * changing state while nothing slides or scales.
 */
@Immutable
class MotionTokens(val reduceMotion: Boolean) {
    val spatial: FiniteAnimationSpec<IntOffset> = if (reduceMotion) {
        tween(durationMillis = 0)
    } else {
        spring(stiffness = Spring.StiffnessHigh)
    }

    val gentleSpatial: FiniteAnimationSpec<IntOffset> = if (reduceMotion) {
        tween(durationMillis = 0)
    } else {
        spring(dampingRatio = Spring.DampingRatioNoBouncy)
    }

    val fade: FiniteAnimationSpec<Float> = tween(
        durationMillis = if (reduceMotion) 80 else 220
    )

    /** Duration for an in-place appearance such as a just-sent message. */
    val appearMillis: Int = if (reduceMotion) 0 else 220
}

@Composable
fun rememberMotionTokens(): MotionTokens {
    val settings by AppSettingsStore.state.collectAsState()

    return remember(settings.reduceMotion) {
        MotionTokens(reduceMotion = settings.reduceMotion)
    }
}
