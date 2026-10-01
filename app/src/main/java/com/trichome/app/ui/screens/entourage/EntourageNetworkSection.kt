package com.trichome.app.ui.screens.entourage

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trichome.app.data.repository.EntourageContent
import com.trichome.app.model.*
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * T8.1 — the synergy network.
 *
 * Two things on screen: the card for the **combination the user has selected**,
 * and the library filtered to a terpene. Both come from the domain layer —
 * [EntouragePlanner.matchedSynergy] decides which synergy describes a selection,
 * [EntourageFilters] decides the filter — so there is no second `when` over the
 * compounds anywhere in the UI that could disagree with the planner.
 *
 * The card is [EntourageSynergyCardPanel], which renders the evidence line in
 * the card body. That is not a stylistic choice: [EntourageSynergy.evidenceEs]
 * exists precisely so "no human evidence" travels with the claim, and a
 * disclosure would put it one interaction away from a user who never asks.
 *
 * The compound selection is shared with the booster on purpose. One selection,
 * two readings of it — the network explains it, the booster measures it — beats
 * two selectors that can hold different answers and make the module contradict
 * itself.
 *
 * ## No scroll of its own
 *
 * A plain [Column], mounted as one item of the module's single
 * [androidx.compose.foundation.lazy.LazyColumn]. It deliberately does not
 * scroll: a vertical scroll nested in a scrolling parent is measured with an
 * infinite maximum height and throws at runtime, which is the crash
 * `ScrollOwnershipTest` exists for and which no compiler or lint check reports.
 */
@Composable
fun EntourageNetworkSection(
    library: EntourageContent,
    filterKey: String,
    onFilter: (String) -> Unit,
    cannabinoids: Set<Cannabinoid>,
    terpenes: Set<EntourageTerpene>,
    onCannabinoids: (Set<Cannabinoid>) -> Unit,
    onTerpenes: (Set<EntourageTerpene>) -> Unit,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    val tertiary = LocalTertiaryText.current
    val filterTerpene = EntourageFilters.terpeneForKey(filterKey)
    val shown = remember(library.synergies, filterTerpene) {
        EntourageFilters.synergiesForTerpene(library.synergies, filterTerpene)
    }
    val selection = remember(cannabinoids, terpenes) {
        EntourageSelection(cannabinoids = cannabinoids, terpenes = terpenes)
    }
    val matched = remember(selection, library.synergies) {
        EntouragePlanner.matchedSynergy(selection, library.synergies)
    }

    // (route key, visible label), with "all of them" as the first entry so the
    // filter always has a way out.
    val filterOptions: List<Pair<String?, String>> = remember(library.synergies) {
        buildList {
            add(null to "Todas")
            EntourageFilters.filterableTerpenes(library.synergies)
                .forEach { add(it.key to it.labelEs) }
        }
    }
    val activeFilterKey = filterKey.takeIf { it.isNotBlank() }
    val activeFilterOption = filterOptions.firstOrNull { it.first == activeFilterKey }
        ?: filterOptions.first()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntourageSectionHeading("Tu combinación", themeState)

        Text(
            "Elegí cannabinoides y terpenos. La biblioteca busca la sinergia que mejor " +
                "describe esa combinación y la muestra con su mecanismo y su nivel de " +
                "evidencia.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

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
            // Volatility order, so the most fragile compounds come first and the
            // list cannot drift away from the shipped boiling points.
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

        if (selection.isEmpty) {
            SolidPanel(contentColor = scheme.onSurface) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Sin combinación seleccionada",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Seleccioná al menos un cannabinoid y un terpeno de la " +
                            "biblioteca para que pueda describir la sinergia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        } else if (matched == null) {
            // "The catalog does not document this" is a fact about the catalog,
            // and saying it that way keeps a well-formed combination from
            // reading as a wrong one.
            SolidPanel(contentColor = scheme.onSurface) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Ninguna sinergia de la biblioteca describe esta combinación",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "La biblioteca documenta ${library.synergies.size} combinaciones. " +
                            "Probá con otra combinación o agregá más terpenos de la lista.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val card = remember(matched) { EntourageCards.cardFor(matched) }
            EntourageSynergyCardPanel(card, themeState)

            if (matched.profiles.isNotEmpty()) {
                Text(
                    "Perfil que describe: " +
                        matched.profiles.sortedBy { it.key }.joinToString(" · ") { it.labelEs },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        EntourageSectionHeading("Biblioteca de sinergias", themeState)

        EntourageChipFlow(
            options = filterOptions,
            selected = setOf(activeFilterOption),
            labelOf = { it.second },
            onToggle = { option -> onFilter(option.first.orEmpty()) }
        )

        Text(
            buildString {
                append("${shown.size} de ${library.synergies.size} sinergias")
                filterTerpene?.let { append(" · filtradas por ${it.labelEs}") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = tertiary
        )

        if (shown.isEmpty()) {
            // Empty means empty. Rendering the whole library under an active
            // filter would be the same defect class as a silently truncated one:
            // the screen would look complete and would not be.
            SolidPanel(contentColor = scheme.onSurface) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Sinergias para este filtro",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "La biblioteca no documenta combinaciones con " +
                            "${filterTerpene?.labelEs ?: "este terpeno"}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        } else {
            shown.forEach { synergy ->
                EntourageSynergyCardPanel(
                    card = remember(synergy) { EntourageCards.cardFor(synergy) },
                    themeState = themeState
                )
            }
        }

        Text(
            "Cada ficha incluye su propio nivel de evidencia. Varias de estas " +
                "combinaciones no tienen ensayos humanos, y la ficha lo dice.",
            style = MaterialTheme.typography.labelSmall,
            color = tertiary
        )
    }
}
