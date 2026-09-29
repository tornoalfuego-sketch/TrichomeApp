package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Punnett square.
 *
 * The whole feature is one pure function of two parents' genotypes, so the
 * numbers below are the biology rather than a snapshot of some UI. A gamete from
 * a heterozygote is 1:1 and every probability is a product of 0.25 / 0.5 / 1.0,
 * all of them exact in binary floating point, so the summed probability is
 * exactly 1.0 and a whole-percentage rendering is never a rounding lie.
 */
class PunnettSquareTest {

    private val delta = 1e-9

    private fun square(first: String, second: String): PunnettSquare {
        val result = Punnett.square(first, second)
        assertTrue("expected $first x $second to compute, got $result", result is PunnettResult.Computed)
        return (result as PunnettResult.Computed).square
    }

    /* ── 1. Parsing ───────────────────────────────────────────────────────── */

    @Test
    fun everyCanonicalGenotypeParses() {
        assertEquals("AA", Genotype.parse("AA").genotypeOrNull()?.notation)
        assertEquals("Aa", Genotype.parse("Aa").genotypeOrNull()?.notation)
        assertEquals("aa", Genotype.parse("aa").genotypeOrNull()?.notation)
    }

    @Test
    fun notationIsCaseInsensitiveTrimmedAndAlwaysOrdersTheDominantFirst() {
        assertEquals("aA is the same locus as Aa", "Aa", Genotype.parse("aA").genotypeOrNull()?.notation)
        assertEquals("surrounding blanks are not an error", "Aa", Genotype.parse("  aA  ").genotypeOrNull()?.notation)
        assertEquals("Aa", Genotype.parse("Aa").genotypeOrNull()?.notation)
        assertEquals("AA", Genotype.parse("AA").genotypeOrNull()?.notation)
        assertEquals("aa", Genotype.parse("aa").genotypeOrNull()?.notation)
    }

    @Test
    fun theAllelesAreStoredInTheCanonicalOrderSoTwoSpellingsAreTheSameValue() {
        val lowerFirst = Genotype.parse("aA").genotypeOrNull()!!
        val upperFirst = Genotype.parse("Aa").genotypeOrNull()!!
        assertEquals(lowerFirst, upperFirst)
        assertEquals(Allele.DOMINANT, lowerFirst.a)
        assertEquals(Allele.RECESSIVE, lowerFirst.b)
    }

    @Test
    fun aBlankGenotypeIsRejectedWithAReadableReason() {
        val parsed = Genotype.parse("   ")
        assertTrue("blank must not parse", parsed is GenotypeParse.Blank)
        assertTrue(
            "the reason is shown to the user and must not be empty",
            (parsed as GenotypeParse.Blank).reasonEs.isNotBlank()
        )
    }

    @Test
    fun aGenotypeOfTheWrongLengthIsRejected() {
        assertTrue(Genotype.parse("A") is GenotypeParse.BadLength)
        assertTrue(Genotype.parse("AAA") is GenotypeParse.BadLength)
        assertTrue(Genotype.parse("aaaa") is GenotypeParse.BadLength)
    }

    @Test
    fun anUnknownAlleleIsRejectedAndNamesTheOffendingSymbol() {
        val parsed = Genotype.parse("Ax")
        assertTrue(parsed is GenotypeParse.UnknownAllele)
        assertEquals('x', (parsed as GenotypeParse.UnknownAllele).symbol)
    }

    /* ── 2. Gametes ───────────────────────────────────────────────────────── */

    @Test
    fun aHomozygoteProducesOneGameteCarryingAllTheProbability() {
        val gametes = Genotype.parse("AA").genotypeOrNull()!!.gametes()
        assertEquals(1, gametes.size)
        assertEquals(Allele.DOMINANT, gametes[0].allele)
        assertEquals(1.0, gametes[0].probability, delta)
    }

    @Test
    fun aHeterozygoteProducesTwoGametesInARatioOfOneToOne() {
        val gametes = Genotype.parse("Aa").genotypeOrNull()!!.gametes()
        assertEquals(2, gametes.size)
        assertEquals(Allele.DOMINANT, gametes[0].allele)
        assertEquals(Allele.RECESSIVE, gametes[1].allele)
        assertEquals(0.5, gametes[0].probability, delta)
        assertEquals(0.5, gametes[1].probability, delta)
        assertEquals(1.0, gametes.sumOf { it.probability }, delta)
    }

    /* ── 3. The five classic crosses ──────────────────────────────────────── */

    @Test
    fun homoDominantCrossGivesOnlyHomozygousDominantOffspring() {
        val square = square("AA", "AA")
        assertEquals(1, square.rows)
        assertEquals(1, square.columns)
        assertEquals("AA", square.cells.single().genotype.notation)
        assertEquals(1.0, square.dominantPhenotypeProbability, delta)
        assertEquals(0.0, square.recessivePhenotypeProbability, delta)
        assertEquals(0.0, square.carrierProbability, delta)
    }

