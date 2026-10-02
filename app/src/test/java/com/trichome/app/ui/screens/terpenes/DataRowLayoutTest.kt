package com.trichome.app.ui.screens.terpenes

import com.trichome.app.model.DataRowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F2 — the detail page's label/value row, which shipped a collision.
 *
 * ## The defect, as seen
 *
 * On the Mirceno page the row reading `Origen de la ventana` collided with its
 * value `Medida en la tabla de Séquito`: no gap between them and the value broke
 * under itself. Reproduced on a device at 03:10.
 *
 * ## Why the row and not the call site
 *
 * Six other rows on the same page have the identical shape — `Fórmula`,
 * `Masa molar`, `Familia química`, `Punto de ebullición`,
 * `Riqueza en cannabis`, `Ventana de vaporización` — and every one of them
 * looked fine only because its value happens to be short. A fix applied to the
 * provenance row would leave a row that breaks again the first time one of the
 * others gets a longer value. So the constraint belongs to `DataRow` itself and
 * these tests hold it there.
 *
 * ## Why some of this reads source
 *
 * Compose has no unit-test runtime in this project, so the *numbers* are pinned
 * in the model by [DataRowLayout] and tested directly, and the *shape* of the
 * composable is pinned here by reading its source — the same mechanism
 * `TerpeneDetailVolatilityTest` uses for the evidence line. Comments are stripped
 * before every scan.
 */
class DataRowLayoutTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/screens/terpenes"),
        File("app/src/main/java/com/trichome/app/ui/screens/terpenes")
    ).filter { it.isDirectory }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    private fun detailScreen(): String {
        val file = sources.flatMap { dir ->
            dir.listFiles { f -> f.name == "TerpeneDetailScreen.kt" }?.toList() ?: emptyList()
        }.firstOrNull() ?: error("TerpeneDetailScreen.kt is not in the terpenes package")
        return stripComments(file.readText(Charsets.UTF_8))
    }

    /**
     * The body of `DataRow` alone, bounded by the next composable in the file.
     *
     * Bounded because the volatility curve further down uses `weight` too, with
     * its own geometry, and this row's rules are not that one's rules.
     */
    private fun dataRowBody(): String {
        val text = detailScreen()
        val start = text.indexOf("private fun DataRow(")
        assertTrue("DataRow is missing from the detail screen", start > 0)
        val rest = text.substring(start)
        val end = listOf("\n@Composable", "\nprivate fun").firstNotNullOfOrNull {
            rest.indexOf(it).takeIf { index -> index > 0 }
        } ?: rest.length
        return rest.substring(0, end)
    }

    /* ── The numbers, in the model ───────────────────────────────────────── */

    @Test
    fun theGapIsNeverZero() {
        assertTrue(
            "the collision shipped because the row had no air in it at all",
            DataRowLayout.GAP_DP > 0
        )
        assertEquals(12, DataRowLayout.GAP_DP)
    }

    @Test
    fun theValueWeightCannotBeZeroBecauseComposeThrowsOnIt() {
        // The same class of defect as the curve's zero bar: `RowScope.weight`
        // throws `IllegalArgumentException: invalid weight 0.0` while composing,
        // and no JVM test in this project can see it happen. It is pinned here so
        // the value cannot regress to zero unnoticed.
        assertTrue(
            "Modifier.weight(0f) throws while composing; the value's weight must " +
                "stay strictly positive, was ${DataRowLayout.VALUE_WEIGHT}",
            DataRowLayout.VALUE_WEIGHT > 0f
        )
        assertEquals(1f, DataRowLayout.VALUE_WEIGHT, 0f)
    }

    @Test
    fun theValueGetsWhateverIsLeftOfTheRowAndNeverMore() {
        // The arithmetic `Row` performs for this row: measure the label first,
        // spend the gap, hand the rest to a value weighted at 1f.
        assertEquals(335, DataRowLayout.remainingForValueDp(rowWidthDp = 360, labelWidthDp = 13))
        assertEquals(
            "a short label must leave the value room to sit flush right",
            213,
            DataRowLayout.remainingForValueDp(rowWidthDp = 360, labelWidthDp = 135)
        )
    }

    @Test
    fun aLabelWiderThanTheRowCannotProduceANegativeWidth() {
        assertEquals(
            "a negative width is not a layout, it is an arithmetic mistake waiting " +
                "to be published",
            0,
            DataRowLayout.remainingForValueDp(rowWidthDp = 360, labelWidthDp = 360)
        )
        assertEquals(0, DataRowLayout.remainingForValueDp(rowWidthDp = 360, labelWidthDp = 500))
    }

    /* ── The shape, in the composable ────────────────────────────────────── */

    @Test
    fun theRowDoesNotSpaceItsChildrenApartWithNoWidthSplit() {
        val body = dataRowBody()

        assertFalse(
            "`Arrangement.SpaceBetween` with two unweighted texts is what let the " +
                "value overflow the row and land on the label",
            body.contains("Arrangement.SpaceBetween")
        )
    }

    @Test
    fun theValueIsTheWeightedChildAndTheLabelIsNot() {
        val body = dataRowBody()

        assertTrue(
            "the value has to be the weighted one or the label competes with it",
            Regex("""\.weight\(DataRowLayout\.VALUE_WEIGHT\)""").containsMatchIn(body)
        )
        assertTrue(
            "the value's modifier must come from the model's constant, not a literal",
            !Regex("""\.weight\(\s*\d""").containsMatchIn(body)
        )
    }

    @Test
    fun theLabelAndTheValueAreSeparatedByTheModelsGap() {
        val body = dataRowBody()

        assertTrue(
            "the gap between label and value is the fix; it has to be there",
            body.contains("Spacer(Modifier.width(DataRowLayout.GAP_DP.dp))")
        )
    }

    @Test
    fun theValueStaysRightAlignedSoShortRowsDoNotMove() {
        val body = dataRowBody()

        assertTrue(
            "removing SpaceBetween must not left-align every value on the page",
            body.contains("textAlign = TextAlign.End")
        )
    }

    @Test
    fun everyDataRowOnThePageGoesThroughTheSharedRow() {
        val text = detailScreen()
        val callSites = Regex("""DataRow\(""").findAll(text).count()

        assertTrue(
            "the identity and volatility cards both use DataRow; found $callSites " +
                "call sites",
            callSites >= 7
        )
        assertTrue(
            "the provenance row that shipped the collision has to reach the shared " +
                "row, not a bespoke composable",
            text.contains("DataRow(\"Origen de la ventana\", content.provenanceLabelEs)")
        )
        assertFalse(
            "no row may carry its own layout while DataRow carries the constraint",
            text.contains("private fun ProvenanceRow(")
        )
    }
}