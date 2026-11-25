package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.features.home.key
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap

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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
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
