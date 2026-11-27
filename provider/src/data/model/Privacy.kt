package com.mrboomdev.scrollix.data.model

/**
 * Who may see when this account was last online. Providers keep it on their
 * servers, so it applies to every device signed in to the account.
 */
enum class LastSeenVisibility(val title: String) {
    Everybody("Everybody"),
    Contacts("My contacts"),
    Nobody("Nobody")
}
