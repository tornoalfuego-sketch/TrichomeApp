package com.trichome.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/* ─────────────────────────── Theme catalogue ──────────────────────────── */

/** Index kept for DataStore/back-compat with previous releases. */
object ThemeIndex {
    const val GREEN = 0
    const val AUTUMN = 1
    const val NIGHT = 2
    const val SUNNY = 3
}

/** Selectable font families. Only generic families are used, so no font assets ship. */
enum class AppFontFamily(val label: String, val family: FontFamily) {
    SANS("Sans Serif", FontFamily.SansSerif),
    SERIF("Serif", FontFamily.Serif),
    MONO("Monoespaciada", FontFamily.Monospace),
    SCRIPT("Manuscrita", FontFamily.Cursive)
}

/** Selectable global font weights. */
enum class AppFontWeight(val label: String, val weight: FontWeight) {
    LIGHT("Ligera", FontWeight.Light),
    NORMAL("Normal", FontWeight.Normal),
    MEDIUM("Media", FontWeight.Medium),
    SEMIBOLD("Seminegrita", FontWeight.SemiBold),
    BOLD("Negrita", FontWeight.Bold)
}

/**
 * A complete, self-contained palette.
 *
 * [isDark] decides whether the scheme is built with `darkColorScheme` or
 * `lightColorScheme`. Every foreground colour is declared explicitly: deriving
 * them from the Material defaults is what made the dark themes unreadable,
 * because a dark background paired with `lightColorScheme` produced dark
 * `onBackground` / `onSurface` text on a dark surface.
 */
data class AppTheme(
    val index: Int,
    val label: String,
    /**
     * Spanish name of the opaque counterpart shown when glassmorphism is off.
     * Kept next to [label] so the two looks of one theme can never drift apart.
     */
    val solidLabel: String,
    val emoji: String,
    val isDark: Boolean,
    /** App backdrop. Deliberately much further from [surface] than a glass panel. */
    val background: Color,
    /** Panel base. Clearly lighter (dark themes) or darker (light theme) than [background]. */
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val secondaryBase: Color,
    val tertiaryBase: Color,
    val error: Color,
    val onError: Color = Color.White
) {
    /** Text colour that stays readable on top of [background]. */
    val onBackground: Color get() = if (isDark) Color(0xFFE9F1EA) else Color(0xFF12180F)
    val onSurface: Color get() = if (isDark) Color(0xFFE9F1EA) else Color(0xFF12180F)
    val onSurfaceVariant: Color get() = if (isDark) Color(0xFFB6C4B8) else Color(0xFF44503F)

    companion object {
        val GREEN = AppTheme(
            index = ThemeIndex.GREEN,
            label = "Brote Verde",
            solidLabel = "Brote Verde Sólido",
            emoji = "🌿",
            isDark = true,
            background = Color(0xFF0A120C),
            surface = Color(0xFF1A2A1D),
            surfaceVariant = Color(0xFF243527),
            outline = Color(0xFF43573F),
            secondaryBase = Color(0xFF7FD1A0),
            tertiaryBase = Color(0xFFFFC857),
            error = Color(0xFFFF8A80)
        )

        val AUTUMN = AppTheme(
            index = ThemeIndex.AUTUMN,
            label = "Cosecha de Otoño",
            solidLabel = "Cosecha Otoñal Sólida",
            emoji = "🍂",
            isDark = true,
            background = Color(0xFF140A05),
            surface = Color(0xFF2C1A10),
            surfaceVariant = Color(0xFF3B2416),
            outline = Color(0xFF6B4A32),
            secondaryBase = Color(0xFFFFB86B),
            tertiaryBase = Color(0xFFFFD166),
            error = Color(0xFFFF8A80)
        )

        val NIGHT = AppTheme(
            index = ThemeIndex.NIGHT,
            label = "Cuidado Nocturno",
            solidLabel = "Oscuro Extremo Sólido",
            emoji = "🌙",
            isDark = true,
            background = Color(0xFF05070F),
            surface = Color(0xFF111726),
            surfaceVariant = Color(0xFF1B2438),
            outline = Color(0xFF38455F),
            secondaryBase = Color(0xFF8AB4F8),
            tertiaryBase = Color(0xFFC7A8FF),
            error = Color(0xFFFF8A80)
        )

        val SUNNY = AppTheme(
            index = ThemeIndex.SUNNY,
            label = "Invernadero Soleado",
            solidLabel = "Claro Solar Sólido",
            emoji = "☀️",
            isDark = false,
            background = Color(0xFFEFF5E9),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDCE8D2),
            outline = Color(0xFF9DB095),
            secondaryBase = Color(0xFFB4631C),
            tertiaryBase = Color(0xFF2F7D32),
            error = Color(0xFFB3261E)
        )

        val ALL = listOf(GREEN, AUTUMN, NIGHT, SUNNY)
    }
}

