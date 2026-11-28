package com.mrboomdev.scrollix.ui.components.photoeditor

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** An ARGB pixel buffer shared by both platforms. */
data class PixelImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    val hasAlpha: Boolean
        get() = pixels.any { pixel -> (pixel ushr 24) < 255 }
}

/** Applies a 4×5 matrix to every pixel, in place on a copy. */
fun PixelImage.withColorMatrix(matrix: FloatArray): PixelImage {
    if (matrix.size < 20) return this

    val out = pixels.copyOf()

    for (index in out.indices) {
        val color = out[index]
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val a = (color ushr 24) and 0xFF

        val red = matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4] * 255f
        val green = matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9] * 255f
        val blue = matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14] * 255f
        val alpha = matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19] * 255f

        out[index] = (clampChannel(alpha) shl 24) or
            (clampChannel(red) shl 16) or
            (clampChannel(green) shl 8) or
            clampChannel(blue)
    }

    return PixelImage(width, height, out)
}

private fun clampChannel(value: Float): Int = value.roundToInt().coerceIn(0, 255)

fun PixelImage.rotatedAndFlipped(turns: Int, flipH: Boolean, flipV: Boolean): PixelImage {
    var image = rotatedQuarterTurns(turns)
    if (flipH) image = image.flippedHorizontally()
    if (flipV) image = image.flippedVertically()
    return image
}

fun PixelImage.rotatedQuarterTurns(turns: Int): PixelImage {
    var image = this
    repeat(((turns % 4) + 4) % 4) { image = image.rotatedClockwise() }
    return image
}

internal fun PixelImage.rotatedClockwise(): PixelImage {
    val targetWidth = height
    val targetHeight = width
    val out = IntArray(targetWidth * targetHeight)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val targetX = height - 1 - y
            val targetY = x
            out[targetY * targetWidth + targetX] = pixels[y * width + x]
        }
    }

    return PixelImage(targetWidth, targetHeight, out)
}

internal fun PixelImage.flippedHorizontally(): PixelImage {
    val out = IntArray(pixels.size)

    for (y in 0 until height) {
        for (x in 0 until width) {
            out[y * width + x] = pixels[y * width + (width - 1 - x)]
        }
    }

    return PixelImage(width, height, out)
}

internal fun PixelImage.flippedVertically(): PixelImage {
    val out = IntArray(pixels.size)

    for (y in 0 until height) {
        val sourceRow = (height - 1 - y) * width
        System.arraycopy(pixels, sourceRow, out, y * width, width)
    }

    return PixelImage(width, height, out)
}

/** Copies the window out of the picture; a full window returns the same image. */
fun PixelImage.cropped(rect: CropRect): PixelImage {
    val left = (rect.left * width).roundToInt().coerceIn(0, width - 1)
    val top = (rect.top * height).roundToInt().coerceIn(0, height - 1)
    val right = (rect.right * width).roundToInt().coerceIn(left + 1, width)
    val bottom = (rect.bottom * height).roundToInt().coerceIn(top + 1, height)

    if (left == 0 && top == 0 && right == width && bottom == height) return this

    val targetWidth = right - left
    val targetHeight = bottom - top
    val out = IntArray(targetWidth * targetHeight)

    for (row in 0 until targetHeight) {
        System.arraycopy(
            pixels,
            (top + row) * width + left,
            out,
            row * targetWidth,
            targetWidth
        )
    }

    return PixelImage(targetWidth, targetHeight, out)
}

fun PixelImage.drawStrokes(strokes: List<StrokeLayer>): PixelImage {
    if (strokes.isEmpty()) return this

    val out = pixels.copyOf()
    val longestSide = max(width, height).toFloat()

    for (stroke in strokes) {
        if (stroke.points.isEmpty()) continue

        val radius = (stroke.width * longestSide / 2f).coerceAtLeast(1f)
        val color = stroke.color.toInt()
        val points = stroke.points.map { point ->
            (point.x * width) to (point.y * height)
        }

        paintPolyline(out, width, height, points, radius, color)
    }

    return PixelImage(width, height, out)
}

