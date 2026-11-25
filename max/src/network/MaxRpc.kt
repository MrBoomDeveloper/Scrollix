package com.mrboomdev.scrollix.data.max.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicInteger
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException

internal class MaxApiException(
    val errorCode: String?,
    val description: String?
) : SessionException(
    code = maxSessionErrorCode(errorCode, description),
    message = description ?: errorCode ?: "MAX request failed"
) {
    val isAuthError: Boolean
        get() = errorCode?.startsWith("auth.") == true
}

/**
 * The transport lost its socket. A request that was in flight fails with this
 * error, and callers can tell it apart from a protocol error (cmd=3) to decide
 * whether reconnecting and resending the same payload is safe.
 */
internal class MaxConnectionClosedException(
    val reason: String?
) : SessionException(
    code = SessionErrorCode.Network,
    message = "MAX connection was lost. Please try again." + (reason?.let { " ($it)" } ?: "")
)

internal data class MaxPush(
    val opcode: Int,
    val payload: JsonObject?
)

/** A decoded protocol frame, shared by the transport and its tests. */
internal data class MaxFrame(
    val cmd: Int,
    val seq: Int,
    val opcode: Int,
    val payload: JsonElement?
)

/**
 * Sends packed frames and matches responses by `seq`, while delivering
 * asynchronous `cmd=0` frames (pushes) to the session.
 *
 * Frame layout (10 bytes + payload):
 * byte 0 = protocol version (10), byte 1 = cmd, int16 seq, int16 opcode,
 * byte 6 = LZ4 compression factor (0 = uncompressed), 24-bit payload length.
 * The payload is MessagePack.
 */
internal class MaxRpc(
    private val socket: MaxSocket
) {
    private companion object {
        // MAX drops a session whose socket stays silent, and a dropped socket
        // is only noticed on the next write (the server answers with a RST, so
        // the frame fails with "Broken pipe"). The web client therefore sends
        // PING (opcode 1) requests of its own between operations.
        const val HeartbeatIntervalMillis = 30_000L
    }

    private val sequence = AtomicInteger(1)
    private val pending = mutableMapOf<Int, CompletableDeferred<JsonElement?>>()
    private val pendingMutex = Mutex()

    private val _pushes = MutableSharedFlow<MaxPush>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pushes: SharedFlow<MaxPush> = _pushes.asSharedFlow()

    val closed = CompletableDeferred<String?>()

    /** False once the socket is gone, so a request is never written to it. */
    val isAlive: Boolean get() = !closed.isCompleted

    @Volatile
    private var closedReason: String? = null

    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null

    suspend fun connect(scope: CoroutineScope) {
        // Subscribe before the socket opens: the server answers the first
        // frame immediately and an unbuffered event would otherwise be lost.
        val reader = scope.launch {
            socket.events.collect(::handleEvent)
        }
        readerJob = reader

        val heartbeat = scope.launch {
            while (isActive) {
                delay(HeartbeatIntervalMillis)
                if (closed.isCompleted) return@launch

                // A keep-alive that cannot even be written means the socket is
                // gone, even when the platform never reported a close frame.
                val heartbeat = runCatching { sendHeartbeat() }

                if (heartbeat.isFailure) {
                    markClosed(heartbeat.exceptionOrNull()?.message ?: "Heartbeat failed")
                }
            }
        }
        heartbeatJob = heartbeat

        try {
            socket.connect()
        } catch (exception: Exception) {
            reader.cancel()
            readerJob = null
            heartbeat.cancel()
            heartbeatJob = null
            throw exception
        }
    }

    suspend fun request(
        opcode: Int,
        payload: JsonObject? = null,
        timeoutMillis: Long = 30_000
    ): JsonElement? {
        if (closed.isCompleted) {
            throw MaxConnectionClosedException(closedReason)
        }

        val seq = nextSequence()
        val response = CompletableDeferred<JsonElement?>()

        if (opcode != 1) {
            AppLog.protocol(
                provider = "max",
                direction = "out opcode=$opcode seq=$seq",
                payload = payload?.toString()
            )
        }

        pendingMutex.withLock { pending[seq] = response }

        try {
            sendFrame(seq, opcode, payload)
        } catch (exception: Exception) {
            pendingMutex.withLock { pending.remove(seq) }
            throw exception
        }

        try {
            return withTimeout(timeoutMillis) { response.await() }
        } finally {
            pendingMutex.withLock { pending.remove(seq) }
        }
    }

    suspend fun close() {
        readerJob?.cancel()
        readerJob = null

        heartbeatJob?.cancel()
        heartbeatJob = null

        runCatching { socket.close() }

        // In-flight requests treat this as a dead socket and can retry, which
        // also covers a connection torn down while another frame was pending.
        markClosed("connection was closed")
    }

    private fun nextSequence(): Int {
        return sequence.getAndUpdate { current ->
            if (current >= Short.MAX_VALUE) 1 else current + 1
        }
    }

    private suspend fun sendFrame(seq: Int, opcode: Int, payload: JsonObject?) {
        socket.send(buildMaxFrame(cmd = 0, seq = seq, opcode = opcode, payload = payload))
    }

    /**
     * A keep-alive PING is a plain request: no response is awaited by a caller,
     * and a late answer for its `seq` finds no pending entry, which is fine.
     */
    private suspend fun sendHeartbeat() {
        sendFrame(
            seq = nextSequence(),
            opcode = 1,
            payload = buildJsonObject { put("interactive", false) }
        )
    }

    private suspend fun handleEvent(event: MaxSocketEvent) {
        when (event) {
            is MaxSocketEvent.Binary -> handleFrame(event.bytes)
            is MaxSocketEvent.Closed -> markClosed(event.reason ?: "Connection closed")
        }
    }

    /**
     * Ends the transport once: every in-flight request fails with
     * [MaxConnectionClosedException] so callers can reconnect and resend, and
     * the session learns the connection is gone.
     */
    private suspend fun markClosed(reason: String?) {
        if (closed.isCompleted) return

        closedReason = reason

        pendingMutex.withLock {
            pending.values.forEach { deferred ->
                deferred.completeExceptionally(MaxConnectionClosedException(reason))
            }
            pending.clear()
        }

        closed.complete(reason ?: "Connection closed")
    }

    private suspend fun handleFrame(bytes: ByteArray) {
        val frame = runCatching { parseMaxFrame(bytes) }.getOrNull() ?: return
        val payload = frame.payload as? JsonObject

        if (frame.opcode != 1) {
            AppLog.protocol(
                provider = "max",
                direction = "in cmd=${frame.cmd} opcode=${frame.opcode} seq=${frame.seq}",
                payload = frame.payload?.toString()
            )
        }

        when (frame.cmd) {
            0 -> {
                if (frame.opcode == 1) {
                    // PING is a request-less heartbeat: acknowledge it so the
                    // server keeps the connection alive.
                    runCatching {
                        sendFrame(
                            seq = frame.seq,
                            opcode = 1,
                            payload = buildJsonObject { put("interactive", true) }
                        )
                    }
                }

                _pushes.tryEmit(MaxPush(frame.opcode, payload))
            }

            1 -> {
                pendingMutex.withLock { pending.remove(frame.seq) }
                    ?.complete(frame.payload)
            }

            3 -> {
                val exception = MaxApiException(
                    errorCode = payload?.string("error"),
                    description = payload?.string("localizedMessage")
                        ?: payload?.string("message")
                        ?: payload?.string("error")
                )

                pendingMutex.withLock { pending.remove(frame.seq) }
                    ?.completeExceptionally(exception)
            }

            else -> Unit
        }
    }
}

