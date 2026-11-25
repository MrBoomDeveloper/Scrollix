package com.mrboomdev.scrollix.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.max.network.MaxApiException
import com.mrboomdev.scrollix.data.max.network.MaxRpc
import com.mrboomdev.scrollix.data.max.network.string
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.storage.CacheStore

internal fun MaxSession.readStoredSession(): StoredMaxSession? {
    val text = storage.readText(sessionPath) ?: return null
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    val token = root.string("token")?.takeIf(String::isNotBlank) ?: return null
    val deviceId = root.string("deviceId")?.takeIf(String::isNotBlank) ?: return null

    return StoredMaxSession(token, deviceId)
}

internal fun MaxSession.persistSession(token: String) {
    runCatching {
        storage.writeText(
            sessionPath,
            buildJsonObject {
                put("token", token)
                put("deviceId", deviceId)
                put("userId", meId)
            }.toString()
        )
    }
}

internal fun MaxSession.clearStoredSession() {
    runCatching { storage.delete(sessionPath) }
}

internal fun MaxSession.newDeviceId(): String {
    fun hex(length: Int) = buildString(length) {
        repeat(length) { append(Random.nextInt(16).toString(16)) }
    }

    return "${hex(8)}-${hex(4)}-${hex(4)}-${hex(4)}-${hex(12)}"
}

// ----------------------------------------------------------- connection --

internal fun MaxSession.startMonitor(active: MaxRpc) {
    monitorJob?.cancel()
    monitorJob = scope.launch {
        val reason = active.closed.await()

        if (closed || token == null || rpc !== active) return@launch

        rpc = null
        monitorJob = null
        _isConnected.value = false

        if (reconnectAttempts >= 5) {
            _authState.value = AuthState.Error(
                "MAX connection was lost: ${reason ?: "unknown reason"}"
            )
            return@launch
        }

        reconnectAttempts++
        delay(2_000L * reconnectAttempts)

        runCatching {
            connectMutex.withLock {
                // A request that hit the dead socket reconnects on demand;
                // do not replace that fresh connection after the backoff.
                if (rpc == null) connect(token!!)
            }
        }.onFailure { exception ->
                _authState.value = AuthState.Error(
                    "Could not reconnect to MAX: ${exception.message ?: "unknown error"}"
                )
            }
    }
}

internal suspend fun MaxSession.closeRpc() {
    pushJob?.cancel()
    pushJob = null

    monitorJob?.cancel()
    monitorJob = null

    val current = rpc
    rpc = null

    current?.close()
}

internal suspend fun MaxSession.startImpl() {
    if (started) return
    started = true
    startHistorySaver()
    _authState.value = AuthState.Initializing

    try {
        val stored = readStoredSession()
        deviceId = stored?.deviceId ?: newDeviceId()

        if (stored == null) {
            _authState.value = AuthState.Challenge(phoneChallenge())
            return
        }

        // Restore the chat list from the previous run before the network
        // answers, so the home screen is populated immediately.
        runCatching { CacheStore.loadDialogs(accountId) }
            .getOrNull()
            ?.takeIf { cached -> cached.isNotEmpty() && _dialogs.value.isEmpty() }
            ?.let { cached -> _dialogs.value = cached }

        // A watchdog keeps the login screen from sitting on "Connecting"
        // forever when the transport stops answering.
        withTimeout(MaxConnectTimeoutMillis) {
            connect(stored.token)
        }
    } catch (exception: TimeoutCancellationException) {
        AppLog.warning("max", "Sign-in timed out", exception)
        _authState.value = AuthState.Error(
            "MAX did not answer the sign-in request. Check the connection and try again."
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: MaxApiException) {
        if (exception.isAuthError) {
            clearStoredSession()
            _authState.value = AuthState.Challenge(phoneChallenge())
        } else {
            AppLog.error(
                "max",
                "Sign-in failed: ${exception.message ?: "unknown error"}",
                exception
            )
            _authState.value = AuthState.Error(exception.message ?: "MAX sign-in failed")
        }
    } catch (exception: Exception) {
        AppLog.error("max", "Could not connect to MAX", exception)
        _authState.value = AuthState.Error(
            "Could not connect to MAX: ${exception.message ?: "unknown error"}"
        )
    }
}
