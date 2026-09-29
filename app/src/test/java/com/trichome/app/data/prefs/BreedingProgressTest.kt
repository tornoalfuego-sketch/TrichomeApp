package com.trichome.app.data.prefs

import com.trichome.app.model.BreedingMedal
import com.trichome.app.model.BreedingMedalTier
import com.trichome.app.model.BreedingProgress
import com.trichome.app.model.breedingAwardFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the breeding progression stored in DataStore.
 *
 * This is deliberately **not** the terpene badge system and not the Room
 * `Gamification` table. A breeding medal is earned by answering a question in
 * the breeding tab, and reading it out of a terpene streak — or writing it into
 * a grow-event XP counter — would couple two unrelated progressions so that one
 * system's reset silently revokes the other's rewards.
 *
 * Two properties matter and both are pure: absent keys mean *nothing earned*
 * (never a crash, never a phantom medal), and awards are **additive**. A worse
 * second attempt at a chapter cannot take a medal away.
 */
class BreedingProgressTest {

    @Test
    fun anAbsentKeyMeansNothingIsEarned() {
        val progress = breedingProgressOf()

        assertEquals(emptySet<String>(), progress.completedChapters)
        assertEquals(emptySet<String>(), progress.earnedMedals)
        assertEquals(0, progress.quizAttempts)
        assertEquals(0, progress.correctAnswers)
    }

    @Test
    fun nullsAndEmptyStringsBehaveTheSameWayAsAnAbsentKey() {
        val progress = breedingProgressOf(
            completedChapters = null,
            earnedMedals = null,
            quizAttempts = null,
            correctAnswers = null
        )

        assertEquals(emptySet<String>(), progress.completedChapters)
        assertEquals(emptySet<String>(), progress.earnedMedals)
        assertEquals(0, progress.quizAttempts)
        assertEquals(0, progress.correctAnswers)
    }

    @Test
    fun storedValuesAreReadBackUntouched() {
        val medal = BreedingMedal("mendel", BreedingMedalTier.BRONZE)

        val progress = breedingProgressOf(
            completedChapters = setOf("mendel"),
            earnedMedals = setOf(medal.id),
            quizAttempts = 3,
            correctAnswers = 11
        )

        assertEquals(setOf("mendel"), progress.completedChapters)
        assertEquals(setOf("mendel:bronce"), progress.earnedMedals)
        assertEquals(3, progress.quizAttempts)
        assertEquals(11, progress.correctAnswers)
    }

    /* ── Awards are additive ──────────────────────────────────────────────── */

    @Test
    fun aFirstPassBanksEveryTierItCrossed() {
        val award = breedingAwardFor(
            earned = emptySet(),
            chapterId = "mendel",
            correct = 4,
            total = 4
        )

        assertEquals(
            setOf(
                BreedingMedal("mendel", BreedingMedalTier.BRONZE),
                BreedingMedal("mendel", BreedingMedalTier.SILVER),
                BreedingMedal("mendel", BreedingMedalTier.GOLD)
            ),
            award.newlyEarned
        )
        assertTrue("a perfect pass completes the chapter", award.chapterCompleted)
    }

    @Test
    fun aPartialPassBanksOnlyWhatItCrossed() {
        val award = breedingAwardFor(
            earned = emptySet(),
            chapterId = "mendel",
            correct = 2,
            total = 4
        )

        assertEquals(setOf(BreedingMedal("mendel", BreedingMedalTier.BRONZE)), award.newlyEarned)
        assertFalse("bronze is not a chapter completion", award.chapterCompleted)
    }

    @Test
    fun aFailedAttemptBanksNothingAndCompletesNothing() {
        val award = breedingAwardFor(
            earned = emptySet(),
            chapterId = "mendel",
            correct = 0,
            total = 4
        )

        assertTrue(award.newlyEarned.isEmpty())
        assertFalse(award.chapterCompleted)
    }

    @Test
    fun aWorseSecondAttemptCannotRevokeAMedalAlreadyBanked() {
        val gold = setOf(
            BreedingMedal("mendel", BreedingMedalTier.BRONZE).id,
            BreedingMedal("mendel", BreedingMedalTier.SILVER).id,
            BreedingMedal("mendel", BreedingMedalTier.GOLD).id
        )

        val award = breedingAwardFor(
            earned = gold,
            chapterId = "mendel",
            correct = 1,
            total = 4
        )

        assertTrue("a bad rerun must not revoke", award.newlyEarned.isEmpty())
        assertTrue("gold is kept", BreedingMedal("mendel", BreedingMedalTier.GOLD).id in gold)
        assertTrue("and gold still counts as complete", award.chapterCompleted)
    }

