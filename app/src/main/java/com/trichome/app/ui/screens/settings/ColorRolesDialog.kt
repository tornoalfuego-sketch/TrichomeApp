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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

    // Which of the four stored picks are actually in effect. A refused pick is
    // still stored, so the colours alone cannot answer this, and everything that
    // reports on a role -- the summary, the readout, the swatch ring -- has to be
    // told or the dialog contradicts itself.
    //
    // The button role is deliberately absent. `solidSchemeFor` takes the button
    // pick as-is and only derives its label with `readableOnStrict`, so a fill
    // that fails the non-text bar is still painted; reporting it as dropped
    // would trade one false report for another.
    val rejectedPicks = RejectedPicks(
        primaryText = pickIsRejected(primaryText, textBackdrop, BODY_TEXT_CONTRAST),
        secondaryText = pickIsRejected(secondaryText, textBackdrop, BODY_TEXT_CONTRAST),
        tertiaryText = pickIsRejected(tertiaryText, textBackdrop, BODY_TEXT_CONTRAST)
    )

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

                // "What have I actually changed?" in one line, above the four
                // panels that each only speak for themselves.
                Text(
                    colorOverrideSummary(
                        primaryText, secondaryText, tertiaryText, buttonColor, rejectedPicks
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface
                )

                ColorRoleSection(
                    title = "Texto primario",
                    description = "Títulos, nombres y valores.",
                    previewText = "Amnesia Blue",
                    // Painted as a title on screen, so previewed as one: a
                    // secondary-sized sample would hide which level this is.
                    previewStyle = MaterialTheme.typography.titleMedium,
                    selected = primaryText,
                    currentRole = scheme.onSurface,
                    backdrop = textBackdrop,
                    minimumContrast = BODY_TEXT_CONTRAST,
                    dropsUnreadablePick = true,
                    onPick = onPickPrimaryText
                )

                ColorRoleSection(
                    title = "Texto secundario",
                    description = "Subtítulos, metadatos y descripciones.",
                    previewText = "Último ciclo · 12 días",
                    previewStyle = MaterialTheme.typography.bodyMedium,
                    selected = secondaryText,
                    currentRole = scheme.onSurfaceVariant,
                    backdrop = textBackdrop,
                    minimumContrast = BODY_TEXT_CONTRAST,
                    dropsUnreadablePick = true,
                    onPick = onPickSecondaryText
                )

                ColorRoleSection(
                    title = "Texto terciario",
                    description = "Contadores, rótulos y ayudas.",
                    previewText = "Riego cada 3 días",
                    previewStyle = MaterialTheme.typography.labelSmall,
                    selected = tertiaryText,
                    currentRole = tertiaryCurrent,
                    backdrop = textBackdrop,
                    minimumContrast = BODY_TEXT_CONTRAST,
                    dropsUnreadablePick = true,
                    onPick = onPickTertiaryText
                )

                ColorRoleSection(
                    title = "Color de los botones",
                    description = "Botones rellenos, la pestaña activa y los acentos.",
                    // A fill, not an ink: the sample has to sit on top of the
                    // colour it previews, exactly as a real button's label does.
                    previewText = "Guardar",
                    previewStyle = MaterialTheme.typography.labelLarge,
                    previewOnFill = true,
                    selected = buttonColor,
                    currentRole = scheme.primary,
                    // Buttons sit on the page, not on a card, and they are judged
                    // as non-text content: a fill only has to read as a shape.
                    backdrop = scheme.background,
                    minimumContrast = NON_TEXT_CONTRAST,
                    // Applied even when it fails that bar -- see the rejected
                    // picks above -- so it is never reported as dropped.
                    dropsUnreadablePick = false,
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

/**
 * True when a pick is stored but never reaches the screen.
 *
 * The preview shows the RESOLVED colour, so it shows the palette fallback
 * whenever this is true -- and the ring and the readout used to claim the
 * opposite in the same breath. One rule, one function: the swatch dot reports
 * the identical condition as its `warning` flag, so the two cannot drift.
 *
 * Same shape as `textOverrideOrNull` in the theme, which is what actually
 * decides it: a `null` pick is never rejected, it is simply the absence of one.
 */
fun pickIsRejected(pick: Color?, backdrop: Color, minimumContrast: Float): Boolean =
    pick != null && contrastRatio(pick, backdrop) < minimumContrast

/**
 * Which of the four roles hold a pick that was dropped at resolution time.
 *
 * A refused pick stays in the store, so the four colours cannot say on their own
 * which of them the app is honouring. This is that missing half, and it has to
 * reach the summary: naming a role as customised when its pick was refused is
 * the claim the user acted on and the app did not keep.
 *
 * @property button never set by this dialog. `solidSchemeFor` paints the button
 *   pick as-is and only derives its label, so a fill that fails the non-text
 *   bar is still in effect and saying otherwise would be a fresh lie.
 */
data class RejectedPicks(
    val primaryText: Boolean = false,
    val secondaryText: Boolean = false,
    val tertiaryText: Boolean = false,
    val button: Boolean = false
) {
    companion object {
        /** No role was refused, i.e. every stored pick is in effect. */
        val NONE = RejectedPicks()
    }
}

/**
 * The line under a swatch row: what colour this role is painting RIGHT NOW.
 *
 * Three states, because "the pick you made" and "the colour in use" are not the
 * same thing. Presenting a refused pick as the value in use -- and printing its
 * hex to prove it -- is how the dialog told the user a colour was set while the
 * app rendered another one, so the refused case names the hex of the colour
 * actually in use and says the pick was discarded.
 *
 * @param inUse the role's resolved colour, the one the preview just painted.
 */
fun roleReadout(selected: Color?, rejected: Boolean, inUse: Color): String = when {
    selected == null -> "Ahora: el color del tema"
    rejected ->
        "Descartado: #${selected.toArgbHex()} no se lee sobre esta superficie. " +
            "Se usa el del tema: #${inUse.toArgbHex()}."
    else -> "Ahora: #${selected.toArgbHex()}"
}

/**
 * One line naming which roles are customised and which are on the theme default.
 *
 * Four panels that each report a colour cannot answer "what did I actually
 * change?" without being read one by one, and the answer is the question this
 * dialog exists to raise. Split out of the composable so the rule is testable
 * without a device: it reads nothing but four nullable colours.
 *
 * A `null` override means the theme decides -- so the split is made on the stored
 * picks, never on the resolved colours: a role the theme happens to resolve to
 * the colour the user picked is still *customised*, and calling it a default
 * would be a lie.
 *
 * A pick named in [rejected] is the one exception, and it is not a nicety. The
 * store keeps a pick the app refused, so splitting on the picks alone counted it
 * as customised and the summary claimed a change that was never in effect. Such a
 * role reads as a default, exactly as it paints.
 */
fun colorOverrideSummary(
    primaryText: Color?,
    secondaryText: Color?,
    tertiaryText: Color?,
    buttonColor: Color?,
    rejected: RejectedPicks = RejectedPicks.NONE
): String {
    val roles = listOf(
        // Customised means "this role is painting a colour the user chose", which
        // is false both when nothing was picked and when the pick was refused.
        "Texto primario" to (primaryText != null && !rejected.primaryText),
        "Texto secundario" to (secondaryText != null && !rejected.secondaryText),
        "Texto terciario" to (tertiaryText != null && !rejected.tertiaryText),
        "Color de los botones" to (buttonColor != null && !rejected.button)
    )
    val customised = roles.filter { it.second }.map { it.first }
    val onTheme = roles.filter { !it.second }.map { it.first }
    if (customised.isEmpty()) {
        return "Ningún color personalizado: los cuatro usan el del tema."
    }
    if (onTheme.isEmpty()) {
        return "Personalizados los cuatro: ${joinInSpanish(customised)}."
    }
    val prefix = if (customised.size == 1) "Personalizado: " else "Personalizados: "
    return "$prefix${joinInSpanish(customised)}. Del tema: ${joinInSpanish(onTheme)}."
}

/**
 * `a`, `a y b`, `a, b y c` -- the Spanish list form, where only the last item
 * takes the conjunction.
 */
private fun joinInSpanish(items: List<String>): String = when {
    items.isEmpty() -> ""
    items.size == 1 -> items.single()
    else -> "${items.dropLast(1).joinToString(", ")} y ${items.last()}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRoleSection(
    title: String,
    description: String,
    previewText: String,
    previewStyle: TextStyle,
    previewOnFill: Boolean = false,
    selected: Color?,
    currentRole: Color,
    backdrop: Color,
    minimumContrast: Float,
    /**
     * Whether resolution drops a pick for this role that cannot clear
     * [minimumContrast]. True for the three text levels, false for the button
     * fill, which the app paints either way.
     */
    dropsUnreadablePick: Boolean,
    onPick: (Color?) -> Unit
) {
    // Decided once, from the same predicate the swatch dot reports as its
    // `warning` flag, so the ring, the readout and the summary cannot end up
    // telling three different stories about one pick.
    val rejected = dropsUnreadablePick && pickIsRejected(selected, backdrop, minimumContrast)

    SolidPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.padding(top = 8.dp))
            // Show, do not tell. The description says what the role paints; this
            // paints it, so the user can see which one owns the big text instead
            // of picking on faith.
            RolePreview(
                text = previewText,
                style = previewStyle,
                role = currentRole,
                backdrop = backdrop,
                onFill = previewOnFill
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
                        rejected = rejected && selected == color,
                        onClick = { onPick(color) }
                    )
                }
            }
            Box(Modifier.padding(top = 8.dp))
            Text(
                text = roleReadout(selected, rejected, currentRole),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One line of sample text in the colour this role actually paints with.
 *
 * @param role the role's RESOLVED colour, never the stored pick. The two differ
 *   whenever a pick was dropped for contrast or a level was derived from the
 *   primary, and a preview built from the pick would then show a colour the app
 *   is not using -- which is the same class of lie as the one this dialog was
 *   written to stop telling.
 * @param onFill true for a fill role: the sample sits on a block of [role], the
 *   way a button's label sits on the button, instead of being inked with it.
 */
@Composable
private fun RolePreview(
    text: String,
    style: TextStyle,
    role: Color,
    backdrop: Color,
    onFill: Boolean
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backdrop)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (onFill) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(role)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = text,
                    style = style,
                    // The label on a fill is resolved from the fill, not from the
                    // theme, exactly as `solidSchemeFor` builds `onPrimary`.
                    color = readableOnStrict(role),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = text,
                style = style,
                color = role,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    /**
     * This is the pick the user made and the app refused.
     *
     * Distinct from [selected], and the distinction is the point: the accent ring
     * is the dialog's promise that the colour is in use, and painting it on a
     * refused pick is one of the three contradictions this file used to show at
     * once. It still gets a ring of its own, so the choice remains visible, in
     * the scheme's error colour so nothing reads it as "applied".
     */
    rejected: Boolean = false,
    onClick: () -> Unit
) {
    val ringWidth: Dp
    val ringColor: Color
    when {
        rejected -> {
            ringWidth = 2.dp
            ringColor = MaterialTheme.colorScheme.error
        }
        selected -> {
            ringWidth = 3.dp
            ringColor = MaterialTheme.colorScheme.primary
        }
        else -> {
            ringWidth = 1.dp
            ringColor = MaterialTheme.colorScheme.outline
        }
    }

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
                    width = ringWidth,
                    color = ringColor,
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
