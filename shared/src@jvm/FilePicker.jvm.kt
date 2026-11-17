package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFilePicker(
    onImagePicked: (PickedFile) -> Unit,
    onFilePicked: (PickedFile) -> Unit
): FilePicker {
    val scope = rememberCoroutineScope()

    return remember(onImagePicked, onFilePicked) {
        object : FilePicker {
            override fun pickImage() {
                scope.launch {
                    chooseFile(imagesOnly = true)?.let(onImagePicked)
                }
            }

            override fun pickFile() {
                scope.launch {
                    chooseFile(imagesOnly = false)?.let(onFilePicked)
                }
            }
        }
    }
}

private suspend fun chooseFile(imagesOnly: Boolean): PickedFile? {
    return withContext(Dispatchers.IO) {
        val type = if (imagesOnly) {
            // Explicit extensions keep WebP selectable on desktop, where the
            // native image filter doesn't always include it.
            FileKitType.File("jpg", "jpeg", "png", "gif", "webp", "bmp")
        } else {
            FileKitType.File()
        }

        val file = runCatching {
            FileKit.openFilePicker(type = type)
        }.getOrNull() ?: return@withContext null

        PickedFile(
            path = file.path,
            name = file.name
        )
    }
}
