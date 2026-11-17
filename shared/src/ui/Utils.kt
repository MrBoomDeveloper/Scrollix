package com.mrboomdev.scrollix.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass

expect fun Modifier.onSecondaryClick(action: () -> Unit): Modifier

@Composable
fun isWideLayout(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    
    return windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )
}

@Composable
fun isTwoPaneLayout(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    return windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )
}