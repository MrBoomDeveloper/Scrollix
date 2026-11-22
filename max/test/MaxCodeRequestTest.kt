package com.mrboomdev.scrollix.data.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaxCodeRequestTest {
    private fun parse(text: String) = parseMaxCodeRequest(Json.parseToJsonElement(text))

    @Test
    fun readsTrackIdAndCodeLengthFromAuthRequest() {
        val parsed = parse(
            """{"trackId":"abc123","codeLength":5,"blockingDuration":0}"""
        )

        assertEquals("abc123", parsed?.trackId)
        assertEquals(5, parsed?.codeLength)
        assertEquals(0, parsed?.blockingDurationSeconds)
    }

    @Test
    fun acceptsTokenAsTrackIdForAlternateFlows() {
        val parsed = parse("""{"token":"xyz","codeLength":6}""")

        assertEquals("xyz", parsed?.trackId)
        assertEquals(6, parsed?.codeLength)
    }

    @Test
    fun reportsRateLimitWindow() {
        val parsed = parse("""{"trackId":"abc","codeLength":6,"blockingDuration":42}""")

        assertEquals(42, parsed?.blockingDurationSeconds)
    }

    @Test
    fun rejectsResponsesWithoutAnyIdentifier() {
        assertNull(parse("""{"codeLength":6}"""))
        assertNull(parseMaxCodeRequest(null))
    }
}
