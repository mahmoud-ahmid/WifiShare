package com.example.wifishare

import android.app.Application
import com.example.wifishare.core.Logger

class WifiShareApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Logger.i("WifiShareApp started (v${BuildConfig.VERSION_NAME})")
    }

    override fun onTerminate() {
        super.onTerminate()
        container.dispose()
    }
}
