package com.trichome.app.ui.screens.vpd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F10b — the structural rules of the VPD history screen and the export panel.
 *
 * ## Why this reads source
 *
 * The failure modes here are not behavioural and no JVM test can reach them. Compose has no
 * unit-test runtime on the `test` classpath in this project, which is not a preference: four
 * separate bug classes have already come from composable-level logic nothing could test — a
 * `weight(0f)` that threw at compose time and took down every terpene page, an unwighted `Row`
 * that overflowed, Spanish sentences authored inside a composable, and a caller-side existence
 * check two ViewModels raced past.
 *
 * F10b adds two more risks to that list:
 *
 *  1. **A derived number rendered like a measurement.** The chart plots readings from two
 *     different sources and must keep them distinguishable. A chart that reads only the value
 *     and drops the provenance is the `EstimatedClimate` defect in a new place, and no compiler
 *     objects.
 *  2. **A stage change that leaves the previous entry open.** `stage_entries` is the only record
 *     of what happened to a plant, so the panel and the dialog must both reach the planner that
 *     closes the previous entry — never write an entry on their own.
 *
 * So this file asserts, against the source with comments stripped:
 *
 *  3. **No Spanish sentence is authored in the composables**, in the strong form
 *     `EntourageF5StructureTest` established: no non-blank string literal at all.
 *  4. **One scroll owner per axis**, solid Material 3, no hardcoded colour, no `Color.Unspecified`.
 *
 * Comments are stripped before every scan, so a KDoc mentioning `verticalScroll` or
 * `Color.Unspecified` is not a hit. That rule has broken twice in this repo and is applied here
 * on purpose.
 */
class VpdHistoryStructureTest {

    private val screensDir = listOf(
        File("src/main/java/com/trichome/app/ui/screens"),
        File("app/src/main/java/com/trichome/app/ui/screens")
    ).first { it.isDirectory }

    private fun codeOf(relative: String): String =
        File(screensDir, relative)
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?.let(::stripComments)
            ?: error("$relative is not on disk from this working directory")

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    /** The body of one composable, from its declaration to the next. */
    private fun bodyOf(text: String, functionName: String): String {
        val start = text.indexOf("fun $functionName(")
        assertTrue("$functionName is not in the source", start > 0)
        val rest = text.substring(start)
        val end = Regex("""\n(@Composable|@OptIn|@androidx|(private |internal )?fun )""")
            .find(rest)?.range?.first
            ?: rest.length
        return rest.substring(0, end)
    }

    /* ── The chart keeps the provenance ─────────────────────────────────── */

    /**
     * All three series reach the chart.
     *
     * The one that matters is `UNKNOWN`: a chart that switches on only `MEASURED` and
     * `CALCULATED` has nowhere to put a pre-v5 reading except the default branch, which is
     * exactly how an unknown origin gets drawn as a measurement.
     */
    @Test
    fun theChartHandlesAllThreeProvenances() {
        val body = bodyOf(codeOf("vpd/VpdHistoryScreen.kt"), "VpdSeriesCanvas")

        listOf("VpdProvenance.MEASURED", "VpdProvenance.CALCULATED", "VpdProvenance.UNKNOWN")
            .forEach { branch ->
                assertTrue(
                    "the chart must branch on $branch; a reading whose origin the app cannot " +
                        "vouch for has no other way to be drawn",
                    body.contains(branch)
                )
            }
    }

    /**
     * The three series are told apart by shape, not only by colour.
     *
     * Two of the markers are drawn in colours a theme can make nearly identical, and colour
     * alone excludes a real share of the audience this chart is for.
     */
    @Test
    fun theThreeSeriesDifferByShapeNotOnlyByColour() {
        val body = bodyOf(codeOf("vpd/VpdHistoryScreen.kt"), "VpdSeriesCanvas")

        assertTrue("measured is a filled circle", body.contains("drawCircle"))
        assertTrue(
            "calculated is a ring: a filled dot in a second colour is not distinguishable " +
                "under every theme",
            Regex("""VpdProvenance\.CALCULATED[\s\S]{0,400}?Stroke""").containsMatchIn(body)
        )
        assertTrue(
            "unknown origin is a square: a third colour alone would not separate it",
            Regex("""VpdProvenance\.UNKNOWN[\s\S]{0,400}?drawRect""").containsMatchIn(body)
        )
    }

