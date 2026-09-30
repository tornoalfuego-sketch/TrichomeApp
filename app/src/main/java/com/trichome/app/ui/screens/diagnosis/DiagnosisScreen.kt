package com.trichome.app.ui.screens.diagnosis

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.trichome.app.domain.vision.PhotoAnalyzer
import com.trichome.app.data.repository.DiagnosisResult
import com.trichome.app.data.repository.DiagnosisSymptom
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.components.accentLabelOn
import com.trichome.app.ui.components.AppTopBar
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.SolidPanel
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
    val plants by vm.plants.collectAsState()

    var symptoms by remember { mutableStateOf<List<DiagnosisSymptom>>(emptyList()) }
    var selectedPlantId by remember { mutableStateOf<Long?>(null) }
    var registerError by remember { mutableStateOf(false) }
    var registered by remember { mutableStateOf(false) }

    var currentImage by remember { mutableStateOf<Uri?>(null) }
/** Non-null when the last capture/import attempt failed. */
var currentImageError by remember { mutableStateOf<String?>(null) }
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
        if (uri == null) return@rememberLauncherForActivityResult
        // A content:// URI is not a file path: copy it into the cache first so
        // the analyser can open it with the same code as a camera capture.
        val copied = context.copyUriToCache(uri, "gallery_${System.currentTimeMillis()}.jpg")
        if (copied == null) {
            currentImageError = "No se pudo leer la imagen seleccionada."
        } else {
            currentImage = Uri.fromFile(copied)
            currentImageError = null
            vm.setImagePath(copied.absolutePath)
        }
    }

    // TakePicturePreview crashed on every press: the implicit intent resolved to
    // no activity (ActivityNotFoundException) and the camera permission was
    // never requested, so the SecurityException surfaced synchronously. CameraX
    // captures in-process and asks for the permission first.
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when {
            result.resultCode == android.app.Activity.RESULT_OK -> {
                val path = result.data?.getStringExtra(CameraCaptureActivity.EXTRA_OUTPUT)
                if (path.isNullOrBlank()) {
                    currentImageError = "La cámara no devolvió ninguna imagen."
                } else {
                    currentImage = Uri.fromFile(File(path))
                    currentImageError = null
                    vm.setImagePath(path)
                }
            }
            else -> {
                val reason = result.data?.getStringExtra(CameraCaptureActivity.EXTRA_ERROR)
                if (!reason.isNullOrBlank()) currentImageError = reason
            }
        }
    }

    fun openCamera() {
        currentImageError = null
        runCatching {
            cameraLauncher.launch(Intent(context, CameraCaptureActivity::class.java))
        }.onFailure {
            currentImageError = "No se pudo abrir la cámara en este dispositivo."
        }
    }

    fun openGallery() {
        currentImageError = null
        runCatching { galleryLauncher.launch("image/*") }
            .onFailure { currentImageError = "No se pudo abrir la galería." }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "🩺 Diagnóstico Inteligente",
                onNavigateBack = { navController.popBackStack() }
            )
        },
        bottomBar = {
            MainBottomBar("diagnosis", { navController.navigate(it) }, themeState)
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
                    onClick = { openCamera() },
                    modifier = Modifier.weight(1f),
                    colors = accentButtonColors(accent)
                ) { Text("📷 Cámara") }
                Button(
                    onClick = { openGallery() },
                    modifier = Modifier.weight(1f)
                ) { Text("🖼️ Galería") }
            }

            currentImageError?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Analysis state and what the photo actually measured. Without
            // this the verdict looked identical whether or not a picture was
            // taken, which is why the feature read as a no-op.
            when {
                vm.analyzing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Analizando la imagen…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                vm.photoFeatures != null -> {
                    SolidPanel(
                        accentColor = accent,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Lectura de la foto", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            PhotoAnalyzer.describe(vm.photoFeatures!!).forEach { (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(value, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (vm.photoMatches.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Coincide con:",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                vm.photoMatches.take(3).forEach { match ->
                                    Text(
                                        "· ${match.condition.labelEs} — ${(match.evidenceFit * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                vm.photoError != null -> {
                    Text(
                        text = vm.photoError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (currentImage != null) {
                SolidPanel(accentColor = accent) {
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
            SolidPanel(accentColor = accent) {
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
                            colors = accentButtonColors(accent)
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
                    accent = accent
                )

                // Register in journal
                SolidPanel(accentColor = accent) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Registrar en Bitácora", style = MaterialTheme.typography.titleMedium)
                        plants.forEach { p ->
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
                            colors = accentButtonColors(accent)
                        ) { Text("Registrar en Bitácora") }
                    }
                }
            } else {
                SolidPanel(accentColor = accent) {
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

@Composable
private fun DiagnosisReport(
    result: DiagnosisResult,
    condition: DiagnosisCondition,
    accent: Color
) {
    val categoryLabel = when (result.category) {
        "deficiency" -> "Deficiencia"
        "excess" -> "Exceso / Estrés"
        "pest" -> "Plaga"
        "fungus" -> "Hongos"
        else -> "Planta Saludable"
    }

    SolidPanel(accentColor = accent) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📋 Reporte de Diagnóstico", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        condition.labelEs,
                        style = MaterialTheme.typography.headlineSmall,
                        // The accent as ink is unreadable on several theme pairs
                        // (1.57:1 for amber on the light theme); the helper keeps
                        // it where it carries and falls back where it does not.
                        color = accentLabelOn(MaterialTheme.colorScheme, accent)
                    )
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
/**
 * Copies a picked `content://` image into the cache and returns the local file.
 *
 * `ActivityResultContracts.GetContent` hands back a URI, not a path. Feeding it
 * straight to the file-based decoder always failed, so gallery picks silently
 * produced no analysis.
 */
private fun Context.copyUriToCache(uri: Uri, fileName: String): File? = runCatching {
    val target = File(cacheDir, fileName)
    contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(target).use { output -> input.copyTo(output) }
    } ?: return null
    target.takeIf { it.length() > 0L }
}.getOrNull()