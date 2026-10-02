package com.trichome.app.ui.screens.entourage

import com.trichome.app.model.EntourageLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F5 — the structural rules of the two surfaces F5 adds.
 *
 * ## Why this reads source
 *
 * The failure modes are not behavioural and no JVM test can reach them. Compose
 * has no unit-test runtime on the `test` classpath in this project, which is not
 * a preference: four separate bug classes have already come from
 * composable-level logic nothing could test — a `weight(0f)` that threw at compose
 * time and took down every terpene page, an unwighted `Row` that overflowed,
 * Spanish sentences authored inside a composable, and a caller-side existence
 * check two ViewModels raced past.
 *
 * F5 adds two new risks to that list. A handling case renders its inputs, and a
 * screen that decides *which* inputs to render from a heuristic rather than from
 * [com.trichome.app.model.LabMode] would put a THC slider on an agricultural case.
 * And a quiz that classified its questions without showing the level would be a
 * number in a table. Neither is reachable from the model, because the model is
 * correct in both cases and the defect is in what the screen chose to print.
 *
 * So this file asserts, against the source with comments stripped:
 *
 *  1. **The mode, not a heuristic, chooses the inputs.** The dials are reachable
 *     only from the pharmacological branch.
 *  2. **The level chip is on the screen** for both open states, and its text comes
 *     from the model rather than being composed here.
 *  3. **No Spanish sentence is authored in either composable**, in the strong form
 *     F3 established: no non-blank string literal at all.
 *  4. **One scroll owner per axis**, solid Material 3, no hardcoded colour.
 *
 * Comments are stripped before every scan, so a KDoc mentioning `verticalScroll`
 * or `Color.Unspecified` is not a hit. That rule has broken twice in this repo and
 * it is applied here on purpose.
 */
class EntourageF5StructureTest {

    private val packageDir = listOf(
        File("src/main/java/com/trichome/app/ui/screens/entourage"),
        File("app/src/main/java/com/trichome/app/ui/screens/entourage")
    ).first { it.isDirectory }

    private fun codeOf(name: String): String {
        val file = packageDir.listFiles { f -> f.name == name && f.extension == "kt" }
            ?.firstOrNull()
            ?: error("$name is not in the entourage package")
        return stripComments(file.readText(Charsets.UTF_8))
    }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    /**
     * The body of one composable, from its declaration to the next one.
     *
     * Scoped on purpose: an unrelated composable in the same file must not decide
     * this. Same pattern `TerpeneDetailProcessingTest` needed.
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

    private fun labSection() = bodyOf(codeOf("EntourageLabSection.kt"), "EntourageLabSection")
    private fun quizSection() = bodyOf(codeOf("EntourageQuizSection.kt"), "EntourageQuizSection")
    private fun levelChip() = bodyOf(codeOf("EntourageQuizSection.kt"), "LevelChip")

    /* ── The mode chooses the inputs, not a heuristic ────────────────────── */

    @Test
    fun theHandlingBranchIsChosenByTheCasesOwnMode() {
        val body = labSection()

        assertTrue(
            "the section has to read case.mode, or the two modes are one screen " +
                "guessing from which fields are populated",
            body.contains("case.mode == LabMode.HANDLING")
        )
        assertTrue(
            "and it has to be a named binding, because every input block below " +
                "branches on it",
            body.contains("val handling = case.mode == LabMode.HANDLING")
        )
    }

    @Test
    fun theCannabinoidDialsAreReachableOnlyFromThePharmacologicalBranch() {
        val body = labSection()

        assertTrue(
            "the dial panel has to be inside the else of the mode branch",
            Regex("""\}\s*else\s*\{\s*dialCannabinoids\.forEach""").containsMatchIn(body)
        )
        assertTrue(
            "and the route chips have to be inside the handling branch, or a " +
                "pharmacological case would offer a processing route",
            Regex("""if \(handling\) \{\s*EntourageChipFlow\(\s*options = routes""")
                .containsMatchIn(body)
        )
    }

    @Test
    fun theDialsAreGatedOnTheModelReturningNoCannabinoidRatherThanOnAnIf() {
        val body = labSection()
        val indexOf = body.indexOf("dialCannabinoids.forEach")

        assertTrue(indexOf > 0)
        val branch = body.substring(0, indexOf)
        assertTrue(
            "the source of truth for 'no cannabinoid on this case' is " +
                "EntourageLabUi.dialCannabinoids, not a second condition here",
            branch.contains("EntourageLabUi.dialCannabinoids(case)")
        )
    }

    /* ── Every headline string comes from the model ──────────────────────── */

    @Test
    fun theHeadlineNumberAndItsLabelAreAssembledInTheModel() {
        val body = labSection()

        assertTrue(
            "the headline line has to be one function of the feedback, or the " +
                "handling mode could print the pharmacological efficacy",
            body.contains("EntourageLabUi.headlineEs(feedback)")
        )
        assertFalse(
            "and it must not interpolate efficacyPercent directly",
            body.contains("feedback.efficacyPercent")
        )
        assertFalse(
            "nor coveragePercent",
            body.contains("feedback.coveragePercent")
        )
    }