/**
 * Builds the Material 3 scheme for a theme, overlaying the user accent onto the
 * primary/secondary/tertiary roles so that picking an accent actually changes
 * the UI. Foreground colours are re-derived from the accent luminance so the
 * contrast never collapses.
 */
fun schemeFor(theme: AppTheme, accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF101410) else Color.White
    val accentContainer = blend(accent, theme.surface, 0.65f)
    val onAccentContainer = if (accentContainer.luminance() > 0.45f) Color(0xFF101410) else Color.White

    val build: (
        primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
        secondary: Color, onSecondary: Color, secondaryContainer: Color, onSecondaryContainer: Color,
        tertiary: Color, onTertiary: Color, tertiaryContainer: Color, onTertiaryContainer: Color,
        background: Color, onBackground: Color, surface: Color, onSurface: Color,
        surfaceVariant: Color, onSurfaceVariant: Color, outline: Color,
        error: Color, onError: Color
    ) -> ColorScheme

    if (theme.isDark) {
        build = { p, op, pc, opc, s, os, sc, osc, t, ot, tc, otc, bg, ob, su, osu, sv, osv, ol, er, oer ->
            darkColorScheme(
                primary = p, onPrimary = op, primaryContainer = pc, onPrimaryContainer = opc,
                secondary = s, onSecondary = os, secondaryContainer = sc, onSecondaryContainer = osc,
                tertiary = t, onTertiary = ot, tertiaryContainer = tc, onTertiaryContainer = otc,
                background = bg, onBackground = ob, surface = su, onSurface = osu,
                surfaceVariant = sv, onSurfaceVariant = osv, outline = ol,
                error = er, onError = oer
            )
        }
    } else {
        build = { p, op, pc, opc, s, os, sc, osc, t, ot, tc, otc, bg, ob, su, osu, sv, osv, ol, er, oer ->
            lightColorScheme(
                primary = p, onPrimary = op, primaryContainer = pc, onPrimaryContainer = opc,
                secondary = s, onSecondary = os, secondaryContainer = sc, onSecondaryContainer = osc,
                tertiary = t, onTertiary = ot, tertiaryContainer = tc, onTertiaryContainer = otc,
                background = bg, onBackground = ob, surface = su, onSurface = osu,
                surfaceVariant = sv, onSurfaceVariant = osv, outline = ol,
                error = er, onError = oer
            )
        }
    }

    val secondary = if (theme.isDark) theme.secondaryBase else theme.secondaryBase.darken(0.25f)
    val tertiary = if (theme.isDark) theme.tertiaryBase else theme.tertiaryBase.darken(0.3f)

    return build(
        accent, onAccent, accentContainer, onAccentContainer,
        secondary, readableOn(secondary), blend(secondary, theme.surface, 0.7f), readableOn(blend(secondary, theme.surface, 0.7f)),
        tertiary, readableOn(tertiary), blend(tertiary, theme.surface, 0.7f), readableOn(blend(tertiary, theme.surface, 0.7f)),
        theme.background, theme.onBackground,
        theme.surface, theme.onSurface,
        theme.surfaceVariant, theme.onSurfaceVariant,
        theme.outline,
        theme.error, theme.onError
    )
}

/** Black or white, whichever stays readable on [color]. */
fun readableOn(color: Color): Color =
    if (color.luminance() > 0.45f) Color(0xFF101410) else Color.White

/* ─────────────────────────── WCAG contrast math ─────────────────────────── */

/** WCAG 2.1 AA ratio for body text. Anything below this is unreadable. */
const val MINIMUM_TEXT_CONTRAST = 4.5f

