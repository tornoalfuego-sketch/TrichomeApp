package com.trichome.app.model

import com.trichome.app.data.repository.DiagnosisCondition
import com.trichome.app.data.repository.DiagnosisSymptom
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F11: per-symptom icons, the four picker categories, and live search.
 *
 * ## What is asserted against the shipped asset and not against a fixture
 *
 * The whole point of the glyph table is that it covers the catalogue the app
 * actually ships. A hand-written list of twelve ids would prove nothing: the
 * defects this guards are a symptom with no figure and two symptoms sharing one,
 * and both need the real 116-row file to exist. So the asset is read off disk
 * exactly as `AssetCatalogTest` does, and the assertions walk it.
 *
 * ## What each test deliberately does not cover
 *
 * Nothing here asserts how a figure *looks*. There is no JVM test runtime for
 * Compose in this project and no golden-image infrastructure, so "the leaf reads
 * as a leaf" is not a claim any test here can make. What is asserted is that
 * every figure is distinct, that every symptom has one, and that an unmatched id
 * is reported rather than silently given another symptom's picture.
 */
class DiagnosisIconsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun assetBytes(name: String): ByteArray {
        val candidates = listOf(
            File("src/main/assets/data/$name"),
            File("app/src/main/assets/data/$name")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not locate $name")
        return file.readBytes()
    }

    private data class Catalog(
        val symptoms: List<DiagnosisSymptom>,
        val conditions: List<DiagnosisCondition>
    )

    /**
     * The asset's own top-level shape, declared here.
     *
     * The shipped `DiagnosisCatalog` is `internal` to `data.repository` and
     * this file lives in `model`, so a local mirror is the honest way to read the
     * bytes without widening a production class's visibility for a test. It is
     * three fields.
     */
    @kotlinx.serialization.Serializable
    private data class CatalogAsset(
        val version: Int = 1,
        val symptoms: List<DiagnosisSymptom> = emptyList(),
        val conditions: List<DiagnosisCondition> = emptyList()
    )

    private fun catalog(): Catalog {
        // The asset is parsed through its own serialisable shapes rather than
        // through `DiagnosisContentRepository`, which needs a `Context` this
        // classpath does not have. Same file, same bytes, no Robolectric.
        val parsed = json.decodeFromString<CatalogAsset>(
            String(assetBytes("diagnostics.json"), Charsets.UTF_8)
        )
        return Catalog(parsed.symptoms, parsed.conditions)
    }

    private fun symptomIds(): List<String> = catalog().symptoms.map { it.id }

    /* ── Coverage ────────────────────────────────────────────────────────── */

    @Test
    fun everyShippedSymptomHasItsOwnFigure() {
        val ids = symptomIds()

        assertEquals(
            "the shipped catalogue has grown and the glyph table has not; the " +
                "affected symptoms will render the neutral figure instead",
            emptyList<String>(),
            DiagnosisIcons.unresolvedFor(ids)
        )
        assertEquals(
            "two symptoms resolve to the same figure, so the picker shows the same " +
                "picture twice and claims those two symptoms look alike",
            emptyList<String>(),
            DiagnosisIcons.duplicateAssignments(ids)
        )
        assertEquals(
            "the table names one symptom more than once, so which figure it gets " +
                "depends on the order the table happens to be written in",
            emptyList<String>(),
            DiagnosisIcons.repeatedIds()
        )
    }

    @Test
    fun everyShippedSymptomResolvesToANonNeutralFigure() {
        // Separate from the coverage test on purpose: a symptom could resolve and
        // still land on NEUTRAL, which would be every chip drawing the same leaf.
        val offenders = symptomIds().filter {
            DiagnosisIcons.forSymptomId(it) == DiagnosisIcons.NEUTRAL
        }

        assertEquals(
            "these symptoms resolve to the neutral fallback, so the whole picker " +
                "shows one figure: $offenders",
            emptyList<String>(),
            offenders
        )
    }

