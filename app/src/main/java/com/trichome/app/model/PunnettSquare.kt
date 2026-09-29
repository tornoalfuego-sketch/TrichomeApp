package com.trichome.app.model

import kotlin.math.roundToInt

/* ─────────────────────────── The genetics ───────────────────────────────── */

/**
 * One copy of a gene at a single locus.
 *
 * [DOMINANT] is written `A` and [RECESSIVE] `a`. This is the classical
 * upper/lower-case notation, and it is the only notation the app uses: a
 * genotype is always spelled with the dominant allele first, so `Aa` and `aA`
 * are the same genotype and only ever one value.
 */
enum class Allele(val symbol: Char) {
    /** `A`: one copy is enough for the trait to show. */
    DOMINANT('A'),

    /** `a`: the trait shows only when both copies are recessive. */
    RECESSIVE('a');

    val isDominant: Boolean get() = this == DOMINANT
}

/** What the plant actually looks like, as opposed to what it carries. */
enum class Phenotype { DOMINANT, RECESSIVE }

/**
 * A parent reduced to the thing the square needs: the allele pair at one locus.
 *
 * This is a deliberately tiny model and it is stated here rather than implied:
 *
 * - **One locus, two alleles.** [Allele.DOMINANT] and [Allele.RECESSIVE] are
 *   the whole vocabulary. The app does not model linkage, polygenic traits,
 *   incomplete dominance, sex linkage or mitochondrial inheritance, because a
 *   simulator that pretends to cover them teaches the wrong thing. A square
 *   here answers exactly one question: *given these two parents, what fraction
 *   of the offspring carries this genotype?*
 * - **Complete dominance.** `Aa` looks like `AA`. That is what makes the
 *   phenotype ratio and the genotype ratio different, and it is the assumption
 *   behind every number this file produces.
 * - **Standard Mendelian segregation.** A heterozygote hands down its two
 *   alleles in equal proportion, 1:1. A homozygote can only hand down the one
 *   allele it carries, at probability 1.
 * - **No selection effect.** Nothing here accounts for viability, germination
 *   or environment. The numbers are the expected ratios over infinite,
 *   unselected offspring.
 *
 * Genotypes are canonicalised through [of]: the dominant allele is always
 * stored first.
 */
data class Genotype(val a: Allele, val b: Allele) {

    /** The canonical spelling, e.g. `Aa`. */
    val notation: String get() = "${a.symbol}${b.symbol}"

    val isHomozygous: Boolean get() = a == b

    val isCarrier: Boolean get() = !isHomozygous

    /**
     * The phenotype, under complete dominance.
     *
     * A heterozygote is [Phenotype.DOMINANT] *and* a carrier: those are two
     * different facts, and collapsing them is the mistake that produces a
     * "2:1" instead of a "1:2:1".
     */
    val phenotype: Phenotype
        get() = if (a.isDominant) Phenotype.DOMINANT else Phenotype.RECESSIVE

    /**
     * The gametes this parent can produce, with their Mendelian probabilities.
     *
     * A homozygote yields a single gamete at probability 1. A heterozygote
     * yields one dominant and one recessive gamete, 1:1, which is the whole
     * content of Mendel's law of segregation.
     */
    fun gametes(): List<Gamete> = when {
        isHomozygous -> listOf(Gamete(a, 1.0))
        else -> listOf(Gamete(Allele.DOMINANT, 0.5), Gamete(Allele.RECESSIVE, 0.5))
    }

    companion object {
        // Named DOMINANT_HOMOLOGUE etc. rather than `AA`/`Aa`/`aa`: a companion
        // property `Aa` generates a JVM getter `getAa()` that collides with the
        // allele accessor's `getA()`, which the Kotlin compiler cannot see and
        // rejects as a platform declaration clash.
        val DOMINANT_HOMOLOGUE = Genotype(Allele.DOMINANT, Allele.DOMINANT)
        val HETEROZYGOUS = Genotype(Allele.DOMINANT, Allele.RECESSIVE)
        val RECESSIVE_HOMOLOGUE = Genotype(Allele.RECESSIVE, Allele.RECESSIVE)

        /**
         * Builds a canonical genotype: the dominant allele first.
         *
         * Going through here rather than through the constructor is what makes
         * `aA` and `Aa` the same value, so equality and the resulting square
         * cannot depend on which letter the user typed first.
         */
        fun of(a: Allele, b: Allele): Genotype = if (a.isDominant) Genotype(a, b) else Genotype(b, a)
    }
}

/** One allele a parent can pass on, with the chance of passing it. */
data class Gamete(val allele: Allele, val probability: Double)

