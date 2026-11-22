package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable

data class PickedFile(
    val path: String,
    val name: String,
    val mimeType: String? = null
)

interface FilePicker {
    fun pickImages()
    fun pickFiles()
}

/**
 * Images currently on the system clipboard, copied into app storage. Called
 * when the composer receives a paste shortcut, never during composition.
 * Returns an empty list when the clipboard holds no image.
 */
expect fun readClipboardImages(): List<PickedFile>

@Composable
expect fun rememberFilePicker(
    onImagesPicked: (List<PickedFile>) -> Unit,
    onFilesPicked: (List<PickedFile>) -> Unit
): FilePicker
