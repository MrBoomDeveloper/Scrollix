package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.emoji.EmojiCatalog
import com.mrboomdev.scrollix.data.media.WebmAnimation
import com.mrboomdev.scrollix.data.media.loadWebmAnimation
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.StickerSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.EmojiRecentsStore
import com.mrboomdev.scrollix.features.home.FolderEmojiImage
import com.mrboomdev.scrollix.features.home.WebmFrames
import com.mrboomdev.scrollix.features.home.key

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.mrboomdev.scrollix.onSecondaryClick

private val GridSpacing = 2.dp

/** Applies the user's pack order, keeping unlisted packs in server order. */
internal fun orderedStickerPacks(
    packs: List<StickerPack>,
    order: List<Long>
): List<StickerPack> {
    if (order.isEmpty()) return packs

    val positions = order.withIndex().associate { (index, id) -> id to index }
    return packs.sortedBy { pack -> positions[pack.id] ?: Int.MAX_VALUE }
}

/**
 * Inline picker shown above the composer. Emoji recents are local, sticker
 * and GIF lists come from the server (installed packs, recent stickers and
 * saved animations).
 *
 * Every tab lists all installed packs in a single scrollable grid. Pack
 * headers show the pack's featured sticker, and the row above the grid jumps
 * straight to a pack.
 */
/**
 * The emoji/sticker picker as a dialog on wide layouts and as a bottom sheet
 * on mobile. Shared by the composer, the poll composer and attachment review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiPickerDialog(
    isMobile: Boolean,
    session: StickerSession,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onCustomEmojiSelected: (emojiId: Long, fallback: String) -> Unit,
    /** Emoji fields (poll question/options) only accept emoji. */
    emojiOnly: Boolean = false
) {
    val content: @Composable () -> Unit = {
        EmojiPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            session = session,
            onEmojiSelected = onEmojiSelected,
            onCustomEmojiSelected = onCustomEmojiSelected,
            onStickerSelected = {},
            onAnimationSelected = {},
            emojiOnly = emojiOnly
        )
    }

    if (isMobile) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            content()
        }
    } else {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = onDismiss,
            text = { content() },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
internal fun EmojiPicker(
    modifier: Modifier = Modifier,
    session: StickerSession,
    onEmojiSelected: (String) -> Unit,
    onCustomEmojiSelected: (emojiId: Long, fallback: String) -> Unit,
    onStickerSelected: (MessageMedia) -> Unit,
    onAnimationSelected: (MessageMedia) -> Unit,
    /** Sessions whose packs are shown alongside this account's own packs. */
    otherSessions: List<AccountSession> = emptyList(),
    /** Importing is only offered where the callback is provided. */
    onCrossProviderStickerSelected: ((StickerSession, MessageMedia) -> Unit)? = null,
    /** Importing a GIF from another account is only offered where provided. */
    onCrossProviderAnimationSelected: ((StickerSession, MessageMedia) -> Unit)? = null,
    /** Renders only the emoji tab, for fields that take no stickers or GIFs. */
    emojiOnly: Boolean = false
) {
    val settings by AppSettingsStore.state.collectAsState()

    // The picker opens on the tab that was used last, even across restarts.
    var selectedTab by remember {
        mutableStateOf(
            if (emojiOnly) 0 else settings.emojiPickerTab.coerceIn(0, 2)
        )
    }

    val activeTab = if (emojiOnly) 0 else selectedTab

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!emojiOnly) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    listOf("Emoji", "Stickers", "GIFs").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                AppSettingsStore.update { it.copy(emojiPickerTab = index) }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                }
            }

            when (activeTab) {
                0 -> EmojiTab(
                    session = session,
                    showRecents = settings.showRecentStickersAndEmojis,
                    showSystemEmojis = settings.showSystemEmojis,
                    disabledEmojis = settings.disabledEmojis,
                    onEmojiSelected = onEmojiSelected,
                    onCustomEmojiSelected = onCustomEmojiSelected,
                    onStickerSelected = onStickerSelected
                )

                1 -> StickerTab(
                    session = session,
                    showRecents = settings.showRecentStickersAndEmojis,
                    onStickerSelected = onStickerSelected,
                    otherSessions = otherSessions,
                    onCrossProviderStickerSelected = onCrossProviderStickerSelected
                )

                2 -> GifTab(
                    session = session,
                    otherSessions = otherSessions,
                    onAnimationSelected = onAnimationSelected,
                    onCrossProviderAnimationSelected = onCrossProviderAnimationSelected
                )
            }
        }
    }
}

