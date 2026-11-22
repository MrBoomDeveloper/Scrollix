package com.mrboomdev.scrollix.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Provides the app-wide shared transition scope to pushed screens. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * Wraps a small element (avatar or display name) that should fly between the
 * chat header, the accounts list and the profile screen. The always-visible
 * [AnimatedVisibility] supplies the [androidx.compose.animation.AnimatedVisibilityScope]
 * required by shared elements; screens are composed together during a nav
 * transition, so matching keys animate between them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedProfileElement(
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val sharedScope = LocalSharedTransitionScope.current
    val visibleState = remember { MutableTransitionState(true) }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val visibilityScope = this
        val sharedModifier = if (sharedScope == null) {
            Modifier
        } else {
            with(sharedScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key),
                    animatedVisibilityScope = visibilityScope
                )
            }
        }

        Box(modifier.then(sharedModifier)) {
            content()
        }
    }
}
