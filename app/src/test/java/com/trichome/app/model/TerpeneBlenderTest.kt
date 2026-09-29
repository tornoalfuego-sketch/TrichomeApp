package com.trichome.app.model

import com.trichome.app.data.repository.Terpene
import com.trichome.app.data.repository.TerpeneCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Master Blender match.
 *
 * ## Where the reference comes from, and why
 *
 * The feature request asked for a match against "the real profile of known
 * strains". `terpenes.json` does not contain one, and it cannot be derived:
 *
 * - `Terpene.strains` is a co-occurrence list, not a composition. 34 of the 55
 *   strain names appear under exactly **one** terpene, while `Blue Dream` is
 *   listed under 140 of the 158. Inverting that field would not recover a
 *   profile; it would manufacture one.
 * - `Terpene.richness` ("muy alto" / "alto" / "medio" / "bajo") *is* a per-compound
 *   abundance rating, and it is complete: all 158 entries carry one of the four
 *   levels. That is the only quantitative signal in the catalogue.
 *
 * So the reference is the catalogue's own richness profile — what the shipped
 * data says a cannabis terpene profile looks like — and the UI says exactly
 * that. No per-strain table is invented, and the number is not dressed up as a
 * verdict about a named cultivar.
 */
class TerpeneBlenderTest {

    private fun terpene(
        id: String,
        name: String = id,
        family: String = "Monoterpeno",
        richness: String = "medio"
    ) = Terpene(id = id, name = name, family = family, richness = richness)

    // --- the reference profile ---------------------------------------------

    @Test
    fun theReferenceIsBuiltFromRichnessAndSumsToOne() {
        val catalog = listOf(
            terpene("a", richness = "muy alto"),
            terpene("b", richness = "alto"),
            terpene("c", richness = "medio"),
            terpene("d", richness = "bajo")
        )

        val reference = TerpeneBlender.referenceProfile(catalog)

        assertEquals("the reference must be a distribution", 1f, reference.values.sum(), 1e-5f)
        reference.values.forEach { share ->
            assertTrue("a share must be positive: $share", share > 0f)
        }
        assertTrue(
            "a richer compound must carry more of the reference than a trace one",
            reference.getValue("a") > reference.getValue("d")
        )
    }

    @Test
    fun anUnknownRichnessContributesNothingRatherThanAGuess() {
        val catalog = listOf(terpene("a", richness = "alto"), terpene("b", richness = "rarísimo"))

        val reference = TerpeneBlender.referenceProfile(catalog)

        assertEquals("only the rated compound is in the reference", setOf("a"), reference.keys)
        assertEquals(1f, reference.getValue("a"), 1e-5f)
    }

    @Test
    fun aCatalogWithNoRatingsYieldsNoReference() {
        val catalog = listOf(terpene("a", richness = ""), terpene("b", richness = ""))

        assertTrue("nothing to score against", TerpeneBlender.referenceProfile(catalog).isEmpty())
    }

    // --- the four required cases -------------------------------------------

    @Test
    fun anEmptySelectionReportsNoSelectionRatherThanZeroPercent() {
        val result = TerpeneBlender.match(emptyMap(), listOf(terpene("a"), terpene("b", richness = "alto")))

        assertEquals("nothing measured is not a zero match", BlendQuality.NO_SELECTION, result.quality)
        assertEquals(0, result.percent)
    }

    @Test
    fun aSelectionWithNoReferenceDataIsReportedAsSuch() {
        val result = TerpeneBlender.match(
            mapOf("a" to 50f, "b" to 50f),
            listOf(terpene("a", richness = ""), terpene("b", richness = ""))
        )

        assertEquals(BlendQuality.NO_REFERENCE, result.quality)
        assertEquals(0, result.percent)
    }

    @Test
    fun anExactMatchIsOneHundredPercent() {
        val catalog = listOf(
            terpene("a", richness = "muy alto"),
            terpene("b", richness = "alto"),
            terpene("c", richness = "medio"),
            terpene("d", richness = "bajo")
        )
        val reference = TerpeneBlender.referenceProfile(catalog)
        // Feed the reference back in, rescaled to look like slider percentages.
        val exact = reference.mapValues { (_, share) -> share * 100f }

        val result = TerpeneBlender.match(exact, catalog)

        assertEquals(100, result.percent)
        assertEquals(BlendQuality.EXACTO, result.quality)
    }

    @Test
    fun aPartialMatchLandsBetweenTheBands() {
        val catalog = listOf(
            terpene("a", richness = "muy alto"),
            terpene("b", richness = "alto"),
            terpene("c", richness = "medio"),
            terpene("d", richness = "bajo")
        )
        val reference = TerpeneBlender.referenceProfile(catalog)
        // Flatten it: same compounds, wrong proportions.
        val flattened = reference.keys.associateWith { 25f }

        val result = TerpeneBlender.match(flattened, catalog)

        assertTrue("a flattened mix must score well below an exact one: ${result.percent}", result.percent < 100)
        assertEquals("a partial match is its own band", BlendQuality.PARCIAL, result.quality)
    }

