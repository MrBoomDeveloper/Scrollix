package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable

/**
 * Desktop windows do not report a back gesture. Escape is published as a
 * [com.mrboomdev.scrollix.BackNavigation] request by the window instead.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
