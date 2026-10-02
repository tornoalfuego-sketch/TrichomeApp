package com.trichome.app.model

import java.util.Locale

/**
 * F2 — volatility, as one view type with its provenance attached.
 *
 * ## The problem this file exists to solve
 *
 * Until now a terpene's temperature behaviour lived in two assets, two models
 * and two screens. `terpenes.json` gave all 158 compounds a boiling point and the
 * detail page printed it; `entourage_data.json` gave ten of them a `min`/`max`
 * window and the Séquito module rendered it. A user browsing the encyclopedia saw
 * `167 °C` and nothing else, which is indistinguishable from a compound the app
 * knows nothing about. Ten pages out of 158 had a window. That is the shape of a
 * broken database, not of a rich one.
 *
 * [TerpeneVolatility] fuses the two into a single value that **carries where it
 * came from**: [VolatilityProvenance.MEASURED] for the ten compounds whose band
 * the Séquito table ships, [VolatilityProvenance.DERIVED] for the 148 that have a
 * boiling point and nothing else. The UI never asks which it is holding — it
 * reads the flag off the type, the same way [ClimateCardContent] carries
 * `isEstimate` so no call site can print a bare sensor-looking number.
 *
 * ## `DERIVED` is a first-class answer, not a consolation prize
 *
 * The alternative to a derived window is a blank field on 148 pages. A blank
 * field is honest and useless; a fabricated-looking precise window is dishonest
 * and useful. This file takes the second failure seriously enough to make the
 * derived value structurally unable to pass as measured:
 *
 *  - [VolatilityWindow] carries [VolatilityProvenance] on the instance, so there
 *    is no constructor path that produces a window without saying where it is
 *    from;
 *  - [VolatilityDerivation] quantises every derived band to
 *    [VolatilityDerivation.STEP_C], so it can never display the single-degree
 *    precision the model does not have;
 *  - [TerpeneVolatilityCopy] prefixes every estimated number with
 *    [TerpeneVolatilityCopy.ESTIMATE_MARK] and ships an [evidenceEs] sentence the
 *    screen is expected to render unconditionally, which is the
 *    [ClimateCardCopy] precedent this file follows.
 *
 * ## What a derived window cannot tell you
 *
 * Stated here because the KDoc is the only place it can be stated once:
 *
 *  - **Yield, recovery time or aroma quality.** Nothing in this repository
 *    measures any of it, for any compound, measured or derived.
 *  - **Whether your equipment reaches the temperature.** A 320 °C diterpene band
 *    describes the molecule, not a device.
 *  - **Whether the compound is present in your flower**, or in what proportion.
 *    That is a laboratory question this offline app cannot answer.
 *  - **A sub-family structure.** The catalog's `family` field is a coarse label,
 *    and the catalog proves it: it files `hexanal` (131 °C) and `vanillin`
 *    (285 °C) under `Monoterpeno`. Family is a coarse prior here, not a
 *    classification the model is entitled to be precise about.
 *
 * The curve ([VolatilityCurve]) adds the one thing a temperature band genuinely
 * tells you that a single band does not: **what you lose by going hot enough to
 * reach the last compound**. That is a fact about the selection, not a
 * prediction about the result.
 */

/* ── Boiling point parsing ─────────────────────────────────────────────── */

/**
 * Reads the encyclopedia's display string into a temperature.
 *
 * ## Why this is not a digit filter
 *
 * `Terpene.boilingPointCelsius` used to be `boilingPoint.filter { it.isDigit() }
 * .toIntOrNull()`, which has two failure modes and no error on either:
 *
 *  - a value with no degree sign returns null, so a compound silently loses its
 *    temperature;
 *  - a **range** concatenates. `"155-156 °C"` reads as `155156`, a plausible
 *    integer that is not a temperature at all.
 *
 * No shipped row is a range today — all 158 are `"NNN °C"` — but a content file
 * is a long-lived thing and the F1 KDoc already calls this out. So the parser
 * takes the **lowest** number it finds, which is the defensible reading of a
 * range ("it starts coming off at 155 °C") and is also the plain value for a
 * single number.
 */
object BoilingPointParser {

    /** Integers in the text, in the order they appear. */
    private val NUMBERS = Regex("""\d+""")

    /**
     * The temperature in [text], °C, or null when it carries none.
     *
     * Null rather than a default, and never a silently mangled number: a
     * compound with no readable boiling point has no volatility row, and
     * [TerpeneVolatilityIndex] names the ids it dropped instead of inventing
     * one.
     */
    fun parseCelsius(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        // The degree sign is the guard. This field is `boilingPoint` and
        // nothing else, so its absence means the value is not a temperature.
        if (!text.contains('°')) return null
        val values = NUMBERS.findAll(text).map { it.value.toIntOrNull() ?: 0 }.toList()
        return values.minOrNull()
    }
}

/* ── Provenance ────────────────────────────────────────────────────────── */

