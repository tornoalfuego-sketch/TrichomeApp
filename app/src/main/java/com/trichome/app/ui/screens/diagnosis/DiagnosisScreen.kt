package com.trichome.app.ui.screens.diagnosis

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.trichome.app.data.repository.DiagnosisCondition
import com.trichome.app.data.repository.DiagnosisResult
import com.trichome.app.data.repository.DiagnosisSymptom
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.DiagnosisViewModel
import com.trichome.app.viewmodel.appViewModel
import com.trichome.app.viewmodel.container
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

/**
 * Photo + symptom based diagnosis. The picture is optional (rule engine works
 * on symptoms alone); results resolve Spanish labels and action plans from
 * `assets/data/diagnostics.json` and can be registered in the journal.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DiagnosisScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { DiagnosisViewModel(it) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val container = container()
    val accent = themeState.colorScheme().primary

    var symptoms by remember { mutableStateOf<List<DiagnosisSymptom>>(emptyList()) }
    var selectedPlantId by remember { mutableStateOf<Long?>(null) }
    var registerError by remember { mutableStateOf(false) }
    var registered by remember { mutableStateOf(false) }

    var currentImage by remember { mutableStateOf<Uri?>(null) }
    var reportCondition by remember { mutableStateOf<DiagnosisCondition?>(null) }

    LaunchedEffect(Unit) {
        symptoms = container.diagnosisContentRepository.getSymptoms()
    }

    LaunchedEffect(vm.result?.condition) {
        reportCondition = vm.result?.let {
            container.diagnosisContentRepository.getConditionOrHealthy(it.condition)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            currentImage = uri
            vm.setImagePath(uri.toString())
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "diagnosis_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            currentImage = Uri.fromFile(file)
            vm.setImagePath(file.absolutePath)
        }
    }

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = themeState.accentColor)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("🩺 Diagnóstico Inteligente") },
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Photo capture / selection ────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) { Text("📷 Cámara") }
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) { Text("🖼️ Galería") }
                }

                if (currentImage != null) {
                    GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                        AsyncImage(
                            model = currentImage,
                            contentDescription = "Foto de la planta",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // ── Symptoms ─────────────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Selecciona los síntomas visibles", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            symptoms.forEach { s ->
                                FilterChip(
                                    selected = vm.selectedSymptoms.contains(s.id),
                                    onClick = { vm.toggleSymptom(s.id) },
                                    label = { Text("${s.icon} ${s.labelEs}") }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { vm.diagnose() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = accent)
                            ) { Text("🔬 Diagnosticar") }
                            OutlinedButton(onClick = { vm.reset(); currentImage = null }) { Text("Limpiar") }
                        }
                    }
                }

                // ── Report ───────────────────────────────────────────────
                val result = vm.result
                if (result != null && reportCondition != null) {
                    DiagnosisReport(
                        result = result,
                        condition = reportCondition!!,
                        accent = accent,
                        glassOpacity = themeState.glassTokens.glassOpacity
                    )

                    // Register in journal
                    GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Registrar en Bitácora", style = MaterialTheme.typography.titleMedium)
                            vm.plants.value.forEach { p ->
                                FilterChip(
                                    selected = selectedPlantId == p.id,
                                    onClick = { selectedPlantId = p.id; registerError = false; registered = false },
                                    label = { Text(p.name) }
                                )
                            }
                            if (registerError) {
                                Text(
                                    "Selecciona una planta para registrar",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (registered) {
                                Text(
                                    "✓ Registrado en la bitácora",
                                    color = accent,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = {
                                    val pid = selectedPlantId
                                    if (pid == null) {
                                        registerError = true
                                    } else {
                                        scope.launch {
                                            vm.registerOnJournal(pid)
                                            registered = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = accent)
                            ) { Text("Registrar en Bitácora") }
                        }
                    }
                } else {
                    GlassCard(accentColor = accent) {
                        Column(
                            modifier = Modifier.padding(28.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Captura una foto y selecciona síntomas para obtener un diagnóstico.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosisReport(
    result: DiagnosisResult,
    condition: DiagnosisCondition,
    accent: Color,
    glassOpacity: Float
) {
    val categoryLabel = when (result.category) {
        "deficiency" -> "Deficiencia"
        "excess" -> "Exceso / Estrés"
        "pest" -> "Plaga"
        "fungus" -> "Hongos"
        else -> "Planta Saludable"
    }

    GlassCard(accentColor = accent, glassOpacity = glassOpacity + 0.08f) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📋 Reporte de Diagnóstico", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(condition.labelEs, style = MaterialTheme.typography.headlineSmall, color = accent)
                    Text("Categoría: $categoryLabel", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Certeza: ${(result.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LinearProgressIndicator(
                    progress = { result.confidence },
                    modifier = Modifier.width(72.dp),
                    color = accent
                )
            }

            if (result.symptoms.isNotEmpty()) {
                Text(
                    "Síntomas detectados: " + result.symptoms.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (condition.causeEs.isNotBlank()) {
                Text("Causa probable: ${condition.causeEs}", style = MaterialTheme.typography.bodyMedium)
            }
            if (condition.actionPlanEs.isNotEmpty()) {
                Text("Plan de Acción", style = MaterialTheme.typography.titleSmall)
                condition.actionPlanEs.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (condition.preventionEs.isNotEmpty()) {
                Text("Prevención", style = MaterialTheme.typography.titleSmall)
                condition.preventionEs.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}