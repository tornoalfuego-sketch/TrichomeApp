package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F3 — the agronomy model's contract, asserted without a device.
 *
 * ## Why the copy lives in `model/` and is tested here
 *
 * Compose has no unit-test runtime on this project's `test` classpath — only
 * `androidTest` has one and it needs a handset. Two bugs in this repository
 * already came from logic only a composable could reach: a `weight(0f)` that
 * threw at compose time and closed every terpene page, and an unwighted `Row`
 * that overflowed. So every Spanish sentence, every show-or-hide decision and
 * every number the agronomy section displays is built here and asserted here.
 * What these tests do **not** cover is layout: there is no measured pixel width
 * in this project, so "the block renders correctly" is a device screenshot.
 *
 * ## What is deliberately asserted about the content
 *
 * The honesty standard, applied rather than restated:
 *
 * 1. **A compound with no documented lever says so.** An absent entry produces
 *    a sentence naming the compound, not an empty block and not a default.
 * 2. **Every lever's basis is visible on both surfaces.** The evidence level
 *    travels inside the same rendered block as the claim, on the detail page and
 *    on the synergy card alike.
 * 3. **A lever whose level the sentence never states cannot render** — the label
 *    is a field of the content, so it cannot be omitted by a call site.
 * 4. **The biosynthetic routes are one explainer.** Two families, one step list,
 *    and a route sentence that differs — that is what "not repeated per
 *    compound" means in code rather than in a comment.
 */
class TerpeneAgronomyTest {

    /* ── Fixtures ─────────────────────────────────────────────────────────── */

    private fun lever(
        kind: AgronomyLeverKind,
        detail: String = "Detalle de prueba para $kind.",
        evidence: AgronomyEvidence = AgronomyEvidence.MIXTO,
        basis: String = "Base declarada para $kind: el nivel está acotado."
    ) = AgronomyLever(kind = kind, detailEs = detail, evidence = evidence, basisEs = basis)

    private fun agronomy(
        terpene: EntourageTerpene = EntourageTerpene.MYRCENE,
        response: String = "Respuesta declarada para $terpene.",
        levers: List<AgronomyLever> = listOf(
            lever(AgronomyLeverKind.HARVEST_POINT),
            lever(AgronomyLeverKind.UV_B, evidence = AgronomyEvidence.BIEN_DOCUMENTADO)
        )
    ) = EntourageAgronomy(terpene = terpene, responseEs = response, levers = levers)

    /* ── The gap is stated, not left blank ────────────────────────────────── */

    @Test
    fun aCompoundWithNoAgronomyEntrySaysSoRatherThanShowingAnEmptyBlock() {
        val content = TerpeneAgronomyCopy.contentOf(EntourageTerpene.CAMPHENE, null)

        assertFalse("there is no documented entry", content.isDocumented)
        assertEquals("", content.responseEs)
        assertTrue(
            "the gap has to be a sentence that names the compound, not an empty " +
                "block: \"${content.notDocumentedEs}\"",
            content.notDocumentedEs.isNotBlank()
        )
        assertTrue(
            "the sentence must name the compound the reader is looking at",
            content.notDocumentedEs.contains(EntourageTerpene.CAMPHENE.labelEs)
        )
        assertTrue(
            "and the whole block must still have something to say",
            content.allTextEs.isNotBlank()
        )
    }

    @Test
    fun theNotDocumentedSentenceIsNotEmittedForADocumentedCompound() {
        val entry = agronomy()

        val content = TerpeneAgronomyCopy.contentOf(entry.terpene, entry)

        assertTrue(content.isDocumented)
        assertEquals(entry.responseEs, content.responseEs)
        assertEquals("", content.notDocumentedEs)
    }

    @Test
    fun aCompoundWithNoAgronomyStillGetsItsBiosyntheticRoute() {
        // The route is a fact about the compound's size, not an agronomic claim,
        // so it survives an empty agronomy set. Dropping it would make an honest
        // gap read as "nothing at all is known about this compound".
        val content = TerpeneAgronomyCopy.contentOf(EntourageTerpene.CAMPHENE, null)

        assertTrue("the route line must still be there", content.routeEs.isNotBlank())
        assertTrue(content.isDrawable)
    }

