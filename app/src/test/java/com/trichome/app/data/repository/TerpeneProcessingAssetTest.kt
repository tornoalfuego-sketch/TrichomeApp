package com.trichome.app.data.repository

import com.trichome.app.model.EntourageCardRole
import com.trichome.app.model.EntourageLanguage
import com.trichome.app.model.EntourageTerpene
import com.trichome.app.model.PreservationFactorKind
import com.trichome.app.model.ProcessingGuides
import com.trichome.app.model.ProcessingMethod
import com.trichome.app.model.TerpeneProcessingCopy
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F4 — validates the shipped processing block as it sits on disk.
 *
 * The companion to `TerpeneAgronomyAssetTest`, which owns the biology. This file
 * owns the processing content and the rules F4 introduced:
 *
 * 1. **Every key resolves.** The block is keyed by terpene, method, factor and
 *    evidence level. A key the enums do not know lands in `unresolvedReferences`
 *    and is dropped, never coerced — and the method case matters more here than
 *    anywhere else in the module, because coercing an unknown method key could
 *    render a solvent-based route as if it carried no solvent.
 * 2. **Nothing reaches the screen unqualified.** A row with no entry basis is
 *    dropped whole; a note with no basis is dropped; both are recorded. This is
 *    F3's rule and it is stricter here, because an unqualified processing claim
 *    is an unqualified instruction.
 * 3. **No number in the block reads as an instruction.** Over the shipped asset,
 *    which is what a grower actually reads.
 * 4. **The asymmetry with `agronomy` is named**, so the ten rows here and the
 *    eight there cannot become accidental.
 */
class TerpeneProcessingAssetTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun assetBytes(name: String): ByteArray {
        val candidates = listOf(
            File("src/main/assets/data/$name"),
            File("app/src/main/assets/data/$name")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not locate $name. Looked in: " + candidates.joinToString { it.absolutePath })
        return file.readBytes()
    }

    private fun entourage(): EntourageBible {
        val bytes = assetBytes("entourage_data.json")
        assertEquals(
            "entourage_data.json must not start with a UTF-8 BOM",
            '{'.code,
            bytes.first().toInt()
        )
        return json.decodeFromString(String(bytes, Charsets.UTF_8))
    }

    private fun content() = entourage().toContent()

    /* ── The asset parses ────────────────────────────────────────────────── */

    @Test
    fun theAssetStillParsesWithTheProcessingBlockAdded() {
        val bible = entourage()

        assertTrue("the processing block has to exist", bible.processing.isNotEmpty())
        assertEquals("the schema version is unchanged", 1, bible.version)
    }

    @Test
    fun theProcessingBlockDroppedNoRowInTheShippedAsset() {
        val parsed = content()

        assertTrue(
            "an unresolved processing key would be dropped silently: " +
                parsed.unresolvedReferences,
            parsed.unresolvedReferences.none { it.startsWith("processing.") }
        )
        assertEquals("every shipped row resolves to an entry", bibleRows(), parsed.processing.size)
    }

    private fun bibleRows(): Int = entourage().processing.size

    /* ── Every key resolves to a real model member ───────────────────────── */

    @Test
    fun everyProcessingKeyResolvesToARealModelMember() {
        val methods = ProcessingMethod.entries.map { it.key }.toSet()
        val factors = PreservationFactorKind.entries.map { it.key }.toSet()
        val levels = com.trichome.app.model.AgronomyEvidence.entries.map { it.key }.toSet()

        entourage().processing.forEach { row ->
            assertTrue(
                "the block is keyed by terpene, so \"${row.terpene}\" has to be an " +
                    "EntourageTerpene",
                EntourageTerpene.fromKey(row.terpene) != null
            )
            assertTrue(
                "${row.terpene}: unknown evidence level \"${row.evidence}\"",
                row.evidence in levels
            )
            row.methods.forEach {
                assertTrue("${row.terpene}: unknown method \"${it.method}\"", it.method in methods)
                assertTrue("${row.terpene}/${it.method}: unknown level \"${it.evidence}\"", it.evidence in levels)
            }
            row.preservation.forEach {
                assertTrue("${row.terpene}: unknown factor \"${it.factor}\"", it.factor in factors)
                assertTrue("${row.terpene}/${it.factor}: unknown level \"${it.evidence}\"", it.evidence in levels)
            }
        }
    }

