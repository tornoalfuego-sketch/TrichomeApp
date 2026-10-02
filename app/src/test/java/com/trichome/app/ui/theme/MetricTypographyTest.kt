package com.trichome.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.trichome.app.model.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the instrumented type register: the two roles, the rule that keeps them
 * monospace whatever the reader picked for prose, and the promise that nothing
 * else in the app decides a typeface on its own.
 *
 * ## Why this exists
 *
 * The app had exactly one monospace number — a `DataRow` value on the terpene
 * detail page — and it was written inline at the call site:
 * `bodyMedium.copy(FontWeight.Medium, FontFamily.Monospace)`. Every other measured
 * value in the app was rendered in the app-wide prose face, which on the device
 * this was verified on resolves to a handwriting font. So this is not a styling
 * preference that was consolidated: it is the difference between a pH reading that
 * can be read and one that cannot.
 *
 * ## Why half of it reads source
 *
 * Compose has no unit-test runtime in this project — neither Robolectric nor
 * `compose-ui-test` is on the `test` classpath, and four separate bug classes have
 * already come from composable-level logic no JVM test could reach. So the
 * *values* are asserted on the real objects `buildTypography` returns (nothing is
 * mocked, `isReturnDefaultValues = true` does not touch Compose's pure text types),
 * and the *wiring* — which file declares the family, which call site reads the role
 * — is asserted against the source with the comments stripped first. That last rule
 * is not decoration: the KDoc on `metricFontFamily` and on `DataRow` both name
 * `FontFamily.Monospace` in prose, so an unstripped scan would be asserting against
 * its own explanation.
 */
class MetricTypographyTest {

    /* ── Source access ────────────────────────────────────────────────────── */

    private fun sourceFile(relative: String): File = listOf(
        File("src/main/java/com/$relative"),
        File("app/src/main/java/com/$relative")
    ).firstOrNull { it.isFile } ?: error("could not locate $relative")

    private fun sourceDir(relative: String): File = listOf(
        File("src/main/java/com/$relative"),
        File("app/src/main/java/com/$relative")
    ).firstOrNull { it.isDirectory } ?: error("could not locate $relative")

    private fun kotlinFilesUnder(relative: String): List<File> =
        sourceDir(relative).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * Block comments first, then line comments.
     *
     * In that order: a `//` sitting inside a block comment would otherwise be
     * treated as the start of a line comment and swallow the rest of the block.
     * This exact ordering has broken twice in this repo.
     */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    private fun strippedCodeOf(relative: String): String =
        stripComments(sourceFile(relative).readText(Charsets.UTF_8))

    /** Every `.kt` under `ui/`, comments stripped, keyed by path for the message. */
    private fun uiSource(): Map<String, String> = kotlinFilesUnder("trichome/app/ui")
        .associate { it.path to stripComments(it.readText(Charsets.UTF_8)) }

    /* ── The guards against an inert scan ─────────────────────────────────── */

    @Test
    fun theSourceScansAreReadingRealFiles() {
        val code = strippedCodeOf("trichome/app/ui/theme/TrichomeTheme.kt")
        assertTrue("TrichomeTheme.kt was not read", code.length > 5000)
        assertTrue(
            "the comment stripper removed the whole theme file",
            code.contains("fun buildTypography")
        )

        val files = uiSource()
        assertTrue("no ui/ source files were found at all", files.size > 30)
        // A file that *should* have been read has to be in the map, or the scans
        // below are proving something about an empty set.
        assertTrue(
            "TerpeneDetailScreen.kt is missing from the scan, so the scan is inert",
            files.keys.any { it.endsWith("TerpeneDetailScreen.kt") }
        )
    }

    /* ── The roles exist and are monospace ────────────────────────────────── */

    @Test
    fun bothMetricRolesAreMonospaceAtTheDefaultPreferences() {
        val built = buildTypography(1.0f)

        assertEquals(
            "a value in a row is no longer set in the instrumented family",
            FontFamily.Monospace, built.metricValue.fontFamily
        )
        assertEquals(
            "the lead number is no longer set in the instrumented family",
            FontFamily.Monospace, built.metricHeadline.fontFamily
        )
    }

    @Test
    fun theMetricRolesStayMonospaceWhenTheReaderPicksSerifOrScript() {
        // The guarantee, per family. `metricFontFamily` takes the prose family and
        // ignores it, so this is not a formality — if it ever returned
        // `prose.family`, these two loops are what would catch it.
        AppFontFamily.entries.forEach { family ->
            val built = buildTypography(1.0f, family)
            assertEquals(
                "a value rendered in ${family.label} left the instrumented family",
                FontFamily.Monospace, built.metricValue.fontFamily
            )
            assertEquals(
                "the lead number rendered in ${family.label} left the instrumented family",
                FontFamily.Monospace, built.metricHeadline.fontFamily
            )
        }
    }

    @Test
    fun theFixtureIsNotInertBecauseProseReallyDoesFollowTheReadersChoice() {
        // The other half of the test above. If prose ignored the family setting
        // too, "the metric roles stay monospace" would pass for the wrong reason —
        // everything would be monospace, or nothing would.
        val nonMonospace = AppFontFamily.entries.filter { it.family != FontFamily.Monospace }
        assertTrue(
            "the sweep needs at least two prose families that are not monospace",
            nonMonospace.size >= 2
        )
        nonMonospace.forEach { family ->
            assertEquals(
                "prose is not following ${family.label}, so the metric assertion is vacuous",
                family.family,
                buildTypography(1.0f, family).material.bodyMedium.fontFamily
            )
        }
        assertEquals(
            "the serif fixture no longer resolves to the serif face",
            FontFamily.Serif, buildTypography(1.0f, AppFontFamily.SERIF).material.bodyMedium.fontFamily
        )
        assertEquals(
            "the script fixture no longer resolves to the cursive face",
            FontFamily.Cursive, buildTypography(1.0f, AppFontFamily.SCRIPT).material.bodyMedium.fontFamily
        )
    }

    @Test
    fun theFamilyHelperIsTheOneDeclarationAndIgnoresItsArgument() {
        AppFontFamily.entries.forEach { family ->
            assertEquals(
                "metricFontFamily(${family.name}) stopped returning the instrumented family",
                FontFamily.Monospace, metricFontFamily(family)
            )
        }
    }

    /* ── The two sizes, and the numbers behind them ───────────────────────── */

    @Test
    fun theTwoMetricSizesAreDistinctAndAreTheOnesTheModelDeclares() {
        val built = buildTypography(1.0f)

        assertEquals(
            "a value in a row is no longer 14sp",
            14f, built.metricValue.fontSize.value, 0.001f
        )
        assertEquals(
            "a value in a row is no longer 20sp of line height",
            20f, built.metricValue.lineHeight.value, 0.001f
        )
        assertEquals(
            "the lead number is no longer 28sp",
            28f, built.metricHeadline.fontSize.value, 0.001f
        )
        assertEquals(
            "the lead number is no longer 34sp of line height",
            34f, built.metricHeadline.lineHeight.value, 0.001f
        )

        assertTrue(
            "the two roles collapsed onto one size, so one of them does not exist",
            built.metricHeadline.fontSize.value > built.metricValue.fontSize.value
        )

        // The constants, cross-checked against what the builder actually produced,
        // so a silent edit to either side fails here.
        assertEquals(
            "MetricType.VALUE_FONT_SIZE_SP and the built value style disagree",
            MetricType.VALUE_FONT_SIZE_SP.toFloat(), built.metricValue.fontSize.value, 0.001f
        )
        assertEquals(
            "MetricType.VALUE_LINE_HEIGHT_SP and the built value style disagree",
            MetricType.VALUE_LINE_HEIGHT_SP.toFloat(), built.metricValue.lineHeight.value, 0.001f
        )
        assertEquals(
            "MetricType.HEADLINE_FONT_SIZE_SP and the built headline style disagree",
            MetricType.HEADLINE_FONT_SIZE_SP.toFloat(), built.metricHeadline.fontSize.value, 0.001f
        )
        assertEquals(
            "MetricType.HEADLINE_LINE_HEIGHT_SP and the built headline style disagree",
            MetricType.HEADLINE_LINE_HEIGHT_SP.toFloat(), built.metricHeadline.lineHeight.value, 0.001f
        )
    }

    @Test
    fun theHeadlineLineHeightIsTighterThanTheValueOneWithoutClippingItsOwnAscender() {
        val built = buildTypography(1.0f)
        val valueRatio = MetricType.VALUE_LINE_HEIGHT_SP.toFloat() / MetricType.VALUE_FONT_SIZE_SP
        val headlineRatio =
            MetricType.HEADLINE_LINE_HEIGHT_SP.toFloat() / MetricType.HEADLINE_FONT_SIZE_SP

        assertTrue(
            "the headline is supposed to be tighter than a paragraph, and is not",
            headlineRatio < valueRatio
        )
        // Spanish runs long and its accents raise the ascender. A lead number set
        // on one or two lines still needs room above the cap line; below this the
        // tightest slot in the app would be the one most likely to clip `Índice`.
        assertTrue(
            "the headline ratio of $headlineRatio is too tight for accented Spanish",
            headlineRatio >= 1.15f
        )
    }

    @Test
    fun bothMetricRolesLandOnTheWeightTheModelDeclaresAtTheDefaultPreference() {
        val built = buildTypography(1.0f, AppFontFamily.SANS, AppFontWeight.NORMAL)

        assertEquals(
            "the documented weight and the built value style disagree",
            MetricType.LIFTED_WEIGHT, built.metricValue.fontWeight?.weight
        )
        assertEquals(
            "the documented weight and the built headline style disagree",
            MetricType.LIFTED_WEIGHT, built.metricHeadline.fontWeight?.weight
        )
    }

    @Test
    fun theMetricRolesFollowTheReadersWeightLiftedOneStep() {
        // `lift()` is private to the theme, so the table is spelled out here rather
        // than read back from the function under test. A metric that ignored the
        // reader's weight would leave somebody who chose *Ligera* unable to make
        // the numbers lighter — which is the whole reason they can reach for it.
        val expected = mapOf(
            AppFontWeight.LIGHT to FontWeight.Normal.weight,
            AppFontWeight.NORMAL to FontWeight.Medium.weight,
            AppFontWeight.MEDIUM to FontWeight.SemiBold.weight,
            AppFontWeight.SEMIBOLD to FontWeight.SemiBold.weight,
            AppFontWeight.BOLD to FontWeight.Bold.weight
        )
        assertEquals(
            "the sweep no longer covers every weight the reader can pick",
            AppFontWeight.entries.toSet(), expected.keys
        )

        expected.forEach { (picked, lifted) ->
            val built = buildTypography(1.0f, AppFontFamily.SANS, picked)
            assertEquals(
                "the value style ignored ${picked.label}",
                lifted, built.metricValue.fontWeight?.weight
            )
            assertEquals(
                "the headline style ignored ${picked.label}",
                lifted, built.metricHeadline.fontWeight?.weight
            )
        }
    }

    /* ── The scale reaches the metric roles; tracking does not ────────────── */

    @Test
    fun theMetricSizesSurviveTheReadersSizeScale() {
        // Independent literals, not `MetricType.fontSizeSp(...)` echoed back: the
        // point is the arithmetic, and echoing the helper would pass if the helper
        // itself were wrong.
        val large = buildTypography(1.30f)
        val small = buildTypography(0.85f)

        assertEquals("14sp at 1.30 is not 18.2sp", 18.2f, large.metricValue.fontSize.value, 0.001f)
        assertEquals(
            "20sp at 1.30 is not 26sp", 26f, large.metricValue.lineHeight.value, 0.001f
        )
        assertEquals("28sp at 1.30 is not 36.4sp", 36.4f, large.metricHeadline.fontSize.value, 0.001f)
        assertEquals("34sp at 1.30 is not 44.2sp", 44.2f, large.metricHeadline.lineHeight.value, 0.001f)

        assertEquals("14sp at 0.85 is not 11.9sp", 11.9f, small.metricValue.fontSize.value, 0.001f)
        assertEquals("28sp at 0.85 is not 23.8sp", 23.8f, small.metricHeadline.fontSize.value, 0.001f)

        assertTrue(
            "the metric roles ignore the size slider entirely, which was never true of prose",
            large.metricValue.fontSize.value > small.metricValue.fontSize.value
        )
    }

    @Test
    fun trackingIsStillUnscaledOnProseAndOnTheMetricRoles() {
        // Pre-existing rule, pinned rather than restated: only `fontSize` and
        // `lineHeight` are multiplied by the reader's scale. If someone "fixes"
        // tracking at the same time as adding the metric register, this is the test
        // that says the second change was not authorised.
        val smallest = buildTypography(0.85f)
        val largest = buildTypography(1.30f)

        assertEquals(
            "prose tracking started being scaled by the size preference",
            smallest.material.bodyMedium.letterSpacing.value,
            largest.material.bodyMedium.letterSpacing.value,
            0.0001f
        )
        assertEquals(
            "prose tracking started being scaled by the size preference",
            smallest.material.labelSmall.letterSpacing.value,
            largest.material.labelSmall.letterSpacing.value,
            0.0001f
        )
        assertEquals(
            "the value role's tracking is no longer zero at every scale",
            0f, largest.metricValue.letterSpacing.value, 0.0001f
        )
        assertEquals(
            "the headline role's tracking is no longer zero at every scale",
            0f, smallest.metricHeadline.letterSpacing.value, 0.0001f
        )
    }

    @Test
    fun theTrackingFixtureIsNotInertBecauseProseSlotsActuallyCarryTracking() {
        // Without this, "tracking is unchanged" would also be satisfied by a
        // `letterSpacing` that was never set on anything.
        val built = buildTypography(1.0f)

        assertTrue(
            "bodyMedium carries no tracking, so the invariance above proves nothing",
            built.material.bodyMedium.letterSpacing.value > 0f
        )
        assertTrue(
            "labelSmall carries no tracking, so the invariance above proves nothing",
            built.material.labelSmall.letterSpacing.value > 0f
        )
        assertEquals(
            "MetricType.LETTER_SPACING_SP and the built value style disagree",
            MetricType.LETTER_SPACING_SP.toFloat(), built.metricValue.letterSpacing.value, 0.0001f
        )
    }

    /* ── The fifteen Material slots are untouched ─────────────────────────── */

    /**
     * The slot names, mapped to the size each one builds at scale 1.
     *
     * Read through explicit property accesses, so a rename upstream of this file is
     * a compile error rather than a slot that quietly reads `TextUnit.Unspecified`.
     * The sizes are the pre-existing Material scale: this register adds roles
     * *beside* the typography and does not restyle what was already there.
     */
    private fun slotSizes(built: TrichomeTypography): Map<String, Float> = mapOf(
        "displayLarge" to built.material.displayLarge.fontSize.value,
        "displayMedium" to built.material.displayMedium.fontSize.value,
        "displaySmall" to built.material.displaySmall.fontSize.value,
        "headlineLarge" to built.material.headlineLarge.fontSize.value,
        "headlineMedium" to built.material.headlineMedium.fontSize.value,
        "headlineSmall" to built.material.headlineSmall.fontSize.value,
        "titleLarge" to built.material.titleLarge.fontSize.value,
        "titleMedium" to built.material.titleMedium.fontSize.value,
        "titleSmall" to built.material.titleSmall.fontSize.value,
        "bodyLarge" to built.material.bodyLarge.fontSize.value,
        "bodyMedium" to built.material.bodyMedium.fontSize.value,
        "bodySmall" to built.material.bodySmall.fontSize.value,
        "labelLarge" to built.material.labelLarge.fontSize.value,
        "labelMedium" to built.material.labelMedium.fontSize.value,
        "labelSmall" to built.material.labelSmall.fontSize.value
    )

    private val expectedSlotSizes = mapOf(
        "displayLarge" to 57f,
        "displayMedium" to 45f,
        "displaySmall" to 36f,
        "headlineLarge" to 32f,
        "headlineMedium" to 28f,
        "headlineSmall" to 24f,
        "titleLarge" to 22f,
        "titleMedium" to 16f,
        "titleSmall" to 14f,
        "bodyLarge" to 16f,
        "bodyMedium" to 14f,
        "bodySmall" to 12f,
        "labelLarge" to 14f,
        "labelMedium" to 12f,
        "labelSmall" to 11f
    )

    @Test
    fun everyMaterialSlotStillExistsAtItsShippedSize() {
        // The snapshot. A rename fails to compile here; a silent restyle fails on
        // the comparison, with the offending slot named in the diff.
        assertEquals(
            "the Material typography was restyled: the slot names or the sizes moved",
            expectedSlotSizes,
            slotSizes(buildTypography(1.0f))
        )
    }

    @Test
    fun theFourSlotsTheColourRolesDialogPreviewsStillResolve() {
        // `ColorRolesDialogPreviewTest` reads these four by name off the live
        // typography to decide what each colour-role preview looks like. A rename
        // would not fail that test — the dialog would keep asking for a slot that
        // no longer exists — so the dependency is pinned from this side.
        val built = buildTypography(1.0f).material

        val previews = linkedMapOf(
            "typography.titleMedium" to built.titleMedium,
            "typography.bodyMedium" to built.bodyMedium,
            "typography.labelSmall" to built.labelSmall,
            "typography.labelLarge" to built.labelLarge
        )

        previews.forEach { (name, style) ->
            assertNotEquals(
                "$name resolves to an unspecified size, so the dialog previews nothing",
                TextUnit.Unspecified, style.fontSize
            )
            assertTrue(
                "$name resolves to an unspecified weight, so the dialog previews a " +
                    "weight the app never paints",
                style.fontWeight != null
            )
        }

        assertEquals(
            "the four previewed slots no longer resolve to the sizes the dialog was " +
                "designed around: $previews",
            mapOf(
                "typography.titleMedium" to 16f,
                "typography.bodyMedium" to 14f,
                "typography.labelSmall" to 11f,
                "typography.labelLarge" to 14f
            ),
            previews.mapValues { (_, style) -> style.fontSize.value }
        )

        // And they have to stay told apart from each other, which is the whole
        // point of the dialog: four panels at one size would answer nothing. They
        // land on three sizes today because `bodyMedium` and `labelLarge` are both
        // 14sp and are separated by weight and tracking alone.
        assertEquals(
            "the four previewed slots collapsed onto one size, so the dialog cannot " +
                "show which role owns which level",
            3,
            previews.values.map { it.fontSize.value }.toSet().size
        )
    }

    /* ── Nothing else in the app declares a typeface ──────────────────────── */

    @Test
    fun noFileOutsideTheThemeNamesTheInstrumentedFamily() {
        val offenders = uiSource()
            .filterKeys { !it.endsWith("TrichomeTheme.kt") }
            .filterValues { it.contains("FontFamily.Monospace") }
            .keys
            .toList()

        assertTrue(
            "these declare FontFamily.Monospace on their own, so the metric register " +
                "has been bypassed: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun theThemeNamesTheInstrumentedFamilyExactlyTwiceAndDeclaresNothingInline() {
        val code = strippedCodeOf("trichome/app/ui/theme/TrichomeTheme.kt")
        val declarations = Regex("""FontFamily\.Monospace""").findAll(code).count()

        // Once for `AppFontFamily.MONO` — the reader asking for monospace prose —
        // and once for `metricFontFamily`, the app refusing to let prose decide what
        // a measurement looks like. A third would be a screen growing its own.
        assertEquals(
            "the instrumented family is declared an unexpected number of times in the theme",
            2,
            declarations
        )
        assertTrue(
            "metricFontFamily is not the function that declares the instrumented family",
            Regex("""fun\s+metricFontFamily\([^)]*\)\s*:\s*FontFamily\s*=\s*FontFamily\.Monospace""")
                .containsMatchIn(code)
        )
        // The enum entry, still there and still the reader's own choice.
        assertTrue(
            "the monospace prose option is gone, so the reader lost a typeface choice",
            code.contains("""MONO("Monoespaciada", FontFamily.Monospace)""")
        )
    }

    @Test
    fun noScreenStylesATextWithAnInlineFontFamilyAnyLonger() {
        // The specific shape this replaced: a `copy()` on a Material slot with a
        // family in it. `SettingsScreen` still does `copy(fontWeight = ...)` for a
        // different reason, so the family is what is checked.
        val offenders = uiSource()
            .filterKeys { !it.endsWith("TrichomeTheme.kt") }
            .filterValues { Regex("""\.copy\([^)]*fontFamily""").containsMatchIn(it) }
            .keys
            .toList()

        assertTrue(
            "these override the typeface inline instead of reading a role: $offenders",
            offenders.isEmpty()
        )
    }

    /* ── The wiring: the readouts actually read the roles ──────────────────── */

    @Test
    fun theTerpeneDetailRowReadsTheRoleInsteadOfCarryingItsOwnOverride() {
        val code = strippedCodeOf("trichome/app/ui/screens/terpenes/TerpeneDetailScreen.kt")
        val row = code.substringAfter("private fun DataRow(")
            .substringBefore("\n@Composable")

        assertTrue(
            "the detail row's value no longer reads the metric role",
            row.contains("style = metricValue()")
        )
        assertFalse(
            "the detail row still builds its own style instead of reading the role",
            Regex("""\.copy\(""").containsMatchIn(row)
        )
        // Everything `DataRowLayoutTest` pins about the row, re-asserted here
        // because this is the test that edited it.
        assertTrue(
            "the value's weight is no longer the shared constant",
            row.contains(".weight(DataRowLayout.VALUE_WEIGHT)")
        )
        assertTrue(
            "the value is no longer flush right",
            row.contains("textAlign = TextAlign.End")
        )
        assertFalse(
            "the row regained the arrangement that shipped the collision",
            row.contains("Arrangement.SpaceBetween")
        )
        assertTrue(
            "the gap between label and value is gone",
            row.contains("Spacer(Modifier.width(DataRowLayout.GAP_DP.dp))")
        )
        assertTrue(
            "the page no longer has the seven DataRow call sites its structure test requires",
            Regex("""DataRow\(""").findAll(code).count() >= 7
        )
    }

    @Test
    fun everyMeasuredReadoutThatWasMigratedReadsARoleAndNotAProseSlot() {
        // The call sites this register was applied to. Read by the style expression
        // next to the value, because that is the decision being made at each one.
        val expectations = listOf(
            Triple("trichome/app/ui/components/EstimatedClimateCard.kt", "ClimateStat", "metricValue()"),
            Triple("trichome/app/ui/components/Charts.kt", "NativeLineChart", "metricValue()"),
            Triple("trichome/app/ui/screens/charts/ChartsScreen.kt", "MetricRow", "metricValue()"),
            Triple("trichome/app/ui/screens/cycle/SuperCycleScreen.kt", "SuperCycleScreen", "metricValue()"),
            Triple("trichome/app/ui/screens/cycle/SuperCycleScreen.kt", "StatLabel", "metricValue()"),
            Triple("trichome/app/ui/screens/entourage/EntourageLabSection.kt", "EntourageLabSection", "metricValue()"),
            Triple("trichome/app/ui/screens/entourage/EntourageBoosterSection.kt", "EntourageBoosterSection", "metricHeadline()")
        )

        expectations.forEach { (file, where, role) ->
            val code = strippedCodeOf(file)
            assertTrue(
                "$where no longer reads $role, so the readout went back to prose",
                code.contains(role)
            )
        }

        // The supercycle screen is the one that uses both registers, so it is worth
        // separating: the lead total and the row values are different roles.
        val supercycle = strippedCodeOf("trichome/app/ui/screens/cycle/SuperCycleScreen.kt")
        assertTrue(
            "the cycle total is no longer the headline register",
            supercycle.contains("style = metricHeadline()")
        )
    }

    @Test
    fun theHeadlineRegisterReachesTheLeadNumberRatherThanAMeasuredRow() {
        // The two roles are separated by function, not by screen, so the split has
        // to be checked where it matters: the lead number is a headline, and the
        // numbers read against each other are values.
        val booster = strippedCodeOf("trichome/app/ui/screens/entourage/EntourageBoosterSection.kt")
        val headline = booster.substringAfter("if (frame.reportable)")

        assertTrue(
            "the booster's lead percentage no longer reads the headline role",
            headline.contains("style = metricHeadline()")
        )
        assertTrue(
            "the lead percentage is styled as a value, which demotes it to row scale",
            !headline.contains("style = metricValue()")
        )
        // The hardcoded weight is gone with it: a role is set like any other text,
        // so the reader's own weight choice has to be able to reach it.
        assertTrue(
            "the lead percentage still overrides the weight by hand, so the reader's " +
                "weight preference cannot reach it",
            !Regex("""fontWeight\s*=\s*FontWeight\.""").containsMatchIn(headline)
        )
    }

    @Test
    fun theMetricRolesAreNotAppliedToTextThatIsNotAMeasurement() {
        // The other direction. These were considered and deliberately left in prose,
        // each because the number is welded to a Spanish word in one interpolated
        // string — so a monospace face would cover the prose, and splitting the
        // string is a layout change rather than a type change.
        listOf(
            "trichome/app/ui/screens/plant/PlantDetailScreen.kt" to
                "the days-in-grow header (\"12 días\")",
            "trichome/app/ui/screens/tent/TentListScreen.kt" to
                "the tent card subtitle (\"3/6 plantas\")",
            "trichome/app/ui/components/LunarPhaseBar.kt" to
                "the lunar caption (\"62 % ilumina\")",
            "trichome/app/ui/screens/calendar/CalendarScreen.kt" to
                "the calendar day cell"
        ).forEach { (file, where) ->
            val code = strippedCodeOf(file)
            assertTrue(
                "$where in $file now reads a metric role, so a sentence with a " +
                    "Spanish unit word in it was set in monospace",
                !code.contains("metricValue()") && !code.contains("metricHeadline()")
            )
        }
    }

    @Test
    fun theThemePublishesBothRolesIntoTheTree() {
        val code = strippedCodeOf("trichome/app/ui/theme/TrichomeTheme.kt")

        assertTrue(
            "the value role is never provided, so a screen reading it gets the default",
            code.contains("LocalMetricValue provides typography.metricValue")
        )
        assertTrue(
            "the headline role is never provided, so a screen reading it gets the default",
            code.contains("LocalMetricHeadline provides typography.metricHeadline")
        )
        // Material still receives the fifteen slots, not the wrapper: a screen
        // reading `MaterialTheme.typography` has to see exactly what it saw before.
        assertTrue(
            "MaterialTheme is no longer given the Material typography",
            code.contains("typography = typography.material")
        )
        // And the locals have to be dynamic, or a size change repaints nothing until
        // something else invalidates the screen — the bug LocalTertiaryText's KDoc
        // records as reported from a device.
        assertTrue(
            "the metric locals are static, so a size change would not reach a " +
                "composed screen",
            !code.contains("staticCompositionLocalOf")
        )
    }

    @Test
    fun theMetricModelIsFreeOfComposeSoItsNumbersAreAssertable() {
        // The numbers had to live somewhere a plain JVM test can read, because
        // Compose has no unit-test runtime here. `model/` is that somewhere, and
        // `ModelPurityTest` now holds the file to it.
        val code = strippedCodeOf("trichome/app/model/MetricType.kt")
        assertTrue("MetricType.kt was not read", code.contains("object MetricType"))
        assertTrue(
            "MetricType.kt imports Compose, so its numbers are no longer pure Kotlin",
            Regex("""^\s*import\s+androidx""", RegexOption.MULTILINE).findAll(code).count() == 0
        )
        assertTrue(
            "MetricType.kt reads the clock, so a metric size would depend on when it " +
                "was asked for",
            !code.contains("LocalDate.now()") && !code.contains("System.currentTimeMillis()")
        )
    }

    @Test
    fun theHelpersAgreeWithTheArithmeticTheyAreGiven() {
        // The scale helpers, checked against the same multiplication written out.
        assertEquals(18.2f, MetricType.fontSizeSp(14, 1.30f), 0.001f)
        assertEquals(11.9f, MetricType.fontSizeSp(14, 0.85f), 0.001f)
        assertEquals(36.4f, MetricType.fontSizeSp(28, 1.30f), 0.001f)
        assertEquals(26f, MetricType.lineHeightSp(20, 1.30f), 0.001f)
        assertEquals(44.2f, MetricType.lineHeightSp(34, 1.30f), 0.001f)
    }

    @Test
    fun theTwoRegistersAreDistinctStylesRatherThanTheSameOneTwice() {
        val built = buildTypography(1.0f)

        assertNotEquals(
            "the headline role is the value role with a different name",
            built.metricValue, built.metricHeadline
        )
        assertEquals(
            "the two roles disagree about the typeface, so they are not one register",
            built.metricValue.fontFamily, built.metricHeadline.fontFamily
        )
        // Both still honour the reader's size scale, or a slider would move the
        // prose and leave every number on screen at its authored size.
        val scaled = buildTypography(1.30f)
        assertEquals(
            "the headline role stopped following the size preference",
            MetricType.HEADLINE_FONT_SIZE_SP * 1.30f, scaled.metricHeadline.fontSize.value, 0.001f
        )
        assertNotEquals(
            "a metric style stopped carrying a size",
            TextUnit.Unspecified, built.metricValue.fontSize
        )
    }
}
