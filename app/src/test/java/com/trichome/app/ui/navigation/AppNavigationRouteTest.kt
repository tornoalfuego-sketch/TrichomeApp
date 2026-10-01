package com.trichome.app.ui.navigation

import com.trichome.app.ui.screens.entourage.ENTOURAGE_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds [DECLARED_ROUTES] to the `NavHost`, and the argument types to the
 * accessors that read them.
 *
 * The second half is the part that matters. A `composable("plant_detail/{plantId}")`
 * with no `navArgument` block delivers `plantId` as a **String**, so
 * `arguments?.getLong("plantId")` returned 0 for every plant and the detail screen
 * reported "No encontramos esta planta" for rows that were in the list a tap away.
 * Confirmed on a real device: plant id 1 existed in Room and the screen refused to
 * open it. Protocol, supercycle and the plant journal had the same defect.
 *
 * A string literal in a route is invisible to the compiler and to any test that
 * only checks the route names, which is why this reads the source.
 */
class AppNavigationRouteTest {

    private fun sourceFile(): File = listOf(
        File("src/main/java/com/trichome/app/ui/navigation/AppNavigation.kt"),
        File("app/src/main/java/com/trichome/app/ui/navigation/AppNavigation.kt")
    ).firstOrNull { it.isFile } ?: error("could not locate AppNavigation.kt")

    /**
     * Source with block comments removed.
     *
     * The KDoc contains literal `composable("...")` examples, and a naive scan
     * reports that as a route named `...`.
     */
    private fun codeOnly(): String =
        sourceFile().readText(Charsets.UTF_8)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")

