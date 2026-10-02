package com.trichome.app.model

/**
 * F3 — the agronomic / biological dimension of the Séquito module.
 *
 * ## What this file is for
 *
 * F1 made the module agree with itself and F2 turned a boiling point into a
 * vapourisation curve. Both of those are chemistry of the *material*. F3 adds
 * the part a grower can actually act on: what makes the plant make more of a
 * compound, and when to cut.
 *
 * Three things live here, and they are deliberately three things rather than one:
 *
 * 1. [EntourageAgronomy] — what **one compound** responds to. Keyed by terpene,
 *    not by cannabinoid x terpene pair. See "Why keyed by terpene" below.
 * 2. [BiosynthesisExplainer] — how the plant builds the C5 units in the first
 *    place, which is identical for every one of the 158 catalog compounds.
 * 3. [GrowOutGuides] — the mechanism of each agronomic lever, also identical
 *    for every compound.
 *
 * ## Why keyed by terpene (decision D-B)
 *
 * The source spec asked for agronomic tips *inside the synergy card*, keyed on
 * the cannabinoid x terpene pair. That was declined, and the reason is
 * duplication plus orphaning:
 *
 * - **Duplication.** `LIMONENE` appears in `thc_limonene` and in
 *   `cbg_limonene_myrcene`. A pair-keyed entry writes "harvest earlier for the
 *   volatile monoterpenes" twice, and the second copy is a second place for it
 *   to go stale.
 * - **Orphaning.** The same key approach means the tip only exists if a synergy
 *   happens to exist. `CAMPHENE` and `TERPINOLENE` ship in the vaporisation
 *   table and are modelled by [EntourageTerpene], but no synergy contains them,
 *   so a pair-keyed entry would never be reachable at all.
 *
 * Keyed by [EntourageTerpene] the advice is written **once** and is reachable
 * from two surfaces: the synergy card (for every terpene of the combination)
 * and the compound's own encyclopedia page (which is where a grower looks).
 *
 * ## What this content is allowed to claim
 *
 * The pharmacology in this module is almost entirely pre-clinical, so F1 pinned
 * every claim to its evidence level in visible text. Agronomy is **not** in that
 * position: UV-B inducing secondary metabolism through UVR8/HY5, controlled
 * water deficit as a pre-harvest technique, and harvest timing driving the
 * monoterpene/sesquiterpene shift are textbook plant physiology and cultivation
 * practice. This file therefore states them directly, and says so at the level
 * the literature actually supports rather than hedging solid agronomy into mush.
 *
 * Where the strength *is* mixed for a particular compound, the lever carries
 * [AgronomyEvidence.MIXTO] and a [AgronomyLever.basisEs] sentence that says
 * which way the evidence leans and what it cannot establish. Where a compound has
 * no documented lever, the honest answer is an **absent entry**, not a
 * fabricated one — and the copy says the catalog documents none, rather than
 * rendering an empty block.
 *
 * ## A note on two banned substrings
 *
 * The module's language guard bans `cura` and `EntourageLanguage` bans `daño`,
 * both as substrings. So the cultivation vocabulary here avoids `curado` /
 * `curación` and `daño` in favour of `secado`, `almacenamiento`, `lesiones` and
 * `degradación`. That is a real constraint on the wording, not a preference:
 * the guard is not loosened to accommodate a nice word.
 */
import java.util.Locale

/* ── Levers and evidence levels ─────────────────────────────────────────── */

/**
 * The three agronomic levers this module documents.
 *
 * An enum, not a string, so an asset key the model does not know is dropped and
 * reported rather than rendered as a label with no content behind it — the same
 * rule the cannabinoid / terpene / profile keys follow in
 * [EntourageBible][com.trichome.app.data.repository.EntourageBible.toContent].
 */
enum class AgronomyLeverKind(val key: String, val labelEs: String) {
    /** Ultraviolet B, 280-315 nm. */
    UV_B("UV_B", "Luz UV-B en floración tardía"),