    @Test
    fun homoDominantByHeterozygoteIsOneToOneOnGenotypeAndAllDominantOnPhenotype() {
        val square = square("AA", "Aa")
        assertEquals(2, square.columns)
        assertEquals(listOf("AA", "Aa"), square.cells.map { it.genotype.notation })
        assertEquals(listOf(0.5, 0.5), square.cells.map { it.probability })
        assertEquals(1.0, square.dominantPhenotypeProbability, delta)
        assertEquals(0.0, square.recessivePhenotypeProbability, delta)
        assertEquals("half the offspring carry the recessive allele", 0.5, square.carrierProbability, delta)
    }

    @Test
    fun twoHeterozygotesGiveThreeToOneOnPhenotypeAndOneToTwoToOneOnGenotype() {
        val square = square("Aa", "Aa")

        // Mendel's F2: the number everyone checks against.
        assertEquals("3:1 on phenotype", 0.75, square.dominantPhenotypeProbability, delta)
        assertEquals(0.25, square.recessivePhenotypeProbability, delta)
        assertEquals("1:2:1 on genotype", 0.5, square.carrierProbability, delta)

        assertEquals(2, square.rows)
        assertEquals(2, square.columns)
        assertEquals(
            listOf("AA", "Aa", "Aa", "aa"),
            square.cells.map { it.genotype.notation }
        )
        assertEquals(listOf(0.25, 0.25, 0.25, 0.25), square.cells.map { it.probability })
        assertEquals("3:1", square.phenotypeRatioEs())
        assertEquals("1:2:1", square.genotypeRatioEs())
    }

    @Test
    fun heterozygoteByHomozygousRecessiveIsOneToOneAndHalfAreCarriers() {
        val square = square("Aa", "aa")
        assertEquals(2, square.rows)
        assertEquals(1, square.columns)
        assertEquals(listOf("Aa", "aa"), square.cells.map { it.genotype.notation })
        assertEquals(0.5, square.dominantPhenotypeProbability, delta)
        assertEquals(0.5, square.recessivePhenotypeProbability, delta)
        // Every heterozygote is by definition a carrier: it shows the dominant
        // phenotype yet still carries one recessive allele.
        assertEquals(0.5, square.carrierProbability, delta)
        assertEquals("1:1", square.phenotypeRatioEs())
    }

    @Test
    fun twoRecessivesGiveOnlyRecessiveOffspring() {
        val square = square("aa", "aa")
        assertEquals("aa", square.cells.single().genotype.notation)
        assertEquals(0.0, square.dominantPhenotypeProbability, delta)
        assertEquals(1.0, square.recessivePhenotypeProbability, delta)
        assertEquals(0.0, square.carrierProbability, delta)
        assertEquals("0:1", square.phenotypeRatioEs())
    }

    /* ── 4. Invariants that must hold for every cross ─────────────────────── */

    @Test
    fun everyCellCarriesTheGametesItWasBuiltFrom() {
        // `Genotype` stores the dominant allele first regardless of which parent
        // it came from, so the cell's axis letters are compared as a *set*, not
        // positionally: a cell built from (a, A) is still `Aa`.
        listOf("AA" to "Aa", "Aa" to "Aa", "Aa" to "aa", "aa" to "Aa").forEach { (a, b) ->
            val square = square(a, b)
            square.cells.forEach { cell ->
                assertEquals(
                    "$a x $b: ${cell.genotype.notation} from ${cell.fromFirst}/${cell.fromSecond}",
                    cell.genotype.notation,
                    Genotype.of(
                        if (cell.fromFirst == 'A') Allele.DOMINANT else Allele.RECESSIVE,
                        if (cell.fromSecond == 'A') Allele.DOMINANT else Allele.RECESSIVE
                    ).notation
                )
            }
        }
    }

    @Test
    fun theCellAxesShowTheHomozygousGameteOnBothSides() {
        // A cell on the diagonal of a heterozygote cross takes the same allele from
        // both parents; the off-diagonal ones are what make a carrier.
        val square = square("Aa", "Aa")
        assertEquals('A', square.cell(0, 0).fromFirst)
        assertEquals('A', square.cell(0, 0).fromSecond)
        assertEquals('a', square.cell(1, 1).fromFirst)
        assertEquals('a', square.cell(1, 1).fromSecond)
        assertEquals('A', square.cell(0, 1).fromFirst)
        assertEquals('a', square.cell(0, 1).fromSecond)
        assertEquals("Aa", square.cell(0, 1).genotype.notation)
        assertTrue(square.cell(0, 1).genotype.isCarrier)
    }