/**
 * WCAG 2.1 AA ratio for non-text UI: borders, dividers, panel edges.
 *
 * Separate from [MINIMUM_TEXT_CONTRAST] because a panel outline carries no
 * text, so 4.5:1 is the wrong bar for it — but 3:1 is the right one, and it is
 * still a real requirement. The solid themes exist to draw a *visible* edge
 * around each panel, and an outline sitting at 2.5:1 against its own surface
 * fails that while passing every text assertion.
 */
const val MINIMUM_NON_TEXT_CONTRAST = 3f

private val SolidInk = Color(0xFF000000)
private val SolidIvory = Color(0xFFFFFFFF)

/** Inverse of the sRGB electro-optical transfer function, per WCAG 2.1. */
private fun linearize(channel: Float): Float {
    val c = channel.coerceIn(0f, 1f)
    return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
}

/**
 * WCAG relative luminance, computed from the sRGB components.
 *
 * Deliberately not `Color.luminance()`: the app runs unit tests with
 * `isReturnDefaultValues = true`, so any code path that reaches an unmocked
 * `android.graphics` method returns zero and a contrast assertion would pass
 * without comparing anything. This version touches no platform API.
 */
fun relativeLuminance(color: Color): Float =
    0.2126f * linearize(color.red) +
        0.7152f * linearize(color.green) +
        0.0722f * linearize(color.blue)

/** WCAG contrast ratio between two opaque colours, from 1.0 to 21.0. */
fun contrastRatio(foreground: Color, background: Color): Float {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * The black-or-white foreground that actually contrasts more with [color].
 *
 * [readableOn] is a luminance threshold, which is why the glass themes still
 * produced unreadable pairings. Picking the better of the two extremes is
 * provably at least 4.58:1 for *any* input, which is what the opaque themes
 * need when the accent is an arbitrary user-chosen colour.
 */
fun readableOnStrict(color: Color): Color =
    if (contrastRatio(SolidInk, color) >= contrastRatio(SolidIvory, color)) SolidInk else SolidIvory

private fun blend(foreground: Color, background: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = foreground.red * r + background.red * (1 - r),
        green = foreground.green * r + background.green * (1 - r),
        blue = foreground.blue * r + background.blue * (1 - r),
        alpha = 1f
    )
}

private fun Color.darken(factor: Float): Color = blend(Color.Black, this, factor.coerceIn(0f, 1f))

/* ─────────────────────────── Solid (opaque) themes ─────────────────────── */

/**
 * The opaque counterpart of an [AppTheme].
 *
 * "Solid" means exactly two things: every colour below is fully opaque, and
 * every `on*` role is resolved with [readableOnStrict] so it clears
 * [MINIMUM_TEXT_CONTRAST] against the surface it is drawn on. `NIGHT` and
 * `SUNNY` are the extremes (true black, true white) and the other two mirror
 * their glass counterparts.
 */
data class SolidPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val error: Color
)

/** The four opaque palettes, keyed by [AppTheme.index]. */
object SolidPalettes {

    val GREEN = SolidPalette(
        background = Color(0xFF040A06),
        surface = Color(0xFF15261A),
        surfaceVariant = Color(0xFF213526),
        outline = Color(0xFF62815D),
        outlineVariant = Color(0xFF5F8262),
        onBackground = Color(0xFFF3F9F2),
        onSurface = Color(0xFFF3F9F2),
        onSurfaceVariant = Color(0xFFC2D5C4),
        error = Color(0xFFFFB4AB)
    )

    val AUTUMN = SolidPalette(
        background = Color(0xFF100702),
        surface = Color(0xFF291608),
        surfaceVariant = Color(0xFF3A2011),
        outline = Color(0xFF956944),
        outlineVariant = Color(0xFF966443),
        onBackground = Color(0xFFFDF2E7),
        onSurface = Color(0xFFFDF2E7),
        onSurfaceVariant = Color(0xFFDDC3A8),
        error = Color(0xFFFFB4AB)
    )

    val NIGHT = SolidPalette(
        background = Color(0xFF000000),
        surface = Color(0xFF0D111C),
        surfaceVariant = Color(0xFF17202F),
        outline = Color(0xFF596D93),
        outlineVariant = Color(0xFF536A97),
        onBackground = Color(0xFFF5F7FF),
        onSurface = Color(0xFFF5F7FF),
        onSurfaceVariant = Color(0xFFBFC9E0),
        error = Color(0xFFFFB4AB)
    )

