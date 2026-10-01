package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The "Cambiar de carpa" dialog: which choice maps to which policy, and what it says.
 *
 * ## What this covers
 *
 * The dialog's own mapping layer — [PlantMigrationPolicyOption] to [PlantMigrationPolicy]
 * to [PlantMigrationChoices] — plus the copy that has to be *visible* rather than
 * implied. Four things are load-bearing and each has failed before in some form:
 *
 *  1. **an untouched dialog has a defined outcome.** The dialog this replaces had a
 *     checkbox whose unchecked state had no branch, so the write landed on whichever
 *     came first. A default that resolves to `ADAPT_TO_DESTINATION_TENT` and to a real
 *     photoperiod is asserted here.
 *  2. **every policy survives the round trip** through the option enum. A missing `when`
 *     branch or a policy with no option would make one of the three choices write
 *     something other than what its label says.
 *  3. **the journal is never a choice.** `PlantMigrationChoices.keepJournalRecords` is
 *     asserted `true` for all three options, and the copy states that journal records
 *     are keyed by `plantId` and so are untouched by a tent move.
 *  4. **the no-destination-config case is loud.** `requiresDestinationConfigWrite` must
 *     produce a warning, and the 18/6 safe default must be named as such rather than
 *     appearing as a number nobody chose.
 *
 * ## What this does NOT cover
 *
 * That the dialog renders, that confirming writes through Room, or that a failed write
 * is reported. Only `androidTest` on a device can assert the last two; the write path
 * itself is a repository call the ViewModel owns.
 */
class PlantMigrationDialogTest {

    private val now = 1_787_000_000_000L
    private val historyAnchor = 1_700_000_000_000L

    private val plant = PlantCycleState(
        plantId = 11L,
        plantName = "Blue Dream",
        photoperiod = PhotoperiodConfig(12, 12),
        cycleStartAt = historyAnchor
    )

    private val tentWithConfig = TentCycleState(
        tentId = 4L,
        tentName = "Carpa 4",
        photoperiod = PhotoperiodConfig(13, 13)
    )

    /** A tent nobody has configured yet: `getConfigByTent` returns null for it. */
    private val tentWithoutConfig = TentCycleState(
        tentId = 7L,
        tentName = "Carpa 7",
        photoperiod = null
    )

    /* ── Choice → policy mapping ──────────────────────────────────── */

    @Test
    fun anUntouchedDialogMeansAdaptToTheDestinationTent() {
        val state = PlantMigrationDialogState()

        assertEquals(
            PlantMigrationPolicyOption.ADAPT_TO_TENT,
            state.selected
        )
        assertEquals(
            PhotoperiodPolicy.ADAPT_TO_DESTINATION_TENT,
            state.toChoices().photoperiodPolicy
        )
    }

    @Test
    fun everyPolicyHasExactlyOneOptionAndSurvivesTheRoundTrip() {
        val policies = PhotoperiodPolicy.entries
        val options = PlantMigrationPolicyOption.ALL.map { it.policy }

        assertEquals(
            "every engine policy needs a dialog option, or a choice is unreachable",
            policies.toSet(),
            options.toSet()
        )
        policies.forEach { policy ->
            assertEquals(
                "policy ${policy.name} does not survive the round trip",
                policy,
                PlantMigrationPolicyOption.forPolicy(policy).policy
            )
        }
    }

    @Test
    fun exactlyOneOptionIsTheDefault() {
        assertEquals(
            "an untouched dialog must have one defined outcome, not a tie",
            1,
            PlantMigrationPolicyOption.ALL.count { it.isDefault }
        )
        assertEquals(
            PlantMigrationPolicyOption.DEFAULT,
            PlantMigrationPolicyOption.ALL.first { it.isDefault }
        )
    }

    @Test
    fun selectingAnOptionResolvesToThatOptionsPolicy() {
        PlantMigrationPolicyOption.ALL.forEach { option ->
            val state = PlantMigrationDialogState().withPolicy(option.policy)
            assertEquals(option, state.selected)
            assertEquals(option.policy, state.toChoices().photoperiodPolicy)
        }
    }

    @Test
    fun everyOptionIsLabelledInSpanish() {
        PlantMigrationPolicyOption.ALL.forEach { option ->
            assertTrue("${option.name} has no label", option.labelEs.isNotBlank())
            assertTrue("${option.name} has no description", option.descriptionEs.isNotBlank())
        }
    }

