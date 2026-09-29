package com.trichome.app.worker

/**
 * What a reminder has to give up when it is deleted, described as data.
 *
 * Deleting the row is not deleting the reminder. A reminder lives in two
 * registrations the database knows nothing about:
 * - an `AlarmManager` exact alarm, whose identity is the broadcast
 *   `PendingIntent` built from the action, the request code and the data URI;
 * - a `WorkManager` unique job named `reminder_due_<id>`.
 *
 * Dropping only the row leaves both behind, and the broadcast receiver looks the
 * reminder up by id, finds nothing and quietly stops — but the periodic
 * [ReminderReschedulerWorker] keeps re-arming anything still in the table, and
 * the job already enqueued still fires. So a delete is a full cancel, and this
 * type is the checklist, extracted so it can be asserted without Android.
 *
 * It is deliberately free of `android.*` and `androidx.work.*` so it runs on a
 * plain JVM: the actual cancelling is injected as two lambdas.
 */
data class ReminderCancelPlan(
    val reminderId: Long,
    /** Broadcast action of the alarm to cancel. */
    val alarmAction: String,
    /** Data URI that makes the alarm's `PendingIntent` unique per reminder. */
    val alarmDataUri: String,
    /** `PendingIntent` request code, derived from the reminder id. */
    val pendingIntentRequestCode: Int,
    /** Unique work name to cancel. */
    val workName: String
)

/** The alarm teardown and the job teardown of a reminder, as callable steps. */
object ReminderCancellation {

    /** Data URI of the broadcast `PendingIntent` armed for [reminderId]. */
    fun alarmDataUriFor(reminderId: Long): String = "trichome://reminder/$reminderId"

    /** Everything that must be torn down for [reminderId]. */
    fun planFor(reminderId: Long): ReminderCancelPlan = ReminderCancelPlan(
        reminderId = reminderId,
        alarmAction = ReminderAlarmScheduler.ACTION_FIRE,
        alarmDataUri = alarmDataUriFor(reminderId),
        pendingIntentRequestCode = reminderId.toInt(),
        workName = ReminderSchedulerWorker.workNameFor(reminderId)
    )

    /**
     * Full teardown: the row is going away for good, so the alarm and the job
     * both have to go with it.
     */
    fun cancelAll(
        reminderId: Long,
        cancelAlarm: (Long) -> Unit,
        cancelWork: (String) -> Unit
    ): ReminderCancelPlan {
        val plan = planFor(reminderId)
        cancelAlarm(plan.reminderId)
        cancelWork(plan.workName)
        return plan
    }

    /**
     * Teardown for an edit: only the job is dropped, because
     * [ReminderAlarmScheduler.schedule] is about to re-register the very same
     * `PendingIntent` at the new time. Cancelling the alarm here would leave a
     * gap where the reminder has neither an alarm nor a job.
     */
    fun cancelStaleWork(
        reminderId: Long,
        cancelWork: (String) -> Unit
    ): ReminderCancelPlan {
        val plan = planFor(reminderId)
        cancelWork(plan.workName)
        return plan
    }
}
