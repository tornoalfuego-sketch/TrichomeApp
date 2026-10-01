package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a plant keeps when it changes tent.
 *
 * The numbers come from the install that motivated the v2 -> v3 migration: plant
 * 11 was running the tent's 13h/13h while its own protocol said 12h/12h, and the
 * grower decided the tent wins. That decision is what these tests pin, together
 * with the case the old dialog got wrong — an "unchecked" default with no defined
 * outcome, which is how a photoperiod silently changed on a move.
 *
 * Nothing here touches Room. The whole point of [PlantMigrationPlanner] is that the
 * dialog's outcome is decided, and testable, before a single row is written.
 */
class PlantMigrationPlanTest {

    private val now = 1_787_000_000_000L
    private val historyAnchor = 1_700_000_000_000L

    /** Plant 11 on the install: 12/12 on its own, headed for a tent running 13/13. */
    private val plant = PlantCycleState(
        plantId = 11L,
        plantName = "Blue Dream",
        photoperiod = PhotoperiodConfig(12, 12),
        cycleStartAt = historyAnchor
    )

    private val tentWithConfig = TentCycleState(
        tentId = 4L,
        tentName = "Carpa 4",
        photoperiod = PhotoperiodConfig(13, 13)
    )

    /** A tent nobody has configured yet: `getConfigByTent` returns null for it. */
    private val tentWithoutConfig = TentCycleState(
        tentId = 7L,
        tentName = "Carpa 7",
        photoperiod = null
    )

    private fun plan(
        current: PlantCycleState = plant,
        destination: TentCycleState = tentWithConfig,
        choices: PlantMigrationChoices = PlantMigrationChoices.DEFAULT,
        nowMillis: Long = now
    ) = PlantMigrationPlanner.plan(current, destination, choices, nowMillis)

    /* ── The invariant: the destination tent wins ───────────────────────── */

    @Test
    fun anUntouchedDialogGivesThePlanToTheDestinationTent() {
        val result = plan()

        assertEquals(MigrationResolution.APPLIED, result.resolution)
        assertTrue(result.isApplied)
        assertEquals(
            "the grower's decision was that the tent's supercycle wins",
            PhotoperiodSource.DESTINATION_TENT,
            result.source
        )
        assertEquals(PhotoperiodConfig(13, 13), result.photoperiod)
        assertEquals(13, result.lightHours)
        assertEquals(13, result.darkHours)
        assertFalse(
            "the tent already has a config row, so nothing has to be written",
            result.requiresDestinationConfigWrite
        )
        assertTrue(
            "12/12 became 13/13, so this move does change the plant's numbers",
            result.changesPhotoperiod
        )
        assertEquals(
            "and the history it is compared against is the plant's, not the tent's",
            PhotoperiodConfig(12, 12),
            result.previousPhotoperiod
        )
    }

    @Test
    fun thePlanSaysWhichSideWonAndWhyInSpanish() {
        val result = plan()

        assertTrue(
            "the dialog has to name the tent it obeyed",
            result.reasonEs.contains("Carpa 4")
        )
        assertTrue(
            "and say the tent is the one that wins",
            result.reasonEs.contains("manda")
        )
        assertTrue(
            "and spell out the hours so the grower can object",
            result.reasonEs.contains("13h")
        )
        plan(choices = PlantMigrationChoices(photoperiodPolicy = PhotoperiodPolicy.KEEP_PREVIOUS))
            .reasonEs
            .let { exception ->
                assertTrue(
                    "the opt-in deviation has to read as an exception",
                    exception.contains("excepción")
                )
            }
    }

    @Test
    fun everyPolicyResolvesToExactlyOneNamedSource() {
        PhotoperiodPolicy.entries.forEach { policy ->
            val result = plan(choices = PlantMigrationChoices(photoperiodPolicy = policy))
            assertNotNull(
                "$policy must produce a photoperiod",
                result.photoperiod
            )
            assertTrue(
                "$policy produced the source $policy itself, which is not a source",
                result.source in PhotoperiodSource.entries
            )
            assertTrue("$policy produced no explanation", result.reasonEs.isNotBlank())
        }
    }

    /* ── Keep history actually keeps ───────────────────────────────────── */

    @Test
    fun keepingThePreviousPhotoperiodIgnoresTheDestinationTent() {
        val result = plan(choices = PlantMigrationChoices(photoperiodPolicy = PhotoperiodPolicy.KEEP_PREVIOUS))

        assertEquals(PhotoperiodSource.PREVIOUS_PLANT, result.source)
        assertEquals(
            "the plant was on 12/12, so 12/12 is what it must stay on",
            PhotoperiodConfig(12, 12),
            result.photoperiod
        )
        assertFalse(
            "keeping the history means the plant runs exactly what it ran before",
            result.changesPhotoperiod
        )
        assertTrue(
            "the plant's old numbers are kept as history, never deleted",
            result.keepsPreviousPhotoperiodAsHistory
        )
    }

