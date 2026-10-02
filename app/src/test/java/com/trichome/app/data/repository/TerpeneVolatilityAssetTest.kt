package com.trichome.app.data.repository

import com.trichome.app.model.CatalogVolatilityRow
import com.trichome.app.model.TerpeneFamily
import com.trichome.app.model.TerpeneVolatilityCopy
import com.trichome.app.model.TerpeneVolatilityIndex
import com.trichome.app.model.TerpeneVaporisation
import com.trichome.app.model.VolatilityDerivation
import com.trichome.app.model.VolatilityProvenance
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F2 — the volatility index, against the two shipped assets as they sit on disk.
 *
 * ## Why this reads the assets rather than a fixture
 *
 * The whole point of F2 is that all 158 catalog compounds are reachable from one
 * view type. A fixture would only prove the model agrees with itself. These
 * tests read `terpenes.json` and `entourage_data.json` for real, which is the
 * only way a claim like "148 of these are derived" can be checked rather than
 * asserted.
 *
 * They also pin the honesty claims to the data: the derivation must never be
 * narrower than a band the app actually measures, and the headroom constant
 * must be the widest margin the shipped table shows.
 */
class TerpeneVolatilityAssetTest {

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

    private fun catalog(): List<Terpene> =
        json.decodeFromString<TerpeneCatalog>(
            String(assetBytes("terpenes.json"), Charsets.UTF_8)
        ).terpenes

    private fun measured(): List<TerpeneVaporisation> =
        json.decodeFromString<EntourageBible>(
            String(assetBytes("entourage_data.json"), Charsets.UTF_8)
        ).toContent().vaporisation

    private fun index(): TerpeneVolatilityIndex {
        val rows = catalog().map { entry ->
            CatalogVolatilityRow(
                catalogId = entry.id,
                labelEs = entry.name,
                family = TerpeneFamily.fromFamilyEs(entry.family),
                molarMassEs = entry.molarMass,
                boilingPointEs = entry.boilingPoint
            )
        }
        return TerpeneVolatilityIndex.from(rows, measured())
    }

    /* ── Every compound is reachable ─────────────────────────────────────── */

    @Test
    fun everyCatalogCompoundHasAVolatilityRow() {
        val catalog = catalog()
        val index = index()

        assertEquals("terpenes.json ships 158 compounds", 158, catalog.size)
        assertEquals(
            "a compound with no volatility row is a page that reads as a broken " +
                "database; dropped ids: ${index.withoutBoilingPoint}",
            emptyList<String>(),
            index.withoutBoilingPoint
        )
        assertEquals(catalog.size, index.size)
        catalog.forEach { entry ->
            assertNotNull(
                "${entry.id} ('${entry.name}', '${entry.boilingPoint}') has no row",
                index.forId(entry.id)
            )
        }
    }

    @Test
    fun exactlyTenBandsAreMeasuredAndTheRestAreDerived() {
        val index = index()

        assertEquals(
            "entourage_data.json ships ten bands; those and only those may claim to " +
                "have measured a window",
            10,
            index.measuredCount
        )
        assertEquals(148, index.derivedCount)
        assertEquals(158, index.size)
    }

    @Test
    fun everyMeasuredRowIsTheModulesOwnWindowAndNotARederivation() {
        val shipped = measured().associate { it.terpene.catalogId to it }
        val index = index()

        assertEquals("the join must be one-to-one", 10, shipped.size)
        shipped.forEach { (catalogId, row) ->
            val entry = index.forId(catalogId)
            assertNotNull("$catalogId is missing from the index", entry)
            assertEquals(
                "$catalogId must keep the module's own band",
                VolatilityProvenance.MEASURED,
                entry!!.provenance
            )
            assertEquals(row.minTempC, entry.window.minTempC)
            assertEquals(row.maxTempC, entry.window.maxTempC)
            assertEquals(row.noteEs, entry.noteEs)
        }
    }

