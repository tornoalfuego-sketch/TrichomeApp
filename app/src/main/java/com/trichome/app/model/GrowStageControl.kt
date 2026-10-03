package com.trichome.app.model

/**
 * Crop stage control: the "Cambiar de fase" transition and the "Finalizar cultivo"
 * archive.
 *
 * ## What already exists, and why this object does not re-model it
 *
 * `protocol_stages` holds the grow's ordered blocks and `stage_entries` holds the
 * timeline of when the plant entered and left each of them. [PlantMigrationPlan]
 * already establishes the shape this file follows: a pure planner that resolves a
 * decision before a single row changes, an exhaustive outcome enum rather than a
 * checkbox whose unchecked state has no meaning, and a `reasonEs` the dialog renders
 * verbatim.
 *
 * ## The invariant this exists to protect
 *
 * **A stage change that leaves the previous entry open corrupts the timeline.** The
 * open entry is the one carrying `exitedAt = NULL`, and the timeline is the only record
 * of what happened to the plant. Writing the new entry is the easy half; closing the
 * old one is the load-bearing half, because a plant with two open entries has no answer
 * to "which stage is it in now" other than the denormalised `plants.currentStage`, and
 * that field is a cache of this table rather than its record.
 *
 * So [StageTransitionPlan] carries the closing write explicitly — [closesOpenEntry] and
 * [openEntryId] are named fields, not something a caller is trusted to remember — and
 * the repository performs both halves inside one transaction. There is no path in this
 * app that writes a `stage_entries` row without also closing the previous one.
 *
 * ## Nothing here deletes
 *
 * Neither operation issues a `DELETE`. Finalizing a grow sets `plants.isActive = false`
 * and closes the open stage entry; every journal row, protocol, reminder and stage entry
 * stays exactly where it was. The data-loss budget in this project is zero, and
 * `grow_events` cascades off `plants`, which makes a delete the one action that would
 * spend it.
 */
object GrowStageCopy {

    /** Title of the stage-change dialog. */
    const val CHANGE_STAGE_TITLE_ES: String = "Cambiar de fase"

    /** Title of the finalize confirmation. */
    const val FINALIZE_TITLE_ES: String = "Finalizar cultivo"

    /** Button label that opens the stage-change dialog. */
    const val CHANGE_STAGE_ACTION_ES: String = "Cambiar de fase"

    /** Button label that opens the finalize confirmation. */
    const val FINALIZE_ACTION_ES: String = "Finalizar cultivo"

    /** Panel heading above the two actions. */
    const val LIFECYCLE_HEADING_ES: String = "Ciclo de cultivo"

    /** Label of the stage timeline list. */
    const val TIMELINE_HEADING_ES: String = "Línea de tiempo de etapas"

    /** Sentence shown when a plant has no stage history at all. */
    const val TIMELINE_EMPTY_ES: String =
        "Esta planta todavía no tiene cambios de etapa registrados. El primero que " +
            "anotes cerrará la etapa que esté abierta, para que el historial cuadre."

    /** Sentence under the stage timeline, stating the invariant to the grower. */
    const val TIMELINE_HINT_ES: String =
        "Al cambiar de fase se cierra la etapa anterior con la fecha de hoy y se abre " +
            "la nueva. Sin ese cierre el historial queda con dos etapas abiertas a la vez."

    /** Dialog heading for the stage options. */
    const val OPTIONS_HEADING_ES: String = "Elige la nueva etapa"

    /** Label of the stage dialog's confirm button. */
    const val SAVE_ES: String = "Guardar"

    /** Label of the stage dialog's dismiss button. */
    const val CANCEL_ES: String = "Cancelar"

    /** Sentence for one open stage entry. */
    fun openEntryEs(stageName: String, daysInStage: Long): String =
        "Abierta: $stageName · $daysInStage día(s) en esta etapa"

