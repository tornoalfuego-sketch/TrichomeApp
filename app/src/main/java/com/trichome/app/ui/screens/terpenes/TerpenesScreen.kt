package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.repository.Terpene
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassChip
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.TerpenesViewModel
import com.trichome.app.viewmodel.appViewModel

/**
 * Terpenes bible: searchable library of terpenes with aroma, effects, boiling
 * point and associated strains loaded from `assets/data/terpenes.json`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerpenesScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { TerpenesViewModel(it) }
    val accent = themeState.colorScheme().primary
    var showFavorites by remember { mutableStateOf(false) }

    val visible = if (showFavorites) vm.terpenes.filter { it.isFavorite } else vm.terpenes

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = themeState.accentColor)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("🌿 Biblia de Terpenos") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.updateQuery(it) },
                    label = { Text("Buscar terpeno, aroma o efecto…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        GlassChip("Todos", selected = !showFavorites, onClick = { showFavorites = false })
                    }
                    item {
                        GlassChip(
                            "⭐ Favoritos (${vm.terpenes.count { it.isFavorite }})",
                            selected = showFavorites,
                            onClick = { showFavorites = true }
                        )
                    }
                }

                if (visible.isEmpty()) {
                    GlassCard(accentColor = accent) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No se encontraron terpenos", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    visible.forEach { t ->
                        TerpeneCard(
                            terpene = t,
                            accent = accent,
                            glassOpacity = themeState.glassTokens.glassOpacity,
                            onToggleFavorite = { vm.toggleFavorite(t) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerpeneCard(
    terpene: Terpene,
    accent: Color,
    glassOpacity: Float,
    onToggleFavorite: () -> Unit
) {
    GlassCard(accentColor = accent, glassOpacity = glassOpacity) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(terpene.name, style = MaterialTheme.typography.titleLarge)
                    Text(terpene.aroma, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(
                    if (terpene.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorito",
                    tint = if (terpene.isFavorite) Color(0xFFFFC107) else accent,
                    modifier = Modifier
                        .clickable { onToggleFavorite() }
                        .padding(6.dp)
                )
            }

            if (terpene.effects.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Efectos:", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(terpene.effects) { effect ->
                        AssistChip(onClick = {}, label = { Text(effect) })
                    }
                }
            }

            if (terpene.boilingPoint.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Punto de ebullición: ${terpene.boilingPoint}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (terpene.strains.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Cepas asociadas: " + terpene.strains.take(5).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}