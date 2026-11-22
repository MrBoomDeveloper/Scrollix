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

@Composable
expect fun rememberFilePicker(
    onImagesPicked: (List<PickedFile>) -> Unit,
    onFilesPicked: (List<PickedFile>) -> Unit
): FilePicker
