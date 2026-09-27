package com.trichome.app.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.trichome.app.di.AppContainer

/**
 * [WorkerFactory] that injects the [AppContainer] into every Trichome worker.
 * WorkManager is initialized in [com.trichome.app.TrichomeApp] with this
 * factory, so workers never construct their own database or repositories.
 */
class TrichomeWorkerFactory(private val container: AppContainer) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            ReminderSchedulerWorker::class.java.name ->
                ReminderSchedulerWorker(appContext, workerParameters, container)
            ReminderReschedulerWorker::class.java.name ->
                ReminderReschedulerWorker(appContext, workerParameters, container)
            DailyCheckinWorker::class.java.name ->
                DailyCheckinWorker(appContext, workerParameters, container)
            CycleCheckWorker::class.java.name ->
                CycleCheckWorker(appContext, workerParameters, container)
            else -> null
        }
    }
}