internal fun buildMaxFrame(
    cmd: Int,
    seq: Int,
    opcode: Int,
    payload: JsonElement?
): ByteArray {
    val body = if (payload == null || payload is JsonNull) {
        ByteArray(0)
    } else {
        encodeMsgPack(payload)
    }

    require(body.size <= 0xFFFFFF) { "MAX frame payload is too large" }

    val frame = ByteArray(10 + body.size)
    frame[0] = 10
    frame[1] = cmd.toByte()
    frame[2] = (seq shr 8).toByte()
    frame[3] = seq.toByte()
    frame[4] = (opcode shr 8).toByte()
    frame[5] = opcode.toByte()
    frame[6] = 0
    frame[7] = (body.size shr 16).toByte()
    frame[8] = (body.size shr 8).toByte()
    frame[9] = body.size.toByte()
    body.copyInto(frame, destinationOffset = 10)

    return frame
}

internal fun parseMaxFrame(bytes: ByteArray): MaxFrame {
    require(bytes.size >= 10) { "MAX frame is shorter than its header" }

    val cmd = bytes[1].toInt() and 0xFF
    val seq = (((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)).toShort().toInt()
    val opcode = (((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)).toShort().toInt()
    val compressionFactor = bytes[6].toInt() and 0xFF
    val length = ((bytes[7].toInt() and 0xFF) shl 16) or
        ((bytes[8].toInt() and 0xFF) shl 8) or
        (bytes[9].toInt() and 0xFF)

    require(10 + length <= bytes.size) { "MAX frame is shorter than its payload" }

    var body = bytes.copyOfRange(10, 10 + length)
    if (compressionFactor > 0) {
        val expectedSize = (length.toLong() * compressionFactor.toLong())
            .coerceAtMost(16L * 1024 * 1024)
            .toInt()
            .coerceAtLeast(length)

        body = decompressLz4Block(body, expectedSize)
    }

    val payload = if (body.isEmpty()) null else decodeMsgPack(body)

    return MaxFrame(cmd = cmd, seq = seq, opcode = opcode, payload = payload)
}
