package com.mrboomdev.scrollix.data.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MaxCodeEntryTest {
    private fun parse(text: String) = parseCodeEntry(Json.parseToJsonElement(text))

    @Test
    fun returnsLoginTokenForFinishedSignIn() {
        val result = parse(
            """{"tokenAttrs":{"LOGIN":{"token":"access-token"}},"profile":{"contact":{"id":1}}}"""
        )

        assertEquals(MaxCodeEntryResult.LoggedIn("access-token"), result)
    }

    @Test
    fun asksForPasswordWhenAccountIsProtected() {
        val result = parse(
            """{"passwordChallenge":{"trackId":"pass-track","hint":"pet name"}}"""
        )

        assertEquals(
            MaxCodeEntryResult.PasswordRequired("pass-track", "pet name"),
            result
        )
    }

    @Test
    fun asksForPasswordWithoutHint() {
        val result = parse("""{"passwordChallenge":{"trackId":"pass-track"}}""")

        assertEquals(MaxCodeEntryResult.PasswordRequired("pass-track", null), result)
    }

    @Test
    fun reportsRegistrationForUnknownNumbers() {
        val result = parse("""{"tokenAttrs":{"REGISTER":{"token":"register-token"}}}""")

        assertEquals(MaxCodeEntryResult.RegistrationRequired("register-token"), result)
    }

    @Test
    fun reportsServerMessageWhenNothingUsableWasReturned() {
        val result = parse("""{"localizedMessage":"Code is no longer valid"}""")

        assertEquals(MaxCodeEntryResult.Rejected("Code is no longer valid"), result)
    }

    @Test
    fun rejectsEmptyResponse() {
        val result = parse("{}")

        assertIs<MaxCodeEntryResult.Rejected>(result)
        assertNull(result.message)
        assertIs<MaxCodeEntryResult.Rejected>(parseCodeEntry(null))
    }

    @Test
    fun prefersLoginTokenOverPasswordChallenge() {
        val result = parse(
            """{"tokenAttrs":{"LOGIN":{"token":"t"}},"passwordChallenge":{"trackId":"ignored"}}"""
        )

        assertEquals(MaxCodeEntryResult.LoggedIn("t"), result)
    }
}
