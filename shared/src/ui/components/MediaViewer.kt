package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.provider.MediaSession
import com.mrboomdev.scrollix.platform.PlatformBackHandler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaViewerRequest(
    val media: MessageMedia,
    val session: MediaSession?,
    val isVideo: Boolean,
    /** Caption of the message the media belongs to; empty when it has none. */
    val caption: String = ""
)

data class MediaGalleryRequest(
    val media: List<MessageMedia>,
    val initialIndex: Int,
    val session: MediaSession?,
    /** Caption of the album's message, shown over every attachment. */
    val caption: String = ""
)

object MediaViewer {
    private val _request = MutableStateFlow<MediaViewerRequest?>(null)
    val request: StateFlow<MediaViewerRequest?> = _request.asStateFlow()

    private val _gallery = MutableStateFlow<MediaGalleryRequest?>(null)
    val gallery: StateFlow<MediaGalleryRequest?> = _gallery.asStateFlow()

    fun showImage(
        media: MessageMedia,
        session: MediaSession?,
        caption: String = ""
    ) {
        _gallery.value = null
        _request.value = MediaViewerRequest(
            media = media,
            session = session,
            isVideo = false,
            caption = caption
        )
    }

    fun showVideo(
        media: MessageMedia,
        session: MediaSession?,
        caption: String = ""
    ) {
        _gallery.value = null
        _request.value = MediaViewerRequest(
            media = media,
            session = session,
            isVideo = true,
            caption = caption
        )
    }

    fun showGallery(
        media: List<MessageMedia>,
        initialIndex: Int,
        session: MediaSession?,
        caption: String = ""
    ) {
        if (media.isEmpty()) return

        _request.value = null
        _gallery.value = MediaGalleryRequest(
            media = media,
            initialIndex = initialIndex.coerceIn(0, media.lastIndex),
            session = session,
            caption = caption
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
            caption = currentGallery.caption,
            onClose = MediaViewer::close
        )

        return
    }

    val currentRequest = current ?: return

    if (currentRequest.isVideo) {
        FullscreenVideoViewer(
            media = currentRequest.media,
            session = currentRequest.session,
            caption = currentRequest.caption,
            onClose = MediaViewer::close
        )
    } else {
        FullscreenImageViewer(
            media = currentRequest.media,
            session = currentRequest.session,
            caption = currentRequest.caption,
            onClose = MediaViewer::close
        )
    }
}
