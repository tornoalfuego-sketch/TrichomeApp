package com.trichome.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.trichome.app.di.AppContainer
import com.trichome.app.di.DefaultAppContainer
import com.trichome.app.worker.TrichomeWorkerFactory

/**
 * Application entry point.
 *
 * WorkManager uses **on-demand initialization**: the app implements
 * [Configuration.Provider] and the default `androidx.work.WorkManagerInitializer`
 * is removed from `androidx.startup.InitializationProvider` in the manifest.
 *
 * This is mandatory: `InitializationProvider` is a [android.content.ContentProvider],
 * so it is created *before* [onCreate]. Calling `WorkManager.initialize()` from
 * [onCreate] while the default initializer is still declared makes WorkManager
 * throw `IllegalStateException: WorkManager is already initialized` and kills the
 * process before any activity is shown.
 */
class TrichomeApp : Application(), Configuration.Provider {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }

    /**
     * Custom configuration handed to WorkManager the first time it is used.
     * Built lazily so that [appContainer] is always available.
     */
    private val wmConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(TrichomeWorkerFactory(appContainer))
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    }

    override val workManagerConfiguration: Configuration
        get() = wmConfiguration
}
