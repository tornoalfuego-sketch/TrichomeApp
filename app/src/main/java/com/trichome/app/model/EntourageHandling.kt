package com.trichome.app.model

/**
 * F5 — the second scoring mode of the Entourage Lab.
 *
 * ## Why a second mode exists at all
 *
 * The Lab had one question — *how much cannabinoid, and inside which side-effect
 * ceiling* — and that question only has an answer for material that has already
 * been made. Someone standing over a harvest has a different decision in front of
 * them, and it is not a pharmacological one: **what happens to the volatile
 * fraction between the cut and the pen**. Nothing in the pharmacological model
 * could answer it, because it has no cannabinoid profile to look at.
 *
 * So the second mode is added rather than bending the first, and [LabMode] makes
 * the branch explicit in the model instead of guessing from which fields happen to
 * be populated.
 *
 * ## The two modes cannot produce the same verdict for the same input
 *
 * That property is **structural**, not incidental:
 *
 *  - a handling case carries no cannabinoid anywhere. [EntourageLab.solve] never
 *    reads a dial, a share or a ceiling on this branch, and there are no
 *    [LabAxis] readings, so nothing here *can* be a potency.
 *  - [LabVerdict.RIESGO] is **unreachable** on this branch. In the pharmacological
 *    mode it exists because a selection can work on the goal *and* cross a
 *    ceiling, and that tension needs a patient to have a ceiling. A handling case
 *    has no patient, so the only outcomes left are coverage: all of the material,
 *    part of it, or none.
 *
 * `EntourageLabTest.theTwoScoringModesCannotProduceTheSameVerdictForTheSameInput`
 * asserts both halves against the shipped asset.
 *
 * ## Nothing here is a schedule
 *
 * F4 refused a temperature, a duration and a quantity, and this file inherits the
 * refusal whole. [HandlingGuides] holds **directions** and, for each one, the
 * shipped compounds the sentence comes from. There is no degree sign, no minutes
 * and no grams in any string this file produces, which is asserted over the
 * object's own copy rather than merely observed by absence.
 *
 * ## The two compound sets are declared, and a test re-derives them
 *
 * [HandlingCompounds.VOLATILE_FRACTION] and
 * [HandlingCompounds.RETAINED_IN_SEPARATION] are hand-declared constants, which is
 * the weakest kind of data in this repository and has to be said so. The
 * alternative — deriving the class from a boiling-point threshold — was tried and
 * does not survive F4's own shipped copy: humulene boils at 166 °C, inside the
 * monoterpene band, yet its shipped solvent note says it "aguanta más que los
 * monoterpenos", so a temperature cut would put it in the wrong class and
 * contradict a note already on screen.
 *
 * So the sets are declared from the shipped notes, and
 * `EntourageHandlingTest.theDeclaredCompoundSetsAgreeWithTheShippedProcessingNotes`
 * re-derives both from `entourage_data.json` and fails if a note is ever edited
 * underneath them. A declaration with a drift detector is a defensible kind of
 * data; a declaration without one is a rumour.
 */

/* ── The mode ────────────────────────────────────────────────────────────── */

/**
 * Which question the Lab is asking.
 *
 * An enum rather than a heuristic, for the reason [VolatilityProvenance] is: a
 * `when` over it is exhaustive, so a third mode becomes a compile break at every
 * renderer rather than a screen that quietly keeps scoring the old way.
 */
enum class LabMode(val key: String, val labelEs: String) {
    /**
     * A clinical case: cannabinoid shares inside declared side-effect ceilings.
     *
     * The original mode, unchanged. It has its own efficacy (profile distance),
     * its own constraints and its own four verdicts.
     */
    PHARMACOLOGICAL("pharmacological", "Caso clínico"),

    /**
     * A post-harvest handling decision: which route keeps what the case says it
     * has to keep.
     */
    HANDLING("handling", "Decisión de procesado");

    companion object {
        /** The asset's `mode` key, or null when it is not one of these. */
        fun fromKey(key: String): LabMode? =
            entries.firstOrNull { it.key == key.trim().lowercase() }
    }
}