    val SUNNY = SolidPalette(
        background = Color(0xFFFCFBF2),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEDEFDF),
        outline = Color(0xFF6E7A66),
        outlineVariant = Color(0xFF858A7C),
        onBackground = Color(0xFF14180F),
        onSurface = Color(0xFF14180F),
        onSurfaceVariant = Color(0xFF3E4739),
        error = Color(0xFFB3261E)
    )

    /** Palette for [theme], falling back to [GREEN] for an unknown index. */
    fun forTheme(theme: AppTheme): SolidPalette = when (theme.index) {
        ThemeIndex.GREEN -> GREEN
        ThemeIndex.AUTUMN -> AUTUMN
        ThemeIndex.NIGHT -> NIGHT
        ThemeIndex.SUNNY -> SUNNY
        else -> GREEN
    }
}

/**
 * The opaque Material 3 scheme for a theme, used whenever the glassmorphism
 * flag is off.
 *
 * The accent keeps its meaning: it is the `primary` role, its container is
 * mixed into the surface, and every `on*` role derived from those user-chosen
 * colours is resolved with [readableOnStrict] rather than a luminance
 * threshold, so any accent stays legible.
 */
fun solidSchemeFor(theme: AppTheme, accent: Color): ColorScheme {
    val palette = SolidPalettes.forTheme(theme)

    val primaryContainer = blend(accent, palette.surface, 0.72f)
    val secondaryBase = if (theme.isDark) theme.secondaryBase else theme.secondaryBase.darken(0.35f)
    val secondaryContainer = blend(secondaryBase, palette.surface, 0.75f)
    val tertiaryBase = if (theme.isDark) theme.tertiaryBase else theme.tertiaryBase.darken(0.40f)
    val tertiaryContainer = blend(tertiaryBase, palette.surface, 0.75f)
    val errorContainer = blend(palette.error, palette.surface, 0.80f)

    // Every default role of darkColorScheme/lightColorScheme is already opaque;
    // only the roles the palette owns are replaced.
    val base = if (theme.isDark) darkColorScheme() else lightColorScheme()

    return base.copy(
        primary = accent,
        onPrimary = readableOnStrict(accent),
        primaryContainer = primaryContainer,
        onPrimaryContainer = readableOnStrict(primaryContainer),
        secondary = secondaryBase,
        onSecondary = readableOnStrict(secondaryBase),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = readableOnStrict(secondaryContainer),
        tertiary = tertiaryBase,
        onTertiary = readableOnStrict(tertiaryBase),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = readableOnStrict(tertiaryContainer),
        background = palette.background,
        onBackground = palette.onBackground,
        surface = palette.surface,
        onSurface = palette.onSurface,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.onSurfaceVariant,
        outline = palette.outline,
        outlineVariant = palette.outlineVariant,
        error = palette.error,
        onError = readableOnStrict(palette.error),
        errorContainer = errorContainer,
        onErrorContainer = readableOnStrict(errorContainer)
    )
}

/**
 * The one place where the glassmorphism flag chooses the active scheme.
 *
 * Everything that needs a scheme goes through here, so there is no way for one
 * screen to keep rendering the glass palette while the rest of the app renders
 * the opaque one.
 */
fun activeSchemeFor(theme: AppTheme, accent: Color, glassEnabled: Boolean): ColorScheme =
    if (glassEnabled) schemeFor(theme, accent) else solidSchemeFor(theme, accent)

/**
 * Every colour role carried by [scheme], keyed by role name.
 *
 * Exists so the opacity guarantee can be checked exhaustively from a JVM test
 * instead of by hand. All the colours returned here are opaque, but nothing
 * stops a future edit from introducing a translucent one — this is the tripwire.
 */
