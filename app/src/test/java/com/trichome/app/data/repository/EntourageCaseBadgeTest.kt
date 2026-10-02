package com.trichome.app.data.repository

import com.trichome.app.model.EntourageAchievement
import com.trichome.app.model.EntourageCase
import com.trichome.app.model.EntourageLab
import com.trichome.app.model.EntourageLabUi
import com.trichome.app.model.EntourageReward
import com.trichome.app.model.EntourageRewards
import com.trichome.app.model.EntourageTerpene
import com.trichome.app.model.LabVerdict
import com.trichome.app.model.ProcessingMethod
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F5 — the third badge, "Alquimista de Terpenos".
 *
 * ## What this badge honestly claims
 *
 * **A verdict in every shipped case.** Not "solved every case" — a case answered
 * with [LabVerdict.RIESGO] also pays, and the row's existence proves a verdict was
 * obtained rather than that it was the best one. The wording says what it can
 * prove.
 *
 * ## Where the condition reads its state
 *
 * From the `achievements` table's own rows. [EntourageRewards.forLabVerdict]
 * writes one row per case named `Séquito: <título>`, and writes **nothing** for
 * [LabVerdict.INEFICAZ], so a name being present is exactly "a paid verdict was
 * obtained". There is deliberately no second "which cases have I played" ledger:
 * a second record of the same fact would be a second source of truth for it, and
 * F4 refused to build one for the resin badge for the same reason.
 *
 * ## What this file cannot prove
 *
 * That the row is written once. That needs Room and a device: F4 shipped four
 * duplicate rows before fixing the caller, and the fix that holds is a read of
 * the **table** inside the same coroutine that writes it, under a mutex, because
 * `appViewModel` scopes to the navigation entry and two live ViewModels can each
 * hold a snapshot taken before the other wrote. What is pinned here is the pure
 * half: the text follows the case list, the condition is a set, and the caller
 * has to claim the name the moment `pending` returns it.
 */
class EntourageCaseBadgeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun content(): EntourageContent {
        val file = listOf(
            File("src/main/assets/data/entourage_data.json"),
            File("app/src/main/assets/data/entourage_data.json")
        ).first { it.isFile }
        return json.decodeFromString<EntourageBible>(String(file.readBytes(), Charsets.UTF_8))
            .toContent()
    }

    private fun cases() = content().cases

    /** The row names a full playthrough would leave in the table. */
    private fun namesAfter(cases: List<EntourageCase>): Set<String> =
        cases.map { EntourageRewards.caseRowName(it.titleEs) }.toSet()

    /* ── The text follows the shipped case list ──────────────────────────── */

    @Test
    fun theBadgeTextFollowsTheShippedCaseList() {
        val shipped = cases()
        assertTrue("the case list has to be non-empty", shipped.isNotEmpty())

        val text = EntourageAchievement.TERPENE_ALCHEMIST.descriptionForCases(shipped.size)

        assertEquals(
            "the badge text has to name the ${shipped.size} cases the asset " +
                "actually ships",
            "Obtén un veredicto en los ${shipped.size} casos del Laboratorio",
            text
        )
    }

    @Test
    fun theBadgeTextChangesWithTheCaseListRatherThanSittingAsAFixedString() {
        assertNotEquals(
            "the badge text must be a function of the data, not a constant",
            EntourageAchievement.TERPENE_ALCHEMIST.descriptionForCases(4),
            EntourageAchievement.TERPENE_ALCHEMIST.descriptionForCases(2)
        )
        assertEquals(
            "Obtén un veredicto en los 2 casos del Laboratorio",
            EntourageAchievement.TERPENE_ALCHEMIST.descriptionForCases(2)
        )
    }

    @Test
    fun theBadgeDoesNotClaimToHaveSolvedTheCases() {
        // The specific dishonesty this test exists for. `forLabVerdict` pays
        // 20 XP for a RIESGO verdict, so a row's presence proves a verdict was
        // obtained and nothing more. A text that said "resuelve" or "completa"
        // would be claiming something the condition cannot establish.
        val text = EntourageAchievement.TERPENE_ALCHEMIST.descriptionForCases(4)

        listOf("resuelve", "resuelto", "completa", "perfecto", "domin").forEach { word ->
            assertFalse(
                "\"$word\" claims more than \"a verdict in every case\" can prove: $text",
                word in text.lowercase()
            )
        }
        assertTrue("and it has to say what it does claim", "veredicto" in text.lowercase())
    }

    /* ── The condition ───────────────────────────────────────────────────── */

    @Test
    fun aPartialPlaythroughPaysNothing() {
        val shipped = cases()
        assertTrue("the fixture needs more than one case", shipped.size > 1)
        val partial = namesAfter(shipped.dropLast(1))

        assertTrue(
            "${partial.size} of ${shipped.size} cases must not pay the badge",
            EntourageRewards.forAllCasesVerdicted(shipped, partial).isEmpty()
        )
    }

    @Test
    fun anEmptyCaseListPaysNothing() {
        // A build whose asset lost the cases must not award a badge claiming the
        // content is there. Same rule F4 gave for its badge.
        assertTrue(
            "an empty case list has nothing to have played",
            EntourageRewards.forAllCasesVerdicted(emptyList(), EntourageTerpene.entries.map { it.labelEs }.toSet())
                .isEmpty()
        )
    }

    @Test
    fun aFullPlaythroughPaysExactlyTheBadge() {
        val shipped = cases()
        val rewards = EntourageRewards.forAllCasesVerdicted(shipped, namesAfter(shipped))

        assertEquals("a full playthrough pays one reward", 1, rewards.size)
        val badge = EntourageAchievement.TERPENE_ALCHEMIST
        assertEquals(
            "and the name has to be the badge's, because the name is the " +
                "idempotency key in the achievements table",
            badge.labelEs,
            rewards.first().nameEs
        )
        assertEquals(
            badge.icon,
            rewards.first().icon
        )
        assertEquals(badge.xpReward, rewards.first().xpReward)
        assertTrue("and it has to be worth something", rewards.first().xpReward > 0)
        assertEquals(
            badge.descriptionForCases(shipped.size),
            rewards.first().descriptionEs
        )
    }

    @Test
    fun theRowNameAndTheConditionAreBuiltFromOneFunction() {
        // The join handle is the title, so the reward's name and the condition's
        // expected name have to come from the same place. Two literals would be
        // two chances to drift, and the drift is invisible: the case would pay
        // and the badge would never unlock.
        val shipped = cases()
        val paid = EntourageRewards
            .forLabVerdict(shipped.first().id, shipped.first().titleEs, LabVerdict.VIABLE)
            .single()

        assertEquals(
            "the paid row name and the badge's expected name must agree",
            EntourageRewards.caseRowName(shipped.first().titleEs),
            paid.nameEs
        )
    }

    @Test
    fun anIneffectiveVerdictWritesNoRowAndSoCannotUnlockTheBadge() {
        // The whole reason the condition can be read from the table.
        val shipped = cases()
        assertTrue(
            "an INEFICAZ verdict pays nothing, so the case leaves no row",
            EntourageRewards.forLabVerdict(shipped.first().id, shipped.first().titleEs, LabVerdict.INEFICAZ)
                .isEmpty()
        )
    }

    @Test
    fun theBadgeIsPaidExactlyOnce() {
        val shipped = cases()
        val rewards = EntourageRewards.forAllCasesVerdicted(shipped, namesAfter(shipped))

        assertTrue(
            "a full playthrough replayed must not pay twice",
            EntourageRewards.pending(rewards, awardedNames = setOf(rewards.first().nameEs)).isEmpty()
        )
        assertEquals(
            "and it pays the first time",
            1,
            EntourageRewards.pending(rewards, awardedNames = emptySet()).size
        )
    }

    /**
     * The rule the F4 duplicate rows broke, applied to this badge.
     *
     * Cannot be asserted on the ViewModel from the JVM — Compose and
     * `viewModelScope` are not on the `test` classpath — so what is pinned is the
     * contract the caller has to honour, over the pure functions:
     * [EntourageRewards.pending] is the **only** gate, so its output must be
     * treated as consumed the moment it is non-empty.
     */
    @Test
    fun theCallerHasToClaimTheNameTheMomentPendingReturnsIt() {
        val shipped = cases()
        var claimed: Set<String> = emptySet()
        repeat(5) {
            val fresh = EntourageRewards.pending(
                EntourageRewards.forAllCasesVerdicted(shipped, namesAfter(shipped) + claimed),
                claimed
            )
            if (fresh.isNotEmpty()) {
                claimed = claimed + fresh.map { it.nameEs }
                assertEquals("the model paid once", 1, fresh.size)
            }
        }
        assertEquals("and only one name was ever claimed", 1, claimed.size)
        assertTrue(
            claimed.contains(EntourageAchievement.TERPENE_ALCHEMIST.labelEs)
        )
    }

    @Test
    fun twoLiveViewModelsRacingOverTheSameTablePayOneRow() {
        // The F4 failure mode, simulated over the pure functions: two holders of
        // the same snapshot both decide the badge is not earned, and both insert.
        val shipped = cases()
        val names = namesAfter(shipped)
        val rewards = EntourageRewards.forAllCasesVerdicted(shipped, names)

        // ViewModel A's snapshot predates the last case row.
        val staleSnapshot = namesAfter(shipped.dropLast(1))
        assertTrue(
            "the stale snapshot alone does not qualify",
            EntourageRewards.forAllCasesVerdicted(shipped, staleSnapshot).isEmpty()
        )

        // Both claim before either inserts, which is exactly the interleaving F4
        // shipped. `pending` on the table's contents is what has to stop the
        // second insert, and the DAO's conditional INSERT is the last line.
        var table = names
        repeat(2) {
            val decision = EntourageRewards.forAllCasesVerdicted(shipped, table)
            val fresh = EntourageRewards.pending(decision, emptySet())
            fresh.forEach { table = table + it.nameEs }
        }
        assertEquals(
            "the table ends up with the badge exactly once",
            1,
            table.count { it == EntourageAchievement.TERPENE_ALCHEMIST.labelEs }
        )
    }

    /* ── It reuses the existing progression system ───────────────────────── */

    @Test
    fun theRewardProjectsThroughTheSameRowBuilderAsEveryOtherReward() {
        val shipped = cases()
        val reward = EntourageRewards
            .forAllCasesVerdicted(shipped, namesAfter(shipped))
            .single()
        val row = reward.toAchievementRow()

        assertEquals(reward.nameEs, row.name)
        assertEquals(reward.descriptionEs, row.description)
        assertEquals(reward.icon, row.icon)
        assertEquals(reward.xpReward, row.xpReward)
        assertTrue("the row has to be the unlock", row.isUnlocked)
    }

    @Test
    fun theOtherTwoBadgesAreUnaffectedByThisOne() {
        val processingIndex = content().processingIndex()
        val resin = EntourageRewards.forProcessingRead(
            processingIndex.documentedTerpenes,
            processingIndex
        ).single()
        assertEquals(
            EntourageAchievement.RESIN_ENGINEER.labelEs,
            resin.nameEs
        )
        assertEquals(
            "its text is still derived from the processing block, not the case list",
            EntourageAchievement.RESIN_ENGINEER.descriptionForProcessing(
                processingIndex.documentedTerpenes.size
            ),
            resin.descriptionEs
        )

        val quiz = EntourageRewards.forQuiz(8, 10, true).single()
        assertEquals(EntourageAchievement.ENTOURAGE_MASTER.labelEs, quiz.nameEs)
        assertEquals(
            EntourageAchievement.ENTOURAGE_MASTER.descriptionFor(10),
            quiz.descriptionEs
        )
    }

    @Test
    fun aCaseRowIsPaidOnceAndTheBadgeDoesNotDependOnTheOrderTheyWerePlayed() {
        val shipped = cases()

        // Played in reverse, still the same set.
        val reversed = shipped.reversed()
        assertTrue(
            EntourageRewards.forAllCasesVerdicted(shipped, namesAfter(reversed)).isNotEmpty()
        )

        val reward = EntourageRewards
            .forLabVerdict(shipped.first().id, shipped.first().titleEs, LabVerdict.OPTIMO)
            .single()
        assertTrue(
            "a replayed case pays nothing",
            EntourageRewards.pending(listOf(reward), setOf(reward.nameEs)).isEmpty()
        )
    }

    @Test
    fun theShippedCasesIncludeBothScoringModesSoTheBadgeSpansThem() {
        val shipped = cases()

        assertTrue(
            "the badge is only honest if it covers both modes",
            shipped.any { it.mode == com.trichome.app.model.LabMode.PHARMACOLOGICAL } &&
                shipped.any { it.mode == com.trichome.app.model.LabMode.HANDLING }
        )

        // And both actually pay a row, or one of them leaves a hole the badge can
        // never close.
        shipped.forEach { case ->
            val reward = if (case.mode == com.trichome.app.model.LabMode.HANDLING) {
                val selection = EntourageLabUi.selectionFromDials(
                    emptyMap(),
                    case.handlingCompounds
                )
                val best = ProcessingMethod.entries
                    .map { route -> EntourageLab.solve(case, selection, emptyList(), route) }
                    .minBy { it.verdict.ordinal }
                EntourageRewards.forLabVerdict(case.id, case.titleEs, best.verdict)
            } else {
                val profiles = content().profiles
                val goal = profiles.first { it.key == case.goal }
                val selection = EntourageLabUi.selectionFromDials(
                    goal.cannabinoidWeights.keys.associateWith {
                        case.maxCannabinoidShare[it] ?: 1f
                    },
                    goal.terpeneShares.keys
                )
                val solved = EntourageLab.solve(case, selection, profiles)
                EntourageRewards.forLabVerdict(case.id, case.titleEs, solved.verdict)
            }
            assertNotNull("${case.id} pays nothing when solved", reward.singleOrNull())
        }
    }
}
