package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Holds the Séquito module's presentation contract.
 *
 * Compose has no unit-test runtime on the `test` classpath — only `androidTest`
 * has one, and that needs a device — so everything the module's screens decide
 * about *what to say* lives in [EntouragePresentation] and is asserted here. What
 * this does NOT cover: the layout, the recomposition, and whether a slider
 * gesture reaches the state it should. Those remain device-only, and
 * `EntourageScrollOwnershipTest` covers the source shape instead.
 *
 * The three rules under test are the ones the module's KDocs promise and that
 * would decay silently: the evidence line is always on the card, the booster's
 * percentage never reads as a verdict, and the Lab never shows its own weights.
 */
class EntouragePresentationTest {

    /* ── Fixtures ─────────────────────────────────────────────────────────── */

    private fun synergy(
        id: String = "s1",
        cannabinoids: Set<Cannabinoid> = setOf(Cannabinoid.THC, Cannabinoid.CBD),
        terpenes: Set<EntourageTerpene> = setOf(EntourageTerpene.MYRCENE),
        profiles: Set<PharmacologicalProfile> = setOf(PharmacologicalProfile.ANSIOLYTIC),
        evidenceEs: String = "No hay evidencia humana que lo confirme.",
        strainsEs: List<String> = listOf("Amnesia", "Northern Lights"),
        interactionEs: String = "Consultá a un profesional si tomás medicación sedante."
    ) = EntourageSynergy(
        id = id,
        cannabinoids = cannabinoids,
        terpenes = terpenes,
        profiles = profiles,
        outcomeEs = "Sedación suave",
        descriptionEs = "Menos euforia y más calma.",
        mechanismEs = "Modulación alostérica de CB1.",
        evidenceEs = evidenceEs,
        strainsEs = strainsEs,
        interactionEs = interactionEs
    )

    private fun profile(
        key: PharmacologicalProfile = PharmacologicalProfile.SEDATIVE,
        shares: Map<EntourageTerpene, Float> = mapOf(
            EntourageTerpene.MYRCENE to 0.5f,
            EntourageTerpene.LINALOOL to 0.5f
        ),
        weights: Map<Cannabinoid, Float> = mapOf(Cannabinoid.THC to 1f)
    ) = EntourageProfile(
        key = key,
        labelEs = "Sedante",
        descriptionEs = "Perfil de sueño.",
        cannabinoidWeights = weights,
        terpeneShares = shares,
        noteEs = "Perfil de prueba, no una indicación médica.",
        evidence = ProfileEvidence.MIXTO
    )

    private fun vapour(terpene: EntourageTerpene, boiling: Int, min: Int, max: Int) =
        TerpeneVaporisation(
            terpene = terpene,
            boilingPointC = boiling,
            minTempC = min,
            maxTempC = max,
            noteEs = "Nota de $terpene."
        )

    /* ── T8.1: the evidence line ───────────────────────────────────────────── */

    @Test
    fun theCardAlwaysCarriesAnEvidenceLine() {
        val card = EntourageCards.cardFor(synergy())

        val evidence = card.linesEs.filter { it.role == EntourageCardRole.EVIDENCE }
        assertEquals("the card must have exactly one evidence line", 1, evidence.size)
        assertEquals("No hay evidencia humana que lo confirme.", evidence.first().bodyEs)
        assertTrue(card.evidenceWasDeclared)
    }

    @Test
    fun aSynergyThatDeclaredNoEvidenceStillRendersTheLine() {
        // The whole point of the rule. A blank `evidence_es` must produce a
        // *sentence*, not a missing block: silence reads as "nothing to add",
        // and only a sentence can say "this claim's support is undeclared".
        val card = EntourageCards.cardFor(synergy(evidenceEs = "   "))

        val evidence = card.linesEs.single { it.role == EntourageCardRole.EVIDENCE }
        assertEquals(EntourageSynergyCard.NO_EVIDENCE_DECLARED, evidence.bodyEs)
        assertFalse(card.evidenceWasDeclared)
    }

    @Test
    fun anAbsentInteractionNoteStaysAbsentRatherThanBecomingAWarning() {
        // `interactionEs` documents "no interaction to declare". Substituting a
        // "the catalog does not describe this point" line would put a warning on
        // every synergy that has nothing to warn about.
        val card = EntourageCards.cardFor(synergy(interactionEs = ""))

        assertEquals("", card.interactionEs)
        assertTrue(card.linesEs.none { it.role == EntourageCardRole.INTERACTION })
    }

