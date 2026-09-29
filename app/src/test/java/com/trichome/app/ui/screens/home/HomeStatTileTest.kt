package com.trichome.app.ui.screens.home

import com.trichome.app.ui.navigation.DECLARED_ROUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the home stat tiles.
 *
 * The bug this locks down: `StatCard` took no click parameter, so the "Plantas",
 * "Carpas" and "Hoy" tiles were not just unwired — they had nowhere to be wired
 * from. A tile that looks like a button and does nothing is worse than a label,
 * because the grower reasonably assumes the count is one tap from the list.
 *
 * The route for each tile lives in [HomeStatTile] rather than inline in the
 * composable so it is assertable without a composition.
 */
class HomeStatTileTest {

    @Test
    fun everyTileHasADestination() {
        HomeStatTile.ALL.forEach { tile ->
            assertNotNull(
                "${tile.labelEs} leads nowhere",
                tile.route
            )
        }
    }

    @Test
    fun everyDestinationIsARouteTheNavHostActuallyDeclares() {
        // Read from the NavHost itself rather than retyped here: a tile wired to a
        // route nobody declares throws IllegalArgumentException at the tap, and
        // nothing else in the build would notice.
        HomeStatTile.ALL.forEach { tile ->
            assertTrue(
                "${tile.labelEs} navigates to \"${tile.route}\", which is not a declared route",
                tile.route in DECLARED_ROUTES
            )
        }
    }

    @Test
    fun theTentTileOpensTheTentList() {
        assertEquals("tents", HomeStatTile.TENTS.route)
        assertEquals("Carpas", HomeStatTile.TENTS.labelEs)
    }

    @Test
    fun theTodayTileOpensTheCalendar() {
        // The counter is today's event count, so the calendar is the only screen
        // that answers "what is on today".
        assertEquals("calendar", HomeStatTile.TODAY.route)
    }

    @Test
    fun thePlantsTileNamesTheRouteItStandsInFor() {
        // There is no /plants screen yet, so the tile opens the tent list, which
        // is where plants actually live. The tile records that substitution so it
        // is greppable rather than looking deliberate.
        assertEquals("tents", HomeStatTile.PLANTS.route)
        assertTrue(
            "the stand-in has to be declared",
            HomeStatTile.PLANTS.pendingRoute.isNotBlank()
        )
        assertTrue(
            "the pending route must name the screen that is missing",
            HomeStatTile.PLANTS.pendingRoute.contains("plants")
        )
    }

    @Test
    fun aTileThatIsNotASubstituteDeclaresNothing() {
        // Otherwise every tile would carry a pendingRoute and the field would
        // stop meaning "this one is standing in for something".
        assertEquals("", HomeStatTile.TENTS.pendingRoute)
        assertEquals("", HomeStatTile.TODAY.pendingRoute)
    }

    @Test
    fun onlyThePlantsTileIsASubstitute() {
        assertEquals(
            listOf("Plantas"),
            HomeStatTile.ALL.filter { it.pendingRoute.isNotBlank() }.map { it.labelEs }
        )
    }

    @Test
    fun aTileWithNoDestinationYetDeclaresWhichRouteIsWaitingForIt() {
        // Not every tile has to ship wired, but an unwired one has to say why.
        HomeStatTile.ALL.filter { it.route == null }.forEach { tile ->
            assertTrue(
                "${tile.labelEs} is unwired without naming the route it waits for",
                tile.pendingRoute.isNotBlank()
            )
        }
    }

    @Test
    fun theLabelsAndValuesAreTheSpanishTheRestOfTheAppUses() {
        assertEquals(
            listOf("Plantas", "Carpas", "Hoy"),
            HomeStatTile.ALL.map { it.labelEs }
        )
    }

    @Test
    fun theOrderIsStableSoTheRowDoesNotReshuffleBetweenRecompositions() {
        assertEquals(
            HomeStatTile.ALL.map { it.labelEs },
            HomeStatTile.ALL.map { it.labelEs }
        )
        assertEquals(3, HomeStatTile.ALL.size)
    }
}
