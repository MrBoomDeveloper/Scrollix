package com.mrboomdev.scrollix.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.EmojiRecentsStore
import com.mrboomdev.scrollix.onSecondaryClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GridSpacing = 2.dp

/**
 * Inline picker shown above the composer. Emoji recents are local, sticker
 * and GIF lists come from the server (installed packs, recent stickers and
 * saved animations).
 *
 * Every tab lists all installed packs in a single scrollable grid. Pack
 * headers show the pack's featured sticker, and the row above the grid jumps
 * straight to a pack.
 */
@Composable
internal fun EmojiPicker(
    modifier: Modifier = Modifier,
    session: AccountSession,
    onEmojiSelected: (String) -> Unit,
    onCustomEmojiSelected: (emojiId: Long, fallback: String) -> Unit,
    onStickerSelected: (MessageMedia) -> Unit,
    onAnimationSelected: (MessageMedia) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val settings by AppSettingsStore.state.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                listOf("Emoji", "Stickers", "GIFs").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
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
                    onStickerSelected = onStickerSelected
                )

                2 -> GifTab(
                    session = session,
                    onAnimationSelected = onAnimationSelected
                )
            }
        }
    }
}

@Composable
private fun EmojiTab(
    session: AccountSession,
    showRecents: Boolean,
    showSystemEmojis: Boolean,
    disabledEmojis: Set<String>,
    onEmojiSelected: (String) -> Unit,
    onCustomEmojiSelected: (Long, String) -> Unit,
    onStickerSelected: (MessageMedia) -> Unit
) {
    val recents by EmojiRecentsStore.recents.collectAsState()
    var packs by remember(session) { mutableStateOf<List<StickerPack>>(emptyList()) }
    var packItems by remember(session) {
        mutableStateOf<Map<Long, List<MessageMedia>>>(emptyMap())
    }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(session) {
        val loadedPacks = session.loadStickerPacks(StickerPackKind.CustomEmoji)
            .getOrDefault(emptyList())

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
            columns = GridCells.Adaptive(44.dp),
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
                        onClear = { EmojiRecentsStore.clear() }
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
                            size = 40.dp,
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTab(
    session: AccountSession,
    showRecents: Boolean,
    onStickerSelected: (MessageMedia) -> Unit
) {
    var recents by remember(session) { mutableStateOf<List<MessageMedia>>(emptyList()) }
    var packs by remember(session) { mutableStateOf<List<StickerPack>>(emptyList()) }
    var packItems by remember(session) {
        mutableStateOf<Map<Long, List<MessageMedia>>>(emptyMap())
    }
    var isLoading by remember(session) { mutableStateOf(true) }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(session) {
        isLoading = true
        recents = session.loadRecentStickers(StickerPackKind.Sticker)
            .getOrDefault(emptyList())

        val loadedPacks = session.loadStickerPacks(StickerPackKind.Sticker)
            .getOrDefault(emptyList())

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
        isLoading = false
    }

    val entries = remember(recents, packs, packItems, showRecents) {
        buildList {
            if (showRecents && recents.isNotEmpty()) {
                add(StickerEntry.RecentsHeader)
                recents.forEach { media -> add(StickerEntry.Recent(media)) }
            }

            packs.forEach { pack ->
                add(StickerEntry.PackHeader(pack))
                packItems[pack.id].orEmpty().forEach { media ->
                    add(StickerEntry.Sticker(packId = pack.id, media = media))
                }
            }
        }
    }

    val packHeaderIndexes = remember(entries) {
        entries.mapIndexedNotNull { index, entry ->
            (entry as? StickerEntry.PackHeader)?.let { header -> header.pack.id to index }
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

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            return@Column
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No stickers installed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            return@Column
        }

        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            columns = GridCells.Adaptive(60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    StickerEntry.RecentsHeader -> SectionHeader(
                        title = "Recent",
                        onClear = {
                            scope.launch {
                                session.clearRecentStickers().onSuccess {
                                    recents = emptyList()
                                }
                            }
                        }
                    )

                    is StickerEntry.Recent -> {
                        val remove: () -> Unit = {
                            scope.launch {
                                session.removeRecentSticker(entry.media).onSuccess {
                                    recents = recents.filterNot { media ->
                                        media.fileId == entry.media.fileId
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .onSecondaryClick(remove)
                                .combinedClickable(
                                    onClick = { onStickerSelected(entry.media) },
                                    onLongClick = remove
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            PickerSticker(
                                media = entry.media,
                                session = session,
                                size = 52.dp
                            )
                        }
                    }

                    is StickerEntry.PackHeader -> PackSectionHeader(
                        pack = entry.pack,
                        featured = packItems[entry.pack.id]?.firstOrNull(),
                        session = session
                    )

                    is StickerEntry.Sticker -> Box(
                        modifier = Modifier.aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        PickerSticker(
                            media = entry.media,
                            session = session,
                            size = 52.dp,
                            onClick = { onStickerSelected(entry.media) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GifTab(
    session: AccountSession,
    onAnimationSelected: (MessageMedia) -> Unit
) {
    var animations by remember(session) { mutableStateOf<List<MessageMedia>>(emptyList()) }
    var isLoading by remember(session) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session) {
        isLoading = true
        animations = session.loadSavedAnimations().getOrDefault(emptyList())
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(
            title = "Saved GIFs",
            onClear = if (animations.isNotEmpty()) {
                {
                    scope.launch {
                        session.clearSavedAnimations().onSuccess {
                            animations = emptyList()
                        }
                    }
                }
            } else {
                null
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            return@Column
        }

        if (animations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved GIFs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            return@Column
        }

        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            columns = GridCells.Adaptive(96.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(animations, key = { media -> media.fileId ?: 0 }) { animation ->
                PickerAnimation(
                    media = animation,
                    session = session,
                    onClick = { onAnimationSelected(animation) },
                    onRemove = {
                        scope.launch {
                            session.removeSavedAnimation(animation).onSuccess {
                                animations = animations.filterNot { media ->
                                    media.fileId == animation.fileId
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PackJumpRow(
    packs: List<StickerPack>,
    featured: (StickerPack) -> MessageMedia?,
    session: AccountSession,
    onPackClick: (StickerPack) -> Unit
) {
    if (packs.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(packs, key = { pack -> pack.id }) { pack ->
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onPackClick(pack) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PackFeaturedSticker(
                        pack = pack,
                        featured = featured(pack),
                        session = session,
                        size = 32.dp
                    )
                }
            }
        }
    }
}

/** A pack section title: its featured sticker followed by the pack's name. */
@Composable
private fun PackSectionHeader(
    pack: StickerPack,
    featured: MessageMedia?,
    session: AccountSession
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PackFeaturedSticker(
            pack = pack,
            featured = featured,
            session = session,
            size = 32.dp
        )

        Text(
            modifier = Modifier.weight(1f),
            text = pack.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PackFeaturedSticker(
    pack: StickerPack,
    featured: MessageMedia?,
    session: AccountSession,
    size: Dp
) {
    val thumbnailFileId = pack.thumbnailFileId
    var data by remember(thumbnailFileId, session) { mutableStateOf<ByteArray?>(null) }
    var bitmap by remember(thumbnailFileId, session) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(thumbnailFileId, session) {
        data = thumbnailFileId?.let { fileId ->
            session.loadFile(fileId).getOrNull()
        }
        bitmap = data?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = bitmap,
            animationSpec = tween(160),
            label = "pack-featured"
        ) { image ->
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                featured != null -> PickerSticker(
                    media = featured,
                    session = session,
                    size = size
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onClear: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        if (onClear != null) {
            TextButton(
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                onClick = onClear
            ) {
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

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
private fun PickerSticker(
    media: MessageMedia,
    session: AccountSession,
    size: Dp,
    onClick: (() -> Unit)? = null
) {
    val fileId = media.stickerFileId ?: media.fileId
    var data by remember(fileId) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(fileId, session) {
        data = fileId?.let { id -> session.loadFile(id).getOrNull() }
    }

    val emoji = remember(fileId, data, media.stickerFormat) {
        DialogFolderEmoji(
            customEmojiId = (fileId ?: 0).toLong(),
            offset = 0,
            length = 0,
            fallback = "",
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
            .clip(RoundedCornerShape(10.dp))
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
                    size = size - 8.dp,
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
private fun PickerAnimation(
    media: MessageMedia,
    session: AccountSession,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var thumbnail by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var animation by remember(media.fileId, session) {
        mutableStateOf<WebmAnimation?>(null)
    }

    val sizePx = with(LocalDensity.current) { 96.dp.roundToPx() }

    LaunchedEffect(media.previewFileId, media.minithumbnail, session) {
        thumbnail = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        if (thumbnail == null) {
            val bytes = media.previewFileId?.let { fileId ->
                session.loadFile(fileId).getOrNull()
            }

            if (bytes != null) {
                thumbnail = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }
            }
        }
    }

    LaunchedEffect(media.fileId, session, sizePx) {
        val fileId = media.fileId
        val bytes = fileId?.let { id -> session.loadFile(id).getOrNull() }

        if (bytes != null) {
            animation = loadWebmAnimation(
                customEmojiId = fileId.toLong(),
                bytes = bytes,
                widthPx = sizePx,
                heightPx = sizePx
            )
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .onSecondaryClick(onRemove)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onRemove
            ),
        contentAlignment = Alignment.Center
    ) {
        val webm = animation
        val poster = thumbnail

        when {
            webm != null && webm.frames.isNotEmpty() -> WebmFrames(
                animation = webm,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            poster != null -> Image(
                bitmap = poster,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            else -> Text(
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

private sealed interface StickerEntry {
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
}