/**
 * Where a vapourisation window came from.
 *
 * An enum rather than a boolean so a `when` over it is exhaustive: a new
 * provenance is a compile break at every renderer instead of a window that
 * quietly renders with no marker at all.
 */
enum class VolatilityProvenance(val labelEs: String) {
    /** The band ships in `entourage_data.json` and was checked against the boiling point. */
    MEASURED("Medida en la tabla de Séquito"),

    /**
     * The band was computed here from the compound's measured boiling point and
     * its chemical family. Never presented as a measurement.
     */
    DERIVED("Estimada por la app")
}

/* ── Families ──────────────────────────────────────────────────────────── */

/**
 * The chemical families the derivation bands by.
 *
 * The family is the **banding key** — it is what `derive` is handed and what the
 * evidence line names — and it carries its own [shippedRowCount] so a reader can
 * tell which numbers behind a derived band were fitted and which were inherited.
 *
 * ## The family was tested for an effect and does not have one
 *
 * The ten rows in `entourage_data.json` have `maxTempC - boilingPointC` between
 * 18 °C and 29 °C. Split by family, the eight monoterpenes run 22–28 °C and the
 * two sesquiterpenes run 18–29 °C: the monoterpene mean is 24.4 °C and the
 * sesquiterpene mean is 23.5 °C, which is **inside the monoterpene spread**.
 * There is no per-family effect to fit, and with 8 and 2 rows a fitted constant
 * would be a number the data does not support. So every family resolves to the
 * same 30 °C — the widest margin any shipped row shows, rounded up to the
 * display grid — and the family is reported rather than fitted.
 *
 * That is the coarse direction on purpose: the model is never more optimistic
 * than the most generous measured row, which is the only defensible side to be
 * wrong on when the evidence is one boiling point.
 *
 * ## `UNCLASSIFIED` is a real bucket, not an error bucket
 *
 * `Terpene.family` is documented as possibly carrying `Triterpeno`, and the
 * KDoc on `AssetContent.Terpene` already lists a fourth family the catalog
 * never ships. A row naming one gets the same inherited band and a visible
 * "unclassified" label, never a narrower one a `when` guessed for it.
 */
enum class TerpeneFamily(
    val labelEs: String,
    /** Degrees of headroom above the boiling point a derived band allows. */
    val derivedHeadroomC: Int,
    /** Rows in `entourage_data.json` behind this family. */
    val shippedRowCount: Int
) {
    MONOTERPENE("Monoterpeno", 30, 8),
    SESQUITERPENE("Sesquiterpeno", 30, 2),

    /** No shipped row, so nothing was measured and nothing was fitted. */
    DITERPENE("Diterpeno", 30, 0),

    /** See the type KDoc. Inherited, never narrower than a measured band. */
    UNCLASSIFIED("Sin clasificar", 30, 0);

    /** Whether any shipped row was measured for this family. */
    val hasMeasuredRow: Boolean get() = shippedRowCount > 0

    companion object {
        /** The catalog's own `family` string, or [UNCLASSIFIED]. */
        fun fromFamilyEs(familyEs: String?): TerpeneFamily {
            val haystack = familyEs?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.labelEs.lowercase() == haystack } ?: UNCLASSIFIED
        }
    }
}

/* ── The window ────────────────────────────────────────────────────────── */

/**
 * The temperature band for **one** compound.
 *
 * Do not confuse this with [TerpeneWindow], which is the aggregate over a
 * *selection*. One band is a property of a molecule; the aggregate is a
 * property of what the user picked.
 *
 * The two invariants are [TerpeneVaporisation]'s, kept here as well so a band
 * built by [VolatilityDerivation] is held to the same rule as one that ships:
 *
 * ```
 *   minTempC <= boilingPointC < maxTempC
 * ```
 *
 * The first half is the documented invariant — the useful window opens at the
 * boiling point, and 9 of the 10 shipped rows sit exactly on that boundary. The
 * second is what stops a band from closing on itself.
 */
data class VolatilityWindow(
    /** Lowest useful working temperature, °C. */
    val minTempC: Int,
    /** Highest temperature before the aroma is spent, °C. */
    val maxTempC: Int,
    /** Which of the two kinds of window this is. There is no default. */
    val provenance: VolatilityProvenance
) {
    init {
        require(maxTempC > minTempC) {
            "a window of $minTempC–$maxTempC °C is not a window"
        }
    }

    /** Width in °C. A derived window is never narrower than 2 x [STEP_C]. */
    val widthC: Int get() = maxTempC - minTempC

    val isDerived: Boolean get() = provenance == VolatilityProvenance.DERIVED

    /** Whether [temperatureC] falls inside the band. */
    fun contains(temperatureC: Int): Boolean = temperatureC in minTempC..maxTempC

    /** `"156–180 °C"`, or `"≈ 160–200 °C"` when the band is derived. */
    fun formatEs(): String {
        val body = "$minTempC–$maxTempC °C"
        return if (isDerived) "${TerpeneVolatilityCopy.ESTIMATE_MARK} $body" else body
    }
}