    @Test
    fun theEvidenceLineIsNeverTheOnlyThingThatCouldBeSkipped() {
        // Evidence is rendered unconditionally, so it sits among the lines a card
        // always draws rather than among the optional ones.
        val card = EntourageCards.cardFor(synergy(strainsEs = emptyList(), interactionEs = ""))

        val roles = card.linesEs.map { it.role }
        assertEquals(
            listOf(
                EntourageCardRole.OUTCOME,
                EntourageCardRole.DESCRIPTION,
                EntourageCardRole.MECHANISM,
                EntourageCardRole.EVIDENCE
            ),
            roles
        )
    }

    @Test
    fun aPartlyAuthoredSynergyDegradesToNamedGapsRatherThanSilence() {
        val card = EntourageCards.cardFor(
            synergy().copy(outcomeEs = "", mechanismEs = "", descriptionEs = "")
        )

        assertEquals(EntourageSynergyCard.NOT_DECLARED, card.outcomeEs)
        assertEquals(EntourageSynergyCard.NOT_DECLARED, card.mechanismEs)
        assertTrue(card.allTextEs.isNotBlank())
    }

    @Test
    fun theOptionalLinesAppearOnlyWhenTheContentIsThere() {
        val bare = EntourageCards.cardFor(synergy().copy(strainsEs = emptyList()))
        assertTrue(bare.linesEs.none { it.role == EntourageCardRole.STRAINS })

        val full = EntourageCards.cardFor(synergy())
        assertTrue(full.linesEs.any { it.role == EntourageCardRole.STRAINS })
        assertTrue(full.linesEs.any { it.role == EntourageCardRole.INTERACTION })
    }

    @Test
    fun theCardNamesTheCombinationItIsDescribing() {
        val card = EntourageCards.cardFor(
            synergy(
                cannabinoids = setOf(Cannabinoid.CBD),
                terpenes = setOf(EntourageTerpene.LINALOOL)
            )
        )
        assertTrue(card.compoundsEs.contains("CBD"))
        assertTrue(card.compoundsEs.contains("Linalool"))
    }

    /* ── T8.5: the filter, resolved through the domain layer ──────────────── */

    @Test
    fun everyTerpeneResolvesFromItsEncyclopediaId() {
        // The join the detail screen depends on. If a `catalogId` drifts from
        // what `terpenes.json` actually ships, the button silently disappears.
        EntourageTerpene.entries.forEach { terpene ->
            assertEquals(
                "$terpene must resolve from its catalogId",
                terpene,
                EntourageFilters.terpeneForCatalogId(terpene.catalogId)
            )
        }
    }

    @Test
    fun theFilterKeepsOnlyTheSynergiesThatContainTheTerpene() {
        val library = listOf(
            synergy(id = "a", terpenes = setOf(EntourageTerpene.MYRCENE)),
            synergy(id = "b", terpenes = setOf(EntourageTerpene.LINALOOL)),
            synergy(id = "c", terpenes = setOf(EntourageTerpene.MYRCENE, EntourageTerpene.LINALOOL))
        )

        val shown = EntourageFilters.synergiesForTerpene(library, EntourageTerpene.MYRCENE)

        assertEquals(listOf("a", "c"), shown.map { it.id })
    }

    @Test
    fun noFilterShowsEverythingAndAnImpossibleFilterShowsNothing() {
        val library = listOf(
            synergy(id = "a", terpenes = setOf(EntourageTerpene.MYRCENE))
        )
        assertEquals(1, EntourageFilters.synergiesForTerpene(library, null).size)
        assertTrue(
            EntourageFilters.synergiesForTerpene(library, EntourageTerpene.HUMULENE).isEmpty()
        )
    }

    @Test
    fun theFilterOrderDoesNotDependOnTheAssetsOrder() {
        val forwards = listOf(
            synergy(id = "b", terpenes = setOf(EntourageTerpene.MYRCENE)),
            synergy(id = "a", terpenes = setOf(EntourageTerpene.MYRCENE))
        )
        val backwards = forwards.reversed()

        assertEquals(
            EntourageFilters.synergiesForTerpene(forwards, EntourageTerpene.MYRCENE).map { it.id },
            EntourageFilters.synergiesForTerpene(backwards, EntourageTerpene.MYRCENE).map { it.id }
        )
    }

