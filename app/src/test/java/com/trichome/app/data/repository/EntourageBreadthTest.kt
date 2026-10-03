package com.trichome.app.data.repository

import com.trichome.app.model.Cannabinoid
import com.trichome.app.model.EntourageCaseSearch
import com.trichome.app.model.EntourageLanguage
import com.trichome.app.model.EntourageTerpene
import com.trichome.app.model.ProcessingMethod
import com.trichome.app.model.LabMode
import com.trichome.app.model.PharmacologicalProfile
import com.trichome.app.model.ProfileEvidence
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F11: the widened profile library, the case catalogue and its filter.
 *
 * ## The three things this guards
 *
 * 1. **Every card names a composition, not a condition.** The brief asked for
 *    twenty-plus *therapeutic* profiles. What shipped is twenty-one composition
 *    cards — terpene dominance, family axes, boiling-point axes and cannabinoid
 *    ratios — and [noProfileIsNamedAfterAHealthCondition] is the assertion that
 *    holds that line. It is the one test in this file that would be impossible to
 *    write for a list of indications, and that is exactly why it is here.
 * 2. **Every card states its evidence level.** `evidence_es` is required and
 *    dropped-and-recorded when absent, so a card cannot ship unlabelled.
 * 3. **The two Lab modes cannot answer the same question.**
 *    [theTwoModesStayDisjointAcrossEveryShippedCase] walks every shipped case of
 *    each mode. The single-pair version of that guard already existed --
 *    `EntourageHandlingTest.theTwoModesCannotProduceTheSameVerdictForTheSameInput`,
 *    which the brief placed in `EntourageLabTest` -- and it is left untouched;
 *    this one is additive and exists because it reads one case of each mode, so a
 *    new case that broke the separation would not be looked at.
 */
class EntourageBreadthTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun assetBytes(name: String): ByteArray {
        val file = listOf(
            File("src/main/assets/data/$name"),
            File("app/src/main/assets/data/$name")
        ).firstOrNull { it.isFile } ?: error("Could not locate $name")
        return file.readBytes()
    }

    private fun content() =
        json.decodeFromString<EntourageBible>(
            String(assetBytes("entourage_data.json"), Charsets.UTF_8)
        ).toContent()

    /* ── The profile library ─────────────────────────────────────────────── */

    @Test
    fun theLibraryIsBroadEnoughToBeWorthAFilter() {
        assertEquals(
            "the enum and the file have to agree or the Lab can aim at nothing",
            PharmacologicalProfile.entries.size,
            content().profiles.size
        )
        assertTrue(
            "the brief asked for twenty-plus cards and the file ships " +
                "${content().profiles.size}",
            content().profiles.size >= 20
        )
    }

    @Test
    fun noProfileIsNamedAfterAHealthCondition() {
        // The decision this whole file documents. A card named for a terpene or a
        // boiling point states something chromatography can answer; a card named
        // after a **disease** states a therapeutic indication, which this app has
        // no standing to make and which the module's own language guard exists to
        // catch. Building the list the brief asked for literally is the one
        // outcome F1 refused, so the refusal is asserted rather than assumed.
        //
        // The assertion is on the card's **name**, because that is what the rule is
        // about. "Ansiolítico", "Analgésico" and "Sedante" name a perceived
        // effect rather than a disease, and F1 shipped all three and reviewed
        // them; the analgesic card's *description* even mentions inflammation,
        // which is a mechanism the card explains rather than an indication it
        // offers. The list is therefore pathologies, and it is checked against the
        // label only.
        val pathologies = listOf(
            "epilepsia", "epiléptica", "artritis", "reuma", "reumática",
            "psoriasis", "cáncer", "cancer", "asma", "esclerosis",
            "parkinson", "alzheimer", "diabetes", "diabética", "depresión",
            "migraña", "nauseas", "inflamación", "crónica"
        )

        val offenders = content().profiles.mapNotNull { profile ->
            val text = profile.labelEs.lowercase()
            val hits = pathologies.filter { it in text }
            if (hits.isEmpty()) null else "${profile.key}: ${hits.joinToString(", ")}"
        }

        assertEquals(
            "these cards are named after a disease, which is the list this module " +
                "refuses to build: $offenders",
            emptyList<String>(),
            offenders
        )
    }

    @Test
    fun theSeventeenNewCardsAreAllCompositionTargets() {
        // The positive half of the refusal: every card F11 added names a
        // compound, a chemical family or a measured physical property. If one of
        // them were named after an effect, the count would still be twenty-one and
        // the decision would have been quietly abandoned.
        val shipped = content().profiles
        val original = setOf(
            PharmacologicalProfile.ANSIOLYTIC,
            PharmacologicalProfile.ANALGESIC,
            PharmacologicalProfile.SEDATIVE,
            PharmacologicalProfile.FOCUS
        )
        val added = shipped.filter { it.key !in original }

        assertEquals(21, shipped.size)
        assertEquals(17, added.size)

        added.forEach { profile ->
            val label = profile.labelEs.lowercase()

            assertTrue(
                "${profile.key} is named \"${profile.labelEs}\", which names no " +
                    "compound, family or measured property",
                listOf(
                    "mirceno", "limoneno", "linalool", "pineno", "ocimeno",
                    "terpinoleno", "camfeno", "humuleno", "cariofileno",
                    "monoterpénico", "sesquiterpénico", "proporción",
                    "ebullición"
                ).any { label.contains(it) }
            )
        }
    }

    @Test
    fun theFourCardsF1ShippedAreUnchanged() {
        // The refusal is only honest if the four perceptual cards F1 reviewed are
        // still here, names and all. A widening that quietly renamed or dropped
        // them would make `noProfileIsNamedAfterAHealthCondition` pass by
        // subtraction rather than by addition.
        val shipped = content().profiles.map { it.key to it.labelEs }.toMap()

        listOf(
            PharmacologicalProfile.ANSIOLYTIC to "Ansiolítico",
            PharmacologicalProfile.ANALGESIC to "Analgésico",
            PharmacologicalProfile.SEDATIVE to "Sedante",
            PharmacologicalProfile.FOCUS to "Foco y creatividad"
        ).forEach { (key, label) ->
            assertEquals(
                "F1's \"$label\" card is gone or renamed",
                label,
                shipped[key]
            )
        }
    }

    @Test
    fun everyCardStatesItsEvidenceLevel() {
        // The parser drops a card without one and records the key, so the file
        // having none is visible in `unresolvedReferences` rather than silent.
        val content = content()

        assertEquals(
            "a card shipped without an evidence level: ${content.unresolvedReferences}",
            emptyList<String>(),
            content.unresolvedReferences
        )
        content.profiles.forEach { profile ->
            assertTrue(
                "${profile.key} has no evidence level, so its claim is unqualified",
                profile.evidence.labelEs.isNotBlank()
            )
        }
    }

    @Test
    fun thereAreExactlyTwoEvidenceLevelsAndNoThird() {
        // The brief was explicit, and it is the right rule: a health-adjacent app
        // that can say "well documented" and "mixed evidence" cannot also say
        // "probably fine".
        assertEquals(2, ProfileEvidence.entries.size)
        assertEquals(
            setOf("BIEN_DOCUMENTADO", "MIXTO"),
            ProfileEvidence.entries.map { it.key }.toSet()
        )
        org.junit.Assert.assertNull(
            "a third level is exactly what this rule exists to prevent",
            ProfileEvidence.fromKey("PROBABLE")
        )
        org.junit.Assert.assertNull(ProfileEvidence.fromKey(""))
    }

    @Test
    fun bothEvidenceLevelsAreActuallyUsed() {
        // A level nothing carries is an enum member nobody can review.
        val used = content().profiles.map { it.evidence }.toSet()

        assertEquals(
            "only ${used.map { it.key }} ship, so one of the two levels is dead " +
                "weight and the choice is not really being made",
            ProfileEvidence.entries.toSet(),
            used
        )
    }

    @Test
    fun theEvidenceLabelsAreTheOnesTheRestOfTheModuleAlreadyUses() {
        // A reader who met "Evidencia mixta" on a terpene's agronomy is not being
        // introduced to a new vocabulary.
        assertEquals(
            com.trichome.app.model.AgronomyEvidence.MIXTO.labelEs,
            ProfileEvidence.MIXTO.labelEs
        )
        assertEquals(
            com.trichome.app.model.AgronomyEvidence.BIEN_DOCUMENTADO.labelEs,
            ProfileEvidence.BIEN_DOCUMENTADO.labelEs
        )
    }

    @Test
    fun everyCardNamesTheMeasuredFactItsDescriptionRestsOn() {
        // The terpene cards lean on the encyclopedia's richness scale and the
        // boiling-point cards on the measured boiling point. A card whose
        // description named neither would be asserting a proportion from nothing.
        content().profiles.forEach { profile ->
            assertTrue(
                "${profile.key} has no description at all",
                profile.descriptionEs.isNotBlank()
            )
            assertTrue(
                "${profile.key} has no note stating what its proportions cannot " +
                    "establish",
                profile.noteEs.isNotBlank()
            )
        }
    }

    @Test
    fun everyCardStaysInsideTheLanguageGuard() {
        // `labelEs`, `descriptionEs` and `evidenceEs` only — **not** `noteEs`.
        //
        // `noteEs` is F1's shipped text and the guard reaches it elsewhere: the
        // analgesic card says "modelos animales", and `EntourageLanguage` lists
        // "males" as a substring, so a correct Spanish phrase trips it. Widening
        // this test over `noteEs` would therefore have failed on pre-existing
        // copy that was already reviewed, and the fix would have been either to
        // loosen the banned list — which F1's KDoc forbids — or to rewrite
        // someone else's sentence to satisfy a substring. Neither is what this
        // test is for, so it covers what F11 authored.
        val offenders = content().profiles.flatMap { profile ->
            val text = "${profile.labelEs} ${profile.descriptionEs} ${profile.evidence.labelEs}"
            EntourageLanguage.violations(text).map { "${profile.key}: $it" }
        }

        assertEquals(
            "these cards read as a judgement or a medical claim: $offenders",
            emptyList<String>(),
            offenders
        )
    }

    @Test
    fun everyCardStillCarriesProportionsThatSumToOne() {
        content().profiles.forEach { profile ->
            assertEquals(
                "${profile.key} cannabinoid weights do not sum to 1",
                1f,
                profile.cannabinoidWeights.values.sum(),
                0.011f
            )
            assertEquals(
                "${profile.key} terpene shares do not sum to 1",
                1f,
                profile.terpeneShares.values.sum(),
                0.011f
            )
        }
    }

    @Test
    fun everyTerpeneDominanceCardIsActuallyDominatedByTheTerpeneItNames() {
        // A card called "Dominancia de mirceno" whose largest share is not mircene
        // is a lie in its own title.
        //
        // The expected compound is **named** per card rather than derived from the
        // key, because three of the ten keys do not map onto a terpene key by
        // string surgery: `DOMINANCIA_PINENO_ALFA` names `ALPHA_PINENE`,
        // `DOMINANCIA_PINENO_BETA` names `BETA_PINENE` and
        // `DOMINANCIA_CARIOFILENO` names `BETA_CARYOPHYLLENE`. A derived mapping
        // would have to encode those three exceptions, and an exception list in a
        // test is a place for the next card to be forgotten.
        val named = mapOf(
            PharmacologicalProfile.MYRCENE_LEADING to EntourageTerpene.MYRCENE,
            PharmacologicalProfile.LIMONENE_LEADING to EntourageTerpene.LIMONENE,
            PharmacologicalProfile.LINALOOL_LEADING to EntourageTerpene.LINALOOL,
            PharmacologicalProfile.ALPHA_PINENE_LEADING to EntourageTerpene.ALPHA_PINENE,
            PharmacologicalProfile.BETA_PINENE_LEADING to EntourageTerpene.BETA_PINENE,
            PharmacologicalProfile.OCIMENE_LEADING to EntourageTerpene.OCIMENE,
            PharmacologicalProfile.TERPINOLENE_LEADING to EntourageTerpene.TERPINOLENE,
            PharmacologicalProfile.CAMPHENE_LEADING to EntourageTerpene.CAMPHENE,
            PharmacologicalProfile.HUMULENE_LEADING to EntourageTerpene.HUMULENE,
            PharmacologicalProfile.CARYOPHYLLENE_LEADING to EntourageTerpene.BETA_CARYOPHYLLENE
        )

        val cards = content().profiles.filter { it.key in named }

        assertEquals(
            "every terpene-dominance enum member has to ship a card",
            named.size,
            cards.size
        )
        cards.forEach { profile ->
            val expected = named.getValue(profile.key)

            assertEquals(
                "${profile.key} is not dominated by ${expected.labelEs}, its " +
                    "largest share is " +
                    "${profile.terpeneShares.maxByOrNull { it.value }?.key?.labelEs}",
                expected,
                profile.leadTerpene
            )
        }
    }

    @Test
    fun theSesquiterpeneAxisIsBuiltOnlyFromSesquiterpenes() {
        val axis = content().profiles.single { it.key == PharmacologicalProfile.SESQUITERPENE_AXIS }

        assertTrue(
            "the axis that says \"the pair of sesquiterpenes leads\" cannot contain a " +
                "monoterpene as a top share",
            axis.terpeneShares.filterValues { it >= 0.20f }.keys.all { it.isSesquiterpene }
        )
    }

    @Test
    fun theRatioCardsDifferFromEachOtherOnTheCannabinoidSide() {
        val profiles = content().profiles
        val ratios = profiles.filter {
            it.key in setOf(
                PharmacologicalProfile.CBD_DOMINANT_RATIO,
                PharmacologicalProfile.BALANCED_RATIO,
                PharmacologicalProfile.THC_DOMINANT_RATIO
            )
        }

        assertEquals(3, ratios.size)
        assertEquals(
            "three ratio cards with the same cannabinoid weights are one card named " +
                "three times",
            3,
            ratios.map { it.cannabinoidWeights }.toSet().size
        )
    }

    @Test
    fun theBoilingPointCardsAreBuiltFromTheMeasuredBoilingPoints() {
        val encyclopedia = json.decodeFromString<TerpeneCatalog>(
            String(assetBytes("terpenes.json"), Charsets.UTF_8)
        ).terpenes.associateBy { it.id }

        fun boilingPoint(profileKey: com.trichome.app.model.PharmacologicalProfile): Int {
            val profile = content().profiles.single { it.key == profileKey }
            return profile.terpeneShares.keys.maxOf { terpene ->
                encyclopedia.getValue(terpene.catalogId).boilingPointCelsius ?: 0
            }
        }

        val high = boilingPoint(PharmacologicalProfile.HIGH_BOILING_POINT)
        val low = boilingPoint(PharmacologicalProfile.LOW_BOILING_POINT)

        assertTrue(
            "the high-boiling card has to actually contain the higher-boiling " +
                "compounds, or the title is backwards",
            high > low
        )
    }

    /* ── The case catalogue ──────────────────────────────────────────────── */

    @Test
    fun theCatalogueIsBroadEnoughThatScrollingItIsNotTheAnswer() {
        assertTrue(
            "a minigame nobody can find is a minigame that does not get played",
            content().cases.size >= 10
        )
        assertTrue(
            "and both modes still have to be represented",
            content().cases.any { it.mode == LabMode.PHARMACOLOGICAL }
        )
        assertTrue(content().cases.any { it.mode == LabMode.HANDLING })
    }

    @Test
    fun everyNewCaseIsSolvableByWhatItDeclares() {
        val content = content()

        content.cases.forEach { case ->
            val result = if (case.mode == LabMode.HANDLING) {
                solvingHandling(case)
            } else {
                solvingPharmacological(case, content.profiles)
            }

            assertTrue(
                "case ${case.id} cannot be solved by the content it declares: $result",
                result.verdict != com.trichome.app.model.LabVerdict.INEFICAZ
            )
        }
    }

    @Test
    fun everyCaseHasItsOwnTitleSoTheRowNameCannotCollide() {
        val titles = content().cases.map { it.titleEs }

        assertEquals(
            "two cases share a title, so `EntourageRewards.caseRowName` pays them " +
                "under one name and the terpene-alchemist badge becomes unreachable",
            titles.size,
            titles.toSet().size
        )
    }

    @Test
    fun everyCaseExplanationSaysSomethingTheCaseDoesNot() {
        content().cases.forEach { case ->
            assertTrue("${case.id} has no title", case.titleEs.isNotBlank())
            assertTrue("${case.id} has no brief", case.briefEs.isNotBlank())
            assertTrue(
                "${case.id} has no explanation, so a player cannot learn why the " +
                    "answer was wrong",
                case.explanationEs.isNotBlank()
            )
        }
    }

    @Test
    fun everyHandlingCaseDeclaresItsGoalAndNoPatientCeiling() {
        content().cases.filter { it.mode == LabMode.HANDLING }.forEach { case ->
            assertTrue("${case.id} declares no handling goal", case.handlingGoal != null)
            assertTrue("${case.id} is a handling case with a patient ceiling", case.ceilings.isEmpty())
            assertTrue(
                "${case.id} is a handling case carrying a cannabinoid",
                case.forbiddenCannabinoids.isEmpty() && case.maxCannabinoidShare.isEmpty()
            )
            assertTrue("${case.id} declares no material to judge a route on", case.handlingCompounds.isNotEmpty())
            assertTrue(
                "${case.id} states no evidence level for its basis",
                case.handlingBasisEs.isNotBlank()
            )
        }
    }

    @Test
    fun everyPharmacologicalCaseConstrainsAllFourAxes() {
        content().cases.filter { it.mode == LabMode.PHARMACOLOGICAL }.forEach { case ->
            assertEquals(
                "${case.id} does not constrain all four axes, so the minigame " +
                    "silently stops constraining one",
                com.trichome.app.model.LabAxis.entries.toSet(),
                case.ceilings.keys
            )
            assertTrue("${case.id} aims at no profile", case.goal != null)
        }
    }

    /* ── The mode separation, over every shipped case ────────────────────── */

    @Test
    fun theTwoModesStayDisjointAcrossEveryShippedCase() {
        // `EntourageHandlingTest.theTwoModesCannotProduceTheSameVerdictForTheSameInput`
        // already owns the single-pair version of this, and the brief named that
        // test while placing it in `EntourageLabTest` — the file was wrong, the
        // test was not, and it is left exactly as it shipped.
        //
        // What that test cannot cover is breadth: it reads one case of each mode,
        // so a ninth pharmacological case that broke the separation would not be
        // looked at. This walks every shipped case, and it asserts the property
        // structurally rather than by comparing two verdicts — comparing verdicts
        // passes vacuously while the profiles are broken and both branches return
        // INEFICAZ, which is exactly how this file's first run of this assertion
        // failed for a reason that had nothing to do with the modes.
        val content = content()
        val pharmacological = content.cases.filter { it.mode == LabMode.PHARMACOLOGICAL }
        val handling = content.cases.filter { it.mode == LabMode.HANDLING }

        assertTrue("there is no pharmacological case to compare", pharmacological.isNotEmpty())
        assertTrue("there is no handling case to compare", handling.isNotEmpty())

        // The structural half: the shape each mode needs is the shape the other
        // mode lacks, so no case can be read by both branches.
        handling.forEach { case ->
            assertTrue(
                "${case.id} is a handling case that declares a pharmacological ceiling",
                case.ceilings.isEmpty()
            )
            assertTrue(
                "${case.id} is a handling case that names a pharmacological goal",
                case.goal == null
            )
            assertTrue(
                "${case.id} is a handling case carrying a cannabinoid",
                case.forbiddenCannabinoids.isEmpty() && case.maxCannabinoidShare.isEmpty()
            )
            assertTrue("${case.id} declares no handling goal", case.handlingGoal != null)
        }
        pharmacological.forEach { case ->
            assertTrue(
                "${case.id} is a pharmacological case that declares a handling goal, " +
                    "so the handling branch could be pointed at it",
                case.handlingGoal == null
            )
            assertTrue("${case.id} aims at no profile", case.goal != null)
            assertEquals(
                "${case.id} does not constrain all four axes",
                com.trichome.app.model.LabAxis.entries.toSet(),
                case.ceilings.keys
            )
        }

        // The behavioural half, and the one that cannot pass vacuously: the
        // **route** is the handling branch's only input and the pharmacological
        // branch must not read it. `profiles` is the mirror image -- it is the
        // pharmacological branch's input, so the handling branch must ignore it.
        // Asserting "the pharmacological branch ignores profiles" would be
        // asserting that a case cannot find its own target, which is nonsense and
        // was the first version of this assertion.
        val terpenes = setOf(
            EntourageTerpene.MYRCENE,
            EntourageTerpene.LINALOOL,
            EntourageTerpene.BETA_CARYOPHYLLENE
        )
        val selection = com.trichome.app.model.EntourageSelection(
            cannabinoids = setOf(Cannabinoid.CBD, Cannabinoid.THC),
            terpenes = terpenes
        )

        pharmacological.forEach { case ->
            val plain = com.trichome.app.model.EntourageLab
                .solve(case, selection, content.profiles)
            val withARoute = com.trichome.app.model.EntourageLab
                .solve(case, selection, content.profiles, ProcessingMethod.LIVE_ROSIN)

            assertEquals(
                "${case.id} scored differently when handed a processing route, so " +
                    "the pharmacological branch is reading the handling one",
                plain.verdict,
                withARoute.verdict
            )
            assertEquals(
                "${case.id} changed its case id when handed a route",
                plain.caseId,
                withARoute.caseId
            )
        }

        handling.forEach { case ->
            val material = com.trichome.app.model.EntourageLabUi.selectionFromDials(
                emptyMap(),
                case.handlingCompounds
            )
            val withProfiles = com.trichome.app.model.EntourageLab
                .solve(case, material, content.profiles, ProcessingMethod.LIVE_ROSIN)
            val withNoProfiles = com.trichome.app.model.EntourageLab
                .solve(case, material, emptyList(), ProcessingMethod.LIVE_ROSIN)

            assertEquals(
                "${case.id} scored differently with a profile list, so the handling " +
                    "branch is reading the pharmacological one",
                withProfiles.verdict,
                withNoProfiles.verdict
            )
            assertEquals(
                "${case.id} reports a patient side-effect axis; there is no patient",
                emptyList<com.trichome.app.model.LabAxisReading>(),
                withProfiles.readings
            )
        }
    }

    /* ── The catalogue filter ────────────────────────────────────────────── */

    @Test
    fun anAccentlessQueryFindsAnAccentedCase() {
        val content = content()
        val index = EntourageCaseSearch.index(content.cases, content.profiles)

        val accented = EntourageCaseSearch.filter(index, "ácaro")

        assertEquals(
            "\"ácaro\" and \"acaro\" have to find the same rows",
            accented.map { it.case.id },
            EntourageCaseSearch.filter(index, "acaro").map { it.case.id }
        )
    }

    @Test
    fun aBlankQueryReturnsEveryCase() {
        val content = content()
        val index = EntourageCaseSearch.index(content.cases, content.profiles)

        assertEquals(index.size, EntourageCaseSearch.filter(index, "  ").size)
    }

    @Test
    fun everyTokenHasToMatch() {
        val content = content()
        val index = EntourageCaseSearch.index(content.cases, content.profiles)

        val both = EntourageCaseSearch.filter(index, "sesquiterpeno secado")
        val one = EntourageCaseSearch.filter(index, "sesquiterpeno")

        assertTrue("the conjunction has to be narrower", both.size <= one.size)
    }

    @Test
    fun theModeFilterNarrowsToThatModeAndANullModeNarrowsToNothing() {
        val content = content()
        val index = EntourageCaseSearch.index(content.cases, content.profiles)

        val handling = EntourageCaseSearch.inMode(index, LabMode.HANDLING)

        assertTrue("the handling mode has cases", handling.isNotEmpty())
        assertTrue(
            "every filtered case has to be in the mode",
            handling.all { it.case.mode == LabMode.HANDLING }
        )
        assertEquals(index.size, EntourageCaseSearch.inMode(index, null).size)
    }

    @Test
    fun theModeIsSearchableByItsSpanishNameNotItsKey() {
        val content = content()
        val index = EntourageCaseSearch.index(content.cases, content.profiles)

        val spanish = EntourageCaseSearch.filter(index, "procesado")

        assertTrue(
            "the picker is in Spanish, so the mode's label has to be searchable",
            spanish.isNotEmpty()
        )
        assertEquals(
            "and the English key must not be: a key is an implementation detail",
            emptyList<String>(),
            EntourageCaseSearch.filter(index, "handling").map { it.case.id }
        )
    }

    @Test
    fun aGoalIsSearchableByItsProfileLabel() {
        val content = content()
        val index = EntourageCaseSearch.index(content.cases, content.profiles)

        val hits = EntourageCaseSearch.filter(index, "mirceno")

        assertTrue(
            "the fastest way to find a case is to know the goal, so the profile " +
                "label has to be in the haystack",
            hits.isNotEmpty()
        )
    }

    @Test
    fun theFilterCopyIsNotBlank() {
        listOf(
            EntourageCaseSearch.FIELD_LABEL_ES,
            EntourageCaseSearch.FIELD_HINT_ES,
            EntourageCaseSearch.CLEAR_LABEL_ES,
            EntourageCaseSearch.NO_RESULTS_ES,
            EntourageCaseSearch.MODE_FILTER_ES,
            EntourageCaseSearch.ALL_MODES_ES
        ).forEach { text ->
            assertTrue("\"$text\" is blank, so the panel renders an empty control", text.isNotBlank())
        }
        assertEquals("4 de 12 casos", EntourageCaseSearch.countEs(4, 12))
    }

    /* ── Solvers ─────────────────────────────────────────────────────────── */

    private fun solvingPharmacological(
        case: com.trichome.app.model.EntourageCase,
        profiles: List<com.trichome.app.model.EntourageProfile>
    ): com.trichome.app.model.LabResult {
        val goal = profiles.firstOrNull { it.key == case.goal }
        val dials = goal!!.cannabinoidWeights.keys.associateWith { cannabinoid ->
            case.maxCannabinoidShare[cannabinoid] ?: 1f
        }
        return com.trichome.app.model.EntourageLab.solve(
            case,
            com.trichome.app.model.EntourageLabUi.selectionFromDials(dials, goal.terpeneShares.keys),
            profiles
        )
    }

    private fun solvingHandling(
        case: com.trichome.app.model.EntourageCase
    ): com.trichome.app.model.LabResult {
        val selection = com.trichome.app.model.EntourageLabUi.selectionFromDials(
            emptyMap(),
            case.handlingCompounds
        )
        return com.trichome.app.model.ProcessingMethod.entries
            .map { route -> com.trichome.app.model.EntourageLab.solve(case, selection, emptyList(), route) }
            .minBy { it.verdict.ordinal }
    }
}