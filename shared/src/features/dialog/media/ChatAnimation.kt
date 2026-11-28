package com.mrboomdev.scrollix.features.dialog.media

import com.mrboomdev.scrollix.data.media.WebmAnimation
import com.mrboomdev.scrollix.data.media.loadWebmAnimation
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.provider.MediaSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.features.dialog.MediaCornerRadius
import com.mrboomdev.scrollix.features.home.WebmFrames
import com.mrboomdev.scrollix.ui.components.BytesImage
import com.mrboomdev.scrollix.ui.components.MediaImage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val AnimationPreviewWidth = 260.dp

@Composable
fun ChatAnimationPreview(
    media: MessageMedia,
    session: MediaSession?,
    onClick: () -> Unit
) {
    var posterLoaded by remember(media.previewFileId) { mutableStateOf(false) }
    var animation by remember(media.fileId, session) {
        mutableStateOf<WebmAnimation?>(null)
    }
    var isLoading by remember(media.fileId, session) { mutableStateOf(true) }
    // Data saver → GIFs and animated stickers are not loaded until the user
    // allows them again. The still preview stays visible instead. Read once
    // instead of collecting: a bubble does not need to react while it is open.
    val animationsAllowed = AppSettingsStore.state.value.dataSaving.animations
    var revealed by remember(media.fileId, media.fileUniqueId) {
        mutableStateOf(!media.isSpoiler)
    }

    val aspectRatio = if (media.width > 0 && media.height > 0) {
        media.width.toFloat() / media.height
    } else {
        1f
    }
    val widthPx = with(LocalDensity.current) { AnimationPreviewWidth.roundToPx() }
    val heightPx = (widthPx / aspectRatio).toInt().coerceAtLeast(1)

    LaunchedEffect(media.fileId, session, widthPx, heightPx, animationsAllowed) {
        val fileId = media.fileId

        if (fileId != null && animationsAllowed) {
            val bytes = session?.loadMediaBytes(fileId, media.fileUniqueId)

            if (bytes != null) {
                animation = loadWebmAnimation(
                    customEmojiId = fileId.toLong(),
                    bytes = bytes,
                    widthPx = widthPx,
                    heightPx = heightPx
                )
            }
        }

        isLoading = false
    }

    val webm = animation
    val minithumbnail = media.minithumbnail
    val hasPoster = minithumbnail != null || posterLoaded

    Box(
        modifier = Modifier
            // Same sizing rule as photos and videos: the bubble's width,
            // capped height, rounded media corners.
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .aspectRatio(aspectRatio.coerceIn(0.6f, 2.5f))
            .clip(RoundedCornerShape(MediaCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable {
                if (!revealed) revealed = true else onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        val isReady = webm != null && webm.frames.isNotEmpty()

        // The low quality preview stays underneath the whole time: blurred
        // while the GIF is still loading, sharp while the animation fades in
        // on top of it.
        if (minithumbnail != null) {
            BytesImage(
                bytes = minithumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .loadingBlur(!isReady)
                    .mediaSpoilerBlur(!revealed),
                contentScale = ContentScale.Crop
            )
        }

        MediaImage(
            session = session,
            fileId = media.previewFileId,
            uniqueId = media.previewUniqueId,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .loadingBlur(!isReady)
                .mediaSpoilerBlur(!revealed),
            contentScale = ContentScale.Crop,
            onSuccess = { posterLoaded = true }
        )

        if (!revealed) {
            SpoilerOverlay(onReveal = { revealed = true })
        } else if (isReady) {
            WebmFrames(
                animation = webm,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!isLoading && !hasPoster) {
            Text(
                text = "GIF",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
