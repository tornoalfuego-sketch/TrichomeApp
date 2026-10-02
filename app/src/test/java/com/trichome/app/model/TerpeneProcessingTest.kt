package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4 — the model rules of the processing dimension, tested without a device.
 *
 * ## Why the safety tests are here and not on the asset
 *
 * The residual-solvent framing is not asset content: it is
 * [ProcessingMethodGuide.safetyEs], a required constructor argument built in
 * `model/`. That is deliberate, and these are the tests that hold it:
 *
 * 1. **A guide cannot be built without a safety line.** Not "the shipped ones
 *    have one" — the constructor refuses. A test asserting the shipped content is
 *    fine would not catch a future fourth method added without the argument,
 *    because the argument does not have a default and the build would already
 *    have failed. The tests below pin the constructor rule anyway, because a
 *    later edit that gives `safetyEs` a default would break the guarantee and a
 *    build is not a test.
 * 2. **Every named method carries its safety line into the rendered card**, so
 *    the framing is in the same block as the method name rather than a footnote.
 * 3. **The comparison names the solvent-based method and travels with its
 *    safety sentence**, because the synergy card's whole body is that sentence
 *    and a card that named a solvent route without it would be the exact defect
 *    this phase exists to fix.
 *
 * ## What the numeric tests are for
 *
 * F4 refuses three things the rest of the module would otherwise happily print: a
 * temperature, a duration and a quantity. The refusal is the feature, so it is
 * asserted — over the shipped asset by `TerpeneProcessingAssetTest` and over the
 * authored copy here, because the authored copy is what a screen shows for every
 * one of the 158 compounds and for all ten module compounds' shared guides.
 */
class TerpeneProcessingTest {

    private val guides = ProcessingGuides

    private fun processing(
        terpene: EntourageTerpene = EntourageTerpene.MYRCENE,
        methods: List<ProcessingMethodNote> = listOf(
            ProcessingMethodNote(
                method = ProcessingMethod.SOLVENT_EXTRACTION,
                detailEs = "Sale entero en el extracto y se va en buena parte.",
                evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
                basisEs = "La etapa de separación se lleva la fracción volátil."
            )
        ),
        preservation: List<PreservationFactorNote> = listOf(
            PreservationFactorNote(
                factor = PreservationFactorKind.OXIDATION,
                detailEs = "Se oxida con facilidad en el material almacenado.",
                evidence = ProcessingEvidence.MIXTO,
                basisEs = "La proporción de cada producto no la puede fijar este catálogo."
            )
        )
    ) = EntourageProcessing(
        terpene = terpene,
        responseEs = "El mirceno se va pronto cuando el material se calienta.",
        evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
        basisEs = "Su pérdida por secado y almacenamiento está bien documentada.",
        methods = methods,
        preservation = preservation
    )

    /* ── Safety is part of the block, not a footnote ────────────────────── */

    @Test
    fun aGuideWithoutASafetyLineIsRefusedRatherThanShipped() {
        // The strongest form of the rule: it is a construction-time check, so
        // there is no code path — asset, copy or future method — that produces a
        // named extraction method with its safety framing stripped off.
        val thrown = runCatching {
            ProcessingMethodGuide(
                method = ProcessingMethod.SOLVENT_EXTRACTION,
                titleEs = "Extracción con disolvente",
                whatEs = "El disolvente disuelve la resina.",
                safetyEs = "   ",
                evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
                basisEs = "Base."
            )
        }.exceptionOrNull()

        assertNotNull(
            "a guide with a blank safety line has to be refused: naming a method " +
                "without the residual-solvent framing is the failure this phase " +
                "exists to prevent",
            thrown
        )
    }

    @Test
    fun everyNamedMethodCarriesANonBlankSafetyLine() {
        ProcessingMethod.entries.forEach { method ->
            val guide = guides.methodFor(method)
            assertTrue(
                "${method.key} ships no safety framing, so the card would name a " +
                    "method with nothing about how to handle it",
                guide.safetyEs.isNotBlank()
            )
            assertTrue(
                "${method.key} must not delegate its safety line to another block",
                guide.safetyEs.length > 60
            )
        }
    }

