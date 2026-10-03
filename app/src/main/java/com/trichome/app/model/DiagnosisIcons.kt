package com.trichome.app.model

/**
 * The per-symptom glyph vocabulary, as data.
 *
 * ## What replaced the emoji
 *
 * `diagnostics.json` shipped one emoji per symptom and the screen printed it
 * beside the label. That has three defects this file exists to remove. The glyph
 * carried no meaning — `🍂` for a deficiency and `🕷` for a mite are both
 * decoration, and most of the 116 entries were the same handful of characters
 * with a different colour rendering. It cannot be recoloured, so it does not
 * follow the theme the way every other mark on the screen does. And an emoji in a
 * `FilterChip` label is announced by a screen reader as its Unicode name before
 * the Spanish label that actually means something.
 *
 * ## The vocabulary: a motif crossed with a qualifier
 *
 * A glyph is [DiagnosisGlyphBase] + [DiagnosisGlyphMark]. The **base** is a
 * botanical or chemical idea — a leaf, a trichome, a hypha, a mite, a flame — and
 * the **mark** is one stroke drawn beside it. So `LEAF` + `CROSS` is a leaf
 * struck through and `LEAF` + `RING` is a ringed leaf: two different drawn
 * figures, not the same leaf twice.
 *
 * The two axes multiply out, which is the whole reason for the split. 23 motifs
 * times 8 qualifiers is 184 distinct figures and the shipped catalogue needs 116,
 * so every symptom can have its **own** glyph without 116 hand-drawn paths. The
 * headroom is published as [DiagnosisIcons.capacity] rather than asserted, so a
 * test can compare the catalogue against it.
 *
 * ## Uniqueness is a property of the table, not of a review
 *
 * No two symptoms may share a `(base, mark)` pair: two rows printing the same
 * figure is a lie about the picture, and a grower comparing two chips would be
 * told those two symptoms look alike when the truth is that one of them ran out
 * of symbols. [DiagnosisIcons.duplicateAssignments] finds those pairs and
 * `DiagnosisIconsTest` asserts the list is empty over the **shipped asset**,
 * not over a comment.
 *
 * That is also why the base list is long and the groups are small. A group that
 * ran long would have to reuse a qualifier to fit, and reusing a qualifier
 * inside one motif is precisely the collision. Splitting "leaf" into
 * [DiagnosisGlyphBase.LEAF], [DiagnosisGlyphBase.SCORCH] and
 * [DiagnosisGlyphBase.CURL] is not padding: chlorosis, scorch and curling are
 * three different things a leaf does, and a grower reads them as three.
 *
 * ## A symptom the table does not know
 *
 * [DiagnosisIcons.forSymptomId] returns null. The screen then renders
 * [DiagnosisIcons.NEUTRAL] **and** records the id in [DiagnosisIcons.unresolvedFor],
 * so a symptom added to `diagnostics.json` without a glyph here shows up in the
 * integrity notice instead of quietly borrowing another symptom's picture. That
 * is the project's standing rule — an unresolvable content key is dropped and
 * reported, never coerced to a default — applied to the glyph rather than to the
 * content, because dropping a symptom for a missing decoration would be the worse
 * of the two mistakes.
 *
 * ## No Android, and no Compose
 *
 * This file names motifs and qualifiers and holds the assignment. It draws
 * nothing: `ImageVector` construction lives in
 * `ui/components/DiagnosisGlyphs.kt`, so this package stays pure Kotlin and the
 * assignment table is testable on the JVM. `ModelPurityTest`'s file list was
 * extended with this file for the same reason it lists every engine.
 */

/** The motif half of a diagnosis glyph: a real plant structure or chemical idea. */
enum class DiagnosisGlyphBase(val key: String, val labelEs: String) {
    /** A leaf blade on its midrib. Chlorosis. */
    LEAF("leaf", "Hoja"),

    /** A browned, dead margin: scorch and necrosis. */
    SCORCH("scorch", "Quemadura"),

