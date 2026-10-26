package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogIdentity

object DialogAggregator {
    fun aggregate(
        accounts: List<AccountRecord>,
        dialogsByAccount: Map<String, List<Dialog>>
    ): List<AccountDialogEntry> {
        val entries = accounts.flatMap { account ->
            dialogsByAccount[account.id].orEmpty().map { dialog ->
                AccountDialogEntry(
                    account = account,
                    dialog = dialog
                )
            }
        }

        val accountsByIdentity = mutableMapOf<DialogIdentity, MutableSet<String>>()
        for (entry in entries) {
            accountsByIdentity
                .getOrPut(entry.dialog.identity) { mutableSetOf() }
                .add(entry.account.id)
        }

        return entries
            .map { entry ->
                entry.copy(
                    showAccountBadge = accountsByIdentity[entry.dialog.identity]
                        .orEmpty()
                        .size > 1
                )
            }
            .sortedWith(
                compareByDescending<AccountDialogEntry> {
                    if (it.dialog.isPinned) 1 else 0
                }
                    .thenByDescending { it.dialog.timestamp }
                    .thenByDescending { it.dialog.order }
                    .thenBy { it.dialog.title.lowercase() }
            )
    }
}