    @Test
    fun onlyTheSolventBasedMethodClaimsASolventAndItIsTheOneThatMentionsResidue() {
        val solvent = ProcessingMethod.entries.filter { it.usesSolvent }
        val plain = ProcessingMethod.entries.filter { !it.usesSolvent }

        assertEquals(
            "exactly one route is solvent-based; a second one would have to ship " +
                "its own residue framing",
            1,
            solvent.size
        )
        solvent.forEach { method ->
            val text = guides.methodFor(method).safetyEs.lowercase()
            assertTrue(
                "${method.key} uses a solvent, so its safety line has to speak " +
                    "about the residue rather than about something else",
                text.contains("residuo") || text.contains("disolvente")
            )
        }
        // And the direction of the error: a solvent-free route must say so in its
        // own words, because "no safety line" and "no solvent, so no residue
        // question" are different things and a reader cannot tell them apart from
        // an empty sentence. Two wordings are accepted because the two routes have
        // different agents: LIVE_ROSIN has no solvent at all, DECARBOXYLATION has
        // heat as its agent and no solvent, and only one of those is "no hay".
        plain.forEach { method ->
            val text = guides.methodFor(method).safetyEs.lowercase()
            val saysNoSolvent = listOf(
                "no hay disolvente",
                "no es un disolvente",
                "y no un disolvente",
                "en lugar de un disolvente"
            ).any { it in text }
            assertTrue(
                "${method.key} uses no solvent and must say so, otherwise its " +
                    "safety line reads as if it did. Got: ${guides.methodFor(method).safetyEs}",
                saysNoSolvent
            )
        }
    }

    @Test
    fun decarboxylationIsNotPresentedAsASolventRoute() {
        // The enum says `usesSolvent = false` for it, and the copy has to agree. A
        // reader who reads "descarboxilación" next to "extracción con disolvente" on
        // the same card could reasonably conclude the first needs the second's
        // residue handling.
        val guide = guides.methodFor(ProcessingMethod.DECARBOXYLATION)

        assertFalse(ProcessingMethod.DECARBOXYLATION.usesSolvent)
        assertTrue(
            "the decarboxylation basis has to say the residue question does not " +
                "apply, because its agent is heat",
            guide.safetyEs.contains("residuo", ignoreCase = true) &&
                guide.safetyEs.contains("no aplica", ignoreCase = true)
        )
    }

    @Test
    fun theFoodGradeLabelIsNotPresentedAsASafetyClaim() {
        // The specific false claim this phase has to refuse: "food grade" on a
        // bottle is a grade of the *solvent*, not a statement about the product
        // made with it. The sentence has to make that distinction explicitly.
        val text = guides.solventSafetyEs().lowercase()

        assertTrue(
            "the framing has to name the label, because a reader who sees it on a " +
                "bottle will otherwise read it as an endorsement",
            text.contains("grado alimentario")
        )
        assertTrue(
            "and it has to say the label describes the bottle, not the product",
            text.contains("botella")
        )
        assertTrue(
            "and it has to point at the only real answer: measuring the finished " +
                "product",
            text.contains("análisis")
        )
    }

    @Test
    fun everyMethodNoteReachesTheCardWithItsMethodOwnSafetyLine() {
        val entry = processing()
        val content = TerpeneProcessingCopy.contentOf(entry.terpene, entry)

        content.methods.forEach { method ->
            assertTrue(
                "${method.method.key} reaches the card without its safety line",
                method.guide.safetyEs.isNotBlank()
            )
            assertEquals(
                "${method.method.key}: the card must show the guide the model " +
                    "owns, not a copy the composable could differ from",
                guides.methodFor(method.method).safetyEs,
                method.guide.safetyEs
            )
        }
    }

