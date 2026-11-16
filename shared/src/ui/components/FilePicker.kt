package com.mrboomdev.scrollix.ui.components

import androidx.compose.runtime.Composable

data class PickedFile(
    val path: String,
    val name: String,
    val mimeType: String? = null
)

interface FilePicker {
    fun pickImage()
    fun pickFile()
}

@Composable
expect fun rememberFilePicker(
    onImagePicked: (PickedFile) -> Unit,
    onFilePicked: (PickedFile) -> Unit
): FilePicker
