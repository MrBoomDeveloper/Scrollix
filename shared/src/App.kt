package com.mrboomdev.scrollix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.NotificationSettings
import com.mrboomdev.scrollix.data.provider.TelegramLinkTarget
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.explore_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.home_icon_filled
import com.mrboomdev.scrollix.ui.icons.menu_icon
import com.mrboomdev.scrollix.ui.icons.qr_code_scanner_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.components.MediaViewerOverlay
import com.mrboomdev.scrollix.ui.components.QrScannerDialog
import com.mrboomdev.scrollix.ui.components.StickerSetDialog
import com.mrboomdev.scrollix.ui.components.StickerSetTarget
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.settings_icon
import com.mrboomdev.scrollix.ui.theme.appTypography
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import com.mrboomdev.scrollix.ui.navigation.LocalSharedTransitionScope
import com.mrboomdev.scrollix.ui.navigation.Route
import com.mrboomdev.scrollix.ui.navigation.rememberAppEntryProvider
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/** Keeps the wide-layout chat pane selection across navigation and recreation. */
private val SelectedDialogSaver = listSaver<Route.Dialog?, Any>(
    save = { dialog ->
        if (dialog == null) {
            emptyList()
        } else {
            listOf(dialog.accountId, dialog.providerDialogId, dialog.messageId ?: -1L)
        }
    },
    restore = { saved ->
        if (saved.isEmpty()) {
            null
        } else {
            Route.Dialog(
                accountId = saved[0] as String,
                providerDialogId = saved[1] as String,
                messageId = (saved[2] as Long).takeIf { it >= 0 }
            )
        }
    }
)

private data class AppNavTab(
    val isActive: Boolean,
    val icon: ImageVector,
    val title: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalSerializationApi::class)