    @Test
    fun theCatalogueFitsInsideTheVocabularyWithRoomToSpare() {
        val ids = symptomIds()

        assertTrue(
            "the catalogue ships ${ids.size} symptoms and the vocabulary can " +
                "produce only ${DiagnosisIcons.capacity} figures",
            DiagnosisIcons.capacity >= ids.size
        )
        assertTrue(
            "no figure may be shared, so the vocabulary has to be strictly larger " +
                "than the catalogue",
            DiagnosisIcons.capacity > ids.size
        )
    }

    @Test
    fun noMotifIsUsedMoreOftenThanThereAreQualifiers() {
        // The structural precondition for the uniqueness guarantee. It happens to
        // hold by construction today, but it is what would break first if someone
        // grouped the table by symptom rather than by motif and grew one block.
        val perBase = DiagnosisIcons.catalog.values.groupingBy { it.base }.eachCount()
        val offenders = perBase.filterValues { it > DiagnosisGlyphMark.entries.size }
            .map { "${it.key}=${it.value}" }

        assertEquals(
            "a motif with more symptoms than qualifiers must reuse a figure: $offenders",
            emptyList<String>(),
            offenders
        )
    }

    /* ── Dropped and reported, never coerced ─────────────────────────────── */

    @Test
    fun aSymptomTheTableDoesNotKnowResolvesToNullRatherThanToSomebodyElsesPicture() {
        assertNull(
            "an unknown id must resolve to null so the caller can report it; " +
                "handing out a figure would be a lie about the picture",
            DiagnosisIcons.forSymptomId("un_sintoma_que_no_existe")
        )
        assertEquals(
            "the id has to be reported, not swallowed",
            listOf("un_sintoma_que_no_existe"),
            DiagnosisIcons.unresolvedFor(listOf("un_sintoma_que_no_existe"))
        )
    }

    @Test
    fun anUnresolvedSymptomIsNamedInTheIntegrityNotice() {
        val notice = DiagnosisSearch.incompleteEs(missingGlyphs = 2, unmappedCategories = 0)

        assertTrue(
            "the notice has to count the symptoms it cannot show: \"$notice\"",
            notice.contains("2")
        )
        assertTrue("\"$notice\"", notice.isNotBlank())
    }

    @Test
    fun aCleanCatalogueProducesNoIntegrityNoticeAtAll() {
        assertEquals(
            "an empty string is the signal the screen uses to render nothing, and a " +
                "complete catalogue must not render a notice",
            "",
            DiagnosisSearch.incompleteEs(missingGlyphs = 0, unmappedCategories = 0)
        )
    }

    /* ── The figures themselves ──────────────────────────────────────────── */

    @Test
    fun everyFigureIsAnnouncedInSpanishRatherThanByItsKey() {
        DiagnosisIcons.catalog.forEach { (id, glyph) ->
            val spoken = glyph.contentDescriptionEs

            assertTrue("$id announces nothing", spoken.isNotBlank())
            assertTrue(
                "$id is announced as \"$spoken\", which is the raw key rather than a " +
                    "description",
                !spoken.contains("__")
            )
            assertTrue(
                "$id is announced as \"$spoken\", which names no motif",
                glyph.base.labelEs.isNotBlank()
            )
        }
    }

    @Test
    fun aFigureWithNoQualifierIsAnnouncedAsTheMotifAlone() {
        val plain = DiagnosisGlyph(DiagnosisGlyphBase.LEAF, DiagnosisGlyphMark.PLAIN)

        assertEquals(
            "adding a dangling comma to a figure that has no qualifier would be a " +
                "screen reader saying \"Hoja, \"",
            DiagnosisGlyphBase.LEAF.labelEs,
            plain.contentDescriptionEs
        )
    }

    @Test
    fun everyFiguresKeyIsDerivedFromItsTwoParts() {
        assertEquals(
            "the key is what the cache and the tests join on, so it cannot be " +
                "written by hand and drift from the drawing",
            "${DiagnosisGlyphBase.MITE.key}__${DiagnosisGlyphMark.DOT.key}",
            DiagnosisGlyph(DiagnosisGlyphBase.MITE, DiagnosisGlyphMark.DOT).key
        )
    }

