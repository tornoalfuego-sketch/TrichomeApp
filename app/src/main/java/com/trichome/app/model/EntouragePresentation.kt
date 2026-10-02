package com.trichome.app.model

import kotlin.math.roundToInt

/**
 * Presentation helpers for the Séquito module.
 *
 * ## Why this file exists
 *
 * Compose has no unit-test runtime on the `test` classpath — only `androidTest`
 * has one, and that needs a device. So every decision the module's screens make
 * about *what to say* lives here, in plain Kotlin, and the composables only
 * decide where to put it. That is the same split `SuperCycleForm` uses for the
 * supercycle screen, and the same reason it exists: the bug it guards is a
 * decision, not a pixel.
 *
 * The three rules encoded here, all of them load-bearing:
 *
 * 1. **The evidence line is always rendered.** [EntourageSynergyCard.linesEs]
 *    carries an `EVIDENCE` entry for every card, including when the shipped
 *    content leaves `evidenceEs` blank — in which case the line says so in
 *    plain words instead of the card quietly reading as if it had none. A
 *    health-adjacent screen that hides how well supported a claim is, or
 *    implies support it does not have, is the worst thing this module could do.
 * 2. **The booster percentage is a distance, never a verdict.**
 *    [EntourageBooster.frame] presents it as a match against ONE profile's
 *    proportions, and every string it authors is checked against
 *    [EntourageLanguage.forbiddenWords].
 * 3. **The Lab shows axes and ceilings, never its own weights.**
 *    [EntourageLabUi.feedback] cannot reach [LabWeights]; the puzzle constants
 *    are not measurements and the enum's KDoc says so.
 */

/* ── Tabs ───────────────────────────────────────────────────────────────── */

/**
 * The module's four sections.
 *
 * An enum rather than a bare string so a route argument cannot name a section
 * that does not exist: an unknown key falls back to [NETWORK] instead of
 * rendering a blank screen.
 */
enum class EntourageTab(val key: String, val labelEs: String) {
    NETWORK("network", "Red de sinergias"),
    BOOSTER("booster", "Impulsor"),
    LAB("lab", "Laboratorio"),
    QUIZ("quiz", "Trivia");

    companion object {
        fun fromKey(key: String?): EntourageTab =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: NETWORK
    }
}

/* ── T8.1: the synergy card ─────────────────────────────────────────────── */

/** The parts of a synergy card, so a test can address one without counting lines. */
enum class EntourageCardRole {
    OUTCOME, DESCRIPTION, MECHANISM, EVIDENCE, STRAINS, INTERACTION
}

/** One labelled paragraph of a synergy card. */
data class EntourageCardLine(val role: EntourageCardRole, val labelEs: String, val bodyEs: String)

/**
 * The synergy card as the screen renders it.
 *
 * [evidenceEs] is never blank once built: a synergy that shipped no evidence
 * text gets [NO_EVIDENCE_DECLARED] rather than a card with the line missing.
 * That substitution is the whole point — the line stays *visible*, which is
 * what makes the difference between "there is no human evidence" and silence.
 */
data class EntourageSynergyCard(
    val synergyId: String,
    val compoundsEs: String,
    val outcomeEs: String,
    val descriptionEs: String,
    val mechanismEs: String,
    val evidenceEs: String,
    val strainsEs: String,
    val interactionEs: String
) {
    /** True when the shipped content actually declared an evidence level. */
    val evidenceWasDeclared: Boolean get() = evidenceEs != NO_EVIDENCE_DECLARED

    /** The card in reading order, always including the evidence line. */
    val linesEs: List<EntourageCardLine> = buildList {
        // "Efecto" is a bare noun: it asserts that the body below it *is* an
        // effect. Five of the seven shipped headlines state as fact what their
        // own evidence line denies, and hedging all five would bury the card in
        // disclaimers. The label carries the honesty instead — the line names a
        // *described* effect, and the evidence line directly beneath it says
        // what that description rests on.
        add(EntourageCardLine(EntourageCardRole.OUTCOME, "Efecto descrito", outcomeEs))
        add(EntourageCardLine(EntourageCardRole.DESCRIPTION, "Qué se percibe", descriptionEs))
        add(EntourageCardLine(EntourageCardRole.MECHANISM, "Mecanismo", mechanismEs))
        add(EntourageCardLine(EntourageCardRole.EVIDENCE, "Evidencia", evidenceEs))
        if (strainsEs.isNotBlank()) {
            add(EntourageCardLine(EntourageCardRole.STRAINS, "Cepas típicas", strainsEs))
        }
        if (interactionEs.isNotBlank()) {
            add(EntourageCardLine(EntourageCardRole.INTERACTION, "Interacciones", interactionEs))
        }
    }

    /** Every word the card shows, for the language and completeness assertions. */
    val allTextEs: String
        get() = linesEs.joinToString(" ") { "${it.labelEs} ${it.bodyEs}" } + " " + compoundsEs

    companion object {
        /**
         * What a card says when the asset shipped no `evidence_es`.
         *
         * A named sentence rather than an empty line, because an empty line
         * reads as "nothing to add here" and a missing one reads as "no
         * evidence exists". Only the second is a claim this app can make.
         */
        const val NO_EVIDENCE_DECLARED =
            "El catálogo no declara el nivel de evidencia de esta sinergia."

        /** Shown when the shipped synergy has no text at all for a field. */
        const val NOT_DECLARED = "El catálogo no describe este punto."
    }
}

