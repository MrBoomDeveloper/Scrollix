package com.mrboomdev.scrollix.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@Composable
actual fun rememberMediaRecorder(): MediaRecordingController {
    val context = LocalContext.current
    val controller = remember(context) { AndroidMediaRecordingController(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    controller.permissionRequester = {
        launcher.launch(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        )
    }

    return controller
}

@Composable
actual fun VideoNotePreview(controller: MediaRecordingController, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        controller.attachPreviewSurface(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        controller.attachPreviewSurface(holder.surface)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        controller.attachPreviewSurface(null)
                    }
                })
            }
        }
    )
}

private class AndroidMediaRecordingController(
    private val context: Context
) : MediaRecordingController {
    override val supportsVideo: Boolean = true

    var permissionRequester: (() -> Unit)? = null

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var kind = MediaRecordingKind.Voice
    private var previewSurface: Surface? = null

    override fun attachPreviewSurface(surface: Any?) {
        previewSurface = surface as? Surface
    }

    override fun start(kind: MediaRecordingKind): Boolean {
        cancel()

        if (!hasPermissions(kind)) {
            requestPermissions()
            return false
        }

        return runCatching {
            val output = File.createTempFile(
                if (kind == MediaRecordingKind.Voice) "voice-" else "video-note-",
                if (kind == MediaRecordingKind.Voice) ".m4a" else ".mp4",
                context.cacheDir
            )

            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (kind == MediaRecordingKind.VideoNote) {
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            }

            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(64_000)

            if (kind == MediaRecordingKind.VideoNote) {
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                mediaRecorder.setVideoSize(480, 480)
                mediaRecorder.setVideoFrameRate(30)
                mediaRecorder.setVideoEncodingBitRate(1_500_000)
                mediaRecorder.setMaxDuration(MaxVideoNoteDurationMs.toInt())

                // The preview surface has to be known before prepare(), which
                // is why the recorder is started only after the circle preview
                // is composed.
                previewSurface?.let { surface ->
                    runCatching { mediaRecorder.setPreviewDisplay(surface) }
                }
            }

            mediaRecorder.setOutputFile(output.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()

            recorder = mediaRecorder
            file = output
            this.kind = kind
            startedAt = System.currentTimeMillis()
            true
        }.getOrDefault(false)
    }

    override fun stop(): RecordedMedia? {
        val current = recorder ?: return null
        val output = file
        val duration = System.currentTimeMillis() - startedAt

        recorder = null
        file = null

        return runCatching {
            current.stop()
            current.release()

            if (output == null || !output.exists()) return null

            RecordedMedia(
                kind = kind,
                path = output.absolutePath,
                bytes = output.readBytes(),
                durationMs = duration.coerceAtLeast(1L)
            )
        }.getOrNull()
    }

    override fun cancel() {
        val current = recorder
        recorder = null

        current?.let { mediaRecorder ->
            runCatching { mediaRecorder.stop() }
            runCatching { mediaRecorder.release() }
        }

        file?.delete()
        file = null
    }

    override fun release(recording: RecordedMedia) {
        runCatching { File(recording.path).delete() }
    }

    override fun requestPermissions() {
        permissionRequester?.invoke()
    }

    private fun hasPermissions(kind: MediaRecordingKind): Boolean {
        val microphone = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val camera = kind != MediaRecordingKind.VideoNote ||
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        return microphone && camera
    }
}

private const val MaxVideoNoteDurationMs = 60_000L