    /** A leaf rolled or folded along its midrib: curling. */
    CURL("curl", "Enrollamiento"),

    /** A leaf hanging off its petiole: loss of turgor. */
    DROOPING_LEAF("drooping_leaf", "Hoja caída"),

    /** A reticulate vein network: interveinal patterning and mosaic. */
    VEINS("veins", "Nervaduras"),

    /** A bordered dry spot with a halo: a lesion. */
    LESION("lesion", "Lesión seca"),

    /** A water-soaked or translucent patch. */
    BLOTCH("blotch", "Mancha húmeda"),

    /** A powder puff: a surface bloom. */
    POWDER("powder", "Polvo"),

    /** A droplet with a sheen: honeydew or exudate. */
    SAP("sap", "Savia"),

    /** An oval body on eight legs: a mite. */
    MITE("mite", "Ácaro"),

    /** A dew web strung between two anchors. */
    WEB("web", "Telaraña"),

    /** A winged body over a flight trail. */
    WING("wing", "Ala"),

    /** A segmented larva. */
    LARVA("larva", "Larva"),

    /** A bitten notch out of a blade edge. */
    NOTCH("notch", "Mordedura"),

    /** A branching hypha: mycelium and grey mould. */
    HYPHA("hypha", "Hifa"),

    /** A round spore on a conidiophore. */
    SPORE("spore", "Espora"),

    /** A whole plant held small: arrested vigour. */
    VIGOUR("vigour", "Vigor"),

    /** A stem section cut open: the vascular column. */
    STEM("stem", "Tallo"),

    /** A leaf taking on a blue or purple cast. */
    BLUEING("blueing", "Azulado"),

    /** A taproot with laterals. */
    ROOT("root", "Raíz"),

    /** A closed bud with its calyx. */
    BUD("bud", "Cogollo"),

    /** A flame tongue: heat and light. */
    FLAME("flame", "Llama"),

    /** A six-point frost star: cold. */
    FROST("frost", "Escarcha")
}

/** The qualifier half of a diagnosis glyph: one stroke drawn beside the motif. */
enum class DiagnosisGlyphMark(val key: String, val labelEs: String) {
    /** No qualifier. */
    PLAIN("plain", ""),

    /** A filled dot. */
    DOT("dot", "punto"),

    /** A closed ring. */
    RING("ring", "anillo"),

    /** A single open arc, opening right. */
    ARC("arc", "arco"),

    /** A diagonal cross. */
    CROSS("cross", "cruz"),

    /** A horizontal bar. */
    BAR("bar", "barra"),

    /** A double chevron pointing down: spreading. */
    CHEVRON("chevron", "doble galón"),

    /** A wavy line. */
    WAVE("wave", "onda")
}

/**
 * One symptom's figure.
 *
 * [key] is the stable identity the UI and the tests join on, derived from the two
 * axes rather than typed — so a figure can never claim an identity its parts do
 * not support, and renaming either enum member changes every key it appears in at
 * once.
 */
data class DiagnosisGlyph(
    val base: DiagnosisGlyphBase,
    val mark: DiagnosisGlyphMark
) {
    /** `"<base>__<mark>"`, e.g. `"leaf__cross"`. */
    val key: String get() = "${base.key}__${mark.key}"

    /**
     * What a screen reader announces, in the order the figure reads.
     *
     * "Hoja, cruz" is a leaf struck through; "Cruz, hoja" is a cross with a leaf
     * on it. Never the raw key: a screen reader reading `leaf__cross` is worse
     * than the emoji it replaced.
     */
    val contentDescriptionEs: String
        get() = if (mark.labelEs.isEmpty()) base.labelEs else "${base.labelEs}, ${mark.labelEs}"
}

