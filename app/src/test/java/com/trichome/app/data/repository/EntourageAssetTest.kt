package com.trichome.app.data.repository

import com.trichome.app.model.Cannabinoid
import com.trichome.app.model.EntourageTerpene
import com.trichome.app.model.LabAxis
import com.trichome.app.model.PharmacologicalProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the shipped Séquito catalog as it actually sits on disk.
 *
 * This is the test that keeps the pharmacology honest, in three ways:
 *
 * 1. **Every key resolves.** The asset names compounds, profiles and axes as
 *    strings; the model names them as enums. A key the enums do not know would
 *    otherwise be dropped at parse time and the user would never see the entry.
 *    [EntourageBible.toContent] collects those in `unresolvedReferences`, and
 *    the shipped asset has to produce an empty list.
 * 2. **Proportions are proportions.** A profile whose shares do not sum to 1
 *    would make the planner's percentage arbitrary, so the test fails on it
 *    rather than letting a wrong number reach the screen.
 * 3. **The vaporisation table agrees with the encyclopedia.** Every boiling
 *    point here is cross-checked against the `boilingPoint` the terpene
 *    encyclopedia already ships. Two catalogs claiming different boiling points
 *    for the same compound is exactly the failure that would teach a user the
 *    wrong temperature.
 */
class EntourageAssetTest {

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

    private fun terpenes(): List<Terpene> =
        json.decodeFromString<TerpeneCatalog>(String(assetBytes("terpenes.json"), Charsets.UTF_8)).terpenes

    private fun content() = entourage().toContent()

    // --- the asset itself ---------------------------------------------------

    @Test
    fun theAssetParsesWithNoBOM() {
        assertTrue("the file has to exist and start with a brace", entourage().version >= 1)
    }

    @Test
    fun everyKeyInTheAssetResolvesToAModelMember() {
        val content = content()

        assertTrue(
            "unresolved keys would be dropped silently: ${content.unresolvedReferences}",
            content.unresolvedReferences.isEmpty()
        )
    }

    @Test
    fun theAssetShipsTheContentTheModulePromises() {
        val content = content()

        assertTrue("synergies", content.synergies.size >= 7)
        assertEquals("all four target profiles", PharmacologicalProfile.entries.size, content.profiles.size)
        assertTrue("quiz questions", content.questions.size == 10)
        assertTrue("lab cases", content.cases.size >= 3)
        assertTrue("a vaporisation row per terpene", content.vaporisation.size >= 10)
    }

    @Test
    fun theModuleShipsADisclaimer() {
        assertTrue(
            "a health-adjacent module has to say what it is not",
            content().disclaimerEs.isNotBlank()
        )
    }

    // --- synergies ----------------------------------------------------------

    @Test
    fun synergyIdsAreUniqueAndBlanksAreNone() {
        val synergies = content().synergies

        assertEquals("ids key the lookups", synergies.size, synergies.map { it.id }.toSet().size)
        synergies.forEach {
            assertTrue("${it.id} has no id", it.id.isNotBlank())
        }
    }

    @Test
    fun everySynergyNamesItsCompoundsAndMechanismAndLimits() {
        content().synergies.forEach { synergy ->
            assertTrue(
                "${synergy.id} has no cannabinoid, so it explains nothing",
                synergy.cannabinoids.isNotEmpty()
            )
            assertTrue("${synergy.id} has no terpene", synergy.terpenes.isNotEmpty())
            assertTrue("${synergy.id} has no outcome title", synergy.outcomeEs.isNotBlank())
            assertTrue("${synergy.id} has no description", synergy.descriptionEs.isNotBlank())
            assertTrue(
                "${synergy.id} has no mechanism, and 'it feels different' is not one",
                synergy.mechanismEs.isNotBlank()
            )
            assertTrue(
                "${synergy.id} has no evidence note; a health-adjacent claim has to carry its own limits",
                synergy.evidenceEs.isNotBlank()
            )
        }
    }