    @Test
    fun theIndexReportsTheGapInsteadOfCoercingACompoundToADefaultEntry() {
        val index = EntourageAgronomyIndex(listOf(agronomy()))

        assertNull(
            "an undocumented compound must return null, not an empty entry",
            index.forTerpene(EntourageTerpene.TERPINOLENE)
        )
        assertTrue(
            "the gap has to be nameable",
            EntourageTerpene.TERPINOLENE in index.undocumentedTerpenes
        )
        assertFalse(
            "a documented compound is not a gap",
            EntourageTerpene.MYRCENE in index.undocumentedTerpenes
        )
    }

    @Test
    fun anEmptyIndexIsTheDefaultSoACallerThatHasNotLoadedItLosesNothing() {
        val index = EntourageAgronomyIndex()

        assertEquals(0, index.size)
        assertNull(index.forTerpene(EntourageTerpene.MYRCENE))
        assertNull(index.forTerpene(null))
        assertEquals(emptyList<EntourageAgronomy>(), index.all())
    }

    @Test
    fun theIndexOrderDoesNotDependOnTheOrderOfTheAsset() {
        val forwards = EntourageAgronomyIndex(
            listOf(agronomy(EntourageTerpene.MYRCENE), agronomy(EntourageTerpene.LIMONENE))
        )
        val backwards = EntourageAgronomyIndex(
            listOf(agronomy(EntourageTerpene.LIMONENE), agronomy(EntourageTerpene.MYRCENE))
        )

        assertEquals(forwards.all().map { it.terpene }, backwards.all().map { it.terpene })
        assertEquals(
            "sorted by enum key so the list cannot drift with the asset",
            listOf(EntourageTerpene.LIMONENE, EntourageTerpene.MYRCENE),
            forwards.all().map { it.terpene }
        )
    }

    /* ── Every lever's basis is visible, on both surfaces ─────────────────── */

    @Test
    fun aLeverCannotBeDrawnWithoutItsBasis() {
        val naked = AgronomyLeverContent(
            kind = AgronomyLeverKind.UV_B,
            titleEs = "Luz UV-B",
            detailEs = "Un detalle sin base.",
            evidenceLabelEs = "Evidencia mixta",
            basisEs = ""
        )

        assertFalse(
            "a claim with no basis sentence must not be drawable, or it reaches " +
                "the screen unqualified",
            naked.isDrawable
        )
    }

    @Test
    fun everyLeverCarriesTheEvidenceLabelTheUserHasToSee() {
        val entry = agronomy(
            levers = listOf(
                lever(AgronomyLeverKind.HARVEST_POINT, evidence = AgronomyEvidence.BIEN_DOCUMENTADO),
                lever(AgronomyLeverKind.WATER_DEFICIT, evidence = AgronomyEvidence.LIMITADO)
            )
        )

        val content = TerpeneAgronomyCopy.contentOf(entry.terpene, entry)

        assertEquals(
            "the label the screen renders must be the level the lever declares",
            listOf("Bien documentado", "Evidencia limitada"),
            content.levers.map { it.evidenceLabelEs }
        )
        content.levers.forEach {
            assertTrue("${it.kind} has no basis sentence", it.isDrawable)
            assertTrue("${it.kind} has a blank basis", it.basisEs.isNotBlank())
        }
    }

    @Test
    fun theAgronomyBasisTravelsInsideTheCardLine() {
        val entry = agronomy(
            levers = listOf(
                lever(
                    AgronomyLeverKind.HARVEST_POINT,
                    evidence = AgronomyEvidence.BIEN_DOCUMENTADO,
                    basis = "Base del punto de cosecha."
                ),
                lever(
                    AgronomyLeverKind.WATER_DEFICIT,
                    evidence = AgronomyEvidence.LIMITADO,
                    basis = "Base del déficit hídrico."
                )
            )
        )

        val body = TerpeneAgronomyCopy.cardLineEs(entry)

        assertTrue("the response must lead", body.contains(entry.responseEs))
        entry.levers.forEach {
            assertTrue(
                "${it.kind}: the basis has to be inside the rendered card body, " +
                    "not behind anything",
                body.contains(it.basisEs)
            )
            assertTrue(
                "${it.kind}: and its evidence level has to be in the same body",
                body.contains(it.evidence.labelEs)
            )
        }
    }

