package com.mrboomdev.scrollix.features.home

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.components.AppMenuItem
import com.mrboomdev.scrollix.ui.components.AppOverflowMenu
import com.mrboomdev.scrollix.ui.components.ReorderDialog
import com.mrboomdev.scrollix.ui.components.ReorderItem
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.brand_awareness_icon
import com.mrboomdev.scrollix.ui.components.appMenuItems
import com.mrboomdev.scrollix.ui.icons.done_all_icon
import com.mrboomdev.scrollix.ui.icons.group_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.keep_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.smart_toy_icon

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalFlexBoxApi
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrboomdev.scrollix.ui.icons.scrollix_icon

internal const val TAB_ALL = 0
internal const val TAB_DIRECT = 1
internal const val TAB_GROUPS = 2
internal const val TAB_CHANNELS = 3
internal const val TAB_BOTS = 4

@OptIn(ExperimentalFlexBoxApi::class)
@Composable
internal fun HomeScreen(
    onOpenAccountsScreen: () -> Unit,
    onOpenSettingsScreen: () -> Unit,
    onOpenDraftsScreen: () -> Unit,
    onScanQr: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenStorageCenter: () -> Unit = {},
    onOpenDialog: (AccountDialogEntry) -> Unit,
    selectedDialogAccountId: String? = null,
    selectedDialogProviderDialogId: String? = null,
    viewModel: HomeScreenViewModel = viewModel { HomeScreenViewModel() }
) {
    val layoutInfo = currentAdaptiveLayoutInfo()
    val settings by AppSettingsStore.state.collectAsState()
    val isAllShown = settings.showAllChatsTab
    val isSingleRowFilters = settings.singleRowFilters
    val isMergeDuplicateDialogs = settings.mergeDuplicateDialogs

    // Chip geometry backs "scroll the selected tab into view".
    val tabRowScroll = rememberScrollState()
    var tabRowWidth by remember { mutableIntStateOf(0) }
    var tabRowOrigin by remember { mutableStateOf(Offset.Zero) }
    val chipBounds = remember { mutableStateMapOf<String, Rect>() }

    LaunchedEffect(settings.defaultHomeTab, isAllShown) {
        viewModel.applyDefaultTab(settings)
    }

    val selectedTab = viewModel.selectedTab
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val loadingDialogs by ApiManager.dialogsLoading.collectAsState()
    val entries by ApiManager.dialogs.collectAsState()
    val sessions by ApiManager.sessions.collectAsState()
    val canMarkAllRead = remember(sessions) {
        sessions.values.any { session -> session.supportsMarkAllRead }
    }

    // Every chip gets a swipeable page, so the list can be switched by
    // dragging it horizontally as well as by tapping a chip.
    val folderPages = remember(isAllShown) {
        buildList {
            if (isAllShown) add(HomePage.All)

            add(HomePage.Tab(TAB_DIRECT))
            add(HomePage.Tab(TAB_GROUPS))
            add(HomePage.Tab(TAB_CHANNELS))
            add(HomePage.Tab(TAB_BOTS))
        }
    }

    val selectedFolderPage = remember(folderPages, selectedTab) {
        folderPages.indexOfFirst { page ->
            when (page) {
                HomePage.All -> selectedTab == TAB_ALL
                is HomePage.Tab -> selectedTab == page.index
            }
        }.coerceAtLeast(0)
    }

    val folderPagerState = rememberPagerState(initialPage = selectedFolderPage) {
        folderPages.size
    }

    LaunchedEffect(selectedFolderPage, folderPages.size) {
        if (folderPagerState.currentPage != selectedFolderPage) {
            folderPagerState.animateScrollToPage(selectedFolderPage)
        }
    }

    LaunchedEffect(folderPagerState.settledPage, folderPages) {
        when (val page = folderPages.getOrNull(folderPagerState.settledPage)) {
            null -> Unit
            HomePage.All -> viewModel.selectAll()
            is HomePage.Tab -> viewModel.selectTab(page.index)
        }
    }

    val selectedChipKey = "tab:$selectedTab"

    // A swipe can land on a tab whose chip sits past the right edge of the
    // row. Bring the chip of the visible page on screen.
    LaunchedEffect(selectedChipKey, isSingleRowFilters, tabRowWidth, chipBounds.size) {
        if (!isSingleRowFilters) return@LaunchedEffect

        val bounds = chipBounds[selectedChipKey] ?: return@LaunchedEffect
        val viewport = tabRowWidth
        if (viewport <= 0) return@LaunchedEffect

        val start = bounds.left.toInt()
        val end = bounds.right.toInt()
        val current = tabRowScroll.value

        when {
            start < current -> tabRowScroll.animateScrollTo(start)
            end > current + viewport -> tabRowScroll.animateScrollTo(end - viewport)
        }
    }

    val unreadCounts = remember(entries) { homeUnreadCounts(entries) }

    val isConnecting = accountState.accounts.any { account ->
        val state = sessionStates[account.id]
        state == null || state is AuthState.Initializing
    }
    // Stay in the loading state until a dialog list actually exists. A failed
    // connection used to flip the spinner off and show "no chats", which
    // looked like an empty account while TDLib was still reconnecting.
    val isDialogsLoading = isConnecting ||
        loadingDialogs.isNotEmpty() ||
        (entries.isEmpty() && accountState.accounts.isNotEmpty())

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.zIndex(1f),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp, 8.dp, 8.dp, 0.dp)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                        imageVector = scrollix_icon,
                        contentDescription = null
                    )

                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = "Scrollix",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.weight(1f))

                    Box {
                        var expand by remember { mutableStateOf(false) }

                        IconButton(
                            onClick = {
                                expand = !expand
                            }
                        ) {
                            Icon(
                                tint = MaterialTheme.colorScheme.secondary,
                                imageVector = more_vertical_icon,
                                contentDescription = "More options"
                            )
                        }

                        AppOverflowMenu(
                            expanded = expand,
                            onDismissRequest = { expand = false },
                            items = appMenuItems(
                                onOpenDrafts = onOpenDraftsScreen,
                                onOpenSettings = onOpenSettingsScreen,
                                onScanQr = onScanQr,
                                onOpenNotifications = onOpenNotifications,
                                onOpenStorage = onOpenStorageCenter,
                                extra = buildList {
                                    add(
                                        AppMenuItem(
                                            title = "Reorder pinned chats",
                                            icon = keep_icon,
                                            onClick = { viewModel.openPinnedOrder() }
                                        )
                                    )

                                    // Only offered while a provider can clear
                                    // the counters in bulk, not chat by chat.
                                    if (canMarkAllRead) {
                                        add(
                                            AppMenuItem(
                                                title = "Mark all as read",
                                                icon = done_all_icon,
                                                onClick = { viewModel.markAllChatsRead() }
                                            )
                                        )
                                    }
                                }
                            )
                        )
                    }
                }

                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                ) {
                    FlexBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size -> tabRowWidth = size.width }
                            .then(if(isSingleRowFilters) {
                                Modifier.horizontalScroll(tabRowScroll)
                            } else Modifier)
                            // Placed after the scroll modifier this reports the
                            // content node, whose origin moves with the scroll
                            // offset, so chip position minus it is stable.
                            .onGloballyPositioned { coordinates ->
                                tabRowOrigin = coordinates.positionInWindow()
                            }
                            .padding(horizontal = 8.dp)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    if(layoutInfo.size >= AdaptiveLayoutSize.EXPANDED) {
                                        WindowInsetsSides.Top
                                    } else WindowInsetsSides.Horizontal
                                )
                            ),

                        config = {
                            gap(4.dp)

                            if(!isSingleRowFilters) {
                                wrap(FlexWrap.Wrap)
                            }
                        }
                    ) {
                        val chipModifier = Modifier.flex {
                            grow(1f)
                        }

                        val pageByKey = folderPages.associateBy { page ->
                            page.orderKey()
                        }

                        folderPages.map { page -> page.orderKey() }.forEach { flowKey ->
                            val page = pageByKey[flowKey] ?: return@forEach

                            val chipKey = page.orderKey()

                            val chipModifierForPage = chipModifier
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInWindow()
                                    val left = position.x - tabRowOrigin.x
                                    val top = position.y - tabRowOrigin.y

                                    chipBounds[chipKey] = Rect(
                                        left = left,
                                        top = top,
                                        right = left + coordinates.size.width,
                                        bottom = top + coordinates.size.height
                                    )
                                }

                            when (page) {
                                HomePage.All -> HomeTab(
                                    modifier = chipModifierForPage,
                                    selected = selectedTab == TAB_ALL,
                                    label = "All",
                                    icon = home_icon,
                                    unreadCount = unreadCounts.all,
                                    onClick = { viewModel.selectAll() }
                                )

                                is HomePage.Tab -> HomeTab(
                                    modifier = chipModifierForPage,
                                    selected = selectedTab == page.index,
                                    label = page.chipTitle(),
                                    icon = when (page.index) {
                                        TAB_DIRECT -> account_circle_icon
                                        TAB_GROUPS -> group_icon
                                        TAB_CHANNELS -> brand_awareness_icon
                                        else -> smart_toy_icon
                                    },
                                    unreadCount = unreadCounts.tabs[page.index] ?: 0,
                                    onClick = { viewModel.selectTab(page.index) }
                                )
                            }
                        }
                    }
                }

                if (isDialogsLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
        }

        // Sits between the filter chips and the list so it stays visible
        // while scrolling the chats of a disconnected account.
        ConnectionBanner()

        HorizontalPager(
            state = folderPagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIndex ->
            val page = folderPages.getOrNull(pageIndex)
            val pageEntries = remember(entries, page) {
                entriesForPage(page, entries)
            }

            DialogList(
                entries = pageEntries,
                mergeDuplicates = isMergeDuplicateDialogs,
                isDialogsLoading = isDialogsLoading,
                hasAccounts = accountState.accounts.isNotEmpty(),
                selectedDialogAccountId = selectedDialogAccountId,
                selectedDialogProviderDialogId = selectedDialogProviderDialogId,
                onOpenAccountsScreen = onOpenAccountsScreen,
                onOpenDialog = onOpenDialog,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (viewModel.showPinnedOrderDialog) {
        val pinnedEntries = entries.filter { entry -> entry.dialog.isPinned }

        ReorderDialog(
            title = "Reorder pinned chats",
            items = pinnedEntries.map { entry ->
                ReorderItem(
                    key = "${entry.account.id}:${entry.dialog.providerDialogId}",
                    title = entry.dialog.title
                )
            },
            onDismiss = { viewModel.closePinnedOrder() },
            onConfirm = { keys -> viewModel.applyPinnedOrder(keys) }
        )
    }
}
