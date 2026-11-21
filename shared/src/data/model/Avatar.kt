package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Avatar(
    val initials: String,
    val colorSeed: Int,
    val image: ByteArray? = null,
    val url: String? = null
) {
    companion object {
        fun fromName(name: String): Avatar {
            val normalized = name.trim()
            val initials = normalized
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(2)
                .mapNotNull { part -> part.firstGlyph() }
                .joinToString("")
                .ifEmpty { "?" }

            return Avatar(
                initials = initials,
                colorSeed = normalized.hashCode()
            )
        }

        /**
         * First code point of the word as a string. Taking a single [Char]
         * would split emoji into a lone surrogate, which is not valid text and
         * corrupts anything that later serializes the initials (the cache).
         */
        private fun String.firstGlyph(): String? {
            if (isEmpty()) return null

            val first = this[0]

            return if (first.isHighSurrogate() && length > 1 && this[1].isLowSurrogate()) {
                substring(0, 2)
            } else {
                first.uppercaseChar().toString()
            }
        }
    }
}
