package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2 — the volatility model, on the JVM.
 *
 * ## What is under test and why it is here rather than on a device
 *
 * `compose-ui-test` lives only in `androidTest` in this project, which is
 * device-only, so every decision F2 makes is extracted into `TerpeneVolatility.kt`
 * as pure Kotlin and asserted here. What lands on the screen — a number, a
 * marker, a sentence — is a field of the model, and these tests hold those
 * fields.
 *
 * ## The standard these tests hold
 *
 *  - a derived band is never narrower than a measured one;
 *  - a derived band satisfies [TerpeneVaporisation]'s invariant by construction;
 *  - a derived number can never be printed without its `≈` marker, and a
 *    measured one can never carry it;
 *  - the curve's aggregate is the same number the module's aggregate window is.
 */
class TerpeneVolatilityTest {

    /* ── Boiling point parsing ───────────────────────────────────────────── */

    @Test
    fun aSingleTemperatureReadsAsItself() {
        assertEquals(167, BoilingPointParser.parseCelsius("167 °C"))
        assertEquals(350, BoilingPointParser.parseCelsius("350 °C"))
    }

    @Test
    fun aRangeTakesItsLowerBoundInsteadOfConcatenating() {
        // The defect this parser exists for: the previous getter filtered digits
        // and read "155-156 °C" as 155156 — a plausible integer that is not a
        // temperature, produced without an error.
        assertEquals(
            "a range must not be concatenated into one number",
            155,
            BoilingPointParser.parseCelsius("155-156 °C")
        )
        assertEquals(150, BoilingPointParser.parseCelsius("150–160 °C"))
    }

    @Test
    fun aValueWithoutADegreeSignCarriesNoTemperature() {
        assertNull(BoilingPointParser.parseCelsius("167 C"))
        assertNull(BoilingPointParser.parseCelsius(""))
        assertNull(BoilingPointParser.parseCelsius("   "))
        assertNull(BoilingPointParser.parseCelsius(null))
        assertNull(BoilingPointParser.parseCelsius("sin dato °"))
    }

    @Test
    fun zeroDegreesIsATemperatureAndNotTreatedAsMissing() {
        assertEquals(0, BoilingPointParser.parseCelsius("0 °C"))
    }

    /* ── Provenance is structural ────────────────────────────────────────── */

    @Test
    fun aWindowCannotBeBuiltWithoutSayingWhereItCameFrom() {
        // Not a behavioural test: `provenance` has no default, so the shape is
        // the assertion. A window that could be built without one would be a
        // window a screen could render unmarked.
        assertEquals(2, VolatilityProvenance.entries.size)
        assertEquals(
            "only the module's table may claim to have measured a band",
            VolatilityProvenance.MEASURED,
            VolatilityWindow(156, 180, VolatilityProvenance.MEASURED).provenance
        )
        assertTrue(
            VolatilityWindow(160, 200, VolatilityProvenance.DERIVED).isDerived
        )
    }

    @Test
    fun anInvertedWindowIsRejectedAtConstruction() {
        val failed = runCatching {
            VolatilityWindow(200, 160, VolatilityProvenance.MEASURED)
        }.isFailure
        assertTrue("a window that closes on itself must not be constructible", failed)
    }

    /* ── The derivation, and why it is that coarse ───────────────────────── */

    @Test
    fun theFloorIsTheBoilingPointItselfRoundedDown() {
        // Nothing is inferred about *where* a compound starts coming off: that
        // is the boiling point, and 9 of the 10 shipped rows sit on it.
        assertEquals(160, VolatilityDerivation.floorFor(167))
        assertEquals(160, VolatilityDerivation.floorFor(160))
        assertEquals(150, VolatilityDerivation.floorFor(151))
        assertEquals(300, VolatilityDerivation.floorFor(300))
    }

    @Test
    fun theFloorIsNeverPushedAboveTheBoilingPoint() {
        // Rounding *down* is the whole reason the derivation cannot fail
        // `minTempC <= boilingPointC` the way an upward rounding would.
        (100..400).forEach { boiling ->
            val family = TerpeneFamily.MONOTERPENE
            val window = VolatilityDerivation.derive(boiling, family)
            assertTrue(
                "$boiling °C: floor ${window.minTempC} must stay at or below it",
                window.minTempC <= boiling
            )
        }
    }

    @Test
    fun aDerivedWindowSatisfiesTheShippedInvariantForEveryFamily() {
        TerpeneFamily.entries.forEach { family ->
            (100..400).forEach { boiling ->
                val window = VolatilityDerivation.derive(boiling, family)
                assertTrue(
                    "$family at $boiling °C: ${window.minTempC} <= $boiling",
                    window.minTempC <= boiling
                )
                assertTrue(
                    "$family at $boiling °C: ${window.maxTempC} > $boiling",
                    window.maxTempC > boiling
                )
            }
        }
    }