    @Test
    fun everySynergyMechanismNamesAReceptorOrAnEnzyme() {
        // A mechanism that names no target is a description of a feeling, and
        // the whole point of the module is that it names them.
        val targets = listOf("CB1", "CB2", "TRPV1", "TRPA1", "5-HT1A", "acetilcolinesterasa", "P450", "CYP")

        content().synergies.forEach { synergy ->
            assertTrue(
                "${synergy.id} names no receptor or enzyme: \"${synergy.mechanismEs}\"",
                targets.any { it in synergy.mechanismEs }
            )
        }
    }

    @Test
    fun everySynergyShipsStrainsThatExistInTheTerpeneEncyclopedia() {
        val known = terpenes().flatMap { it.strains }.toSet()
        val unknown = content().synergies
            .flatMap { it.strainsEs }
            .filter { it !in known }
            .distinct()

        assertTrue(
            "these strain names are not in terpenes.json, so they cannot be resolved: $unknown",
            unknown.isEmpty()
        )
    }

    @Test
    fun everySynergyLinksToAtLeastOneShippedProfile() {
        val shipped = content().profiles.map { it.key }.toSet()
        val orphans = content().synergies.filter { it.profiles.isEmpty() }.map { it.id }

        assertTrue("a synergy reachable from no profile is invisible: $orphans", orphans.isEmpty())
        content().synergies.forEach { synergy ->
            val unknown = synergy.profiles - shipped
            assertTrue("${synergy.id} links to unshipped profiles: $unknown", unknown.isEmpty())
        }
    }

    @Test
    fun theCombinationsThePlanCallsForAreAllPresent() {
        val byId = content().synergies.associateBy { it.id }

        listOf(
            "thc_myrcene",
            "thc_limonene",
            "thc_linalool",
            "thc_pinene",
            "cbd_caryophyllene",
            "cbg_limonene_myrcene",
            "cbn_linalool_myrcene"
        ).forEach { id ->
            assertTrue("$id is missing from the asset", id in byId)
        }
    }

    @Test
    fun theCbdCaryophylleneSynergyDeclaresItsInteraction() {
        val synergy = content().synergyById("cbd_caryophyllene")

        assertTrue("the CBD row has to carry the CYP warning", synergy!!.hasInteractionWarning)
        assertTrue(
            "and the warning has to be about drug metabolism",
            synergy.interactionEs.contains("CYP") || synergy.interactionEs.contains("citocromo")
        )
    }

    @Test
    fun noInteractionNoteReadsAsMedicalAdvice() {
        val forbidden = listOf("deberías tomar", "dosis recomendada", "te recomendamos tomar", "es seguro", "cura", "trata la")

        content().synergies.forEach { synergy ->
            val text = synergy.interactionEs.lowercase()
            forbidden.forEach { phrase ->
                assertFalsePhrase(synergy.id, text, phrase)
            }
        }
    }

    private fun assertFalsePhrase(id: String, text: String, phrase: String) {
        assertTrue(
            "$id reads as medical advice: it contains '$phrase'",
            !text.contains(phrase)
        )
    }

    // --- profiles -----------------------------------------------------------

    @Test
    fun everyShippedProfileMapsToAnEnumMemberAndTheOtherWayAround() {
        val shipped = content().profiles.map { it.key }.toSet()

        assertEquals(PharmacologicalProfile.entries.toSet(), shipped)
    }

    @Test
    fun profileTerpeneSharesSumToOne() {
        content().profiles.forEach { profile ->
            val total = profile.terpeneShares.values.sum()
            assertTrue(
                "${profile.key} terpene shares sum to $total, not 1; the planner's percentage would be arbitrary",
                sharesSumToOne(profile.terpeneShares.values.toList())
            )
        }
    }

    @Test
    fun profileCannabinoidWeightsSumToOne() {
        content().profiles.forEach { profile ->
            assertTrue(
                "${profile.key} cannabinoid weights sum to ${profile.cannabinoidWeights.values.sum()}, not 1",
                sharesSumToOne(profile.cannabinoidWeights.values.toList())
            )
        }
    }

