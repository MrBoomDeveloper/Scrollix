package com.mrboomdev.scrollix.ui.components

import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.ByteArrayInputStream
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

actual fun createVoicePlayer(): VoicePlayback = JvmVoicePlayer()

private class JvmVoicePlayer : VoicePlayback {
    private var thread: Thread? = null
    private var bytes: ByteArray? = null
    private var completion: (() -> Unit)? = null
    private var activeSession: Any? = null
    private var line: SourceDataLine? = null

    @Volatile
    private var volume = 1f

    @Volatile
    private var currentPositionMs = 0L

    override fun play(
        bytes: ByteArray,
        startPositionMs: Long,
        onFinished: () -> Unit
    ): Boolean {
        stop()

        return try {
            silenceFfmpegLogs()
            val grabber = FFmpegFrameGrabber(ByteArrayInputStream(bytes))
            grabber.sampleFormat = AV_SAMPLE_FMT_S16
            grabber.start()

            if (startPositionMs > 0L) {
                runCatching { grabber.timestamp = startPositionMs * 1000L }
            }

            val sampleRate = grabber.sampleRate
            val channels = grabber.audioChannels.coerceAtLeast(1)
            val format = AudioFormat(
                sampleRate.toFloat(),
                16,
                channels,
                true,
                false
            )
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(format)
            line.start()
            this.line = line
            applyVolume(line)

            this.bytes = bytes
            completion = onFinished
            currentPositionMs = startPositionMs

            val session = Any()
            activeSession = session
            val baseSamples = startPositionMs * sampleRate / 1000L

            thread = Thread {
                var playedSamples = 0L

                try {
                    while (activeSession === session) {
                        val frame = grabber.grabSamples() ?: break
                        val buffer = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
                        val samples = ShortArray(buffer.remaining())
                        buffer.get(samples)

                        val pcm = ByteArray(samples.size * 2)

                        for (index in samples.indices) {
                            val value = samples[index].toInt()
                            pcm[index * 2] = (value and 0xFF).toByte()
                            pcm[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
                        }

                        line.write(pcm, 0, pcm.size)
                        playedSamples += samples.size / channels
                        currentPositionMs =
                            (baseSamples + playedSamples) * 1000L / sampleRate
                    }

                    runCatching { line.drain() }
                } catch (_: Exception) {
                } finally {
                    if (this.line === line) this.line = null
                    runCatching { line.stop() }
                    runCatching { line.close() }
                    runCatching { grabber.close() }

                    if (activeSession === session) {
                        activeSession = null
                        onFinished()
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

            true
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    override fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs

        val data = bytes ?: return
        val callback = completion ?: return
        if (activeSession == null) return

        play(data, positionMs, callback)
    }

    override fun positionMs(): Long = currentPositionMs

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        line?.let(::applyVolume)
    }

    private fun applyVolume(line: SourceDataLine) {
        runCatching {
            val control = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val gain = if (volume <= 0f) {
                control.minimum
            } else {
                (20.0 * log10(volume.toDouble()))
                    .toFloat()
                    .coerceIn(control.minimum, control.maximum)
            }

            control.value = gain
        }
    }

    override fun stop() {
        activeSession = null
        thread?.interrupt()
        thread = null
    }
}