    @Test
    fun noDerivedWindowIsNarrowerThanTheNarrowestOneTheAppMeasures() {
        // The honest direction to be wrong in. The shipped measured bands are
        // 22–29 °C wide (max - min), and the widest, caryophyllene's 250–280, is
        // 30 °C. A derived band narrower than that would be claiming more than
        // the app can support for a compound it has no reading for.
        var narrowest = Int.MAX_VALUE
        (100..400).forEach { boiling ->
            TerpeneFamily.entries.forEach { family ->
                narrowest = minOf(
                    narrowest,
                    VolatilityDerivation.derive(boiling, family).widthC
                )
            }
        }

        assertEquals(
            "the derivation's own floor for its width, which must not be narrower " +
                "than any band the app actually measures",
            VolatilityDerivation.NARROWEST_DERIVED_WIDTH_C,
            narrowest
        )
        assertTrue(
            "a derived band of $narrowest °C is narrower than caryophyllene's " +
                "measured 30 °C, which means it is finer than the evidence",
            narrowest >= 30
        )
    }

    @Test
    fun aDerivedBandNeverAddsLessHeadroomThanTheNarrowestShippedOne() {
        // The honest direction to be wrong in. The shipped margins run 18–29 °C,
        // so a derived band that added less than the smallest of them would be
        // claiming a tighter window than the app can stand behind for any
        // compound it has actually measured.
        (100..400).forEach { boiling ->
            TerpeneFamily.entries.forEach { family ->
                val window = VolatilityDerivation.derive(boiling, family)
                val added = window.maxTempC - boiling
                assertTrue(
                    "$family at $boiling °C: adds $added °C, narrower than the " +
                        "smallest shipped margin " +
                        "(${VolatilityDerivation.NARROWEST_SHIPPED_HEADROOM_C} °C)",
                    added >= VolatilityDerivation.NARROWEST_SHIPPED_HEADROOM_C
                )
            }
        }
    }

    @Test
    fun everyFamilyResolvesToTheSameHeadroomBecauseTheDataShowsNoFamilyEffect() {
        // The finding, not a convenience: the eight monoterpene rows run 22–28 °C
        // and the two sesquiterpene rows run 18–29 °C, so their means differ by
        // less than a degree and sit inside the monoterpene spread. A per-family
        // constant there would be a number the data does not support.
        val headrooms = TerpeneFamily.entries.map { it.derivedHeadroomC }.distinct()
        assertEquals(
            "the shipped rows show no per-family effect, so one constant is the " +
                "honest model rather than four fitted ones",
            listOf(VolatilityDerivation.DERIVED_HEADROOM_C),
            headrooms
        )
        assertEquals(30, VolatilityDerivation.DERIVED_HEADROOM_C)
    }

    @Test
    fun theHeadroomConstantNeverShrinksBelowTheWidestShippedMargin() {
        TerpeneFamily.entries.forEach { family ->
            assertTrue(
                "${family.labelEs}: headroom ${family.derivedHeadroomC} °C is narrower " +
                    "than the widest margin the shipped table shows " +
                    "(${VolatilityDerivation.WIDEST_SHIPPED_HEADROOM_C} °C)",
                family.derivedHeadroomC >= VolatilityDerivation.WIDEST_SHIPPED_HEADROOM_C
            )
        }
    }

    @Test
    fun aFamilyWithNoShippedRowIsDeclaredAsSuch() {
        // The honesty of the constants is part of the contract: a reader has to
        // be able to tell which numbers were fitted and which were inherited.
        assertEquals(8, TerpeneFamily.MONOTERPENE.shippedRowCount)
        assertEquals(2, TerpeneFamily.SESQUITERPENE.shippedRowCount)
        assertTrue(TerpeneFamily.MONOTERPENE.hasMeasuredRow)
        assertTrue(TerpeneFamily.SESQUITERPENE.hasMeasuredRow)
        assertEquals(
            "a diterpene has no shipped row, so its headroom cannot be called measured",
            0,
            TerpeneFamily.DITERPENE.shippedRowCount
        )
        assertFalse(TerpeneFamily.DITERPENE.hasMeasuredRow)
        assertEquals(0, TerpeneFamily.UNCLASSIFIED.shippedRowCount)
        assertTrue(
            "an unclassified row must inherit the widest band, not a narrower guess",
            TerpeneFamily.UNCLASSIFIED.derivedHeadroomC >=
                VolatilityDerivation.WIDEST_SHIPPED_HEADROOM_C
        )
    }