    /** Sentence for one closed stage entry, with both dates and the duration. */
    fun closedEntryEs(
        stageName: String,
        enteredLabelEs: String,
        exitedLabelEs: String,
        daysInStage: Long
    ): String =
        "$stageName · de $enteredLabelEs a $exitedLabelEs · $daysInStage día(s)"

    /** The archived banner a finalized plant shows in place of the two actions. */
    const val ARCHIVED_BANNER_ES: String =
        "Cultivo finalizado. La planta queda archivada y su historial se conserva " +
            "entero: puedes seguir consultándolo y exportándolo."

    /** Short label for an archived plant in a list. */
    const val ARCHIVED_CHIP_ES: String = "Finalizado"

    /** Sentence shown once a transition has been written. */
    fun transitionAppliedEs(plan: StageTransitionPlan): String =
        if (plan.closesOpenEntry) {
            "Se cerró «${plan.previousStageName}» y se abrió «${plan.stageName}» el " +
                "${plan.dayLabelEs}. La línea de tiempo queda con una sola etapa abierta."
        } else {
            "Se abrió «${plan.stageName}» el ${plan.dayLabelEs}."
        }

    /** Sentence shown once a finalize has been written. */
    fun finalizeAppliedEs(plan: PlantFinalizationPlan): String = plan.confirmationEs
}

/**
 * Which stage list the "Cambiar de fase" dialog offers.
 *
 * Two vocabularies exist in this app and they are not interchangeable. `protocol_stages`
 * holds the grow's own blocks — free text the grower typed, ordered, with durations.
 * `plants.currentStage` holds one of five lifecycle keys. A plant with a protocol should
 * be moved through its own blocks; a plant without one can only be moved through the
 * lifecycle keys, because there is nothing else to move it through.
 */
enum class StageOptionSource(val labelEs: String) {
    /** The blocks of the plant's active protocol. */
    PROTOCOL("Etapas del protocolo"),

    /** The five lifecycle keys, because the plant has no protocol. */
    LIFECYCLE("Etapas del ciclo")
}

/** How a requested stage change resolved. */
enum class StageTransitionResolution {
    /** The write happens: the requested stage differs from the current one. */
    APPLIED,

    /** Nothing is written: the requested stage is the one the plant is already in. */
    NO_CHANGE,

    /** Nothing is written, and [StageTransitionPlan.reasonEs] says why it must not be. */
    REJECTED
}

/** How a requested finalize resolved. */
enum class PlantFinalizationOutcome {
    /** The plant is archived: `isActive` goes false and its open stage entry closes. */
    ARCHIVED,

    /** Nothing is written: the plant was already finalized. */
    ALREADY_FINALIZED,

    /** Nothing is written, and [PlantFinalizationPlan.reasonEs] says why. */
    BLOCKED
}

/**
 * The stage a plant is in, as the planner sees it.
 *
 * @param currentStage `plants.currentStage`. Free text: a lifecycle key, or a protocol
 *   block name the grower typed. Compared through [trimmedStage] rather than by
 *   equality, because `" Vegetativa "` and `"Vegetativa"` are the same stage, and writing
 *   a second entry for them would split one continuous stay in a stage in two.
 * @param openStageName the `stageName` of the open `stage_entries` row, when there is
 *   one. Deliberately **not** the same field as [currentStage]: when the two disagree the
 *   timeline is the record and [currentStage] is the stale cache, so the planner reports
 *   the disagreement instead of silently trusting one of them.
 * @param growStartMillis `plants.growStartTimestamp`, for the day count the finalize
 *   confirmation quotes.
 */
