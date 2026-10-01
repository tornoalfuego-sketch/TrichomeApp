package com.trichome.app.model

/**
 * A photoperiod, in hours. Deliberately **not** the Room entity: this is domain
 * state, so a dialog can resolve its outcome on the JVM before anything is written.
 *
 * @param lightHours hours of light per cycle, coerced to `0..24` on read.
 * @param darkHours hours of darkness per cycle, coerced to `0..24` on read.
 */
data class PhotoperiodConfig(
    val lightHours: Int,
    val darkHours: Int
) {
    /** `lightHours + darkHours`. Always positive for anything this model returns. */
    val totalHours: Int get() = lightHours + darkHours

    /** Whether the numbers can actually drive [SuperCycleEngine]. */
    val isValid: Boolean get() = totalHours > 0 && lightHours in 0..24 && darkHours in 0..24

    /** Normalised copy: no negative hours, and a real total. Never zero-length. */
    fun sanitized(): PhotoperiodConfig {
        val light = lightHours.coerceIn(0, 24)
        val dark = darkHours.coerceIn(0, 24)
        return if (light + dark > 0) PhotoperiodConfig(light, dark)
        else SAFE_DEFAULT_PHOTOPERIOD
    }
}

/** The photoperiod a plant or tent falls back on when nothing usable is known. */
val SAFE_DEFAULT_PHOTOPERIOD: PhotoperiodConfig = PhotoperiodConfig(18, 6)

/**
 * Where the resulting photoperiod came from.
 *
 * The migration decision that produced it is not inferable from the numbers — the
 * tent's `13/13` and the plant's `13/13` are equal, and only the source says
 * whether the move confirmed the tent or was overridden. The old data-loss bug was
 * exactly an ambiguous default, so the source is carried explicitly and is always
 * one of these three.
 */
enum class PhotoperiodSource {
    /** The destination tent's supercycle, the grower's decided default. */
    DESTINATION_TENT,

    /** The plant's own photoperiod, kept because the tent has nothing better. */
    PREVIOUS_PLANT,

    /** Neither side had a usable photoperiod; the safe default was substituted. */
    SAFE_DEFAULT
}

/** What happens to the journal when the plant changes tent. */
enum class JournalAction {
    /**
     * The only value there is. Journal rows live in `grow_events`, keyed by
     * `plantId` and carrying a `CASCADE` foreign key to `plants` — the tent is not
     * in that relationship at all, so `assignPlantToTent()` (which writes only
     * `plants.tentId`) cannot drop, hide or invalidate a single row. A move is not
     * a data event for the journal.
     */
    KEEP_ALWAYS
}

/** Whether the plan describes a move that can be written, or one that must not be. */
enum class MigrationResolution {
    /** [PlantMigrationPlan.photoperiod] is set and usable. */
    APPLIED,

    /**
     * The requested choices cannot be honoured without losing user data, so nothing
     * is written and [PlantMigrationPlan.reasonEs] says why. A dialog shows the
     * reason; it does not silently do something else.
     */
    REJECTED
}

/**
 * What the plant is running right now, before the move.
 *
 * @param cycleStartAt the anchor the superday count is measured from, or null when
 *   the plant has never run a cycle.
 */
data class PlantCycleState(
    val plantId: Long,
    val plantName: String,
    val photoperiod: PhotoperiodConfig?,
    val cycleStartAt: Long?
)

/**
 * The tent the plant is moving into.
 *
 * @param photoperiod the tent's supercycle, or **null** when the tent has no
 *   config row at all. That null is a real state, not a missing argument: v3
 *   migrations leave configs with `tentId = NULL`, and a brand new tent has no row
 *   until one is written.
 */
data class TentCycleState(
    val tentId: Long,
    val tentName: String,
    val photoperiod: PhotoperiodConfig?
)

/**
 * How the resulting photoperiod was decided.
 *
 * Exhaustive `when` over three values, not two checkboxes. The bug this type
 * exists to prevent was a dialog with a "conservar el fotoperiodo anterior"
 * checkbox whose *unchecked* state had no defined outcome, so the write landed on
 * whichever branch happened to be first. Here there is no unchecked state: a
 * default [PlantMigrationChoices] resolves to [ADAPT_TO_DESTINATION_TENT].
 */