    /** A short, reversible water deficit applied before harvest. */
    WATER_DEFICIT("WATER_DEFICIT", "Déficit hídrico controlado antes de la cosecha"),

    /** Where in the trichome maturation the plant is cut. */
    HARVEST_POINT("HARVEST_POINT", "Punto de cosecha y maduración del tricoma")
}

/**
 * How well a single claim is supported, in the user's language.
 *
 * The level is carried on the instance and its [labelEs] is required to appear
 * inside [AgronomyLever.basisEs], so a claim cannot claim a level it never says
 * out loud. This is the F1 rule (`EntourageCardRole.EVIDENCE` always rendered,
 * in visible text) applied to agronomy.
 */
enum class AgronomyEvidence(val key: String, val labelEs: String) {
    /** Textbook pathway biology or replicated cultivation practice. */
    BIEN_DOCUMENTADO("BIEN_DOCUMENTADO", "Bien documentado"),

    /** Reported, with a direction that is credible but a magnitude that varies. */
    MIXTO("MIXTO", "Evidencia mixta"),

    /** Consistent with a broader body of work, but thin for this compound. */
    LIMITADO("LIMITADO", "Evidencia limitada")
}

/* ── One compound's agronomy ────────────────────────────────────────────── */

/**
 * What one terpene responds to in the grow.
 *
 * [levers] is a **subset** of [AgronomyLeverKind] on purpose. A compound with no
 * documented lever for UV-B has no UV-B lever, and the absent lever is not
 * replaced by the generic behaviour of its chemical family: the generic
 * behaviour is delivered once, in [GrowOutGuides].
 *
 * @property basisEs always non-blank for a lever that survives the asset
 *   mapping. A lever whose `basis_es` is blank is **dropped and recorded** in
 *   `unresolvedReferences`, because an unqualified agronomy claim is exactly
 *   what this module exists to not ship.
 */
data class EntourageAgronomy(
    val terpene: EntourageTerpene,
    /** One sentence: what this compound responds to, and to what it does not. */
    val responseEs: String,
    val levers: List<AgronomyLever>
) {
    /** The lever of [kind], or null when the asset documents none. */
    fun leverFor(kind: AgronomyLeverKind): AgronomyLever? = levers.firstOrNull { it.kind == kind }

    /** Which of the three levers the asset documents, for this compound. */
    val documentedKinds: Set<AgronomyLeverKind> get() = levers.map { it.kind }.toSet()

    /** Every word this entry shows, for the language assertion. */
    val allTextEs: String
        get() = buildString {
            append(responseEs)
            levers.forEach { append(" ${it.detailEs} ${it.basisEs} ${it.kind.labelEs}") }
        }
}

/** One documented lever for one compound, with its evidence level. */
data class AgronomyLever(
    val kind: AgronomyLeverKind,
    /** What the compound does, and the direction. Compound-specific. */
    val detailEs: String,
    val evidence: AgronomyEvidence,
    /** How well supported this is, and what it cannot establish. Always visible. */
    val basisEs: String
)

/**
 * The agronomy index, keyed by terpene.
 *
 * Same shape as [TerpeneVolatilityIndex] and for the same reason: one lookup,
 * built once, so no screen can invent a fallback for a compound the asset does
 * not document. A missing entry returns null and the caller says so; it is never
 * coerced to an empty entry that would read as "this compound responds to
 * nothing in particular".
 */
class EntourageAgronomyIndex(val entries: List<EntourageAgronomy> = emptyList()) {

    private val byTerpene: Map<EntourageTerpene, EntourageAgronomy> =
        entries.associateBy { it.terpene }

    val size: Int get() = entries.size

    /** The compounds the asset documents, enum order, for determinism. */
    val documentedTerpenes: Set<EntourageTerpene> get() = byTerpene.keys

    /** The module's compounds the asset leaves without an entry. */
    val undocumentedTerpenes: Set<EntourageTerpene>
        get() = EntourageTerpene.entries.toSet() - documentedTerpenes

