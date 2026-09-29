package com.trichome.app.model

import com.trichome.app.data.entity.GrowEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Contract for "does this event belong in the month the user is looking at".
 *
 * The bug this locks down: the calendar read its events with a one-shot
 * `getEventsBetween(from, to)` fired from `LaunchedEffect(month, filterPlantId)`,
 * so a row written while the screen was already open stayed invisible until the
 * user changed month or plant filter. The window is the piece of state that
 * decides whether a freshly written event must show up, so it is pinned here.
 *
 * Every case pins its own dates, so the suite never reads the wall clock.
 */
class CalendarWindowTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val march: YearMonth = YearMonth.of(2026, 3)

    private fun millis(date: LocalDate, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    private fun event(plantId: Long, at: Long, id: Long = 1L) = GrowEvent(
        id = id,
        plantId = plantId,
        eventType = "IRRIGATION",
        timestamp = at
    )

    /* ── The window itself ────────────────────────────────────────────── */

    @Test
    fun theWindowCoversEveryInstantOfTheRenderedMonth() {
        val window = CalendarWindow.boundsFor(march, zone)

        assertEquals(
            "the window must open on the first millisecond of the 1st",
            millis(LocalDate.of(2026, 3, 1), 0, 0),
            window.first
        )
        assertEquals(
            "the window must close on the last millisecond of the last day",
            millis(LocalDate.of(2026, 3, 31), 23, 59) + 59_999L,
            window.last
        )
    }

    @Test
    fun theWindowIsExactlyTheRangeTheMonthQueryUses() {
        val window = CalendarWindow.boundsFor(march, zone)

        assertTrue("the window must not be empty", !window.isEmpty())
        assertEquals(
            "the exclusive upper bound is what Room's BETWEEN needs",
            window.last + 1,
            CalendarWindow.exclusiveEndFor(march, zone)
        )
    }

    @Test
    fun aFebruaryWindowRespectsTheRealMonthLength() {
        val window = CalendarWindow.boundsFor(YearMonth.of(2028, 2), zone)

        assertEquals(
            "2028 is a leap year: 29 days, not 28",
            millis(LocalDate.of(2028, 2, 29), 23, 59) + 59_999L,
            window.last
        )
    }

    /* ── Does a written event belong here? ────────────────────────────── */

    @Test
    fun anEventInsideTheWindowBelongs() {
        val window = CalendarWindow.boundsFor(march, zone)

        assertTrue(
            CalendarWindow.belongsToWindow(event(1L, millis(LocalDate.of(2026, 3, 15))), window, null)
        )
    }

    @Test
    fun anEventFromAnotherMonthDoesNotBelong() {
        val window = CalendarWindow.boundsFor(march, zone)

        assertFalse(
            "February is not March",
            CalendarWindow.belongsToWindow(
                event(1L, millis(LocalDate.of(2026, 2, 28))), window, null
            )
        )
        assertFalse(
            "April is not March",
            CalendarWindow.belongsToWindow(
                event(1L, millis(LocalDate.of(2026, 4, 1))), window, null
            )
        )
    }

    @Test
    fun theFirstAndLastDayOfTheWindowAreBothInsideIt() {
        val window = CalendarWindow.boundsFor(march, zone)

        assertTrue(
            "the 1st at 00:00 is the first valid instant",
            CalendarWindow.belongsToWindow(
                event(1L, millis(LocalDate.of(2026, 3, 1), 0, 0)), window, null
            )
        )
        assertTrue(
            "the last day at 23:59:59.999 is the last valid instant",
            CalendarWindow.belongsToWindow(
                event(1L, millis(LocalDate.of(2026, 3, 31), 23, 59) + 59_999L), window, null
            )
        )
    }

    @Test
    fun theMillisecondBeforeAndAfterTheWindowAreBothOutsideIt() {
        val window = CalendarWindow.boundsFor(march, zone)

        assertFalse(
            "one millisecond early is the previous month",
            CalendarWindow.belongsToWindow(event(1L, window.first - 1), window, null)
        )
        assertFalse(
            "one millisecond late is the next month",
            CalendarWindow.belongsToWindow(event(1L, window.last + 1), window, null)
        )
    }

    @Test
    fun anEventOfAnotherPlantIsExcludedWhileAPlantFilterIsActive() {
        val window = CalendarWindow.boundsFor(march, zone)
        val at = millis(LocalDate.of(2026, 3, 15))

        assertFalse(
            "the filter is on plant 7, this event belongs to plant 9",
            CalendarWindow.belongsToWindow(event(9L, at), window, filterPlantId = 7L)
        )
        assertTrue(
            "the same event is the filter's own plant",
            CalendarWindow.belongsToWindow(event(7L, at), window, filterPlantId = 7L)
        )
    }

    @Test
    fun aNullFilterKeepsEveryPlantInTheWindow() {
        val window = CalendarWindow.boundsFor(march, zone)
        val at = millis(LocalDate.of(2026, 3, 15))

        assertTrue(
            "'Todas' must not behave like a filter that matches nothing",
            CalendarWindow.belongsToWindow(event(1L, at), window, filterPlantId = null)
        )
        assertTrue(
            CalendarWindow.belongsToWindow(event(9L, at), window, filterPlantId = null)
        )
    }

    @Test
    fun aFilteredOutEventIsStillInsideTheWindowItWasWrittenFor() {
        // Window membership and filter membership are two different questions;
        // conflating them is how "my event vanished" bugs are born.
        val window = CalendarWindow.boundsFor(march, zone)
        val at = millis(LocalDate.of(2026, 3, 15))

        assertTrue(
            "the row is in March, so the query must return it",
            CalendarWindow.belongsToWindow(event(9L, at), window, filterPlantId = null)
        )
        assertFalse(
            "...and the screen must then hide it under the active filter",
            CalendarWindow.visibleEvents(listOf(event(7L, at), event(9L, at)), 7L)
                .map { it.plantId }
                .contains(9L)
        )
    }

    /* ── Which events the screen renders ──────────────────────────────── */

    @Test
    fun visibleEventsAppliesTheFilterAndKeepsTheRestUntouched() {
        val at = millis(LocalDate.of(2026, 3, 15))
        val all = listOf(event(7L, at, id = 1), event(9L, at, id = 2))

        assertEquals(2, CalendarWindow.visibleEvents(all, null).size)
        assertEquals(listOf(1L), CalendarWindow.visibleEvents(all, 7L).map { it.id })
        assertEquals(
            "a filter that matches nothing empties the list, it never crashes",
            0,
            CalendarWindow.visibleEvents(all, 99L).size
        )
    }

    /* ── When is a re-query actually needed? ──────────────────────────── */

    @Test
    fun theSameMonthIsNotReQueriedButAChangeOfMonthIs() {
        val first = CalendarWindow.boundsFor(march, zone)
        val again = CalendarWindow.boundsFor(march, zone)
        val next = CalendarWindow.boundsFor(march.plusMonths(1), zone)

        assertFalse(
            "re-arming the collector for an identical window is a wasted query",
            CalendarWindow.shouldReload(first, again)
        )
        assertTrue(
            CalendarWindow.shouldReload(first, next)
        )
    }

    @Test
    fun theFirstWindowAlwaysLoadsEvenThoughThereIsNoPreviousOne() {
        assertTrue(
            "a cold screen has no window yet, so it must query",
            CalendarWindow.shouldReload(null, CalendarWindow.boundsFor(march, zone))
        )
    }

    @Test
    fun theWindowIsRebuiltFromTheMonthNotTheWallClock() {
        val built = CalendarWindow.boundsFor(march, zone)

        assertEquals(
            "two calls for the same month must agree, so the collector is not restarted",
            built,
            CalendarWindow.boundsFor(march, zone)
        )
    }
}
