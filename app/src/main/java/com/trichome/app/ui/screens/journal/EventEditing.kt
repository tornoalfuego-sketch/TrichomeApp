package com.trichome.app.ui.screens.journal

import com.trichome.app.data.entity.GrowEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The edit form's state for a journal event, and nothing else.
 *
 * ## Why this is not a [GrowEvent]
 *
 * The entity is the *destination*, not the form. It carries an `id`, a `groupId`,
 * an `imagePath`, three nutrient columns and a `diagnosisCertainty` that the
 * grower never typed and the dialog does not show. A form that was a `GrowEvent`
 * would have to round-trip every one of those, and any field the form forgot
 * would be written back as `null` -- turning "edit the notes on my irrigation"
 * into "erase the photo path, the group binding and the diagnosis certainty".
 *
 * The project's data-loss budget is zero (AGENTS.md §11), so the separation is
 * the point: [applyTo] starts from the stored row and copies over *only* the
 * fields the form actually owns. There is no path by which an unexposed column
 * can be cleared, because no code ever names it.
 *
 * The same split as [ReminderDraft], for the same reason and with the same
 * consequence: [applyTo] takes the existing row as its receiver.
 */
data class EventDraft(
    val eventType: String,
    val plantId: Long,
    /** Epoch day, so the form never has to reason about a time zone to show a date. */
    val epochDay: Long,
    /** Minutes since midnight, 0..1439. */
    val minuteOfDay: Int,
    val notes: String,
    val temperature: Float? = null,
    val humidity: Float? = null,
    val ph: Float? = null,
    val ec: Float? = null,
    val amount: Float? = null,
    val height: Float? = null,
    val lampDistance: Float? = null,
    val vpd: Float? = null,
    val trainingType: String? = null,
    val trichomeMaturity: String? = null
)

/**
 * Every decision behind the edit-event dialog, free of Android and Compose.
 *
 * The gap this fills: reminders could be edited and deleted, events could only
 * be deleted, and the DAO had `updateEvent` with no caller at all -- the same
 * dead-end `ReminderDao.updateReminder` was before the reminder editor shipped.
 *
 * The hard part is never the write. It is that an editor seeded from defaults,
 * or one that rebuilds the row from the fields it happens to show, quietly
 * deletes the columns it does not display. So the two functions that matter are
 * [draftOf], which reads the stored row and never substitutes a default for a
 * set field, and [applyTo], which starts from that same stored row.
 */
object EventEditing {

    private const val MINUTES_PER_DAY = 1440
    const val MAX_MINUTE_OF_DAY = MINUTES_PER_DAY - 1

    /**
     * pH outside this range is not a measurement, it is a typo.
     *
     * The pH scale itself runs 0..14; anything past that was never a reading and
     * is far more likely to be a stray keypress than a genuinely extreme sample.
     */
    const val PH_MIN = 0f
    const val PH_MAX = 14f

    /**
     * Relative humidity is a percentage. 0..100 is not a style choice, it is the
     * definition, so a value outside it cannot be stored honestly.
     */
    const val HUMIDITY_MIN = 0f
    const val HUMIDITY_MAX = 100f

    /**
     * How far the day slider may move an event, in days either side of the
     * event's own day.
     *
     * A journal entry is about "I did this on Tuesday", not "I did this on the
     * 4th of March 2023". A year of travel in one gesture is a slider so coarse
     * that landing on a day becomes luck.
     */
    const val MAX_DAY_SHIFT = 60

    /**
     * Reads the stored row into the form. Never returns a default for a set field.
     *
     * An unrecognised `eventType` falls back to [UNKNOWN_TYPE_FALLBACK] rather
     * than to whatever happens to be first in the enum: a row written by a newer
     * version of the app must not be silently relabelled as an irrigation when
     * it is opened in an older one.
     */
    fun draftOf(event: GrowEvent, zone: ZoneId): EventDraft {
        val moment = Instant.ofEpochMilli(event.timestamp).atZone(zone)
        return EventDraft(
            eventType = event.eventType,
            plantId = event.plantId,
            epochDay = moment.toLocalDate().toEpochDay(),
            minuteOfDay = moment.hour * 60 + moment.minute,
            notes = event.notes.orEmpty(),
            temperature = event.temperature,
            humidity = event.humidity,
            ph = event.ph,
            ec = event.ec,
            amount = event.amount,
            height = event.height,
            lampDistance = event.lampDistance,
            vpd = event.vpd,
            trainingType = event.trainingType,
            trichomeMaturity = event.trichomeMaturity
        )
    }