    @Test
    fun everyDocumentedTerpeneOfACombinationGetsExactlyOneCardLine() {
        val index = EntourageAgronomyIndex(
            listOf(
                agronomy(EntourageTerpene.MYRCENE),
                agronomy(EntourageTerpene.BETA_CARYOPHYLLENE)
            )
        )

        val lines = TerpeneAgronomyCopy.cardLinesFor(
            listOf(EntourageTerpene.LIMONENE, EntourageTerpene.MYRCENE),
            index
        )

        assertEquals(1, lines.size)
        assertEquals(EntourageCardRole.AGRONOMY, lines.first().role)
        assertTrue(lines.first().labelEs.contains(EntourageTerpene.MYRCENE.labelEs))
    }

    @Test
    fun anUndocumentedTerpeneAddsNoCardLineRatherThanAnEmptyOne() {
        // The one place the module's "name the gap" rule is relaxed, deliberately
        // and documented on `cardLinesFor`: a combination of three terpenes where
        // only one has an entry would otherwise be three "no data" lines next to
        // one line of content. The compound's own page is where the gap is named.
        val index = EntourageAgronomyIndex(listOf(agronomy(EntourageTerpene.MYRCENE)))

        val lines = TerpeneAgronomyCopy.cardLinesFor(EntourageTerpene.entries, index)

        assertEquals(1, lines.size)
        assertTrue(lines.none { it.bodyEs.isBlank() })
    }

    @Test
    fun aCardBuiltWithoutTheAgronomyIndexIsTheCardF2Shipped() {
        // The regression guard on the new parameter: a caller that has not loaded
        // the agronomy block yet gets no agronomy line, and nothing else moves.
        val synergy = EntourageSynergy(
            id = "s1",
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.MYRCENE, EntourageTerpene.LINALOOL),
            profiles = setOf(PharmacologicalProfile.SEDATIVE),
            outcomeEs = "Efecto descrito",
            descriptionEs = "Qué se percibe",
            mechanismEs = "Mecanismo",
            evidenceEs = "Evidencia",
            strainsEs = listOf("Amnesia"),
            interactionEs = ""
        )

        val bare = EntourageCards.cardFor(synergy)

