package com.trichome.app.model

import java.util.Locale

/**
 * F4 — the engineering / processing dimension of the Séquito module.
 *
 * ## What this file is for
 *
 * F1 made the module agree with itself, F2 turned a boiling point into a curve,
 * F3 described the plant. F4 describes **what the user does to the material they
 * have already grown**: which extraction route, what the heat does, and what the
 * profile does between the harvest and the pen.
 *
 * Three things live here, and it is deliberately three rather than one:
 *
 * 1. [EntourageProcessing] — what a **named method** does to **one compound**.
 *    Keyed by terpene, for exactly the reason decision D-B gave (see below).
 * 2. [ProcessingGuides] — how each method and each preservation factor works,
 *    identical for every compound. This is where the **safety framing** lives.
 * 3. [TerpeneProcessingCopy] — the Spanish wording both surfaces render.
 *
 * ## Why keyed by terpene (decision D-B, restated)
 *
 * F3 declined to key agronomy on the cannabinoid x terpene pair, because a
 * pair-keyed entry writes the same advice twice and exists only if a synergy
 * happens to exist. F4 has the same two failure modes and takes the same
 * decision: keyed by [EntourageTerpene], the note is written once and is
 * reachable both from the synergy card and from the compound's own page.
 *
 * ## The rule this file is written around
 *
 * **Nothing in this block may be read as an instruction to process material.**
 *
 * F1 refused to state a potency percentage and F2 refused to state a
 * vaporiser setpoint. F4 refuses the same way, for the same reason, and the
 * stakes are higher: this is the only dimension of the module where a wrong
 * number is not a wrong opinion but ruined material. So:
 *
 * - **No temperature.** The direction of the heat effect is stated; a schedule
 *   is not. `DECARBOXYLATION`'s own basis sentence says so on screen, in
 *   Spanish, where the reader is.
 * - **No duration.** Same argument for time.
 * - **No yield and no quantity, not even as a range.** A method recovering
 *   "more material" is a documented *direction* and is stated as one; a figure
 *   is not, anywhere in this block.
 *
 * `TerpeneProcessingTest.noProcessingNumberReadsAsAnInstruction` pins all three
 * by scanning the shipped text, so a future edit cannot reintroduce one.
 *
 * ## Solvent safety is part of the block, not a footnote
 *
 * [ProcessingMethodGuide.safetyEs] is a required constructor argument with no
 * default, and [ProcessingGuides] refuses to build a guide whose safety line is
 * blank. There is therefore **no path** through this model that produces a named
 * extraction method without its safety framing — including from a malformed
 * asset, because the safety line lives in `model/` and not in the asset.
 *
 * That is the difference between this dimension and F1's disclaimer: a
 * disclaimer can be scrolled past, and a level statement is a statement about
 * evidence. Residual solvent is a fact about the method, so it travels with the
 * method, in the same rendered block, on both surfaces.
 *
 * ## One evidence vocabulary, reused
 *
 * [ProcessingEvidence] is a type alias for F3's [AgronomyEvidence], and that is
 * the point: a claim in this module cannot be labelled "bien documentado" here
 * and "evidencia mixta" one screen away. Two levels are shipped, the same two
 * F3 shipped.
 *
 * ## A note on two banned substrings
 *
 * The language guard bans `cura` and `EntourageLanguage` bans `daño`, both as
 * substrings, and also `peligro`, `inseguro`, `tóxico` and `peor`. Writing
 * solvent safety without those words is a real constraint, not a stylistic one,
 * and the copy uses `residuo`, `concentración`, `análisis`, `normativa`,
 * `oxidación`, `almacenamiento` and `degradación` instead. No guard was loosened
 * to accommodate a nicer word.
 */
typealias ProcessingEvidence = AgronomyEvidence

/* ── The named methods ──────────────────────────────────────────────────── */

/**
 * The three processing routes this module names.
 *
 * [usesSolvent] is a property of the chemistry and not a judgement: it is what
 * decides whether the residual-solvent question applies at all, and it is read
 * by the copy rather than by the composable, so a screen cannot decide that a
 * solvent-based route needs no safety line.
 */
enum class ProcessingMethod(val key: String, val labelEs: String, val usesSolvent: Boolean) {
    /**
     * Heat and pressure on fresh material, no solvent anywhere in the process.
     *
     * "En vivo" names the state of the starting material, not a machine: the
     * route exists precisely because it skips the drying and purging steps, and
     * that is the whole of the trade-off.
     */
    LIVE_ROSIN(
        key = "LIVE_ROSIN",
        labelEs = "Resina en vivo (prensado en caliente, sin disolvente)",
        usesSolvent = false
    ),

