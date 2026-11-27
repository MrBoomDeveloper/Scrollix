package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage

/** A language the chat translator can target. */
internal data class TranslationLanguage(
    val code: String,
    val title: String
)

/**
 * Languages the chat translator offers. TDLib accepts any ISO 639-1 code, so
 * this is only the picker's list; the device language is offered separately and
 * is used while no explicit choice exists.
 */
internal val TranslationLanguages = listOf(
    TranslationLanguage("en", "English"),
    TranslationLanguage("ru", "Русский"),
    TranslationLanguage("uk", "Українська"),
    TranslationLanguage("de", "Deutsch"),
    TranslationLanguage("fr", "Français"),
    TranslationLanguage("es", "Español"),
    TranslationLanguage("it", "Italiano"),
    TranslationLanguage("pt", "Português"),
    TranslationLanguage("nl", "Nederlands"),
    TranslationLanguage("pl", "Polski"),
    TranslationLanguage("cs", "Čeština"),
    TranslationLanguage("ro", "Română"),
    TranslationLanguage("sv", "Svenska"),
    TranslationLanguage("tr", "Türkçe"),
    TranslationLanguage("ar", "العربية"),
    TranslationLanguage("fa", "فارسی"),
    TranslationLanguage("he", "עברית"),
    TranslationLanguage("hi", "हिन्दी"),
    TranslationLanguage("id", "Bahasa Indonesia"),
    TranslationLanguage("vi", "Tiếng Việt"),
    TranslationLanguage("th", "ไทย"),
    TranslationLanguage("ja", "日本語"),
    TranslationLanguage("ko", "한국어"),
    TranslationLanguage("zh", "中文")
)

/**
 * The text a bubble renders. A shown translation replaces the original in
 * place, which is what the message menu means by "Show original"; an absent
 * or blank translation, or a hidden one, leaves the original untouched.
 */
internal fun messageTranslationToShow(
    translation: String?,
    isTranslationHidden: Boolean
): String? {
    if (isTranslationHidden) return null
    return translation?.takeIf { it.isNotBlank() }
}

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

/**
 * Validates a language stored in settings. Null means "follow the device",
 * which is also what a malformed value degrades to instead of failing a
 * translation request with a code the server does not know.
 */
internal fun normalizeTranslationLanguage(code: String?): String? {
    val normalized = code
        ?.trim()
        ?.lowercase()
        ?.substringBefore('-')
        ?.substringBefore('_')
        .orEmpty()

    return normalized.takeIf { candidate ->
        candidate.length in 2..3 && candidate.all { character -> character in 'a'..'z' }
    }
}

/** The language a translation actually targets: the choice or the device one. */
internal fun translationTargetLanguage(configured: String?, deviceLanguage: String?): String {
    return normalizeTranslationLanguage(configured) ?: translationLanguageCode(deviceLanguage)
}

/** Display name of a language code, falling back to the uppercase code. */
internal fun translationLanguageLabel(code: String): String {
    val normalized = normalizeTranslationLanguage(code)
        ?: code.trim().lowercase()

    return TranslationLanguages
        .firstOrNull { language -> language.code == normalized }
        ?.title
        ?: normalized.uppercase()
}

/** Progress of translating the loaded part of a chat. */
internal data class TranslationProgress(
    val done: Int,
    val total: Int
) {
    val fraction: Float
        get() = if (total <= 0) {
            0f
        } else {
            (done.toFloat() / total).coerceIn(0f, 1f)
        }
}

/**
 * Messages the "translate chat" action still has to translate: only rows with
 * text, without a translation yet, and never service notices (their text is
 * informational and would produce noise).
 */
internal fun messagesToTranslate(
    messages: List<ChatMessage>,
    translated: Set<Long>
): List<ChatMessage> {
    return messages
        .filter { message ->
            !message.isService &&
                message.text.isNotBlank() &&
                message.messageId !in translated
        }
        .distinctBy { message -> message.messageId }
}
