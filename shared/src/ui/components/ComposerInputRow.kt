package com.mrboomdev.scrollix.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.example.test.microphone_icon
import com.mrboomdev.scrollix.ui.icons.add_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.ui.icons.videocam_icon
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The input row of the chat composer: attachment menu, text field, emoji
 * button, voice/video-note button and send button.
 *
 * Recording controls, reply/edit banners and pickers stay in the dialog screen;
 * this component owns only the row itself so it can be rendered and golden
 * tested without a session.
 */
@Composable
fun ComposerInputRow(
    input: String,
    inputSelection: TextRange,
    inputCustomEmojis: List<CustomEmojiSpan>,
    inputEmojiData: Map<Long, ByteArray>,
    inputEmojiAdaptive: Set<Long>,
    isSending: Boolean,
    canAttach: Boolean,
    hasAttachments: Boolean,
    showAttachmentMenu: Boolean,
    showEmojiPicker: Boolean,
    videoMessageMode: Boolean,
    isRecording: Boolean,
    emojiButtonInteraction: MutableInteractionSource,
    onAttachmentMenuChange: (Boolean) -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onAddPoll: () -> Unit,
    onAddLocation: () -> Unit,
    onEmojiAnchorChanged: (Rect) -> Unit,
    onToggleEmojiPicker: () -> Unit,
    onInputChange: (String, TextRange) -> Unit,
    onPasteImages: () -> Boolean,
    onToggleVideoMessageMode: () -> Unit,
    onBeginRecording: (MediaRecordingKind) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    autoFocus: Boolean = false
) {
    // Desktop requests focus after the row exists, so a freshly opened chat
    // accepts typing without a click. Mobile waits for a tap instead.
    LaunchedEffect(autoFocus, focusRequester) {
        if (!autoFocus || focusRequester == null) return@LaunchedEffect

        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 4.dp)
                // The screen already applies imePadding(), so only the
                // navigation bar may add bottom inset here. Using safeDrawing
                // would add the keyboard height twice.
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .clip(RoundedCornerShape(32.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(32.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .weight(1f)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box {
                IconButton(
                    enabled = canAttach,
                    onClick = { onAttachmentMenuChange(true) }
                ) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = add_icon,
                        contentDescription = "Attach photos or files"
                    )
                }

                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { onAttachmentMenuChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Photos") },
                        onClick = {
                            onAttachmentMenuChange(false)
                            onPickImages()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Files") },
                        onClick = {
                            onAttachmentMenuChange(false)
                            onPickFiles()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Poll") },
                        onClick = {
                            onAttachmentMenuChange(false)
                            onAddPoll()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Location") },
                        onClick = {
                            onAttachmentMenuChange(false)
                            onAddLocation()
                        }
                    )
                }
            }

            BasicTextField(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 35.dp)
                    .then(
                        focusRequester?.let { requester ->
                            Modifier.focusRequester(requester)
                        } ?: Modifier
                    )
                    .onPreviewKeyEvent { event ->
                        val isPaste = event.type == KeyEventType.KeyDown &&
                            event.isCtrlPressed && event.key == Key.V

                        if (isPaste && canAttach && onPasteImages()) {
                            return@onPreviewKeyEvent true
                        }

                        if (
                            event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                            !event.isShiftPressed
                        ) {
                            onSend()
                            true
                        } else {
                            false
                        }
                    },
                value = TextFieldValue(
                    text = input,
                    selection = TextRange(
                        start = inputSelection.start.coerceIn(0, input.length),
                        end = inputSelection.end.coerceIn(0, input.length)
                    )
                ),
                onValueChange = { value ->
                    onInputChange(value.text, value.selection)
                },
                enabled = !isSending,
                maxLines = 10,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),

                // With custom emoji in the draft the field text is drawn by
                // the overlay below, which can render them as stickers; the
                // field keeps the caret and selection.
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (inputCustomEmojis.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.Transparent
                    }
                ),

                decorationBox = { innerTextField ->
                    Box {
                        if (inputCustomEmojis.isNotEmpty()) {
                            CustomEmojiMessageText(
                                modifier = Modifier.fillMaxWidth(),
                                text = input,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                emojis = inputCustomEmojis,
                                data = inputEmojiData,
                                adaptiveEmojis = inputEmojiAdaptive
                            )
                        }

                        innerTextField()

                        if (input.isEmpty()) {
                            Text(
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.secondary,
                                text = if (hasAttachments) {
                                    "Add a caption..."
                                } else {
                                    "Write a message..."
                                }
                            )
                        }
                    }
                }
            )

            IconButton(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        onEmojiAnchorChanged(coordinates.boundsInWindow())
                    }
                    .hoverable(emojiButtonInteraction),
                onClick = onToggleEmojiPicker
            ) {
                Icon(
                    tint = if (showEmojiPicker) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    imageVector = mood_icon,
                    contentDescription = "Emoji and stickers"
                )
            }

            AnimatedVisibility(input.isEmpty() && !hasAttachments) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(videoMessageMode, isRecording) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)

                                // A tap switches the button between voice notes
                                // and circle video notes; holding it starts a
                                // recording in the selected mode that keeps
                                // running after the finger is lifted.
                                val releasedEarly = withTimeoutOrNull(350L) {
                                    waitForUpOrCancellation()
                                }

                                if (releasedEarly != null) {
                                    if (!isRecording) {
                                        onToggleVideoMessageMode()
                                    }

                                    return@awaitEachGesture
                                }

                                onBeginRecording(
                                    if (videoMessageMode) {
                                        MediaRecordingKind.VideoNote
                                    } else {
                                        MediaRecordingKind.Voice
                                    }
                                )

                                waitForUpOrCancellation()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        tint = if (videoMessageMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        imageVector = if (videoMessageMode) {
                            videocam_icon
                        } else {
                            microphone_icon
                        },
                        contentDescription = if (videoMessageMode) {
                            "Tap to switch to voice messages, hold to record a video message"
                        } else {
                            "Tap to switch to video messages, hold to record a voice message"
                        }
                    )
                }
            }
        }

        AnimatedVisibility(input.isNotEmpty() || hasAttachments) {
            FilledIconButton(
                enabled = (input.isNotBlank() || hasAttachments) && !isSending,
                onClick = onSend
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )

                    return@FilledIconButton
                }

                Icon(
                    imageVector = send_icon,
                    contentDescription = null
                )
            }
        }
    }
}
