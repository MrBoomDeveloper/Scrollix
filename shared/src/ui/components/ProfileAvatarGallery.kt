package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.fetchUrlBytes
import com.mrboomdev.scrollix.ui.icons.close_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows every profile photo of a user, newest first, and can save the
 * currently selected one to the device. Providers without a photo list (MAX)
 * fall back to the profile avatar, which can still be viewed and saved.
 */
@Composable
fun ProfileAvatarGalleryDialog(
    userId: Long,
    session: AccountSession?,
    onDismiss: () -> Unit,
    fallbackAvatar: Avatar? = null
) {
    var photos by remember(userId) { mutableStateOf<List<MessageMedia>>(emptyList()) }
    var isLoading by remember(userId) { mutableStateOf(true) }
    var selectedIndex by remember(userId) { mutableStateOf(0) }
    var bitmap by remember(userId, selectedIndex) { mutableStateOf<ImageBitmap?>(null) }
    var fallbackBytes by remember(userId) { mutableStateOf<ByteArray?>(null) }
    var fallbackBitmap by remember(userId) { mutableStateOf<ImageBitmap?>(null) }
    var isDownloading by remember(userId) { mutableStateOf(false) }
    var status by remember(userId) { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(userId, session) {
        isLoading = true
        status = null

        val result = session?.loadProfilePhotos(userId)
            ?: Result.failure(IllegalStateException("Account session not found"))

        photos = result.getOrDefault(emptyList())
        isLoading = false
        selectedIndex = 0

        result.exceptionOrNull()?.let { error ->
            status = error.message ?: "Failed to load profile photos"
        }
    }

    // No photo list from the provider: show the avatar the profile already
    // loaded instead of an empty gallery.
    LaunchedEffect(isLoading, photos, fallbackAvatar) {
        if (isLoading || photos.isNotEmpty()) return@LaunchedEffect

        val avatar = fallbackAvatar ?: return@LaunchedEffect
        val bytes = avatar.image
            ?: avatar.url?.let { url ->
                withContext(Dispatchers.IO) { fetchUrlBytes(url) }
            }

        fallbackBytes = bytes
        fallbackBitmap = bytes?.let { image ->
            withContext(Dispatchers.Default) { decodeImageBitmap(image) }
        }
    }

    LaunchedEffect(photos, selectedIndex, session) {
        val photo = photos.getOrNull(selectedIndex) ?: return@LaunchedEffect

        bitmap = photo.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) { decodeImageBitmap(bytes) }
        }

        val bytes = session?.loadMediaBytes(
            fileId = photo.fileId,
            uniqueId = photo.fileUniqueId ?: photo.previewUniqueId
        )

        if (bytes != null) {
            bitmap = withContext(Dispatchers.Default) { decodeImageBitmap(bytes) }
        }
    }

    fun download() {
        val photo = photos.getOrNull(selectedIndex)
        val currentSession = session ?: return

        if (photo == null) {
            val bytes = fallbackBytes ?: return

            scope.launch {
                isDownloading = true
                status = null
                status = saveProfilePhoto("profile-photo.jpg", bytes)
                isDownloading = false
            }

            return
        }

        scope.launch {
            isDownloading = true
            status = null

            val bytes = currentSession.loadMediaBytes(
                fileId = photo.fileId,
                uniqueId = photo.fileUniqueId ?: photo.previewUniqueId
            )

            status = if (bytes == null) {
                "Could not load the photo"
            } else {
                saveProfilePhoto("profile-photo-${selectedIndex + 1}.jpg", bytes)
            }

            isDownloading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "Profile photos",
                        style = MaterialTheme.typography.titleLarge
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = close_icon,
                            contentDescription = "Close"
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val image = bitmap ?: fallbackBitmap

                    when {
                        image != null -> Image(
                            bitmap = image,
                            contentDescription = if (photos.isEmpty()) {
                                "Profile photo"
                            } else {
                                "Profile photo ${selectedIndex + 1}"
                            },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        isLoading -> CircularProgressIndicator()

                        else -> Text(
                            text = "No profile photos",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (photos.size > 1) {
                    LazyRow(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(photos) { index, photo ->
                            AvatarGalleryThumbnail(
                                media = photo,
                                session = session,
                                isSelected = index == selectedIndex,
                                onClick = { selectedIndex = index }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = when {
                            photos.isNotEmpty() -> status ?: "${selectedIndex + 1} / ${photos.size}"
                            fallbackBitmap != null -> status ?: "Current profile photo"
                            else -> status.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Button(
                        enabled = (photos.isNotEmpty() || fallbackBytes != null) && !isDownloading,
                        onClick = ::download
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                        }

                        Text("Download")
                    }
                }
            }
        }
    }
}

private suspend fun saveProfilePhoto(fileName: String, bytes: ByteArray): String {
    val path = saveFileToDevice(fileName = fileName, bytes = bytes)
    return if (path != null) "Saved to $path" else "Could not save the photo"
}

@Composable
private fun AvatarGalleryThumbnail(
    media: MessageMedia,
    session: AccountSession?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var bitmap by remember(media.previewFileId, media.previewUniqueId, session) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(media.previewFileId, media.previewUniqueId, media.minithumbnail, session) {
        bitmap = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) { decodeImageBitmap(bytes) }
        }

        val bytes = session?.loadMediaBytes(
            fileId = media.previewFileId ?: media.fileId,
            uniqueId = media.previewUniqueId ?: media.fileUniqueId
        )

        if (bytes != null) {
            bitmap = withContext(Dispatchers.Default) { decodeImageBitmap(bytes) }
        }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
