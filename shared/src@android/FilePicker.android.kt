package com.mrboomdev.scrollix.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
actual fun rememberFilePicker(
    onImagePicked: (PickedFile) -> Unit,
    onFilePicked: (PickedFile) -> Unit
): FilePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                copyToCache(context, uri)?.let(onImagePicked)
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                copyToCache(context, uri)?.let(onFilePicked)
            }
        }
    }

    return remember(imageLauncher, fileLauncher, onImagePicked, onFilePicked) {
        object : FilePicker {
            override fun pickImage() {
                imageLauncher.launch("image/*")
            }

            override fun pickFile() {
                fileLauncher.launch("*/*")
            }
        }
    }
}

private suspend fun copyToCache(context: Context, uri: Uri): PickedFile? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: uri.lastPathSegment ?: "file"

            val mimeType = resolver.getType(uri)
            val extension = name.substringAfterLast('.', "")
                .takeIf { it.isNotBlank() }
                ?.let { ".$it" }
                .orEmpty()

            val target = File(
                context.cacheDir,
                "scrollix-upload-${UUID.randomUUID()}$extension"
            )

            val input = resolver.openInputStream(uri) ?: return@runCatching null
            input.use { source ->
                target.outputStream().use { output ->
                    source.copyTo(output)
                }
            }

            PickedFile(
                path = target.absolutePath,
                name = name,
                mimeType = mimeType
            )
        }.getOrNull()
    }
}
