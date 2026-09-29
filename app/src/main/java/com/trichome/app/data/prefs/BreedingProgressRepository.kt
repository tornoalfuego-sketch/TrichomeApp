package com.trichome.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trichome.app.model.BreedingAward
import com.trichome.app.model.BreedingProgress
import com.trichome.app.model.breedingAwardFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.breedingProgressDataStore by preferencesDataStore(name = "breeding_progress")

/**
 * Pure projection from the raw stored values onto [BreedingProgress].
 *
 * Split out from the repository for the same reason as [appearanceSettingsOf]: a
 * `null` means "the key was never written", which is what DataStore hands back on
 * a fresh install, and pulling the defaults out here is what makes "absent key
 * means nothing earned" something a JVM test can assert instead of a behaviour
 * buried in a Flow.
 *
 * Negative counters are clamped to zero. A corrupted count should read as "no
 * attempts recorded", not as a negative score that has to be defended against
 * every time it is displayed.
 */
fun breedingProgressOf(
    completedChapters: Set<String>? = null,
    earnedMedals: Set<String>? = null,
    quizAttempts: Int? = null,
    correctAnswers: Int? = null
): BreedingProgress = BreedingProgress(
    completedChapters = completedChapters ?: emptySet(),
    earnedMedals = earnedMedals ?: emptySet(),
    quizAttempts = (quizAttempts ?: 0).coerceAtLeast(0),
    correctAnswers = (correctAnswers ?: 0).coerceAtLeast(0)
)

/**
 * Persists the breeding theory progression.
 *
 * Its own DataStore file, its own keys, and no reads from
 * [TerpeneProgressRepository] or from Room. The app has two other progression
 * systems; this one is about answering questions in the breeding tab and shares
 * no state with either of them.
 */
class BreedingProgressRepository(private val context: Context) {

    private object Keys {
        val COMPLETED = stringSetPreferencesKey("completed_chapters")
        val MEDALS = stringSetPreferencesKey("earned_medals")
        val ATTEMPTS = intPreferencesKey("quiz_attempts")
        val CORRECT = intPreferencesKey("correct_answers")
    }

    val progress: Flow<BreedingProgress> = context.breedingProgressDataStore.data.map { prefs ->
        breedingProgressOf(
            completedChapters = prefs[Keys.COMPLETED],
            earnedMedals = prefs[Keys.MEDALS],
            quizAttempts = prefs[Keys.ATTEMPTS],
            correctAnswers = prefs[Keys.CORRECT]
        )
    }

    /**
     * Records one quiz run and banks whatever it earned.
     *
     * Medals are **unioned**, never replaced: the score decides what to add and
     * never what to take away, so a worse rerun cannot revoke a medal and a
     * write that arrives out of order cannot drop one. Idempotent too — running
     * the same perfect attempt twice adds nothing the second time.
     *
     * @return the medals that were new, and whether the chapter is now complete.
     */
    suspend fun recordQuiz(chapterId: String, correct: Int, total: Int): BreedingAward {
        var award = BreedingAward(newlyEarned = emptySet(), chapterCompleted = false)
        context.breedingProgressDataStore.edit { prefs ->
            val earned = prefs[Keys.MEDALS] ?: emptySet()
            award = breedingAwardFor(earned, chapterId, correct, total)

            prefs[Keys.ATTEMPTS] = (prefs[Keys.ATTEMPTS] ?: 0) + 1
            prefs[Keys.CORRECT] = (prefs[Keys.CORRECT] ?: 0) + correct.coerceAtLeast(0)

            if (award.newlyEarned.isNotEmpty()) {
                prefs[Keys.MEDALS] = earned + award.newlyEarned.map { it.id }
            }
            if (award.chapterCompleted) {
                prefs[Keys.COMPLETED] = (prefs[Keys.COMPLETED] ?: emptySet()) + chapterId
            }
        }
        return award
    }
}