    /** A solvent dissolves the resin, and a later step takes the solvent away. */
    SOLVENT_EXTRACTION(
        key = "SOLVENT_EXTRACTION",
        labelEs = "Extracción con disolvente",
        usesSolvent = true
    ),

    /**
     * A reaction on the acidic cannabinoids, not a third extraction route.
     *
     * The distinction matters and the copy keeps it: calling decarboxylation an
     * extraction method would imply the solvent question applies to it, and it
     * does not.
     */
    DECARBOXYLATION(
        key = "DECARBOXYLATION",
        labelEs = "Descarboxilación",
        usesSolvent = false
    )
}

/**
 * The four things that move a profile between the harvest and the pen.
 *
 * Split rather than merged on purpose. Light, oxygen, heat and time are not four
 * words for one thing: they are four different levers, three of which accelerate
 * the same oxidation chemistry and one of which (time) is what decides whether
 * the other three have had their effect. Merging them would produce a block that
 * says "store it well" and teaches nothing.
 */
enum class PreservationFactorKind(val key: String, val labelEs: String) {
    LIGHT("LIGHT", "Luz"),
    OXIDATION("OXIDATION", "Oxígeno y oxidación"),
    HEAT("HEAT", "Calor"),
    TIME("TIME", "Tiempo")
}

/* ── One compound's processing behaviour ────────────────────────────────── */

/**
 * What one named method does to one compound.
 *
 * [detailEs] is compound-specific and short; the mechanism, the trade-off and
 * the safety framing are shared, in [ProcessingMethodGuide]. Neither restates the
 * other, which is what keeps a method from becoming ten copies of one paragraph.
 */
data class ProcessingMethodNote(
    val method: ProcessingMethod,
    /** What this compound does under this method, and the direction. */
    val detailEs: String,
    val evidence: ProcessingEvidence,
    /** How well supported this is, and what it cannot establish. Always visible. */
    val basisEs: String
) {
    val isDrawable: Boolean get() = detailEs.isNotBlank() && basisEs.isNotBlank()
}

/** What one preservation factor does to one compound. */
data class PreservationFactorNote(
    val factor: PreservationFactorKind,
    /** What this compound does under this factor, and the direction. */
    val detailEs: String,
    val evidence: ProcessingEvidence,
    /** How well supported this is, and what it cannot establish. Always visible. */
    val basisEs: String
) {
    val isDrawable: Boolean get() = detailEs.isNotBlank() && basisEs.isNotBlank()
}

/**
 * What processing does to one compound, keyed by terpene.
 *
 * The entry-level [basisEs] and [evidence] qualify [responseEs] — the summary
 * sentence. It is required, exactly as F3 required a basis on every lever: a
 * processing claim whose evidence level is missing is what this module exists to
 * not ship, so a row with a blank entry basis is **dropped and recorded** in
 * `unresolvedReferences` rather than shown unqualified.
 *
 * ## Why every compound gets an entry here, when F3 left two out
 *
 * F3's rule was that an entry earns its place when a **lever's** direction can
 * be stated for the compound — a lever being a response of the *plant*, and
 * Camphene and Terpinolene have none documented. F4's question is different:
 * it is what happens to the **compound** under a named method, and that is
 * chemistry of the molecule, which every one of the ten has. Camphene and
 * Terpinolene therefore ship an entry here and none in `agronomy`, and
 * `TerpeneProcessingAssetTest.theProcessingBlockCoversEveryCompoundF3LeftOut`
 * pins that asymmetry so it cannot become an accident.
 */
data class EntourageProcessing(
    val terpene: EntourageTerpene,
    /** One sentence: what processing does to this compound overall. */
    val responseEs: String,
    /** The level of [responseEs] itself. */
    val evidence: ProcessingEvidence,
    /** How well supported [responseEs] is. Required, always visible. */
    val basisEs: String,
    /** A **subset** of [ProcessingMethod]; the absent ones are not invented. */
    val methods: List<ProcessingMethodNote>,
    /** A **subset** of [PreservationFactorKind]. */
    val preservation: List<PreservationFactorNote>
) {
    /** The note for [method], or null when the asset documents none. */
    fun methodFor(method: ProcessingMethod): ProcessingMethodNote? =
        methods.firstOrNull { it.method == method }

    /** The note for [factor], or null when the asset documents none. */
    fun factorFor(factor: PreservationFactorKind): PreservationFactorNote? =
        preservation.firstOrNull { it.factor == factor }

    val documentedMethods: Set<ProcessingMethod> get() = methods.map { it.method }.toSet()
    val documentedFactors: Set<PreservationFactorKind> get() = preservation.map { it.factor }.toSet()

    /** Every word this entry shows, for the language assertion. */
    val allTextEs: String
        get() = buildString {
            append(responseEs)
            append(' ').append(basisEs)
            append(' ').append(evidence.labelEs)
            methods.forEach { append(" ${it.method.labelEs} ${it.detailEs} ${it.basisEs}") }
            preservation.forEach { append(" ${it.factor.labelEs} ${it.detailEs} ${it.basisEs}") }
        }
}