    @Test
    fun theJournalIsNeverAChoice() {
        // `GrowEvent` is keyed by `plantId` with a CASCADE FK to `plants`; the tent is
        // not in that relationship, so `assignPlantToTent()` cannot drop a row. If this
        // ever became false, the dialog would be lying.
        PlantMigrationPolicyOption.ALL.forEach { option ->
            val choices = PlantMigrationDialogState().withPolicy(option.policy).toChoices()
            assertTrue(
                "${option.name} must never offer to drop the journal",
                choices.keepJournalRecords
            )
        }
        // And the other half of the contract: asking to drop it is refused upstream
        // rather than honoured later by code that does not know about grow_events.
        val rejected = PlantMigrationPlanner.plan(
            current = plant,
            destination = tentWithConfig,
            choices = PlantMigrationChoices(keepJournalRecords = false),
            nowMillis = now
        )
        assertEquals(MigrationResolution.REJECTED, rejected.resolution)
    }

    /* ── The plan each choice produces ────────────────────────────── */

    @Test
    fun adaptToTentTakesTheDestinationsHours() {
        val plan = PlantMigrationDialogState().planFor(plant, tentWithConfig, now)

        assertTrue(plan.isApplied)
        assertEquals(PhotoperiodSource.DESTINATION_TENT, plan.source)
        assertEquals(13, plan.lightHours)
        assertEquals(13, plan.darkHours)
    }

    @Test
    fun keepPreviousKeepsThePlantsHours() {
        val plan = PlantMigrationDialogState()
            .withPolicy(PhotoperiodPolicy.KEEP_PREVIOUS)
            .planFor(plant, tentWithConfig, now)

        assertEquals(PhotoperiodSource.PREVIOUS_PLANT, plan.source)
        assertEquals(12, plan.lightHours)
        assertEquals(12, plan.darkHours)
    }

    @Test
    fun keepCycleAnchorTakesTheTentsHoursButThePlantsAnchor() {
        val plan = PlantMigrationDialogState()
            .withPolicy(PhotoperiodPolicy.KEEP_PREVIOUS_CYCLE_ANCHOR)
            .copy(resetCycleStartToNow = false)
            .planFor(plant, tentWithConfig, now)

        assertEquals(PhotoperiodSource.DESTINATION_TENT, plan.source)
        assertEquals(13, plan.lightHours)
        assertEquals(historyAnchor, plan.cycleStartAt)
    }

    @Test
    fun resettingTheAnchorUsesNow() {
        val plan = PlantMigrationDialogState()
            .copy(resetCycleStartToNow = true)
            .planFor(plant, tentWithConfig, now)

        assertEquals(now, plan.cycleStartAt)
    }

    /* ── The no-destination-config case ───────────────────────────── */

    @Test
    fun aTentWithNoConfigIsFlaggedSoTheUiMustShowIt() {
        val plan = PlantMigrationDialogState().planFor(plant, tentWithoutConfig, now)

        assertTrue(
            "the tent has no config row, so one has to be written",
            plan.requiresDestinationConfigWrite
        )
        assertTrue(plan.isApplied)
        // The plant keeps its own hours rather than being silently moved to 18/6.
        assertEquals(PhotoperiodSource.PREVIOUS_PLANT, plan.source)
        assertEquals(12, plan.lightHours)
    }

    @Test
    fun aTentWithAConfigIsNotFlagged() {
        val plan = PlantMigrationDialogState().planFor(plant, tentWithConfig, now)

        assertFalse(
            "the tent already owns a config, so nothing has to be written",
            plan.requiresDestinationConfigWrite
        )
        assertNull(
            "no warning may be shown for a configured tent",
            PlantMigrationDialogCopy.destinationConfigWarningEs(plan, tentWithConfig.tentName)
        )
    }

    @Test
    fun theNoConfigWarningNamesTheSafeDefaultInsteadOfQuietlyWritingIt() {
        // A plant with no photoperiod of its own, into a tent with none: the plan
        // substitutes 18/6, and that number would then drive every future plant added
        // to that tent. The copy has to say it.
        val bare = plant.copy(photoperiod = null)
        val plan = PlantMigrationDialogState().planFor(bare, tentWithoutConfig, now)

        assertEquals(PhotoperiodSource.SAFE_DEFAULT, plan.source)
        assertTrue(plan.requiresDestinationConfigWrite)

        val warning = PlantMigrationDialogCopy.destinationConfigWarningEs(plan, "Carpa 7")
        assertNotNull("the 18/6 substitution must be visible", warning)
        val text = warning!!
        assertTrue(
            "the warning must name the tent: $text",
            text.contains("Carpa 7")
        )
        assertTrue(
            "the warning must say the value is substituted, not chosen: $text",
            text.contains("valor seguro")
        )
        assertTrue(
            "the warning must state the hours: $text",
            text.contains("18 h") && text.contains("6 h")
        )
        assertTrue(
            "the warning must say it will be saved for future plants: $text",
            text.contains("guardará en la carpa")
        )
    }

