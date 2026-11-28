package com.mrboomdev.scrollix.platform.media

import androidx.compose.ui.graphics.ImageBitmap

/** The result of a trim: a fresh file, the source is never modified. */
data class TrimmedVideo(
    val path: String,
    val durationMs: Long
)

data class VideoTrimRequest(
    val path: String,
    val startMs: Long,
    val endMs: Long,
    val muted: Boolean,
    val targetPath: String
)

/** Shortest accepted range; a shorter clip is almost never what was meant. */
const val MinTrimDurationMs = 1_000L

/** A range is usable when it is ordered, inside the file and long enough. */
fun trimRangeValid(startMs: Long, endMs: Long, durationMs: Long): Boolean {
    if (startMs < 0L || endMs <= startMs) return false
    if (durationMs > 0L && endMs > durationMs) return false
    return endMs - startMs >= MinTrimDurationMs
}

/** How much of the kept range [positionMs] has passed, 0..1. */
fun trimProgress(startMs: Long, endMs: Long, positionMs: Long): Float {
    if (endMs <= startMs) return 1f

    return ((positionMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
}

/**
 * Output size if the cut kept the source bitrate. Providers use it for the
 * free-space check before a transcode starts.
 */
fun estimatedTrimBytes(
    sourceBytes: Long,
    durationMs: Long,
    startMs: Long,
    endMs: Long
): Long {
    if (sourceBytes <= 0L || durationMs <= 0L || endMs <= startMs) return 0L

    return (sourceBytes.toDouble() * (endMs - startMs) / durationMs)
        .toLong()
        .coerceAtLeast(1L)
}

/** A trim needs room for the output and the muxer's own tail. */
fun trimFitsOnDisk(freeBytes: Long, estimatedBytes: Long): Boolean =
    estimatedBytes > 0L && freeBytes >= estimatedBytes * 2L

/** Duration of a local video, or null when the file cannot be read. */
expect suspend fun loadVideoDurationMs(path: String): Long?

/** [count] evenly spaced frames for the trim filmstrip. */
expect suspend fun extractVideoThumbnails(
    path: String,
    count: Int,
    maxWidthPx: Int = 160
): List<ImageBitmap>

/** A fresh path in app storage for a trimmed video. */
expect fun trimOutputPath(): String

/** Removes a trimmed file that will not be sent. */
expect fun deleteTrimFile(path: String)

expect fun localFileSizeBytes(path: String): Long

/** Bytes available on the volume that holds [path]. */
expect fun freeDiskSpaceBytes(path: String): Long

/**
 * Re-encodes `[startMs, endMs)` exactly — no keyframe snapping — and drops the
 * audio track when [VideoTrimRequest.muted]. A failure or cancellation leaves
 * no partial file behind.
 */
expect suspend fun trimVideo(
    request: VideoTrimRequest,
    onProgress: (Float) -> Unit
): Result<TrimmedVideo>