/** Builds the synergy cards. */
object EntourageCards {

    /**
     * The card for [synergy].
     *
     * The outcome, the description and the mechanism are defaulted rather than
     * skipped: a partially authored entry degrades to a card with a visible "not
     * described" line, which is honest, instead of a heading with nothing under
     * it, which reads as a bug.
     *
     * [EntourageSynergy.interactionEs] is the exception — it is *not*
     * defaulted. Its KDoc says an empty value means "there is none to declare",
     * so substituting a "the catalog does not describe this point" line would
     * put a warning on every synergy that has nothing to warn about. Blank stays
     * blank and the block is omitted.
     */
    fun cardFor(synergy: EntourageSynergy): EntourageSynergyCard = EntourageSynergyCard(
        synergyId = synergy.id,
        compoundsEs = compoundsEs(synergy),
        outcomeEs = synergy.outcomeEs.orDeclared(),
        descriptionEs = synergy.descriptionEs.orDeclared(),
        mechanismEs = synergy.mechanismEs.orDeclared(),
        evidenceEs = synergy.evidenceEs.takeIf { it.isNotBlank() }
            ?: EntourageSynergyCard.NO_EVIDENCE_DECLARED,
        strainsEs = synergy.strainsEs.filter { it.isNotBlank() }.joinToString(" · "),
        interactionEs = synergy.interactionEs
    )

    /** The combination heading, cannabinoid side first. */
    private fun compoundsEs(synergy: EntourageSynergy): String = buildString {
        append(synergy.cannabinoids.sortedBy { it.key }.joinToString(" + ") { it.labelEs })
        if (synergy.terpenes.isNotEmpty()) {
            append(" · ")
            append(synergy.terpenes.sortedBy { it.key }.joinToString(" + ") { it.labelEs })
        }
    }

    private fun String.orDeclared(): String =
        takeIf { it.isNotBlank() } ?: EntourageSynergyCard.NOT_DECLARED
}

/* ── T8.5: filtering by terpene ─────────────────────────────────────────── */

/**
 * The terpene filter, resolved through the domain layer.
 *
 * The terpene detail page knows a `terpenes.json` id; the module knows
 * [EntourageTerpene]s. [terpeneForCatalogId] is the join, and it goes through
 * [EntourageTerpene.catalogId] rather than a second hand-written mapping — the
 * two catalogs having separate literals for the same compound is how one of
 * them ends up wrong.
 */
object EntourageFilters {

    /** The module terpene whose encyclopedia entry is [catalogId], or null. */
    fun terpeneForCatalogId(catalogId: String): EntourageTerpene? =
        EntourageTerpene.entries.firstOrNull { it.catalogId == catalogId.trim().lowercase() }

    /** The module terpene a route argument names, or null when it names none. */
    fun terpeneForKey(key: String?): EntourageTerpene? =
        key?.takeIf { it.isNotBlank() }?.let { EntourageTerpene.fromKey(it) }

    /**
     * Every synergy whose terpene set contains [terpene].
     *
     * Sorted by id so the list does not depend on the asset's ordering, and
     * empty rather than "everything" when nothing matches: showing the whole
     * library under a filter the user applied is the same defect class as a
     * silently truncated one.
     */
    fun synergiesForTerpene(
        synergies: List<EntourageSynergy>,
        terpene: EntourageTerpene?
    ): List<EntourageSynergy> {
        if (terpene == null) return synergies.sortedBy { it.id }
        return synergies
            .filter { terpene in it.terpenes }
            .sortedBy { it.id }
    }

