package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Decodes the provider's waveform: one 5-bit sample per byte, which is how
 * TDLib stores voice notes. Values are normalized to 0..1 for drawing.
 */
internal fun decodeVoiceWaveform(bytes: ByteArray): List<Float> {
    if (bytes.isEmpty()) return emptyList()

    return bytes.map { byte -> (byte.toInt() and 0x1F) / 31f }
}

/** Horizontal position turned into a 0..1 seek fraction, safe for any width. */
internal fun waveformSeekFraction(x: Float, width: Int): Float {
    if (width <= 0) return 0f

    return (x / width).coerceIn(0f, 1f)
}

/**
 * The waveform as a seek bar: bars before the playhead are drawn in the active
 * color, the rest dimmed. A tap seeks at once; dragging previews the position
 * and commits when the finger is lifted, so desktop playback is not restarted
 * for every pixel.
 */
@Composable
internal fun WaveformSeekBar(
    levels: List<Float>,
    progress: Float,
    color: Color,
    playedColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekPreview: (Float) -> Unit = onSeek
) {
    Canvas(
        modifier = modifier
            .height(28.dp)
            .pointerInput(levels) {
                detectTapGestures { offset ->
                    onSeek(waveformSeekFraction(offset.x, size.width))
                }
            }
            .pointerInput(levels) {
                var dragFraction = 0f

                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        dragFraction = waveformSeekFraction(
                            x = change.position.x,
                            width = size.width
                        )
                        onSeekPreview(dragFraction)
                    },
                    onDragEnd = {
                        // Commit the last previewed fraction, the same way a
                        // tap seeks at once.
                        onSeek(dragFraction)
                    }
                )
            }
    ) {
        if (levels.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas

        val barWidth = 2.dp.toPx()
        val gap = barWidth * 0.8f
        val slot = barWidth + gap
        val maxBars = (size.width / slot).toInt().coerceAtLeast(1)
        val bars = if (levels.size <= maxBars) {
            levels
        } else {
            // More samples than pixels: average neighbouring ones instead of
            // drawing a solid block.
            val step = levels.size.toFloat() / maxBars
            List(maxBars) { index ->
                val from = (index * step).toInt()
                val to = ((index + 1) * step).toInt().coerceAtMost(levels.size)
                levels.subList(from, to.coerceAtLeast(from + 1)).average().toFloat()
            }
        }

        val playhead = progress.coerceIn(0f, 1f) * size.width
        val minHeight = 2.dp.toPx()

        bars.forEachIndexed { index, level ->
            val height = (minHeight + (size.height - minHeight) * level)
                .coerceAtMost(size.height)
            val x = index * slot
            val played = x + barWidth / 2f <= playhead

            drawRoundRect(
                color = if (played) playedColor else color,
                topLeft = Offset(x, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

