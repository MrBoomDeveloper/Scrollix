package com.mrboomdev.scrollix.features.dialog.composer

import com.mrboomdev.scrollix.features.search.formatFileSize
import com.mrboomdev.scrollix.platform.files.PickedFile
import com.mrboomdev.scrollix.platform.media.MinTrimDurationMs
import com.mrboomdev.scrollix.platform.media.VideoTrimRequest
import com.mrboomdev.scrollix.platform.media.deleteTrimFile
import com.mrboomdev.scrollix.platform.media.estimatedTrimBytes
import com.mrboomdev.scrollix.platform.media.extractVideoThumbnails
import com.mrboomdev.scrollix.platform.media.freeDiskSpaceBytes
import com.mrboomdev.scrollix.platform.media.loadVideoDurationMs
import com.mrboomdev.scrollix.platform.media.localFileSizeBytes
import com.mrboomdev.scrollix.platform.media.trimFitsOnDisk
import com.mrboomdev.scrollix.platform.media.trimOutputPath
import com.mrboomdev.scrollix.platform.media.trimRangeValid
import com.mrboomdev.scrollix.platform.media.trimVideo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TrimThumbnailCount = 6
private const val TrimThumbnailWidthPx = 160

/**
 * Trim and mute a video before it is sent. The range is exact: both platforms
 * re-encode instead of snapping to a keyframe, and the original file is never
 * modified. The sheet stays open while the transcode runs and only offers Stop,
 * which deletes the partial output.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoTrimSheet(
    path: String,
    fileName: String,
    isMobile: Boolean,
    onDismiss: () -> Unit,
    onApply: (PickedFile) -> Unit
) {
    val scope = rememberCoroutineScope()

    var durationMs by remember(path) { mutableLongStateOf(0L) }
    var thumbnails by remember(path) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var startFraction by remember(path) { mutableFloatStateOf(0f) }
    var endFraction by remember(path) { mutableFloatStateOf(1f) }
    var muted by remember(path) { mutableStateOf(false) }
    var sourceBytes by remember(path) { mutableLongStateOf(0L) }
    var freeBytes by remember(path) { mutableLongStateOf(0L) }
    var isLoading by remember(path) { mutableStateOf(true) }
    var isTrimming by remember(path) { mutableStateOf(false) }
    var progress by remember(path) { mutableFloatStateOf(0f) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var job by remember(path) { mutableStateOf<Job?>(null) }

    LaunchedEffect(path) {
        isLoading = true
        error = null
        durationMs = loadVideoDurationMs(path) ?: 0L
        thumbnails = extractVideoThumbnails(path, TrimThumbnailCount, TrimThumbnailWidthPx)

        val sizes = withContext(Dispatchers.IO) {
            localFileSizeBytes(path) to freeDiskSpaceBytes(path)
        }

        sourceBytes = sizes.first
        freeBytes = sizes.second
        isLoading = false

        if (durationMs <= 0L) {
            error = "Couldn't read this video"
        } else if (durationMs < MinTrimDurationMs) {
            error = "This video is too short to trim"
        }
    }

    val startMs = (startFraction * durationMs).toLong()
    val endMs = (endFraction * durationMs).toLong()
    val estimatedBytes = estimatedTrimBytes(sourceBytes, durationMs, startMs, endMs)
    val fitsOnDisk = freeBytes <= 0L || trimFitsOnDisk(freeBytes, estimatedBytes)
    val canApply = !isTrimming &&
        durationMs >= MinTrimDurationMs &&
        trimRangeValid(startMs, endMs, durationMs) &&
        fitsOnDisk

    fun stopTrim() {
        job?.cancel()
        job = null
        isTrimming = false
        progress = 0f
    }

    fun applyTrim() {
        if (!canApply) return

        val target = trimOutputPath()

        job = scope.launch {
            isTrimming = true
            progress = 0f
            error = null

            val result = trimVideo(
                request = VideoTrimRequest(
                    path = path,
                    startMs = startMs,
                    endMs = endMs,
                    muted = muted,
                    targetPath = target
                ),
                onProgress = { fraction -> progress = fraction }
            )

            isTrimming = false

            result.onSuccess { trimmed ->
                onApply(
                    PickedFile(
                        path = trimmed.path,
                        name = trimmedFileName(fileName),
                        mimeType = "video/mp4"
                    )
                )
            }.onFailure { failure ->
                deleteTrimFile(target)
                error = failure.message ?: "Couldn't trim this video"
            }
        }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Trim video",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    TrimBar(
                        thumbnails = thumbnails,
                        startFraction = startFraction,
                        endFraction = endFraction,
                        minFraction = if (durationMs > 0L) {
                            (MinTrimDurationMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        enabled = !isTrimming && durationMs > 0L,
                        onRangeChange = { start, end ->
                            startFraction = start
                            endFraction = end
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatTrimTime(startMs)} – ${formatTrimTime(endMs)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = formatTrimTime(endMs - startMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (estimatedBytes > 0L && durationMs > 0L) {
                Text(
                    text = "About ${formatFileSize(estimatedBytes)}" +
                        if (fitsOnDisk) "" else " · not enough free space",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fitsOnDisk) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mute audio",
                    style = MaterialTheme.typography.bodyMedium
                )

                Switch(
                    checked = muted,
                    enabled = !isTrimming,
                    onCheckedChange = { muted = it }
                )
            }

            if (isTrimming) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        if (isTrimming) stopTrim() else onDismiss()
                    }
                ) {
                    Text(if (isTrimming) "Stop" else "Cancel")
                }

                TextButton(
                    enabled = !isTrimming &&
                        (startFraction > 0f || endFraction < 1f || muted),
                    onClick = {
                        startFraction = 0f
                        endFraction = 1f
                        muted = false
                    }
                ) {
                    Text("Reset")
                }

                Button(
                    enabled = canApply,
                    onClick = { applyTrim() }
                ) {
                    Text("Apply")
                }
            }
        }
    }

    if (isMobile) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { !isTrimming }
        )

        ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { if (!isTrimming) onDismiss() },
            sheetState = sheetState
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = { if (!isTrimming) onDismiss() },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { if (!isTrimming) onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .padding(24.dp)
                        // Clicks on the card must not reach the backdrop.
                        .clickable {},
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * The filmstrip with two handles. Which handle a drag moves is decided once,
 * when the drag starts, by which one is closer to the finger.
 */
