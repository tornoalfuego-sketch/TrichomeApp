package com.trichome.app.data.repository

import com.trichome.app.model.AgronomyEvidence
import com.trichome.app.model.AgronomyLeverKind
import com.trichome.app.model.BiosynthesisExplainer
import com.trichome.app.model.EntourageAgronomy
import com.trichome.app.model.EntourageAgronomyIndex
import com.trichome.app.model.EntourageCards
import com.trichome.app.model.EntourageLanguage
import com.trichome.app.model.EntourageTerpene
import com.trichome.app.model.TerpeneAgronomyCopy
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F3 — validates the shipped agronomy block as it sits on disk.
 *
 * The companion to `EntourageAssetTest`, which owns the pharmacology. This file
 * owns the biology and the wording rules F3 introduced:
 *
 * 1. **Every agronomy key resolves.** The block is keyed by terpene, by lever
 *    kind and by evidence level. A key the enums do not know must land in
 *    `unresolvedReferences` and be dropped, never coerced to a default that would
 *    render a label with nothing behind it.
 * 2. **A lever with no `basis_es` is dropped, not shown.** This is the one the
 *    honesty standard turns on: an agronomy claim whose evidence level is
 *    missing is exactly what this module exists to not ship, so the mapper
 *    refuses it and says so in the integrity notice.
 * 3. **The science-word guards pass on the new content.** The same forbidden
 *    phrases F1 banned, plus the module's own `EntourageLanguage` list. Neither
 *    guard is loosened for F3 — which is why the cultivation copy says
 *    `secado` and `almacenamiento` rather than `curado`, and `lesiones` rather
 *    than `daño`: `cura` and `daño` are banned as substrings and that is a
 *    constraint, not a preference.
 * 4. **A compound with no entry gets the honest sentence.** Verified against the
 *    shipped block, not a fixture, so the gap has to be real for the test to mean
 *    anything.
 */
class TerpeneAgronomyAssetTest {

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

    /* ── The asset parses ─────────────────────────────────────────────────── */

    @Test
    fun theAssetStillParsesWithTheAgronomyBlockAdded() {
        val bible = entourage()

        assertTrue("the agronomy block has to exist", bible.agronomy.isNotEmpty())
        assertEquals("the schema version is unchanged", 1, bible.version)
    }

    @Test
    fun theAgronomyBlockDroppedNoRowInTheShippedAsset() {
        val content = content()

        assertTrue(
            "an unresolved agronomy key would be dropped silently: ${content.unresolvedReferences}",
            content.unresolvedReferences.none { it.startsWith("agronomy.") }
        )
        assertEquals(bibleAgronomyRows(), content.agronomy.size)
    }

    private fun bibleAgronomyRows(): Int = entourage().agronomy.size

    /* ── Every key resolves to a real model member ────────────────────────── */

    @Test
    fun everyAgronomyKeyResolvesToARealEntourageTerpene() {
        entourage().agronomy.forEach { row ->
            assertTrue(
                "the block is keyed by terpene, so \"${row.terpene}\" has to be an " +
                    "EntourageTerpene",
                EntourageTerpene.fromKey(row.terpene) != null
            )
        }
        content().agronomy.forEach { entry ->
            assertTrue(
                "${entry.terpene.key} produced an entry outside the enum",
                entry.terpene in EntourageTerpene.entries
            )
        }
    }

    @Test
    fun everyLeverKeyAndEveryEvidenceKeyResolves() {
        val kinds = AgronomyLeverKind.entries.map { it.key }.toSet()
        val levels = AgronomyEvidence.entries.map { it.key }.toSet()

        entourage().agronomy.forEach { row ->
            row.levers.forEach { lever ->
                assertTrue("unknown lever kind \"${lever.kind}\"", lever.kind in kinds)
                assertTrue("unknown evidence level \"${lever.evidence}\"", lever.evidence in levels)
            }
        }
    }

