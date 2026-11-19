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

data class PersonalChannel(
    val providerDialogId: String,
    val title: String,
    val avatar: Avatar? = null,
    val subscriberCount: Int? = null
)

data class CommonGroup(
    val providerDialogId: String,
    val title: String,
    val avatar: Avatar? = null
)

data class ChatMemberInfo(
    val userId: Long,
    val name: String,
    val username: String? = null,
    val avatar: Avatar? = null,
    val emojiStatusId: Long? = null
)

data class UserProfileDetails(
    val displayName: String? = null,
    val avatar: Avatar? = null,
    val username: String? = null,
    val phoneNumber: String? = null,
    val bio: String? = null,
    val birthdate: String? = null,
    val status: String? = null,
    val isOnline: Boolean = false,
    val emojiStatusId: Long? = null,
    val kind: DialogKind? = null,
    val memberCount: Int? = null,
    val description: String? = null,
    val personalChannel: PersonalChannel? = null,
    val commonGroups: List<CommonGroup> = emptyList(),
    val members: List<ChatMemberInfo> = emptyList(),
    val isSubscribed: Boolean? = null,
    val discussionChatId: String? = null
)
