package com.trichome.app.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.trichome.app.TrichomeApp
import com.trichome.app.data.entity.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Exact-alarm scheduling for cultivation reminders.
 *
 * WorkManager alone is not an alarm clock: its jobs are best-effort, batched and
 * heavily deferred by Doze and App Standby, so a "regarla a las 09:00" reminder
 * can easily arrive hours late. This layer registers a real
 * [AlarmManager.setAlarmClock] alarm, which the system honours even while the
 * device is idle and shows the user-visible next-alarm indicator.
 */
object ReminderAlarmScheduler {

    private const val TAG = "ReminderAlarm"
    const val ACTION_FIRE = "com.trichome.app.action.REMINDER_FIRE"
    const val EXTRA_REMINDER_ID = "reminder_id"

    /**
     * Registers an exact alarm for [reminder].
     *
     * @return true when the alarm was registered, false when the platform
     *   refused it (exact-alarm permission revoked on API 31+). Callers must
     *   surface that to the user instead of silently pretending it was set.
     */
    fun schedule(context: Context, reminder: Reminder): Boolean {
        if (!reminder.isActive) {
            cancel(context, reminder.id)
            return false
        }
        val triggerAt = nextTriggerAt(reminder, System.currentTimeMillis())
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        val operation = firePendingIntent(context, reminder.id)

        return try {
            if (canScheduleExact(manager)) {
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent(context)),
                    operation
                )
            } else {
                // Degraded but still functional: no doze exemption, no alarm icon.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied for reminder ${reminder.id}", e)
            false
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(firePendingIntent(context, reminderId))
    }

    /** True when the process is still allowed to set exact alarms. */
    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return canScheduleExact(manager)
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.canScheduleExactAlarms() else true

    /** Intent the system uses for the "next alarm" chip in the status bar. */
    private fun showPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_MAIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun firePendingIntent(context: Context, reminderId: Long): PendingIntent {
        // Built from the same plan a cancel uses, so scheduling and cancelling
        // can never end up addressing two different alarms.
        val plan = ReminderCancellation.planFor(reminderId)
        return PendingIntent.getBroadcast(
            context,
            plan.pendingIntentRequestCode,
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = plan.alarmAction
                putExtra(EXTRA_REMINDER_ID, reminderId)
                // The data URI keeps the extras distinct per reminder; without it
                // PendingIntent treats every alarm as the same one and extras leak.
                data = android.net.Uri.parse(plan.alarmDataUri)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Next fire time as an absolute epoch-millis instant.
     *
     * The reminder stores a millis-of-day plus a recurrence in days. Today's
     * occurrence is used when it is still in the future, otherwise the next
     * interval is taken. Non-recurring reminders fire once, on the next day that
     * has not elapsed yet.
     */
    fun nextTriggerAt(reminder: Reminder, now: Long): Long {
        val timeOfDay = reminder.reminderTime.coerceIn(0L, 86_399_999L)
        val days = reminder.recurrenceIntervalDays.coerceAtLeast(0)
        if (days == 0) return nextDailyOccurrence(now, timeOfDay)
        val interval = days * 86_400_000L
        val todayAtTime = startOfDay(now) + timeOfDay
        if (todayAtTime > now) return todayAtTime
        val elapsed = now - todayAtTime
        val periods = elapsed / interval
        return todayAtTime + (periods + 1) * interval
    }

    private fun nextDailyOccurrence(now: Long, timeOfDay: Long): Long {
        val todayAtTime = startOfDay(now) + timeOfDay
        return if (todayAtTime > now) todayAtTime else todayAtTime + 86_400_000L
    }

    private fun startOfDay(millis: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = millis
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * Receives the exact-alarm broadcast, posts the notification and re-arms the
 * next occurrence of a recurring reminder.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderAlarmScheduler.ACTION_FIRE) return
        val reminderId = intent.getLongExtra(ReminderAlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = (appContext as? TrichomeApp)?.appContainer ?: run {
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reminder = container.reminderRepository.getActiveRemindersSnapshot()
                    .firstOrNull { it.id == reminderId }
                if (reminder != null) {
                    val plantName = container.reminderRepository.plantName(reminder.plantId)
                    val title = if (plantName != null) "${reminder.title} · $plantName" else reminder.title
                    val body = reminder.message.ifBlank { reminder.title }
                    ReminderNotifications.showReminder(appContext, reminderId.toInt(), title, body)
                    // Re-arm for the following occurrence.
                    ReminderAlarmScheduler.schedule(appContext, reminder)
                }
            } catch (e: Exception) {
                Log.e("ReminderAlarmReceiver", "Failed to fire reminder $reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * AlarmManager drops every registered alarm when the device reboots or the app
 * is replaced, so all pending reminders must be re-armed from Room.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = (appContext as? TrichomeApp)?.appContainer ?: run {
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                container.reminderRepository.getActiveRemindersSnapshot().forEach { reminder ->
                    ReminderAlarmScheduler.schedule(appContext, reminder)
                }
            } catch (e: Exception) {
                Log.e("ReminderBootReceiver", "Failed to re-arm reminders", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private const val REQUEST_SHOW = 9_100