data class StageContext(
    val plantId: Long,
    val plantName: String,
    val currentStage: String,
    val growStartMillis: Long = 0L,
    val openStageName: String? = null,
    val isActive: Boolean = true,
    val protocolId: Long? = null
) {
    /** The stage text as it is compared: trimmed, so padding is not a difference. */
    val trimmedStage: String get() = currentStage.trim()

    /**
     * Whether `plants.currentStage` and the open stage entry agree.
     *
     * A disagreement is a real, recoverable state — it is what a stage entry written
     * directly by an older build looks like — so it is *reported*, never used to refuse a
     * transition. Refusing would leave the grower unable to repair the row in front of
     * them.
     */
    val isCacheConsistent: Boolean
        get() = openStageName == null || openStageName.trim() == trimmedStage
}

/** One selectable stage in the dialog. [stageName] is exactly what gets stored. */
data class StageOption(
    val stageName: String,
    val labelEs: String,
    val source: StageOptionSource,
    val durationDays: Int?
) {
    /** Whether choosing this option would change anything. */
    fun isCurrent(context: StageContext): Boolean =
        stageName.trim() == context.trimmedStage
}

/**
 * The resolved stage change, before anything is written.
 *
 * @param protocolId what the new `stage_entries` row is attributed to, or
 *   [NO_PROTOCOL_ID] when the plant has no protocol. `stage_entries.protocolId` is
 *   `NOT NULL` and carries **no foreign key** — the v1→v2 DDL in `MIGRATION_1_2` declares
 *   the table without one — so the sentinel is a legal stored value and a plant with no
 *   protocol can still have a timeline.
 * @param closesOpenEntry whether a `stage_entries` row with `exitedAt IS NULL` exists and
 *   will be closed. Named rather than left implicit so a caller cannot write the new entry
 *   and forget this one.
 * @param openEntryId the row to close, read from the table rather than inferred.
 * @param enteredAt / [exitedAt] the same instant, so the timeline is contiguous: no gap,
 *   and no overlap.
 * @param dayLabelEs `dd/MM/yyyy` of [enteredAt]. Passed in rather than formatted here, so
 *   the planner reads neither a clock nor a calendar; the caller resolves the day in the
 *   device's own zone.
 */
data class StageTransitionPlan(
    val resolution: StageTransitionResolution,
    val plantId: Long,
    val stageName: String,
    val previousStageName: String,
    val protocolId: Long,
    val optionSource: StageOptionSource,
    val closesOpenEntry: Boolean,
    val openEntryId: Long?,
    val enteredAt: Long,
    val exitedAt: Long,
    val dayLabelEs: String,
    val reasonEs: String
) {
    /** Whether the caller has to write anything at all. */
    val isApplied: Boolean get() = resolution == StageTransitionResolution.APPLIED

    /** Whether the previous open entry is closed at the same instant the new one opens. */
    val closesAtTheSameInstant: Boolean get() = exitedAt == enteredAt

    /** Whether the row is attributed to a real protocol rather than to the plant alone. */
    val hasProtocol: Boolean get() = protocolId != NO_PROTOCOL_ID

    companion object {
        /**
         * The `protocolId` stored on a stage entry that belongs to no protocol.
         *
         * Zero, because that is the sentinel this schema already uses: the v2→v3
         * fixtures contain rows with `plantId = 0` for exactly this reason, and a large
         * negative sentinel would read as a real id in raw SQL.
         */
        const val NO_PROTOCOL_ID: Long = 0L
    }
}

/**
 * The resolved finalize, before anything is written.
 *
 * @param closesOpenStageEntry whether the plant had an open stage entry. Finalizing leaves
 *   it closed, for the same reason a transition does: a finished grow whose last stage
 *   never ended is a timeline still claiming the plant is in it.
 * @param plantName as the confirmation must name it, so the grower sees which plant is
 *   being archived rather than whichever row was nearest the tap.
 */
