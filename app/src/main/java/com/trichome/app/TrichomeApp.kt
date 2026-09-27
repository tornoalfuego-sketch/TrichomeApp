package com.trichome.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.trichome.app.di.AppContainer
import com.trichome.app.di.DefaultAppContainer
import com.trichome.app.worker.TrichomeWorkerFactory

class TrichomeApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(TrichomeWorkerFactory(appContainer))
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
        )
    }
}