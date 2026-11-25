package com.mrboomdev.scrollix.data.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import com.mrboomdev.scrollix.data.contract.AccountSessionContract
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.PlatformStorage

class TelegramSessionContractTest : AccountSessionContract() {
    override val provider = TelegramProvider

    override fun createSession(
        storage: PlatformStorage,
        scope: CoroutineScope
    ): AccountSession = TelegramSession(
        account = account,
        storage = storage,
        parentScope = scope
    )

    @Test
    fun providerMetadataFollowsTheContract() {
        verifyProvider()
    }

    @Test
    fun sessionFollowsTheContract() = runBlocking {
        verifySession()
    }
}
