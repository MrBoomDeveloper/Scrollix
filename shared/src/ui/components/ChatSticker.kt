package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
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

private val StickerSize = 120.dp

@Composable
internal fun ChatSticker(
    media: MessageMedia,
    session: AccountSession?
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
    val aspectRatio = when {
        decoded != null && decoded.height > 0 -> decoded.width.toFloat() / decoded.height
        media.width > 0 && media.height > 0 -> media.width.toFloat() / media.height
        else -> 1f
    }
    val height = StickerSize
    val width = (height * aspectRatio).coerceIn(48.dp, 260.dp)

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

    Box(
        modifier = Modifier.size(width, height),
        contentAlignment = Alignment.Center
    ) {
        when {
            decoded != null -> Image(
                bitmap = decoded,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            media.stickerFormat == MessageStickerFormat.Webm && animationData != null -> {
                WebmSticker(
                    stickerId = fileId?.toLong() ?: 0L,
                    data = animationData,
                    width = width,
                    height = height,
                    modifier = Modifier.fillMaxSize()
                ) {
                    FolderEmojiImage(
                        emoji = emoji,
                        animate = false,
                        size = height,
                        color = Color.Unspecified,
                        contentModifier = Modifier.fillMaxSize()
                    )
                }
            }

            data != null || media.minithumbnail != null -> FolderEmojiImage(
                emoji = emoji,
                animate = true,
                size = height,
                color = Color.Unspecified,
                contentModifier = Modifier.fillMaxSize()
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
