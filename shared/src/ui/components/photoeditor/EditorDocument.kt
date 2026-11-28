package com.mrboomdev.scrollix.ui.components.photoeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A point in 0..1 of the edited image. */
data class NormPoint(val x: Float, val y: Float)

/**
 * A crop window in normalized coordinates of the image *after* rotation and
 * flipping, which is the picture the user sees while cropping.
 */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    val isFull: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    companion object {
        val Full = CropRect(left = 0f, top = 0f, right = 1f, bottom = 1f)
    }
}

/** The smallest crop side, so a window can never collapse to nothing. */
internal const val MinCropSize = 0.05f

enum class EditorAspect(val label: String, val ratio: Float?) {
    Free("Free", null),
    Square("1:1", 1f),
    Portrait("4:5", 4f / 5f),
    Landscape("16:9", 16f / 9f)
}

enum class EditorTool(val label: String) {
    Crop("Crop"),
    Filter("Filter"),
    Draw("Draw"),
    Text("Text"),
    Sticker("Sticker"),
    Blur("Blur")
}

enum class BlurMode(val label: String) {
    Blur("Blur"),
    Pixelate("Pixelate")
}

/** Presets are 4×5 color matrices; the last column is an offset in 0..255. */
enum class FilterPreset(val label: String, val matrix: FloatArray?) {
    None("None", null),

