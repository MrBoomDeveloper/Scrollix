package com.mrboomdev.scrollix.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.features.accounts.AccountsScreen
import com.mrboomdev.scrollix.features.dialog.DialogScreen
import com.mrboomdev.scrollix.features.diagnostics.DiagnosticsScreen
import com.mrboomdev.scrollix.features.home.HomeScreen
import com.mrboomdev.scrollix.features.login.LoginScreen
import com.mrboomdev.scrollix.features.profile.ProfileScreen
import com.mrboomdev.scrollix.features.search.SearchScreen
import com.mrboomdev.scrollix.features.settings.SettingsScreen
import com.mrboomdev.scrollix.isTwoPaneLayout
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberAppEntryProvider(
    backStack: NavBackStack<NavKey>,
    selectedDialog: State<Route.Dialog?>,
    onSelectDialog: (Route.Dialog?) -> Unit
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        // The home entry is the list pane of the Navigation 3 list/detail
        // scene. On a wide window the Dialog entry below becomes the detail
        // pane; on a phone the same entry fills the screen. The strategy
        // itself decides whether a scene is possible, so there is no custom
        // wide-layout branching left here.
        entry<Route.Home>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = { NoDialogSelected() }
            )
        ) {
            val selected = backStack.lastOrNull() as? Route.Dialog

            HomeScreen(
                onOpenAccountsScreen = {
                    backStack += Route.Accounts
                },
                onOpenSettingsScreen = {
                    backStack += Route.Settings
                },
                onOpenDialog = { entry ->
                    backStack.openDialog(
                        Route.Dialog(
                            accountId = entry.account.id,
                            providerDialogId = entry.dialog.providerDialogId
                        )
                    )
                },
                selectedDialogAccountId = selected?.accountId,
                selectedDialogProviderDialogId = selected?.providerDialogId
            )
        }

        entry<Route.Accounts> {
            AccountsScreen(
                onBack = {
                    backStack.removeLastOrNull()
                },
                onOpenLogin = { accountId ->
                    backStack += Route.Login(accountId)
                },
                onOpenOwnProfile = { accountId ->
                    backStack += Route.OwnProfile(accountId)
                }
            )
        }

        entry<Route.OwnProfile> { route ->
            ProfileScreen(
                accountId = route.accountId,
                providerDialogId = "",
                ownAccount = true,
                onMessage = {},
                onBack = {
                    backStack.removeLastOrNull()
                }
            )
        }

        entry<Route.Dialog>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) { route ->
            DialogScreen(
                accountId = route.accountId,
                providerDialogId = route.providerDialogId,
                initialMessageId = route.messageId,
                onOpenProfile = {
                    backStack += Route.Profile(
                        accountId = route.accountId,
                        providerDialogId = route.providerDialogId
                    )
                },
                onOpenUserProfile = { userId ->
                    backStack += Route.Profile(
                        accountId = route.accountId,
                        providerDialogId = route.providerDialogId,
                        userId = userId
                    )
                },
                onOpenHashtag = { hashtag -> backStack += Route.Search(hashtag) },
                onBack = {
                    if (!DialogRecordingState.isActive) {
                        backStack.removeLastOrNull()
                    }
                }
            )
        }

        entry<Route.Search> { route ->
            val isTwoPane = isTwoPaneLayout()
            val currentSelection by selectedDialog

            SearchScreen(
                initialQuery = route.query,
                onBack = {
                    backStack.removeLastOrNull()
                },
                onOpenDialog = { accountId, providerDialogId, messageId ->
                    val target = Route.Dialog(
                        accountId = accountId,
                        providerDialogId = providerDialogId,
                        messageId = messageId
                    )

                    // On a wide layout Search already shows its own chat pane;
                    // keep the Search tab open and update the shared selection
                    // instead of stacking a Dialog route over it.
                    if (isTwoPane) {
                        onSelectDialog(target)
                    } else {
                        backStack += target
                    }
                },
                selectedDialogAccountId = currentSelection?.accountId,
                selectedDialogProviderDialogId = currentSelection?.providerDialogId,
                selectedDialogMessageId = currentSelection?.messageId,
                onSelectDialog = { accountId, providerDialogId, messageId ->
                    onSelectDialog(
                        Route.Dialog(
                            accountId = accountId,
                            providerDialogId = providerDialogId,
                            messageId = messageId
                        )
                    )
                },
                onOpenProfile = { accountId, providerDialogId ->
                    backStack += Route.Profile(
                        accountId = accountId,
                        providerDialogId = providerDialogId
                    )
                },
                onOpenUserProfile = { accountId, providerDialogId, userId ->
                    backStack += Route.Profile(
                        accountId = accountId,
                        providerDialogId = providerDialogId,
                        userId = userId
                    )
                }
            )
        }

        entry<Route.Settings> {
            SettingsScreen(
                onOpenDiagnostics = { backStack += Route.Diagnostics }
            )
        }

        entry<Route.Diagnostics> {
            DiagnosticsScreen(
                onBack = {
                    backStack.removeLastOrNull()
                }
            )
        }

        entry<Route.Profile> { route ->
            val isTwoPane = isTwoPaneLayout()

            // Opening a chat from a profile – the message button, a personal
            // channel or a group in common – lands in the chat itself, so the
            // profile of that chat is never pushed on top of itself.
            val openChat: (String) -> Unit = { targetDialogId ->
                val target = Route.Dialog(
                    accountId = route.accountId,
                    providerDialogId = targetDialogId
                )

                backStack.removeLastOrNull()

                if (isTwoPane) {
                    backStack.clear()
                    backStack.add(Route.Home)
                    backStack.add(target)
                } else if (backStack.lastOrNull() != target) {
                    backStack += target
                }
            }

            ProfileScreen(
                accountId = route.accountId,
                providerDialogId = route.providerDialogId,
                userId = route.userId,
                onMessage = openChat,
                onOpenMessage = { messageId ->
                    val target = Route.Dialog(
                        accountId = route.accountId,
                        providerDialogId = route.providerDialogId,
                        messageId = messageId
                    )

                    backStack.removeLastOrNull()

                    if (isTwoPane) {
                        onSelectDialog(target)
                        backStack.clear()
                        backStack.add(Route.Home)
                    } else if (backStack.lastOrNull() != target) {
                        backStack += target
                    }
                },
                onOpenChat = openChat,
                onBack = {
                    backStack.removeLastOrNull()
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

private fun androidx.navigation3.runtime.NavBackStack<NavKey>.openDialog(
    route: Route.Dialog
) {
    val current = lastOrNull()

    if (current is Route.Dialog) {
        if (
            current.accountId == route.accountId &&
            current.providerDialogId == route.providerDialogId &&
            current.messageId == route.messageId
        ) {
            return
        }

        // Keep one detail entry in the back stack; selecting another chat
        // swaps the detail pane instead of stacking histories.
        removeLastOrNull()
    }

    add(route)
}

@Composable
private fun NoDialogSelected() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "Select a dialog",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Choose a chat from the list to start messaging.",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