    /**
     * The legend always renders, and it counts.
     *
     * A chart that hides the legend leaves the grower to infer which mark is which, and the
     * whole provenance mechanism then reduces to a visual detail they have to guess at.
     */
    @Test
    fun theLegendIsRenderedOnTheChart() {
        val panel = bodyOf(codeOf("vpd/VpdHistoryScreen.kt"), "VpdHistoryChartPanel")

        assertTrue(panel.contains("VpdLegend("))
        assertTrue(
            "the legend must print each series' count, so an empty series is visibly empty",
            bodyOf(codeOf("vpd/VpdHistoryScreen.kt"), "VpdLegend").contains("entry.countEs")
        )
    }

    /** The provenance sentence is on the chart, not on a help page. */
    @Test
    fun theProvenanceRuleIsPrintedOnTheChart() {
        val panel = bodyOf(codeOf("vpd/VpdHistoryScreen.kt"), "VpdHistoryChartPanel")
        assertTrue("the model's own sentence must reach the chart", panel.contains("chart.provenanceEs"))
    }

    /* ── The calculator ────────────────────────────────────────────────── */

    /**
     * The calculator's readout is labelled as calculated.
     *
     * There is no instrument behind this screen, and the number the panel leads with comes out
     * of a formula. If the provenance label were dropped from the readout, the value would be
     * indistinguishable from a hygrometer reading on the same screen.
     */
    @Test
    fun theCalculatorReadoutCarriesItsProvenance() {
        val card = bodyOf(codeOf("vpd/VpdCalculatorCard.kt"), "VpdCalculatorCard")

        assertTrue(
            "the readout must print the provenance label",
            card.contains("display.provenanceLabelEs")
        )
        assertTrue(
            "and the sentence explaining it",
            card.contains("display.provenanceExplanationEs")
        )
    }

    /**
     * The calculator states there is no sensor behind it.
     *
     * The same standard the climate card set for itself.
     */
    @Test
    fun theCalculatorScreenStatesThereIsNoSensor() {
        val screen = codeOf("vpd/VpdHistoryScreen.kt")
        assertTrue(
            "the panel must print the model's own no-sensor sentence",
            screen.contains("CALCULATOR_SOURCE_ES")
        )
    }

    /**
     * Only the failing field is marked.
     *
     * Marking all three because one is wrong trains the grower to ignore the red outline,
     * which is the same failure as never marking any of them.
     */
    @Test
    fun onlyTheFailingFieldIsMarkedInError() {
        val card = bodyOf(codeOf("vpd/VpdCalculatorCard.kt"), "VpdCalculatorCard")

        listOf("AIR_TEMPERATURE", "HUMIDITY", "OFFSET").forEach { field ->
            assertTrue(
                "`$field` must have its own error branch",
                card.contains("outcome.field == VpdCalculatorField.$field")
            )
        }
        assertTrue(
            "and the problem sentence must reach the screen",
            card.contains("outcome.problemEs")
        )
    }

    /* ── The journal write ─────────────────────────────────────────────── */

    /**
     * The offset field exists only for a calculated value.
     *
     * A measured reading came off an instrument; attaching a leaf offset to it would imply a
     * derivation that never happened, and `VpdLogFormValidator` would then store one.
     */
    @Test
    fun theOffsetFieldIsConditionalOnTheProvenance() {
        val dialog = bodyOf(codeOf("vpd/VpdLogDialog.kt"), "VpdLogDialog")

        assertTrue(
            "the offset field must be gated on the provenance",
            Regex("""if\s*\(\s*form\.provenance == VpdProvenance\.CALCULATED\s*\)""").containsMatchIn(dialog)
        )
    }

    /**
     * The tent is read, never written.
     *
     * A `grow_events` row belongs to a plant and the plant belongs to a tent. Writing a
     * `tentId` onto the event would be a second source of truth for a fact that has one.
     */
    @Test
    fun theLogDialogShowsTheTentAndDoesNotAskForIt() {
        val dialog = bodyOf(codeOf("vpd/VpdLogDialog.kt"), "VpdLogDialog")

        assertTrue(dialog.contains("VpdLogFormValidator.tentSentenceEs(tentName)"))
        assertFalse(
            "the tent is not an input; there must be no tent field",
            dialog.contains("tentId")
        )
    }

