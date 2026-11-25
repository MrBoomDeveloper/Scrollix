package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.platform.media.AndroidVideoPlayer
import com.mrboomdev.scrollix.platform.media.VideoPlayback

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun VideoRenderSurface(player: VideoPlayback, modifier: Modifier) {
    val context = LocalContext.current
    val textureView = remember { TextureView(context) }

    AndroidView(
        modifier = modifier,
        factory = { textureView }
    )

    DisposableEffect(player, textureView) {
        (player as? AndroidVideoPlayer)?.attach(textureView)
        onDispose { (player as? AndroidVideoPlayer)?.detach() }
    }
}
