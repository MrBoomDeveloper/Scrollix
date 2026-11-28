package com.mrboomdev.scrollix.ui.components.photoeditor

import com.mrboomdev.scrollix.platform.media.EditableSource
import com.mrboomdev.scrollix.platform.media.drawEditorTextLayers
import com.mrboomdev.scrollix.platform.media.encodeEditedImage

/**
 * Renders the document into pixels: geometry first, then color, then the
 * painted layers, then the platform text pass (fonts differ per platform).
 * Preview and the final file use this one function, so what the user sees is
 * what gets uploaded.
 */
suspend fun renderEditorDocument(
    source: EditableSource,
    document: EditorDocument
): PixelImage {
    var image = source.image.rotatedAndFlipped(
        turns = document.rotation,
        flipH = document.flipH,
        flipV = document.flipV
    )

    image = image.cropped(document.crop)

    document.filter.matrix?.let { matrix ->
        image = image.withColorMatrix(matrix)
    }

    image = image.applyBlurRegions(document.layers.filterIsInstance<BlurLayer>())
    image = image.drawStrokes(document.layers.filterIsInstance<StrokeLayer>())

    val overlays = document.layers.filter { layer ->
        layer is TextLayer || layer is StickerLayer
    }

    if (overlays.isNotEmpty()) {
        image = drawEditorTextLayers(image, overlays)
    }

    return image
}

/** Renders at full editing resolution and writes the result to [targetPath]. */
suspend fun bakeEditorDocument(
    source: EditableSource,
    document: EditorDocument,
    targetPath: String
): Boolean {
    val image = renderEditorDocument(source, document)
    val keepAlpha = source.hasAlpha && targetPath.endsWith(".png", ignoreCase = true)

    return encodeEditedImage(image, targetPath, keepAlpha)
}
