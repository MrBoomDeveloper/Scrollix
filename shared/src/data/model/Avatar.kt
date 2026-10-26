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
                .mapNotNull { part -> part.firstOrNull()?.uppercaseChar() }
                .joinToString("")
                .ifEmpty { "?" }

            return Avatar(
                initials = initials,
                colorSeed = normalized.hashCode()
            )
        }
    }
}
