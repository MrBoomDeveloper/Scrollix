package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable

enum class MediaRecordingKind {
    Voice,
    VideoNote
}

data class RecordedMedia(
    val kind: MediaRecordingKind,
    val path: String,
    val bytes: ByteArray,
    val durationMs: Long
)

interface MediaRecordingController {
    val supportsVideo: Boolean

    /** Starts recording, returning false when permission or hardware is unavailable. */
    fun start(kind: MediaRecordingKind): Boolean

    /** Stops recording and returns the captured file for review. */
    fun stop(): RecordedMedia?

    fun cancel()
    fun release(recording: RecordedMedia)
    fun requestPermissions()
}

@Composable
expect fun rememberMediaRecorder(): MediaRecordingController