/**
 * Which symptom gets which figure.
 *
 * ## How this table is grouped
 *
 * By motif, and the grouping **is** the diagnosis rather than a convenience.
 * Everything about how a leaf loses chlorophyll — bottom leaves first, between
 * the veins, in the new growth, with the veins staying green — is one motif
 * crossed with a different qualifier, because a grower reads those as one family
 * and seven identical leaves would waste the only thing the figure has going for
 * it. A different family gets a different motif: root problems are
 * [DiagnosisGlyphBase.ROOT] whatever their colour, and a mite is
 * [DiagnosisGlyphBase.MITE] whether or not there is webbing.
 *
 * ## No qualifier repeats inside a motif
 *
 * That is the uniqueness guarantee, and it is why each motif's block is at most
 * [DiagnosisGlyphMark.entries] long. Two symptoms on one motif with one qualifier
 * would print the same picture;
 * `DiagnosisIconsTest.everyShippedSymptomHasItsOwnGlyph` fails with both ids
 * named. The table is checked against the asset rather than trusted.
 *
 * The order inside a block is the order the symptoms were added to
 * `diagnostics.json` — the first block is 1..36, the rest 37..116 — and carries no
 * meaning of its own.
 */
object DiagnosisIcons {

    /** One row of the assignment table. */
    private data class Row(val id: String, val glyph: DiagnosisGlyph)

    /**
     * The figure shown when the table knows no symptom with this id.
     *
     * Deliberately a `(base, mark)` pair **nothing in the shipped catalogue
     * uses**, so "this symptom fell back to the neutral figure" and "this symptom
     * has its own figure that happens to look like the neutral one" are
     * distinguishable. `LEAF + DOT` was the first choice and was wrong: the
     * chlorosis family already uses it, so `yellowing_lower_leaves` resolved to
     * its own authored figure *and* compared equal to the fallback.
     *
     * `NOTCH + DOT` is free because the single chewing-damage symptom needs no
     * qualifier and so takes `NOTCH + PLAIN`.
     */
    val NEUTRAL: DiagnosisGlyph = DiagnosisGlyph(
        DiagnosisGlyphBase.NOTCH,
        DiagnosisGlyphMark.DOT
    )

    /**
     * How many distinct figures the two axes can produce.
     *
     * Published so the headroom is a number a test compares the catalogue
     * against, rather than an assurance that "23 x 8 should be enough".
     */
    val capacity: Int = DiagnosisGlyphBase.entries.size * DiagnosisGlyphMark.entries.size

