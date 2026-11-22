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
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.features.accounts.AccountsScreen
import com.mrboomdev.scrollix.features.dialog.DialogScreen
import com.mrboomdev.scrollix.features.home.HomeScreen
import com.mrboomdev.scrollix.features.login.LoginScreen
import com.mrboomdev.scrollix.features.profile.ProfileScreen
import com.mrboomdev.scrollix.features.search.SearchScreen
import com.mrboomdev.scrollix.features.settings.SettingsScreen
import com.mrboomdev.scrollix.isTwoPaneLayout
import kotlinx.coroutines.launch

@Composable
fun rememberAppEntryProvider(
    backStack: NavBackStack<NavKey>,
    selectedDialog: State<Route.Dialog?>,
    onSelectDialog: (Route.Dialog?) -> Unit
): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
        entry<Route.Home> {
            val isTwoPane = isTwoPaneLayout()
            val selected by selectedDialog

            if (isTwoPane) {
                WideDialogsScreen(
                    selectedDialog = selected,
                    onOpenDialog = onSelectDialog,
                    onCloseDialog = { onSelectDialog(null) },
                    onOpenSettingsScreen = {
                        backStack += Route.Settings
                    },
                    onOpenProfile = { dialog ->
                        backStack += Route.Profile(
                            accountId = dialog.accountId,
                            providerDialogId = dialog.providerDialogId
                        )
                    },
                    onOpenUserProfile = { dialog, userId ->
                        backStack += Route.Profile(
                            accountId = dialog.accountId,
                            providerDialogId = dialog.providerDialogId,
                            userId = userId
                        )
                    },
                    onOpenAccountsScreen = { backStack += Route.Accounts },
                    onOpenHashtag = { hashtag -> backStack += Route.Search(hashtag) }
                )
            } else {
                HomeScreen(
                    onOpenAccountsScreen = {
                        backStack += Route.Accounts
                    },
                    onOpenSettingsScreen = {
                        backStack += Route.Settings
                    },
                    onOpenDialog = { entry ->
                        onSelectDialog(null)
                        backStack += Route.Dialog(
                            accountId = entry.account.id,
                            providerDialogId = entry.dialog.providerDialogId
                        )
                    }
                )
            }
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

        entry<Route.Dialog> { route ->
            val isTwoPane = isTwoPaneLayout()
            val selected by selectedDialog

            if (isTwoPane) {
                LaunchedEffect(route) {
                    onSelectDialog(route)
                    backStack.remove(route)
                }

                WideDialogsScreen(
                    selectedDialog = selected ?: route,
                    onOpenDialog = onSelectDialog,
                    onCloseDialog = { onSelectDialog(null) },
                    onOpenSettingsScreen = {
                        backStack += Route.Settings
                    },
                    onOpenProfile = { dialog ->
                        backStack += Route.Profile(
                            accountId = dialog.accountId,
                            providerDialogId = dialog.providerDialogId
                        )
                    },
                    onOpenUserProfile = { dialog, userId ->
                        backStack += Route.Profile(
                            accountId = dialog.accountId,
                            providerDialogId = dialog.providerDialogId,
                            userId = userId
                        )
                    },
                    onOpenAccountsScreen = { backStack += Route.Accounts },
                    onOpenHashtag = { hashtag -> backStack += Route.Search(hashtag) }
                )
            } else {
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
                        backStack.removeLastOrNull()
                    }
                )
            }
        }

        entry<Route.Search> { route ->
            SearchScreen(
                initialQuery = route.query,
                onBack = {
                    backStack.removeLastOrNull()
                },
                onOpenDialog = { accountId, providerDialogId, messageId ->
                    backStack += Route.Dialog(
                        accountId = accountId,
                        providerDialogId = providerDialogId,
                        messageId = messageId
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
                onOpenAccountsScreen = { backStack += Route.Accounts }
            )
        }

        entry<Route.Profile> { route ->
            val isTwoPane = isTwoPaneLayout()

            ProfileScreen(
                accountId = route.accountId,
                providerDialogId = route.providerDialogId,
                userId = route.userId,
                onMessage = { targetDialogId ->
                    val target = Route.Dialog(
                        accountId = route.accountId,
                        providerDialogId = targetDialogId
                    )

                    // The profile is a full screen entry, so it has to be
                    // dismissed for the chat to become visible.
                    backStack.removeLastOrNull()

                    if (isTwoPane) {
                        // On wide layouts the chat lives in the home pane;
                        // drop whatever covered it (search, profile, ...).
                        onSelectDialog(target)
                        backStack.clear()
                        backStack.add(Route.Home)
                    } else if (backStack.lastOrNull() != target) {
                        backStack += target
                    }
                },
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
                onOpenChannelProfile = { channelId ->
                    backStack += Route.Profile(
                        accountId = route.accountId,
                        providerDialogId = channelId
                    )
                },
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

@Composable
private fun WideDialogsScreen(
    selectedDialog: Route.Dialog?,
    onOpenDialog: (Route.Dialog) -> Unit,
    onCloseDialog: () -> Unit,
    onOpenProfile: (Route.Dialog) -> Unit,
    onOpenUserProfile: (Route.Dialog, Long) -> Unit,
    onOpenAccountsScreen: () -> Unit,
    onOpenSettingsScreen: () -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.38f).coerceIn(300.dp, 400.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
            ) {
                HomeScreen(
                    onOpenAccountsScreen = onOpenAccountsScreen,
                    onOpenDialog = { entry ->
                        onOpenDialog(
                            Route.Dialog(
                                accountId = entry.account.id,
                                providerDialogId = entry.dialog.providerDialogId
                            )
                        )
                    },
                    onOpenSettingsScreen = onOpenSettingsScreen,
                    selectedDialogAccountId = selectedDialog?.accountId,
                    selectedDialogProviderDialogId = selectedDialog?.providerDialogId
                )
            }

            VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (selectedDialog != null) {
                    key(
                        selectedDialog.accountId,
                        selectedDialog.providerDialogId,
                        selectedDialog.messageId
                    ) {
                        DialogScreen(
                            accountId = selectedDialog.accountId,
                            providerDialogId = selectedDialog.providerDialogId,
                            initialMessageId = selectedDialog.messageId,
                            onOpenProfile = { onOpenProfile(selectedDialog) },
                            onOpenUserProfile = { userId ->
                                onOpenUserProfile(selectedDialog, userId)
                            },
                            onOpenHashtag = onOpenHashtag,
                            onBack = onCloseDialog
                        )
                    }
                } else {
                    NoDialogSelected()
                }
            }
        }
    }
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