/* ── What a handling case is trying to keep ──────────────────────────────── */

/**
 * The two ends of the one trade-off F4's comparison documents.
 *
 * Two and not more, deliberately: F4's shipped `COMPARISON_ES` says the three
 * routes "se diferencian en una sola cosa que importa aquí: qué le ocurre a la
 * fracción volátil del perfil", and the two directions on either side of that
 * sentence are all this repository can support. A third goal would be a claim
 * nothing here establishes.
 */
enum class HandlingGoal(val key: String, val labelEs: String) {
    /**
     * Keep the part of the profile that leaves first.
     *
     * Scored over the **volatile** members of the selection: a material with no
     * volatile compound is not the material this goal is about, and the module
     * says so rather than quietly scoring it.
     */
    VOLATILE_FRACTION(
        key = "VOLATILE_FRACTION",
        labelEs = "Conservar la fracción volátil del perfil"
    ),

    /**
     * Keep everything, the compounds that survive the separation step included.
     *
     * Scored over the **whole** selection, so it is the goal under which the
     * material itself changes the verdict: a solvent route keeps the heavy
     * compounds and loses the volatile ones, and this goal is the one that
     * notices.
     */
    WHOLE_PROFILE(
        key = "WHOLE_PROFILE",
        labelEs = "Conservar el perfil entero, compuestos pesados incluidos"
    );

    companion object {
        /**
         * The asset's key, or null when it is not one of these.
         *
         * Null rather than a default, so a typo in `entourage_data.json` drops
         * the case and names the key in `unresolvedReferences` instead of
         * scoring a harvest against a goal the asset never declared.
         */
        fun fromKey(key: String): HandlingGoal? =
            entries.firstOrNull { it.key == key.trim().uppercase() }
    }
}

/* ── How each compound behaves ───────────────────────────────────────────── */

/**
 * What a route does to one compound, in the vocabulary the shipped notes use.
 *
 * Three words because the shipped notes use three: a compound either stays, goes
 * off, or stays as something else. [DEGRADED] is the one that matters most and the
 * one F4 wrote most carefully — "lo que se queda ya no es el limoneno de la planta
 * sino parte de su óxido" is a claim about **identity**, not about amount.
 */
enum class HandlingOutcome(val key: String, val labelEs: String) {
    /** The route keeps it: what leaves the process is the plant's compound. */
    KEEPS("KEEPS", "Se conserva tal cual"),

    /** It leaves during the step the route adds, in good part. */
    LOSES("LOSES", "Se va en buena parte durante la separación"),

    /**
     * What remains is an oxide or another oxidation product, not the compound.
     *
     * Distinct from [LOSES] because the two are different failures: one is a
     * quantity, the other is a substitution.
     */
    DEGRADED("DEGRADED", "Lo que queda ya no es el compuesto de la planta")
}

/**
 * What one route does to one class of compound.
 *
 * Every cell carries [whatEs] (the direction) and [basisEs] (the shipped
 * compounds it comes from, and what the evidence cannot fix), both always
 * rendered — the same rule [ProcessingMethodGuide] follows, and for the same
 * reason: a direction with no basis behind it is a bare instruction.
 */
data class HandlingGuide(
    val route: ProcessingMethod,
    val outcome: HandlingOutcome,
    /** Always visible. The direction, in words. Never a figure. */
    val whatEs: String,
    /** Always visible. Which shipped notes say it, and what they cannot fix. */
    val basisEs: String
)

