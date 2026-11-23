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

data class MediaGalleryRequest(
    val media: List<MessageMedia>,
    val initialIndex: Int,
    val session: AccountSession?
)

object MediaViewer {
    private val _request = MutableStateFlow<MediaViewerRequest?>(null)
    val request: StateFlow<MediaViewerRequest?> = _request.asStateFlow()

    private val _gallery = MutableStateFlow<MediaGalleryRequest?>(null)
    val gallery: StateFlow<MediaGalleryRequest?> = _gallery.asStateFlow()

    fun showImage(media: MessageMedia, session: AccountSession?) {
        _gallery.value = null
        _request.value = MediaViewerRequest(media = media, session = session, isVideo = false)
    }

    fun showVideo(media: MessageMedia, session: AccountSession?) {
        _gallery.value = null
        _request.value = MediaViewerRequest(media = media, session = session, isVideo = true)
    }

    fun showGallery(
        media: List<MessageMedia>,
        initialIndex: Int,
        session: AccountSession?
    ) {
        if (media.isEmpty()) return

        _request.value = null
        _gallery.value = MediaGalleryRequest(
            media = media,
            initialIndex = initialIndex.coerceIn(0, media.lastIndex),
            session = session
        )
    }

    fun close() {
        _request.value = null
        _gallery.value = null
    }

    val isShowing: Boolean
        get() = _request.value != null || _gallery.value != null
}

@Composable
fun MediaViewerOverlay() {
    val galleryRequest by MediaViewer.gallery.collectAsState()
    val request by MediaViewer.request.collectAsState()
    val currentGallery = galleryRequest
    val current = request

    // The viewer is an overlay, not a route: back closes it and must not pop
    // the chat behind it.
    PlatformBackHandler(enabled = MediaViewer.isShowing, onBack = MediaViewer::close)

    if (currentGallery != null) {
        FullscreenGalleryViewer(
            media = currentGallery.media,
            initialIndex = currentGallery.initialIndex,
            session = currentGallery.session,
            onClose = MediaViewer::close
        )

        return
    }

    val currentRequest = current ?: return

    if (currentRequest.isVideo) {
        FullscreenVideoViewer(
            media = currentRequest.media,
            session = currentRequest.session,
            onClose = MediaViewer::close
        )
    } else {
        FullscreenImageViewer(
            media = currentRequest.media,
            session = currentRequest.session,
            onClose = MediaViewer::close
        )
    }
}
