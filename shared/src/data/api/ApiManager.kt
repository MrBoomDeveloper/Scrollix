package com.mrboomdev.scrollix.data.api

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

object ApiManager {
    private var didInit = false
    
    private val sources = ApiSource::class.sealedSubclasses.mapNotNull { clazz ->
        clazz.objectInstance
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun init() {
        if(didInit) return
        didInit = true

        sources.forEach { 
            it.init(GlobalScope)
        }
    }
}