    @Test
    fun anUnknownRouteArgumentResolvesToNoFilterRatherThanToACrash() {
        assertNull(EntourageFilters.terpeneForKey("NOT_A_TERPENE"))
        assertNull(EntourageFilters.terpeneForKey(null))
        assertNull(EntourageFilters.terpeneForKey("  "))
        assertEquals(EntourageTerpene.MYRCENE, EntourageFilters.terpeneForKey("MYRCENE"))
    }

    @Test
    fun onlyTerpenesSomeSynergyActuallyUsesAreOfferedAsFilters() {
        val library = listOf(
            synergy(id = "a", terpenes = setOf(EntourageTerpene.MYRCENE))
        )
        assertEquals(
            listOf(EntourageTerpene.MYRCENE),
            EntourageFilters.filterableTerpenes(library)
        )
    }

    /* ── T8.2: the score is a distance, never a verdict ───────────────────── */

    @Test
    fun anUnansweredScreenIsNotReportedAsZeroPercent() {
        val plan = EntouragePlanner.plan(EntourageSelection(), profile())
        val frame = EntourageBooster.frame(plan, profile())

        assertFalse("nothing selected is not a score", frame.reportable)
        assertEquals(EntourageMatchQuality.NO_SELECTION, plan.quality)
    }

    @Test
    fun aMissingCatalogIsNotReportedAsZeroPercentEither() {
        val plan = EntouragePlanner.plan(
            EntourageSelection(terpenes = setOf(EntourageTerpene.MYRCENE)),
            null
        )
        val frame = EntourageBooster.frame(plan, null)

        assertFalse(frame.reportable)
        assertEquals(EntourageMatchQuality.NO_REFERENCE, plan.quality)
    }

    @Test
    fun everyQualityBandIsPhrasedAsADistanceFromOneProfile() {
        val selection = EntourageSelection(
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.MYRCENE)
        )
        val plan = EntouragePlanner.plan(selection, profile())
        val frame = EntourageBooster.frame(plan, profile())

