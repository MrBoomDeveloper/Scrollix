package com.mrboomdev.scrollix.ui.components.photoeditor

import com.mrboomdev.scrollix.data.emoji.EmojiCatalog
import com.mrboomdev.scrollix.platform.files.PickedFile
import com.mrboomdev.scrollix.platform.media.EditableSource
import com.mrboomdev.scrollix.platform.media.deleteEditorFile
import com.mrboomdev.scrollix.platform.media.editorOutputPath
import com.mrboomdev.scrollix.platform.media.loadEditableSource
import com.mrboomdev.scrollix.platform.media.pixelImageToImageBitmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** The preview is decoded once at this size; baking re-reads the file larger. */
private const val PreviewMaxEdge = 1280
private const val BakeMaxEdge = 4096

private val BrushColors = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFF4D4F, 0xFFFFB020,
    0xFF34C759, 0xFF3B82F6, 0xFFAF52DE
)

private val BrushWidths = listOf(0.006f, 0.016f, 0.036f)
private val BlurRadii = listOf(0.015f, 0.03f, 0.06f)
private val TextSizes = listOf(0.06f, 0.1f, 0.16f)

/**
 * Full-screen photo editor. Every edit is non-destructive: the document is
 * changed, the preview is re-rendered from the same pipeline that bakes the
 * final file, and the source file is only read again when the user applies.
 */