    @Test
    fun anUnknownFamilyLabelBecomesUnclassifiedRatherThanAGuess() {
        assertEquals(TerpeneFamily.MONOTERPENE, TerpeneFamily.fromFamilyEs("Monoterpeno"))
        assertEquals(TerpeneFamily.SESQUITERPENE, TerpeneFamily.fromFamilyEs("Sesquiterpeno"))
        assertEquals(TerpeneFamily.DITERPENE, TerpeneFamily.fromFamilyEs("Diterpeno"))
        assertEquals(TerpeneFamily.UNCLASSIFIED, TerpeneFamily.fromFamilyEs("Triterpeno"))
        assertEquals(TerpeneFamily.UNCLASSIFIED, TerpeneFamily.fromFamilyEs(""))
        assertEquals(TerpeneFamily.UNCLASSIFIED, TerpeneFamily.fromFamilyEs(null))
    }

    /* ── The index ───────────────────────────────────────────────────────── */

    private fun row(
        id: String,
        family: TerpeneFamily = TerpeneFamily.MONOTERPENE,
        boilingPointEs: String = "167 °C"
    ) = CatalogVolatilityRow(
        catalogId = id,
        labelEs = id,
        family = family,
        molarMassEs = "136.24",
        boilingPointEs = boilingPointEs
    )

    private fun measured(
        key: EntourageTerpene,
        boiling: Int,
        min: Int,
        max: Int
    ) = TerpeneVaporisation(
        terpene = key,
        boilingPointC = boiling,
        minTempC = min,
        maxTempC = max,
        noteEs = "nota"
    )

    @Test
    fun aCatalogRowWithNoShippedBandIsDerivedAndLabelledSo() {
        val index = TerpeneVolatilityIndex.from(listOf(row("myrcene")), emptyList())
        val entry = index.forId("myrcene")

        assertNotNull(entry)
        assertEquals(VolatilityProvenance.DERIVED, entry!!.provenance)
        assertEquals(160, entry.window.minTempC)
        assertEquals(200, entry.window.maxTempC)
        assertEquals("", entry.noteEs)
    }

    @Test
    fun aCatalogRowTheModuleMeasuresKeepsTheModulesBandAndNote() {
        val index = TerpeneVolatilityIndex.from(
            catalog = listOf(row("myrcene", boilingPointEs = "167 °C")),
            measured = listOf(measured(EntourageTerpene.MYRCENE, 167, 167, 195))
        )
        val entry = index.forId("myrcene")!!

        assertEquals(VolatilityProvenance.MEASURED, entry.provenance)
        assertEquals(
            "the module's band wins for a measured row; the app must not re-derive it",
            "167–195 °C",
            entry.window.formatEs()
        )
        assertEquals("nota", entry.noteEs)
    }

    @Test
    fun theBoilingPointComesFromTheEncyclopediaEvenForAMeasuredRow() {
        // F1 made `terpenes.json` canonical. A row the module measures must not
        // be allowed to import the module's copy of the number instead.
        val index = TerpeneVolatilityIndex.from(
            catalog = listOf(row("myrcene", boilingPointEs = "167 °C")),
            measured = listOf(measured(EntourageTerpene.MYRCENE, 167, 167, 195))
        )

        assertEquals(167, index.forId("myrcene")!!.boilingPointC)
    }

    @Test
    fun aRowWithNoReadableTemperatureIsNamedRatherThanDroppedSilently() {
        val index = TerpeneVolatilityIndex.from(
            catalog = listOf(row("broken", boilingPointEs = "sin dato"), row("myrcene")),
            emptyList()
        )

        assertEquals(listOf("broken"), index.withoutBoilingPoint)
        assertEquals("the other row is still reachable", 1, index.size)
        assertNull(index.forId("broken"))
    }

    @Test
    fun theIndexCountsWhatIsMeasuredAndWhatIsDerived() {
        val index = TerpeneVolatilityIndex.from(
            // The join is by catalog id, so the measured row's id has to be one
            // of the catalog rows or nothing matches and every row is derived.
            catalog = listOf(row("myrcene")) + (2..10).map { row("t$it") },
            measured = listOf(measured(EntourageTerpene.MYRCENE, 167, 167, 195))
        )

        assertEquals(1, index.measuredCount)
        assertEquals(9, index.derivedCount)
    }

    @Test
    fun theIndexJoinsToTheModuleThroughTheCatalogId() {
        val index = TerpeneVolatilityIndex.from(
            catalog = listOf(row("alpha_pinene", boilingPointEs = "156 °C"), row("bisabolol")),
            measured = listOf(measured(EntourageTerpene.ALPHA_PINENE, 156, 156, 180))
        )

        assertEquals(
            EntourageTerpene.ALPHA_PINENE,
            index.forId("alpha_pinene")!!.entourageTerpene
        )
        assertNull(
            "a compound the module does not model joins to nothing rather than to a guess",
            index.forId("bisabolol")!!.entourageTerpene
        )
    }

