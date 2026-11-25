package com.mrboomdev.scrollix.features.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.UndiscoveredMessagesMessageId
import com.mrboomdev.scrollix.data.model.asSessionException
import com.mrboomdev.scrollix.data.model.groupMessageAlbums
import com.mrboomdev.scrollix.data.outbox.OutboxAttachment
import com.mrboomdev.scrollix.data.outbox.OutboxEntryKind
import com.mrboomdev.scrollix.data.outbox.OutboxEntryState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DialogScrollPositionStore
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.moved
import com.mrboomdev.scrollix.openDialer
import com.mrboomdev.scrollix.resources.Res
import com.mrboomdev.scrollix.resources.wallpaper
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.ChatAnimationPreview
import com.mrboomdev.scrollix.ui.components.ChatSticker
import com.mrboomdev.scrollix.ui.components.ChatVideoPreview
import com.mrboomdev.scrollix.ui.components.ComposerInputRow
import com.mrboomdev.scrollix.ui.components.EmojiPicker
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.ForumTopicsDialog
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.MediaRecordingKind
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.components.MessageDatePill
import com.mrboomdev.scrollix.ui.components.MessageGallery
import com.mrboomdev.scrollix.ui.components.MessageThreadDialog
import com.mrboomdev.scrollix.ui.components.MessageThreadRail
import com.mrboomdev.scrollix.ui.components.PickedFile
import com.mrboomdev.scrollix.ui.components.PlatformBackHandler
import com.mrboomdev.scrollix.ui.components.RecordedMedia
import com.mrboomdev.scrollix.ui.components.RecordedMediaDialog
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.components.VideoNotePreview
import com.mrboomdev.scrollix.ui.components.VoiceMessagePlayer
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.components.messageDate
import com.mrboomdev.scrollix.ui.components.readClipboardImages
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.components.rememberMediaRecorder
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.ui.icons.verified_icon
import com.mrboomdev.scrollix.ui.navigation.SharedProfileElement
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.provider.AccountSession
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State


internal const val MaxAlbumAttachments = 10

/**
 * Keeps decoded wallpapers outside the composition: switching dialogs (or
 * recreating the dialog screen) can show the previous image immediately
 * instead of flashing the default wallpaper.
 */
internal object WallpaperImageCache {
    private val byDialog = mutableMapOf<String, ImageBitmap>()

    var last: ImageBitmap? = null
        private set

    fun exact(key: String): ImageBitmap? = byDialog[key]

    fun put(key: String, image: ImageBitmap) {
        byDialog[key] = image
        last = image
    }
}

@Composable
internal fun PickerPopupAnimation(
    visibleState: MutableTransitionState<Boolean>,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(160)) + scaleIn(
            initialScale = 0.94f,
            animationSpec = tween(160)
        ),
        exit = fadeOut(tween(120)) + scaleOut(
            targetScale = 0.94f,
            animationSpec = tween(120)
        )
    ) {
        content()
    }
}

/**
 * Shifts or drops custom emoji spans after the draft was edited, so the
 * entities keep pointing at the text they were inserted with.
 */
internal fun adjustCustomEmojiSpans(
    oldText: String,
    newText: String,
    spans: List<CustomEmojiSpan>
): List<CustomEmojiSpan> {
    if (spans.isEmpty() || oldText == newText) return spans

    var prefix = 0
    while (
        prefix < oldText.length &&
        prefix < newText.length &&
        oldText[prefix] == newText[prefix]
    ) {
        prefix++
    }

    var suffix = 0
    while (
        suffix < oldText.length - prefix &&
        suffix < newText.length - prefix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) {
        suffix++
    }

    val removed = oldText.length - prefix - suffix
    val inserted = newText.length - prefix - suffix
    val delta = inserted - removed
    val editEnd = prefix + removed

    return spans.mapNotNull { span ->
        when {
            span.end <= prefix -> span

            span.start >= editEnd -> span.copy(
                start = span.start + delta,
                end = span.end + delta
            )

            else -> null
        }
    }
}

internal fun wallpaperBrush(wallpaper: ChatWallpaper): Brush {
    val colors = wallpaper.colors.map { color ->
        Color(color or (0xFF shl 24))
    }

    return when (colors.size) {
        0 -> SolidColor(Color.Transparent)
        1 -> SolidColor(colors.first())
        else -> Brush.verticalGradient(colors)
    }
}

@Composable

internal fun DialogWallpaperEffects(
    settings: AppSettings,
    dialog: Dialog?,
    session: AccountSession?,
    appWallpaper: MutableState<ImageBitmap?>,
    wallpaperImage: MutableState<ImageBitmap?>
) {
        LaunchedEffect(settings.hasCustomWallpaper, settings.wallpaperUpdatedAt) {
            appWallpaper.value = if (settings.hasCustomWallpaper) {
                val bytes = withContext(Dispatchers.IO) {
                    AppSettingsStore.readWallpaper()
                }

                bytes?.let { data ->
                    withContext(Dispatchers.Default) {
                        decodeImageBitmap(data)
                    }
                }
            } else {
                null
            }
        }

        LaunchedEffect(dialog?.providerDialogId, dialog?.wallpaper, session) {
            val currentDialog = dialog ?: return@LaunchedEffect
            val wallpaper = currentDialog.wallpaper

            if (wallpaper == null) {
                wallpaperImage.value = null
                return@LaunchedEffect
            }

            val cacheKey = buildString {
                append(currentDialog.providerDialogId)
                append(':')
                append(wallpaper.fileUniqueId ?: wallpaper.fileId)
                append(':')
                append(wallpaper.thumbnailUniqueId ?: wallpaper.thumbnailFileId)
            }

            WallpaperImageCache.exact(cacheKey)?.let { cached ->
                wallpaperImage.value = cached
                return@LaunchedEffect
            }

            var image: ImageBitmap? = null
            val thumbnailFileId = wallpaper.thumbnailFileId

            // Cached media is addressed by the durable remote unique id, so a
            // custom wallpaper is restored right away instead of waiting for TDLib
            // to replay the chat: the file ids stored in a cached dialog are only
            // valid for the run that produced them.
            val thumbnailBytes = wallpaper.thumbnailUniqueId
                ?.let { uniqueId -> session?.loadCachedMedia(uniqueId) }
                ?: thumbnailFileId?.let { fileId -> session?.loadFile(fileId)?.getOrNull() }

            if (thumbnailBytes != null) {
                image = withContext(Dispatchers.Default) {
                    decodeImageBitmap(thumbnailBytes)
                }
            }

            val fileId = wallpaper.fileId

            if (image == null && fileId != null && fileId != thumbnailFileId) {
                val bytes = wallpaper.fileUniqueId
                    ?.let { uniqueId -> session?.loadCachedMedia(uniqueId) }
                    ?: session?.loadFile(fileId)?.getOrNull()

                if (bytes != null) {
                    // Animated wallpapers (video/TGS) can't be decoded as a bitmap,
                    // in that case the thumbnail stays as the wallpaper.
                    image = withContext(Dispatchers.Default) {
                        decodeImageBitmap(bytes)
                    }
                }
            }

            image?.let { decoded ->
                WallpaperImageCache.put(cacheKey, decoded)
                wallpaperImage.value = decoded
            }
        }
}
