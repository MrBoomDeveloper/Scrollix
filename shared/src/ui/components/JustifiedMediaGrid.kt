package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One tile of a [JustifiedMediaGrid]; the ratio is width / height. */
data class JustifiedMediaItem(
    val key: Any,
    val aspectRatio: Float
)

/**
 * Lays media out the way Telegram and Google Photos do: rows of equal height,
 * with every tile's width proportional to its aspect ratio so the row exactly
 * fills the available width. The only empty space left is the gap between
 * tiles, never a hole in the grid.
 *
 * The grid scrolls on its own, so it can be embedded in a profile screen that
 * already scrolls.
 */
@Composable
fun JustifiedMediaGrid(
    items: List<JustifiedMediaItem>,
    modifier: Modifier = Modifier,
    gap: Dp = 4.dp,
    minRowHeight: Dp = 96.dp,
    maxRowHeight: Dp = 220.dp,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable (JustifiedMediaItem, Modifier) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val rows = remember(items, maxWidth, gap, minRowHeight, maxRowHeight) {
            buildJustifiedRows(
                items = items,
                availableWidth = maxWidth,
                gap = gap,
                minRowHeight = minRowHeight,
                maxRowHeight = maxRowHeight
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            itemsIndexed(
                items = rows,
                key = { _, row ->
                    row.items.joinToString("|") { slot -> slot.item.key.toString() }
                }
            ) { _, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    row.items.forEach { slot ->
                        Box(
                            modifier = Modifier
                                .weight(slot.weight)
                                .height(row.height)
                        ) {
                            content(slot.item, Modifier.fillMaxSize())
                        }
                    }
                }
            }

            if (footer != null) {
                item(key = "justified-media-footer") {
                    footer()
                }
            }
        }
    }
}

internal data class JustifiedMediaSlot(
    val item: JustifiedMediaItem,
    val weight: Float
)

internal data class JustifiedMediaRow(
    val items: List<JustifiedMediaSlot>,
    val height: Dp
)

/**
 * Greedy row packing: items are added until the justified height of the row
 * drops to the target, then the row is fixed. Widths inside a row are the
 * aspect ratios themselves, so `sum(widths) == availableWidth` minus the gaps.
 */
internal fun buildJustifiedRows(
    items: List<JustifiedMediaItem>,
    availableWidth: Dp,
    gap: Dp,
    minRowHeight: Dp,
    maxRowHeight: Dp
): List<JustifiedMediaRow> {
    if (items.isEmpty() || availableWidth <= 0.dp) return emptyList()

    val width = availableWidth.value
    val gapWidth = gap.value.coerceAtLeast(0f)
    val minHeight = minRowHeight.value.coerceAtLeast(1f)
    val maxHeight = maxRowHeight.value.coerceAtLeast(minHeight)

    val rows = mutableListOf<JustifiedMediaRow>()
    var slots = mutableListOf<JustifiedMediaSlot>()
    var ratioSum = 0f

    fun flush() {
        if (slots.isEmpty()) return

        val gaps = gapWidth * (slots.size - 1)
        val height = ((width - gaps) / ratioSum).coerceIn(minHeight, maxHeight)

        rows += JustifiedMediaRow(items = slots, height = height.dp)
        slots = mutableListOf()
        ratioSum = 0f
    }

    for (item in items) {
        // A panorama or a very tall phone photo must not produce a row that is
        // a few pixels high or hundreds of them high.
        val ratio = item.aspectRatio
            .takeIf { value -> value.isFinite() && value > 0f }
            ?.coerceIn(0.4f, 3f)
            ?: 1f

        slots += JustifiedMediaSlot(item = item, weight = ratio)
        ratioSum += ratio

        val gaps = gapWidth * (slots.size - 1)
        val height = (width - gaps) / ratioSum

        if (height <= maxHeight) flush()
    }

    // The last row may still be taller than the cap; it is clamped to it, and
    // the proportional widths keep filling the whole width.
    flush()

    return rows
}