/**
 * The processing index, keyed by terpene.
 *
 * Same shape as [EntourageAgronomyIndex] and for the same reason: one lookup,
 * built once, so no screen can invent a fallback for a compound the asset does
 * not document. A missing entry returns null and the caller says so.
 */
class EntourageProcessingIndex(val entries: List<EntourageProcessing> = emptyList()) {

    private val byTerpene: Map<EntourageTerpene, EntourageProcessing> =
        entries.associateBy { it.terpene }

    val size: Int get() = entries.size

    /** The compounds the asset documents, enum order, for determinism. */
    val documentedTerpenes: Set<EntourageTerpene> get() = byTerpene.keys

    /** The module's compounds the asset leaves without an entry. */
    val undocumentedTerpenes: Set<EntourageTerpene>
        get() = EntourageTerpene.entries.toSet() - documentedTerpenes

    fun forTerpene(terpene: EntourageTerpene?): EntourageProcessing? =
        terpene?.let { byTerpene[it] }

    /** The entries for [terpenes], sorted by key so the order never depends on the asset. */
    fun forTerpenes(terpenes: Collection<EntourageTerpene>): List<EntourageProcessing> =
        terpenes.mapNotNull { byTerpene[it] }.sortedBy { it.terpene.key }

    /** Every entry, sorted by key. */
    fun all(): List<EntourageProcessing> = entries.sortedBy { it.terpene.key }
}

/* ── T-F2: the shared method guides, safety included ────────────────────── */

/**
 * The mechanism of one named method, the same for every compound.
 *
 * [safetyEs] is a required argument with **no default**, and [ProcessingGuides]
 * refuses to build a guide whose safety line is blank. That is the whole safety
 * guarantee of this dimension, and it is a construction rule rather than a test
 * so that it holds for every code path rather than for the ones a test names.
 *
 * The reason it cannot be a disclaimer is not stylistic. A disclaimer is
 * something the reader can skip and the developer can weaken in one edit; a
 * constructor argument that will not compile without it cannot be dropped from
 * the card, the page or the asset without the build failing.
 */
data class ProcessingMethodGuide(
    val method: ProcessingMethod,
    val titleEs: String,
    /** The trade-off: what the method keeps and what it costs. Never a figure. */
    val whatEs: String,
    /**
     * The safety framing, in the same block as the method name. Never blank.
     *
     * For a solvent-based route this carries the residual-solvent point and the
     * fact that "food grade" on a bottle describes the bottle, not the product
     * made with it.
     */
    val safetyEs: String,
    val evidence: ProcessingEvidence,
    /** Always visible. The level, and what it cannot establish. */
    val basisEs: String
) {
    init {
        require(safetyEs.isNotBlank()) {
            "the guide for ${method.key} would name an extraction method with no safety framing"
        }
    }
}

/** The mechanism of one preservation factor, the same for every compound. */
data class PreservationGuide(
    val factor: PreservationFactorKind,
    val titleEs: String,
    /** How the factor moves the profile. Never a duration or a figure. */
    val whatEs: String,
    val evidence: ProcessingEvidence,
    /** Always visible. The level, and what it cannot establish. */
    val basisEs: String
)

/**
 * The shared processing guidance: three methods, four preservation factors.
 *
 * Modelled once rather than repeated per compound, for the reason
 * [BiosynthesisExplainer] is: the chemistry of a named method does not depend on
 * which terpene is present, and ten copies of one paragraph is ten places for it
 * to go stale.
 *
 * ## What is here that is deliberately absent
 *
 * No temperature, no duration, no quantity, anywhere in this object. Each of the
 * three method bases says on screen which figure it is declining and why, so the
 * absence reads as a decision rather than as an omission.
 */
object ProcessingGuides {

    /**
     * The comparison, as one block.
     *
     * It is what the synergy card line carries, because a comparison is
     * method-independent and every compound's card would otherwise restate it.
     * It **names all three methods**, which is precisely why
     * [SolventSafetyEs][solventSafetyEs] has to travel with it: a sentence that
     * puts "extracción con disolvente" on screen has an obligation about
     * residual solvent in the same block, and that is enforced by
     * `TerpeneProcessingTest.theComparisonNamesTheMethodsAndCarriesTheSolventSafety`.
     */
    const val COMPARISON_ES: String =
        "Los tres métodos se diferencian en una sola cosa que importa aquí: qué " +
            "le ocurre a la fracción volátil del perfil. La resina en vivo, " +
            "obtenida por prensado en caliente, no tiene ninguna etapa posterior " +
            "de evaporación, de modo que es la ruta que menos expulsa a los " +
            "terpenos más volátiles. La extracción con disolvente recupera más " +
            "material total y, a cambio, introduce una etapa de purificado y " +
            "secado en la que esa fracción se va. La descarboxilación no es una " +
            "tercera ruta de extracción: actúa sobre el lado de los ácidos, y su " +
            "efecto sobre los terpenos es el efecto secundario del calor que la " +
            "acompaña. Ninguno de los tres viene acompañado de una cantidad, " +
            "porque las cifras dependen del material de partida, del equipo y " +
            "del proceso, y esta aplicación no puede medirlas."

