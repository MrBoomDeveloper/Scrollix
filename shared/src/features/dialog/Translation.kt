package com.mrboomdev.scrollix.features.dialog

/**
 * Normalizes a platform locale into the language code TDLib expects. Devices
 * report locales such as "ru-RU", "pt_BR" or "zh-Hans-CN", while the
 * translation API takes the bare language ("ru", "pt", "zh"). An unknown or
 * empty value falls back to English so a request never fails on an unusual
 * device locale.
 */
internal fun translationLanguageCode(locale: String?): String {
    val language = locale
        ?.trim()
        ?.lowercase()
        ?.substringBefore('-')
        ?.substringBefore('_')
        .orEmpty()

    return language.takeIf { code ->
        code.length in 2..3 && code.all { character -> character in 'a'..'z' }
    } ?: "en"
}
