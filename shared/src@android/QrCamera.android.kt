package com.mrboomdev.scrollix.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
actual fun PlatformQrCamera(
    modifier: Modifier,
    onQrCode: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnQrCode by rememberUpdatedState(onQrCode)
    val latestOnError by rememberUpdatedState(onError)

    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var requestedPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted

        if (!granted) {
            latestOnError("Camera permission is required to scan QR codes.")
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !requestedPermission) {
            requestedPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasPermission) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "Camera access is required to scan the QR code. " +
                    "Allow the permission and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }

        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val isDecoded = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )

    LaunchedEffect(previewView, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            try {
                val provider = future.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val reader = MultiFormatReader().apply {
                    setHints(
                        mapOf(
                            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                            DecodeHintType.TRY_HARDER to true
                        )
                    )
                }

                analysis.setAnalyzer(executor) { proxy ->
                    try {
                        if (isDecoded.get()) return@setAnalyzer

                        val link = decodeQrCode(proxy, reader) ?: return@setAnalyzer

                        if (isDecoded.compareAndSet(false, true)) {
                            previewView.post { latestOnQrCode(link) }
                        }
                    } finally {
                        proxy.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (throwable: Throwable) {
                latestOnError(
                    throwable.message ?: "The camera is not available on this device"
                )
            }
        }, context.mainExecutor)
    }
}

private fun decodeQrCode(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    val mediaImage = imageProxy.image ?: return null
    val plane = mediaImage.planes.firstOrNull() ?: return null
    val rowStride = plane.rowStride
    val height = mediaImage.height

    if (rowStride <= 0 || height <= 0) return null

    val data = ByteArray(rowStride * height)
    val buffer = plane.buffer
    buffer.rewind()
    buffer.get(data, 0, minOf(buffer.remaining(), data.size))

    return try {
        val source = PlanarYUVLuminanceSource(
            data,
            rowStride,
            height,
            0,
            0,
            minOf(mediaImage.width, rowStride),
            height,
            false
        )

        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Throwable) {
        null
    }
}
