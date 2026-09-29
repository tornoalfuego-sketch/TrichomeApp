package com.trichome.app.ui.screens.journal

import com.trichome.app.data.entity.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the edit-reminder form: what the dialog collects, and what it
 * writes back onto the [Reminder] row.
 *
 * The bug this locks down: `ReminderDao.updateReminder` had zero callers, so the
 * only reminder screen was create-only. A dialog that seeded itself with
 * defaults instead of the existing row would have silently destroyed the user's
 * title, time and recurrence the first time they opened it — the same
 * `remember`-seeding trap that already bit `ProtocolScreen` in v1.1.0.
 *
 * `reminderTime` is millis-of-day, not an epoch, so the encoding is pinned here
 * too: an off-by-one hour in this conversion is an alarm that fires an hour off
 * for every reminder the user ever has.
 */
class ReminderEditingTest {

    private fun existing() = Reminder(
        id = 12L,
        plantId = 4L,
        title = "Riego semanal",
        message = "Recordatorio: Riego semanal",
        recurrenceType = "custom",
        recurrenceIntervalDays = 7,
        reminderTime = 9 * 3_600_000L,
        isActive = true
    )

    private fun draft(
        title: String = "Riego semanal",
        hour: Int = 9,
        minute: Int = 0,
        recurrenceType: String = "custom",
        intervalDays: Int = 7
    ) = ReminderDraft(
        title = title,
        hour = hour,
        minute = minute,
        recurrenceType = recurrenceType,
        recurrenceIntervalDays = intervalDays
    )

    /* ── Reading the stored row back into the form ────────────────────── */

    @Test
    fun theDraftIsSeededFromTheStoredRowNotFromDefaults() {
        val seeded = ReminderEditing.draftOf(
            existing().copy(title = "Poda", recurrenceType = "daily", recurrenceIntervalDays = 1, reminderTime = 21 * 3_600_000L)
        )

        assertEquals("Poda", seeded.title)
        assertEquals(21, seeded.hour)
        assertEquals(0, seeded.minute)
        assertEquals("daily", seeded.recurrenceType)
        assertEquals(1, seeded.recurrenceIntervalDays)
    }

    @Test
    fun millisOfDayRoundTripThroughTheDraft() {
        val at1430 = existing().copy(reminderTime = 14 * 3_600_000L + 30 * 60_000L)

        val seeded = ReminderEditing.draftOf(at1430)

        assertEquals(14, seeded.hour)
        assertEquals(30, seeded.minute)
        assertEquals(at1430.reminderTime, ReminderEditing.millisOfDay(14, 30))
    }

    @Test
    fun midnightAndTheLastMinuteOfTheDayAreBothEncodable() {
        assertEquals(0L, ReminderEditing.millisOfDay(0, 0))
        assertEquals(23 * 3_600_000L + 59 * 60_000L, ReminderEditing.millisOfDay(23, 59))
    }

    @Test
    fun anOutOfRangeTimeIsClampedInsteadOfProducingAnImpossibleInstant() {
        // `ReminderAlarmScheduler.nextTriggerAt` coerces to 0..86_399_999, but a
        // value above that would still show a wrong clock in the UI.
        assertEquals(0L, ReminderEditing.millisOfDay(-1, 0))
        assertEquals(23 * 3_600_000L, ReminderEditing.millisOfDay(25, 0))
        assertEquals(9 * 3_600_000L + 59 * 60_000L, ReminderEditing.millisOfDay(9, 99))
        assertEquals(
            "whatever the input, the result must stay inside a real day",
            true,
            ReminderEditing.millisOfDay(Int.MAX_VALUE, Int.MAX_VALUE) in 0L..86_399_999L
        )
    }

    /* ── Writing the form back onto the row ───────────────────────────── */

    @Test
    fun editingKeepsTheIdentityAndOnlyChangesWhatTheUserTyped() {
        val updated = ReminderEditing.applyTo(draft(title = "Riego de la mañana", hour = 7, minute = 15), existing())

        assertEquals("the id must not change, an update would insert a second row", 12L, updated.id)
        assertEquals("the plant binding must not change", 4L, updated.plantId)
        assertEquals(true, updated.isActive)
        assertEquals("Riego de la mañana", updated.title)
        assertEquals(7 * 3_600_000L + 15 * 60_000L, updated.reminderTime)
    }

