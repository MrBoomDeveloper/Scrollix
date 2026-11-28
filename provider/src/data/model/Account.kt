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

data class ProfileTrack(
    val title: String,
    val performer: String? = null,
    val durationSeconds: Int = 0,
    val media: MessageMedia
)

data class UserProfileDetails(
    /** Provider user id, when the profile belongs to a single user. */
    val userId: Long? = null,
    val displayName: String? = null,
    val avatar: Avatar? = null,
    val username: String? = null,
    val phoneNumber: String? = null,
    val bio: String? = null,
    val birthdate: String? = null,
    val status: String? = null,
    val isOnline: Boolean = false,
    /** Peer or channel carries the provider's verification mark. */
    val isVerified: Boolean = false,
    /** The user pays for the provider's premium subscription. */
    val isPremium: Boolean = false,
    val emojiStatusId: Long? = null,
    /** Custom profile colors from a collectible gift, when Telegram exposes them. */
    val accentColorsLight: List<Long> = emptyList(),
    val accentColorsDark: List<Long> = emptyList(),
    val kind: DialogKind? = null,
    val memberCount: Int? = null,
    val description: String? = null,
    val personalChannel: PersonalChannel? = null,
    val commonGroups: List<CommonGroup> = emptyList(),
    val tracks: List<ProfileTrack> = emptyList(),
    val members: List<ChatMemberInfo> = emptyList(),
    val isSubscribed: Boolean? = null,
    val discussionChatId: String? = null,
    /** True when the signed-in user has blocked this user. */
    val isBlocked: Boolean? = null,
    /**
     * False when the provider refuses blocking this profile, such as Telegram
     * support accounts or MAX service accounts.
     */
    val canBlock: Boolean = true,
    /** Whether this user is in the account's contact list. */
    val isContact: Boolean? = null,
    /** Providers that can add, rename and remove contacts set this to true. */
    val canManageContacts: Boolean = false
)
