package com.trichome.app.ui.screens.terpenes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F3 — the structural rules of the two surfaces the agronomy block reaches.
 *
 * ## Why this reads source
 *
 * The failure modes it guards are not behavioural and no JVM test can reach them:
 *
 * 1. **The basis line is on the screen.** [TerpeneAgronomyTest] proves the card
 *    *model* always carries each lever's `basisEs` and its evidence label. This
 *    proves both composables *render* them, with no `if`, no `AnimatedVisibility`
 *    and no "ver más". The user asked for this module's standard explicitly: the
 *    evidence line has to be visible, not behind a disclosure.
 * 2. **The gap is stated.** A compound with no documented lever must render a
 *    sentence, not an empty card. That is `TerpeneAgronomyCopy.NOT_DOCUMENTED_ES`
 *    reaching the screen, and it is the one case where the block has something to
 *    show while the lever list is empty.
 * 3. **One scroll owner per axis.** A nested scroll inside a `LazyColumn` throws
 *    at runtime with an infinite maximum height. That crash shipped once, which is
 *    why `ScrollOwnershipTest` exists.
 *
 * Comments are stripped before every scan, so a KDoc that mentions
 * `verticalScroll`, `AnimatedVisibility` or `Color.Unspecified` is not a hit —
 * the same rule the other source-scanning tests follow, and the one that has
 * broken twice.
 */
class TerpeneDetailAgronomyTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/screens/terpenes"),
        File("app/src/main/java/com/trichome/app/ui/screens/terpenes")
    ).filter { it.isDirectory }

    private val entourageSources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/screens/entourage"),
        File("app/src/main/java/com/trichome/app/ui/screens/entourage")
    ).filter { it.isDirectory }

    /** Source with block and line comments removed. */
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
     * this. This is the fix pattern `DataRowLayoutTest` needed.
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

    private fun agronomyCard(): String = bodyOf(detailScreen(), "AgronomyCard")

    /* ── It sits beside the vapourisation card ────────────────────────────── */

    @Test
    fun theAgronomyBlockIsOnThePageBesideVaporisationAndNotInsteadOfIt() {
        val text = detailScreen()

        assertTrue("the volatility card has to still be there", text.contains("VolatilityCard("))
        assertTrue("the agronomy card has to be there", text.contains("AgronomyCard("))
        assertTrue(
            "the block comes from the ViewModel, not from a local lookup",
            text.contains("vm.agronomyFor(")
        )
        assertTrue(
            "and it is mounted beside the volatility card, not instead of it",
            text.indexOf("VolatilityCard(") < text.indexOf("AgronomyCard(")
        )
    }

    @Test
    fun theBlockIsGatedOnTheCompoundAndNotOnWhetherItHasAnEntry() {
        // The gate is "is this a compound the Séquito module models", the same one
        // the page's Séquito button uses — not "does it have agronomy". Gating on
        // the entry would make the honest no-entry sentence unreachable.
        val body = bodyOf(detailScreen(), "TerpeneDetailScreen")

        assertTrue("the null check has to be on the resolved block", body.contains("if (agronomyBlock != null)"))
    }

    /* ── The basis line is rendered, and is not a disclosure ──────────────── */

    @Test
    fun everyLeversBasisIsRenderedUnconditionally() {
        val body = agronomyCard()

        assertTrue(
            "the basis sentence is the whole honesty guarantee of a lever",
            body.contains("lever.basisEs")
        )
        assertTrue(
            "and the level that sentence belongs to has to be rendered too",
            body.contains("lever.evidenceLabelEs")
        )
        assertFalse(
            "a conditional here would put the evidence behind a decision nobody " +
                "makes on purpose",
            Regex("""if\s*\(\s*lever\.""").containsMatchIn(body)
        )
    }

    @Test
    fun theGapForACompoundWithNoEntryIsStatedRatherThanLeftBlank() {
        val body = agronomyCard()

        assertTrue(
            "a compound with no documented lever has to print the sentence that " +
                "says so",
            body.contains("content.notDocumentedEs")
        )
        assertTrue(
            "and the documented branch has to print the response instead",
            body.contains("content.responseEs")
        )
    }

    @Test
    fun theBiosynthesisCaveatIsRenderedUnconditionally() {
        val body = agronomyCard()

        assertTrue(
            "the route explainer has to say what it cannot settle, in the same " +
                "block as the steps",
            body.contains("biosynthesis.caveatEs")
        )
        assertFalse(
            "wrapping it in a condition would leave an explainer that reads as " +
                "settled",
            Regex("""if\s*\(\s*[^)]*caveatEs""").containsMatchIn(body)
        )
    }

    @Test
    fun theBasisIsNotBehindADisclosureOnEitherSurface() {
        listOf(
            "the terpene detail page" to agronomyCard(),
            "the synergy card" to codeOfIn(
                "src/main/java/com/trichome/app/ui/screens/entourage",
                "EntourageCommon.kt"
            )
        ).forEach { (where, text) ->
            listOf("AnimatedVisibility", "expandVertically", "Ver más", "ver más").forEach { affordance ->
                assertFalse(
                    "the basis must not be behind a disclosure on $where ($affordance)",
                    text.contains(affordance)
                )
            }
        }
    }

    @Test
    fun theSynergyCardRendersTheAgronomyRoleItself() {
        val text = codeOfIn("src/main/java/com/trichome/app/ui/screens/entourage", "EntourageCommon.kt")

        assertTrue(
            "the card panel needs a branch for the agronomy role, or the line " +
                "would be dropped by the `when`",
            text.contains("EntourageCardRole.AGRONOMY")
        )
        assertTrue(
            "and it still has to iterate every line so it cannot be skipped",
            text.contains("card.linesEs.forEach")
        )
    }

    @Test
    fun theNetworkTabBuildsItsCardsWithTheAgronomyIndex() {
        val text = codeOfIn(
            "src/main/java/com/trichome/app/ui/screens/entourage",
            "EntourageNetworkSection.kt"
        )

        assertTrue("the index has to be built once", text.contains("library.agronomyIndex()"))
        assertEquals(
            "both the selected combination and the library have to carry the " +
                "agronomy lines, or the block shows in one place and not the other",
            2,
            Regex("""EntourageCards\.cardFor\([^)]*agronomyIndex""").findAll(text).count()
        )
    }

    /* ── One scroll owner ─────────────────────────────────────────────────── */

    @Test
    fun theAgronomySectionDeclaresNoSecondScroll() {
        val body = agronomyCard()

        assertFalse(
            "the page's LazyColumn owns the scroll; a nested verticalScroll inside " +
                "it is measured with an infinite maximum height and throws",
            body.contains("verticalScroll")
        )
        assertFalse(
            "and a nested list would be a second scroll owner",
            body.contains("LazyColumn")
        )
    }

    @Test
    fun thePageStillOwnsExactlyOneLazyColumn() {
        val text = detailScreen()

        assertEquals(
            "a second list would be a second scroll owner",
            1,
            Regex("""LazyColumn\(""").findAll(text).count()
        )
    }

    /* ── Colours and surface ──────────────────────────────────────────────── */

    @Test
    fun noHardcodedColourReachesTheAgronomyBlock() {
        val offenders = Regex("""Color\(0x""").findAll(agronomyCard()).map { it.value }.toList()

        assertTrue("these hardcode a colour instead of reading the scheme: $offenders", offenders.isEmpty())
    }

    @Test
    fun noSurfaceIsPublishedAnUnspecifiedColourByTheAgronomyBlock() {
        // The block declares no `Surface` at all, which is the stronger property:
        // the rule exists because `Surface` does not resolve `Color.Unspecified`.
        assertFalse(
            "the agronomy block draws with Column and Text, not surfaces",
            agronomyCard().contains("Surface(")
        )
    }

    @Test
    fun theAgronomyBlockHasNoGlassmorphism() {
        listOf("blur(", "BlurredEdgeTreatment", "graphicsLayer", "GlassPanel").forEach { banned ->
            assertFalse(
                "the terpenes screens must stay solid, found $banned",
                agronomyCard().contains(banned)
            )
        }
    }

    /* ── The copy is the model's, not the composable's ────────────────────── */

    @Test
    fun noSpanishSentenceIsAuthoredInTheComposable() {
        val body = agronomyCard()

        // The strong form of the rule: the block holds **no** string literal that
        // could be a sentence. A sentence written here is a sentence no JVM test
        // can reach — the rule the `weight(0f)` crash turned into a convention,
        // and the reason every label above a basis line is a constant in
        // `TerpeneAgronomyCopy`.
        val literals = Regex(""""([^"]*)"""").findAll(body).map { it.groupValues[1] }.toList()

        assertTrue(
            "these literals in the composable are copy no test can reach: $literals",
            literals.all { it.isBlank() }
        )
    }

    @Test
    fun everyBasisLabelComesFromOneConstant() {
        val body = agronomyCard()

        assertTrue(
            "a lever's basis label has to be the shared one, or the card and the " +
                "page name the same thing differently",
            body.contains("TerpeneAgronomyCopy.basisLabelEs(")
        )
        assertEquals(
            "two call sites: the compound's levers and the shared guides",
            2,
            Regex("""TerpeneAgronomyCopy\.basisLabelEs\(""").findAll(body).count()
        )
    }

    @Test
    fun everyLabelTheBlockPrintsComesFromTheContentModel() {
        val body = agronomyCard()

        listOf(
            "content.titleEs",
            "content.responseEs",
            "content.notDocumentedEs",
            "content.routeEs",
            "lever.titleEs",
            "lever.detailEs",
            "lever.basisEs",
            "biosynthesis.titleEs",
            "biosynthesis.introEs",
            "biosynthesis.sizeRuleEs",
            "biosynthesis.trichomeEs",
            "biosynthesis.caveatEs",
            "guide.titleEs",
            "guide.whatEs",
            "guide.basisEs"
        ).forEach { field ->
            assertTrue(
                "$field must reach the screen, or the block is showing the " +
                    "composable's own idea of the content",
                body.contains(field)
            )
        }
    }

    @Test
    fun theCommittedGuideListIsTheWholeSharedGuidance() {
        val body = agronomyCard()

        assertTrue(
            "all three guides are rendered, including the levers this compound " +
                "does not respond to: dropping them silently would hide the fact " +
                "that the mechanism is shared",
            body.contains("GrowOutGuides.all")
        )
    }
}