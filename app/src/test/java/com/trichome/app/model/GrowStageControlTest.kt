package com.trichome.app.model

import com.trichome.app.ui.screens.plant.PlantEditForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Crop stage control: the transition rules and the finalize archive.
 *
 * ## The assertion that carries this file
 *
 * **`closesOpenEntry` is true whenever an entry is open.** Every test below that sets up an
 * open entry checks it, and the ones that set up none check the opposite. That single field is
 * the difference between a timeline that can be read and one that cannot: a plant with two
 * open stage entries has no answer to "which stage is it in now" except a denormalised cache
 * column, and the timeline is the only record of what actually happened.
 *
 * The second field carrying weight is [StageTransitionPlan.closesAtTheSameInstant] — the close
 * and the open share one timestamp, so the timeline is contiguous: no gap, no overlap.
 *
 * ## Why finalize is not a delete, in test form
 *
 * There is no `DELETE` in the finalize path for a JVM test to inspect directly, so what is
 * asserted instead is the shape of the decision: it always reports what survives, it never
 * resolves to anything that writes a stage entry, and its own copy says the data is intact.
 * `PlantDeletionNotice` documents the cascade that makes a delete lossy.
 */
class GrowStageControlTest {

    private val now = 1_753_000_000_000L
    private val day = "15/07/2026"

    private fun context(
        currentStage: String = "vegetative",
        openStageName: String? = null,
        isActive: Boolean = true,
        protocolId: Long? = 1L
    ) = StageContext(
        plantId = 42L,
        plantName = "Planta 42",
        currentStage = currentStage,
        growStartMillis = now - 40L * 86_400_000L,
        openStageName = openStageName,
        isActive = isActive,
        protocolId = protocolId
    )

    private fun option(name: String, source: StageOptionSource = StageOptionSource.PROTOCOL) =
        StageOption(stageName = name, labelEs = name, source = source, durationDays = 35)

    /* ── The transition: closing the previous entry ────────────────────── */

    @Test
    fun aTransitionWithAnOpenEntryClosesItAtTheSameInstantItOpensTheNext() {
        val plan = GrowStagePlanner.transition(
            context = context(currentStage = "vegetative", openStageName = "Vegetativa"),
            option = option("Floración"),
            openEntryId = 7L,
            nowMillis = now,
            dayLabelEs = day
        )

        assertEquals(StageTransitionResolution.APPLIED, plan.resolution)
        assertTrue("the previous entry must be closed", plan.closesOpenEntry)
        assertEquals(7L, plan.openEntryId)
        assertEquals(now, plan.enteredAt)
        assertEquals(now, plan.exitedAt)
        assertTrue(
            "a gap or an overlap between the two entries is a corrupt timeline",
            plan.closesAtTheSameInstant
        )
        assertEquals("Vegetativa", plan.previousStageName)
        assertEquals("Floración", plan.stageName)
    }

    @Test
    fun aTransitionWithNoOpenEntryInsertsOneAndClosesNothing() {
        val plan = GrowStagePlanner.transition(
            context = context(currentStage = "vegetative"),
            option = option("Floración"),
            openEntryId = null,
            nowMillis = now,
            dayLabelEs = day
        )

        assertEquals(StageTransitionResolution.APPLIED, plan.resolution)
        assertFalse(plan.closesOpenEntry)
        assertNull(plan.openEntryId)
    }

    @Test
    fun choosingTheStageThePlantIsAlreadyInWritesNothing() {
        // One continuous stay split into two adjacent entries is noise, and a grower who taps
        // the current chip has not asked for it.
        val plan = GrowStagePlanner.transition(
            context = context(currentStage = "vegetative"),
            option = option("vegetative"),
            openEntryId = null,
            nowMillis = now,
            dayLabelEs = day
        )

        assertEquals(StageTransitionResolution.NO_CHANGE, plan.resolution)
        assertFalse(plan.isApplied)
        assertFalse(plan.closesOpenEntry)
        assertTrue(plan.reasonEs.contains("ya está en"))
    }