enum class PhotoperiodPolicy {
    /**
     * The default, and the grower's decision recorded in the v2 -> v3 migration:
     * the tent's supercycle wins. The plant adopts the tent's `lightHours` /
     * `darkHours`, whatever it was running before.
     */
    ADAPT_TO_DESTINATION_TENT,

    /**
     * The opt-in deviation. The plant keeps the photoperiod it was running even
     * though the destination tent has its own — which makes the move, in
     * photoperiod terms, a no-op.
     */
    KEEP_PREVIOUS,

    /**
     * The tent's hours, but the plant's original cycle anchor, so the superday
     * count is continuous across the move. For the grower who moves a tent around
     * and does not want day 47 to restart at day 1.
     */
    KEEP_PREVIOUS_CYCLE_ANCHOR
}

/**
 * The grower's choices in the move dialog.
 *
 * Defaults are the decisions the migration already made, so constructing the empty
 * value is the same as confirming the dialog with nothing ticked.
 */
data class PlantMigrationChoices(
    /** Default: the destination tent's supercycle wins. */
    val photoperiodPolicy: PhotoperiodPolicy = PhotoperiodPolicy.ADAPT_TO_DESTINATION_TENT,
    /** Restart the superday count at the move instead of continuing it. */
    val resetCycleStartToNow: Boolean = true,
    /**
     * Journal rows are always kept; see [JournalAction.KEEP_ALWAYS]. This flag
     * exists so a dialog can state it, and setting it to `false` is not honoured —
     * it produces [MigrationResolution.REJECTED] rather than a plan that quietly
     * drops rows.
     */
    val keepJournalRecords: Boolean = true
) {
    companion object {
        /** What an untouched dialog means. */
        val DEFAULT = PlantMigrationChoices()
    }
}

/**
 * The outcome of a plant-to-tent move, resolved without touching Room.
 *
 * @param photoperiod the resolved photoperiod, non-null and positive whenever
 *   [resolution] is [MigrationResolution.APPLIED].
 * @param cycleStartAt the anchor the resulting cycle starts from.
 * @param source which side supplied [photoperiod]. Never inferred from the numbers.
 * @param reasonEs Spanish sentence the dialog shows, stating what was chosen and
 *   why. UI copy, so Spanish; the type is English.
 * @param requiresDestinationConfigWrite whether the destination tent has no config
 *   row and one must be written from this plan. False when the tent already has a
 *   config and the plan only reads it.
 */
data class PlantMigrationPlan(
    val resolution: MigrationResolution,
    val photoperiod: PhotoperiodConfig?,
    /**
     * What the plant was running before the move, sanitized. Kept so
     * [changesPhotoperiod] is a comparison against history rather than against a
     * constant, and so the dialog can show "was / will be".
     */
    val previousPhotoperiod: PhotoperiodConfig?,
    val source: PhotoperiodSource,
    val cycleStartAt: Long,
    val journalAction: JournalAction,
    val requiresDestinationConfigWrite: Boolean,
    val keepsPreviousPhotoperiodAsHistory: Boolean,
    val reasonEs: String
) {
    val isApplied: Boolean get() = resolution == MigrationResolution.APPLIED

    /** The resulting hours, or null when the plan was rejected. */
    val lightHours: Int? get() = photoperiod?.lightHours
    val darkHours: Int? get() = photoperiod?.darkHours

    /**
     * Whether the resolved photoperiod differs from what the plant was running.
     *
     * Compared against [previousPhotoperiod] rather than against a constant: the
     * point of the flag is "this move changes the numbers", and only the plant's
     * own history can answer that.
     */
    val changesPhotoperiod: Boolean
        get() = photoperiod != null &&
            previousPhotoperiod?.sanitized()?.takeIf { it.isValid } != photoperiod
}

