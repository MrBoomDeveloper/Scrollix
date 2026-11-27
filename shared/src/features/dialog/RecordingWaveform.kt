package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** How many sampled levels the live waveform keeps on screen. */
internal const val RecordingWaveformBars = 36

/**
 * Appends one sampled microphone level, dropping the oldest bar once the
 * window is full. Levels are clamped so a noisy recorder cannot draw outside
 * the bar.
 */
internal fun appendWaveformLevel(
    levels: List<Float>,
    level: Float,
    size: Int = RecordingWaveformBars
): List<Float> {
    if (size <= 0) return emptyList()

    return (levels + level.coerceIn(0f, 1f)).takeLast(size)
}

/**
 * Recent microphone levels as centered bars, newest on the right. An empty
 * list draws a flat line, which is what a silent first moment looks like.
 */
@Composable
internal fun RecordingWaveform(
    levels: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.height(24.dp)) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val barCount = RecordingWaveformBars
        val slot = size.width / barCount
        val barWidth = (slot * 0.55f).coerceAtLeast(1f)
        val minHeight = 2.dp.toPx()
        val centerY = size.height / 2f
        val visible = levels.takeLast(barCount)
        val firstIndex = barCount - visible.size

        visible.forEachIndexed { index, level ->
            val height = (minHeight + (size.height - minHeight) * level)
                .coerceAtMost(size.height)

            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * level),
                topLeft = Offset(
                    x = (firstIndex + index) * slot + (slot - barWidth) / 2f,
                    y = centerY - height / 2f
                ),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
