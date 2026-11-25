package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.platform.files.PickedFile
import com.mrboomdev.scrollix.ui.components.EmojiPickerDialog
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentReviewDialog(
    attachments: List<PendingAttachment>,
    caption: String,
    isSending: Boolean,
    isMobile: Boolean,
    session: AccountSession?,
    onCaptionChange: (String) -> Unit,
    onInsertEmoji: (fallback: String, emojiId: Long?) -> Unit,
    onAddImages: () -> Unit,
    onAddFiles: () -> Unit,
    /** Moves an attachment in the album; the order is the order it is sent. */
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDiscard: () -> Unit,
    onSend: () -> Unit
) {
    var showEmojiPicker by remember { mutableStateOf(false) }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                    Text(
                        text = "Send ${attachments.size} " +
                            if (attachments.size == 1) "attachment" else "attachments",
                        style = MaterialTheme.typography.titleMedium
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(attachments) { index, attachment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // The number is the position in the album, which
                                // is also the order the attachments are sent.
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = attachment.file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = if (attachment.asPhoto) "Photo" else "File",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.size(32.dp),
                                    enabled = !isSending && index > 0,
                                    onClick = { onMove(index, index - 1) }
                                ) {
                                    Text(
                                        text = "↑",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.size(32.dp),
                                    enabled = !isSending && index < attachments.lastIndex,
                                    onClick = { onMove(index, index + 1) }
                                ) {
                                    Text(
                                        text = "↓",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                IconButton(onClick = { onRemove(index) }) {
                                    Icon(
                                        imageVector = close_icon,
                                        contentDescription = "Remove attachment"
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = caption,
                            onValueChange = onCaptionChange,
                            label = { Text("Caption (optional)") },
                            minLines = 1,
                            maxLines = 4
                        )

                        IconButton(
                            enabled = session != null,
                            onClick = { showEmojiPicker = true }
                        ) {
                            Icon(
                                imageVector = mood_icon,
                                contentDescription = "Insert emoji"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = !isSending &&
                                    attachments.size < MaxAlbumAttachments,
                                onClick = onAddImages
                            ) {
                                Text("Add photos")
                            }

                            TextButton(
                                enabled = !isSending &&
                                    attachments.size < MaxAlbumAttachments,
                                onClick = onAddFiles
                            ) {
                                Text("Add files")
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = !isSending,
                                onClick = onDiscard
                            ) {
                                Text("Discard")
                            }

                            Button(
                                enabled = !isSending,
                                onClick = onSend
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Send")
                                }
                            }
                        }
                    }
        }
    }

    if (isMobile) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value -> value != SheetValue.Hidden }
        )

        // The review sheet blocks back/drag dismissal: the only ways out are
        // Send and Discard, matching the desktop modal.
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = sheetState
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    // Taps outside the card are swallowed, not treated as dismiss.
                    .clickable {},
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    content()
                }
            }
        }
    }

    if (showEmojiPicker && session != null) {
        EmojiPickerDialog(
            isMobile = isMobile,
            session = session,
            // An attachment caption is plain text: stickers and GIFs cannot
            // be inserted into it, so only the emoji tab belongs here.
            emojiOnly = true,
            onDismiss = { showEmojiPicker = false },
            onEmojiSelected = { emoji ->
                onInsertEmoji(emoji, null)
                showEmojiPicker = false
            },
            onCustomEmojiSelected = { emojiId, fallback ->
                onInsertEmoji(fallback, emojiId)
                showEmojiPicker = false
            }
        )
    }
}

@Composable
internal fun PendingAttachmentsTray(
    attachments: List<PendingAttachment>,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "${attachments.size} of $MaxAlbumAttachments attached",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(attachments) { index, attachment ->
                PendingAttachmentTile(
                    attachment = attachment,
                    onRemove = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
internal fun PendingAttachmentTile(
    attachment: PendingAttachment,
    onRemove: () -> Unit
) {
    var preview by remember(attachment.file.path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(attachment.file.path, attachment.asPhoto) {
        if (!attachment.asPhoto) return@LaunchedEffect

        val bytes = withContext(Dispatchers.IO) {
            readFileBytes(attachment.file.path)
        }

        preview = bytes?.let { data ->
            withContext(Dispatchers.Default) { decodeImageBitmap(data) }
        }
    }

    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
    ) {
        val image = preview

        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = attachment.file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (attachment.asPhoto) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = "PHOTO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = attachment.file.extensionLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = attachment.file.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(14.dp),
                imageVector = close_icon,
                contentDescription = "Remove attachment",
                tint = Color.White
            )
        }
    }
}

internal fun PickedFile.extensionLabel(): String {
    val extension = name.substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it.length <= 5 }
        ?: return "FILE"

    return extension.uppercase()
}

internal data class PendingAttachment(
    val file: PickedFile,
    val asPhoto: Boolean
)