/**
 * Builds a band for a compound that ships a boiling point but no window.
 *
 * ## The model, in full, because a model nobody can reconstruct is a rumour
 *
 * ```
 *   floor   = 10 * floor(boilingPointC / 10)                      // round DOWN
 *   ceiling = 10 * ceil((boilingPointC + headroomC) / 10)         // round UP
 *   headroomC = family.derivedHeadroomC
 * ```
 *
 * Two rules, and neither of them is free:
 *
 * 1. **The floor is the compound's own boiling point**, rounded down to the
 *    display grid. Nothing is inferred about *where* a compound starts coming
 *    off the element: that is the boiling point, and the shipped table says so
 *    in 9 of its 10 rows. The rounding is the only concession to coarseness,
 *    and it rounds *down*, so the band never claims a compound starts before
 *    it physically can.
 * 2. **The headroom is the widest margin the shipped table shows**, for every
 *    family. This is the part that is a judgement, so it is stated as one: the
 *    ten `entourage_data.json` rows run `maxTempC - boilingPointC` from 18 °C to
 *    29 °C, and [DERIVED_HEADROOM_C] is that 29 °C rounded up to the display
 *    grid. The family does not move it — see [TerpeneFamily]'s KDoc, which
 *    reports the measurement that showed there is no per-family effect to fit.
 *
 * ## Why the band is coarse on purpose
 *
 * The within-family spread of boiling points in the shipped catalog is the
 * honest reason the number cannot be finer:
 *
 * | family | compounds | boiling-point range | spread |
 * | --- | --- | --- | --- |
 * | Monoterpeno | 85 | 131–285 °C | 154 °C |
 * | Sesquiterpeno | 63 | 166–307 °C | 141 °C |
 * | Diterpeno | 10 | 300–350 °C | 50 °C |
 *
 * A monoterpene family label cannot tell you whether a compound boils at 156 °C
 * or 285 °C. Any window quoted to the degree from that label is a fabrication,
 * which is why:
 *
 *  - the derived band is quantised to [STEP_C], so the narrowest it can ever be
 *    is 30 °C wide — **at least as wide as every band the app actually
 *    measures** (the widest shipped one, caryophyllene's 250–280 °C, is 30 °C);
 *  - its headroom is never less than the smallest margin any shipped row adds,
 *    so the band is never the tighter promise;
 *  - its ceiling is a rounded *setpoint*, exactly what the shipped ceilings are:
 *    every one of them is a multiple of 5, which is a device's dial and not a
 *    thermodynamic constant.
 *
 * ## What it cannot tell you
 *
 * Yield, recovery time, aroma quality, whether the compound is in your flower,
 * whether your device reaches the temperature, and anything at all about the
 * spread *inside* the family — see the type KDoc. It also cannot tell you the
 * band is right: no published per-compound extraction window for these 148 is
 * recorded anywhere in this repository, so nothing here cites one. That is the
 * whole reason the value is labelled `DERIVED`.
 */
object VolatilityDerivation {

    /** Display resolution of a derived band, °C. Both ends are quantised to it. */
    const val STEP_C: Int = 10

    /**
     * Headroom every derived band allows above its boiling point, °C.
     *
     * The widest margin the shipped table shows (29 °C), rounded up to
     * [STEP_C]. Every family resolves to this; see [TerpeneFamily].
     */
    const val DERIVED_HEADROOM_C: Int = 30

    /** The narrowest band this model can produce, °C. Proven in a test. */
    const val NARROWEST_DERIVED_WIDTH_C: Int = 30

    /** Largest `maxTempC - boilingPointC` in the shipped table, °C. */
    const val WIDEST_SHIPPED_HEADROOM_C: Int = 29

    /** Smallest `maxTempC - boilingPointC` in the shipped table, °C. */
    const val NARROWEST_SHIPPED_HEADROOM_C: Int = 18

    /** The floor for a boiling point: itself, rounded **down** to [STEP_C]. */
    fun floorFor(boilingPointC: Int): Int =
        ((boilingPointC / STEP_C) * STEP_C)

    /** The ceiling for a boiling point in [family], rounded **up** to [STEP_C]. */
    fun ceilingFor(boilingPointC: Int, family: TerpeneFamily): Int {
        val target = boilingPointC + family.derivedHeadroomC
        return ((target + STEP_C - 1) / STEP_C) * STEP_C
    }

    /**
     * The derived band for [boilingPointC] in [family].
     *
     * Rounding the floor down and the ceiling up means the result always
     * satisfies `minTempC <= boilingPointC < maxTempC`, which is
     * [TerpeneVaporisation]'s invariant — a derivation can never produce a band
     * the shipped table would have rejected.
     */
    fun derive(boilingPointC: Int, family: TerpeneFamily): VolatilityWindow =
        VolatilityWindow(
            minTempC = floorFor(boilingPointC),
            maxTempC = ceilingFor(boilingPointC, family),
            provenance = VolatilityProvenance.DERIVED
        )
}

