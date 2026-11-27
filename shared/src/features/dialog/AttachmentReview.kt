package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.platform.files.PickedFile
import com.mrboomdev.scrollix.ui.components.EmojiPickerDialog
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon
import com.mrboomdev.scrollix.ui.icons.view_list_icon

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentReviewDialog(
    attachments: List<PendingAttachment>,
    caption: String,
    captionCustomEmojis: List<CustomEmojiSpan> = emptyList(),
    captionEmojiData: Map<Long, ByteArray> = emptyMap(),
    captionEmojiAdaptive: Set<Long> = emptySet(),
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
    onSend: () -> Unit,
    /** Opens the schedule picker; null hides the option. */
    onSchedule: (() -> Unit)? = null,
    /** Sends without a notification; null hides the option. */
    onSendSilently: (() -> Unit)? = null
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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

                    // The album order is fixed by dragging a row, which is
                    // also the order the attachments are sent in. The library
                    // owns the gesture and auto-scroll; the arrows that used
                    // to do this are gone.
                    val listState = rememberLazyListState()
                    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                        if (!isSending) {
                            onMove(from.index, to.index)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            items = attachments,
                            key = { _, attachment -> attachment.file.path }
                        ) { index, attachment ->
                            ReorderableItem(
                                state = reorderState,
                                key = attachment.file.path
                            ) { isDragging ->
                                AttachmentReviewRow(
                                    modifier = Modifier.background(
                                        if (isDragging) {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            Color.Transparent
                                        }
                                    ),
                                    index = index,
                                    attachment = attachment,
                                    isSending = isSending,
                                    dragHandleModifier = Modifier.longPressDraggableHandle(
                                        enabled = !isSending
                                    ),
                                    onRemove = { onRemove(index) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CaptionField(
                            modifier = Modifier.weight(1f),
                            caption = caption,
                            customEmojis = captionCustomEmojis,
                            emojiData = captionEmojiData,
                            adaptiveEmojis = captionEmojiAdaptive,
                            onCaptionChange = onCaptionChange
                        )

                        IconButton(
                            enabled = session != null,
                            onClick = {
                                // The picker replaces the keyboard instead of
                                // sharing the screen with it.
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                showEmojiPicker = true
                            }
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

                            SendButton(
                                enabled = !isSending,
                                isSending = isSending,
                                onSend = onSend,
                                onSchedule = onSchedule,
                                onSendSilently = onSendSilently
                            )
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                    color = MaterialTheme.colorScheme.surfaceContainer
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

/**
 * The caption field. Custom emoji are drawn by an overlay, exactly like the
 * composer, so a caption shows the same stickers before it is sent.
 */
@Composable
private fun CaptionField(
    caption: String,
    customEmojis: List<CustomEmojiSpan>,
    emojiData: Map<Long, ByteArray>,
    adaptiveEmojis: Set<Long>,
    onCaptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // The caret and selection are local: the caption itself lives in the
    // shared composer state, which the emoji picker also appends to.
    var value by remember {
        mutableStateOf(TextFieldValue(caption, TextRange(caption.length)))
    }

    LaunchedEffect(caption) {
        if (value.text != caption) {
            value = TextFieldValue(caption, TextRange(caption.length))
        }
    }

    BasicTextField(
        modifier = modifier,
        value = value,
        onValueChange = { updated ->
            value = updated
            onCaptionChange(updated.text)
        },
        maxLines = 4,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        // With custom emoji present the overlay draws the caption, so the
        // field keeps only the caret and selection.
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (customEmojis.isEmpty()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                Color.Transparent
            }
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (customEmojis.isNotEmpty()) {
                    CustomEmojiMessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = caption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        emojis = customEmojis,
                        data = emojiData,
                        adaptiveEmojis = adaptiveEmojis
                    )
                }

                innerTextField()

                if (caption.isEmpty()) {
                    Text(
                        text = "Caption (optional)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    )
}

/**
 * One attachment in the review list: its thumbnail (or file type), the album
 * position and a drag handle. Dragging the handle moves the row along the
 * single vertical axis, which is the album order.
 */
@Composable
private fun AttachmentReviewRow(
    index: Int,
    attachment: PendingAttachment,
    isSending: Boolean,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit
) {
    val isImage = attachment.asPhoto ||
        attachment.file.mimeType?.startsWith("image/") == true
    var preview by remember(attachment.file.path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(attachment.file.path, isImage) {
        if (!isImage) {
            preview = null
            return@LaunchedEffect
        }

        val bytes = withContext(Dispatchers.IO) {
            readFileBytes(attachment.file.path)
        }

        preview = bytes?.let { data ->
            withContext(Dispatchers.Default) { decodeImageBitmap(data) }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            val image = preview

            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = attachment.file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = attachment.file.extensionLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // The number is the position in the album, which is also the
            // order the attachments are sent.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
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

        // Holding the handle drags the row; the rest of it stays tappable.
        Icon(
            modifier = Modifier
                .size(20.dp)
                .then(dragHandleModifier),
            imageVector = view_list_icon,
            tint = MaterialTheme.colorScheme.secondary,
            contentDescription = "Drag to reorder"
        )

        IconButton(
            enabled = !isSending,
            onClick = onRemove
        ) {
            Icon(
                imageVector = close_icon,
                contentDescription = "Remove attachment"
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
