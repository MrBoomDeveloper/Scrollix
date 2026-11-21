package com.mrboomdev.scrollix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fades freshly loaded content in instead of letting it pop into the layout
 * the moment its bytes or decoder are ready. Restarts whenever [key] changes.
 */
@Composable
internal fun rememberFadeIn(key: Any?, durationMillis: Int = 200): Float {
    val fade = remember { Animatable(0f) }

    LaunchedEffect(key) {
        if (key != null) {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(durationMillis))
        }
    }

    return fade.value
}

@Composable
internal fun FolderEmojiImage(
    emoji: DialogFolderEmoji,
    animate: Boolean,
    size: Dp,
    color: Color,
    contentModifier: Modifier? = null,
    showFallback: Boolean = true
) {
    val colorFilter = if (emoji.needsRepainting) {
        ColorFilter.tint(color)
    } else {
        null
    }

    when {
        !animate || LocalInspectionMode.current ->
            StaticFolderEmoji(emoji, size, color, colorFilter, contentModifier, showFallback)

        emoji.format == DialogFolderEmojiFormat.Tgs ->
            TgsFolderEmoji(emoji, size, color, colorFilter, contentModifier, showFallback)

        emoji.format == DialogFolderEmojiFormat.Webm ->
            WebmFolderEmoji(emoji, size, color, colorFilter, contentModifier, showFallback)

        else ->
            StaticFolderEmoji(emoji, size, color, colorFilter, contentModifier, showFallback)
    }
}

@Composable
internal fun StaticFolderEmoji(
    emoji: DialogFolderEmoji,
    size: Dp,
    color: Color,
    colorFilter: ColorFilter?,
    contentModifier: Modifier? = null,
    showFallback: Boolean = true
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
            modifier = (contentModifier ?: Modifier.size(size))
                .alpha(rememberFadeIn(bitmap)),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
    } else if (showFallback) {
        FolderEmojiFallback(emoji, size, color)
    }
}

/** Renders a user emoji status (the custom emoji shown next to a name). */
@Composable
internal fun EmojiStatusIcon(
    emojiId: Long,
    session: AccountSession?,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    var data by remember(emojiId, session) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(emojiId, session) {
        data = session?.loadCustomEmoji(emojiId)
    }

    val bytes = data

    Box(modifier = modifier.size(size)) {
        if (bytes != null) {
            val emoji = remember(emojiId, bytes) { customEmojiModel(emojiId, bytes) }

            FolderEmojiImage(
                emoji = emoji,
                animate = true,
                size = size,
                color = Color.Unspecified,
                contentModifier = Modifier.size(size)
            )
        }
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
