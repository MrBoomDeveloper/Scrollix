package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val GallerySpacing = 2.dp

/** A compact grid used for album messages instead of one bubble per item. */
@Composable
internal fun MessageGallery(
    media: List<MessageMedia>,
    session: AccountSession?,
    onOpen: (Int) -> Unit
) {
    val columns = when (media.size) {
        2, 4 -> 2
        else -> 3
    }

    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalArrangement = Arrangement.spacedBy(GallerySpacing)
    ) {
        media.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GallerySpacing)
            ) {
                row.forEachIndexed { columnIndex, item ->
                    GalleryTile(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        media = item,
                        session = session,
                        onClick = { onOpen(rowIndex * columns + columnIndex) }
                    )
                }

                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(
    media: MessageMedia,
    session: AccountSession?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var bitmap by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var isLoading by remember(media.previewFileId, media.minithumbnail, media.fileId, session) {
        mutableStateOf(true)
    }

    LaunchedEffect(media.previewFileId, media.minithumbnail, media.fileId, session) {
        isLoading = true
        bitmap = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        val previewId = media.previewFileId ?: media.fileId
        val bytes = previewId?.let { fileId -> session?.loadFile(fileId)?.getOrNull() }

        if (bytes != null) {
            bitmap = withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        isLoading = false
    }

    val isVideo = media.kind == MessageMediaKind.Video ||
        media.kind == MessageMediaKind.Animation ||
        media.kind == MessageMediaKind.VideoNote

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap

        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .loadingBlur(isLoading),
                contentScale = ContentScale.Crop
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 1.5.dp
            )
        }

        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}
