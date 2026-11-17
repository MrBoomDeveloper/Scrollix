package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FolderEmojiImage(
    emoji: DialogFolderEmoji,
    animate: Boolean,
    size: Dp,
    color: Color,
    contentModifier: Modifier? = null
) {
    val colorFilter = if (emoji.needsRepainting) {
        ColorFilter.tint(color)
    } else {
        null
    }

    when {
        !animate || LocalInspectionMode.current ->
            StaticFolderEmoji(emoji, size, color, colorFilter, contentModifier)

        emoji.format == DialogFolderEmojiFormat.Tgs ->
            TgsFolderEmoji(emoji, size, color, colorFilter, contentModifier)

        emoji.format == DialogFolderEmojiFormat.Webm ->
            WebmFolderEmoji(emoji, size, color, colorFilter, contentModifier)

        else ->
            StaticFolderEmoji(emoji, size, color, colorFilter, contentModifier)
    }
}

@Composable
internal fun StaticFolderEmoji(
    emoji: DialogFolderEmoji,
    size: Dp,
    color: Color,
    colorFilter: ColorFilter?,
    contentModifier: Modifier? = null
) {
    val bytes = when (emoji.format) {
        DialogFolderEmojiFormat.Webp -> emoji.data ?: emoji.thumbnail
        else -> emoji.thumbnail
    }
    val image by produceState<ImageBitmap?>(
        initialValue = null,
        bytes
    ) {
        value = bytes?.let { data ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(data)
            }
        }
    }

    val bitmap = image

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = contentModifier ?: Modifier.size(size),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
    } else {
        FolderEmojiFallback(emoji, size, color)
    }
}

@Composable
internal fun FolderEmojiFallback(
    emoji: DialogFolderEmoji,
    size: Dp,
    color: Color
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji.fallback,
            color = color,
            fontSize = with(LocalDensity.current) { size.toSp() },
            maxLines = 1
        )
    }
}
