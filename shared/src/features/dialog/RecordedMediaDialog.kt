package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.platform.audio.createVoicePlayer
import com.mrboomdev.scrollix.platform.media.MediaRecordingKind
import com.mrboomdev.scrollix.platform.media.RecordedMedia
import com.mrboomdev.scrollix.platform.media.createVideoPlayer
import com.mrboomdev.scrollix.ui.components.VideoRenderSurface

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun RecordedMediaDialog(
    recording: RecordedMedia,
    isSending: Boolean,
    error: String?,
    onDiscard: () -> Unit,
    onSend: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable {},
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (recording.kind == MediaRecordingKind.Voice) {
                            "Voice message"
                        } else {
                            "Video message"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (recording.kind == MediaRecordingKind.Voice) {
                        VoiceRecordingPreview(recording.bytes)
                    } else {
                        VideoRecordingPreview(recording.bytes)
                    }

                    Text(
                        text = formatRecordingDuration(recording.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    error?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            enabled = !isSending,
                            onClick = onDiscard
                        ) {
                            Text("Discard")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !isSending,
                            onClick = onSend
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordingPreview(bytes: ByteArray) {
    val player = remember { createVoicePlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose { player.stop() }
    }

    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            kotlinx.coroutines.delay(200)
        }
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable {
                if (isPlaying) {
                    player.stop()
                    isPlaying = false
                } else {
                    isPlaying = player.play(bytes) { isPlaying = false }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPlaying) "⏸" else "▶",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 28.sp
        )
    }
}

@Composable
private fun VideoRecordingPreview(bytes: ByteArray) {
    val player = remember { createVideoPlayer() }
    val state by player.state.collectAsState()

    DisposableEffect(player) {
        onDispose { player.stop() }
    }

    LaunchedEffect(bytes) {
        player.load(bytes, autoPlay = false)
    }

    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .clickable {
                if (state.isPlaying) player.pause() else player.play()
            },
        contentAlignment = Alignment.Center
    ) {
        VideoRenderSurface(player, Modifier.fillMaxSize())

        if (!state.isPlaying) {
            Text(
                text = "▶",
                color = Color.White,
                fontSize = 28.sp
            )
        }
    }
}

private fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