    Warm(
        "Warm",
        floatArrayOf(
            1.08f, 0f, 0f, 0f, 6f,
            0f, 1.02f, 0f, 0f, 2f,
            0f, 0f, 0.92f, 0f, -4f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    Cool(
        "Cool",
        floatArrayOf(
            0.92f, 0f, 0f, 0f, -4f,
            0f, 1f, 0f, 0f, 2f,
            0f, 0f, 1.1f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    Mono(
        "Mono",
        floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    Contrast(
        "Punch",
        floatArrayOf(
            1.32f, 0f, 0f, 0f, -38f,
            0f, 1.32f, 0f, 0f, -38f,
            0f, 0f, 1.32f, 0f, -38f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    Fade(
        "Fade",
        floatArrayOf(
            0.86f, 0.05f, 0.05f, 0f, 26f,
            0.05f, 0.86f, 0.05f, 0f, 26f,
            0.05f, 0.05f, 0.86f, 0f, 26f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    Vintage(
        "Vintage",
        floatArrayOf(
            0.92f, 0.16f, 0.08f, 0f, 8f,
            0.08f, 0.86f, 0.1f, 0f, 6f,
            0.08f, 0.12f, 0.74f, 0f, 12f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

/** One painted edit. Every layer is normalized to the cropped result. */
sealed interface EditorLayer

data class StrokeLayer(
    val points: List<NormPoint>,
    /** ARGB, alpha included. */
    val color: Long,
    /** Line width as a fraction of the longest image side. */
    val width: Float
) : EditorLayer

data class BlurLayer(
    val points: List<NormPoint>,
    /** Brush radius as a fraction of the longest image side. */
    val radius: Float,
    val pixelate: Boolean
) : EditorLayer

data class TextLayer(
    val text: String,
    val x: Float,
    val y: Float,
    /** Font size as a fraction of the longest image side. */
    val size: Float,
    val color: Long = 0xFFFFFFFF,
    val bold: Boolean = false,
    val italic: Boolean = false
) : EditorLayer

data class StickerLayer(
    val glyph: String,
    val x: Float,
    val y: Float,
    /** Glyph size as a fraction of the longest image side. */
    val size: Float
) : EditorLayer

/**
 * The non-destructive description of an edit. Nothing here touches pixels; the
 * source file is only read when the picture is rendered or baked.
 */
data class EditorDocument(
    /** Clockwise quarter turns, 0..3. */
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val crop: CropRect = CropRect.Full,
    val filter: FilterPreset = FilterPreset.None,
    val layers: List<EditorLayer> = emptyList()
) {
    val hasEdits: Boolean
        get() = rotation % 4 != 0 ||
            flipH ||
            flipV ||
            !crop.isFull ||
            filter != FilterPreset.None ||
            layers.isNotEmpty()

    fun rotatedClockwise(): EditorDocument = copy(
        rotation = (rotation + 1) % 4,
        crop = rotateCropClockwise(crop),
        layers = layers.map(EditorLayer::rotatedClockwise)
    )

    fun flippedHorizontally(): EditorDocument = copy(
        flipH = !flipH,
        crop = CropRect(
            left = 1f - crop.right,
            top = crop.top,
            right = 1f - crop.left,
            bottom = crop.bottom
        ),
        layers = layers.map(EditorLayer::flippedHorizontally)
    )

    fun flippedVertically(): EditorDocument = copy(
        flipV = !flipV,
        crop = CropRect(
            left = crop.left,
            top = 1f - crop.bottom,
            right = crop.right,
            bottom = 1f - crop.top
        ),
        layers = layers.map(EditorLayer::flippedVertically)
    )

    /** Changes the crop window and keeps the layers where the user put them. */
    fun withCrop(next: CropRect): EditorDocument {
        if (next == crop) return this

        return copy(
            crop = next,
            layers = layers.map { layer -> layer.remapped(crop, next) }
        )
    }

    fun withLayer(layer: EditorLayer): EditorDocument = copy(layers = layers + layer)

    fun removingLastLayer(): EditorDocument {
        if (layers.isEmpty()) return this
        return copy(layers = layers.dropLast(1))
    }
}

internal fun rotateCropClockwise(rect: CropRect) = CropRect(
    left = 1f - rect.bottom,
    top = rect.left,
    right = 1f - rect.top,
    bottom = rect.right
)

internal fun NormPoint.rotatedClockwise() = NormPoint(x = 1f - y, y = x)

internal fun NormPoint.flippedHorizontally() = NormPoint(x = 1f - x, y = y)

internal fun NormPoint.flippedVertically() = NormPoint(x = x, y = 1f - y)

internal fun EditorLayer.rotatedClockwise(): EditorLayer = when (this) {
    is StrokeLayer -> copy(points = points.map(NormPoint::rotatedClockwise))
    is BlurLayer -> copy(points = points.map(NormPoint::rotatedClockwise))
    is TextLayer -> copy(x = 1f - y, y = x)
    is StickerLayer -> copy(x = 1f - y, y = x)
}

internal fun EditorLayer.flippedHorizontally(): EditorLayer = when (this) {
    is StrokeLayer -> copy(points = points.map(NormPoint::flippedHorizontally))
    is BlurLayer -> copy(points = points.map(NormPoint::flippedHorizontally))
    is TextLayer -> copy(x = 1f - x)
    is StickerLayer -> copy(x = 1f - x)
}

internal fun EditorLayer.flippedVertically(): EditorLayer = when (this) {
    is StrokeLayer -> copy(points = points.map(NormPoint::flippedVertically))
    is BlurLayer -> copy(points = points.map(NormPoint::flippedVertically))
    is TextLayer -> copy(y = 1f - y)
    is StickerLayer -> copy(y = 1f - y)
}

/**
 * Moves a normalized point from the old crop window into the new one, so an
 * arrow drawn before a crop stays on the same part of the picture.
 */
internal fun NormPoint.remapped(old: CropRect, new: CropRect): NormPoint {
    if (new.width <= 0f || new.height <= 0f) return this

    val sourceX = old.left + x * old.width
    val sourceY = old.top + y * old.height

    return NormPoint(
        x = (sourceX - new.left) / new.width,
        y = (sourceY - new.top) / new.height
    )
}

internal fun EditorLayer.remapped(old: CropRect, new: CropRect): EditorLayer = when (this) {
    is StrokeLayer -> copy(points = points.map { point -> point.remapped(old, new) })
    is BlurLayer -> copy(points = points.map { point -> point.remapped(old, new) })
    is TextLayer -> {
        val moved = NormPoint(x, y).remapped(old, new)
        copy(x = moved.x, y = moved.y)
    }

    is StickerLayer -> {
        val moved = NormPoint(x, y).remapped(old, new)
        copy(x = moved.x, y = moved.y)
    }
}

/**
 * Moves one corner of [crop] to the normalized point ([x], [y]) while the
 * opposite corner stays put. Corners are 0 = top-left, 1 = top-right,
 * 2 = bottom-left, 3 = bottom-right. A ratio keeps the window proportional;
 * [imageAspect] is the width/height of the rotated source.
 */
fun resizeCrop(
    crop: CropRect,
    corner: Int,
    x: Float,
    y: Float,
    targetRatio: Float?,
    imageAspect: Float
): CropRect {
    val left = if (corner == 0 || corner == 2) {
        x.coerceAtMost(crop.right - MinCropSize)
    } else {
        crop.left
    }

    val right = if (corner == 1 || corner == 3) {
        x.coerceAtLeast(crop.left + MinCropSize)
    } else {
        crop.right
    }

    val top = if (corner == 0 || corner == 1) {
        y.coerceAtMost(crop.bottom - MinCropSize)
    } else {
        crop.top
    }

    val bottom = if (corner == 2 || corner == 3) {
        y.coerceAtLeast(crop.top + MinCropSize)
    } else {
        crop.bottom
    }

    var result = CropRect(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f)
    )

    if (targetRatio != null && imageAspect > 0f) {
        result = fitCropToRatio(result, corner, targetRatio, imageAspect)
    }

    return result
}

/** Grows or shrinks the window height so its pixel ratio matches [targetRatio]. */
internal fun fitCropToRatio(
    crop: CropRect,
    corner: Int,
    targetRatio: Float,
    imageAspect: Float
): CropRect {
    val normalizedRatio = (targetRatio / imageAspect).coerceAtLeast(0.0001f)
    val height = (crop.width / normalizedRatio).coerceIn(MinCropSize, 1f)

    return when (corner) {
        0 -> CropRect(
            left = crop.left,
            top = (crop.bottom - height).coerceAtLeast(0f),
            right = crop.right,
            bottom = crop.bottom
        )

        1 -> CropRect(
            left = crop.left,
            top = (crop.bottom - height).coerceAtLeast(0f),
            right = crop.right,
            bottom = crop.bottom
        )

        2 -> CropRect(
            left = crop.left,
            top = crop.top,
            right = crop.right,
            bottom = (crop.top + height).coerceAtMost(1f)
        )

        else -> CropRect(
            left = crop.left,
            top = crop.top,
            right = crop.right,
            bottom = (crop.top + height).coerceAtMost(1f)
        )
    }
}

/** A centred window of [aspect] that fits inside the whole rotated picture. */
fun centredCropFor(aspect: EditorAspect, imageAspect: Float): CropRect {
    val ratio = aspect.ratio ?: return CropRect.Full
    if (imageAspect <= 0f) return CropRect.Full

    val normalizedRatio = ratio / imageAspect

    return if (normalizedRatio <= 1f) {
        val width = normalizedRatio
        CropRect(
            left = (1f - width) / 2f,
            top = 0f,
            right = (1f + width) / 2f,
            bottom = 1f
        )
    } else {
        val height = 1f / normalizedRatio
        CropRect(
            left = 0f,
            top = (1f - height) / 2f,
            right = 1f,
            bottom = (1f + height) / 2f
        )
    }
}

/**
 * Undo/redo over whole [EditorDocument]s. A drag runs inside one gesture, so a
 * brush stroke is a single undo step instead of one per pointer move.
 */
class EditorSession(initial: EditorDocument = EditorDocument()) {
    var document by mutableStateOf(initial)
        private set

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = mutableListOf<EditorDocument>()
    private val redoStack = mutableListOf<EditorDocument>()

    fun apply(next: EditorDocument) {
        if (next == document) return

        undoStack += document
        redoStack.clear()
        document = next
        refresh()
    }

    fun beginGesture() {
        undoStack += document
        redoStack.clear()
        refresh()
    }

    fun updateGesture(next: EditorDocument) {
        if (next != document) document = next
    }

    fun endGesture() = refresh()

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack += document
        document = previous
        refresh()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack += document
        document = next
        refresh()
    }

    fun reset() = apply(EditorDocument())

    private fun refresh() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}
