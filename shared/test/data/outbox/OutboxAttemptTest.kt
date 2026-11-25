package com.mrboomdev.scrollix.data.outbox

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutboxAttemptTest {
    @Test
    fun `appends attempts oldest to newest`() {
        val history = appendOutboxAttempt(emptyList(), attempt(1))

        assertEquals(listOf(1), history.map { it.attempt })
        assertEquals(
            listOf(1, 2),
            appendOutboxAttempt(history, attempt(2)).map { it.attempt }
        )
    }

    @Test
    fun `drops the oldest attempts past the cap`() {
        var history = emptyList<OutboxAttempt>()

        repeat(4) { index ->
            history = appendOutboxAttempt(history, attempt(index + 1), limit = 2)
        }

        assertEquals(listOf(3, 4), history.map { it.attempt })
    }

    @Test
    fun `default cap is bounded`() {
        var history = emptyList<OutboxAttempt>()

        repeat(MaxOutboxAttemptHistory + 5) { index ->
            history = appendOutboxAttempt(history, attempt(index + 1))
        }

        assertEquals(MaxOutboxAttemptHistory, history.size)
        assertEquals(MaxOutboxAttemptHistory + 5, history.last().attempt)
    }

    @Test
    fun `legacy entries decode with an empty history`() {
        val legacy = """
            {
              "id": "a",
              "localMessageId": -5,
              "providerId": "telegram",
              "accountId": "acc",
              "providerDialogId": "1",
              "text": "hi",
              "createdAt": 100
            }
        """.trimIndent()

        val entry = Json { ignoreUnknownKeys = true }
            .decodeFromString<OutboxEntry>(legacy)

        assertTrue(entry.attemptHistory.isEmpty())
        assertEquals(OutboxEntryState.Queued, entry.state)
    }

    private fun attempt(number: Int) = OutboxAttempt(
        atMillis = number * 1_000L,
        attempt = number,
        succeeded = false,
        error = "offline"
    )
}