    @Test
    fun theComparisonNamesTheMethodsAndCarriesTheSolventSafety() {
        // The synergy card body *is* the comparison, so this sentence is the only
        // place a reader of a card can learn what "food grade" does and does not
        // mean. If the comparison stopped naming the solvent route, this test would
        // be asserting a card that no longer needs the safety line — which is the
        // moment to notice, not after.
        val comparison = guides.COMPARISON_ES.lowercase()

        // Matched on the method's **head noun**, not its whole label. The label carries a
        // parenthetical ("Resina en vivo (prensado en caliente, sin disolvente)")
        // that belongs on the per-method card and has no place in a flowing
        // sentence, so comparing against the whole string tested the copy's phrasing
        // rather than the requirement.
        val named = mapOf(
            ProcessingMethod.LIVE_ROSIN to "resina en vivo",
            ProcessingMethod.SOLVENT_EXTRACTION to "extracción con disolvente",
            ProcessingMethod.DECARBOXYLATION to "descarboxilación"
        )
        assertEquals(
            "this map has to cover every method the enum declares",
            named.keys.toSet(),
            ProcessingMethod.entries.toSet()
        )
        named.forEach { (method, phrase) ->
            assertTrue(
                "the comparison has to name ${method.key} (\"$phrase\"): a reader " +
                    "cannot compare methods that are not on the screen. Got: " + comparison,
                phrase in comparison
            )
        }
        assertTrue(
            "and it has to carry the residual-solvent sentence with it",
            TerpeneProcessingCopy.contentOf(
                EntourageTerpene.LIMONENE,
                null
            ).solventSafetyEs.contains("grado alimentario", ignoreCase = true)
        )
    }

    @Test
    fun anUndocumentedCompoundStillGetsTheComparisonAndTheSolventSafety() {
        // The gap path. A compound the catalog says nothing about still shows the
        // shared comparison — which names the methods — so it must also show the
        // residue sentence. Otherwise the honest "no entry" page would be the one
        // page that names a solvent route with no framing.
        val content = TerpeneProcessingCopy.contentOf(EntourageTerpene.CAMPHENE, null)

        assertFalse(content.isDocumented)
        assertTrue(content.notDocumentedEs.contains("Camfeno"))
        assertTrue(content.comparisonEs.isNotBlank())
        assertTrue(content.solventSafetyEs.isNotBlank())
        assertEquals(ProcessingMethod.entries.size, content.guides.size)
        assertEquals(PreservationFactorKind.entries.size, content.factorGuides.size)
        content.guides.forEach { assertTrue("${it.method.key} has no safety line", it.safetyEs.isNotBlank()) }
    }

    /* ── The refusal: no temperature, no duration, no quantity ───────────── */

    /**
     * Numbers that read as instructions.
     *
     * Two forms matter and both are caught. `°C` next to a digit is the obvious
     * one. The other is a bare count attached to a time word — "30 minutos" — which
     * is just as much a setpoint and much easier to write by accident. A count
     * attached to nothing (a mole count in a chemistry aside) is deliberately not
     * matched, because the module is allowed to say how many carbons something has.
     */
    private val numbersThatReadAsInstructions = listOf(
        Regex("""\d+\s*°C"""),
        Regex("""\d+\s*(?:min|minutos?|hora|horas?|d[ií]as?|dias?|semanas?|segundos?)\b""", RegexOption.IGNORE_CASE),
        Regex("""\d+\s*(?:g|gr|mg|ml|kg|l)\b""", RegexOption.IGNORE_CASE),
        Regex("""\d+\s*%"""),
        Regex("""\b(?:usa|usar|use|aplicar|calienta|calentar|hornea|hornear)\s+\d+""", RegexOption.IGNORE_CASE)
    )

    private fun assertNoInstructionNumbers(what: String, text: String) {
        numbersThatReadAsInstructions.forEach { pattern ->
            val match = pattern.find(text)
            assertNull(
                "$what contains \"${match?.value}\", which reads as an " +
                    "instruction rather than a description: a number that reads " +
                    "as \"do this\" is the one liability this dimension cannot ship",
                match
            )
        }
    }

    @Test
    fun theAuthoredCopyStatesNoTemperatureNoDurationAndNoQuantity() {
        // Every one of the 158 encyclopedia pages renders these strings, so the
        // check belongs over all of them, not over a sample.
        assertNoInstructionNumbers("the shared guides", guides.allTextEs())
    }