    @Test
    fun aSelectionThatMatchesNothingIsDistinguishableFromAPartialMatch() {
        val catalog = listOf(
            terpene("a", richness = "muy alto"),
            terpene("b", richness = "alto"),
            terpene("c", richness = "medio"),
            terpene("d", richness = "bajo")
        )
        // A true inversion, not a reordering: each compound is handed the share
        // of the least abundant one, so the proportions are exactly backwards.
        val reference = TerpeneBlender.referenceProfile(catalog)
        val byAbundance = reference.entries.sortedByDescending { it.value }.map { it.key }
        val inverted = byAbundance.withIndex().associate { (rank, id) ->
            id to reference.getValue(byAbundance[byAbundance.size - 1 - rank]) * 100f
        }

        val result = TerpeneBlender.match(inverted, catalog)

        assertEquals("an inverted mix is not a partial match", BlendQuality.DEBIL, result.quality)
        assertTrue("an inverted mix must score low: ${result.percent}", result.percent < 50)
    }

    @Test
    fun theScoreIsOrderIndependentAndBounded() {
        val catalog = listOf(
            terpene("a", richness = "muy alto"),
            terpene("b", richness = "alto"),
            terpene("c", richness = "medio")
        )
        val mix = mapOf("a" to 40f, "b" to 35f, "c" to 25f)

        val result = TerpeneBlender.match(mix, catalog)
        val reordered = TerpeneBlender.match(mix.toList().reversed().toMap(), catalog)

        assertEquals("a match must not depend on slider order", result.percent, reordered.percent)
        repeat(200) { seed ->
            val random = kotlin.random.Random(seed)
            val noisy = catalog.associate { it.id to random.nextFloat() * 100f }
            val scored = TerpeneBlender.match(noisy, catalog)
            assertTrue("percent out of range: ${scored.percent}", scored.percent in 0..100)
        }
    }

    @Test
    fun onlyPositiveCompoundsAreScored() {
        val catalog = listOf(terpene("a", richness = "alto"), terpene("b", richness = "bajo"))
        val full = TerpeneBlender.match(mapOf("a" to 70f, "b" to 30f), catalog)
        val withZeros = TerpeneBlender.match(mapOf("a" to 70f, "b" to 30f, "c" to 0f, "d" to -5f), catalog)

        assertEquals("a zero or negative slider is 'not set', not a zero share", full.percent, withZeros.percent)
    }

    @Test
    fun idsAbsentFromTheCatalogAreIgnored() {
        val catalog = listOf(terpene("a", richness = "alto"), terpene("b", richness = "bajo"))

        val baseline = TerpeneBlender.match(mapOf("a" to 50f, "b" to 50f), catalog)
        val polluted = TerpeneBlender.match(mapOf("a" to 50f, "b" to 50f, "inventado" to 90f), catalog)

        assertEquals(
            "an id the catalogue has never heard of must not skew the score",
            baseline.percent,
            polluted.percent
        )
        assertEquals("and it must not reach the report either", 2, polluted.overRepresented.size + polluted.underRepresented.size)
    }

    // --- the explanation the UI shows --------------------------------------

    @Test
    fun theReportNamesTheOverAndUnderRepresentedCompounds() {
        val catalog = listOf(
            terpene("mirceno", name = "Mirceno", richness = "muy alto"),
            terpene("limoneno", name = "Limonen", richness = "alto"),
            terpene("cariofileno", name = "Cariofileno", family = "Sesquiterpeno", richness = "alto")
        )
        val reference = TerpeneBlender.referenceProfile(catalog)

        val result = TerpeneBlender.match(
            mapOf("mirceno" to 10f, "limoneno" to 20f, "cariofileno" to 70f),
            catalog
        )

        assertTrue(
            "the compound the user over-weighted must be called out, got ${result.overRepresented}",
            result.overRepresented.any { it.id == "cariofileno" }
        )
        assertTrue(
            "the compound the user under-weighted must be called out, got ${result.underRepresented}",
            result.underRepresented.any { it.id == "mirceno" }
        )
        result.overRepresented.forEach {
            assertTrue("a report line needs a display name", it.name.isNotBlank())
        }
    }

    @Test
    fun theFamilySplitIsReportedOnBothSides() {
        val catalog = listOf(
            terpene("a", family = "Monoterpeno", richness = "alto"),
            terpene("b", family = "Monoterpeno", richness = "bajo"),
            terpene("c", family = "Sesquiterpeno", richness = "alto")
        )

        val result = TerpeneBlender.match(mapOf("a" to 50f, "b" to 25f, "c" to 25f), catalog)

        assertEquals("every family in the selection is reported", setOf("Monoterpeno", "Sesquiterpeno"), result.userFamilySplit.keys)
        assertTrue("the user split must total one", result.userFamilySplit.values.sum() in 0.99f..1.01f)
        assertTrue("the reference split must be reported too", result.referenceFamilySplit.isNotEmpty())
    }

    // --- the slider set ----------------------------------------------------

