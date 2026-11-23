package com.mrboomdev.scrollix.data.max.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

internal actual fun createMaxSocket(
    url: String,
    headers: Map<String, String>
): MaxSocket = OkHttpMaxSocket(url, headers)

private class OkHttpMaxSocket(
    private val url: String,
    private val headers: Map<String, String>
) : MaxSocket {
    private val _events = MutableSharedFlow<MaxSocketEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: Flow<MaxSocketEvent> = _events.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var opened: CompletableDeferred<Unit>? = null

    override suspend fun connect() {
        val ready = CompletableDeferred<Unit>()
        opened = ready

        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ready.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                _events.tryEmit(MaxSocketEvent.Binary(bytes.toByteArray()))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!ready.isCompleted) ready.completeExceptionally(t)
                _events.tryEmit(MaxSocketEvent.Closed(t.message))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _events.tryEmit(MaxSocketEvent.Closed("$code $reason"))
            }
        }

        webSocket = client.newWebSocket(request, listener)
        withTimeout(20_000) { ready.await() }
    }

    override suspend fun send(bytes: ByteArray) {
        val socket = webSocket
            ?: throw MaxConnectionClosedException("WebSocket is not connected")

        if (!socket.send(bytes.toByteString())) {
            throw MaxConnectionClosedException("WebSocket rejected the frame")
        }
    }

    override suspend fun close() {
        webSocket?.close(1000, "Closing")
        webSocket = null
        opened?.completeExceptionally(IllegalStateException("MAX WebSocket was closed"))
        opened = null
        client.dispatcher.executorService.shutdown()
    }
}