    @Test
    fun theBoilingPointAlwaysComesFromTheEncyclopedia() {
        val index = index()
        catalog().forEach { entry ->
            assertEquals(
                "${entry.id}: terpenes.json is canonical for the boiling point",
                entry.boilingPointCelsius,
                index.forId(entry.id)!!.boilingPointC
            )
            assertEquals(
                "${entry.id}: the display string is rendered verbatim",
                entry.boilingPoint,
                index.forId(entry.id)!!.boilingPointEs
            )
        }
    }

    @Test
    fun theMolarMassSurvivesTheMappingIntoTheIndex() {
        val hexanal = index().forId("hexanal")

        assertNotNull(hexanal)
        assertEquals("100.16", hexanal!!.molarMassEs)
        assertEquals("Hexanal", hexanal.labelEs)
        assertEquals("Monoterpeno", hexanal.family.labelEs)
    }

    /* ── The derivation, measured against the shipped table ──────────────── */

    @Test
    fun noDerivedBandIsNarrowerThanABandTheAppActuallyMeasures() {
        val shippedWidths = measured().map { it.maxTempC - it.minTempC }
        val derivedWidths = index().rows
            .filter { it.isDerived }
            .map { it.window.widthC }

        assertTrue("the shipped table ships widths", shippedWidths.isNotEmpty())
        assertTrue("the index ships derived bands", derivedWidths.isNotEmpty())
        assertTrue(
            "the narrowest derived band is ${derivedWidths.min()} °C but the " +
                "narrowest measured one is ${shippedWidths.min()} °C; a derived " +
                "band must never be the finer promise",
            derivedWidths.min() >= shippedWidths.min()
        )
        assertTrue(
            "the widest shipped band is ${shippedWidths.max()} °C and the " +
                "narrowest derived band is ${derivedWidths.min()} °C",
            derivedWidths.min() >= shippedWidths.max()
        )
    }

    @Test
    fun theHeadroomConstantIsTheWidestMarginTheShippedTableShows() {
        val shippedMargins = measured().map { it.maxTempC - it.boilingPointC }

        assertEquals(
            "the constant has to be the widest observed margin, or the model is " +
                "more optimistic than anything the app has measured",
            VolatilityDerivation.WIDEST_SHIPPED_HEADROOM_C,
            shippedMargins.max()
        )
        assertEquals(
            VolatilityDerivation.NARROWEST_SHIPPED_HEADROOM_C,
            shippedMargins.min()
        )
        assertTrue(
            "every family must resolve to a headroom at least the widest margin",
            TerpeneFamily.entries.all {
                it.derivedHeadroomC >= VolatilityDerivation.WIDEST_SHIPPED_HEADROOM_C
            }
        )
    }

    @Test
    fun theShippedFamiliesShowNoPerFamilyHeadroomEffect() {
        // The measurement behind the model having one constant rather than four:
        // the eight monoterpenes and the two sesquiterpenes overlap, so there is
        // nothing to fit.
        val byFamily = measured().groupBy { TerpeneFamily.fromFamilyEs(it.terpene.familyEs) }
            .mapValues { (_, rows) -> rows.map { it.maxTempC - it.boilingPointC } }

        val mono = byFamily.getValue(TerpeneFamily.MONOTERPENE)
        val sesqui = byFamily.getValue(TerpeneFamily.SESQUITERPENE)

        assertEquals(8, mono.size)
        assertEquals(2, sesqui.size)
        assertTrue(
            "the monoterpene margins run ${mono.sorted()} and the sesquiterpene " +
                "margins run ${sesqui.sorted()}",
            mono.max() <= sesqui.max() && sesqui.min() <= mono.min()
        )
    }

    @Test
    fun aDerivedCeilingIsNeverBelowTheMeasuredCeilingOfTheSameCompound() {
        val index = index()
        measured().forEach { row ->
            val derived = VolatilityDerivation.derive(
                row.boilingPointC,
                TerpeneFamily.fromFamilyEs(row.terpene.familyEs)
            )
            assertTrue(
                "${row.terpene.key}: derived ceiling ${derived.maxTempC} °C is below " +
                    "the measured ${row.maxTempC} °C",
                derived.maxTempC >= row.maxTempC
            )
        }
    }