@Composable
internal fun PhotoEditorDialog(
    path: String,
    onDismiss: () -> Unit,
    onApply: (PickedFile) -> Unit
) {
    val scope = rememberCoroutineScope()
    val session = remember(path) { EditorSession() }

    var source by remember(path) { mutableStateOf<EditableSource?>(null) }
    var preview by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(path) { mutableStateOf(true) }
    var isBaking by remember(path) { mutableStateOf(false) }
    var error by remember(path) { mutableStateOf<String?>(null) }

    var tool by remember(path) { mutableStateOf(EditorTool.Crop) }
    var aspect by remember(path) { mutableStateOf(EditorAspect.Free) }

    var brushColor by remember(path) { mutableStateOf(BrushColors.first()) }
    var brushWidth by remember(path) { mutableStateOf(BrushWidths[1]) }

    var blurMode by remember(path) { mutableStateOf(BlurMode.Blur) }
    var blurRadius by remember(path) { mutableStateOf(BlurRadii[1]) }

    var activePoints by remember(path) { mutableStateOf<List<NormPoint>>(emptyList()) }

    var showTextDialog by remember(path) { mutableStateOf(false) }
    var textValue by remember(path) { mutableStateOf("") }
    var textSize by remember(path) { mutableStateOf(TextSizes[1]) }
    var textColor by remember(path) { mutableStateOf(BrushColors.first()) }

    var dragLayerIndex by remember(path) { mutableStateOf<Int?>(null) }

    LaunchedEffect(path) {
        isLoading = true
        error = null
        source = loadEditableSource(path, PreviewMaxEdge)
        isLoading = false

        if (source == null) {
            error = "Couldn't open this photo"
        }
    }

    // The crop tool needs the uncropped picture so the rest can be brought
    // back by dragging a handle outwards.
    LaunchedEffect(source, session.document, tool) {
        val editable = source ?: return@LaunchedEffect
        val document = if (tool == EditorTool.Crop) {
            session.document.copy(crop = CropRect.Full)
        } else {
            session.document
        }

        delay(40)
        preview = pixelImageToImageBitmap(renderEditorDocument(editable, document))
    }

    fun commitActiveStroke() {
        val points = activePoints
        activePoints = emptyList()
        if (points.isEmpty()) return

        val layer: EditorLayer = if (tool == EditorTool.Blur) {
            BlurLayer(points = points, radius = blurRadius, pixelate = blurMode == BlurMode.Pixelate)
        } else {
            StrokeLayer(points = points, color = brushColor, width = brushWidth)
        }

        session.apply(session.document.withLayer(layer))
    }

    Dialog(
        onDismissRequest = { if (!isBaking) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(Modifier.fillMaxSize()) {
                EditorTopBar(
                    canUndo = session.canUndo,
                    canRedo = session.canRedo,
                    isBaking = isBaking,
                    hasEdits = session.document.hasEdits,
                    onCancel = {
                        if (!isBaking) onDismiss()
                    },
                    onUndo = session::undo,
                    onRedo = session::redo,
                    onReset = session::reset,
                    onApply = {
                        scope.launch {
                            isBaking = true
                            error = null

                            val full = loadEditableSource(path, BakeMaxEdge)

                            if (full == null) {
                                error = "Couldn't open this photo"
                                isBaking = false
                                return@launch
                            }

                            val extension = if (full.hasAlpha) "png" else "jpg"
                            val target = editorOutputPath(extension)
                            val baked = bakeEditorDocument(full, session.document, target)

                            isBaking = false

                            if (!baked) {
                                deleteEditorFile(target)
                                error = "Couldn't save the edited photo"
                                return@launch
                            }

                            onApply(
                                PickedFile(
                                    path = target,
                                    name = editedFileName(path, extension),
                                    mimeType = if (extension == "png") "image/png" else "image/jpeg"
                                )
                            )
                        }
                    }
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val density = LocalDensity.current
                    val containerWidth = with(density) { maxWidth.toPx() }
                    val containerHeight = with(density) { maxHeight.toPx() }
                    val bitmap = preview

                    if (bitmap == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White)
                            } else {
                                Text(
                                    text = error ?: "Couldn't open this photo",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        val imageRect = fitRect(
                            containerWidth = containerWidth,
                            containerHeight = containerHeight,
                            aspect = bitmap.width.toFloat() / bitmap.height
                        )

                        Image(
                            bitmap = bitmap,
                            contentDescription = "Photo being edited",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        when (tool) {
                            EditorTool.Crop -> CropOverlay(
                                rect = imageRect,
                                crop = session.document.crop,
                                aspect = aspect,
                                imageAspect = bitmap.width.toFloat() / bitmap.height,
                                onChange = { next -> session.apply(session.document.withCrop(next)) }
                            )

                            EditorTool.Draw, EditorTool.Blur -> {
                                val overlayColor = if (tool == EditorTool.Blur) {
                                    Color.White.copy(alpha = 0.35f)
                                } else {
                                    Color(brushColor)
                                }

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(imageRect, tool, brushColor, brushWidth, blurRadius, blurMode) {
                                            detectDragGestures(
                                                onDragStart = { position ->
                                                    val point = position.toNormalized(imageRect)
                                                    if (point != null) {
                                                        activePoints = listOf(point)
                                                    }
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val point = change.position.toNormalized(imageRect)
                                                    if (point != null) {
                                                        activePoints = activePoints + point
                                                    }
                                                },
                                                onDragEnd = { commitActiveStroke() },
                                                onDragCancel = { activePoints = emptyList() }
                                            )
                                        }
                                ) {
                                    drawActiveStroke(
                                        points = activePoints,
                                        rect = imageRect,
                                        color = overlayColor,
                                        widthFraction = if (tool == EditorTool.Blur) blurRadius * 2f else brushWidth,
                                        round = tool != EditorTool.Blur || blurMode == BlurMode.Blur
                                    )
                                }
                            }

                            EditorTool.Text, EditorTool.Sticker -> Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(imageRect, session.document.layers, tool) {
                                        detectDragGestures(                                            onDragStart = { position ->
                                                val point = position.toNormalized(imageRect)

                                                if (point != null) {
                                                    val index = session.document.layers.indexOfLast { layer ->
                                                        val center = when (layer) {
                                                            is TextLayer -> NormPoint(layer.x, layer.y)
                                                            is StickerLayer -> NormPoint(layer.x, layer.y)
                                                            else -> null
                                                        }

                                                        center != null && center.distanceTo(point) < 0.12f
                                                    }

                                                    if (index >= 0) {
                                                        dragLayerIndex = index
                                                        session.beginGesture()
                                                    }
                                                }
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                val index = dragLayerIndex

                                                if (index != null) {
                                                    val dx = amount.x / imageRect.width
                                                    val dy = amount.y / imageRect.height

                                                    val layers = session.document.layers.toMutableList()
                                                    val layer = layers.getOrNull(index)

                                                    if (layer != null) {
                                                        layers[index] = when (layer) {
                                                            is TextLayer -> layer.copy(
                                                                x = (layer.x + dx).coerceIn(0f, 1f),
                                                                y = (layer.y + dy).coerceIn(0f, 1f)
                                                            )

                                                            is StickerLayer -> layer.copy(
                                                                x = (layer.x + dx).coerceIn(0f, 1f),
                                                                y = (layer.y + dy).coerceIn(0f, 1f)
                                                            )

                                                            else -> layer
                                                        }

                                                        session.updateGesture(
                                                            session.document.copy(layers = layers)
                                                        )
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                if (dragLayerIndex != null) session.endGesture()
                                                dragLayerIndex = null
                                            },
                                            onDragCancel = {
                                                if (dragLayerIndex != null) session.endGesture()
                                                dragLayerIndex = null
                                            }
                                        )
                                    },
                                // Layers are painted by the shared bake pass; the
                                // canvas only owns the drag gesture.
                                onDraw = { }
                            )

                            EditorTool.Filter -> Unit
                        }
                    }
                }

                error?.takeIf { !isLoading }?.let { message ->
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                val imageAspect = preview?.let { bitmap ->
                    bitmap.width.toFloat() / bitmap.height
                } ?: 1f

                ToolPanel(
                    tool = tool,
                    document = session.document,
                    aspect = aspect,
                    onAspectChange = { next ->
                        aspect = next

                        if (next.ratio != null) {
                            session.apply(
                                session.document.withCrop(
                                    centredCropFor(next, imageAspect)
                                )
                            )
                        }
                    },
                    onRotate = { session.apply(session.document.rotatedClockwise()) },
                    onFlip = { session.apply(session.document.flippedHorizontally()) },
                    onFilterChange = { preset ->
                        session.apply(session.document.copy(filter = preset))
                    },
                    brushColor = brushColor,
                    onBrushColorChange = { brushColor = it },
                    brushWidth = brushWidth,
                    onBrushWidthChange = { brushWidth = it },
                    blurMode = blurMode,
                    onBlurModeChange = { blurMode = it },
                    blurRadius = blurRadius,
                    onBlurRadiusChange = { blurRadius = it },
                    onAddText = { showTextDialog = true },
                    textLayers = session.document.layers.count { layer -> layer is TextLayer },
                    onAddSticker = { glyph ->
                        session.apply(
                            session.document.withLayer(
                                StickerLayer(glyph = glyph, x = 0.5f, y = 0.5f, size = 0.22f)
                            )
                        )
                    },
                    canRemoveLayer = session.document.layers.isNotEmpty(),
                    onRemoveLayer = { session.apply(session.document.removingLastLayer()) }
                )

                ToolTabs(
                    selected = tool,
                    onSelect = { tool = it }
                )
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = { Text("Add text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = textValue,
                        onValueChange = { textValue = it },
                        placeholder = { Text("Text") },
                        singleLine = false,
                        maxLines = 3
                    )

                    Text("Size", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = textSize,
                        onValueChange = { textSize = it.coerceIn(0.03f, 0.3f) },
                        valueRange = 0.03f..0.3f
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrushColors.forEach { color ->
                            ColorDot(
                                color = Color(color),
                                selected = textColor == color,
                                onClick = { textColor = color }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = textValue.isNotBlank(),
                    onClick = {
                        session.apply(
                            session.document.withLayer(
                                TextLayer(
                                    text = textValue,
                                    x = 0.5f,
                                    y = 0.5f,
                                    size = textSize,
                                    color = textColor
                                )
                            )
                        )

                        textValue = ""
                        showTextDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    isBaking: Boolean,
    hasEdits: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(enabled = !isBaking, onClick = onCancel) {
            Text("Cancel", color = Color.White)
        }

        TextButton(enabled = canUndo && !isBaking, onClick = onUndo) {
            Text("Undo", color = if (canUndo) Color.White else Color.White.copy(alpha = 0.35f))
        }

        TextButton(enabled = canRedo && !isBaking, onClick = onRedo) {
            Text("Redo", color = if (canRedo) Color.White else Color.White.copy(alpha = 0.35f))
        }

        Box(Modifier.weight(1f))

        if (hasEdits && !isBaking) {
            TextButton(onClick = onReset) {
                Text("Reset", color = Color.White)
            }
        }

        Button(
            enabled = !isBaking,
            onClick = onApply,
            shape = RoundedCornerShape(50)
        ) {
            if (isBaking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ToolTabs(
    selected: EditorTool,
    onSelect: (EditorTool) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        EditorTool.entries.forEach { entry ->
            TextButton(onClick = { onSelect(entry) }) {
                Text(
                    text = entry.label,
                    color = if (entry == selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolPanel(
    tool: EditorTool,
    document: EditorDocument,
    aspect: EditorAspect,
    onAspectChange: (EditorAspect) -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onFilterChange: (FilterPreset) -> Unit,
    brushColor: Long,
    onBrushColorChange: (Long) -> Unit,
    brushWidth: Float,
    onBrushWidthChange: (Float) -> Unit,
    blurMode: BlurMode,
    onBlurModeChange: (BlurMode) -> Unit,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    onAddText: () -> Unit,
    textLayers: Int,
    onAddSticker: (String) -> Unit,
    canRemoveLayer: Boolean,
    onRemoveLayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (tool) {
            EditorTool.Crop -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorAspect.entries.forEach { entry ->
                        FilterChip(
                            selected = entry == aspect,
                            onClick = { onAspectChange(entry) },
                            label = { Text(entry.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRotate) { Text("Rotate") }
                    TextButton(onClick = onFlip) { Text("Flip") }
                }
            }

            EditorTool.Filter -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPreset.entries.take(6).forEach { preset ->
                        FilterChip(
                            selected = preset == document.filter,
                            onClick = { onFilterChange(preset) },
                            label = { Text(preset.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPreset.entries.drop(6).forEach { preset ->
                        FilterChip(
                            selected = preset == document.filter,
                            onClick = { onFilterChange(preset) },
                            label = { Text(preset.label) }
                        )
                    }
                }
            }

            EditorTool.Draw -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrushColors.forEach { color ->
                        ColorDot(
                            color = Color(color),
                            selected = brushColor == color,
                            onClick = { onBrushColorChange(color) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrushWidths.forEach { width ->
                        FilterChip(
                            selected = brushWidth == width,
                            onClick = { onBrushWidthChange(width) },
                            label = { Text(if (width < 0.01f) "S" else if (width < 0.03f) "M" else "L") }
                        )
                    }

                    TextButton(enabled = canRemoveLayer, onClick = onRemoveLayer) {
                        Text("Remove last")
                    }
                }
            }

            EditorTool.Blur -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlurMode.entries.forEach { mode ->
                        FilterChip(
                            selected = blurMode == mode,
                            onClick = { onBlurModeChange(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlurRadii.forEachIndexed { index, radius ->
                        FilterChip(
                            selected = blurRadius == radius,
                            onClick = { onBlurRadiusChange(radius) },
                            label = { Text(listOf("S", "M", "L")[index]) }
                        )
                    }

                    TextButton(enabled = canRemoveLayer, onClick = onRemoveLayer) {
                        Text("Remove last")
                    }
                }
            }

            EditorTool.Text -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAddText) { Text("Add text") }

                    TextButton(enabled = canRemoveLayer, onClick = onRemoveLayer) {
                        Text("Remove last")
                    }
                }

                Text(
                    text = if (textLayers == 0) {
                        "Drag a text layer to move it."
                    } else {
                        "$textLayers text layer${if (textLayers == 1) "" else "s"} · drag to move"
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            EditorTool.Sticker -> {
                val emojis = remember { EmojiCatalog.first().emojis.take(120) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = canRemoveLayer, onClick = onRemoveLayer) {
                        Text("Remove last")
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 36.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    items(emojis) { emoji ->
                        Text(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onAddSticker(emoji) }
                                .padding(6.dp),
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun CropOverlay(
    rect: Rect,
    crop: CropRect,
    aspect: EditorAspect,
    imageAspect: Float,
    onChange: (CropRect) -> Unit
) {
    var activeCorner by remember { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(rect, crop, aspect, imageAspect) {
                detectDragGestures(
                    onDragStart = { position ->
                        activeCorner = nearestCorner(
                            position = position,
                            rect = rect,
                            crop = crop
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val corner = activeCorner

                        if (corner != null) {
                            val x = ((change.position.x - rect.left) / rect.width).coerceIn(0f, 1f)
                            val y = ((change.position.y - rect.top) / rect.height).coerceIn(0f, 1f)

                            onChange(
                                resizeCrop(
                                    crop = crop,
                                    corner = corner,
                                    x = x,
                                    y = y,
                                    targetRatio = aspect.ratio,
                                    imageAspect = imageAspect
                                )
                            )
                        }
                    },
                    onDragEnd = { activeCorner = null },
                    onDragCancel = { activeCorner = null }
                )
            }
    ) {
        val left = rect.left + crop.left * rect.width
        val top = rect.top + crop.top * rect.height
        val right = rect.left + crop.right * rect.width
        val bottom = rect.top + crop.bottom * rect.height

        val dim = Color.Black.copy(alpha = 0.55f)

        drawRect(dim, topLeft = Offset(rect.left, rect.top), size = androidx.compose.ui.geometry.Size(rect.width, top - rect.top))
        drawRect(
            dim,
            topLeft = Offset(rect.left, bottom),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.bottom - bottom)
        )
        drawRect(
            dim,
            topLeft = Offset(rect.left, top),
            size = androidx.compose.ui.geometry.Size(left - rect.left, bottom - top)
        )
        drawRect(
            dim,
            topLeft = Offset(right, top),
            size = androidx.compose.ui.geometry.Size(rect.right - right, bottom - top)
        )

        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            style = Stroke(width = 1.5.dp.toPx())
        )

        val thirdX = (right - left) / 3f
        val thirdY = (bottom - top) / 3f

        for (index in 1..2) {
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(left + thirdX * index, top),
                end = Offset(left + thirdX * index, bottom),
                strokeWidth = 1.dp.toPx()
            )

            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(left, top + thirdY * index),
                end = Offset(right, top + thirdY * index),
                strokeWidth = 1.dp.toPx()
            )
        }

        val handles = listOf(left to top, right to top, left to bottom, right to bottom)
        handles.forEach { (x, y) ->
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
        }
    }
}

private fun nearestCorner(position: Offset, rect: Rect, crop: CropRect): Int {
    val corners = listOf(
        Offset(rect.left + crop.left * rect.width, rect.top + crop.top * rect.height),
        Offset(rect.left + crop.right * rect.width, rect.top + crop.top * rect.height),
        Offset(rect.left + crop.left * rect.width, rect.top + crop.bottom * rect.height),
        Offset(rect.left + crop.right * rect.width, rect.top + crop.bottom * rect.height)
    )

    return corners.indices.minByOrNull { index ->
        val corner = corners[index]
        val dx = position.x - corner.x
        val dy = position.y - corner.y
        dx * dx + dy * dy
    } ?: 0
}

private fun fitRect(containerWidth: Float, containerHeight: Float, aspect: Float): Rect {
    val safeAspect = if (aspect > 0f) aspect else 1f
    val width = min(containerWidth, containerHeight * safeAspect)
    val height = width / safeAspect

    return Rect(
        left = (containerWidth - width) / 2f,
        top = (containerHeight - height) / 2f,
        right = (containerWidth + width) / 2f,
        bottom = (containerHeight + height) / 2f
    )
}

private fun Offset.toNormalized(rect: Rect): NormPoint? {
    if (rect.width <= 0f || rect.height <= 0f) return null
    if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) return null

    return NormPoint(
        x = ((x - rect.left) / rect.width).coerceIn(0f, 1f),
        y = ((y - rect.top) / rect.height).coerceIn(0f, 1f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawActiveStroke(
    points: List<NormPoint>,
    rect: Rect,
    color: Color,
    widthFraction: Float,
    round: Boolean
) {
    if (points.isEmpty() || rect.width <= 0f) return

    val strokeWidth = max(2f, widthFraction * max(rect.width, rect.height))

    if (!round) {
        points.forEach { point ->
            drawCircle(
                color = color,
                radius = strokeWidth / 2f,
                center = Offset(rect.left + point.x * rect.width, rect.top + point.y * rect.height)
            )
        }
        return
    }

    if (points.size == 1) {
        val point = points.first()
        drawCircle(
            color = color,
            radius = strokeWidth / 2f,
            center = Offset(rect.left + point.x * rect.width, rect.top + point.y * rect.height)
        )
        return
    }

    val path = Path()
    points.forEachIndexed { index, point ->
        val x = rect.left + point.x * rect.width
        val y = rect.top + point.y * rect.height

        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/** `IMG_1234.jpg` becomes `IMG_1234-edited.jpg`. */
internal fun editedFileName(path: String, extension: String): String {
    val original = path.substringAfterLast('/').substringAfterLast('\\')
    val base = original.substringBeforeLast('.', original).ifBlank { "photo" }
    return "$base-edited.$extension"
}
