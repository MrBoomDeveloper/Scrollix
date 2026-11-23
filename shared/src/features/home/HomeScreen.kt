package com.mrboomdev.scrollix.features.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalFlexBoxApi
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexBoxConfig
import androidx.compose.foundation.layout.FlexBoxScope
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AccountDialogFolder
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.formatTime
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.CustomEmojiMessageText
import com.mrboomdev.scrollix.ui.components.DialogMediaPreview
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.FolderNameText
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.MainMiniAppDialog
import com.mrboomdev.scrollix.ui.components.ReorderDialog
import com.mrboomdev.scrollix.ui.components.ReorderItem
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.brand_awareness_icon
import com.mrboomdev.scrollix.ui.icons.check_icon
import com.mrboomdev.scrollix.ui.icons.delete_icon
import com.mrboomdev.scrollix.ui.icons.done_all_icon
import com.mrboomdev.scrollix.ui.icons.group_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.keep_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon
import com.mrboomdev.scrollix.ui.icons.settings_icon
import com.mrboomdev.scrollix.ui.icons.smart_toy_icon
import com.mrboomdev.scrollix.isWideLayout
import com.mrboomdev.scrollix.onSecondaryClick
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val TAB_ALL = 0
private const val TAB_DIRECT = 1
private const val TAB_GROUPS = 2
private const val TAB_CHANNELS = 3
private const val TAB_BOTS = 4

private fun HomePage.orderKey(): String = when (this) {
    HomePage.All -> "all"
    is HomePage.Folder -> "folder:$key"
    is HomePage.Tab -> "tab:$index"
}

private fun HomePage.chipTitle(folders: List<AccountDialogFolder>): String = when (this) {
    HomePage.All -> "All"
    is HomePage.Folder -> folders.firstOrNull { it.key == key }?.folder?.name ?: key
    is HomePage.Tab -> when (index) {
        TAB_DIRECT -> "DM"
        TAB_GROUPS -> "Groups"
        TAB_CHANNELS -> "Channels"
        TAB_BOTS -> "Bots"
        else -> "Tab $index"
    }
}