/**
 * What each route does to each class of compound.
 *
 * ## Every cell restates a shipped note
 *
 * The constants carry no new claim. Read against the `processing` block of
 * `entourage_data.json`, which is where each sentence comes from:
 *
 * | route | volatile class | retained class |
 * | --- | --- | --- |
 * | `LIVE_ROSIN` | limoneno: "es la ruta donde menos se oxida y menos se va"; ocimeno and terpinoleno: "es la ruta donde más conserva su firma"; alfa-pineno: "es la ruta donde menos se pierde, porque no tiene evaporación posterior" | cariofileno: "es el compuesto que más conserva de los tres métodos"; linalool: "es de los que más conserva de los tres métodos"; humuleno: "conserva más que los monoterpenos de la lista" |
 * | `SOLVENT_EXTRACTION` | the seven volatile notes all say the compound "sale entero en el extracto y se va en buena parte en la etapa de separación" | cariofileno and linalool: "es de los últimos en irse en la etapa de separación"; humuleno: "aguanta más que los monoterpenos en la etapa de separación, aunque menos que el cariofileno beta" |
 * | `DECARBOXYLATION` | alfa-pineno: "es el primer compuesto que se va de la lista"; limoneno: "es el primero en irse de la lista"; camfeno, ocimeno, mirceno, beta-pineno and terpinoleno: "de los primeros en irse… lo que se queda ya no es el compuesto de la planta sino parte de sus productos de oxidación" | cariofileno: "con temperatura alta también termina degradándose, y entonces lo que se encuentra es su óxido y otros productos de la misma familia" (MIXTO); humuleno: "lo que se queda, como en el otro sesquiterpeno, incluye su óxido"; linalool: "también termina degradándose" |
 *
 * ## What the table deliberately does not say
 *
 * No amount, no yield, no time and no temperature — F4's three refusals,
 * inherited. A cell says which way a compound moves, never how far.
 */
object HandlingGuides {

    /**
     * Every guide.
     *
     * A flat list rather than a nested map so a screen cannot render one cell and
     * skip its basis; the lookup below is a view over it, not a second source.
     */
    val all: List<HandlingGuide> = listOf(
        HandlingGuide(
            route = ProcessingMethod.LIVE_ROSIN,
            outcome = HandlingOutcome.KEEPS,
            whatEs =
                "El prensado en vivo es la ruta en la que menos se va el compuesto y " +
                    "menos se oxida, porque no tiene ninguna etapa posterior de " +
                    "evaporación que lo exponga al aire. Es la vía que más se le " +
                    "parece al aroma de la planta recién cortada, y también la que " +
                    "conserva más a los compuestos menos volátiles de la lista.",
            basisEs =
                "Las diez notas de prensado en vivo del bloque de procesado dicen " +
                    "esta misma dirección, y las diez están bien documentadas. Lo que " +
                    "no existe es una comparación controlada, con el mismo material de " +
                    "partida y el mismo perfil medido, entre prensado en vivo y " +
                    "extracción con disolvente: aquí se afirma la dirección, no la " +
                    "magnitud."
        ),
        HandlingGuide(
            route = ProcessingMethod.SOLVENT_EXTRACTION,
            outcome = HandlingOutcome.LOSES,
            whatEs =
                "El disolvente disuelve la resina y arrastra también los terpenos " +
                    "más volátiles, y por eso recupera más material total. A cambio, " +
                    "el proceso tiene una etapa posterior —separar el disolvente del " +
                    "extracto— y en esa etapa la fracción volátil se va en buena " +
                    "parte: el compuesto sale entero en el extracto y se va al " +
                    "separarlo.",
            basisEs =
                "Las dos direcciones están bien descritas en la literatura de " +
                    "procesado de productos vegetales: una extracción con disolvente " +
                    "recupera más material total, y la etapa de separación es la que " +
                    "se lleva la fracción más volátil. No hay comparación controlada " +
                    "entre métodos ni entre disolventes concretos, así que la " +
                    "magnitud de esa pérdida no la puede fijar este catálogo."
        ),
        HandlingGuide(
            route = ProcessingMethod.SOLVENT_EXTRACTION,
            outcome = HandlingOutcome.KEEPS,
            whatEs =
                "Los compuestos menos volátiles de la biblioteca salen enteros en el " +
                    "extracto y son de los últimos en irse en la etapa de separación " +
                    "del disolvente. El cariofileno beta es el último de toda la " +
                    "lista; el linalool llega después que todos los demás " +
                    "monoterpenos; el humuleno aguanta más que ellos, aunque menos " +
                    "que el cariofileno beta.",
            basisEs =
                "Las tres notas de esta clase dicen lo mismo que se afirma aquí, y " +
                    "las tres están bien documentadas. No hay comparación controlada " +
                    "entre métodos, así que la magnitud de la ventaja de cada ruta " +
                    "sobre el material completo no la puede fijar este catálogo."
        ),
        HandlingGuide(
            route = ProcessingMethod.DECARBOXYLATION,
            outcome = HandlingOutcome.DEGRADED,
            whatEs =
                "La descarboxilación no es una tercera ruta de extracción: actúa " +
                    "sobre el lado de los ácidos, y su efecto sobre el perfil " +
                    "terpénico es el efecto secundario del calor que la acompaña. Al " +
                    "calentar el material, lo primero que se va es la fracción " +
                    "volátil, y lo que se queda deja de ser el compuesto de la " +
                    "planta para ser parte de sus productos de oxidación.",
            basisEs =
                "Que calentar el material expulsa terpenos y que los ácidos se " +
                    "descarboxilan están ambos bien documentados, y los dos " +
                    "compuestos que resisten más el calor del catálogo siguen " +
                    "terminando degradados a su óxido. Lo que no está fijado es cuánto " +
                    "dura ninguno de esos procesos ni qué productos forman, porque " +
                    "dependen del material y del equipo: por eso aquí no hay ninguna " +
                    "temperatura ni ningún tiempo."
        )
    )

