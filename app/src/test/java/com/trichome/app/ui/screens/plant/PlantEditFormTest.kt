package com.trichome.app.ui.screens.plant

import com.trichome.app.data.entity.Plant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the plant editor and for what a plant delete actually removes.
 *
 * The bug this locks down: `PlantDetailScreen` had no action icons at all and
 * `PlantDetailViewModel` had no edit and no delete method, so a plant could
 * only be renamed or removed from a tent row. The form half matters as much as
 * the buttons: `AddPlantRow` creates with `strain = ""`, `stage = "seedling"`
 * and `growStart = System.currentTimeMillis()`, and reusing those defaults for
 * an edit would silently reset a flowering plant to seedling day one.
 */
class PlantEditFormTest {

    /** Pinned: the entity's own defaults read the system clock. */
    private val now: Long = 1_800_000_000_000L
    private val day: Long = 86_400_000L

    private fun plant() = Plant(
        id = 9L,
        name = "OG Kush",
        tentId = 3L,
        sortOrder = 2,
        growStartTimestamp = now - 40 * day,
        currentStage = "floracion",
        strain = "Critical",
        notes = "Riego cada 3 días",
        isActive = true,
        createdAt = now - 60 * day
    )

    private fun form(
        name: String = "OG Kush",
        strain: String = "Critical",
        stage: String = "floracion",
        notes: String = "Riego cada 3 días"
    ) = PlantForm(name = name, strain = strain, stage = stage, notes = notes)

    /* ── Reading the row into the form ────────────────────────────────── */

    @Test
    fun theFormIsSeededFromTheStoredRowNotFromCreateDefaults() {
        val seeded = PlantEditForm.formOf(plant())

        assertEquals("OG Kush", seeded.name)
        assertEquals("Critical", seeded.strain)
        assertEquals("floracion", seeded.stage)
        assertEquals("Riego cada 3 días", seeded.notes)
    }

    @Test
    fun seedingIsExactSoAnUntouchedDialogWritesBackTheSameValues() {
        val original = plant()

        val round = PlantEditForm.applyTo(PlantEditForm.formOf(original), original)

        assertEquals("an untouched editor must be a no-op", original, round)
    }

    /* ── Writing the form back onto the row ───────────────────────────── */

    @Test
    fun editingPreservesTheIdentityAndTheGrowStart() {
        val original = plant()

        val updated = PlantEditForm.applyTo(form(name = "OG Kush #2"), original)

        assertEquals("the id must not change or the update inserts a second plant", 9L, updated.id)
        assertEquals("the tent binding must not change", 3L, updated.tentId)
        assertEquals("day one of flowering is not day one of the plant", now - 40 * day, updated.growStartTimestamp)
        assertEquals("the sort order inside the tent must not change", 2, updated.sortOrder)
        assertEquals("the creation instant must not change", now - 60 * day, updated.createdAt)
        assertEquals("OG Kush #2", updated.name)
    }

    @Test
    fun editingCanPromoteTheStageWithoutResettingTheGrowStart() {
        val updated = PlantEditForm.applyTo(form(stage = "cosecha"), plant())

        assertEquals("cosecha", updated.currentStage)
        assertEquals(now - 40 * day, updated.growStartTimestamp)
    }

    @Test
    fun blankOptionalFieldsAreStoredAsBlankNotAsTheCreateDefault() {
        val updated = PlantEditForm.applyTo(form(strain = "", notes = ""), plant())

        assertEquals("", updated.strain)
        assertEquals("", updated.notes)
    }

    /* ── Validation ───────────────────────────────────────────────────── */

    @Test
    fun aBlankNameIsRejectedWithAReadableSpanishMessage() {
        val problem = PlantEditForm.validate(form(name = "   "))

        assertNotNull(problem)
        assertTrue("shown to the user as-is: $problem", problem!!.isNotBlank())
    }

    @Test
    fun aValidFormHasNoProblem() {
        assertEquals(null, PlantEditForm.validate(form()))
    }

    @Test
    fun theNameIsTrimmedOnTheWayIn() {
        assertEquals("OG Kush", PlantEditForm.applyTo(form(name = "  OG Kush  "), plant()).name)
    }

    /* ── What a delete really removes ─────────────────────────────────── */

    @Test
    fun theConfirmationNamesThePlant() {
        val copy = PlantDeletionNotice.message(plant())

        assertTrue("the grower must see which row they confirmed: $copy", copy.contains("OG Kush"))
    }

    @Test
    fun theConfirmationSaysTheJournalHistoryGoes() {
        val copy = PlantDeletionNotice.message(plant())

        assertTrue("grow_events cascade, so the journal really is lost: $copy", copy.contains("bitácora"))
    }

    @Test
    fun theConfirmationAlsoSaysWhatStaysBehind() {
        val copy = PlantDeletionNotice.message(plant())

        // protocols, super_cycle_configs, stage_entries and reminders carry a
        // plantId with no foreign key, so they survive and become orphans. Saying
        // only "and all its journal history" is true but incomplete.
        listOf(
            "protocolos",
            "SuperCycle",
            "registros de etapa",
            "recordatorios",
            "quedarán guardados sin planta asociada"
        ).forEach { orphan ->
            assertTrue("the copy must mention «$orphan»: $copy", copy.contains(orphan, ignoreCase = true))
        }
    }

    @Test
    fun theConfirmationWarnsThatTheActionCannotBeUndone() {
        assertTrue(PlantDeletionNotice.message(plant()).contains("no se puede deshacer"))
    }

    @Test
    fun theConfirmationIsNotEmptyForABlankName() {
        assertFalse(PlantDeletionNotice.message(Plant(name = "", tentId = null)).isBlank())
    }
}