private fun paintPolyline(
    pixels: IntArray,
    width: Int,
    height: Int,
    points: List<Pair<Float, Float>>,
    radius: Float,
    color: Int
) {
    if (points.size == 1) {
        val (x, y) = points.first()
        stampDisc(pixels, width, height, x, y, radius, color)
        return
    }

    val step = (radius * 0.35f).coerceAtLeast(1f)

    for (index in 0 until points.lastIndex) {
        val (x1, y1) = points[index]
        val (x2, y2) = points[index + 1]
        val distance = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
        val steps = max(1, ceil(distance / step).toInt())

        for (s in 0..steps) {
            val fraction = s / steps.toFloat()
            stampDisc(
                pixels = pixels,
                width = width,
                height = height,
                centerX = x1 + (x2 - x1) * fraction,
                centerY = y1 + (y2 - y1) * fraction,
                radius = radius,
                color = color
            )
        }
    }
}

private fun stampDisc(
    pixels: IntArray,
    width: Int,
    height: Int,
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Int
) {
    val minX = (centerX - radius).toInt().coerceIn(0, width - 1)
    val maxX = (centerX + radius).toInt().coerceIn(0, width - 1)
    val minY = (centerY - radius).toInt().coerceIn(0, height - 1)
    val maxY = (centerY + radius).toInt().coerceIn(0, height - 1)
    val radiusSquared = radius * radius

    for (y in minY..maxY) {
        for (x in minX..maxX) {
            val dx = x - centerX
            val dy = y - centerY
            if (dx * dx + dy * dy > radiusSquared) continue

            blendPixel(pixels, y * width + x, color)
        }
    }
}

private fun blendPixel(pixels: IntArray, index: Int, color: Int) {
    val alpha = (color ushr 24) and 0xFF

    if (alpha == 0) return
    if (alpha == 255) {
        pixels[index] = color
        return
    }

    val source = alpha / 255f
    val target = pixels[index]
    val targetAlpha = ((target ushr 24) and 0xFF) / 255f

    val outAlpha = source + targetAlpha * (1f - source)
    if (outAlpha <= 0f) return

    fun channel(sourceShift: Int, targetShift: Int): Int {
        val sourceValue = ((color shr sourceShift) and 0xFF) * source
        val targetValue = ((target shr targetShift) and 0xFF) * targetAlpha * (1f - source)
        return ((sourceValue + targetValue) / outAlpha).roundToInt().coerceIn(0, 255)
    }

    pixels[index] = ((outAlpha * 255f).roundToInt().coerceIn(0, 255) shl 24) or
        (channel(16, 16) shl 16) or
        (channel(8, 8) shl 8) or
        channel(0, 0)
}

/**
 * Blurs or pixelates the painted regions. Only the bounding box of a region is
 * processed, so a small brush on a large photo stays cheap.
 */
fun PixelImage.applyBlurRegions(regions: List<BlurLayer>): PixelImage {
    if (regions.isEmpty()) return this

    val out = pixels.copyOf()
    val longestSide = max(width, height).toFloat()

    for (region in regions) {
        if (region.points.isEmpty()) continue

        val radius = (region.radius * longestSide).coerceIn(2f, 80f)
        val pad = ceil(radius).toInt() + 1

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        for (point in region.points) {
            val x = (point.x * width).toInt()
            val y = (point.y * height).toInt()
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }

        minX = (minX - pad).coerceIn(0, width - 1)
        minY = (minY - pad).coerceIn(0, height - 1)
        maxX = (maxX + pad).coerceIn(minX, width - 1)
        maxY = (maxY + pad).coerceIn(minY, height - 1)

        val boxWidth = maxX - minX + 1
        val boxHeight = maxY - minY + 1
        if (boxWidth <= 2 || boxHeight <= 2) continue

        val mask = BooleanArray(boxWidth * boxHeight)
        val points = region.points.map { point ->
            (point.x * width - minX) to (point.y * height - minY)
        }

        paintMask(mask, boxWidth, boxHeight, points, radius)

        val area = IntArray(boxWidth * boxHeight)
        for (row in 0 until boxHeight) {
            System.arraycopy(
                out,
                (minY + row) * width + minX,
                area,
                row * boxWidth,
                boxWidth
            )
        }

        val processed = if (region.pixelate) {
            pixelateArea(area, boxWidth, boxHeight, max(2, (radius / 2f).roundToInt()))
        } else {
            blurArea(area, boxWidth, boxHeight, radius.roundToInt().coerceAtLeast(1))
        }

        for (row in 0 until boxHeight) {
            for (column in 0 until boxWidth) {
                if (!mask[row * boxWidth + column]) continue

                out[(minY + row) * width + minX + column] =
                    processed[row * boxWidth + column]
            }
        }
    }

    return PixelImage(width, height, out)
}

