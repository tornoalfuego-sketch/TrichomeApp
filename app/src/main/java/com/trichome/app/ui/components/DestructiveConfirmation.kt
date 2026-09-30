package com.trichome.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A destructive action that has been asked for but not yet confirmed.
 *
 * This is the whole point of [ConfirmDestructiveDialog]: a tap on a delete icon
 * only *requests* a deletion. The database write happens exclusively inside
 * [confirm], so dismissing the dialog deletes nothing, confirming twice deletes
 * once, and there is no path from a tap to a repository call that skips the
 * dialog.
 *
 * [pending] is snapshot state, and it has to be. It was a plain `var`, and that
 * produced two bugs at once, both reported from a device:
 *
 * - **The dialog did not appear until something else forced a recomposition.**
 *   Writing a plain field does not invalidate any composition, so the `if (pending
 *   != null)` that shows the dialog never re-ran. Switching tabs did, which is
 *   why the confirmation appeared to be "late" rather than missing.
 * - **The dialog did not close after confirming or cancelling.** The same missing
 *   invalidation: `pending` went back to null on the object, but nothing recomposed
 *   to take the dialog off screen, so it stayed there looking unhandled.
 *
 * Six screens share this holder -- plants, tents, reminders, journal events,
 * protocols, breeding projects and crosses -- so both bugs were in all of them.
 *
 * @param onConfirmed invoked once, with the pending item, when the user confirms.
 */
class DestructiveConfirmation<T>(private val onConfirmed: (T) -> Unit) {

    /**
     * The item waiting for confirmation, or `null` when nothing is pending.
     *
     * Read by [rememberDestructiveConfirmation] to decide whether to show the
     * dialog, and written by [request], [confirm] and [dismiss]. Every one of
     * those four paths is a composition boundary, so the field is observable.
     */
    var pending: T? by mutableStateOf(null)
        private set

    /**
     * A delete was requested. Nothing is written until [confirm].
     *
     * Clearing any previous pending item first means a second delete icon tapped
     * while a confirmation is open replaces the target rather than queueing it,
     * so the dialog can never confirm a row the user is no longer looking at.
     */
    fun request(item: T) {
        pending = item
    }

    /**
     * The user confirmed: the deletion runs exactly once.
     *
     * [pending] is cleared *before* [onConfirmed] runs. That order matters: the
     * write triggers a recomposition, and if the flag were still set the dialog
     * would be on screen again for the frame in between, and a double tap on the
     * confirm button would have a second target to fire at.
     */
    fun confirm() {
        val target = pending ?: return
        pending = null
        onConfirmed(target)
    }

    /** The user backed out: the pending item is dropped, nothing is deleted. */
    fun dismiss() {
        pending = null
    }
}
