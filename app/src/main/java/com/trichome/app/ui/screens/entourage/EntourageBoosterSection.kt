package com.trichome.app.ui.screens.entourage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.data.repository.EntourageContent
import com.trichome.app.model.*
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * T8.2 — the booster.
 *
 * Pick a target profile, and the planner reports how far the selected terpene
 * set sits from that one profile's proportions.
 *
 * ## What the percentage is, on screen
 *
 * A match to a **profile's proportions**, never a quality verdict. The planner's
 * KDoc is explicit that a low number is a distance and not a judgement about the
 * product, and the same selection is a fine answer to a different target — so
 * the number is always rendered with its heading, its band and
 * [EntourageBooster.CAVEAT_ES] attached. A bare "35%" with no frame reads as a
 * grade, and that is the exact failure this screen exists to avoid. Every
 * string here comes from [EntourageBooster.frame], which is where the wording
 * lives and where the language guard is asserted.
 *
 * What is deliberately absent: any cannabinoid contribution to the percentage.
 * [EntourageProfile.cannabinoidWeights] is loaded and validated but never
 * scored, so a cannabinoid appears here only as a *presence* note ("this profile
 * does not include CBD") and never as a share of a number.
 *
 * A plain [Column]: the module's single `LazyColumn` owns the scroll. See
 * [EntourageNetworkSection] for why a nested vertical scroll is not an option.
 */
@Composable
fun EntourageBoosterSection(
    library: EntourageContent,
    target: PharmacologicalProfile?,
    onTarget: (PharmacologicalProfile) -> Unit,
    cannabinoids: Set<Cannabinoid>,
    terpenes: Set<EntourageTerpene>,
    onCannabinoids: (Set<Cannabinoid>) -> Unit,
    onTerpenes: (Set<EntourageTerpene>) -> Unit,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    val tertiary = LocalTertiaryText.current

    val available = remember(library.profiles) {
        // The asset's own profiles, falling back to the enum's labels when the
        // catalog ships none: the picker has to be selectable before the
        // proportions are known, and an unanswerable screen is not an option.
        if (library.profiles.isEmpty()) {
            PharmacologicalProfile.entries.map { key ->
                EntourageProfile(
                    key = key,
                    labelEs = key.labelEs,
                    descriptionEs = "",
                    cannabinoidWeights = emptyMap(),
                    terpeneShares = emptyMap(),
                    noteEs = ""
                )
            }
        } else {
            library.profiles
        }
    }

    val profile = remember(target, available) {
        available.firstOrNull { it.key == target } ?: available.firstOrNull()
    }
    val selection = remember(cannabinoids, terpenes) {
        EntourageSelection(cannabinoids = cannabinoids, terpenes = terpenes)
    }
    val plan = remember(selection, profile, library.synergies) {
        EntouragePlanner.plan(selection, profile, library.synergies)
    }
    val frame = remember(plan, profile) { EntourageBooster.frame(plan, profile) }
    val vapour = remember(terpenes, library.vaporisation) {
        EntourageBooster.vapourReport(terpenes, library.vaporisation)
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntourageSectionHeading("Objetivo del perfil", themeState)

        Text(
            "El perfil objetivo son proporciones de terpenos, no una dosis ni una " +
                "indicación médica. El porcentaje se mide contra ese único perfil.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

        EntourageChipFlow(
            options = available.map { it.key },
            selected = setOfNotNull(profile?.key),
            labelOf = { "${it.icon} ${it.labelEs}" },
            onToggle = onTarget
        )

        if (profile != null) {
            if (profile.descriptionEs.isNotBlank()) {
                EntourageLabelledBlock("Qué propone", profile.descriptionEs, themeState)
            }

            EntourageSectionHeading("Tu selección", themeState)

            EntourageSectionHeading("Cannabinoides", themeState)
            EntourageChipFlow(
                options = Cannabinoid.entries,
                selected = cannabinoids,
                labelOf = { it.labelEs },
                onToggle = { candidate ->
                    onCannabinoids(
                        if (candidate in cannabinoids) cannabinoids - candidate
                        else cannabinoids + candidate
                    )
                }
            )

            EntourageSectionHeading("Terpenos", themeState)
            EntourageChipFlow(
                options = EntouragePlanner.byVolatility(library.vaporisation),
                selected = terpenes,
                labelOf = { it.labelEs },
                onToggle = { candidate ->
                    onTerpenes(
                        if (candidate in terpenes) terpenes - candidate
                        else terpenes + candidate
                    )
                }
            )

            // The score, its framing and the caveat are one block. Splitting them
            // would let the number be read on its own, which is the reading the
            // caveat exists to prevent.
            SolidPanel(contentColor = scheme.onSurface) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        frame.headingEs,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    if (frame.reportable) {
                        Text(
                            "${frame.percent}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Text(
                            frame.bandEs,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface
                        )
                        Text(
                            "Basado en ${frame.compoundsCompared} compuestos comparados.",
                            style = MaterialTheme.typography.labelSmall,
                            color = tertiary
                        )
                    } else {
                        Text(
                            frame.bandEs,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        frame.guidanceEs,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )

                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = scheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            frame.caveatEs,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    if (profile.noteEs.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            profile.noteEs,
                            style = MaterialTheme.typography.labelSmall,
                            color = tertiary
                        )
                    }
                }
            }

            if (frame.gapLinesEs.isNotEmpty()) {
                EntourageSectionHeading("Lo que el perfil pide y no está", themeState)
                frame.gapLinesEs.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                }
            }

            if (frame.offTargetLinesEs.isNotEmpty()) {
                EntourageSectionHeading("Fuera del perfil", themeState)
                frame.offTargetLinesEs.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (frame.missingCannabinoidLinesEs.isNotEmpty()) {
                EntourageSectionHeading("Compuestos que el perfil no usa", themeState)
                frame.missingCannabinoidLinesEs.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            frame.interactionWarningsEs.forEach { warning ->
                Surface(
                    color = scheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "⚠️ Interacciones",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            warning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            EntourageSectionHeading("Temperatura de vaporización", themeState)

            Text(
                "Cada terpeno tiene su propia ventana. Llevar un monoterpeno a la " +
                    "ventana de un sesquiterpeno lo destruye antes de que el otro aparezca.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )

            if (vapour.rows.isEmpty()) {
                Text(
                    vapour.missingEs,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            } else {
                SolidPanel(contentColor = scheme.onSurface) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Compuestos elegidos",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        vapour.rows.forEach { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.terpeneLabelEs,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = scheme.onSurface
                                    )
                                    Text(
                                        "${row.familyEs} · ebullición ${row.boilingEs}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tertiary
                                    )
                                }
                                Text(
                                    row.windowEs,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.primary
                                )
                            }
                            if (row.noteEs.isNotBlank()) {
                                Text(
                                    row.noteEs,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // The single window and the contradiction it can carry. A
                // selection whose compounds cannot share one pass is reported as
                // such: averaging the two temperatures would produce a number
                // that reads as advice and preserves nothing.
                SolidPanel(contentColor = scheme.onSurface) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Ventana conjunta",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        if (vapour.contradictionEs.isNotBlank()) {
                            Text(
                                vapour.contradictionEs,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.error
                            )
                        } else {
                            Text(
                                vapour.windowEs,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface
                            )
                        }

                        // F2: the same contradiction as rungs. The boolean above
                        // says that the selection does not fit in one pass; this
                        // says at what temperature each compound arrives and what
                        // is already gone by then, which is the part the user can
                        // act on.
                        if (vapour.stageLinesEs.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Curva de calor",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Orden de salida de los compuestos elegidos, del más " +
                                    "volátil al menos volátil.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            vapour.stageLinesEs.forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface
                                )
                            }
                            if (vapour.derivedWarningEs.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    vapour.derivedWarningEs,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