    /** The terpenes worth offering as a filter, in enum order for determinism. */
    fun filterableTerpenes(synergies: List<EntourageSynergy>): List<EntourageTerpene> =
        EntourageTerpene.entries.filter { candidate ->
            synergies.any { candidate in it.terpenes }
        }
}

/* ── T8.2: the booster frame ────────────────────────────────────────────── */

/**
 * How the booster presents an [EntouragePlan].
 *
 * The plan is already worded honestly; this type is what the screen draws, and
 * every string in [authoredTextEs] is one this file wrote rather than one the
 * asset shipped — that is the scope the language guard can honestly police.
 */
data class EntourageScoreFrame(
    val percent: Int,
    val profileKey: PharmacologicalProfile?,
    val profileLabelEs: String,
    val headingEs: String,
    val bandEs: String,
    val caveatEs: String,
    val gapLinesEs: List<String>,
    val offTargetLinesEs: List<String>,
    val missingCannabinoidLinesEs: List<String>,
    val interactionWarningsEs: List<String>,
    val guidanceEs: String,
    val compoundsCompared: Int,
    /** False when there is no number worth showing yet. */
    val reportable: Boolean
) {
    /**
     * The strings this file authored, for the language assertion.
     *
     * Deliberately excludes [guidanceEs] and [interactionWarningsEs]: those come
     * from the asset, and a guard that failed on shipped content would be a
     * guard the author cannot fix.
     */
    val authoredTextEs: String
        get() = listOf(headingEs, bandEs, caveatEs)
            .plus(gapLinesEs)
            .plus(offTargetLinesEs)
            .plus(missingCannabinoidLinesEs)
            .joinToString(" ")
}

/** The vapourisation tips for a selection. */
data class EntourageVapourReport(
    val rows: List<EntourageVapourRow>,
    val window: TerpeneWindow?,
    val windowEs: String,
    val contradictionEs: String,
    val missingEs: String
)

/** One terpene's temperature row, in Spanish. */
data class EntourageVapourRow(
    val terpeneLabelEs: String,
    val familyEs: String,
    val boilingEs: String,
    val windowEs: String,
    val noteEs: String
)

/**
 * Turns a plan into what the screen says.
 *
 * No scoring happens here. The percentage is [EntouragePlanner]'s, the gaps are
 * its [EntourageGap]s, and the only thing added is the framing — which is the
 * part that has to be right, because "35%" next to no context reads as a grade
 * and "35% de coincidencia con las proporciones de un perfil de referencia"
 * does not.
 */
object EntourageBooster {

    /** One standing caveat, shown with every score so the number cannot be read bare. */
    const val CAVEAT_ES =
        "Mide qué tan cerca están tus terpenos de las proporciones de este perfil. " +
            "No es una estimación de potencia ni de seguridad, y no predice qué vas a sentir."

    /** Shown while no reference profile is loaded, rather than a zero percent. */
    const val NO_REFERENCE_ES =
        "El catálogo no trae un perfil de referencia para este objetivo, así que no hay con qué comparar."

    /** The frame for [plan]. [profile] is the loaded row, or null. */
    fun frame(plan: EntouragePlan, profile: EntourageProfile?): EntourageScoreFrame {
        val label = profile?.labelEs ?: plan.profileKey?.labelEs.orEmpty()
        val reportable = plan.quality != EntourageMatchQuality.NO_SELECTION &&
            plan.quality != EntourageMatchQuality.NO_REFERENCE

        return EntourageScoreFrame(
            percent = plan.percent,
            profileKey = plan.profileKey,
            profileLabelEs = label,
            headingEs = headingEs(label),
            bandEs = bandEs(plan.quality),
            caveatEs = if (reportable) CAVEAT_ES else NO_REFERENCE_ES,
            gapLinesEs = plan.gaps.map { gap ->
                "El perfil pide ${sharePercent(gap.targetShare)}% de ${gap.labelEs}."
            },
            offTargetLinesEs = plan.offTarget.map { terpene ->
                "${terpene.labelEs} no aparece entre los terpenos de este perfil."
            },
            // Presence, not a contribution: the profiles ship cannabinoid
            // weights only to report one as missing, and the planner KDoc is
            // explicit that they are never summed into a score. So this line
            // names what the profile asks for and stops there.
            missingCannabinoidLinesEs = plan.missingCannabinoids.map { cannabinoid ->
                "Este perfil no incluye ${cannabinoid.labelEs} en sus compuestos."
            },
            interactionWarningsEs = plan.interactionWarningsEs,
            guidanceEs = plan.guidanceEs,
            compoundsCompared = plan.compoundsCompared,
            reportable = reportable
        )
    }

