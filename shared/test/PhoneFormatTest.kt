package com.mrboomdev.scrollix.data.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneFormatTest {
    @Test
    fun `formats russian number with plus prefix`() {
        assertEquals(
            "+7 999 123-45-67",
            formatPhoneNumber("+79991234567")
        )
    }

    @Test
    fun `formats russian number without plus prefix`() {
        assertEquals(
            "+7 999 123-45-67",
            formatPhoneNumber("79991234567")
        )
    }

    @Test
    fun `formats russian number with trunk prefix`() {
        assertEquals(
            "+7 999 123-45-67",
            formatPhoneNumber("89991234567")
        )
    }

    @Test
    fun `formats north american number`() {
        assertEquals(
            "+1 (555) 123-4567",
            formatPhoneNumber("+15551234567")
        )
    }

    @Test
    fun `formats north american number without plus prefix`() {
        assertEquals(
            "+1 (555) 123-4567",
            formatPhoneNumber("15551234567")
        )
    }

    @Test
    fun `formats british mobile number`() {
        assertEquals(
            "+44 7911 123456",
            formatPhoneNumber("+447911123456")
        )
    }

    @Test
    fun `formats french number`() {
        assertEquals(
            "+33 6 12 34 56 78",
            formatPhoneNumber("+33612345678")
        )
    }

    @Test
    fun `formats ukrainian number`() {
        assertEquals(
            "+380 67 123 45 67",
            formatPhoneNumber("+380671234567")
        )
    }

    @Test
    fun `normalizes already formatted number`() {
        assertEquals(
            "+7 999 123-45-67",
            formatPhoneNumber("+7 (999) 123-45-67")
        )
    }

    @Test
    fun `keeps short and empty values unchanged`() {
        assertEquals("", formatPhoneNumber(""))
        assertEquals("12345", formatPhoneNumber("12345"))
        assertEquals("not a phone", formatPhoneNumber("not a phone"))
    }
}
