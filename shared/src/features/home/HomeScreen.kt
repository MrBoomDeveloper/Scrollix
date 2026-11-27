package com.mrboomdev.scrollix.features.home

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.features.settings.icon
import com.mrboomdev.scrollix.moved
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
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon
import com.mrboomdev.scrollix.ui.icons.smart_toy_icon

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrboomdev.scrollix.ui.icons.scrollix_icon
import kotlin.math.abs

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
    onOpenDialog: (AccountDialogEntry) -> Unit,
    selectedDialogAccountId: String? = null,
    selectedDialogProviderDialogId: String? = null,
    viewModel: HomeScreenViewModel = viewModel { HomeScreenViewModel() }
) {
    val layoutInfo = currentAdaptiveLayoutInfo()
    val settings by AppSettingsStore.state.collectAsState()
    val isAllShown = settings.showAllChatsTab
    val isSingleRowFilters = settings.singleRowFilters
    val isUsingNativeFolders = settings.useNativeFolders
    val isMergeDuplicateDialogs = settings.mergeDuplicateDialogs

    // Chip geometry backs both the "scroll the selected folder into view"
    // behaviour and dragging a chip to a new place in the row.
    val folderRowScroll = rememberScrollState()
    var folderRowWidth by remember { mutableIntStateOf(0) }
    var folderRowOrigin by remember { mutableStateOf(Offset.Zero) }
    val chipBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggedChipKey by remember { mutableStateOf<String?>(null) }
    var draggedChipShift by remember { mutableStateOf(Offset.Zero) }
    // Geometry captured when the drag starts. The preview insertion index is
    // computed against this snapshot, so the chips that reflow to make room
    // cannot move the drop target under the finger.
    var dragStartKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragStartBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var dragInsertionIndex by remember { mutableStateOf<Int?>(null) }
    var draggedChipWidth by remember { mutableStateOf(0.dp) }
    var draggedChipHeight by remember { mutableStateOf(0.dp) }
    val dragEdge = with(LocalDensity.current) { 32.dp.toPx() }
    val dragScrollStep = with(LocalDensity.current) { 12.dp.toPx() }
    val dragGapPx = with(LocalDensity.current) { 4.dp.toPx() }


    LaunchedEffect(settings.defaultHomeTab, isAllShown) {
        viewModel.applyDefaultTab(settings)
    }

    val selectedTab = viewModel.selectedTab
    val selectedNativeFolderKey = viewModel.selectedNativeFolderKey
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val loadingDialogs by ApiManager.dialogsLoading.collectAsState()
    val entries by ApiManager.dialogs.collectAsState()
    val folders by ApiManager.folders.collectAsState()
    val sessions by ApiManager.sessions.collectAsState()
    val canMarkAllRead = remember(sessions) {
        sessions.values.any { session -> session.supportsMarkAllRead }
    }

    val selectedNativeFolder = remember(
        selectedNativeFolderKey,
        folders,
        isAllShown
    ) {
        if(selectedNativeFolderKey == null && !isAllShown) {
            return@remember folders.firstOrNull()
        }
        
        folders.firstOrNull {
            it.key == selectedNativeFolderKey
        }
    }
    
    // Every chip gets a swipeable page, so the list can be switched by
    // dragging it horizontally as well as by tapping a chip, for both native
    // folders and the built-in tabs.
    val folderPages = remember(
        isUsingNativeFolders,
        isAllShown,
        folders,
        settings.folderOrder
    ) {
        val pages = buildList {
            if (isAllShown) add(HomePage.All)

            if (isUsingNativeFolders) {
                folders.forEach { folder -> add(HomePage.Folder(folder.key)) }
            } else {
                add(HomePage.Tab(TAB_DIRECT))
                add(HomePage.Tab(TAB_GROUPS))
                add(HomePage.Tab(TAB_CHANNELS))
                add(HomePage.Tab(TAB_BOTS))
            }
        }

        val positions = settings.folderOrder
            .withIndex()
            .associate { (index, key) -> key to index }

        pages.sortedBy { page -> positions[page.orderKey()] ?: Int.MAX_VALUE }
    }

    val selectedFolderPage = remember(
        folderPages,
        selectedNativeFolder,
        selectedTab,
        isUsingNativeFolders
    ) {
        folderPages.indexOfFirst { page ->
            when (page) {
                HomePage.All -> if (isUsingNativeFolders) {
                    selectedNativeFolder == null
                } else {
                    selectedTab == TAB_ALL
                }

                is HomePage.Folder -> selectedNativeFolder?.key == page.key
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

            HomePage.All -> viewModel.selectAll(isUsingNativeFolders)

            is HomePage.Folder -> viewModel.selectFolder(page.key)
            is HomePage.Tab -> viewModel.selectTab(page.index)
        }
    }

    val selectedChipKey = if (isUsingNativeFolders) {
        selectedNativeFolder?.key?.let { key -> "folder:$key" } ?: "all"
    } else {
        "tab:$selectedTab"
    }

    // A swipe can land on a folder whose chip sits past the right edge of the
    // row, which used to leave the row looking like a different folder was
    // selected. Bring the chip of the visible page on screen.
    LaunchedEffect(selectedChipKey, isSingleRowFilters, folderRowWidth, chipBounds.size) {
        if (!isSingleRowFilters) return@LaunchedEffect

        val bounds = chipBounds[selectedChipKey] ?: return@LaunchedEffect
        val viewport = folderRowWidth
        if (viewport <= 0) return@LaunchedEffect

        val start = bounds.left.toInt()
        val end = bounds.right.toInt()
        val current = folderRowScroll.value

        when {
            start < current -> folderRowScroll.animateScrollTo(start)
            end > current + viewport -> folderRowScroll.animateScrollTo(end - viewport)
        }
    }

    val duplicateFolderNames = remember(folders) {
        folders.groupingBy { it.folder.name }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
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

    val activeFolderId = if (isUsingNativeFolders) {
        selectedNativeFolder?.folder?.id
    } else {
        null
    }

    /**
     * Index among the other chips where the dragged chip would land. In the
     * single-row setting only the horizontal position decides, so a small
     * vertical wobble can never move a folder into another row.
     */
    fun insertionIndexFor(key: String): Int? {
        val keys = dragStartKeys
        if (key !in keys) return null

        val origin = dragStartBounds[key] ?: chipBounds[key] ?: return null
        val center = Offset(
            origin.center.x + draggedChipShift.x,
            origin.center.y + draggedChipShift.y
        )
        val tolerance = (origin.height / 2f).coerceAtLeast(1f)
        val others = keys.filter { other -> other != key }

        return others.count { other ->
            val bounds = dragStartBounds[other] ?: return@count false

            if (isSingleRowFilters) {
                bounds.center.x < center.x
            } else if (abs(bounds.center.y - center.y) > tolerance) {
                bounds.center.y < center.y
            } else {
                bounds.center.x < center.x
            }
        }
    }

    /** Commits the preview insertion once the finger is lifted. */
    fun applyDrop(key: String) {
        val insertion = dragInsertionIndex ?: return
        if (dragStartKeys.isEmpty() || key !in dragStartKeys) return

        val others = dragStartKeys.filter { other -> other != key }
        val reordered = others.toMutableList().apply {
            add(insertion.coerceIn(0, size), key)
        }

        if (reordered != dragStartKeys) {
            AppSettingsStore.update { current ->
                current.copy(folderOrder = reordered)
            }
        }
    }

    fun resetDrag() {
        draggedChipKey = null
        draggedChipShift = Offset.Zero
        dragStartKeys = emptyList()
        dragStartBounds = emptyMap()
        dragInsertionIndex = null
    }

    /** Scrolls the row when a dragged chip reaches its left or right edge. */
    fun autoScrollRow(key: String) {
        if (!isSingleRowFilters) return

        val bounds = chipBounds[key] ?: return
        val viewportStart = folderRowScroll.value.toFloat()
        val viewportEnd = viewportStart + folderRowWidth
        val visualStart = bounds.left + draggedChipShift.x
        val visualEnd = bounds.right + draggedChipShift.x

        val delta = when {
            visualStart < viewportStart + dragEdge -> -dragScrollStep
            visualEnd > viewportEnd - dragEdge -> dragScrollStep
            else -> 0f
        }

        if (delta == 0f) return

        val consumed = folderRowScroll.dispatchRawDelta(delta)

        // The content moved under the finger, so move the dragged chip with it
        // and the drop target keeps matching what the user sees.
        if (consumed != 0f) draggedChipShift += Offset(consumed, 0f)
    }

    val visibleEntries = remember(
        entries,
        selectedTab,
        selectedNativeFolder,
        isUsingNativeFolders
    ) {
        if (isUsingNativeFolders) {
            val folderId = selectedNativeFolder?.folder?.id

            entries
                .filter { entry ->
                    folderId == null || entry.account.id == selectedNativeFolder.account.id &&
                            folderId in entry.dialog.folderIds
                }
                .let { filtered -> sortDialogs(filtered, folderId) }
        } else {
            entries
                .filter { entry ->
                    when (selectedTab) {
                        TAB_DIRECT -> entry.dialog.kind == DialogKind.Direct ||
                            entry.dialog.kind == DialogKind.Secret

                        TAB_GROUPS -> entry.dialog.kind == DialogKind.Group
                        TAB_CHANNELS -> entry.dialog.kind == DialogKind.Channel
                        TAB_BOTS -> entry.dialog.kind == DialogKind.Bot
                        else -> true
                    }
                }
                .let { filtered -> sortDialogs(filtered, null) }
        }
    }

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
                                contentDescription = null
                            )
                        }

                        AppOverflowMenu(
                            expanded = expand,
                            onDismissRequest = { expand = false },
                            items = appMenuItems(
                                onOpenDrafts = onOpenDraftsScreen,
                                onOpenSettings = onOpenSettingsScreen,
                                onScanQr = onScanQr,
                                extra = buildList {
                                    add(
                                        AppMenuItem(
                                            title = "Reorder folders",
                                            icon = select_check_box_icon,
                                            onClick = { viewModel.openFolderOrder() }
                                        )
                                    )

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
                            .onSizeChanged { size -> folderRowWidth = size.width }
                            .then(if(isSingleRowFilters) {
                                Modifier.horizontalScroll(folderRowScroll)
                            } else Modifier)
                            // Placed after the scroll modifier this reports the
                            // content node, whose origin moves with the scroll
                            // offset, so chip position minus it is stable.
                            .onGloballyPositioned { coordinates ->
                                folderRowOrigin = coordinates.positionInWindow()
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

                        fun chipModifierFor(key: String): Modifier = chipModifier
                            .zIndex(if (draggedChipKey == key) 1f else 0f)
                            .graphicsLayer {
                                // The held chip lifts and grows with an animated
                                // transition as soon as the drag begins.
                                val lift = if (draggedChipKey == key) 1.08f else 1f
                                scaleX = lift
                                scaleY = lift

                                if (draggedChipKey != key) return@graphicsLayer

                                // Inserting the preview gap reflows the chip;
                                // compensate so it stays under the finger. In
                                // the single-row mode vertical movement is
                                // disabled entirely.
                                val insertion = dragInsertionIndex
                                val originalIndex = dragStartKeys.indexOf(key)
                                val gapShift = if (
                                    isSingleRowFilters &&
                                    insertion != null &&
                                    originalIndex >= 0 &&
                                    insertion < originalIndex
                                ) {
                                    (dragStartBounds[key]?.width ?: 0f) + dragGapPx
                                } else 0f

                                translationX = draggedChipShift.x + gapShift
                                translationY = if (isSingleRowFilters) 0f else draggedChipShift.y
                            }
                            .onGloballyPositioned { coordinates ->
                                // The dragged chip is translated by a graphics
                                // layer, so its reported position is not a
                                // content position; keep the pre-drag bounds.
                                if (draggedChipKey == key) return@onGloballyPositioned

                                val position = coordinates.positionInWindow()
                                val left = position.x - folderRowOrigin.x
                                val top = position.y - folderRowOrigin.y

                                chipBounds[key] = Rect(
                                    left = left,
                                    top = top,
                                    right = left + coordinates.size.width,
                                    bottom = top + coordinates.size.height
                                )
                            }
                            .pointerInput(key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val bounds = chipBounds[key]
                                        draggedChipKey = key
                                        draggedChipShift = Offset.Zero
                                        draggedChipWidth = bounds?.width?.toDp() ?: 0.dp
                                        draggedChipHeight = bounds?.height?.toDp() ?: 0.dp
                                        dragStartKeys = folderPages.map { page ->
                                            page.orderKey()
                                        }
                                        dragStartBounds = chipBounds.toMap()
                                        dragInsertionIndex = dragStartKeys
                                            .indexOf(key)
                                            .coerceAtLeast(0)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        draggedChipShift += if (isSingleRowFilters) {
                                            Offset(amount.x, 0f)
                                        } else {
                                            amount
                                        }
                                        autoScrollRow(key)
                                        // Only preview the target slot here; the
                                        // order is committed on release, so a
                                        // folder never "snaps" to a new place
                                        // while the finger is still down.
                                        dragInsertionIndex = insertionIndexFor(key)
                                    },
                                    onDragEnd = {
                                        applyDrop(key)
                                        resetDrag()
                                    },
                                    onDragCancel = {
                                        resetDrag()
                                    }
                                )
                            }

                        val pageByKey = folderPages.associateBy { page ->
                            page.orderKey()
                        }
                        val flowKeys = if (
                            draggedChipKey != null && dragStartKeys.isNotEmpty()
                        ) {
                            dragStartKeys
                        } else {
                            folderPages.map { page -> page.orderKey() }
                        }
                        val dragging = draggedChipKey
                        val originalFlowIndex = dragging?.let { key ->
                            flowKeys.indexOf(key)
                        } ?: -1
                        // The dragged chip still occupies its own row, so the
                        // gap is placed before or after it depending on the
                        // direction of travel. No gap is drawn while it sits
                        // on its original slot.
                        val gapFlowIndex = if (
                            dragging == null ||
                            dragInsertionIndex == null ||
                            dragInsertionIndex == originalFlowIndex
                        ) {
                            -1
                        } else if (dragInsertionIndex!! < originalFlowIndex) {
                            dragInsertionIndex!!
                        } else {
                            dragInsertionIndex!! + 1
                        }

                        flowKeys.forEachIndexed { flowIndex, flowKey ->
                            // The dragged folder keeps its own row; every other
                            // folder moves out of the preview slot, leaving an
                            // empty space exactly as wide as the dragged chip.
                            if (gapFlowIndex == flowIndex) {
                                Spacer(
                                    modifier = Modifier
                                        .width(draggedChipWidth)
                                        .height(draggedChipHeight)
                                )
                            }

                            val page = pageByKey[flowKey] ?: return@forEachIndexed

                            when (page) {
                                HomePage.All -> HomeTab(
                                    modifier = chipModifierFor(page.orderKey()),
                                    selected = if (isUsingNativeFolders) {
                                        selectedNativeFolder == null
                                    } else {
                                        selectedTab == TAB_ALL
                                    },
                                    label = "All",
                                    icon = home_icon,
                                    unreadCount = unreadCounts.all,
                                    onClick = { viewModel.selectAll(isUsingNativeFolders) }
                                )

                                is HomePage.Folder -> {
                                    val folder = folders.firstOrNull { it.key == page.key }
                                        ?: return@forEachIndexed

                                    NativeFolderTab(
                                        modifier = chipModifierFor(page.orderKey()),
                                        selected = selectedNativeFolder?.key == folder.key,
                                        folder = folder.folder,
                                        account = folder.account,
                                        unreadCount = unreadCounts.folders[folder.key] ?: 0,
                                        showAccountIndicator =
                                            folder.folder.name in duplicateFolderNames,
                                        onClick = { viewModel.selectFolder(folder.key) }
                                    )
                                }

                                is HomePage.Tab -> HomeTab(
                                    modifier = chipModifierFor(page.orderKey()),
                                    selected = selectedTab == page.index,
                                    label = page.chipTitle(folders),
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

                        if (gapFlowIndex == flowKeys.size) {
                            Spacer(
                                modifier = Modifier
                                    .width(draggedChipWidth)
                                    .height(draggedChipHeight)
                            )
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

        if (folderPages.isNotEmpty()) {
            HorizontalPager(
                state = folderPagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val page = folderPages.getOrNull(pageIndex)
                val pageEntries = remember(entries, folders, page, isUsingNativeFolders) {
                    entriesForPage(page, entries, folders, isUsingNativeFolders)
                }

                DialogList(
                    entries = pageEntries.first,
                    folderId = pageEntries.second,
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
        } else {
            DialogList(
                entries = visibleEntries,
                folderId = activeFolderId,
                mergeDuplicates = isMergeDuplicateDialogs,
                isDialogsLoading = isDialogsLoading,
                hasAccounts = accountState.accounts.isNotEmpty(),
                selectedDialogAccountId = selectedDialogAccountId,
                selectedDialogProviderDialogId = selectedDialogProviderDialogId,
                onOpenAccountsScreen = onOpenAccountsScreen,
                onOpenDialog = onOpenDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    if (viewModel.showFolderOrderDialog) {
        ReorderDialog(
            title = "Reorder folders",
            items = folderPages.map { page ->
                ReorderItem(key = page.orderKey(), title = page.chipTitle(folders))
            },
            onDismiss = { viewModel.closeFolderOrder() },
            onConfirm = { keys -> viewModel.applyFolderOrder(keys) }
        )
    }

    if (viewModel.showPinnedOrderDialog) {
        val pinnedEntries = entries.filter { entry -> entry.dialog.isPinnedIn(null) }

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
