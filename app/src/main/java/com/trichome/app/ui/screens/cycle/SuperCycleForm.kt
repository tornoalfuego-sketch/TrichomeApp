package com.trichome.app.ui.screens.cycle

import com.trichome.app.data.entity.SuperCycleConfig

/**
 * The photoperiod the screen is editing, and the rule that keeps the sliders and
 * the saved row from diverging.
 *
 * The bug this replaces was real user data loss: the screen called a suspending
 * `vm.load(plantId)` and then read `vm.config` on the next line, which happened
 * before the load had resolved, so the sliders kept their `18/6` defaults while
 * the result card showed the saved photoperiod. Pressing Save without moving a
 * slider wrote `18/6` over the tent's saved cycle. Since the v3 migration the
 * supercycle is inherited by every plant in the tent, so that one bad save hit
 * four rows instead of one.
 *
 * Two invariants make it unrepeatable:
 *
 *  1. **The sliders always show the saved values once the load has resolved.**
 *     [onLoaded] is the only thing that writes the hours, and the screen calls
 *     it with the value `vm.load` returned, so there is no window in which the
 *     defaults are live.
 *  2. **Save cannot write a value the user never saw.** [saveRequest] returns
 *     `null` until [loaded] is true, and the Save button is disabled until then,
 *     so an unresolved load cannot overwrite anything.
 *
 * Deliberately a plain immutable state object with no Compose or coroutine types,
 * so the decision is testable on the JVM.
 */
data class SuperCycleForm(
    val lightHours: Int = DEFAULT_LIGHT_HOURS,
    val darkHours: Int = DEFAULT_DARK_HOURS,
    val presetType: String = DEFAULT_PRESET,
    /** True once a load resolved, whether or not it found a saved row. */
    val loaded: Boolean = false,
    /** True once the user moved a slider or picked a preset. */
    val dirty: Boolean = false,
    /** `cycleStartAt` of the saved row, kept so an edit does not reset the clock. */
    val savedCycleStartAt: Long? = null,
    /**
     * The instant the grower chose in the anchor picker, or null while they have not.
     *
     * Deliberately a **separate** field from [savedCycleStartAt] rather than an
     * overwrite of it. Two reasons, and both are the same reason:
     *
     *  1. **Cancelling has to be a no-op.** The picker writes here on confirm and
     *     nothing else, so a cancel, a dismiss-by-tap and a rotation all leave
     *     [savedCycleStartAt] exactly as the load found it. A picker that wrote the
     *     field directly would need a second copy to restore, and the restore is the
     *     part that gets forgotten.
     *  2. **A late load must not clobber the choice.** `onLoaded` already has a branch
     *     for an edit that arrived before the load resolved, and it deliberately adopts
     *     the saved anchor. Doing that when the anchor is what was edited would silently
     *     undo the grower's decision on the frame after they made it — the same defect
     *     class as the slider race this file exists to prevent, wearing a different hat.
     */
    val chosenCycleStartAt: Long? = null
) {
    /** Save is only reachable with the loaded values already on screen. */
    val canSave: Boolean get() = loaded

    /**
     * The anchor the screen is currently showing: the grower's choice when there is one,
     * otherwise the saved row's, otherwise null before the load resolves.
     *
     * The heatmap and the consequence preview both read this, so both are drawn against
     * the same instant Save would write. A preview computed from the saved value while
     * Save writes the chosen one is a preview of nothing.
     */
    val effectiveCycleStartAt: Long? get() = chosenCycleStartAt ?: savedCycleStartAt

    /** True once the anchor picker has confirmed an instant. */
    val anchorEdited: Boolean get() = chosenCycleStartAt != null

    /**
     * Folds a finished load into the form.
     *
     * A config that arrives after the user has already moved a slider does not
     * yank their edit back: the saved start timestamp is still adopted, because
     * the preview and the save both have to run against the real cycle start.
     * The pre-migration screen lost the edit silently in this same situation,
     * which is the same defect class as the race.
     */
    fun onLoaded(config: SuperCycleConfig?): SuperCycleForm =
        if (dirty) {
            copy(
                loaded = true,
                savedCycleStartAt = config?.cycleStartAt ?: savedCycleStartAt,
                // The grower's anchor survives a late load. See
                // [chosenCycleStartAt]: adopting the stored instant here would undo the
                // choice on the frame after it was made.
                chosenCycleStartAt = chosenCycleStartAt
            )
        } else {
            SuperCycleForm(
                lightHours = config?.lightHours ?: DEFAULT_LIGHT_HOURS,
                darkHours = config?.darkHours ?: DEFAULT_DARK_HOURS,
                presetType = config?.presetType ?: DEFAULT_PRESET,
                loaded = true,
                dirty = false,
                savedCycleStartAt = config?.cycleStartAt
            )
        }

    /**
     * The anchor picker confirmed [millis].
     *
     * Marks the form dirty for the same reason moving a slider does: Save has to be the
     * only writer, and it decides what it writes from this state. The stored value is not
     * touched here, so cancelling in the dialog and reopening the screen shows the
     * anchor that is actually saved.
     */
    fun withCycleStartAt(millis: Long): SuperCycleForm =
        copy(chosenCycleStartAt = millis, dirty = true)

    fun withLightHours(hours: Int): SuperCycleForm =
        copy(
            lightHours = hours.coerceIn(0, 24),
            presetType = PRESET_CUSTOM,
            dirty = true
        )

    fun withDarkHours(hours: Int): SuperCycleForm =
        copy(
            darkHours = hours.coerceIn(0, 24),
            presetType = PRESET_CUSTOM,
            dirty = true
        )

    /**
     * Picks a preset chip. `custom` keeps the current hours, which is what the
     * chip has always meant: "these hours are mine".
     */
    fun withPreset(preset: String): SuperCycleForm = when (preset) {
        "18/6" -> copy(lightHours = 18, darkHours = 6, presetType = preset, dirty = true)
        "12/12" -> copy(lightHours = 12, darkHours = 12, presetType = preset, dirty = true)
        "24/0" -> copy(lightHours = 24, darkHours = 0, presetType = preset, dirty = true)
        else -> copy(presetType = preset, dirty = true)
    }

    /**
     * The values Save is allowed to write, or `null` while the load is still in
     * flight. `null` is the whole point: an unresolved load has nothing the user
     * has seen, so it has nothing to write.
     */
    fun saveRequest(nowMillis: Long): SuperCycleSaveRequest? =
        if (!loaded) null
        else SuperCycleSaveRequest(
            lightHours = lightHours,
            darkHours = darkHours,
            // The chosen anchor first, then the saved one, then the instant of the
            // request. The order is the whole point of [chosenCycleStartAt] being a
            // separate field: this is the single place the anchor is decided.
            cycleStartAt = effectiveCycleStartAt ?: nowMillis,
            presetType = presetType
        )

    companion object {
        const val DEFAULT_LIGHT_HOURS = 18
        const val DEFAULT_DARK_HOURS = 6
        const val DEFAULT_PRESET = "18/6"
        const val PRESET_CUSTOM = "custom"
    }
}

/** Exactly what the Save button is allowed to persist. */
data class SuperCycleSaveRequest(
    val lightHours: Int,
    val darkHours: Int,
    val cycleStartAt: Long,
    val presetType: String
)