/* ── The one view type ─────────────────────────────────────────────────── */

/**
 * One catalog compound's volatility: the boiling point, the band, and where the
 * band came from.
 *
 * This is the only type a screen needs to render volatility. It replaced two
 * models reading two assets, and the identity join is
 * [EntourageTerpene.catalogId] — the encyclopedia id, which F1 declared
 * canonical for the boiling point.
 */
data class TerpeneVolatility(
    val catalogId: String,
    val labelEs: String,
    val family: TerpeneFamily,
    /** Molar mass as the catalog writes it, e.g. `"136.24"`. Verbatim. */
    val molarMassEs: String,
    /**
     * The measured boiling point, always from `terpenes.json`.
     *
     * Canonical per F1, including for the ten compounds whose band the module
     * ships: [TerpeneVolatilityIndex] takes the *band* from the module and the
     * *point* from the encyclopedia, so a future divergence cannot produce a
     * band whose invariant no longer holds.
     */
    val boilingPointC: Int,
    /** The catalog's own display string, e.g. `"167 °C"`. Verbatim. */
    val boilingPointEs: String,
    val window: VolatilityWindow,
    /** The module's note for a measured band. Empty for a derived one. */
    val noteEs: String = ""
) {
    init {
        require(window.minTempC <= boilingPointC) {
            "$catalogId: window floor (${window.minTempC}) is above the boiling point ($boilingPointC)"
        }
        require(window.maxTempC > boilingPointC) {
            "$catalogId: window ceiling (${window.maxTempC}) is not above the boiling point ($boilingPointC)"
        }
    }

    val provenance: VolatilityProvenance get() = window.provenance
    val isDerived: Boolean get() = window.isDerived

    /** The same compound as a curve step. */
    fun step(): VolatilityStep = VolatilityStep(
        catalogId = catalogId,
        labelEs = labelEs,
        family = family,
        boilingPointC = boilingPointC,
        window = window
    )

    /** The Séquito module's own view of this compound, when it models it. */
    val entourageTerpene: EntourageTerpene? get() = EntourageTerpene.fromCatalogId(catalogId)
}

/**
 * The 158-row source.
 *
 * Built once from both assets and read by every screen, so a compound cannot
 * have one volatility on the detail page and another in the module. This is the
 * single index F2 introduces; nothing else is allowed to hold a second.
 *
 * @param rows every catalog compound that has a readable boiling point.
 * @param withoutBoilingPoint the ids dropped because the catalog's
 *   `boilingPoint` could not be read as a temperature. Reported, never silent,
 *   following [EntourageContent.unresolvedReferences]. The shipped asset must
 *   produce an empty list.
 */
class TerpeneVolatilityIndex(
    val rows: List<TerpeneVolatility>,
    val withoutBoilingPoint: List<String> = emptyList()
) {
    private val byId: Map<String, TerpeneVolatility> = rows.associateBy { it.catalogId }

    val size: Int get() = rows.size

    /** How many bands are measured and how many are derived. */
    val measuredCount: Int get() = rows.count { !it.isDerived }
    val derivedCount: Int get() = rows.size - measuredCount

    /** The compound's volatility, or null when [catalogId] is not in the index. */
    fun forId(catalogId: String?): TerpeneVolatility? = catalogId?.let { byId[it] }

    /** Every compound of [family], least volatile last. */
    fun forFamily(family: TerpeneFamily): List<TerpeneVolatility> =
        rows.filter { it.family == family }.sortedBy { it.boilingPointC }

    /**
     * The curve for [catalogIds], in the order given, skipping unknown ids and
     * duplicates.
     *
     * An empty selection is a curve with no steps, which reports itself as
     * empty rather than throwing — the caller decides what an empty answer
     * means, the same way [EntouragePlanner.windowFor] returns null instead of
     * failing on a selection it cannot score.
     */
    fun curveFor(catalogIds: Collection<String>): VolatilityCurve =
        VolatilityCurve(catalogIds.distinct().mapNotNull { byId[it]?.step() })

    companion object {
        /**
         * Fuses the encyclopedia's boiling points with the module's bands.
         *
         * @param catalog one row per catalog compound: id, name, family,
         *   molar mass and the `boilingPoint` display string.
         * @param measured the module's shipped table. A catalog row its
         *   `catalogId` matches becomes [VolatilityProvenance.MEASURED] with the
         *   module's own band and note; everything else becomes
         *   [VolatilityProvenance.DERIVED].
         */
        fun from(
            catalog: List<CatalogVolatilityRow>,
            measured: List<TerpeneVaporisation>
        ): TerpeneVolatilityIndex {
            val measuredByCatalogId = measured.associate { it.terpene.catalogId to it }
            val rows = mutableListOf<TerpeneVolatility>()
            val dropped = mutableListOf<String>()

            catalog.forEach { entry ->
                val boilingPointC = BoilingPointParser.parseCelsius(entry.boilingPointEs)
                if (boilingPointC == null) {
                    dropped += entry.catalogId
                    return@forEach
                }
                val shipped = measuredByCatalogId[entry.catalogId]
                val window = shipped?.let {
                    // The band is the module's; the boiling point is the
                    // encyclopedia's. F1 pinned them equal and its test holds
                    // them equal, so taking the point from the catalog cannot
                    // change a shipped row — it only stops a future divergence
                    // from producing a band that fails the invariant above.
                    VolatilityWindow(
                        minTempC = it.minTempC,
                        maxTempC = it.maxTempC,
                        provenance = VolatilityProvenance.MEASURED
                    )
                } ?: VolatilityDerivation.derive(boilingPointC, entry.family)

                rows += TerpeneVolatility(
                    catalogId = entry.catalogId,
                    labelEs = entry.labelEs,
                    family = entry.family,
                    molarMassEs = entry.molarMassEs,
                    boilingPointC = boilingPointC,
                    boilingPointEs = entry.boilingPointEs,
                    window = window,
                    noteEs = shipped?.noteEs.orEmpty()
                )
            }

            return TerpeneVolatilityIndex(rows, dropped)
        }
    }
}

