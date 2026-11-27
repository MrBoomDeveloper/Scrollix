package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import kotlinx.coroutines.CancellationException

/**
 * Translates plain text through TDLib. Telegram returns formatted text, but a
 * bubble shows the translation without the original's ranges, so only the text
 * is kept. Blank input is answered locally instead of failing the request.
 */
internal suspend fun TelegramSession.translateTextImpl(
    text: String,
    languageCode: String?
): Result<String> {
    if (text.isBlank()) return Result.success("")

    return try {
        when (val result = client.translateText(
            text = FormattedText(text = text, entities = emptyArray()),
            toLanguageCode = languageCode?.takeIf { it.isNotBlank() } ?: "en",
            // Empty means "no AI composer tone", only a plain translation.
            tone = ""
        )) {
            is TdlResult.Success -> Result.success(result.result.text)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}
