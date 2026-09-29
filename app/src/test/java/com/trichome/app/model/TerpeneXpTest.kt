package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The XP ledger, the family-completion rule and badge accumulation.
 *
 * These are the rules that were living inline in `TerpenesViewModel` (or not
 * living anywhere at all — `XP_PER_FAMILY` was declared and never awarded).
 * They are here so the ledger has exactly one definition and a new XP event is a
 * new field, not a new expression pasted into a composable-scoped ViewModel.
 */
class TerpeneXpTest {

    // --- the XP ledger -----------------------------------------------------

    @Test
    fun theTotalIsTheSumOfEveryAwardedEvent() {
        val sources = TerpeneXpSources(
            discovered = 3,
            familiesCompleted = 2,
            quizCorrect = 4,
            quizzesCompleted = 5
        )

        assertEquals(
            3 * TerpeneProgression.XP_PER_DISCOVERY +
                2 * TerpeneProgression.XP_PER_FAMILY +
                4 * TerpeneProgression.XP_PER_QUIZ_CORRECT +
                5 * TerpeneProgression.XP_PER_QUIZ_COMPLETED,
            TerpeneProgression.totalXp(sources)
        )
    }

    @Test
    fun noProgressMeansNoExperience() {
        assertEquals(0, TerpeneProgression.totalXp(TerpeneXpSources()))
    }

    @Test
    fun completingAFamilyIsActuallyWorthSomething() {
        val withoutFamily = TerpeneProgression.totalXp(TerpeneXpSources(discovered = 10))
        val withFamily = TerpeneProgression.totalXp(TerpeneXpSources(discovered = 10, familiesCompleted = 1))

        assertEquals(
            "XP_PER_FAMILY was dead code; it has to reach the total",
            TerpeneProgression.XP_PER_FAMILY,
            withFamily - withoutFamily
        )
        assertTrue("a family must be worth a visible amount", TerpeneProgression.XP_PER_FAMILY > 0)
    }

    @Test
    fun completingATriviaIsWorthSomethingEvenWithNoCorrectAnswers() {
        val blank = TerpeneProgression.totalXp(TerpeneXpSources(quizCorrect = 0))
        val played = TerpeneProgression.totalXp(TerpeneXpSources(quizCorrect = 0, quizzesCompleted = 1))

        assertEquals(
            "finishing a run must contribute even at zero correct answers",
            TerpeneProgression.XP_PER_QUIZ_COMPLETED,
            played - blank
        )
    }

    @Test
    fun everyXpEventMovesTheLevelInTheSameDirection() {
        val base = TerpeneProgression.totalXp(TerpeneXpSources())
        val events = listOf(
            TerpeneXpSources(discovered = 1),
            TerpeneXpSources(familiesCompleted = 1),
            TerpeneXpSources(quizCorrect = 1),
            TerpeneXpSources(quizzesCompleted = 1)
        )

        events.forEach { event ->
            assertTrue(
                "an XP event must not reduce the total: $event",
                TerpeneProgression.totalXp(event) > base
            )
        }
    }

    @Test
    fun aCorruptCounterCannotDrainExperience() {
        val negative = TerpeneProgression.totalXp(
            TerpeneXpSources(discovered = -5, familiesCompleted = -1, quizCorrect = -3, quizzesCompleted = -2)
        )

        assertEquals("a negative counter must not subtract experience", 0, negative)
    }

    @Test
    fun theLedgerDrivesTheLevelAndTheProgressBar() {
        val xp = TerpeneProgression.totalXp(TerpeneXpSources(discovered = 12, quizCorrect = 3))

        assertTrue("XP must feed the level curve", TerpeneProgression.levelFor(xp) >= 1)
        assertTrue("XP must feed the progress bar", TerpeneProgression.levelProgress(xp) > 0f)
    }

    // --- the family rule ---------------------------------------------------

    private val catalog = listOf(
        TerpeneFamilyMember("a", "Monoterpeno"),
        TerpeneFamilyMember("b", "Monoterpeno"),
        TerpeneFamilyMember("c", "Sesquiterpeno"),
        TerpeneFamilyMember("d", "Sesquiterpeno"),
        TerpeneFamilyMember("e", "Diterpeno")
    )