        assertFalse(bare.hasAgronomy)
        assertTrue(bare.linesEs.none { it.role == EntourageCardRole.AGRONOMY })
        assertTrue(
            "the evidence line must survive the new parameter untouched",
            bare.linesEs.any { it.role == EntourageCardRole.EVIDENCE }
        )
    }

    @Test
    fun theAgronomyLineSitsNextToTheEvidenceLineAndNotBelowTheOptionalOnes() {
        val synergy = EntourageSynergy(
            id = "s1",
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.MYRCENE),
            profiles = emptySet(),
            outcomeEs = "Efecto descrito",
            descriptionEs = "Qué se percibe",
            mechanismEs = "Mecanismo",
            evidenceEs = "Evidencia",
            strainsEs = listOf("Amnesia"),
            interactionEs = "Interacciones"
        )
        val index = EntourageAgronomyIndex(listOf(agronomy()))

        val roles = EntourageCards.cardFor(synergy, index).linesEs.map { it.role }

        assertEquals(
            "agronomy is the same kind of statement as evidence, so it belongs " +
                "beside it rather than under the optional lines",
            listOf(
                EntourageCardRole.OUTCOME,
                EntourageCardRole.DESCRIPTION,
                EntourageCardRole.MECHANISM,
                EntourageCardRole.EVIDENCE,
                EntourageCardRole.AGRONOMY,
                EntourageCardRole.STRAINS,
                EntourageCardRole.INTERACTION
            ),
            roles
        )
    }

    /* ── T-B: one biosynthetic explainer, not 158 ─────────────────────────── */

    @Test
    fun theBiosyntheticStepListIsIdenticalForEveryCompound() {
        val mono = BiosynthesisExplainer.contentFor(TerpeneFamily.MONOTERPENE)
        val sesqui = BiosynthesisExplainer.contentFor(TerpeneFamily.SESQUITERPENE)
        val di = BiosynthesisExplainer.contentFor(TerpeneFamily.DITERPENE)

        assertEquals(mono.steps, sesqui.steps)
        assertEquals(mono.steps, di.steps)
        assertEquals(mono.steps, BiosynthesisExplainer.contentFor(TerpeneFamily.UNCLASSIFIED).steps)
        assertTrue("an explainer with no steps would be a heading", mono.steps.isNotEmpty())
    }

    @Test
    fun whatChangesBetweenCompoundsIsTheRouteSentenceAndNothingElse() {
        val mono = BiosynthesisExplainer.contentFor(TerpeneFamily.MONOTERPENE)
        val sesqui = BiosynthesisExplainer.contentFor(TerpeneFamily.SESQUITERPENE)

        assertEquals(mono.introEs, sesqui.introEs)
        assertEquals(mono.sizeRuleEs, sesqui.sizeRuleEs)
        assertEquals(mono.trichomeEs, sesqui.trichomeEs)
        assertEquals(mono.caveatEs, sesqui.caveatEs)
        assertTrue("the route sentence has to actually differ", mono.routeEs != sesqui.routeEs)
    }

    @Test
    fun theRouteIsChosenByCarbonCount() {
        assertEquals(BiosyntheticPathway.MEP, BiosynthesisExplainer.pathwayFor(TerpeneFamily.MONOTERPENE))
        assertEquals(BiosyntheticPathway.MVA, BiosynthesisExplainer.pathwayFor(TerpeneFamily.SESQUITERPENE))
        assertEquals(BiosyntheticPathway.MVA, BiosynthesisExplainer.pathwayFor(TerpeneFamily.DITERPENE))
        assertNull(
            "an unclassified compound gets no invented route",
            BiosynthesisExplainer.pathwayFor(TerpeneFamily.UNCLASSIFIED)
        )
    }

    @Test
    fun everyExplainerCarriesItsCaveatAndSaysWhatItCannotSettle() {
        TerpeneFamily.entries.forEach { family ->
            val content = BiosynthesisExplainer.contentFor(family)

            assertTrue("${family.labelEs} explainer is not drawable", content.isDrawable)
            assertTrue(
                "${family.labelEs}: the caveat is required, or the explainer is half " +
                    "a statement",
                content.caveatEs.isNotBlank()
            )
            assertTrue(
                "${family.labelEs}: the route sentence must name the family it " +
                    "resolved",
                content.routeEs.contains(family.labelEs.substringBefore(" ").lowercase()) ||
                    family == TerpeneFamily.UNCLASSIFIED
            )
        }
    }

    @Test
    fun bothPathwaysAreActuallyWalkedThrough() {
        val pathways = BiosynthesisExplainer.steps.map { it.pathway }.toSet()

        assertEquals(BiosyntheticPathway.entries.toSet(), pathways)
        assertTrue(
            "the carbon-count rule has to be stated, or the step list is a " +
                "sequence with no meaning",
            BiosynthesisExplainer.SIZE_RULE_ES.contains("C10") &&
                BiosynthesisExplainer.SIZE_RULE_ES.contains("C15")
        )
    }

    @Test
    fun theExplainerNamesTheTrichomeItHappensIn() {
        assertTrue(
            "the whole explainer is about a capitate-stalked glandular trichome; " +
                "without it the routes read as generic botany",
            BiosynthesisExplainer.TRICHOME_ES.contains("tricoma")
        )
    }

    /* ── T-C: the three guides ────────────────────────────────────────────── */

    @Test
    fun thereIsOneGuidePerLeverKindAndNoDuplicates() {
        val kinds = GrowOutGuides.all.map { it.kind }

        assertEquals(
            "a lever kind with no guide would leave the card title undefined, and a " +
                "duplicate would render the same mechanism twice",
            AgronomyLeverKind.entries.toSet(),
            kinds.toSet()
        )
        assertEquals(AgronomyLeverKind.entries.size, kinds.size)
    }

    @Test
    fun everyGuideStatesWhatItDoesAndHowWellItIsSupported() {
        GrowOutGuides.all.forEach { guide ->
            assertTrue("${guide.kind} guide has no mechanism", guide.whatEs.isNotBlank())
            assertTrue(
                "${guide.kind} guide has no basis: a shared guide that omits its " +
                    "evidence level is the exact failure this module exists to avoid",
                guide.basisEs.isNotBlank()
            )
            assertEquals(
                "${guide.kind}: the guide title must be the lever's own label so " +
                    "the card and the page name the same thing",
                guide.kind.labelEs,
                guide.titleEs
            )
            assertNotNull(GrowOutGuides.guideFor(guide.kind))
        }
    }

    @Test
    fun aGuideForAKindThatDoesNotExistFailsLoudlyRatherThanRenderingNothing() {
        // `guideFor` is on the path of every lever render. An unknown kind cannot
        // reach it — the mapper resolves through the enum — but a missing guide
        // for a real kind would be a crash on the page, so the contract is
        // asserted instead of assumed.
        AgronomyLeverKind.entries.forEach { kind ->
            assertNotNull("no guide for $kind", GrowOutGuides.guideFor(kind))
        }
    }

    @Test
    fun theLeverKindIsReachableFromTheAssetKeyInBothCases() {
        assertEquals(
            listOf("UV_B", "WATER_DEFICIT", "HARVEST_POINT"),
            AgronomyLeverKind.entries.map { it.key }
        )
        assertEquals(
            listOf("BIEN_DOCUMENTADO", "MIXTO", "LIMITADO"),
            AgronomyEvidence.entries.map { it.key }
        )
        AgronomyEvidence.entries.forEach {
            assertTrue(
                "${it.key} has no visible label, so the level could never be read",
                it.labelEs.isNotBlank()
            )
        }
    }

    /* ── The language guard applies here too ──────────────────────────────── */

    @Test
    fun theAuthoredAgronomyCopyPassesTheModulesOwnGuard() {
        // `EntourageLanguage` is the module's word list. The agronomy copy is
        // authored in this file for exactly the same reason `LIMITS_ES` is: the
        // guard can only police text a JVM test can reach.
        val authored: List<String> = listOf(
            TerpeneAgronomyCopy.TITLE_ES,
            TerpeneAgronomyCopy.NOT_DOCUMENTED_ES,
            BiosynthesisExplainer.TITLE_ES,
            BiosynthesisExplainer.INTRO_ES,
            BiosynthesisExplainer.SIZE_RULE_ES,
            BiosynthesisExplainer.TRICHOME_ES,
            BiosynthesisExplainer.CAVEAT_ES
        ) + TerpeneFamily.entries.map { BiosynthesisExplainer.routeLineEs(it) } +
            listOf(GrowOutGuides.allTextEs())

        authored.forEach { text ->
            assertTrue(
                "authored agronomy copy used ${EntourageLanguage.violations(text)}: $text",
                EntourageLanguage.violations(text).isEmpty()
            )
        }
    }

    @Test
    fun theAuthoredCopyIsNotBlankAnywhere() {
        val authored: List<String> = listOf(
            TerpeneAgronomyCopy.TITLE_ES,
            TerpeneAgronomyCopy.NOT_DOCUMENTED_ES,
            TerpeneAgronomyCopy.CARD_LABEL_ES,
            TerpeneAgronomyCopy.CARD_LEVER_LABEL_ES
        ) + BiosynthesisExplainer.steps.map { "${it.titleEs} ${it.bodyEs}" } +
            TerpeneFamily.entries.map { BiosynthesisExplainer.routeLineEs(it) }

        authored.forEach {
            assertTrue("blank authored agronomy copy: \"$it\"", it.isNotBlank())
        }
    }
}