        assertTrue("a scored plan is reportable", frame.reportable)
        assertTrue(
            "the heading must name the profile the number is measured against",
            frame.headingEs.contains("Sedante")
        )
        assertTrue(
            "the caveat is what stops a bare percentage reading as a grade",
            frame.caveatEs.isNotBlank()
        )
        // The same selection is an excellent answer to a different target, so
        // the wording may not attach a value judgement to any band.
        assertTrue(EntourageLanguage.violations(frame.authoredTextEs).isEmpty())
    }

    @Test
    fun noBandAnywhereInTheBandTableCallsASelectionBadWeakOrUnsafe() {
        val profiles = PharmacologicalProfile.entries.map { key ->
            profile(key = key, shares = mapOf(EntourageTerpene.MYRCENE to 1f))
        }
        val selections = listOf(
            EntourageSelection(terpenes = setOf(EntourageTerpene.MYRCENE)),
            EntourageSelection(terpenes = setOf(EntourageTerpene.HUMULENE)),
            EntourageSelection(terpenes = emptySet())
        )

        profiles.forEach { target ->
            selections.forEach { selection ->
                val plan = EntouragePlanner.plan(selection, target)
                val frame = EntourageBooster.frame(plan, target)
                assertTrue(
                    "authored copy for quality ${plan.quality} against ${target.key} used " +
                        "${EntourageLanguage.violations(frame.authoredTextEs)}",
                    EntourageLanguage.violations(frame.authoredTextEs).isEmpty()
                )
            }
        }
    }

    @Test
    fun theGapLinesReportAProfileShareAndNeverACannabinoidContribution() {
        val selection = EntourageSelection(
            cannabinoids = setOf(Cannabinoid.CBD),
            terpenes = setOf(EntourageTerpene.MYRCENE)
        )
        val frame = EntourageBooster.frame(
            EntouragePlanner.plan(selection, profile()),
            profile()
        )

        // The profile weights THC, which the selection lacks. That is a presence
        // note — the weights are never summed into the percentage, so the line
        // cannot carry a share.
        assertEquals(listOf("Este perfil no incluye THC en sus compuestos."), frame.missingCannabinoidLinesEs)
        assertTrue(frame.missingCannabinoidLinesEs.none { it.contains("%") })
        assertTrue(frame.gapLinesEs.all { it.contains("El perfil pide") })
    }

    @Test
    fun aTerpeneTheProfileDoesNotUseIsReportedNeutrally() {
        val selection = EntourageSelection(terpenes = setOf(EntourageTerpene.HUMULENE))
        val frame = EntourageBooster.frame(
            EntouragePlanner.plan(selection, profile()),
            profile()
        )

        assertEquals(1, frame.offTargetLinesEs.size)
        assertTrue(EntourageLanguage.violations(frame.offTargetLinesEs.joinToString()).isEmpty())
    }

    @Test
    fun theScoreItselfComesFromThePlannerAndIsNotReDerived() {
        val selection = EntourageSelection(terpenes = setOf(EntourageTerpene.MYRCENE))
        val plan = EntouragePlanner.plan(selection, profile())
        assertEquals(plan.percent, EntourageBooster.frame(plan, profile()).percent)
    }

    /* ── Vapourisation ────────────────────────────────────────────────────── */

    @Test
    fun aSelectionThatFitsOnePassReportsThatWindow() {
        val report = EntourageBooster.vapourReport(
            setOf(EntourageTerpene.MYRCENE, EntourageTerpene.LINALOOL),
            listOf(
                vapour(EntourageTerpene.MYRCENE, boiling = 167, min = 150, max = 200),
                vapour(EntourageTerpene.LINALOOL, boiling = 198, min = 180, max = 220)
            )
        )

        assertEquals(2, report.rows.size)
        assertEquals("", report.contradictionEs)
        // The window is the highest minimum against the lowest maximum, never an
        // average of the two ranges.
        assertTrue(report.windowEs.contains("180") && report.windowEs.contains("200"))
    }

    @Test
    fun aSelectionThatCannotShareOnePassIsReportedAsTheContradictionItIs() {
        // Averaging 210..240 with 150..200 would print a number that reads as
        // advice and preserves neither compound.
        val report = EntourageBooster.vapourReport(
            setOf(EntourageTerpene.MYRCENE, EntourageTerpene.LINALOOL),
            listOf(
                vapour(EntourageTerpene.MYRCENE, boiling = 220, min = 210, max = 240),
                vapour(EntourageTerpene.LINALOOL, boiling = 167, min = 150, max = 200)
            )
        )

        assertNotNull(report.window)
        assertFalse(report.window!!.isViable)
        assertTrue(report.contradictionEs.isNotBlank())
        assertEquals("", report.windowEs)
    }

    @Test
    fun aSelectionWithNoTemperatureDataSaysSoRatherThanShowingNothing() {
        val report = EntourageBooster.vapourReport(setOf(EntourageTerpene.MYRCENE), emptyList())

        assertTrue(report.rows.isEmpty())
        assertTrue(report.missingEs.isNotBlank())
    }

    @Test
    fun theBoilingPointRowIsShownBecauseTheWindowAloneDoesNotExplainIt() {
        val report = EntourageBooster.vapourReport(
            setOf(EntourageTerpene.MYRCENE),
            listOf(vapour(EntourageTerpene.MYRCENE, boiling = 167, min = 150, max = 200))
        )
        assertEquals("167 °C", report.rows.single().boilingEs)
    }

    /* ── T8.3: the Lab ────────────────────────────────────────────────────── */

    @Test
    fun dialsAreReadAsSharesNotAsIndependentAmounts() {
        // Three dials at 60% each is 60/20/20 of a profile, not 180% of one.
        val selection = EntourageLabUi.selectionFromDials(
            mapOf(
                Cannabinoid.THC to 0.6f,
                Cannabinoid.CBD to 0.6f,
                Cannabinoid.CBN to 0.6f
            ),
            emptySet()
        )

        assertEquals(3, selection.cannabinoids.size)
        val total = selection.cannabinoids.sumOf { c -> selection.shareOf(c).toDouble() }
        assertEquals(1.0, total, 0.001)
    }

    @Test
    fun aDialAtZeroDropsTheCompoundAndItsShareWithIt() {
        val selection = EntourageLabUi.selectionFromDials(
            mapOf(Cannabinoid.THC to 0.5f, Cannabinoid.CBD to 0f),
            emptySet()
        )

        assertFalse(Cannabinoid.CBD in selection.cannabinoids)
        assertEquals(1.0f, selection.shareOf(Cannabinoid.THC), 0.001f)
    }

    @Test
    fun noCompoundAboveZeroIsNotAnOverBudgetSelection() {
        val selection = EntourageLabUi.selectionFromDials(
            mapOf(Cannabinoid.THC to 0f, Cannabinoid.CBD to 0f),
            setOf(EntourageTerpene.MYRCENE)
        )
        assertTrue(selection.cannabinoids.isEmpty())
        assertTrue(selection.shareOf(Cannabinoid.THC) == 0f)
    }

    @Test
    fun aDialValueTheSliderCannotProduceIsClamped() {
        assertEquals(1f, EntourageLabUi.clampDial(4.2f), 0f)
        assertEquals(0f, EntourageLabUi.clampDial(-4.2f), 0f)
        assertEquals(0.5f, EntourageLabUi.clampDial(0.5f), 0f)
    }

    @Test
    fun theLabOffersDialsForWhatTheCaseConstrainsAndAWholePanelOtherwise() {
        val constrained = EntourageCase(
            id = "c1",
            titleEs = "T",
            briefEs = "B",
            goal = PharmacologicalProfile.SEDATIVE,
            forbiddenCannabinoids = setOf(Cannabinoid.CBN),
            maxCannabinoidShare = mapOf(Cannabinoid.THC to 0.2f),
            explanationEs = "El caso descarta CBN."
        )
        assertEquals(
            listOf(Cannabinoid.CBN, Cannabinoid.THC),
            EntourageLabUi.dialCannabinoids(constrained)
        )
        assertFalse(Cannabinoid.CBD in EntourageLabUi.dialCannabinoids(constrained))

        val unconstrained = constrained.copy(
            forbiddenCannabinoids = emptySet(),
            maxCannabinoidShare = emptyMap()
        )
        assertEquals(
            Cannabinoid.entries.toList(),
            EntourageLabUi.dialCannabinoids(unconstrained)
        )
    }

    @Test
    fun theTerpeneListLeadsWithTheGoalProfileButDoesNotBoxThePlayerIn() {
        val order = EntourageLabUi.terpeneOrder(profile())
        assertEquals(EntourageTerpene.MYRCENE, order.first())
        assertEquals(EntourageTerpene.entries.size, order.size)
        assertEquals(EntourageTerpene.entries.size, order.toSet().size)
    }

    @Test
    fun theFeedbackCarriesAxesAndCeilingsAndNeverAWeight() {
        val case = EntourageCase(
            id = "c1",
            titleEs = "T",
            briefEs = "B",
            goal = PharmacologicalProfile.SEDATIVE,
            maxCannabinoidShare = mapOf(Cannabinoid.THC to 0.2f),
            ceilings = mapOf(LabAxis.SEDATION to 0.5f),
            explanationEs = "El paciente tolera poca sedación."
        )
        val selection = EntourageSelection(
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.MYRCENE, EntourageTerpene.LINALOOL),
            cannabinoidShares = mapOf(Cannabinoid.THC to 1f)
        )
        val result = EntourageLab.solve(case, selection, listOf(profile()))

        val feedback = EntourageLabUi.feedback(result)
        assertEquals(LabAxis.entries.size, feedback.axes.size)

        // The only numeric shape the feedback carries is a whole percent of a
        // load against a ceiling, both of which the case itself declares. A raw
        // puzzle weight would reach the screen as a decimal, and rounding to
        // whole percents is what makes that impossible to show by accident.
        feedback.axes.forEach { axis ->
            assertTrue("load must be a whole percent", axis.loadPercent in 0..100)
            assertTrue("ceiling must be a whole percent", axis.ceilingPercent in 0..100)
        }
        assertTrue(
            "a weight constant would reach the screen as a decimal: ${feedback.allTextEs}",
            !Regex("""\b0\.\d""").containsMatchIn(feedback.allTextEs)
        )
    }

    @Test
    fun everyVerdictIsPhrasedAboutTheCaseAndNotAboutTheProduct() {
        LabVerdict.entries.forEach { verdict ->
            val text = EntourageLabUi.verdictEs(verdict)
            assertTrue("$verdict must say something", text.isNotBlank())
            assertTrue(
                "$verdict used ${EntourageLanguage.violations(text)}",
                EntourageLanguage.violations(text).isEmpty()
            )
        }
    }

    /* ── T8.4: the quiz and the reward ────────────────────────────────────── */

    private fun questions(count: Int) = (1..count).map { index ->
        EntourageQuizQuestion(
            id = "q$index",
            promptEs = "P$index",
            optionsEs = listOf("a", "b", "c"),
            correctIndex = 0,
            explanationEs = "E$index",
            level = EntourageQuizLevel.AGRONOMO
        )
    }

    @Test
    fun anUnfinishedRunPaysNothingAndSaysSo() {
        val quiz = EntourageQuiz(questions(10), Random(7))
        val asking = EntourageQuizUi.outcomeOf(quiz.state)
        assertFalse(asking.finished)
        assertTrue(asking.rewards.isEmpty())

        quiz.answer(0)
        quiz.next()
        val revealed = EntourageQuizUi.outcomeOf(quiz.state)
        assertFalse(
            "a run abandoned mid-way is not a completed run",
            revealed.finished
        )
        assertTrue(revealed.rewards.isEmpty())
    }

    @Test
    fun aFinishedRunAtTheThresholdPaysTheModulesBadge() {
        val quiz = EntourageQuiz(questions(10), Random(7))
        repeat(10) {
            quiz.answer(currentCorrectIndex(quiz))
            quiz.next()
        }

        val outcome = EntourageQuizUi.outcomeOf(quiz.state)
        assertTrue(outcome.finished)
        assertEquals(1, outcome.rewards.size)
        assertEquals(
            EntourageAchievement.ENTOURAGE_MASTER.xpReward,
            outcome.rewards.single().xpReward
        )
        assertEquals(
            EntourageAchievement.ENTOURAGE_MASTER.labelEs,
            outcome.rewards.single().nameEs
        )
    }

    @Test
    fun aFinishedRunBelowTheThresholdPaysNothing() {
        val quiz = EntourageQuiz(questions(10), Random(7))
        // Seven correct is one short of the threshold for ten rounds, so the run
        // finishes and the badge stays locked.
        repeat(7) {
            quiz.answer(currentCorrectIndex(quiz))
            quiz.next()
        }
        repeat(3) {
            quiz.answer((currentCorrectIndex(quiz) + 1) % 3)
            quiz.next()
        }

        val outcome = EntourageQuizUi.outcomeOf(quiz.state)
        assertTrue(outcome.finished)
        assertEquals(7, outcome.score)
        assertFalse(outcome.unlockedBadge)
        assertTrue(outcome.rewards.isEmpty())
    }

    @Test
    fun anEmptyQuizIsUnavailableRatherThanAFinishedZero() {
        val outcome = EntourageQuizUi.outcomeOf(EntourageQuiz(emptyList()).state)
        assertFalse(outcome.finished)
        assertEquals(0, outcome.rounds)
        assertTrue(outcome.rewards.isEmpty())
    }

    @Test
    fun theOutcomePercentageIsRoundedFromTheRoundsPlayed() {
        val outcome = EntourageQuizUi.outcomeOf(EntourageQuizState.Finished(1, 3))
        assertEquals(33, outcome.correctPercent)
    }

    /** The answer index that is currently correct, read from the machine itself. */
    private fun currentCorrectIndex(quiz: EntourageQuiz): Int =
        (quiz.state as EntourageQuizState.Asking).question.correctIndex

    /* ── Rewards, and the one ledger there is ─────────────────────────────── */

    @Test
    fun anUnsolvedCasePaysNothing() {
        assertTrue(
            EntourageRewards.forLabVerdict("c1", "Caso", LabVerdict.INEFICAZ).isEmpty()
        )
    }

    @Test
    fun aSolvedCasePaysMoreThanOneThatCrossedACeiling() {
        val optimal = EntourageRewards.forLabVerdict("c1", "Caso", LabVerdict.OPTIMO).single()
        val viable = EntourageRewards.forLabVerdict("c1", "Caso", LabVerdict.VIABLE).single()
        val risk = EntourageRewards.forLabVerdict("c1", "Caso", LabVerdict.RIESGO).single()

        assertTrue(optimal.xpReward > viable.xpReward)
        assertTrue(viable.xpReward > risk.xpReward)
        assertTrue(optimal.xpReward > 0)
    }

    @Test
    fun aRewardIsPaidOnceAndOnlyOnce() {
        // The `achievements` table keys on an auto-generated id, so re-inserting
        // is a new row, not an update. Replaying a case has to be a no-op.
        val rewards = EntourageRewards.forLabVerdict("c1", "Caso", LabVerdict.OPTIMO)
        val awarded = rewards.map { it.nameEs }.toSet()

        assertTrue(EntourageRewards.pending(rewards, awarded).isEmpty())
        assertEquals(1, EntourageRewards.pending(rewards, emptySet()).size)
    }

    @Test
    fun twoDifferentCasesPayTwoDifferentRows() {
        val one = EntourageRewards.forLabVerdict("c1", "Insomnio", LabVerdict.VIABLE)
        val two = EntourageRewards.forLabVerdict("c2", "Dolor", LabVerdict.VIABLE)

        assertEquals(1, EntourageRewards.pending(one + two, one.map { it.nameEs }.toSet()).size)
    }

    @Test
    fun theBadgeRewardIsTheModulesOwnEnumEntryAndNotAFreshNumber() {
        val reward = EntourageRewards.forQuiz(8, 10, true).single()
        val achievement = EntourageAchievement.ENTOURAGE_MASTER

        assertEquals(achievement.labelEs, reward.nameEs)
        assertEquals(achievement.icon, reward.icon)
        assertEquals(achievement.xpReward, reward.xpReward)
        assertEquals(achievement.descriptionFor(10), reward.descriptionEs)
    }

    /* ── Data integrity ───────────────────────────────────────────────────── */

    @Test
    fun aCleanParseRendersNoIntegrityNotice() {
        assertNull(EntourageIntegrity.noticeFor(emptyList()))
    }

    @Test
    fun aTruncatedCatalogIsAdmittedRatherThanHidden() {
        val notice = EntourageIntegrity.noticeFor(
            listOf("synergies.s1 -> NOPE", "cases.c1 -> SEDATION")
        )

        assertNotNull(notice)
        assertEquals(2, notice!!.count)
        assertTrue(notice.headlineEs.contains("2"))
        // The detail is printed, because a count is not something the user can
        // act on.
        assertTrue(notice.detailEs.contains("synergies.s1"))
    }

    @Test
    fun aMissingDisclaimerIsStatedRatherThanRenderingAsNone() {
        val fallback = EntourageIntegrity.disclaimerFor("")
        assertTrue(fallback.isNotBlank())
        assertEquals(EntourageIntegrity.disclaimerFor(""), EntourageIntegrity.disclaimerFor("  "))
        assertEquals("Aviso real.", EntourageIntegrity.disclaimerFor("Aviso real."))
    }

    /* ── The language guard itself ────────────────────────────────────────── */

    @Test
    fun theGuardWouldActuallyCatchAForbiddenWord() {
        // A guard that cannot fail is not a guard.
        assertTrue(EntourageLanguage.violations("Esta mezcla es débil.").isNotEmpty())
        assertTrue(EntourageLanguage.violations("Selección insegura.").isNotEmpty())
        assertTrue(EntourageLanguage.violations("Un perfil PELIGROSO.").isNotEmpty())
        assertTrue(EntourageLanguage.violations("Coincide en parte.").isEmpty())
    }

    @Test
    fun theModuleCopyItselfPassesItsOwnGuard() {
        val authored = listOf(
            EntourageBooster.CAVEAT_ES,
            EntourageBooster.NO_REFERENCE_ES,
            EntourageSynergyCard.NO_EVIDENCE_DECLARED,
            EntourageSynergyCard.NOT_DECLARED
        ) + LabVerdict.entries.map { EntourageLabUi.verdictEs(it) }

        authored.forEach { text ->
            assertTrue(
                "authored copy used ${EntourageLanguage.violations(text)}: $text",
                EntourageLanguage.violations(text).isEmpty()
            )
        }
    }
}