    @Test
    fun forFamilyIsOrderedLeastVolatileLast() {
        val index = TerpeneVolatilityIndex.from(
            catalog = listOf(
                row("vanillin", boilingPointEs = "285 °C"),
                row("hexanal", boilingPointEs = "131 °C"),
                row("myrcene", boilingPointEs = "167 °C")
            ),
            emptyList()
        )

        assertEquals(
            listOf("hexanal", "myrcene", "vanillin"),
            index.forFamily(TerpeneFamily.MONOTERPENE).map { it.catalogId }
        )
        assertTrue(index.forFamily(TerpeneFamily.DITERPENE).isEmpty())
    }

    @Test
    fun aVolatilityRowHasToHoldTheSameInvariantAsAShippedOne() {
        val failed = runCatching {
            TerpeneVolatility(
                catalogId = "x",
                labelEs = "X",
                family = TerpeneFamily.MONOTERPENE,
                molarMassEs = "",
                boilingPointC = 167,
                boilingPointEs = "167 °C",
                // A floor above the boiling point is the shape that would teach
                // the user the wrong temperature.
                window = VolatilityWindow(180, 200, VolatilityProvenance.MEASURED)
            )
        }.isFailure
        assertTrue("a band that excludes its own boiling point must be rejected", failed)
    }

    /* ── The curve ───────────────────────────────────────────────────────── */

    private fun step(
        id: String,
        boiling: Int,
        min: Int,
        max: Int,
        measured: Boolean = true
    ) = VolatilityStep(
        catalogId = id,
        labelEs = id,
        family = TerpeneFamily.MONOTERPENE,
        boilingPointC = boiling,
        window = VolatilityWindow(
            min,
            max,
            if (measured) VolatilityProvenance.MEASURED else VolatilityProvenance.DERIVED
        )
    )

    // --- the crash that shipped ------------------------------------------------
    //
    // `Modifier.weight(0f)` throws `IllegalArgumentException: invalid weight 0.0;
    // must be greater than zero` while composing. The first step of a curve opens
    // at the scale floor, so its raw start fraction was exactly 0f, and EVERY
    // terpene detail page crashed on open. No JVM test could see it: the throw
    // is in the renderer, and this project only renders on a device.

    @Test
    fun aBarCannotCarryAZeroWeightBecauseComposeThrowsOnIt() {
        // The construction-time guard, asserted directly. This is the invariant
        // that has to hold for the renderer to survive.
        val zeroStart = runCatching { VolatilityBar(0f, 0.5f) }
        assertTrue(
            "a zero start fraction must be rejected at construction, not in the renderer",
            zeroStart.isFailure
        )

        val zeroWidth = runCatching { VolatilityBar(0.5f, 0f) }
        assertTrue(
            "a zero width fraction must be rejected at construction, not in the renderer",
            zeroWidth.isFailure
        )

        // The floor itself is legal, which is what makes the clamp usable.
        val floor = VolatilityBar(VolatilityBar.MIN_FRACTION, VolatilityBar.MIN_FRACTION)
        assertTrue(floor.startFraction > 0f)
        assertTrue(floor.widthFraction > 0f)
    }

    @Test
    fun theFirstStepOfEveryCurveHasAPositiveStartFraction() {
        // The exact shape that crashed: a single-step curve, so the step opens
        // at the scale floor and the raw fraction is 0.
        val single = VolatilityCurve(listOf(step("myrcene", 167, 167, 195)))
        val bar = single.barFor(single.steps.first())

        assertTrue(
            "the first bar of a one-step curve has startFraction ${bar.startFraction}; " +
                "RowScope.weight throws on anything <= 0",
            bar.startFraction > 0f
        )
        assertTrue("width must be positive too, was ${bar.widthFraction}", bar.widthFraction > 0f)
    }

    @Test
    fun everyBarOfEveryMultiStepCurveCarriesTwoPositiveFractions() {
        // Swept across shapes rather than one hand-picked case, because the zero
        // only appears when a step sits exactly on the scale floor — which is
        // the *first* step of any curve, whatever else is selected.
        val curves = listOf(
            VolatilityCurve(listOf(step("a", 155, 155, 180), step("b", 262, 250, 280))),
            VolatilityCurve(listOf(step("a", 155, 155, 180), step("b", 167, 167, 195), step("c", 198, 198, 220))),
            VolatilityCurve(listOf(step("a", 300, 290, 340), step("b", 310, 300, 350), step("c", 320, 310, 360))),
            VolatilityCurve(listOf(step("a", 155, 155, 180), step("b", 156, 156, 181)))
        )

        curves.forEach { curve ->
            curve.steps.forEach { s ->
                val bar = curve.barFor(s)
                assertTrue(
                    "${s.catalogId}: startFraction ${bar.startFraction} is not > 0",
                    bar.startFraction > 0f
                )
                assertTrue(
                    "${s.catalogId}: widthFraction ${bar.widthFraction} is not > 0",
                    bar.widthFraction > 0f
                )
                assertTrue(
                    "${s.catalogId}: startFraction ${bar.startFraction} is not a fraction",
                    bar.startFraction <= 1f
                )
            }
        }
    }