@OptIn(ExperimentalFlexBoxApi::class)
@Composable
fun HomeScreen(
    onOpenAccountsScreen: () -> Unit,
    onOpenSettingsScreen: () -> Unit,
    onOpenDialog: (AccountDialogEntry) -> Unit,
    selectedDialogAccountId: String? = null,
    selectedDialogProviderDialogId: String? = null
) {
    val layoutInfo = currentAdaptiveLayoutInfo()
    val settings by AppSettingsStore.state.collectAsState()
    val isAllShown = settings.showAllChatsTab
    val isSingleRowFilters = settings.singleRowFilters
    val isUsingNativeFolders = settings.useNativeFolders
    val scope = rememberCoroutineScope()

    // Chip geometry backs both the "scroll the selected folder into view"
    // behaviour and dragging a chip to a new place in the row.
    val folderRowScroll = rememberScrollState()
    var folderRowWidth by remember { mutableIntStateOf(0) }
    var folderRowOrigin by remember { mutableStateOf(Offset.Zero) }
    val chipBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggedChipKey by remember { mutableStateOf<String?>(null) }
    var draggedChipShift by remember { mutableStateOf(Offset.Zero) }
    val dragEdge = with(LocalDensity.current) { 32.dp.toPx() }
    val dragScrollStep = with(LocalDensity.current) { 12.dp.toPx() }

    var showFolderOrderDialog by remember { mutableStateOf(false) }
    var showPinnedOrderDialog by remember { mutableStateOf(false) }
    
    var selectedTab by remember {
        mutableStateOf(
            when (settings.defaultHomeTab) {
                "direct" -> TAB_DIRECT
                "groups" -> TAB_GROUPS
                "channels" -> TAB_CHANNELS
                "bots" -> TAB_BOTS
                else -> if(isAllShown) TAB_ALL else TAB_DIRECT
            }
        )
    }
    var selectedNativeFolderKey by remember { mutableStateOf<String?>(null) }
    val accountState by ApiManager.accountState.collectAsState()
    val sessionStates by ApiManager.sessionStates.collectAsState()
    val loadingDialogs by ApiManager.dialogsLoading.collectAsState()
    val entries by ApiManager.dialogs.collectAsState()
    val folders by ApiManager.folders.collectAsState()

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

            HomePage.All -> if (isUsingNativeFolders) {
                selectedNativeFolderKey = null
            } else {
                selectedTab = TAB_ALL
            }

            is HomePage.Folder -> selectedNativeFolderKey = page.key
            is HomePage.Tab -> selectedTab = page.index
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

    val folderUnreadCounts = remember(entries) {
        val counts = mutableMapOf<String, Int>()

        entries.forEach { entry ->
            if (!entry.dialog.hasUnreadIndicator()) return@forEach

            entry.dialog.folderIds.forEach { folderId ->
                val key = "${entry.account.id}:$folderId"
                counts[key] = (counts[key] ?: 0) + 1
            }
        }

        counts
    }

    val allUnreadCount = remember(entries) {
        entries.count { entry -> entry.dialog.hasUnreadIndicator() }
    }

    val tabUnreadCounts = remember(entries) {
        entries.filter { entry -> entry.dialog.hasUnreadIndicator() }
            .groupingBy { entry ->
                when (entry.dialog.kind) {
                    DialogKind.Direct, DialogKind.Secret -> TAB_DIRECT
                    DialogKind.Group -> TAB_GROUPS
                    DialogKind.Channel -> TAB_CHANNELS
                    DialogKind.Bot -> TAB_BOTS
                }
            }
            .eachCount()
    }

    val isConnecting = accountState.accounts.any { account ->
        val state = sessionStates[account.id]
        state == null || state is AuthState.Initializing
    }
    val isDialogsLoading = isConnecting || loadingDialogs.isNotEmpty()

    val activeFolderId = if (isUsingNativeFolders) {
        selectedNativeFolder?.folder?.id
    } else {
        null
    }

    /**
     * Applies a drop: the chip is inserted where it was released, judged by
     * the centres of the other chips in the row (row-major, so wrapped chips
     * reorder too). The resulting list is the same `folderOrder` the reorder
     * dialog and the pager pages use.
     */
    fun dropChip(key: String) {
        val bounds = chipBounds[key] ?: return
        val keys = folderPages.map { page -> page.orderKey() }
        if (key !in keys) return

        val center = Offset(
            bounds.center.x + draggedChipShift.x,
            bounds.center.y + draggedChipShift.y
        )
        val tolerance = (bounds.height / 2f).coerceAtLeast(1f)
        val others = keys.filter { other -> other != key }

        val before = others.count { other ->
            val otherCenter = chipBounds[other]?.center ?: return@count false

            if (abs(otherCenter.y - center.y) > tolerance) {
                otherCenter.y < center.y
            } else {
                otherCenter.x < center.x
            }
        }

        val reordered = others.toMutableList().apply {
            add(before.coerceIn(0, size), key)
        }

        if (reordered == keys) return

        AppSettingsStore.update { current -> current.copy(folderOrder = reordered) }
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

    // The drag gesture must not restart when the preview order changes (that
    // would drop the finger). This holder always calls the latest dropChip,
    // which reads the current chip geometry and order.
    val currentDropChip by rememberUpdatedState<(String) -> Unit>({ key -> dropChip(key) })

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
                    if (folderId == null) {
                        true
                    } else {
                        entry.account.id == selectedNativeFolder.account.id &&
                            folderId in entry.dialog.folderIds
                    }
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
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxWidth()) {
                if(layoutInfo.size < AdaptiveLayoutSize.EXPANDED) {
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
//                        Image(
//                            modifier = Modifier.size(IconButtonDefaults.smallContainerSize()),
//                            painter = rememberVectorPainter(home_icon),
//                            contentDescription = null
//                        )

                        Text(
                            modifier = Modifier.padding(start = 4.dp),
                            text = "Scrollix",
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
                                        onOpenSettingsScreen()
                                        expand = false
                                    }
                                )

                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = select_check_box_icon,
                                            contentDescription = null
                                        )
                                    },
                                    text = { Text("Reorder folders") },
                                    onClick = {
                                        showFolderOrderDialog = true
                                        expand = false
                                    }
                                )

                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = keep_icon,
                                            contentDescription = null
                                        )
                                    },
                                    text = { Text("Reorder pinned chats") },
                                    onClick = {
                                        showPinnedOrderDialog = true
                                        expand = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
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

                                translationX = draggedChipShift.x
                                translationY = draggedChipShift.y
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
                                        draggedChipKey = key
                                        draggedChipShift = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        draggedChipShift += amount
                                        autoScrollRow(key)
                                        // Reorder while the finger is still down, so
                                        // the chips behind visibly make room before
                                        // the drop.
                                        currentDropChip(key)
                                    },
                                    onDragEnd = {
                                        currentDropChip(key)
                                        draggedChipKey = null
                                        draggedChipShift = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggedChipKey = null
                                        draggedChipShift = Offset.Zero
                                    }
                                )
                            }

                        folderPages.forEach { page ->
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
                                    unreadCount = allUnreadCount,
                                    onClick = {
                                        if (isUsingNativeFolders) {
                                            selectedNativeFolderKey = null
                                        } else {
                                            selectedTab = TAB_ALL
                                        }
                                    }
                                )

                                is HomePage.Folder -> {
                                    val folder = folders.firstOrNull { it.key == page.key }
                                        ?: return@forEach

                                    NativeFolderTab(
                                        modifier = chipModifierFor(page.orderKey()),
                                        selected = selectedNativeFolder?.key == folder.key,
                                        folder = folder.folder,
                                        account = folder.account,
                                        unreadCount = folderUnreadCounts[folder.key] ?: 0,
                                        showAccountIndicator =
                                            folder.folder.name in duplicateFolderNames,
                                        onClick = {
                                            selectedNativeFolderKey = folder.key
                                        }
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
                                    unreadCount = tabUnreadCounts[page.index] ?: 0,
                                    onClick = { selectedTab = page.index }
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

    if (showFolderOrderDialog) {
        ReorderDialog(
            title = "Reorder folders",
            items = folderPages.map { page ->
                ReorderItem(key = page.orderKey(), title = page.chipTitle(folders))
            },
            onDismiss = { showFolderOrderDialog = false },
            onConfirm = { keys ->
                AppSettingsStore.update { it.copy(folderOrder = keys) }
                showFolderOrderDialog = false
            }
        )
    }

    if (showPinnedOrderDialog) {
        val pinnedEntries = entries.filter { entry -> entry.dialog.isPinnedIn(null) }

        ReorderDialog(
            title = "Reorder pinned chats",
            items = pinnedEntries.map { entry ->
                ReorderItem(
                    key = "${entry.account.id}:${entry.dialog.providerDialogId}",
                    title = entry.dialog.title
                )
            },
            onDismiss = { showPinnedOrderDialog = false },
            onConfirm = { keys ->
                showPinnedOrderDialog = false

                val byAccount = keys.mapNotNull { key ->
                    val separator = key.indexOf(':')
                    if (separator <= 0) return@mapNotNull null

                    key.substring(0, separator) to key.substring(separator + 1)
                }.groupBy({ it.first }, { it.second })

                scope.launch {
                    byAccount.forEach { (accountId, dialogIds) ->
                        ApiManager.session(accountId)
                            ?.reorderPinnedDialogs(dialogIds)
                    }
                }
            }
        )
    }
}

@Composable
private fun DialogList(
    entries: List<AccountDialogEntry>,
    folderId: Int?,
    isDialogsLoading: Boolean,
    hasAccounts: Boolean,
    selectedDialogAccountId: String?,
    selectedDialogProviderDialogId: String?,
    onOpenAccountsScreen: () -> Unit,
    onOpenDialog: (AccountDialogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    // Several accounts can each have a "Saved messages" chat. Without a badge
    // the rows are indistinguishable, so every one of them shows its account.
    val showSavedMessagesAccountBadge = remember(entries) {
        entries.count { entry -> entry.dialog.isSavedMessages } > 1
    }

    LazyColumn(modifier = modifier) {
        items(
            items = entries,
            key = { entry ->
                "${entry.account.id}:${entry.dialog.providerDialogId}"
            }
        ) { entry ->
            DialogItem(
                entry = entry,
                isSelected = entry.account.id == selectedDialogAccountId &&
                    entry.dialog.providerDialogId == selectedDialogProviderDialogId,
                folderId = folderId,
                showSavedMessagesAccountBadge = showSavedMessagesAccountBadge,
                onClick = { onOpenDialog(entry) }
            )
        }

        if (entries.isEmpty()) {
            item {
                if (isDialogsLoading) {
                    LoadingContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        text = "Loading dialogs..."
                    )
                } else {
                    EmptyState(
                        hasAccounts = hasAccounts,
                        onOpenAccountsScreen = onOpenAccountsScreen
                    )
                }
            }
        }
    }
}

private sealed interface HomePage {
    data object All : HomePage
    data class Folder(val key: String) : HomePage
    data class Tab(val index: Int) : HomePage
}

/** Entries and active folder id shown by one pager page. */
private fun entriesForPage(
    page: HomePage?,
    entries: List<AccountDialogEntry>,
    folders: List<AccountDialogFolder>,
    isUsingNativeFolders: Boolean
): Pair<List<AccountDialogEntry>, Int?> {
    return when (page) {
        is HomePage.Folder -> {
            val folder = folders.firstOrNull { folder -> folder.key == page.key }

            if (folder == null) {
                entries to null
            } else {
                val folderId = folder.folder.id

                entries
                    .filter { entry ->
                        entry.account.id == folder.account.id &&
                            folderId in entry.dialog.folderIds
                    }
                    .let { filtered -> sortDialogs(filtered, folderId) } to folderId
            }
        }

        is HomePage.Tab -> entries
            .filter { entry ->
                when (page.index) {
                    TAB_DIRECT -> entry.dialog.kind == DialogKind.Direct ||
                        entry.dialog.kind == DialogKind.Secret

                    TAB_GROUPS -> entry.dialog.kind == DialogKind.Group
                    TAB_CHANNELS -> entry.dialog.kind == DialogKind.Channel
                    TAB_BOTS -> entry.dialog.kind == DialogKind.Bot
                    else -> true
                }
            }
            .let { filtered -> sortDialogs(filtered, null) } to null

        HomePage.All, null -> sortDialogs(entries, null) to null
    }
}

@Composable
private fun HomeTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: ImageVector,
    unreadCount: Int = 0,
    onClick: () -> Unit
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),

        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                    text = label
                )

                if (unreadCount > 0) {
                    UnreadCountBadge(
                        count = unreadCount,
                        selected = selected
                    )
                }
            }
        },
        
        leadingIcon = {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = icon,
                contentDescription = null
            )
        }
    )
}

