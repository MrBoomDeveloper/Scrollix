package com.mrboomdev.scrollix.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.features.accounts.AccountsScreen
import com.mrboomdev.scrollix.features.home.HomeScreen
import com.mrboomdev.scrollix.features.login.LoginScreen
import kotlinx.coroutines.launch

@Composable
fun rememberAppEntryProvider(
    backStack: NavBackStack<NavKey>
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        entry<Route.Home> {
            HomeScreen(
                onOpenAccountsScreen = {
                    backStack += Route.Accounts
                }
            )
        }

        entry<Route.Accounts> {
            AccountsScreen(
                onBack = {
                    backStack.removeLastOrNull()
                },
                onOpenLogin = { accountId ->
                    backStack += Route.Login(accountId)
                }
            )
        }

        entry<Route.Login> { route ->
            val scope = rememberCoroutineScope()

            LoginScreen(
                accountId = route.accountId,
                onBack = {
                    scope.launch {
                        ApiManager.cancelPendingAccount(route.accountId)
                        backStack.remove(route)
                    }
                },
                onDone = {
                    backStack.remove(route)
                }
            )
        }
    }
}