    @Test
    fun everyBandInTheIndexHoldsTheShippedWindowInvariant() {
        index().rows.forEach { entry ->
            assertTrue(
                "${entry.catalogId}: floor ${entry.window.minTempC} must be at or " +
                    "below the boiling point ${entry.boilingPointC}",
                entry.window.minTempC <= entry.boilingPointC
            )
            assertTrue(
                "${entry.catalogId}: ceiling ${entry.window.maxTempC} must be above " +
                    "the boiling point ${entry.boilingPointC}",
                entry.window.maxTempC > entry.boilingPointC
            )
        }
    }

    @Test
    fun aDerivedFloorNeverSitsAboveTheBoilingPoint() {
        // F1 documented that `minTempC == boilingPointC` in 9 of 10 shipped rows
        // is the invariant holding, not a copy-paste bug. The derivation rounds
        // its floor *down*, so it lands below that boundary and must never push
        // it up above it.
        index().rows.filter { it.isDerived }.forEach { entry ->
            assertTrue(
                "${entry.catalogId}: a derived floor of ${entry.window.minTempC} must " +
                    "never sit above the boiling point ${entry.boilingPointC}",
                entry.window.minTempC <= entry.boilingPointC
            )
        }
    }

    @Test
    fun noFamilyInTheCatalogFallsBackToUnclassified() {
        val index = index()
        assertTrue(
            "an unclassified family means the derivation fell back, and the " +
                "labels on screen would read 'Sin clasificar' for shipped data",
            index.rows.none { it.family == TerpeneFamily.UNCLASSIFIED }
        )
    }

    /* ── The three families the screenshots cover ────────────────────────── */

    @Test
    fun eachFamilyHasItsOwnCharacteristicBand() {
        val index = index()

        val mono = index.forFamily(TerpeneFamily.MONOTERPENE)
        val sesqui = index.forFamily(TerpeneFamily.SESQUITERPENE)
        val di = index.forFamily(TerpeneFamily.DITERPENE)

        assertEquals(85, mono.size)
        assertEquals(63, sesqui.size)
        assertEquals(10, di.size)

        // The diterpenes are the interesting case: the largest molecules, all
        // above every other family, and none of them measured.
        assertTrue(
            "every diterpene must boil at or above 300 °C; found " +
                di.map { it.boilingPointC },
            di.all { it.boilingPointC >= 300 }
        )
        assertTrue(
            "every diterpene band is derived, which is what the diterpene " +
                "screenshot has to be exercising",
            di.all { it.provenance == VolatilityProvenance.DERIVED }
        )
    }

    @Test
    fun theDiterpeneBandsAreNeverReachableBeforeTheHardestSesquiterpene() {
        // The three families are a ladder, not three separate shelves, and the
        // top of it overlaps: alpha_bisabolol boils at 307 °C, so its derived
        // band is 300–340 °C and the lowest diterpene's is 300–330 °C. The
        // honest statement is about the *floors*: no diterpene starts coming off
        // before the hardest sesquiterpene does. Anything stronger would be a
        // claim the catalog does not support.
        val index = index()
        val lowestDiterpeneFloor = index.forFamily(TerpeneFamily.DITERPENE)
            .minOf { it.window.minTempC }
        val highestOtherFloor = index.rows
            .filter { it.family != TerpeneFamily.DITERPENE }
            .maxOf { it.window.minTempC }

        assertTrue(
            "the lowest diterpene floor ($lowestDiterpeneFloor °C) must not start " +
                "before the highest other-family floor ($highestOtherFloor °C)",
            lowestDiterpeneFloor >= highestOtherFloor
        )
    }

