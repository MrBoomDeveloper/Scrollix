package com.mrboomdev.scrollix.platform.media

import com.mrboomdev.scrollix.data.storage.AndroidAppContext

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.StatFs
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.min

actual suspend fun loadVideoDurationMs(path: String): Long? = withContext(Dispatchers.IO) {
    withRetriever(path) { retriever ->
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }
}

actual suspend fun extractVideoThumbnails(
    path: String,
    count: Int,
    maxWidthPx: Int
): List<ImageBitmap> = withContext(Dispatchers.IO) {
    if (count <= 0) return@withContext emptyList()

    withRetriever(path) { retriever ->
        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L

        val frames = ArrayList<ImageBitmap>(count)

        for (index in 0 until count) {
            // The last frame is taken just before the end, not at it: the
            // exact end often decodes to nothing.
            val timeUs = if (durationMs > 0L) {
                durationMs * 1000L * index / count
            } else {
                0L
            }

            val frame = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: continue

            frames += frame.scaledToWidth(maxWidthPx)
        }

        frames
    }.orEmpty()
}

private inline fun <T> withRetriever(
    path: String,
    block: (MediaMetadataRetriever) -> T
): T? {
    val retriever = MediaMetadataRetriever()

    return try {
        retriever.setDataSource(path)
        block(retriever)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun Bitmap.scaledToWidth(maxWidthPx: Int): ImageBitmap {
    val target = maxWidthPx.coerceAtLeast(1)
    if (width <= target) return asImageBitmap()

    val scaledHeight = (height * (target.toFloat() / width)).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, target, scaledHeight, true).asImageBitmap()
}

actual fun trimOutputPath(): String {
    val name = "scrollix-trim-${UUID.randomUUID()}.mp4"
    return File(AndroidAppContext.context.cacheDir, name).absolutePath
}

actual fun deleteTrimFile(path: String) {
    runCatching { File(path).delete() }
}

actual fun localFileSizeBytes(path: String): Long =
    runCatching { File(path).length() }.getOrDefault(0L)

actual fun freeDiskSpaceBytes(path: String): Long =
    runCatching { StatFs(path).availableBytes }.getOrDefault(0L)

/**
 * Exact re-encode over `[start, end)`: the video is decoded to YUV, encoded
 * again as H.264 and muxed; an AAC audio track is copied with rebased
 * timestamps, other audio codecs are dropped. Cancellation and failures delete
 * the partial output.
 */
actual suspend fun trimVideo(
    request: VideoTrimRequest,
    onProgress: (Float) -> Unit
): Result<TrimmedVideo> = withContext(Dispatchers.IO) {
    val target = File(request.targetPath)
    val scope = coroutineContext
    val checkActive = { scope.ensureActive() }

    try {
        if (!trimRangeValid(request.startMs, request.endMs, 0L)) {
            return@withContext Result.failure(
                IllegalArgumentException("Pick at least one second")
            )
        }

        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val startUs = request.startMs * 1000L
        val endUs = request.endMs * 1000L
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null

        try {
            extractor.setDataSource(request.path)

            val videoIndex = findTrack(extractor, "video/")
                ?: error("This file has no video track")
            val audioIndex = findTrack(extractor, "audio/")
            val audioFormat = audioIndex?.let { extractor.getTrackFormat(it) }

            val copyAudio = !request.muted &&
                audioFormat != null &&
                audioFormat.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC

            val output = MediaMuxer(
                target.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            muxer = output

            // The audio track comes first so the muxer can start as soon as the
            // encoder knows its own format.
            val audioMuxTrack = if (copyAudio) output.addTrack(audioFormat!!) else -1

            val videoFormat = extractor.getTrackFormat(videoIndex)
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Unknown video format")

            // H.264 encoders only take even dimensions.
            val width = (videoFormat.getInteger(MediaFormat.KEY_WIDTH) / 2 * 2)
                .coerceAtLeast(2)
            val height = (videoFormat.getInteger(MediaFormat.KEY_HEIGHT) / 2 * 2)
                .coerceAtLeast(2)

            val frameRate = videoFormat.frameRateOr(30)
            val colorFormat = pickYuvColorFormat()
            val semiPlanar = colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

            val videoDecoder = MediaCodec.createDecoderByType(videoMime)
            decoder = videoDecoder
            videoDecoder.configure(videoFormat, null, null, 0)
            videoDecoder.start()

            val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder = videoEncoder
            videoEncoder.configure(
                MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    width,
                    height
                ).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                    setInteger(MediaFormat.KEY_BIT_RATE, videoFormat.bitRateOr(width, height))
                    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                },
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )
            videoEncoder.start()

            extractor.selectTrack(videoIndex)
            // The decoder needs a keyframe before the first kept frame.
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val info = MediaCodec.BufferInfo()
            var decoderInputDone = false
            var decoderDone = false
            var encoderEosQueued = false
            var encoderDone = false
            var videoMuxTrack = -1
            var lastMuxedUs = -1L
            var muxedFrames = 0L

            while (!encoderDone) {
                checkActive()

                if (!decoderInputDone) {
                    val inputIndex = videoDecoder.dequeueInputBuffer(InputTimeoutUs)

                    if (inputIndex >= 0) {
                        val buffer = videoDecoder.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        val sampleTime = extractor.sampleTime

                        if (size < 0 || sampleTime < 0L || sampleTime > endUs) {
                            videoDecoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            decoderInputDone = true
                        } else {
                            videoDecoder.queueInputBuffer(
                                inputIndex,
                                0,
                                size,
                                sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val outputIndex = videoDecoder.dequeueOutputBuffer(info, InputTimeoutUs)

                    if (outputIndex >= 0) {
                        val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val time = info.presentationTimeUs
                        val keep = !end && time >= startUs && time < endUs

                        if (keep) {
                            val image = videoDecoder.getOutputImage(outputIndex)
                                ?: error("This video cannot be decoded")

                            feedEncoder(
                                encoder = videoEncoder,
                                image = image,
                                presentationTimeUs = time - startUs,
                                width = width,
                                height = height,
                                semiPlanar = semiPlanar
                            )

                            onProgress(trimProgress(startUs, endUs, time))
                        }

                        videoDecoder.releaseOutputBuffer(outputIndex, false)
                        if (end) decoderDone = true
                    }
                }

                if (decoderDone && !encoderEosQueued) {
                    val inputIndex = videoEncoder.dequeueInputBuffer(InputTimeoutUs)

                    if (inputIndex >= 0) {
                        videoEncoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        encoderEosQueued = true
                    }
                }

                val outputIndex = videoEncoder.dequeueOutputBuffer(info, InputTimeoutUs)

                when (outputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoMuxTrack = output.addTrack(videoEncoder.outputFormat)

                        if (!muxerStarted) {
                            output.start()
                            muxerStarted = true
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> if (outputIndex >= 0) {
                        val buffer = videoEncoder.getOutputBuffer(outputIndex)!!

                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }

                        if (info.size > 0 && muxerStarted && videoMuxTrack >= 0) {
                            val time = info.presentationTimeUs

                            // A muxer refuses timestamps that step backwards;
                            // duplicate frames are dropped instead.
                            if (time > lastMuxedUs) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                output.writeSampleData(videoMuxTrack, buffer, info)
                                lastMuxedUs = time
                                muxedFrames++
                            }
                        }

                        videoEncoder.releaseOutputBuffer(outputIndex, false)

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                        }
                    }
                }
            }

            if (muxedFrames == 0L) error("The selected range has no frames")

            if (copyAudio && audioIndex != null && audioMuxTrack >= 0) {
                copyAudioTrack(
                    extractor = extractor,
                    muxer = output,
                    track = audioMuxTrack,
                    audioTrackIndex = audioIndex,
                    startUs = startUs,
                    endUs = endUs,
                    checkActive = checkActive
                )
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { extractor.release() }

            muxer?.let { current ->
                if (muxerStarted) runCatching { current.stop() }
                runCatching { current.release() }
            }
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

private const val InputTimeoutUs = 10_000L

private fun findTrack(extractor: MediaExtractor, prefix: String): Int? {
    for (index in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(index)
            .getString(MediaFormat.KEY_MIME)
            ?: continue

        if (mime.startsWith(prefix)) return index
    }

    return null
}

private fun MediaFormat.frameRateOr(fallback: Int): Int {
    if (!containsKey(MediaFormat.KEY_FRAME_RATE)) return fallback

    return runCatching { getInteger(MediaFormat.KEY_FRAME_RATE) }
        .getOrDefault(fallback)
        .coerceIn(1, 120)
}

private fun MediaFormat.bitRateOr(width: Int, height: Int): Int {
    val source = if (containsKey(MediaFormat.KEY_BIT_RATE)) {
        runCatching { getInteger(MediaFormat.KEY_BIT_RATE) }.getOrDefault(0)
    } else {
        0
    }

    return source.takeIf { it > 0 } ?: (width * height * 6).coerceIn(1_000_000, 20_000_000)
}

private fun pickYuvColorFormat(): Int = runCatching {
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val formats = codec.codecInfo
        .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        .colorFormats

    codec.release()

    when {
        formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

        formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

        else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    }
}.getOrDefault(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)

/** Decoder frames become encoder input without a surface round trip. */
private fun feedEncoder(
    encoder: MediaCodec,
    image: Image,
    presentationTimeUs: Long,
    width: Int,
    height: Int,
    semiPlanar: Boolean
) {
    while (true) {
        val inputIndex = encoder.dequeueInputBuffer(InputTimeoutUs)
        if (inputIndex < 0) continue

        val buffer = encoder.getInputBuffer(inputIndex) ?: return
        val bytes = yuvBytes(image, width, height, semiPlanar)

        if (buffer.capacity() < bytes.size) {
            error("The video encoder buffer is too small")
        }

        buffer.clear()
        buffer.put(bytes)
        encoder.queueInputBuffer(inputIndex, 0, bytes.size, presentationTimeUs, 0)
        return
    }
}

private fun yuvBytes(
    image: Image,
    width: Int,
    height: Int,
    semiPlanar: Boolean
): ByteArray {
    val copyWidth = min(width, image.width)
    val copyHeight = min(height, image.height)
    val chromaWidth = (copyWidth + 1) / 2
    val chromaHeight = (copyHeight + 1) / 2
    val out = ByteArray(width * height * 3 / 2)

    copyPlane(image.planes[0], copyWidth, copyHeight, out, 0, width)

    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    if (semiPlanar) {
        val uvOffset = width * height

        for (row in 0 until chromaHeight) {
            for (column in 0 until chromaWidth) {
                val target = uvOffset + row * width + column * 2
                out[target] = planeByte(uPlane, row, column)
                out[target + 1] = planeByte(vPlane, row, column)
            }
        }
    } else {
        val uOffset = width * height
        val vOffset = uOffset + (width / 2) * (height / 2)

        copyPlane(uPlane, chromaWidth, chromaHeight, out, uOffset, width / 2)
        copyPlane(vPlane, chromaWidth, chromaHeight, out, vOffset, width / 2)
    }

    return out
}

private fun copyPlane(
    plane: Image.Plane,
    width: Int,
    height: Int,
    out: ByteArray,
    offset: Int,
    stride: Int
) {
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    for (row in 0 until height) {
        val base = offset + row * stride
        val available = buffer.capacity() - row * rowStride
        if (available <= 0) continue

        if (pixelStride == 1) {
            buffer.position(row * rowStride)
            buffer.get(out, base, min(width, available))
        } else {
            for (column in 0 until width) {
                out[base + column] = buffer.get(row * rowStride + column * pixelStride)
            }
        }
    }
}

private fun planeByte(plane: Image.Plane, row: Int, column: Int): Byte =
    plane.buffer.get(row * plane.rowStride + column * plane.pixelStride)

private fun copyAudioTrack(
    extractor: MediaExtractor,
    muxer: MediaMuxer,
    track: Int,
    audioTrackIndex: Int,
    startUs: Long,
    endUs: Long,
    checkActive: () -> Unit
) {
    extractor.selectTrack(audioTrackIndex)
    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

    val buffer = ByteBuffer.allocateDirect(512 * 1024)
    val info = MediaCodec.BufferInfo()

    while (true) {
        checkActive()
        buffer.clear()

        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break

        val time = extractor.sampleTime
        if (time < 0L || time >= endUs) break

        if (time >= startUs) {
            buffer.position(0)
            buffer.limit(size)
            info.set(0, size, time - startUs, 0)
            muxer.writeSampleData(track, buffer, info)
        }

        extractor.advance()
    }
}
