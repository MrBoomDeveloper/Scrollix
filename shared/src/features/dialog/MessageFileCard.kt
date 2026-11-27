package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.openFileWith
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.data.storage.shareFile
import com.mrboomdev.scrollix.features.search.formatFileSize
import com.mrboomdev.scrollix.isDesktopPlatform
import com.mrboomdev.scrollix.ui.components.savedMediaFileName

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MessageFileCard(
    media: MessageMedia,
    session: AccountSession?,
    isOutgoing: Boolean
) {
    val scope = rememberCoroutineScope()
    var isDownloading by remember(media.fileId) { mutableStateOf(false) }
    var savedPath by remember(media.fileId) { mutableStateOf<String?>(null) }
    var isPreviewing by remember(media.fileId) { mutableStateOf(false) }
    var previewText by remember(media.fileId) { mutableStateOf<String?>(null) }
    var openFailed by remember(media.fileId) { mutableStateOf(false) }
    var downloadFraction by remember(media.fileId) { mutableStateOf<Float?>(null) }
    var downloadJob by remember(media.fileId) { mutableStateOf<Job?>(null) }

    // Only the download this card started is watched, so a file downloaded
    // elsewhere never makes the bar jump on its own.
    LaunchedEffect(isDownloading, media.fileId) {
        val fileId = media.fileId ?: return@LaunchedEffect
        if (!isDownloading) return@LaunchedEffect

        while (true) {
            downloadFraction = session?.mediaDownloadProgress(fileId)
            delay(150)
        }
    }

    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isOutgoing) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = media.fileTypeLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = media.fileName?.takeIf { it.isNotBlank() } ?: "File",
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = formatFileSize(media.size).ifBlank { "File" },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            enabled = !isDownloading && media.fileId != null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                disabledContentColor = secondaryColor
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            onClick = {
                val fileId = media.fileId ?: return@Button

                downloadJob = scope.launch {
                    isDownloading = true

                    val bytes = session?.loadMediaBytes(fileId, media.fileUniqueId)

                    if (bytes != null) {
                        savedPath = withContext(Dispatchers.IO) {
                            saveFileToDevice(savedMediaFileName(media), bytes)
                        }
                    }

                    isDownloading = false
                    downloadFraction = null
                    downloadJob = null
                }
            }
        ) {
            Text(
                text = when {
                    savedPath != null -> "Saved"
                    isDownloading -> downloadFraction
                        ?.let { fraction -> "${(fraction * 100).toInt()}%" }
                        ?: "Saving..."

                    else -> "Download"
                },
                maxLines = 1
            )
        }

        if (isDownloading) {
            val fraction = downloadFraction
            val barColor = if (isOutgoing) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            }

            if (fraction != null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { fraction.coerceIn(0f, 1f) },
                    color = barColor,
                    trackColor = secondaryColor.copy(alpha = 0.25f)
                )
            } else {
                // Providers that report no progress keep an indeterminate bar.
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = barColor,
                    trackColor = secondaryColor.copy(alpha = 0.25f)
                )
            }

            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    val fileId = media.fileId

                    downloadJob?.cancel()
                    downloadJob = null
                    isDownloading = false
                    downloadFraction = null

                    if (fileId != null) {
                        scope.launch { session?.cancelMediaDownload(fileId) }
                    }
                }
            ) {
                Text(text = "Cancel", maxLines = 1)
            }
        }

        // Only a saved file can be handed to another application; the media
        // cache is not readable by them.
        savedPath?.let { path ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { openFailed = !openFileWith(path, media.mimeType) }
                ) {
                    Text(text = "Open", maxLines = 1)
                }

                if (!isDesktopPlatform()) {
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { shareFile(path, media.mimeType) }
                    ) {
                        Text(text = "Share", maxLines = 1)
                    }
                }
            }
        }

        if (openFailed) {
            Text(
                text = "No app on this device can open this file.",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }

        if (isTextPreviewable(media)) {
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                enabled = !isPreviewing && media.fileId != null,
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    val fileId = media.fileId ?: return@OutlinedButton

                    scope.launch {
                        isPreviewing = true

                        val bytes = session?.loadMediaBytes(fileId, media.fileUniqueId)

                        if (bytes != null) {
                            previewText = previewText(bytes)
                        }

                        isPreviewing = false
                    }
                }
            ) {
                Text(
                    text = if (isPreviewing) "Opening..." else "Preview",
                    maxLines = 1
                )
            }
        }
    }

    previewText?.let { text ->
        FileTextPreviewDialog(
            fileName = media.fileName?.takeIf { it.isNotBlank() } ?: "File",
            text = text,
            onDismiss = { previewText = null }
        )
    }
}

internal fun MessageMedia.fileTypeLabel(): String {
    val fromName = fileName
        ?.substringAfterLast('.', "")
        ?.takeIf { extension ->
            extension.length in 1..4 && extension.all(Char::isLetterOrDigit)
        }

    val fromMime = mimeType
        ?.substringAfter('/', "")
        ?.substringBefore(';')
        ?.substringBefore('+')
        ?.takeIf { subtype ->
            subtype.length in 1..4 && subtype.all(Char::isLetterOrDigit)
        }

    return (fromName ?: fromMime ?: "FILE").uppercase()
}