    @Test
    fun choosingTheSameStageWhileAnEntryIsOpenStillClosesIt() {
        // The stale-cache case, and the one `NO_CHANGE` would otherwise swallow: the cache says
        // "vegetative" but the open entry says "seedling". Writing nothing leaves two open
        // entries; writing repairs the timeline.
        val plan = GrowStagePlanner.transition(
            context = context(currentStage = "vegetative", openStageName = "seedling"),
            option = option("vegetative"),
            openEntryId = 9L,
            nowMillis = now,
            dayLabelEs = day
        )

        assertEquals(StageTransitionResolution.APPLIED, plan.resolution)
        assertTrue("the open entry must still be closed", plan.closesOpenEntry)
        assertEquals("seedling", plan.previousStageName)
    }

    @Test
    fun aBlankStageNameIsRefused() {
        listOf("", "   ", "\t").forEach { blank ->
            val plan = GrowStagePlanner.transition(
                context = context(),
                option = option(blank),
                openEntryId = 3L,
                nowMillis = now,
                dayLabelEs = day
            )

            assertEquals(StageTransitionResolution.REJECTED, plan.resolution)
            assertFalse(plan.isApplied)
            assertFalse("nothing is written, so nothing is closed", plan.closesOpenEntry)
            assertEquals(0L, plan.enteredAt)
            assertTrue(plan.reasonEs.contains("sin nombre"))
        }
    }

    @Test
    fun anArchivedPlantCannotChangeStage() {
        // Archiving is terminal in the UI, and the planner has to enforce it or the dialog
        // would offer a transition that produces a finished grow still in a stage.
        val plan = GrowStagePlanner.transition(
            context = context(isActive = false, openStageName = "Vegetativa"),
            option = option("Floración"),
            openEntryId = 4L,
            nowMillis = now,
            dayLabelEs = day
        )

        assertEquals(StageTransitionResolution.REJECTED, plan.resolution)
        assertFalse(plan.closesOpenEntry)
        assertTrue(plan.reasonEs.contains("archivada"))
    }

    @Test
    fun aPlantWithNoProtocolGetsTheSentinelRatherThanAFakeId() {
        // `stage_entries.protocolId` is NOT NULL with no foreign key, so a plant with no
        // protocol still needs a legal value.
        val plan = GrowStagePlanner.transition(
            context = context(protocolId = null),
            option = option("Floración", StageOptionSource.LIFECYCLE),
            openEntryId = null,
            nowMillis = now,
            dayLabelEs = day
        )

        assertEquals(StageTransitionPlan.NO_PROTOCOL_ID, plan.protocolId)
        assertFalse(plan.hasProtocol)
        assertTrue(plan.reasonEs.contains("no tiene protocolo"))
    }

    @Test
    fun aProtocolPlantKeepsItsProtocolId() {
        val plan = GrowStagePlanner.transition(
            context = context(protocolId = 12L),
            option = option("Floración"),
            openEntryId = null,
            nowMillis = now,
            dayLabelEs = day
        )

        assertEquals(12L, plan.protocolId)
        assertTrue(plan.hasProtocol)
    }

    @Test
    fun theStageIsTrimmedSoPaddingIsNotADifferentStage() {
        // Protocol block names come from the grower's own typing, and `" Floración "` against
        // `"Floración"` would otherwise split one stay in a stage into two.
        val plan = GrowStagePlanner.transition(
            context = context(currentStage = " Floración ", openStageName = "Floración"),
            option = option("Floración"),
            openEntryId = null,
            nowMillis = now,
            dayLabelEs = day
        )
        assertEquals("Floración", plan.stageName)
    }