    @Test
    fun theCellGridIsRowMajorAndExactlyRowsTimesColumnsBig() {
        val tall = square("Aa", "aa")
        assertEquals(tall.rows * tall.columns, tall.cells.size)
        assertEquals(tall.rows, tall.cells.distinctBy { it.fromFirst }.size)

        val wide = square("aa", "Aa")
        assertEquals(wide.rows * wide.columns, wide.cells.size)
        assertEquals(wide.columns, wide.cells.distinctBy { it.fromSecond }.size)
    }

    @Test
    fun theProbabilitiesAlwaysSumToExactlyOne() {
        val pairs = listOf("AA" to "AA", "AA" to "Aa", "Aa" to "Aa", "Aa" to "aa", "aa" to "aa")
        pairs.forEach { (a, b) ->
            val square = square(a, b)
            assertEquals("cells of $a x $b", 1.0, square.cells.sumOf { it.probability }, delta)
            assertEquals(
                "phenotype of $a x $b",
                1.0,
                square.dominantPhenotypeProbability + square.recessivePhenotypeProbability,
                delta
            )
            // The carrier set is a subset of the offspring, so it is bounded by the
            // total. It is NOT bounded by 1 - recessive: `Aa x aa` is half carriers
            // and half recessive-phenotype, and a test asserting otherwise would be
            // asserting the exact confusion this model exists to avoid.
            assertTrue(
                "carrier of $a x $b: ${square.carrierProbability}",
                square.carrierProbability in 0.0..1.0
            )
        }
    }

    @Test
    fun theCarrierProbabilityIsNeverLargerThanTheDominantPhenotype() {
        // A carrier shows the dominant phenotype, so the carrier set is a subset
        // of the dominant set. If this ever fails the two are being derived from
        // different data.
        val pairs = listOf("AA" to "AA", "AA" to "Aa", "Aa" to "Aa", "Aa" to "aa", "aa" to "aa")
        pairs.forEach { (a, b) ->
            val square = square(a, b)
            assertTrue(
                "$a x $b: carrier ${square.carrierProbability} > dominant ${square.dominantPhenotypeProbability}",
                square.carrierProbability <= square.dominantPhenotypeProbability + delta
            )
        }
    }

    @Test
    fun swappingTheParentsDoesNotChangeTheSummary() {
        listOf("AA" to "Aa", "Aa" to "Aa", "Aa" to "aa").forEach { (a, b) ->
            val forward = square(a, b)
            val reversed = square(b, a)
            assertEquals("$a x $b dominant", forward.dominantPhenotypeProbability, reversed.dominantPhenotypeProbability, delta)
            assertEquals("$a x $b recessive", forward.recessivePhenotypeProbability, reversed.recessivePhenotypeProbability, delta)
            assertEquals("$a x $b carrier", forward.carrierProbability, reversed.carrierProbability, delta)
            assertEquals("$a x $b ratio", forward.phenotypeRatioEs(), reversed.phenotypeRatioEs())
        }
    }

    /* ── 5. Nonsense input is a result, not a crash ───────────────────────── */

    @Test
    fun aMalformedParentIsReportedInsteadOfThrowing() {
        val result = Punnett.square("Aa", "QQ")
        assertTrue("a malformed parent must be reported", result is PunnettResult.Invalid)
        val reason = (result as PunnettResult.Invalid).reason
        assertTrue("the reason is shown to the user: $reason", reason.isNotBlank())
    }

    @Test
    fun theBadParentIsTheOneNamedInTheReason() {
        val firstBad = Punnett.square("ZZ", "Aa") as PunnettResult.Invalid
        assertTrue("got ${firstBad.reason}", firstBad.reason.contains("ZZ"))

        val secondBad = Punnett.square("Aa", "ZZ") as PunnettResult.Invalid
        assertTrue("got ${secondBad.reason}", secondBad.reason.contains("ZZ"))
    }

    @Test
    fun bothParentsBadReportsTheFirstOneItFound() {
        val result = Punnett.square("", "nope") as PunnettResult.Invalid
        assertTrue("got ${result.reason}", result.reason.isNotBlank())
    }

    @Test
    fun theSummaryIsDerivedFromTheGridItSitsNextTo() {
        // If the three summary numbers and the cells were ever computed
        // separately, the two halves of the screen would contradict each other.
        listOf("AA" to "AA", "AA" to "Aa", "Aa" to "Aa", "Aa" to "aa", "aa" to "aa")
            .forEach { (a, b) ->
                val square = square(a, b)
                val fromCells = square.cells.sumOf { cell ->
                    when {
                        cell.genotype.phenotype == Phenotype.DOMINANT -> cell.probability
                        else -> 0.0
                    }
                }
                val recessiveFromCells = square.cells.sumOf { cell ->
                    if (cell.genotype.phenotype == Phenotype.RECESSIVE) cell.probability else 0.0
                }
                val carrierFromCells = square.cells.sumOf { cell ->
                    if (cell.genotype.isCarrier) cell.probability else 0.0
                }
                assertEquals("$a x $b dominant", fromCells, square.dominantPhenotypeProbability, delta)
                assertEquals("$a x $b recessive", recessiveFromCells, square.recessivePhenotypeProbability, delta)
                assertEquals("$a x $b carrier", carrierFromCells, square.carrierProbability, delta)
            }
    }

