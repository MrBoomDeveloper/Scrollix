package com.mrboomdev.scrollix.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowSizeClass

enum class AdaptiveLayoutSize {
    SMALL,
    MEDIUM,
    EXPANDED,
    LARGE,
    EXTRA_LARGE
}

data class AdaptiveLayoutInfo(
    val isTwoPane: Boolean,
    val size: AdaptiveLayoutSize
)

@Composable
fun currentAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val windowInfo = currentWindowAdaptiveInfoV2()
    
    return remember(windowInfo) { 
        AdaptiveLayoutInfo(
            size = when {
                windowInfo.windowSizeClass.isWidthAtLeastBreakpoint(
                    WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND
                ) -> AdaptiveLayoutSize.EXTRA_LARGE
                
                windowInfo.windowSizeClass.isWidthAtLeastBreakpoint(
                    WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND
                ) -> AdaptiveLayoutSize.LARGE
                
                windowInfo.windowSizeClass.isWidthAtLeastBreakpoint(
                    WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
                ) -> AdaptiveLayoutSize.EXPANDED
                
                windowInfo.windowSizeClass.isWidthAtLeastBreakpoint(
                    WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
                ) -> AdaptiveLayoutSize.MEDIUM
                
                else -> AdaptiveLayoutSize.SMALL
            },
            
            isTwoPane = windowInfo.windowSizeClass.isWidthAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
            )
        )
    }
}