    @Test
    fun theReasonNamesBothStagesWhenOneIsClosed() {
        val plan = GrowStagePlanner.transition(
            context = context(currentStage = "vegetative", openStageName = "Vegetativa"),
            option = option("Floración"),
            openEntryId = 7L,
            nowMillis = now,
            dayLabelEs = day
        )

        assertTrue(plan.reasonEs.contains("Vegetativa"))
        assertTrue(plan.reasonEs.contains("Floración"))
        assertTrue(plan.reasonEs.contains(day))
        assertTrue(
            "the reason should state the invariant, not just the action",
            plan.reasonEs.contains("una sola etapa abierta")
        )
    }

    /* ── The options ───────────────────────────────────────────────────── */

    @Test
    fun aProtocolsOwnBlocksAreOfferedWhenThereIsAProtocol() {
        val options = GrowStagePlanner.optionsFor(
            context(protocolId = 1L),
            listOf(
                ProtocolStageBlock("Floración", 56, 1),
                ProtocolStageBlock("Vegetativa", 35, 0)
            )
        )

        // Ordered by the protocol's own `sortOrder`, not alphabetically: the protocol's
        // sequence is the grow's shape and reordering it would misdescribe it.
        assertEquals(listOf("Vegetativa", "Floración"), options.map { it.stageName })
        assertTrue(options.all { it.source == StageOptionSource.PROTOCOL })
        assertEquals(listOf(35, 56), options.map { it.durationDays })
    }

    @Test
    fun theLifecycleKeysAreOfferedWhenThereIsNoProtocol() {
        val options = GrowStagePlanner.optionsFor(context(protocolId = null), emptyList())

        assertEquals(PlantEditStages.keys, options.map { it.stageName })
        assertTrue(options.all { it.source == StageOptionSource.LIFECYCLE })
        assertTrue(options.all { it.durationDays == null })
    }

    @Test
    fun theTwoVocabulariesAreNeverMixedInOneList() {
        // Offering "seedling" beside a protocol's "Vegetativa" would let a grower move a plant
        // into one vocabulary while the protocol speaks the other, and the timeline would then
        // hold both.
        val withProtocol = GrowStagePlanner.optionsFor(
            context(protocolId = 1L),
            listOf(ProtocolStageBlock("Vegetativa", 35, 0))
        )

        assertFalse("a protocol plant must not be offered a lifecycle key", withProtocol.any { it.stageName == "seedling" })
        assertEquals(
            "every option must come from the protocol's own vocabulary, never the lifecycle keys",
            listOf(StageOptionSource.PROTOCOL),
            withProtocol.map { it.source }.distinct()
        )
    }

    @Test
    fun duplicateProtocolBlocksCollapseToOneOption() {
        val options = GrowStagePlanner.optionsFor(
            context(),
            listOf(
                ProtocolStageBlock("Vegetativa", 35, 0),
                ProtocolStageBlock("Vegetativa", 20, 1)
            )
        )
        assertEquals(1, options.size)
    }

    @Test
    fun theLifecycleKeysMatchTheEditorTheGrowerAlreadyUses() {
        // `PlantEditStages` exists because `model/` cannot import from `ui/screens/plant/`.
        // A drifted second list is how a plant ends up with `currentStage = "flower"`, which
        // no screen maps to a label.
        assertEquals(
            "PlantEditStages.keys has drifted from PlantEditForm.STAGES",
            PlantEditForm.STAGES,
            PlantEditStages.keys
        )
    }

    @Test
    fun everyLifecycleKeyHasASpanishLabel() {
        PlantEditStages.keys.forEach { key ->
            val label = PlantEditStages.labelEs(key)
            assertTrue("$key has no label", label.isNotBlank())
            assertNotEquals("`$key` would render as the raw key", key, label)
        }
        // A key this build does not recognise renders as itself rather than as a blank line, so
        // a stage written by a newer build is visible instead of invisible.
        assertEquals("Cosecha", PlantEditStages.labelEs("Cosecha"))
        assertEquals("inventada", PlantEditStages.labelEs("inventada"))
    }

    /* ── Finalize ──────────────────────────────────────────────────────── */

