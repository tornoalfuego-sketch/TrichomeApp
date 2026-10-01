package com.trichome.app.ui.screens.entourage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the structural rules of the Séquito module, which no behavioural test
 * of the chemistry can reach.
 *
 * ## Why this reads source
 *
 * Two of the module's promises are about shape, and shape is exactly what a JVM
 * test cannot see:
 *
 * 1. **One scroll owner.** A `verticalScroll` nested in an already-scrolling
 *    parent is measured with an infinite maximum height and throws at runtime
 *    with `Vertically scrollable component was measured with an infinity maximum
 *    height constraints`. That crash shipped once, in the breeding theory tab,
 *    and `ScrollOwnershipTest` exists for it. Nothing about the new shape fails
 *    to compile, fails lint, or fails a test of the planner underneath — it
 *    fails when a human taps a tab.
 * 2. **The evidence line is on the card.** [EntouragePresentationTest] proves
 *    the card *model* always carries it. This proves the card *composable*
 *    renders it unconditionally and without a disclosure, which is the part that
 *    could be regressed by someone tidying the `when`.
 *
 * Comments are stripped before every scan, so a KDoc that mentions
 * `verticalScroll` or `LabWeights` is not a hit — the same rule
 * `ScrollOwnershipTest` and `SuperCycleFormTest` follow.
 */
class EntourageScrollOwnershipTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/screens/entourage"),
        File("app/src/main/java/com/trichome/app/ui/screens/entourage")
    ).filter { it.isDirectory }.flatMap { dir ->
        dir.listFiles { f -> f.name.endsWith(".kt") }?.toList() ?: emptyList()
    }

    /** Source with block and line comments removed. */
    private fun codeOf(name: String): String {
        val file = sources.firstOrNull { it.name == name }
            ?: error("$name is not in the entourage package")
        return file.readText(Charsets.UTF_8)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")
    }

    private fun allCode(): String = sources.joinToString("\n") {
        it.readText(Charsets.UTF_8)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")
    }

    /* ── One scroll owner ─────────────────────────────────────────────────── */

    /** The one file allowed to own the module's scroll. */
    private val scrollOwner = "EntourageModuleScreen.kt"

    @Test
    fun noFileInTheModuleIntroducesASecondVerticalScrollOwner() {
        // A section that scrolls inside the module's LazyColumn is the crash.
        // The owner itself is exempt, and how many lists it declares is asserted
        // separately so "exempt" cannot quietly become "any number of them".
        val offenders = sources.map { it.name }
            .filter { it != scrollOwner }
            .filter { name ->
                val code = codeOf(name)
                code.contains("verticalScroll") || code.contains("LazyColumn")
            }

        assertTrue(
            "these would each mount a scrollable inside the module's LazyColumn, " +
                "which is measured with an infinite maximum height and throws: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun theModuleScreenStillOwnsExactlyOneScroll() {
        // The other half of the contract. Removing the list without a replacement
        // would leave the module unable to scroll at all, and it would look like
        // a fix until the content overflowed.
        val text = codeOf(scrollOwner)

        assertEquals(
            "the module must own exactly one lazy list; a second one is a second " +
                "scroll owner and the tabs would have to coordinate them",
            1,
            Regex("""LazyColumn\(""").findAll(text).count()
        )
        assertTrue(
            "the owner must not also nest a plain verticalScroll",
            !text.contains("verticalScroll")
        )
        assertTrue(
            "the list must fill the space it is given",
            text.contains("Modifier.fillMaxSize()")
        )
    }

    @Test
    fun theFourSectionsArePlainColumnsInsideOneListItem() {
        // Each section is emitted as a single `item { }`, so no section can grow
        // its own scroll without the scan above catching it.
        val text = codeOf(scrollOwner)

        listOf(
            "EntourageNetworkSection",
            "EntourageBoosterSection",
            "EntourageLabSection",
            "EntourageQuizSection"
        ).forEach { section ->
            assertTrue(
                "$section is mounted but never called from the module screen",
                text.contains(section)
            )
        }
        assertTrue(
            "the section switch has to be inside a list item",
            Regex("""item\s*\{\s*when \(tab\)""").containsMatchIn(text)
        )
    }

    /* ── The evidence line is on the card ─────────────────────────────────── */

    @Test
    fun theEvidenceLineIsRenderedAndIsNotADisclosure() {
        val text = codeOf("EntourageCommon.kt")

        assertTrue(
            "the card panel must have a branch for the evidence role",
            text.contains("EntourageCardRole.EVIDENCE")
        )
        // A disclosure is exactly the failure this guards: AnimatedVisibility,
        // an expandable, or a "ver más" affordance would put the evidence behind
        // an interaction.
        listOf("AnimatedVisibility", "expandVertically", "Ver más", "ver más").forEach { affordance ->
            assertFalse(
                "the evidence line must not be behind a disclosure ($affordance)",
                text.contains(affordance)
            )
        }
    }

    @Test
    fun theCardIteratesEveryLineSoTheEvidenceOneCannotBeSkipped() {
        val text = codeOf("EntourageCommon.kt")
        assertTrue(
            "the panel must render the card's lines rather than naming them one by one",
            text.contains("card.linesEs.forEach")
        )
    }

    /* ── The Lab never shows its own weights ──────────────────────────────── */

    @Test
    fun theLabScreenCannotReachThePuzzleConstants() {
        val text = codeOf("EntourageLabSection.kt")

        assertFalse(
            "LabWeights are puzzle numbers, not measurements; the UI must show the " +
                "verdict, the axes and the ceilings instead",
            text.contains("LabWeights")
        )
        assertTrue(
            "the axes must come from the domain feedback, not from a local guess",
            text.contains("EntourageLabUi.feedback")
        )
    }

    @Test
    fun theLabDisplaysTheAxesAgainstTheirCeilings() {
        val text = codeOf("EntourageLabSection.kt")
        assertTrue(text.contains("axis.loadPercent"))
        assertTrue(text.contains("axis.ceilingPercent"))
        assertTrue(text.contains("axis.crossed"))
    }

    /* ── Colours go through the theme ─────────────────────────────────────── */

    @Test
    fun noHardcodedColourReachesTheModule() {
        // A literal would be a colour no theme check covers, and the contrast
        // assertions in the theme package would not see it.
        val offenders = Regex("""Color\(0x""").findAll(allCode())
            .map { it.value }
            .toList()

        assertEquals("these hardcode a colour instead of reading the scheme", emptyList<String>(), offenders)
    }

    @Test
    fun noSurfaceIsPublishedAnUnspecifiedColour() {
        // `Color.Unspecified` is Material's "decide for me" sentinel and `Surface`
        // does not decide: it hands whatever it is given straight into
        // `LocalContentColor`, so a panel that omitted it painted its subtree
        // from an undefined colour.
        val offenders = sources.map { it.name }.filter { name ->
            Regex("""Surface\((?:[^)]*\bcolor\s*=\s*Color\.Unspecified)""").containsMatchIn(codeOf(name))
        }

        assertTrue(
            "a Surface must never be given Color.Unspecified: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun theModuleHasNoGlassmorphism() {
        // The module is solid Material 3. A blurred or translucent surface would
        // reintroduce the contrast class of defect the theme package removed.
        listOf("blur(", "BlurredEdgeTreatment", "graphicsLayer", "GlassPanel").forEach { banned ->
            assertFalse(
                "the Séquito module must stay solid, found $banned",
                allCode().contains(banned)
            )
        }
    }

    /* ── The filter goes through the domain layer ─────────────────────────── */

    @Test
    fun theTerpeneDetailActionResolvesTheTerpeneThroughTheDomainLayer() {
        val file = listOf(
            File("src/main/java/com/trichome/app/ui/screens/terpenes/TerpeneDetailScreen.kt"),
            File("app/src/main/java/com/trichome/app/ui/screens/terpenes/TerpeneDetailScreen.kt")
        ).firstOrNull { it.isFile } ?: error("could not locate TerpeneDetailScreen.kt")

        val text = file.readText(Charsets.UTF_8)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

        assertTrue(
            "the join from a catalog id to a module terpene must be EntourageFilters",
            text.contains("EntourageFilters.terpeneForCatalogId")
        )
        assertFalse(
            "a second inline match over the compounds is exactly what the domain " +
                "layer exists to prevent",
            Regex("""when\s*\(\s*entry\.id""").containsMatchIn(text)
        )
        assertTrue(
            "the action must name the module it opens",
            text.contains("Ver sinergias del Efecto")
        )
    }

    @Test
    fun theTerpeneFilterRowOffersAWayOutOfTheFilter() {
        val text = codeOf("EntourageNetworkSection.kt")
        assertTrue(
            "a filter with no clear option is a dead end",
            text.contains("\"Todas\"")
        )
    }

    /* ── The disclaimer comes before the claims ──────────────────────────── */

    @Test
    fun theDisclaimerIsTheFirstThingTheModuleRenders() {
        val text = codeOf(scrollOwner)

        val disclaimer = text.indexOf("EntourageDisclaimerPanel(")
        val tabSwitch = text.indexOf("when (tab)")

        assertTrue("the disclaimer must be rendered at all", disclaimer > 0)
        assertTrue("the tab switch must exist", tabSwitch > 0)
        assertTrue(
            "the module's limits have to be stated before its claims, not in a " +
                "footer below them",
            disclaimer < tabSwitch
        )
    }

    @Test
    fun theDisclaimerPanelIsNotTheLastThingInTheModule() {
        val text = codeOf("EntourageCommon.kt")
        assertTrue(
            "the disclaimer must be a filled panel with a heading, not a caption",
            text.contains("Antes de leer el módulo")
        )
    }

    @Test
    fun theIntegrityNoticeRendersNothingWhenTheParseWasClean() {
        val text = codeOf("EntourageCommon.kt")
        assertTrue(
            "the notice must early-return on null, or it would always show",
            Regex("""if \(notice == null\) return""").containsMatchIn(text)
        )
    }
}
