package com.mrboomdev.scrollix.data.vk

import com.mrboomdev.scrollix.data.contract.AccountSessionContract
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class VkSessionContractTest : AccountSessionContract() {
    override val provider = VkProvider

    override fun createSession(
        storage: PlatformStorage,
        scope: CoroutineScope
    ): AccountSession = VkSession(
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