/**
 * Decides what a plant keeps when it moves between tents.
 *
 * ## What exists and what this adds
 *
 * `PlantDao.assignPlantToTent()` already performs the move; it is a single
 * `UPDATE plants SET tentId = :tentId WHERE id = :plantId`. What did not exist is
 * the decision that has to be made around it, which is the one that used to be
 * ambiguous and lose data:
 *
 * - The plant may have been running a photoperiod that differs from the
 *   destination tent's. Which one survives?
 * - The superday count is anchored to `cycleStartAt`. Does it continue or restart?
 * - The journal is keyed by `plantId`, so it is untouched — but "untouched" has to
 *   be *decided* rather than assumed, or a future refactor of the move can start
 *   touching it.
 *
 * This object answers all three as a pure function, so the dialog's outcome is
 * unit-tested before a single row changes.
 *
 * ## The invariant
 *
 * **The destination tent's supercycle wins by default**, matching the grower
 * decision recorded in `MIGRATION_2_3` and in `SuperCycleRepository`'s
 * precedence: a config belongs to a tent, the tent's plants inherit it, and the
 * per-plant row stays as history. Keeping the previous photoperiod is the opt-in
 * [PhotoperiodPolicy.KEEP_PREVIOUS], and whichever branch runs,
 * [PlantMigrationPlan.source] and [PlantMigrationPlan.reasonEs] say so out loud.
 *
 * ## When the destination tent has no supercycle config
 *
 * The tent's config does not exist, so the tent cannot win by default and the
 * default would have nothing to return — the failure mode that produces a null
 * `lightHours` and a plant whose cycle is 0/0.
 *
 * Resolution, in order, and never "null":
 *
 * 1. the destination tent's config if it has a usable one — source
 *    [PhotoperiodSource.DESTINATION_TENT];
 * 2. the plant's own photoperiod if the tent has none — source
 *    [PhotoperiodSource.PREVIOUS_PLANT], with
 *    [PlantMigrationPlan.requiresDestinationConfigWrite] `true` so the caller writes
 *    the tent's first config from the plant it just received. This is the same
 *    precedence `SuperCycleRepository.getConfigForPlant` already applies, so the
 *    dialog and the repository cannot disagree;
 * 3. neither side usable — [PhotoperiodSource.SAFE_DEFAULT], 18/6, which is the
 *    app's own documented photoperiod preset. The plant is left on a working cycle
 *    and the dialog says the tent has no supercycle yet, instead of the move
 *    producing an invalid cycle.
 *
 * [MigrationResolution.REJECTED] exists for exactly one case, and it is not a
 * photoperiod problem: [PlantMigrationChoices.keepJournalRecords] `false`. The
 * journal cannot be dropped by a tent move, so a request to drop it is refused
 * rather than honoured.
 */
object PlantMigrationPlanner {