data class PlantFinalizationPlan(
    val outcome: PlantFinalizationOutcome,
    val plantId: Long,
    val plantName: String,
    val daysInGrow: Int,
    val stageEntriesKept: Int,
    val journalRowsKept: Int,
    val closesOpenStageEntry: Boolean,
    val finalizedAt: Long,
    val reasonEs: String
) {
    /** Whether the caller has to write anything. */
    val isArchived: Boolean get() = outcome == PlantFinalizationOutcome.ARCHIVED

    /**
     * Spanish confirmation, printed after the write succeeds.
     *
     * Counts what survived, not just what changed. "Archivada" on its own tells the
     * grower nothing about the thing they are actually afraid of, which is losing the
     * season.
     */
    val confirmationEs: String
        get() = "«$plantName» queda archivada con $daysInGrow día(s) de cultivo, " +
            "${countEs(journalRowsKept, "evento", "eventos")} de bitácora y " +
            "${countEs(stageEntriesKept, "cambio", "cambios")} de etapa. " +
            "No se ha borrado nada."

    companion object {
        /**
         * `1 evento` / `3 eventos`.
         *
         * Spanish pluralises the noun, so `evento(s)` — the shape this codebase uses
         * elsewhere for live counts — is wrong in a sentence the grower reads once and
         * remembers. It is accepted for counts shown as live figures; a confirmation is
         * not that.
         */
        fun countEs(count: Int, singular: String, plural: String): String =
            "$count ${if (count == 1) singular else plural}"
    }
}

/**
 * A protocol block, reduced to what a stage option needs.
 *
 * `model/` holds no Room entities, so this is what the ViewModel maps
 * `com.trichome.app.data.entity.ProtocolStage` onto.
 */
data class ProtocolStageBlock(
    val stageName: String,
    val durationDays: Int,
    val sortOrder: Int
)

/**
 * The five lifecycle stage keys and their Spanish labels.
 *
 * The keys are the same five `ui/screens/plant/PlantEditForm.STAGES` writes, extracted
 * here only because `model/` cannot import from `ui/screens/plant/`. A second list that
 * had quietly drifted from the editor's is exactly how a plant ends up with
 * `currentStage = "flower"`, which no screen maps to a label — so `GrowStageKeysTest`
 * asserts the two lists are equal rather than trusting this comment.
 */
object PlantEditStages {

    /** The stored keys, in the order the editor offers them. */
    val keys: List<String> = listOf("seedling", "vegetativo", "floracion", "lavado", "cosecha")

    /** Spanish label for a stored key, or the key itself when it is not one of the five. */
    fun labelEs(stage: String): String = when (stage) {
        "seedling" -> "Plántula"
        "vegetativo", "vegetative" -> "Vegetativo"
        "floracion", "floración" -> "Floración"
        "lavado" -> "Lavado"
        "cosecha" -> "Cosecha"
        else -> stage
    }
}

/**
 * Resolves both operations as pure functions.
 *
 * ## The clock is injected
 *
 * [transition] and [finalize] take [nowMillis]. Same rule as [SuperCycleEngine] and
 * [PlantMigrationPlanner]: a decision whose timestamp comes from
 * `System.currentTimeMillis()` is not reproducible in a test, and the `exitedAt` of a
 * closed stage entry is exactly the number a migration test would want to assert. The
 * day label is formatted by the caller for the same reason.
 */
object GrowStagePlanner {