    /** The entry for [terpene], or null when the catalog documents none. */
    fun forTerpene(terpene: EntourageTerpene?): EntourageAgronomy? =
        terpene?.let { byTerpene[it] }

    /** The entries for [terpenes], sorted by key so the order never depends on the asset. */
    fun forTerpenes(terpenes: Collection<EntourageTerpene>): List<EntourageAgronomy> =
        terpenes.mapNotNull { byTerpene[it] }.sortedBy { it.terpene.key }

    /** Every entry, sorted by key. */
    fun all(): List<EntourageAgronomy> = entries.sortedBy { it.terpene.key }
}

/* ── T-B: the biosynthetic routes, as one explainer ─────────────────────── */

/** The two terpene routes, by the compartment they run in. */
enum class BiosyntheticPathway(
    val key: String,
    val nameEs: String,
    val compartmentEs: String,
    val productEs: String
) {
    MVA("MVA", "Ruta del mevalonato (MVA)", "citosol", "farnesil pirofosfato (C15)"),
    MEP("MEP", "Ruta del MEP / DOXP", "plastidio", "geranil pirofosfato (C10)")
}

/** One step of one route. */
data class BiosynthesisStep(
    val pathway: BiosyntheticPathway,
    val titleEs: String,
    val bodyEs: String
)

/**
 * How a capitate-stalked glandular trichome builds the terpene skeleton.
 *
 * ## Why this is one explainer and not 158 lines
 *
 * The MEP/DOXP and mevalonate routes are biochemistry. Every compound in the
 * encyclopedia is built from the same two C5 building blocks, and the only
 * thing that differs between a monoterpene and a sesquiterpene is which prenyl
 * diphosphate the terpene synthase receives. Repeating that per compound would
 * be 158 copies of one paragraph, four of which would be wrong in the way a
 * paraphrase usually is.
 *
 * It is therefore modelled once and reached from the agronomy section, the same
 * way `TerpeneVolatilityCopy.LIMITS_ES` is one string rather than 158.
 *
 * ## The honest part
 *
 * The textbook split — monoterpenes from GPP in the plastid, sesquiterpenes from
 * FPP assembled through the cytosolic mevalonate route — is what the routes are
 * named for. It is **not** a clean partition: the plastid also exports part of
 * the IPP and DMAPP pool that the cytosolic route consumes, so [CAVEAT_ES] says
 * so instead of leaving a tidier story than the biochemistry supports.
 */
object BiosynthesisExplainer {

    const val TITLE_ES: String = "🧬 Rutas biosintéticas"

    const val INTRO_ES: String =
        "Los terpenos de la resina se fabrican dentro de los tricomas glandulares " +
            "pedunculados: células secretoras que coronan un pie y descargan el " +
            "aceite en el espacio subcuticular, por encima del disco. Esa síntesis " +
            "sale de dos rutas que producen los mismos dos bloques de cinco átomos " +
            "de carbono, IPP y DMAPP, y lo que cambia entre un monoterpeno y un " +
            "sesquiterpeno es sobre qué esqueleto trabaja la sintasa de terpeno."

