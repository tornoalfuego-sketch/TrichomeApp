package com.trichome.app.ui.screens.entourage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.model.EntourageCardRole
import com.trichome.app.model.EntourageIntegrityNotice
import com.trichome.app.model.EntourageSynergyCard
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.SelectableChip
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * Pieces shared by the four Séquito sections.
 *
 * Nothing here scrolls. The module has exactly one scroll owner — the
 * [androidx.compose.foundation.lazy.LazyColumn] in [EntourageModuleScreen] —
 * and these are the leaves mounted inside it, which is the shape
 * `EntourageScrollOwnershipTest` holds. A vertical scroll introduced in one of
 * them would be measured with an infinite maximum height by the parent
 * `LazyColumn` and throw at runtime, the same way the breeding theory tab did.
 *
 * Colours all come from `themeState.colorScheme()` or [LocalTertiaryText]; no
 * literal appears here, and no [Color.Unspecified] is ever handed to a
 * `Surface`.
 */

/* ── Module-level honesty ───────────────────────────────────────────────── */

/**
 * The module disclaimer, on a filled panel rather than a line of small text.
 *
 * It is rendered as the first thing under the tab bar on **every** tab, and it
 * is never collapsed, truncated behind a disclosure, or moved to a footer: the
 * claims in this module are pharmacology, and a health-adjacent screen whose
 * limits are one scroll away is not stating them.
 */
@Composable
fun EntourageDisclaimerPanel(text: String, themeState: TrichomeThemeState) {
    val scheme = themeState.colorScheme()
    val tertiary = LocalTertiaryText.current
    SolidPanel {
        Column(Modifier.padding(14.dp)) {
            Text(
                "⚠️ Antes de leer el módulo",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Contenido informativo. No es consejo médico ni sustituye a un profesional.",
                style = MaterialTheme.typography.labelSmall,
                color = tertiary
            )
        }
    }
}

/**
 * What the module admits about its own content.
 *
 * [notice] is null exactly when every key in the asset resolved, so this renders
 * nothing in the healthy case and cannot be mistaken for decoration. When it is
 * not null the detail is printed verbatim, because a count is not something the
 * user can act on.
 */
@Composable
fun EntourageIntegrityPanel(notice: EntourageIntegrityNotice?) {
    if (notice == null) return
    val scheme = MaterialTheme.colorScheme
    // `errorContainer` is opaque and paired with `onErrorContainer` by
    // `solidSchemeFor`, so this reads on every theme and on a user-chosen accent.
    Surface(
        color = scheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "⚠️ ${notice.headlineEs}",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onErrorContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                notice.detailEs,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onErrorContainer
            )
        }
    }
}

/* ── Synergy card ───────────────────────────────────────────────────────── */

/**
 * A synergy card, with the evidence line in the card body.
 *
 * The evidence block is a filled [Surface] and not a disclosure, a tooltip or a
 * footnote: the module's own `EntourageSynergy.evidenceEs` KDoc says the claim's
 * limits belong next to the claim, and the shipped content carries lines like
 * "no hay evidencia humana" that the user has to be able to read without asking
 * for them. `EntourageSynergyCard.linesEs` always contains that entry — see
 * [EntourageCardRole.EVIDENCE] — so a card can never render without it.
 */
@Composable
fun EntourageSynergyCardPanel(card: EntourageSynergyCard, themeState: TrichomeThemeState) {
    val scheme = themeState.colorScheme()
    val tertiary = LocalTertiaryText.current

    SolidPanel(contentColor = scheme.onSurface) {
        Column(Modifier.padding(16.dp)) {
            Text(
                card.outcomeEs,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                card.compoundsEs,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary
            )

            Spacer(Modifier.height(12.dp))

            card.linesEs.forEach { line ->
                when (line.role) {
                    EntourageCardRole.EVIDENCE -> {
                        // The evidence block: filled, in the card, never behind a
                        // tap. Sized and coloured as its own block so it cannot
                        // be read as another caption.
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = scheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "🔬 ${line.labelEs}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    line.bodyEs,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface
                                )
                            }
                        }
                    }

                    EntourageCardRole.AGRONOMY -> {
                        // F3. The agronomy block sits in the same list as the
                        // evidence line and is drawn by the same unconditional
                        // `forEach`, so it cannot be skipped and it is not behind
                        // a disclosure either. It gets its own branch only so it
                        // reads as a secondary block rather than another claim
                        // about the combination — the body's `Base (…)` clause
                        // carries each lever's evidence level in visible text.
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                line.labelEs,
                                style = MaterialTheme.typography.labelLarge,
                                color = tertiary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                line.bodyEs,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface
                            )
                        }
                    }

                    else -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            line.labelEs,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            line.bodyEs,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface
                        )
                    }
                }
            }

            if (!card.evidenceWasDeclared) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Esta sinergia no trae evidencia declarada en el catálogo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiary
                )
            }
        }
    }
}

/* ── Chips ──────────────────────────────────────────────────────────────── */

/**
 * A wrapping grid of toggle chips.
 *
 * A `FlowRow` rather than a `LazyRow` on purpose: a chip rail that scrolls
 * sideways hides half its options with no affordance, and the compound list here
 * is short enough to wrap. It also keeps the module's single scroll owner on one
 * axis — a horizontal list would be a second owner, and a horizontal one
 * *inside* a vertical one is the shape that has to stay out of the tree.
 *
 * @param accentFor an optional per-chip accent, so cannabinoids and terpenes can
 *   be told apart. Null falls back to the theme's `primary`, which is what
 *   [SelectableChip] does on its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> EntourageChipFlow(
    options: List<T>,
    selected: Set<T>,
    labelOf: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentFor: ((T) -> Color?)? = null
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            SelectableChip(
                text = labelOf(option),
                selected = option in selected,
                onClick = { onToggle(option) },
                accentColor = accentFor?.invoke(option)
            )
        }
    }
}

/** A section heading inside the module's single list. */
@Composable
fun EntourageSectionHeading(text: String, themeState: TrichomeThemeState) {
    val tertiary = LocalTertiaryText.current
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = tertiary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** Body copy in a panel, with the label the content shipped above it. */
@Composable
fun EntourageLabelledBlock(labelEs: String, bodyEs: String, themeState: TrichomeThemeState) {
    val scheme = themeState.colorScheme()
    Column(Modifier.fillMaxWidth()) {
        Text(labelEs, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(bodyEs, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
    }
}