    private fun headingEs(profileLabelEs: String): String =
        if (profileLabelEs.isBlank()) "Coincidencia con un perfil objetivo"
        else "Coincidencia con las proporciones de $profileLabelEs"

    /**
     * The band, phrased as a distance.
     *
     * [EntourageMatchQuality.LEJANO] is "far from this one profile", not "your
     * product is bad", and the wording has to carry that: the same selection is
     * an excellent answer to a different target.
     */
    private fun bandEs(quality: EntourageMatchQuality): String = when (quality) {
        EntourageMatchQuality.AJUSTADO -> "Proporciones cercanas a las del perfil"
        EntourageMatchQuality.PARCIAL -> "Coincide en parte con el perfil"
        EntourageMatchQuality.LEJANO -> "Lejos de las proporciones de este perfil"
        EntourageMatchQuality.NO_SELECTION -> "Todavía no hay terpenos seleccionados"
        EntourageMatchQuality.NO_REFERENCE -> NO_REFERENCE_ES
    }

    /**
     * The temperature report for [selected].
     *
     * Driven by [EntouragePlanner.windowFor], so the contradiction case — a
     * selection that cannot be vaporised in one pass without losing a compound
     * — is reported rather than averaged into a number that reads as advice.
     */
    fun vapourReport(
        selected: Set<EntourageTerpene>,
        vaporisation: List<TerpeneVaporisation>
    ): EntourageVapourReport {
        val window = EntouragePlanner.windowFor(selected, vaporisation)
        val rows = selected
            .mapNotNull { terpene -> vaporisation.firstOrNull { it.terpene == terpene } }
            .sortedBy { it.boilingPointC }
            .map { row ->
                EntourageVapourRow(
                    terpeneLabelEs = row.terpene.labelEs,
                    familyEs = row.terpene.familyEs,
                    boilingEs = "${row.boilingPointC} °C",
                    windowEs = "${row.minTempC}–${row.maxTempC} °C",
                    noteEs = row.noteEs
                )
            }

        return EntourageVapourReport(
            rows = rows,
            window = window,
            windowEs = when {
                window == null -> ""
                window.isViable -> "Una sola pasada entre ${window.minTempC} °C y " +
                    "${window.maxTempC} °C mantiene los ${window.terpenes.size} compuestos elegidos."
                else -> ""
            },
            contradictionEs = if (window != null && !window.isViable) {
                "Esta selección no entra en una sola pasada: el mínimo más alto " +
                    "(${window.minTempC} °C) está por encima del máximo más bajo " +
                    "(${window.maxTempC} °C). Alguien tiene que sacrificeear un compuesto."
            } else "",
            missingEs = if (rows.isEmpty()) {
                "El catálogo no trae temperaturas de vaporización para esta selección."
            } else ""
        )
    }
}

/* ── The language guard ─────────────────────────────────────────────────── */

/**
 * Words this module's own copy must never use about a selection.
 *
 * ## Why a word list
 *
 * The planner's KDoc is explicit that a poor match is a *distance* and never a
 * judgement about the product, and that the guidance never says a selection is
 * bad, weak or unsafe. That is a promise about language, and a promise about
 * language is the kind of thing that decays silently: nobody adds "débil" to a
 * card on purpose, it arrives as a synonym in a rewrite. So the ban is a
 * constant next to the copy and a test asserts the produced strings are clean.
 *
 * Only strings authored in this file are guarded. Shipped asset text is
 * reported, not policed, because the author cannot fix it from here.
 */
object EntourageLanguage {

    /** Substrings, lower-cased, that must not appear in authored copy. */
    val forbiddenWords: List<String> = listOf(
        "malo", "mala", "males", "peor",
        "débil", "debil", "débiles", "debiles",
        "insegur", "peligros", "nociv",
        "daño", "dano", "tóxic", "toxic",
        "inútil", "inutil", "inútiles", "inutiles",
        "fatal", "no sirve", "no funciona", "no sirve para nada", "inaceptable"
    )