    @Test
    fun clampingToTheFloorDoesNotMoveABarThatWasAlreadyLegal() {
        // The floor is a legality guard, not a geometry change. A bar in the
        // middle of the scale must come out of `barFor` exactly where the raw
        // ratio put it, or the picture quietly lies about where a compound sits.
        val curve = VolatilityCurve(listOf(step("a", 155, 155, 180), step("b", 262, 250, 280)))
        val last = curve.steps.last()
        val bar = curve.barFor(last)

        val rawSpan = (curve.scaleMaxC - curve.scaleMinC).coerceAtLeast(1)
        val rawStart = (last.opensAtC - curve.scaleMinC).toFloat() / rawSpan

        assertTrue(
            "a bar well above the floor must be untouched: raw $rawStart vs ${bar.startFraction}",
            bar.startFraction == rawStart
        )
    }

    @Test
    fun theCurveOrdersTheSelectionLeastVolatileFirst() {
        val curve = VolatilityCurve(
            listOf(
                step("linalool", 198, 198, 220),
                step("alpha_pinene", 156, 156, 180),
                step("myrcene", 167, 167, 195)
            )
        )

        assertEquals(
            listOf("alpha_pinene", "myrcene", "linalool"),
            curve.steps.map { it.catalogId }
        )
    }

    @Test
    fun theOrderIsATotalOneSoTwoIdenticalBandsNeverSwap() {
        val curve = VolatilityCurve(
            listOf(
                step("z", 200, 200, 220),
                step("a", 200, 200, 220)
            )
        )

        assertEquals(
            "compounds sharing a band must come out in the same order every time",
            listOf("a", "z"),
            curve.steps.map { it.catalogId }
        )
    }

    @Test
    fun theAggregateIsTheHighestFloorAgainstTheLowestCeiling() {
        val curve = VolatilityCurve(
            listOf(
                step("alpha_pinene", 156, 156, 180),
                step("linalool", 198, 198, 220)
            )
        )

        assertEquals(198, curve.floorC)
        assertEquals(180, curve.ceilingC)
        assertFalse(
            "a 156 °C pinene and a 198 °C linalool cannot both survive one pass",
            curve.isViable
        )
    }

    @Test
    fun theCurveAgreesWithTheModulesAggregateWindowOnTheSameSelection() {
        // The reason `windowFor` was refactored: two implementations of the same
        // band is two truths about one number.
        val rows = listOf(
            measured(EntourageTerpene.ALPHA_PINENE, 156, 156, 180),
            measured(EntourageTerpene.LINALOOL, 198, 198, 220),
            measured(EntourageTerpene.BETA_CARYOPHYLLENE, 262, 250, 280)
        )
        val selection = setOf(
            EntourageTerpene.ALPHA_PINENE,
            EntourageTerpene.LINALOOL,
            EntourageTerpene.BETA_CARYOPHYLLENE
        )

        val window = EntouragePlanner.windowFor(selection, rows)!!
        val curve = EntouragePlanner.curveFor(selection, rows)!!

        assertEquals(window.minTempC, curve.floorC)
        assertEquals(window.maxTempC, curve.ceilingC)
        assertEquals(window.isViable, curve.isViable)
        assertEquals(
            "the same compounds, in the same order",
            window.terpenes.sortedBy { it.key },
            curve.steps.mapNotNull { it.entourageTerpene }.sortedBy { it.key }
        )
    }

    @Test
    fun aCurveNamesWhatIsLostByReachingTheHardestCompound() {
        // The contradiction used to be a boolean and nothing else. It is a list.
        val curve = VolatilityCurve(
            listOf(
                step("alpha_pinene", 156, 156, 180),
                step("beta_caryophyllene", 262, 250, 280)
            )
        )

        assertEquals(
            listOf("alpha_pinene"),
            curve.lostSteps.map { it.catalogId }
        )
    }

    @Test
    fun nothingIsLostWhenEveryCompoundSurvivesTheHighestFloor() {
        val curve = VolatilityCurve(
            listOf(
                step("myrcene", 167, 167, 195),
                step("limonene", 176, 176, 200)
            )
        )

        assertTrue(curve.lostSteps.isEmpty())
        assertTrue(curve.isViable)
    }