@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    val layoutInfo = currentAdaptiveLayoutInfo()
    val useRail = layoutInfo.size >= AdaptiveLayoutSize.EXPANDED
    
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclassesOfSealed<Route>()
                }
            }
        }, Route.Home
    )
    
    LaunchedEffect(Unit) {
        ApiManager.init()
    }
    
    // Saving this keeps the open chat when Settings or Search cover the home
    // pane, and across activity recreation.
    val selectedDialog = rememberSaveable(stateSaver = SelectedDialogSaver) {
        mutableStateOf<Route.Dialog?>(null)
    }
    var showQrScanner by remember { mutableStateOf(false) }
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val pendingDeepLink by DeepLinkHandler.pending.collectAsState()
    val currentRoute = backStack.lastOrNull()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    var stickerSetOverlay by remember {
        mutableStateOf<Pair<String, StickerSetTarget>?>(null)
    }

    // Desktop notifications for chats that receive a new message while their
    // conversation is not the one on screen. Only an increase of the unread
    // counter is announced, so re-emitting the dialog list stays silent.
    LaunchedEffect(Unit) {
        val seenUnread = mutableMapOf<String, Int>()
        val settingsCache = mutableMapOf<String, Pair<Long, NotificationSettings?>>()

        suspend fun notificationSettings(accountId: String): NotificationSettings? {
            val cached = settingsCache[accountId]
            val now = System.currentTimeMillis()

            if (cached != null && now - cached.first < 60_000L) return cached.second

            val settings = ApiManager.session(accountId)
                ?.loadNotificationSettings()
                ?.getOrNull()

            settingsCache[accountId] = now to settings
            return settings
        }

        ApiManager.dialogs.collect { entries ->
            entries.forEach { entry ->
                val dialog = entry.dialog
                val key = "${entry.account.id}:${dialog.providerDialogId}"
                val unread = dialog.unreadCount
                val previous = seenUnread.put(key, unread)

                if (previous == null || unread <= previous) return@forEach

                val open = selectedDialog.value
                val isOpen = open != null &&
                    open.accountId == entry.account.id &&
                    open.providerDialogId == dialog.providerDialogId

                if (isOpen) return@forEach

                // Server-side notification settings win: a muted scope or a
                // muted chat must not raise an OS notification.
                val scope = when (dialog.kind) {
                    DialogKind.Group -> NotificationScope.Groups
                    DialogKind.Channel -> NotificationScope.Channels
                    else -> NotificationScope.PrivateChats
                }

                if (notificationSettings(entry.account.id)?.isMuted(scope) == true) {
                    return@forEach
                }

                val session = ApiManager.session(entry.account.id)
                if (session?.isDialogMuted(dialog.providerDialogId) == true) {
                    return@forEach
                }

                showDesktopNotification(
                    title = dialog.title,
                    body = dialog.preview.ifBlank { "New message" }
                )
            }
        }
    }

    LaunchedEffect(
        pendingDeepLink?.id,
        accountState.accounts,
        accountState.activeAccountId,
        sessionStates
    ) {
        val request = pendingDeepLink ?: return@LaunchedEffect
        val accounts = accountState.accounts
        if (accounts.isEmpty()) return@LaunchedEffect

        // Prefer the active account, but try every ready session: shared code
        // must not hard-code the Telegram provider. MAX and other providers
        // simply fail the default link resolver.
        val ordered = buildList {
            val activeId = accountState.activeAccountId
            if (activeId != null) {
                accounts.firstOrNull { account -> account.id == activeId }?.let(::add)
            }
            accounts.forEach { account ->
                if (account.id != activeId) add(account)
            }
        }

        var resolvedAccountId: String? = null
        var resolvedTarget: TelegramLinkTarget? = null
        var lastError: String? = null

        for (account in ordered) {
            val session = ApiManager.session(account.id) ?: continue
            if (sessionStates[account.id] !is AuthState.Ready) continue

            session.resolveTelegramLinkTarget(request.url)
                .onSuccess { target ->
                    resolvedAccountId = account.id
                    resolvedTarget = target
                }
                .onFailure { failure ->
                    lastError = failure.message
                }

            if (resolvedTarget != null) break
        }

        if (resolvedTarget == null) {
            // Keep the link pending while authentication is still running.
            val anyReady = ordered.any { account ->
                sessionStates[account.id] is AuthState.Ready
            }
            if (!anyReady) return@LaunchedEffect

            DeepLinkHandler.consume()

            // The app could not handle the link; hand it to the system
            // browser instead of dropping it. The guard stops a loop when
            // Scrollix itself is the default handler for the same URL.
            val openedInBrowser = DeepLinkHandler.allowBrowserFallback(request.url) &&
                runCatching { uriHandler.openUri(request.url) }.isSuccess

            if (!openedInBrowser) {
                snackbarHostState.showSnackbar(
                    lastError ?: "This link could not be opened"
                )
            }

            return@LaunchedEffect
        }

        val target = resolvedTarget
        val accountId = resolvedAccountId ?: return@LaunchedEffect

        DeepLinkHandler.consume()

        when (target) {
            is TelegramLinkTarget.Dialog -> {
                selectedDialog.value = null
                backStack += Route.Dialog(
                    accountId = accountId,
                    providerDialogId = target.providerDialogId,
                    messageId = target.messageId
                )
            }

            is TelegramLinkTarget.StickerSet -> {
                stickerSetOverlay = accountId to StickerSetTarget.ByName(
                    name = target.name,
                    kind = target.kind
                )
            }
        }
    }
    
    val showNavigation = remember(currentRoute, layoutInfo.isTwoPane) {
        if(!layoutInfo.isTwoPane && currentRoute is Route.Dialog) {
            return@remember false
        }
        
        currentRoute !is Route.Login
    }
    
    val tabs = remember(currentRoute) {
        val isHomeTab = currentRoute is Route.Home || currentRoute is Route.Dialog
        
        listOf(
            AppNavTab(
                isActive = isHomeTab,
                icon = if(isHomeTab) home_icon_filled else home_icon,
                title = "Home",
                onClick = {
                    selectedDialog.value = null
                    
                    backStack.clearExcept {
                        it is Route.Home 
                    }
                }
            ),

            AppNavTab(
                isActive = false,
                icon = explore_icon,
                title = "Explore",
                onClick = {

                }
            ),

            AppNavTab(
                isActive = currentRoute is Route.Search,
                icon = search_icon,
                title = "Search",
                onClick = {
                    if (backStack.lastOrNull() != Route.Search()) {
                        backStack.add(Route.Search())
                    }
                }
            ),

            AppNavTab(
                isActive = currentRoute is Route.Accounts ||
                        currentRoute is Route.Login,
                icon = account_circle_icon,
                title = "Accounts",
                onClick = {
                    if (backStack.lastOrNull() != Route.Accounts) {
                        backStack.add(Route.Accounts)
                    }
                }
            )
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surfaceContainer = Color(0xff101010)
        ),
        typography = appTypography()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                Row {
                    AnimatedVisibility(showNavigation && useRail) {
                        Row {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.background,
                                header = {
                                    Box {
                                        var expand by remember { mutableStateOf(false) }

                                        IconButton(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            onClick = {
                                                expand = !expand
                                            }
                                        ) {
                                            Icon(
                                                tint = MaterialTheme.colorScheme.secondary,
                                                imageVector = menu_icon,
                                                contentDescription = null
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = expand,
                                            onDismissRequest = { expand = false },
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = settings_icon,
                                                        contentDescription = null
                                                    )
                                                },

                                                text = {
                                                    Text(
                                                        text = "Settings"
                                                    )
                                                },

                                                onClick = {
                                                    if(backStack.lastOrNull() != Route.Settings) {
                                                        backStack.add(Route.Settings)
                                                    }

                                                    expand = false
                                                }
                                            )

                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = qr_code_scanner_icon,
                                                        contentDescription = null
                                                    )
                                                },

                                                text = {
                                                    Text(
                                                        text = "Scan QR"
                                                    )
                                                },

                                                onClick = {
                                                    showQrScanner = true
                                                    expand = false
                                                }
                                            )
                                        }
                                    }
                                }
                            ) {
                                tabs.forEach { tab ->
                                    NavigationRailItem(
                                        selected = tab.isActive,
                                        onClick = tab.onClick,

                                        colors = NavigationRailItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primary,
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary
                                        ),

                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = null
                                            )
                                        },

                                        label = {
                                            Text(
                                                text = tab.title
                                            )
                                        }
                                    )
                                }
                            }

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainer
                            )
                        }
                    }

                    Column(Modifier.fillMaxSize()) {
                        SharedTransitionLayout(modifier = Modifier.weight(1f)) {
                            CompositionLocalProvider(
                                LocalSharedTransitionScope provides this
                            ) {
                        NavDisplay(
                            modifier = Modifier.fillMaxSize(),
                            backStack = backStack,

                            entryProvider = rememberAppEntryProvider(
                                backStack = backStack,
                                selectedDialog = selectedDialog,
                                onSelectDialog = { selectedDialog.value = it }
                            ),

                            transitionSpec = {
                                if(useRail) {
                                    return@NavDisplay ContentTransform(
                                        fadeIn(spring(stiffness = Spring.StiffnessHigh)),
                                        fadeOut(spring(stiffness = Spring.StiffnessHigh))
                                    ) 
                                }
                                
                                ContentTransform(
                                    fadeIn() + scaleIn(animationSpec = spring(stiffness = Spring.StiffnessHigh), initialScale = 1.1f) + slideIn(spring(stiffness = Spring.StiffnessHigh)) { IntOffset(it.width, 0) },
                                    fadeOut() + slideOut(spring(stiffness = Spring.StiffnessHigh)) { IntOffset(-it.width, 0) }
                                )
                            },

                            popTransitionSpec = {
                                if(useRail) {
                                    return@NavDisplay ContentTransform(
                                        fadeIn(spring(stiffness = Spring.StiffnessHigh)),
                                        fadeOut(spring(stiffness = Spring.StiffnessHigh))
                                    )
                                }
                                
                                ContentTransform(
                                    fadeIn() + slideIn(spring()) { IntOffset(-it.width, 0) } + scaleIn(initialScale = 0.95f),
                                    fadeOut() + slideOut(spring()) { IntOffset(it.width, 0) }
                                )
                            },

                            predictivePopTransitionSpec = {
                                if(useRail) {
                                    return@NavDisplay ContentTransform(
                                        fadeIn(spring(stiffness = Spring.StiffnessHigh)),
                                        fadeOut(spring(stiffness = Spring.StiffnessHigh))
                                    )
                                }
                                
                                ContentTransform(
                                    fadeIn() + slideIn(spring(stiffness = Spring.StiffnessHigh)) { IntOffset(-it.width, 0) } + scaleIn(initialScale = 0.95f),
                                    fadeOut() + slideOut(spring(stiffness = Spring.StiffnessHigh)) { IntOffset(it.width, 0) }
                                )
                            },

                            entryDecorators = listOf(
                                rememberViewModelStoreNavEntryDecorator(),
                                rememberSaveableStateHolderNavEntryDecorator()
                            )
                        )
                            }
                        }

                        AnimatedVisibility(showNavigation && !useRail) {
                            ShortNavigationBar(
                                containerColor = MaterialTheme.colorScheme.background
                            ) {
                                tabs.forEach { tab ->
                                    ShortNavigationBarItem(
                                        selected = tab.isActive,
                                        onClick = tab.onClick,
                                        
                                        iconPosition = if(layoutInfo.size >= AdaptiveLayoutSize.MEDIUM) {
                                            NavigationItemIconPosition.Start
                                        } else NavigationItemIconPosition.Top,

                                        colors = ShortNavigationBarItemDefaults.colors(
                                            selectedIndicatorColor = MaterialTheme.colorScheme.primary,
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedTextColor = if(layoutInfo.size >= AdaptiveLayoutSize.MEDIUM) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else MaterialTheme.colorScheme.primary,
//                                            selectedTextColorTopIconPosition = MaterialTheme.colorScheme.primary,
//                                            selectedTextColorStartIconPosition = MaterialTheme.colorScheme.onPrimary
                                        ),

                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = null
                                            )
                                        },

                                        label = {
                                            Text(
                                                text = tab.title
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                MediaViewerOverlay()

                stickerSetOverlay?.let { (accountId, target) ->
                    ApiManager.session(accountId)?.let { stickerSession ->
                        StickerSetDialog(
                            target = target,
                            session = stickerSession,
                            onDismiss = { stickerSetOverlay = null }
                        )
                    }
                }

                if (showQrScanner) {
                    val activeSession = remember(accountState.accounts) {
                        accountState.accounts.firstOrNull()?.let { account ->
                            ApiManager.session(account.id)
                        }
                    }

                    QrScannerDialog(
                        session = activeSession,
                        onDismiss = { showQrScanner = false }
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}