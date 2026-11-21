package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage

@Composable
actual fun PlatformQrCamera(
    modifier: Modifier,
    onQrCode: (String) -> Unit,
    onError: (String) -> Unit
) {
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val latestOnQrCode by rememberUpdatedState(onQrCode)
    val latestOnError by rememberUpdatedState(onError)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            var grabber: FFmpegFrameGrabber? = null
            val converter = Java2DFrameConverter()

            try {
                // FFmpeg has no notion of "device numbers", so the input format
                // and device name have to be given per platform.
                val created = openCamera()
                    ?: throw IllegalStateException(
                        "No camera was found. Connect a webcam and try again."
                    )

                grabber = created

                val reader = MultiFormatReader().apply {
                    setHints(
                        mapOf(
                            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                            DecodeHintType.TRY_HARDER to true
                        )
                    )
                }

                while (currentCoroutineContext().isActive) {
                    val frame = created.grabImage() ?: continue
                    val image = converter.convert(frame) ?: continue

                    val link = try {
                        reader.decode(
                            BinaryBitmap(
                                HybridBinarizer(BufferedImageLuminanceSource(image))
                            )
                        ).text
                    } catch (_: Throwable) {
                        null
                    }

                    val bitmap = image.toComposeImageBitmap()

                    withContext(Dispatchers.Main) {
                        preview = bitmap
                    }

                    if (link != null) {
                        withContext(Dispatchers.Main) {
                            latestOnQrCode(link)
                        }

                        break
                    }
                }
            } catch (throwable: Throwable) {
                withContext(Dispatchers.Main) {
                    val message = throwable.message
                        ?: "The camera is not available on this device"

                    error = message
                    latestOnError(message)
                }
            } finally {
                runCatching { grabber?.stop() }
                runCatching { grabber?.release() }
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val image = preview

        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            error != null -> Text(
                modifier = Modifier.padding(16.dp),
                text = error ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            else -> CircularProgressIndicator()
        }
    }
}

/** Opens the first camera FFmpeg can grab from on this platform. */
private fun openCamera(): FFmpegFrameGrabber? {
    val os = System.getProperty("os.name").orEmpty().lowercase()

    val attempts: List<Pair<String?, String>> = when {
        os.contains("win") -> listOf(
            "dshow" to "video=Integrated Camera",
            "dshow" to "video=USB Video Device",
            "dshow" to "video=HD Webcam",
            "gdigrab" to "desktop"
        )

        os.contains("mac") || os.contains("darwin") -> listOf(
            "avfoundation" to "0",
            "avfoundation" to "1",
            "avfoundation" to "default"
        )

        else -> listOf(
            "video4linux2" to "/dev/video0",
            "video4linux2" to "/dev/video1",
            "video4linux2" to "/dev/video2",
            null to "0"
        )
    }

    for ((format, source) in attempts) {
        val grabber = FFmpegFrameGrabber(source)

        if (format != null) {
            grabber.format = format
        }

        grabber.imageWidth = 640
        grabber.imageHeight = 480

        try {
            grabber.start()
            return grabber
        } catch (_: Throwable) {
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    return null
}

/** ZXing luminance source backed by a regular AWT image. */
private class BufferedImageLuminanceSource(
    private val image: BufferedImage
) : LuminanceSource(image.width, image.height) {

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val width = width
        val target = if (row != null && row.size >= width) row else ByteArray(width)
        val pixels = IntArray(width)
        image.getRGB(0, y, width, 1, pixels, 0, width)

        for (x in 0 until width) {
            val pixel = pixels[x]
            val red = pixel shr 16 and 0xFF
            val green = pixel shr 8 and 0xFF
            val blue = pixel and 0xFF

            target[x] = ((red * 33 + green * 34 + blue * 33) / 100).toByte()
        }

        return target
    }

    override fun getMatrix(): ByteArray {
        val width = width
        val height = height
        val matrix = ByteArray(width * height)

        for (y in 0 until height) {
            getRow(y, null).copyInto(matrix, y * width)
        }

        return matrix
    }

    override fun isCropSupported(): Boolean = false
    override fun isRotateSupported(): Boolean = false
}
