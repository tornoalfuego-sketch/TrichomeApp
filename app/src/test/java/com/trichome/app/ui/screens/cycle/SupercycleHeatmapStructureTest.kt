package com.trichome.app.ui.screens.cycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The structural rules of the two surfaces F9 adds: the heatmap and the anchor picker.
 *
 * ## Why this reads source
 *
 * Compose has no unit-test runtime in this project — neither Robolectric nor
 * `compose-ui-test` is on the `test` classpath — and four separate bug classes have
 * already come from logic authored inside a composable: a `weight(0f)` that threw at
 * compose time and took every terpene page down, an unweighted `Row` that overflowed its
 * label, Spanish sentences authored in the composable, and a caller-side existence check
 * two ViewModels raced past.
 *
 * F9 adds three more risks to that list, none of them reachable from a JVM test of the
 * model underneath:
 *
 *  1. **A copy string that nothing can assert.** Every sentence the heatmap and the
 *     picker print comes from `model/`. A sentence written inside a composable is a
 *     sentence no test on this classpath holds to the language guard, and one of them
 *     has to say *that the map is not a daily pattern* — which is the load-bearing claim
 *     of the whole card.
 *  2. **A second scroll owner.** The heatmap is mounted inside `SuperCycleScreen`'s
 *     column. A `verticalScroll` in it is measured with an infinite maximum height and
 *     throws at runtime with `Vertically scrollable component was measured with an
 *     infinity maximum height constraints` — the crash `ScrollOwnershipTest` documents,
 *     which shipped once in the breeding theory tab.
 *  3. **A cell that cannot be drawn.** A `weight(0f)` in the twelve-column row is the
 *     same bug that already took every terpene page down, and it is invisible to the
 *     compiler, to lint and to every behavioural test in
 *     `SupercycleScheduleTest`.
 *
 * Comments are stripped before every scan, so this file's own prose about
 * `verticalScroll` or `Color.Unspecified` cannot register as a hit. That rule has broken
 * twice in this repo and it is applied here on purpose.
 *
 * ## What this does NOT cover
 *
 * That the twenty-four cells are legible at the size they render, that the palette
 * distinguishes `primaryContainer` from `surfaceVariant` on all four themes, and that the
 * `DatePicker` and `TimePicker` dialogs fit a small window. Those are device facts, and
 * the phase was verified on one.
 */
class SupercycleHeatmapStructureTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/components"),
        File("app/src/main/java/com/trichome/app/ui/components")
    ).filter { it.isDirectory }
        .flatMap { dir -> dir.listFiles { f -> f.name.endsWith(".kt") }?.toList() ?: emptyList() } +
        listOf(
            File("src/main/java/com/trichome/app/ui/screens/cycle"),
            File("app/src/main/java/com/trichome/app/ui/screens/cycle")
        ).filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.name.endsWith(".kt") }?.toList() ?: emptyList() }

    /** Block and line comments removed, so this file's own prose is not a hit. */
    private fun codeOf(name: String): String {
        val file = sources.firstOrNull { it.name == name }
            ?: error("$name is not in the scanned packages")
        return stripComments(file.readText(Charsets.UTF_8))
    }

    private fun allCode(): String = sources.joinToString("\n") {
        stripComments(it.readText(Charsets.UTF_8))
    }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    /**
     * The body of one composable, from its declaration to the next one.
     *
     * Scoped on purpose: an unrelated composable in the same file must not decide this.
     * Same pattern `ScrollOwnershipTest` and `EntourageF5StructureTest` needed.
     */
    private fun bodyOf(text: String, functionName: String): String {
        val start = text.indexOf("fun $functionName(")
        assertTrue("$functionName is not in the source", start > 0)
        val rest = text.substring(start)
        val end = Regex("""\n(@Composable|(private |internal )?fun )""").find(rest)
            ?.range?.first
            ?: rest.length
        return rest.substring(0, end)
    }

    private val heatmap = "SupercycleHeatmap.kt"
    private val picker = "SupercycleAnchorPicker.kt"

    /* ── No copy is authored in a composable ─────────────────────────────── */

    @Test
    fun theHeatmapHoldsNoStringLiteralOfItsOwn() {
        // The strong form of the rule, the one `EntourageF5StructureTest` established:
        // no non-blank literal at all. A Spanish sentence written here is a sentence no
        // JVM test on this classpath can hold to the language guard, and the sentence
        // that matters most — that a 26 h map is not a daily pattern — is exactly the
        // kind of sentence that has to live in the model with its arithmetic beside it.
        listOf("SupercycleHeatmap" to bodyOf(codeOf(heatmap), "SupercycleHeatmap"),
            "HourRow" to bodyOf(codeOf(heatmap), "HourRow"),
            "HeatmapLegend" to bodyOf(codeOf(heatmap), "HeatmapLegend")
        ).forEach { (where, body) ->
            val literals = Regex(""""([^"]*)"""").findAll(body)
                .map { it.groupValues[1] }
                .toList()
            assertTrue(
                "these literals in $where are copy no test can reach: $literals",
                literals.all { it.isBlank() }
            )
        }
    }

    @Test
    fun thePickerHoldsNoStringLiteralOfItsOwn() {
        listOf(
            "SupercycleAnchorPickerDialog" to
                bodyOf(codeOf(picker), "SupercycleAnchorPickerDialog"),
            "AnchorConsequence" to bodyOf(codeOf(picker), "AnchorConsequence"),
            "SupercycleAnchorSummary" to bodyOf(codeOf(picker), "SupercycleAnchorSummary")
        ).forEach { (where, body) ->
            val literals = Regex(""""([^"]*)"""").findAll(body)
                .map { it.groupValues[1] }
                .toList()
            assertTrue(
                "these literals in $where are copy no test can reach: $literals",
                literals.all { it.isBlank() }
            )
        }
    }

    @Test
    fun everyLabelTheHeatmapPrintsComesFromTheModel() {
        val body = bodyOf(codeOf(heatmap), "SupercycleHeatmap")

        listOf(
            "schedule.titleEs", "schedule.photoperiodLabelEs", "schedule.phaseLabelEs",
            "schedule.phaseDetailEs", "schedule.driftValueEs", "schedule.driftLabelEs",
            "schedule.driftExplanationEs", "schedule.nowMarkerLabelEs"
        ).forEach { field ->
            assertTrue(
                "$field must reach the screen, or the card is showing the composable's " +
                    "own idea of what the cycle is",
                body.contains(field)
            )
        }
    }

    @Test
    fun theDriftSentenceIsRenderedAndNotBehindADisclosure() {
        // The claim the card exists to make honestly. Behind an "AnimatedVisibility" or
        // a "ver más" it would be a footnote on a lie.
        val body = bodyOf(codeOf(heatmap), "SupercycleHeatmap")

        assertTrue(body.contains("schedule.driftExplanationEs"))
        listOf("AnimatedVisibility", "expandVertically").forEach { affordance ->
            assertFalse(
                "the disclaimer must not sit behind a disclosure ($affordance)",
                body.contains(affordance)
            )
        }
    }

    @Test
    fun theLegendIteratesTheModelsEntriesRatherThanNamingTheSwatches() {
        // Four hand-written swatches would be four hand-written states, and the fifth
        // one the model ever adds would have nowhere to go.
        val body = bodyOf(codeOf(heatmap), "HeatmapLegend")

        assertTrue(body.contains("schedule.legend.forEach"))
        assertTrue(body.contains("entry.labelEs"))
    }

    @Test
    fun theConsequenceBlockPrintsEveryLineThePreviewCarries() {
        val body = bodyOf(codeOf(picker), "AnchorConsequence")

        listOf(
            "preview.anchorLabelEs", "preview.superdayLabelEs",
            "preview.superdayStartLabelEs", "preview.offsetLabelEs",
            "preview.phaseNowLabelEs", "preview.warningEs"
        ).forEach { field ->
            assertTrue(
                "$field must reach the grower before they commit to an instant that " +
                    "renumbers every superday",
                body.contains(field)
            )
        }
    }

    /* ── One scroll owner per axis ───────────────────────────────────────── */

    @Test
    fun neitherNewSurfaceIntroducesAScroll() {
        // The heatmap is inside SuperCycleScreen's own verticalScroll, and the picker
        // is a dialog whose body is a fixed-height column. Either one scrolling again is
        // measured with an infinite maximum height and throws.
        listOf(
            "the heatmap" to bodyOf(codeOf(heatmap), "SupercycleHeatmap"),
            "the hour row" to bodyOf(codeOf(heatmap), "HourRow"),
            "the legend" to bodyOf(codeOf(heatmap), "HeatmapLegend"),
            "the picker" to bodyOf(codeOf(picker), "SupercycleAnchorPickerDialog"),
            "the consequence block" to bodyOf(codeOf(picker), "AnchorConsequence")
        ).forEach { (where, text) ->
            assertFalse(
                "$where must not declare a scroll: verticalScroll, LazyColumn or LazyRow",
                text.contains("verticalScroll") || text.contains("LazyColumn") ||
                    text.contains("LazyRow")
            )
        }
    }

    @Test
    fun thePickerImportsNoScrollStateItDoesNotUse() {
        // An unused import is not a crash, but it is the fingerprint of the scroll being
        // taken back out — which is what a partial merge leaves behind.
        val text = codeOf(picker)

        assertFalse(
            "the picker must not import verticalScroll",
            text.contains("import androidx.compose.foundation.verticalScroll") ||
                text.contains("import androidx.compose.foundation.rememberScrollState")
        )
    }

    /* ── Colours come from the scheme ────────────────────────────────────── */

    @Test
    fun neitherNewFileHardcodesAColour() {
        listOf(heatmap, picker).forEach { name ->
            val offenders = Regex("""Color\(0x""").findAll(codeOf(name))
                .map { it.value }
                .toList()
            assertTrue("$name hardcodes a colour instead of reading the scheme: $offenders", offenders.isEmpty())
        }
    }

    @Test
    fun theHeatmapResolvesEveryFillFromAColourSchemeRole() {
        // The encoding is fill density from the scheme, never a hue: a literal would be a
        // colour no theme's contrast checks ever see, and the app ships four palettes
        // whose supporting hues differ.
        val text = codeOf(heatmap)

        listOf("secondaryContainer", "surfaceVariant", "outline", "onSurface").forEach { role ->
            assertTrue(
                "$role must reach the heatmap, or a fill is resolving from somewhere " +
                    "that is not the theme",
                text.contains(role)
            )
        }
    }

    @Test
    fun theHeatmapDoesNotUseAnAccentDerivedContainer() {
        // `primaryContainer` is `containerFor(accent, surface)`: it is the accent wearing a
        // different name. A 24-cell grid painted with it is the loudest instance of the
        // "content must not repaint when the accent changes" defect in the app, and it is
        // invisible to the compiler and to every behavioural test in
        // `SupercycleScheduleTest`.
        val text = codeOf(heatmap)

        assertFalse(
            "the lit fill has to come from the theme's own supporting role",
            Regex("""primaryContainer""").containsMatchIn(text)
        )
        assertTrue(
            "and it has to be that role, not a comment: " + "secondaryContainer",
            text.contains("scheme.secondaryContainer")
        )
    }

    @Test
    fun theHeatmapDoesNotUseTheAccent() {
        // `AccentRoleSeparationTest` holds the rule that content takes its colours from
        // the text and container roles so a list does not repaint when the accent changes.
        // A 24-cell grid that repainted on every accent change would be the loudest
        // instance of that defect in the app, and `primary` is not the accent's meaning:
        // the accent means "the thing you chose", not "on".
        val text = codeOf(heatmap)

        assertFalse(
            "the heatmap must not read the accent",
            Regex("""accentLabelOn\(""").containsMatchIn(text) ||
                Regex("""accentButtonColors\(""").containsMatchIn(text)
        )
        assertFalse(
            "nor take a bare `primary` as a cell fill",
            Regex("""=\s*(MaterialTheme\.colorScheme\.)?primary\b""").containsMatchIn(text)
        )
    }

    @Test
    fun noSurfaceIsPublishedAnUnspecifiedColour() {
        // `Color.Unspecified` is Material's "decide for me" sentinel and `Surface` does
        // not decide: it hands whatever it is given straight into `LocalContentColor`, so
        // a panel that omitted it painted its whole subtree from an undefined colour.
        val offenders = listOf(heatmap, picker).filter { name ->
            Regex("""Surface\((?:[^)]*\bcolor\s*=\s*Color\.Unspecified)""")
                .containsMatchIn(codeOf(name))
        }

        assertTrue("a Surface must never be given Color.Unspecified: $offenders", offenders.isEmpty())
    }

    @Test
    fun neitherNewFileReintroducesGlassmorphism() {
        // Removed three versions ago; four tests forbid its return.
        listOf(heatmap, picker).forEach { name ->
            listOf("blur(", "BlurredEdgeTreatment", "graphicsLayer", "GlassPanel").forEach { banned ->
                assertFalse("$name must stay solid; found $banned", codeOf(name).contains(banned))
            }
        }
    }

    /* ── The cells have to be drawable ───────────────────────────────────── */

    @Test
    fun everyWeightedCellHasAPositiveWeight() {
        // The shipped crash: `weight(0f)` throws at compose time and takes every screen
        // that mounts the composable down with it. The compiler accepts it, lint accepts
        // it, and every behavioural test in `SupercycleScheduleTest` passes.
        val offenders = Regex("""weight\(\s*([0-9.]+)f\s*\)""").findAll(allCode())
            .map { it.groupValues[1].toFloat() }
            .filter { it <= 0f }
            .toList()

        assertTrue(
            "a non-positive weight throws at compose time, which is how every terpene " +
                "page went down once: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun theGridRowAndItsTickRowShareOneColumnCount() {
        // A second row of four tick labels positions itself against its own arrangement,
        // and a "00" ends up printed under 01. Both rows are `forEach` loops over a
        // twelve-element list with `weight(1f)` inside, so the source has exactly two
        // weighted children and they are siblings — one per loop, not one per column.
        val body = bodyOf(codeOf(heatmap), "HourRow")

        assertEqualsOne(
            "the hour row must carry one weighted child for the cells and one for the ticks",
            Regex("""\.weight\(1f\)""").findAll(body).count(),
            2
        )
        assertTrue("the cells have to come from the model's row", body.contains("row.cells.forEach"))
        assertTrue("and the ticks from the same row's list", body.contains("row.ticks.forEach"))
        // Both lists are twelve long because the model built them that way, and the
        // composable cannot tell. `SupercycleScheduleTest` pins the count.
        assertTrue(
            "the composable must not slice or pad the model's rows; that is the model's " +
                "decision",
            !body.contains("take(") && !body.contains("drop(") && !body.contains("chunked(")
        )
    }

    @Test
    fun theNowMarkerReservesItsHeightWhetherOrNotACellCarriesIt() {
        // A cell that skips the 3 dp rule entirely would make one row shorter than the
        // other and shift every tick under it.
        val body = bodyOf(codeOf(heatmap), "HourRow")

        assertTrue("the branch on the marker has to be in the source", body.contains("if (cell.isNow)"))
        assertTrue(body.contains("Spacer("))
    }

    @Test
    fun everyCellIsReadOutLoud() {
        // The grid is a picture with no reading unless each cell carries its state. A
        // screen-reader user must hear "14:00–15:00, oscuridad", not a list of hour
        // numbers.
        val body = bodyOf(codeOf(heatmap), "HourRow")

        assertTrue(body.contains("clearAndSetSemantics"))
        assertTrue(body.contains("cell.descriptionEs"))
    }

    /* ── The anchor picker's discipline ──────────────────────────────────── */

    @Test
    fun thePickerHasNoNewDependencyAndUsesTheMaterial3Pickers() {
        // No new dependency is a hard constraint of the phase, so the pickers have to be
        // the ones already on the classpath.
        val text = codeOf(picker)

        assertTrue(text.contains("androidx.compose.material3.DatePicker"))
        assertTrue(text.contains("androidx.compose.material3.TimePicker"))
        assertFalse(
            "the picker must not reach for the platform dialogs through an Intent",
            text.contains("Intent(")
        )
    }

    @Test
    fun confirmingIsTheOnlyWayTheDraftLeavesTheDialog() {
        // Cancel, dismiss-by-tap and rotation must all reach `onDismiss` and nothing
        // else. The screen's Save button stays the only writer of the row.
        val body = bodyOf(codeOf(picker), "SupercycleAnchorPickerDialog")

        assertTrue("the commit path has to hand the instant up", body.contains("onConfirm(pendingMillis)"))
        listOf("onDismissRequest = onDismiss", "onDismissRequest = {").forEach { exit ->
            assertTrue("the dialog has to exit through onDismiss via $exit", body.contains(exit))
        }
        assertFalse(
            "the dialog must not write anything itself",
            body.contains("vm.save") || body.contains("insertSuperCycle")
        )
    }

    @Test
    fun theConfirmButtonIsGatedOnThePreviewRatherThanOnItsOwnCondition() {
        // The copy says "this anchor is a problem" and the button has to say the same
        // thing, or the grower is told one thing and allowed to do another.
        val body = bodyOf(codeOf(picker), "SupercycleAnchorPickerDialog")

        assertTrue(body.contains("enabled = pendingPreview.canConfirm"))
        assertFalse(
            "the dialog must not decide for itself whether the anchor is valid",
            Regex("""enabled\s*=\s*(draftMillis|pendingMillis)""").containsMatchIn(body)
        )
    }

    @Test
    fun thePreviewIsRecomputedFromTheTimeWheelRatherThanOnlyOnCommit() {
        // A preview computed once on open describes the instant the dialog opened with,
        // which is exactly the value the grower is dragging away from.
        val body = bodyOf(codeOf(picker), "SupercycleAnchorPickerDialog")

        assertTrue(
            "the pending instant has to be built from the live time state",
            body.contains("timeState.hour * 60 + timeState.minute")
        )
        assertTrue(body.contains("pendingPreview"))
    }

    @Test
    fun thePickerDateConversionGoesThroughTheModel() {
        // `DatePicker` reports UTC midnight. Reading it as a local date is the bug that
        // lands a UTC-3 grower's anchor on the previous evening, and it renumbers every
        // superday from there.
        val body = bodyOf(codeOf(picker), "SupercycleAnchorPickerDialog")

        assertTrue(
            body.contains("SupercycleAnchorPicker.toPickerDateMillis")
        )
        assertTrue(
            body.contains("SupercycleAnchorPicker.fromPickerDateMillis")
        )
        assertFalse(
            "the dialog must not build a LocalDateTime from the picker value itself",
            Regex("""LocalDateTime\.ofEpochMilli\(""").containsMatchIn(body)
        )
    }

    /* ── The screen wires both surfaces ──────────────────────────────────── */

    @Test
    fun theScreenMountsBothSurfaces() {
        val text = codeOf("SuperCycleScreen.kt")

        assertTrue(
            "the heatmap has to be on the supercycle screen, or it is unreachable",
            text.contains("SupercycleHeatmap(")
        )
        assertTrue(
            "and the picker has to be openable from it",
            text.contains("SupercycleAnchorPickerDialog(")
        )
        assertTrue(text.contains("SupercycleAnchorSummary("))
    }

    @Test
    fun theScreenHasOneScrollOwnerAndTheNewSurfacesDoNotAddAnother() {
        val text = codeOf("SuperCycleScreen.kt")

        assertTrue(
            "the screen still owns its own vertical scroll",
            text.contains("verticalScroll")
        )
        assertEqualsOne(
            "the screen must not declare a second vertical scroll",
            Regex("""\.verticalScroll\(""").findAll(text).count(),
            1
        )
    }

    @Test
    fun theScreenShowsTheHeatmapOnlyAfterTheLoadResolved() {
        // Before the load, the anchor is unknown, and a heatmap built from a default
        // instant would draw a day of a cycle nobody is running.
        val text = codeOf("SuperCycleScreen.kt")

        assertTrue(
            "the heatmap has to be gated on the resolved load",
            Regex("""if \(form\.loaded\) \{\s*SupercycleHeatmap\(""").containsMatchIn(text)
        )
    }

    @Test
    fun theScreenDrivesTheHeatmapAndTheSaveFromTheSameAnchor() {
        // A preview computed from the saved value while Save writes the chosen one is a
        // preview of nothing. Both have to read `effectiveCycleStartAt`.
        val text = codeOf("SuperCycleScreen.kt")

        assertTrue(
            "the heatmap reads the effective anchor",
            text.contains("form.effectiveCycleStartAt")
        )
        assertFalse(
            "and must not read the saved anchor directly, or a pending choice would " +
                "draw a cycle the grower is about to replace",
            Regex("""cycleStartAt\s*=\s*form\.savedCycleStartAt""").containsMatchIn(text)
        )
    }

    private fun assertEqualsOne(message: String, actual: Int, expected: Int) {
        assertEquals("$message: found $actual, expected $expected", expected, actual)
    }
}