package com.mrboomdev.scrollix.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.onSecondaryClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StickerTab(
    session: AccountSession,
    showRecents: Boolean,
    onStickerSelected: (MessageMedia) -> Unit,
    otherSessions: List<AccountSession>,
    onCrossProviderStickerSelected: ((AccountSession, MessageMedia) -> Unit)?
) {
    val settings by AppSettingsStore.state.collectAsState()
    var recents by remember(session) { mutableStateOf<List<MessageMedia>>(emptyList()) }
    var packs by remember(session) { mutableStateOf<List<StickerPack>>(emptyList()) }
    var packItems by remember(session) {
        mutableStateOf<Map<Long, List<MessageMedia>>>(emptyMap())
    }
    var foreignPacks by remember(session) {
        mutableStateOf<List<ForeignStickerPack>>(emptyList())
    }
    var isLoading by remember(session) { mutableStateOf(true) }
    var pendingRemoveSticker by remember(session) { mutableStateOf<MessageMedia?>(null) }

    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()

    // The other accounts register asynchronously, so their packs must be
    // re-read when a session shows up; keying on the session alone loaded the
    // foreign list once, while it was still empty.
    val otherAccountIds = otherSessions.map { source -> source.accountId }

    LaunchedEffect(
        session,
        settings.stickerPackOrders[session.accountId],
        otherAccountIds
    ) {
        isLoading = true
        recents = session.loadRecentStickers(StickerPackKind.Sticker)
            .getOrDefault(emptyList())

        val loadedPacks = orderedStickerPacks(
            session.loadStickerPacks(StickerPackKind.Sticker)
                .getOrDefault(emptyList()),
            settings.stickerPackOrders[session.accountId].orEmpty()
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

        foreignPacks = if (onCrossProviderStickerSelected == null) {
            emptyList()
        } else {
            coroutineScope {
                otherSessions
                    .filter { source -> source.accountId != session.accountId }
                    .map { source ->
                        async {
                            val sourcePacks = orderedStickerPacks(
                                source.loadStickerPacks(StickerPackKind.Sticker)
                                    .getOrDefault(emptyList()),
                                settings.stickerPackOrders[source.accountId].orEmpty()
                            )

                            sourcePacks.mapNotNull { pack ->
                                val items = source.loadStickerSet(pack.id)
                                    .getOrNull()
                                    ?.stickers
                                    .orEmpty()

                                if (items.isEmpty()) {
                                    null
                                } else {
                                    ForeignStickerPack(source, pack, items)
                                }
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
            }
        }

        isLoading = false
    }

    val showForeignPacks = onCrossProviderStickerSelected != null

    val entries = remember(
        recents,
        packs,
        packItems,
        foreignPacks,
        showRecents,
        showForeignPacks
    ) {
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

            if (showForeignPacks) {
                foreignPacks.forEach { foreign ->
                    add(StickerEntry.ForeignPackHeader(foreign))
                    foreign.items.forEach { media ->
                        add(StickerEntry.ForeignSticker(foreign, media))
                    }
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

        // A staggered grid lets each sticker keep its own aspect ratio while
        // the columns pack tightly, instead of forcing square cells that leave
        // empty holes under wide stickers.
        LazyVerticalStaggeredGrid(
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            columns = StaggeredGridCells.Adaptive(60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalItemSpacing = 4.dp
        ) {
            items(
                count = entries.size,
                key = { index -> entries[index].key },
                span = { index ->
                    if (entries[index].isHeader) {
                        StaggeredGridItemSpan.FullLine
                    } else {
                        StaggeredGridItemSpan.SingleLane
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
                        // Removing a recent sticker is destructive, so it asks
                        // for confirmation instead of acting on the gesture.
                        val remove: () -> Unit = {
                            pendingRemoveSticker = entry.media
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(stickerAspect(entry.media))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(stickerAspect(entry.media)),
                        contentAlignment = Alignment.Center
                    ) {
                        PickerSticker(
                            media = entry.media,
                            session = session,
                            size = 52.dp,
                            onClick = { onStickerSelected(entry.media) }
                        )
                    }

                    is StickerEntry.ForeignPackHeader -> PackSectionHeader(
                        pack = entry.pack.pack.copy(title = entry.pack.title),
                        featured = entry.pack.items.firstOrNull(),
                        session = entry.pack.source
                    )

                    is StickerEntry.ForeignSticker -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(stickerAspect(entry.media)),
                        contentAlignment = Alignment.Center
                    ) {
                        PickerSticker(
                            media = entry.media,
                            session = entry.pack.source,
                            size = 52.dp,
                            onClick = {
                                onCrossProviderStickerSelected?.invoke(
                                    entry.pack.source,
                                    entry.media
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    pendingRemoveSticker?.let { sticker ->
        AlertDialog(
            onDismissRequest = { pendingRemoveSticker = null },
            title = { Text("Remove sticker?") },
            text = { Text("Remove this sticker from the recent stickers?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveSticker = null

                        scope.launch {
                            session.removeRecentSticker(sticker).onSuccess {
                                recents = recents.filterNot { media ->
                                    media.fileId == sticker.fileId
                                }
                            }
                        }
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveSticker = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun GifTab(
    session: AccountSession,
    otherSessions: List<AccountSession>,
    onAnimationSelected: (MessageMedia) -> Unit,
    onCrossProviderAnimationSelected: ((AccountSession, MessageMedia) -> Unit)?
) {
    val sources = remember(session, otherSessions) {
        (listOf(session) + otherSessions).distinctBy { it.accountId }
    }

    var gifs by remember(sources) { mutableStateOf<List<SavedGif>>(emptyList()) }
    var isLoading by remember(sources) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sources) {
        isLoading = true

        // A MAX account has no saved GIFs of its own, so the tab would be
        // empty in a MAX chat even though the Telegram accounts have GIFs.
        gifs = sources.flatMap { source ->
            source.loadSavedAnimations()
                .getOrDefault(emptyList())
                .map { media -> SavedGif(source, media) }
        }

        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(
            title = "Saved GIFs",
            onClear = if (gifs.isNotEmpty()) {
                {
                    scope.launch {
                        session.clearSavedAnimations().onSuccess {
                            gifs = gifs.filterNot { gif ->
                                gif.source.accountId == session.accountId
                            }
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

        if (gifs.isEmpty()) {
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
            columns = GridCells.Adaptive(96.dp)
        ) {
            items(gifs, key = { gif -> "${gif.source.accountId}:${gif.media.fileId}" }) { gif ->
                PickerAnimation(
                    media = gif.media,
                    session = gif.source,
                    onClick = {
                        if (gif.source.accountId == session.accountId) {
                            onAnimationSelected(gif.media)
                        } else {
                            onCrossProviderAnimationSelected?.invoke(gif.source, gif.media)
                                ?: onAnimationSelected(gif.media)
                        }
                    },
                    onRemove = {
                        if (gif.source.accountId != session.accountId) return@PickerAnimation

                        scope.launch {
                            gif.source.removeSavedAnimation(gif.media).onSuccess {
                                gifs = gifs.filterNot { entry -> entry === gif }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun PackJumpRow(
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
internal fun PackSectionHeader(
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
internal fun PackFeaturedSticker(
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
internal fun SectionHeader(
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