    @Test
    fun theNoConfigWarningDistinguishesACarryOverFromASubstitution() {
        // Two different things: the plant's own hours carried across (nothing is
        // invented) versus 18/6 that nobody typed. One sentence for both would hide
        // the second case.
        val carried = PlantMigrationDialogState().planFor(plant, tentWithoutConfig, now)
        val substituted = PlantMigrationDialogState()
            .planFor(plant.copy(photoperiod = null), tentWithoutConfig, now)

        val carriedText = PlantMigrationDialogCopy.destinationConfigWarningEs(carried, "Carpa 7")!!
        val substitutedText = PlantMigrationDialogCopy.destinationConfigWarningEs(substituted, "Carpa 7")!!

        assertTrue(
            "the carry-over case must state the hours it is creating the config with",
            carriedText.contains("12 h luz")
        )
        assertFalse(
            "the carry-over case must not claim the safe default was substituted",
            carriedText.contains("valor seguro")
        )
        assertTrue(
            "the two warnings must not be the same sentence",
            carriedText != substitutedText
        )
    }

    @Test
    fun theNoConfigWarningIsPresentForEveryPolicy() {
        // Whichever branch the grower picks, moving into an unconfigured tent writes a
        // config row. Missing it on one policy would silently leave that tent empty.
        PlantMigrationPolicyOption.ALL.forEach { option ->
            val state = PlantMigrationDialogState().withPolicy(option.policy)
            val plan = state.planFor(plant, tentWithoutConfig, now)
            if (plan.requiresDestinationConfigWrite) {
                assertNotNull(
                    "${option.name} writes a destination config but shows no warning",
                    PlantMigrationDialogCopy.destinationConfigWarningEs(plan, "Carpa 7")
                )
            }
        }
    }

    /* ── The dialog's copy ────────────────────────────────────────── */

    @Test
    fun theJournalNoticeIsPresentAndStatesTheKeying() {
        val plan = PlantMigrationDialogState().planFor(plant, tentWithConfig, now)
        val content = PlantMigrationDialogCopy.contentFor(plan, "Blue Dream", "Carpa 4")

        assertTrue(
            "the dialog must state the journal is kept",
            content.journalNoticeEs.isNotBlank()
        )
        assertTrue(
            "the notice must explain why: keyed to the plant, not the tent",
            content.journalNoticeEs.contains("planta")
        )
    }

    @Test
    fun theReasonIsThePlannersOwnSentenceNotARewording() {
        // The copy cannot disagree with the write because both read the same object.
        val plan = PlantMigrationDialogState().planFor(plant, tentWithConfig, now)
        val content = PlantMigrationDialogCopy.contentFor(plan, "Blue Dream", "Carpa 4")

        assertEquals(plan.reasonEs, content.reasonEs)
    }

    @Test
    fun theTitleNamesThePlantAndTheConfirmButtonNamesTheTent() {
        val plan = PlantMigrationDialogState().planFor(plant, tentWithConfig, now)
        val content = PlantMigrationDialogCopy.contentFor(plan, "Blue Dream", "Carpa 4")

        assertTrue(
            "the title must name the plant: ${content.titleEs}",
            content.titleEs.contains("Blue Dream")
        )
        assertTrue(
            "the confirm button must name the destination: ${content.confirmLabelEs}",
            content.confirmLabelEs.contains("Carpa 4")
        )
        assertEquals("Cancelar", content.cancelLabelEs)
    }

    @Test
    fun thePhotoperiodLineShowsTheChangeAndNotAFalseOne() {
        val changed = PlantMigrationDialogState().planFor(plant, tentWithConfig, now)
        val changedText = PlantMigrationDialogCopy.photoperiodChangeEs(changed)
        assertNotNull("12/12 -> 13/13 is a change and must be shown", changedText)
        assertTrue(changedText!!.contains("12 h luz") && changedText.contains("13 h luz"))

        // Same numbers on both sides: "12/12 -> 12/12" reads as a change and is not one.
        val unchanged = PlantMigrationDialogState()
            .withPolicy(PhotoperiodPolicy.KEEP_PREVIOUS)
            .planFor(plant, tentWithConfig, now)
        assertNull(
            "an unchanged photoperiod must not be shown as changing",
            PlantMigrationDialogCopy.photoperiodChangeEs(unchanged)
        )
    }