    /** The steps, in the order the chemistry happens. */
    val steps: List<BiosynthesisStep> = listOf(
        BiosynthesisStep(
            pathway = BiosyntheticPathway.MVA,
            titleEs = "1. Acetil-CoA → acetoacetil-CoA (MVA)",
            bodyEs = "La tiolasa condensa dos moléculas de acetil-CoA. Es el arranque " +
                "de la ruta del mevalonato, que ocurre en el citosol de la célula " +
                "secretora."
        ),
        BiosynthesisStep(
            pathway = BiosyntheticPathway.MVA,
            titleEs = "2. HMG-CoA → mevalonato (MVA)",
            bodyEs = "La HMG-CoA reductasa reduce HMG-CoA a mevalonato. Es la etapa " +
                "limitante de la ruta y la diana de las estatinas; el carbono del " +
                "mevalonato acaba siendo el esqueleto de cinco carbonos de todos " +
                "los terpenos."
        ),
        BiosynthesisStep(
            pathway = BiosyntheticPathway.MVA,
            titleEs = "3. Mevalonato → isopentenil pirofosfato (IPP y DMAPP)",
            bodyEs = "Tres fosforilaciones y una descarboxilación. En esta misma " +
                "etapa interviene la DMAPP reductasa, la diana del antibiótico " +
                "fosfomicina."
        ),
        BiosynthesisStep(
            pathway = BiosyntheticPathway.MVA,
            titleEs = "4. IPP + DMAPP → farnesil pirofosfato, C15 (MVA)",
            bodyEs = "La farnesil pirofosfato sintasa une un IPP y dos DMAPP. De aquí " +
                "salen los sesquiterpenos y, añadiendo un segundo farnesil, los " +
                "diterpenos."
        ),
        BiosynthesisStep(
            pathway = BiosyntheticPathway.MEP,
            titleEs = "5. Gliceraldehído-3-fosfato + acetil-CoA → DXP (MEP)",
            bodyEs = "La DXP sintasa condensa las dos moléculas y produce 1-deoxi-" +
                "D-xilulosa-5-fosfato. El paso ocurre dentro del plastidio, no en " +
                "el citosol: es la diferencia de compartimento que separa las dos " +
                "rutas."
        ),
        BiosynthesisStep(
            pathway = BiosyntheticPathway.MEP,
            titleEs = "6. DXP → mevalonaquinolato → IPP y DMAPP (MEP)",
            bodyEs = "La cadena MEP/DOXP produce los mismos dos isómeros de cinco " +
                "carbonos que la ruta del mevalonato, por una vía bioquímica " +
                "distinta y sin el esqueleto de mevalonato."
        ),
        BiosynthesisStep(
            pathway = BiosyntheticPathway.MEP,
            titleEs = "7. IPP + DMAPP → geranil pirofosfato, C10 (MEP)",
            bodyEs = "La geranil pirofosfato sintasa une un IPP y un DMAPP. De aquí " +
                "salen los monoterpenos, entre ellos el limoneno, los pinenos, el " +
                "mirceno y el linalool del módulo."
        )
    )

    const val SIZE_RULE_ES: String =
        "El número de átomos de carbono lo decide el esqueleto que sale de la " +
            "ruta, no el terpeno: un C10 se sintetiza sobre GPP y un C15 sobre " +
            "FPP. Por eso la ruta se puede leer en la ficha del compuesto sin " +
            "mirar nada más."

    const val TRICHOME_ES: String =
        "Todo esto ocurre en la célula secretora del tricoma glandular " +
            "pedunculado, no en la hoja: el aceite se acumula en el espacio " +
            "subcuticular sobre el disco, que es donde se mide la producción de " +
            "terpenos de una flor."

    const val CAVEAT_ES: String =
        "Las dos rutas no están totalmente separadas: el plastidio también " +
            "exporta parte del IPP y del DMAPP que consume la ruta del mevalonato, " +
            "así que el reparto entre C10 y C15 no es una frontera limpia. Y esta " +
            "bioquímica es la misma para los 158 compuestos de la enciclopedia: lo " +
            "que cambia entre ellos es qué sintasa de terpeno actúa sobre GPP o " +
            "sobre FPP."

    /** The route a compound of [family] is built by, or null when it is unclassified. */
    fun pathwayFor(family: TerpeneFamily): BiosyntheticPathway? = when (family) {
        TerpeneFamily.MONOTERPENE -> BiosyntheticPathway.MEP
        TerpeneFamily.SESQUITERPENE, TerpeneFamily.DITERPENE -> BiosyntheticPathway.MVA
        TerpeneFamily.UNCLASSIFIED -> null
    }

