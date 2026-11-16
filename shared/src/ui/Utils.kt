package com.mrboomdev.scrollix.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

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