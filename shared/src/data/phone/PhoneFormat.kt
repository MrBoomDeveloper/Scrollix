package com.mrboomdev.scrollix.data.phone

private val oneDigitCountryCodes = setOf(
    "1",
    "7"
)

private val twoDigitCountryCodes = setOf(
    "20", "27", "30", "31", "32", "33", "34", "36", "39", "40",
    "41", "43", "44", "45", "46", "47", "48", "49", "51", "52",
    "53", "54", "55", "56", "57", "58", "60", "61", "62", "63",
    "64", "65", "66", "81", "82", "84", "86", "90", "91", "92",
    "93", "94", "95", "98"
)

private val threeDigitCountryCodes = setOf(
    "211", "212", "213", "216", "218", "220", "221", "222", "223",
    "224", "225", "226", "227", "228", "229", "230", "231", "232",
    "233", "234", "235", "236", "237", "238", "239", "240", "241",
    "242", "243", "244", "245", "246", "247", "248", "249", "250",
    "251", "252", "253", "254", "255", "256", "257", "258", "260",
    "261", "262", "263", "264", "265", "266", "267", "268", "269",
    "290", "291", "297", "298", "299", "350", "351", "352", "353",
    "354", "355", "356", "357", "358", "359", "370", "371", "372",
    "373", "374", "375", "376", "377", "378", "379", "380", "381",
    "382", "383", "385", "386", "387", "389", "420", "421", "423",
    "500", "501", "502", "503", "504", "505", "506", "507", "508",
    "509", "590", "591", "592", "593", "594", "595", "596", "597",
    "598", "599", "670", "672", "673", "674", "675", "676", "677",
    "678", "679", "680", "681", "682", "683", "685", "686", "687",
    "688", "689", "690", "691", "692", "850", "852", "853", "855",
    "856", "880", "886", "960", "961", "962", "963", "964", "965",
    "966", "967", "968", "970", "971", "972", "973", "974", "975",
    "976", "977", "992", "993", "994", "995", "996", "998"
)

private val countryPatterns = mapOf(
    "1" to mapOf(10 to listOf(3, 3, 4)),
    "7" to mapOf(10 to listOf(3, 3, 2, 2)),
    "20" to mapOf(10 to listOf(2, 4, 4)),
    "27" to mapOf(9 to listOf(2, 3, 4)),
    "30" to mapOf(10 to listOf(3, 3, 4)),
    "31" to mapOf(9 to listOf(2, 3, 4)),
    "32" to mapOf(9 to listOf(3, 2, 2, 2)),
    "33" to mapOf(9 to listOf(1, 2, 2, 2, 2)),
    "34" to mapOf(9 to listOf(3, 2, 2, 2)),
    "36" to mapOf(9 to listOf(2, 3, 4)),
    "39" to mapOf(9 to listOf(3, 3, 3), 10 to listOf(3, 3, 4)),
    "40" to mapOf(9 to listOf(3, 3, 3)),
    "41" to mapOf(9 to listOf(2, 3, 2, 2)),
    "43" to mapOf(10 to listOf(3, 3, 4)),
    "44" to mapOf(10 to listOf(2, 4, 4)),
    "45" to mapOf(8 to listOf(2, 2, 2, 2)),
    "46" to mapOf(9 to listOf(2, 3, 2, 2)),
    "47" to mapOf(8 to listOf(3, 2, 3)),
    "48" to mapOf(9 to listOf(3, 3, 3)),
    "49" to mapOf(10 to listOf(3, 3, 4)),
    "51" to mapOf(9 to listOf(3, 3, 3)),
    "52" to mapOf(10 to listOf(2, 4, 4)),
    "53" to mapOf(8 to listOf(1, 3, 4)),
    "54" to mapOf(10 to listOf(2, 4, 4)),
    "55" to mapOf(10 to listOf(2, 4, 4), 11 to listOf(2, 5, 4)),
    "56" to mapOf(9 to listOf(1, 4, 4)),
    "57" to mapOf(10 to listOf(3, 3, 4)),
    "58" to mapOf(10 to listOf(3, 3, 4)),
    "60" to mapOf(9 to listOf(2, 3, 4)),
    "61" to mapOf(9 to listOf(3, 3, 3)),
    "62" to mapOf(10 to listOf(3, 3, 4)),
    "63" to mapOf(10 to listOf(3, 3, 4)),
    "64" to mapOf(9 to listOf(2, 3, 4)),
    "65" to mapOf(8 to listOf(4, 4)),
    "66" to mapOf(9 to listOf(2, 3, 4)),
    "81" to mapOf(10 to listOf(2, 4, 4)),
    "82" to mapOf(10 to listOf(2, 4, 4)),
    "84" to mapOf(9 to listOf(3, 3, 3)),
    "86" to mapOf(11 to listOf(3, 4, 4)),
    "90" to mapOf(10 to listOf(3, 3, 4)),
    "91" to mapOf(10 to listOf(5, 5)),
    "92" to mapOf(10 to listOf(3, 3, 4)),
    "94" to mapOf(9 to listOf(2, 3, 4)),
    "95" to mapOf(9 to listOf(3, 3, 3)),
    "98" to mapOf(10 to listOf(3, 3, 4)),
    "212" to mapOf(9 to listOf(3, 3, 3)),
    "213" to mapOf(9 to listOf(3, 3, 3)),
    "216" to mapOf(8 to listOf(2, 3, 3)),
    "234" to mapOf(10 to listOf(3, 3, 4)),
    "254" to mapOf(9 to listOf(3, 3, 3)),
    "351" to mapOf(9 to listOf(3, 3, 3)),
    "353" to mapOf(9 to listOf(2, 3, 4)),
    "358" to mapOf(9 to listOf(2, 3, 4)),
    "380" to mapOf(9 to listOf(2, 3, 2, 2)),
    "381" to mapOf(9 to listOf(2, 3, 4)),
    "385" to mapOf(9 to listOf(2, 3, 4)),
    "420" to mapOf(9 to listOf(3, 3, 3)),
    "421" to mapOf(9 to listOf(3, 3, 3)),
    "852" to mapOf(8 to listOf(4, 4)),
    "886" to mapOf(9 to listOf(3, 3, 3)),
    "961" to mapOf(8 to listOf(2, 3, 3)),
    "962" to mapOf(9 to listOf(2, 3, 4)),
    "963" to mapOf(9 to listOf(3, 3, 3)),
    "964" to mapOf(10 to listOf(3, 3, 4)),
    "965" to mapOf(8 to listOf(4, 4)),
    "966" to mapOf(9 to listOf(2, 3, 4)),
    "967" to mapOf(9 to listOf(3, 3, 3)),
    "968" to mapOf(8 to listOf(4, 4)),
    "971" to mapOf(9 to listOf(2, 3, 4)),
    "972" to mapOf(9 to listOf(2, 3, 4)),
    "973" to mapOf(8 to listOf(4, 4)),
    "974" to mapOf(8 to listOf(4, 4)),
    "977" to mapOf(10 to listOf(3, 3, 4)),
    "992" to mapOf(9 to listOf(2, 3, 4)),
    "994" to mapOf(9 to listOf(2, 3, 4)),
    "995" to mapOf(9 to listOf(3, 3, 3)),
    "996" to mapOf(9 to listOf(3, 3, 3)),
    "998" to mapOf(9 to listOf(2, 3, 4))
)

