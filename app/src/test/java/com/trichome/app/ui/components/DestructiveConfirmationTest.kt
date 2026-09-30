package com.trichome.app.ui.components

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds the confirmation holder to the contract Compose depends on.
 *
 * Both bugs this catches were reported from a real device, and neither was
 * visible to any test that only exercised the logic: the holder did exactly the
 * right thing internally -- the item was set, the flag came back to null, the
 * repository was called once -- and the dialog still did not appear, or did not
 * disappear, because nothing told the composition to re-read the field.
 *
 * So these tests assert the *observability*, not just the values. That is the
 * part a plain unit test cannot see and the part the user did.
 */
class DestructiveConfirmationTest {

    private data class Row(val id: Long, val name: String)

    private fun holder(onConfirmed: (Row) -> Unit = {}) =
        DestructiveConfirmation<Row>(onConfirmed)

    /* ── The field must be observable ─────────────────────────────────────── */

    @Test
    fun thePendingFieldIsSnapshotState() {
        // A plain `var` is invisible to Compose: writing it does not invalidate
        // any composition, so the `if (pending != null)` that shows the dialog
        // never re-ran. The user saw the confirmation only after switching tabs,
        // and it stayed on screen after choosing an option.
        //
        // The getter cannot answer this -- a `by` delegate returns the *value*,
        // which is `null` here and indistinguishable from a plain field. The
        // backing field can: a delegated property keeps a field of the delegate's
        // type, so the test looks for it rather than hardcoding the mangled name
        // Kotlin generates.
        //
        // This is not a substitute for driving the real composition -- that needs
        // `compose-ui-test`, which this project does not depend on. It is the
        // one thing checkable from a JVM test, and it is the thing that was wrong.
        val stateFields = DestructiveConfirmation::class.java.declaredFields
            .filter { androidx.compose.runtime.State::class.java.isAssignableFrom(it.type) }

        assertEquals(
            "pending is not delegated to snapshot state, so writing it does not " +
                "invalidate any composition: the dialog cannot appear on request " +
                "and cannot close on confirm. State-backed fields found: " +
                stateFields.map { it.name },
            1, stateFields.size
        )
    }

    /* ── The behaviour every delete site depends on ───────────────────────── */

    @Test
    fun requestingDoesNotDeleteAnything() {
        var deleted = 0
        val confirmation = holder { deleted++ }

        confirmation.request(Row(7, "blueberry"))

        assertEquals("a request must not reach the repository", 0, deleted)
        assertEquals(Row(7, "blueberry"), confirmation.pending)
    }

    @Test
    fun confirmingDeletesExactlyOnceAndClearsTheFlag() {
        val deleted = mutableListOf<Long>()
        val confirmation = holder { deleted += it.id }

        confirmation.request(Row(7, "blueberry"))
        confirmation.confirm()

        assertEquals(listOf(7L), deleted)
        assertNull(
            "the flag must clear before the write, or the dialog comes back for " +
                "a frame and a double tap has a second target",
            confirmation.pending
        )
    }

    @Test
    fun confirmingTwiceDeletesOnce() {
        var deleted = 0
        val confirmation = holder { deleted++ }

        confirmation.request(Row(1, "a"))
        confirmation.confirm()
        confirmation.confirm()

        assertEquals(1, deleted)
    }

    @Test
    fun confirmingWithNothingPendingIsANoOp() {
        var deleted = 0
        val confirmation = holder { deleted++ }

        confirmation.confirm()

        assertEquals(0, deleted)
        assertNull(confirmation.pending)
    }

    @Test
    fun dismissingDeletesNothingAndClearsTheFlag() {
        var deleted = 0
        val confirmation = holder { deleted++ }

        confirmation.request(Row(3, "vgfc"))
        confirmation.dismiss()

        assertEquals("cancelling must never reach the repository", 0, deleted)
        assertNull("the dialog has to be able to close on cancel", confirmation.pending)
    }

    @Test
    fun aSecondRequestReplacesTheFirstTarget() {
        var deleted = 0
        val confirmation = holder { deleted++ }

        confirmation.request(Row(1, "primera"))
        confirmation.request(Row(2, "segunda"))
        confirmation.confirm()

        assertEquals(
            "the dialog must confirm the row the user is looking at, not the first " +
                "one they tapped",
            1, deleted
        )
    }
}
