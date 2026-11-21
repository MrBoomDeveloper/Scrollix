package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

@Composable
internal fun TgsFolderEmoji(
    emoji: DialogFolderEmoji,
    size: Dp,
    color: Color,
    colorFilter: ColorFilter?,
    contentModifier: Modifier? = null,
    showFallback: Boolean = true
) {
    val data = emoji.data

    if (data == null) {
        StaticFolderEmoji(emoji, size, color, colorFilter, contentModifier, showFallback)
        return
    }

    val compositionResult = rememberLottieComposition(emoji.customEmojiId, data) {
        val json = gunzip(data)?.decodeToString() ?: error("Invalid TGS data")
        LottieCompositionSpec.JsonString(json)
    }
    val composition = compositionResult.value

    if (composition == null) {
        StaticFolderEmoji(emoji, size, color, colorFilter, contentModifier, showFallback)
        return
    }

    val painter = rememberLottiePainter(
        composition = composition,
        iterations = Compottie.IterateForever
    )

    Image(
        painter = painter,
        contentDescription = null,
        modifier = (contentModifier ?: Modifier.size(size))
            .alpha(rememberFadeIn(composition)),
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter
    )
}