    /**
     * The assignment, one row per shipped symptom.
     *
     * A **flat** list rather than a `Map<base, members>`: a map keyed on the
     * motif would need the motif to appear once per block, and three `LEAF`
     * blocks in one `mapOf` would keep only the last two. That is a silent
     * truncation of authored content, which is the defect class this module
     * refuses, so the shape that cannot truncate is the one used.
     */
    private val assignments: List<Row> = buildList {
        // ── Chlorosis: the leaf losing green ───────────────────────────────
        row(DiagnosisGlyphBase.LEAF, "chlorosis", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.LEAF, "yellowing_lower_leaves", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.LEAF, "interveinal_chlorosis", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.LEAF, "yellowing_between_veins", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.LEAF, "young_leaves_yellow", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.LEAF, "interveinal_chlorosis_young", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.LEAF, "nervios_verdes_hojas_amarillas", DiagnosisGlyphMark.CHEVRON)
        row(DiagnosisGlyphBase.LEAF, "hojas_inferiores_amarillas", DiagnosisGlyphMark.WAVE)

        // ── Scorch: dead margin and burnt tip ──────────────────────────────
        row(DiagnosisGlyphBase.SCORCH, "leaf_tip_burn", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.SCORCH, "brown_tips", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.SCORCH, "brown_leaf_edges", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.SCORCH, "margenes_secos_color_tostado", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.SCORCH, "tip_burn", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.SCORCH, "punta_brote_quemada_seca", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.SCORCH, "necrosis_marginal_marron_hojas", DiagnosisGlyphMark.CHEVRON)
        row(DiagnosisGlyphBase.SCORCH, "terminales_oscurecidos_bronceados", DiagnosisGlyphMark.WAVE)

        // ── Curl: the blade folding ────────────────────────────────────────
        row(DiagnosisGlyphBase.CURL, "young_leaves_curled", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.CURL, "leaf_curl_heat", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.CURL, "crooked_growing", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.CURL, "hojas_curvas_torcidas_vertice", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.CURL, "apices_retorcidos_hojas_filiformes", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.CURL, "margenes_acartonados_hojas_curvas", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.CURL, "hojas_arrugadas_cogollos_tiernos", DiagnosisGlyphMark.CHEVRON)
        row(DiagnosisGlyphBase.CURL, "hojas_nuevas_pequenas_reducidas", DiagnosisGlyphMark.WAVE)

        // ── Wilt: loss of turgor ───────────────────────────────────────────
        row(DiagnosisGlyphBase.DROOPING_LEAF, "wilting", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.DROOPING_LEAF, "marchitez_sequedad_hojas_bajas", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.DROOPING_LEAF, "marchitez_rapida_copa_densa", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.DROOPING_LEAF, "marchitez_general_tras_riego", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.DROOPING_LEAF, "marchitez_por_calor_extremo", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.DROOPING_LEAF, "marchitez_con_sustrato_saturado", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.DROOPING_LEAF, "perdida_vigor_manchas_plantas", DiagnosisGlyphMark.CHEVRON)
        row(DiagnosisGlyphBase.DROOPING_LEAF, "crecimiento_lento_planta_entera", DiagnosisGlyphMark.WAVE)

        // ── Vein patterning, mosaic and interveinal chlorosis ───────────────
        row(DiagnosisGlyphBase.VEINS, "angulos_amarillos_limitados_venas", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.VEINS, "amarillamiento_entre_nervaduras", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.VEINS, "necrosis_entre_venas_hojas_medias", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.VEINS, "moteado_verde_amarillo_hojas", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.VEINS, "clorosis_intensa_hojas_jovenes", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.VEINS, "clorosis_amarilla_alrededor_manchas", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.VEINS, "deformacion_mosaico_hojas_jovenes", DiagnosisGlyphMark.CHEVRON)
        row(DiagnosisGlyphBase.VEINS, "brotes_jovenes_dorados_blanqueados", DiagnosisGlyphMark.WAVE)

        // ── Dry lesions ────────────────────────────────────────────────────
        row(DiagnosisGlyphBase.LESION, "manchas_anillos_concentricos", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.LESION, "manchas_oscuras_halo_amarillo", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.LESION, "halo_amarillo_alrededor_manchas", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.LESION, "manchas_herrumbrosas_tostadas", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.LESION, "manchas_blancas_extendidas_copa", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.LESION, "manchas_marrones_puntos_negros", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.LESION, "tono_herrumbrroso_uniforme_hoja", DiagnosisGlyphMark.CHEVRON)
        row(DiagnosisGlyphBase.LESION, "manchas_blancas_bronceadas_hoja", DiagnosisGlyphMark.WAVE)

        // ── Wet lesions and dried-out white patches ────────────────────────
        row(DiagnosisGlyphBase.BLOTCH, "manchas_acuosas_humedas_hojas", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.BLOTCH, "franja_oscura_humeda_borde_hoja", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.BLOTCH, "zonas_blancas_secas_como_papel", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.BLOTCH, "excrementos_oscuros_sobre_hojas", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.BLOTCH, "puntos_negros_fecales_petiolos", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.BLOTCH, "black_dots", DiagnosisGlyphMark.CHEVRON)

        // ── Surface bloom, powder and silvering ────────────────────────────
        row(DiagnosisGlyphBase.POWDER, "white_powdery_residue", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.POWDER, "polvo_blanco_talco_hojas", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.POWDER, "fuzzy_white_mold", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.POWDER, "moho_algodonoso_enves", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.POWDER, "vello_gris_aterciopelado_enves", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.POWDER, "punteado_blanco_superficie_foliar", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.POWDER, "silvering", DiagnosisGlyphMark.CHEVRON)
        row(DiagnosisGlyphBase.POWDER, "rayas_plateadas_argenteas_hojas", DiagnosisGlyphMark.WAVE)

        // ── Honeydew and exudate ───────────────────────────────────────────
        row(DiagnosisGlyphBase.SAP, "baba_pegajosa_suciedad_hojas", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.SAP, "melaza_pegajosa_hormigas_hojas", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.SAP, "hojas_amarillas_pequenas_pegajosas", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.SAP, "pulgones_agrupados_brotes_nuevos", DiagnosisGlyphMark.ARC)

        // ── Mites ──────────────────────────────────────────────────────────
        row(DiagnosisGlyphBase.MITE, "tiny_spiders", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.MITE, "stippled_leaves", DiagnosisGlyphMark.DOT)

        // ── Webbing ────────────────────────────────────────────────────────
        row(DiagnosisGlyphBase.WEB, "webbing", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.WEB, "telaranas_finas_entre_hojas", DiagnosisGlyphMark.DOT)

        // ── Winged insects ─────────────────────────────────────────────────
        row(DiagnosisGlyphBase.WING, "mosca_blanca_adherida_enves", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.WING, "nube_aleteo_al_tocar_planta", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.WING, "moscas_negras_sobre_sustrato", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.WING, "tiny_black_flies", DiagnosisGlyphMark.ARC)

        // ── Soil fauna and chewing damage ──────────────────────────────────
        row(DiagnosisGlyphBase.LARVA, "larvae_in_soil", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.NOTCH, "bordes_masticados_hojas_transparentes", DiagnosisGlyphMark.PLAIN)

        // ── Grey mould and bud rot ─────────────────────────────────────────
        row(DiagnosisGlyphBase.HYPHA, "gray_mold", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.HYPHA, "velo_gris_sobre_las_flores", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.HYPHA, "pudricion_blanda_olor_desagradable", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.HYPHA, "pudricion_babosa_cogollos_humedos", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.HYPHA, "rotting_buds", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.HYPHA, "mohos_negros_cabellos_cogollos", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.HYPHA, "cabellos_negros_en_cogollos", DiagnosisGlyphMark.CHEVRON)

        // ── A single spore type, on its own ────────────────────────────────
        row(DiagnosisGlyphBase.SPORE, "pustulas_naranjas_redondas", DiagnosisGlyphMark.PLAIN)

        // ── Arrested vigour ────────────────────────────────────────────────
        row(DiagnosisGlyphBase.VIGOUR, "stunted_growth", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.VIGOUR, "caida_premura_hojas_bajas", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.VIGOUR, "desarrollo_planta_estancado", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.VIGOUR, "hojas_inferiores_secan_desprenden", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.VIGOUR, "tallos_debiles_dobladizos", DiagnosisGlyphMark.CROSS)

        // ── Stem and vascular ──────────────────────────────────────────────
        row(DiagnosisGlyphBase.STEM, "purple_stems", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.STEM, "tallos_petiolos_tono_purpura", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.STEM, "weak_stems", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.STEM, "corte_tallo_venas_oscurecidas", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.STEM, "tallo_blando_colapsa_al_tocarlo", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.STEM, "pudricion_oscura_base_tallo", DiagnosisGlyphMark.BAR)
        row(DiagnosisGlyphBase.STEM, "locked_nutrients", DiagnosisGlyphMark.CHEVRON)
        row(DiagnosisGlyphBase.STEM, "ph_out_of_range", DiagnosisGlyphMark.WAVE)

        // ── The blue and purple cast ───────────────────────────────────────
        row(DiagnosisGlyphBase.BLUEING, "dark_green_leaves", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.BLUEING, "hojas_verde_oscuro_azulado", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.BLUEING, "hojas_oscuras_tono_purpura", DiagnosisGlyphMark.RING)

        // ── Roots and substrate ────────────────────────────────────────────
        row(DiagnosisGlyphBase.ROOT, "stunted_root", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.ROOT, "raices_finas_cortas_marrones", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.ROOT, "raiz_negra_descompuesta", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.ROOT, "algodon_blanco_raices_maceta", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.ROOT, "raiz_podrida_olor_desagradable", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.ROOT, "sustrato_humedo_superficie_maceta", DiagnosisGlyphMark.BAR)

        // ── Buds and new growth ────────────────────────────────────────────
        row(DiagnosisGlyphBase.BUD, "cogollos_tiernos_deformados_rigidos", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.BUD, "aborto_floral_caida_cogollos", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.BUD, "new_growth_distorted", DiagnosisGlyphMark.ARC)

        // ── Light and chemical burn ────────────────────────────────────────
        row(DiagnosisGlyphBase.FLAME, "hot_spots", DiagnosisGlyphMark.PLAIN)
        row(DiagnosisGlyphBase.FLAME, "bleached_leaves", DiagnosisGlyphMark.DOT)
        row(DiagnosisGlyphBase.FLAME, "blanqueo_hojas_cerca_lampara", DiagnosisGlyphMark.RING)
        row(DiagnosisGlyphBase.FLAME, "light_burn", DiagnosisGlyphMark.ARC)
        row(DiagnosisGlyphBase.FLAME, "manchas_tras_aplicar_productos", DiagnosisGlyphMark.CROSS)
        row(DiagnosisGlyphBase.FLAME, "quemaduras_quimicas_bordes_hojas", DiagnosisGlyphMark.BAR)

        // ── Cold ───────────────────────────────────────────────────────────
        row(DiagnosisGlyphBase.FROST, "rigidez_general_baja_temperatura", DiagnosisGlyphMark.PLAIN)
    }

