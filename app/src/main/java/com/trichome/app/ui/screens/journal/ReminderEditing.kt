package com.trichome.app.ui.screens.journal

import com.trichome.app.data.entity.Reminder

/**
 * One recurrence choice offered by the edit form.
 *
 * [type] is written verbatim to `Reminder.recurrenceType`, which the entity
 * stores as a raw `String`; the values match the ones the existing create paths
 * already write ("none", "daily", "weekly", "custom") so an edited reminder is
 * indistinguishable from a created one.
 */
data class ReminderRecurrence(
    val type: String,
    val labelEs: String,
    val intervalDays: Int
)

/**
 * The edit form's state, and nothing else.
 *
 * It is deliberately *not* a [Reminder]: the entity carries the identity
 * (`id`, `plantId`, `isActive`) and the derived `message`, none of which the
 * grower typed. Keeping the two apart is what stops a half-filled dialog from
 * overwriting a row with defaults.
 */
data class ReminderDraft(
    val title: String = "",
    val hour: Int = 9,
    val minute: Int = 0,
    val recurrenceType: String = "weekly",
    val recurrenceIntervalDays: Int = 7
)

/**
 * The decision behind the edit-reminder dialog, free of Android and Compose so
 * it runs on the JVM under test.
 *
 * The bug this exists for: `ReminderDao.updateReminder` had no callers at all,
 * so a reminder could be created but never changed or removed. The hard part was
 * never the DAO — it was that opening an editor seeded from defaults silently
 * destroys the row's real values, and that `reminderTime` is millis-of-day, so
 * a wrong conversion produces an alarm that fires an hour off.
 */
object ReminderEditing {

    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_DAY = 86_400_000L

    /** Longest interval the form offers, matching the create card's slider. */
    const val MAX_INTERVAL_DAYS = 60

    /** Recurrence choices, in the order the dialog shows them. */
    val RECURRENCE_PRESETS: List<ReminderRecurrence> = listOf(
        ReminderRecurrence("none", "No se repite", 0),
        ReminderRecurrence("daily", "Cada día", 1),
        ReminderRecurrence("weekly", "Cada 7 días", 7),
        ReminderRecurrence("biweekly", "Cada 14 días", 14),
        ReminderRecurrence("custom", "Personalizado", 7)
    )

    /** The preset for "does not repeat", needed when a row has interval 0. */
    val NONE: ReminderRecurrence get() = RECURRENCE_PRESETS[0]

    /**
     * Millis-of-day for a wall-clock time, clamped into the range the alarm
     * scheduler accepts.
     *
     * `Reminder.reminderTime` is millis since midnight, not an epoch: a form
     * that passed an absolute instant here would arm the alarm at the wrong
     * time of day, and the entity's own 86_399_999 ceiling exists precisely
     * because that is the contract.
     */
    fun millisOfDay(hour: Int, minute: Int): Long =
        (hour.coerceIn(0, 23) * MILLIS_PER_HOUR + minute.coerceIn(0, 59) * MILLIS_PER_MINUTE)
            .coerceIn(0L, MILLIS_PER_DAY - 1)

    /** Reads the stored row into the form. Never returns defaults for a set field. */
    fun draftOf(reminder: Reminder): ReminderDraft {
        val time = reminder.reminderTime.coerceIn(0L, MILLIS_PER_DAY - 1)
        return ReminderDraft(
            title = reminder.title,
            hour = (time / MILLIS_PER_HOUR).toInt(),
            minute = ((time % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt(),
            recurrenceType = reminder.recurrenceType,
            recurrenceIntervalDays = reminder.recurrenceIntervalDays
        )
    }

    /**
     * Writes the form back onto the existing row.
     *
     * The identity of the row — id, plant binding and active flag — is never
     * taken from the form, because the form does not carry it. That is the whole
     * point: an update that changed the id would insert a second reminder and
     * leave the old alarm armed.
     */
    fun applyTo(draft: ReminderDraft, existing: Reminder): Reminder = existing.copy(
        title = draft.title.trim(),
        message = messageFor(draft.title.trim(), existing),
        recurrenceType = draft.recurrenceType,
        recurrenceIntervalDays = draft.recurrenceIntervalDays,
        reminderTime = millisOfDay(draft.hour, draft.minute)
    )

    /**
     * The body shown in the notification.
     *
     * The create paths write `"Recordatorio: <title>"` as a convenience, so an
     * edited title would otherwise leave the body describing the old one. A
     * message the grower typed is left untouched — it is theirs.
     */
    private fun messageFor(newTitle: String, existing: Reminder): String {
        val generated = autoMessage(existing.title)
        return if (existing.message == generated) autoMessage(newTitle) else existing.message
    }

    /** The body the create forms generate for [title]. */
    fun autoMessage(title: String): String = "Recordatorio: $title"

    /**
     * The Spanish reason [draft] cannot be saved, or null when it can.
     *
     * Returned rather than thrown or logged, because it is rendered verbatim in
     * the dialog and a silent save is the failure mode this replaces.
     */
    fun validate(draft: ReminderDraft): String? {
        if (draft.title.isBlank()) return "El título no puede estar vacío."
        if (draft.recurrenceIntervalDays < 0) {
            return "El intervalo de repetición no puede ser negativo."
        }
        if (draft.recurrenceIntervalDays > MAX_INTERVAL_DAYS) {
            return "El intervalo no puede superar los $MAX_INTERVAL_DAYS días."
        }
        // Zero is only meaningful for a reminder that does not repeat; anything
        // else with no interval would never fire again and would look scheduled.
        if (draft.recurrenceIntervalDays == 0 && draft.recurrenceType != NONE.type) {
            return "Elige cada cuántos días se repite el recordatorio."
        }
        return null
    }

    /** `"09:05"`, zero padded: the shape the user reads on a clock. */
    fun timeLabel(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    /**
     * The preset [draft] corresponds to.
     *
     * An exact type-and-interval match wins; a row with an interval the presets
     * do not list (a "custom" reminder the grower tuned to 3 days) still falls
     * back to its type, so the editor highlights the right chip instead of none.
     */
    fun presetFor(draft: ReminderDraft): ReminderRecurrence? =
        RECURRENCE_PRESETS.firstOrNull {
            it.type == draft.recurrenceType && it.intervalDays == draft.recurrenceIntervalDays
        } ?: RECURRENCE_PRESETS.firstOrNull { it.type == draft.recurrenceType }

    /** Spanish label of a recurrence, for the row summary. */
    fun recurrenceLabel(recurrence: ReminderRecurrence): String = recurrence.labelEs
}