@Composable
private fun EmojiTab(
    session: StickerSession,
    showRecents: Boolean,
    showSystemEmojis: Boolean,
    disabledEmojis: Set<String>,
    onEmojiSelected: (String) -> Unit,
    onCustomEmojiSelected: (Long, String) -> Unit,
    onStickerSelected: (MessageMedia) -> Unit
) {
    val recents by EmojiRecentsStore.recents.collectAsState()
    val settings by AppSettingsStore.state.collectAsState()
    var packs by remember(session) { mutableStateOf<List<StickerPack>>(emptyList()) }
    var packItems by remember(session) {
        mutableStateOf<Map<Long, List<MessageMedia>>>(emptyMap())
    }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var clearRecents by remember { mutableStateOf(false) }

    LaunchedEffect(session, settings.emojiPackOrders[session.accountId]) {
        val loadedPacks = orderedStickerPacks(
            session.loadStickerPacks(StickerPackKind.CustomEmoji)
                .getOrDefault(emptyList()),
            settings.emojiPackOrders[session.accountId].orEmpty()
        )

        packs = loadedPacks
        packItems = coroutineScope {
            loadedPacks.map { pack ->
                async {
                    pack.id to session.loadStickerSet(pack.id)
                        .getOrNull()
                        ?.stickers
                        .orEmpty()
                }
            }.awaitAll().toMap()
        }
    }

    val visibleRecents = remember(recents, disabledEmojis) {
        recents.filterNot { emoji -> emoji in disabledEmojis }
    }

    val entries = remember(
        visibleRecents,
        packs,
        packItems,
        showRecents,
        showSystemEmojis,
        disabledEmojis
    ) {
        buildList {
            if (showRecents && visibleRecents.isNotEmpty()) {
                add(EmojiEntry.RecentsHeader)
                visibleRecents.forEach { emoji -> add(EmojiEntry.Recent(emoji)) }
            }

            if (showSystemEmojis) {
                EmojiCatalog.forEach { category ->
                    add(EmojiEntry.CategoryHeader(category.name))
                    category.emojis
                        .filterNot { emoji -> emoji in disabledEmojis }
                        .forEach { emoji ->
                            add(EmojiEntry.Palette(category = category.name, emoji = emoji))
                        }
                }
            }

            packs.forEach { pack ->
                add(EmojiEntry.PackHeader(pack))
                packItems[pack.id].orEmpty().forEach { media ->
                    add(EmojiEntry.PackItem(packId = pack.id, media = media))
                }
            }
        }
    }

    val packHeaderIndexes = remember(entries) {
        entries.mapIndexedNotNull { index, entry ->
            (entry as? EmojiEntry.PackHeader)?.let { header -> header.pack.id to index }
        }.toMap()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PackJumpRow(
            packs = packs,
            featured = { pack -> packItems[pack.id]?.firstOrNull() },
            session = session
        ) { pack ->
            packHeaderIndexes[pack.id]?.let { index ->
                scope.launch { gridState.scrollToItem(index) }
            }
        }

        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            columns = GridCells.Adaptive(48.dp),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(GridSpacing)
        ) {
            items(
                count = entries.size,
                key = { index -> entries[index].key },
                span = { index ->
                    if (entries[index].isHeader) {
                        GridItemSpan(maxLineSpan)
                    } else {
                        GridItemSpan(1)
                    }
                }
            ) { index ->
                when (val entry = entries[index]) {
                    EmojiEntry.RecentsHeader -> SectionHeader(
                        title = "Recent",
                        onClear = { clearRecents = true }
                    )

                    is EmojiEntry.Recent -> EmojiCell(
                        emoji = entry.emoji,
                        onRemove = { EmojiRecentsStore.forget(entry.emoji) }
                    ) {
                        EmojiRecentsStore.remember(entry.emoji)
                        onEmojiSelected(entry.emoji)
                    }

                    is EmojiEntry.CategoryHeader -> SectionHeader(title = entry.title)

                    is EmojiEntry.Palette -> EmojiCell(emoji = entry.emoji) {
                        EmojiRecentsStore.remember(entry.emoji)
                        onEmojiSelected(entry.emoji)
                    }

                    is EmojiEntry.PackHeader -> PackSectionHeader(
                        pack = entry.pack,
                        featured = packItems[entry.pack.id]?.firstOrNull(),
                        session = session
                    )

                    is EmojiEntry.PackItem -> Box(
                        modifier = Modifier.aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        PickerSticker(
                            media = entry.media,
                            session = session,
                            size = 48.dp,
                            onClick = {
                                val emojiId = entry.media.stickerId
                                val emoji = entry.media.stickerEmoji ?: "😀"

                                if (emojiId != null) {
                                    // Keep the custom emoji identity: it is sent
                                    // as a text entity, not as a sticker.
                                    onCustomEmojiSelected(emojiId, emoji)
                                } else {
                                    onEmojiSelected(emoji)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (clearRecents) {
            ConfirmDeleteDialog(
                title = "Clear recent emoji?",
                message = "The list of recently used emoji is emptied. It is only " +
                    "a shortcut: nothing else is deleted.",
                confirmLabel = "Clear",
                onConfirm = { EmojiRecentsStore.clear() },
                onDismiss = { clearRecents = false }
            )
        }
    }
}

/** A saved GIF together with the account it belongs to. */
internal data class SavedGif(
    val source: StickerSession,
    val media: MessageMedia
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(
    emoji: String,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionModifier = if (onRemove != null) {
        Modifier
            .onSecondaryClick(onRemove)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onRemove
            )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(interactionModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 24.sp)
    }
}

@Composable
internal fun PickerSticker(
    media: MessageMedia,
    session: StickerSession,
    size: Dp,
    onClick: (() -> Unit)? = null
) {
    val fileId = media.stickerFileId ?: media.fileId
    var data by remember(fileId) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(fileId, session) {
        data = session.loadMediaBytes(
            fileId = fileId,
            uniqueId = media.stickerUniqueId ?: media.fileUniqueId
        )
    }

    val emoji = remember(fileId, data, media.stickerFormat, media.needsRepainting) {
        DialogFolderEmoji(
            customEmojiId = (fileId ?: 0).toLong(),
            offset = 0,
            length = 0,
            fallback = "",
            needsRepainting = media.needsRepainting,
            format = when (media.stickerFormat) {
                MessageStickerFormat.Tgs -> DialogFolderEmojiFormat.Tgs
                MessageStickerFormat.Webm -> DialogFolderEmojiFormat.Webm
                MessageStickerFormat.Webp -> DialogFolderEmojiFormat.Webp
            },
            data = data
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Stickers and custom emoji fade in once their file has loaded.
        Crossfade(
            targetState = data,
            animationSpec = tween(160),
            label = "picker-sticker"
        ) { bytes ->
            if (bytes == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                FolderEmojiImage(
                    emoji = emoji,
                    animate = true,
                    size = size,
                    color = Color.Unspecified,
                    contentModifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Saved GIFs autoload and autoplay, so the picker previews the animation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PickerAnimation(
    media: MessageMedia,
    session: StickerSession,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var animation by remember(media.fileId, session) {
        mutableStateOf<WebmAnimation?>(null)
    }
    var posterFailed by remember(media.previewFileId) { mutableStateOf(false) }

    val sizePx = with(LocalDensity.current) { 96.dp.roundToPx() }

    LaunchedEffect(media.fileId, session, sizePx) {
        val fileId = media.fileId
        val bytes = session.loadMediaBytes(fileId, media.fileUniqueId)

        if (bytes != null) {
            animation = loadWebmAnimation(
                customEmojiId = (fileId ?: 0).toLong(),
                bytes = bytes,
                widthPx = sizePx,
                heightPx = sizePx
            )
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .onSecondaryClick(onRemove)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onRemove
            ),
        contentAlignment = Alignment.Center
    ) {
        val webm = animation

        if (webm != null && webm.frames.isNotEmpty()) {
            WebmFrames(
                animation = webm,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!posterFailed) {
            media.minithumbnail?.let { thumbnail ->
                BytesImage(
                    bytes = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            MediaImage(
                session = session,
                fileId = media.previewFileId,
                uniqueId = media.previewUniqueId,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { posterFailed = true }
            )
        } else {
            Text(
                text = "GIF",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private sealed interface EmojiEntry {
    val key: String
    val isHeader: Boolean get() = false

    data object RecentsHeader : EmojiEntry {
        override val key: String = "emoji-header-recents"
        override val isHeader: Boolean get() = true
    }

    data class CategoryHeader(val title: String) : EmojiEntry {
        override val key: String = "emoji-header-$title"
        override val isHeader: Boolean get() = true
    }

    data class Recent(val emoji: String) : EmojiEntry {
        override val key: String = "emoji-recent-$emoji"
    }

    data class Palette(
        val category: String,
        val emoji: String
    ) : EmojiEntry {
        override val key: String = "emoji-palette-$category-$emoji"
    }

    data class PackHeader(val pack: StickerPack) : EmojiEntry {
        override val key: String = "emoji-pack-${pack.id}"
        override val isHeader: Boolean get() = true
    }

    data class PackItem(
        val packId: Long,
        val media: MessageMedia
    ) : EmojiEntry {
        override val key: String = "emoji-pack-item-$packId-${media.fileId}"
    }
}

/** Cell ratio of a sticker, clamped so one odd asset cannot break the grid. */
internal fun stickerAspect(media: MessageMedia): Float {
    if (media.width <= 0 || media.height <= 0) return 1f
    return (media.width.toFloat() / media.height.toFloat()).coerceIn(0.6f, 1.8f)
}

internal data class ForeignStickerPack(
    val source: AccountSession,
    val pack: StickerPack,
    val items: List<MessageMedia>
) {
    val title: String
        get() = "${source.providerId.replaceFirstChar(Char::uppercase)} · ${pack.title}"
}

internal sealed interface StickerEntry {
    val key: String
    val isHeader: Boolean get() = false

    data object RecentsHeader : StickerEntry {
        override val key: String = "sticker-header-recents"
        override val isHeader: Boolean get() = true
    }

    data class Recent(val media: MessageMedia) : StickerEntry {
        override val key: String = "sticker-recent-${media.fileId}"
    }

    data class PackHeader(val pack: StickerPack) : StickerEntry {
        override val key: String = "sticker-pack-${pack.id}"
        override val isHeader: Boolean get() = true
    }

    data class Sticker(
        val packId: Long,
        val media: MessageMedia
    ) : StickerEntry {
        override val key: String = "sticker-item-$packId-${media.fileId}"
    }

    data class ForeignPackHeader(
        val pack: ForeignStickerPack
    ) : StickerEntry {
        override val key: String =
            "sticker-foreign-pack-${pack.source.accountId}-${pack.pack.id}"
        override val isHeader: Boolean get() = true
    }

    data class ForeignSticker(
        val pack: ForeignStickerPack,
        val media: MessageMedia
    ) : StickerEntry {
        override val key: String =
            "sticker-foreign-${pack.source.accountId}-${pack.pack.id}-${media.fileId}"
    }
}