    private fun sharesSumToOne(values: List<Float>): Boolean =
        values.isNotEmpty() && kotlin.math.abs(values.sum() - 1f) < 0.01f

    @Test
    fun everyProfileIsDescribedAndCarriesItsLimits() {
        content().profiles.forEach { profile ->
            assertTrue("${profile.key} has no label", profile.labelEs.isNotBlank())
            assertTrue("${profile.key} has no description", profile.descriptionEs.isNotBlank())
            assertTrue(
                "${profile.key} has no note; a profile is a guide, not a specification",
                profile.noteEs.isNotBlank()
            )
            assertTrue("${profile.key} names no terpenes", profile.terpeneShares.isNotEmpty())
            assertTrue("${profile.key} names no cannabinoids", profile.cannabinoidWeights.isNotEmpty())
        }
    }

    @Test
    fun everyProfilesLeadTerpeneIsActuallyItsLargestShare() {
        content().profiles.forEach { profile ->
            val lead = profile.leadTerpene
            assertTrue("${profile.key} has no lead terpene", lead != null)
            val best = profile.terpeneShares.maxByOrNull { it.value }!!
            assertEquals("${profile.key} lead", best.key, lead)
        }
    }

    // --- vaporisation -------------------------------------------------------

    @Test
    fun everyBoilingPointAgreesWithTheTerpeneEncyclopedia() {
        val catalog = terpenes().associateBy { it.id }

        content().vaporisation.forEach { row ->
            val entry = catalog[row.terpene.catalogId]
            assertTrue(
                "${row.terpene.key} points at '${row.terpene.catalogId}', which is not in terpenes.json",
                entry != null
            )
            val shipped = entry!!.boilingPointCelsius
            assertTrue(
                "${row.terpene.key} boils at ${row.boilingPointC} °C here and ${shipped?.toString() ?: "nothing"} °C in the " +
                    "encyclopedia; two catalogs cannot both be right",
                shipped != null && shipped == row.boilingPointC
            )
        }
    }

    @Test
    fun everyTerpeneTheModelKnowsHasATemperatureRow() {
        val shipped = content().vaporisation.map { it.terpene }.toSet()

        assertEquals(
            "a terpene with no temperature would be missing from the preservation tips",
            EntourageTerpene.entries.toSet(),
            shipped
        )
    }

    @Test
    fun everyTerpeneResolvesToTheEncyclopediaEntryItNames() {
        val ids = terpenes().map { it.id }.toSet()

        EntourageTerpene.entries.forEach { terpene ->
            assertTrue(
                "${terpene.key} names catalog id '${terpene.catalogId}', which does not exist",
                terpene.catalogId in ids
            )
        }
    }

    @Test
    fun theWindowIsOrderedAroundTheBoilingPoint() {
        content().vaporisation.forEach { row ->
            assertTrue(
                "${row.terpene.key}: the floor ${row.minTempC} is above the boiling point ${row.boilingPointC}",
                row.minTempC <= row.boilingPointC
            )
            assertTrue(
                "${row.terpene.key}: the ceiling ${row.maxTempC} is not above the boiling point ${row.boilingPointC}",
                row.maxTempC > row.boilingPointC
            )
            assertTrue(
                "${row.terpene.key} has no preservation note",
                row.noteEs.isNotBlank()
            )
        }
    }

