package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DialogMediaPreview(
    media: MessageMedia,
    session: AccountSession?,
    size: Dp = 32.dp
) {
    when (media.kind) {
        MessageMediaKind.Photo,
        MessageMediaKind.Video,
        MessageMediaKind.Animation,
        MessageMediaKind.VideoNote -> {
            if (media.previewFileId != null || media.minithumbnail != null) {
                DialogImagePreview(media, session, size)
            }
        }

        MessageMediaKind.Sticker -> {
            if (media.stickerFileId != null) {
                DialogStickerPreview(media, session, size)
            }
        }

        else -> Unit
    }
}

@Composable
private fun DialogImagePreview(
    media: MessageMedia,
    session: AccountSession?,
    size: Dp
) {
    var image by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var finished by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf(false)
    }

    LaunchedEffect(media.previewFileId, media.minithumbnail, session) {
        finished = false

        image = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        if (image == null) {
            val bytes = session?.loadMediaBytes(
                fileId = media.previewFileId ?: media.fileId,
                uniqueId = media.previewUniqueId ?: media.fileUniqueId
            )

            if (bytes != null) {
                image = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }
            }
        }

        finished = true
    }

    val bitmap = image
    if (bitmap == null && finished) return

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(rememberFadeIn(bitmap)),
                contentScale = ContentScale.Crop
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp
            )
        }
    }
}

@Composable
private fun DialogStickerPreview(
    media: MessageMedia,
    session: AccountSession?,
    size: Dp
) {
    val fileId = media.stickerFileId ?: return

    var data by remember(fileId, session) { mutableStateOf<ByteArray?>(null) }
    var finished by remember(fileId, session) { mutableStateOf(false) }

    LaunchedEffect(fileId, session) {
        finished = false
        data = session?.loadMediaBytes(
            fileId = fileId,
            uniqueId = media.stickerUniqueId ?: media.fileUniqueId
        )
        finished = true
    }

    val bytes = data
    if (bytes == null && finished) return

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (bytes != null) {
            val emoji = remember(
                fileId,
                bytes,
                media.minithumbnail,
                media.stickerFormat,
                media.needsRepainting
            ) {
                DialogFolderEmoji(
                    customEmojiId = fileId.toLong(),
                    offset = 0,
                    length = 0,
                    fallback = "",
                    needsRepainting = media.needsRepainting,
                    format = when (media.stickerFormat) {
                        MessageStickerFormat.Tgs -> DialogFolderEmojiFormat.Tgs
                        MessageStickerFormat.Webm -> DialogFolderEmojiFormat.Webm
                        MessageStickerFormat.Webp -> DialogFolderEmojiFormat.Webp
                    },
                    thumbnail = media.minithumbnail,
                    data = bytes
                )
            }

            FolderEmojiImage(
                emoji = emoji,
                animate = true,
                size = size,
                color = Color.Unspecified,
                contentModifier = Modifier.size(size)
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp
            )
        }
    }
}