    /**
     * The solvent-safety sentence, callable on its own.
     *
     * Exposed as a function rather than only sitting inside the guide so the
     * synergy card line can carry it without restating the method's whole
     * mechanism, and so a test can assert that it *does*.
     */
    fun solventSafetyEs(): String = methodFor(ProcessingMethod.SOLVENT_EXTRACTION).safetyEs

    private val methodGuides = listOf(
        ProcessingMethodGuide(
            method = ProcessingMethod.LIVE_ROSIN,
            titleEs = ProcessingMethod.LIVE_ROSIN.labelEs,
            whatEs =
                "El prensado en vivo actúa por calor y presión sobre el tricoma, sin " +
                    "añadir ni retirar ningún disolvente. Al no existir una etapa " +
                    "posterior de evaporación, la fracción volátil del perfil se " +
                    "pierde en menor medida que en una ruta que sí la tiene, y por " +
                    "eso es la vía que más se le parece al aroma de la planta " +
                    "recién cortada.",
            safetyEs =
                "No hay disolvente en el proceso y, por tanto, no hay residuo de " +
                    "disolvente que medir. Lo que sí hay es calor y presión: la " +
                    "prensa trabaja con material caliente y aplica fuerza. Que una " +
                    "vía no lleve disolvente no la convierte en la vía adecuada " +
                    "para cualquier planta, para cualquier persona ni para " +
                    "cualquier momento del proceso.",
            evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
            basisEs =
                "Que el secado y el almacenamiento se llevan la fracción volátil " +
                    "está bien documentado, y el prensado en vivo no tiene ninguna " +
                    "de las dos etapas. Lo que no está establecido es una " +
                    "comparación controlada, con el mismo material de partida y el " +
                    "mismo perfil medido, entre prensado en vivo y extracción con " +
                    "disolvente. La dirección es la que se afirma aquí; la " +
                    "magnitud no la puede fijar este catálogo."
        ),
        ProcessingMethodGuide(
            method = ProcessingMethod.SOLVENT_EXTRACTION,
            titleEs = ProcessingMethod.SOLVENT_EXTRACTION.labelEs,
            whatEs =
                "El disolvente disuelve la resina y arrastra también los terpenos " +
                    "más volátiles, y por eso recupera más material total. A cambio, " +
                    "el proceso incluye una etapa posterior: hay que separar el " +
                    "disolvente del extracto, y en esa etapa la fracción volátil se " +
                    "va en buena parte.",
            safetyEs =
                "Lo que un disolvente deja en el material final es una cuestión " +
                    "aparte de la calidad del propio disolvente. La palabra " +
                    "\"grado alimentario\" de la etiqueta describe para qué se " +
                    "vende esa botella, no lo que queda en un producto elaborado " +
                    "con ella: al evaporar el disolvente, la mayor parte del " +
                    "volumen se va y lo que no se evapora se queda cada vez más " +
                    "concentrado. El límite que llega a aplicar lo fija el producto " +
                    "terminado y su vía de uso, no la botella, y el único dato " +
                    "fiable sobre lo que queda es un análisis del producto ya " +
                    "elaborado.",
            evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
            basisEs =
                "Las dos direcciones están bien descritas en la literatura de " +
                    "procesado de productos vegetales: una extracción con " +
                    "disolvente recupera más material total, y la etapa de " +
                    "separación es la que se lleva la fracción más volátil. Lo que " +
                    "no hay es una comparación controlada, con el mismo material y " +
                    "el mismo perfil medido, entre métodos y entre disolventes " +
                    "concretos, así que la magnitud de esa pérdida no la puede " +
                    "fijar este catálogo."
        ),
        ProcessingMethodGuide(
            method = ProcessingMethod.DECARBOXYLATION,
            titleEs = ProcessingMethod.DECARBOXYLATION.labelEs,
            whatEs =
                "La descarboxilación no es una tercera ruta de extracción: es una " +
                    "reacción sobre los ácidos, que pierden un grupo carboxilo y " +
                    "liberan dióxido de carbono. Sobre el perfil terpénico su " +
                    "efecto es el efecto secundario del calor que la acompaña. Al " +
                    "subir la temperatura, lo que se va primero son los terpenos " +
                    "más volátiles; lo que se queda gana proporción, y con " +
                    "temperatura alta aparece además una fracción que ya no es el " +
                    "compuesto original.",
            safetyEs =
                "Aquí el agente es el calor y no un disolvente, así que la cuestión " +
                    "del residuo no aplica: no hay disolvente que pueda quedar en " +
                    "el material. Lo que sí conviene es que el proceso no se dé en " +
                    "un espacio cerrado, porque el material calentado desprende " +
                    "vapor y el aire de la sala acaba conteniendo lo que salió de " +
                    "él.",
            evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
            basisEs =
                "La pérdida de terpenos por calentamiento está bien documentada, y " +
                    "la transformación de los ácidos por descarboxilación también. " +
                    "Lo que no está fijado es cuánto dura cada transformación ni " +
                    "qué mezcla de productos de degradación aparece, porque depende " +
                    "del material y del proceso. Por eso aquí no hay ningún valor " +
                    "de temperatura ni de tiempo: serían una recomendación, no un " +
                    "dato, y una recomendación de proceso no la puede dar una " +
                    "aplicación que no mide el material de nadie."
        )
    )