    @Test
    fun theAgronomyBlockIsKeyedOncePerCompound() {
        val keys = content().agronomy.map { it.terpene.key }

        assertEquals("a compound documented twice is two truths waiting to diverge", keys.size, keys.toSet().size)
    }

    /* ── Unresolved keys are reported, never coerced ──────────────────────── */

    @Test
    fun anUnknownTerpeneKeyLandsInUnresolvedReferencesRatherThanBeingCoerced() {
        val mutated = entourage().copy(
            agronomy = listOf(
                EntourageAgronomyAsset(
                    terpene = "NOT_A_TERPENE",
                    responseEs = "Respuesta que no debe aparecer.",
                    levers = emptyList()
                )
            )
        )

        val parsed = mutated.toContent()

        assertEquals("the row must be dropped", 0, parsed.agronomy.size)
        assertTrue(
            "and named: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any {
                it == "agronomy.NOT_A_TERPENE -> unknown terpene"
            }
        )
    }

    @Test
    fun anUnknownLeverKindLandsInUnresolvedReferencesRatherThanBeingCoerced() {
        val mutated = entourage().copy(
            agronomy = listOf(
                EntourageAgronomyAsset(
                    terpene = "MYRCENE",
                    responseEs = "Respuesta.",
                    levers = listOf(
                        EntourageAgronomyLeverAsset(
                            kind = "ROTOR_DE_LUCES",
                            detailEs = "Detalle.",
                            evidence = "MIXTO",
                            basisEs = "Base."
                        )
                    )
                )
            )
        )

        val parsed = mutated.toContent()

        assertEquals("the lever must be dropped", 1, parsed.agronomy.size)
        assertEquals(0, parsed.agronomy.first().levers.size)
        assertTrue(
            "and named: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any {
                it == "agronomy.MYRCENE.levers -> ROTOR_DE_LUCES"
            }
        )
    }

    @Test
    fun anUnknownEvidenceLevelLandsInUnresolvedReferencesRatherThanBecomingADefault() {
        val mutated = entourage().copy(
            agronomy = listOf(
                EntourageAgronomyAsset(
                    terpene = "MYRCENE",
                    responseEs = "Respuesta.",
                    levers = listOf(
                        EntourageAgronomyLeverAsset(
                            kind = "HARVEST_POINT",
                            detailEs = "Detalle.",
                            evidence = "SEGURO",
                            basisEs = "Base."
                        )
                    )
                )
            )
        )

        val parsed = mutated.toContent()

        assertEquals(0, parsed.agronomy.first().levers.size)
        assertTrue(
            "and named: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any {
                it == "agronomy.MYRCENE.HARVEST_POINT -> SEGURO"
            }
        )
    }

    @Test
    fun aLeverWithNoBasisIsDroppedAndReportedRatherThanShownUnqualified() {
        val mutated = entourage().copy(
            agronomy = listOf(
                EntourageAgronomyAsset(
                    terpene = "MYRCENE",
                    responseEs = "Respuesta.",
                    levers = listOf(
                        EntourageAgronomyLeverAsset(
                            kind = "HARVEST_POINT",
                            detailEs = "Cosecha antes, siempre.",
                            evidence = "BIEN_DOCUMENTADO",
                            basisEs = "   "
                        ),
                        EntourageAgronomyLeverAsset(
                            kind = "UV_B",
                            detailEs = "   ",
                            evidence = "MIXTO",
                            basisEs = "Base."
                        )
                    )
                )
            )
        )

        val parsed = mutated.toContent()

        assertEquals(
            "both levers are unqualified in some way and both must be dropped",
            0,
            parsed.agronomy.first().levers.size
        )
        assertEquals(2, parsed.unresolvedReferences.count { it.startsWith("agronomy.MYRCENE.") })
        assertTrue(
            "the reason has to be legible in the integrity notice: ${parsed.unresolvedReferences}",
            parsed.unresolvedReferences.any { it.contains("basis_es") }
        )
    }

    @Test
    fun anEntryWithNoResponseIsDroppedRatherThanRenderedAsAnEmptyBlock() {
        val mutated = entourage().copy(
            agronomy = listOf(
                EntourageAgronomyAsset(
                    terpene = "MYRCENE",
                    responseEs = "",
                    levers = emptyList()
                )
            )
        )

        val parsed = mutated.toContent()

        assertEquals(0, parsed.agronomy.size)
        assertTrue(
            parsed.unresolvedReferences.any {
                it == "agronomy.MYRCENE -> no response declared"
            }
        )
    }

    /* ── The science-word guards still pass on the new content ────────────── */

    @Test
    fun noAgronomyStringPromisesATherapeuticOutcome() {
        // The F1 list, verbatim. Not loosened: `cura` is a substring ban, so
        // `curado` is out and the copy says `secado` instead.
        val forbidden = listOf(
            "cura", "trata la enfermedad", "elimina el dolor",
            "dosis recomendada", "es seguro", "deberías tomar", "te recomendamos tomar"
        )

        content().agronomy.forEach { entry ->
            entry.allTextEs.lowercase().let { text ->
                forbidden.forEach { phrase ->
                    assertTrue(
                        "${entry.terpene.key} uses '$phrase'",
                        !text.contains(phrase)
                    )
                }
            }
        }
    }

    @Test
    fun noAgronomyStringBreaksTheModulesOwnWordList() {
        val forbidden = com.trichome.app.model.EntourageLanguage.violations(
            content().agronomy.joinToString(" ") { it.allTextEs }
        )

        assertTrue("the agronomy block used $forbidden", forbidden.isEmpty())
    }

    /* ── The content itself ───────────────────────────────────────────────── */

    @Test
    fun everyDocumentedEntryNamesItsCompoundAndEveryLeverCarriesABasis() {
        content().agronomy.forEach { entry ->
            assertTrue(
                "${entry.terpene.key} has no response sentence",
                entry.responseEs.isNotBlank()
            )
            assertTrue(
                "${entry.terpene.key} has no lever at all, so the entry is " +
                    "indistinguishable from an empty one",
                entry.levers.isNotEmpty()
            )
            entry.levers.forEach { lever ->
                assertTrue("${entry.terpene.key}/${lever.kind} has no detail", lever.detailEs.isNotBlank())
                assertTrue(
                    "${entry.terpene.key}/${lever.kind} has no basis sentence, so " +
                        "the claim would reach the screen unqualified",
                    lever.basisEs.isNotBlank()
                )
                assertTrue(
                    "${entry.terpene.key}/${lever.kind} has a one-word basis",
                    lever.basisEs.length > 40
                )
            }
            assertEquals(
                "${entry.terpene.key} repeats a lever kind",
                entry.levers.size,
                entry.levers.map { it.kind }.toSet().size
            )
        }
    }

    @Test
    fun everyLeverStatesItsEvidenceLevelWhereTheUserCanSeeIt() {
        // The level is rendered as its own label on the detail page and folded
        // into the card body, so it never has to appear inside the sentence — but
        // it does have to be somewhere, and the label a screen prints must be
        // the level the lever declares.
        content().agronomy.forEach { entry ->
            val built = TerpeneAgronomyCopy.contentOf(entry.terpene, entry)
            val body = TerpeneAgronomyCopy.cardLineEs(entry)

            assertEquals(
                "the content model must not drop or add a lever",
                entry.levers.map { it.kind },
                built.levers.map { it.kind }
            )
            entry.levers.zip(built.levers).forEach { (declared, shown) ->
                assertEquals(
                    "${entry.terpene.key}/${declared.kind}",
                    declared.evidence.labelEs,
                    shown.evidenceLabelEs
                )
                assertTrue(
                    "${entry.terpene.key}/${declared.kind}: the level has to be in " +
                        "the card body too",
                    body.contains(declared.evidence.labelEs)
                )
                assertTrue(
                    "${entry.terpene.key}/${declared.kind}: and the basis has to be " +
                        "in the card body",
                    body.contains(declared.basisEs)
                )
                assertEquals(declared.basisEs, shown.basisEs)
            }
        }
    }

    @Test
    fun aLeverWhoseBasisDisclaimsWhatItCannotShowCarriesItsQualifierIntoTheSameBlock() {
        // F1's inline-qualifier rule, applied to agronomy. A lever whose own
        // basis says what it cannot establish must print that basis and its level
        // in the same rendered block, not one on a page and one on a card.
        val disclaims = listOf(
            "no hay", "no está", "no se ha", "no se puede", "no está fijad",
            "no está fijado", "sin curva", "no una", "depende", "escaso"
        )

        val checked = content().agronomy.flatMap { entry ->
            entry.levers.map { entry to it }
        }.filter { (_, lever) -> disclaims.any { it in lever.basisEs.lowercase() } }

        assertTrue(
            "the fixture must actually contain disclaiming levers, or this test " +
                "proves nothing",
            checked.isNotEmpty()
        )
        checked.forEach { (entry, lever) ->
            val body = TerpeneAgronomyCopy.cardLineEs(entry)
            assertTrue(
                "${entry.terpene.key}/${lever.kind}: the disclaimer has to be in the " +
                    "card body",
                body.contains(lever.basisEs)
            )
            assertTrue(
                "${entry.terpene.key}/${lever.kind}: and its qualifier with it",
                body.contains(lever.evidence.labelEs)
            )
        }
    }

    @Test
    fun everyLeverKindTheModuleExplainsIsRepresentedInTheShippedBlock() {
        // The three grow-out guides have to be reachable from somewhere, or they
        // are authored text no compound ever shows.
        val used = content().agronomy.flatMap { it.documentedKinds }.toSet()

        assertEquals(AgronomyLeverKind.entries.toSet(), used)
    }

    @Test
    fun everyDocumentedCompoundAnswersTheHarvestPointBecauseEveryOneOfThemMoves() {
        // The harvest-point lever is the one claim that applies to all ten module
        // compounds in principle. If a documented entry omits it, the omission is
        // a content decision and has to be argued in the asset, not made silently.
        content().agronomy.forEach { entry ->
            assertTrue(
                "${entry.terpene.key} documents levers but not the harvest point, " +
                    "which is the one that applies to every compound in the module",
                AgronomyLeverKind.HARVEST_POINT in entry.documentedKinds
            )
        }
    }

    @Test
    fun theMonoterpeneAndSesquiterpeneEntriesDisagreeAboutTheHarvestPoint() {
        // The mono/sesqui shift is only honest if both directions are present: a
        // volatile monoterpene falls with maturity while the heavier
        // sesquiterpenes gain. A block where both said the same thing would be
        // describing one family.
        val mono = content().agronomy.filter { !it.terpene.isSesquiterpene }
        val sesqui = content().agronomy.filter { it.terpene.isSesquiterpene }

        assertTrue("no monoterpene is documented", mono.isNotEmpty())
        assertTrue("no sesquiterpene is documented", sesqui.isNotEmpty())
        assertTrue(
            "a sesquiterpene has to state that it gains with maturity",
            sesqui.any { it.levers.first { l -> l.kind == AgronomyLeverKind.HARVEST_POINT }.detailEs.contains("gana peso relativo") }
        )
        assertTrue(
            "a monoterpene has to state that it falls with maturity",
            mono.any { it.levers.first { l -> l.kind == AgronomyLeverKind.HARVEST_POINT }.detailEs.contains("cae") }
        )
    }

    @Test
    fun theShippedBlockNamesTheTwoCompoundsItLeavesOut() {
        // An explicit gap beats an implied one. If somebody adds an entry for one
        // of these, this test fails and they have to say which way the decision
        // went; the reason is in the feature doc, not in this file.
        val index = content().agronomyIndex()

        assertEquals(
            "Camfeno and Terpinoleno are modelled by the module and ship a " +
                "vaporisation row, but neither has a documented agronomic lever, so " +
                "they get the honest 'no lever' sentence",
            setOf(EntourageTerpene.CAMPHENE, EntourageTerpene.TERPINOLENE),
            index.undocumentedTerpenes
        )
    }

    @Test
    fun theGapForAnUndocumentedCompoundIsStatedOnBothPathsThatCanShowIt() {
        val index = content().agronomyIndex()

        // The detail page path.
        val detail = TerpeneAgronomyCopy.contentOf(EntourageTerpene.CAMPHENE, index.forTerpene(EntourageTerpene.CAMPHENE))
        assertFalse(detail.isDocumented)
        assertTrue(detail.notDocumentedEs.isNotBlank())
        assertTrue(detail.notDocumentedEs.contains("Camfeno"))
        assertTrue(detail.routeEs.isNotBlank())
        assertTrue(
            "and the route it can still state: " + BiosynthesisExplainer.routeLineEs(
                com.trichome.app.model.TerpeneFamily.fromFamilyEs(EntourageTerpene.CAMPHENE.familyEs)
            ),
            detail.routeEs.isNotBlank()
        )

        // The synergy card path. A combination of two compounds with no agronomy
        // must produce no agronomy line rather than a blank one.
        val synergy = content().synergies.first { it.terpenes.isEmpty() == false }
        val card = EntourageCards.cardFor(synergy, index)
        card.linesEs.filter { it.role == com.trichome.app.model.EntourageCardRole.AGRONOMY }
            .forEach { assertFalse("a blank agronomy line", it.bodyEs.isBlank()) }
    }

    @Test
    fun theBlockIsReachableFromTheShippedCombinations() {
        // Keying by terpene (decision D-B) means the entry is reachable from the
        // card *and* from the compound's own page. The first half is the reason
        // keying on the pair was declined, so it is asserted against the asset.
        val index = content().agronomyIndex()
        val withAgronomy = content().synergies.filter { synergy ->
            TerpeneAgronomyCopy.cardLinesFor(synergy.terpenes, index).isNotEmpty()
        }

        assertTrue(
            "no shipped synergy carries agronomy, which is the orphaning failure " +
                "D-B was written against",
            withAgronomy.isNotEmpty()
        )
    }

    @Test
    fun theIndexAndTheListAreOneSourceOfTruth() {
        val parsed = content()
        val index = parsed.agronomyIndex()

        assertEquals(parsed.agronomy.size, index.size)
        assertEquals(
            "the index is a view over the parsed list, never a second parse",
            parsed.agronomy.map { it.terpene }.toSet(),
            index.all().map { it.terpene }.toSet()
        )
        assertEquals(
            "and it hands back the same entries, not copies with a different order",
            parsed.agronomy.toSet(),
            index.all().toSet()
        )
        assertEquals(
            index.all().map { it.terpene.key }.sorted(),
            index.all().map { it.terpene.key }
        )
    }

    @Test
    fun addingAgronomyDidNotTouchTheVaporisationTableOrTheProfiles() {
        // Regression guard on the rest of the parse: F3 added one collection and
        // F1's guarantees have to still hold.
        val parsed = content()

        assertEquals("the measured bands are untouched", 10, parsed.vaporisation.size)
        assertEquals("the profiles are untouched", 4, parsed.profiles.size)
        assertEquals("the synergies are untouched", 7, parsed.synergies.size)
        assertEquals("the quiz is untouched", 10, parsed.questions.size)
        assertTrue(
            "a schema-version bump or a dropped row would show up here",
            parsed.unresolvedReferences.isEmpty()
        )
    }

    @Test
    fun anEntryWithNoLeverForAKindTheGuidesExplainWouldStillRender() {
        // Defensive: a shipped entry that carries only a `HARVEST_POINT` lever
        // still resolves every label, because the titles come from
        // `GrowOutGuides` and the evidence label comes from the lever itself.
        val entry: EntourageAgronomy = content().agronomy.first { it.levers.size == 1 }
        val built = TerpeneAgronomyCopy.contentOf(entry.terpene, entry)

        assertEquals(1, built.levers.size)
        assertTrue(built.levers.first().isDrawable)
    }
}