fun schemeColorsOf(scheme: ColorScheme): Map<String, Color> = mapOf(
    "primary" to scheme.primary,
    "onPrimary" to scheme.onPrimary,
    "primaryContainer" to scheme.primaryContainer,
    "onPrimaryContainer" to scheme.onPrimaryContainer,
    "inversePrimary" to scheme.inversePrimary,
    "secondary" to scheme.secondary,
    "onSecondary" to scheme.onSecondary,
    "secondaryContainer" to scheme.secondaryContainer,
    "onSecondaryContainer" to scheme.onSecondaryContainer,
    "tertiary" to scheme.tertiary,
    "onTertiary" to scheme.onTertiary,
    "tertiaryContainer" to scheme.tertiaryContainer,
    "onTertiaryContainer" to scheme.onTertiaryContainer,
    "background" to scheme.background,
    "onBackground" to scheme.onBackground,
    "surface" to scheme.surface,
    "onSurface" to scheme.onSurface,
    "surfaceVariant" to scheme.surfaceVariant,
    "onSurfaceVariant" to scheme.onSurfaceVariant,
    "surfaceTint" to scheme.surfaceTint,
    "inverseSurface" to scheme.inverseSurface,
    "inverseOnSurface" to scheme.inverseOnSurface,
    "error" to scheme.error,
    "onError" to scheme.onError,
    "errorContainer" to scheme.errorContainer,
    "onErrorContainer" to scheme.onErrorContainer,
    "outline" to scheme.outline,
    "outlineVariant" to scheme.outlineVariant,
    "scrim" to scheme.scrim,
    "surfaceBright" to scheme.surfaceBright,
    "surfaceDim" to scheme.surfaceDim,
    "surfaceContainer" to scheme.surfaceContainer,
    "surfaceContainerHigh" to scheme.surfaceContainerHigh,
    "surfaceContainerHighest" to scheme.surfaceContainerHighest,
    "surfaceContainerLow" to scheme.surfaceContainerLow,
    "surfaceContainerLowest" to scheme.surfaceContainerLowest
)

/* ─────────────────────────── Accent constants ──────────────────────────── */

/**
 * The single source of truth for the accent greens.
 *
 * The app used to ship three different "default accent" literals and nothing
 * asserted they agreed:
 *
 * - `0xFF2E7D32` in `AppearanceSettings`, which is the one that wins on a fresh
 *   install because `TrichomeThemeState.collectFromRepository()` reads it;
 * - `0xFF4CAF50` in `TrichomeThemeState` and `GlassConfig`;
 * - `0xFF66BB6A` in `GlassTokens` and in the orb background's first colour.
 *
 * The latter two were effectively dead — every real path overwrote them from the
 * preference — which is exactly why they were free to rot, and why any new call
 * site reading `GlassTokens()` got a different green from every other surface.
 *
 * `DEFAULT_ACCENT_ARG` is `0xFF2E7D32` because that is what users already have.
 * Changing it would silently repaint everyone's app on upgrade with no
 * migration, no data change, and no way to notice it was a default rather than a
 * choice. The remaining constants exist so a new surface has something to read
 * instead of writing a fourth literal.
 */
object AccentPalette {
    /**
     * The green every existing install already has. Do not change silently.
     *
     * Written as an explicit signed Int rather than `const`: `0xFF2E7D32` is
     * above `Int.MAX_VALUE`, so Kotlin types the literal as a Long and a
     * `const val ... = 0xFF2E7D32.toInt()` is not a compile-time constant.
     */
    val DEFAULT_ACCENT_ARG: Int = 0xFF2E7D32.toInt()

    /** The same green as a Compose [Color]. */
    val DEFAULT_ACCENT: Color get() = Color(DEFAULT_ACCENT_ARG)

    /**
     * Warmer green used by the theme catalogue's own accents.
     *
     * A second user-selectable swatch, not a default. It is named here so the
     * settings palette and any future surface read the same value.
     */
    val LIME_ACCENT_ARG: Int = 0xFF7CB342.toInt()

    /** Amber, the secondary hue of the floating orb background. */
    val ORB_SECONDARY_ARG: Int = 0xFFFFC107.toInt()

    /** Every accent the settings screen offers, so the palette has one list. */
    val SELECTABLE_ARGB: List<Int> = listOf(
        DEFAULT_ACCENT_ARG, LIME_ACCENT_ARG, ORB_SECONDARY_ARG,
        0xFFE65100.toInt(), 0xFF00ACC1.toInt(), 0xFF7C4DFF.toInt(),
        0xFFEC407A.toInt(), 0xFFE53935.toInt()
    )
}

/* ─────────────────────────── Glass control ranges ──────────────────────── */

/**
 * The single source of truth for the glass sliders.
 *
 * The repository used to clamp opacity to 0.50 while the state holder and the
 * Settings slider offered 0.55, so every value in `(0.50, 0.55]` was silently
 * discarded on restart. All three sites now read these constants, and
 * [opacity] / [blur] are the exact ranges handed to the sliders.
 */
