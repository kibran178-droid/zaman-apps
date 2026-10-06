package com.example.zaman_apps

import android.app.Application
import com.example.zaman_apps.utils.TelegramLogger

class ZamanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Setup Global Crash Reporter ke Telegram Admin
        TelegramLogger.setupGlobalCrashHandler(this)
    }
}