private fun paintMask(
    mask: BooleanArray,
    width: Int,
    height: Int,
    points: List<Pair<Float, Float>>,
    radius: Float
) {
    fun stamp(centerX: Float, centerY: Float) {
        val minX = (centerX - radius).toInt().coerceIn(0, width - 1)
        val maxX = (centerX + radius).toInt().coerceIn(0, width - 1)
        val minY = (centerY - radius).toInt().coerceIn(0, height - 1)
        val maxY = (centerY + radius).toInt().coerceIn(0, height - 1)
        val radiusSquared = radius * radius

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy <= radiusSquared) mask[y * width + x] = true
            }
        }
    }

    if (points.size == 1) {
        val (x, y) = points.first()
        stamp(x, y)
        return
    }

    val step = (radius * 0.35f).coerceAtLeast(1f)

    for (index in 0 until points.lastIndex) {
        val (x1, y1) = points[index]
        val (x2, y2) = points[index + 1]
        val distance = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
        val steps = max(1, ceil(distance / step).toInt())

        for (s in 0..steps) {
            val fraction = s / steps.toFloat()
            stamp(x1 + (x2 - x1) * fraction, y1 + (y2 - y1) * fraction)
        }
    }
}

internal fun blurArea(source: IntArray, width: Int, height: Int, radius: Int): IntArray {
    var current = source

    repeat(3) {
        current = boxBlur(current, width, height, radius)
    }

    return current
}

private fun boxBlur(source: IntArray, width: Int, height: Int, radius: Int): IntArray {
    val horizontal = IntArray(source.size)
    val output = IntArray(source.size)

    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var alpha = 0
            var count = 0

            for (offset in -radius..radius) {
                val sampleX = (x + offset).coerceIn(0, width - 1)
                val color = source[y * width + sampleX]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
                alpha += (color ushr 24) and 0xFF
                count++
            }

            horizontal[y * width + x] = (alpha / count shl 24) or
                (red / count shl 16) or
                (green / count shl 8) or
                (blue / count)
        }
    }

    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var alpha = 0
            var count = 0

            for (offset in -radius..radius) {
                val sampleY = (y + offset).coerceIn(0, height - 1)
                val color = horizontal[sampleY * width + x]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
                alpha += (color ushr 24) and 0xFF
                count++
            }

            output[y * width + x] = (alpha / count shl 24) or
                (red / count shl 16) or
                (green / count shl 8) or
                (blue / count)
        }
    }

    return output
}

internal fun pixelateArea(
    source: IntArray,
    width: Int,
    height: Int,
    blockSize: Int
): IntArray {
    val block = blockSize.coerceAtLeast(2)
    val output = source.copyOf()

    var blockY = 0
    while (blockY < height) {
        var blockX = 0

        while (blockX < width) {
            val endX = min(blockX + block, width)
            val endY = min(blockY + block, height)
            var red = 0L
            var green = 0L
            var blue = 0L
            var alpha = 0L
            var count = 0

            for (y in blockY until endY) {
                for (x in blockX until endX) {
                    val color = source[y * width + x]
                    red += (color shr 16) and 0xFF
                    green += (color shr 8) and 0xFF
                    blue += color and 0xFF
                    alpha += (color ushr 24) and 0xFF
                    count++
                }
            }

            if (count > 0) {
                val average = ((alpha / count).toInt() shl 24) or
                    ((red / count).toInt() shl 16) or
                    ((green / count).toInt() shl 8) or
                    (blue / count).toInt()

                for (y in blockY until endY) {
                    for (x in blockX until endX) {
                        output[y * width + x] = average
                    }
                }
            }

            blockX += block
        }

        blockY += block
    }

    return output
}

/** Distance between two normalized points, used for layer hit testing. */
internal fun NormPoint.distanceTo(other: NormPoint): Float {
    val dx = abs(x - other.x)
    val dy = abs(y - other.y)
    return sqrt(dx * dx + dy * dy)
}