/** One of the pairs in the square, with the chance of the offspring it produces. */
data class PunnettCell(
    /** Allele from the first parent, as written on the axis. */
    val fromFirst: Char,
    /** Allele from the second parent, as written on the axis. */
    val fromSecond: Char,
    val genotype: Genotype,
    val probability: Double
)

/**
 * The completed cross: every cell plus the three probabilities the grower
 * actually asked for.
 *
 * - [dominantPhenotypeProbability] — shows the dominant trait.
 * - [recessivePhenotypeProbability] — shows the recessive trait.
 * - [carrierProbability] — heterozygous. Strictly larger than the recessive
 *   probability, because a carrier *also* shows the dominant trait; that is the
 *   single most misreported number in breeding circles, so it is tracked
 *   separately rather than derived from the other two.
 *
 * The cells are row-major over [rows] x [columns], so [cell] can index them
 * without searching.
 */
class PunnettSquare internal constructor(
    val first: Genotype,
    val second: Genotype,
    val cells: List<PunnettCell>,
    val dominantPhenotypeProbability: Double,
    val recessivePhenotypeProbability: Double,
    val carrierProbability: Double
) {
    val rows: Int get() = first.gametes().size
    val columns: Int get() = second.gametes().size

    fun cell(row: Int, col: Int): PunnettCell = cells[row * columns + col]

    /** Dominant:recessive, reduced to the smallest honest integers, e.g. `3:1`. */
    fun phenotypeRatioEs(): String {
        val dominant = caseUnits(dominantPhenotypeProbability)
        val recessive = caseUnits(recessivePhenotypeProbability)
        val divisor = gcdOf(dominant, recessive)
        return "$dominant:$recessive".let { if (divisor > 1) divide(it, divisor) else it }
    }

    /** AA:Aa:aa, e.g. `1:2:1`. */
    fun genotypeRatioEs(): String {
        val homoDominant = caseUnits(
            cells.filter { it.genotype.isHomozygous && it.genotype.a.isDominant }
                .sumOf { it.probability }
        )
        val heterozygous = caseUnits(carrierProbability)
        val homoRecessive = caseUnits(
            cells.filter { it.genotype.isHomozygous && !it.genotype.a.isDominant }
                .sumOf { it.probability }
        )
        val divisor = gcdOf(homoDominant, heterozygous, homoRecessive)
        return "$homoDominant:$heterozygous:$homoRecessive"
            .let { if (divisor > 1) divide(it, divisor) else it }
    }
}

/**
 * Result of a cross that may not be computable.
 *
 * Nonsense input is a value, never an exception: a simulator wired to a text
 * field or a picker will see whatever the user typed, and a crash on `??` is a
 * worse answer than a sentence saying which parent is wrong.
 */
sealed interface PunnettResult {
    data class Computed(val square: PunnettSquare) : PunnettResult
    data class Invalid(val reason: String) : PunnettResult
}

/**
 * The genetics, as a pure function.
 *
 * Every probability in this file is a product of 0.25, 0.5 or 1.0, which are
 * exact in binary floating point. So the cells sum to exactly 1.0 and a whole
 * percentage is always a whole number of cases in a hundred — [wholePercent]
 * never has to round a repeating value, which is why it can be an `Int` rather
 * than a float with digits that mean nothing.
 */
object Punnett {

    /** Gametes each parent can contribute. */
    const val MAX_AXIS_LENGTH = 2

    /** Every genotype the app can build a square for, with its Spanish label. */
    val GENOTYPES: List<Genotype> = listOf(
        Genotype.DOMINANT_HOMOLOGUE,
        Genotype.HETEROZYGOUS,
        Genotype.RECESSIVE_HOMOLOGUE
    )

    /**
     * The square for [first] x [second], or [PunnettResult.Invalid] naming the
     * parent that could not be read.
     */
    fun square(first: String, second: String): PunnettResult {
        val parsedFirst = Genotype.parse(first)
        if (parsedFirst !is GenotypeParse.Ok) {
            return PunnettResult.Invalid(parsedFirst.reasonEs)
        }
        val parsedSecond = Genotype.parse(second)
        if (parsedSecond !is GenotypeParse.Ok) {
            return PunnettResult.Invalid(parsedSecond.reasonEs)
        }
        return PunnettResult.Computed(cross(parsedFirst.genotype, parsedSecond.genotype))
    }

