package com.mrboomdev.scrollix.platform.media

import com.mrboomdev.scrollix.data.storage.AndroidAppContext
import com.mrboomdev.scrollix.ui.components.photoeditor.EditorLayer
import com.mrboomdev.scrollix.ui.components.photoeditor.PixelImage
import com.mrboomdev.scrollix.ui.components.photoeditor.StickerLayer
import com.mrboomdev.scrollix.ui.components.photoeditor.TextLayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

actual suspend fun loadEditableSource(path: String, maxEdge: Int): EditableSource? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)

            val options = BitmapFactory.Options().apply {
                inSampleSize = editorSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            }

            val decoded = BitmapFactory.decodeFile(path, options) ?: return@runCatching null

            // BitmapFactory ignores the EXIF tag, so the turn is burned into
            // the pixels here; the baked file therefore carries none.
            val bitmap = decoded.applyingExifOrientation(readExifOrientation(path))
            if (bitmap !== decoded) decoded.recycle()

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val hasAlpha = bitmap.hasAlpha()
            bitmap.recycle()

            EditableSource(
                image = PixelImage(width, height, pixels),
                hasAlpha = hasAlpha,
                extension = path.substringAfterLast('.', "jpg").lowercase()
            )
        }.getOrNull()
    }

/** Power-of-two sample that keeps both sides within [maxEdge]. */
internal fun editorSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1

    var sample = 1
    while (max(width, height) / (sample * 2) >= maxEdge) {
        sample *= 2
    }

    return sample
}

actual suspend fun drawEditorTextLayers(
    image: PixelImage,
    layers: List<EditorLayer>
): PixelImage = withContext(Dispatchers.Default) {
    val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val longestSide = max(image.width, image.height).toFloat()

    for (layer in layers) {
        when (layer) {
            is TextLayer -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = layer.color.toInt()
                    textSize = (layer.size * longestSide).coerceAtLeast(1f)
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(
                        Typeface.DEFAULT,
                        when {
                            layer.bold && layer.italic -> Typeface.BOLD_ITALIC
                            layer.bold -> Typeface.BOLD
                            layer.italic -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        }
                    )
                }

                drawCenteredText(
                    canvas = canvas,
                    text = layer.text,
                    centerX = layer.x * image.width,
                    centerY = layer.y * image.height,
                    paint = paint
                )
            }

            is StickerLayer -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = (layer.size * longestSide).coerceAtLeast(1f)
                    textAlign = Paint.Align.CENTER
                }

                drawCenteredText(
                    canvas = canvas,
                    text = layer.glyph,
                    centerX = layer.x * image.width,
                    centerY = layer.y * image.height,
                    paint = paint
                )
            }

            else -> Unit
        }
    }

    val out = IntArray(image.width * image.height)
    bitmap.getPixels(out, 0, image.width, 0, 0, image.width, image.height)
    bitmap.recycle()

    PixelImage(image.width, image.height, out)
}

/** Draws text with its visual center at ([centerX], [centerY]). */
private fun drawCenteredText(
    canvas: Canvas,
    text: String,
    centerX: Float,
    centerY: Float,
    paint: Paint
) {
    val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
    canvas.drawText(text, centerX, baseline, paint)
}

actual suspend fun encodeEditedImage(
    image: PixelImage,
    path: String,
    hasAlpha: Boolean
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val bitmap = Bitmap.createBitmap(
            image.pixels,
            image.width,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        val format = if (hasAlpha) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }

        val target = File(path)
        target.parentFile?.mkdirs()

        target.outputStream().use { output ->
            bitmap.compress(format, 95, output)
        }

        bitmap.recycle()
        true
    }.getOrDefault(false)
}

actual fun editorOutputPath(extension: String): String {
    val name = "scrollix-edit-${UUID.randomUUID()}.$extension"
    return File(AndroidAppContext.context.cacheDir, name).absolutePath
}

actual fun deleteEditorFile(path: String) {
    runCatching { File(path).delete() }
}

actual fun pixelImageToImageBitmap(image: PixelImage): ImageBitmap? = runCatching {
    Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}.getOrNull()

/** The EXIF header sits at the front; a large file is not read whole. */
private fun readExifOrientation(path: String): Int = runCatching {
    File(path).inputStream().use { stream ->
        val buffer = ByteArray(128 * 1024)
        val read = stream.read(buffer)

        if (read <= 0) 1 else jpegOrientation(buffer.copyOf(read))
    }
}.getOrDefault(1)

private fun Bitmap.applyingExifOrientation(orientation: Int): Bitmap {
    if (orientation == 1) return this

    val matrix = Matrix()

    when (orientation) {
        2 -> matrix.setScale(-1f, 1f)
        3 -> matrix.setRotate(180f)
        4 -> matrix.setScale(1f, -1f)
        5 -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        6 -> matrix.setRotate(90f)
        7 -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }

        8 -> matrix.setRotate(-90f)
        else -> return this
    }

    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
