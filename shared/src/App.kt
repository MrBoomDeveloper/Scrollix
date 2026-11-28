package com.mrboomdev.scrollix

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.NotificationSettings
import com.mrboomdev.scrollix.data.provider.TelegramLinkTarget
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.features.dialog.DialogScreen
import com.mrboomdev.scrollix.features.dialog.bots.WebAppLinkDialog
import com.mrboomdev.scrollix.features.explore.ExploreScreen
import com.mrboomdev.scrollix.features.home.adjacentDialogEntry
import com.mrboomdev.scrollix.features.home.firstUnreadEntry
import com.mrboomdev.scrollix.features.home.key
import com.mrboomdev.scrollix.features.settings.icon
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.components.AppOverflowMenu
import com.mrboomdev.scrollix.ui.components.ConfigureCoilImageLoader
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.MediaViewerOverlay
import com.mrboomdev.scrollix.ui.components.QrScannerDialog
import com.mrboomdev.scrollix.ui.components.ShortcutCheatSheetDialog
import com.mrboomdev.scrollix.ui.components.StickerSetDialog
import com.mrboomdev.scrollix.ui.components.StickerSetTarget
import com.mrboomdev.scrollix.ui.components.appMenuItems
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.explore_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.home_icon_filled
import com.mrboomdev.scrollix.ui.icons.menu_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.navigation.Route
import com.mrboomdev.scrollix.platform.PlatformBackHandler
import com.mrboomdev.scrollix.ui.navigation.rememberAppEntryProvider
import com.mrboomdev.scrollix.ui.notice.AppNotice
import com.mrboomdev.scrollix.ui.notice.AppNoticeHost
import com.mrboomdev.scrollix.ui.notice.NoticeCenter
import com.mrboomdev.scrollix.ui.rememberMotionTokens
import com.mrboomdev.scrollix.ui.theme.appTypography

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
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
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import org.jetbrains.compose.reload.DevelopmentEntryPoint
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

/** Each bottom-bar tab owns a back stack that survives switching tabs. */
private enum class AppTab { Home, Explore, Search, Accounts }