@Composable
private fun TrimBar(
    thumbnails: List<ImageBitmap>,
    startFraction: Float,
    endFraction: Float,
    minFraction: Float,
    enabled: Boolean,
    onRangeChange: (Float, Float) -> Unit
) {
    var activeHandle by remember { mutableStateOf(-1) }
    val currentStart by rememberUpdatedState(startFraction)
    val currentEnd by rememberUpdatedState(endFraction)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val barWidth = with(density) { maxWidth.toPx() }

        Row(Modifier.fillMaxSize()) {
            if (thumbnails.isEmpty()) {
                Box(Modifier.fillMaxSize())
            } else {
                thumbnails.forEach { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, barWidth, minFraction) {
                    if (!enabled || barWidth <= 0f) return@pointerInput

                    detectDragGestures(
                        onDragStart = { position ->
                            activeHandle = if (
                                abs(position.x - barWidth * currentStart) <=
                                abs(position.x - barWidth * currentEnd)
                            ) {
                                0
                            } else {
                                1
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()

                            val fraction = (change.position.x / barWidth).coerceIn(0f, 1f)
                            val start = currentStart
                            val end = currentEnd

                            if (activeHandle == 0) {
                                onRangeChange(
                                    fraction.coerceIn(0f, (end - minFraction).coerceAtLeast(0f)),
                                    end
                                )
                            } else {
                                onRangeChange(
                                    start,
                                    fraction.coerceIn((start + minFraction).coerceAtMost(1f), 1f)
                                )
                            }
                        },
                        onDragEnd = { activeHandle = -1 },
                        onDragCancel = { activeHandle = -1 }
                    )
                }
        ) {
            val width = size.width
            val startX = width * startFraction
            val endX = width * endFraction
            val dim = Color.Black.copy(alpha = 0.6f)

            drawRect(color = dim, size = Size(startX, size.height))
            drawRect(
                color = dim,
                topLeft = Offset(endX, 0f),
                size = Size((width - endX).coerceAtLeast(0f), size.height)
            )

            drawRect(
                color = Color.White,
                topLeft = Offset(startX, 0f),
                size = Size((endX - startX).coerceAtLeast(0f), size.height),
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawHandle(Offset(startX, 0f), size.height)
            drawHandle(Offset(endX, 0f), size.height)
        }
    }
}

private fun DrawScope.drawHandle(x: Offset, trackHeight: Float) {
    val width = 6.dp.toPx()

    drawRoundRect(
        color = Color.White,
        topLeft = Offset(x.x - width / 2f, 0f),
        size = Size(width, trackHeight),
        cornerRadius = CornerRadius(width / 2f, width / 2f)
    )
}

/** `1:05` — compact enough for a range label in one line. */
internal fun formatTrimTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)

    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** `clip.mp4` becomes `clip-trimmed.mp4`. */
internal fun trimmedFileName(fileName: String): String {
    val base = fileName.substringBeforeLast('.', fileName).ifBlank { "video" }
    return "$base-trimmed.mp4"
}