    @Test
    fun everyMotifAndEveryQualifierIsUsedByAtLeastOneShippedSymptom() {
        // A vocabulary member nothing draws is dead weight, and worse, it is a
        // slot a future symptom will be assigned to by the "there is room here"
        // reasoning without anybody checking what it looks like.
        val unusedBases = DiagnosisGlyphBase.entries.filterNot { it in DiagnosisIcons.usedBases }

        assertEquals(
            "these motifs are in the vocabulary but no symptom uses them, so they " +
                "cannot be reviewed: $unusedBases",
            emptyList<DiagnosisGlyphBase>(),
            unusedBases
        )
        val usedMarks = DiagnosisIcons.catalog.values.map { it.mark }.toSet()
        val unusedMarks = DiagnosisGlyphMark.entries.filterNot { it in usedMarks }

        assertEquals(
            "these qualifiers are in the vocabulary but no symptom uses them: $unusedMarks",
            emptyList<DiagnosisGlyphMark>(),
            unusedMarks
        )
    }

    /* ── Categories ──────────────────────────────────────────────────────── */

    @Test
    fun everyShippedConditionCategoryFallsIntoOneOfTheFourPickerBuckets() {
        val keys = catalog().conditions.map { it.category }

        assertEquals(
            "a condition category with no bucket would be filed nowhere and be " +
                "unreachable through the filter",
            emptyList<String>(),
            DiagnosisCategory.unmappedKeys(keys)
        )
    }

    @Test
    fun anUnknownConditionCategoryIsReportedRatherThanGuessedAt() {
        assertNull(
            "guessing would file the condition under whichever bucket was nearest",
            DiagnosisCategory.forConditionKey("viral_nuevo_del_asset")
        )
        assertEquals(
            listOf("viral_nuevo_del_asset"),
            DiagnosisCategory.unmappedKeys(listOf("deficiency", "viral_nuevo_del_asset"))
        )
    }

    @Test
    fun theFourBucketsBetweenThemCoverEveryConditionExactlyOnce() {
        val buckets = DiagnosisCategory.entries
        val counts = buckets.associateWith { it.conditionKeys.size }
        val total = counts.values.sum()

        assertEquals(
            "the buckets' condition keys overlap or leave a gap, so a condition " +
                "would appear in two filter rows or in none: $counts",
            DiagnosisCategory.knownConditionKeys.size,
            total
        )
        val duplicated = counts.filterValues { it == 0 }.keys.toList()

        assertTrue(
            "a bucket with no conditions would render as an empty filter row: $duplicated",
            duplicated.isEmpty()
        )
    }

    @Test
    fun everySymptomIsReachableFromAtLeastOneBucket() {
        val shipped = catalog()

        DiagnosisSearch.index(shipped.symptoms, shipped.conditions).forEach { entry ->
            assertTrue(
                "${entry.symptom.id} is in no bucket, so the category filter hides " +
                    "it permanently",
                entry.buckets.isNotEmpty()
            )
        }
    }

    @Test
    fun aSymptomNoConditionDeclaresIsReachableFromEveryBucket() {
        // The one documented default in this model. A symptom nobody has
        // attributed has to be findable everywhere rather than hidden in one, and
        // the fallback is spelled out in `DiagnosisSearch.symptomBuckets`.
        val orphan = DiagnosisCondition(id = "sin_esta", category = "pest", symptomWeights = emptyMap())

        assertEquals(
            DiagnosisCategory.entries.toList(),
            DiagnosisSearch.symptomBuckets("ningun_sintoma", listOf(orphan))
        )
    }

    /* ── Search ──────────────────────────────────────────────────────────── */

    @Test
    fun anAccentlessQueryFindsAnAccentedSymptom() {
        val shipped = catalog()
        val index = DiagnosisSearch.index(shipped.symptoms, shipped.conditions)

        val lower = DiagnosisSearch.filter(index, "acaro")
        val accented = DiagnosisSearch.filter(index, "ácaro")
        val upper = DiagnosisSearch.filter(index, "ÁCARO")

        assertTrue("\"ácaro\" has to find something", accented.isNotEmpty())
        assertEquals(
            "folding is supposed to make these three queries identical, and the " +
                "shipped catalogue has \"Ácaros diminutos\"",
            accented.map { it.symptom.id },
            lower.map { it.symptom.id }
        )
        assertEquals(accented.map { it.symptom.id }, upper.map { it.symptom.id })
    }