    @Test
    fun everyStageCarriesWhatHasNotComeOffYet() {
        val curve = VolatilityCurve(
            listOf(
                step("alpha_pinene", 156, 156, 180),
                step("myrcene", 167, 167, 195),
                step("linalool", 198, 198, 220)
            )
        )

        // At 156 °C only the pinene has started, so both of the others are still
        // pending. The count falls by one per rung because the rungs are sorted.
        assertEquals(listOf(2, 1, 0), curve.stages.map { it.pending.size })
        assertEquals(listOf(1, 2, 3), curve.stages.map { it.arrived.size })
        assertEquals(
            listOf("myrcene, linalool", "linalool", ""),
            curve.stages.map { it.pending.joinToString(", ") { p -> p.labelEs } }
        )
    }

    @Test
    fun twoCompoundsSharingAFloorAreBothStillPendingForNeither() {
        // The quantisation to 10 °C makes ties common, and a tie is information:
        // both compounds arrive at the same temperature.
        val curve = VolatilityCurve(
            listOf(
                step("a", 167, 160, 200, measured = false),
                step("b", 169, 160, 200, measured = false)
            )
        )

        assertEquals(listOf(0, 0), curve.stages.map { it.pending.size })
        assertEquals(listOf(2, 2), curve.stages.map { it.arrived.size })
    }

    @Test
    fun anEmptyCurveSaysItIsEmptyRatherThanThrowing() {
        val curve = VolatilityCurve(emptyList())

        assertTrue(curve.isEmpty)
        assertFalse(curve.isViable)
        assertNull(curve.floorC)
        assertNull(curve.ceilingC)
        assertTrue(curve.stages.isEmpty())
        assertNull(EntouragePlanner.curveFor(emptySet(), emptyList()))
        assertNull(EntouragePlanner.curveFor(setOf(EntourageTerpene.MYRCENE), emptyList()))
    }

    @Test
    fun theScaleSpansEveryBandSoNoBarEverHasToBeClamped() {
        val curve = VolatilityCurve(
            listOf(
                step("vanillin", 285, 280, 310, measured = false),
                step("hexanal", 131, 130, 160, measured = false)
            )
        )

        assertEquals(130, curve.scaleMinC)
        assertEquals(310, curve.scaleMaxC)
        curve.stages.forEach { stage ->
            assertTrue(
                "${stage.step.catalogId} opens below the scale's start",
                stage.opensAtC >= curve.scaleMinC
            )
            assertTrue(
                "${stage.step.catalogId} closes above the scale's end",
                stage.closesAtC <= curve.scaleMaxC
            )
        }
    }

    @Test
    fun everyBarStartsInsideTheScaleAndHasAPositiveWidth() {
        val curve = VolatilityCurve(
            listOf(
                step("hexanal", 131, 130, 160, measured = false),
                step("myrcene", 167, 160, 200, measured = false),
                step("vanillin", 285, 280, 310, measured = false)
            )
        )

        curve.steps.forEach { step ->
            val bar = curve.barFor(step)
            assertTrue(
                "${step.catalogId}: start ${bar.startFraction} must be >= 0",
                bar.startFraction >= 0f
            )
            assertTrue(
                "${step.catalogId}: width ${bar.widthFraction} must be > 0",
                bar.widthFraction > 0f
            )
            assertTrue(
                "${step.catalogId}: start ${bar.startFraction} must be < 1",
                bar.startFraction < 1f
            )
        }
    }

    @Test
    fun theSubjectOfThePageIsFoundByItsCatalogId() {
        val curve = VolatilityCurve(
            listOf(step("hexanal", 131, 130, 160), step("myrcene", 167, 167, 195))
        )

        assertEquals("myrcene", curve.stepFor("myrcene")?.catalogId)
        assertNull(curve.stepFor("not_a_compound"))
        assertNull(curve.stepFor(null))
    }

    @Test
    fun theCurveCountsHowManyBandsItIsShowingWereDerived() {
        val curve = VolatilityCurve(
            listOf(
                step("myrcene", 167, 167, 195),
                step("bisabolol", 263, 260, 300, measured = false)
            )
        )

        assertEquals(1, curve.derivedCount)
    }

    @Test
    fun aCurveFromUnknownIdsIsEmptyRatherThanInvented() {
        val index = TerpeneVolatilityIndex.from(listOf(row("myrcene")), emptyList())

        val curve = index.curveFor(listOf("nope", "myrcene", "myrcene"))

        assertEquals(
            "an unknown id is skipped and a duplicate collapses; nothing is invented",
            listOf("myrcene"),
            curve.steps.map { it.catalogId }
        )
        assertTrue(index.curveFor(listOf("nope")).isEmpty)
    }

    /* ── The copy: the marker and the evidence ───────────────────────────── */

    private val derived = TerpeneVolatility(
        catalogId = "myrcene",
        labelEs = "Mirceno",
        family = TerpeneFamily.MONOTERPENE,
        molarMassEs = "136.24",
        boilingPointC = 167,
        boilingPointEs = "167 °C",
        window = VolatilityDerivation.derive(167, TerpeneFamily.MONOTERPENE)
    )

