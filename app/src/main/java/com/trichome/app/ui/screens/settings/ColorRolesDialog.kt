package com.trichome.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.AppTheme
import com.trichome.app.ui.theme.SolidPalettes
import com.trichome.app.ui.theme.contrastRatio
import com.trichome.app.ui.theme.readableOnStrict
import com.trichome.app.ui.theme.toArgbHex

/**
 * The four colours the user can override, app-wide.
 *
 * They are not screen settings. [com.trichome.app.ui.theme.solidSchemeFor] is
 * the only place a `ColorScheme` is ever built and `TrichomeTheme` publishes
 * that same instance into `MaterialTheme`, so a pick here reaches every screen
 * that reads a scheme — 25 files — without one of them being told about it.
 *
 * The third level needed a channel of its own. Material 3 has exactly 36 scheme
 * roles and none of them is a muted text ink, and seventeen call sites used to
 * fake it with `onSurface.copy(alpha = 0.5f..0.8f)`, so a "tertiary text" setting
 * had nothing to override. That role now exists and this dialog drives it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRolesDialog(
    theme: AppTheme,
    scheme: ColorScheme,
    /**
     * The third level's current value.
     *
     * Passed in rather than read from [scheme] because Material 3 has no role
     * for it; it is resolved by the theme and carried alongside the scheme.
     */
    tertiaryCurrent: Color,
    primaryText: Color?,
    secondaryText: Color?,
    tertiaryText: Color?,
    buttonColor: Color?,
    onPickPrimaryText: (Color?) -> Unit,
    onPickSecondaryText: (Color?) -> Unit,
    onPickTertiaryText: (Color?) -> Unit,
    onPickButton: (Color?) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    // A pick is validated against the surface it will actually be read on. The
    // palette decides what that is, so a colour that reads on the dark themes can
    // still be unreadable on the light one -- and the swatch is marked here
    // before the user taps it, rather than after the text disappears.
    val palette = SolidPalettes.forTheme(theme)
    val textBackdrop = palette.surface

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colores de la app") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Estos colores se aplican a toda la aplicación. " +
                        "El punto marca los que no se pueden leer sobre esta superficie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )

                ColorRoleSection(
                    title = "Texto primario",
                    description = "Títulos, nombres y valores.",
                    selected = primaryText,
                    currentRole = scheme.onSurface,
                    backdrop = textBackdrop,
                    minimumContrast = BODY_TEXT_CONTRAST,
                    onPick = onPickPrimaryText
                )

                ColorRoleSection(
                    title = "Texto secundario",
                    description = "Subtítulos, metadatos y descripciones.",
                    selected = secondaryText,
                    currentRole = scheme.onSurfaceVariant,
                    backdrop = textBackdrop,
                    minimumContrast = BODY_TEXT_CONTRAST,
                    onPick = onPickSecondaryText
                )

                ColorRoleSection(
                    title = "Texto terciario",
                    description = "Contadores, rótulos y ayudas.",
                    selected = tertiaryText,
                    currentRole = tertiaryCurrent,
                    backdrop = textBackdrop,
                    minimumContrast = BODY_TEXT_CONTRAST,
                    onPick = onPickTertiaryText
                )

                ColorRoleSection(
                    title = "Color de los botones",
                    description = "Botones rellenos, la pestaña activa y los acentos.",
                    selected = buttonColor,
                    currentRole = scheme.primary,
                    // Buttons sit on the page, not on a card, and they are judged
                    // as non-text content: a fill only has to read as a shape.
                    backdrop = scheme.background,
                    minimumContrast = NON_TEXT_CONTRAST,
                    onPick = onPickButton
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClearAll()
                    onDismiss()
                }
            ) { Text("Restablecer") }
        }
    )
}

/** WCAG 2.1 AA for body text. */
private const val BODY_TEXT_CONTRAST = 4.5f

/** WCAG 2.1 AA for non-text UI parts such as a control fill. */
private const val NON_TEXT_CONTRAST = 3.0f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRoleSection(
    title: String,
    description: String,
    selected: Color?,
    currentRole: Color,
    backdrop: Color,
    minimumContrast: Float,
    onPick: (Color?) -> Unit
) {
    SolidPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.padding(top = 10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 5
            ) {
                // "Del tema" is first so the default is one tap away rather than
                // something to hunt for after a bad pick.
                ThemeSwatch(
                    label = "Del tema",
                    preview = currentRole,
                    selected = selected == null,
                    onClick = { onPick(null) }
                )
                AccentSwatches.forEach { (name, color) ->
                    ColorSwatch(
                        label = name,
                        color = color,
                        selected = selected == color,
                        warning = contrastRatio(color, backdrop) < minimumContrast,
                        onClick = { onPick(color) }
                    )
                }
            }
            Box(Modifier.padding(top = 8.dp))
            Text(
                text = if (selected == null) {
                    "Ahora: el color del tema"
                } else {
                    "Ahora: #${selected.toArgbHex()}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The swatch for "use whatever the theme says".
 *
 * It previews the role's current value rather than drawing the letter "A", so it
 * shows what clearing the override will actually look like — which is a
 * different colour for every one of the four sections.
 */
@Composable
private fun ThemeSwatch(
    label: String,
    preview: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(preview, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // The ink that will be painted on top of it, resolved rather than
            // hardcoded, so the preview is the real pairing.
            Text(
                "A",
                style = MaterialTheme.typography.labelSmall,
                color = readableOnStrict(preview)
            )
        }
        SwatchLabel(label)
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    warning: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // A dot marks a colour that will not carry this role's content, so
            // the user is allowed the choice but not left discovering it.
            if (warning) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(readableOnStrict(color), CircleShape)
                )
            }
        }
        SwatchLabel(label)
    }
}

/**
 * A one-line label so the names line up under their circles and ellipsize
 * instead of being clipped mid-word.
 */
@Composable
private fun SwatchLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 2.dp)
    )
}
