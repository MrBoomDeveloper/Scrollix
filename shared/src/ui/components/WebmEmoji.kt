package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji

@Composable
internal fun WebmFolderEmoji(
    emoji: DialogFolderEmoji,
    size: Dp,
    color: Color,
    colorFilter: ColorFilter?,
    contentModifier: Modifier? = null
) {
    val data = emoji.data

    if (data == null) {
        StaticFolderEmoji(emoji, size, color, colorFilter, contentModifier)
        return
    }

    val sizePx = with(LocalDensity.current) {
        size.roundToPx()
    }.coerceIn(16, 96)

    val animation by produceState<WebmAnimation?>(
        initialValue = null,
        emoji.customEmojiId,
        data,
        sizePx
    ) {
        value = loadWebmAnimation(
            customEmojiId = emoji.customEmojiId,
            bytes = data,
            widthPx = sizePx,
            heightPx = sizePx
        )
    }
    val webm = animation

    if (webm == null || webm.frames.isEmpty()) {
        StaticFolderEmoji(emoji, size, color, colorFilter, contentModifier)
        return
    }

    WebmFrames(
        animation = webm,
        modifier = contentModifier ?: Modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter
    )
}

@Composable
internal fun WebmSticker(
    stickerId: Long,
    data: ByteArray,
    width: Dp,
    height: Dp,
    modifier: Modifier,
    fallback: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }
    val heightPx = with(density) { height.roundToPx() }

    val animation by produceState<WebmAnimation?>(
        initialValue = null,
        stickerId,
        data,
        widthPx,
        heightPx
    ) {
        value = loadWebmAnimation(
            customEmojiId = stickerId,
            bytes = data,
            widthPx = widthPx,
            heightPx = heightPx
        )
    }
    val webm = animation

    if (webm == null || webm.frames.isEmpty()) {
        fallback()
        return
    }

    WebmFrames(
        animation = webm,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
internal fun WebmFrames(
    animation: WebmAnimation,
    modifier: Modifier,
    contentScale: ContentScale,
    colorFilter: ColorFilter? = null
) {
    var frameIndex by remember(animation) { mutableIntStateOf(0) }

    LaunchedEffect(animation) {
        val frameRate = animation.frameRate.coerceAtLeast(1f)
        val frameDuration = (1_000_000_000.0 / frameRate)
            .toLong()
            .coerceAtLeast(1L)
        var startedAt = 0L

        while (true) {
            withFrameNanos { now ->
                if (startedAt == 0L) startedAt = now
                frameIndex = (((now - startedAt) / frameDuration).toInt()) %
                    animation.frames.size
            }
        }
    }

    Image(
        bitmap = animation.frames[frameIndex.coerceIn(0, animation.frames.lastIndex)],
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter
    )
}
