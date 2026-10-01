package com.trichome.app.model

/**
 * One answer the "Cambiar de carpa" dialog can offer, as data.
 *
 * The three policies are not a set of independent checkboxes: they are an exclusive
 * choice with a defined default, which is the shape [PlantMigrationPlanner] was
 * built for. [PlantMigrationPolicyOption] is the *UI-facing* spelling of that
 * exclusive choice, kept out of the engine because the engine deliberately knows
 * nothing about dialogs or widgets.
 */
enum class PlantMigrationPolicyOption(
    val policy: PhotoperiodPolicy,
    /** Title of the selectable row. Spanish: shown to the grower. */
    val labelEs: String,
    /** One sentence on what this row means. Spanish: shown to the grower. */
    val descriptionEs: String,
    /** True for the row the dialog starts on. Exactly one. */
    val isDefault: Boolean
) {
    ADAPT_TO_TENT(
        policy = PhotoperiodPolicy.ADAPT_TO_DESTINATION_TENT,
        labelEs = "Adaptar al superciclo de la carpa destino",
        descriptionEs = "La planta toma las horas de luz y oscuridad de la carpa " +
            "destino. Es lo que la app ya aplica por defecto.",
        isDefault = true
    ),
    KEEP_PREVIOUS_PHOTOPERIOD(
        policy = PhotoperiodPolicy.KEEP_PREVIOUS,
        labelEs = "Conservar el fotoperiodo anterior",
        descriptionEs = "La planta sigue con las mismas horas aunque la carpa " +
            "tenga otras. Excepción que marcas a mano.",
        isDefault = false
    ),
    KEEP_PREVIOUS_CYCLE_ANCHOR(
        policy = PhotoperiodPolicy.KEEP_PREVIOUS_CYCLE_ANCHOR,
        labelEs = "Conservar el día de superciclo",
        descriptionEs = "Toma las horas de la carpa destino pero mantiene el día " +
            "de superciclo, para que la cuenta no vuelva a empezar.",
        isDefault = false
    );

    companion object {
        /** What an untouched dialog means. */
        val DEFAULT = ADAPT_TO_TENT

        /** Every option in the order the dialog lists them. */
        val ALL: List<PlantMigrationPolicyOption> = entries

        /** The option for a policy, falling back to the default for an unknown one. */
        fun forPolicy(policy: PhotoperiodPolicy): PlantMigrationPolicyOption =
            ALL.firstOrNull { it.policy == policy } ?: DEFAULT
    }
}

/**
 * The dialog's state, and the decision it resolves to.
 *
 * Two invariants, both of which exist because of a real ambiguity in the dialog
 * this replaces:
 *
 *  1. **The policy is always defined.** It is a selected enum option, never a set of
 *     booleans whose unchecked state had no outcome — that missing branch is how a
 *     photoperiod changed without the grower choosing to.
 *  2. **The journal is never a choice.** [PlantMigrationChoices.keepJournalRecords]
 *     is carried as `true` unconditionally. `GrowEvent` rows are keyed by `plantId`
 *     with a `CASCADE` foreign key to `plants` and the tent is not in that
 *     relationship at all, so `assignPlantToTent()` cannot drop or hide one. The
 *     dialog states that in its copy instead of pretending to offer the option.
 */
data class PlantMigrationDialogState(
    val selected: PlantMigrationPolicyOption = PlantMigrationPolicyOption.DEFAULT,
    /** Restart the superday count at the move. See [PlantMigrationChoices]. */
    val resetCycleStartToNow: Boolean = true
) {
    /** Selects a policy row by its engine policy. */
    fun withPolicy(policy: PhotoperiodPolicy): PlantMigrationDialogState =
        copy(selected = PlantMigrationPolicyOption.forPolicy(policy))

    /** The choices handed to [PlantMigrationPlanner.plan]. */
    fun toChoices(): PlantMigrationChoices = PlantMigrationChoices(
        photoperiodPolicy = selected.policy,
        resetCycleStartToNow = resetCycleStartToNow,
        // Never a choice. See the class KDoc.
        keepJournalRecords = true
    )

    /**
     * Resolves the move without touching Room.
     *
     * @param nowMillis the instant of the move, so a test is deterministic.
     */
    fun planFor(
        current: PlantCycleState,
        destination: TentCycleState,
        nowMillis: Long
    ): PlantMigrationPlan = PlantMigrationPlanner.plan(
        current = current,
        destination = destination,
        choices = toChoices(),
        nowMillis = nowMillis
    )
}