    @Test
    fun featuredCompoundsAreOrderedByAbundanceAndAreDistinctToRead() {
        val catalog = listOf(
            terpene("a", name = "Cariofileno", richness = "muy alto"),
            terpene("b", name = "Cariofileno", richness = "muy alto"),
            terpene("c", name = "Mirceno", richness = "alto"),
            terpene("d", name = "Limonen", richness = "medio")
        )

        val featured = TerpeneBlender.featuredCompounds(catalog, limit = 3)

        // The catalogue ships two records both named "Cariofileno"; a slider
        // list showing the same label twice would be unusable.
        assertEquals("labels must be distinct", featured.size, featured.map { it.name }.toSet().size)
        assertEquals("the richest compound leads", "Cariofileno", featured.first().name)
        assertTrue("a rated compound outranks an unrated one", featured.map { it.id }.contains("c"))
    }

    @Test
    fun featuredCompoundsCopeWithACatalogSmallerThanTheLimit() {
        val featured = TerpeneBlender.featuredCompounds(listOf(terpene("a")), limit = 8)

        assertEquals(1, featured.size)
    }

    // --- the shipped catalogue actually supports this ----------------------

    @Test
    fun theShippedCatalogCarriesARichnessRatingForEveryEntry() {
        val catalog = shippedCatalog()

        val unrated = catalog.filter { TerpeneBlender.richnessWeight(it.richness) == 0f }
        assertTrue(
            "the reference profile is built from richness; unrated entries are " +
                "invisible to it: ${unrated.map { it.id }}",
            unrated.isEmpty()
        )
    }

    @Test
    fun theShippedCatalogCannotSupplyAProfilePerNamedStrain() {
        val catalog = shippedCatalog()

        val perStrain = catalog.flatMap { it.strains }.groupingBy { it }.eachCount()
        val singletons = perStrain.filterValues { it == 1 }.size

        // This is the finding that forced the reference-profile decision: a
        // strain named by a single terpene has no profile to match against, and
        // the popular names are catch-alls rather than compositions.
        assertTrue(
            "expected most strain names to appear under one terpene, got $singletons of ${perStrain.size}",
            singletons > perStrain.size / 2
        )
        assertEquals(
            "the most listed strain is a catch-all, not a composition",
            true,
            perStrain.values.max() > catalog.size / 2
        )
    }

    @Test
    fun theShippedCatalogScoresARealisticHeadlineBlend() {
        val catalog = shippedCatalog()
        val featured = TerpeneBlender.featuredCompounds(catalog, limit = 8)

        // The three headline compounds of the brief have to be reachable from
        // the sliders. They are resolved by catalogue id rather than by label,
        // because the shipped data is not label-clean: limonene appears twice,
        // as `limonene` ("Limonen", rated alto) and `d_limonene` ("Limoneno d",
        // rated muy alto), and the richer record wins the single limonene
        // slider. The player only ever sees the label that record carries.
        val featuredIds = featured.map { it.id }.toSet()
        val headline = listOf(
            "myrcene" to listOf("myrcene", "beta_myrcene"),
            "limonene" to listOf("limonene", "d_limonene"),
            "caryophyllene" to listOf("caryophyllene", "beta_caryophyllene")
        )

        headline.forEach { (compound, candidates) ->
            assertTrue(
                "'$compound' should be reachable from the sliders; featured ids were $featuredIds",
                candidates.any { it in featuredIds }
            )
        }

        // A tasting-lab style reading: the three headline compounds, weighted
        // towards myrcene. Percentages are on a 0..100 scale, as the sliders are.
        fun pick(candidates: List<String>) = candidates.first { it in featuredIds }
        val reading = mapOf(
            pick(listOf("myrcene", "beta_myrcene")) to 40f,
            pick(listOf("limonene", "d_limonene")) to 35f,
            pick(listOf("caryophyllene", "beta_caryophyllene")) to 25f
        )

        val result = TerpeneBlender.match(reading, catalog)

        assertNotEquals(BlendQuality.NO_SELECTION, result.quality)
        assertTrue("percent out of range: ${result.percent}", result.percent in 0..100)
        assertTrue("the reference must be populated from real data", result.referenceFamilySplit.isNotEmpty())
        // Two monoterpenes against one sesquiterpene is the classic profile
        // shape, so the split has to come out that way.
        assertTrue(
            "a two-mono/one-sesquiterpene reading should read as monoterpene-dominant, got " +
                result.userFamilySplit,
            result.userFamilySplit.getOrDefault("Monoterpeno", 0f) > 0.5f
        )
    }

    // --- helpers ------------------------------------------------------------

    private fun shippedCatalog(): List<Terpene> {
        val file = listOf(
            File("src/main/assets/data/terpenes.json"),
            File("app/src/main/assets/data/terpenes.json")
        ).firstOrNull { it.isFile }
            ?: error("could not locate terpenes.json")
        val catalog = Json { ignoreUnknownKeys = true }
            .decodeFromString<TerpeneCatalog>(file.readText())
        return catalog.terpenes
    }
}
