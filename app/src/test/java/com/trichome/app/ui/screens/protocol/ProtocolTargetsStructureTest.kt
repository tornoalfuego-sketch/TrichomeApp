package com.trichome.app.ui.screens.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The structural rules of the two write surfaces for a protocol's declared targets.
 *
 * ## Why this reads source
 *
 * Compose has no unit-test runtime in this project — neither Robolectric nor
 * `compose-ui-test` is on the `test` classpath — and four bug classes have already
 * shipped from composable-level logic nothing could reach: a `weight(0f)` that threw at
 * compose time and took every terpene page down, an unweighted `Row` that clipped a
 * label to "SuperCicl", a "CUSTOM" chip that rendered one letter per line, and 184
 * diagnosis glyphs made invisible by `tint = Color.Unspecified`. None of those compiles
 * wrong, lints wrong, or fails a test of the model underneath. So the shape of the
 * surface is asserted here, against the source with the comments stripped first — a
 * rule that has broken twice in this repo when the `//` and `/* */` order was wrong.
 *
 * The behaviour — what a typed band means, what a cleared field writes — is in
 * `model/ProtocolTargetEditorTest`, which needs no source scan at all.
 */
class ProtocolTargetsStructureTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/screens/protocol"),
        File("app/src/main/java/com/trichome/app/ui/screens/protocol")
    ).filter { it.isDirectory }

    /**
     * Block comments first, then line comments.
     *
     * In that order: a `//` inside a block comment would otherwise start a line comment
     * and swallow the rest of the block. This exact ordering has broken twice here.
     */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    private fun codeOf(name: String): String {
        val file = sources.flatMap { dir ->
            dir.listFiles { f -> f.name.endsWith(".kt") }?.toList() ?: emptyList()
        }.firstOrNull { it.name == name }
            ?: error("$name is not in the protocol package")
        return stripComments(file.readText(Charsets.UTF_8))
    }

    /** The body of one composable, from its declaration to the next one. */
    private fun bodyOf(text: String, functionName: String): String {
        val start = text.indexOf("fun $functionName(")
        assertTrue("$functionName is not in the source", start > 0)
        val rest = text.substring(start)
        val end = Regex("""\n(@Composable|(private |internal )?(data )?fun )""").find(rest)
            ?.range?.first
            ?: rest.length
        return rest.substring(0, end)
    }

    private val editors = codeOf("ProtocolTargetEditors.kt")
    private val screen = codeOf("ProtocolScreen.kt")

    /* ── The scan is not inert ─────────────────────────────────────────────── */

    @Test
    fun theSourcesWereActuallyFound() {
        assertTrue(
            "the protocol package was not found; every guard in this file is inert",
            sources.isNotEmpty()
        )
        assertTrue("ProtocolTargetEditors.kt was not read", editors.length > 3000)
        assertTrue("ProtocolScreen.kt was not read", screen.contains("fun ProtocolScreen("))
        assertTrue(
            "the write surface is missing, so this file is guarding a screen that has " +
                "no write path",
            screen.contains("ProtocolTargetsScreen(") &&
                screen.contains("ProtocolStageTargetScreen(")
        )
    }

    /* ── The copy is the model's ───────────────────────────────────────────── */

    @Test
    fun noStringLiteralIsAuthoredInTheWriteSurfaces() {
        // The strong form of the rule, the one the Entourage and F5 phases established:
        // the file holds no string literal that could be a sentence. A label written here
        // is a label no JVM test can reach.
        val literals = Regex(""""([^"]*)"""").findAll(editors)
            .map { it.groupValues[1] }
            .filterNot { it.isBlank() }
            .toList()

        assertTrue(
            "these literals in the write surfaces are copy no test can reach: $literals",
            literals.isEmpty()
        )
    }

    @Test
    fun noUnitIsTypedIntoAComposable() {
        listOf("kPa", "mS/cm", "°C", "mol/m²/d", "µmol", "Sustrato", "Objetivo").forEach { banned ->
            assertFalse(
                "`$banned` is a unit or a label and has to come from " +
                    "ProtocolExtendedFields / ProtocolTargetEditor",
                editors.contains(banned)
            )
        }
    }

    @Test
    fun theSurfaceReadsItsLabelsFromTheFieldTable() {
        listOf(
            "ProtocolTargetEditor.formFor",
            "ProtocolTargetEditor.resolve",
            // The band's two-inputs-one-value rule is `resolveBand`, and it is reached
            // through `resolve` and `resolveStageTarget` rather than called here: a
            // composable that built a band itself would be a second implementation of
            // the rule that a half-filled band cannot commit.
            "ProtocolTargetEditor.resolveStageTarget",
            "ProtocolTargetEditor.titleEs",
            "ProtocolTargetEditor.stageTitleEs",
            "ProtocolTargetEditor.fieldHeadingEs",
            "ProtocolTargetEditor.CONFIRM_ES",
            "ProtocolTargetEditor.CANCEL_ES",
            "ProtocolTargetEditor.MIN_ES",
            "ProtocolTargetEditor.MAX_ES",
            "ProtocolTargetEditor.GOAL_NOT_MEASUREMENT_ES"
        ).forEach { call ->
            assertTrue(
                "$call has to reach the surface, or the composable decided the copy itself",
                editors.contains(call)
            )
        }
    }

    @Test
    fun aRefusalIsRenderedOutsideTheScrollSoItCannotBeScrolledOutOfSight() {
        val chrome = bodyOf(editors, "TargetEditorPage")

        assertTrue("the refusal is rendered", chrome.contains("problemEs"))
        assertTrue(
            "and it is not inside the scrolling column: a refusal about the fifth of " +
                "seven fields must be visible without scrolling",
            chrome.indexOf("HorizontalDivider()") < chrome.indexOf("problemEs != null")
        )
    }

    /* ── One scroll owner, and it is not the list's ────────────────────────── */

    @Test
    fun theWriteSurfacesDeclareExactlyOneScrollOwner() {
        assertEquals(
            "one vertical scroll per surface; a second one throws when it composes",
            1,
            Regex("""\.verticalScroll\(""").findAll(editors).count()
        )
    }

    @Test
    fun theWriteSurfacesDeclareNoList() {
        assertFalse(
            "a LazyColumn or LazyRow inside the surface is a second scroll owner",
            editors.contains("LazyColumn") || editors.contains("LazyRow")
        )
    }

    @Test
    fun theProtocolListIsNotComposedWhileAWriteSurfaceIsOpen() {
        // The scroll-ownership rule is structural: the surface must replace the list, not
        // mount inside it. Both branches and the shared flag are pinned by name, so a
        // change that put the page inside the LazyColumn fails here rather than on a
        // device, as an infinity-constraints crash.
        assertTrue(
            "the surface's branches have to exist",
            screen.contains("session != null -> ProtocolTargetsScreen(")
        )
        assertTrue(
            "and the per-stage one too, on its own entity",
            screen.contains("ProtocolStageTargetScreen(")
        )
        assertTrue(
            "the bar is hidden while a surface is open, or two titles stack",
            screen.contains("if (!onTargetsSurface)")
        )
        assertEquals(
            "the screen still owns exactly one list",
            1,
            Regex("""LazyColumn\(""").findAll(screen).count()
        )
        // The list lives in the `else` branch, after the two surface branches.
        val firstSurface = screen.indexOf("session != null -> ProtocolTargetsScreen(")
        val secondSurface = screen.indexOf("stage != null ->")
        val listBranch = screen.indexOf("else -> Column(")
        assertTrue(
            "the list has to be the branch that is taken when no surface is open",
            firstSurface > 0 && secondSurface > firstSurface && listBranch > secondSurface
        )
    }

    /* ── The dialog did not become the write path ──────────────────────────── */

    @Test
    fun theProtocolEditorDialogCarriesNoTargetInput() {
        val dialog = bodyOf(screen, "ProtocolEditorDialog")

        listOf(
            "ProtocolTargetField",
            "TargetInput",
            "GrowRange",
            "ProtocolTargetEditor.formFor"
        ).forEach { banned ->
            assertFalse(
                "`$banned` in ProtocolEditorDialog: the fifteen targets are written on " +
                    "their own pages. That dialog was already measured filling most of a " +
                    "2000-pixel-tall screen on a real device.",
                dialog.contains(banned)
            )
        }
        assertTrue(
            "and it still writes the schedule",
            dialog.contains("blocks")
        )
    }

    @Test
    fun theScheduleEditorHandsBackTheStageIds() {
        // The other half of the same rule: the save carries each stage's own id, because
        // `saveStages` tells an edit from a removal by that id and nothing else.
        val dialog = bodyOf(screen, "ProtocolEditorDialog")

        assertTrue(
            "the dialog's blocks must be ProtocolStage rows, not name/days pairs",
            dialog.contains("List<ProtocolStage>")
        )
        assertTrue(
            "and they are seeded from the persisted rows",
            dialog.contains("initialStageDrafts")
        )
        val blocks = codeOf("ProtocolBlocks.kt")
        assertTrue(
            "the seed has to keep the id",
            blocks.contains("stages.sortedBy { it.sortOrder }")
        )
    }

    /* ── Colours, glyphs, glass ────────────────────────────────────────────── */

    @Test
    fun noHardcodedColourReachesTheWriteSurfaces() {
        val offenders = Regex("""Color\(0x""").findAll(editors).map { it.value }.toList()
        assertTrue("these hardcode a colour instead of reading the scheme: $offenders", offenders.isEmpty())
    }

    @Test
    fun everyGlyphOnTheNewSurfacesIsTintedFromTheTheme() {
        val glyph = bodyOf(editors, "TargetEditGlyph")

        assertTrue("the affordance glyph is not in the source", glyph.contains("Icon("))
        assertFalse(
            "`tint = Color.Unspecified` installs no ColorFilter, so the vector draws its " +
                "own colour — that shipped 184 invisible glyphs at 1.11:1",
            glyph.contains("Color.Unspecified")
        )
        assertTrue(
            "and it takes its colour from a scheme role",
            glyph.contains("tint = LocalTertiaryText.current")
        )
    }

    @Test
    fun theWriteSurfacesStaySolidMaterialAndFollowTheAccentOnlyWhereItMeansSomething() {
        listOf("blur(", "BlurredEdgeTreatment", "graphicsLayer", "GlassPanel").forEach { banned ->
            assertFalse(
                "the write surfaces must stay solid, found $banned",
                editors.contains(banned)
            )
        }
        assertFalse(
            "`accentLabelOn` is chrome, and a form field is not chrome: a form that " +
                "repaints with the accent is the inconsistency AccentRoleSeparationTest " +
                "was written for",
            editors.contains("accentLabelOn(")
        )
    }

    @Test
    fun theNumericBoxesShareARowAndTheirWidthIsWonFromThePageMargin() {
        val band = bodyOf(editors, "TargetBandField")

        assertTrue(
            "the two bounds of a band are drawn side by side, each weighted",
            band.contains("Modifier.weight(1f)")
        )
        assertTrue(
            "with room between them",
            band.contains("Arrangement.spacedBy(BAND_FIELD_GAP)")
        )
        assertTrue(
            "and the row is inset less than the rest of the form, because two default-inset " +
                "boxes in half a 360 dp page cannot hold `0,80` twice",
            band.contains("padding(horizontal = BAND_FIELD_INSET)")
        )
        assertTrue(
            "the insets are declared as one named value each, not tuned per call site",
            editors.contains("private val BAND_FIELD_INSET = 8.dp") &&
                editors.contains("private val BAND_FIELD_GAP = 8.dp") &&
                editors.contains("private val FORM_FIELD_INSET = 16.dp")
        )
        assertFalse(
            "`RowScope.weight(0f)` throws while composing and has shipped a crash here",
            Regex("""weight\(\s*0f""").containsMatchIn(editors)
        )
    }

    @Test
    fun theFormKeepsThePagesOwnMarginForEverythingThatIsNotABand() {
        assertTrue(
            "the scroll column must not eat the margin, or a full-width field would touch " +
                "the screen edge",
            bodyOf(editors, "TargetEditorPage")
                .contains("padding(vertical = 12.dp)")
        )
        listOf("TargetMetricField", "TargetFreeField").forEach { fn ->
            assertTrue(
                "$fn lost the page margin",
                bodyOf(editors, fn).contains("padding(horizontal = FORM_FIELD_INSET)")
            )
        }
    }

    @Test
    fun theNumbersAreSetInTheInstrumentedRegister() {
        listOf("TargetMetricField", "NumericField").forEach { fn ->
            val body = bodyOf(editors, fn)
            assertTrue(
                "$fn no longer sets the typed value in the metric register, so a VPD band " +
                    "and a substrate name are read in the same face",
                body.contains("textStyle = LocalMetricValue.current")
            )
        }
        assertFalse(
            "the grower's own words are prose, not instrumentation",
            bodyOf(editors, "TargetFreeField").contains("LocalMetricValue")
        )
    }
}