object GlassRanges {
    const val OPACITY_MIN = 0.05f
    const val OPACITY_MAX = 0.55f
    const val BLUR_MIN = 0f
    const val BLUR_MAX = 32f

    /** Persisted default, also the [GlassTokens] default. */
    const val OPACITY_DEFAULT = 0.15f
    const val BLUR_DEFAULT = 12f

    val opacity: ClosedFloatingPointRange<Float> = OPACITY_MIN..OPACITY_MAX
    val blur: ClosedFloatingPointRange<Float> = BLUR_MIN..BLUR_MAX

    fun clampOpacity(value: Float): Float = value.coerceIn(OPACITY_MIN, OPACITY_MAX)
    fun clampBlur(value: Float): Float = value.coerceIn(BLUR_MIN, BLUR_MAX)
}

/* ─────────────────────────── Glass config ──────────────────────────────── */

data class GlassTokens(
    val glassOpacity: Float = GlassRanges.OPACITY_DEFAULT,
    val blurRadius: Float = GlassRanges.BLUR_DEFAULT,
    /**
     * Reads [AccentPalette.DEFAULT_ACCENT_ARG], which is what the persisted
     * default in `AppearanceSettings` is.
     */
    val accentColor: Color = AccentPalette.DEFAULT_ACCENT
)

/**
 * The resolved glass configuration, provided to the whole tree by
 * [TrichomeTheme] and read by every glass component.
 *
 * [enabled] is the on/off preference. When it is `false` the components render
 * opaque surfaces instead of translucent ones; the three tuning fields stay in
 * the config either way, so turning the effect back on restores the exact look
 * the user had set up.
 */
data class GlassConfig(
    val enabled: Boolean = false,
    val glassOpacity: Float = GlassRanges.OPACITY_DEFAULT,
    val blurRadius: Float = GlassRanges.BLUR_DEFAULT,
    /** Reads [AccentPalette.DEFAULT_ACCENT_ARG], like every other accent default. */
    val accentColor: Color = AccentPalette.DEFAULT_ACCENT
) {
    /**
     * Applies the per-call-site overrides, if any.
     *
     * Note what is *not* overridable: [enabled]. A call site can still nudge
     * the opacity of one panel, but nothing but the user's preference decides
     * whether the app is glassy — which is what stops a leftover literal from
     * silently defeating the toggle.
     */
    fun resolve(
        glassOpacity: Float? = null,
        blurRadius: Float? = null,
        accentColor: Color? = null
    ): GlassConfig = GlassConfig(
        enabled = enabled,
        glassOpacity = glassOpacity ?: this.glassOpacity,
        blurRadius = blurRadius ?: this.blurRadius,
        accentColor = accentColor ?: this.accentColor
    )
}

/**
 * Reads the glass configuration from the nearest [TrichomeTheme].
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: the value is
 * recomputed on every preference change anyway, and the whole subtree is
 * invalidated with it, so per-reader recomposition would buy nothing.
 */
val LocalGlassConfig = staticCompositionLocalOf { GlassConfig() }

/** Pure projection from the persisted primitives onto the composition local. */
fun glassConfigOf(
    enabled: Boolean,
    glassOpacity: Float,
    blurRadius: Float,
    accentArgb: Int
): GlassConfig = GlassConfig(
    enabled = enabled,
    glassOpacity = GlassRanges.clampOpacity(glassOpacity),
    blurRadius = GlassRanges.clampBlur(blurRadius),
    accentColor = Color(accentArgb)
)

/* ─────────────────────────── Typography ───────────────────────────────── */

private val LineHeightStyleCompat = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

/**
 * Builds the full Material typography from the user preferences.
 * [scale] is the 0.85–1.30 size multiplier, [family] the typeface and [weight]
 * the base weight applied to every style.
 */
