package com.mrboomdev.scrollix

import com.mrboomdev.scrollix.features.home.key

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform back requests that are not delivered by the navigation stack
 * itself, such as the desktop Escape key. [App] consumes them by closing the
 * topmost overlay or popping a route.
 */
object BackNavigation {
    private val _requests = MutableStateFlow(0L)
    val requests: StateFlow<Long> = _requests.asStateFlow()

    fun requestBack() {
        _requests.value += 1
    }
}