    @Test
    fun caryophylleneIsTheOnlyTerpeneThatNeedsSesquiterpeneHeat() {
        // A "sesquiterpenes boil high" rule would be wrong: alpha-humulene
        // genuinely boils at ~166 °C, which the encyclopedia also ships, and
        // the row agrees with it. The claim the module actually makes is
        // narrower: caryophyllene beta is the one terpene in the list that
        // needs heat no other terpene here survives, so its floor has to sit
        // above every other row's ceiling or the vaporisation tip is a lie.
        val rows = content().vaporisation
        val caryophyllene = rows.single { it.terpene == EntourageTerpene.BETA_CARYOPHYLLENE }
        val others = rows.filter { it.terpene != EntourageTerpene.BETA_CARYOPHYLLENE }

        assertTrue(
            "cariofileno beta boils at ${caryophyllene.boilingPointC} °C, which is monoterpene territory",
            caryophyllene.boilingPointC > 240
        )
        others.forEach { other ->
            assertTrue(
                "${other.terpene.key} tops out at ${other.maxTempC} °C, which would already destroy the " +
                    "cariofileno beta, so its floor of ${caryophyllene.minTempC} °C has to be above it",
                caryophyllene.minTempC > other.maxTempC
            )
        }
    }

    @Test
    fun noTerpeneBoilsOutsideThePlausibleRangeForTerpenoids() {
        // Terpenoids boil between roughly 150 and 280 °C. A row outside that
        // band is a transposed number rather than a compound behaviour, and it
        // would send the user to a temperature no cannabis material has.
        content().vaporisation.forEach { row ->
            assertTrue(
                "${row.terpene.key} boils at ${row.boilingPointC} °C, outside the terpenoid range",
                row.boilingPointC in 150..280
            )
        }
    }

    @Test
    fun theCitedLimoneneAndPineneTemperaturesAreTheOnesThePlanAsksFor() {
        val rows = content().vaporisation.associateBy { it.terpene }

        assertEquals(
            "the plan cites 176 °C for limonene",
            176,
            rows.getValue(EntourageTerpene.LIMONENE).boilingPointC
        )
        assertEquals(
            "the plan cites 157 °C for pinene; the encyclopedia ships 156, so the asset has to agree with it",
            156,
            rows.getValue(EntourageTerpene.ALPHA_PINENE).boilingPointC
        )
    }

    @Test
    fun theTerpenesAreOrderedConsistentlyWithTheirBoilingPoints() {
        val rows = content().vaporisation.sortedBy { it.boilingPointC }
        val expected = rows.map { it.terpene }

        assertEquals(
            "a stable order matters for a selector; ties break on nothing and the UI would shuffle",
            expected,
            com.trichome.app.model.EntouragePlanner.byVolatility(content().vaporisation)
        )
    }

    // --- lab cases ----------------------------------------------------------

    @Test
    fun caseIdsAreUniqueAndEveryCaseIsPlayable() {
        val cases = content().cases

        assertEquals("ids key the case picker", cases.size, cases.map { it.id }.toSet().size)
        cases.forEach {
            assertTrue("${it.id} has no id", it.id.isNotBlank())
            assertTrue("${it.id} has no title", it.titleEs.isNotBlank())
            assertTrue("${it.id} has no brief, so the player does not know the case", it.briefEs.isNotBlank())
            assertTrue(
                "${it.id} has no explanation, so the player cannot learn why the answer was wrong",
                it.explanationEs.isNotBlank()
            )
        }
    }

    @Test
    fun everyCaseDeclaresAllFourAxes() {
        content().cases.forEach {
            assertEquals(
                "${it.id} has to constrain all four axes, or the minigame silently stops constraining one",
                LabAxis.entries.toSet(),
                it.ceilings.keys
            )
        }
    }

    @Test
    fun everyCaseAimsAtAShippedProfile() {
        val shipped = content().profiles.map { it.key }.toSet()

        content().cases.forEach {
            assertTrue("${it.id} aims at ${it.goal}, which is not shipped", it.goal in shipped)
        }
    }

    @Test
    fun everyShareCeilingResolvesToACannabinoid() {
        content().cases.forEach { case ->
            case.maxCannabinoidShare.keys.forEach { cannabinoid ->
                assertTrue(
                    "${case.id} sets a ceiling on '$cannabinoid', which is not a cannabinoid the app knows",
                    cannabinoid in Cannabinoid.entries
                )
            }
        }
    }