    /**
     * The cross itself, for callers that already hold two [Genotype]s.
     *
     * A cell's probability is the product of the two gamete probabilities:
     * independent assortment. Cells are built row-major over the first parent's
     * gametes, so [PunnettSquare.rows] and [PunnettSquare.columns] follow from
     * the parents' homozygosity rather than from a hardcoded 2x2 — which is why
     * `AA x Aa` is 1x2 and `aa x aa` is 1x1.
     */
    fun cross(first: Genotype, second: Genotype): PunnettSquare {
        val firstGametes = first.gametes()
        val secondGametes = second.gametes()

        val cells = firstGametes.flatMap { a ->
            secondGametes.map { b ->
                PunnettCell(
                    fromFirst = a.allele.symbol,
                    fromSecond = b.allele.symbol,
                    genotype = Genotype.of(a.allele, b.allele),
                    probability = a.probability * b.probability
                )
            }
        }

        return PunnettSquare(
            first = first,
            second = second,
            cells = cells,
            dominantPhenotypeProbability = cells
                .filter { it.genotype.phenotype == Phenotype.DOMINANT }
                .sumOf { it.probability },
            recessivePhenotypeProbability = cells
                .filter { it.genotype.phenotype == Phenotype.RECESSIVE }
                .sumOf { it.probability },
            carrierProbability = cells
                .filter { it.genotype.isCarrier }
                .sumOf { it.probability }
        )
    }
}

/* ─────────────────────────── Parsing ─────────────────────────────────────── */

/** Why a genotype string could not be read, with text safe to show a user. */
sealed interface GenotypeParse {
    val reasonEs: String

    data class Ok(val genotype: Genotype) : GenotypeParse {
        override val reasonEs: String get() = ""
    }

    /** Empty, or only whitespace. */
    data class Blank(val raw: String) : GenotypeParse {
        override val reasonEs: String get() = "Falta el genotipo. Usa AA, Aa o aa."
    }

    /** Not two characters. */
    data class BadLength(val raw: String, val length: Int) : GenotypeParse {
        override val reasonEs: String
            get() = "«$raw» no es un genotipo: tiene $length caracteres y debe tener 2 (AA, Aa o aa)."
    }

    /** Two characters, but not a letter this model knows. */
    data class UnknownAllele(val raw: String, val symbol: Char) : GenotypeParse {
        override val reasonEs: String
            get() = "«$raw» no es un genotipo: «$symbol» no es un alelo. Solo A (dominante) y a (recesivo)."
    }
}

/**
 * Parses one parent.
 *
 * Tolerant of case and surrounding whitespace, because a picker and a text
 * field both end up here and neither should punish the user for `aA` or a stray
 * space. Strict about everything else: an unknown letter or the wrong length is
 * reported with [GenotypeParse.reasonEs] rather than silently coerced to
 * something plausible.
 */
fun Genotype.Companion.parse(raw: String): GenotypeParse {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return GenotypeParse.Blank(raw)

    if (trimmed.length != 2) return GenotypeParse.BadLength(raw, trimmed.length)

    // Case carries the meaning here, so it has to be read before anything
    // normalises it: uppercase A is dominant, lowercase a is recessive, and
    // anything else is not an allele this model knows.
    val resolved = trimmed.map { ch ->
        when (ch) {
            'A' -> Allele.DOMINANT
            'a' -> Allele.RECESSIVE
            else -> null
        }
    }
    val first = resolved[0]
    val second = resolved[1]
    if (first == null || second == null) {
        val offending = listOf(trimmed[0], trimmed[1]).firstOrNull {
            it != 'A' && it != 'a'
        } ?: trimmed[0]
        return GenotypeParse.UnknownAllele(raw, offending)
    }

    return GenotypeParse.Ok(Genotype.of(first, second))
}

/** The parsed genotype, or null when the parse failed. */
fun GenotypeParse.genotypeOrNull(): Genotype? = (this as? GenotypeParse.Ok)?.genotype

/* ─────────────────────────── Presentation ────────────────────────────────── */

/**
 * A probability as a whole percentage.
 *
 * Deliberately an `Int`. Every probability this model produces is a multiple of
 * 0.25, so there is never a fraction to round and a `33.333333333333336 %`
 * can never reach the screen — the kind of precision the genetics does not
 * support and that would imply the square predicts individual plants. It
 * predicts a population.
 */
fun wholePercent(probability: Double): Int =
    (probability * 100.0).roundToInt().coerceIn(0, 100)

/** [wholePercent] with the Spanish spacing rule: `75 %`. */
fun percentLabelEs(probability: Double): String = "${wholePercent(probability)} %"

/* ─────────────────────────── Ratio helpers ──────────────────────────────── */

/**
 * Probability expressed in cases out of four; exact for everything [Punnett]
 * emits, since no cell probability is finer than 0.25.
 */
private fun caseUnits(probability: Double): Int = (probability * 4.0).roundToInt()

private fun gcdOf(vararg values: Int): Int {
    var result = 0
    values.forEach { result = gcd(result, it) }
    return result
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

private fun divide(ratio: String, divisor: Int): String =
    ratio.split(':').joinToString(":") { (it.toInt() / divisor).toString() }