fun buildTypography(
    scale: Float,
    family: AppFontFamily = AppFontFamily.SANS,
    weight: AppFontWeight = AppFontWeight.NORMAL
): Typography {
    fun style(size: Int, lineHeight: Int, w: FontWeight? = null, letterSpacing: Double = 0.0) =
        androidx.compose.ui.text.TextStyle(
            fontFamily = family.family,
            fontWeight = w ?: weight.weight,
            fontSize = size.sp * scale,
            lineHeight = lineHeight.sp * scale,
            letterSpacing = letterSpacing.sp,
            lineHeightStyle = LineHeightStyleCompat
        )

    return Typography(
        displayLarge = style(57, 64, weight.weight, (-0.25)),
        displayMedium = style(45, 52),
        displaySmall = style(36, 44),
        headlineLarge = style(32, 40),
        headlineMedium = style(28, 36),
        headlineSmall = style(24, 32),
        titleLarge = style(22, 28, weight.weight.lift()),
        titleMedium = style(16, 24, weight.weight.lift(), 0.15),
        titleSmall = style(14, 20, weight.weight.lift(), 0.1),
        bodyLarge = style(16, 24, letterSpacing = 0.5),
        bodyMedium = style(14, 20, letterSpacing = 0.25),
        bodySmall = style(12, 16, letterSpacing = 0.4),
        labelLarge = style(14, 20, weight.weight.lift(), 0.1),
        labelMedium = style(12, 16, weight.weight.lift(), 0.5),
        labelSmall = style(11, 16, weight.weight.lift(), 0.5)
    )
}

private fun FontWeight.lift(): FontWeight = when (this) {
    FontWeight.Light -> FontWeight.Normal
    FontWeight.Normal -> FontWeight.Medium
    FontWeight.Medium -> FontWeight.SemiBold
    else -> this
}

/* ─────────────────────────── Runtime state ────────────────────────────── */

/**
 * Runtime holder for the appearance preferences, persisted through
 * [com.trichome.app.data.prefs.AppearanceSettingsRepository].
 */