    @Test
    fun theThreeFamiliesToPhotographHaveTheBandsTheAppWillRender() {
        // Pinned so the screenshots have something to be checked against, and so
        // a change to the derivation shows up as a failing number rather than as
        // a screenshot nobody diffs.
        val index = index()

        assertEquals(
            "Vanilina is a monoterpene at 285 °C, so its band is derived " +
                "280–320 °C",
            "≈ 280–320 °C",
            index.forId("vanillin")!!.window.formatEs()
        )
        assertEquals(
            "Bisabolol is a sesquiterpene at 263 °C, so its band is derived " +
                "260–300 °C",
            "≈ 260–300 °C",
            index.forId("bisabolol")!!.window.formatEs()
        )
        assertEquals(
            "Fitol is a diterpene at 350 °C, so its band is derived 350–380 °C",
            "≈ 350–380 °C",
            index.forId("phytol")!!.window.formatEs()
        )
        assertEquals(
            "Pineno alfa is one of the ten measured rows, so it must not be marked",
            "156–180 °C",
            index.forId("alpha_pinene")!!.window.formatEs()
        )
        assertEquals(
            "Mirceno is also measured, at 167–195 °C; a screenshot of it must " +
                "show a measured band, not a derived one",
            "167–195 °C",
            index.forId("myrcene")!!.window.formatEs()
        )
    }

    /* ── The detail page's curve ─────────────────────────────────────────── */

    @Test
    fun everyCompoundHasBetweenThreeAndEightPartnersSoNoCurveIsTruncated() {
        val counts = catalog().map { it.pairsWith.size }

        assertTrue("every shipped compound ships partners", counts.all { it >= 3 })
        assertEquals(
            "the detail page's curve is the compound plus its partners; a " +
                "compound with an unbounded partner list would have to be cut, and " +
                "a cut curve that did not say so is the defect this avoids",
            8,
            counts.max()
        )
    }

    @Test
    fun everyPartnerIdResolvesToACatalogCompoundWithARow() {
        val index = index()
        val byId = catalog().associateBy { it.id }

        catalog().forEach { entry ->
            entry.pairsWith.forEach { partnerId ->
                assertTrue(
                    "${entry.id} points at '$partnerId', which is not in the catalog",
                    byId.containsKey(partnerId)
                )
                assertNotNull(
                    "${entry.id} -> $partnerId has no volatility row",
                    index.forId(partnerId)
                )
            }
        }
    }

    @Test
    fun theDetailPageCurveOfEveryCompoundIsBuildableAndCoversItself() {
        val index = index()

        catalog().forEach { entry ->
            val curve = index.curveFor(listOf(entry.id) + entry.pairsWith)

            assertFalse("${entry.id}: a curve over itself and its partners", curve.isEmpty)
            assertTrue(
                "${entry.id}: the curve must contain the compound it is about",
                curve.stepFor(entry.id) != null
            )
            assertTrue(
                "${entry.id}: every partner must be a rung too",
                entry.pairsWith.all { curve.stepFor(it) != null }
            )
        }
    }

    @Test
    fun everyDetailPageCurveCanPlaceEveryRungOnItsScale() {
        val index = index()

        catalog().forEach { entry ->
            val curve = index.curveFor(listOf(entry.id) + entry.pairsWith)
            curve.stages.forEach { stage ->
                val bar = curve.barFor(stage.step)
                assertTrue(
                    "${entry.id}: ${stage.step.catalogId} starts at ${bar.startFraction}",
                    bar.startFraction >= 0f && bar.startFraction < 1f
                )
                assertTrue(
                    "${entry.id}: ${stage.step.catalogId} is ${bar.widthFraction} wide",
                    bar.widthFraction > 0f
                )
            }
        }
    }

    /* ── A derived value is never quietly printed ────────────────────────── */

    @Test
    fun theMarkedAndUnmarkedCountsMatchTheProvenance() {
        val rows = index().rows
        val marked = rows.count { it.window.formatEs().startsWith(TerpeneVolatilityCopy.ESTIMATE_MARK) }

        assertEquals(
            "the approximation marker and the provenance flag have to agree on " +
                "every row, or a derived band can be printed as a measured one",
            rows.count { it.isDerived },
            marked
        )
        assertEquals(rows.size, rows.count { it.provenance == VolatilityProvenance.MEASURED } + marked)
    }

