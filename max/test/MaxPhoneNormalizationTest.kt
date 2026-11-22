package com.mrboomdev.scrollix.data.max

import com.mrboomdev.scrollix.data.max.network.normalizeMaxPhone
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxPhoneNormalizationTest {
    @Test
    fun keepsAnInternationalNumber() {
        assertEquals("+79991234567", normalizeMaxPhone("+7 (999) 123-45-67"))
    }

    @Test
    fun convertsTheRussianDomesticPrefix() {
        assertEquals("+79991234567", normalizeMaxPhone("8 999 123 45 67"))
    }

    @Test
    fun addsThePlusToBareDigits() {
        assertEquals("+79991234567", normalizeMaxPhone("79991234567"))
    }
}