    @Test
    fun theProcessingBlockIsKeyedOncePerCompound() {
        val keys = content().processing.map { it.terpene.key }

        assertEquals(
            "a compound documented twice is two truths waiting to diverge",
            keys.size,
            keys.toSet().size
        )
    }

    /* ── Unresolved keys are dropped and recorded, never coerced ────────── */

    @Test
    fun anUnknownTerpeneKeyLandsInUnresolvedReferences() {
        val parsed = entourage().copy(
            processing = listOf(
                EntourageProcessingAsset(
                    terpene = "NOT_A_TERPENE",
                    responseEs = "Respuesta que no debe aparecer.",
                    evidence = "BIEN_DOCUMENTADO",
                    basisEs = "Base."
                )
            )
        ).toContent()

        assertEquals(0, parsed.processing.size)
        assertTrue(
            "and named: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any { it == "processing.NOT_A_TERPENE -> unknown terpene" }
        )
    }

    @Test
    fun anUnknownMethodKeyLandsInUnresolvedReferencesRatherThanBecomingASolventFreeMethod() {
        // This is the drop-and-record path with the most bite in F4. An unknown
        // method key must not be coerced to any default: the default this block
        // would otherwise reach is a solvent-based route with no residue framing,
        // which is the exact defect the phase exists to fix.
        val parsed = entourage().copy(
            processing = listOf(
                EntourageProcessingAsset(
                    terpene = "MYRCENE",
                    responseEs = "Respuesta.",
                    evidence = "BIEN_DOCUMENTADO",
                    basisEs = "Base.",
                    methods = listOf(
                        EntourageProcessingMethodAsset(
                            method = "EXTRACCION_EN_AGUA",
                            detailEs = "Detalle.",
                            evidence = "MIXTO",
                            basisEs = "Base."
                        )
                    )
                )
            )
        ).toContent()

        assertEquals(1, parsed.processing.size)
        assertEquals("the note must be dropped", 0, parsed.processing.first().methods.size)
        assertTrue(
            "and named: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any {
                it == "processing.MYRCENE.methods -> EXTRACCION_EN_AGUA"
            }
        )
    }

    @Test
    fun anUnknownPreservationFactorKeyLandsInUnresolvedReferences() {
        val parsed = entourage().copy(
            processing = listOf(
                EntourageProcessingAsset(
                    terpene = "MYRCENE",
                    responseEs = "Respuesta.",
                    evidence = "BIEN_DOCUMENTADO",
                    basisEs = "Base.",
                    preservation = listOf(
                        EntourageProcessingFactorAsset(
                            factor = "HUMEDAD",
                            detailEs = "Detalle.",
                            evidence = "MIXTO",
                            basisEs = "Base."
                        )
                    )
                )
            )
        ).toContent()

        assertEquals(0, parsed.processing.first().preservation.size)
        assertTrue(
            "and named: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any {
                it == "processing.MYRCENE.preservation -> HUMEDAD"
            }
        )
    }

    @Test
    fun anUnknownEvidenceLevelLandsInUnresolvedReferencesRatherThanBecomingADefault() {
        val parsed = entourage().copy(
            processing = listOf(
                EntourageProcessingAsset(
                    terpene = "MYRCENE",
                    responseEs = "Respuesta.",
                    evidence = "BIEN_DOCUMENTADO",
                    basisEs = "Base.",
                    methods = listOf(
                        EntourageProcessingMethodAsset(
                            method = "SOLVENT_EXTRACTION",
                            detailEs = "Detalle.",
                            evidence = "SEGURO",
                            basisEs = "Base."
                        )
                    ),
                    preservation = listOf(
                        EntourageProcessingFactorAsset(
                            factor = "OXIDATION",
                            detailEs = "Detalle.",
                            evidence = "SEGURA",
                            basisEs = "Base."
                        )
                    )
                )
            )
        ).toContent()

        val entry = parsed.processing.first()
        assertEquals("both notes must be dropped", 0, entry.methods.size)
        assertEquals(0, entry.preservation.size)
        assertEquals(2, parsed.unresolvedReferences.count { it.startsWith("processing.MYRCENE.") })
        assertTrue(
            "and both named: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any { it == "processing.MYRCENE.SOLVENT_EXTRACTION -> SEGURO" } &&
                parsed.unresolvedReferences.any { it == "processing.MYRCENE.OXIDATION -> SEGURA" }
        )
    }

    @Test
    fun aNoteWithNoBasisIsDroppedAndReportedRatherThanShownUnqualified() {
        val parsed = entourage().copy(
            processing = listOf(
                EntourageProcessingAsset(
                    terpene = "MYRCENE",
                    responseEs = "Respuesta.",
                    evidence = "BIEN_DOCUMENTADO",
                    basisEs = "Base.",
                    methods = listOf(
                        EntourageProcessingMethodAsset(
                            method = "SOLVENT_EXTRACTION",
                            detailEs = "Recupera más material total.",
                            evidence = "BIEN_DOCUMENTADO",
                            basisEs = "   "
                        )
                    ),
                    preservation = listOf(
                        EntourageProcessingFactorAsset(
                            factor = "TIME",
                            detailEs = "  ",
                            evidence = "MIXTO",
                            basisEs = "Base."
                        )
                    )
                )
            )
        ).toContent()

        val entry = parsed.processing.first()
        assertEquals(0, entry.methods.size)
        assertEquals(0, entry.preservation.size)
        assertEquals(2, parsed.unresolvedReferences.count { it.startsWith("processing.MYRCENE.") })
        assertTrue(
            "the basis reason has to be legible in the integrity notice: " +
                parsed.unresolvedReferences,
            parsed.unresolvedReferences.any { it.contains("basis_es") }
        )
        assertTrue(
            "and so does the missing-detail reason: " + parsed.unresolvedReferences,
            parsed.unresolvedReferences.any { it.contains("no detail declared") }
        )
    }

    @Test
    fun aRowWithNoEntryBasisIsDroppedWholeRatherThanDegradingToAnUnqualifiedClaim() {
        // The path F3 did not have, and the one that matters most here: the
        // compound's own summary sentence would still be on screen with nothing
        // behind it, so the row goes rather than degrading.
        val parsed = entourage().copy(
            processing = listOf(
                EntourageProcessingAsset(
                    terpene = "MYRCENE",
                    responseEs = "El mirceno se va pronto al calentar el material.",
                    evidence = "BIEN_DOCUMENTADO",
                    basisEs = "   "
                )
            )
        ).toContent()

        assertEquals(0, parsed.processing.size)
        assertTrue(
            "and named legibly: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any {
                it == "processing.MYRCENE -> the row ships no entry basis_es and was " +
                    "dropped rather than shown unqualified"
            }
        )
    }

    @Test
    fun aRowWithNoEntryEvidenceIsDroppedRatherThanBecomingAnUndeclaredLevel() {
        val parsed = entourage().copy(
            processing = listOf(
                EntourageProcessingAsset(
                    terpene = "MYRCENE",
                    responseEs = "Respuesta.",
                    evidence = "CLARO",
                    basisEs = "Base."
                )
            )
        ).toContent()

        assertEquals(0, parsed.processing.size)
        assertTrue(
            "and named: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any { it == "processing.MYRCENE -> CLARO" }
        )
    }

    @Test
    fun aRowWithNoResponseIsDroppedRatherThanRenderedAsAnEmptyBlock() {
        val parsed = entourage().copy(
            processing = listOf(
                EntourageProcessingAsset(
                    terpene = "MYRCENE",
                    responseEs = "",
                    evidence = "BIEN_DOCUMENTADO",
                    basisEs = "Base."
                )
            )
        ).toContent()

        assertEquals(0, parsed.processing.size)
        assertTrue(
            parsed.unresolvedReferences.any { it == "processing.MYRCENE -> no response declared" }
        )
    }

    /* ── The science-word guards ────────────────────────────────────────── */

    @Test
    fun noProcessingStringPromisesATherapeuticOutcome() {
        // The F1 list, verbatim. Not loosened: `cura` is a substring ban, so
        // `curado` would be out and the copy says `secado` instead.
        val forbidden = listOf(
            "cura", "trata la enfermedad", "elimina el dolor",
            "dosis recomendada", "es seguro", "deberías tomar", "te recomendamos tomar"
        )

        content().processing.forEach { entry ->
            val text = entry.allTextEs.lowercase()
            forbidden.forEach { phrase ->
                assertTrue("${entry.terpene.key} uses \"$phrase\"", !text.contains(phrase))
            }
        }
    }

    @Test
    fun noProcessingStringBreaksTheModulesOwnWordList() {
        val violations = EntourageLanguage.violations(
            content().processing.joinToString(" ") { it.allTextEs }
        )

        assertTrue("the processing block used $violations", violations.isEmpty())
    }

    /* ── The refusal: no instruction number ships ───────────────────────── */

    @Test
    fun noProcessingNumberReadsAsAnInstruction() {
        // F4's refusal, asserted over what a grower actually reads. A temperature,
        // a duration or a quantity in this block would be a setpoint, and F4's
        // whole premise is that it prints none.
        val patterns = listOf(
            Regex("""\d+\s*°C"""),
            Regex("""\d+\s*(?:min|minutos?|hora|horas?|d[ií]as?|dias?|semanas?|segundos?)\b""", RegexOption.IGNORE_CASE),
            Regex("""\d+\s*(?:g|gr|mg|ml|kg)\b""", RegexOption.IGNORE_CASE),
            Regex("""\d+\s*%"""),
            Regex("""\b(?:usa|usar|use|aplicar|calienta|calentar|hornea|hornear|mantén|mantener)\s+\d+""", RegexOption.IGNORE_CASE)
        )

        content().processing.forEach { entry ->
            patterns.forEach { pattern ->
                val hit = pattern.find(entry.allTextEs)
                assertFalse(
                    "${entry.terpene.key} ships \"${hit?.value}\", which reads as " +
                        "an instruction rather than a description",
                    hit != null
                )
            }
        }
    }

    @Test
    fun everyBasisStatesWhatItCannotEstablish() {
        content().processing.forEach { entry ->
            val disclaimers = listOf(
                "no está", "no hay", "no existe", "no se", "no lo", "no la",
                "no puede", "no se puede", "no está fijado", "no está fijada",
                "no publica", "no se traslada", "no las traslada"
            )
            fun check(what: String, basis: String) {
                assertTrue(
                    "$what does not say what it cannot establish: \"$basis\"",
                    disclaimers.any { it in basis.lowercase() }
                )
                assertTrue("$what has a one-word basis", basis.length > 80)
            }
            check("${entry.terpene.key} entry basis", entry.basisEs)
            entry.methods.forEach { check("${entry.terpene.key}/${it.method.key}", it.basisEs) }
            entry.preservation.forEach { check("${entry.terpene.key}/${it.factor.key}", it.basisEs) }
        }
    }

    /* ── Every method and factor is represented ─────────────────────────── */

    @Test
    fun everyMethodTheModuleExplainsIsDocumentedForEveryCompound() {
        // A shared guide no compound ever reaches is authored text nothing shows.
        content().processing.forEach { entry ->
            assertEquals(
                "${entry.terpene.key} documents a subset of the methods; the " +
                    "comparison names all three, so a missing note has to be argued " +
                    "in the asset rather than left silent",
                ProcessingMethod.entries.toSet(),
                entry.documentedMethods
            )
        }
    }

    @Test
    fun everyPreservationFactorTheModuleExplainsIsRepresented() {
        val used = content().processing.flatMap { it.documentedFactors }.toSet()

        assertEquals(PreservationFactorKind.entries.toSet(), used)
    }

    @Test
    fun theSolventRouteIsDocumentedForEveryCompoundSoNoPageNamesItWithoutTheResidueLine() {
        // If a compound shipped no SOLVENT_EXTRACTION note, its page would still
        // show the shared comparison — which names the method — so the note's
        // absence would be silent. Requiring it keeps the two surfaces in step.
        content().processing.forEach { entry ->
            assertNotNullSafe(
                "${entry.terpene.key} documents no solvent-extraction note, yet its " +
                    "page will name the method through the shared comparison",
                entry.methodFor(ProcessingMethod.SOLVENT_EXTRACTION)
            )
        }
    }

    private fun assertNotNullSafe(why: String, value: Any?) {
        assertTrue(why, value != null)
    }

    /* ── What each row says, and that the two halves disagree ───────────── */

    @Test
    fun theVolatileAndTheHeavyCompoundsDisagreeAboutWhatEachMethodDoes() {
        // The trade-off is only honest in both directions: a volatile monoterpene
        // leaves early and a heavy sesquiterpene does not. A block where every row
        // said the same thing would be describing one compound ten times.
        val volatile = content().processing.filter {
            it.terpene in setOf(
                EntourageTerpene.LIMONENE,
                EntourageTerpene.ALPHA_PINENE,
                EntourageTerpene.OCIMENE
            )
        }
        val heavy = content().processing.filter {
            it.terpene == EntourageTerpene.BETA_CARYOPHYLLENE
        }

        assertTrue("no volatile compound is documented", volatile.isNotEmpty())
        assertTrue("no heavy compound is documented", heavy.isNotEmpty())

        val solventNote = ProcessingMethod.SOLVENT_EXTRACTION
        volatile.forEach { entry ->
            val detail = entry.methodFor(solventNote)?.detailEs.orEmpty().lowercase()
            assertTrue(
                "${entry.terpene.key} is volatile and its solvent note has to say it " +
                    "leaves during the separation step",
                detail.contains("se va") || detail.contains("pierde") || detail.contains("sigue")
            )
        }
        heavy.forEach { entry ->
            val detail = entry.methodFor(solventNote)?.detailEs.orEmpty().lowercase()
            assertTrue(
                "${entry.terpene.key} is the least volatile compound and its solvent " +
                    "note has to say it is among the last to go",
                detail.contains("últimos") || detail.contains("aguanta") ||
                    detail.contains("sobrevive") || detail.contains("menos se pierde")
            )
        }
    }

    @Test
    fun theDecarboxylationNoteStatesWhatIsLostAndWhatForms() {
        // Both halves, on every row: the volatile fraction leaves, and something
        // that is no longer the original compound forms in its place. A note that
        // only said "part of it goes" would not tell a reader what they end up with.
        content().processing.forEach { entry ->
            val detail = entry.methodFor(ProcessingMethod.DECARBOXYLATION)?.detailEs.orEmpty().lowercase()
            assertTrue(
                "${entry.terpene.key}: the decarboxylation note has to say what is lost",
                detail.contains("se va") || detail.contains("sale") || detail.contains("irse") ||
                    detail.contains("pierde") || detail.contains("cae") || detail.contains("aguanta")
            )
            assertTrue(
                "${entry.terpene.key}: and what forms in its place",
                detail.contains("óxido") || detail.contains("productos") ||
                    detail.contains("firma") || detail.contains("proporción") ||
                    detail.contains("desplaza") || detail.contains("sube") ||
                    detail.contains("queda")
            )
        }
    }

    @Test
    fun theHeatFactorTellsTheReaderThatAVolatilityWindowIsNotAShelfLife() {
        // F2's lesson, reused rather than reinvented: a temperature window says
        // where a compound is useful in a device, not how long it survives in a
        // jar. The HEAT guide says it, and every compound's HEAT note has to reach
        // a reader who will otherwise read the card above as the answer.
        val heatGuide = ProcessingGuides.factorFor(PreservationFactorKind.HEAT).whatEs.lowercase()
        assertTrue(
            "the shared heat guide has to make the distinction explicit",
            heatGuide.contains("vaporizador") && heatGuide.contains("almacenamiento")
        )

        val rowsWithHeat = content().processing.filter {
            it.factorFor(PreservationFactorKind.HEAT) != null
        }
        assertTrue("no compound documents the heat factor", rowsWithHeat.isNotEmpty())
        rowsWithHeat.forEach { entry ->
            val detail = entry.factorFor(PreservationFactorKind.HEAT)?.detailEs.orEmpty().lowercase()
            assertTrue(
                "${entry.terpene.key}/HEAT: the note has to say the window above is " +
                    "about a device, not about storage, or a reader takes the " +
                    "temperature as a preservation answer",
                detail.contains("vaporizador") || detail.contains("vaporización")
            )
        }
    }

    @Test
    fun noCompoundInTheBlockStatesAShelfLife() {
        // F2's `LIMITS_ES` established that a window says nothing about how long a
        // compound survives. F4 is the block where a reader is most likely to want
        // one, so the refusal is asserted rather than trusted.
        //
        // The list is deliberately **comparative-proof**. "aguanta" and "dura" on
        // their own are not shelf-life claims — "aguanta más que los monoterpenos"
        // and "durante el secado" are the module's own comparative and temporal
        // vocabulary — and an earlier version of this test failed on both. What is
        // banned is a claim that a compound *lasts* something, unattached to a
        // comparison: an invented life shelf.
        val forbidden = listOf(
            "vida útil de",
            "vida útil para el",
            "conservación de",
            "se conserva ",
            "guardar durante",
            "durante meses",
            "durante semanas",
            "durante días",
            "durante meses o"
        )

        content().processing.forEach { entry ->
            val text = entry.allTextEs.lowercase()
            forbidden.forEach { phrase ->
                assertTrue(
                    "${entry.terpene.key} states a shelf life (\"$phrase\"), which " +
                        "F2's own limits line says this module cannot know",
                    !text.contains(phrase)
                )
            }
        }
        // And the positive half: the refusal has to be stated, not merely absent.
        // A reader who wants a shelf life needs to be told the module has none.
        val timeBases = content().processing.map { it.factorFor(PreservationFactorKind.TIME) }
        assertTrue("no compound documents the time factor", timeBases.all { it != null })
        val refusals = timeBases.count { note ->
            note!!.basisEs.contains("no existe") || note.basisEs.contains("no publica")
        }
        assertTrue(
            "every time note has to say that no shelf life is published; only " +
                "$refusals of ${timeBases.size} do",
            refusals == timeBases.size
        )
    }

    /* ── The block reaches both surfaces ────────────────────────────────── */

    @Test
    fun everyDocumentedEntryNamesItsCompoundAndEveryNoteCarriesItsLevelAndBasis() {
        content().processing.forEach { entry ->
            assertTrue("${entry.terpene.key} has no response sentence", entry.responseEs.isNotBlank())
            assertTrue(
                "${entry.terpene.key} has no entry basis, so its own claim would " +
                    "reach the screen unqualified",
                entry.basisEs.isNotBlank()
            )
            assertTrue(
                "${entry.terpene.key} documents no method and no factor, so the " +
                    "entry is indistinguishable from an empty one",
                entry.methods.isNotEmpty() && entry.preservation.isNotEmpty()
            )
            assertEquals(
                "${entry.terpene.key} repeats a method",
                entry.methods.size,
                entry.methods.map { it.method }.toSet().size
            )
            assertEquals(
                "${entry.terpene.key} repeats a factor",
                entry.preservation.size,
                entry.preservation.map { it.factor }.toSet().size
            )
        }
    }

    @Test
    fun everyLevelAndBasisReachesTheCardBodyVerbatim() {
        // The F1 inline-qualifier rule on F4's data: the label a screen prints has
        // to be the level the note declares, and the basis has to travel with it.
        content().processing.forEach { entry ->
            val built = TerpeneProcessingCopy.contentOf(entry.terpene, entry)
            val body = TerpeneProcessingCopy.cardLineEs(entry)

            assertEquals(
                "${entry.terpene.key}: the content model must not drop or add a method",
                entry.methods.map { it.method },
                built.methods.map { it.method }
            )
            assertEquals(
                "${entry.terpene.key}: nor a factor",
                entry.preservation.map { it.factor },
                built.preservation.map { it.factor }
            )
            entry.methods.zip(built.methods).forEach { (declared, shown) ->
                assertEquals(
                    "${entry.terpene.key}/${declared.method.key}: the label a screen " +
                        "prints must be the level the note declares",
                    declared.evidence.labelEs,
                    shown.evidenceLabelEs
                )
                assertEquals(declared.basisEs, shown.basisEs)
            }
            entry.preservation.zip(built.preservation).forEach { (declared, shown) ->
                assertEquals(declared.evidence.labelEs, shown.evidenceLabelEs)
                assertEquals(declared.basisEs, shown.basisEs)
            }
            assertTrue(
                "${entry.terpene.key}: its summary claim has to reach the card body",
                body.contains(entry.responseEs)
            )
            assertTrue(
                "${entry.terpene.key}: and its level",
                body.contains(entry.evidence.labelEs)
            )
            assertTrue(
                "${entry.terpene.key}: and its basis",
                body.contains(entry.basisEs)
            )
        }
    }

    @Test
    fun theBlockIsReachableFromTheShippedCombinations() {
        // Keying by terpene (decision D-B) means the entry is reachable from the
        // card *and* from the compound's own page. The first half is the reason
        // keying on the pair was declined, so it is asserted against the asset.
        val index = content().processingIndex()
        val withProcessing = content().synergies.filter { synergy ->
            TerpeneProcessingCopy.cardLinesFor(synergy.terpenes, index).isNotEmpty()
        }

        assertTrue(
            "no shipped synergy carries a processing line, which is the " +
                "orphaning failure D-B was written against",
            withProcessing.isNotEmpty()
        )
        withProcessing.forEach { synergy ->
            val card = com.trichome.app.model.EntourageCards.cardFor(
                synergy,
                processing = index
            )
            card.linesEs.filter { it.role == EntourageCardRole.PROCESSING }.forEach { line ->
                assertFalse("a blank processing line", line.bodyEs.isBlank())
                assertTrue(
                    "a card line that names the methods has to carry the residue " +
                        "sentence in the same body",
                    line.bodyEs.contains("grado alimentario", ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun theProcessingBlockCoversEveryCompoundF3LeftOut() {
        // The asymmetry, named. F3 left Camphene and Terpinolene out of
        // `agronomy` because a palanca agronómica is a response of the *plant* and
        // neither has one documented. F4's question is different — what happens to
        // the *compound* under a named method — and that covers all ten. If somebody
        // removes one of these two rows, this test fails and they have to argue
        // which question they were answering.
        val agronomyIndex = content().agronomyIndex()
        val processingIndex = content().processingIndex()

        assertEquals(
            "F3's two gaps are pinned here so the two blocks cannot drift apart",
            setOf(EntourageTerpene.CAMPHENE, EntourageTerpene.TERPINOLENE),
            agronomyIndex.undocumentedTerpenes
        )
        assertEquals(
            "F4 covers every module compound, and the two F3 left out have to " +
                "say why in their own basis rather than look like an oversight",
            emptySet<EntourageTerpene>(),
            processingIndex.undocumentedTerpenes
        )
        setOf(EntourageTerpene.CAMPHENE, EntourageTerpene.TERPINOLENE).forEach { terpene ->
            val entry = processingIndex.forTerpene(terpene)
            assertTrue("${terpene.key} has no processing entry", entry != null)
            assertTrue(
                "${terpene.key} is documented for processing while F3 documented " +
                    "no agronomic lever for it, so its entry has to say that the " +
                    "question is different — otherwise a reader sees two blocks " +
                    "that appear to contradict each other",
                entry!!.basisEs.contains("agronómic") || entry.basisEs.contains("ficha de agronomía")
            )
        }
    }

    @Test
    fun theIndexAndTheListAreOneSourceOfTruth() {
        val parsed = content()
        val index = parsed.processingIndex()

        assertEquals(parsed.processing.size, index.size)
        assertEquals(
            "the index is a view over the parsed list, never a second parse",
            parsed.processing.map { it.terpene }.toSet(),
            index.all().map { it.terpene }.toSet()
        )
        assertEquals(parsed.processing.toSet(), index.all().toSet())
        assertEquals(
            index.all().map { it.terpene.key }.sorted(),
            index.all().map { it.terpene.key }
        )
    }

    @Test
    fun addingProcessingDidNotTouchAnythingF1ToF3Shipped() {
        // Regression guard on the rest of the parse: F4 added one collection and
        // the earlier phases' guarantees have to still hold.
        val parsed = content()

        assertEquals("the measured bands are untouched", 10, parsed.vaporisation.size)
        assertEquals(
            "the profile library is untouched: F11 grew it, so the guard is that " +
                "the enum and the file still agree rather than a literal count",
            com.trichome.app.model.PharmacologicalProfile.entries.size,
            parsed.profiles.size
        )
        assertEquals("the synergies are untouched", 7, parsed.synergies.size)
        // F5 grew the quiz, so the count is no longer the guard; the guard's real
        // intent is "F4 did not rewrite what F1 to F3 shipped", which survives by
        // identity rather than by total. See the same line in
        // `TerpeneAgronomyAssetTest`.
        val originalQuiz = setOf(
            "q1_cariofileno_cb2", "q2_limonene_ansiedad", "q3_pineno_acetilcolina",
            "q4_mirceno_hipotesis", "q5_cbn_afinidad", "q6_limonene_temperatura",
            "q7_cariofileno_temperatura", "q8_cbd_trpv1", "q9_interaccion_cbd",
            "q10_evidencia_entourage"
        )
        assertTrue(
            "F1's ten questions have to survive verbatim",
            parsed.questions.map { it.id }.containsAll(originalQuiz)
        )
        assertEquals("the agronomy block is untouched", 8, parsed.agronomy.size)
        assertTrue(
            "a schema-version bump or a dropped row would show up here",
            parsed.unresolvedReferences.isEmpty()
        )
    }
}