    @Test
    fun aBlankQueryReturnsEverythingRatherThanNothing() {
        val shipped = catalog()
        val index = DiagnosisSearch.index(shipped.symptoms, shipped.conditions)

        listOf("", "   ", "\t").forEach { query ->
            assertEquals(
                "a blank query is not a filter that matches no rows",
                index.size,
                DiagnosisSearch.filter(index, query).size
            )
        }
    }

    @Test
    fun everyTokenOfAMultiWordQueryHasToMatch() {
        val shipped = catalog()
        val index = DiagnosisSearch.index(shipped.symptoms, shipped.conditions)

        val both = DiagnosisSearch.filter(index, "hoja moho")
        val one = DiagnosisSearch.filter(index, "hoja")

        assertTrue("the conjunction has to be narrower than either term", both.size < one.size)
        both.forEach { entry ->
            assertTrue(
                "${entry.symptom.id} does not mention a leaf",
                "hoja" in entry.haystack
            )
        }
    }

    @Test
    fun aQueryNobodyWroteReturnsNothingAndSaysSo() {
        val shipped = catalog()
        val index = DiagnosisSearch.index(shipped.symptoms, shipped.conditions)

        assertEquals(
            emptyList<DiagnosisSearchEntry>(),
            DiagnosisSearch.filter(index, "zzzzqqq")
        )
        assertTrue(
            "the empty state needs a sentence, or the picker looks broken",
            DiagnosisSearch.NO_RESULTS_ES.isNotBlank()
        )
    }

    @Test
    fun theSearchableTextCarriesTheConditionsOwnProseNotAnInventedSummary() {
        val shipped = catalog()
        val index = DiagnosisSearch.index(shipped.symptoms, shipped.conditions)
        // "fusarium" appears in no symptom label; it is in a condition's cause line.
        // Finding it proves the index folds in the shipped prose rather than a
        // hand-written summary nobody fact-checks.
        val hits = DiagnosisSearch.filter(index, "fusarium")

        assertTrue(
            "the search has to reach the conditions' own text, or \"symptom text\" " +
                "means only the label and the search is half a feature",
            hits.isNotEmpty()
        )
    }

    @Test
    fun theBucketFilterAndTheTextFilterAreIndependentInputs() {
        val shipped = catalog()
        val index = DiagnosisSearch.index(shipped.symptoms, shipped.conditions)

        val bugs = DiagnosisSearch.inBucket(index, DiagnosisCategory.PATHOGENS)
        val every = DiagnosisSearch.inBucket(index, null)

        assertTrue("the bucket has to actually narrow the list", bugs.size < index.size)
        assertEquals("a null bucket returns everything", index.size, every.size)
        assertTrue(
            "every filtered entry has to belong to the bucket",
            bugs.all { DiagnosisCategory.PATHOGENS in it.buckets }
        )
    }

    @Test
    fun theCountLineReadsBothNumbersSoTheResultIsNotTakenOnTrust() {
        assertEquals("7 de 116 síntomas", DiagnosisSearch.countEs(7, 116))
    }

    @Test
    fun everyTissueKeyTheAssetShipsHasASpanishLabel() {
        // A tissue key is a different axis from a diagnosis category; the asset
        // writes bare English (`leaf`, `root`) and this is the only Spanish a
        // picker would otherwise print.
        val tissues = catalog().symptoms.map { it.category }.toSet()

        tissues.forEach { key ->
            val label = DiagnosisSearch.tissueLabelEs(key)

            assertTrue("$key has no Spanish label", label.isNotBlank())
            assertTrue(
                "the label for '$key' is the raw English key, so the picker would " +
                    "print \"$label\"",
                label != key
            )
        }
    }
}