    private val factorGuides = listOf(
        PreservationGuide(
            factor = PreservationFactorKind.LIGHT,
            titleEs = PreservationFactorKind.LIGHT.labelEs,
            whatEs =
                "La luz acelera la oxidación de la fracción volátil: los terpenos " +
                    "más reactivos reaccionan con el oxígeno en mayor medida bajo " +
                    "luz que en oscuridad. El resultado es un aroma distinto, no " +
                    "simplemente más tenue.",
            evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
            basisEs =
                "La degradación fotoquímica de los terpenos volátiles está bien " +
                    "documentada, y por eso la luz es el factor que más se vigila " +
                    "en el almacenamiento. Lo que no está fijado es cuánto tiempo " +
                    "de exposición cambia un perfil concreto: esa relación depende " +
                    "del envase, de la temperatura y del propio compuesto. Ningún " +
                    "plazo entra en esta aplicación."
        ),
        PreservationGuide(
            factor = PreservationFactorKind.OXIDATION,
            titleEs = PreservationFactorKind.OXIDATION.labelEs,
            whatEs =
                "El oxígeno es el agente principal. La transformación de un " +
                    "terpeno volátil en su óxido es una oxidación, y ocurre en el " +
                    "material almacenado igual que en el de la planta. El perfil se " +
                    "desplaza hacia productos que ya no son los compuestos que la " +
                    "planta construyó, y ese desplazamiento se acumula.",
            evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
            basisEs =
                "La oxidación de los terpenos volátiles y sus productos están bien " +
                    "caracterizados: el limoneno se oxida a óxido de limoneno y a " +
                    "carveol, y el pineno a óxido de pineno. Lo que no está fijado es " +
                    "qué proporción de cada óxido se forma en una muestra concreta " +
                    "de cannabis, porque depende de la variedad, del secado, del " +
                    "envase y del tiempo. Esta aplicación no puede medirlo."
        ),
        PreservationGuide(
            factor = PreservationFactorKind.HEAT,
            titleEs = PreservationFactorKind.HEAT.labelEs,
            whatEs =
                "El calor acelera todo lo anterior: la salida de la fracción " +
                    "volátil y la oxidación de la que se queda. Y hay una " +
                    "confusión frecuente que conviene deshacer: la ventana de " +
                    "temperatura que aparece más arriba en esta ficha describe " +
                    "dónde el compuesto es útil en un vaporizador, que es un uso, " +
                    "no un almacenamiento. Una ventana de temperatura no dice " +
                    "cuánto sobrevive un compuesto dentro de un bote cerrado.",
            evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
            basisEs =
                "Que calentar expulsa antes los terpenos más volátiles que los " +
                    "pesados está bien documentado, y la tarjeta de vaporización de " +
                    "esta ficha ya declara de dónde sale su banda y qué es lo que " +
                    "esa banda no es. Lo que no hay, y esta aplicación no intenta " +
                    "suplirlo, es una vida útil por compuesto."
        ),
        PreservationGuide(
            factor = PreservationFactorKind.TIME,
            titleEs = PreservationFactorKind.TIME.labelEs,
            whatEs =
                "El tiempo es el eje que ninguno de los otros tres sustituye: la " +
                    "oxidación y la foto-oxidación son reacciones que avanzan " +
                    "mientras el material esté cerrado y expuesto al aire. Un " +
                    "material guardado mucho no se distingue de uno guardado poco " +
                    "por un análisis posterior, así que el perfil que se mide al " +
                    "abrir un bote mezcla la planta con su historia.",
            evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
            basisEs =
                "Que la fracción oxidable se convierte con el tiempo está bien " +
                    "documentado. Lo que no existe es una vida útil por compuesto " +
                    "para cannabis: las cifras que circulan proceden de cultivos de " +
                    "aceites esenciales, con otra matriz, otro secado y otro " +
                    "envase. Por eso esta ficha no publica un plazo, y no es un " +
                    "olvido: es la cifra que no se puede sostener."
        )
    )

