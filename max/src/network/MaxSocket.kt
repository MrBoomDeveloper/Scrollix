package com.mrboomdev.scrollix.data.max.network

import kotlinx.coroutines.flow.Flow

internal sealed interface MaxSocketEvent {
    data class Binary(val bytes: ByteArray) : MaxSocketEvent
    data class Closed(val reason: String?) : MaxSocketEvent
}

/**
 * A binary WebSocket, implemented with OkHttp on Android and on the JVM.
 *
 * MAX's web transport sends packed protocol frames (not JSON text): a 10-byte
 * header followed by a MessagePack payload, LZ4-compressed when the frame says
 * so.
 */
internal interface MaxSocket {
    val events: Flow<MaxSocketEvent>

    suspend fun connect()
    suspend fun send(bytes: ByteArray)
    suspend fun close()
}

internal expect fun createMaxSocket(
    url: String,
    headers: Map<String, String>
): MaxSocket