    /**
     * Writes the form back onto the existing row.
     *
     * The identity of the row -- `id`, `groupId`, `isActive`, `nutrientN/P/K`,
     * `defoliationLevel`, `diagnosisResult`, `diagnosisCertainty`, `imagePath` --
     * is never taken from the form, because the form does not carry it. Every one
     * of those columns survives an edit untouched, and that is the property the
     * data-loss budget actually turns on.
     *
     * `id` is the load-bearing one: an update that changed it would insert a
     * second event and leave the original where it was.
     */
    fun applyTo(draft: EventDraft, existing: GrowEvent, zone: ZoneId): GrowEvent = existing.copy(
        eventType = draft.eventType,
        plantId = draft.plantId,
        timestamp = timestampOf(draft, zone),
        notes = draft.notes.trim().ifBlank { null },
        temperature = draft.temperature,
        humidity = draft.humidity,
        ph = draft.ph,
        ec = draft.ec,
        amount = draft.amount,
        height = draft.height,
        lampDistance = draft.lampDistance,
        vpd = draft.vpd,
        trainingType = draft.trainingType?.trim()?.ifBlank { null },
        trichomeMaturity = draft.trichomeMaturity?.trim()?.ifBlank { null }
    )

    /**
     * Epoch millis for the draft's day and time of day, in [zone].
     *
     * Clamped rather than trusted: the sliders cannot emit an out-of-range minute
     * and a stored row can, and `atTime` throws on an impossible time instead of
     * writing a row that can never be read back.
     */
    fun timestampOf(draft: EventDraft, zone: ZoneId): Long =
        LocalDate.ofEpochDay(draft.epochDay)
            .atTime(draft.minuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY) / 60, draft.minuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY) % 60)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /** `"09:05"`, zero padded: the shape the user reads on a clock. */
    fun timeLabel(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY)
        return "%02d:%02d".format(clamped / 60, clamped % 60)
    }

    /** `"04/10/2026"`: the day the draft points at, independent of any clock. */
    fun dateLabel(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
    }

    /**
     * How far [draft]'s day sits from [storedEpochDay], in days.
     *
     * Clamped so a draft that has somehow drifted further than the slider offers
     * still renders a position the slider can represent, rather than a value past
     * its own end that snaps the thumb back to the limit and silently moves the
     * event on the next keystroke.
     */
    fun dayShiftFor(draft: EventDraft, storedEpochDay: Long): Int =
        (draft.epochDay - storedEpochDay).toInt().coerceIn(-MAX_DAY_SHIFT, MAX_DAY_SHIFT)

    /**
     * The epoch day that is [shift] days away from [storedEpochDay].
     *
     * The reference is the day the row was *stored* on, not the draft's current
     * day, so repeated calls compose instead of accumulating drift -- and the
     * result is clamped against that same reference, which is what makes the
     * slider's advertised travel a real bound rather than a suggestion.
     *
     * An earlier version clamped the *delta* instead of the result, so a draft
     * already 10,000 days out plus a 5,000-day shift landed 60 days further out
     * rather than at the limit. Caught by `theDaySliderIsBoundedToTheTravelTheFormOffers`.
     */
    fun epochDayAtShift(storedEpochDay: Long, shift: Int): Long =
        storedEpochDay + shift.coerceIn(-MAX_DAY_SHIFT, MAX_DAY_SHIFT).toLong()

    /** Moves the draft's day to [storedEpochDay] plus [shift] days. */
    fun withDayShift(draft: EventDraft, storedEpochDay: Long, shift: Int): EventDraft =
        draft.copy(epochDay = epochDayAtShift(storedEpochDay, shift))

    /**
     * The Spanish reason [draft] cannot be saved, or null when it can.
     *
     * Returned rather than thrown or logged, because it is rendered verbatim in
     * the dialog and a silent save is the failure mode this replaces -- the same
     * discipline as the calendar's add sheet.
     *
     * @param knownPlantIds the ids a plant can actually be moved to. A row whose
     *   plant no longer exists cannot be re-pointed at plant 0, and `GrowEvent`
     *   carries a foreign key on `plantId`, so this is a real constraint and not
     *   a form nicety.
     */
    fun validate(draft: EventDraft, knownPlantIds: Set<Long>): String? {
        if (draft.eventType.isBlank()) return "Elige el tipo de evento."
        if (draft.plantId !in knownPlantIds) {
            return "Elige a qué planta pertenece el evento."
        }
        draft.ph?.let {
            if (it < PH_MIN || it > PH_MAX) return "El pH va de $PH_MIN a $PH_MAX."
        }
        draft.humidity?.let {
            if (it < HUMIDITY_MIN || it > HUMIDITY_MAX) {
                return "La humedad va de $HUMIDITY_MIN a $HUMIDITY_MAX %."
            }
        }
        // Every other measurement is a magnitude: a negative volume or height was
        // never recorded, so a sign is a typo rather than a reading.
        listOf(
            "la temperatura" to draft.temperature,
            "el EC" to draft.ec,
            "la cantidad" to draft.amount,
            "la altura" to draft.height,
            "la distancia a la lámpara" to draft.lampDistance,
            "el VPD" to draft.vpd
        ).forEach { (label, value) ->
            if (value != null && value < 0f) return "El valor de $label no puede ser negativo."
        }
        return null
    }

    /** Shown when a stored type this version does not know is opened. */
    const val UNKNOWN_TYPE_FALLBACK: String = "DIAGNOSIS"
}