    @Test
    fun finalizingAnActivePlantArchivesItAndReportsWhatSurvives() {
        val plan = GrowStagePlanner.finalize(
            context = context(isActive = true, openStageName = "Floración"),
            openEntryId = 11L,
            stageEntryCount = 3,
            journalRowCount = 47,
            nowMillis = now
        )

        assertEquals(PlantFinalizationOutcome.ARCHIVED, plan.outcome)
        assertTrue(plan.isArchived)
        assertTrue("the open stage entry must be closed", plan.closesOpenStageEntry)
        assertEquals(now, plan.finalizedAt)
        assertEquals(3, plan.stageEntriesKept)
        assertEquals(47, plan.journalRowsKept)
        assertTrue("the reason must name the plant", plan.reasonEs.contains("Planta 42"))
        assertTrue("and say nothing was deleted", plan.reasonEs.contains("no borra nada"))
    }

    @Test
    fun finalizingAnAlreadyArchivedPlantWritesNothing() {
        val plan = GrowStagePlanner.finalize(
            context = context(isActive = false),
            openEntryId = null,
            stageEntryCount = 3,
            journalRowCount = 47,
            nowMillis = now
        )

        assertEquals(PlantFinalizationOutcome.ALREADY_FINALIZED, plan.outcome)
        assertFalse(plan.isArchived)
        assertFalse(plan.closesOpenStageEntry)
        assertEquals(0L, plan.finalizedAt)
        assertTrue(plan.reasonEs.contains("ya estaba archivada"))
    }

    @Test
    fun theFinalizePlanNeverResolvesToBlocked() {
        // Archiving writes terminal state and deletes nothing that could be refused, so there
        // is no condition that blocks it. The value exists so a future rule has somewhere to
        // go, and this test says out loud that nothing uses it yet.
        var blocked = false
        listOf(true, false).forEach { active ->
            val outcome = GrowStagePlanner.finalize(
                context = context(isActive = active),
                openEntryId = null,
                stageEntryCount = 0,
                journalRowCount = 0,
                nowMillis = now
            ).outcome
            if (outcome == PlantFinalizationOutcome.BLOCKED) blocked = true
        }
        assertFalse("no condition blocks a finalize today", blocked)
    }

    @Test
    fun theConfirmationCountsWhatSurvivedRatherThanOnlyWhatChanged() {
        // A grower afraid of losing the season needs the counts, not just the word
        // "archivada".
        val plan = GrowStagePlanner.finalize(
            context = context(),
            openEntryId = null,
            stageEntryCount = 3,
            journalRowCount = 47,
            nowMillis = now
        )

        assertTrue(plan.confirmationEs.contains("47 eventos de bitácora"))
        assertTrue(plan.confirmationEs.contains("3 cambios de etapa"))
        assertTrue(plan.confirmationEs.contains("No se ha borrado nada"))
    }

    @Test
    fun theConfirmationPluralisesInSpanish() {
        val one = GrowStagePlanner.finalize(
            context = context(),
            openEntryId = null,
            stageEntryCount = 1,
            journalRowCount = 1,
            nowMillis = now
        )
        assertTrue(one.confirmationEs.contains("1 evento de bitácora"))
        assertTrue(one.confirmationEs.contains("1 cambio de etapa"))
        assertFalse(
            "Spanish pluralises the noun, not with (s). This is a sentence the grower reads " +
                "once and remembers, not a live count.",
            one.confirmationEs.contains("evento(s)")
        )
        assertFalse(one.confirmationEs.contains("cambio(s)"))

        // The day count uses "(s)" deliberately: it is a live figure that changes as the grow
        // runs, and the `(s)` form is the shape this codebase already uses for those.
        val many = GrowStagePlanner.finalize(
            context = context(),
            openEntryId = null,
            stageEntryCount = 7,
            journalRowCount = 3,
            nowMillis = now
        )
        assertTrue(many.confirmationEs.contains("3 eventos de bitácora"))
        assertTrue(many.confirmationEs.contains("7 cambios de etapa"))
    }

