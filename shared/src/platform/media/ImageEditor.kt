package com.mrboomdev.scrollix.platform.media

import androidx.compose.ui.graphics.ImageBitmap
import com.mrboomdev.scrollix.ui.components.photoeditor.EditorLayer
import com.mrboomdev.scrollix.ui.components.photoeditor.PixelImage

/** A decoded source photo, ready to be transformed by the shared pipeline. */
data class EditableSource(
    val image: PixelImage,
    val hasAlpha: Boolean,
    val extension: String
)

/**
 * Decodes [path] with its longest side capped at [maxEdge]. The editor decodes
 * a small copy for the live preview and a large one only when baking.
 */
expect suspend fun loadEditableSource(path: String, maxEdge: Int = 4096): EditableSource?

/**
 * Draws text and emoji layers on top of the pixels. This is the only part of
 * baking that needs platform fonts; everything else is shared code.
 */
expect suspend fun drawEditorTextLayers(
    image: PixelImage,
    layers: List<EditorLayer>
): PixelImage

/** Encodes the pixels: JPEG for opaque photos, PNG when alpha must survive. */
expect suspend fun encodeEditedImage(
    image: PixelImage,
    path: String,
    hasAlpha: Boolean
): Boolean

/** A fresh path in app storage for a baked edit. */
expect fun editorOutputPath(extension: String): String

/** Removes a baked file that will not be sent. */
expect fun deleteEditorFile(path: String)

/** Shows a baked preview without going through the encoder. */
expect fun pixelImageToImageBitmap(image: PixelImage): ImageBitmap?
