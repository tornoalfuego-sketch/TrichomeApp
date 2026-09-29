package com.trichome.app.model

import com.trichome.app.data.entity.GrowEvent
import java.time.YearMonth
import java.time.ZoneId

/**
 * The month window the calendar renders, and the decision "does this event
 * belong in it".
 *
 * The bug this exists for: the calendar read its events with the one-shot
 * `EventDao.getEventsBetween(from, to)` and wrote them into a `mutableStateOf`,
 * so a row saved while the screen was already open stayed invisible until the
 * user changed month or plant filter. Making the read a Flow fixes the symptom,
 * but only if the *window* is the single piece of state that decides what the
 * screen shows — otherwise a reactive query and a plant filter can disagree and
 * an event that was just written is written off as "not in this month".
 *
 * Two different questions, deliberately kept apart:
 * - [belongsToWindow] — is the row in the range the query asked Room for?
 *   Window and filter both answer here, because a Flow query has to be
 *   parameterised by both or it either re-reads the whole table or never
 *   re-queries when the filter changes.
 * - [visibleEvents] — which of the rows already fetched does the screen draw?
 *
 * Everything here is pure: no Android, no Compose, no Room. It lives in the
 * model package beside SuperCycleEngine and StageProgressEngine precisely
 * because of that: the viewmodel package needs it, so keeping it under ui
 * would make viewmodel depend on ui while ui already depends on viewmodel.
 */
object CalendarWindow {

    /**
     * Closed millis range covering [month] entirely, in [zone].
     *
     * Both ends are inclusive, which is what `LongRange` means; the Room query
     * uses the `BETWEEN :from AND :to` form, so it is fed the same two values.
     */
    fun boundsFor(month: YearMonth, zone: ZoneId): LongRange {
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from..to
    }

    /**
     * First millisecond **after** [month], for callers that want a half-open
     * range. Kept next to [boundsFor] so the two can never drift apart.
     */
    fun exclusiveEndFor(month: YearMonth, zone: ZoneId): Long =
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Whether [event] must be part of the rows shown for [window] and
     * [filterPlantId].
     *
     * @param filterPlantId null means "Todas": every plant in the window is
     *   included. It must never mean "a plant literally null", which would make
     *   the unfiltered view empty.
     */
    fun belongsToWindow(
        event: GrowEvent,
        window: LongRange,
        filterPlantId: Long?
    ): Boolean {
        if (event.timestamp !in window) return false
        return filterPlantId == null || event.plantId == filterPlantId
    }

    /**
     * The rows the calendar draws: everything [window] holds, narrowed by the
     * active plant filter.
     */
    fun visibleEvents(events: List<GrowEvent>, filterPlantId: Long?): List<GrowEvent> =
        if (filterPlantId == null) events else events.filter { it.plantId == filterPlantId }

    /**
     * Whether a new window requires a fresh query.
     *
     * A month change must re-query; the very same month must not, because
     * re-arming the collector would drop the rows already on screen and query
     * again for no reason.
     */
    fun shouldReload(current: LongRange?, next: LongRange): Boolean = current != next
}
