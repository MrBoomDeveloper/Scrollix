package com.mrboomdev.scrollix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val StickerSize = 200.dp

@Composable
internal fun ChatSticker(
    media: MessageMedia,
    session: AccountSession?,
    onClick: (() -> Unit)? = null
) {
    val fileId = media.stickerFileId
    var data by remember(fileId, session) { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember(fileId, session) { mutableStateOf(fileId != null) }
    var bitmap by remember(fileId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(fileId, session) {
        data = fileId?.let { session?.loadFile(it)?.getOrNull() }
        isLoading = false
    }

    LaunchedEffect(data, media.stickerFormat) {
        val bytes = data

        bitmap = if (
            bytes != null &&
            media.stickerFormat == MessageStickerFormat.Webp
        ) {
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        } else {
            null
        }
    }

    val decoded = bitmap
    val animationData = data
    // The dimensions that come with the message are stable. Letting the
    // decoded bitmap redefine the ratio would resize the item (and shift
    // every message below it) as soon as the sticker finishes loading.
    val aspectRatio = when {
        media.width > 0 && media.height > 0 -> media.width.toFloat() / media.height
        decoded != null && decoded.height > 0 -> decoded.width.toFloat() / decoded.height
        else -> 1f
    }
    // Keep the box at the sticker's own aspect ratio: capping the width
    // without shrinking the height would squish landscape WebM stickers.
    val maxWidth = 260.dp
    val width = (StickerSize * aspectRatio).coerceAtMost(maxWidth)
    val height = width / aspectRatio

    val emoji = remember(fileId, data, media.minithumbnail, media.stickerFormat) {
        DialogFolderEmoji(
            customEmojiId = fileId?.toLong() ?: 0L,
            offset = 0,
            length = 0,
            fallback = "🙂",
            format = when (media.stickerFormat) {
                MessageStickerFormat.Tgs -> DialogFolderEmojiFormat.Tgs
                MessageStickerFormat.Webm -> DialogFolderEmojiFormat.Webm
                MessageStickerFormat.Webp -> DialogFolderEmojiFormat.Webp
            },
            thumbnail = media.minithumbnail,
            data = data
        )
    }

    // Nothing is drawn in place of the sticker while it loads; the sticker
    // itself fades in once its bytes (or preview) are available.
    val contentAlpha by animateFloatAsState(
        targetValue = if (data != null || media.minithumbnail != null) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "stickerFadeIn"
    )

    Box(
        modifier = Modifier
            .size(width, height)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            decoded != null -> Image(
                bitmap = decoded,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha),
                contentScale = ContentScale.Fit
            )

            media.stickerFormat == MessageStickerFormat.Webm && animationData != null -> {
                WebmSticker(
                    stickerId = fileId?.toLong() ?: 0L,
                    data = animationData,
                    width = width,
                    height = height,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(contentAlpha)
                ) {
                    // An empty fallback: no emoji placeholder, just the blank
                    // reserved space until the animation is ready.
                }
            }

            data != null || media.minithumbnail != null -> FolderEmojiImage(
                emoji = emoji,
                animate = true,
                size = height,
                color = Color.Unspecified,
                contentModifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha),
                showFallback = false
            )

            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))

            else -> Text(
                text = "Sticker",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
