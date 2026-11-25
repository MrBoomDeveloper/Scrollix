package com.mrboomdev.scrollix.data.media

import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level

/**
 * This initializer runs exactly once, the first time a decoder in this file is
 * used. It only configures FFmpeg's own logger (demuxer/decoder chatter like
 * `Input #0, matroska,webm ...` or `[libvpx-vp9 @ ...]`); the application's
 * logging is untouched.
 */
private val ffmpegLoggerDisabled: Boolean = runCatching {
    av_log_set_level(AV_LOG_QUIET)
    true
}.getOrDefault(false)

/** Disables FFmpeg's console output for the process; repeated calls are no-ops. */
internal fun silenceFfmpegLogs() {
    ffmpegLoggerDisabled
}
