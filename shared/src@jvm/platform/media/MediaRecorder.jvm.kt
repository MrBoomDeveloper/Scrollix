package com.mrboomdev.scrollix.platform.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
actual fun rememberMediaRecorder(): MediaRecordingController {
    return remember { JvmMediaRecordingController() }
}

@Composable
actual fun VideoNotePreview(controller: MediaRecordingController, modifier: Modifier) {
    // Desktop has no video note recording, so there is no camera to preview.
    Box(modifier = modifier.background(Color.Black))
}

private class JvmMediaRecordingController : MediaRecordingController {
    override val supportsVideo: Boolean = false

    private val format = AudioFormat(44_100f, 16, 1, true, false)
    private var line: TargetDataLine? = null
    private var thread: Thread? = null
    private var buffer: ByteArrayOutputStream? = null
    private var file: File? = null
    private var startedAt = 0L

    override fun start(kind: MediaRecordingKind): Boolean {
        if (kind == MediaRecordingKind.VideoNote) return false
        cancel()

        return runCatching {
            val dataLine = AudioSystem.getTargetDataLine(format)
            dataLine.open(format)
            dataLine.start()

            val output = ByteArrayOutputStream()
            val target = File.createTempFile("scrollix-voice-", ".wav")

            line = dataLine
            buffer = output
            file = target
            startedAt = System.currentTimeMillis()

            thread = Thread {
                val chunk = ByteArray(4096)

                try {
                    while (line === dataLine) {
                        val read = dataLine.read(chunk, 0, chunk.size)
                        if (read > 0) output.write(chunk, 0, read)
                    }
                } catch (_: Exception) {
                }
            }.apply {
                isDaemon = true
                start()
            }

            true
        }.getOrDefault(false)
    }

    override fun stop(): RecordedMedia? {
        val dataLine = line ?: return null
        val output = buffer
        val target = file
        val duration = System.currentTimeMillis() - startedAt

        line = null
        buffer = null
        file = null
        thread?.interrupt()
        thread = null

        return runCatching {
            dataLine.stop()
            dataLine.close()
        }.let {
            if (output == null || target == null) return null

            val pcm = output.toByteArray()
            val wav = pcm.toWav(format)
            target.writeBytes(wav)

            RecordedMedia(
                kind = MediaRecordingKind.Voice,
                path = target.absolutePath,
                bytes = wav,
                durationMs = duration.coerceAtLeast(1L)
            )
        }
    }

    override fun cancel() {
        line?.let { dataLine ->
            runCatching { dataLine.stop() }
            runCatching { dataLine.close() }
        }
        line = null
        thread?.interrupt()
        thread = null
        buffer = null
        file?.delete()
        file = null
    }

    override fun release(recording: RecordedMedia) {
        runCatching { File(recording.path).delete() }
    }

    override fun requestPermissions() = Unit
}

private fun ByteArray.toWav(format: AudioFormat): ByteArray {
    val sampleRate = format.sampleRate.toInt()
    val channels = format.channels
    val bitsPerSample = format.sampleSizeInBits
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = size
    val output = ByteArrayOutputStream(44 + dataSize)

    fun writeInt(value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
        output.write((value shr 16) and 0xFF)
        output.write((value shr 24) and 0xFF)
    }

    fun writeShort(value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    output.write("RIFF".encodeToByteArray())
    writeInt(36 + dataSize)
    output.write("WAVE".encodeToByteArray())
    output.write("fmt ".encodeToByteArray())
    writeInt(16)
    writeShort(1)
    writeShort(channels)
    writeInt(sampleRate)
    writeInt(byteRate)
    writeShort(blockAlign)
    writeShort(bitsPerSample)
    output.write("data".encodeToByteArray())
    writeInt(dataSize)
    output.write(this)

    return output.toByteArray()
}
