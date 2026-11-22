package com.mrboomdev.scrollix.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route: NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Accounts : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Search : Route

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
