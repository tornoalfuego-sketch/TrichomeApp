package com.trichome.app.data.repository

import com.trichome.app.model.EntourageAchievement
import com.trichome.app.model.EntourageQuizLevel
import com.trichome.app.model.EntourageQuizLevels
import com.trichome.app.model.EntourageQuizQuestion
import com.trichome.app.model.EntourageQuiz
import com.trichome.app.model.EntourageQuizState
import com.trichome.app.model.EntourageRewards
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * F5 — the levelled quiz.
 *
 * ## Why the classification is asserted here and not just documented
 *
 * A level is a claim about a question, and a claim nobody checks is a number in a
 * table. So the two rules the classification is held to are asserted **over the
 * shipped asset**, not over a comment:
 *
 *  1. a question whose explanation cites **human trials** is never
 *     [EntourageQuizLevel.PRINCIPIANTE];
 *  2. a question that **only names a receptor** is never
 *     [EntourageQuizLevel.BIOQUIMICO].
 *
 * Neither rule can be satisfied by an enum with three members; both can be
 * violated by an assignment, and [theShippedClassificationFollowsTheRuleItDocuments]
 * is what catches it.
 *
 * ## Why `QUIZ_ROUNDS` had to go
 *
 * F1 removed a literal badge sentence ("Acierta 8 de 10") that sat next to a
 * derived threshold, and left `descriptionFor(rounds)` to build the text from the
 * run actually played. But it also left `const val QUIZ_ROUNDS = 10` behind as the
 * argument for a zero-argument `description`. The moment F5 added six questions,
 * that constant was a hand-maintained second copy of a number the asset already
 * holds — the same drift F1's KDoc warned about, one layer up. There is
 * deliberately no zero-argument reading left to reach for, and
 * [thereIsNoSecondCopyOfTheQuestionCountAnywhere] checks that against the source.
 */
class EntourageQuizLevelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun content(): EntourageContent {
        val file = listOf(
            File("src/main/assets/data/entourage_data.json"),
            File("app/src/main/assets/data/entourage_data.json")
        ).first { it.isFile }
        return json.decodeFromString<EntourageBible>(String(file.readBytes(), Charsets.UTF_8))
            .toContent()
    }

    private fun questions() = content().questions

    /* ── The shipped classification ──────────────────────────────────────── */

    @Test
    fun everyShippedQuestionHasALevelAndEveryLevelIsUsed() {
        val all = questions()

        assertTrue(
            "level has no default on the model, so this is a distribution " +
                "assertion rather than a null check",
            all.all { it.level in EntourageQuizLevel.entries }
        )
        val counts = EntourageQuizLevels.countsByLevel(all)
        assertEquals(
            "the counts have to name every level, including one that ships none, " +
                "or a screen cannot tell 'absent' from 'empty'",
            EntourageQuizLevel.entries.toSet(),
            counts.keys
        )
        EntourageQuizLevel.entries.forEach { level ->
            assertTrue(
                "$level ships no question, so the level is a name with no content. " +
                    "Counts: $counts",
                (counts[level] ?: 0) >= 1
            )
        }
    }

    /**
     * The two rules, over the real questions.
     *
     * Rule 1 reads the shipped explanations for evidence of human trials.
     *
     * Rule 2 needs a declared marker list rather than a count of receptor
     * mentions, and that is worth being explicit about: the first version counted
     * occurrences of a receptor name and flagged `q8_cbd_trpv1`, whose answer *is*
     * a receptor pair and which is genuinely biochemistry — its explanation says
     * the two compounds "convergen en la vía inflamatoria por mecanismos
     * distintos". A heuristic this blunt does not encode the rule, it encodes an
     * approximation of it, and a rule the test can only approximate is a rule
     * nobody is holding. So the markers below are the substance of "the answer
     * requires reasoning about a mechanism", listed where a reviewer can argue
     * with them.
     */
    @Test
    fun theShippedClassificationFollowsTheRuleItDocuments() {
        val receptors = listOf("CB1", "CB2", "TRPV1", "TRPA1", "5-HT1A", "PPAR-gamma")
        val humanTrialMarkers = listOf(
            "ensayos en personas", "en personas", "estudio clínico", "ensayo clínico",
            "pacientes", "voluntarios"
        )
        // What makes a receptor answer biochemistry rather than recall.
        val mechanismMarkers = listOf(
            "mecanismo", "mecanismo", "mecanicista", "vía", "señaliz", "convergen",
            "agonista", "antagonista", "afinidad", "parcial", "in vitro",
            "modelos animales", "hipótesis", "hipotesis", "evidencia", "se describe",
            "alostérica", "alosterica", "compet", "selectividad", "inhibidor",
            "modula", "descrita como"
        )

        questions().forEach { question ->
            val explanation = question.explanationEs.lowercase()
            val prompt = question.promptEs.lowercase()
            val options = question.optionsEs.map { it.lowercase() }

            if (humanTrialMarkers.any { it in explanation }) {
                assertNotEquals(
                    "${question.id} cites human trials, so it cannot be a " +
                        "beginner question",
                    EntourageQuizLevel.PRINCIPIANTE,
                    question.level
                )
            }

            // Rule 2: the answer is a receptor name, the prompt asks for a
            // receptor, and the explanation offers no mechanism reasoning.
            val answerNamesAReceptor = receptors.any { it.lowercase() in options[question.correctIndex] }
            val promptAsksForAReceptor = "receptor" in prompt
            val explanationReasonsAboutMechanism = mechanismMarkers.any { it in explanation }

            if (answerNamesAReceptor && promptAsksForAReceptor && !explanationReasonsAboutMechanism) {
                assertNotEquals(
                    "${question.id} is answered by naming a receptor with no " +
                        "mechanism reasoning behind it, so it is recall rather " +
                        "than biochemistry",
                    EntourageQuizLevel.BIOQUIMICO,
                    question.level
                )
            }
        }
    }

    @Test
    fun theClassificationIsNotTrivial() {
        val counts = EntourageQuizLevels.countsByLevel(questions())

        assertTrue(
            "a levelled quiz with every question on one rung is a flat quiz with " +
                "extra steps. Counts: $counts",
            counts.values.count { it > 0 } >= 3
        )
        assertTrue(
            "and the beginner band has to hold more than one question or it is a " +
                "token",
            (counts[EntourageQuizLevel.PRINCIPIANTE] ?: 0) >= 2
        )
    }

    /* ── What the level does ─────────────────────────────────────────────── */

    @Test
    fun aRunIsOrderedByLevelAndTheOrderIsStableWithinABand() {
        val shipped = questions()
        val reversed = shipped.reversed()

        // Ramping: the reversed input has to come out in level order regardless of
        // how the caller happened to hand the questions over.
        listOf(shipped, reversed).forEach { input ->
            val ordered = EntourageQuizLevels.orderByLevel(input)
            assertEquals(
                "the run has to ramp: every level, once, in order",
                EntourageQuizLevel.entries.toList(),
                ordered.map { it.level }.distinct()
            )
            assertEquals(
                "and it cannot drop or gain a question on the way",
                shipped.size,
                ordered.size
            )
        }

        // Stability: a stable sort preserves the order of its **input**, so this
        // is asserted against the input rather than against the shipped file.
        // Asserting it against the file would be asserting a different property —
        // that the sort is the identity — and would pass or fail for the wrong
        // reason.
        val ordered = EntourageQuizLevels.orderByLevel(reversed)
        val band = ordered.first().level
        assertEquals(
            "the sort has to be stable, or two questions of the same level would " +
                "come out in a different order on a different device",
            reversed.filter { it.level == band }.map { it.id },
            ordered.filter { it.level == band }.map { it.id }
        )
    }

    @Test
    fun theQuizMachineItselfOrdersByLevel() {
        // The ordering lives in the machine's constructor rather than at the call
        // site, so a run cannot be assembled unlevelled.
        val quiz = EntourageQuiz(questions().reversed(), random = Random(3))

        val seen = mutableListOf<EntourageQuizLevel>()
        var state: EntourageQuizState = quiz.state
        while (state is EntourageQuizState.Asking || state is EntourageQuizState.Revealed) {
            val question = when (state) {
                is EntourageQuizState.Asking -> state.question
                is EntourageQuizState.Revealed -> state.question
                else -> error("unreachable")
            }
            seen += question.level
            if (state is EntourageQuizState.Asking) state = quiz.answer(question.correctIndex)
            state = quiz.next()
        }
        assertEquals(
            "the machine has to play the levels in order",
            seen.distinct(),
            EntourageQuizLevel.entries.toList()
        )
        assertEquals("and every question exactly once", questions().size, seen.size)
    }

    @Test
    fun everyLevelHasTextTheScreenCanPrint() {
        EntourageQuizLevel.entries.forEach { level ->
            assertTrue("${level.key} has no label", level.labelEs.isNotBlank())
            assertTrue("${level.key} has no gloss", level.blurbEs.isNotBlank())
            assertTrue(
                "${level.key} prints text the language guard would reject: " +
                    EntourageLanguageViolations(level.labelEs + " " + level.blurbEs),
                EntourageLanguageViolations(level.labelEs + " " + level.blurbEs).isEmpty()
            )
        }
    }

    private fun EntourageLanguageViolations(text: String) =
        com.trichome.app.model.EntourageLanguage.violations(text)

    @Test
    fun theBadgeOnAQuestionCarriesBothTheLevelAndWhyItMatters() {
        questions().forEach { question ->
            val badge = EntourageQuizLevels.badgeEs(question)
            assertTrue("$badge does not name the level", question.level.labelEs in badge)
            assertTrue("$badge does not explain the level", question.level.blurbEs in badge)
        }
    }

    @Test
    fun theRunSummaryNamesEveryLevelItActuallyPlayed() {
        val all = questions()
        val summary = EntourageQuizLevels.summaryEs(all)

        EntourageQuizLevels.levelsIn(all).forEach { level ->
            assertTrue("$summary does not mention $level", level.labelEs in summary)
            assertTrue(
                "$summary does not count $level",
                "${level.labelEs}: ${EntourageQuizLevels.countsByLevel(all)[level]}" in summary
            )
        }
        assertTrue(
            "and it is not empty for a shipped run",
            summary.isNotBlank()
        )
    }

    @Test
    fun anEmptyQuizSummaryIsEmptyRatherThanAWrongSentence() {
        assertEquals("", EntourageQuizLevels.summaryEs(emptyList()))
        assertTrue(EntourageQuizLevels.levelsIn(emptyList()).isEmpty())
    }

    /* ── The badge text, after six more questions ────────────────────────── */

    @Test
    fun theQuizBadgeTextFollowsTheNewTotalAndAPartialRun() {
        val rounds = questions().size
        val shipped = EntourageAchievement.ENTOURAGE_MASTER

        assertEquals(
            "the shipped quiz grew, so a hardcoded 10 would be a lie",
            "Acierta 12 de 16 preguntas sobre modulación terpénica",
            shipped.descriptionFor(rounds)
        )
        assertEquals(
            "and a partial run derives from its own length, not the shipped total",
            "Acierta 4 de 5 preguntas sobre modulación terpénica",
            shipped.descriptionFor(5)
        )
        assertEquals(
            "including the degenerate short run, because thresholdFor coerces to " +
                "at least one and the sentence has to agree with it",
            "Acierta 1 de 1 preguntas sobre modulación terpénica",
            shipped.descriptionFor(1)
        )
        assertEquals(
            "and a run of zero rounds still gets a sentence",
            "Acierta 1 de 0 preguntas sobre modulación terpénica",
            shipped.descriptionFor(0)
        )
    }

    @Test
    fun theThresholdArithmeticHoldsAgainstTheNewTotalAndAPartialRun() {
        val rounds = questions().size

        assertEquals(12, EntourageAchievement.thresholdFor(rounds))
        assertTrue(
            "a full run has to reach it",
            EntourageAchievement.isEarned(12, rounds, finished = true)
        )
        assertTrue(
            "and one short must not",
            !EntourageAchievement.isEarned(11, rounds, finished = true)
        )
        assertTrue(
            "an abandoned run at the threshold is not a completed run",
            !EntourageAchievement.isEarned(12, rounds, finished = false)
        )
        assertEquals(4, EntourageAchievement.thresholdFor(5))
        assertTrue(EntourageAchievement.isEarned(4, 5, finished = true))
        assertTrue(!EntourageAchievement.isEarned(3, 5, finished = true))
        assertTrue(
            "and the reward agrees with the rule over the shipped length",
            EntourageRewards.forQuiz(12, rounds, finished = true).size == 1
        )
        assertTrue(
            "while one short pays nothing",
            EntourageRewards.forQuiz(11, rounds, finished = true).isEmpty()
        )
    }

    /**
     * There is no hand-maintained copy of the question count left anywhere.
     *
     * Read from the source because the removal is a compile-time fact that no
     * behavioural test can reach: nothing calls `QUIZ_ROUNDS`, so nothing fails
     * if it comes back. Scanning for it is the cheapest honest guard.
     */
    @Test
    fun thereIsNoSecondCopyOfTheQuestionCountAnywhere() {
        val sources = listOf(
            File("src/main/java/com/trichome/app"),
            File("app/src/main/java/com/trichome/app")
        ).filter { it.isDirectory }

        val offenders = sources.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file ->
                    val code = file.readText(Charsets.UTF_8)
                        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                        .replace(Regex("""//[^\n]*"""), " ")
                    code.contains("QUIZ_ROUNDS")
                }
                .map { it.name }
        }

        assertTrue(
            "a QUIZ_ROUNDS constant is a second copy of the asset's question " +
                "count and is exactly the drift F1 removed: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun everyShippedQuestionIsStillAnswerableWithItsLevelIntact() {
        questions().forEach { question ->
            assertTrue(
                "${question.id} has no level, so it would render with no chip",
                question.level != null
            )
            assertTrue(
                "${question.id} lost its answer",
                question.correctIndex in question.optionsEs.indices
            )
            assertEquals(
                "${question.id} lost its options",
                question.optionsEs.size,
                question.optionsEs.distinct().size
            )
        }
    }

    @Test
    fun anUnlevelledQuestionCannotBeConstructed() {
        // The model makes this a compile error rather than a runtime one; the
        // shipped-asset guard above is what catches a missing `level` key in the
        // file, which is the only way it could happen at all.
        val withoutLevel = "EntourageQuizQuestion"
        val source = listOf(
            File("src/main/java/com/trichome/app/model/Entourage.kt"),
            File("app/src/main/java/com/trichome/app/model/Entourage.kt")
        ).first { it.isFile }.readText(Charsets.UTF_8)

        val block = source.substringAfter("data class $withoutLevel(").substringBefore(")")
        assertTrue(
            "level must be a constructor parameter of $withoutLevel",
            block.contains("val level: EntourageQuizLevel")
        )
        assertTrue(
            "and it must carry no default, or a question could exist without one",
            !block.contains("val level: EntourageQuizLevel =")
        )
    }

    @Test
    fun aQuestionWithNoLevelIsDroppedAndNamedRatherThanRenderedBlank() {
        // The parse path, verified by mutating the bible the way F3 and F4 did.
        val shipped = content()
        val parsed = shipped
        val broken = json.decodeFromString<EntourageBible>(
            String(
                listOf(
                    File("src/main/assets/data/entourage_data.json"),
                    File("app/src/main/assets/data/entourage_data.json")
                ).first { it.isFile }.readBytes(),
                Charsets.UTF_8
            )
        ).copy(
            quiz = json.decodeFromString<EntourageBible>(
                String(
                    listOf(
                        File("src/main/assets/data/entourage_data.json"),
                        File("app/src/main/assets/data/entourage_data.json")
                    ).first { it.isFile }.readBytes(),
                    Charsets.UTF_8
                )
            ).quiz.map {
                if (it.id == "q6_limonene_temperatura") it.copy(level = "") else it
            }
        ).toContent()

        assertEquals(
            "the question must go rather than render with no level",
            parsed.questions.size - 1,
            broken.questions.size
        )
        assertTrue(
            "and the drop has to be named, found ${broken.unresolvedReferences}",
            broken.unresolvedReferences.any {
                it.startsWith("quiz.q6_limonene_temperatura.level")
            }
        )
    }
}