/**
 * The encyclopedia row a [TerpeneVolatility] is built from.
 *
 * A separate type from `data.repository.Terpene` on purpose: that class lives in
 * a file that imports `android.content.Context`, and the derivation must stay
 * assertable from the JVM with no Android on the classpath. The repository maps
 * one into the other.
 */
data class CatalogVolatilityRow(
    val catalogId: String,
    val labelEs: String,
    val family: TerpeneFamily,
    val molarMassEs: String,
    val boilingPointEs: String
)

/* ── The curve ─────────────────────────────────────────────────────────── */

/**
 * One compound's place in a staged temperature plan.
 *
 * A band is a property of a molecule; a step is that band positioned against
 * the others the user picked. The curve is what makes "at what point does each
 * one come off, and what do I lose by reaching the last" answerable.
 */
data class VolatilityStep(
    val catalogId: String,
    val labelEs: String,
    val family: TerpeneFamily,
    val boilingPointC: Int,
    val window: VolatilityWindow
) {
    /** Temperature at which this compound starts to come off the element. */
    val opensAtC: Int get() = window.minTempC

    /** Temperature past which this compound is spent. */
    val closesAtC: Int get() = window.maxTempC

    val provenance: VolatilityProvenance get() = window.provenance
    val isDerived: Boolean get() = window.isDerived
    val entourageTerpene: EntourageTerpene? get() = EntourageTerpene.fromCatalogId(catalogId)

    fun volatility(): TerpeneVolatility = TerpeneVolatility(
        catalogId = catalogId,
        labelEs = labelEs,
        family = family,
        molarMassEs = "",
        boilingPointC = boilingPointC,
        boilingPointEs = "$boilingPointC °C",
        window = window
    )
}

/** The aggregate every caller shares, so it is defined exactly once. */
data class CurveAggregate(
    /** The highest floor in the selection: what you must reach for the last one. */
    val floorC: Int,
    /** The lowest ceiling in the selection: what the most fragile one can take. */
    val ceilingC: Int,
    /** [floorC] <= [ceilingC]. False means no single pass keeps them all. */
    val isViable: Boolean,
    val steps: List<VolatilityStep>
)

/**
 * One rung of the curve.
 *
 * [pending] is the useful half: what has not come off yet at [opensAtC]. It is
 * what turns "no se puede en una pasada" from a boolean into a list.
 */
data class VolatilityStage(
    val step: VolatilityStep,
    val opensAtC: Int,
    val closesAtC: Int,
    /** Compounds already off the element at [opensAtC], this one included. */
    val arrived: List<VolatilityStep>,
    /** Compounds that have not started yet. */
    val pending: List<VolatilityStep>
)

/**
 * Where one compound's band sits on a shared temperature scale.
 *
 * Pure geometry, extracted from the composable so it can be asserted from the
 * JVM. Compose has no unit-test runtime in this project — only device-only
 * `androidTest` — so anything a bar does with numbers belongs here.
 *
 * ## Both fractions are strictly positive, and that is enforced here
 *
 * These two values are consumed by `RowScope.weight`, which **rejects zero**:
 * `Modifier.weight(0f)` throws `IllegalArgumentException: invalid weight 0.0;
 * must be greater than zero` while composing. It shipped that way — the first
 * step of any curve opens at the scale floor, so its start fraction was
 * exactly `0f`, and every terpene detail page crashed on open. The failure was
 * invisible to the whole test suite because the throw happens in the renderer,
 * and this project can only test the renderer on a device.
 *
 * So the constraint is asserted where it can be asserted. [require] turns "the
 * renderer will throw" into a JVM-testable construction failure, and
 * [barFor]'s [MIN_FRACTION] floor keeps the value legal in the first place. The
 * floor is far below one rendered pixel at any realistic width, so clamping to
 * it does not move the bar.
 */
