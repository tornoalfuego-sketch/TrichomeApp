package com.trichome.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.terpeneDataStore by preferencesDataStore(name = "terpene_progress")

/**
 * Progress state for the terpenes encyclopedia.
 *
 * Kept in DataStore rather than Room on purpose: it is a flat set of ids and two
 * counters, so it needs no schema version and therefore no migration, and it
 * survives an APK upgrade untouched.
 */
data class TerpeneProgress(
    /** Ids the grower has opened at least once. */
    val discovered: Set<String> = emptySet(),
    /** Times the trivia game was played. */
    val quizzesPlayed: Int = 0,
    val quizCorrect: Int = 0,
    /** Consecutive days with at least one discovery. */
    val streak: Int = 0,
    val lastActiveEpochDay: Long = 0L
)

class TerpeneProgressRepository(private val context: Context) {

    private object Keys {
        val DISCOVERED = stringSetPreferencesKey("discovered_ids")
        val QUIZZES = intPreferencesKey("quizzes_played")
        val QUIZ_CORRECT = intPreferencesKey("quiz_correct")
        val STREAK = intPreferencesKey("streak")
        val LAST_ACTIVE = longPreferencesKey("last_active_epoch_day")
    }

    val progress: Flow<TerpeneProgress> = context.terpeneDataStore.data.map { prefs ->
        TerpeneProgress(
            discovered = prefs[Keys.DISCOVERED] ?: emptySet(),
            quizzesPlayed = prefs[Keys.QUIZZES] ?: 0,
            quizCorrect = prefs[Keys.QUIZ_CORRECT] ?: 0,
            streak = prefs[Keys.STREAK] ?: 0,
            lastActiveEpochDay = prefs[Keys.LAST_ACTIVE] ?: 0L
        )
    }

    /**
     * Registers [terpeneId] as discovered and updates the daily streak.
     *
     * @return true when this was the first time the terpene was opened, i.e.
     *   when experience was actually awarded.
     */
    suspend fun discover(terpeneId: String, todayEpochDay: Long): Boolean {
        var isNew = false
        context.terpeneDataStore.edit { prefs ->
            val seen = prefs[Keys.DISCOVERED] ?: emptySet()
            isNew = terpeneId !in seen
            if (isNew) prefs[Keys.DISCOVERED] = seen + terpeneId

            val last = prefs[Keys.LAST_ACTIVE] ?: 0L
            val streak = prefs[Keys.STREAK] ?: 0
            prefs[Keys.STREAK] = when {
                last == todayEpochDay -> streak
                last == todayEpochDay - 1L -> streak + 1
                else -> 1
            }
            prefs[Keys.LAST_ACTIVE] = todayEpochDay
        }
        return isNew
    }

    suspend fun recordQuiz(correct: Boolean) {
        context.terpeneDataStore.edit { prefs ->
            prefs[Keys.QUIZZES] = (prefs[Keys.QUIZZES] ?: 0) + 1
            if (correct) {
                prefs[Keys.QUIZ_CORRECT] = (prefs[Keys.QUIZ_CORRECT] ?: 0) + 1
            }
        }
    }
}
