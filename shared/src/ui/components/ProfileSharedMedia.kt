package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.SharedMediaKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val SharedMediaPageSize = 30

/** Tabs of the shared media section shown on chat profiles. */
private enum class SharedMediaTab(val label: String, val kind: SharedMediaKind) {
    Photos("Photos", SharedMediaKind.Photo),
    Videos("Videos", SharedMediaKind.Video),
    Gifs("GIFs", SharedMediaKind.Animation),
    Voices("Voice", SharedMediaKind.Voice),
    Links("Links", SharedMediaKind.Link),
    Files("Files", SharedMediaKind.File)
}

/**
 * Lists the media, voice notes, links and files of a chat with tabs and
 * pagination. Every row can jump to the message it came from.
 */
@Composable
fun ProfileSharedMediaSection(
    providerDialogId: String,
    session: AccountSession?,
    onOpenMessage: (Long) -> Unit
) {
    var selectedTab by remember(providerDialogId) { mutableStateOf(SharedMediaTab.Photos) }
    var pages by remember(providerDialogId) {
        mutableStateOf<Map<SharedMediaTab, List<ChatMessage>>>(emptyMap())
    }
    var hasMore by remember(providerDialogId) {
        mutableStateOf<Map<SharedMediaTab, Boolean>>(emptyMap())
    }
    var isLoading by remember(providerDialogId) { mutableStateOf(false) }
    var error by remember(providerDialogId) { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val loaded = pages[selectedTab].orEmpty()

    suspend fun loadPage(fromMessageId: Long, replace: Boolean) {
        val currentSession = session ?: return
        val tab = selectedTab

        isLoading = true
        error = null

        currentSession.loadSharedMedia(
            providerDialogId = providerDialogId,
            kind = tab.kind,
            fromMessageId = fromMessageId,
            limit = SharedMediaPageSize
        ).onSuccess { page ->
            val merged = if (replace) {
                page
            } else {
                (pages[tab].orEmpty() + page).distinctBy { message -> message.messageId }
            }

            pages = pages + (tab to merged)
            hasMore = hasMore + (tab to (page.size >= SharedMediaPageSize))
        }.onFailure { failure ->
            error = failure.message ?: "Failed to load media"
        }

        isLoading = false
    }

    LaunchedEffect(providerDialogId, selectedTab, session) {
        if (pages[selectedTab] == null) {
            loadPage(fromMessageId = 0, replace = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Shared media",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SharedMediaTab.entries) { tab ->
                FilterChip(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    label = { Text(tab.label) }
                )
            }
        }

        error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        when {
            isLoading && loaded.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            loaded.isEmpty() -> Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            // Photos, videos and GIFs are a grid of tiles; the other tabs
            // read better as a list of rows.
            selectedTab.isVisual -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(loaded, key = { message -> message.messageId }) { message ->
                    SharedMediaTile(
                        message = message,
                        tab = selectedTab,
                        session = session,
                        onOpenMessage = onOpenMessage
                    )
                }

                if (hasMore[selectedTab] == true) {
                    item(
                        key = "shared-media-sentinel",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        SharedMediaLoadMore(
                            failure = error,
                            effectKey = Triple(selectedTab, loaded.size, error),
                            onLoadMore = {
                                loadPage(
                                    fromMessageId = loaded.lastOrNull()?.messageId ?: 0,
                                    replace = false
                                )
                            }
                        )
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(loaded) { _, message ->
                    SharedMediaRow(
                        message = message,
                        tab = selectedTab,
                        session = session,
                        onOpenMessage = onOpenMessage
                    )
                }

                if (hasMore[selectedTab] == true) {
                    item(key = "shared-media-sentinel") {
                        SharedMediaLoadMore(
                            failure = error,
                            effectKey = Triple(selectedTab, loaded.size, error),
                            onLoadMore = {
                                loadPage(
                                    fromMessageId = loaded.lastOrNull()?.messageId ?: 0,
                                    replace = false
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The end-of-page marker shared by the grid and the list. Composing it means
 * the next page is near, so it loads without a button; a failure shows a retry.
 */
@Composable
private fun SharedMediaLoadMore(
    failure: String?,
    effectKey: Any,
    onLoadMore: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (failure != null) {
            Text(
                text = failure,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            TextButton(onClick = { scope.launch { onLoadMore() } }) {
                Text("Retry")
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }

        LaunchedEffect(effectKey) {
            if (failure == null) {
                onLoadMore()
            }
        }
    }
}

@Composable
private fun SharedMediaTile(
    message: ChatMessage,
    tab: SharedMediaTab,
    session: AccountSession?,
    onOpenMessage: (Long) -> Unit
) {
    val media = message.media

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onOpenMessage(message.messageId) }
    ) {
        if (media != null) {
            SharedMediaThumbnail(
                media = media,
                session = session,
                modifier = Modifier.fillMaxSize()
            )
        }

        val badge = when (tab) {
            SharedMediaTab.Videos -> "▶"
            SharedMediaTab.Gifs -> "GIF"
            else -> null
        }

        if (badge != null) {
            Text(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                text = badge,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SharedMediaRow(
    message: ChatMessage,
    tab: SharedMediaTab,
    session: AccountSession?,
    onOpenMessage: (Long) -> Unit
) {
    val media = message.media

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenMessage(message.messageId) }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tab.isVisual && media != null) {
            SharedMediaThumbnail(media = media, session = session)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sharedMediaTitle(message, tab),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = sharedMediaDate(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = "Open",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SharedMediaThumbnail(
    media: MessageMedia,
    session: AccountSession?,
    modifier: Modifier = Modifier.size(44.dp)
) {
    var bitmap by remember(media.previewFileId, media.previewUniqueId, session) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(media.previewFileId, media.previewUniqueId, media.minithumbnail, session) {
        bitmap = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) { decodeImageBitmap(bytes) }
        }

        val bytes = session?.loadMediaBytes(
            fileId = media.previewFileId ?: media.fileId,
            uniqueId = media.previewUniqueId ?: media.fileUniqueId
        )

        if (bytes != null) {
            bitmap = withContext(Dispatchers.Default) { decodeImageBitmap(bytes) }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap

        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = when (media.kind) {
                    MessageMediaKind.VoiceNote -> "🎤"
                    MessageMediaKind.VideoNote -> "▶"
                    MessageMediaKind.Video -> "▶"
                    MessageMediaKind.Animation -> "GIF"
                    else -> "📷"
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private val SharedMediaTab.isVisual: Boolean
    get() = this == SharedMediaTab.Photos ||
        this == SharedMediaTab.Videos ||
        this == SharedMediaTab.Gifs

private fun sharedMediaTitle(message: ChatMessage, tab: SharedMediaTab): String {
    return when (tab) {
        SharedMediaTab.Photos -> message.text.ifBlank { "Photo" }
        SharedMediaTab.Videos -> message.text.ifBlank { "Video" }
        SharedMediaTab.Gifs -> message.text.ifBlank { "GIF" }
        SharedMediaTab.Voices -> message.text.ifBlank { "Voice message" }
        SharedMediaTab.Links -> message.linkPreview?.url
            ?: message.text.ifBlank { "Link" }

        SharedMediaTab.Files -> message.text.ifBlank {
            message.media?.fileName?.takeIf { it.isNotBlank() } ?: "File"
        }
    }
}

private fun sharedMediaDate(timestamp: Long): String {
    val time = Instant.fromEpochSeconds(timestamp / 1000)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val day = time.dayOfMonth.toString().padStart(2, '0')
    val month = time.monthNumber.toString().padStart(2, '0')

    return "$day.$month.${time.year}"
}
