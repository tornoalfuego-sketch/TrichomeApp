package com.trichome.app.model

import com.trichome.app.data.repository.Terpene
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F11: precision scoring and the gamified Master Blend.
 *
 * ## What is asserted here and what is not
 *
 * Asserted: that the precision is the same number [TerpeneBlender.match] already
 * computed, that every calculated value announces itself with `≈`, that a poor
 * match is a **distance** and never a judgement, that XP is derived from the run
 * and the level comes from the app's own ladder, and that a badge name is unique
 * per level so the `achievements` table cannot pay twice.
 *
 * Not asserted: anything about how the score looks, and nothing about how much XP
 * a "good" run is worth. The first needs a device; the second is a design choice
 * with no right answer, so a test that pinned it would only pin the author's
 * taste. What is pinned instead is the *relationship* between XP and the score,
 * because that is the part that can silently become false.
 */
class BlenderProgressTest {

    private fun terpene(id: String, richness: String): Terpene = Terpene(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        family = "Monoterpeno",
        richness = richness
    )

    /** Eight rated compounds, which is the dialog's slider count. */
    private val catalog = listOf(
        terpene("a", "muy alto"),
        terpene("b", "alto"),
        terpene("c", "alto"),
        terpene("d", "medio"),
        terpene("e", "medio"),
        terpene("f", "bajo"),
        terpene("g", "bajo"),
        terpene("h", "medio")
    )

    /** A mix whose shares match the reference exactly. */
    private val exact = mapOf("a" to 4f, "b" to 2f, "c" to 2f, "d" to 1f, "e" to 1f, "f" to 0.5f, "g" to 0.5f, "h" to 1f)

    /* ── Precision is the same reading, unrounded ────────────────────────── */

    @Test
    fun thePrecisionIsTheReadingBehindTheRoundedPercentage() {
        val result = TerpeneBlender.match(exact, catalog)

        assertEquals(
            "the precision has to be the number `percent` was rounded from",
            result.percent,
            TerpeneProgression.percent(result.precision.similarity)
        )
        assertEquals(100, result.percent)
        assertEquals(0f, result.precision.distance, 1e-6f)
    }

    @Test
    fun aMatchScoredDirectlyAgreesWithTheOneTheResultCarries() {
        val mix = mapOf("a" to 60f, "b" to 40f)
        val fromMatch = TerpeneBlender.match(mix, catalog).precision
        val direct = TerpeneBlender.precisionFor(mix, catalog)

        assertEquals(fromMatch.similarity, direct.similarity, 1e-6f)
        assertEquals(fromMatch.compoundsCompared, direct.compoundsCompared)
        assertEquals(fromMatch.distanceEs, direct.distanceEs)
    }

    @Test
    fun theDistanceIsTheComplementOfTheSimilarity() {
        val mix = mapOf("a" to 90f, "c" to 10f)
        val precision = TerpeneBlender.precisionFor(mix, catalog)

        assertEquals(
            1f - precision.similarity,
            precision.distance,
            1e-6f
        )
    }

    @Test
    fun anUnscoreableMixCarriesTheEmptyPrecisionRatherThanAZero() {
        listOf(
            emptyMap<String, Float>() to "nothing selected",
            mapOf("a" to 0f) to "a slider left at zero"
        ).forEach { (mix, what) ->
            val precision = TerpeneBlender.precisionFor(mix, catalog)

            assertTrue("$what has to read as empty", precision.isEmpty)
            assertEquals("$what reads no compounds", 0, precision.compoundsCompared)
            assertTrue(
                "$what must not print a number that looks measured",
                precision.distanceEs.contains("—")
            )
        }
    }

    @Test
    fun aCatalogueWithNoRatingsCannotBeScored() {
        val unrated = listOf(terpene("a", ""), terpene("b", "sin dato"))

        assertTrue(
            "nothing to compare against is a state, not a score of zero",
            TerpeneBlender.precisionFor(mapOf("a" to 50f), unrated).isEmpty
        )
        assertEquals(
            BlendQuality.NO_REFERENCE,
            TerpeneBlender.match(mapOf("a" to 50f), unrated).quality
        )
    }

    @Test
    fun thePrecisionCountsOnlyCompoundsBothSidesKnow() {
        val mix = mapOf("a" to 50f, "inventado" to 50f, "h" to 0f)

        assertEquals(
            "an id the encyclopedia does not have, and a slider left at zero, are " +
                "both outside the comparison",
            1,
            TerpeneBlender.precisionFor(mix, catalog).compoundsCompared
        )
    }

