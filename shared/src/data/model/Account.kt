package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AccountRecord(
    val id: String,
    val providerId: String,
    val displayName: String,
    val nickname: String? = null,
    val avatar: Avatar? = null,
    val isPending: Boolean = true,
    val addedAt: Long,
    val lastUsedAt: Long,
    val providerData: Map<String, String> = emptyMap()
)

@Serializable
data class AccountProfile(
    val displayName: String,
    val nickname: String? = null,
    val avatar: Avatar? = null
)

data class UserProfileDetails(
    val displayName: String? = null,
    val avatar: Avatar? = null,
    val username: String? = null,
    val phoneNumber: String? = null,
    val bio: String? = null,
    val birthdate: String? = null,
    val status: String? = null
)
