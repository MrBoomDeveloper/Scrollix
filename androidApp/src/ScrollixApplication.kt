package com.mrboomdev.scrollix

import android.app.Application
import com.mrboomdev.scrollix.data.storage.AndroidAppContext

class ScrollixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.context = this
    }
}