    /**
     * Appends one row.
     *
     * A function rather than a bare `add(...)` because the nested-pair form
     * `add(A to "id" to B)` destructures as `Pair<Pair<A, String>, B>`, which is
     * both harder to read and — the compiler's actual complaint — a shape this
     * type does not destructure without an explicit `component1`/`component2` on
     * the enum. Three named parameters cannot be got wrong.
     */
    private fun MutableList<Row>.row(
        base: DiagnosisGlyphBase,
        id: String,
        mark: DiagnosisGlyphMark
    ) = add(Row(id, DiagnosisGlyph(base, mark)))

    /** Every symptom id in the table, mapped to its figure. */
    val catalog: Map<String, DiagnosisGlyph> = assignments.associate { it.id to it.glyph }

    /**
     * The figure for [symptomId], or null when the table knows no such symptom.
     *
     * Null rather than [NEUTRAL] — that is the whole contract. The caller renders
     * the neutral figure **and** records the id, so a new symptom without a glyph
     * is reported instead of borrowing another symptom's picture.
     */
    fun forSymptomId(symptomId: String): DiagnosisGlyph? =
        catalog[symptomId.trim().lowercase()]

    /** Ids the table does not cover, de-duplicated, in the order they were given. */
    fun unresolvedFor(symptomIds: List<String>): List<String> =
        symptomIds.map { it.trim().lowercase() }
            .filter { it !in catalog }
            .distinct()

    /**
     * Ids that resolve to a figure another id already holds.
     *
     * Two ids on one `DiagnosisGlyph.key` means two symptoms print the same
     * picture, which the screen cannot support. Empty for the shipped catalogue
     * and asserted empty.
     */
    fun duplicateAssignments(symptomIds: List<String>): List<String> =
        symptomIds.map { it.trim().lowercase() }
            .mapNotNull { id -> forSymptomId(id)?.let { glyph -> id to glyph.key } }
            .groupBy({ it.second }, { it.first })
            .filterValues { it.size > 1 }
            .flatMap { (_, ids) -> ids.sorted() }

    /** Ids the table names more than once, which is a table defect and not an override. */
    fun repeatedIds(): List<String> =
        assignments.groupBy { it.id }.filterValues { it.size > 1 }.keys.sorted()

    /** The motifs the table uses, for tests and for the screen's legend. */
    val usedBases: Set<DiagnosisGlyphBase> = catalog.values.map { it.base }.toSet()
}