    @Test
    fun everyMethodAndFactorBasisSaysWhatItCannotEstablish() {
        // The module's rule is that a claim carries what it cannot settle. F4's
        // stakes are higher, so this is asserted on **every** basis rather than on
        // the subset that happens to disclaim.
        val disclaimers = listOf(
            "no está", "no hay", "no existe", "no se", "no lo", "no la", "no puede",
            "no lo puede", "no la puede", "no se puede", "no está fijado", "no está fijada",
            "no se puede afirmar", "no se traslada", "no las traslada", "no publica"
        )
        val allBases = guides.methods.map { it.basisEs } + guides.factors.map { it.basisEs }

        assertTrue("the fixture must be non-empty", allBases.isNotEmpty())
        allBases.forEach { basis ->
            assertTrue(
                "this basis does not say what it cannot establish: \"$basis\"",
                disclaimers.any { it in basis.lowercase() }
            )
        }
    }

    @Test
    fun theDecarboxylationBasisNamesTheRefusalOutLoud() {
        // Not "the number is absent" — the sentence has to say it was declined and
        // why, in the reader's language, at the point the number would have been.
        val basis = guides.methodFor(ProcessingMethod.DECARBOXYLATION).basisEs.lowercase()

        assertTrue(
            "the decarboxylation basis has to name the temperature it is not " +
                "publishing, so the absence reads as a decision",
            basis.contains("temperatura")
        )
        assertTrue(
            "and it has to say why",
            basis.contains("recomendación")
        )
    }

    /* ── Evidence levels: reused, never a third ────────────────────────── */

    @Test
    fun processingReusesAgronomysEvidenceVocabularyRatherThanDeclaringAThird() {
        // A second evidence enum would let the same claim be labelled one way on
        // the agronomy card and another on the processing card. The alias is the
        // guarantee: `ProcessingEvidence` resolves to the very same class, so the
        // two axes cannot drift apart even if one of them is renamed.
        assertEquals(
            "F4's evidence type has to be F3's, not a copy of it",
            AgronomyEvidence::class,
            ProcessingEvidence::class
        )
        assertEquals(
            "and therefore the label a processing card prints is drawn from the " +
                "same vocabulary a reader already saw on the agronomy card",
            setOf("Bien documentado", "Evidencia mixta", "Evidencia limitada"),
            ProcessingEvidence.entries.map { it.labelEs }.toSet()
        )
    }

    @Test
    fun theAuthoredGuidesUseOnlyTheLevelsF3ShippedAndSayWhich() {
        // Not "the same two levels", because that is not what is true: the seven
        // shared guides are the *mechanism* of a named method, and the mechanism of
        // a method is not compound-specific — a solvent route loses volatiles during
        // its separation step whatever compound is present, so all seven are
        // BIEN_DOCUMENTADO. `LIMITADO` is never used here and `MIXTO` reaches the
        // mixed evidence through the per-compound notes, where it belongs.
        //
        // What is asserted is the rule rather than a count: no guide may claim a
        // level outside the shipped vocabulary, so F4 cannot quietly invent a third.
        val shipped = setOf(ProcessingEvidence.BIEN_DOCUMENTADO, ProcessingEvidence.MIXTO)
        val used = guides.methods.map { it.evidence } + guides.factors.map { it.evidence }

        used.forEach { level ->
            assertTrue(
                "a guide claims $level, which F3 never shipped; the vocabulary is " +
                    "BIEN_DOCUMENTADO and MIXTO and F4 adds no third",
                level in shipped
            )
            assertTrue(
                "a guide claims ${level.key} but its label does not reach the card",
                level.labelEs.isNotBlank()
            )
        }
        assertTrue("the guide list must not be empty", used.isNotEmpty())
    }

    @Test
    fun everyNoteCarriesItsLevelWhereTheCardPrintsIt() {
        val entry = processing()
        val content = TerpeneProcessingCopy.contentOf(entry.terpene, entry)

        content.methods.forEach { method ->
            assertTrue(
                "${method.method.key} has no evidence label",
                method.evidenceLabelEs.isNotBlank()
            )
            assertEquals(
                "the label a screen prints must be the level the note declares",
                guides.methodFor(method.method).evidence.labelEs,
                method.guide.evidence.labelEs
            )
        }
        content.preservation.forEach { factor ->
            assertTrue(
                "${factor.factor.key} has no evidence label",
                factor.evidenceLabelEs.isNotBlank()
            )
        }
    }

