package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.storage.AppSettingsStore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Covers media that the sender marked as a spoiler. The image underneath is
 * blurred and the layer is the only tap target, so a hidden photo cannot be
 * opened by accident. Like Telegram there is no label: the blurred content is
 * the hint, and tapping it fades the blur out.
 */
@Composable
fun SpoilerOverlay(
    modifier: Modifier = Modifier,
    onReveal: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // A light scrim keeps the blurred edges from giving the picture
            // away without darkening it as much as a full overlay would.
            .background(Color.Black.copy(alpha = 0.16f))
            .clickable(onClick = onReveal)
    )
}

/**
 * Blur behind a hidden spoiler. The radius eases in when the message appears
 * and keeps a slight wobble while hidden, which is what makes a Telegram
 * spoiler look animated instead of like a static frosted rectangle. The wobble
 * is decorative, so it is dropped with `reduceMotion`.
 */
@Composable
fun Modifier.mediaSpoilerBlur(hidden: Boolean, radius: Dp = 22.dp): Modifier {
    val settings by AppSettingsStore.state.collectAsState()
    val target = if (hidden) radius else 0.dp
    val eased by animateDpAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "spoiler-blur"
    )

    // A driven Animatable keeps the wobble alive frame by frame without a
    // second composition-level animation API.
    val wobble = remember { Animatable(0f) }

    LaunchedEffect(hidden, settings.reduceMotion) {
        if (!hidden || settings.reduceMotion) {
            wobble.snapTo(0f)
            return@LaunchedEffect
        }

        while (true) {
            wobble.animateTo(1f, tween(durationMillis = 1300, easing = LinearEasing))
            wobble.animateTo(0f, tween(durationMillis = 1300, easing = LinearEasing))
        }
    }

    val effective = eased + (wobble.value * 5f).dp

    // A zero radius is a no-op, and skipping blur() there keeps the revealed
    // image from being rendered through an extra graphics layer.
    return if (effective > 0.5.dp) blur(effective) else this
}