    /**
     * Resolves a stage change.
     *
     * ## The rules, in order
     *
     * 1. A blank stage name is refused. An entry with an empty `stageName` is a row the
     *    timeline cannot render.
     * 2. A finalized plant is refused. Archiving is the terminal state, and reopening it
     *    from a stage dialog would make "finalizada" a word the app uses and then
     *    withdraws.
     * 3. A plant with an open entry and no protocol is **not** refused. That is the
     *    `plants.currentStage` / `stage_entries.stageName` disagreement
     *    [StageContext.isCacheConsistent] describes, and the transition is the fix: the
     *    stale cache is corrected while the open entry is closed.
     * 4. Choosing the stage the plant is already in writes nothing and says so. Splitting
     *    one continuous stay in a stage into two adjacent entries is noise, and a grower
     *    who taps the current chip has not asked for it.
     *
     * @param option the chosen stage, compared by trimmed name because
     *   `protocol_stages.stageName` comes from the grower's own typing.
     * @param openEntryId the id of the plant's open entry, read from the table. This is
     *   what makes [StageTransitionPlan.closesOpenEntry] trustworthy: it is observed, not
     *   inferred from a name that may be stale.
     * @param dayLabelEs `dd/MM/yyyy` of [nowMillis] in the device's zone.
     */
    fun transition(
        context: StageContext,
        option: StageOption,
        openEntryId: Long?,
        nowMillis: Long,
        dayLabelEs: String
    ): StageTransitionPlan {
        val target = option.stageName.trim()
        val cached = context.trimmedStage
        val previousName = context.openStageName?.trim()?.ifEmpty { null } ?: cached

        if (target.isEmpty()) {
            return refused(
                context = context,
                option = option,
                dayLabelEs = dayLabelEs,
                reasonEs = "Elige una etapa antes de guardar: una entrada sin nombre no se " +
                    "puede leer en la línea de tiempo."
            )
        }

        if (!context.isActive) {
            return refused(
                context = context,
                option = option,
                dayLabelEs = dayLabelEs,
                reasonEs = "«${context.plantName}» está archivada por un cultivo finalizado. " +
                    "No se le cambia la etapa: su historia ya está cerrada."
            )
        }

        val closesExisting = openEntryId != null

        if (!closesExisting && target == cached) {
            return StageTransitionPlan(
                resolution = StageTransitionResolution.NO_CHANGE,
                plantId = context.plantId,
                stageName = target,
                previousStageName = cached,
                protocolId = context.protocolId ?: StageTransitionPlan.NO_PROTOCOL_ID,
                optionSource = option.source,
                closesOpenEntry = false,
                openEntryId = null,
                enteredAt = nowMillis,
                exitedAt = nowMillis,
                dayLabelEs = dayLabelEs,
                reasonEs = "«${context.plantName}» ya está en «$target». No hace falta abrir " +
                    "otra entrada ni cerrar ninguna."
            )
        }

        val protocolId = context.protocolId ?: StageTransitionPlan.NO_PROTOCOL_ID
        val reasonEs = when {
            closesExisting && previousName != target ->
                "«$previousName» se cierra el $dayLabelEs y «$target» se abre en el mismo " +
                    "instante: la línea de tiempo queda con una sola etapa abierta."

            closesExisting ->
                "Ya había una entrada abierta («$previousName»). Se cierra y se abre " +
                    "«$target», para que quede una sola etapa abierta."

            protocolId == StageTransitionPlan.NO_PROTOCOL_ID ->
                "«${context.plantName}» no tiene protocolo, así que la entrada queda " +
                    "registrada contra la planta. Se abre «$target»."

            else ->
                "Se abre «$target» el $dayLabelEs en la línea de tiempo de " +
                    "«${context.plantName}»."
        }

        return StageTransitionPlan(
            resolution = StageTransitionResolution.APPLIED,
            plantId = context.plantId,
            stageName = target,
            previousStageName = previousName,
            protocolId = protocolId,
            optionSource = option.source,
            closesOpenEntry = closesExisting,
            openEntryId = openEntryId,
            enteredAt = nowMillis,
            exitedAt = nowMillis,
            dayLabelEs = dayLabelEs,
            reasonEs = reasonEs
        )
    }