    @Test
    fun theSideEffectPanelIsGatedOnTheModeAndNotOnAnEmptyList() {
        val body = labSection()

        assertTrue(
            "the axes block has to be gated on the model, not on `axes.isEmpty()`: " +
                "a pharmacological case with no declared ceiling renders zero axes " +
                "too",
            body.contains("feedback.hasAxes")
        )
        assertFalse(
            "and the compound readings have to be gated the same way",
            body.contains("feedback.compounds.isEmpty()")
        )
        assertTrue(body.contains("feedback.hasCompounds"))
    }

    /* ── The level is on screen ──────────────────────────────────────────── */

    @Test
    fun theLevelChipRendersOnBothOpenStates() {
        val body = quizSection()

        assertEquals(
            "the chip has to render on the open question and on the reveal, or " +
                "the level appears and then disappears",
            2,
            Regex("""LevelChip\(""").findAll(body).count()
        )
    }

    @Test
    fun theLevelChipTextComesFromTheModelAndNotFromTheComposable() {
        assertTrue(
            "the chip's text has to be built in the model",
            quizSection().contains("EntourageQuizLevels.badgeEs(")
        )
        assertTrue(
            "and the finished run's tally has to be derived too",
            quizSection().contains("EntourageQuizLevels.summaryEs(")
        )
    }

    @Test
    fun theQuizSectionReadsTheQuestionsItWasGivenRatherThanRecomputingThem() {
        // The tally is built from the run's questions, not from a field the
        // caller could fill in with a different list.
        val body = quizSection()
        assertTrue(
            "the section has to take the questions as a parameter",
            body.contains("questions: List<EntourageQuizQuestion>")
        )
        assertTrue(
            "and order them with the model's own rule",
            body.contains("EntourageQuizLevels.orderByLevel(questions)")
        )
    }

    /* ── The copy is the model's, not the composable's ───────────────────── */

