package com.mrboomdev.scrollix

import android.app.Application
import com.mrboomdev.scrollix.data.max.MaxIntegration
import com.mrboomdev.scrollix.data.storage.AndroidAppContext
import com.mrboomdev.scrollix.data.telegram.TelegramIntegration
import com.mrboomdev.scrollix.data.vk.VkIntegration

class ScrollixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.context = this
        createNotificationChannels()
        TelegramIntegration.init()
        MaxIntegration.init()
        VkIntegration.init()
        ScrollixNotificationService.start(this)
    }
}
