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
 * Kept in DataStore rather than Room on purpose: it is a flat set of ids and a
 * few counters, so it needs no schema version and therefore no migration, and it
 * survives an APK upgrade untouched.
 *
 * This is the **terpenes** progress system. It is deliberately separate from the
 * Room-backed `Gamification`/`Achievement` tables, which track grow events: a
 * terpene streak and a journal streak are different numbers that happen to
 * share a name, and merging them would make one system's resets corrupt the
 * other's badges.
 */
data class TerpeneProgress(
    /** Ids the grower has opened at least once. */
    val discovered: Set<String> = emptySet(),
    /**
     * Questions answered across all runs.
     *
     * Named `quizzes_played` in storage since v1.1.0, where it was incremented
     * once per *answer*. Re-reading it as whole games would silently rewrite the
     * meaning of every existing save, so [quizzesCompleted] is a separate key
     * instead.
     */
    val quizzesPlayed: Int = 0,
    val quizCorrect: Int = 0,
    /** Whole runs played through to the last round. */
    val quizzesCompleted: Int = 0,
    /** Consecutive days with at least one discovery, as of today. */
    val streak: Int = 0,
    /**
     * Longest streak ever reached.
     *
     * The live [streak] resets to one the moment a day is missed, so a badge read
     * from it would be revoked for taking a day off. This is what the streak
     * badge is measured against.
     */
    val bestStreak: Int = 0,
    /** Ids of [com.trichome.app.model.TerpeneBadge] entries already banked. */
    val earnedBadges: Set<String> = emptySet(),
    val lastActiveEpochDay: Long = 0L
)

class TerpeneProgressRepository(private val context: Context) {

    private object Keys {
        val DISCOVERED = stringSetPreferencesKey("discovered_ids")
        val QUIZZES = intPreferencesKey("quizzes_played")
        val QUIZ_CORRECT = intPreferencesKey("quiz_correct")
        val STREAK = intPreferencesKey("streak")
        val LAST_ACTIVE = longPreferencesKey("last_active_epoch_day")

        // Added in v1.2.0. Preferences DataStore has no schema, so a new key
        // needs no migration: it reads as its default until something writes it.
        val QUIZZES_COMPLETED = intPreferencesKey("quizzes_completed")
        val BEST_STREAK = intPreferencesKey("best_streak")
        val EARNED_BADGES = stringSetPreferencesKey("earned_badges")
    }

    val progress: Flow<TerpeneProgress> = context.terpeneDataStore.data.map { prefs ->
        TerpeneProgress(
            discovered = prefs[Keys.DISCOVERED] ?: emptySet(),
            quizzesPlayed = prefs[Keys.QUIZZES] ?: 0,
            quizCorrect = prefs[Keys.QUIZ_CORRECT] ?: 0,
            quizzesCompleted = prefs[Keys.QUIZZES_COMPLETED] ?: 0,
            streak = prefs[Keys.STREAK] ?: 0,
            bestStreak = prefs[Keys.BEST_STREAK] ?: 0,
            earnedBadges = prefs[Keys.EARNED_BADGES] ?: emptySet(),
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
            val next = when {
                last == todayEpochDay -> streak
                last == todayEpochDay - 1L -> streak + 1
                else -> 1
            }
            prefs[Keys.STREAK] = next
            prefs[Keys.LAST_ACTIVE] = todayEpochDay
            // The peak only ever climbs, so a missed day costs the live streak
            // without costing the medal.
            if (next > (prefs[Keys.BEST_STREAK] ?: 0)) prefs[Keys.BEST_STREAK] = next
        }
        return isNew
    }

    /** Records one answered question. */
    suspend fun recordQuiz(correct: Boolean) {
        context.terpeneDataStore.edit { prefs ->
            prefs[Keys.QUIZZES] = (prefs[Keys.QUIZZES] ?: 0) + 1
            if (correct) {
                prefs[Keys.QUIZ_CORRECT] = (prefs[Keys.QUIZ_CORRECT] ?: 0) + 1
            }
        }
    }

    /** Records a whole run played through to the last round. */
    suspend fun recordQuizCompleted() {
        context.terpeneDataStore.edit { prefs ->
            prefs[Keys.QUIZZES_COMPLETED] = (prefs[Keys.QUIZZES_COMPLETED] ?: 0) + 1
        }
    }

    /**
     * Banks any newly earned badge ids.
     *
     * A union rather than a replace: badges are never revoked, so a write that
     * arrived out of order cannot drop one.
     */
    suspend fun keepBadges(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.terpeneDataStore.edit { prefs ->
            prefs[Keys.EARNED_BADGES] = (prefs[Keys.EARNED_BADGES] ?: emptySet()) + ids
        }
    }
}