class TrichomeThemeState(
    private val appContainer: com.trichome.app.di.AppContainer? = null
) {
    var glassTokens by mutableStateOf(GlassTokens())
        private set

    /**
     * Whether the app renders translucent glass panels at all.
     *
     * Defaults to `false`: a user who never chose a look gets the opaque,
     * high-contrast themes, which is the safer default for legibility.
     */
    var isGlassmorphismEnabled by mutableStateOf(false)
        private set

    var fontScale by mutableStateOf(1.0f)
        private set

    var selectedColorIndex by mutableStateOf(ThemeIndex.GREEN)
        private set

    /**
     * The user's accent, before the repository has been collected.
     *
     * [AccentPalette.DEFAULT_ACCENT_ARG] rather than a literal of its own: the
     * first frame of a launch used to be `0xFF4CAF50` and the second the
     * persisted `0xFF2E7D32`, so the app visibly repainted itself on startup.
     */
    var accentColor by mutableStateOf(AccentPalette.DEFAULT_ACCENT)
        private set

    var fontFamilyIndex by mutableStateOf(0)
        private set

    var fontWeightIndex by mutableStateOf(AppFontWeight.NORMAL.ordinal)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val theme: AppTheme
        get() = AppTheme.ALL.firstOrNull { it.index == selectedColorIndex } ?: AppTheme.GREEN

    val fontFamily: AppFontFamily
        get() = AppFontFamily.entries.getOrElse(fontFamilyIndex) { AppFontFamily.SANS }

    val fontWeight: AppFontWeight
        get() = AppFontWeight.entries.getOrElse(fontWeightIndex) { AppFontWeight.NORMAL }

    /** One-shot collection of the persisted preferences. */
    suspend fun collectFromRepository() {
        val container = appContainer ?: return
        container.appearanceSettings.settings.collect { prefs ->
            selectedColorIndex = prefs.themeIndex.coerceIn(AppTheme.ALL.indices)
            fontScale = prefs.fontScale
            accentColor = Color(prefs.accentArgb)
            fontFamilyIndex = prefs.fontFamilyIndex.coerceIn(AppFontFamily.entries.indices)
            fontWeightIndex = prefs.fontWeightIndex.coerceIn(AppFontWeight.entries.indices)
            isGlassmorphismEnabled = prefs.glassEnabled
            glassTokens = GlassTokens(
                glassOpacity = GlassRanges.clampOpacity(prefs.glassOpacity),
                blurRadius = GlassRanges.clampBlur(prefs.blurRadius),
                accentColor = Color(prefs.accentArgb)
            )
        }
    }

    fun selectColor(index: Int) {
        val safe = index.coerceIn(AppTheme.ALL.indices)
        selectedColorIndex = safe
        scope.launch { appContainer?.appearanceSettings?.setThemeIndex(safe) }
    }

    fun setGlassOpacity(opacity: Float) {
        val v = GlassRanges.clampOpacity(opacity)
        glassTokens = glassTokens.copy(glassOpacity = v)
        scope.launch { appContainer?.appearanceSettings?.setGlassOpacity(v) }
    }

    fun setBlurRadius(radius: Float) {
        val v = GlassRanges.clampBlur(radius)
        glassTokens = glassTokens.copy(blurRadius = v)
        scope.launch { appContainer?.appearanceSettings?.setBlurRadius(v) }
    }

    /**
     * Turns the glassmorphism effect on or off across the whole UI.
     *
     * Named `update...` rather than `set...` because the `isGlassmorphismEnabled`
     * property already compiles to a `setGlassmorphismEnabled(Z)V` setter, and
     * a function of that name would clash on the JVM signature.
     */
    fun updateGlassmorphismEnabled(enabled: Boolean) {
        isGlassmorphismEnabled = enabled
        scope.launch { appContainer?.appearanceSettings?.setGlassEnabled(enabled) }
    }

    /** The accent is the single source of truth for the `primary` role. */
    fun updateAccentColor(color: Color) {
        val argb = color.toArgbInt()
        accentColor = color
        glassTokens = glassTokens.copy(accentColor = color)
        scope.launch { appContainer?.appearanceSettings?.setAccentArgb(argb) }
    }

    fun updateFontScale(scale: Float) {
        val v = scale.coerceIn(0.85f, 1.30f)
        fontScale = v
        scope.launch { appContainer?.appearanceSettings?.setFontScale(v) }
    }

    fun updateFontFamily(index: Int) {
        val safe = index.coerceIn(AppFontFamily.entries.indices)
        fontFamilyIndex = safe
        scope.launch { appContainer?.appearanceSettings?.setFontFamilyIndex(safe) }
    }

    fun updateFontWeight(index: Int) {
        val safe = index.coerceIn(AppFontWeight.entries.indices)
        fontWeightIndex = safe
        scope.launch { appContainer?.appearanceSettings?.setFontWeightIndex(safe) }
    }

    /**
     * The config handed to [LocalGlassConfig]. Reading the state fields here is
     * what subscribes [TrichomeTheme] to every appearance change.
     */
    fun glassConfig(): GlassConfig = glassConfigOf(
        enabled = isGlassmorphismEnabled,
        glassOpacity = glassTokens.glassOpacity,
        blurRadius = glassTokens.blurRadius,
        accentArgb = accentColor.toArgbInt()
    )

    fun colorScheme(): ColorScheme = activeSchemeFor(theme, accentColor, isGlassmorphismEnabled)

    fun typography(): Typography = buildTypography(fontScale, fontFamily, fontWeight)
}

/* ─────────────────────────── ARGB helpers ─────────────────────────────── */

/**
 * Converts a Compose [Color] to a packed 32-bit ARGB int.
 *
 * `Color.value` is a `ULong` holding the RGBA bits in the low 32 positions, so
 * masking is the only safe way to get a Java `int` colour back.
 */
fun Color.toArgbInt(): Int = (value.toLong() and 0xFFFFFFFFL).toInt()

/** Hex string for the accent picker readout, e.g. `FF2E7D32`. */
fun Color.toArgbHex(): String = toArgbInt().toUInt().toString(16).uppercase().padStart(8, '0')

/**
 * Applies the appearance state to the whole tree.
 *
 * The colour scheme and the typography are recomputed on every preference
 * change, so a theme, accent, typeface or size tweak is reflected immediately
 * without recreating the activity. The glass configuration is published
 * alongside them, which is what lets every glass component read the user's
 * preference instead of a hardcoded default.
 */
@Composable
fun TrichomeTheme(
    themeState: TrichomeThemeState,
    content: @Composable () -> Unit
) {
    val scheme = themeState.colorScheme()
    val typography = themeState.typography()
    val glassConfig = themeState.glassConfig()
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val context = androidx.compose.ui.platform.LocalContext.current
        SideEffect {
            (context as? android.app.Activity)?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = typography
    ) {
        CompositionLocalProvider(
            LocalGlassConfig provides glassConfig,
            content = content
        )
    }
}