data class VolatilityBar(val startFraction: Float, val widthFraction: Float) {
    init {
        require(startFraction > 0f) {
            "startFraction must be > 0 for RowScope.weight, was $startFraction"
        }
        require(startFraction <= 1f) { "startFraction must be a fraction, was $startFraction" }
        require(widthFraction > 0f) {
            "widthFraction must be > 0 for RowScope.weight, was $widthFraction"
        }
    }

    companion object {
        /**
         * Smallest fraction a bar may carry.
         *
         * Not zero — see the class KDoc — and not large enough to be visible:
         * a 360 dp track at 0.0001 is under a tenth of a pixel, so the leading
         * gap and a zero-width band both render as nothing while staying legal
         * for the composable.
         */
        const val MIN_FRACTION = 0.0001f
    }
}

/**
 * The staged arrival of a selection, as steps.
 *
 * ## The aggregate is derived from the steps, not computed beside them
 *
 * [floorC], [ceilingC] and [isViable] all read [aggregate], which
 * [VolatilityCurves.aggregate] defines once over the step list. The module's
 * [EntouragePlanner.windowFor] calls the *same* function. That is the entire
 * point: two implementations of "the single band that keeps every selected
 * compound in play" is two truths about the same number, which is the defect F1
 * closed on the boiling point. Here the disagreement is structurally impossible
 * — the aggregate is a property of the steps, so it cannot disagree with them.
 *
 * ## The loss case, stated rather than encoded in a boolean
 *
 * [lostSteps] is the answer to "what do I lose by going hot enough to reach the
 * last one": every compound whose ceiling is already below the floor the
 * selection demands. That is a fact about the selection, not a prediction, and
 * when [isViable] is false it is usually not empty — reporting only the boolean
 * hid the information the user needed.
 */
class VolatilityCurve(steps: List<VolatilityStep>) {

    /**
     * The steps, least volatile first.
     *
     * Sorted by floor, then boiling point, then id, so the order is a total one
     * and two compounds that share a band always come out in the same order.
     */
    val steps: List<VolatilityStep> = steps.sortedWith(
        compareBy<VolatilityStep> { it.opensAtC }
            .thenBy { it.boilingPointC }
            .thenBy { it.catalogId }
    )

    private val aggregate: CurveAggregate? = VolatilityCurves.aggregate(this.steps)

    val isEmpty: Boolean get() = aggregate == null
    val isNotEmpty: Boolean get() = !isEmpty

    /** Temperature the hardest compound needs, °C. Null when there are no steps. */
    val floorC: Int? get() = aggregate?.floorC

    /** Temperature the most fragile compound can take, °C. */
    val ceilingC: Int? get() = aggregate?.ceilingC

    /** Whether one pass keeps them all. Null-window is false by definition. */
    val isViable: Boolean get() = aggregate?.isViable == true

    /** Compounds already spent before [floorC] is reached. */
    val lostSteps: List<VolatilityStep>
        get() = floorC?.let { floor -> steps.filter { it.closesAtC < floor } }.orEmpty()

    /** Earliest temperature anything in the selection starts. */
    val scaleMinC: Int get() = steps.minOfOrNull { it.opensAtC } ?: 0

    /** Latest temperature anything in the selection is still working. */
    val scaleMaxC: Int get() = steps.maxOfOrNull { it.closesAtC } ?: 0

    /** How many bands in this curve are derived rather than measured. */
    val derivedCount: Int get() = steps.count { it.isDerived }

    /** The steps as rungs, each carrying what is still to come. */
    val stages: List<VolatilityStage> = steps.map { step ->
        VolatilityStage(
            step = step,
            opensAtC = step.opensAtC,
            closesAtC = step.closesAtC,
            arrived = steps.filter { it.opensAtC <= step.opensAtC },
            pending = steps.filter { it.opensAtC > step.opensAtC }
        )
    }

    /**
     * Where [step]'s band sits on [scaleMinC]..[scaleMaxC].
     *
     * Both fractions are **strictly positive**, and that is not a nicety: the
     * first step of a curve opens at the scale floor, so its raw start fraction
     * is exactly `0f`, and `RowScope.weight` throws on zero. "Non-negative" is
     * what this KDoc used to promise, and it shipped a crash on every terpene
     * page. See [VolatilityBar].
     */
    fun barFor(step: VolatilityStep): VolatilityBar {
        val span = (scaleMaxC - scaleMinC).coerceAtLeast(1)
        return VolatilityBar(
            startFraction = fractionOf(step.opensAtC - scaleMinC, span),
            widthFraction = fractionOf(step.closesAtC - step.opensAtC, span)
        )
    }

    /**
     * A share of the scale, clamped into the range [VolatilityBar] accepts.
     *
     * Clamping to [VolatilityBar.MIN_FRACTION] rather than zero is what keeps
     * the first bar — the most common one on screen — from closing the page,
     * and the floor is far below a rendered pixel so the geometry is unchanged.
     */
    private fun fractionOf(numerator: Int, span: Int): Float =
        (numerator.toFloat() / span).coerceIn(VolatilityBar.MIN_FRACTION, 1f)

