package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.contract.AccountSessionContract
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class MaxSessionContractTest : AccountSessionContract() {
    override val provider = MaxProvider

    override fun createSession(
        storage: PlatformStorage,
        scope: CoroutineScope
    ): AccountSession = MaxSession(
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
