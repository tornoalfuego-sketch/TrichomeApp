package com.trichome.app.ui.screens.home

import com.trichome.app.ui.navigation.DECLARED_ROUTES
import com.trichome.app.ui.screens.entourage.ENTOURAGE_ROUTE
import com.trichome.app.ui.screens.entourage.entourageRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract for the two things Home was missing.
 *
 * ## The gap
 *
 * The Entourage module shipped with a full screen, a planner, a lab and a quiz,
 * and was reachable only from one card on the Terpenes screen -- two screens in.
 * The complaint, "deben estar visibles y utilizables", is a navigation-distance
 * complaint, and the fix is one row on the quick-action list rather than a new
 * feature.
 *
 * The lunar engine had the same shape of problem from the other side: it was
 * correct, expanded into a full advice panel, and entirely absent from the screen
 * the app opens on. "No se ve implementado" describes what is reachable, not what
 * is implemented.
 *
 * ## What is pinned
 *
 * The route, because a hard-coded string that is not in [DECLARED_ROUTES] throws
 * `IllegalArgumentException` at the tap and nothing in the build notices. The
 * Spanish label, because the module names itself "Efecto Séquito" and a row
 * calling it anything else is a second name for one feature.
 */
class HomeQuickActionsTest {

    private val homeSource: String = stripComments(
        listOf(
            File("src/main/java/com/trichome/app/ui/screens/home"),
            File("app/src/main/java/com/trichome/app/ui/screens/home")
        )
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.name == "HomeScreen.kt" }?.toList() ?: emptyList() }
            .firstOrNull()
            ?.readText(Charsets.UTF_8)
            ?: error("HomeScreen.kt is not in the home package")
    )

    @Test
    fun theEntourageRouteIsOneTheNavHostActuallyDeclares() {
        val bare = entourageRoute()
        assertTrue(
            "entourage is registered as \"$bare\", which the NavHost does not declare",
            bare in DECLARED_ROUTES
        )
    }

    @Test
    fun aQuickActionOpensTheBareModuleAndNotAPreFilteredSection() {
        // No arguments, so the module opens on its own default tab. Passing `tab` or
        // `terpene` here would be a decision the quick-action row has no basis for.
        assertEquals(ENTOURAGE_ROUTE, entourageRoute())
    }

    @Test
    fun homeOffersTheEntourageModuleAsAFifthQuickAction() {
        assertTrue(
            "the quick-action list must reach the Séquito module, or the feature is " +
                "still two screens deep",
            homeSource.contains("entourageRoute()")
        )
    }

    @Test
    fun theEntourageRowIsLabelledTheWayTheModuleNamesItself() {
        // The module's own header is "🧬 Efecto Séquito". A row calling it
        // anything else gives one feature two names.
        assertTrue(
            "the row must use the module's own name, \"Efecto Séquito\"",
            homeSource.contains("Efecto Séquito")
        )
    }

    @Test
    fun theEntourageRowReusesTheExistingActionRowRatherThanASecondOne() {
        // A second row composable would be a second set of colour decisions, and
        // `AccentRoleSeparationTest` only guards the one that already exists.
        // Whitespace-insensitive: a reformat must not decide whether the module
        // is still reachable through the shared row.
        assertTrue(
            "the module must be reached through the existing ActionRow",
            Regex("""ActionRow\(\s*navController\s*=\s*navController,\s*route\s*=\s*entourageRoute\(\)""")
                .containsMatchIn(homeSource)
        )
    }

    @Test
    fun homeShowsTheCurrentLunarPhaseWithoutNeedingTheExpandTap() {
        // The phase alone, on the collapsed bar, is what makes the engine's
        // existence evident. The advice panel behind the disclosure stays in the
        // calendar; duplicating it on Home would be two sources of truth.
        assertTrue(
            "home must render the current lunar phase",
            homeSource.contains("LunarGlanceRow")
        )
        assertTrue(
            "it must come from the engine, not from a hard-coded phase",
            homeSource.contains("LunarEngine.snapshot")
        )
    }

    @Test
    fun theLunarRowLinksToTheCalendarWhereTheFullPanelLives() {
        assertTrue(
            "the glance row must lead somewhere, not sit as a dead label",
            Regex("""LunarGlanceRow\(\s*content = lunarGlance,\s*onClick = \{ navController\.navigate\("calendar"\) \}""")
                .containsMatchIn(homeSource)
        )
    }

    @Test
    fun theLunarSnapshotIsReadFromTheDayAndNotFromEveryFrame() {
        // A per-frame read recomposes the whole home screen sixty times a second
        // for a value that moves on the order of hours.
        assertTrue(
            "the snapshot must be remembered, not recomputed on every recomposition",
            homeSource.contains("remember(today)")
        )
    }

    @Test
    fun homeDoesNotDuplicateTheEstimatedClimateCard() {
        // An estimate shown twice reads as two readings, and the card's whole
        // value is that it is visibly an estimate. One place, honestly labelled.
        assertTrue(
            "the climate estimate stays in the calendar only; it must not be " +
                "duplicated onto Home where it would read as a sensor reading",
            !homeSource.contains("EstimatedClimateCard")
        )
    }

    @Test
    fun theLunarRowTakesItsGlyphsFromTheTextRolesAndNotFromTheAccent() {
        // The same rule `AccentRoleSeparationTest` enforces for `ActionRow`:
        // a row that follows the accent repaints every time the user changes it.
        val start = homeSource.indexOf("private fun LunarGlanceRow(")
        assertTrue("LunarGlanceRow is not in HomeScreen.kt", start > 0)
        val body = homeSource.substring(start, minOf(start + 1600, homeSource.length))

        assertTrue(
            "LunarGlanceRow must resolve one furniture colour for its glyphs",
            Regex("""val furniture = LocalTertiaryText\.current""").containsMatchIn(body)
        )
        assertTrue(
            "LunarGlanceRow must not resolve a glyph from the accent",
            !body.contains("accentLabelOn")
        )
    }

    @Test
    fun theLunarRowIsNotASecondScrollOwner() {
        // `ScrollOwnershipTest` exists because a nested vertical scroll measured
        // with an infinite maximum height killed the process. Home's column already
        // owns the axis.
        val start = homeSource.indexOf("private fun LunarGlanceRow(")
        val body = homeSource.substring(start, minOf(start + 1600, homeSource.length))

        assertTrue(
            "LunarGlanceRow is mounted inside Home's own verticalScroll and must " +
                "not scroll again",
            !body.contains("verticalScroll")
        )
    }

    /** Block and line comments removed, so this file's own prose cannot satisfy an
     *  assertion or register as a call site. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")
}