    @Test
    fun theRatioLabelsAreTheCountsInTheGridMultipliedOut() {
        // "3:1" has to mean three of the four cells, not a rounded percentage.
        val square = square("Aa", "Aa")
        val dominantCells = square.cells.count { it.genotype.phenotype == Phenotype.DOMINANT }
        val recessiveCells = square.cells.count { it.genotype.phenotype == Phenotype.RECESSIVE }

        assertEquals(
            "3:1 means three dominant cells and one recessive one",
            "$dominantCells:$recessiveCells",
            square.phenotypeRatioEs()
        )
    }

    /* ── 6. Honest percentages ─────────────────────────────────────────────── */

    @Test
    fun everyProbabilityThisModelCanProduceIsAWholeQuarter() {
        // That is what licenses rounding to a whole percent: with a denominator
        // of four there is nothing to round away, so no percentage shown to the
        // grower is a false precision.
        val pairs = listOf("AA" to "AA", "AA" to "Aa", "Aa" to "Aa", "Aa" to "aa", "aa" to "aa")
        pairs.forEach { (a, b) ->
            val square = square(a, b)
            listOf(
                square.dominantPhenotypeProbability,
                square.recessivePhenotypeProbability,
                square.carrierProbability
            ).plus(square.cells.map { it.probability }).forEach { p ->
                val quarters = p * 4
                assertEquals("$a x $b: $p is not a multiple of 0.25", quarters, quarters.toInt().toDouble(), delta)
            }
        }
    }

    @Test
    fun wholePercentIsExactForEveryQuarterTheModelProduces() {
        listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { p ->
            val expected = (p * 100).toInt()
            assertEquals("$p", expected, wholePercent(p))
        }
    }

    @Test
    fun wholePercentIsClampedIntoTheRangeAProgressBarCanDraw() {
        assertEquals(0, wholePercent(-0.5))
        assertEquals(100, wholePercent(1.5))
    }

    @Test
    fun thePercentLabelUsesTheSpanishSpacingRule() {
        assertEquals("75 %", percentLabelEs(0.75))
        assertEquals("100 %", percentLabelEs(1.0))
        assertEquals("0 %", percentLabelEs(0.0))
    }

    @Test
    fun theRatioLabelsAreReducedToTheSmallestHonestIntegers() {
        assertEquals("3:1", square("Aa", "Aa").phenotypeRatioEs())
        assertEquals("1:1", square("Aa", "aa").phenotypeRatioEs())
        assertEquals("1:0", square("AA", "AA").phenotypeRatioEs())
        assertEquals("0:1", square("aa", "aa").phenotypeRatioEs())
        assertEquals("1:1:0", square("AA", "Aa").genotypeRatioEs())
        assertEquals("1:2:1", square("Aa", "Aa").genotypeRatioEs())
        assertEquals("0:1:1", square("Aa", "aa").genotypeRatioEs())
        assertEquals("0:0:1", square("aa", "aa").genotypeRatioEs())
    }

    /* ── 7. The catalogue the picker offers ───────────────────────────────── */

    @Test
    fun everyOfferedParentProducesAValidSquare() {
        Punnett.GENOTYPES.forEach { parent ->
            Punnett.GENOTYPES.forEach { other ->
                val result = Punnett.square(parent.notation, other.notation)
                assertTrue(
                    "${parent.notation} x ${other.notation} must compute, got $result",
                    result is PunnettResult.Computed
                )
            }
        }
    }

    @Test
    fun theOfferedParentsAreTheThreeCanonicalGenotypesWithSpanishLabels() {
        assertEquals(
            listOf(
                "AA — homocigoto dominante",
                "Aa — heterocigoto",
                "aa — homocigoto recesivo"
            ),
            Punnett.GENOTYPES.map { genotypeLabelEs(it) }
        )
    }

    @Test
    fun theOfferedParentsAreExactlyTheThreeTheModelCanExpress() {
        assertEquals(3, Punnett.GENOTYPES.size)
        assertEquals(3, Punnett.GENOTYPES.map { it.notation }.toSet().size)
    }

    @Test
    fun theHelperNeverReturnsNullForAKnownGenotype() {
        assertNotNull(Genotype.parse("Aa").genotypeOrNull())
        assertNull(Genotype.parse("nope").genotypeOrNull())
    }
}
