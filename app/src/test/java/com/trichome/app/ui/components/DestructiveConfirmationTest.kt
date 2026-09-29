package com.trichome.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for a destructive action that has to be confirmed.
 *
 * The bug this locks down: before the confirmation dialog every delete in the
 * app fired from a single tap on the trash icon. A tap must *request* a
 * deletion; only an explicit confirmation may reach the repository.
 */
class DestructiveConfirmationTest {

    private val deleted = mutableListOf<String>()
    private val confirmation = DestructiveConfirmation<String> { deleted.add(it) }

    @Test
    fun requestingADeletionDeletesNothing() {
        confirmation.request("Carpa 1")

        assertEquals(
            "a tap must not reach the repository before the user confirms",
            emptyList<String>(),
            deleted
        )
    }

    @Test
    fun aRequestedDeletionBecomesPendingSoTheDialogCanNameIt() {
        confirmation.request("Carpa 1")

        assertEquals("Carpa 1", confirmation.pending)
    }

    @Test
    fun confirmingDeletesThePendingItemExactlyOnce() {
        confirmation.request("Carpa 1")

        confirmation.confirm()

        assertEquals(listOf("Carpa 1"), deleted)
    }

    @Test
    fun confirmingTwiceDeletesOnce() {
        confirmation.request("Carpa 1")

        confirmation.confirm()
        confirmation.confirm()

        assertEquals(
            "a double confirm must not delete twice",
            listOf("Carpa 1"),
            deleted
        )
    }

    @Test
    fun dismissingDeletesNothingAndClearsThePendingItem() {
        confirmation.request("Carpa 1")

        confirmation.dismiss()

        assertTrue("dismissing must not delete anything", deleted.isEmpty())
        assertNull("dismiss must clear the pending item", confirmation.pending)
    }

    @Test
    fun confirmingAfterDismissingDeletesNothing() {
        confirmation.request("Carpa 1")
        confirmation.dismiss()

        confirmation.confirm()

        assertTrue(
            "a confirmation that arrives after a dismiss must not delete",
            deleted.isEmpty()
        )
    }

    @Test
    fun confirmingWithNothingPendingDeletesNothing() {
        confirmation.confirm()

        assertTrue(deleted.isEmpty())
    }

    @Test
    fun aSecondRequestReplacesTheFirstSoTheDialogNeverNamesTheWrongRow() {
        confirmation.request("Carpa 1")
        confirmation.request("Planta 2")

        assertEquals("Planta 2", confirmation.pending)

        confirmation.confirm()

        assertEquals(
            "only the row the user last tapped may be deleted",
            listOf("Planta 2"),
            deleted
        )
    }
}
