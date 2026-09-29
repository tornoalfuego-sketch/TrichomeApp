package com.trichome.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_preferences")

/**
 * Tracks the first-run introduction.
 *
 * The user asked for an introduction that stops appearing once the app has been
 * opened three times. The counter is therefore incremented on every cold start
 * and the flow stops reporting `shouldShow` once [LAUNCHES_BEFORE_HIDE] is
 * reached — not when the user swipes the last page, so the count tracks real
 * launches as requested.
 */
class OnboardingRepository(private val context: Context) {

    companion object {
        /** The introduction is shown for the first three launches. */
        const val LAUNCHES_BEFORE_HIDE = 3
    }

    private object Keys {
        val LAUNCH_COUNT = intPreferencesKey("launch_count")
        val COMPLETED = booleanPreferencesKey("completed")
    }

    data class State(
        val launchCount: Int,
        val shouldShow: Boolean,
        val completed: Boolean
    )

    val state: Flow<State> = context.onboardingDataStore.data.map { prefs ->
        val count = prefs[Keys.LAUNCH_COUNT] ?: 0
        val completed = prefs[Keys.COMPLETED] ?: false
        State(
            launchCount = count,
            shouldShow = !completed && count < LAUNCHES_BEFORE_HIDE,
            completed = completed
        )
    }

    /**
     * Registers a cold start.
     *
     * @return the launch number just recorded, starting at 1.
     */
    suspend fun registerLaunch(): Int {
        var current = 0
        context.onboardingDataStore.edit { prefs ->
            current = (prefs[Keys.LAUNCH_COUNT] ?: 0) + 1
            prefs[Keys.LAUNCH_COUNT] = current
        }
        return current
    }

    /** The grower finished reading the introduction. */
    suspend fun complete() {
        context.onboardingDataStore.edit { it[Keys.COMPLETED] = true }
    }

    /** Restores the introduction, for the "ver de nuevo" action in Settings. */
    suspend fun reset() {
        context.onboardingDataStore.edit {
            it[Keys.LAUNCH_COUNT] = 0
            it[Keys.COMPLETED] = false
        }
    }
}
