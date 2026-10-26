package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.generated.BuildConfig
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.UpdateAuthorizationState
import dev.g000sha256.tdl.dto.UpdateNewChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data object TelegramApiSource: ApiSource {
    private val client by lazy {
        TdlClient.create()
    }
    
    override val name: String = "Telegram"
    
    private val _dialogs = MutableStateFlow(emptyList<Dialog>())
    override val dialogs = _dialogs.asStateFlow()

    override suspend fun init(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            client.allUpdates.collect { update ->
                when(update) {
                    is UpdateAuthorizationState -> {
                        when(update.authorizationState) {
                            is AuthorizationStateWaitTdlibParameters -> {
                                when(val result = client.setTdlibParameters(
                                    useTestDc = false,
                                    databaseDirectory = "tdlib_files",
                                    filesDirectory = "tdlib_files",
                                    databaseEncryptionKey = ByteArray(0),
                                    useFileDatabase = false,
                                    useChatInfoDatabase = false,
                                    useMessageDatabase = false,
                                    useSecretChats = false,
                                    apiId = BuildConfig.TELEGRAM_API_ID,
                                    apiHash = BuildConfig.TELEGRAM_API_HASH,
                                    systemLanguageCode = "en",
                                    deviceModel = "Unknown",
                                    systemVersion = "Unknown",
                                    applicationVersion = "1.0.0"
                                )) {
                                    is TdlResult.Success -> {
                                        println("TDLib parameters successfully set!")
                                    }

                                    is TdlResult.Failure -> {
                                        throw IllegalStateException("Failed to set parameters: [${result.code}] ${result.message}")
                                    }
                                }
                            }

                            else -> {

                            }
                        }
                    }
                    
                    is UpdateNewChat -> {
                        println("Added chat ${update.chat.title}!")
                    }

                    else -> {

                    }
                }
            }
        }
        
        coroutineScope.launch { 
            println("Before load chats")
            client.loadChats(limit = Int.MAX_VALUE)
            println("After load chats")
        }
    }
}