    @Test
    fun keepingHistoryIsTheOptInAndNotTheDefault() {
        assertEquals(
            "an untouched dialog must not be the deviation",
            PhotoperiodPolicy.ADAPT_TO_DESTINATION_TENT,
            PlantMigrationChoices().photoperiodPolicy
        )
        assertEquals(
            "the empty value and the DEFAULT companion must be the same thing",
            PlantMigrationChoices.DEFAULT,
            PlantMigrationChoices()
        )
        assertTrue(
            "keeping history must produce a different photoperiod than adapting",
            plan(choices = PlantMigrationChoices(photoperiodPolicy = PhotoperiodPolicy.KEEP_PREVIOUS))
                .photoperiod != plan().photoperiod
        )
    }

    /* ── Adapting actually applies the destination values ──────────────── */

    @Test
    fun adaptingTakesTheDestinationValuesEvenWhenTheyAreUnusual() {
        val oddTent = tentWithConfig.copy(photoperiod = PhotoperiodConfig(20, 4))

        val result = plan(destination = oddTent)

        assertEquals(PhotoperiodSource.DESTINATION_TENT, result.source)
        assertEquals(PhotoperiodConfig(20, 4), result.photoperiod)
        assertEquals(24, result.photoperiod?.totalHours)
    }

    @Test
    fun aPlantAlreadyOnTheTenPhotoperiodIsNotChanged() {
        val same = PlantCycleState(
            plantId = 11L,
            plantName = "Blue Dream",
            photoperiod = PhotoperiodConfig(13, 13),
            cycleStartAt = historyAnchor
        )

        val result = plan(current = same)

        assertEquals(PhotoperiodConfig(13, 13), result.photoperiod)
        assertFalse(
            "equal numbers mean the move changed nothing about the cycle",
            result.changesPhotoperiod
        )
    }

    /* ── The cycle anchor ──────────────────────────────────────────────── */

    @Test
    fun resettingTheAnchorRestartsTheSuperdayCountAtTheMove() {
        val result = plan(choices = PlantMigrationChoices(resetCycleStartToNow = true))
        assertEquals(now, result.cycleStartAt)
    }

    @Test
    fun notResettingKeepsThePlantsHistorySoTheCountIsContinuous() {
        val result = plan(choices = PlantMigrationChoices(resetCycleStartToNow = false))
        assertEquals(historyAnchor, result.cycleStartAt)
    }

    @Test
    fun keepingThePreviousAnchorStillAdoptsTheTenHours() {
        val result = plan(
            choices = PlantMigrationChoices(
                photoperiodPolicy = PhotoperiodPolicy.KEEP_PREVIOUS_CYCLE_ANCHOR,
                resetCycleStartToNow = false
            )
        )

        assertEquals(
            "the anchor choice must not change where the hours come from",
            PhotoperiodSource.DESTINATION_TENT,
            result.source
        )
        assertEquals(PhotoperiodConfig(13, 13), result.photoperiod)
        assertEquals(historyAnchor, result.cycleStartAt)
    }

    @Test
    fun aPlantWithNoAnchorFallsBackToTheMoveInstant() {
        val fresh = plant.copy(cycleStartAt = null)

        assertEquals(
            now,
            plan(current = fresh, choices = PlantMigrationChoices(resetCycleStartToNow = false))
                .cycleStartAt
        )
        assertEquals(
            "a zero anchor is not a real one either",
            now,
            plan(current = fresh.copy(cycleStartAt = 0L))
                .cycleStartAt
        )
    }

    @Test
    fun everyPolicyAndAnchorCombinationResolves() {
        PhotoperiodPolicy.entries.forEach { policy ->
            listOf(true, false).forEach { reset ->
                val result = plan(
                    choices = PlantMigrationChoices(
                        photoperiodPolicy = policy,
                        resetCycleStartToNow = reset
                    )
                )
                assertTrue("$policy / reset=$reset was not applied", result.isApplied)
                assertTrue(
                    "$policy / reset=$reset produced no usable cycle",
                    result.photoperiod?.isValid == true
                )
                assertEquals(
                    "$policy / reset=$reset moved the anchor inconsistently",
                    if (reset) now else historyAnchor,
                    result.cycleStartAt
                )
            }
        }
    }

    /* ── A destination tent with no supercycle config ──────────────────── */

    @Test
    fun aTentWithNoConfigNeverYieldsAnInvalidPhotoperiod() {
        PhotoperiodPolicy.entries.forEach { policy ->
            val result = plan(
                destination = tentWithoutConfig,
                choices = PlantMigrationChoices(photoperiodPolicy = policy)
            )

            assertTrue("$policy was not applied", result.isApplied)
            val photoperiod = result.photoperiod
            assertNotNull("$policy produced no photoperiod at all", photoperiod)
            assertTrue(
                "$policy produced ${photoperiod!!.lightHours}/${photoperiod.darkHours}, " +
                    "which is not a cycle",
                photoperiod.isValid
            )
            assertTrue(
                "$policy produced a total of ${photoperiod.totalHours} hours",
                photoperiod.totalHours > 0
            )
            assertTrue(
                "$policy must flag that the tent needs its first config row",
                result.requiresDestinationConfigWrite
            )
        }
    }

