package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.StickerSetInfo
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.launch

/**
 * Modal that shows the whole pack a sticker belongs to, together with the
 * server side action to add or remove the pack from the account.
 */
@Composable
internal fun StickerSetDialog(
    setId: Long,
    session: AccountSession,
    onDismiss: () -> Unit
) {
    var info by remember(setId) { mutableStateOf<StickerSetInfo?>(null) }
    var isLoading by remember(setId) { mutableStateOf(true) }
    var isUpdating by remember(setId) { mutableStateOf(false) }
    var error by remember(setId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(setId) {
        isLoading = true

        session.loadStickerSet(setId)
            .onSuccess { loaded -> info = loaded }
            .onFailure { failure ->
                error = failure.message ?: "Failed to load the sticker pack"
            }

        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = info?.title ?: "Sticker pack",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val count = info?.stickers?.size ?: 0
                        val shortName = info?.name?.takeIf { it.isNotBlank() }

                        if (count > 0) {
                            Text(
                                text = "$count stickers",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                when {
                    isLoading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }

                    error != null -> Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    info == null -> Text(
                        text = "This sticker pack is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    else -> LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(info?.stickers.orEmpty()) { sticker ->
                            StickerSetCell(sticker, session)
                        }
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = info != null && !isUpdating,
                    onClick = {
                        val current = info ?: return@Button

                        scope.launch {
                            isUpdating = true
                            error = null

                            session.setStickerSetInstalled(current.id, !current.isInstalled)
                                .onSuccess {
                                    info = current.copy(isInstalled = !current.isInstalled)
                                }
                                .onFailure { failure ->
                                    error = failure.message ?: "Failed to update the sticker pack"
                                }

                            isUpdating = false
                        }
                    }
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (info?.isInstalled == true) {
                                "Remove from my stickers"
                            } else {
                                "Add to my stickers"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerSetCell(
    media: MessageMedia,
    session: AccountSession
) {
    var data by remember(media.fileId) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(media.fileId) {
        data = session.loadMediaBytes(
            fileId = media.fileId,
            uniqueId = media.stickerUniqueId ?: media.fileUniqueId
        )
    }

    val bytes = data

    val emoji = remember(media.fileId, bytes, media.stickerFormat, media.needsRepainting) {
        DialogFolderEmoji(
            customEmojiId = (media.fileId ?: 0).toLong(),
            offset = 0,
            length = 0,
            fallback = "",
            needsRepainting = media.needsRepainting,
            format = when (media.stickerFormat) {
                MessageStickerFormat.Tgs -> DialogFolderEmojiFormat.Tgs
                MessageStickerFormat.Webm -> DialogFolderEmojiFormat.Webm
                MessageStickerFormat.Webp -> DialogFolderEmojiFormat.Webp
            },
            data = bytes
        )
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center
    ) {
        if (bytes == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        } else {
            FolderEmojiImage(
                emoji = emoji,
                animate = true,
                size = 64.dp,
                color = Color.Unspecified,
                contentModifier = Modifier.fillMaxSize()
            )
        }
    }
}
