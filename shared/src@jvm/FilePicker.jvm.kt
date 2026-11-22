package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFilePicker(
    onImagesPicked: (List<PickedFile>) -> Unit,
    onFilesPicked: (List<PickedFile>) -> Unit
): FilePicker {
    val scope = rememberCoroutineScope()

    return remember(onImagesPicked, onFilesPicked) {
        object : FilePicker {
            override fun pickImages() {
                scope.launch {
                    chooseFiles(imagesOnly = true).let(onImagesPicked)
                }
            }

            override fun pickFiles() {
                scope.launch {
                    chooseFiles(imagesOnly = false).let(onFilesPicked)
                }
            }
        }
    }
}

private suspend fun chooseFiles(imagesOnly: Boolean): List<PickedFile> {
    return withContext(Dispatchers.IO) {
        val type = if (imagesOnly) {
            // Explicit extensions keep WebP selectable on desktop, where the
            // native image filter doesn't always include it.
            FileKitType.File("jpg", "jpeg", "png", "gif", "webp", "bmp")
        } else {
            FileKitType.File()
        }

        val files = runCatching {
            FileKit.openFilePicker(
                type = type,
                mode = FileKitMode.Multiple()
            )
        }.getOrNull().orEmpty()

        files.map { file ->
            PickedFile(
                path = file.path,
                name = file.name
            )
        }
    }
}