    @Test
    fun noSpanishSentenceIsAuthoredInTheLabComposable() {
        val literals = Regex(""""([^"]*)"""").findAll(labSection())
            .map { it.groupValues[1] }
            .toList()

        // The strong form of the rule: the section holds no string literal that
        // could be a sentence. A sentence written here is a sentence no JVM test
        // on this classpath can hold to the language guard, and on a case whose
        // claim is "what this route does to your harvest" that is the sentence
        // most worth checking.
        assertTrue(
            "these literals in the Lab section are copy no test can reach: $literals",
            literals.all { it.isBlank() }
        )
    }

    @Test
    fun theLevelChipHoldsNoStringLiteral() {
        val literals = Regex(""""([^"]*)"""").findAll(levelChip())
            .map { it.groupValues[1] }
            .toList()

        assertTrue(
            "the chip must take its text as a parameter: $literals",
            literals.all { it.isBlank() }
        )
    }

    @Test
    fun everyLabelTheTwoSectionsPrintComesFromTheContentModelOrACase() {
        val lab = labSection()
        val quiz = quizSection()

        listOf(
            "case.titleEs", "case.briefEs", "case.explanationEs",
            "EntourageLabUi.goalLabelEs", "EntourageLabUi.evidenceLabelEs",
            "EntourageLabUi.basisEs", "EntourageLabUi.inputsHeadingEs",
            "EntourageLabUi.compoundsHeadingEs", "EntourageLabUi.evaluateLabelEs",
            "EntourageLabUi.headlineEs", "feedback.headlineGlossEs",
            "feedback.verdictEs", "feedback.notesEs", "case.mode.labelEs",
            "EntourageLabCopy.CASES_ES", "EntourageLabCopy.NO_CASES_ES",
            "EntourageLabCopy.FORBIDDEN_COMPOUNDS_ES", "EntourageLabCopy.SHARE_CEILINGS_ES",
            "EntourageLabCopy.PATIENT_CEILINGS_ES", "EntourageLabCopy.FORBIDDEN_ROUTES_ES",
            "EntourageLabCopy.EVIDENCE_ES", "EntourageLabCopy.COMPOUND_READINGS_ES",
            "EntourageLabCopy.SIDE_EFFECTS_ES", "EntourageLabCopy.WHY_ES",
            "EntourageLabCopy.FORBIDDEN_DIAL_ES", "EntourageLabCopy.dialReadoutEs(",
            "EntourageLabCopy.shareCeilingEs(", "EntourageLabCopy.patientCeilingEs(",
            "EntourageLabCopy.caseChipEs("
        ).forEach { field ->
            assertTrue(
                "$field must reach the screen, or the section is showing the " +
                    "composable's own idea of the content",
                lab.contains(field)
            )
        }

        listOf(
            "state.question.promptEs", "question.promptEs", "question.explanationEs",
            "EntourageQuizLevels.badgeEs", "EntourageQuizLevels.summaryEs",
            "outcome.headlineEs", "EntourageAchievement.thresholdFor"
        ).forEach { field ->
            assertTrue("$field must reach the quiz screen", quiz.contains(field))
        }
    }

    /* ── Layout and colours ──────────────────────────────────────────────── */

    @Test
    fun neitherSectionDeclaresASecondScroll() {
        listOf("the Lab" to labSection(), "the quiz" to quizSection()).forEach { (where, text) ->
            assertFalse(
                "the module's LazyColumn owns the scroll; a nested verticalScroll " +
                    "in $where is measured with an infinite maximum height and throws",
                text.contains("verticalScroll")
            )
            assertFalse(
                "and a nested list in $where would be a second scroll owner",
                text.contains("LazyColumn") || text.contains("LazyRow")
            )
        }
    }

    @Test
    fun noHardcodedColourReachesEitherSection() {
        listOf("the Lab" to labSection(), "the quiz" to quizSection()).forEach { (where, text) ->
            val offenders = Regex("""Color\(0x""").findAll(text).map { it.value }.toList()
            assertTrue("$where hardcodes a colour instead of reading the scheme: $offenders", offenders.isEmpty())
        }
    }

    @Test
    fun neitherSectionPublishesAnUnspecifiedColourIntoASurface() {
        listOf("the Lab" to labSection(), "the quiz" to quizSection()).forEach { (where, text) ->
            // The owner name is a bare identifier, so the parenthesis stays out of
            // the pattern and is added separately. Putting `Surface(` inside the
            // pattern unbalances the group, which is what this test did on its
            // first run.
            listOf("Surface", "AlertDialog", "SolidPanel").forEach { owner ->
                val offenders = Regex(
                    """\b$owner\s*\(\s*color\s*=\s*Color\.Unspecified"""
                ).findAll(text).map { it.value }.toList()
                assertTrue(
                    "$where publishes Color.Unspecified into a $owner: $offenders",
                    offenders.isEmpty()
                )
            }
        }
    }

    @Test
    fun neitherSectionReintroducesGlassmorphism() {
        listOf("the Lab" to labSection(), "the quiz" to quizSection()).forEach { (where, text) ->
            listOf("blur(", "BlurredEdgeTreatment", "graphicsLayer", "GlassPanel").forEach { banned ->
                assertFalse("the module must stay solid; found $banned in $where", text.contains(banned))
            }
        }
    }

    /* ── The badge path ──────────────────────────────────────────────────── */

    @Test
    fun theModuleScreenPassesTheRouteIntoTheSolver() {
        val text = codeOf("EntourageModuleScreen.kt")

        assertTrue(
            "the screen owns the route state, or nothing would choose one",
            text.contains("var labRoute by remember { mutableStateOf<ProcessingMethod?>(null) }")
        )
        assertTrue(
            "and it has to reset with the case, or a route chosen for one case " +
                "would score the next",
            text.contains("labRoute = null")
        )
        assertTrue(
            "and it has to reach the solver, or the mode would score with no route",
            text.contains("route = labRoute")
        )
    }

    /* ── The language guard over the shipped copy ────────────────────────── */

    @Test
    fun theModelCopyTheseScreensPrintPassesTheLanguageGuard() {
        val copy = listOf(
            com.trichome.app.model.LabMode.PHARMACOLOGICAL.labelEs,
            com.trichome.app.model.LabMode.HANDLING.labelEs,
            com.trichome.app.model.HandlingGoal.VOLATILE_FRACTION.labelEs,
            com.trichome.app.model.HandlingGoal.WHOLE_PROFILE.labelEs,
            com.trichome.app.model.HandlingOutcome.KEEPS.labelEs,
            com.trichome.app.model.HandlingOutcome.LOSES.labelEs,
            com.trichome.app.model.HandlingOutcome.DEGRADED.labelEs,
            com.trichome.app.model.EntourageLabUi.PHARMACOLOGICAL_HEADLINE_ES,
            com.trichome.app.model.EntourageLabUi.PHARMACOLOGICAL_GLOSS_ES,
            com.trichome.app.model.EntourageLabUi.HANDLING_HEADLINE_ES,
            com.trichome.app.model.EntourageLabUi.HANDLING_GLOSS_ES,
            com.trichome.app.model.EntourageQuizLevel.PRINCIPIANTE.labelEs,
            com.trichome.app.model.EntourageQuizLevel.PRINCIPIANTE.blurbEs,
            com.trichome.app.model.EntourageQuizLevel.AGRONOMO.labelEs,
            com.trichome.app.model.EntourageQuizLevel.AGRONOMO.blurbEs,
            com.trichome.app.model.EntourageQuizLevel.BIOQUIMICO.labelEs,
            com.trichome.app.model.EntourageQuizLevel.BIOQUIMICO.blurbEs
        ) + com.trichome.app.model.HandlingGuides.all.flatMap { listOf(it.whatEs, it.basisEs) } +
            com.trichome.app.model.LabMode.entries.map { it.labelEs } +
            com.trichome.app.model.EntourageAchievement.entries.map { it.labelEs }

        val offenders = copy
            .flatMap { text -> EntourageLanguage.violations(text).map { "$it in: $text" } }
            .toList()

        assertTrue("the copy these two screens print must be clean: $offenders", offenders.isEmpty())
    }
}