    /** Every method guide, in [ProcessingMethod] declaration order. */
    val methods: List<ProcessingMethodGuide> get() = methodGuides

    /** Every preservation guide, in [PreservationFactorKind] declaration order. */
    val factors: List<PreservationGuide> get() = factorGuides

    /** The guide for [method]. Always found: the enum and the list are written together. */
    fun methodFor(method: ProcessingMethod): ProcessingMethodGuide =
        methodGuides.first { it.method == method }

    /** The guide for [factor]. Always found. */
    fun factorFor(factor: PreservationFactorKind): PreservationGuide =
        factorGuides.first { it.factor == factor }

    /**
     * The methods the catalog's own evidence vocabulary does not separate.
     *
     * Not a shortcut around the per-compound notes: it is the set a screen may
     * render as "one guide covers both", and it exists so the composable cannot
     * invent that pairing for a compound the asset never said it for.
     */
    fun methodForOrNull(method: ProcessingMethod): ProcessingMethodGuide? =
        methodGuides.firstOrNull { it.method == method }

    /** Every word the guides show, for the language assertion. */
    fun allTextEs(): String = buildString {
        append(COMPARISON_ES)
        methodGuides.forEach { append(" ${it.titleEs} ${it.whatEs} ${it.safetyEs} ${it.basisEs}") }
        factorGuides.forEach { append(" ${it.titleEs} ${it.whatEs} ${it.basisEs}") }
    }
}

/* ── Copy ───────────────────────────────────────────────────────────────── */

/** One method, as the detail card renders it. */
data class ProcessingMethodContent(
    val method: ProcessingMethod,
    val titleEs: String,
    val detailEs: String,
    /** [ProcessingEvidence.labelEs]. Rendered, so the level is never only implied. */
    val evidenceLabelEs: String,
    val basisEs: String,
    /**
     * The shared mechanism and its safety framing.
     *
     * Carried inside the same object on purpose: a safety line the composable
     * has to fetch from somewhere else is a safety line a composable can forget.
     */
    val guide: ProcessingMethodGuide
) {
    val isDrawable: Boolean get() = detailEs.isNotBlank() && basisEs.isNotBlank()
}

/** One preservation factor, as the detail card renders it. */
data class PreservationFactorContent(
    val factor: PreservationFactorKind,
    val titleEs: String,
    val detailEs: String,
    /** [ProcessingEvidence.labelEs]. Rendered. */
    val evidenceLabelEs: String,
    val basisEs: String,
    /** The shared mechanism. Carried with the note, for the same reason. */
    val guide: PreservationGuide
) {
    val isDrawable: Boolean get() = detailEs.isNotBlank() && basisEs.isNotBlank()
}

/**
 * One compound's processing block, as both surfaces render it.
 *
 * [guides] and [factors] are the **shared** blocks and are always present, for
 * the reason the biosynthesis explainer is: they are facts about a method and
 * about a factor, not about a compound, and they survive a compound with no
 * documented note. [notDocumentedEs] is the honest half of [isDocumented] — an
 * absent note reads as "nothing to add here", and only a sentence can say the
 * catalog documents none.
 */
data class TerpeneProcessingContent(
    val titleEs: String,
    val terpeneLabelEs: String,
    val isDocumented: Boolean,
    val responseEs: String,
    /** Non-empty exactly when [isDocumented] is false. */
    val notDocumentedEs: String,
    /** The compound's own claim and its level. Empty when not documented. */
    val responseEvidenceLabelEs: String,
    val responseBasisEs: String,
    val methods: List<ProcessingMethodContent>,
    val preservation: List<PreservationFactorContent>,
    /** The shared comparison. Always present: it names the methods. */
    val comparisonEs: String,
    /** The residual-solvent sentence, travelling with the comparison. */
    val solventSafetyEs: String,
    /** The shared method guides, each with its own safety line. Always present. */
    val guides: List<ProcessingMethodGuide>,
    /** The shared preservation guides. Always present. */
    val factorGuides: List<PreservationGuide>
) {
    val isDrawable: Boolean
        get() = comparisonEs.isNotBlank() && solventSafetyEs.isNotBlank() && guides.isNotEmpty()

    /** Every word this block shows, for the language assertion. */
    val allTextEs: String
        get() = buildString {
            append(comparisonEs)
            append(' ').append(solventSafetyEs)
            append(' ').append(responseEs)
            append(' ').append(notDocumentedEs)
            append(' ').append(responseBasisEs)
            append(' ').append(responseEvidenceLabelEs)
            methods.forEach {
                append(" ${it.titleEs} ${it.detailEs} ${it.basisEs} ${it.guide.whatEs} ")
                append(it.guide.safetyEs)
                append(' ').append(it.guide.basisEs)
            }
            preservation.forEach {
                append(" ${it.titleEs} ${it.detailEs} ${it.basisEs} ${it.guide.whatEs}")
                append(' ').append(it.guide.basisEs)
            }
            guides.forEach { append(" ${it.titleEs} ${it.whatEs} ${it.safetyEs} ${it.basisEs}") }
            factorGuides.forEach { append(" ${it.titleEs} ${it.whatEs} ${it.basisEs}") }
        }
}

