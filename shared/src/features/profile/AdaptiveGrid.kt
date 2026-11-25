package com.mrboomdev.scrollix.features.profile

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridScope
import androidx.compose.foundation.layout.GridTrackSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A Grid whose column count is derived from the available width.
 * Each column will be at least [minColumnWidth] wide and grow equally
 * to fill the remaining space.
 */
@OptIn(ExperimentalGridApi::class)
@Composable
fun AdaptiveGrid(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp,
    gap: Dp = 0.dp,
    content: @Composable GridScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val columnCount = calculateColumnCount(
            availableWidth = maxWidth,
            minColumnWidth = minColumnWidth,
            gap = gap,
        )

        Grid(
            modifier = Modifier.fillMaxWidth(),
            content = content,
            config = {
                gap(gap)
                repeat(columnCount) {
                    column(GridTrackSize.MinMax(minColumnWidth, 1.fr))
                }
            }
        )
    }
}

/**
 * How many columns of at least [minColumnWidth] fit in [availableWidth]
 * when each pair is separated by [gap].
 */
private fun calculateColumnCount(
    availableWidth: Dp,
    minColumnWidth: Dp,
    gap: Dp,
): Int {
    require(minColumnWidth > 0.dp) { "minColumnWidth must be > 0" }
    return ((availableWidth + gap) / (minColumnWidth + gap))
        .toInt()
        .coerceAtLeast(1)
}