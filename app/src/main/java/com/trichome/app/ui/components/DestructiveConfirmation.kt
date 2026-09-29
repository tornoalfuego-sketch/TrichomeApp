package com.trichome.app.ui.components

/**
 * A destructive action that has been asked for but not yet confirmed.
 *
 * This is the whole point of [ConfirmDestructiveDialog]: a tap on a delete icon
 * only *requests* a deletion. The database write happens exclusively inside
 * [confirm], so dismissing the dialog deletes nothing, confirming twice deletes
 * once, and there is no path from a tap to a repository call that skips the
 * dialog.
 *
 * @param onConfirmed invoked once, with the pending item, when the user confirms.
 */
class DestructiveConfirmation<T>(private val onConfirmed: (T) -> Unit) {

    /** The item waiting for confirmation, or `null` when nothing is pending. */
    var pending: T? = null
        private set

    /** A delete was requested. Nothing is written until [confirm]. */
    fun request(item: T) {
        pending = item
    }

    /** The user confirmed: the deletion runs exactly once. */
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