    @Test
    fun aFamilyIsCompleteOnlyWhenEveryMemberIsDiscovered() {
        assertEquals("nothing discovered", 0, TerpeneProgression.completedFamilies(catalog, emptySet()))
        assertEquals("one of two monoterpenes", 0, TerpeneProgression.completedFamilies(catalog, setOf("a")))
        assertEquals("both monoterpenes", 1, TerpeneProgression.completedFamilies(catalog, setOf("a", "b")))
        assertEquals("a complete family plus a partial one", 1, TerpeneProgression.completedFamilies(catalog, setOf("a", "b", "c")))
        assertEquals(
            "monoterpenes done, sesquiterpenes still missing d",
            1,
            TerpeneProgression.completedFamilies(catalog, setOf("a", "b", "c", "d") - "d")
        )
        assertEquals(
            "monoterpenes and sesquiterpenes done, diterpene pending",
            2,
            TerpeneProgression.completedFamilies(catalog, setOf("a", "b", "c", "d"))
        )
        assertEquals("all three families done", 3, TerpeneProgression.completedFamilies(catalog, setOf("a", "b", "c", "d", "e")))
    }

    @Test
    fun discoveryOfUnrelatedTerpenesDoesNotCompleteAFamily() {
        val partial = TerpeneProgression.completedFamilies(catalog, setOf("x", "y", "z"))

        assertEquals("ids that are not in the catalog complete nothing", 0, partial)
    }

    @Test
    fun terpenesWithNoFamilyAreNeverCountedAsAFamily() {
        val mixed = catalog + TerpeneFamilyMember("f", "")

        assertEquals(
            "a blank family is not a family to complete: discovering it alone must complete nothing",
            0,
            TerpeneProgression.completedFamilies(mixed, setOf("f"))
        )
        assertEquals(
            "adding a blank family must not change the count for the real ones",
            1,
            TerpeneProgression.completedFamilies(mixed, setOf("a", "b", "f"))
        )
    }

    @Test
    fun anEmptyCatalogCompletesNothing() {
        assertEquals(0, TerpeneProgression.completedFamilies(emptyList(), setOf("a")))
    }

    // --- badges accumulate in the profile ----------------------------------

    private val counters = BadgeCounters(
        discoveredCount = 150,
        familiesCompleted = 1,
        favorites = 6,
        bestStreak = 9,
        quizzesCorrect = 25
    )

    @Test
    fun everyBadgeUnlocksWhenItsRequirementIsMet() {
        val unlocked = TerpeneProgression.badges(counters)

        assertEquals("this progress should unlock every shipped badge", unlocked.size, unlocked.count { it.unlocked })
    }

    @Test
    fun aBadgeEarnedFromTheBestStreakSurvivesTheStreakResetting() {
        // Earned during a nine-day run, then the live streak lapsed to zero.
        val earned = TerpeneProgression.unlockedBadgeIds(counters)
        assertTrue(
            "the streak medal must have been earned in the first place",
            TerpeneBadge.STREAK.id in earned
        )

        val afterLapse = TerpeneProgression.badges(counters.copy(bestStreak = 0), earned)

        assertTrue(
            "a lapsed streak must not revoke a medal already in the profile",
            afterLapse.single { it.id == TerpeneBadge.STREAK.id }.unlocked
        )
    }

    @Test
    fun onceEarnedABadgeIsNeverTakenAway() {
        val earned = TerpeneProgression.unlockedBadgeIds(counters)

        val afterUnstarring = TerpeneProgression.badges(
            BadgeCounters(),
            earned = earned
        )

        val stillEarned = afterUnstarring.filter { it.unlocked }
        assertEquals("a badge in the profile must stay unlocked", earned.size, stillEarned.size)
        assertTrue(
            "un-starring must not un-earn the collector badge",
            stillEarned.any { it.id == TerpeneBadge.COLLECTOR.id }
        )
    }

    @Test
    fun badgeIdsAreStableAndUnique() {
        val ids = TerpeneProgression.badges(BadgeCounters()).map { it.id }

        assertEquals("badge ids key the persisted set, so they must be unique", ids.size, ids.toSet().size)
        assertTrue("badge ids must not be blank", ids.none { it.isBlank() })
    }

    @Test
    fun noProgressUnlocksNothingBeyondTheStarterBadge() {
        val fresh = TerpeneProgression.badges(BadgeCounters())

        assertFalse("the first-ever badge must be locked too", fresh.any { it.unlocked })
    }
}
