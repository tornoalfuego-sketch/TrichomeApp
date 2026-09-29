package com.trichome.app.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the shipped catalogs as they actually sit on disk.
 *
 * The assets are parsed with the very same schema classes the app uses, so a
 * field rename in the data classes fails here instead of shipping an
 * encyclopedia that silently renders empty.
 */
class AssetCatalogTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun assetBytes(name: String): ByteArray {
        val candidates = listOf(
            File("src/main/assets/data/$name"),
            File("app/src/main/assets/data/$name")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error(
                "Could not locate $name. Looked in: " +
                    candidates.joinToString { it.absolutePath }
            )
        return file.readBytes()
    }

    private fun terpeneCatalog(): TerpeneCatalog {
        val bytes = assetBytes("terpenes.json")
        assertTrue(
            "terpenes.json must not start with a UTF-8 BOM",
            bytes.first().toInt() == '{'.code
        )
        return json.decodeFromString(String(bytes, Charsets.UTF_8))
    }

    private fun diagnosisCatalog(): DiagnosisCatalog {
        val bytes = assetBytes("diagnostics.json")
        assertTrue(
            "diagnostics.json must not start with a UTF-8 BOM",
            bytes.first().toInt() == '{'.code
        )
        return json.decodeFromString(String(bytes, Charsets.UTF_8))
    }

    // --- terpenes -----------------------------------------------------------

    @Test
    fun terpeneCatalogShipsAtLeastOneHundredAndFiftyEntries() {
        val count = terpeneCatalog().terpenes.size

        assertTrue("the encyclopedia must hold at least 150 terpenes, found $count", count >= 150)
    }

    @Test
    fun everyTerpeneHasAUniqueIdAndName() {
        val terpenes = terpeneCatalog().terpenes

        terpenes.forEach {
            assertTrue("terpene with a blank id or name: $it", it.id.isNotBlank() && it.name.isNotBlank())
        }
        assertEquals("terpene ids must be unique", terpenes.size, terpenes.map { it.id }.toSet().size)

        // The name used to be the only field not checked, and the catalog shipped
        // three collisions: `myrcene` and `beta_myrcene` were both "Mirceno",
        // `caryophyllene` and `beta_caryophyllene` both "Cariofileno", `humulene`
        // and `alpha_humulene` both "Humuleno". The chemistry is fine -- these
        // are isomers, and isomers share a molecular formula and a molar mass by
        // definition -- but the encyclopedia showed the user two identical rows.
        // A bare "humulene" conventionally means alpha-humulene, so the qualified
        // form carries the distinction and the bare one keeps the common name.
        val byName = terpenes.groupBy { it.name }
        val collisions = byName.filterValues { it.size > 1 }
        assertTrue(
            "terpene display names must be unique, found: " +
                collisions.map { (name, group) -> "$name -> ${group.map { it.id }}" },
            collisions.isEmpty()
        )
    }

    @Test
    fun everyTerpeneNameIsAccentCorrect() {
        // Spanish display names are user-facing. "Limonen" shipped without its
        // accent, and nothing looked at spelling because nothing could.
        val terpenes = terpeneCatalog().terpenes
        val knownMisspellings = mapOf(
            "Limonen" to "Limoneno",
            "Citrico" to "Cítrico",
            "Aromatico" to "Aromático"
        )
        terpenes.forEach { t ->
            val corrected = knownMisspellings[t.name]
            assertTrue(
                "terpene '${t.id}' ships as '$t.name'; it should be '$corrected'",
                corrected == null
            )
        }
    }

    @Test
    fun entourageLinksAlwaysResolveInsideTheCatalog() {
        val terpenes = terpeneCatalog().terpenes
        val ids = terpenes.map { it.id }.toSet()

        val dangling = terpenes.flatMap { t -> t.pairsWith.filter { it !in ids } }.distinct()
        assertTrue(
            "every pairsWith reference must exist in the catalog; dangling: $dangling",
            dangling.isEmpty()
        )
    }

    @Test
    fun boilingPointsAreWrittenAsTemperatures() {
        val points = terpeneCatalog().terpenes.mapNotNull { it.boilingPoint.takeIf { p -> p.isNotBlank() } }

        assertTrue("some boiling points should be present", points.isNotEmpty())
        points.forEach {
            assertTrue("boiling point must read as a temperature, was '$it'", Regex("""\d+\s*°C""").matches(it))
        }
    }

    @Test
    fun mostTerpenesCarryChemicalIdentity() {
        val terpenes = terpeneCatalog().terpenes
        val withChemistry = terpenes.count { it.formula.isNotBlank() && it.molarMass.isNotBlank() }

        assertTrue(
            "the encyclopedia is meant to be doctoral level: only $withChemistry of " +
                "${terpenes.size} entries carry a formula and molar mass",
            withChemistry >= terpenes.size * 4 / 5
        )
    }

    @Test
    fun everyTerpeneExplainsItselfAndItsLimits() {
        val terpenes = terpeneCatalog().terpenes

        terpenes.forEach {
            assertTrue("${it.id} has no mechanism", it.mechanism.isNotBlank())
            assertTrue("${it.id} has no toxicity note", it.toxicity.isNotBlank())
        }
    }

    // --- diagnostics --------------------------------------------------------

    @Test
    fun diagnosticsCatalogHasEnoughConditionsToBeUseful() {
        val conditions = diagnosisCatalog().conditions

        assertTrue("expected a real clinical database, found ${conditions.size} conditions", conditions.size >= 30)
        assertEquals(
            "condition ids must be unique",
            conditions.size,
            conditions.map { it.id }.toSet().size
        )
    }

    @Test
    fun mostConditionsCarryPhotographicEvidence() {
        val conditions = diagnosisCatalog().conditions
        val withEvidence = conditions.count { it.photoEvidence != null && !it.photoEvidence.isEmpty }

        assertTrue(
            "photographic diagnosis needs evidence on most conditions: " +
                "$withEvidence of ${conditions.size}",
            withEvidence >= conditions.size / 2
        )
    }

    @Test
    fun everyConditionOffersAnActionPlan() {
        val conditions = diagnosisCatalog().conditions

        conditions.forEach {
            assertTrue(
                "${it.id} has no treatment plan, so the app could not say how to fix it",
                it.actionPlanEs.isNotEmpty()
            )
            assertTrue("${it.id} has no cause explanation", it.causeEs.isNotBlank())
        }
    }

    @Test
    fun everySymptomWeightResolvesToAShippedSymptom() {
        // The link is condition -> symptom -> weight, so a weight naming a
        // symptom that does not exist would score against nothing.
        val catalog = diagnosisCatalog()
        val symptomIds = catalog.symptoms.map { it.id }.toSet()

        val dangling = catalog.conditions.flatMap { c ->
            c.symptomWeights.filterKeys { it !in symptomIds }.keys.map { "${c.id} -> $it" }
        }
        assertTrue("symptomWeights reference unknown symptoms: $dangling", dangling.isEmpty())
        assertTrue("no symptoms shipped", catalog.symptoms.isNotEmpty())
        assertEquals(
            "symptom ids must be unique",
            catalog.symptoms.size,
            symptomIds.size
        )
    }

    @Test
    fun everyConditionIsReachableFromTheSymptomPicker() {
        // Without a symptom weight a condition can only be found by photo, and
        // the grower ticking symptoms in the UI would never see it.
        val catalog = diagnosisCatalog()
        val unreachable = catalog.conditions.filter { it.symptomWeights.isEmpty() }

        assertTrue(
            "a condition no symptom can reach is invisible to the user: ${unreachable.map { it.id }}",
            unreachable.isEmpty()
        )
    }
}