    /** One sentence naming the route for a compound of [family]. */
    fun routeLineEs(family: TerpeneFamily): String = when (family) {
        TerpeneFamily.MONOTERPENE ->
            "Este compuesto es un monoterpeno (C10): la planta lo construye sobre " +
                "el geranil pirofosfato que da la ruta MEP/DOXP, dentro del plastidio."
        TerpeneFamily.SESQUITERPENE ->
            "Este compuesto es un sesquiterpeno (C15): se construye sobre el " +
                "farnesil pirofosfato, ensamblado con la ruta del mevalonato, cuya " +
                "sede principal es el citosol."
        TerpeneFamily.DITERPENE ->
            "Este compuesto es un diterpeno (C20): la planta añade un segundo " +
                "farnesil pirofosfato al esqueleto C15, y ese paso también sale de " +
                "la ruta del mevalonato."
        TerpeneFamily.UNCLASSIFIED ->
            "La enciclopedia no clasifica este compuesto por tamaño, así que no hay " +
                "una ruta que le corresponda aquí. Las dos de arriba cubren los " +
                "terpenos C10 y C15, que son los que modela el módulo de Séquito."
    }

    /** The whole explainer, as the screen renders it. */
    fun contentFor(family: TerpeneFamily): BiosynthesisContent = BiosynthesisContent(
        titleEs = TITLE_ES,
        introEs = INTRO_ES,
        steps = steps,
        routeEs = routeLineEs(family),
        sizeRuleEs = SIZE_RULE_ES,
        trichomeEs = TRICHOME_ES,
        caveatEs = CAVEAT_ES
    )
}

/**
 * The biosynthesis explainer, as data.
 *
 * A model and not a composable for the reason the rest of this module is written
 * that way: Compose has no unit-test runtime on the `test` classpath, so copy
 * that lives in a composable cannot be asserted before it ships. The caveat is a
 * required field for the same reason [TerpeneVolatilityContent.evidenceEs] is —
 * an explainer that can render its steps without saying what it cannot
 * establish is half a statement.
 */
data class BiosynthesisContent(
    val titleEs: String,
    val introEs: String,
    val steps: List<BiosynthesisStep>,
    /** The route for the compound this explainer was opened from. */
    val routeEs: String,
    val sizeRuleEs: String,
    val trichomeEs: String,
    /** Always visible. What the two-route picture does not settle. */
    val caveatEs: String
) {
    val isDrawable: Boolean
        get() = introEs.isNotBlank() && steps.isNotEmpty() && caveatEs.isNotBlank()
}

/* ── T-C: the shared grow-out guidance ──────────────────────────────────── */

/**
 * The mechanism of one lever, the same for every compound.
 *
 * Split from [AgronomyLever] deliberately: [AgronomyLever.detailEs] says what
 * **this compound** does under the lever, and this says how the lever works and
 * how well the whole technique is supported. Neither restates the other, which is
 * what keeps a lever per terpene from becoming eight copies of one paragraph.
 */
data class AgronomyGuide(
    val kind: AgronomyLeverKind,
    val titleEs: String,
    /** How the lever works on the plant. Compound-independent. */
    val whatEs: String,
    val evidence: AgronomyEvidence,
    /** Always visible. The level, and what it cannot establish. */
    val basisEs: String
)

/**
 * The three grow-out guides, authored once.
 *
 * Unlike [EntourageAgronomy], which is shipped content, this is the module's own
 * wording for mechanism it states directly. The reason it lives in `model/` is
 * the same reason `TerpeneVolatilityCopy.LIMITS_ES` does: Compose has no JVM
 * test runtime, so a sentence only a composable can reach is a sentence nobody
 * can hold to the language guard before it ships.
 */
object GrowOutGuides {