    /** The step for a catalog id, so a screen can highlight the subject. */
    fun stepFor(catalogId: String?): VolatilityStep? =
        catalogId?.let { id -> steps.firstOrNull { it.catalogId == id } }

    override fun toString(): String =
        "VolatilityCurve(${steps.size} steps, " +
            "${floorC ?: "-"}–${ceilingC ?: "-"} °C, viable=$isViable)"

    override fun equals(other: Any?): Boolean = other is VolatilityCurve && other.steps == steps

    override fun hashCode(): Int = steps.hashCode()
}

/**
 * Builds curves and the one aggregate rule they all read.
 *
 * An object with one real job, and [aggregate] is the job. It is called from
 * [VolatilityCurve] and from [EntouragePlanner.windowFor] so the module's
 * aggregate window and a catalog curve cannot compute the same number two ways.
 */
object VolatilityCurves {

    /**
     * The single aggregate rule: highest floor, lowest ceiling, viable when
     * they do not cross.
     *
     * Null for an empty step list, which is what [EntouragePlanner.windowFor]
     * returns for a selection it cannot score and what [VolatilityCurve] reports
     * as empty. One null, two callers, one meaning: there is nothing to say.
     */
    fun aggregate(steps: List<VolatilityStep>): CurveAggregate? {
        if (steps.isEmpty()) return null
        val floorC = steps.maxOf { it.opensAtC }
        val ceilingC = steps.minOf { it.closesAtC }
        return CurveAggregate(
            floorC = floorC,
            ceilingC = ceilingC,
            isViable = floorC <= ceilingC,
            steps = steps
        )
    }

    /** A curve over [steps]. */
    fun of(steps: List<VolatilityStep>): VolatilityCurve = VolatilityCurve(steps)
}

/* ── Copy ──────────────────────────────────────────────────────────────── */

/**
 * What the terpene detail page says about volatility, as data.
 *
 * A model and not a composable for the reason [ClimateCardCopy] gives: a card's
 * wording cannot be asserted from the JVM where it lives in a composable, and
 * the wording is the part that can silently be wrong. Here the part that can
 * silently be wrong is *the absence of a provenance marker*, so [isDerived] is
 * carried here and [windowEs] cannot be assembled anywhere else.
 */
data class TerpeneVolatilityContent(
    /** The catalog's own string, verbatim. Always a measured value. */
    val boilingPointEs: String,
    /** `"≈ 160–200 °C"` when derived, `"156–180 °C"` when measured. */
    val windowEs: String,
    /** [VolatilityProvenance.labelEs]. */
    val provenanceLabelEs: String,
    /** The module's own note for a measured band; empty when derived. */
    val noteEs: String,
    /** Always visible. Says what the band is and where it came from. */
    val evidenceEs: String,
    /** Always visible. Says what a temperature band is not. */
    val limitsEs: String,
    val isDerived: Boolean
) {
    /** Whether the evidence line may be drawn. It always may. */
    val isDrawable: Boolean get() = windowEs.isNotBlank() && evidenceEs.isNotBlank()
}

/**
 * The volatility copy, in Spanish.
 *
 * Follows [ClimateCardCopyFormatter] exactly, because the failure it prevents
 * is the same: a number that looks like a reading. Two rules carried over:
 *
 *  - every **estimated** number is prefixed with [ESTIMATE_MARK], in the same
 *    place, so no call site can print a bare band;
 *  - the sentence explaining where the number came from is a field of the model,
 *    so it arrives with the number and cannot be dropped from one card and kept
 *    on another.
 *
 * Note what is *not* marked: the boiling point. It is read from the encyclopedia
 * and it is measured on all 158 compounds, including the ones whose band is
 * derived. The evidence line says so in those sentences, because "estimated"
 * applied to the whole card would be its own kind of inaccuracy.
 */
object TerpeneVolatilityCopy {

    /** Marker prefixed to every estimated number. Same marker the climate card uses. */
    const val ESTIMATE_MARK: String = "≈"

    private const val DERIVED_TEMPLATE =
        "Ventana estimada por la app: el punto de ebullición (%s) está medido y " +
            "viene de la enciclopedia, pero la banda %s se ha calculado a partir de " +
            "él y de la familia %s. No es una ventana medida: sirve para comparar y " +
            "ordenar, no para fijar una temperatura."

    private const val MEASURED_TEMPLATE =
        "Ventana medida: la banda %s viene de la tabla de Séquito y el punto de " +
            "ebullición (%s) de la enciclopedia."

    /**
     * Always visible, on measured and derived bands alike.
     *
     * The three things a temperature band is routinely mistaken for, and is not:
     * a yield figure, a time, and a device capability.
     */
    const val LIMITS_ES: String =
        "Una ventana de temperatura no dice cuánto rinde el compuesto, cuánto " +
            "dura el aroma ni si tu equipo alcanza esa temperatura."

