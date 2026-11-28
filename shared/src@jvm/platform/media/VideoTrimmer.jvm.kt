package com.mrboomdev.scrollix.platform.media

import com.mrboomdev.scrollix.data.media.silenceFfmpegLogs
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_NONE
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.coroutines.coroutineContext
import kotlin.math.max

actual suspend fun loadVideoDurationMs(path: String): Long? = withContext(Dispatchers.IO) {
    runCatching {
        silenceFfmpegLogs()

        val grabber = FFmpegFrameGrabber(File(path))
        grabber.start()

        try {
            (grabber.lengthInTime / 1000L).takeIf { it > 0L }
        } finally {
            runCatching { grabber.close() }
        }
    }.getOrNull()
}

actual suspend fun extractVideoThumbnails(
    path: String,
    count: Int,
    maxWidthPx: Int
): List<ImageBitmap> = withContext(Dispatchers.IO) {
    if (count <= 0) return@withContext emptyList()

    runCatching {
        silenceFfmpegLogs()

        val grabber = FFmpegFrameGrabber(File(path))
        grabber.start()

        val converter = Java2DFrameConverter()
        val lengthUs = grabber.lengthInTime
        val frames = ArrayList<ImageBitmap>(count)

        try {
            for (index in 0 until count) {
                if (lengthUs > 0L) {
                    grabber.timestamp = lengthUs * index / count
                }

                val frame = grabber.grabImage() ?: continue
                val image = converter.getBufferedImage(frame) ?: continue
                val scaled = image.scaledToWidth(maxWidthPx)

                val bytes = ByteArrayOutputStream()
                ImageIO.write(scaled, "png", bytes)
                decodeImageBitmap(bytes.toByteArray())?.let { bitmap -> frames += bitmap }
            }
        } finally {
            runCatching { grabber.close() }
        }

        frames
    }.getOrDefault(emptyList())
}

private fun BufferedImage.scaledToWidth(maxWidthPx: Int): BufferedImage {
    val target = maxWidthPx.coerceAtLeast(1)
    if (width <= target) return this

    val scaledHeight = max(1, (height * (target.toFloat() / width)).toInt())
    val scaled = BufferedImage(target, scaledHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = scaled.createGraphics()

    graphics.drawImage(this, 0, 0, target, scaledHeight, null)
    graphics.dispose()

    return scaled
}

actual fun trimOutputPath(): String {
    val directory = File(System.getProperty("java.io.tmpdir"), "scrollix-trims")
    directory.mkdirs()

    return File(directory, "scrollix-trim-${UUID.randomUUID()}.mp4").absolutePath
}

actual fun deleteTrimFile(path: String) {
    runCatching { File(path).delete() }
}

actual fun localFileSizeBytes(path: String): Long =
    runCatching { File(path).length() }.getOrDefault(0L)

actual fun freeDiskSpaceBytes(path: String): Long =
    runCatching {
        val file = File(path)
        val directory = if (file.isDirectory) file else file.parentFile ?: file
        directory.usableSpace
    }.getOrDefault(0L)

/**
 * FFmpeg re-encodes the kept range. The grabber is positioned at the range
 * start rather than at a keyframe, which is what makes the cut exact.
 */
actual suspend fun trimVideo(
    request: VideoTrimRequest,
    onProgress: (Float) -> Unit
): Result<TrimmedVideo> = withContext(Dispatchers.IO) {
    val target = File(request.targetPath)
    val scope = coroutineContext

    try {
        if (!trimRangeValid(request.startMs, request.endMs, 0L)) {
            return@withContext Result.failure(
                IllegalArgumentException("Pick at least one second")
            )
        }

        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        silenceFfmpegLogs()

        val startUs = request.startMs * 1000L
        val endUs = request.endMs * 1000L
        val grabber = FFmpegFrameGrabber(File(request.path))
        grabber.start()

        val recorder = FFmpegFrameRecorder(target, grabber.imageWidth, grabber.imageHeight)
        recorder.format = "mp4"
        recorder.videoCodec = AV_CODEC_ID_H264
        recorder.videoBitrate = grabber.videoBitrate.takeIf { it > 0 } ?: 2_500_000
        recorder.frameRate = grabber.frameRate.takeIf { it > 0.0 } ?: 30.0

        if (!request.muted && grabber.audioChannels > 0) {
            recorder.audioCodec = AV_CODEC_ID_AAC
            recorder.sampleRate = grabber.sampleRate
            recorder.audioChannels = grabber.audioChannels
        } else {
            recorder.audioCodec = AV_CODEC_ID_NONE
        }

        recorder.start()

        try {
            grabber.timestamp = startUs

            while (true) {
                scope.ensureActive()

                val frame = grabber.grab() ?: break
                val timestamp = grabber.timestamp
                if (timestamp > endUs) break

                // With audio disabled an audio-only frame has nothing to write.
                if (request.muted && frame.image == null && frame.samples != null) continue

                recorder.record(frame)
                onProgress(trimProgress(startUs, endUs, timestamp))
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
            runCatching { grabber.stop() }
            runCatching { grabber.close() }
        }

        Result.success(
            TrimmedVideo(
                path = target.absolutePath,
                durationMs = request.endMs - request.startMs
            )
        )
    } catch (cancellation: CancellationException) {
        target.delete()
        throw cancellation
    } catch (exception: Exception) {
        target.delete()

        Result.failure(
            IllegalStateException(
                exception.message ?: "Couldn't trim this video",
                exception
            )
        )
    }
}