    /**
     * Resolves a move. Pure: no Room, no DAO, no clock — [nowMillis] is injected
     * for the same reason [SuperCycleEngine] takes `nowTimestamp`, so the whole
     * decision is deterministic in a test.
     *
     * @param nowMillis the instant of the move, used only when the anchor resets.
     */
    fun plan(
        current: PlantCycleState,
        destination: TentCycleState,
        choices: PlantMigrationChoices = PlantMigrationChoices.DEFAULT,
        nowMillis: Long
    ): PlantMigrationPlan {
        // The journal is keyed by plantId, so a tent move cannot drop a row. A
        // request to drop one is refused here instead of being honoured later by
        // code that does not know about grow_events.
        if (!choices.keepJournalRecords) {
            return rejected(
                "Las notas del diario están vinculadas a la planta (grow_events) " +
                    "y un cambio de carpa no las afecta: no se pueden eliminar " +
                    "desde aquí."
            )
        }

        val tent = destination.photoperiod?.sanitized()?.takeIf { it.isValid }
        val plant = current.photoperiod?.sanitized()?.takeIf { it.isValid }

        // What is used when the destination tent has nothing to say. Resolved once,
        // with its own source and its own sentence, so the two branches below cannot
        // pick a photoperiod without also saying where it came from.
        val fallbackSource =
            if (plant != null) PhotoperiodSource.PREVIOUS_PLANT
            else PhotoperiodSource.SAFE_DEFAULT
        val fallbackPhotoperiod = plant ?: SAFE_DEFAULT_PHOTOPERIOD
        val fallbackReason = if (plant != null) {
            "La carpa ${destination.tentName} no tiene superciclo configurado, " +
                "así que ${current.plantName} conserva su fotoperiodo de " +
                "${plant.lightHours}h luz / ${plant.darkHours}h oscuridad."
        } else {
            "Ni ${current.plantName} ni la carpa ${destination.tentName} tienen " +
                "un fotoperiodo utilizable, así que se aplica el valor seguro de " +
                "18/6 luz / 6h oscuridad."
        }

        val source: PhotoperiodSource
        val photoperiod: PhotoperiodConfig
        val reasonEs: String

        when (choices.photoperiodPolicy) {
            PhotoperiodPolicy.KEEP_PREVIOUS -> {
                if (plant != null) {
                    source = PhotoperiodSource.PREVIOUS_PLANT
                    photoperiod = plant
                    reasonEs = "Conservas el fotoperiodo anterior de " +
                        "${current.plantName} (${plant.lightHours}h luz / " +
                        "${plant.darkHours}h oscuridad). La carpa " +
                        "${destination.tentName} manda por defecto; esta es la " +
                        "excepción que marcaste."
                } else {
                    source = PhotoperiodSource.SAFE_DEFAULT
                    photoperiod = SAFE_DEFAULT_PHOTOPERIOD
                    reasonEs = "${current.plantName} no tenía un fotoperiodo " +
                        "guardado, así que se queda en el valor seguro de 18/6 " +
                        "hasta que configures la carpa ${destination.tentName}."
                }
            }

            PhotoperiodPolicy.KEEP_PREVIOUS_CYCLE_ANCHOR -> {
                if (tent != null) {
                    source = PhotoperiodSource.DESTINATION_TENT
                    photoperiod = tent
                    reasonEs = "Tomas las ${tent.lightHours}h/${tent.darkHours}h de " +
                        "la carpa ${destination.tentName} y conservas el día de " +
                        "superciclo, para que la cuenta no vuelva a empezar."
                } else {
                    source = fallbackSource
                    photoperiod = fallbackPhotoperiod
                    reasonEs = fallbackReason
                }
            }

            PhotoperiodPolicy.ADAPT_TO_DESTINATION_TENT -> {
                if (tent != null) {
                    source = PhotoperiodSource.DESTINATION_TENT
                    photoperiod = tent
                    reasonEs = "El superciclo de la carpa " +
                        "${destination.tentName} manda: ${tent.lightHours}h luz / " +
                        "${tent.darkHours}h oscuridad."
                } else {
                    source = fallbackSource
                    photoperiod = fallbackPhotoperiod
                    reasonEs = fallbackReason
                }
            }
        }

        // The anchor: keep the plant's history unless the grower asked for a
        // restart, and fall back to now when there is no history to keep.
        val anchor = when {
            choices.resetCycleStartToNow -> nowMillis
            current.cycleStartAt != null && current.cycleStartAt > 0L -> current.cycleStartAt
            else -> nowMillis
        }

        return PlantMigrationPlan(
            resolution = MigrationResolution.APPLIED,
            photoperiod = photoperiod,
            previousPhotoperiod = plant,
            source = source,
            cycleStartAt = anchor,
            journalAction = JournalAction.KEEP_ALWAYS,
            // The tent has to be given a config row unless it already owns one.
            requiresDestinationConfigWrite = tent == null,
            keepsPreviousPhotoperiodAsHistory = current.photoperiod != null,
            reasonEs = reasonEs
        )
    }

    private fun rejected(reasonEs: String) = PlantMigrationPlan(
        resolution = MigrationResolution.REJECTED,
        photoperiod = null,
        previousPhotoperiod = null,
        source = PhotoperiodSource.SAFE_DEFAULT,
        cycleStartAt = 0L,
        journalAction = JournalAction.KEEP_ALWAYS,
        requiresDestinationConfigWrite = false,
        keepsPreviousPhotoperiodAsHistory = true,
        reasonEs = reasonEs
    )
}