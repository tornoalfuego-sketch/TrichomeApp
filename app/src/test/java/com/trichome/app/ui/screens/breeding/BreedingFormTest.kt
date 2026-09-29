package com.trichome.app.ui.screens.breeding

import com.trichome.app.data.entity.BreedingCross
import com.trichome.app.data.entity.BreedingProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the breeding editors: insert when the dialog was opened for a
 * new row, update when it was opened on an existing one.
 *
 * The bug this locks down: `BreedingDao` had no `@Update` at all, and
 * `CrossDialog` only ever inserted, so opening it on a cross that already
 * existed silently wrote a **second** row instead of changing the first. The
 * tell is [BreedingForm.isNew] — if it cannot distinguish "new" from "existing",
 * the duplicate is unavoidable.
 */
class BreedingFormTest {

    /** Pinned: the entity's own `createdAt` default reads the system clock. */
    private val now: Long = 1_800_000_000_000L

    private fun project(id: Long = 5L) = BreedingProject(
        id = id,
        name = "Amnesia Haze",
        motherId = "M-01",
        fatherId = "P-02",
        generation = "F1",
        createdAt = now - 10_000L,
        status = "archived"
    )

    private fun cross(id: Long = 8L, projectId: Long = 5L) = BreedingCross(
        id = id,
        projectId = projectId,
        parent1 = "M-01",
        parent2 = "P-02",
        phenotypeScore = 7f,
        notes = "Fenotipo resinoso"
    )

    /* ── Insert or update? ────────────────────────────────────────────── */

    @Test
    fun noRowIdMeansANewRow() {
        assertTrue(BreedingForm.isNew(BreedingForm.NO_ROW))
        assertTrue(BreedingForm.isNew(null))
    }

    @Test
    fun anyStoredIdMeansAnExistingRow() {
        assertFalse(BreedingForm.isNew(1L))
        assertFalse(BreedingForm.isNew(5L))
    }

    @Test
    fun aNewProjectCarriesNoIdSoTheInsertGetsOne() {
        val draft = BreedingForm.projectOf(null)

        assertTrue(BreedingForm.isNew(draft.id))
    }

    @Test
    fun anExistingProjectKeepsItsIdSoTheSaveUpdatesIt() {
        val draft = BreedingForm.projectOf(project())

        assertEquals(5L, draft.id)
        assertFalse(BreedingForm.isNew(draft.id))
    }

    @Test
    fun aNewCrossCarriesNoIdAndNoProjectBindingYet() {
        val draft = BreedingForm.crossOf(projectId = 5L, existing = null)

        assertTrue(BreedingForm.isNew(draft.id))
        assertEquals(5L, draft.projectId)
    }

    @Test
    fun anExistingCrossKeepsItsIdSoTheSaveUpdatesIt() {
        val draft = BreedingForm.crossOf(projectId = 5L, existing = cross())

        assertEquals(8L, draft.id)
        assertFalse(BreedingForm.isNew(draft.id))
    }

    /* ── Seeding ──────────────────────────────────────────────────────── */

    @Test
    fun theProjectFormIsSeededFromTheStoredRow() {
        val draft = BreedingForm.projectOf(project())

        assertEquals("Amnesia Haze", draft.name)
        assertEquals("M-01", draft.mother)
        assertEquals("P-02", draft.father)
        assertEquals("F1", draft.generation)
    }

    @Test
    fun theCrossFormIsSeededFromTheStoredRow() {
        val draft = BreedingForm.crossOf(projectId = 5L, existing = cross())

        assertEquals("M-01", draft.parent1)
        assertEquals("P-02", draft.parent2)
        assertEquals(7f, draft.score, 0.001f)
        assertEquals("Fenotipo resinoso", draft.notes)
    }

    @Test
    fun aNewFormStartsEmptyRatherThanHalfFilled() {
        val project = BreedingForm.projectOf(null)
        val cross = BreedingForm.crossOf(projectId = 5L, existing = null)

        assertEquals("", project.name)
        assertEquals("F1", project.generation)
        assertEquals("", cross.parent1)
        assertEquals(5f, cross.score, 0.001f)
    }