    @Test
    fun aDerivedEvidenceLineNeverClaimsAMeasurement() {
        index().rows.filter { it.isDerived }.forEach { entry ->
            val content = TerpeneVolatilityCopy.contentOf(entry)
            assertTrue(
                "${entry.catalogId}: ${content.evidenceEs}",
                content.evidenceEs.contains("estimada") &&
                    content.evidenceEs.contains("No es una ventana medida")
            )
            assertFalse(
                "${entry.catalogId}: a derived band must not be called measured",
                content.provenanceLabelEs.contains("Medida")
            )
        }
    }

    @Test
    fun aMeasuredEvidenceLineNeverClaimsAnEstimate() {
        index().rows.filter { !it.isDerived }.forEach { entry ->
            val content = TerpeneVolatilityCopy.contentOf(entry)
            assertTrue(
                "${entry.catalogId}: ${content.evidenceEs}",
                content.evidenceEs.contains("Ventana medida")
            )
            assertFalse(
                "${entry.catalogId}: a measured band must not be called an estimate",
                content.provenanceLabelEs.contains("Estimada")
            )
        }
    }

    @Test
    fun aRowIsOnlyEverMeasuredWhenTheModuleShipsIt() {
        val shippedIds = measured().map { it.terpene.catalogId }.toSet()
        index().rows.forEach { entry ->
            assertEquals(
                "${entry.catalogId}: provenance must follow the module's table",
                entry.catalogId in shippedIds,
                entry.provenance == VolatilityProvenance.MEASURED
            )
        }
    }

    @Test
    fun noBoilingPointInTheShippedCatalogIsARange() {
        // Nothing ships a range today, so the parser's range branch is untested
        // by the asset. Recorded rather than asserted: if one ever does, the
        // parsing test's synthetic cases are what carry it, and this is the note
        // that says the branch was never needed yet.
        val ranges = catalog().filter { it.boilingPoint.filter { c -> c.isDigit() }.length > 3 }

        assertEquals("no shipped boilingPoint is a range: $ranges", emptyList<Terpene>(), ranges)
    }

    /* ── Why the band is coarse, measured rather than claimed ────────────── */

    @Test
    fun theWithinFamilySpreadIsWhatForbidsAFinerBand() {
        // The whole derivation rests on one claim: a family label cannot tell you
        // which molecule you are holding. Measured here from the asset so the
        // KDoc table in `VolatilityDerivation` cannot drift or be wrong — it
        // understated the monoterpene spread by 31 °C, having missed
        // `umbellulone` at 100 °C.
        val index = index()

        data class Spread(val min: Int, val max: Int, val count: Int)

        fun spread(family: TerpeneFamily): Spread {
            val points = index.forFamily(family).map { it.boilingPointC }
            return Spread(points.min(), points.max(), points.size)
        }

        val mono = spread(TerpeneFamily.MONOTERPENE)
        val sesqui = spread(TerpeneFamily.SESQUITERPENE)
        val di = spread(TerpeneFamily.DITERPENE)

        assertEquals(Spread(100, 285, 85), mono)
        assertEquals(Spread(166, 307, 63), sesqui)
        assertEquals(Spread(300, 350, 10), di)

        assertEquals(
            "umbellulone is filed as a monoterpene at 100 °C, which is the reason a " +
                "monoterpene label supports nothing finer than a coarse band",
            100,
            index.forId("umbellulone")!!.boilingPointC
        )
        assertTrue(
            "the derivation is 30–40 °C wide; if a family's spread were narrower " +
                "than the narrowest derived band, the model would be quoting more " +
                "precision than the family supports",
            listOf(mono, sesqui, di).all { it.max - it.min >= VolatilityDerivation.NARROWEST_DERIVED_WIDTH_C }
        )
    }
}