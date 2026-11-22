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

actual fun readClipboardImages(): List<PickedFile> {
    // Android exposes clipboard images only through a context-bound reader.
    // The compose picker path is used there instead of a global clipboard call.
    return emptyList()
}

@Composable
actual fun rememberFilePicker(
    onImagesPicked: (List<PickedFile>) -> Unit,
    onFilesPicked: (List<PickedFile>) -> Unit
): FilePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val files = buildList {
                    for (uri in uris) {
                        copyToCache(context, uri)?.let(::add)
                    }
                }

                if (files.isNotEmpty()) onImagesPicked(files)
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val files = buildList {
                    for (uri in uris) {
                        copyToCache(context, uri)?.let(::add)
                    }
                }

                if (files.isNotEmpty()) onFilesPicked(files)
            }
        }
    }

    return remember(imageLauncher, fileLauncher, onImagesPicked, onFilesPicked) {
        object : FilePicker {
            override fun pickImages() {
                imageLauncher.launch("image/*")
            }

            override fun pickFiles() {
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
