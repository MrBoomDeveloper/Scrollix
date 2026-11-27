package com.mrboomdev.scrollix.platform.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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

    /**
     * Attaches the live camera preview used by circle video notes, or detaches
     * it with null. Platforms without a camera preview ignore the call.
     */
    fun attachPreviewSurface(surface: Any?) = Unit

    /** Starts recording, returning false when permission or hardware is unavailable. */
    fun start(kind: MediaRecordingKind): Boolean

    /**
     * Peak level of the audio captured since the previous call, 0..1. The
     * composer samples it while recording to draw the live waveform. Zero on
     * platforms or modes that cannot report it.
     */
    fun currentAmplitude(): Float = 0f

    /** Stops recording and returns the captured file for review. */
    fun stop(): RecordedMedia?

    fun cancel()
    fun release(recording: RecordedMedia)
    fun requestPermissions()
}

@Composable
expect fun rememberMediaRecorder(): MediaRecordingController

/**
 * Live circular preview of the video note being recorded. Android renders the
 * actual camera surface; platforms without video notes show a placeholder.
 */
@Composable
expect fun VideoNotePreview(controller: MediaRecordingController, modifier: Modifier)
