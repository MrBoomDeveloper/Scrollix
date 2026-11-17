package com.mrboomdev.scrollix.data.storage

fun formatPhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || !trimmed.startsWith("+")) return trimmed

    val digits = trimmed.filter(Char::isDigit)
    if (digits.length < 10) return trimmed

    val countryCodeLength = when {
        digits.length >= 11 && (digits[0] == '7' || digits[0] == '1') -> 1
        digits.length >= 10 -> 2
        else -> return trimmed
    }

    val countryCode = digits.take(countryCodeLength)
    val national = digits.drop(countryCodeLength)

    val formattedNational = when {
        countryCode == "7" && national.length == 10 ->
            "${national.take(3)} ${national.substring(3, 6)}-" +
                "${national.substring(6, 8)}-${national.substring(8, 10)}"

        countryCode == "1" && national.length == 10 ->
            "(${national.take(3)}) ${national.substring(3, 6)}-${national.substring(6, 10)}"

        else -> national.chunked(3).joinToString(" ")
    }

    return "+$countryCode $formattedNational"
}
