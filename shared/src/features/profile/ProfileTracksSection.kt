package com.mrboomdev.scrollix.features.profile

import com.mrboomdev.scrollix.data.model.ProfileTrack
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.platform.audio.createVoicePlayer
import com.mrboomdev.scrollix.ui.components.BytesImage
import com.mrboomdev.scrollix.ui.icons.music_note_icon
import com.mrboomdev.scrollix.ui.icons.pause_icon_filled
import com.mrboomdev.scrollix.ui.icons.play_icon_filled

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalGridApi::class)
@Composable
internal fun TracksSection(
    tracks: List<ProfileTrack>,
    session: AccountSession?
) {
    val player = remember { createVoicePlayer() }
    val scope = rememberCoroutineScope()
    var playingFileId by remember { mutableStateOf<Int?>(null) }
    var loadingFileId by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(player) {
        onDispose { player.stop() }
    }

    fun toggle(track: ProfileTrack) {
        val fileId = track.media.fileId ?: return

        if (loadingFileId != null) return

        if (playingFileId == fileId) {
            player.stop()
            playingFileId = null
            return
        }

        player.stop()
        playingFileId = null
        loadingFileId = fileId

        scope.launch {
            val bytes = session?.loadFile(fileId)?.getOrNull()
            loadingFileId = null

            if (bytes != null) {
                playingFileId = fileId

                val started = player.play(bytes, 0L) {
                    if (playingFileId == fileId) {
                        playingFileId = null
                    }
                }

                if (!started) {
                    playingFileId = null
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        )

        val textStyle = MaterialTheme.typography.bodyMedium

        val inlinePlaceholder = remember(textStyle) {
            Placeholder(
                width = textStyle.fontSize * 1.4,
                height = textStyle.fontSize * 1.4,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        }

        Text(
            style = textStyle,
            color = MaterialTheme.colorScheme.secondary,

            inlineContent = remember(textStyle) {
                mapOf("icon" to InlineTextContent(inlinePlaceholder) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = music_note_icon,
                        contentDescription = null
                    )
                })
            },

            text = buildAnnotatedString {
                appendInlineContent("icon")
                append(" Pinned tracks")
            }
        )

        AdaptiveGrid(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            minColumnWidth = 250.dp,
            gap = 8.dp
        ) {
            for (track in tracks) {
                val fileId = track.media.fileId

                ProfileTrackCard(
                    track = track,
                    isPlaying = fileId != null && playingFileId == fileId,
                    isLoading = fileId != null && loadingFileId == fileId,
                    onToggle = { toggle(track) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileTrackCard(
    track: ProfileTrack,
    isPlaying: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit
) {
    val cover = track.media.minithumbnail
    var coverLoaded by remember(cover) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (!coverLoaded) {
                    Text(
                        text = "♪",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (cover != null) {
                    BytesImage(
                        bytes = cover,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onSuccess = { coverLoaded = true },
                        onError = { coverLoaded = false }
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = listOfNotNull(
                    track.performer?.takeIf { it.isNotBlank() },
                    formatTrackDuration(track.durationSeconds).takeIf { it.isNotBlank() }
                ).joinToString(" · ")

                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified
            ) {
                FilledTonalIconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onToggle
                ) {
                    if(isLoading) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = if(isPlaying) {
                                pause_icon_filled
                            } else play_icon_filled,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

internal fun formatTrackDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)

    if (safeSeconds == 0) return ""

    val minutes = safeSeconds / 60
    val remaining = safeSeconds % 60

    return "$minutes:${remaining.toString().padStart(2, '0')}"
}
