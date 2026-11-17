package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.provider.AccountSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaViewerRequest(
    val media: MessageMedia,
    val session: AccountSession?,
    val isVideo: Boolean
)

object MediaViewer {
    private val _request = MutableStateFlow<MediaViewerRequest?>(null)
    val request: StateFlow<MediaViewerRequest?> = _request.asStateFlow()

    fun showImage(media: MessageMedia, session: AccountSession?) {
        _request.value = MediaViewerRequest(media = media, session = session, isVideo = false)
    }

    fun showVideo(media: MessageMedia, session: AccountSession?) {
        _request.value = MediaViewerRequest(media = media, session = session, isVideo = true)
    }

    fun close() {
        _request.value = null
    }
}

@Composable
fun MediaViewerOverlay() {
    val request by MediaViewer.request.collectAsState()
    val current = request ?: return

    if (current.isVideo) {
        FullscreenVideoViewer(
            media = current.media,
            session = current.session,
            onClose = MediaViewer::close
        )
    } else {
        FullscreenImageViewer(
            media = current.media,
            session = current.session,
            onClose = MediaViewer::close
        )
    }
}
