package com.trichome.app.worker

import com.trichome.app.data.entity.Reminder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reminder arming has to land on a real future instant, otherwise the exact
 * alarm fires immediately or never.
 *
 * Every case pins its own reference instant; nothing here reads the wall clock,
 * so the suite stays deterministic.
 */
class ReminderAlarmSchedulerTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisOf(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun atNineAm(intervalDays: Int) = Reminder(
        plantId = 1L,
        title = "Riego",
        message = "",
        recurrenceType = "custom",
        recurrenceIntervalDays = intervalDays,
        reminderTime = 9 * 3_600_000L
    )

    @Test
    fun reminderLaterTodayTriggersLaterToday() {
        val today = LocalDate.of(2026, 3, 10)
        val now = millisOf(today, 7, 0)
        val due = millisOf(today, 9, 0)

        val next = ReminderAlarmScheduler.nextTriggerAt(atNineAm(1), now)

        assertTrue("expected today 09:00 ($due) but got $next", next == due)
    }

    @Test
    fun reminderAlreadyPastTodayRollsToTheNextInterval() {
        val today = LocalDate.of(2026, 3, 10)
        val now = millisOf(today, 14, 0)

        val next = ReminderAlarmScheduler.nextTriggerAt(atNineAm(1), now)

        assertTrue("a passed reminder must not fire in the past, got $next", next > now)
        assertTrue("next occurrence is 09:00, got $next", next == millisOf(today.plusDays(1), 9, 0))
    }

    @Test
    fun nonRecurringReminderFiresOnceAtTheNextOccurrence() {
        val today = LocalDate.of(2026, 3, 10)
        val now = millisOf(today, 7, 0)

        val next = ReminderAlarmScheduler.nextTriggerAt(atNineAm(0), now)

        assertTrue("a one-shot reminder must still be in the future, got $next", next > now)
        assertTrue("one-shot reminder should fire today at 09:00, got $next", next == millisOf(today, 9, 0))
    }

    @Test
    fun multiDayReminderLandsExactlyOnItsInterval() {
        val today = LocalDate.of(2026, 3, 10)
        val now = millisOf(today, 14, 0)

        val next = ReminderAlarmScheduler.nextTriggerAt(atNineAm(3), now)

        assertTrue("3-day reminder should fire in 3 days, got $next", next == millisOf(today.plusDays(3), 9, 0))
    }

    @Test
    fun everyTriggerIsStrictlyInTheFuture() {
        val today = LocalDate.of(2026, 3, 10)
        for (hour in 0..23) {
            val now = millisOf(today, hour, 30)
            for (interval in listOf(0, 1, 2, 7, 30)) {
                val next = ReminderAlarmScheduler.nextTriggerAt(atNineAm(interval), now)
                assertTrue(
                    "interval=$interval at ${hour}:30 produced a non-future instant $next",
                    next > now
                )
            }
        }
    }
}