/** Everything the migration dialog has to write, as data. */
data class PlantMigrationDialogContent(
    /** How the move resolved. Drives [canApply]. */
    val resolution: MigrationResolution,
    /** Dialog title. */
    val titleEs: String,
    /** The "was / will be" photoperiod line. */
    val photoperiodChangeEs: String,
    /** `plan.reasonEs` — why this outcome, in the planner's own words. */
    val reasonEs: String,
    /**
     * The warning to show when `plan.requiresDestinationConfigWrite` is true, or
     * null when the destination already has its own config.
     *
     * Never silently omitted: writing 18/6 into a tent that had no config at all is
     * a decision, and the grower has to see it made.
     */
    val destinationConfigWarningEs: String?,
    /** The journal statement. Always present; there is nothing to toggle. */
    val journalNoticeEs: String,
    /** Confirm button label. */
    val confirmLabelEs: String,
    /** Dismiss button label. */
    val cancelLabelEs: String
) {
    /**
     * Whether the move may be written at all.
     *
     * A rejected plan carries no photoperiod, so applying it would move the plant and
     * leave its cycle unresolved — the plant would land in the tent running 0/0. The
     * single rejection case is a request to drop the journal, which
     * [PlantMigrationDialogState] never produces; this exists so a future choice
     * cannot slip past the button.
     */
    val canApply: Boolean get() = resolution == MigrationResolution.APPLIED
}

/** The dialog's copy, resolved from a [PlantMigrationPlan]. */
object PlantMigrationDialogCopy {

    private const val JOURNAL_NOTICE_ES: String =
        "La bitácora se mantiene: sus registros están ligados a la planta, no a " +
            "la carpa, así que el cambio no los afecta."

    private const val TITLE_ES: String = "Cambiar de carpa"

    /**
     * The warning shown when the destination tent has no supercycle config at all.
     *
     * Two cases have to be distinguishable, because they mean different things:
     *
     *  - source [PhotoperiodSource.PREVIOUS_PLANT]: the plant keeps its own
     *    photoperiod and the tent is given a config from it. Nothing is invented.
     *  - source [PhotoperiodSource.SAFE_DEFAULT]: neither side had one, so 18/6 is
     *    substituted. That is a number nobody typed, and it is written into a tent
     *    that will silently drive every future plant added to it.
     */
    fun destinationConfigWarningEs(
        plan: PlantMigrationPlan,
        tentName: String
    ): String? {
        if (!plan.requiresDestinationConfigWrite) return null
        val photoperiod = plan.photoperiod
        val hours = if (photoperiod != null) {
            "${photoperiod.lightHours} h luz / ${photoperiod.darkHours} h oscuridad"
        } else {
            "sin fotoperiodo"
        }
        return when (plan.source) {
            PhotoperiodSource.SAFE_DEFAULT ->
                "Ojo: «$tentName» no tiene superciclo configurado, y esta planta " +
                    "tampoco tenía uno guardado. Se usará el valor seguro de 18 h " +
                    "de luz / 6 h de oscuridad y se guardará en la carpa para " +
                    "futuras plantas. Cámbialo cuando quieras."

            PhotoperiodSource.PREVIOUS_PLANT ->
                "«$tentName» no tiene superciclo configurado. Se creará con las " +
                    "horas que ya usaba esta planta ($hours) para que no cambie " +
                    "nada al moverla."

            PhotoperiodSource.DESTINATION_TENT ->
                "Se actualizará la configuración de «$tentName» a $hours."
        }
    }

    /**
     * The "was / will be" line.
     *
     * Null when the numbers do not move ([PlantMigrationPlan.changesPhotoperiod] is
     * false), because "12 h luz / 12 h oscuridad → 12 h luz / 12 h oscuridad" reads
     * as a change and is not one.
     */
    fun photoperiodChangeEs(plan: PlantMigrationPlan): String? {
        val next = plan.photoperiod ?: return null
        if (!plan.changesPhotoperiod) return null
        val previous = plan.previousPhotoperiod
            ?: return "Se configurará con ${next.lightHours} h luz / ${next.darkHours} h oscuridad."
        return "De ${previous.lightHours} h luz / ${previous.darkHours} h oscuridad " +
            "a ${next.lightHours} h luz / ${next.darkHours} h oscuridad."
    }

    /** The whole dialog's copy for one resolved [plan]. */
    fun contentFor(
        plan: PlantMigrationPlan,
        plantName: String,
        tentName: String
    ): PlantMigrationDialogContent = PlantMigrationDialogContent(
        resolution = plan.resolution,
        titleEs = "$TITLE_ES · $plantName",
        photoperiodChangeEs = photoperiodChangeEs(plan) ?:
            "El fotoperiodo no cambia: sigue en ${plan.photoperiod?.let { "${it.lightHours} h luz / ${it.darkHours} h oscuridad" } ?: "el valor actual"}.",
        reasonEs = plan.reasonEs,
        destinationConfigWarningEs = destinationConfigWarningEs(plan, tentName),
        journalNoticeEs = JOURNAL_NOTICE_ES,
        confirmLabelEs = "Mover a $tentName",
        cancelLabelEs = "Cancelar"
    )
}