    /**
     * The stages offered for [context].
     *
     * Protocol blocks when there is a protocol, lifecycle keys otherwise. The two are
     * never mixed: offering a protocol block and a lifecycle key in one list would let a
     * grower move a plant to `seedling` while the protocol's own blocks say
     * "Vegetativa", and the timeline would then hold two vocabularies at once.
     */
    fun optionsFor(
        context: StageContext,
        protocolStages: List<ProtocolStageBlock>
    ): List<StageOption> {
        if (protocolStages.isNotEmpty()) {
            return protocolStages
                .sortedBy { it.sortOrder }
                .map { block ->
                    StageOption(
                        stageName = block.stageName,
                        labelEs = block.stageName,
                        source = StageOptionSource.PROTOCOL,
                        durationDays = block.durationDays
                    )
                }
                .distinctBy { it.stageName.trim() }
        }
        return PlantEditStages.keys.map { key ->
            StageOption(
                stageName = key,
                labelEs = PlantEditStages.labelEs(key),
                source = StageOptionSource.LIFECYCLE,
                durationDays = null
            )
        }
    }

    /**
     * Resolves a finalize.
     *
     * Never returns [PlantFinalizationOutcome.BLOCKED]: there is no condition under which
     * this app refuses to archive a plant, because archiving writes two columns of
     * terminal state and deletes nothing that could be refused. The value exists so a
     * future rule that *does* have one has somewhere to go, and so `when` over this enum
     * stays exhaustive.
     */
    fun finalize(
        context: StageContext,
        openEntryId: Long?,
        stageEntryCount: Int,
        journalRowCount: Int,
        nowMillis: Long
    ): PlantFinalizationPlan {
        val name = context.plantName.trim().ifEmpty { "la planta" }
        val days = StageProgressEngine.daysInGrow(context.growStartMillis, nowMillis)

        if (!context.isActive) {
            return PlantFinalizationPlan(
                outcome = PlantFinalizationOutcome.ALREADY_FINALIZED,
                plantId = context.plantId,
                plantName = name,
                daysInGrow = days,
                stageEntriesKept = stageEntryCount,
                journalRowsKept = journalRowCount,
                closesOpenStageEntry = false,
                finalizedAt = 0L,
                reasonEs = "«$name» ya estaba archivada. No se ha vuelto a cambiar nada."
            )
        }

        return PlantFinalizationPlan(
            outcome = PlantFinalizationOutcome.ARCHIVED,
            plantId = context.plantId,
            plantName = name,
            daysInGrow = days,
            stageEntriesKept = stageEntryCount,
            journalRowsKept = journalRowCount,
            closesOpenStageEntry = openEntryId != null,
            finalizedAt = nowMillis,
            reasonEs = "«$name» pasa a archivada y su etapa abierta se cierra. Sus " +
                "${PlantFinalizationPlan.countEs(journalRowCount, "evento", "eventos")} " +
                "de bitácora, sus " +
                "${PlantFinalizationPlan.countEs(stageEntryCount, "cambio de etapa", "cambios de etapa")}, " +
                "sus protocolos y sus recordatorios se conservan intactos: finalizar " +
                "no borra nada."
        )
    }

    /**
     * The Spanish body of the finalize confirmation.
     *
     * [PlantFinalizationPlan.reasonEs] already says what is kept; this adds what the
     * button does and that the *state* is one-way. The irreversibility sentence is about
     * the archived flag, not the data, because the data survives — conflating the two is
     * what makes a grower afraid to archive a finished plant.
     */
    fun finalizeDialogBodyEs(plan: PlantFinalizationPlan): String =
        plan.reasonEs + "\n\nPodrás seguir viendo su historial y exportarlo. El estado de " +
            "archivada no se puede deshacer desde la app."

    private fun refused(
        context: StageContext,
        option: StageOption,
        dayLabelEs: String,
        reasonEs: String
    ): StageTransitionPlan = StageTransitionPlan(
        resolution = StageTransitionResolution.REJECTED,
        plantId = context.plantId,
        stageName = option.stageName.trim(),
        previousStageName = context.trimmedStage,
        protocolId = context.protocolId ?: StageTransitionPlan.NO_PROTOCOL_ID,
        optionSource = option.source,
        closesOpenEntry = false,
        openEntryId = null,
        enteredAt = 0L,
        exitedAt = 0L,
        dayLabelEs = dayLabelEs,
        reasonEs = reasonEs
    )
}