    private val shipped = TerpeneVolatility(
        catalogId = "alpha_pinene",
        labelEs = "Pineno alfa",
        family = TerpeneFamily.MONOTERPENE,
        molarMassEs = "136.24",
        boilingPointC = 156,
        boilingPointEs = "156 °C",
        window = VolatilityWindow(156, 180, VolatilityProvenance.MEASURED),
        noteEs = "Es el primero en salir."
    )

    @Test
    fun aDerivedNumberAlwaysCarriesTheApproximationMarker() {
        val content = TerpeneVolatilityCopy.contentOf(derived)

        assertTrue(
            "an estimated band printed without '≈' is indistinguishable from a measured one",
            content.windowEs.startsWith(TerpeneVolatilityCopy.ESTIMATE_MARK)
        )
        assertEquals("≈ 160–200 °C", content.windowEs)
    }

    @Test
    fun aMeasuredNumberNeverCarriesTheApproximationMarker() {
        val content = TerpeneVolatilityCopy.contentOf(shipped)

        assertFalse(
            "a band the table measures is not an estimate, and marking it as one " +
                "would discredit the ten rows the app can actually stand behind",
            content.windowEs.contains(TerpeneVolatilityCopy.ESTIMATE_MARK)
        )
        assertEquals("156–180 °C", content.windowEs)
    }

    @Test
    fun theBoilingPointIsNeverMarkedAsAnEstimate() {
        // It is measured on all 158 compounds, derived band or not. Marking it
        // would be its own kind of inaccuracy.
        listOf(derived, shipped).forEach { volatility ->
            val content = TerpeneVolatilityCopy.contentOf(volatility)
            assertFalse(
                "${volatility.catalogId}: the encyclopedia's boiling point is measured",
                content.boilingPointEs.contains(TerpeneVolatilityCopy.ESTIMATE_MARK)
            )
        }
    }

    @Test
    fun theDerivedEvidenceLineSaysWhichHalfIsMeasured() {
        val content = TerpeneVolatilityCopy.contentOf(derived)

        assertTrue(
            "it must name the measured part: ${content.evidenceEs}",
            content.evidenceEs.contains("167 °C") &&
                content.evidenceEs.contains("medido") &&
                content.evidenceEs.contains("enciclopedia")
        )
        assertTrue(
            "it must name the estimated part: ${content.evidenceEs}",
            content.evidenceEs.contains("estimada") &&
                content.evidenceEs.contains("calculado") &&
                content.evidenceEs.contains("Monoterpeno")
        )
        assertTrue(
            "it must refuse the measured reading: ${content.evidenceEs}",
            content.evidenceEs.contains("No es una ventana medida")
        )
    }

    @Test
    fun theMeasuredEvidenceLineSaysWhereTheBandCameFrom() {
        val content = TerpeneVolatilityCopy.contentOf(shipped)

        assertTrue(
            content.evidenceEs.contains("Ventana medida") &&
                content.evidenceEs.contains("tabla de Séquito") &&
                content.evidenceEs.contains("enciclopedia")
        )
    }

    @Test
    fun bothKindsOfBandStateWhatABandIsNot() {
        listOf(derived, shipped).forEach { volatility ->
            val content = TerpeneVolatilityCopy.contentOf(volatility)
            assertEquals(TerpeneVolatilityCopy.LIMITS_ES, content.limitsEs)
            assertTrue(
                "${volatility.catalogId}: the limits line must not be blank",
                content.limitsEs.isNotBlank()
            )
        }
    }

    @Test
    fun theProvenanceLabelIsAlwaysCarriedSoAScreenCannotRenderWithoutIt() {
        assertEquals(
            VolatilityProvenance.DERIVED.labelEs,
            TerpeneVolatilityCopy.contentOf(derived).provenanceLabelEs
        )
        assertEquals(
            VolatilityProvenance.MEASURED.labelEs,
            TerpeneVolatilityCopy.contentOf(shipped).provenanceLabelEs
        )
    }

    @Test
    fun aMeasuredKeepsTheModulesNoteAndADerivedHasNoneToShow() {
        assertEquals("Es el primero en salir.", TerpeneVolatilityCopy.contentOf(shipped).noteEs)
        assertEquals("", TerpeneVolatilityCopy.contentOf(derived).noteEs)
    }

    /* ── The curve copy ──────────────────────────────────────────────────── */

    @Test
    fun theCurveNamesTheSelectionItCovers() {
        val curve = VolatilityCurve(
            listOf(step("myrcene", 167, 167, 195), step("limonene", 176, 176, 200))
        )

        val content = VolatilityCurveCopy.contentOf(curve, "Mirceno", partnerCount = 1)

        assertTrue(content.scopeEs.contains("Mirceno"))
        assertTrue(content.scopeEs.contains("1"))
        assertTrue(
            "a curve without its scope reads as a profile plan",
            content.scopeEs.contains("No es un plan")
        )
    }