/**
 * Builds the processing copy, in Spanish.
 *
 * Two surfaces, one content: the compound's encyclopedia page and the synergy
 * card both read from here, so a method's safety line is visible in the same
 * words in both. `EntourageCards.cardFor` folds a compound's processing claim
 * into a single card line through [cardLineEs] so the card does not grow seven
 * extra paragraphs per combination.
 */
object TerpeneProcessingCopy {

    const val TITLE_ES: String = "⚗️ Procesado"

    /** Shown for a compound the catalog documents no note for. */
    const val NOT_DOCUMENTED_ES: String =
        "El catálogo no documenta una nota de procesado específica para %s: lo " +
            "que se puede decir de este compuesto es lo que comparte con su " +
            "familia química, no una respuesta propia a cada método."

    /** The label a compound's processing claim carries on a synergy card. */
    const val CARD_LABEL_ES: String = "⚗️ %s"

    /** `"Base: Evidencia mixta"`. One label for both surfaces. */
    const val BASIS_LABEL_TEMPLATE_ES: String = "Base: %s"

    /** Label above the residual-solvent sentence wherever the block is rendered. */
    const val SAFETY_LABEL_ES: String = "Residuo y exposición"

    /** Label above the compound's own summary claim. */
    const val RESPONSE_LABEL_ES: String = "Qué le hace el procesado"

    /** Heading of the shared comparison block. */
    const val COMPARISON_HEADING_ES: String = "Los tres métodos, uno a uno"

    /** What the comparison is relative to the compound's own notes. */
    const val COMPARISON_SCOPE_ES: String =
        "Lo de arriba es lo que le ocurre a este compuesto en cada método. Lo de " +
            "abajo es el mecanismo de cada método, el mismo para todos, con su " +
            "nivel de evidencia y su nota de seguridad."

    /** Heading of the shared mechanism list. */
    const val GUIDES_HEADING_ES: String = "Mecanismo y seguridad de cada método"

    /** Heading of the shared preservation block. */
    const val PRESERVATION_HEADING_ES: String = "Entre la cosecha y el bolígrafo"

    /** What the preservation block is relative to the compound's own notes. */
    const val PRESERVATION_SCOPE_ES: String =
        "Estos cuatro factores mueven el perfil después de la cosecha, y el " +
            "compuesto decide cuánto se mueve cada uno. Ninguno de los cuatro " +
            "viene con un valor: lo que esta ficha no puede sostener, no lo " +
            "publica."

    /** Closing line: what this block is not. */
    const val NOT_A_DIRECTIVE_ES: String =
        "Esta ficha describe métodos y sus efectos; no es una guía de trabajo. No " +
            "hay aquí ninguna temperatura, ningún tiempo y ninguna cantidad, porque " +
            "esas cifras dependen del material, del equipo y del proceso, y esta " +
            "aplicación no puede medirlos. Trabajar con material que va a " +
            "consumirse es una decisión que se toma con la normativa de tu país y " +
            "con tu propia experiencia, no con una tarjeta de una aplicación."

    /** `"Base: Evidencia mixta"`. */
    fun basisLabelEs(evidenceLabelEs: String): String =
        String.format(Locale.US, BASIS_LABEL_TEMPLATE_ES, evidenceLabelEs)

    private const val BASIS_PREFIX_ES = "Base (%s): %s"

    private const val SAFETY_PREFIX_ES = "%s: %s"