    /** The forbidden words present in [text], lower-cased. Empty when clean. */
    fun violations(text: String): List<String> {
        val haystack = text.lowercase()
        return forbiddenWords.filter { it in haystack }
    }
}

/* ── T8.3: the Lab ──────────────────────────────────────────────────────── */

/** One axis as the Lab reports it: a load against a ceiling. */
data class LabAxisFeedback(
    val axis: LabAxis,
    val labelEs: String,
    val loadPercent: Int,
    val ceilingPercent: Int,
    val crossed: Boolean
)

/** The Lab's verdict, as the screen shows it. */
data class LabFeedback(
    val verdict: LabVerdict,
    val verdictEs: String,
    val efficacyPercent: Int,
    val axes: List<LabAxisFeedback>,
    val notesEs: List<String>
) {
    /** Everything the Lab feedback shows, for the "no weights are displayed" assertion. */
    val allTextEs: String
        get() = buildString {
            append(verdictEs)
            axes.forEach {
                append(" ${it.labelEs}: ${it.loadPercent}% sobre un máximo de ${it.ceilingPercent}%")
            }
            notesEs.forEach { append(" $it") }
        }
}

/**
 * The interaction model of the Entourage Lab.
 *
 * Dials are continuous because a cannabinoid *share* is the only thing the
 * domain can express a ceiling over: [EntourageCase.maxCannabinoidShare] is a
 * fraction of the selection, so a plain on/off set could not represent "THC,
 * but only up to a fifth of the profile". Terpenes are toggles because
 * [EntouragePlanner] reads them as presence.
 */
object EntourageLabUi {

    /**
     * The selection a set of dials describes.
     *
     * The dials are normalised to sum to 1 across the compounds the user left
     * above zero, because a share is a fraction: three dials at 60% each is not
     * 180% of a profile, it is 60/20/20 of one. A dial at zero drops the
     * compound, so the share it implied disappears with it.
     */
    fun selectionFromDials(
        dials: Map<Cannabinoid, Float>,
        terpenes: Set<EntourageTerpene>
    ): EntourageSelection {
        val active = dials.filterValues { it > 0f }
        val total = active.values.sum()
        val shares = if (total <= 0f) {
            emptyMap()
        } else {
            active.mapValues { (_, value) -> (value / total).coerceIn(0f, 1f) }
        }
        return EntourageSelection(
            cannabinoids = active.keys,
            terpenes = terpenes,
            cannabinoidShares = shares
        )
    }

    /** A dial value the [androidx.compose.material3.Slider] can actually produce. */
    fun clampDial(value: Float): Float = value.coerceIn(0f, 1f)

    /**
     * The cannabinoids worth a dial on [case].
     *
     * Whatever the case constrains, plus the whole enum when it constrains
     * nothing: a case with no share ceilings and no forbidden compounds would
     * otherwise present an empty panel and look broken.
     */
    fun dialCannabinoids(case: EntourageCase): List<Cannabinoid> {
        val constrained =
            (case.maxCannabinoidShare.keys + case.forbiddenCannabinoids).toList()
        return if (constrained.isEmpty()) Cannabinoid.entries.toList()
        else constrained.sortedBy { it.key }
    }

    /** The compounds a case leaves unconstrained, so a dial can still be moved. */
    fun openCannabinoids(case: EntourageCase): List<Cannabinoid> =
        Cannabinoid.entries.filter { it !in dialCannabinoids(case) }

    /**
     * The terpenes to offer, goal profile first.
     *
     * The profile's own compounds lead because they are the ones the case is
     * scored against; the rest follow so the player is never boxed into the
     * reference mix.
     */
    fun terpeneOrder(profile: EntourageProfile?): List<EntourageTerpene> {
        val lead = profile?.terpeneShares?.keys.orEmpty().toList()
        return lead + EntourageTerpene.entries.filter { it !in lead }
    }

