package com.mrboomdev.scrollix.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route: NavKey {
    @Serializable
    data object Home : Route
    
    @Serializable
    data object Login: Route
}