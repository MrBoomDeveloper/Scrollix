package com.mrboomdev.scrollix.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.mrboomdev.scrollix.features.home.HomeScreen
import com.mrboomdev.scrollix.features.login.LoginScreen

@Composable
fun rememberAppEntryProvider(
    backStack: NavBackStack<NavKey>
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        entry<Route.Home> {
            HomeScreen(
                onOpenLoginScreen = {
                    backStack += Route.Login
                }
            )
        }

        entry<Route.Login> {
            LoginScreen(
                onBack = {
                    backStack.removeLastOrNull()
                }
            )
        }
    }
}