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
    colorFilter: ColorFilter?
) {
    val data = emoji.data

    if (data == null) {
        StaticFolderEmoji(emoji, size, color, colorFilter)
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
            sizePx = sizePx
        )
    }
    val webm = animation

    if (webm == null || webm.frames.isEmpty()) {
        StaticFolderEmoji(emoji, size, color, colorFilter)
        return
    }

    var frameIndex by remember(webm) { mutableIntStateOf(0) }

    LaunchedEffect(webm) {
        val frameRate = webm.frameRate.coerceAtLeast(1f)
        val frameDuration = (1_000_000_000.0 / frameRate)
            .toLong()
            .coerceAtLeast(1L)
        var startedAt = 0L

        while (true) {
            withFrameNanos { now ->
                if (startedAt == 0L) startedAt = now
                frameIndex = (((now - startedAt) / frameDuration).toInt()) %
                    webm.frames.size
            }
        }
    }

    Image(
        bitmap = webm.frames[frameIndex.coerceIn(0, webm.frames.lastIndex)],
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter
    )
}
