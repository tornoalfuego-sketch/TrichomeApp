package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import com.trichome.app.ui.components.accentTextButtonColors
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.repository.Terpene
import com.trichome.app.model.TerpeneProgression
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.accentLabelOn
import com.trichome.app.ui.components.SelectableChip
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.screens.entourage.entourageRoute
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.TerpenesViewModel
import com.trichome.app.viewmodel.appViewModel
import kotlinx.coroutines.launch

/**
 * Terpenes encyclopedia.
 *
 * The list is a [LazyColumn]: with 150+ entries the previous implementation
 * composed every card eagerly inside a `verticalScroll` Column, which froze the
 * UI on every filter change. Filters (family / effect / aroma / favourites) and
 * the progression header are driven by [TerpenesViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerpenesScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { TerpenesViewModel(it) }
    val scope = rememberCoroutineScope()
    val scheme = themeState.colorScheme()
    val accent = scheme.primary


    var showQuiz by remember { mutableStateOf(false) }
    var showBadges by remember { mutableStateOf(false) }
    var showBlender by remember { mutableStateOf(false) }

    // F11: the XP total and badge names the blender's level history read. Loaded
    // once here rather than when the dialog opens, because a total that only
    // refreshes when a player looks at it is a total the progression card above
    // would contradict.
    LaunchedEffect(Unit) { vm.loadBlenderProgress() }

    // The header, the progression card and the filter rows are siblings of the
    // list, not items in it, so scrolling the LazyColumn never moves them. The
    // trigger is derived from the list's own scroll position instead.
    //
    // The threshold is a *distance* rather than a boolean the list flips: a small
    // scroll back up brings the filters in again, and a fling that overshoots
    // does not leave them stuck half-hidden.
    val listState = rememberLazyListState()
    val headersCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > HEADER_COLLAPSE_PX
        }
    }

    // A new search reopens the filters and returns to the top: a new query means
    // a new narrowing step, and the filters are how that step is taken.
    LaunchedEffect(vm.query) {
        if (vm.query.isNotEmpty()) listState.scrollToItem(0)
    }

    Scaffold(
        bottomBar = {
            MainBottomBar("terpenes", { navController.navigate(it) }, themeState)
        }    ) { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        /* ── Cabecera ─────────────────────────────────────────────── */
        // The title row collapses first and is the only place the colour
        // sections can be reopened from, so it is never removed outright.
        AnimatedVisibility(
            visible = !headersCollapsed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Column(Modifier.weight(1f)) {
                    // Four header actions plus a back arrow leave roughly 190dp
                    // for the title, and "Biblia de Terpenos" wrapped onto three
                    // lines at titleLarge. One line, slightly smaller, is the
                    // difference between a title and a stack of words.
                    Text(
                        "📖 Biblia de Terpenos",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${vm.terpenes.size} compuestos · ${vm.discovered.size} descubiertos",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Header icons carry the accent only where it stays legible; on a
                // light theme with a pale accent the glyph would otherwise sink
                // into the surface. The fallback is the theme's own onSurface.
                val iconTint = accentLabelOn(MaterialTheme.colorScheme, accent)
                IconButton(onClick = { showBlender = true }) {
                    Icon(Icons.Filled.Science, contentDescription = "Master Blender", tint = iconTint)
                }
                IconButton(onClick = { showBadges = true }) {
                    Icon(Icons.Filled.Star, contentDescription = "Medallas", tint = iconTint)
                }
                IconButton(onClick = { showQuiz = true }) {
                    Icon(Icons.Filled.Quiz, contentDescription = "Trivia", tint = iconTint)
                }
            }
        }

        /* ── Progresión ───────────────────────────────────────────── */
        // The progression card is the first thing to go when the user starts
        // scrolling: it is a badge, not a control, and it was eating a third of
        // the screen on a 2340px-tall device. The header, the search field and
        // the filters collapse progressively for the same reason -- searching
        // through 158 compounds is impossible when half the viewport is menus.
        AnimatedVisibility(
            visible = !headersCollapsed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ProgressionCard(
                level = vm.level,
                rank = vm.rankTitle,
                progress = vm.levelProgress,
                xp = vm.xp,
                streak = vm.streak,
                discovered = vm.discovered.size,
                total = vm.terpenes.size,
                themeState = themeState
            )
        }

        /* ── Buscador ────────────────────────────────────────────── */
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::updateQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("Buscar por nombre, efecto, aroma o cepa") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.updateQuery("") }) {
                        Icon(Icons.Filled.FilterAltOff, contentDescription = "Limpiar búsqueda")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )

        /* ── Filtros ─────────────────────────────────────────────── */
        AnimatedVisibility(
            visible = !headersCollapsed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                FilterRow(
                    label = "Familia",
                    options = vm.families,
                    selected = vm.familyFilter,
                    onToggle = vm::toggleFamilyFilter
                )
                FilterRow(
                    label = "Efecto",
                    options = vm.effectGroups,
                    selected = vm.effectFilter,
                    onToggle = vm::toggleEffectFilter
                )
                FilterRow(
                    label = "Aroma",
                    options = vm.aromaFamilies,
                    selected = vm.aromaFilter,
                    onToggle = vm::toggleAromaFilter
                )
            }
        }

        // A way back once the filters are gone, otherwise collapsing them is a
        // one-way trip: the user would have to scroll to the very top to change
        // a filter with no idea it had moved.
        AnimatedVisibility(
            visible = headersCollapsed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            TextButton(
                // headersCollapsed is derived from the list position, so the way
                // back is to scroll up rather than to flip a flag: scrolling to
                // the first item is the same gesture the user would use anyway.
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = accentTextButtonColors(scheme, accent)
            ) {
                Text("▾ Mostrar progresión y filtros", maxLines = 1)
            }
        }

        /* ── Acceso al módulo Séquito ────────────────────────────── */
        // Outside the collapsing header on purpose. The header exists to get out
        // of the way of a search across 158 compounds, and the Séquito entry is
        // not part of that search: a user who scrolled deep into the list and
        // wants the synergy network must not have to scroll all the way back up
        // to find it. One row, always present, above the filters.
        EntourageEntryCard(
            onClick = { navController.navigate(entourageRoute()) },
            themeState = themeState
        )

        /* ── Lista ───────────────────────────────────────────────── */
        if (vm.terpenes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Ningún terpeno coincide con los filtros.",
                    color = scheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(vm.terpenes, key = { it.id }) { terpene ->
                    TerpeneRow(
                        terpene = terpene,
                        discovered = terpene.id in vm.discovered,
                        themeState = themeState,
                        onClick = { navController.navigate("terpene/${terpene.id}") },
                        onToggleFavorite = { vm.toggleFavorite(terpene) }
                    )
                }
            }
        }
    }
    }

        if (showQuiz) {
            TerpeneQuizDialog(
                pool = vm.terpenes.ifEmpty { emptyList() },
                onDismiss = { showQuiz = false },
                onAnswer = vm::recordQuiz,
                onCompleted = vm::recordQuizCompleted
            )
        }

        if (showBadges) {
            BadgesDialog(badges = vm.badges, onDismiss = { showBadges = false })
        }

        if (showBlender) {
            MasterBlenderDialog(
                catalog = vm.terpenes,
                themeState = themeState,
                // F11: read from the same `achievements` rows the app sums its
                // own total from, so the level the blender shows and the level
                // the progression card above shows are one number.
                totalXp = vm.blenderTotalXp,
                awardedNames = vm.blenderAwardedNames,
                onRecord = vm::recordBlend,
                onDismiss = { showBlender = false }
            )
        }
}