    @Test
    fun aCurveOfOneSaysItIsNotACurve() {
        val curve = VolatilityCurve(listOf(step("myrcene", 167, 167, 195)))

        val content = VolatilityCurveCopy.contentOf(curve, "Mirceno", partnerCount = 0)

        assertTrue(content.scopeEs.contains("no tiene curva"))
    }

    @Test
    fun everyStageLineCarriesItsOwnBandAndWhatComesAfter() {
        val curve = VolatilityCurve(
            listOf(
                step("alpha_pinene", 156, 156, 180),
                step("linalool", 198, 198, 220)
            )
        )

        val content = VolatilityCurveCopy.contentOf(curve, "Pineno alfa", partnerCount = 1)

        assertEquals(2, content.stageLinesEs.size)
        assertEquals(
            "the first rung's range must lead the line",
            "156–180 °C",
            content.stageLinesEs[0].substringBefore(" · ")
        )
        assertEquals(
            "the first rung must name what has not come off yet",
            "156–180 °C · alpha_pinene — Después siguen: linalool",
            content.stageLinesEs[0]
        )
        assertEquals(
            "the last rung has nothing after it, so the line stops",
            "198–220 °C · linalool",
            content.stageLinesEs[1]
        )
    }

    @Test
    fun theCurveCopyMarksADerivedRungTheSameWayTheRowDoes() {
        val curve = VolatilityCurve(
            listOf(
                step("myrcene", 167, 167, 195),
                step("bisabolol", 263, 260, 300, measured = false)
            )
        )

        val content = VolatilityCurveCopy.contentOf(curve, "Mirceno", partnerCount = 1)

        assertFalse(content.stageLinesEs[0].startsWith(TerpeneVolatilityCopy.ESTIMATE_MARK))
        assertTrue(
            "the derived rung must carry the marker: ${content.stageLinesEs[1]}",
            content.stageLinesEs[1].startsWith(TerpeneVolatilityCopy.ESTIMATE_MARK)
        )
        assertTrue(
            "the card must say how many of its bands are estimates: ${content.derivedWarningEs}",
            content.derivedWarningEs.contains("1 de 2") &&
                content.derivedWarningEs.contains("estimaciones")
        )
    }

    @Test
    fun anAllMeasuredCurveWarnsAboutNothing() {
        val curve = VolatilityCurve(
            listOf(step("myrcene", 167, 167, 195), step("limonene", 176, 176, 200))
        )

        val content = VolatilityCurveCopy.contentOf(curve, "Mirceno", partnerCount = 1)

        assertEquals("", content.derivedWarningEs)
        assertEquals("", content.contradictionEs)
        assertTrue(content.aggregateEs.contains("Una sola pasada"))
    }

    @Test
    fun theContradictionIsStatedAndTheLossIsNamed() {
        val curve = VolatilityCurve(
            listOf(
                step("alpha_pinene", 156, 156, 180),
                step("beta_caryophyllene", 262, 250, 280)
            )
        )

        val content = VolatilityCurveCopy.contentOf(curve, "Pineno alfa", partnerCount = 1)

        assertTrue(
            "the crossing must be stated: ${content.contradictionEs}",
            content.contradictionEs.contains("250") && content.contradictionEs.contains("180")
        )
        assertTrue(
            "the compound that is lost must be named: ${content.aggregateEs}",
            content.aggregateEs.contains("alpha_pinene")
        )
        assertTrue(
            "the sentence has to say what going that hot costs: ${content.aggregateEs}",
            content.aggregateEs.contains("ya se fueron")
        )
    }

    @Test
    fun theBoosterReportCarriesTheSameCurveAndItsStages() {
        val rows = listOf(
            measured(EntourageTerpene.ALPHA_PINENE, 156, 156, 180),
            measured(EntourageTerpene.LINALOOL, 198, 198, 220)
        )

        val report = EntourageBooster.vapourReport(
            setOf(EntourageTerpene.ALPHA_PINENE, EntourageTerpene.LINALOOL),
            rows
        )

        assertNotNull(report.curve)
        assertEquals(2, report.stageLinesEs.size)
        assertEquals("", report.derivedWarningEs)
        assertEquals(report.window!!.isViable, report.curve!!.isViable)
    }

    @Test
    fun aBoosterReportWithNoCurveStillRenders() {
        val report = EntourageVapourReport(
            rows = emptyList(),
            window = null,
            windowEs = "",
            contradictionEs = "",
            missingEs = "sin datos"
        )

        assertTrue(report.stageLinesEs.isEmpty())
        assertEquals("", report.derivedWarningEs)
    }
}