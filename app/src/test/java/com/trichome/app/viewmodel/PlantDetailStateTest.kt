package com.trichome.app.viewmodel

import com.trichome.app.data.entity.Plant
import com.trichome.app.model.StageProgressEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Contract for the plant-detail load decision.
 *
 * The bug this locks down: `PlantDetailViewModel.loadPlant` had no not-found
 * branch, so an unknown id left `plant == null` forever and the screen rendered
 * `"Cargando planta…"` indefinitely. These cases pin the three guarantees that
 * replace it: a resolved plant always reaches `Success` (never `Loading`), a
 * missing or failing row reaches `Error`, and the lookup is bounded so a row
 * that never arrives cannot hang.
 *
 * Every case pins its own reference instant, and the timeout case runs on
 * `runTest`'s virtual clock, so the suite never waits on the wall clock.
 */
class PlantDetailStateTest {

    /** Fixed reference instant; nothing here reads the system clock. */
    private val now: Long = 1_800_000_000_000L

    /**
     * `createdAt` is pinned too: its entity default is `System.currentTimeMillis()`,
     * which would make two otherwise identical plants compare unequal.
     */
    private fun plant(id: Long = 7L, daysAgo: Int = 3) = Plant(
        id = id,
        name = "OG Kush",
        tentId = 1L,
        growStartTimestamp = now - daysAgo * 86_400_000L,
        currentStage = "vegetative",
        strain = "Critical",
        createdAt = now - 30 * 86_400_000L
    )

    private fun stageProgress(): StageProgressEngine.StageProgress =
        StageProgressEngine.StageProgress(
            blockIndex = 1,
            stageName = "Floracion",
            daysInCurrentStage = 4,
            daysRemainingInStage = 17,
            totalCycleDays = 63,
            overallDaysElapsed = 21,
            overallProgress = 0.33f,
            isFinished = false
        )

    private fun fail(message: String): Nothing = throw AssertionError(message)

    @Test
    fun foundPlantYieldsSuccessWithDaysDerivedFromGrowStart() = runTest {
        val state = PlantDetailLoader.load(
            lookupPlant = { plant() },
            nowMillis = now
        )

        val success = state as? PlantDetailUiState.Success
            ?: fail("a found plant must reach Success, got $state")
        assertEquals(plant(), success.plant)
        // 3 elapsed days, 1-based: entering today counts as Day 1.
        assertEquals(4, success.daysInGrow)
    }

    @Test
    fun successCarriesStageProgressWhenTheProtocolResolves() = runTest {
        val state = PlantDetailLoader.load(
            lookupPlant = { plant() },
            stageProgressFor = { stageProgress() },
            nowMillis = now
        )

        val success = state as? PlantDetailUiState.Success
            ?: fail("a found plant must reach Success, got $state")
        assertEquals(stageProgress(), success.stageProgress)
    }

    @Test
    fun absentStageProgressDoesNotFailTheLoad() = runTest {
        val state = PlantDetailLoader.load(
            lookupPlant = { plant() },
            stageProgressFor = { null },
            nowMillis = now
        )

        val success = state as? PlantDetailUiState.Success
            ?: fail("a plant without a protocol must still reach Success, got $state")
        assertNull("no protocol means no stage progress", success.stageProgress)
    }

    @Test
    fun failingStageProgressLookupDoesNotFailTheLoad() = runTest {
        val state = PlantDetailLoader.load(
            lookupPlant = { plant() },
            stageProgressFor = { throw IOException("protocol table locked") },
            nowMillis = now
        )

        val success = state as? PlantDetailUiState.Success
            ?: fail("a broken stage query must not cost the plant, got $state")
        assertEquals(plant(), success.plant)
        assertNull(success.stageProgress)
    }

    @Test
    fun unknownIdYieldsErrorAndNeverLoading() = runTest {
        val state = PlantDetailLoader.load(
            lookupPlant = { null },
            nowMillis = now
        )

        val error = state as? PlantDetailUiState.Error
            ?: fail("an unknown id must reach Error, got $state")
        assertTrue("the message must be readable, not blank", error.message.isNotBlank())
        assertTrue(state !is PlantDetailUiState.Loading)
    }

    @Test
    fun throwingLookupYieldsErrorAndNeverLoading() = runTest {
        val state = PlantDetailLoader.load(
            lookupPlant = { throw IOException("disk gone") },
            nowMillis = now
        )

        val error = state as? PlantDetailUiState.Error
            ?: fail("a failing database read must reach Error, got $state")
        assertTrue(error.message.isNotBlank())
        assertTrue(state !is PlantDetailUiState.Loading)
    }

    @Test
    fun lookupThatNeverReturnsYieldsErrorExactlyAtTheTimeout() = runTest {
        val state = PlantDetailLoader.load(
            lookupPlant = { awaitCancellation() },
            nowMillis = now
        )

        val error = state as? PlantDetailUiState.Error
            ?: fail("a lookup that never returns must reach Error, got $state")
        assertTrue(error.message.isNotBlank())
        assertEquals(
            "the loader must give up exactly at the timeout bound",
            PlantDetailLoader.LOOKUP_TIMEOUT_MS,
            testScheduler.currentTime
        )
    }

    @Test
    fun cancellationOfTheCallerPropagatesInsteadOfBecomingAnError() {
        // Structured concurrency: a cancelled scope must stay cancelled, not be
        // laundered into a "the database failed" state the UI would then render.
        assertThrows(CancellationException::class.java) {
            runBlocking {
                PlantDetailLoader.load(
                    lookupPlant = { throw CancellationException("caller went away") },
                    nowMillis = now
                )
            }
        }
    }

    @Test
    fun timeoutBoundIsShortAndFinite() {
        assertTrue(
            "the bound must be long enough for a real read",
            PlantDetailLoader.LOOKUP_TIMEOUT_MS >= 1_000L
        )
        assertTrue(
            "the bound must be short enough that no user waits on a spinner",
            PlantDetailLoader.LOOKUP_TIMEOUT_MS <= 10_000L
        )
    }
}