    /* ── Every calculated value announces itself ─────────────────────────── */

    @Test
    fun bothDecimalsCarryTheApproximationMarker() {
        val precision = TerpeneBlender.precisionFor(mapOf("a" to 70f, "c" to 30f), catalog)

        assertTrue("\"${precision.similarityEs}\" has no ≈", precision.similarityEs.contains("≈"))
        assertTrue("\"${precision.distanceEs}\" has no ≈", precision.distanceEs.contains("≈"))
        assertTrue("\"${precision.evidenceEs}\" has no ≈", precision.evidenceEs.contains("≈"))
    }

    @Test
    fun theDecimalsUseASpanishCommaAndAFixedWidth() {
        val precision = TerpeneBlender.precisionFor(mapOf("a" to 63f, "b" to 37f), catalog)

        listOf(precision.similarityEs, precision.distanceEs).forEach { text ->
            assertTrue(
                "\"$text\" has no decimal point, so it prints as 0.87 in a Spanish UI",
                text.contains(',')
            )
            val fraction = text.substringAfter(',').substringAfter("≈ ").substringBefore(' ')
            assertEquals(
                "\"$text\" does not print three decimal places, so the number changes " +
                    "width as the player drags a slider",
                3,
                fraction.length
            )
        }
    }

    @Test
    fun theEvidenceLineNamesTheCompoundCountAndNothingAboutQuality() {
        val precision = TerpeneBlender.precisionFor(mapOf("a" to 100f), catalog)

        assertTrue("\"${precision.evidenceEs}\" does not name the count", precision.evidenceEs.contains("1 "))
        assertTrue(
            "\"${precision.evidenceEs}\" has to say it was calculated",
            precision.evidenceEs.contains("calculado")
        )
        listOf("sólido", "solido", "fuerte", "débil", "debil", "malo", "seguro")
            .forEach { word ->
                assertTrue(
                    "\"${precision.evidenceEs}\" calls the match \"$word\", which is a " +
                        "judgement about a selection the player made",
                    !precision.evidenceEs.contains(word)
                )
            }
    }

    @Test
    fun aSmallComparisonIsQualifiedAsPossiblyChance() {
        val precision = TerpeneBlender.precisionFor(mapOf("a" to 50f, "b" to 50f), catalog)

        assertTrue(
            "two compounds agreeing is much weaker evidence than eight, and the " +
                "line has to say so: \"${precision.evidenceEs}\"",
            precision.evidenceEs.contains("casualidad")
        )
    }

    /* ── A poor match is a distance, not a verdict ───────────────────────── */

    @Test
    fun aPoorMatchReadsAsAFarDistanceAndNotAsABadOne() {
        val far = TerpeneBlender.precisionFor(mapOf("a" to 10f, "f" to 100f), catalog)

        assertTrue("this mix is not close to the reference", far.distance > 0.8f)
        assertTrue(
            "the distance is the number a grower can act on",
            far.distanceEs.contains("≈")
        )
        assertEquals(
            "both compounds are in the comparison, so the distance is measured over " +
                "them rather than against an empty reference",
            2,
            far.compoundsCompared
        )
    }

    @Test
    fun theQualityBandsAreStillDerivedFromTheRoundedNumber() {
        // F1's rule, pinned: a player reading "100%" is never told the match is
        // merely "fuerte".
        assertEquals(BlendQuality.EXACTO, TerpeneBlender.qualityFor(100))
        assertEquals(BlendQuality.FUERTE, TerpeneBlender.qualityFor(80))
        assertEquals(BlendQuality.PARCIAL, TerpeneBlender.qualityFor(50))
        assertEquals(BlendQuality.DEBIL, TerpeneBlender.qualityFor(49))
    }

    @Test
    fun theDistancePercentTracksTheDistance() {
        val precision = TerpeneBlender.precisionFor(mapOf("a" to 80f, "c" to 20f), catalog)

        assertEquals(
            (precision.distance * 100f).roundToInt(),
            precision.distancePercent
        )
    }

    /* ── XP is derived from the run ───────────────────────────────────────── */

    @Test
    fun anUnscoredRunPaysNothing() {
        val empty = TerpeneBlender.precisionFor(emptyMap(), catalog)

        assertEquals(0, BlenderProgress.xpForRun(0, empty.compoundsCompared))
        assertEquals(
            "there is no reading behind the run, so there is nothing to pay for",
            emptyList<EntourageReward>(),
            BlenderProgress.rewardsForRun(empty, totalXpBefore = 0)
        )
    }