    @Test
    fun anImprovedSecondAttemptAddsTheNewTierWithoutRepeatingTheOldOnes() {
        val bronze = setOf(BreedingMedal("mendel", BreedingMedalTier.BRONZE).id)

        val award = breedingAwardFor(
            earned = bronze,
            chapterId = "mendel",
            correct = 3,
            total = 4
        )

        assertEquals(
            "silver is new; bronze is not re-awarded",
            setOf(BreedingMedal("mendel", BreedingMedalTier.SILVER)),
            award.newlyEarned
        )
    }

    @Test
    fun repeatingTheSamePerfectRunAwardsNothingNew() {
        // The union write is idempotent, so a double tap on "Registrar" cannot
        // inflate the counter or re-fire the medal animation.
        val first = breedingAwardFor(emptySet(), "mendel", 4, 4)
        val second = breedingAwardFor(
            earned = first.newlyEarned.map { it.id }.toSet(),
            chapterId = "mendel",
            correct = 4,
            total = 4
        )

        assertTrue(second.newlyEarned.isEmpty())
        assertTrue(second.chapterCompleted)
    }

    @Test
    fun medalsFromAnotherChapterAreUntouchedByThisAward() {
        val others = setOf(BreedingMedal("fenohunting", BreedingMedalTier.GOLD).id)

        val award = breedingAwardFor(others, "mendel", 4, 4)

        assertTrue(
            "a mendel award must not claim a fenohunting medal",
            award.newlyEarned.none { it.chapterId == "fenohunting" }
        )
    }

    /* ── Degenerate input ─────────────────────────────────────────────────── */

    @Test
    fun aZeroQuestionQuizAwardsNothingAndIsNotAnError() {
        val award = breedingAwardFor(emptySet(), "mendel", correct = 0, total = 0)

        assertTrue(award.newlyEarned.isEmpty())
        assertFalse(award.chapterCompleted)
    }

    @Test
    fun anUnreadableMedalIdInStorageIsKeptButNotCounted() {
        // Forward compatibility: a key written by a future build must not make the
        // current one throw. It is kept verbatim in the stored set — discarding it
        // would mean rewriting a preference this build does not understand — and
        // ignored when counting.
        val stored = setOf("mendel:platinum", "mendel:oro", "not-a-medal")
        val progress = BreedingProgress(
            earnedMedals = stored,
            completedChapters = setOf("mendel"),
            quizAttempts = 1,
            correctAnswers = 1
        )

        assertEquals("an unknown key must survive a read untouched", stored, progress.earnedMedals)
        assertEquals(1, progress.medalsEarnedIn("mendel").size)
        assertEquals(BreedingMedalTier.GOLD, progress.medalsEarnedIn("mendel").first().tier)
        assertEquals(1, progress.chapterMedals.size)
        assertEquals("only the readable medal counts", 1, progress.totalMedals)
    }

    @Test
    fun anUnknownChapterIdReportsNoMedalsRatherThanFailing() {
        val progress = BreedingProgress(
            earnedMedals = setOf(BreedingMedal("mendel", BreedingMedalTier.BRONZE).id)
        )

        assertTrue(progress.medalsEarnedIn("no-such-chapter").isEmpty())
        assertEquals(1, progress.totalMedals)
    }

    @Test
    fun theBestTierIsTheHighestOneActuallyBanked() {
        val bronze = BreedingProgress(
            earnedMedals = setOf(BreedingMedal("mendel", BreedingMedalTier.BRONZE).id)
        )
        val silver = BreedingProgress(
            earnedMedals = setOf(
                BreedingMedal("mendel", BreedingMedalTier.BRONZE).id,
                BreedingMedal("mendel", BreedingMedalTier.SILVER).id
            )
        )
        val none = BreedingProgress()

        assertEquals(BreedingMedalTier.BRONZE, bronze.bestTierIn("mendel"))
        assertEquals(BreedingMedalTier.SILVER, silver.bestTierIn("mendel"))
        assertEquals(null, none.bestTierIn("mendel"))
    }

    @Test
    fun aNegativeStoredCounterReadsAsZeroRatherThanANegativeScore() {
        val progress = breedingProgressOf(quizAttempts = -4, correctAnswers = -9)

        assertEquals(0, progress.quizAttempts)
        assertEquals(0, progress.correctAnswers)
    }
}