    @Test
    fun theConfirmationStatesTheDayCountFromTheGrowStart() {
        // `daysInGrow` is 1-based: a plant created today is on day one, and this codebase
        // already fixed an off-by-one here.
        val plan = GrowStagePlanner.finalize(
            context = context(),
            openEntryId = null,
            stageEntryCount = 0,
            journalRowCount = 0,
            nowMillis = now
        )
        assertEquals(41, plan.daysInGrow)
    }

    @Test
    fun theDialogBodySeparatesTheIrreversibleStateFromTheDataThatSurvives() {
        val plan = GrowStagePlanner.finalize(
            context = context(),
            openEntryId = null,
            stageEntryCount = 0,
            journalRowCount = 0,
            nowMillis = now
        )
        val body = GrowStagePlanner.finalizeDialogBodyEs(plan)

        assertTrue(body.contains(plan.reasonEs))
        assertTrue(
            "the one-way sentence is about the archived flag, not about the data",
            body.contains("no se puede deshacer")
        )
        assertTrue(
            "and the grower must be told they can still read and export the history",
            body.contains("exportarlo")
        )
    }

    @Test
    fun theArchivedBannerSaysTheHistoryIsIntact() {
        val banner = GrowStageCopy.ARCHIVED_BANNER_ES

        assertTrue("the banner must mention the history", banner.contains("historial"))
        assertTrue(
            "`conserva entero` is the claim the grower is actually afraid about",
            banner.contains("conserva entero")
        )
        assertTrue(
            "and it must say the history is still reachable, including by export",
            banner.contains("export")
        )
    }

    /* ── The cache, and the copy ───────────────────────────────────────── */

    @Test
    fun theContextReportsWhenTheCacheAndTheOpenEntryDisagree() {
        // Agreed cases.
        assertTrue(context(currentStage = "vegetative", openStageName = null).isCacheConsistent)
        assertTrue(context(currentStage = "vegetative", openStageName = "vegetative").isCacheConsistent)

        // The disagreement: the cache says vegetative, the timeline's open entry says seedling.
        // This is a real recoverable state, and it must be visible.
        assertFalse(
            "the cache and the open entry disagree",
            context(currentStage = "vegetative", openStageName = "seedling").isCacheConsistent
        )

        // Padding is not a disagreement — it is the same stage typed with a stray space, and
        // treating it as one would report a fault on every protocol block the grower wrote.
        assertTrue(
            context(currentStage = " Vegetativa ", openStageName = "Vegetativa").isCacheConsistent
        )
    }

    @Test
    fun theCacheDisagreementIsReportedAndNotUsedToRefuseATransition() {
        // Refusing would leave the grower unable to repair the row in front of them.
        val plan = GrowStagePlanner.transition(
            context = context(currentStage = "vegetative", openStageName = "seedling"),
            option = option("Floración"),
            openEntryId = 5L,
            nowMillis = now,
            dayLabelEs = day
        )
        assertEquals(StageTransitionResolution.APPLIED, plan.resolution)
    }

    @Test
    fun theTimelineHintStatesTheInvariantToTheGrower() {
        assertTrue(GrowStageCopy.TIMELINE_HINT_ES.contains("cierra"))
        assertTrue(GrowStageCopy.TIMELINE_HINT_ES.contains("dos etapas abiertas"))
    }

    @Test
    fun theAppliedTransitionSentenceNamesBothStagesAndTheDay() {
        val plan = GrowStagePlanner.transition(
            context = context(currentStage = "vegetative", openStageName = "Vegetativa"),
            option = option("Floración"),
            openEntryId = 7L,
            nowMillis = now,
            dayLabelEs = day
        )
        val message = GrowStageCopy.transitionAppliedEs(plan)

        assertTrue(message.contains("Vegetativa"))
        assertTrue(message.contains("Floración"))
        assertTrue(message.contains(day))
    }

    @Test
    fun everyOptionSourceCarriesASpanishLabel() {
        StageOptionSource.entries.forEach {
            assertTrue(it.labelEs.isNotBlank())
        }
    }
}