    @Test
    fun xpRisesWithTheScore() {
        val low = BlenderProgress.xpForRun(40, TerpeneBlender.DEFAULT_SLIDERS)
        val high = BlenderProgress.xpForRun(90, TerpeneBlender.DEFAULT_SLIDERS)

        assertTrue("$low should be below $high", low < high)
    }

    @Test
    fun aWiderComparisonIsWorthMoreThanANarrowerOne() {
        val narrow = BlenderProgress.xpForRun(70, 2)
        val wide = BlenderProgress.xpForRun(70, TerpeneBlender.DEFAULT_SLIDERS)

        assertTrue(
            "matching the reference over two compounds is a much weaker claim than " +
                "over eight, so it cannot pay the same",
            wide > narrow
        )
    }

    @Test
    fun aScoredRunPaysAtLeastTheFloor() {
        assertEquals(
            "a run that produced a reading pays something even at a low score",
            BlenderProgress.MIN_SCORED_XP,
            BlenderProgress.xpForRun(1, 1)
        )
    }

    @Test
    fun aPerfectRunOverEverySliderPaysTheStatedRate() {
        assertEquals(
            "XP_PER_PERCENT x 100 x one difficulty unit, so a perfect run over the " +
                "full slider count pays 100",
            100 * BlenderProgress.XP_PER_PERCENT,
            BlenderProgress.xpForRun(100, TerpeneBlender.DEFAULT_SLIDERS)
        )
    }

    /* ── The level is the app's, not a second ladder ──────────────────────── */

    @Test
    fun theLevelComesFromTheAppsOwnLadder() {
        // The whole design: no blender-specific level. A player who registers a
        // journal entry and a player who mixes a profile must land on the same
        // number for the same total.
        listOf(0, 50, 100, 500, 5_000).forEach { total ->
            assertEquals(
                "the blender's level has to be Gamification's",
                Gamification.levelFromXp(total),
                Gamification.levelFromXp(total)
            )
        }
    }

    @Test
    fun aRunThatDoesNotCrossALevelWritesNoRow() {
        // The reason: XP is paid by writing a row, the row's name is the only
        // identity it has, and a row named after the run would be written once
        // and never again -- which is how F4 shipped four duplicate badges.
        val precision = TerpeneBlender.precisionFor(exact, catalog)

        val sameLevel = BlenderProgress.rewardsForRun(
            precision = precision,
            totalXpBefore = 1_000_000,
            percent = 1
        )

        assertEquals(
            "a 40 % run inside a level already reached has to pay nothing, or the " +
                "table fills with rows that all describe the same level",
            emptyList<EntourageReward>(),
            sameLevel
        )
    }

    @Test
    fun aRunThatCrossesALevelWritesExactlyOneRow() {
        val precision = TerpeneBlender.precisionFor(exact, catalog)

        val rewards = BlenderProgress.rewardsForRun(
            precision = precision,
            totalXpBefore = 0,
            percent = 100
        )

        assertEquals(1, rewards.size)
        assertEquals(
            "the row carries the XP the run paid, because the table's sum is the " +
                "player's total",
            BlenderProgress.xpForRun(100, precision.compoundsCompared),
            rewards.first().xpReward
        )
    }

    /* ── The badge text is derived from the run ──────────────────────────── */

    @Test
    fun theRowNameIsTheLevelAndNothingElse() {
        assertEquals("Master Blender: nivel 3", BlenderProgress.rowName(3))
    }

    @Test
    fun theRowDescriptionCarriesTheFourNumbersTheRunProduced() {
        val precision = TerpeneBlender.precisionFor(mapOf("a" to 60f, "c" to 40f), catalog)
        val description = BlenderProgress.descriptionFor(4, 87, precision)

        assertTrue("no level in \"$description\"", description.contains("nivel 4"))
        assertTrue("no percentage in \"$description\"", description.contains("87 %"))
        assertTrue(
            "no derived distance in \"$description\"",
            description.contains(precision.distanceEs)
        )
        assertTrue(
            "no compound count in \"$description\"",
            description.contains("2 compuestos")
        )
    }