    private val guides = listOf(
        AgronomyGuide(
            kind = AgronomyLeverKind.UV_B,
            titleEs = AgronomyLeverKind.UV_B.labelEs,
            whatEs =
                "La UV-B (280-315 nm) activa el fotorreceptor UVR8, que desactiva la " +
                    "vía COP1 y libera a HY5, y con él la transcripción de las " +
                    "sintasas de terpenos. Añadir UV-B al final de la floración es " +
                    "la forma más directa de empujar la respuesta defensiva de la " +
                    "planta, que en el cannabis cultivado es donde está el aroma.",
            evidence = AgronomyEvidence.BIEN_DOCUMENTADO,
            basisEs =
                "La cadena UVR8 → COP1 → HY5 → sintasa de terpeno está bien " +
                    "caracterizada en plantas, y la inducción de sesquiterpenos por " +
                    "UV-B está documentada en varias especies. Lo que no está " +
                    "fijado es la dosis, ni el efecto sobre un terpeno concreto en " +
                    "una variedad concreta. Y UV-B también absorbe: por encima de " +
                    "cierta intensidad la planta se estresa, pierde clorofila y " +
                    "termina con menos biomasa."
        ),
        AgronomyGuide(
            kind = AgronomyLeverKind.WATER_DEFICIT,
            titleEs = AgronomyLeverKind.WATER_DEFICIT.labelEs,
            whatEs =
                "Un déficit de agua moderado, corto y reversible antes de la cosecha " +
                    "se usa para forzar una respuesta de estrés: la planta concentra " +
                    "y, en varios cultivos de aceites esenciales, aumenta el " +
                    "contenido de terpenos. El margen entre esa respuesta y la " +
                    "sequeda es estrecho. Un déficit prolongado reduce la biomasa y " +
                    "sube el estrés oxidativo, y eso no es la misma técnica.",
            evidence = AgronomyEvidence.MIXTO,
            basisEs =
                "La respuesta al déficit hídrico moderado está documentada en " +
                    "cultivos de aceites esenciales y se ha reportado en cannabis, " +
                    "pero la magnitud y la ventana concreta varían según variedad, " +
                    "sustrato y etapa. No hay una curva dosis-respuesta publicada " +
                    "para un terpeno concreto en una variedad concreta."
        ),
        AgronomyGuide(
            kind = AgronomyLeverKind.HARVEST_POINT,
            titleEs = AgronomyLeverKind.HARVEST_POINT.labelEs,
            whatEs =
                "El tricoma glandular pedunculado pasa de transparente a lechoso y " +
                    "de lechoso a ámbar, y ese es el reloj de la cosecha. La firma " +
                    "terpénica se mueve con él: los terpenos volátiles se van " +
                    "mientras la planta madura y los pesados entran en su lugar, así " +
                    "que la proporción monoterpeno/sesquiterpeno se invierte.",
            evidence = AgronomyEvidence.MIXTO,
            basisEs =
                "El desplazamiento de la proporción hacia sesquiterpenos con la " +
                    "maduración está descrito en la literatura de Cannabis, pero la " +
                    "dirección no es constante entre variedades. Además el secado y " +
                    "el almacenamiento posterior cambian por sí solos el perfil " +
                    "medido, así que un análisis posterior no separa la maduración " +
                    "del proceso. El tricoma es un indicador de momento, no un " +
                    "objetivo de composición."
        )
    )

    /** Every guide, in [AgronomyLeverKind] declaration order. */
    val all: List<AgronomyGuide> get() = guides

    /** The guide for [kind]. Always found: the enum and the list are written together. */
    fun guideFor(kind: AgronomyLeverKind): AgronomyGuide =
        guides.first { it.kind == kind }

    /** Every word the guides show, for the language assertion. */
    fun allTextEs(): String =
        guides.joinToString(" ") { "${it.titleEs} ${it.whatEs} ${it.basisEs}" }
}

/* ── Copy ───────────────────────────────────────────────────────────────── */

/** One lever, as a card or a detail page renders it. */
data class AgronomyLeverContent(
    val kind: AgronomyLeverKind,
    val titleEs: String,
    val detailEs: String,
    /** [AgronomyEvidence.labelEs]. Rendered, so the level is never only implied. */
    val evidenceLabelEs: String,
    val basisEs: String
) {
    /** Whether the lever may be drawn. It may not be drawn without its basis. */
    val isDrawable: Boolean get() = detailEs.isNotBlank() && basisEs.isNotBlank()
}