@Composable
private fun ProgressionCard(
    level: Int,
    rank: String,
    progress: Float,
    xp: Int,
    streak: Int,
    discovered: Int,
    total: Int,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    SolidPanel(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        contentColor = scheme.onSurface
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(scheme.primary, scheme.tertiary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$level",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(rank, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$xp XP · ${TerpeneProgression.xpToNextLevel(xp)} XP para el nivel ${level + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        // The secondary line keeps the theme's variant tone: the
                        // custom ink is for the headline and the body, and two
                        // competing user colours in one card is noise.
                        color = scheme.onSurfaceVariant
                    )
                }
                if (streak > 0) {
                    Text(
                        "🔥 $streak",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.tertiary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = scheme.primary,
                trackColor = scheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Descubiertos $discovered de $total · Toca una tarjeta para leer la ficha completa",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The one always-visible door into the Séquito module.
 *
 * A full-width row rather than a fifth header icon: the title row already spends
 * its width on the title and three actions, and an icon with no label reads as
 * another encyclopedia tool rather than as the cannabinoid-side module it is.
 * The subtitle carries the state the module works in — it explains cannabinoid
 * x terpene combinations, it does not score the flower — so the entry does not
 * promise a number the module will not give.
 */
@Composable
private fun EntourageEntryCard(onClick: () -> Unit, themeState: TrichomeThemeState) {
    val scheme = themeState.colorScheme()
    SolidPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        contentColor = scheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🧬", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Efecto Séquito",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface
                )
                Text(
                    "Sinergias entre terpenos y cannabinoides",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Text("→", color = scheme.primary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    onToggle: (String) -> Unit
) {
    if (options.isEmpty()) return
    val scheme = themeStateScheme()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                // A row header, not body text: it keeps the theme's dimmer tone
                // rather than the custom reading colour, which is reserved for
                // the compound text itself.
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        items(options) { option ->
            SelectableChip(
                text = option,
                selected = selected == option,
                onClick = { onToggle(option) }
            )
        }
    }
}

/** Local helper so [FilterRow] does not need the full theme state passed down. */
@Composable
private fun themeStateScheme() = MaterialTheme.colorScheme

@Composable
private fun TerpeneRow(
    terpene: Terpene,
    discovered: Boolean,
    themeState: TrichomeThemeState,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val scheme = themeState.colorScheme()
    SolidPanel(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        terpene.name,
                        style = MaterialTheme.typography.titleSmall,
                        // An undiscovered compound keeps the dimmer tone, so the
                        // custom colour brightens the names without flattening the
                        // difference between found and unfound.
                        color = if (discovered) Color.Unspecified else scheme.onSurfaceVariant
                    )
                    if (discovered) {
                        Spacer(Modifier.width(6.dp))
                        Text("✓", color = scheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (terpene.formula.isNotBlank()) append(terpene.formula)
                        if (terpene.family.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(terpene.family)
                        }
                        if (terpene.boilingPoint.isNotBlank()) {
                            append(" · ").append(terpene.boilingPoint)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    terpene.aroma,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                if (terpene.effects.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        terpene.effects.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (terpene.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (terpene.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = if (terpene.isFavorite) scheme.tertiary else scheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * How far the list must scroll before the header, the progression card and the
 * three filter rows collapse, in pixels.
 *
 * A scroll threshold rather than a nested scroll listener: it is one line, it
 * cannot leak a callback, and it behaves the same whether the user flings the
 * list or nudges it. On a 440dpi screen this is roughly a fifth of the viewport,
 * which is enough to signal "you are in the results" without hiding anything
 * while the user is still reading the controls.
 */
private const val HEADER_COLLAPSE_PX = 220f

@Composable
private fun BadgesDialog(badges: List<com.trichome.app.model.Badge>, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏅 Medallas") },
        text = {
            LazyColumn(
                modifier = Modifier.height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(badges) { badge ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            badge.emoji,
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (badge.unlocked) scheme.onSurface else scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                badge.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (badge.unlocked) scheme.onSurface else scheme.onSurfaceVariant
                            )
                            Text(
                                badge.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (badge.unlocked) "✓" else "🔒",
                            color = if (badge.unlocked) scheme.primary else scheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}