    /**
     * The block for [terpene], which [processing] may leave null.
     *
     * Null is a first-class answer and gets a named sentence, never an empty
     * block. The shared comparison, the residual-solvent sentence and every guide
     * are still present, because they are true of the methods rather than of the
     * compound — and because a card that named "extracción con disolvente" and
     * left out the residue would be the exact failure this phase exists to fix.
     */
    fun contentOf(
        terpene: EntourageTerpene,
        processing: EntourageProcessing?
    ): TerpeneProcessingContent {
        val shared = sharedContent()
        if (processing == null) {
            return TerpeneProcessingContent(
                titleEs = TITLE_ES,
                terpeneLabelEs = terpene.labelEs,
                isDocumented = false,
                responseEs = "",
                notDocumentedEs = String.format(Locale.US, NOT_DOCUMENTED_ES, terpene.labelEs),
                responseEvidenceLabelEs = "",
                responseBasisEs = "",
                methods = emptyList(),
                preservation = emptyList(),
                comparisonEs = shared.comparisonEs,
                solventSafetyEs = shared.solventSafetyEs,
                guides = shared.guides,
                factorGuides = shared.factors
            )
        }
        return TerpeneProcessingContent(
            titleEs = TITLE_ES,
            terpeneLabelEs = terpene.labelEs,
            isDocumented = true,
            responseEs = processing.responseEs,
            notDocumentedEs = "",
            responseEvidenceLabelEs = processing.evidence.labelEs,
            responseBasisEs = processing.basisEs,
            methods = processing.methods.map { note ->
                ProcessingMethodContent(
                    method = note.method,
                    titleEs = ProcessingGuides.methodFor(note.method).titleEs,
                    detailEs = note.detailEs,
                    evidenceLabelEs = note.evidence.labelEs,
                    basisEs = note.basisEs,
                    guide = ProcessingGuides.methodFor(note.method)
                )
            },
            preservation = processing.preservation.map { note ->
                PreservationFactorContent(
                    factor = note.factor,
                    titleEs = ProcessingGuides.factorFor(note.factor).titleEs,
                    detailEs = note.detailEs,
                    evidenceLabelEs = note.evidence.labelEs,
                    basisEs = note.basisEs,
                    guide = ProcessingGuides.factorFor(note.factor)
                )
            },
            comparisonEs = shared.comparisonEs,
            solventSafetyEs = shared.solventSafetyEs,
            guides = shared.guides,
            factorGuides = shared.factors
        )
    }

    /** The parts both branches share, resolved once so the two cannot differ. */
    private fun sharedContent() = SharedProcessingCopy

    /**
     * One synergy-card line for [processing].
     *
     * The line carries the compound's summary claim **with its level and its
     * basis folded into the body**, then the shared comparison, then the
     * residual-solvent sentence. The per-method and per-factor notes stay on the
     * compound's own page: a card line carrying seven more paragraphs per
     * combination is a card nobody finishes reading, and the comparison — which
     * is what a combination has to say about processing — is method-independent
     * anyway.
     *
     * The fold of the level and the basis is what F1's inline-qualifier rule
     * requires: a claim on a card carries its evidence in visible text rather
     * than behind a disclosure.
     */
    fun cardLineEs(processing: EntourageProcessing): String = buildString {
        append(processing.responseEs)
        append(' ')
        append(
            String.format(
                Locale.US,
                BASIS_PREFIX_ES,
                processing.evidence.labelEs,
                processing.basisEs
            )
        )
        append(' ').append(ProcessingGuides.COMPARISON_ES)
        append(' ')
        append(
            String.format(
                Locale.US,
                SAFETY_PREFIX_ES,
                SAFETY_LABEL_ES,
                ProcessingGuides.solventSafetyEs()
            )
        )
    }

    /** The card line for [processing], ready for [EntourageCardRole.PROCESSING]. */
    fun cardLine(processing: EntourageProcessing): EntourageCardLine = EntourageCardLine(
        role = EntourageCardRole.PROCESSING,
        labelEs = String.format(Locale.US, CARD_LABEL_ES, processing.terpene.labelEs),
        bodyEs = cardLineEs(processing)
    )

    /**
     * The processing lines for [terpenes], in enum order.
     *
     * Compounds the catalog does not document are **absent from the card** and
     * the card says nothing about their absence, for the reason
     * `TerpeneAgronomyCopy.cardLinesFor` gives: three "no data" lines next to
     * one line of content is worse than silence, and the compound's own page is
     * where the gap is stated in full.
     */
    fun cardLinesFor(
        terpenes: Collection<EntourageTerpene>,
        index: EntourageProcessingIndex
    ): List<EntourageCardLine> = index.forTerpenes(terpenes).map { cardLine(it) }
}

/** The shared parts of the block, held once so two call sites cannot diverge. */
private object SharedProcessingCopy {
    val comparisonEs: String = ProcessingGuides.COMPARISON_ES
    val solventSafetyEs: String = ProcessingGuides.solventSafetyEs()
    val guides: List<ProcessingMethodGuide> = ProcessingGuides.methods
    val factors: List<PreservationGuide> = ProcessingGuides.factors
}