    /**
     * What [route] does to a compound of the given class.
     *
     * Total by construction: every `(route, class)` pair the two enums can produce
     * lands in a cell, so a new member on either side is a compile break here
     * rather than a route that silently keeps an old answer.
     */
    fun outcomeOf(route: ProcessingMethod, isVolatileFraction: Boolean): HandlingOutcome = when (route) {
        ProcessingMethod.LIVE_ROSIN -> HandlingOutcome.KEEPS
        ProcessingMethod.SOLVENT_EXTRACTION ->
            if (isVolatileFraction) HandlingOutcome.LOSES else HandlingOutcome.KEEPS
        ProcessingMethod.DECARBOXYLATION -> HandlingOutcome.DEGRADED
    }

    /** The guide whose cell `route` / [isVolatileFraction] lands in. */
    fun guideFor(route: ProcessingMethod, isVolatileFraction: Boolean): HandlingGuide =
        all.first {
            it.route == route && it.outcome == outcomeOf(route, isVolatileFraction)
        }
}

/**
 * The two classes of compound, as the shipped processing notes divide them.
 *
 * Declared rather than derived, and the reason is in the file KDoc: no boiling
 * point separates humulene (166 °C) from the monoterpenes its own note says it
 * beats. [VOLATILE_FRACTION] is the set whose shipped `SOLVENT_EXTRACTION` note
 * says the compound leaves during the separation; [RETAINED_IN_SEPARATION] is the
 * set whose note says it does not. Every one of the ten module compounds is in
 * exactly one, which a test asserts.
 */
object HandlingCompounds {

    /**
     * The compounds whose shipped solvent note says they go during the separation.
     *
     * Seven of the ten. Named rather than computed because F4's shipped sentences
     * are the source.
     */
    val VOLATILE_FRACTION: Set<EntourageTerpene> = setOf(
        EntourageTerpene.LIMONENE,
        EntourageTerpene.ALPHA_PINENE,
        EntourageTerpene.BETA_PINENE,
        EntourageTerpene.CAMPHENE,
        EntourageTerpene.OCIMENE,
        EntourageTerpene.TERPINOLENE,
        EntourageTerpene.MYRCENE
    )

    /**
     * The compounds whose shipped solvent note says they hold up in that step.
     *
     * Three: the two whose note puts them among the last to go, and humulene,
     * whose note says it beats the monoterpenes without claiming it beats
     * caryophyllene.
     */
    val RETAINED_IN_SEPARATION: Set<EntourageTerpene> = setOf(
        EntourageTerpene.BETA_CARYOPHYLLENE,
        EntourageTerpene.LINALOOL,
        EntourageTerpene.HUMULENE
    )

