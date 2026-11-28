package com.mrboomdev.scrollix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

@Composable
fun PaddingValues.add(
    start: Dp = 0.dp, 
    top: Dp = 0.dp, 
    end: Dp = 0.dp, 
    bottom: Dp = 0.dp
): PaddingValues = PaddingValues(
    start = calculateStartPadding(LocalLayoutDirection.current) + start,
    top = calculateTopPadding() + top,
    end = calculateEndPadding(LocalLayoutDirection.current) + end,
    bottom = calculateBottomPadding() + bottom,
)

@Composable
fun PaddingValues.add(
    all: Dp
) = add(all, all, all, all)

@Composable
fun PaddingValues.add(
    vertical: Dp = 0.dp, 
    horizontal: Dp = 0.dp
) = add(horizontal, vertical, horizontal, vertical)

expect fun Modifier.onSecondaryClick(action: () -> Unit): Modifier

fun Modifier.clickableNoIndication(
    onClick: () -> Unit
) = clickable(
    onClick = onClick,
    interactionSource = null,
    indication = null
)

@Composable
fun isExpandedLayout(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    return windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )
}

/**
 * Returns a copy of the list with the item at [from] moved to [to]. Invalid
 * indices return the list unchanged, so a move button at either edge is
 * harmless and the caller does not need bounds checks.
 */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this

    val result = toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

/**
 * @param condition Return true to not remove an item
 */
fun <T> MutableIterable<T>.clearExcept(condition: (T) -> Boolean) {
    val iterator = this.iterator()
    
    while (iterator.hasNext()) {
        if (!condition(iterator.next())) {
            iterator.remove()
        }
    }
}

@Composable
@Deprecated("Use currentAdaptiveLayoutInfo instead!")
fun isTwoPaneLayout(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    return windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )
}