    /* ── Writing back ─────────────────────────────────────────────────── */

    @Test
    fun savingOverAnExistingProjectDoesNotTouchItsImmutableFields() {
        val existing = project()

        val saved = BreedingForm.projectFrom(BreedingForm.projectOf(existing).copy(name = "Amnesia Haze XL"))

        assertEquals(5L, saved.id)
        assertEquals(
            "`createdAt` is written once; an edit must not rewind it",
            existing.createdAt,
            saved.createdAt
        )
        assertEquals(
            "`status` is not on the form, so an update must not reset it to the default",
            existing.status,
            saved.status
        )
        assertEquals("Amnesia Haze XL", saved.name)
    }

    @Test
    fun savingOverAnExistingCrossKeepsItsIdAndProject() {
        val saved = BreedingForm.crossFrom(
            BreedingForm.crossOf(projectId = 5L, existing = cross()).copy(score = 9f)
        )

        assertEquals(8L, saved.id)
        assertEquals(5L, saved.projectId)
        assertEquals(9f, saved.phenotypeScore, 0.001f)
    }

    @Test
    fun aNewRowStampsTheCreationInstant() {
        val saved = BreedingForm.projectFrom(
            BreedingForm.projectOf(null).copy(name = "OG Kush x Haze"),
            now = now
        )

        assertEquals(BreedingForm.NO_ROW, saved.id)
        assertEquals(now, saved.createdAt)
        assertEquals("active", saved.status)
    }

    @Test
    fun aNewCrossStampsNoIdSoRoomAssignsOne() {
        val saved = BreedingForm.crossFrom(
            BreedingForm.crossOf(projectId = 5L, existing = null).copy(parent1 = "A", parent2 = "B")
        )

        assertEquals(BreedingForm.NO_ROW, saved.id)
    }

    @Test
    fun theFormIsTrimmedOnTheWayIn() {
        val saved = BreedingForm.projectFrom(
            BreedingForm.projectOf(null).copy(name = "  Amnesia  ", mother = " M-01 ")
        )

        assertEquals("Amnesia", saved.name)
        assertEquals("M-01", saved.motherId)
    }

    /* ── Validation ───────────────────────────────────────────────────── */

    @Test
    fun aProjectWithoutANameIsRejectedWithAReadableSpanishMessage() {
        val problem = BreedingForm.validate(BreedingForm.projectOf(null).copy(name = "  "))

        assertNotNull(problem)
        assertTrue("shown to the user as-is: $problem", problem!!.isNotBlank())
    }

    @Test
    fun aValidProjectHasNoProblem() {
        assertNull(BreedingForm.validate(BreedingForm.projectOf(null).copy(name = "Amnesia")))    }

    @Test
    fun aCrossScoreIsClampedToTheScaleItIsShownOn() {
        val high = BreedingForm.crossFrom(
            BreedingForm.crossOf(projectId = 1L, existing = null).copy(score = 14f)
        )
        val low = BreedingForm.crossFrom(
            BreedingForm.crossOf(projectId = 1L, existing = null).copy(score = -3f)
        )

        assertEquals(10f, high.phenotypeScore, 0.001f)
        assertEquals(0f, low.phenotypeScore, 0.001f)
    }

    @Test
    fun theGenerationsOfferedMatchTheOnesTheDialogShows() {
        assertTrue(BreedingForm.GENERATIONS.contains("F1"))
        assertTrue(BreedingForm.GENERATIONS.contains("IBL"))
    }

    @Test
    fun aGenerationNotInTheListIsStillAccepted() {
        // The row may hold a value typed elsewhere; refusing to save it would
        // make the project uneditable until the field is overwritten.
        val saved = BreedingForm.projectFrom(
            BreedingForm.projectOf(null).copy(name = "X", generation = "F5")
        )

        assertEquals("F5", saved.generation)
    }
}
