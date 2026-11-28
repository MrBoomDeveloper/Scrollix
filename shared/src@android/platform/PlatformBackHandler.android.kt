package com.mrboomdev.scrollix.platform

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // NavigationEvent is the predictive-back path on SDK 33+. The legacy
    // activity BackHandler is deprecated and would swallow the gesture
    // progress that NavDisplay animates.
    val state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)

    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCompleted = onBack
    )
}
