package com.mrboomdev.scrollix.data.model

/** A media file which has been fully downloaded by an account session. */
data class DownloadedFile(
    val accountId: String,
    val fileId: Int,
    val fileName: String,
    val size: Long,
    val downloadedAt: Long
)

data class AccountDownloadedFile(
    val account: AccountRecord,
    val file: DownloadedFile
)
