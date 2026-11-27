package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.icons.attach_file_icon
import com.mrboomdev.scrollix.ui.icons.microphone_icon
import com.mrboomdev.scrollix.ui.icons.music_note_icon
import com.mrboomdev.scrollix.ui.icons.play_icon_filled

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Preview of the replied message. Visual media keep a picture and get a play
 * mark when they are videos or GIFs; a sticker shows its own preview, while
 * voice notes, audio and files get a small type icon, so the quoted message is
 * recognizable even when it has no thumbnail of its own.
 */
@Composable
internal fun ReplyMediaThumbnail(
    media: MessageMedia,
    session: AccountSession?
) {
    when (media.kind) {
        MessageMediaKind.Photo,
        MessageMediaKind.Sticker -> ReplyImageThumbnail(media = media, session = session)

        MessageMediaKind.Video,
        MessageMediaKind.Animation,
        MessageMediaKind.VideoNote -> Box {
            ReplyImageThumbnail(media = media, session = session)

            Icon(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(18.dp),
                imageVector = play_icon_filled,
                contentDescription = null,
                tint = Color.White
            )
        }

        MessageMediaKind.VoiceNote -> ReplyIconThumbnail(microphone_icon)

        MessageMediaKind.Audio -> ReplyIconThumbnail(music_note_icon)

        MessageMediaKind.File -> ReplyIconThumbnail(attach_file_icon)
    }
}

@Composable
private fun ReplyIconThumbnail(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