    /* ── The index ──────────────────────────────────────────────────────── */

    @Test
    fun theIndexIsAViewOverTheListAndNeverASecondParse() {
        val entries = listOf(
            processing(EntourageTerpene.MYRCENE),
            processing(EntourageTerpene.LIMONENE)
        )
        val index = EntourageProcessingIndex(entries)

        assertEquals(2, index.size)
        assertEquals(
            "sorted by key so the order never depends on the asset",
            listOf("LIMONENE", "MYRCENE"),
            index.all().map { it.terpene.key }
        )
        assertEquals(entries.toSet(), index.all().toSet())
        assertNotNull(index.forTerpene(EntourageTerpene.MYRCENE))
        assertNull(index.forTerpene(EntourageTerpene.OCIMENE))
        assertNull(index.forTerpene(null))
    }

    @Test
    fun anEmptyIndexReportsEveryModuleCompoundAsUndocumented() {
        // The gap is a first-class answer. A missing entry returns null and the
        // caller says so; it is never coerced to an empty entry that would read as
        // "this compound is unaffected by processing".
        assertEquals(
            EntourageTerpene.entries.toSet(),
            EntourageProcessingIndex().undocumentedTerpenes
        )
    }

    @Test
    fun aNoteWithNoBasisIsNotDrawable() {
        // Defence in depth: the mapper already drops these, but a caller building
        // an entry by hand must not be able to draw an unqualified note.
        val note = ProcessingMethodNote(
            method = ProcessingMethod.SOLVENT_EXTRACTION,
            detailEs = "Sale entero en el extracto.",
            evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
            basisEs = "  "
        )
        assertFalse(note.isDrawable)
        assertFalse(
            ProcessingMethodNote(
                method = ProcessingMethod.LIVE_ROSIN,
                detailEs = "  ",
                evidence = ProcessingEvidence.BIEN_DOCUMENTADO,
                basisEs = "Base."
            ).isDrawable
        )
        assertFalse(
            PreservationFactorNote(
                factor = PreservationFactorKind.TIME,
                detailEs = "Detalle.",
                evidence = ProcessingEvidence.MIXTO,
                basisEs = ""
            ).isDrawable
        )
    }

    /* ── The synergy card line ──────────────────────────────────────────── */

    @Test
    fun theCardLineCarriesTheLevelAndTheBasisInsideTheBody() {
        val entry = processing()
        val line = TerpeneProcessingCopy.cardLine(entry)

        assertEquals(EntourageCardRole.PROCESSING, line.role)
        assertTrue(
            "the claim has to reach the card body verbatim",
            line.bodyEs.contains(entry.responseEs)
        )
        assertTrue(
            "and its evidence level, in visible text rather than behind a " +
                "disclosure",
            line.bodyEs.contains(entry.evidence.labelEs)
        )
        assertTrue(
            "and its basis, which is what the level qualifies",
            line.bodyEs.contains(entry.basisEs)
        )
    }

    @Test
    fun theCardLineCarriesTheSolventSafetyBecauseItNamesTheMethods() {
        // The property that ties the two together: the card line names the
        // extraction methods, so the residual-solvent sentence cannot be "on the
        // detail page instead".
        val line = TerpeneProcessingCopy.cardLine(processing())

        assertTrue(
            "the card line names the methods, so the residue sentence has to be " +
                "in the same body",
            line.bodyEs.contains(TerpeneProcessingCopy.SAFETY_LABEL_ES)
        )
        assertTrue(
            "and the sentence itself has to be the real one, not a pointer to it",
            line.bodyEs.contains("grado alimentario", ignoreCase = true)
        )
    }

    @Test
    fun aCardBuiltWithoutTheIndexIsTheF3CardUnchanged() {
        // The same guarantee F3 shipped for the agronomy index: a caller that has
        // not loaded the processing block gets exactly the card it got before F4.
        val synergy = EntourageSynergy(
            id = "thc_myrcene",
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.MYRCENE),
            profiles = emptySet(),
            outcomeEs = "Efecto",
            descriptionEs = "Descripción",
            mechanismEs = "Mecanismo",
            evidenceEs = "Evidencia mixta, sin datos humanos.",
            strainsEs = emptyList(),
            interactionEs = ""
        )

