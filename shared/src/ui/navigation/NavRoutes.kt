package com.mrboomdev.scrollix.ui.navigation

import com.mrboomdev.scrollix.data.model.Dialog

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route: NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Accounts : Route

    @Serializable
    data class AccountManage(val accountId: String) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Drafts : Route

    @Serializable
    data object Onboarding : Route

    @Serializable
    data object Notifications : Route

    @Serializable
    data object StorageCenter : Route

    @Serializable
    data object Diagnostics : Route

    @Serializable
    data class Search(val query: String = "") : Route

    @Serializable
    data class Login(val accountId: String) : Route

    @Serializable
    data class Dialog(
        val accountId: String,
        val providerDialogId: String,
        val messageId: Long? = null
    ) : Route

    @Serializable
    data class Profile(
        val accountId: String,
        val providerDialogId: String,
        val userId: Long? = null
    ) : Route

    @Serializable
    data class OwnProfile(val accountId: String) : Route
}
