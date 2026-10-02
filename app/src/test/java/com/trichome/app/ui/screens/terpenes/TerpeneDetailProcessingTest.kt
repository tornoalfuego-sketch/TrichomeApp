package com.trichome.app.ui.screens.terpenes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F4 — the structural rules of the two surfaces the processing block reaches.
 *
 * ## Why this reads source
 *
 * The failure modes are not behavioural and no JVM test can reach them. Compose has
 * no unit-test runtime on the `test` classpath in this project, which is not a
 * preference: three separate bugs have already come from composable-level logic
 * nothing could test — a `weight(0f)` that threw at compose time and took down
 * every terpene page, an unwighted `Row` that overflowed, and Spanish sentences
 * authored inside a composable. F4 adds a fourth class of risk to that list: a
 * card that names an extraction method and puts its safety framing somewhere else.
 * That cannot be caught by asserting on the model, because the model is correct and
 * the defect is in what the screen chose to print.
 *
 * So this file asserts, against the source with comments stripped:
 *
 * 1. **The safety line renders unconditionally**, in the same block as the method
 *    name — no `if`, no `AnimatedVisibility`, no "ver más".
 * 2. **The card is separate** from the volatility and agronomy cards rather than
 *    folded into either, because heat in a device and heat in a jar are different
 *    subjects and one of them carries a number.
 * 3. **No sentence is authored in the composable.** The strongest form of the rule,
 *    the one F3 established: the block holds no non-blank string literal at all.
 * 4. **One scroll owner per axis**, solid Material 3, no hardcoded colour, no
 *    `Surface` publishing `Color.Unspecified`.
 *
 * Comments are stripped before every scan, so a KDoc mentioning
 * `verticalScroll` or `Color.Unspecified` is not a hit. That rule has broken twice
 * in this repo and it is applied here on purpose.
 */
class TerpeneDetailProcessingTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/screens/terpenes"),
        File("app/src/main/java/com/trichome/app/ui/screens/terpenes")
    ).filter { it.isDirectory }

    private fun codeOf(name: String): String {
        val file = sources.flatMap { dir ->
            dir.listFiles { f -> f.name.endsWith(".kt") }?.toList() ?: emptyList()
        }.firstOrNull { it.name == name }
            ?: error("$name is not in the terpenes package")
        return stripComments(file.readText(Charsets.UTF_8))
    }

    private fun codeOfIn(dirPath: String, name: String): String {
        val file = listOf(File(dirPath), File("app/$dirPath"))
            .firstOrNull { it.isDirectory }
            ?.listFiles { f -> f.name == name }
            ?.firstOrNull()
            ?: error("could not locate $name in $dirPath")
        return stripComments(file.readText(Charsets.UTF_8))
    }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    private fun detailScreen(): String = codeOf("TerpeneDetailScreen.kt")

    /**
     * The body of one composable, from its declaration to the next one.
     *
     * Scoped on purpose: an unrelated composable in the same file must not decide
     * this. Same fix pattern `DataRowLayoutTest` needed.
     */
    private fun bodyOf(text: String, functionName: String): String {
        val start = text.indexOf("fun $functionName(")
        assertTrue("$functionName is not in the source", start > 0)
        val rest = text.substring(start)
        val end = Regex("""\n(@Composable|(private |internal )?fun )""").find(rest)
            ?.range?.first
            ?: rest.length
        return rest.substring(0, end)
    }

    private fun processingCard(): String = bodyOf(detailScreen(), "ProcessingCard")

    /* ── It is a third card, not a fold-in ──────────────────────────────── */

    @Test
    fun theProcessingBlockIsOnThePageAsItsOwnCardBesideTheOtherTwo() {
        val text = detailScreen()

        assertTrue("the volatility card has to still be there", text.contains("VolatilityCard("))
        assertTrue("the agronomy card has to still be there", text.contains("AgronomyCard("))
        assertTrue("the processing card has to be there", text.contains("ProcessingCard("))
        assertTrue(
            "the block comes from the ViewModel, not from a local lookup",
            text.contains("vm.processingFor(")
        )
        assertTrue(
            "and the reading order is volatility, then agronomy, then processing",
            text.indexOf("VolatilityCard(") < text.indexOf("AgronomyCard(") &&
                text.indexOf("AgronomyCard(") < text.indexOf("ProcessingCard(")
        )
    }

    @Test
    fun theCardIsGatedOnTheCompoundAndNotOnWhetherItHasAnEntry() {
        // The gate is "is this a compound the Séquito module models", the same one
        // the page's Séquito button uses. Gating on the entry would make the honest
        // no-entry sentence unreachable — and on F4's terms that sentence matters
        // more, because the undocumented path still shows the comparison, which
        // names the extraction methods.
        val body = bodyOf(detailScreen(), "TerpeneDetailScreen")

        assertTrue(
            "the null check has to be on the resolved block",
            body.contains("if (processingBlock != null)")
        )
    }

    @Test
    fun theProcessingCardDoesNotFoldTheVolatilityOrAgronomyBlocksIntoItself() {
        val card = processingCard()

        assertFalse(
            "the processing card must not re-render the volatility content: heat " +
                "in a device and heat in a jar are different subjects, and the " +
                "first one carries a number",
            card.contains("volatility") || card.contains("Volatility")
        )
        assertFalse(
            "nor the agronomy content",
            card.contains("agronomy") || card.contains("Agronomy")
        )
    }

    /* ── The safety line renders, and is not a disclosure ───────────────── */

    @Test
    fun theComparisonAndTheResidueSentenceRenderUnconditionally() {
        val card = processingCard()

        assertTrue(
            "the comparison names the extraction methods, so it has to reach the " +
                "screen",
            card.contains("content.comparisonEs")
        )
        assertTrue(
            "and the residual-solvent sentence has to be in the same block as it, " +
                "not in a footnote further down",
            card.contains("content.solventSafetyEs")
        )
        // Two separate checks, because conflating them made this test vacuous: a bare
        // `card.contains("if")` matches any identifier ending in `if`, which every
        // Kotlin file has, so it never failed. The conditionals are named, and the
        // disclosure affordances are a separate list.
        listOf("AnimatedVisibility", "expandVertically", "Ver más", "ver más").forEach { affordance ->
            assertFalse(
                "the residue sentence must not be behind a disclosure ($affordance)",
                card.contains(affordance)
            )
        }
        listOf("solventSafetyEs", "comparisonEs").forEach { field ->
            assertFalse(
                "$field has to render unconditionally: a conditional here would " +
                    "leave a card that names a solvent route with nothing about the " +
                    "residue",
                Regex("""\bif\s*\([^)]*\b$field\b""").containsMatchIn(card)
            )
        }
    }

    @Test
    fun everyMethodsOwnSafetyLineRendersInsideItsBlock() {
        val card = processingCard()

        assertTrue(
            "each method note carries its guide, and the guide carries the safety " +
                "line, so the composable has to print guide.safetyEs",
            card.contains("method.guide.safetyEs")
        )
        assertFalse(
            "a conditional per method would let a method name render with no " +
                "framing",
            Regex("""\bif\s*\(\s*method\.""").containsMatchIn(card)
        )
    }

    @Test
    fun everySharedGuideIsRenderedIncludingTheMethodsThisCompoundHasNoNoteFor() {
        val card = processingCard()

        assertTrue(
            "all the guides are rendered: dropping the ones this compound has no " +
                "note for would hide that the mechanism is shared, and it would " +
                "also remove the guaranteed place a solvent-free route's safety " +
                "line is read",
            card.contains("content.guides.forEach")
        )
        assertTrue(
            "and so are the shared preservation guides",
            card.contains("content.factorGuides.forEach")
        )
    }

    @Test
    fun everyBasisIsRenderedAndNotBehindADisclosureOnEitherSurface() {
        listOf(
            "the terpene detail page" to processingCard(),
            "the synergy card" to codeOfIn(
                "src/main/java/com/trichome/app/ui/screens/entourage",
                "EntourageCommon.kt"
            )
        ).forEach { (where, text) ->
            listOf("AnimatedVisibility", "expandVertically", "Ver más", "ver más").forEach { affordance ->
                assertFalse(
                    "the safety and basis lines must not be behind a disclosure on " +
                        "$where ($affordance)",
                    text.contains(affordance)
                )
            }
        }
    }

    @Test
    fun theSynergyCardRendersTheProcessingRoleItself() {
        val text = codeOfIn("src/main/java/com/trichome/app/ui/screens/entourage", "EntourageCommon.kt")

        assertTrue(
            "the card panel needs a branch for the processing role, or the line " +
                "would be dropped by the `when` and a reader would never learn what " +
                "the residue sentence was",
            text.contains("EntourageCardRole.PROCESSING")
        )
        assertTrue(
            "and it still has to iterate every line so it cannot be skipped",
            text.contains("card.linesEs.forEach")
        )
    }

    @Test
    fun theNetworkTabBuildsItsCardsWithTheProcessingIndex() {
        val text = codeOfIn(
            "src/main/java/com/trichome/app/ui/screens/entourage",
            "EntourageNetworkSection.kt"
        )

        assertTrue("the index has to be built once", text.contains("library.processingIndex()"))
        assertEquals(
            "both the selected combination and the library listing have to carry " +
                "the processing lines, or the block shows in one place and not the " +
                "other",
            2,
            Regex("""EntourageCards\.cardFor\([^)]*processingIndex""").findAll(text).count()
        )
    }

    /* ── One scroll owner ───────────────────────────────────────────────── */

    @Test
    fun theProcessingSectionDeclaresNoSecondScroll() {
        val card = processingCard()

        assertFalse(
            "the page's LazyColumn owns the scroll; a nested verticalScroll inside " +
                "it is measured with an infinite maximum height and throws",
            card.contains("verticalScroll")
        )
        assertFalse(
            "and a nested list would be a second scroll owner",
            card.contains("LazyColumn") || card.contains("LazyRow")
        )
    }

    @Test
    fun thePageStillOwnsExactlyOneLazyColumn() {
        assertEquals(
            "a second list would be a second scroll owner",
            1,
            Regex("""LazyColumn\(""").findAll(detailScreen()).count()
        )
    }

    /* ── Colours and surface ────────────────────────────────────────────── */

    @Test
    fun noHardcodedColourReachesTheProcessingBlock() {
        val offenders = Regex("""Color\(0x""").findAll(processingCard()).map { it.value }.toList()

        assertTrue("these hardcode a colour instead of reading the scheme: $offenders", offenders.isEmpty())
    }

    @Test
    fun noSurfaceIsPublishedAnUnspecifiedColourByTheProcessingBlock() {
        // The block declares no `Surface` at all, which is the stronger property:
        // the rule exists because `Surface` does not resolve `Color.Unspecified`.
        assertFalse(
            "the processing block draws with Column and Text, not surfaces",
            processingCard().contains("Surface(")
        )
    }

    @Test
    fun theProcessingBlockHasNoGlassmorphism() {
        listOf("blur(", "BlurredEdgeTreatment", "graphicsLayer", "GlassPanel").forEach { banned ->
            assertFalse(
                "the terpenes screens must stay solid, found $banned",
                processingCard().contains(banned)
            )
        }
    }

    /* ── The copy is the model's, not the composable's ──────────────────── */

    @Test
    fun noSpanishSentenceIsAuthoredInTheComposable() {
        val card = processingCard()

        // The strong form of the rule: the block holds **no** string literal that
        // could be a sentence. A sentence written here is a sentence no JVM test can
        // reach, and on this card it would be a sentence about solvent residue that
        // nothing could hold to the language guard before it shipped.
        val literals = Regex(""""([^"]*)"""").findAll(card).map { it.groupValues[1] }.toList()

        assertTrue(
            "these literals in the composable are copy no test can reach: $literals",
            literals.all { it.isBlank() }
        )
    }

    @Test
    fun everyLabelTheBlockPrintsComesFromTheContentModelOrASharedConstant() {
        val card = processingCard()

        listOf(
            "content.titleEs",
            "content.responseEs",
            "content.notDocumentedEs",
            "content.comparisonEs",
            "content.solventSafetyEs",
            "method.titleEs",
            "method.detailEs",
            "method.basisEs",
            "method.evidenceLabelEs",
            "method.guide.whatEs",
            "method.guide.safetyEs",
            "method.guide.basisEs",
            "guide.titleEs",
            "guide.whatEs",
            "guide.safetyEs",
            "guide.basisEs",
            "factor.titleEs",
            "factor.detailEs",
            "factor.basisEs",
            "factor.evidenceLabelEs",
            "factor.guide.whatEs",
            "factor.guide.basisEs"
        ).forEach { field ->
            assertTrue(
                "$field must reach the screen, or the block is showing the " +
                    "composable's own idea of the content",
                card.contains(field)
            )
        }
    }

    @Test
    fun theBlockHoldsNoTemperatureOfItsOwn() {
        // F4's refusal, asserted on the source rather than only on the copy: the
        // volatility card above legitimately prints a band, and this card must not
        // acquire a number that could be read as a processing setpoint.
        val card = processingCard()

        assertFalse(
            "the processing card must print no temperature: a number here would " +
                "read as an instruction, which is the one thing this phase refuses",
            Regex("""\d+\s*°C""").containsMatchIn(card)
        )
        assertFalse(
            "and it must not reach for the volatility window either",
            card.contains("windowEs") || card.contains("boilingPoint")
        )
    }
}