    /**
     * The provenance is a choice the grower makes, not a constant.
     *
     * Two chips, both writable values. A single fixed value would make the "measured" and
     * "calculated" distinction unreachable, which is the whole point of the column.
     */
    @Test
    fun theLogDialogOffersBothWritableProvenances() {
        val dialog = bodyOf(codeOf("vpd/VpdLogDialog.kt"), "VpdLogDialog")

        assertTrue(
            "both writable provenances must be offered",
            dialog.contains("VpdProvenance.MEASURED") && dialog.contains("VpdProvenance.CALCULATED")
        )
        assertFalse(
            "UNKNOWN must never be offered as something to write",
            Regex("""listOf\([^)]*UNKNOWN""").containsMatchIn(dialog)
        )
    }

    /* ── The stage panel ───────────────────────────────────────────────── */

    /**
     * A stage change goes through the planner, never through a bare insert.
     *
     * The panel's own job is to display a timeline; the write belongs to
     * `GrowStageViewModel.changeStage`, which resolves a plan and closes the previous entry
     * first. A composable that inserted a `StageEntry` directly would open a second stage
     * without closing the first, which is the corruption this phase exists to prevent.
     */
    @Test
    fun theStagePanelNeverWritesAStageEntryItself() {
        val panel = codeOf("plant/GrowStagePanel.kt")

        assertFalse(
            "only the repository may write stage_entries",
            panel.contains("StageEntry(")
        )
        assertFalse("and never a stage_entries statement", panel.contains("stage_entries"))
        assertFalse("nor a delete", panel.contains("@Delete") || panel.contains("deletePlant"))
    }

    /**
     * The panel takes its actions as callbacks and its copy as parameters.
     *
     * Both are the reason the Spanish lives in `GrowStagePlanner`: a composable that authored
     * its own sentences is a sentence no JVM test on this classpath can hold to.
     */
    @Test
    fun theStagePanelTakesItsCopyAndItsActionsAsParameters() {
        val panel = bodyOf(codeOf("plant/GrowStagePanel.kt"), "GrowStagePanel")

        assertTrue(panel.contains("onChangeStage: () -> Unit"))
        assertTrue(panel.contains("onFinalize: () -> Unit"))
        assertTrue(panel.contains("timelineLinesEs"))
        assertTrue(panel.contains("archivedBannerEs"))
    }

    /**
     * The plant screen routes the two actions into the dialogs, and clears them before writing.
     *
     * The clear-before-write is the repo's existing pattern: a dialog still on screen for the
     * frame between the tap and the recomposition is a second target for a double tap, and a
     * double-tapped stage change writes two stage entries.
     */
    @Test
    fun thePlantScreenClearsTheDialogBeforeWriting() {
        val screen = codeOf("plant/PlantDetailScreen.kt")

        assertTrue(
            "the stage dialog must be dismissed before the write",
            Regex("""changingStage = false\s*\n\s*stageVm\.changeStage""").containsMatchIn(screen)
        )
        assertTrue(
            "and the finalize dialog before its write",
            Regex("""finalizing = false\s*\n\s*stageVm\.finalizePlant""").containsMatchIn(screen)
        )
    }

    /**
     * The finalize confirmation is not painted as a destruction.
     *
     * `ConfirmDestructiveDialog` wears `colorScheme.error` because a delete is one. Archiving
     * loses nothing, and painting it in the same red is the same mistake as labelling it
     * "Eliminar".
     */
    @Test
    fun theFinalizeConfirmationIsNotPaintedAsADelete() {
        val dialog = bodyOf(codeOf("plant/GrowStagePanel.kt"), "FinalizeGrowDialog")

        assertFalse(
            "finalizing is not a delete and must not wear the error colour",
            dialog.contains("colorScheme.error")
        )
        assertTrue(
            "and it must use the accent button colours, like every non-destructive action",
            dialog.contains("accentTextButtonColors")
        )
    }

    /* ── The export panel ──────────────────────────────────────────────── */