    /** Every compound this mode knows how to score. */
    val known: Set<EntourageTerpene> get() = VOLATILE_FRACTION + RETAINED_IN_SEPARATION

    /**
     * Whether [terpene] leaves during a solvent separation.
     *
     * Null rather than `false` when the module documents no processed note for it:
     * an unknown compound is not a heavy one, and defaulting it to "retained"
     * would score it as the best-behaved compound in the library.
     */
    fun isVolatileFraction(terpene: EntourageTerpene): Boolean? = when (terpene) {
        in VOLATILE_FRACTION -> true
        in RETAINED_IN_SEPARATION -> false
        else -> null
    }
}

/* ── One compound's reading ──────────────────────────────────────────────── */

/** One compound as the handling mode reports it. */
data class HandlingReading(
    val terpene: EntourageTerpene,
    val labelEs: String,
    val outcome: HandlingOutcome,
    /** Whether this route keeps this compound. */
    val keeps: Boolean,
    /** Whether this compound belongs to the volatile fraction. */
    val isVolatileFraction: Boolean
)

/* ── The decision ────────────────────────────────────────────────────────── */

/**
 * The post-harvest scoring rule.
 *
 * ```
 *   route absent, or selection empty             -> INEFICAZ
 *   route ruled out by the case                   -> INEFICAZ
 *   a selected compound with no shipped note      -> INEFICAZ
 *   the route serves the case's goal              -> OPTIMO
 *   the route keeps part of the selection         -> VIABLE
 *   otherwise                                     -> INEFICAZ
 * ```
 *
 * ## Why [LabVerdict.RIESGO] is unreachable here
 *
 * It is the structural difference between the two modes, and a fact about the
 * model rather than a policy. RIESGO exists in the pharmacological mode because a
 * selection can work on the goal *and* cross a ceiling; that tension needs a
 * patient to have a ceiling. A handling case has no patient, no cannabinoid and no
 * ceiling, so the outcomes left are coverage: all of the material, part of it, or
 * none. Manufacturing a fourth outcome here would put a verdict on screen with
 * nothing behind it.
 *
 * ## Both goals are load-bearing, over the same selection
 *
 * [HandlingGoal.VOLATILE_FRACTION] is scored over the volatile members only, so a
 * selection of nothing but heavy compounds cannot satisfy it. [HandlingGoal
 * .WHOLE_PROFILE] is scored over the whole selection, so it is the goal under which
 * the material itself changes the verdict. The same route and the same compounds
 * therefore reach different verdicts under the two goals, which is the property
 * that makes them two questions rather than one question asked twice.
 *
 * ## What the coverage number is, and is not
 *
 * [coveragePercent] is the share of the **selection** the route keeps. It is not a
 * yield, not a potency and not an estimate of the finished product: it says how
 * much of what went in is still the plant's compound afterwards.
 */
object EntourageHandling {