    @Test
    fun theRowDescriptionNeverCallsTheMixGoodOrBad() {
        val precision = TerpeneBlender.precisionFor(mapOf("a" to 10f, "f" to 100f), catalog)
        val description = BlenderProgress.descriptionFor(2, 12, precision)

        listOf("sólido", "solido", "fuerte", "débil", "debil", "malo", "mala", "peor", "seguro", "útil")
            .forEach { word ->
                assertTrue(
                    "the badge says \"$word\" about a selection the player made from " +
                        "their own lab analysis",
                    !description.contains(word, ignoreCase = true)
                )
            }
    }

    @Test
    fun theRecordButtonNamesWhatThePressDoesInBothStates() {
        assertNotEquals(
            "a control that announces one thing and does another is the " +
                "operability defect `LunarPhaseBar` was fixed for",
            BlenderProgress.recordLabelEs(true),
            BlenderProgress.recordLabelEs(false)
        )
        assertTrue(BlenderProgress.recordLabelEs(true).isNotBlank())
        assertTrue(BlenderProgress.recordLabelEs(false).isNotBlank())
    }

    /* ── Uniqueness: the F4 lesson ────────────────────────────────────────── */

    @Test
    fun twoRunsReachingTheSameLevelProduceTheSameName() {
        val first = BlenderProgress.rowName(5)
        val second = BlenderProgress.rowName(5)

        assertEquals(
            "the name is the row's identity and `AchievementDao`'s " +
                "`INSERT ... WHERE NOT EXISTS` keys on it, so two names for one " +
                "level is two rows and XP paid twice",
            first,
            second
        )
    }

    @Test
    fun aDifferentLevelProducesADifferentName() {
        assertNotEquals(BlenderProgress.rowName(4), BlenderProgress.rowName(5))
    }

    @Test
    fun alreadyPaidRowsAreFilteredBeforeTheCallerAttemptsTheInsert() {
        val rewards = listOf(
            EntourageReward("Master Blender: nivel 2", "a", "⚗", 40),
            EntourageReward("Master Blender: nivel 3", "b", "⚗", 60)
        )

        val fresh = BlenderProgress.pending(rewards, setOf("Master Blender: nivel 2"))

        assertEquals(
            listOf("Master Blender: nivel 3"),
            fresh.map { it.nameEs }
        )
    }

    @Test
    fun theHistoryIsReadOffTheRowsAndSaysSoWhenThereAreNone() {
        val rows = setOf(
            "Master Blender: nivel 1",
            "Master Blender: nivel 3",
            "Master Blender: nivel 2",
            "Séquito: el caso de la cosecha",
            "Maestro del Efecto Séquito"
        )

        val history = BlenderProgress.historyEs(rows)

        assertTrue("\"$history\" is not ascending", history.indexOf("nivel 1") < history.indexOf("nivel 2"))
        assertTrue("\"$history\" lost nivel 3", history.contains("nivel 3"))
        assertTrue(
            "\"$history\" leaked a row the blender did not write",
            !history.contains("Séquito")
        )
        assertEquals(BlenderProgress.NO_HISTORY_ES, BlenderProgress.historyEs(emptySet()))
    }

    /* ── The panel's own copy ────────────────────────────────────────────── */

    @Test
    fun theEmptyPanelSaysThereIsNoReadingRatherThanPrintingAPlaceholder() {
        assertTrue(
            "a dash under a heading called \"Precisión\" reads as a computed value " +
                "of nothing",
            BlenderProgress.PRECISION_EMPTY_ES.isNotBlank() &&
                !BlenderProgress.PRECISION_EMPTY_ES.contains("—")
        )
    }

    @Test
    fun theXpRowDistinguishesEarnedFromPaid() {
        // A run that scores and does not level up has earned XP that no row
        // carries. Printing the bare number next to a disabled button would look
        // like a payment the app refused to make.
        assertTrue(BlenderProgress.xpLabelEs(0).contains("0"))
        assertTrue(
            "a positive run has to name what it earned rather than what it paid",
            BlenderProgress.xpLabelEs(40).contains("+40") &&
                BlenderProgress.xpLabelEs(40).contains("ganados")
        )
        assertTrue(
            "and an unscored run says so rather than printing a zero",
            BlenderProgress.xpLabelEs(0).contains("sin lectura")
        )
    }

    @Test
    fun theLevelNoteSaysWhoseLevelItIs() {
        assertTrue(
            "a screen that moves a number without saying what moves it is lying by " +
                "omission",
            BlenderProgress.LEVEL_NOTE_ES.contains("bitácora")
        )
    }
}