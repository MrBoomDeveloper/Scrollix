package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Avatar

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

internal val LinkRegex = Regex(
    """(https?://[^\s]+|www\.[^\s]+)|(?<![\w@])@[A-Za-z][A-Za-z0-9_]{2,31}|(?<![\w#])#[\p{L}\p{N}_]{1,64}|(?<![\w+])\+?\d[\d\s().-]{7,}\d(?!\w)""",
    RegexOption.IGNORE_CASE
)

internal fun AnnotatedString.Builder.appendTextWithLinks(
    text: String,
    onMentionClick: ((String) -> Unit)? = null,
    onPhoneClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    mentionAvatars: Map<String, Avatar> = emptyMap()
) {
    var index = 0

    LinkRegex.findAll(text).forEach { match ->
        val linkText = match.value
        val isMention = linkText.startsWith("@")
        val isHashtag = linkText.startsWith("#")
        val isUrl = linkText.startsWith("http", ignoreCase = true) ||
            linkText.startsWith("www.", ignoreCase = true)
        val isPhone = !isMention && !isHashtag && !isUrl

        // Mentions and phone numbers are only rendered as links when someone
        // can react to them.
        if (isMention && onMentionClick == null) return@forEach
        if (isHashtag && onHashtagClick == null) return@forEach
        if (isPhone && onPhoneClick == null) return@forEach

        if (match.range.first > index) {
            append(text.substring(index, match.range.first))
        }

        val styles = TextLinkStyles(
            style = SpanStyle(textDecoration = TextDecoration.Underline)
        )

        when {
            isMention -> {
                val username = linkText.removePrefix("@")

                if (mentionAvatars.containsKey(username)) {
                    // The avatar is inline content, so the mention itself stays
                    // the only tap target. The alternate text is invisible and
                    // kept zero-width to avoid duplicating the handle on copy.
                    appendInlineContent(
                        alternateText = "\u200B",
                        id = "mention-avatar:$username"
                    )
                }

                withLink(
                    LinkAnnotation.Clickable(
                        tag = "mention:$username",
                        styles = styles,
                        linkInteractionListener = { onMentionClick?.invoke(username) }
                    )
                ) {
                    append(linkText)
                }
            }

            isHashtag -> {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "hashtag:${linkText.removePrefix("#")}",
                        styles = styles,
                        linkInteractionListener = { onHashtagClick?.invoke(linkText) }
                    )
                ) {
                    append(linkText)
                }
            }

            isUrl -> {
                val url = if (linkText.startsWith("http", ignoreCase = true)) {
                    linkText
                } else {
                    "https://$linkText"
                }

                if (com.mrboomdev.scrollix.DeepLinkHandler.isTelegramLink(url)) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "deep-link:$url",
                            styles = styles,
                            linkInteractionListener = {
                                com.mrboomdev.scrollix.DeepLinkHandler.publish(url)
                            }
                        )
                    ) {
                        append(linkText)
                    }
                } else {
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = styles
                        )
                    ) {
                        append(linkText)
                    }
                }
            }

            else -> {
                val phone = linkText.filter { character ->
                    character.isDigit() || character == '+'
                }

                withLink(
                    LinkAnnotation.Clickable(
                        tag = "phone:$phone",
                        styles = styles,
                        linkInteractionListener = { onPhoneClick?.invoke(linkText) }
                    )
                ) {
                    append(linkText)
                }
            }
        }

        index = match.range.last + 1
    }

    if (index < text.length) {
        append(text.substring(index))
    }
}

internal fun formatCount(count: Int): String {
    return count.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}