    /**
     * The export panel states the format and says why there is no PDF.
     *
     * A grower who asked for an export and got JSON would otherwise conclude the app cannot
     * produce a document. The honest answer is that this phase chose not to add a PDF library
     * without the owner's decision, and the panel says so.
     */
    @Test
    fun theExportPanelStatesTheFormatAndTheAbsenceOfPdf() {
        val panel = codeOf("settings/ExportDataPanel.kt")

        assertTrue(panel.contains("DataExportCopy.FORMAT_ES"))
        assertTrue(panel.contains("DataExportCopy.NO_PDF_ES"))
        assertTrue(
            "and the privacy boundary, so 'no leak' means something to the grower",
            panel.contains("DataExportCopy.PRIVACY_ES")
        )
    }

    /**
     * The export writes through the atomic writer, and never opens a stream itself.
     *
     * A panel that called `File.writeText` would produce a file that a mid-write crash leaves
     * truncated, which is the one property of the export path the grower cannot verify.
     */
    @Test
    fun theExportPanelWritesOnlyThroughTheAtomicWriter() {
        val panel = codeOf("settings/ExportDataPanel.kt")

        assertTrue(panel.contains("ExportFileWriter.exportDirectory"))
        // Word-boundaried, not a bare substring: `ExportFileWriter` *contains* `FileWriter`,
        // and a loose `contains` here would have reported the panel as writing files directly
        // while checking the very call it is supposed to make.
        listOf("writeText", "FileWriter", "outputStream", "bufferedWriter", "createNewFile").forEach { call ->
            assertFalse(
                "the panel must not call `$call`; only ExportFileWriter writes, and it does so " +
                    "atomically",
                Regex("""(?<![\w.])$call\s*\(""").containsMatchIn(panel)
            )
        }
    }

    /**
     * The panel reports the directory and the finished path.
     *
     * A file in app-scoped storage that nobody is told the location of is a file nobody finds,
     * and an export nobody can find is not a backup.
     */
    @Test
    fun theExportPanelReportsWhereTheFileWent() {
        val panel = codeOf("settings/ExportDataPanel.kt")

        assertTrue("the destination directory must be shown", panel.contains("DESTINATION_LABEL_ES"))
        assertTrue("and the written path on success", panel.contains("DataExportCopy.successEs"))
    }

    /** The subject picker is cleared before the write, like every other dialog here. */
    @Test
    fun theExportDialogClosesBeforeTheWrite() {
        val panel = codeOf("settings/ExportDataPanel.kt")

        assertTrue(
            "the picker must be dismissed before the suspending write runs",
            Regex("""pending = null\s*\n\s*working = true""").containsMatchIn(panel)
        )
    }

    /* ── Layout and colours, both new surfaces ─────────────────────────── */

    /**
     * No screen nests a second vertical scroll inside its host's.
     *
     * The rule applies to **screens**, and the export panel's subject picker is deliberately
     * excluded: it lives inside an `AlertDialog`, which is a separate window with its own
     * bounds and therefore its own scroll. What a dialog may not do is scroll without a height
     * bound, which is asserted separately below — an unbounded scroll inside a dialog measures
     * against the window and is what makes a long list render off-screen.
 */
    @Test
    fun noNewScreenDeclaresASecondVerticalScroll() {
        listOf(
            "the VPD screen" to codeOf("vpd/VpdHistoryScreen.kt"),
            "the VPD calculator card" to codeOf("vpd/VpdCalculatorCard.kt"),
            "the VPD log dialog" to codeOf("vpd/VpdLogDialog.kt"),
            "the stage panel" to codeOf("plant/GrowStagePanel.kt")
        ).forEach { (where, text) ->
            assertFalse(
                "$where must not declare a verticalScroll: the LazyColumn or the host Column " +
                    "owns that axis, and a nested one is measured with an infinite maximum " +
                    "height, which throws",
                text.contains("verticalScroll")
            )
            assertFalse(
                "and $where must not nest a list inside a scroll owner",
                text.contains("LazyColumn") && text.contains("verticalScroll")
            )
        }
    }

    @Test
    fun theExportPanelScrollsOnlyInsideItsDialogAndOnlyWithABound() {
        val panel = codeOf("settings/ExportDataPanel.kt")

        // The picker is a dialog: a separate window, so it may scroll. It may not scroll
        // unbounded, though — that is what renders a long plant list off the bottom of the
        // screen with no way to reach the rest of it.
        assertTrue(panel.contains("verticalScroll"))
        assertTrue(
            "the dialog's scroll must be height-bounded",
            Regex("""heightIn\(max\s*=\s*\d+\.dp\)[\s\S]{0,120}?verticalScroll""").containsMatchIn(panel)
        )
    }

