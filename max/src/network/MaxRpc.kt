package com.mrboomdev.scrollix.data.max.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

internal class MaxApiException(
    val errorCode: String?,
    val description: String?
) : Exception(description ?: errorCode ?: "MAX request failed") {
    val isAuthError: Boolean
        get() = errorCode?.startsWith("auth.") == true
}

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
    private val sequence = AtomicInteger(1)
    private val pending = mutableMapOf<Int, CompletableDeferred<JsonElement?>>()
    private val pendingMutex = Mutex()

    private val _pushes = MutableSharedFlow<MaxPush>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pushes: SharedFlow<MaxPush> = _pushes.asSharedFlow()

    val closed = CompletableDeferred<String?>()

    private var readerJob: Job? = null

    suspend fun connect(scope: CoroutineScope) {
        // Subscribe before the socket opens: the server answers the first
        // frame immediately and an unbuffered event would otherwise be lost.
        val reader = scope.launch {
            socket.events.collect(::handleEvent)
        }
        readerJob = reader

        try {
            socket.connect()
        } catch (exception: Exception) {
            reader.cancel()
            readerJob = null
            throw exception
        }
    }

    suspend fun request(
        opcode: Int,
        payload: JsonObject? = null,
        timeoutMillis: Long = 30_000
    ): JsonElement? {
        val seq = nextSequence()
        val response = CompletableDeferred<JsonElement?>()

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

        runCatching { socket.close() }

        pendingMutex.withLock {
            pending.values.forEach { deferred ->
                deferred.completeExceptionally(IllegalStateException("MAX session was closed"))
            }
            pending.clear()
        }

        closed.complete(null)
    }

    private fun nextSequence(): Int {
        return sequence.getAndUpdate { current ->
            if (current >= Short.MAX_VALUE) 1 else current + 1
        }
    }

    private suspend fun sendFrame(seq: Int, opcode: Int, payload: JsonObject?) {
        socket.send(buildMaxFrame(cmd = 0, seq = seq, opcode = opcode, payload = payload))
    }

    private suspend fun handleEvent(event: MaxSocketEvent) {
        when (event) {
            is MaxSocketEvent.Binary -> handleFrame(event.bytes)
            is MaxSocketEvent.Closed -> {
                pendingMutex.withLock {
                    pending.values.forEach { deferred ->
                        deferred.completeExceptionally(
                            IllegalStateException("MAX connection closed: ${event.reason ?: "unknown reason"}")
                        )
                    }
                    pending.clear()
                }

                closed.complete(event.reason ?: "Connection closed")
            }
        }
    }

    private suspend fun handleFrame(bytes: ByteArray) {
        val frame = runCatching { parseMaxFrame(bytes) }.getOrNull() ?: return
        val payload = frame.payload as? JsonObject

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