@OptIn(ExperimentalSerializationApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    ConfigureCoilImageLoader()

    val layoutInfo = currentAdaptiveLayoutInfo()
    val useRail = layoutInfo.size >= AdaptiveLayoutSize.EXPANDED
    
    val savedStateConfiguration = remember {
        SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclassesOfSealed<Route>()
                }
            }
        }
    }

    val homeStack = rememberNavBackStack(savedStateConfiguration, Route.Home)
    val exploreStack = rememberNavBackStack(savedStateConfiguration, Route.Explore)
    val searchStack = rememberNavBackStack(savedStateConfiguration, Route.Search())
    val accountsStack = rememberNavBackStack(savedStateConfiguration, Route.Accounts)
    var currentTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    val backStack = when (currentTab) {
        AppTab.Home -> homeStack
        AppTab.Explore -> exploreStack
        AppTab.Search -> searchStack
        AppTab.Accounts -> accountsStack
    }
    
    LaunchedEffect(Unit) {
        ApiManager.init()
    }
    
    // Saving this keeps the open chat when Settings or Search cover the home
    // pane, and across activity recreation.
    val selectedDialog = rememberSaveable(stateSaver = SelectedDialogSaver) {
        mutableStateOf<Route.Dialog?>(null)
    }
    var showQrScanner by remember { mutableStateOf(false) }
    var showShortcuts by remember { mutableStateOf(false) }
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val pendingDeepLink by DeepLinkHandler.pending.collectAsState()
    val pendingChatOpen by ChatOpenHandler.pending.collectAsState()
    val backRequests by BackNavigation.requests.collectAsState()
    val shortcutRequests by KeyboardShortcuts.requests.collectAsState()
    val isRecording by DialogRecordingState.isRecording.collectAsState()
    val currentRoute = backStack.lastOrNull()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val motion = rememberMotionTokens()
    val settings by AppSettingsStore.state.collectAsState()
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    var stickerSetOverlay by remember {
        mutableStateOf<Pair<String, StickerSetTarget>?>(null)
    }

    // A mini app opened from a t.me link instead of a chat button.
    var webAppOverlay by remember {
        mutableStateOf<Pair<String, TelegramLinkTarget.WebApp>?>(null)
    }

    // First run: nothing is blocked. The onboarding route is only pushed when
    // there is no account and the user has not dismissed it yet; existing
    // installs already have accounts, so they never see it.
    val initialDialogsReady by ApiManager.initialDialogsReady.collectAsState()

    LaunchedEffect(
        initialDialogsReady,
        accountState.accounts,
        settings.onboardingCompleted,
        currentRoute
    ) {
        if (!initialDialogsReady) return@LaunchedEffect
        if (settings.onboardingCompleted) return@LaunchedEffect
        if (accountState.accounts.isNotEmpty()) return@LaunchedEffect
        if (currentRoute is Route.Login || currentRoute is Route.Onboarding) {
            return@LaunchedEffect
        }

        currentTab = AppTab.Home

        if (homeStack.none { route -> route is Route.Onboarding }) {
            homeStack += Route.Onboarding
        }
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

                // Saved Messages only ever receive messages the user sent, so
                // they never warrant a notification.
                if (dialog.isSavedMessages) return@forEach

                val open = (backStack.lastOrNull() as? Route.Dialog)
                    ?: selectedDialog.value
                val isOpen = open != null &&
                    open.accountId == entry.account.id &&
                    open.providerDialogId == dialog.providerDialogId

                // Keep the open chat silent only while its conversation is
                // actually visible; in the background every chat notifies.
                if (isOpen && isAppInForeground()) return@forEach

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
                    body = dialog.preview.ifBlank { "New message" },
                    accountId = entry.account.id,
                    providerDialogId = dialog.providerDialogId
                )
            }
        }
    }

    LaunchedEffect(pendingChatOpen, accountState.accounts) {
        val request = pendingChatOpen ?: return@LaunchedEffect

        // Accounts load asynchronously; keep the request until its account is
        // known instead of navigating to a chat that has no session yet.
        if (accountState.accounts.none { it.id == request.accountId }) {
            return@LaunchedEffect
        }

        ChatOpenHandler.consume()
        selectedDialog.value = null
        currentTab = AppTab.Home
        homeStack += Route.Dialog(
            accountId = request.accountId,
            providerDialogId = request.providerDialogId
        )
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
                AppLog.warning(
                    "deeplink",
                    "Could not open ${request.url}: ${lastError ?: "unknown error"}"
                )

                NoticeCenter.error(lastError ?: "This link could not be opened")
            }

            return@LaunchedEffect
        }

        val target = resolvedTarget
        val accountId = resolvedAccountId ?: return@LaunchedEffect

        DeepLinkHandler.consume()

        when (target) {
            is TelegramLinkTarget.Dialog -> {
                selectedDialog.value = null
                currentTab = AppTab.Home
                homeStack += Route.Dialog(
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

            is TelegramLinkTarget.WebApp -> {
                webAppOverlay = accountId to target
            }

            is TelegramLinkTarget.ChatFolder -> {
                NoticeCenter.post(
                    AppNotice(message = "Folder \"${target.title}\" was added")
                )
            }
        }
    }
    
    // Desktop Escape (published by the entry point) behaves like a back
    // press: overlays close first, then the navigation stack pops.
    LaunchedEffect(backRequests, isRecording) {
        if (backRequests == 0L) return@LaunchedEffect

        // A recording must not be abandoned through the navigation stack;
        // DialogScreen consumes the same back press on mobile.
        if (isRecording) return@LaunchedEffect

        when {
            showShortcuts -> showShortcuts = false
            MediaViewer.isShowing -> MediaViewer.close()
            webAppOverlay != null -> webAppOverlay = null
            stickerSetOverlay != null -> stickerSetOverlay = null
            showQrScanner -> showQrScanner = false

            // Back on the first-run screen means "not now": the flag is set
            // so the screen does not immediately reappear on the next frame.
            backStack.lastOrNull() == Route.Onboarding -> {
                AppSettingsStore.update { settings ->
                    settings.copy(onboardingCompleted = true)
                }

                backStack.removeLastOrNull()
            }

            backStack.size > 1 -> backStack.removeAt(backStack.lastIndex)
            selectedDialog.value != null -> selectedDialog.value = null
            currentTab != AppTab.Home -> currentTab = AppTab.Home
        }
    }

    // Desktop keyboard commands. Login owns the whole window, so shortcuts must
    // not stack a route on top of it, and a recording owns the back gesture too.
    LaunchedEffect(shortcutRequests, isRecording, currentRoute) {
        val request = shortcutRequests ?: return@LaunchedEffect
        if (isRecording) return@LaunchedEffect
        if (currentRoute is Route.Login) return@LaunchedEffect

        // The cheat sheet is modal; navigation keys must not act behind it.
        if (showShortcuts) return@LaunchedEffect

        KeyboardShortcuts.consume()

        when (request.shortcut) {
            AppShortcut.Search -> {
                currentTab = AppTab.Search

                if (searchStack.lastOrNull() != Route.Search()) {
                    searchStack.add(Route.Search())
                }
            }

            AppShortcut.Settings -> {
                if (backStack.lastOrNull() != Route.Settings) {
                    backStack.add(Route.Settings)
                }
            }

            AppShortcut.ShortcutCheatSheet -> showShortcuts = true

            AppShortcut.NextChat,
            AppShortcut.PreviousChat,
            AppShortcut.JumpToUnread -> {
                val entries = ApiManager.dialogs.value
                val current = backStack.lastOrNull() as? Route.Dialog
                    ?: selectedDialog.value

                // The aggregated list is already sorted the way the chat list
                // is displayed, so the keyboard moves through what the user sees.
                val target = when (request.shortcut) {
                    AppShortcut.JumpToUnread -> firstUnreadEntry(entries)

                    else -> adjacentDialogEntry(
                        entries = entries,
                        currentAccountId = current?.accountId,
                        currentProviderDialogId = current?.providerDialogId,
                        step = if (request.shortcut == AppShortcut.NextChat) 1 else -1
                    )
                }

                if (target == null) {
                    if (request.shortcut == AppShortcut.JumpToUnread) {
                        NoticeCenter.post(
                            com.mrboomdev.scrollix.ui.notice.AppNotice("No unread chats")
                        )
                    }
                } else {
                    val route = Route.Dialog(
                        accountId = target.account.id,
                        providerDialogId = target.dialog.providerDialogId
                    )

                    // Replace the open conversation instead of stacking one
                    // history entry per key press.
                    if (homeStack.lastOrNull() is Route.Dialog) {
                        homeStack.removeLastOrNull()
                    }

                    selectedDialog.value = null
                    currentTab = AppTab.Home
                    homeStack.add(route)
                }
            }
        }
    }

    val showNavigation = remember(currentRoute, layoutInfo.isTwoPane) {
        if(!layoutInfo.isTwoPane && currentRoute is Route.Dialog) {
            return@remember false
        }
        
        currentRoute !is Route.Login
    }
    
    val tabs = remember(currentTab) {
        listOf(
            AppNavTab(
                isActive = currentTab == AppTab.Home,
                icon = if(currentTab == AppTab.Home) home_icon_filled else home_icon,
                title = "Home",
                onClick = {
                    if (!isRecording) currentTab = AppTab.Home
                }
            ),

            AppNavTab(
                isActive = currentTab == AppTab.Explore,
                icon = explore_icon,
                title = "Explore",
                onClick = {
                    if (!isRecording) currentTab = AppTab.Explore
                }
            ),

            AppNavTab(
                isActive = currentTab == AppTab.Search,
                icon = search_icon,
                title = "Search",
                onClick = {
                    if (!isRecording) currentTab = AppTab.Search
                }
            ),

            AppNavTab(
                isActive = currentTab == AppTab.Accounts,
                icon = account_circle_icon,
                title = "Accounts",
                onClick = {
                    if (!isRecording) currentTab = AppTab.Accounts
                }
            )
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xff101010),
            secondaryContainer = Color(0xFF3A1E5A)
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
                                                contentDescription = "Main menu"
                                            )
                                        }

                                        AppOverflowMenu(
                                            expanded = expand,
                                            onDismissRequest = { expand = false },
                                            items = appMenuItems(
                                                onOpenDrafts = {
                                                    if (backStack.lastOrNull() != Route.Drafts) {
                                                        backStack.add(Route.Drafts)
                                                    }
                                                },
                                                onOpenSettings = {
                                                    if (backStack.lastOrNull() != Route.Settings) {
                                                        backStack.add(Route.Settings)
                                                    }
                                                },
                                                onScanQr = { showQrScanner = true },
                                                onOpenNotifications = {
                                                    backStack.add(Route.Notifications)
                                                },
                                                onOpenStorage = {
                                                    backStack.add(Route.StorageCenter)
                                                }
                                            )
                                        )
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
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedTextColor = MaterialTheme.colorScheme.secondary,
                                            unselectedIconColor = MaterialTheme.colorScheme.secondary
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
                        // Android back on a tab root returns to Home instead of
                        // leaving the app; NavDisplay owns deeper pops.
                        PlatformBackHandler(
                            enabled = backStack.size == 1 &&
                                !isRecording &&
                                (currentTab != AppTab.Home || selectedDialog.value != null)
                        ) {
                            if (selectedDialog.value != null) {
                                selectedDialog.value = null
                            } else {
                                currentTab = AppTab.Home
                            }
                        }

                        NavDisplay(
                            modifier = Modifier.weight(1f),
                            backStack = backStack,
                            sceneStrategies = listOf(listDetailStrategy),

                            entryProvider = rememberAppEntryProvider(
                                backStack = backStack,
                                selectedDialog = selectedDialog,
                                onSelectDialog = {
                                    if (!DialogRecordingState.isActive) {
                                        selectedDialog.value = it
                                    }
                                },
                                onScanQr = { showQrScanner = true }
                            ),

                            transitionSpec = {
                                if (useRail || motion.reduceMotion) {
                                    return@NavDisplay ContentTransform(
                                        fadeIn(motion.fade),
                                        fadeOut(motion.fade)
                                    )
                                }

                                ContentTransform(
                                    fadeIn() + scaleIn(animationSpec = spring(stiffness = Spring.StiffnessHigh), initialScale = 1.1f) + slideIn(motion.spatial) { IntOffset(it.width, 0) },
                                    fadeOut() + slideOut(motion.spatial) { IntOffset(-it.width, 0) }
                                )
                            },

                            popTransitionSpec = {
                                if (useRail || motion.reduceMotion) {
                                    return@NavDisplay ContentTransform(
                                        fadeIn(motion.fade),
                                        fadeOut(motion.fade)
                                    )
                                }

                                ContentTransform(
                                    fadeIn() + slideIn(motion.gentleSpatial) { IntOffset(-it.width, 0) } + scaleIn(initialScale = 0.95f),
                                    fadeOut() + slideOut(motion.gentleSpatial) { IntOffset(it.width, 0) }
                                )
                            },

                            predictivePopTransitionSpec = {
                                if (useRail || motion.reduceMotion) {
                                    return@NavDisplay ContentTransform(
                                        fadeIn(motion.fade),
                                        fadeOut(motion.fade)
                                    )
                                }

                                ContentTransform(
                                    fadeIn() + slideIn(motion.spatial) { IntOffset(-it.width, 0) } + scaleIn(initialScale = 0.95f),
                                    fadeOut() + slideOut(motion.spatial) { IntOffset(it.width, 0) }
                                )
                            },

                            entryDecorators = listOf(
                                rememberViewModelStoreNavEntryDecorator(),
                                rememberSaveableStateHolderNavEntryDecorator()
                            )
                        )

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
                                            unselectedTextColor = MaterialTheme.colorScheme.secondary,
                                            unselectedIconColor = MaterialTheme.colorScheme.secondary,
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
                                                text = tab.title,
                                                fontWeight = if(layoutInfo.size >= AdaptiveLayoutSize.MEDIUM && tab.isActive) {
                                                    FontWeight.SemiBold
                                                } else null
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

                webAppOverlay?.let { (accountId, target) ->
                    WebAppLinkDialog(
                        session = ApiManager.session(accountId),
                        title = target.title,
                        url = target.url,
                        onDismiss = { webAppOverlay = null }
                    )
                }

                if (showQrScanner) {
                    // Only accounts that can actually approve a QR login are
                    // offered; a MAX session used to be picked first and the
                    // scan failed there even though a Telegram account existed.
                    val qrSessions = remember(accountState.accounts) {
                        accountState.accounts
                            .mapNotNull { account -> ApiManager.session(account.id) }
                            .filter { session -> session.supportsQrLogin }
                    }

                    QrScannerDialog(
                        sessions = qrSessions,
                        onDismiss = { showQrScanner = false }
                    )
                }

                if (showShortcuts) {
                    ShortcutCheatSheetDialog(onDismiss = { showShortcuts = false })
                }

                AppNoticeHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
