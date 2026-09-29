package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAltOff
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
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassChip
import com.trichome.app.ui.components.GlassmorphicBottomBar
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
    val glass = themeState.glassTokens
    var showQuiz by remember { mutableStateOf(false) }
    var showBadges by remember { mutableStateOf(false) }
    var showBlender by remember { mutableStateOf(false) }

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = scheme.tertiary)

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                GlassmorphicBottomBar("terpenes", { navController.navigate(it) }, themeState)
            }
        ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            /* ── Cabecera ─────────────────────────────────────────────── */
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
                    Text("📖 Biblia de Terpenos", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${vm.terpenes.size} compuestos · ${vm.discovered.size} descubiertos",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showBlender = true }) {
                    Icon(Icons.Filled.Science, contentDescription = "Master Blender", tint = accent)
                }
                IconButton(onClick = { showBadges = true }) {
                    Icon(Icons.Filled.Star, contentDescription = "Medallas", tint = accent)
                }
                IconButton(onClick = { showQuiz = true }) {
                    Icon(Icons.Filled.Quiz, contentDescription = "Trivia", tint = accent)
                }
            }

            /* ── Progresión ───────────────────────────────────────────── */
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
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
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
    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        glassOpacity = themeState.glassTokens.glassOpacity,
        blurRadius = themeState.glassTokens.blurRadius,
        accentColor = scheme.primary,
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
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        items(options) { option ->
            GlassChip(
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
    GlassCard(
        glassOpacity = themeState.glassTokens.glassOpacity,
        blurRadius = themeState.glassTokens.blurRadius,
        accentColor = scheme.primary,
        contentColor = scheme.onSurface,
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
                        color = if (discovered) scheme.onSurface else scheme.onSurfaceVariant
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
                    color = scheme.onSurface,
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
