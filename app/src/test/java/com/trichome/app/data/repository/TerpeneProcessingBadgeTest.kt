package com.trichome.app.data.repository

import com.trichome.app.model.EntourageAchievement
import com.trichome.app.model.EntourageRewards
import com.trichome.app.model.EntourageTerpene
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F4 — the resin engineer badge.
 *
 * ## Why the third badge's text is a function and not a string
 *
 * F1 already removed one hardcoded badge sentence: the quiz badge used to read
 * "Acierta 8 de 10 preguntas" while sitting next to a `thresholdFor` that derived
 * the same 8 from `floor(rounds * 0.8)`. Those agreed only while the asset shipped
 * exactly ten questions. `descriptionFor(rounds)` fixed it.
 *
 * This badge has the same defect waiting in it, one layer over. Its condition is a
 * **count of the compounds the asset documents** in the `processing` block, so a
 * literal sentence beside it would drift the first time a compound is added to or
 * removed from `entourage_data.json` — and it would drift in the one place the
 * player cannot check it, because the row is written once into the `achievements`
 * table and then lives there forever.
 *
 * So the description is derived from the parsed content and there is deliberately
 * **no** no-argument `description` on this constant. The two badges do not derive
 * from the same data, so giving them a shared default reading would reintroduce
 * exactly the hardcoded copy F1 removed.
 *
 * ## What is not tested here
 *
 * That the row is persisted, or that XP is summed. Those need Room and a device.
 * What is asserted is the part that can go wrong silently: the text agreeing with
 * the file, the reward being payable exactly once, and the reward going through
 * the same table and projector as every other reward in the module.
 */
class TerpeneProcessingBadgeTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The shipped asset, read as the module reads it.
     *
     * `decodeFromString<EntourageBible>` with the explicit type is required: an
     * inferred one makes the compiler treat the string as a bare JSON literal and
     * fail with a decoding error about a numeric literal at offset 0. That is a
     * test-authoring bug, not a content bug, and it reads like one.
     */
    private fun content(): EntourageContent {
        val file = listOf(
            File("src/main/assets/data/entourage_data.json"),
            File("app/src/main/assets/data/entourage_data.json")
        ).first { it.isFile }
        return json.decodeFromString<EntourageBible>(String(file.readBytes(), Charsets.UTF_8))
            .toContent()
    }

    /* ── The text is derived from the asset ──────────────────────────────── */

    @Test
    fun theResinBadgeTextFollowsTheShippedProcessingBlock() {
        val documented = content().processing.size

        assertTrue(
            "the processing block has to document something for the badge to " +
                "describe",
            documented > 0
        )
        val text = EntourageAchievement.RESIN_ENGINEER.descriptionForProcessing(documented)

        assertTrue(
            "the badge text has to name the $documented compounds the asset " +
                "actually ships, or a player is told to read a number of compounds " +
                "that does not exist",
            text.contains("$documented")
        )
        assertTrue(
            "and it has to say what reading them means",
            text.contains("procesado")
        )
    }

    @Test
    fun theResinBadgeTextChangesWithTheBlockRatherThanSittingAsAFixedString() {
        // The property that makes the derivation worth having: two different
        // catalogues produce two different sentences. A literal would produce the
        // same one, and that is exactly how F1's bug happened.
        assertTrue(
            "the badge text must be a function of the data, not a constant",
            EntourageAchievement.RESIN_ENGINEER.descriptionForProcessing(10) !=
                EntourageAchievement.RESIN_ENGINEER.descriptionForProcessing(3)
        )
        assertEquals(
            "and it has to name whichever count it was given",
            EntourageAchievement.RESIN_ENGINEER.descriptionForProcessing(3),
            "Lee el procesado de los 3 compuestos que el catálogo documenta"
        )
    }

    @Test
    fun thePaidResinRewardDescribesTheBlockThatPaidIt() {
        val parsed = content()
        val index = parsed.processingIndex()
        val documented = index.documentedTerpenes

        val rewards = EntourageRewards.forProcessingRead(documented, index)

        assertEquals("a fully read block pays exactly one reward", 1, rewards.size)
        assertEquals(
            "the paid description has to name the compounds the asset ships",
            EntourageAchievement.RESIN_ENGINEER.descriptionForProcessing(documented.size),
            rewards.first().descriptionEs
        )
        assertEquals(
            "and the reward has to be the badge, by name, because the name is the " +
                "idempotency key against the achievements table",
            EntourageAchievement.RESIN_ENGINEER.labelEs,
            rewards.first().nameEs
        )
        assertEquals(
            EntourageAchievement.RESIN_ENGINEER.icon,
            rewards.first().icon
        )
        assertTrue("and it has to be worth something", rewards.first().xpReward > 0)
    }

    /* ── The condition ───────────────────────────────────────────────────── */

    @Test
    fun aPartiallyReadBlockPaysNothing() {
        val index = content().processingIndex()
        val documented = index.documentedTerpenes
        assertTrue("the fixture must document more than one compound", documented.size > 1)

        val partial = documented.toList().dropLast(1).toSet()

        assertTrue(
            "reading $partial of ${documented.size} compounds is not the badge",
            EntourageRewards.forProcessingRead(partial, index).isEmpty()
        )
    }

    @Test
    fun anEmptyBlockPaysNothing() {
        // A build whose asset lost the block must not award a badge claiming the
        // content is there. This is the reason the badge is F4's and could not be
        // shipped earlier.
        val index = com.trichome.app.model.EntourageProcessingIndex()

        assertTrue(
            "an empty processing block has nothing to have read",
            EntourageRewards.forProcessingRead(EntourageTerpene.entries.toSet(), index).isEmpty()
        )
    }

    @Test
    fun theRewardIsPaidExactlyOnce() {
        val index = content().processingIndex()
        val documented = index.documentedTerpenes
        val reward = EntourageRewards.forProcessingRead(documented, index)

        assertTrue(
            "a block read twice must not pay twice: the achievements table keys on " +
                "an auto-generated id, so a second insert would be a second row",
            EntourageRewards.pending(reward, awardedNames = setOf(reward.first().nameEs)).isEmpty()
        )
        assertEquals(
            "and it pays the first time",
            1,
            EntourageRewards.pending(reward, awardedNames = emptySet()).size
        )
    }

    /**
     * The rule the duplicate row broke.
     *
     * F4 shipped `Ingeniero de Resina` twice on the device, and the cause was not
     * in [EntourageRewards]: the caller claimed the names **after** awaiting the
     * insert, so a second evaluation between the decision and the claim paid again.
     *
     * This cannot be asserted on the ViewModel from the JVM — Compose and
     * `viewModelScope` are not on the `test` classpath — so what is pinned here is
     * the contract the caller has to honour, stated as a test over the pure
     * functions: [EntourageRewards.pending] is the **only** gate, so the caller must
     * treat its output as consumed the moment it is non-empty.
     */
    @Test
    fun theCallerHasToClaimTheNameTheMomentPendingReturnsIt() {
        val index = content().processingIndex()
        val documented = index.documentedTerpenes
        val first = EntourageRewards.forProcessingRead(documented, index)

        // The whole guarantee in one line: as long as the caller feeds the name back
        // in, the same live state pays once. There is no state inside the model, so
        // there is nothing else that could stop a second payment.
        var claimed: Set<String> = emptySet()
        repeat(5) { _ ->
            val fresh = EntourageRewards.pending(
                EntourageRewards.forProcessingRead(documented, index),
                claimed
            )
            if (fresh.isNotEmpty()) {
                claimed = claimed + fresh.map { it.nameEs }
                assertEquals(
                    "the model paid once",
                    1,
                    fresh.size
                )
            }
        }
        assertEquals("and only one name was ever claimed", 1, claimed.size)
        assertTrue(
            "the claimed name has to be the badge's own, because the name is the " +
                "idempotency key in the achievements table",
            claimed.contains(EntourageAchievement.RESIN_ENGINEER.labelEs)
        )
        assertEquals(1, first.size)
    }

    /* ── It reuses the existing progression system ──────────────────────── */

    @Test
    fun theRewardProjectsThroughTheSameRowBuilderAsEveryOtherReward() {
        // `EntourageReward.toAchievementRow` is the only path into the
        // `achievements` table, and it writes `isUnlocked = true` because the row
        // *is* the unlock. If F4 had introduced a second projector, the two could
        // disagree about how much is paid.
        val index = content().processingIndex()
        val reward = EntourageRewards.forProcessingRead(index.documentedTerpenes, index).first()
        val row = reward.toAchievementRow()

        assertEquals(reward.nameEs, row.name)
        assertEquals(reward.descriptionEs, row.description)
        assertEquals(reward.icon, row.icon)
        assertEquals(reward.xpReward, row.xpReward)
        assertTrue("the row has to be the unlock", row.isUnlocked)
    }

    @Test
    fun theBadgeIsTheThirdAndSharesTheBadgeEnumRatherThanASecondProgressionSystem() {
        // One enum, one table, one idempotency rule. A second progression system
        // would be a second source of truth for the same XP number, and the two
        // would drift.
        // Two badges in the module enum, not three. The module's Lab pays XP through
        // the same table but is not a badge, and `EntourageAchievement` holds only
        // what a screen names as a seal. The count is asserted so a future badge
        // cannot be added without this test arguing for it.
        assertEquals(
            "the module ships exactly two badges in this enum: the quiz seal and " +
                "F4's resin engineer seal",
            2,
            EntourageAchievement.entries.size
        )
        assertEquals(
            "every badge label has to be distinct, since the label is the " +
                "idempotency key in the achievements table",
            EntourageAchievement.entries.size,
            EntourageAchievement.entries.map { it.labelEs }.toSet().size
        )
        assertTrue(
            "no badge may pay zero XP: the table sums xpReward over unlocked rows",
            EntourageAchievement.entries.all { it.xpReward > 0 }
        )
        assertEquals(
            "the two existing badges keep their identity, so a save written before " +
                "F4 still resolves",
            setOf("Maestro del Efecto Séquito", "Ingeniero de Resina"),
            setOf(
                EntourageAchievement.ENTOURAGE_MASTER.labelEs,
                EntourageAchievement.RESIN_ENGINEER.labelEs
            )
        )
    }

    @Test
    fun theQuizBadgeIsUnaffectedByTheNewOne() {
        // F3's regression guard in reverse: adding a badge with a different data
        // source must not change what the quiz pays.
        val rewards = EntourageRewards.forQuiz(
            score = EntourageAchievement.thresholdFor(10),
            rounds = 10,
            finished = true
        )

        assertEquals(1, rewards.size)
        assertEquals(
            EntourageAchievement.ENTOURAGE_MASTER.labelEs,
            rewards.first().nameEs
        )
        assertEquals(
            EntourageAchievement.ENTOURAGE_MASTER.descriptionFor(10),
            rewards.first().descriptionEs
        )
    }

    @Test
    fun theQuizBadgeStillResolvesTheBadgeRowFromTheRunThatPaidIt() {
        // The **badge** projection, not the reward one. They differ in one field and
        // it matters: `EntourageReward.toAchievementRow` writes `isUnlocked = true`
        // because the row *is* the unlock, while `EntourageAchievement
        // .toAchievementRow(rounds)` writes it locked and lets the caller flip it.
        // Reaching the wrong one here would have passed silently.
        val rounds = 7
        val rewards = EntourageRewards.forQuiz(
            score = EntourageAchievement.thresholdFor(rounds),
            rounds = rounds,
            finished = true
        )
        val badgeRow = EntourageAchievement.ENTOURAGE_MASTER.toAchievementRow(rounds)
        val rewardRow = rewards.first().toAchievementRow()

        assertEquals(
            "the badge projection has to be built from the run that paid it",
            EntourageAchievement.ENTOURAGE_MASTER.descriptionFor(rounds),
            badgeRow.description
        )
        assertFalse(
            "the badge projection is written locked and the caller decides",
            badgeRow.isUnlocked
        )
        assertTrue(
            "the reward projection is the unlock itself; the two must not be " +
                "confused",
            rewardRow.isUnlocked
        )
    }

    @Test
    fun theBadgeProjectionRefusesTheResinRowRatherThanWritingAQuizSentence() {
        // The compile-time argument for the overload split: the resin row cannot be
        // built from a round count, so this function errors loudly rather than
        // writing "Acierta N de M preguntas" into the resin engineer's row.
        val thrown = runCatching {
            EntourageAchievement.RESIN_ENGINEER.toAchievementRow(10)
        }.exceptionOrNull()

        assertNotNull(
            "writing the resin badge from a round count would put a quiz sentence " +
                "in a row that has to describe the processing block",
            thrown
        )
    }
}