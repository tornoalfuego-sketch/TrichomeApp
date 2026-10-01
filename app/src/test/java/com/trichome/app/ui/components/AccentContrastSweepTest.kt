package com.trichome.app.ui.components

import androidx.compose.ui.graphics.Color
import com.trichome.app.ui.screens.settings.AccentSwatches
import com.trichome.app.ui.theme.AccentPalette
import com.trichome.app.ui.theme.AppTheme
import com.trichome.app.ui.theme.MINIMUM_TEXT_CONTRAST
import com.trichome.app.ui.theme.contrastRatio
import com.trichome.app.ui.theme.readableOnStrict
import com.trichome.app.ui.theme.solidSchemeFor
import com.trichome.app.ui.theme.toArgbInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sweeps **every accent the app offers** and proves the buttons are legible.
 *
 * The defect this locks down: eleven call sites wrote
 * `ButtonDefaults.buttonColors(containerColor = accent)`, where `accent` is a
 * colour the *user* picks from eight swatches. That reads correctly and
 * compiles, but the label colour it leaves behind is Material's default, which
 * knows nothing about that accent. The shipped green hid it; the amber swatch
 * would have shipped a white label on a yellow button at 1.7:1.
 *
 * Three layers, because each one can fail on its own:
 *
 * 1. the pure resolution ([accentContentOn] / [accentLabelOn]) clears WCAG AA for
 *    every accent;
 * 2. the scheme publishes it, so `onPrimary` really is that value;
 * 3. the call sites actually route through the helpers — a colour function can
 *    be perfect while the code keeps hand-rolling its own.
 */
class AccentContrastSweepTest {

    /** Every accent the palette exposes. */
    private val paletteAccents: List<Color> = AccentPalette.SELECTABLE_ARGB.map { Color(it) }

    /** Every accent the Settings screen offers, read from the UI's own list. */
    private val swatchAccents: List<Color> = AccentSwatches.map { it.second }

    /** The union, so an accent that reached the UI by any route is covered. */
    private val everyAccent: List<Color> = (paletteAccents + swatchAccents).distinct()

    private fun label(accent: Color): String =
        "#" + accent.toArgbInt().toUInt().toString(16).uppercase().padStart(8, '0')

    /* ── The two lists have to be the same list ───────────────────────────── */

    @Test
    fun theSettingsSwatchesAreExactlyTheSelectablePalette() {
        // The swatches used to be eight literals written out in the screen while
        // the palette kept its own copy, and nothing asserted they agreed. An
        // accent added to the UI and forgotten in the palette would ship an
        // unverified colour straight onto a button.
        assertEquals(
            "AccentSwatches and AccentPalette.SELECTABLE_ARGB have drifted apart",
            AccentPalette.SELECTABLE_ARGB,
            swatchAccents.map { it.toArgbInt() }
        )
    }

    @Test
    fun theSweepActuallyCoversTheOfferedAccents() {
        // Guards the sweep itself: an empty or tiny list would make every other
        // assertion in this file vacuously true.
        assertEquals("the app offers eight accents", 8, everyAccent.size)
        assertTrue(paletteAccents.isNotEmpty())
        assertTrue(swatchAccents.isNotEmpty())
    }

    /* ── 1. Ink on the accent ─────────────────────────────────────────────── */

