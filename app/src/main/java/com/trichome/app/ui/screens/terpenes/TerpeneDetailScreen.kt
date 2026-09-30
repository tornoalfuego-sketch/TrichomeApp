package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.repository.Terpene
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.TerpenesViewModel
import com.trichome.app.viewmodel.appViewModel
import kotlinx.coroutines.launch

/**
 * Full detail card for a single terpene.
 *
 * The catalog entry is resolved by id, then every optional field is rendered
 * only when it is populated, so a partially authored entry degrades to a
 * shorter card instead of showing empty headings.
 *
 * Opening the card is what registers the discovery in the progression, so the
 * reward is tied to actually reading the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerpeneDetailScreen(
    terpeneId: String,
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { TerpenesViewModel(it) }
    val scope = rememberCoroutineScope()
    val scheme = themeState.colorScheme()

    var terpene by remember(terpeneId) { mutableStateOf<Terpene?>(null) }
    var partners by remember(terpeneId) { mutableStateOf<List<Terpene>>(emptyList()) }
    var notFound by remember(terpeneId) { mutableStateOf(false) }
    var unlocked by remember(terpeneId) { mutableStateOf(false) }
    var isNewDiscovery by remember(terpeneId) { mutableStateOf(false) }

    LaunchedEffect(terpeneId) {
        val found = vm.detail(terpeneId)
        if (found == null) {
            notFound = true
        } else {
            terpene = found
            partners = vm.partners(found)
            unlocked = found.id in vm.discovered
            if (!unlocked) {
                isNewDiscovery = vm.markDiscovered(found)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                terpene?.name ?: "Terpeno",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            terpene?.let { entry ->
                IconButton(onClick = { vm.toggleFavorite(entry) }) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (entry.isFavorite) {
                            "Quitar de favoritos"
                        } else {
                            "Añadir a favoritos"
                        },
                        tint = if (entry.isFavorite) scheme.tertiary else scheme.onSurfaceVariant
                    )
                }
            }
        }

        if (notFound) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontró ese terpeno.", color = scheme.onSurfaceVariant)
            }
            return@Column
        }

        val entry = terpene
        if (entry == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                TerpeneHeader(entry, themeState, isNewDiscovery)
            }

            // Identity: formula, mass, family, boiling point, richness.
            item {
                DetailCard("🧪 Identidad química", themeState) {
                    DataRow("Fórmula", entry.formula)
                    DataRow("Masa molar", entry.molarMass)
                    DataRow("Familia química", entry.family)
                    DataRow("Punto de ebullición", entry.boilingPoint)
                    DataRow("Riqueza en cannabis", entry.richness)
                }
            }

            item {
                DetailCard("👃 Perfil sensorial", themeState) {
                    DetailParagraph("Aroma", entry.aroma)
                    DetailParagraph("Sabor", entry.taste)
                }
            }

            if (entry.effects.isNotEmpty()) {
                item {
                    DetailCard("🧠 Efectos", themeState) {
                        ChipList(entry.effects, scheme.primary)
                    }
                }
            }

            if (entry.medicalProperties.isNotEmpty()) {
                item {
                    DetailCard("⚕️ Propiedades médicas", themeState) {
                        ChipList(entry.medicalProperties, scheme.tertiary)
                    }
                }
            }

            if (entry.mechanism.isNotBlank()) {
                item {
                    DetailCard("🎯 Mecanismo de acción", themeState) {
                        DetailParagraph("", entry.mechanism)
                    }
                }
            }

            if (entry.biosynthesis.isNotBlank()) {
                item {
                    DetailCard("🧬 Biosíntesis", themeState) {
                        DetailParagraph("", entry.biosynthesis)
                    }
                }
            }

            if (entry.toxicity.isNotBlank()) {
                item {
                    DetailCard("⚠️ Toxicidad y precauciones", themeState) {
                        DetailParagraph("", entry.toxicity)
                    }
                }
            }

            if (partners.isNotEmpty()) {
                item {
                    DetailCard("🤝 Efecto entourage", themeState) {
                        Text(
                            "Estos compuestos actúan como sinergistas: " +
                                "modulan el receptor y amplifican o matizan el efecto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            partners.forEach { partner ->
                                PartnerRow(partner, scheme) {
                                    navController.navigate("terpene/${partner.id}")
                                }
                            }
                        }
                    }
                }
            }

            if (entry.foundIn.isNotEmpty()) {
                item {
                    DetailCard("🌍 También se encuentra en", themeState) {
                        ChipList(entry.foundIn, scheme.secondary)
                    }
                }
            }

            if (entry.strains.isNotEmpty()) {
                item {
                    DetailCard("🌿 Cepas con alto contenido", themeState) {
                        ChipList(entry.strains, scheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerpeneHeader(
    entry: Terpene,
    themeState: TrichomeThemeState,
    isNew: Boolean
) {
    val scheme = themeState.colorScheme()
    SolidPanel(
        accentColor = scheme.primary,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(scheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Science,
                        contentDescription = null,
                        tint = scheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.headlineSmall)
                    if (entry.family.isNotBlank()) {
                        Text(
                            entry.family,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isNew) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = scheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "✨ Descubrimiento registrado · +20 XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    themeState: TrichomeThemeState,
    content: @Composable () -> Unit
) {
    val scheme = themeState.colorScheme()
    SolidPanel(
        accentColor = scheme.primary,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    if (value.isBlank()) return
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            ),
            color = scheme.onSurface
        )
    }
}

@Composable
private fun DetailParagraph(label: String, value: String) {
    if (value.isBlank()) return
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 4.dp)) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface
        )
    }
}

@Composable
private fun ChipList(items: List<String>, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PartnerRow(
    partner: Terpene,
    scheme: androidx.compose.material3.ColorScheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(partner.name, style = MaterialTheme.typography.titleSmall)
            Text(
                partner.family,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
        Text("→", color = scheme.primary)
    }
}