    @Test
    fun editingCanChangeTheRecurrence() {
        val updated = ReminderEditing.applyTo(
            draft(recurrenceType = "daily", intervalDays = 1), existing()
        )

        assertEquals("daily", updated.recurrenceType)
        assertEquals(1, updated.recurrenceIntervalDays)
    }

    @Test
    fun aGeneratedMessageFollowsTheNewTitleButACustomOneIsLeftAlone() {
        val generated = ReminderEditing.applyTo(draft(title = "Nuevo"), existing())
        assertEquals(
            "the body was derived from the old title, it has to follow",
            "Recordatorio: Nuevo",
            generated.message
        )

        val custom = existing().copy(message = "No olvidar la maca")
        val kept = ReminderEditing.applyTo(draft(title = "Nuevo"), custom)
        assertEquals(
            "a message the grower wrote is theirs, not ours to overwrite",
            "No olvidar la maca",
            kept.message
        )
    }

    @Test
    fun anEditedReminderCanBeMadeInactiveWithoutLosingTheRow() {
        val paused = ReminderEditing.applyTo(draft(), existing()).copy(isActive = false)

        assertFalse(paused.isActive)
        assertEquals(12L, paused.id)
    }

    /* ── Validation ───────────────────────────────────────────────────── */

    @Test
    fun anEmptyTitleIsRejectedWithAReadableSpanishMessage() {
        val problem = ReminderEditing.validate(draft(title = "   "))

        assertNotNull("a blank title must not be savable", problem)
        assertTrue(
            "the message is shown to the user as-is: $problem",
            problem!!.isNotBlank()
        )
    }

    @Test
    fun aValidDraftHasNoProblem() {
        assertNull(ReminderEditing.validate(draft()))
        assertNull(ReminderEditing.validate(draft(title = "  Poda  ")))
    }

    @Test
    fun aRecurringReminderWithNoIntervalIsRejected() {
        // Zero is the "does not repeat" encoding, not a missing answer: a
        // weekly reminder with interval 0 would never fire again while still
        // looking scheduled.
        assertNotNull(ReminderEditing.validate(draft(intervalDays = 0)))
        assertNotNull(ReminderEditing.validate(draft(intervalDays = -3)))
    }

    @Test
    fun aNonRepeatingReminderMayCarryNoInterval() {
        assertNull(
            "the calendar already creates 'none' reminders with interval 0",
            ReminderEditing.validate(draft(recurrenceType = "none", intervalDays = 0))
        )
    }

    @Test
    fun anIntervalBeyondTheSupportedRangeIsRejected() {
        assertNull(ReminderEditing.validate(draft(intervalDays = ReminderEditing.MAX_INTERVAL_DAYS)))
        assertNotNull(
            ReminderEditing.validate(draft(intervalDays = ReminderEditing.MAX_INTERVAL_DAYS + 1))
        )
    }

    /* ── Labels ───────────────────────────────────────────────────────── */

    @Test
    fun theTimeLabelIsZeroPaddedToTheClockTheUserReads() {
        assertEquals("09:05", ReminderEditing.timeLabel(9, 5))
        assertEquals("00:00", ReminderEditing.timeLabel(0, 0))
        assertEquals("23:59", ReminderEditing.timeLabel(23, 59))
    }

    @Test
    fun everyRecurrencePresetRoundTripsThroughTheDraft() {
        ReminderEditing.RECURRENCE_PRESETS.forEach { preset ->
            val seeded = ReminderEditing.draftOf(
                existing().copy(
                    recurrenceType = preset.type,
                    recurrenceIntervalDays = preset.intervalDays
                )
            )

            assertEquals(
                "preset ${preset.type} must survive a round trip",
                preset,
                ReminderEditing.presetFor(seeded)
            )
        }
    }

    @Test
    fun aCustomIntervalFallsBackToTheCustomPreset() {
        val seeded = ReminderEditing.draftOf(
            existing().copy(recurrenceType = "custom", recurrenceIntervalDays = 3)
        )

        assertEquals("custom", ReminderEditing.presetFor(seeded)?.type)
        assertEquals(3, seeded.recurrenceIntervalDays)
    }

    @Test
    fun theRecurrenceLabelIsSpanishAndNamesTheInterval() {
        assertEquals("No se repite", ReminderEditing.recurrenceLabel(ReminderEditing.NONE))
        assertEquals("Cada 7 días", ReminderEditing.recurrenceLabel(ReminderEditing.RECURRENCE_PRESETS[2]))
    }
}
