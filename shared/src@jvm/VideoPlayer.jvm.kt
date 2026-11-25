package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.platform.media.VideoPlayback

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
actual fun VideoRenderSurface(player: VideoPlayback, modifier: Modifier) {
    val state by player.state.collectAsState()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        state.frame?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