    /**
     * The feedback for a solved case.
     *
     * The verdict, the efficacy, the axes against their ceilings and the
     * shipped notes — and nothing else. [LabWeights] is deliberately not
     * reachable from here: those constants are puzzle numbers, and a slider
     * labelled "0.55 de carga" would be a dose-response curve the app cannot
     * support.
     */
    fun feedback(result: LabResult): LabFeedback = LabFeedback(
        verdict = result.verdict,
        verdictEs = verdictEs(result.verdict),
        efficacyPercent = result.efficacy,
        axes = result.readings.map { reading ->
            LabAxisFeedback(
                axis = reading.axis,
                labelEs = reading.labelEs,
                loadPercent = sharePercent(reading.load),
                ceilingPercent = sharePercent(reading.ceiling),
                crossed = reading.crosses
            )
        },
        notesEs = result.notesEs
    )

    /**
     * The verdict in words, phrased about the case.
     *
     * Every line names a constraint the case itself declares, so a line can
     * never be read as a verdict on the product.
     */
    fun verdictEs(verdict: LabVerdict): String = when (verdict) {
        LabVerdict.OPTIMO -> "Resuelve el caso sin cruzar ningún techo"
        LabVerdict.VIABLE -> "Ayuda al objetivo del caso y respeta sus techos"
        LabVerdict.RIESGO -> "Cruza al menos un techo del caso"
        LabVerdict.INEFICAZ -> "No cumple el objetivo del caso"
    }
}

/* ── T8.4: the quiz outcome and the rewards ─────────────────────────────── */

/**
 * One reward, as the `achievements` table stores it.
 *
 * The module has no XP ledger of its own: it writes rows into the existing
 * table, whose `xpReward` the app already sums into the player's total. A
 * second counter here would be a second source of truth for the same number,
 * and the two would drift.
 *
 * [nameEs] doubles as the idempotency key, because the table's primary key is
 * an auto-generated id and inserting the same reward twice would create two
 * rows. See [EntourageRewards.pending].
 */
data class EntourageReward(
    val nameEs: String,
    val descriptionEs: String,
    val icon: String,
    val xpReward: Int
)

/**
 * What the module pays, and when.
 *
 * All decisions here, none of them about layout: the Lab pays per case and
 * [EntourageRewards.pending] refuses to pay the same case twice, which is what
 * makes retrying a puzzle free.
 */
object EntourageRewards {

    /** XP for a case answered on the case's own terms, without crossing a ceiling. */
    const val LAB_VIABLE_XP = 40

    /** XP for a case solved outright, with no ceiling crossed. */
    const val LAB_OPTIMAL_XP = 60

    /** XP for a solve that crossed a ceiling: a result, but not a clean one. */
    const val LAB_RISK_XP = 20

    /**
     * The rewards a finished quiz run pays.
     *
     * Only the module's single achievement, and only when the run reached its
     * last round at the threshold — [EntourageAchievement.isEarned] owns that
     * rule, so the UI cannot grant the badge on an abandoned run.
     */
    fun forQuiz(score: Int, rounds: Int, finished: Boolean): List<EntourageReward> {
        val achievement = EntourageAchievement.ENTOURAGE_MASTER
        return if (EntourageAchievement.isEarned(score, rounds, finished)) {
            listOf(
                EntourageReward(
                    nameEs = achievement.labelEs,
                    descriptionEs = achievement.description,
                    icon = achievement.icon,
                    xpReward = achievement.xpReward
                )
            )
        } else {
            emptyList()
        }
    }

    /**
     * The rewards for one Lab verdict on [caseTitleEs].
     *
     * Empty for [LabVerdict.INEFICAZ]: a case the selection does not solve
     * pays nothing, so the XP means "a case was solved", not "a button was
     * pressed". A verdict that crossed a ceiling still pays a smaller amount,
     * because reading the feedback and trying again is the behaviour the module
     * is trying to teach.
     */
    fun forLabVerdict(
        caseId: String,
        caseTitleEs: String,
        verdict: LabVerdict
    ): List<EntourageReward> {
        val xp = when (verdict) {
            LabVerdict.OPTIMO -> LAB_OPTIMAL_XP
            LabVerdict.VIABLE -> LAB_VIABLE_XP
            LabVerdict.RIESGO -> LAB_RISK_XP
            LabVerdict.INEFICAZ -> 0
        }
        if (xp <= 0) return emptyList()
        return listOf(
            EntourageReward(
                nameEs = "Séquito: $caseTitleEs",
                descriptionEs = "Resolviste el caso \"$caseTitleEs\" ($caseId)",
                icon = "🧪",
                xpReward = xp
            )
        )
    }