/**
 * One compound's agronomy block, as both surfaces render it.
 *
 * [notDocumentedEs] is the honest half of [isDocumented]. A compound the
 * catalog says nothing about gets a **sentence** naming that fact, exactly like
 * [EntourageSynergyCard.NO_EVIDENCE_DECLARED]: an absent block reads as "nothing
 * to add here", and only a sentence can say "no lever is documented".
 *
 * [routeEs] is filled even when [isDocumented] is false, because the
 * biosynthetic route is a fact about the compound's size and not an agronomic
 * claim. That is the difference between "we have no agronomy for this one" and
 * "this one has no agronomy but we can still say how it is built".
 */
data class TerpeneAgronomyContent(
    val titleEs: String,
    val terpeneLabelEs: String,
    /**
     * The compound's family, which is what [routeEs] was derived from.
     *
     * Carried so the biosynthetic explainer opened from this block is built for
     * the same family the route sentence came from, instead of a call site
     * resolving it a second time and being free to pick a different one.
     */
    val family: TerpeneFamily,
    val isDocumented: Boolean,
    val responseEs: String,
    /** Non-empty exactly when [isDocumented] is false. */
    val notDocumentedEs: String,
    val levers: List<AgronomyLeverContent>,
    /** The compound's route, from [BiosynthesisExplainer]. Always present. */
    val routeEs: String
) {
    val isDrawable: Boolean get() = routeEs.isNotBlank()

    /** Every word this block shows, for the language assertion. */
    val allTextEs: String
        get() = buildString {
            append(responseEs)
            append(' ').append(notDocumentedEs)
            append(' ').append(routeEs)
            levers.forEach { append(" ${it.titleEs} ${it.detailEs} ${it.basisEs}") }
        }
}

/**
 * Builds the agronomy copy, in Spanish.
 *
 * Two surfaces, one content: the compound's encyclopedia page and the synergy
 * card both read [TerpeneAgronomyContent], so a lever's basis is visible in the
 * same words in both. `EntourageCards.cardFor` folds a compound's levers into a
 * single card line through [cardLineEs] so the card does not grow six extra
 * paragraphs per combination.
 */
object TerpeneAgronomyCopy {

    const val TITLE_ES: String = "🌱 Agronomía"

    /** Shown for a compound the catalog documents no lever for. */
    const val NOT_DOCUMENTED_ES: String =
        "El catálogo no documenta ninguna palanca agronómica específica para %s: " +
            "su comportamiento se explica por su familia química, no por una " +
            "respuesta propia a la luz, al agua o al momento de cosecha."

    /** The label a compound's levers carry on a synergy card. */
    const val CARD_LABEL_ES: String = "🌱 %s"

    /** The label one lever carries on a synergy card. */
    const val CARD_LEVER_LABEL_ES: String = "🌱 %s · %s"

    /** Label above a documented compound's response sentence. */
    const val RESPONSE_LABEL_ES: String = "Qué responde"

    /**
     * Label above every basis sentence, wherever the block is rendered.
     *
     * In `model/` so the label cannot differ between the detail page and the
     * card, and so the composable holds no Spanish literal at all —
     * `TerpeneDetailAgronomyTest` asserts that, which is the cheapest way to keep
     * an untestable sentence out of a composable.
     */
    const val BASIS_LABEL_TEMPLATE_ES: String = "Base: %s"

    /** Heading of the shared grow-out mechanism block. */
    const val SHARED_GUIDES_HEADING_ES: String = "Cómo funcionan las tres palancas"

    /** What the shared block is relative to the compound's own levers. */
    const val SHARED_GUIDES_SCOPE_ES: String =
        "Lo de arriba es lo que le pasa a este compuesto. Lo de abajo es el " +
            "mecanismo de cada palanca, que es el mismo para todos y está citado " +
            "con su nivel de evidencia."

    /** Closing line: what an agronomic lever is not. */
    const val NOT_A_DIRECTIVE_ES: String =
        "Una palanca agronómica no es una instrucción: el resultado depende de la " +
            "variedad, del sustrato y del proceso, y esta aplicación no puede " +
            "medir el de tu planta."

