package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Retries queued work every [IntervalMillis] while at least one signed-in
 * account is disconnected.
 *
 * The outbox backs off up to five minutes, which is right for a server that
 * keeps rejecting a message, but wrong for the common case of a chat that is
 * simply waiting for its provider to come back. This pulse restarts the queue
 * on a fixed cadence so a message leaves as soon as the connection returns.
 * [remainingMillis] drives the countdown under the offline banner.
 */
object ConnectionRetry {
    const val IntervalMillis = 10_000L

    private const val TickMillis = 1_000L

    private val _remainingMillis = MutableStateFlow(IntervalMillis)
    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()

    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true

        scope.launch {
            var remaining = IntervalMillis

            while (isActive) {
                _remainingMillis.value = remaining
                delay(TickMillis)

                if (!anyAccountOffline()) {
                    remaining = IntervalMillis
                    continue
                }

                remaining -= TickMillis

                if (remaining <= 0L) {
                    OutboxManager.retryAll()
                    remaining = IntervalMillis
                }
            }
        }
    }

    private fun anyAccountOffline(): Boolean {
        return ApiManager.sessions.value.any { (_, session) ->
            session.authState.value is AuthState.Ready && !session.isConnected.value
        }
    }
}
