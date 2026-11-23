package com.mrboomdev.scrollix.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxContactTest {
    private fun parse(text: String) = parseMaxUser(Json.parseToJsonElement(text) as JsonObject)

    @Test
    fun joinsFirstNameAndLastNameFromOnemeName() {
        val user = parse(
            """{"id":7,"names":[{"type":"CUSTOM","firstName":"Vasya"},{"type":"ONEME","firstName":"Ivan","lastName":"Petrov"}]}"""
        )

        assertEquals("Ivan Petrov", user?.name)
    }

    @Test
    fun keepsExplicitNameFromServer() {
        val user = parse(
            """{"id":7,"names":[{"type":"ONEME","name":"Ivan Petrov","firstName":"Ivan","lastName":"Petrov"}]}"""
        )

        assertEquals("Ivan Petrov", user?.name)
    }

    @Test
    fun fallsBackToFirstAvailableName() {
        val user = parse("""{"id":7,"names":[{"type":"CUSTOM","lastName":"Petrov"}]}""")

        assertEquals("Petrov", user?.name)
    }

    @Test
    fun readsContactAvatarUrl() {
        val user = parse(
            """{"id":7,"names":[{"type":"ONEME","firstName":"Ivan"}],"baseRawUrl":"https://st.max.ru/avatar.jpg"}"""
        )

        assertEquals("https://st.max.ru/avatar.jpg", user?.pictureUrl)
    }

    @Test
    fun formatsContactPhoneNumber() {
        val user = parse("""{"id":7,"names":[{"type":"ONEME","name":"Ivan"}],"phone":79991234567}""")

        assertEquals("+7 999 123-45-67", user?.phone)
    }

    @Test
    fun returnsNullWithoutId() {
        assertEquals(null, parse("""{"names":[{"name":"Ivan"}]}"""))
    }
}
