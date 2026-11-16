package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DialogFolder(
    val id: Int,
    val name: String,
    val iconName: String? = null,
    val colorId: Int = -1,
    val order: Int = 0,
    val isShareable: Boolean = false
)

data class AccountDialogFolder(
    val account: AccountRecord,
    val folder: DialogFolder
) {
    val key: String
        get() = "${account.id}:${folder.id}"
}
