package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ApiSource {
    val name: String
    suspend fun init(coroutineScope: CoroutineScope)
    val dialogs: StateFlow<List<Dialog>>
}