    /**
     * The rewards that have not been paid yet.
     *
     * The `achievements` table keys on an auto-generated id, so a second insert
     * of the same reward is a new row rather than an update: the name is the
     * only stable handle, and it is the display name the module already owns.
     * Deduping here rather than in the DAO is what makes replaying a case free.
     */
    fun pending(rewards: List<EntourageReward>, awardedNames: Set<String>): List<EntourageReward> =
        rewards.filter { it.nameEs !in awardedNames }
}

/** The quiz's end state, as the screen shows it. */
data class EntourageQuizOutcome(
    val score: Int,
    val rounds: Int,
    val finished: Boolean,
    val correctPercent: Int,
    val headlineEs: String,
    val rewards: List<EntourageReward>,
    val unlockedBadge: Boolean
)

/** Turns quiz state into what the screen says and what it pays. */
object EntourageQuizUi {

    /**
     * The outcome of the current [state].
     *
     * `finished` is read from the state rather than from "a score exists": an
     * abandoned run at round ten has a score and is not a completed run, and
     * [EntourageAchievement.isEarned] is the thing that has to agree.
     */
    fun outcomeOf(state: EntourageQuizState): EntourageQuizOutcome {
        val (score, rounds, finished) = when (state) {
            is EntourageQuizState.Unavailable -> Triple(0, 0, false)
            is EntourageQuizState.Asking -> Triple(state.score, state.totalRounds, false)
            is EntourageQuizState.Revealed -> Triple(state.score, state.totalRounds, false)
            is EntourageQuizState.Finished -> Triple(state.score, state.totalRounds, true)
        }
        val rewards = EntourageRewards.forQuiz(score, rounds, finished)
        val percent = if (rounds <= 0) 0 else ((score.toFloat() / rounds) * 100).roundToInt()
        return EntourageQuizOutcome(
            score = score,
            rounds = rounds,
            finished = finished,
            correctPercent = percent,
            headlineEs = headlineEs(score, rounds, finished),
            rewards = rewards,
            unlockedBadge = rewards.isNotEmpty()
        )
    }

    private fun headlineEs(score: Int, rounds: Int, finished: Boolean): String = when {
        !finished && rounds <= 0 -> "No hay preguntas de Séquito cargadas."
        !finished -> "$score de $rounds respondidas. La trivia no termina hasta la última."
        score >= EntourageAchievement.thresholdFor(rounds) ->
            "$score de $rounds correctas. Lograste el sello de Maestro del Efecto Séquito."
        else -> "$score de $rounds correctas. El sello pide " +
            "${EntourageAchievement.thresholdFor(rounds)} aciertos."
    }
}

/* ── Data integrity ─────────────────────────────────────────────────────── */

/**
 * A notice the module owes the user about its own content.
 *
 * A silently truncated encyclopedia is the same defect class as an invisible
 * orphan row: the app looks complete and is not. [EntourageIntegrity.noticeFor]
 * returns non-null exactly when there is something to admit.
 */
data class EntourageIntegrityNotice(
    val count: Int,
    val headlineEs: String,
    val detailEs: String
)

/** The module's own completeness reporting, and its disclaimer. */
object EntourageIntegrity {

    /**
     * What to admit, or null when the content resolved cleanly.
     *
     * @param unresolvedReferences the repository's dropped-and-recorded keys.
     *   The list is the honest half of the parse: a key the enums do not know is
     *   dropped rather than coerced to a default, so the count is the number of
     *   entries the user will never see.
     */
    fun noticeFor(unresolvedReferences: List<String>): EntourageIntegrityNotice? {
        if (unresolvedReferences.isEmpty()) return null
        return EntourageIntegrityNotice(
            count = unresolvedReferences.size,
            headlineEs = "Contenido incompleto: ${unresolvedReferences.size} " +
                "referencias del catálogo no se pudieron resolver y no se muestran.",
            detailEs = unresolvedReferences.joinToString("\n")
        )
    }

    /**
     * The module disclaimer, or an honest stand-in.
     *
     * Blank is not an option: a screen that shows no disclaimer reads as
     * though none were written, which is a claim this app cannot make.
     */
    fun disclaimerFor(disclaimerEs: String): String =
        disclaimerEs.takeIf { it.isNotBlank() }
            ?: "El módulo no trae un aviso de cierre en esta versión. " +
            "Nada de lo que muestra es consejo médico."
}
