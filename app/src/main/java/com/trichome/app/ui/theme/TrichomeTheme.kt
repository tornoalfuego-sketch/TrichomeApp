package com.trichome.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
 * One selectable theme: its identity, and nothing else.
 *
 * The colours live in exactly one place, [SolidPalette]. They used to be
 * declared here too, as a second copy used by the translucent palettes, and the
 * two copies drifted: a theme had a `background` for the app and a different
 * `background` for its opaque counterpart, so the Settings preview could show a
 * palette the app was not rendering. With one palette per theme that class of
 * bug is not expressible.
 *
 * @param isDark decides whether the scheme is built with `darkColorScheme` or
 *   `lightColorScheme`. Deriving the foregrounds from the Material defaults is
 *   what made the dark themes unreadable, so every `on*` role is declared
 *   explicitly in [SolidPalette] instead.
 */
data class AppTheme(
    val index: Int,
    /** Spanish display name. Shown in Settings and in the diagnosis report. */
    val label: String,
    val emoji: String,
    val isDark: Boolean
) {
    companion object {
        val GREEN = AppTheme(
            index = ThemeIndex.GREEN,
            label = "Brote Verde",
            emoji = "🌿",
            isDark = true
        )

        val AUTUMN = AppTheme(
            index = ThemeIndex.AUTUMN,
            label = "Cosecha de Otoño",
            emoji = "🍂",
            isDark = true
        )

        val NIGHT = AppTheme(
            index = ThemeIndex.NIGHT,
            label = "Cuidado Nocturno",
            emoji = "🌙",
            isDark = true
        )

        val SUNNY = AppTheme(
            index = ThemeIndex.SUNNY,
            label = "Invernadero Soleado",
            emoji = "☀️",
            isDark = false
        )

        val ALL = listOf(GREEN, AUTUMN, NIGHT, SUNNY)
    }
}

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
 * A luminance threshold is not enough here. The accent is an arbitrary
 * user-chosen colour, and the worst case for any threshold rule is an accent
 * sitting exactly on it. Picking the better of the two extremes is
 * provably at least 4.58:1 for *any* input.
 */
fun readableOnStrict(color: Color): Color =
    if (contrastRatio(SolidInk, color) >= contrastRatio(SolidIvory, color)) SolidInk else SolidIvory

/**
 * A container that reads as a distinct edge against [surface], derived from
 * [accent].
 *
 * Container roles used to be a fixed blend factor — 0.72, 0.75, 0.75, 0.80 —
 * which left `primaryContainer` 2.25:1 from its own surface on the default
 * green: below the 3:1 WCAG 1.4.11 asks of a boundary, and useless as a
 * selection marker. A constant cannot work here. The accent is user-chosen and
 * the four themes differ widely in lightness, so any factor that is right for a
 * dark theme is invisible on a light one.
 *
 * Two searches, in order, both taking the *smallest* change that clears the bar:
 *
 * 1. **Tint the accent towards the surface.** Contrast falls monotonically as
 *    the tint strengthens, so the first ratio walking down from 1 that still
 *    clears is the strongest visible tint. This is the normal case: a mid-tone
 *    accent on a mid-tone surface.
 * 2. **Push the accent away from the surface.** Some accents cannot be tinted
 *    into visibility at all. Lime on the lightest theme is the one that bites:
 *    lime is already bright, so tinting it towards white *lowers* contrast, and
 *    even the undiluted accent sits near 1.9:1. The only way to get a visible
 *    container is to move the accent itself away from the surface — darker on a
 *    light theme, lighter on a dark one.
 *
 * The second search is what stops the failure mode where a pale accent on the
 * lightest theme yields a container identical to its own background, which would
 * be a 1:1 "edge" and a marker nobody can see.
 */
fun containerFor(
    accent: Color,
    surface: Color,
    minimumContrast: Float = MINIMUM_NON_TEXT_CONTRAST,
    steps: Int = 24
): Color {
    // 1. Strongest tint towards the surface that still reads as an edge.
    for (step in steps downTo 0) {
        val ratio = step / steps.toFloat()
        val candidate = blend(accent, surface, ratio)
        if (contrastRatio(candidate, surface) >= minimumContrast) return candidate
    }

    // 2. No tint works. Walk the accent away from the surface instead, towards
    //    whichever extreme is further from it, and take the first that clears.
    val away = if (contrastRatio(SolidInk, surface) >= contrastRatio(SolidIvory, surface)) {
        SolidInk
    } else {
        SolidIvory
    }
    for (step in 1..steps) {
        val candidate = blend(accent, away, step / steps.toFloat())
        if (contrastRatio(candidate, surface) >= minimumContrast) return candidate
    }
    // Unreachable for any real surface: at ratio 1 the candidate is the extreme
    // furthest from the surface, and an ink-or-ivory colour always clears 3:1
    // against any surface this app ships.
    return blend(accent, away, 1f)
}

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

/* ─────────────────────────── Theme palettes ───────────────────────────── */

