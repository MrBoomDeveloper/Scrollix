package com.mrboomdev.scrollix.platform.media

import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.components.photoeditor.EditorLayer
import com.mrboomdev.scrollix.ui.components.photoeditor.PixelImage
import com.mrboomdev.scrollix.ui.components.photoeditor.StickerLayer
import com.mrboomdev.scrollix.ui.components.photoeditor.TextLayer

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

actual suspend fun loadEditableSource(path: String, maxEdge: Int): EditableSource? =
    withContext(Dispatchers.IO) {
        runCatching {
            val original = ImageIO.read(File(path)) ?: return@runCatching null
            if (original.width <= 0 || original.height <= 0) return@runCatching null

            // ImageIO ignores the EXIF tag, so the turn is burned into the
            // pixels here; the baked file therefore carries none.
            val oriented = original.oriented(readExifOrientation(path))
            val longestSide = max(oriented.width, oriented.height)
            val scale = if (longestSide > maxEdge) {
                maxEdge.toFloat() / longestSide
            } else {
                1f
            }

            val width = (oriented.width * scale).roundToInt().coerceAtLeast(1)
            val height = (oriented.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = scaled.createGraphics()

            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            graphics.drawImage(oriented, 0, 0, width, height, null)
            graphics.dispose()

            val pixels = IntArray(width * height)
            scaled.getRGB(0, 0, width, height, pixels, 0, width)

            EditableSource(
                image = PixelImage(width, height, pixels),
                hasAlpha = oriented.colorModel.hasAlpha(),
                extension = path.substringAfterLast('.', "jpg").lowercase()
            )
        }.getOrNull()
    }

actual suspend fun drawEditorTextLayers(
    image: PixelImage,
    layers: List<EditorLayer>
): PixelImage = withContext(Dispatchers.Default) {
    val buffered = image.toBufferedImage()
    val graphics = buffered.createGraphics()

    graphics.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON
    )

    val longestSide = max(image.width, image.height).toFloat()

    for (layer in layers) {
        when (layer) {
            is TextLayer -> {
                val style = when {
                    layer.bold && layer.italic -> Font.BOLD or Font.ITALIC
                    layer.bold -> Font.BOLD
                    layer.italic -> Font.ITALIC
                    else -> Font.PLAIN
                }

                val font = Font("SansSerif", style, (layer.size * longestSide).roundToInt().coerceAtLeast(1))

                paintCentered(
                    graphics = graphics,
                    text = layer.text,
                    centerX = layer.x * image.width,
                    centerY = layer.y * image.height,
                    font = font,
                    color = layer.color.toAwtColor()
                )
            }

            is StickerLayer -> {
                val font = Font(
                    "SansSerif",
                    Font.PLAIN,
                    (layer.size * longestSide).roundToInt().coerceAtLeast(1)
                )

                paintCentered(
                    graphics = graphics,
                    text = layer.glyph,
                    centerX = layer.x * image.width,
                    centerY = layer.y * image.height,
                    font = font,
                    color = Color.WHITE
                )
            }

            else -> Unit
        }
    }

    graphics.dispose()

    val out = IntArray(image.width * image.height)
    buffered.getRGB(0, 0, image.width, image.height, out, 0, image.width)
    PixelImage(image.width, image.height, out)
}

private fun paintCentered(
    graphics: Graphics2D,
    text: String,
    centerX: Float,
    centerY: Float,
    font: Font,
    color: Color
) {
    graphics.font = font
    graphics.color = color

    val metrics = graphics.fontMetrics
    val width = metrics.stringWidth(text)

    graphics.drawString(
        text,
        centerX - width / 2f,
        centerY - (metrics.ascent + metrics.descent) / 2f
    )
}

private fun Long.toAwtColor(): Color {
    val argb = toInt()
    return Color(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
        (argb ushr 24) and 0xFF
    )
}

internal fun PixelImage.toBufferedImage(): BufferedImage {
    val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    buffered.setRGB(0, 0, width, height, pixels, 0, width)
    return buffered
}

actual suspend fun encodeEditedImage(
    image: PixelImage,
    path: String,
    hasAlpha: Boolean
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val target = File(path)
        target.parentFile?.mkdirs()

        val type = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val buffered = BufferedImage(image.width, image.height, type)

        if (hasAlpha) {
            buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        } else {
            val opaque = IntArray(image.pixels.size) { index ->
                image.pixels[index] or (0xFF shl 24)
            }

            buffered.setRGB(0, 0, image.width, image.height, opaque, 0, image.width)
        }

        ImageIO.write(buffered, if (hasAlpha) "png" else "jpg", target)
    }.getOrDefault(false)
}

actual fun editorOutputPath(extension: String): String {
    val directory = File(System.getProperty("java.io.tmpdir"), "scrollix-edits")
    directory.mkdirs()

    return File(directory, "scrollix-edit-${UUID.randomUUID()}.$extension").absolutePath
}

actual fun deleteEditorFile(path: String) {
    runCatching { File(path).delete() }
}

actual fun pixelImageToImageBitmap(image: PixelImage): ImageBitmap? = runCatching {
    val buffered = image.toBufferedImage()
    val output = ByteArrayOutputStream()

    ImageIO.write(buffered, "png", output)
    decodeImageBitmap(output.toByteArray())
}.getOrNull()

/** The EXIF header sits at the front; a large file is not read whole. */
private fun readExifOrientation(path: String): Int = runCatching {
    File(path).inputStream().use { stream ->
        val buffer = ByteArray(128 * 1024)
        val read = stream.read(buffer)

        if (read <= 0) 1 else jpegOrientation(buffer.copyOf(read))
    }
}.getOrDefault(1)

private fun BufferedImage.oriented(orientation: Int): BufferedImage {
    if (orientation == 1) return this

    val swapsSides = orientation in 5..8
    val target = BufferedImage(
        if (swapsSides) height else width,
        if (swapsSides) width else height,
        BufferedImage.TYPE_INT_ARGB
    )

    val transform = when (orientation) {
        2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, width.toDouble(), 0.0)
        3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, width.toDouble(), height.toDouble())
        4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, height.toDouble())
        5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
        6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, height.toDouble(), 0.0)
        7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, height.toDouble(), width.toDouble())
        8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, width.toDouble())
        else -> return this
    }

    val graphics = target.createGraphics()
    graphics.drawImage(this, transform, null)
    graphics.dispose()

    return target
}

/** Kept for symmetry with the Android implementation and for future cropping. */
internal fun scaledSize(width: Int, height: Int, maxEdge: Int, minEdge: Int = 1): Pair<Int, Int> {
    val scale = min(1f, maxEdge.toFloat() / max(width, height))
    return (width * scale).roundToInt().coerceAtLeast(minEdge) to
        (height * scale).roundToInt().coerceAtLeast(minEdge)
}
