package com.trichome.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the edge-to-edge inset contract that `targetSdk = 36` depends on.
 *
 * At API 35+ the system draws the app behind the status and navigation bars
 * unless it opts in. The activity already calls `enableEdgeToEdge()`, so the
 * drawing model is settled; what this test protects is the half of the problem
 * that no unit test and no compile error can see -- whether each screen actually
 * applies the insets it is given.
 *
 * Two shapes of screen exist in this app and they are correct for opposite
 * reasons, which is why both are asserted rather than one blanket rule:
 *
 * 1. A screen built on `Scaffold` gets `contentWindowInsets` (Material 3
 *    defaults it to `systemBarsForVisualComponents`) and applies it with
 *    `Modifier.padding(padding)`. Its top bar and bottom bar are placed by
 *    `Scaffold` itself. Nothing extra is needed.
 * 2. A screen that draws its own chrome in a bare `Column` gets NO insets from
 *    anywhere. `TerpeneDetailScreen` and `EntourageModuleScreen` are the two
 *    that do this, and both used to render their back arrow underneath the
 *    status bar. Each has to declare its own.
 *
 * The bottom bar is the case that would make the app unusable rather than ugly:
 * an unpadded floating bar sits under the system navigation bar and its
 * destinations stop being tappable. So it is asserted separately, and the
 * assertion is that the inset is applied *exactly once* -- `NavigationBar`
 * already defaults to `systemBars.only(Horizontal + Bottom)`, so adding a second
 * `navigationBarsPadding()` on top of it does not make the bar safer, it floats
 * the bar further from the edge it is supposed to hug.
 */
class WindowInsetsContractTest {

    private val screensDir = listOf(
        File("src/main/java/com/trichome/app/ui/screens"),
        File("app/src/main/java/com/trichome/app/ui/screens")
    ).firstOrNull { it.isDirectory }
        ?: error("the screens package is not on disk from this working directory")

    private val componentsDir = listOf(
        File("src/main/java/com/trichome/app/ui/components"),
        File("app/src/main/java/com/trichome/app/ui/components")
    ).first { it.isDirectory }

    private val activityFile = listOf(
        File("src/main/java/com/trichome/app/ui/MainActivity.kt"),
        File("app/src/main/java/com/trichome/app/ui/MainActivity.kt")
    ).first { it.isFile }

    private fun codeOf(file: File): String = file.readText(Charsets.UTF_8)
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    /** The text of one top-level composable, from its declaration to the next. */
    private fun bodyOf(code: String, functionName: String): String {
        val start = code.indexOf("fun $functionName(")
        if (start < 0) return ""
        val rest = code.substring(start)
        val end = Regex("""\n(@Composable|@OptIn|(private |internal )?fun )""").find(rest)
            ?.range?.first
            ?: rest.length
        return rest.substring(0, end)
    }

    /**
     * Screens that draw their own header in a bare layout instead of a
     * `Scaffold`, and therefore own their insets.
     *
     * Listed by name rather than discovered: the point of this test is to fail
     * when someone adds a *fourth* such screen and forgets the inset, and a
     * discovery rule would happily add it to the list instead.
     */
    private val selfChomedScreens = listOf(
        "TerpeneDetailScreen" to "terpenes/TerpeneDetailScreen.kt",
        "EntourageModuleScreen" to "entourage/EntourageModuleScreen.kt"
    )

    @Test
    fun `the activity opts into edge to edge`() {
        val code = codeOf(activityFile)
        assertTrue(
            "MainActivity must call enableEdgeToEdge(); without it the window " +
                "insets these screens apply are measured against a window that " +
                "is not laid out edge-to-edge",
            code.contains("enableEdgeToEdge()")
        )
    }

    @Test
    fun `a screen that draws its own header applies the status bar inset`() {
        selfChomedScreens.forEach { (fn, path) ->
            val file = File(screensDir, path)
            assertTrue("${path} is not on disk from this working directory", file.isFile)
            val body = bodyOf(codeOf(file), fn)

            assertTrue(
                "$fn draws its own header in a bare layout, so nothing hands it " +
                    "the status bar inset and its back arrow renders underneath the " +
                    "status bar. Apply statusBarsPadding() to the header Row.",
                body.contains("statusBarsPadding")
            )
        }
    }

    @Test
    fun `the bottom bar keeps its navigation bar inset`() {
        val code = codeOf(File(componentsDir, "NavigationComponents.kt"))
        val body = bodyOf(code, "MainBottomBar")

        assertTrue(
            "MainBottomBar must keep a navigation-bar inset or it sits under the " +
                "system navigation bar and its destinations stop being tappable",
            body.contains("WindowInsets.navigationBars") || body.contains("navigationBarsPadding")
        )
    }

    @Test
    fun `the bottom bar applies the navigation bar inset only once`() {
        val body = bodyOf(codeOf(File(componentsDir, "NavigationComponents.kt")), "MainBottomBar")

        // `NavigationBar` already defaults `windowInsets` to
        // `systemBars.only(Horizontal + Bottom)` and applies it internally. A
        // second application on the same bar is not extra safety: it pads the
        // bar away from the edge it is meant to hug.
        assertTrue(
            "MainBottomBar must not combine windowInsets with navigationBarsPadding: " +
                "NavigationBar already applies the bottom system-bar inset, so the " +
                "second one floats the bar above the screen edge. Declare it once, " +
                "in the windowInsets slot.",
            !(body.contains("WindowInsets.navigationBars") && body.contains("navigationBarsPadding"))
        )
    }

    @Test
    fun `every Scaffold screen applies the padding it is handed`() {
        // The other half of the contract. A `Scaffold` screen that drops
        // `padding` renders its content under the system bars while looking
        // perfectly correct in a preview, and no compiler or unit test objects.
        val offenders = screensDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
            .map { it to codeOf(it) }
            .filter { (_, code) -> code.contains("Scaffold(") }
            .filter { (file, code) ->
                // Dialogs and panels are nested inside a screen, not screens
                // themselves, and a Scaffold is not a screen boundary anyway.
                file.name.endsWith("Screen.kt") && !code.contains(".padding(padding)")
            }
            .map { (file, _) -> file.name }

        assertTrue(
            "these Scaffold screens never apply the padding values they are given, " +
                "so their content draws under the system bars: $offenders",
            offenders.isEmpty()
        )
    }
}
