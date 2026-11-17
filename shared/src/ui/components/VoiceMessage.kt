package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun VoiceMessagePlayer(
    media: MessageMedia,
    session: AccountSession?
) {
    var isPlaying by remember(media.fileId) { mutableStateOf(false) }
    var isLoading by remember(media.fileId) { mutableStateOf(false) }
    var positionMs by remember(media.fileId) { mutableLongStateOf(0L) }
    var isSeeking by remember(media.fileId) { mutableStateOf(false) }
    var seekValue by remember(media.fileId) { mutableFloatStateOf(0f) }
    val player = remember { createVoicePlayer() }
    val scope = rememberCoroutineScope()
    val durationMs = (media.durationSeconds.toLong() * 1000L).coerceAtLeast(1L)

    DisposableEffect(player) {
        onDispose {
            player.stop()
        }
    }

    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            positionMs = player.positionMs()
            delay(200)
        }
    }

    Row(
        modifier = Modifier
            .width(280.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    if (isPlaying) {
                        val pausedAt = positionMs
                        player.stop()
                        isPlaying = false
                        positionMs = pausedAt
                        return@clickable
                    }

                    val fileId = media.fileId ?: return@clickable

                    scope.launch {
                        isLoading = true
                        val bytes = session?.loadFile(fileId)?.getOrNull()
                        isLoading = false

                        if (bytes != null) {
                            isPlaying = player.play(bytes, positionMs) {
                                isPlaying = false
                                positionMs = 0L
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            if (media.kind == MessageMediaKind.Audio) {
                Text(
                    text = media.fileName ?: "Audio",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Slider(
                modifier = Modifier.fillMaxWidth(),
                value = (if (isSeeking) seekValue else positionMs.toFloat())
                    .coerceIn(0f, durationMs.toFloat()),
                onValueChange = { value ->
                    isSeeking = true
                    seekValue = value
                },
                onValueChangeFinished = {
                    isSeeking = false
                    positionMs = seekValue.toLong()
                    player.seekTo(seekValue.toLong())
                },
                valueRange = 0f..durationMs.toFloat()
            )
        }

        Text(
            text = "${formatDuration((positionMs / 1000L).toInt())} / " +
                formatDuration(media.durationSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remaining = safeSeconds % 60

    return if (minutes > 0) {
        "$minutes:${remaining.toString().padStart(2, '0')}"
    } else {
        "0:${remaining.toString().padStart(2, '0')}"
    }
}
