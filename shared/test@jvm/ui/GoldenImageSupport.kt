package com.mrboomdev.scrollix.ui.golden

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Compares a rendered Compose surface against a committed PNG.
 *
 * Goldens live next to the tests so they survive a clean checkout. Set
 * `SCROLLIX_UPDATE_GOLDENS=1` to regenerate them deliberately; and inspect the
 * images afterwards. A mismatch writes the actual render under
 * `build/golden-actual/` so it can be diffed with the committed file.
 *
 * The comparison allows a small per-channel tolerance and a small fraction of
 * differing pixels, because text antialiasing depends on the host font stack.
 */
object GoldenImageSupport {
    private const val ChannelTolerance = 8
    private const val MaxDifferentPixelRatio = 0.002

    fun assertMatches(name: String, image: ImageBitmap) {
        val actual = image.toAwtImage()
        val golden = goldenFile(name)

        if (System.getenv("SCROLLIX_UPDATE_GOLDENS") == "1") {
            golden.parentFile.mkdirs()
            check(ImageIO.write(actual, "png", golden)) { "No PNG writer is available" }
            return
        }

        check(golden.isFile) {
            "Missing golden '${golden.path}'. Run with SCROLLIX_UPDATE_GOLDENS=1 to create it."
        }

        val expected = ImageIO.read(golden)
            ?: error("Cannot read golden '${golden.path}'")

        val differentPixels = countDifferentPixels(expected, actual)
        val allowed = (expected.width * expected.height * MaxDifferentPixelRatio).toInt()

        if (differentPixels > allowed) {
            writeActual(name, actual)

            error(
                "Golden '$name' changed: $differentPixels of " +
                    "${expected.width * expected.height} pixels differ " +
                    "(allowed $allowed). Inspect build/golden-actual/$name.png, " +
                    "then regenerate with SCROLLIX_UPDATE_GOLDENS=1 if the change is intended."
            )
        }
    }

    private fun countDifferentPixels(expected: BufferedImage, actual: BufferedImage): Int {
        check(expected.width == actual.width && expected.height == actual.height) {
            "Golden size changed: expected ${expected.width}x${expected.height}, " +
                "rendered ${actual.width}x${actual.height}"
        }

        var different = 0

        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val left = expected.getRGB(x, y)
                val right = actual.getRGB(x, y)

                val differs =
                    abs((left ushr 24 and 0xFF) - (right ushr 24 and 0xFF)) > ChannelTolerance ||
                        abs((left ushr 16 and 0xFF) - (right ushr 16 and 0xFF)) > ChannelTolerance ||
                        abs((left ushr 8 and 0xFF) - (right ushr 8 and 0xFF)) > ChannelTolerance ||
                        abs((left and 0xFF) - (right and 0xFF)) > ChannelTolerance

                if (differs) different++
            }
        }

        return different
    }

    private fun writeActual(name: String, image: BufferedImage) {
        val file = File(projectRoot(), "build/golden-actual/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private fun goldenFile(name: String): File {
        return File(projectRoot(), "shared/test@jvm/ui/golden/$name.png")
    }

    /** Tests may run with the project root or a module directory as `user.dir`. */
    private fun projectRoot(): File {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile

        while (directory != null) {
            if (File(directory, "project.yaml").isFile) return directory
            directory = directory.parentFile
        }

        error("Cannot locate project.yaml above ${System.getProperty("user.dir")}")
    }
}