    /**
     * Scores [selection] under [route] as an answer to [case].
     *
     * The pharmacological mode's inputs are absent from the signature on purpose:
     * there is no cannabinoid here, so nothing on this path *can* be a potency.
     */
    fun solve(
        case: EntourageCase,
        route: ProcessingMethod?,
        selection: EntourageSelection
    ): LabResult {
        fun unscored(note: String) = LabResult(
            caseId = case.id,
            mode = LabMode.HANDLING,
            verdict = LabVerdict.INEFICAZ,
            efficacy = 0,
            readings = emptyList(),
            notesEs = listOf(note),
            handlingReadings = emptyList()
        )

        val goal = case.handlingGoal
            ?: return unscored("Este caso no declara qué tiene que conservar.")

        val chosenRoute = route
            ?: return unscored("El caso pide elegir una ruta de procesado antes de evaluarlo.")

        val terpenes = selection.terpenes.sortedBy { it.key }
        if (terpenes.isEmpty()) {
            return unscored("Sin compuestos seleccionados el caso no se puede evaluar.")
        }

        if (chosenRoute in case.handlingForbiddenRoutes) {
            return unscored(
                "El caso descarta ${chosenRoute.labelEs}: la dirección documentada de " +
                    "esa ruta va en contra de lo que el caso pide conservar."
            )
        }

        val undocumented = terpenes.filter { HandlingCompounds.isVolatileFraction(it) == null }
        if (undocumented.isNotEmpty()) {
            return unscored(
                "El catálogo no documenta el procesado de " +
                    undocumented.joinToString(", ") { it.labelEs } +
                    ", así que esa ruta no puede puntuarse para este caso."
            )
        }

        val readings = terpenes.map { terpene ->
            val volatile = HandlingCompounds.isVolatileFraction(terpene) == true
            HandlingReading(
                terpene = terpene,
                labelEs = terpene.labelEs,
                outcome = HandlingGuides.outcomeOf(chosenRoute, volatile),
                keeps = HandlingGuides.outcomeOf(chosenRoute, volatile) == HandlingOutcome.KEEPS,
                isVolatileFraction = volatile
            )
        }

        val notes = mutableListOf<String>()
        val lost = readings.filter { !it.keeps }
        lost.forEach { reading ->
            val guide = HandlingGuides.guideFor(chosenRoute, !reading.isVolatileFraction)
            notes += "${reading.labelEs} · ${reading.outcome.labelEs}. ${guide.whatEs}"
        }

        // ## Why the goal narrows the scope rather than decorating the verdict
        //
        // The first version of this scored `coverage` over the whole selection
        // and let the goal add a note. That was wrong, and the defect was only
        // visible because a test asked whether the two goals could disagree:
        // they could not. A solvent route over a mixed material keeps the heavy
        // compound and loses the volatile one, which under both goals came out
        // VIABLE, so [HandlingGoal] was a label rather than a question.
        //
        // The goal now decides **which compounds count**. Under
        // [HandlingGoal.VOLATILE_FRACTION] the volatile members are the whole
        // question, so losing all of them fails the case outright; under
        // [HandlingGoal.WHOLE_PROFILE] every member counts, so the same route
        // with the same material is a partial success. Same input, two questions,
        // two answers.
        val inScope = readings.filter {
            when (goal) {
                HandlingGoal.VOLATILE_FRACTION -> it.isVolatileFraction
                HandlingGoal.WHOLE_PROFILE -> true
            }
        }
        val keptInScope = inScope.count { it.keeps }
        val servesGoal = inScope.isNotEmpty() && keptInScope == inScope.size

        if (lost.isEmpty()) {
            notes += EntourageLabCopy.routeKeepsAll(chosenRoute.labelEs, readings.size)
        }
        if (!servesGoal) {
            notes += if (inScope.isEmpty()) {
                EntourageLabCopy.goalHasNoSubject(goal)
            } else {
                EntourageLabCopy.goalNotMet(goal)
            }
        }

        val verdict = when {
            servesGoal -> LabVerdict.OPTIMO
            keptInScope > 0 -> LabVerdict.VIABLE
            else -> LabVerdict.INEFICAZ
        }

        return LabResult(
            caseId = case.id,
            mode = LabMode.HANDLING,
            verdict = verdict,
            efficacy = 0,
            readings = emptyList(),
            notesEs = notes,
            handlingReadings = readings
        )
    }

    /** The share of [readings] the route keeps, 0..100. Never a yield. */
    fun coveragePercent(readings: List<HandlingReading>): Int =
        if (readings.isEmpty()) 0 else (readings.count { it.keeps } * 100) / readings.size

    /**
     * The Spanish the Lab section prints, as data.
     *
     * It exists for the same reason `TerpeneProcessingCopy` and
     * `ClimateCardCopyFormatter` do: a sentence authored inside a composable
     * cannot be held to [EntourageLanguage] by any test on this classpath, and
     * F3 found three that way. Every string the Lab screen shows — including the
     * ones that shipped before this phase — is built here, which is what lets the
     * structural test assert the strong form: **no non-blank string literal in the
     * composable at all**.
     */
    object EntourageLabCopy {