    @Test
    fun anAccentFilledButtonLabelClearsTheTextBarForEveryAccent() {
        everyAccent.forEach { accent ->
            val ink = accentContentOn(accent)
            val ratio = contrastRatio(ink, accent)
            assertTrue(
                "${label(accent)}: the button label is $ratio:1 against the accent, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun onPrimaryClearsTheTextBarForEveryAccentOnEveryTheme() {
        AppTheme.ALL.forEach { theme ->
            everyAccent.forEach { accent ->
                val scheme = solidSchemeFor(theme, accent)
                val ratio = contrastRatio(scheme.onPrimary, scheme.primary)
                assertTrue(
                    "${theme.label}/${label(accent)}: onPrimary on primary is $ratio:1, " +
                        "need ${MINIMUM_TEXT_CONTRAST}:1",
                    ratio >= MINIMUM_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun onSecondaryAndOnTertiaryClearTheTextBarToo() {
        AppTheme.ALL.forEach { theme ->
            everyAccent.forEach { accent ->
                val scheme = solidSchemeFor(theme, accent)
                listOf(
                    "onSecondary" to (scheme.onSecondary to scheme.secondary),
                    "onTertiary" to (scheme.onTertiary to scheme.tertiary)
                ).forEach { (role, pair) ->
                    val ratio = contrastRatio(pair.first, pair.second)
                    assertTrue(
                        "${theme.label}/${label(accent)}: $role is $ratio:1, " +
                            "need ${MINIMUM_TEXT_CONTRAST}:1",
                        ratio >= MINIMUM_TEXT_CONTRAST
                    )
                }
            }
        }
    }

    /* ── 2. The accent on the background ──────────────────────────────────── */

    @Test
    fun anAccentLabelOnTheBackgroundClearsTheTextBarForEveryAccentAndTheme() {
        // The opposite pairing, and the one that actually failed: a text button
        // label sits on the page background, so here the accent is on its own.
        // Measured before the fix, the default green was 3.90:1 on `Brote Verde`
        // and 4.10:1 on `Cuidado Nocturno`, and the amber swatch was 1.57:1 on
        // `Invernadero Soleado` — every one of them below 4.5:1.
        AppTheme.ALL.forEach { theme ->
            everyAccent.forEach { accent ->
                val scheme = solidSchemeFor(theme, accent)
                val ink = accentLabelOn(scheme, accent)
                val ratio = contrastRatio(ink, scheme.background)
                assertTrue(
                    "${theme.label}/${label(accent)}: the accent label resolves to $ratio:1 " +
                        "on the background, need ${MINIMUM_TEXT_CONTRAST}:1",
                    ratio >= MINIMUM_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun theAccentIsKeptWhereItContrastsAndDroppedWhereItDoesNot() {
        // The fallback is not a silent downgrade to grey everywhere: the accent
        // is still used wherever it can carry the label, which is most of the
        // dark themes. A blanket "always onSurface" would pass the test above
        // while throwing the user's chosen colour away.
        val night = solidSchemeFor(AppTheme.NIGHT, Color(0xFFFFC107))
        assertEquals(
            "amber is 12.88:1 on the near-black theme and must be kept",
            Color(0xFFFFC107),
            accentLabelOn(night, Color(0xFFFFC107))
        )

        val sunny = solidSchemeFor(AppTheme.SUNNY, Color(0xFFFFC107))
        assertEquals(
            "amber is 1.57:1 on the white theme and must be replaced by the theme's ink",
            sunny.onSurface,
            accentLabelOn(sunny, Color(0xFFFFC107))
        )
    }

    /* ── 3. The selected chip and the bottom-bar indicator ─────────────────── */

    @Test
    fun aSelectedChipLabelClearsTheTextBarOnEveryAccent() {
        // The chip fill is the accent, so its label is the same pairing as a
        // button. Unselected chips use `surfaceVariant` / `onSurfaceVariant`,
        // which the theme tests already cover.
        everyAccent.forEach { accent ->
            val ratio = contrastRatio(accentContentOn(accent), accent)
            assertTrue(
                "${label(accent)}: the selected chip label is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theBottomBarIndicatorKeepsItsLabelLegibleOnEveryAccent() {
        // The selected tab paints the accent as a filled pill with an icon and a
        // label on it, so the same resolution applies to both.
        everyAccent.forEach { accent ->
            val ratio = contrastRatio(accentContentOn(accent), accent)
            assertTrue(
                "${label(accent)}: the selected tab label is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    /* ── 4. The top bar's own content, against the surface it sits on ──────── */

    @Test
    fun theTopBarContentClearsTheTextBarForEveryAccentOnEveryTheme() {
        AppTheme.ALL.forEach { theme ->
            everyAccent.forEach { accent ->
                val palette = appTopBarPaletteFor(solidSchemeFor(theme, accent))
                listOf(
                    "title" to palette.titleContent,
                    "back arrow" to palette.navigationIconContent
                ).forEach { (what, ink) ->
                    val ratio = contrastRatio(ink, palette.containerColor)
                    assertTrue(
                        "${theme.label}/${label(accent)}: the top bar $what is $ratio:1, " +
                            "need ${MINIMUM_TEXT_CONTRAST}:1",
                        ratio >= MINIMUM_TEXT_CONTRAST
                    )
                }
                val action = contrastRatio(palette.actionIconContent, palette.containerColor)
                assertTrue(
                    "${theme.label}/${label(accent)}: the top bar action icon is $action:1, " +
                        "need ${MINIMUM_TEXT_CONTRAST}:1",
                    action >= MINIMUM_TEXT_CONTRAST
                )
            }
        }
    }

    /* ── 5. The call sites actually use the helpers ───────────────────────── */

    @Test
    fun noCallSiteFillsAButtonWithTheAccentAndLeavesTheLabelToMaterial() {
        // The pure functions above can all be correct while the screens keep
        // writing `ButtonDefaults.buttonColors(containerColor = accent)`, which
        // is the exact shape of the original defect. `accentButtonColors` and
        // `accentTextButtonColors` are the only two ways to paint a button with
        // the accent, so the source is checked rather than trusted.
        val offenders = mutableListOf<String>()
        for (file in productionSources()) {
            val code = file.readText(Charsets.UTF_8)
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
                .replace(Regex("""//[^\n]*"""), " ")
            Regex("""ButtonDefaults\.(buttonColors|textButtonColors)\s*\(""")
                .findAll(code)
                .forEach { match ->
                    // Read the call's own argument list, not the rest of the file.
                    val body = code.substring(match.range.last, minOf(code.length, match.range.last + 300))
                    val call = body.substringBefore(") {").substringBefore(")\n")
                    if (Regex("""containerColor\s*=\s*(accent|scheme\.primary|themeState\.accentColor)""").containsMatchIn(call) ||
                        Regex("""contentColor\s*=\s*accent\b""").containsMatchIn(call)
                    ) {
                        offenders += "${file.name}: ${match.value}"
                    }
                }
        }
        assertTrue(
            "these call sites paint a button with the user-chosen accent without " +
                "resolving the label against it: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun noFloatingActionButtonIsFilledWithTheAccentWithoutResolvingItsIcon() {
        // The FAB is the same pairing as a button, and it is easy to miss: it is
        // not a Button, so the button sweep above would not see it.
        val offenders = mutableListOf<String>()
        for (file in productionSources()) {
            val code = file.readText(Charsets.UTF_8)
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
                .replace(Regex("""//[^\n]*"""), " ")
            Regex("""FloatingActionButton\s*\(""").findAll(code).forEach { match ->
                val body = code.substring(match.range.last, minOf(code.length, match.range.last + 300))
                val call = body.substringBefore(") {").substringBefore(")\n")
                if (Regex("""containerColor\s*=\s*accent""").containsMatchIn(call) &&
                    !call.contains("accentContentOn(accent)")
                ) {
                    offenders += "${file.name}: FloatingActionButton"
                }
            }
        }
        assertTrue(
            "these FABs are filled with the accent and leave the icon colour to " +
                "Material: $offenders",
            offenders.isEmpty()
        )
    }

    /** Every production Kotlin file, wherever Gradle happens to run from. */
    private fun productionSources(): List<File> {
        val root = listOf(File("src/main/java/com/trichome/app"), File("app/src/main/java/com/trichome/app"))
            .firstOrNull { it.isDirectory }
            ?: error("could not locate the production source root")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // The helpers themselves live in the components package and are
            // *supposed* to build a button from the accent; only the call sites
            // are under test here.
            .filterNot { it.parentFile?.name == "components" && it.parentFile?.parentFile?.name == "ui" }
            .toList()
    }

    /* ── 6. The failure mode worth naming ─────────────────────────────────── */

    @Test
    fun noSingleAccentNeedsMoreThanOneInkColour() {
        // A design that needed a second rule — "dark accents get white, light
        // accents get black, and midtones get something else" — would be a sign
        // the rule is wrong. Black-or-white is enough for every colour there is,
        // which is why [readableOnStrict] is provable rather than hopeful.
        everyAccent.forEach { accent ->
            val ink = accentContentOn(accent)
            assertTrue(
                "${label(accent)}: the label ink must be pure black or pure white",
                ink == Color(0xFF000000) || ink == Color(0xFFFFFFFF)
            )
            assertEquals(
                "${label(accent)}: the ink must be the one that actually contrasts more",
                if (contrastRatio(Color(0xFF000000), accent) >= contrastRatio(Color(0xFFFFFFFF), accent)) {
                    Color(0xFF000000)
                } else {
                    Color(0xFFFFFFFF)
                },
                ink
            )
        }
    }

    @Test
    fun thePanelEdgeIsVisibleOnEveryThemeAndAccent() {
        // The edge no longer takes the accent, so the sweep is really over the
        // themes. It is kept over every accent anyway, because a colour the user
        // can pick changing the weight of the frame is exactly what this removed.
        // Held to 1.2:1 rather than the 3:1 non-text bar: the edge is structure,
        // not a state indicator, and the accent-filled surfaces carry the states.
        // Only the lower bound is swept here; the upper one lives in
        // `OpaqueThemeContrastTest`, which pins the whole band, and this sweep
        // would only repeat it for the same four themes eight times over.
        everyAccent.forEach { accent ->
            AppTheme.ALL.forEach { theme ->
                val scheme = solidSchemeFor(theme, accent)
                val edge = panelBorderColor(scheme.surface)
                assertTrue(
                    "${theme.label}/${label(accent)}: panel edge is " +
                        "${contrastRatio(edge, scheme.surface)}:1",
                    contrastRatio(edge, scheme.surface) >= 1.2f
                )
            }
        }
    }
}