    /** Spanish `"156–180 °C"`. */
    fun formatWindowEs(window: VolatilityWindow): String = window.formatEs()

    /** The card's copy for one compound. */
    fun contentOf(volatility: TerpeneVolatility): TerpeneVolatilityContent {
        val windowEs = volatility.window.formatEs()
        return TerpeneVolatilityContent(
            boilingPointEs = volatility.boilingPointEs,
            windowEs = windowEs,
            provenanceLabelEs = volatility.provenance.labelEs,
            noteEs = volatility.noteEs,
            evidenceEs = if (volatility.isDerived) {
                String.format(
                    Locale.US,
                    DERIVED_TEMPLATE,
                    "${volatility.boilingPointC} °C",
                    windowEs,
                    volatility.family.labelEs
                )
            } else {
                String.format(
                    Locale.US,
                    MEASURED_TEMPLATE,
                    windowEs,
                    "${volatility.boilingPointC} °C"
                )
            },
            limitsEs = LIMITS_ES,
            isDerived = volatility.isDerived
        )
    }
}

/**
 * The curve card's copy, as data.
 *
 * The scope line is the one that matters most: a curve is only meaningful
 * against the selection it was built from, and a card showing four rungs without
 * saying which four compounds they are would read as a profile.
 */
data class VolatilityCurveContent(
    val titleEs: String,
    /** Which compounds this curve covers, in words. */
    val scopeEs: String,
    /** One line per rung, ascending. */
    val stageLinesEs: List<String>,
    /** The single band, or the contradiction. */
    val aggregateEs: String,
    /** Non-empty when the selection cannot be done in one pass. */
    val contradictionEs: String,
    /** Non-empty when any band in the curve is derived. */
    val derivedWarningEs: String
) {
    val isDrawable: Boolean get() = stageLinesEs.isNotEmpty() && scopeEs.isNotBlank()
}

/** Builds the curve card's copy. Spanish, and marked the same way. */
object VolatilityCurveCopy {

    private const val TITLE_ES = "🌡️ Curva de calor"

    private const val SCOPE_WITH_SUBJECT_ES =
        "Escalonado de %s y de los %d compuestos con los que la enciclopedia lo " +
            "asocia. No es un plan de todo el perfil."

    private const val SCOPE_SINGLE_ES =
        "Un solo compuesto no tiene curva: %s y nada más. Un escalonado necesita " +
            "al menos dos."

    private const val AGGREGATE_VIABLE_ES =
        "Una sola pasada entre %d y %d °C mantiene los %d compuestos."

    private const val AGGREGATE_LOST_ES =
        "Hay que llegar a %d °C por el compuesto más tardío, y a esa temperatura " +
            "ya se fueron %s."

    private const val DERIVED_WARNING_ES =
        "%d de %d ventanas de esta curva son estimaciones de la app; el resto " +
            "viene de la tabla de Séquito."

    private const val PENDING_ES = "Después siguen: %s"

    /** The card's copy for [curve] about [subjectLabelEs]. */
    fun contentOf(
        curve: VolatilityCurve,
        subjectLabelEs: String,
        partnerCount: Int
    ): VolatilityCurveContent {
        val stageLines = curve.stages.map { stage ->
            val range = stage.step.window.formatEs()
            val pending = stage.pending.map { it.labelEs }
            val head = "$range · ${stage.step.labelEs}"
            if (pending.isEmpty()) {
                head
            } else {
                "$head — ${String.format(Locale.US, PENDING_ES, pending.joinToString(", "))}"
            }
        }

        val floor = curve.floorC
        val ceiling = curve.ceilingC
        val lost = curve.lostSteps.map { it.labelEs }
        val viable = curve.isViable && lost.isEmpty()

        return VolatilityCurveContent(
            titleEs = TITLE_ES,
            scopeEs = if (partnerCount == 0) {
                String.format(Locale.US, SCOPE_SINGLE_ES, subjectLabelEs)
            } else {
                String.format(Locale.US, SCOPE_WITH_SUBJECT_ES, subjectLabelEs, partnerCount)
            },
            stageLinesEs = stageLines,
            aggregateEs = if (floor == null || ceiling == null) {
                ""
            } else if (viable) {
                String.format(Locale.US, AGGREGATE_VIABLE_ES, floor, ceiling, curve.steps.size)
            } else {
                String.format(
                    Locale.US,
                    AGGREGATE_LOST_ES,
                    floor,
                    if (lost.isEmpty()) "el compuesto más frágil" else lost.joinToString(", ")
                )
            },
            contradictionEs = if (curve.isViable) {
                ""
            } else {
                "No entra en una sola pasada: el mínimo más alto ($floor °C) está " +
                    "por encima del máximo más bajo ($ceiling °C). Alguien tiene " +
                    "que sacrificar un compuesto."
            },
            derivedWarningEs = if (curve.derivedCount == 0) {
                ""
            } else {
                String.format(Locale.US, DERIVED_WARNING_ES, curve.derivedCount, curve.steps.size)
            }
        )
    }
}