/**
 * The complete colour set of one [AppTheme].
 *
 * Two guarantees, both asserted from a JVM test rather than by eye:
 *
 * 1. every colour is fully opaque, and
 * 2. every `on*` role is resolved with [readableOnStrict] so it clears
 *    [MINIMUM_TEXT_CONTRAST] against the surface it is drawn on.
 *
 * `NIGHT` and `SUNNY` are the extremes (true black, true white); the other two
 * are warm and cool mid-tones.
 *
 * @param secondaryBase the theme's own supporting hue, before the light-theme
 *   darkening in [solidSchemeFor]. It is a *base*, not a scheme role: keeping it
 *   named as such is what stops a future edit from treating it as something that
 *   is already contrast-checked.
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
    val secondaryBase: Color,
    val tertiaryBase: Color,
    val error: Color
)

/** The four palettes, keyed by [AppTheme.index]. */
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
        secondaryBase = Color(0xFF7FD1A0),
        tertiaryBase = Color(0xFFFFC857),
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
        secondaryBase = Color(0xFFFFB86B),
        tertiaryBase = Color(0xFFFFD166),
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
        secondaryBase = Color(0xFF8AB4F8),
        tertiaryBase = Color(0xFFC7A8FF),
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
        secondaryBase = Color(0xFFB4631C),
        tertiaryBase = Color(0xFF2F7D32),
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
 * The one Material 3 scheme for a theme.
 *
 * Everything that needs a scheme goes through here, so there is no way for one
 * screen to render a different palette than the rest of the app.
 *
 * The accent keeps its meaning: it is the `primary` role, its container is mixed
 * into the surface, and every `on*` role derived from a user-chosen colour is
 * resolved with [readableOnStrict] rather than a luminance threshold — so *any*
 * accent stays legible, not just the green that shipped.
 */
fun solidSchemeFor(theme: AppTheme, accent: Color): ColorScheme {
    val palette = SolidPalettes.forTheme(theme)

    val primaryContainer = containerFor(accent, palette.surface)
    val secondaryBase = if (theme.isDark) palette.secondaryBase else palette.secondaryBase.darken(0.35f)
    val secondaryContainer = containerFor(secondaryBase, palette.surface)
    val tertiaryBase = if (theme.isDark) palette.tertiaryBase else palette.tertiaryBase.darken(0.40f)
    val tertiaryContainer = containerFor(tertiaryBase, palette.surface)
    val errorContainer = containerFor(palette.error, palette.surface)

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
 * - `0xFF4CAF50` in the old theme state holder;
 * - `0xFF66BB6A` in the old translucent panel tokens and in the orb backdrop.
 *
 * The latter two were effectively dead — every real path overwrote them from the
 * preference — which is exactly why they were free to rot, and why any new call
 * site reading the translucent tokens got a different green from every other surface.
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
     * Warmer green, a second user-selectable swatch.
     *
     * Named here so the settings palette and any future surface read the same
     * value instead of writing a fourth literal.
     */
    val LIME_ACCENT_ARG: Int = 0xFF7CB342.toInt()

    /**
     * Amber, the third user-selectable swatch.
     *
     * The brightest colour the app offers, and the reason
     * [com.trichome.app.ui.components.accentContentOn] exists: it is the accent
     * that a hardcoded white label would be unreadable on.
     */
    val AMBER_ACCENT_ARG: Int = 0xFFFFC107.toInt()

    val ORANGE_ACCENT_ARG: Int = 0xFFE65100.toInt()
    val CYAN_ACCENT_ARG: Int = 0xFF00ACC1.toInt()
    val VIOLET_ACCENT_ARG: Int = 0xFF7C4DFF.toInt()
    val PINK_ACCENT_ARG: Int = 0xFFEC407A.toInt()
    val RED_ACCENT_ARG: Int = 0xFFE53935.toInt()

    /**
     * Every accent the app offers, so the palette has one list.
     *
     * The Settings swatches read this same list, and a test asserts they agree,
     * so a new swatch cannot be added to the UI and forgotten here — which is
     * exactly how an unverified accent reaches a button and fails in the field
     * instead of on a build server.
     */
    val SELECTABLE_ARGB: List<Int> = listOf(
        DEFAULT_ACCENT_ARG, LIME_ACCENT_ARG, AMBER_ACCENT_ARG,
        ORANGE_ACCENT_ARG, CYAN_ACCENT_ARG, VIOLET_ACCENT_ARG,
        PINK_ACCENT_ARG, RED_ACCENT_ARG
    )
}

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
        }
    }

    fun selectColor(index: Int) {
        val safe = index.coerceIn(AppTheme.ALL.indices)
        selectedColorIndex = safe
        scope.launch { appContainer?.appearanceSettings?.setThemeIndex(safe) }
    }

    /** The accent is the single source of truth for the `primary` role. */
    fun updateAccentColor(color: Color) {
        val argb = color.toArgbInt()
        accentColor = color
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

    fun colorScheme(): ColorScheme = solidSchemeFor(theme, accentColor)

    fun typography(): Typography = buildTypography(fontScale, fontFamily, fontWeight)
}

/* ─────────────────────────── ARGB helpers ─────────────────────────────── */

/**
 * Converts a Compose [Color] to a packed 32-bit ARGB int.
 *
 * `Color.value` is a `ULong` that packs the colour in the **high** 32 bits and
 * the alpha and colour-space tag in the low 32, so masking the low half returns
 * the tag, not the colour: it made every accent round-trip to `0x00000000`.
 * That was not cosmetic. [TrichomeThemeState.updateAccentColor] persists this
 * value, so picking any swatch in Settings stored a zero and the next launch
 * read `Color(0)` — a fully transparent accent — and the Settings readout printed
 * `#00000000` for every user. The shift is the whole fix.
 */
fun Color.toArgbInt(): Int = ((value shr 32) and 0xFFFFFFFFUL).toInt()

/** Hex string for the accent picker readout, e.g. `FF2E7D32`. */
fun Color.toArgbHex(): String = toArgbInt().toUInt().toString(16).uppercase().padStart(8, '0')

/**
 * Applies the appearance state to the whole tree.
 *
 * The colour scheme and the typography are recomputed on every preference
 * change, so a theme, accent, typeface or size tweak is reflected immediately
 * without recreating the activity.
 */
@Composable
fun TrichomeTheme(
    themeState: TrichomeThemeState,
    content: @Composable () -> Unit
) {
    val scheme = themeState.colorScheme()
    val typography = themeState.typography()
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
        typography = typography,
        content = content
    )
}