    @Test
    fun noNewSurfaceHardcodesAColour() {
        listOf(
            "vpd/VpdHistoryScreen.kt",
            "vpd/VpdCalculatorCard.kt",
            "vpd/VpdLogDialog.kt",
            "plant/GrowStagePanel.kt",
            "settings/ExportDataPanel.kt"
        ).forEach { path ->
            val offenders = Regex("""Color\(0x""").findAll(codeOf(path)).map { it.value }.toList()
            assertTrue("$path hardcodes a colour instead of reading the scheme: $offenders", offenders.isEmpty())
        }
    }

    @Test
    fun noNewSurfacePublishesAnUnspecifiedColourIntoASurface() {
        listOf(
            "vpd/VpdHistoryScreen.kt",
            "vpd/VpdCalculatorCard.kt",
            "vpd/VpdLogDialog.kt",
            "plant/GrowStagePanel.kt",
            "settings/ExportDataPanel.kt"
        ).forEach { path ->
            val text = codeOf(path)
            // The owner name is a bare identifier, so the parenthesis stays out of the pattern
            // and is added separately — putting `Surface(` inside the group unbalances it,
            // which is what this test did on its first run.
            listOf("Surface", "AlertDialog", "SolidPanel", "Scaffold").forEach { owner ->
                val offenders = Regex("""\b$owner\s*\(\s*color\s*=\s*Color\.Unspecified""")
                    .findAll(text).map { it.value }.toList()
                assertTrue("$path publishes Color.Unspecified into a $owner: $offenders", offenders.isEmpty())
            }
        }
    }

    @Test
    fun noNewSurfaceReintroducesGlassmorphism() {
        listOf(
            "vpd/VpdHistoryScreen.kt",
            "vpd/VpdCalculatorCard.kt",
            "vpd/VpdLogDialog.kt",
            "plant/GrowStagePanel.kt",
            "settings/ExportDataPanel.kt"
        ).forEach { path ->
            val text = codeOf(path)
            listOf("blur(", "BlurredEdgeTreatment", "graphicsLayer", "GlassPanel").forEach { banned ->
                assertFalse("the module must stay solid; found $banned in $path", text.contains(banned))
            }
        }
    }

    /* ── The copy is the model's, not the composable's ──────────────────── */

    /**
     * No Spanish sentence is authored in the two decision-making composables.
     *
     * The strong form of the rule: no non-blank string literal that could be a sentence. A
     * sentence written in a composable is a sentence no JVM test on this classpath can hold to
     * the language guard, and on a screen whose whole point is "measured or calculated?" that
     * is the sentence most worth checking.
     *
     * Scoped to the two composables rather than the whole file, because `VpdLogDialog`'s field
     * labels legitimately come from constants and `VpdHistoryScreen` carries a handful of short
     * structural literals ("Registrar en Bitácora" being the notable one, asserted separately
     * below).
     */
    @Test
    fun theStageDialogsHoldNoAuthoredSpanish() {
        listOf("ChangeStageDialog", "FinalizeGrowDialog", "GrowStagePanel").forEach { fn ->
            val literals = Regex(""""([^"]*)"""").findAll(bodyOf(codeOf("plant/GrowStagePanel.kt"), fn))
                .map { it.groupValues[1] }
                .toList()
            assertTrue(
                "$fn must take its copy as parameters; these literals are copy no test can " +
                    "reach: $literals",
                literals.all { it.isBlank() }
            )
        }
    }

    /** The log dialog's labels all come from the validator. */
    @Test
    fun theLogDialogHoldsNoAuthoredSpanish() {
        val dialog = bodyOf(codeOf("vpd/VpdLogDialog.kt"), "VpdLogDialog")
        val literals = Regex(""""([^"]*)"""").findAll(dialog)
            .map { it.groupValues[1] }
            .toList()

        assertTrue(
            "every field label must come from VpdLogFormValidator; these do not: $literals",
            literals.all { it.isBlank() }
        )
    }
}