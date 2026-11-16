package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

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
        var selected: File? = null

        runCatching {
            SwingUtilities.invokeAndWait {
                val chooser = JFileChooser()

                if (imagesOnly) {
                    chooser.isAcceptAllFileFilterUsed = false
                    chooser.fileFilter = FileNameExtensionFilter(
                        "Images",
                        "jpg",
                        "jpeg",
                        "png",
                        "gif",
                        "webp",
                        "bmp"
                    )
                }

                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    selected = chooser.selectedFile
                }
            }
        }

        selected?.let { file ->
            PickedFile(
                path = file.absolutePath,
                name = file.name
            )
        }
    }
}
