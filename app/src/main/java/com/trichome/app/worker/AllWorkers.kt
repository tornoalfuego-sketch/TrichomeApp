package com.trichome.app.worker

import android.content.Context
import androidx.work.*
import com.trichome.app.TrichomeApp
import com.trichome.app.data.entity.Reminder
import com.trichome.app.di.AppContainer
import java.util.concurrent.TimeUnit

/**
 * One-time worker that fires a specific reminder notification, then
 * automatically re-queues the next occurrence (recurrence maintenance).
 */
class ReminderSchedulerWorker(
    context: Context,
    params: WorkerParameters,
    private val container: AppContainer
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
            if (reminderId < 0) return Result.failure()

            val reminder = container.reminderRepository.getActiveRemindersSnapshot()
                .firstOrNull { it.id == reminderId }
                ?: return Result.success() // deleted meanwhile

            val plantName = container.reminderRepository.plantName(reminder.plantId)
            val title = if (plantName != null) "${reminder.title} · $plantName" else reminder.title
            val body = reminder.message.ifBlank { reminder.title }

            ReminderNotifications.showReminder(applicationContext, reminder.id.toInt(), title, body)

            // ── Auto re-queue for the next occurrence ──────────────────
            if (reminder.recurrenceIntervalDays > 0) {
                val nextFire = computeNextFire(reminder, now = System.currentTimeMillis())
                scheduleNext(applicationContext, reminder.id, nextFire)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun computeNextFire(reminder: Reminder, now: Long): Long {
        val intervalMillis = reminder.recurrenceIntervalDays * 86_400_000L
        var next = now + intervalMillis
        // Align to the configured time-of-day (reminderTime = millis of day).
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = next }
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis
        val configured = dayStart + reminder.reminderTime.coerceIn(0L, 86_399_999L)
        return if (configured >= now) configured else configured + intervalMillis
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"

        /**
         * Template of the unique work name carrying a reminder's one-shot job.
         *
         * This string was written out literally in three places — the enqueue,
         * the rescheduler's lookup and (once delete existed) the cancel — so
         * the job and its cancel could drift apart and a deleted reminder would
         * keep firing. Every other worker in this file publishes a `WORK_NAME`;
         * this is the per-row equivalent.
         */
        const val WORK_NAME_TEMPLATE = "reminder_due_%d"

        /** WorkManager tag identifying the jobs of one reminder. */
        const val TAG_TEMPLATE = "reminder_%d"

        /** Unique work name of [reminderId]'s one-shot job. */
        fun workNameFor(reminderId: Long): String = WORK_NAME_TEMPLATE.format(reminderId)

        /** Tag carried by [reminderId]'s one-shot job. */
        fun tagFor(reminderId: Long): String = TAG_TEMPLATE.format(reminderId)

        /** Schedules (or reschedules) the one-time worker for a reminder. */
        fun scheduleNext(context: Context, reminderId: Long, fireAtMillis: Long) {
            val delay = (fireAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ReminderSchedulerWorker>()
                .setInputData(workDataOf(KEY_REMINDER_ID to reminderId))
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(tagFor(reminderId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workNameFor(reminderId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** Queues a worker now (no delay) to sync scheduling. */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReminderReschedulerWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ReminderReschedulerWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

/**
 * Periodic (15 min) worker that guarantees every active recurring reminder
 * has its next occurrence scheduled.
 */
class ReminderReschedulerWorker(
    context: Context,
    params: WorkerParameters,
    private val container: AppContainer
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val reminders = container.reminderRepository.getActiveRemindersSnapshot()
            val now = System.currentTimeMillis()
            reminders.forEach { reminder ->
                val workManager = WorkManager.getInstance(applicationContext)
                val existing = workManager
                    .getWorkInfosForUniqueWork(ReminderSchedulerWorker.workNameFor(reminder.id))
                    .get()
                val pending = existing.any { info ->
                    info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
                }
                if (!pending && reminder.recurrenceIntervalDays > 0) {
                    // Catch-up: if due time already passed, fire soon.
                    var next = now
                    val interval = reminder.recurrenceIntervalDays * 86_400_000L
                    val earliest = next + interval - interval
                    if (earliest <= now) {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = next }
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        val configured = cal.timeInMillis + reminder.reminderTime.coerceIn(0L, 86_399_999L)
                        next = if (configured > now) configured else configured + interval
                        if (configured <= now) next = now + 60_000L // due → fire in 1 min
                    }
                    ReminderSchedulerWorker.scheduleNext(applicationContext, reminder.id, next)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "reminder_rescheduler"

        /** Registers the periodic rescheduler. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderReschedulerWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/**
 * Daily gentle progress check-in: reminds the grower to register at least one
 * observation when a plant has had no journal activity in the last 2 days.
 */
class DailyCheckinWorker(
    context: Context,
    params: WorkerParameters,
    private val container: AppContainer
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val plants = container.plantRepository.getPlantsSnapshot()
            val now = System.currentTimeMillis()
            val twoDaysAgo = now - 2 * 86_400_000L

            val idle = plants.filter { plant ->
                val last = container.eventRepository.getEventsByPlantSnapshot(plant.id).firstOrNull()
                last == null || last.timestamp < twoDaysAgo
            }

            if (idle.isNotEmpty()) {
                val message = buildString {
                    append("Tienes ${idle.size} planta(s) sin registros recientes:\n")
                    idle.take(3).forEach { append("• ${it.name}\n") }
                    append("Abre la bitácora y registra una observación.")
                }
                ReminderNotifications.showDailyCheckin(
                    applicationContext,
                    notificationId = 7001,
                    title = "🌿 Check-in de cultivo",
                    message = message
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "daily_checkin"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyCheckinWorker>(
                24, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/**
 * Hourly monitor for photoperiod transitions (super-cycle). Notifies when a
 * configured plant enters a LIGHT phase on a new superday.
 */
class CycleCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val container: AppContainer
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val configs = container.superCycleRepository.getAllConfigs()
            configs.forEach { config ->
                val result = com.trichome.app.model.SuperCycleEngine.calculateSuperCycle(
                    cycleStartAt = config.cycleStartAt,
                    lightHours = config.lightHours,
                    darkHours = config.darkHours
                )
                // Notify only when a new superday's light phase has just begun.
                if (result.isLight && result.phaseProgress < 0.02f && result.superday > 1) {
                    val plant = container.plantRepository.getPlantById(config.plantId)
                    if (plant != null) {
                        ReminderNotifications.showDailyCheckin(
                            applicationContext,
                            notificationId = (7000 + config.plantId.toInt()),
                            title = "☀️ Nuevo Superday ${result.superday}",
                            message = "${plant.name} comenzó el ciclo de luz (${config.lightHours}/${config.darkHours})."
                        )
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "cycle_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CycleCheckWorker>(
                1, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}