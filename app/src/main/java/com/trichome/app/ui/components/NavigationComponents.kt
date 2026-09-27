package com.trichome.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.trichome.app.ui.theme.TrichomeThemeState

data class BottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

private val MainTabItems = listOf(
    BottomBarItem("home", "Inicio", Icons.Filled.Home, Icons.Filled.Home),
    BottomBarItem("tents", "Carpas", Icons.Filled.Festival, Icons.Filled.Festival),
    BottomBarItem("journal", "Bitácora", Icons.Filled.Book, Icons.AutoMirrored.Filled.MenuBook),
    BottomBarItem("diagnosis", "Diagnóstico", Icons.Filled.MedicalServices, Icons.Filled.MedicalServices),
    BottomBarItem("settings", "Ajustes", Icons.Filled.Settings, Icons.Filled.Settings)
)

@Composable
fun GlassmorphicBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    themeState: TrichomeThemeState,
    modifier: Modifier = Modifier
) {
    val accent = themeState.colorScheme().primary

    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Black.copy(alpha = (themeState.glassTokens.glassOpacity + 0.25f).coerceAtMost(0.9f))
    ) {
        MainTabItems.forEach { item ->
            val selected = currentRoute.startsWith(item.route)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = accent.copy(alpha = 0.18f)
                )
            )
        }
    }
}

/** Quick chip with glass style, e.g. event type chips. */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF66BB6A)
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accentColor.copy(alpha = 0.25f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = accentColor.copy(alpha = 0.6f),
            selectedBorderColor = accentColor,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        )
    )
}