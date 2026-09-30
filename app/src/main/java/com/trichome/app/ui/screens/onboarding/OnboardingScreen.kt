package com.trichome.app.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.TrichomeThemeState
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val emoji: String,
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val PAGES = listOf(
    OnboardingPage(
        "🌱", Icons.Filled.Spa,
        "Tu cultivo, ordenado de verdad",
        "Carpas, plantas y etapas conectadas. Cada riego, poda y medición queda " +
            "registrado con fecha y hora, sin conexión y sin perder nada al actualizar."
    ),
    OnboardingPage(
        "📖", Icons.Filled.MenuBook,
        "La Biblia de Terpenos",
        "Más de 150 terpenos con fórmula, mecanismo farmacológico, biosíntesis, " +
            "toxicidad y cepas donde abundan. Gana experiencia y desbloquea medallas " +
            "descubriendo la enciclopedia."
    ),
    OnboardingPage(
        "🔬", Icons.Filled.Insights,
        "Diagnóstico que mide de verdad",
        "Sube una foto o usa la cámara: el motor analiza la imagen en el móvil " +
            "—clorosis, necrosis, manchas, telarañas— y la contrasta con una base de " +
            "datos de enfermedades para decirte qué es y cómo se solutiona."
    ),
    OnboardingPage(
        "📅", Icons.Filled.CalendarMonth,
        "Calendario con alarmas reales",
        "Crea eventos y recordatorios directamente en el calendario. Cada " +
            "recordatorio programa una alarma exacta del sistema, así que suena " +
            "aunque el móvil esté en reposo."
    ),
    OnboardingPage(
        "🎨", Icons.Filled.Palette,
        "Un tema que se lee",
        "Cuatro paletas con contraste verificado, color de acento funcional y " +
            "tipografía personalizable: familia, peso y tamaño. El fondo siempre " +
            "difiere de las tarjetas para que nada se pierda de vista."
    ),
    OnboardingPage(
        "📸", Icons.Filled.CameraAlt,
        "Tricomas y detección visual",
        "Asistente de enfoque por varianza de Laplacian, filtro de contraste para " +
            "resina ámbar y análisis de clorosis. Todo ocurre en el dispositivo."
    )
)

/**
 * First-run introduction.
 *
 * The caller decides whether to show it: this screen only renders the pages.
 * Visibility is owned by [com.trichome.app.data.prefs.OnboardingRepository],
 * which stops reporting `shouldShow` after three cold starts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    themeState: TrichomeThemeState,
    onFinish: () -> Unit
) {
    val scheme = themeState.colorScheme()
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage
    val lastPage = PAGES.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            // Only offer "skip" while there is still something to skip.
            if (page != lastPage) {
                TextButton(onClick = onFinish) {
                    Text("Saltar", color = scheme.onSurfaceVariant)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = 16.dp
        ) { index ->
            OnboardingPageContent(page = PAGES[index], themeState = themeState)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            PAGES.indices.forEach { i ->
                val active = i == page
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (active) 26.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) scheme.primary
                            else scheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Button(
            onClick = {
                if (page < lastPage) {
                    scope.launch { pagerState.animateScrollToPage(page + 1) }
                } else {
                    onFinish()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = accentButtonColors(scheme.primary)
        ) {
            Text(
                text = if (page < lastPage) "Siguiente" else "Empezar a cultivar",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = if (page < lastPage) "Paso ${page + 1} de ${PAGES.size}"
            else "Solo se mostrará 3 veces",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            scheme.primary.copy(alpha = 0.35f),
                            scheme.tertiary.copy(alpha = 0.25f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "${page.emoji}  ${page.title}",
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        SolidPanel(
            accentColor = scheme.primary,
            contentColor = scheme.onSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
