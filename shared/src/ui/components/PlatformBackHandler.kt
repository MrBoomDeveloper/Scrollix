package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform back gesture while [enabled]. Used by overlays that
 * are not navigation routes, so back closes them instead of leaving the chat.
 * Desktop back requests arrive through [com.mrboomdev.scrollix.BackNavigation].
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