    @Test
    fun theUnchangedCaseStillSaysWhatThePlantWillRun() {
        val unchanged = PlantMigrationDialogState()
            .withPolicy(PhotoperiodPolicy.KEEP_PREVIOUS)
            .planFor(plant, tentWithConfig, now)
        val content = PlantMigrationDialogCopy.contentFor(unchanged, "Blue Dream", "Carpa 4")

        assertFalse(
            "the fallback line must not claim a change: ${content.photoperiodChangeEs}",
            content.photoperiodChangeEs.startsWith("De ")
        )
        assertTrue(
            "the fallback line must still state the hours: ${content.photoperiodChangeEs}",
            content.photoperiodChangeEs.contains("12 h luz")
        )
    }

    @Test
    fun anAppliedPlanIsApplicableAndARejectedOneIsNot() {
        val applied = PlantMigrationDialogState().planFor(plant, tentWithConfig, now)
        assertTrue(
            PlantMigrationDialogCopy.contentFor(applied, "Blue Dream", "Carpa 4").canApply
        )

        // The rejection the dialog itself cannot produce, pinned so a future choice
        // cannot slip past the button.
        val rejected = PlantMigrationPlanner.plan(
            current = plant,
            destination = tentWithConfig,
            choices = PlantMigrationChoices(keepJournalRecords = false),
            nowMillis = now
        )
        val content = PlantMigrationDialogCopy.contentFor(rejected, "Blue Dream", "Carpa 4")
        assertFalse(
            "a rejected plan has no photoperiod; the button must be disabled",
            content.canApply
        )
        assertEquals("Cambiar de carpa", content.titleEs.substringBefore(" · "))
    }

    /* ── A rejected plan still has to render something ────────────── */

    @Test
    fun aRejectedPlanRendersItsReasonRatherThanCrashing() {
        val rejected = PlantMigrationPlanner.plan(
            current = plant,
            destination = tentWithConfig,
            choices = PlantMigrationChoices(keepJournalRecords = false),
            nowMillis = now
        )
        val content = PlantMigrationDialogCopy.contentFor(rejected, "Blue Dream", "Carpa 4")

        assertTrue("the rejection reason must reach the UI", content.reasonEs.isNotBlank())
        assertTrue(
            "the rejection must be explained in terms of grow_events",
            content.reasonEs.contains("grow_events")
        )
        // `photoperiodChangeEs` has to survive a null photoperiod without NPE-ing.
        assertTrue(content.photoperiodChangeEs.isNotBlank())
    }

    /* ── Source shape ─────────────────────────────────────────────── */

    @Test
    fun theDialogSourceScrollsExactlyOnce() {
        // `ScrollOwnershipTest` exists because a scrollable inside another scrollable
        // measures its child with an infinite maximum height and throws on a tap. The
        // dialog body is the only scroll; nothing mounted inside it may scroll again.
        val code = dialogSource()

        val scrolls = Regex("""\.verticalScroll\(""").findAll(code).count()
        assertEquals(
            "the migration dialog must own exactly one vertical scroll: found $scrolls",
            1,
            scrolls
        )
    }

    @Test
    fun theDialogSourceNeverPublishesAnUnspecifiedContentColour() {
        // `Surface` publishes whatever it is handed into `LocalContentColor`, so an
        // `Unspecified` that reaches it paints the subtree black on a dark panel. That
        // shipped once, as a component bug, and is checked against the source here
        // because the alternative is eight call-site patches.
        val code = dialogSource()
        val offenders = Regex("""SolidPanel\([^)]*contentColor\s*=\s*Color\.Unspecified""")
            .findAll(code)
            .count()
        assertEquals(
            "SolidPanel must never be given Color.Unspecified directly",
            0,
            offenders
        )
        assertFalse(
            "the dialog must not hardcode a colour literal",
            Regex("""Color\(0x""").containsMatchIn(code)
        )
    }

    @Test
    fun theDialogSourceConfirmsThroughThePlanNotItsOwnLogic() {
        // The decision is the tested pure function. A dialog that recomputed the
        // photoperiod itself would be a second implementation of the same rule, which is
        // exactly how the copy and the write came to disagree before.
        val code = dialogSource()
        assertTrue(
            "the dialog must resolve its outcome through the planner",
            code.contains("planFor(")
        )
        assertTrue(
            "the dialog must not recompute a photoperiod itself",
            !Regex("""when\s*\(.*photoperiod""").containsMatchIn(code)
        )
    }

    /** The dialog's source, with comments stripped so this file's prose cannot match. */
    private fun dialogSource(): String {
        val candidates = listOf(
            File("src/main/java/com/trichome/app/ui/screens/plant/PlantMigrationDialog.kt"),
            File("app/src/main/java/com/trichome/app/ui/screens/plant/PlantMigrationDialog.kt")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("PlantMigrationDialog.kt is not in the plant package")
        return file.readText(Charsets.UTF_8)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""(?m)//.*$"""), " ")
    }
}