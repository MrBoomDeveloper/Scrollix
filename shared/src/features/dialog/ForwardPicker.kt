package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.icons.check_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.search_icon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Forwarding picker. On phones it is a bottom sheet; on wide windows it stays
 * a modal card. Targets are multi-select, so a tap only toggles the ring and
 * nothing is sent until the Send button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForwardPicker(
    messageIds: LongArray,
    isMobileLayout: Boolean,
    targets: List<Dialog>,
    folders: List<Pair<String, String>>,
    query: String,
    folderKey: String,
    supportsOptions: Boolean,
    hideSender: Boolean,
    removeCaption: Boolean,
    onQueryChange: (String) -> Unit,
    onFolderChange: (String) -> Unit,
    onHideSenderChange: (Boolean) -> Unit,
    onRemoveCaptionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSend: (List<Dialog>) -> Unit
) {
    var selectedKeys by remember(messageIds) { mutableStateOf<Set<String>>(emptySet()) }

    val body: @Composable (Modifier) -> Unit = { modifier ->
        ForwardPickerBody(
            modifier = modifier,
            targets = targets,
            folders = folders,
            query = query,
            folderKey = folderKey,
            selectedKeys = selectedKeys,
            supportsOptions = supportsOptions,
            hideSender = hideSender,
            removeCaption = removeCaption,
            onQueryChange = onQueryChange,
            onFolderChange = onFolderChange,
            onHideSenderChange = onHideSenderChange,
            onRemoveCaptionChange = onRemoveCaptionChange,
            onToggleTarget = { providerDialogId ->
                selectedKeys = if (providerDialogId in selectedKeys) {
                    selectedKeys - providerDialogId
                } else {
                    selectedKeys + providerDialogId
                }
            },
            onCancel = onDismiss,
            onSend = {
                onSend(targets.filter { target -> target.providerDialogId in selectedKeys })
            }
        )
    }

    if (isMobileLayout) {
        ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            body(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .imePadding()
            )
        }
    } else {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = onDismiss,
            text = {
                body(
                    Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                )
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun ForwardPickerBody(
    modifier: Modifier,
    targets: List<Dialog>,
    folders: List<Pair<String, String>>,
    query: String,
    folderKey: String,
    selectedKeys: Set<String>,
    supportsOptions: Boolean,
    hideSender: Boolean,
    removeCaption: Boolean,
    onQueryChange: (String) -> Unit,
    onFolderChange: (String) -> Unit,
    onHideSenderChange: (Boolean) -> Unit,
    onRemoveCaptionChange: (Boolean) -> Unit,
    onToggleTarget: (String) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val pages = folders.ifEmpty { listOf("all" to "All") }
    val scope = rememberCoroutineScope()
    val initialPage = pages.indexOfFirst { page -> page.first == folderKey }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }

    LaunchedEffect(pagerState.settledPage, pages) {
        pages.getOrNull(pagerState.settledPage)?.let { (key, _) ->
            if (key != folderKey) onFolderChange(key)
        }
    }

    LaunchedEffect(folderKey, pages) {
        val target = pages.indexOfFirst { page -> page.first == folderKey }

        if (target >= 0 && target != pagerState.currentPage) {
            pagerState.animateScrollToPage(target)
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Forward to",
                style = MaterialTheme.typography.titleLarge
            )

            if (selectedKeys.isNotEmpty()) {
                Text(
                    text = "${selectedKeys.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Search chats") },
            leadingIcon = {
                Icon(imageVector = search_icon, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = close_icon,
                            contentDescription = "Clear search"
                        )
                    }
                }
            }
        )

        if (pages.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = pages, key = { page -> page.first }) { (key, label) ->
                    FilterChip(
                        selected = folderKey == key,
                        onClick = {
                            onFolderChange(key)
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pages.indexOfFirst { page -> page.first == key }
                                )
                            }
                        },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }

        if (supportsOptions) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ForwardOptionRow(
                    label = "Hide sender",
                    checked = hideSender,
                    onCheckedChange = onHideSenderChange
                )

                ForwardOptionRow(
                    label = "Remove caption",
                    checked = removeCaption,
                    onCheckedChange = onRemoveCaptionChange
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIndex ->
            val key = pages.getOrNull(pageIndex)?.first ?: "all"

            ForwardTargetPage(
                targets = targets,
                query = query,
                folderKey = key,
                selectedKeys = selectedKeys,
                onToggleTarget = onToggleTarget
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }

            Button(
                enabled = selectedKeys.isNotEmpty(),
                onClick = onSend
            ) {
                Text(
                    if (selectedKeys.isEmpty()) {
                        "Send"
                    } else {
                        "Send (${selectedKeys.size})"
                    }
                )
            }
        }
    }
}

@Composable
private fun ForwardTargetPage(
    targets: List<Dialog>,
    query: String,
    folderKey: String,
    selectedKeys: Set<String>,
    onToggleTarget: (String) -> Unit
) {
    val pageTargets = remember(targets, query, folderKey) {
        forwardTargetsForFolder(targets, query, folderKey)
    }

    if (pageTargets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (query.isBlank()) {
                    "No chats to forward to"
                } else {
                    "No chats match \"$query\""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }

        return
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        items(
            items = pageTargets,
            key = { target -> target.providerDialogId }
        ) { target ->
            ForwardTargetCell(
                target = target,
                selected = target.providerDialogId in selectedKeys,
                onClick = { onToggleTarget(target.providerDialogId) }
            )
        }
    }
}

@Composable
private fun ForwardTargetCell(
    target: Dialog,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ringColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 2.5.dp, color = ringColor, shape = CircleShape)
                    .padding(6.dp)
            ) {
                if (target.isSavedMessages) {
                    SavedMessagesAvatar(size = 56.dp)
                } else {
                    AvatarImage(
                        avatar = target.avatar ?: Avatar.fromName(target.title),
                        size = 56.dp
                    )
                }
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        imageVector = check_icon,
                        contentDescription = null
                    )
                }
            }
        }

        Text(
            text = target.title,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ForwardOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