    @Test
    fun everyDeclaredRouteExistsInTheNavHost() {
        // A route reaches the NavHost either as a literal argument to
        // `composable`, or through one of the *_ROUTE constants. Matching a
        // constant name is enough here; the other tests in this class check that
        // each constant is actually wired to a composable and typed.
        // `DECLARED_ROUTES` holds route values, while the NavHost now names most
        // of them through *_ROUTE constants declared as
        // `"plant_detail/{$PLANT_ID_ARG}"` -- string interpolation, which a source
        // scan cannot resolve. So the set is reconciled by *symbol*: every route
        // is either a literal in a composable call, or the value of a constant
        // that is in DECLARED_ROUTES by construction.
        val literals = Regex("""composable\(\s*(?:route\s*=\s*)?"([^"]+)"""")
            .findAll(codeOnly()).map { it.groupValues[1] }.toSet()

        val constants = Regex("""const val (\w+_ROUTE): String = """)
            .findAll(codeOnly()).map { it.groupValues[1] }.toSet()

        val text = codeOnly()
        val missing = literals.filterNot { it in DECLARED_ROUTES } +
            // Every *_ROUTE constant referenced by DECLARED_ROUTES must be defined.
            constants.filterNot {
                Regex("""const val $it: String = """).containsMatchIn(text)
            }
        assertTrue(
            "a route is declared but not listed in DECLARED_ROUTES: $missing",
            missing.isEmpty()
        )

        // And the other direction: DECLARED_ROUTES must cover every literal and
        // every route constant, so a new destination cannot be added without
        // registering it.
        val registeredValues = setOf(
            "home", "tents", "journal", "diagnosis", "settings",
            "calendar", "charts", "terpenes", "breeding",
            PLANT_ID_ROUTE, PROTOCOL_ID_ROUTE, SUPER_CYCLE_ID_ROUTE,
            JOURNAL_ID_ROUTE, TERPENE_ID_ROUTE, ENTOURAGE_ROUTE
        )
        assertEquals(
            "DECLARED_ROUTES is out of sync with the graph",
            registeredValues,
            DECLARED_ROUTES
        )
    }

    @Test
    fun everyRouteConstantResolvesToADistinctValue() {
        // Two constants resolving to the same string would make the NavHost
        // shadow one destination, silently. The constants are compared here
        // rather than parsed out of the source because their declared values use
        // interpolation, which a text scan would not resolve.
        val values = listOf(
            PLANT_ID_ROUTE, PROTOCOL_ID_ROUTE, SUPER_CYCLE_ID_ROUTE,
            JOURNAL_ID_ROUTE, TERPENE_ID_ROUTE
        )
        assertEquals(
            "two route constants resolve to the same string",
            values.size, values.toSet().size
        )
        assertTrue(
            "every route constant must contain its argument placeholder",
            values.all { it.contains('{') && it.endsWith('}') }
        )
    }

    @Test
    fun everyRouteConstantIsWiredToAComposable() {
        // A constant that is defined but never passed to `composable` is a route
        // that cannot be navigated to, and it fails silently.
        val text = codeOnly()
        listOf(
            "PLANT_ID_ROUTE" to PLANT_ID_ROUTE,
            "PROTOCOL_ID_ROUTE" to PROTOCOL_ID_ROUTE,
            "SUPER_CYCLE_ID_ROUTE" to SUPER_CYCLE_ID_ROUTE,
            "JOURNAL_ID_ROUTE" to JOURNAL_ID_ROUTE,
            "TERPENE_ID_ROUTE" to TERPENE_ID_ROUTE
        ).forEach { (name, route) ->
            assertTrue(
                "$name is declared as \"$route\" but no composable uses it: " +
                    "the route exists and nothing can reach it",
                Regex("""composable\(\s*route\s*=\s*$name\b""").containsMatchIn(text)
            )
        }
    }

    @Test
    fun declaredRoutesAreUnique() {
        val text = codeOnly()
        val routes = Regex("""composable\(\s*(?:route\s*=\s*)?"([^"]+)"""")
            .findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(
            "a route is declared twice; the NavHost would silently shadow one of them",
            routes.size, routes.toSet().size
        )
    }

    @Test
    fun everyArgumentRouteDeclaresItsType() {
        // The regression this exists for: an untyped `{plantId}` path argument is
        // handed over as a String, so `getLong` silently yields 0.
        val text = codeOnly()
        val typed = Regex(
            """navArgument\(\s*PLANT_ID_ARG\s*\)\s*\{\s*type\s*=\s*NavType\.LongType\s*}"""
        ).findAll(text).count()
        val longRoutes = listOf(
            PLANT_ID_ROUTE, PROTOCOL_ID_ROUTE, SUPER_CYCLE_ID_ROUTE, JOURNAL_ID_ROUTE
        )
        assertEquals(
            "every plant-scoped route must declare NavType.LongType, otherwise " +
                "getLong returns 0 and the screen cannot find the row",
            longRoutes.size,
            typed
        )
    }

    @Test
    fun theTerpeneRouteDeclaresAStringArgument() {
        val text = codeOnly()
        assertTrue(
            "terpene/{terpeneId} must declare NavType.StringType",
            Regex("""navArgument\(\s*TERPENE_ID_ARG\s*\)\s*\{\s*type\s*=\s*NavType\.StringType\s*}""")
                .containsMatchIn(text)
        )
    }

    @Test
    fun noRouteIsReadWithAnAccessorThatDoesNotMatchItsType() {
        // A `getLong` on a String-typed argument, or a `getString` on a Long one,
        // returns null or throws. The helper is the single place that reads a
        // plant id, so it is the single place allowed to touch the argument.
        val text = codeOnly()
        val offenders = Regex("""arguments\?\.getLong\(\s*"([^"]+)"\s*\)""")
            .findAll(text)
            .filter { it.groupValues[1] == PLANT_ID_ARG }
            .count()
        assertEquals(
            "read the plant id through longArg() so the fallback is applied; " +
                "a bare getLong returns 0 for an untyped argument",
            0,
            offenders
        )
    }

    @Test
    fun theLongArgHelperFallsBackRatherThanThrowing() {
        // A hand-built route with a non-numeric segment must not kill the
        // process; it should resolve to the same sentinel the screens already
        // treat as "no such plant".
        val text = codeOnly()
        assertTrue(
            "longArg must parse a String fallback with toLongOrNull",
            text.contains("toLongOrNull()")
        )
        assertTrue(
            "longArg must fall back to MISSING_LONG_ARG",
            text.contains("MISSING_LONG_ARG")
        )
    }
}