fun formatPhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed

    var digits = trimmed.filter(Char::isDigit)
    if (digits.length < 7) return trimmed

    val hasPlus = trimmed.startsWith("+")

    if (!hasPlus && digits.length == 11 && digits.startsWith('8')) {
        digits = "7" + digits.drop(1)
    }

    val countryCode = detectCountryCode(digits)
        ?: return if (hasPlus) {
            "+" + applyGroups(digits, genericGroups(digits.length), brackets = false)
        } else {
            trimmed
        }

    val national = digits.drop(countryCode.length)
    if (national.length < 4) return trimmed

    val groups = if (
        countryCode == "44" &&
        national.length == 10 &&
        national.startsWith('7')
    ) {
        listOf(4, 6)
    } else {
        countryPatterns[countryCode]?.get(national.length)
            ?: genericGroups(national.length)
    }

    val formattedNational = if (countryCode == "1" && national.length == 10) {
        applyGroups(national, groups, brackets = true)
    } else {
        applyGroups(national, groups, brackets = false)
    }

    return "+$countryCode $formattedNational"
}

private fun detectCountryCode(digits: String): String? {
    for (length in 3 downTo 1) {
        if (digits.length - length < 4) continue

        val code = digits.take(length)
        val known = when (length) {
            3 -> code in threeDigitCountryCodes
            2 -> code in twoDigitCountryCodes
            else -> code in oneDigitCountryCodes
        }

        if (known) return code
    }

    return null
}

private fun applyGroups(
    digits: String,
    groups: List<Int>,
    brackets: Boolean
): String {
    val parts = mutableListOf<String>()
    var index = 0

    for (size in groups) {
        if (index >= digits.length) break

        val end = minOf(index + size, digits.length)
        parts += digits.substring(index, end)
        index = end
    }

    if (index < digits.length) {
        parts += digits.substring(index)
    }

    if (parts.isEmpty()) return digits

    if (brackets && parts.size == 3) {
        return "(${parts[0]}) ${parts[1]}-${parts[2]}"
    }

    if (parts.size == 4 && groups == listOf(3, 3, 2, 2)) {
        return "${parts[0]} ${parts[1]}-${parts[2]}-${parts[3]}"
    }

    return parts.joinToString(" ")
}

private fun genericGroups(length: Int): List<Int> {
    return when {
        length <= 4 -> listOf(length)
        length == 8 -> listOf(4, 4)
        length == 11 -> listOf(3, 4, 4)
        else -> buildList {
            var remaining = length

            while (remaining > 4) {
                add(3)
                remaining -= 3
            }

            add(remaining)
        }
    }
}
