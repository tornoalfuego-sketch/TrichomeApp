package com.trichome.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps [DECLARED_ROUTES] and the `composable(...)` declarations in step.
 *
 * `NavHostController.navigate` throws `IllegalArgumentException` at runtime for a
 * route nobody declared, so a tile wired to a typo crashes on tap instead of
 * opening the wrong screen. A `composable("...")` literal is a string, so the
 * compiler cannot see the mismatch — only this can.
 *
 * The declarations are read from the source because `NavHost` is a composable
 * and cannot be introspected from a JVM unit test.
 */
class AppNavigationRouteTest {

    private fun sourceFile(): File = listOf(
        File("src/main/java/com/trichome/app/ui/navigation/AppNavigation.kt"),
        File("app/src/main/java/com/trichome/app/ui/navigation/AppNavigation.kt")
    ).firstOrNull { it.isFile } ?: error("could not locate AppNavigation.kt")

    /**
     * Source with block comments removed.
     *
     * The KDoc on `DECLARED_ROUTES` contains a literal `composable("...")`
     * example, and a naive scan reports that as a route named `...`. Only code
     * declares a destination.
     */
    private fun codeOnly(): String =
        sourceFile().readText(Charsets.UTF_8)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")

    private fun declaredRoutesInSource(): Set<String> {
        val pattern = Regex("""composable\(\s*"([^"]+)"""")
        return pattern.findAll(codeOnly()).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun everyDeclaredRouteExistsInTheNavHost() {
        val inSource = declaredRoutesInSource()
        val declared = DECLARED_ROUTES

        val missingFromSource = declared - inSource
        assertTrue(
            "DECLARED_ROUTES lists routes the NavHost does not declare: $missingFromSource",
            missingFromSource.isEmpty()
        )
        val missingFromList = inSource - declared
        assertTrue(
            "the NavHost declares routes that DECLARED_ROUTES omits: $missingFromList",
            missingFromList.isEmpty()
        )
    }

    @Test
    fun declaredRoutesAreUnique() {
        val routes = Regex("""composable\(\s*"([^"]+)"""")
            .findAll(codeOnly()).map { it.groupValues[1] }.toList()
        assertEquals(
            "a route is declared twice; the NavHost would silently shadow one of them",
            routes.size, routes.toSet().size
        )
    }

    @Test
    fun argumentRoutesAreReadWithTheMatchingType() {
        // A route carrying a path argument must be read with getLong or
        // getString, never with the wrong accessor: the wrong one returns null
        // and the screen silently loads the wrong row.
        val text = codeOnly()

        Regex("""composable\(\s*"([^"]*\{[^}]+\}[^"]*)"""")
            .findAll(text)
            .forEach { m ->
                val route = m.groupValues[1]
                val body = text.substring(m.range.last, minOf(text.length, m.range.last + 320))
                val isLong = route.contains("{plantId}") || route.contains("{id}")
                val accessor = when {
                    isLong -> "getLong"
                    else -> "getString"
                }
                assertTrue(
                    "route '$route' declares a path argument but does not read it with $accessor",
                    body.contains(accessor)
                )
            }
    }
}
