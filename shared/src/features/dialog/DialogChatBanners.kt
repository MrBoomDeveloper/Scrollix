package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.resources.Res
import com.mrboomdev.scrollix.resources.wallpaper
import com.mrboomdev.scrollix.ui.icons.close_icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

/** The chat wallpaper: a server gradient/pattern or a decoded custom image. */
@Composable
internal fun DialogChatBackground(
    wallpaper: ChatWallpaper?,
    wallpaperImage: ImageBitmap?,
    appWallpaper: ImageBitmap?
) {
    val customWallpaper = wallpaperImage ?: appWallpaper
    val hasFill = wallpaper != null && wallpaper.colors.isNotEmpty()

    // Pattern wallpapers are drawn over the gradient fill with the intensity
    // from the server, photo wallpapers are drawn as is.
    if (hasFill) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(wallpaperBrush(wallpaper))
        )
    } else if (customWallpaper == null) {
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(Res.drawable.wallpaper),
            contentScale = ContentScale.Crop,
            contentDescription = null
        )
    }

    if (customWallpaper != null) {
        val isPattern = wallpaper?.isPattern == true

        Image(
            modifier = Modifier
                .fillMaxSize()
                .alpha(
                    if (isPattern) {
                        (wallpaper.intensity / 100f).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                ),
            bitmap = customWallpaper,
            contentScale = ContentScale.Crop,
            contentDescription = null
        )
    }
}

/** Banner for the live location this account is still sharing. */
@Composable
internal fun LiveLocationBanner(
    onOpen: () -> Unit,
    onStop: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onOpen)
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sharing live location",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Tap to open the message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            TextButton(onClick = onStop) {
                Text("Stop")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
}

/** Banner for the chat's pinned message; it may be outside the loaded window. */
@Composable
internal fun PinnedMessageBanner(
    preview: String,
    onOpen: () -> Unit,
    onUnpin: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onOpen)
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pinned message",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onUnpin) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = close_icon,
                    contentDescription = "Unpin message"
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
}