        /** The heading above the case picker. */
        const val CASES_ES: String = "Caso"

        /** Shown when the asset resolved no cases at all. */
        const val NO_CASES_ES: String = "El catálogo no trae casos para el Laboratorio."

        /** The heading above the pharmacological share and side-effect ceilings. */
        const val FORBIDDEN_COMPOUNDS_ES: String = "Compuestos que el caso descarta"

        /** The heading above the pharmacological cannabinoid share ceilings. */
        const val SHARE_CEILINGS_ES: String = "Límites de aporte"

        /** The heading above the pharmacological patient ceilings. */
        const val PATIENT_CEILINGS_ES: String = "Techos del paciente"

        /** The heading above the routes a handling case rules out. */
        const val FORBIDDEN_ROUTES_ES: String = "Rutas que el caso descarta"

        /** The heading above the handling case's evidence level and limit. */
        const val EVIDENCE_ES: String = "Base de la decisión"

        /** The heading above the per-compound handling readings. */
        const val COMPOUND_READINGS_ES: String = "Compuestos y ruta"

        /** The heading above the axis readings. */
        const val SIDE_EFFECTS_ES: String = "Efectos secundarios"

        /** The heading above the case's own explanation. */
        const val WHY_ES: String = "Por qué el caso es así"

        /** The forbidden-compound warning under a cannabinoid dial. */
        const val FORBIDDEN_DIAL_ES: String =
            "Este caso descarta %s: cualquier aporte da por perdido el caso."

        /** `"THC hasta 20%"` for the pharmacological share ceilings. */
        fun shareCeilingEs(labelEs: String, maxPercent: Int): String =
            "$labelEs hasta $maxPercent%"

        /** `"Ansiedad hasta 30%"` for the pharmacological patient ceilings. */
        fun patientCeilingEs(labelEs: String, maxPercent: Int): String =
            "$labelEs hasta $maxPercent%"

        /** `"${(value * 100).toInt()}% · máx 20%"` for one dial's readout. */
        fun dialReadoutEs(percent: Int, maxPercent: Int?): String =
            if (maxPercent == null) "$percent%" else "$percent% · máx $maxPercent%"

        /** `"${index + 1}. ${title}"` for the case picker's chips. */
        fun caseChipEs(position: Int, titleEs: String): String = "$position. $titleEs"

        /** The positive note when nothing in the selection is lost. */
        fun routeKeepsAll(routeLabelEs: String, compoundCount: Int): String =
            "$routeLabelEs conserva los $compoundCount compuestos del caso."

        /** The note when the goal is not met. */
        fun goalNotMet(goal: HandlingGoal): String =
            "El objetivo del caso es ${goal.labelEs.lowercase()}, y esa ruta no lo " +
                "cumple para el material seleccionado."

        /** The note when the selection holds nothing the goal is about. */
        fun goalHasNoSubject(goal: HandlingGoal): String =
            "El objetivo del caso es ${goal.labelEs.lowercase()}, y el material " +
                "seleccionado no contiene ningún compuesto de esa clase."

        /**
         * The separator the Lab joins its constraint lists with.
         *
         * Here rather than inline because the structural test asserts the strong
         * form — **no non-blank string literal in the composable at all** — and a
         * separator is exactly the kind of literal an exception clause would grow
         * to cover. One constant is cheaper than the exception.
         */
        const val JOINER_ES: String = " · "

        /** `"30% / 40%"` for one axis against its ceiling. */
        fun axisReadingEs(loadPercent: Int, ceilingPercent: Int): String =
            "$loadPercent% / $ceilingPercent%"

        /** The word beside an axis whose load is under its ceiling. */
        const val AXIS_WITHIN_ES: String = "dentro"

        /** The word beside an axis whose load is over its ceiling. */
        const val AXIS_OVER_ES: String = "supera"

        /** Joins a case's constraint names into the one line the section prints. */
        fun joinNamesEs(labels: List<String>): String = labels.joinToString(JOINER_ES)
    }
}