@Composable
private fun NativeFolderTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    folder: DialogFolder,
    account: AccountRecord,
    unreadCount: Int,
    showAccountIndicator: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,

        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),

        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolderNameText(
                    folder = folder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (unreadCount > 0) {
                    UnreadCountBadge(
                        count = unreadCount,
                        selected = selected
                    )
                }

                if (showAccountIndicator) {
                    AvatarImage(
                        avatar = account.avatar
                            ?: Avatar.fromName(account.displayName),
                        size = 14.dp
                    )
                }
            }
        }
    )
}

private fun Dialog.hasUnreadIndicator(): Boolean =
    unreadCount > 0 || isMarkedAsUnread

@Composable
private fun DialogPreviewText(
    modifier: Modifier = Modifier,
    dialog: Dialog,
    session: AccountSession?
) {
    val emojis = dialog.previewCustomEmojis
    var data by remember(dialog.providerDialogId, emojis) {
        mutableStateOf<Map<Long, ByteArray>>(emptyMap())
    }
    var adaptive by remember(dialog.providerDialogId, emojis) {
        mutableStateOf<Set<Long>>(emptySet())
    }

    LaunchedEffect(session, emojis) {
        val currentSession = session ?: return@LaunchedEffect
        val missing = emojis
            .map { span -> span.emojiId }
            .distinct()
            .filter { id -> id !in data }

        if (missing.isEmpty()) return@LaunchedEffect

        val loaded = missing.mapNotNull { id ->
            currentSession.loadCustomEmoji(id)?.let { asset -> id to asset }
        }.toMap()

        if (loaded.isNotEmpty()) {
            data = data + loaded.mapValues { (_, asset) -> asset.data }
            adaptive = adaptive + loaded
                .filterValues { asset -> asset.needsRepainting }
                .keys
        }
    }

    CustomEmojiMessageText(
        modifier = modifier,
        text = dialog.preview,
        emojis = emojis,
        data = data,
        adaptiveEmojis = adaptive,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun UnreadCountBadge(
    count: Int,
    selected: Boolean
) {
    val textStyle = MaterialTheme.typography.labelSmall
    val textSizeDp = with(LocalDensity.current) { textStyle.fontSize.toDp() }

    Box(
        modifier = Modifier
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                shape = RoundedCornerShape(textSizeDp)
            )
            .padding(vertical = 1.dp, horizontal = 4.dp)
            .widthIn(min = textSizeDp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            text = count.toString()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialogItem(
    entry: AccountDialogEntry,
    isSelected: Boolean,
    folderId: Int?,
    showSavedMessagesAccountBadge: Boolean,
    onClick: () -> Unit
) {
    val dialog = entry.dialog
    val isPinned = dialog.isPinnedIn(folderId)

    // The same chat can be open on more than one account (and every account
    // has its own "Saved messages"). Such rows carry the account avatar on the
    // corner of the chat avatar so the copies stay distinguishable.
    val showAccountAvatarBadge = entry.showAccountBadge ||
        (dialog.isSavedMessages && showSavedMessagesAccountBadge)
    val scope = rememberCoroutineScope()
    val session = remember(dialog.accountId) { ApiManager.session(dialog.accountId) }
    var menuExpanded by remember(dialog.providerDialogId) { mutableStateOf(false) }
    var confirmDelete by remember(dialog.providerDialogId) { mutableStateOf(false) }
    var showMainWebApp by remember(dialog.providerDialogId) { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
                .onSecondaryClick { menuExpanded = true },
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else Color.Transparent
        ) {
        Row(
            modifier = Modifier.padding(8.dp)
        ) {
            Box {
                if (dialog.isSavedMessages) {
                    SavedMessagesAvatar(
                        modifier = Modifier.padding(end = 2.dp),
                        size = 48.dp
                    )
                } else {
                    AvatarImage(
                        modifier = Modifier.padding(end = 2.dp),
                        avatar = dialog.avatar ?: Avatar.fromName(dialog.title),
                        size = 48.dp
                    )
                }

                if (showAccountAvatarBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(1.5.dp)
                    ) {
                        AvatarImage(
                            avatar = entry.account.avatar
                                ?: Avatar.fromName(entry.account.displayName),
                            size = 15.dp
                        )
                    }
                }

                if (dialog.statusText == "online" && dialog.kind != DialogKind.Bot) {
                    // The online dot sits just left of the account badge (with
                    // a small gap) instead of covering it.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(
                                x = if (showAccountAvatarBadge) (-18).dp else 4.dp,
                                y = 4.dp
                            )
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(3.dp)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .fillMaxSize()
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = dialog.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (dialog.isVerified) {
                        Icon(
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                            imageVector = verified_icon,
                            contentDescription = "Verified"
                        )
                    }

                    dialog.emojiStatusId?.let { emojiId ->
                        EmojiStatusIcon(
                            emojiId = emojiId,
                            session = session,
                            size = 16.dp
                        )
                    }

                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val typingText = dialog.typingText

                    if (typingText != null) {
                        Text(
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            text = typingText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        if (dialog.lastMessageIsOutgoing) {
                            Text(
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                text = "You:",
                                maxLines = 1
                            )
                        }

                        dialog.lastMessageMedia?.let { media ->
                            DialogMediaPreview(
                                media = media,
                                session = session,
                                size = 20.dp
                            )
                        }

                        DialogPreviewText(
                            modifier = Modifier.weight(1f),
                            dialog = dialog,
                            session = session
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dialog.lastMessageIsOutgoing && !dialog.isSavedMessages) {
                        Icon(
                            modifier = Modifier.size(14.dp),
                            tint = if (dialog.lastMessageIsRead) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            imageVector = if (dialog.lastMessageIsRead) {
                                done_all_icon
                            } else {
                                check_icon
                            },
                            contentDescription = if (dialog.lastMessageIsRead) {
                                "Read"
                            } else {
                                "Sent"
                            }
                        )
                    }
                    
                    Text(
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        text = formatTime(dialog.timestamp)
                    )
                }

                if (dialog.unreadCount > 0) {
                    val textStyle = MaterialTheme.typography.labelMedium
                    val textSizeDp = with(LocalDensity.current) { textStyle.fontSize.toDp() }
                    
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(textSizeDp))
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                            .widthIn(min = textSizeDp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            style = textStyle,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            text = dialog.unreadCount.toString()
                        )
                    }
                }

                if (dialog.kind == DialogKind.Bot && dialog.hasMainWebApp) {
                    TextButton(
                        modifier = Modifier.heightIn(min = 24.dp),
                        onClick = { showMainWebApp = true }
                    ) {
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (isPinned) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = keep_icon,
                        contentDescription = "Pinned"
                    )
                }
            }
        }
    }

    DropdownMenu(
        expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = keep_icon,
                        contentDescription = null
                    )
                },

                text = {
                    Text(
                        text = if (isPinned) "Unpin chat" else "Pin chat"
                    )
                },

                onClick = {
                    menuExpanded = false

                    scope.launch {
                        ApiManager.setDialogPinned(
                            accountId = entry.account.id,
                            providerDialogId = dialog.providerDialogId,
                            isPinned = !isPinned,
                            folderId = folderId
                        )
                    }
                }
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = delete_icon,
                        contentDescription = null
                    )
                },

                text = {
                    Text(text = "Delete chat")
                },

                onClick = {
                    menuExpanded = false
                    confirmDelete = true
                }
            )
        }
    }

    if (showMainWebApp && session != null) {
        MainMiniAppDialog(
            session = session,
            providerDialogId = dialog.providerDialogId,
            title = dialog.title,
            onDismiss = { showMainWebApp = false }
        )
    }

    if (confirmDelete) {
        val isSharedChat = dialog.kind == DialogKind.Group ||
            dialog.kind == DialogKind.Channel

        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete chat") },
            text = {
                Text(
                    if (isSharedChat) {
                        "Leave \"${dialog.title}\" and remove it from your chat list?"
                    } else {
                        "Delete the conversation with \"${dialog.title}\" for both sides?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false

                        scope.launch {
                            ApiManager.deleteDialog(
                                accountId = entry.account.id,
                                providerDialogId = dialog.providerDialogId
                            )
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun Dialog.isPinnedIn(folderId: Int?): Boolean {
    if (folderId == null) return isPinned

    return folderPositions.any { position ->
        position.folderId == folderId && position.isPinned
    }
}

private fun Dialog.orderIn(folderId: Int?): Long {
    if (folderId == null) return order

    return folderPositions.firstOrNull { position ->
        position.folderId == folderId
    }?.order ?: 0L
}

private fun AccountDialogEntry.key(): String = "${account.id}:${dialog.providerDialogId}"

/**
 * Pinned chats stay on top in their provider pin order, and every unpinned
 * chat is mixed across accounts by its last message time. `Dialog.order`
 * itself cannot be compared across providers: TDLib uses a huge int64 sort
 * key while MAX stores the last event time there.
 */
private fun sortDialogs(
    entries: List<AccountDialogEntry>,
    folderId: Int?
): List<AccountDialogEntry> {
    val pinRanks = entries
        .filter { entry -> entry.dialog.isPinnedIn(folderId) }
        .groupBy { entry -> entry.account.id }
        .flatMap { (_, pinned) ->
            pinned
                .sortedByDescending { entry -> entry.dialog.orderIn(folderId) }
                .mapIndexed { index, entry -> entry.key() to index }
        }
        .toMap()

    return entries.sortedWith(
        compareByDescending<AccountDialogEntry> { entry -> entry.dialog.isPinnedIn(folderId) }
            .thenBy { entry -> pinRanks[entry.key()] ?: 0 }
            .thenByDescending { entry -> entry.dialog.timestamp }
            .thenBy { entry -> entry.dialog.title.lowercase() }
    )
}

@Composable
private fun EmptyState(
    hasAccounts: Boolean,
    onOpenAccountsScreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            style = MaterialTheme.typography.titleMedium,
            text = if (hasAccounts) "No dialogs yet" else "No accounts yet"
        )

        Text(
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
            text = if (hasAccounts) {
                "Dialogs will appear here after the accounts finish loading."
            } else {
                "Add a Telegram account to see its dialogs here."
            }
        )

        Button(onClick = onOpenAccountsScreen) {
            Text(if (hasAccounts) "Manage accounts" else "Add account")
        }
    }
}