        val withoutIndex = EntourageCards.cardFor(synergy)
        val withIndex = EntourageCards.cardFor(
            synergy,
            processing = EntourageProcessingIndex(listOf(processing()))
        )

        assertFalse(withoutIndex.hasProcessing)
        assertTrue(withIndex.hasProcessing)
        assertEquals(
            "adding F4 may only add lines, never reorder or drop the ones F1 " +
                "shipped",
            withoutIndex.linesEs.map { it.role }.toSet(),
            withIndex.linesEs.map { it.role }.toSet().minus(EntourageCardRole.PROCESSING)
        )
    }

    @Test
    fun theProcessingLineSitsAfterTheAgronomyLineAndBeforeTheOptionalOnes() {
        // Reading order is part of the claim: agronomy is what the plant was asked
        // for, processing is what happens to the material afterwards, and both sit
        // next to the evidence line rather than below the optional ones.
        val synergy = EntourageSynergy(
            id = "thc_myrcene",
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.MYRCENE),
            profiles = emptySet(),
            outcomeEs = "Efecto",
            descriptionEs = "Descripción",
            mechanismEs = "Mecanismo",
            evidenceEs = "Evidencia mixta, sin datos humanos.",
            strainsEs = listOf("Critical"),
            interactionEs = "Sin interacción declarada."
        )
        val card = EntourageCards.cardFor(
            synergy,
            EntourageAgronomyIndex(
                listOf(
                    EntourageAgronomy(
                        terpene = EntourageTerpene.MYRCENE,
                        responseEs = "Responde al momento de cosecha.",
                        levers = emptyList()
                    )
                )
            ),
            EntourageProcessingIndex(listOf(processing()))
        )
        val roles = card.linesEs.map { it.role }

        assertTrue(roles.indexOf(EntourageCardRole.AGRONOMY) < roles.indexOf(EntourageCardRole.PROCESSING))
        assertTrue(roles.indexOf(EntourageCardRole.PROCESSING) < roles.indexOf(EntourageCardRole.STRAINS))
        assertTrue(
            "and it stays next to the evidence line, which is the line that " +
                "disclaims the claim above it",
            roles.indexOf(EntourageCardRole.EVIDENCE) < roles.indexOf(EntourageCardRole.PROCESSING)
        )
        assertTrue("the evidence line still renders", card.linesEs.any { it.role == EntourageCardRole.EVIDENCE })
    }

    @Test
    fun anUndocumentedCompoundAddsNoCardLineRatherThanABlankOne() {
        // The asymmetry with the detail page, and the reason for it: three "no
        // data" lines next to one line of content is worse than silence, and the
        // compound's own page is where the gap is stated in full.
        val index = EntourageProcessingIndex(listOf(processing(EntourageTerpene.MYRCENE)))

        val lines = TerpeneProcessingCopy.cardLinesFor(
            listOf(EntourageTerpene.MYRCENE, EntourageTerpene.OCIMENE),
            index
        )

        assertEquals(1, lines.size)
        assertTrue(lines.first().bodyEs.isNotBlank())
    }

    /* ── The authored copy passes the language guard ────────────────────── */

    @Test
    fun theAuthoredCopyPassesTheModulesOwnWordList() {
        // `EntourageLanguage` bans `daño`, `peligro`, `inseguro`, `tóxico` and
        // `peor` as substrings, which is a real constraint on a block whose whole
        // subject is solvent residue. It is asserted here so the copy stays inside
        // it rather than the guard being loosened to fit the copy.
        val violations = EntourageLanguage.violations(guides.allTextEs())

        assertTrue("the authored processing copy used $violations", violations.isEmpty())
    }

    @Test
    fun theAuthoredCopyPromisesNoTherapeuticOutcome() {
        val forbidden = listOf(
            "cura", "trata la enfermedad", "elimina el dolor",
            "dosis recomendada", "es seguro", "deberías tomar", "te recomendamos tomar"
        )
        val text = guides.allTextEs().lowercase()

        forbidden.forEach { phrase ->
            assertTrue(
                "the authored processing copy uses \"$phrase\"",
                !text.contains(phrase)
            )
        }
    }
}