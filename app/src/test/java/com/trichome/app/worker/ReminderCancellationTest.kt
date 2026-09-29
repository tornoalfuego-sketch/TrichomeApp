package com.trichome.app.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for what a reminder delete has to tear down.
 *
 * The bug this locks down: `ReminderDao.deleteReminder` had zero callers, and the
 * unique work name `"reminder_due_$reminderId"` was a bare template repeated in
 * three places, so nothing could cancel a reminder by name. A row delete alone
 * is not a cancel — the `AlarmManager` alarm and the `WorkManager` job are
 * separate registrations and both survive the row disappearing, which is how a
 * deleted reminder keeps firing.
 */
class ReminderCancellationTest {

    @Test
    fun theUniqueWorkNameIsBuiltFromOneTemplate() {
        assertEquals("reminder_due_42", ReminderSchedulerWorker.workNameFor(42L))
        assertEquals("reminder_due_1", ReminderSchedulerWorker.workNameFor(1L))
    }

    @Test
    fun theUniqueWorkNameMatchesTheTemplateTheWorkersAlreadyEnqueue() {
        // Every other worker in AllWorkers.kt exposes a `WORK_NAME` constant; the
        // per-reminder one must be reachable by the same name, not inlined.
        assertEquals(
            "reminder_due_%d",
            ReminderSchedulerWorker.WORK_NAME_TEMPLATE
        )
        assertEquals(
            ReminderSchedulerWorker.WORK_NAME_TEMPLATE.format(7L),
            ReminderSchedulerWorker.workNameFor(7L)
        )
    }

    @Test
    fun theWorkTagIsAlsoDerivedFromTheReminderId() {
        assertEquals("reminder_42", ReminderSchedulerWorker.tagFor(42L))
    }

    @Test
    fun twoRemindersNeverShareAUniqueWorkName() {
        assertTrue(
            ReminderSchedulerWorker.workNameFor(1L) != ReminderSchedulerWorker.workNameFor(2L)
        )
    }

    @Test
    fun thePlanCarriesTheAlarmIdentityForAGivenReminderId() {
        val plan = ReminderCancellation.planFor(42L)

        assertEquals(42L, plan.reminderId)
        assertEquals(
            "the cancel must target the very PendingIntent the schedule registered",
            "trichome://reminder/42",
            plan.alarmDataUri
        )
        assertEquals(ReminderAlarmScheduler.ACTION_FIRE, plan.alarmAction)
        assertEquals(42, plan.pendingIntentRequestCode)
    }

    @Test
    fun thePlanCarriesTheWorkNameForAGivenReminderId() {
        assertEquals(
            "reminder_due_42",
            ReminderCancellation.planFor(42L).workName
        )
    }

    @Test
    fun aFullDeleteCancelsBothTheAlarmAndTheUniqueWork() {
        val cancelledAlarms = mutableListOf<Long>()
        val cancelledWork = mutableListOf<String>()

        val plan = ReminderCancellation.cancelAll(
            reminderId = 42L,
            cancelAlarm = { cancelledAlarms += it },
            cancelWork = { cancelledWork += it }
        )

        assertEquals(listOf(42L), cancelledAlarms)
        assertEquals(listOf("reminder_due_42"), cancelledWork)
        assertEquals("reminder_due_42", plan.workName)
    }

    @Test
    fun anEditOnlyDropsTheWorkJobAndKeepsTheAlarmSlotForTheReschedule() {
        val cancelledWork = mutableListOf<String>()

        ReminderCancellation.cancelStaleWork(
            reminderId = 42L,
            cancelWork = { cancelledWork += it }
        )

        assertEquals(
            "the alarm is re-registered by the schedule, not cancelled here",
            listOf("reminder_due_42"),
            cancelledWork
        )
    }

    @Test
    fun thePlanForTheSameIdIsAlwaysIdentical() {
        assertEquals(ReminderCancellation.planFor(9L), ReminderCancellation.planFor(9L))
    }
}