    @Test
    fun aTentWithNoConfigLeavesThePlantOnItsOwnPhotoperiod() {
        val result = plan(destination = tentWithoutConfig)

        assertEquals(
            "the tent cannot win by default when it has nothing to say",
            PhotoperiodSource.PREVIOUS_PLANT,
            result.source
        )
        assertEquals(PhotoperiodConfig(12, 12), result.photoperiod)
        assertFalse(
            "the numbers are unchanged",
            result.changesPhotoperiod
        )
        assertTrue(
            "and the dialog has to say why",
            result.reasonEs.contains("no tiene superciclo configurado")
        )
    }

    @Test
    fun neitherSideConfiguredFallsBackToTheAppsOwnPreset() {
        val bare = plant.copy(photoperiod = null)

        val result = plan(current = bare, destination = tentWithoutConfig)

        assertEquals(PhotoperiodSource.SAFE_DEFAULT, result.source)
        assertEquals(
            "18/6 is the app's documented preset, so it is the safe answer",
            PhotoperiodConfig(18, 6),
            result.photoperiod
        )
        assertEquals(24, result.photoperiod?.totalHours)
        assertTrue(
            "and it must be labelled as a substitute, not as a real config",
            result.reasonEs.contains("18/6")
        )
    }

    @Test
    fun garbageOnEitherSideStillProducesAUsableCycle() {
        val brokenPlants = listOf(
            null,
            PhotoperiodConfig(0, 0),
            PhotoperiodConfig(-6, -6),
            PhotoperiodConfig(99, 99),
            PhotoperiodConfig(24, 0)
        )
        brokenPlants.forEach { candidate ->
            val brokenTent = listOf(null, PhotoperiodConfig(0, 0), PhotoperiodConfig(-1, 30))
                .forEach { tent ->
                    val result = plan(
                        current = plant.copy(photoperiod = candidate),
                        destination = tentWithConfig.copy(photoperiod = tent)
                    )
                    assertTrue(
                        "plant=$candidate tent=$tent produced $result",
                        result.isApplied
                    )
                    assertTrue(
                        "plant=$candidate tent=$tent produced an invalid cycle",
                        result.photoperiod?.isValid == true && result.photoperiod.totalHours > 0
                    )
                }
        }
    }

    @Test
    fun aBrokenPlantDoesNotBlockTheTenConfig() {
        val result = plan(current = plant.copy(photoperiod = PhotoperiodConfig(0, 0)))

        assertEquals(PhotoperiodSource.DESTINATION_TENT, result.source)
        assertEquals(PhotoperiodConfig(13, 13), result.photoperiod)
    }

    /* ── The journal is not part of this move ───────────────────────────── */

    @Test
    fun theJournalIsAlwaysKeptBecauseItIsKeyedByPlantAndNotByTent() {
        val combinations = buildList {
            PhotoperiodPolicy.entries.forEach { policy ->
                listOf(true, false).forEach { reset ->
                    listOf(tentWithConfig, tentWithoutConfig).forEach { tent ->
                        add(
                            plan(
                                destination = tent,
                                choices = PlantMigrationChoices(
                                    photoperiodPolicy = policy,
                                    resetCycleStartToNow = reset
                                )
                            )
                        )
                    }
                }
            }
        }

        assertEquals(12, combinations.size)
        combinations.forEach { result ->
            assertEquals(
                "a tent move writes plants.tentId; it cannot touch grow_events",
                JournalAction.KEEP_ALWAYS,
                result.journalAction
            )
        }
    }

    @Test
    fun askingToDropTheJournalIsRefusedRatherThanHonoured() {
        val result = plan(
            choices = PlantMigrationChoices(
                photoperiodPolicy = PhotoperiodPolicy.ADAPT_TO_DESTINATION_TENT,
                keepJournalRecords = false
            )
        )

        assertEquals(MigrationResolution.REJECTED, result.resolution)
        assertFalse(result.isApplied)
        assertNull(
            "a refused plan writes nothing at all, including no photoperiod",
            result.photoperiod
        )
        assertTrue(
            "and it has to explain itself",
            result.reasonEs.contains("grow_events")
        )
        assertEquals(JournalAction.KEEP_ALWAYS, result.journalAction)
    }

    /* ── The plan is a pure function of its inputs ──────────────────────── */

    @Test
    fun theSameInputsAlwaysProduceTheSamePlan() {
        val first = plan()
        val second = plan()

        assertEquals(first, second)
        assertEquals(
            "the planner must not read the clock",
            first.cycleStartAt,
            second.cycleStartAt
        )
        assertEquals(
            "and a different move instant only moves the anchor",
            plan(nowMillis = now + 86_400_000L).photoperiod,
            first.photoperiod
        )
    }

    @Test
    fun thePlanIsSerialisableAsPlainData() {
        val result = plan()
        assertEquals(result.photoperiod, result.copy().photoperiod)
        assertEquals(result, result.copy(reasonEs = result.reasonEs))
    }
}