    @Test
    fun theInsomniaCaseCarriesThePlanTachycardiaConstraint() {
        val insomnia = content().caseById("insomnio_alta_tolerancia")

        assertTrue("the plan's case has to ship", insomnia != null)
        val tachycardio = insomnia!!.ceilings.getValue(LabAxis.TACHYCARDIA)
        assertTrue(
            "the whole point is a low tachycardio ceiling, got $tachycardio",
            tachycardio < 0.35f
        )
        assertTrue(
            "and a THC share ceiling, or 'high tolerance' would mean 'no limit'",
            insomnia.maxCannabinoidShare.containsKey(Cannabinoid.THC)
        )
    }

    @Test
    fun noCaseForbidsACompoundItAlsoAimsFor() {
        content().cases.forEach { case ->
            assertTrue(
                "${case.id} forbids ${case.forbiddenCannabinoids} while aiming at ${case.goal}",
                case.forbiddenCannabinoids.isEmpty() ||
                    content().profile(case.goal)!!.cannabinoidWeights.keys.none { it in case.forbiddenCannabinoids }
            )
        }
    }

    // --- quiz ---------------------------------------------------------------

    @Test
    fun theQuizShipsTenAnswerableQuestions() {
        val questions = content().questions

        assertEquals(10, questions.size)
        questions.forEach { question ->
            assertTrue("${question.id} has no id", question.id.isNotBlank())
            assertTrue("${question.id} has no prompt", question.promptEs.isNotBlank())
            assertTrue(
                "${question.id} has ${question.optionsEs.size} options",
                question.optionsEs.size >= 4
            )
            assertEquals("${question.id} has duplicate options", question.optionsEs.size, question.optionsEs.distinct().size)
            assertTrue(
                "${question.id}: the answer at ${question.correctIndex} is outside the options",
                question.correctIndex in question.optionsEs.indices
            )
            assertTrue("${question.id} has no explanation", question.explanationEs.isNotBlank())
        }
    }

    @Test
    fun everyQuizOptionIsDistinctAndNonBlank() {
        content().questions.forEach { question ->
            question.optionsEs.forEach { option ->
                assertTrue("${question.id} has a blank option", option.isNotBlank())
            }
        }
    }

    @Test
    fun theQuizAsksAboutMechanismsAndNotAboutStrains() {
        // Every question has to name a receptor, an enzyme or a temperature, so
        // the quiz tests the pharmacology the module exists to teach.
        val targets = listOf(
            "CB1", "CB2", "TRPV1", "TRPA1", "5-HT1A", "CYP", "P450",
            "acetilcolinesterasa", "°C", "afinidad", "agonista", "antagonista",
            "barriera hematoencefálica", "mecanística", "in vitro", "modelos animales"
        )

        content().questions.forEach { question ->
            val text = question.promptEs + " " + question.explanationEs
            assertTrue(
                "${question.id} names no mechanism: \"${question.promptEs}\"",
                targets.any { it in text }
            )
        }
    }

    @Test
    fun theQuizExplainsItsOwnLimits() {
        // At least a third of the questions have to be about what is *not*
        // proven, or the quiz teaches the module's claims as established fact.
        val uncertainty = listOf("hipótesis", "no está", "no está medido", "preclínic", "no se ha", "en personas")

        val careful = content().questions.count { question ->
            uncertainty.any { it in question.explanationEs.lowercase() }
        }

        assertTrue(
            "only $careful of ${content().questions.size} questions carry a limitation; " +
                "a health-adjacent quiz that never says 'this is not proven' teaches them as fact",
            careful >= 3
        )
    }

    @Test
    fun noQuizOptionPromisesATherapeuticOutcome() {
        val forbidden = listOf("cura", "trata la enfermedad", "elimina el dolor", "dosis recomendada", "es seguro")

        content().questions.forEach { question ->
            val text = (question.optionsEs + question.promptEs).joinToString(" ").lowercase()
            forbidden.forEach { phrase ->
                assertTrue(
                    "${question.id} promises '$phrase'",
                    !text.contains(phrase)
                )
            }
        }
    }
}