    /** `"Base: Evidencia mixta"`. */
    fun basisLabelEs(evidenceLabelEs: String): String =
        String.format(Locale.US, BASIS_LABEL_TEMPLATE_ES, evidenceLabelEs)

    private const val BASIS_PREFIX_ES = "Base (%s): %s"

    /**
     * The block for [terpene], which [agronomy] may leave null.
     *
     * Null is a first-class answer and gets a named sentence, never an empty
     * block: a grower reading a compound with no agronomy needs to know the
     * catalog is silent rather than assume there is nothing to know.
     */
    fun contentOf(terpene: EntourageTerpene, agronomy: EntourageAgronomy?): TerpeneAgronomyContent {
        val family = TerpeneFamily.fromFamilyEs(terpene.familyEs)
        val route = BiosynthesisExplainer.routeLineEs(family)
        if (agronomy == null) {
            return TerpeneAgronomyContent(
                titleEs = TITLE_ES,
                terpeneLabelEs = terpene.labelEs,
                family = family,
                isDocumented = false,
                responseEs = "",
                notDocumentedEs = String.format(Locale.US, NOT_DOCUMENTED_ES, terpene.labelEs),
                levers = emptyList(),
                routeEs = route
            )
        }
        return TerpeneAgronomyContent(
            titleEs = TITLE_ES,
            terpeneLabelEs = terpene.labelEs,
            family = family,
            isDocumented = true,
            responseEs = agronomy.responseEs,
            notDocumentedEs = "",
            levers = agronomy.levers.map { lever ->
                AgronomyLeverContent(
                    kind = lever.kind,
                    titleEs = GrowOutGuides.guideFor(lever.kind).titleEs,
                    detailEs = lever.detailEs,
                    evidenceLabelEs = lever.evidence.labelEs,
                    basisEs = lever.basisEs
                )
            },
            routeEs = route
        )
    }

    /**
     * One synergy-card line for [agronomy], with every lever's basis folded in.
     *
     * The fold is the point. A card line is a label and a paragraph, and the
     * module's rule is that an evidence line is **visible** on the card — so the
     * basis travels inside the same body rather than as a separate line the card
     * could skip. `theAgronomyBasisTravelsInsideTheCardLine` asserts each lever's
     * `basisEs` appears verbatim in the produced body.
     */
    fun cardLineEs(agronomy: EntourageAgronomy): String = buildString {
        append(agronomy.responseEs)
        agronomy.levers.forEach { lever ->
            append(' ')
            append(GrowOutGuides.guideFor(lever.kind).titleEs)
            append(": ")
            append(lever.detailEs)
            append(' ')
            append(
                String.format(
                    Locale.US,
                    BASIS_PREFIX_ES,
                    lever.evidence.labelEs,
                    lever.basisEs
                )
            )
        }
    }

    /** The card line for [agronomy], ready for [EntourageCardRole.AGRONOMY]. */
    fun cardLine(agronomy: EntourageAgronomy): EntourageCardLine = EntourageCardLine(
        role = EntourageCardRole.AGRONOMY,
        labelEs = String.format(Locale.US, CARD_LABEL_ES, agronomy.terpene.labelEs),
        bodyEs = cardLineEs(agronomy)
    )

    /**
     * The agronomy lines for [terpenes], in enum order.
     *
     * Compounds the catalog does not document are **absent from the card**, and
     * the card says nothing about their absence — that is the one place this
     * module's rule is relaxed, deliberately. On a combination of three terpenes
     * where only one has an entry, a "no data" line per silent compound would
     * outnumber the content it sits next to, and the compound's own page is
     * where the gap is stated in full.
     */
    fun cardLinesFor(
        terpenes: Collection<EntourageTerpene>,
        index: EntourageAgronomyIndex
    ): List<EntourageCardLine> = index.forTerpenes(terpenes).map { cardLine(it) }
}