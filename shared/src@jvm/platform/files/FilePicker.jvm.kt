package com.mrboomdev.scrollix.platform.files

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
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

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

actual fun readClipboardImages(): List<PickedFile> {
    val contents = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
    }.getOrNull() ?: return emptyList()

    return runCatching<List<PickedFile>> {
        when {
            contents.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
                val image = contents.getTransferData(DataFlavor.imageFlavor) as? Image

                image?.let { value -> listOfNotNull(writeClipboardImage(value)) }
                    ?: emptyList()
            }

            contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                val files = contents.getTransferData(DataFlavor.javaFileListFlavor)
                    as? List<*> ?: emptyList<Any>()

                files.filterIsInstance<File>().mapNotNull { file ->
                    val isImage = file.extension.lowercase() in ClipboardImageExtensions
                    if (!isImage || !file.isFile) null else PickedFile(
                        path = file.absolutePath,
                        name = file.name
                    )
                }
            }

            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

private val ClipboardImageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private fun writeClipboardImage(image: Image): PickedFile? {
    val directory = File(
        System.getProperty("java.io.tmpdir"),
        "scrollix-paste"
    ).apply { mkdirs() }

    val target = File(directory, "clipboard-${UUID.randomUUID()}.png")
    if (!ImageIO.write(imageToBuffered(image), "png", target)) return null

    return PickedFile(path = target.absolutePath, name = "Pasted image.png")
}

private fun imageToBuffered(image: Image): java.awt.image.BufferedImage {
    if (image is java.awt.image.BufferedImage) return image

    val buffered = java.awt.image.BufferedImage(
        image.getWidth(null),
        image.getHeight(null),
        java.awt.image.BufferedImage.TYPE_INT_ARGB
    )

    val graphics = buffered.createGraphics()
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()
    return buffered
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
