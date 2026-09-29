package com.trichome.app.ui.screens.breeding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.entity.BreedingCross
import com.trichome.app.data.entity.BreedingProject
import com.trichome.app.data.repository.BreedingGeneration
import com.trichome.app.data.repository.BreedingTechnique
import com.trichome.app.data.repository.BreedingTerm
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.rememberDestructiveConfirmation
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.BreedingViewModel
import com.trichome.app.viewmodel.appViewModel
import com.trichome.app.viewmodel.container
import kotlinx.coroutines.launch

/**
 * Breeding center: theory bible (generations, techniques, glossary) from
 * `assets/data/breeding.json` plus the grower's breeding projects and crosses
 * stored in Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreedingScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { BreedingViewModel(it) }
    val scope = rememberCoroutineScope()
    val accent = themeState.colorScheme().primary
    val projects by vm.projects.collectAsState()
    val container = container()

    var tab by remember { mutableIntStateOf(1) } // 0 = teoría, 1 = proyectos
    var generations by remember { mutableStateOf<List<BreedingGeneration>>(emptyList()) }
    var techniques by remember { mutableStateOf<List<BreedingTechnique>>(emptyList()) }
    var glossary by remember { mutableStateOf<List<BreedingTerm>>(emptyList()) }

    LaunchedEffect(Unit) {
        generations = container.breedingContentRepository.getGenerations()
        techniques = container.breedingContentRepository.getTechniques()
        glossary = container.breedingContentRepository.getGlossary()
    }

    var showProjectDialog by remember { mutableStateOf(false) }
    var crossTarget by remember { mutableStateOf<BreedingProject?>(null) }

    // A tap only arms the dialog; the project and its crosses go once the user
    // confirms, naming the row they picked.
    val deleteProjectConfirmation = rememberDestructiveConfirmation<BreedingProject>(
        title = { "Eliminar proyecto" },
        message = { project ->
            "Se eliminará el proyecto de cría «${project.name}» y todos sus cruces. " +
                "Esta acción no se puede deshacer."
        },
        confirmLabel = { "Eliminar" },
        onConfirmed = { project -> scope.launch { vm.deleteProject(project) } }
    )
    val deleteCrossConfirmation = rememberDestructiveConfirmation<BreedingCross>(
        title = { "Eliminar cruce" },
        message = { cross ->
            "Se eliminará el cruce «${cross.parent1.ifBlank { "?" }} × ${cross.parent2.ifBlank { "?" }}». " +
                "Esta acción no se puede deshacer."
        },
        confirmLabel = { "Eliminar" },
        onConfirmed = { cross -> scope.launch { vm.deleteCross(cross) } }
    )

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = themeState.accentColor)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("🧬 Biblia de Breeding") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (tab == 1) {
                    FloatingActionButton(
                        onClick = { showProjectDialog = true },
                        containerColor = accent
                    ) {
                        Icon(Icons.Default.Add, "Nuevo Proyecto")
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Black.copy(alpha = 0.2f)
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("📖 Teoría") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("🌱 Proyectos") })
                }
                Spacer(Modifier.height(12.dp))

                when (tab) {
                    0 -> TheoryTab(generations, techniques, glossary, accent, themeState.glassTokens.glassOpacity)
                    else -> ProjectsTab(
                        projects = projects,
                        crosses = vm.crosses.groupBy { it.projectId },
                        accent = accent,
                        glassOpacity = themeState.glassTokens.glassOpacity,
                        onDeleteProject = { deleteProjectConfirmation.request(it) },
                        onAddCross = { crossTarget = it },
                        onDeleteCross = { deleteCrossConfirmation.request(it) }
                    )
                }
            }
        }
    }

    if (showProjectDialog) {
        ProjectDialog(
            accent = accent,
            onDismiss = { showProjectDialog = false },
            onSave = { name, mother, father, generation ->
                scope.launch { vm.addProject(name, mother, father, generation) }
                showProjectDialog = false
            }
        )
    }

    crossTarget?.let { project ->
        CrossDialog(
            project = project,
            accent = accent,
            onDismiss = { crossTarget = null },
            onSave = { parent1, parent2, score, notes ->
                scope.launch { vm.addCross(project.id, parent1, parent2, score, notes) }
                crossTarget = null
            }
        )
    }
}

@Composable
private fun TheoryTab(
    generations: List<BreedingGeneration>,
    techniques: List<BreedingTechnique>,
    glossary: List<BreedingTerm>,
    accent: Color,
    glassOpacity: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Generaciones", style = MaterialTheme.typography.titleMedium)
        generations.forEach { g ->
            GlassCard(accentColor = accent, glassOpacity = glassOpacity) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(g.label + " — " + g.titleEs, style = MaterialTheme.typography.titleMedium)
                    Text(g.descriptionEs, style = MaterialTheme.typography.bodyMedium)
                    g.stepsEs.takeIf { it.isNotEmpty() }?.let { steps ->
                        Spacer(Modifier.height(6.dp))
                        Text("Pasos:", style = MaterialTheme.typography.labelLarge)
                        steps.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    g.prosEs.takeIf { it.isNotEmpty() }?.let { pros ->
                        Spacer(Modifier.height(6.dp))
                        Text("Pros: " + pros.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    g.consEs.takeIf { it.isNotEmpty() }?.let { cons ->
                        Text("Contras: " + cons.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Técnicas", style = MaterialTheme.typography.titleMedium)
        techniques.forEach { t ->
            GlassCard(accentColor = accent, glassOpacity = glassOpacity) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(t.labelEs, style = MaterialTheme.typography.titleMedium)
                    Text(t.descriptionEs, style = MaterialTheme.typography.bodyMedium)
                    t.methodsEs.forEach { m ->
                        Text("• ${m.name}: ${m.detailEs}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Glosario", style = MaterialTheme.typography.titleMedium)
        glossary.forEach { term ->
            GlassCard(accentColor = accent, glassOpacity = glassOpacity) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(term.term, style = MaterialTheme.typography.titleSmall)
                    Text(term.definitionEs, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ProjectsTab(
    projects: List<BreedingProject>,
    crosses: Map<Long, List<BreedingCross>>,
    accent: Color,
    glassOpacity: Float,
    onDeleteProject: (BreedingProject) -> Unit,
    onAddCross: (BreedingProject) -> Unit,
    onDeleteCross: (BreedingCross) -> Unit
) {
    if (projects.isEmpty()) {
        GlassCard(accentColor = accent) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No hay proyectos de cría", style = MaterialTheme.typography.bodyLarge)
                Text("Toca + para crear tu primer proyecto", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(projects, key = { it.id }) { project ->
            GlassCard(accentColor = accent, glassOpacity = glassOpacity) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Madre: ${project.motherId.ifBlank { "—" }} · Padre: ${project.fatherId.ifBlank { "—" }} · ${project.generation}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { onDeleteProject(project) }) {
                            Icon(Icons.Default.Delete, "Eliminar proyecto")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    val projectCrosses = crosses[project.id].orEmpty()
                    if (projectCrosses.isEmpty()) {
                        Text("Sin cruces registrados", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        projectCrosses.forEach { cross ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${cross.parent1.ifBlank { "?" }} × ${cross.parent2.ifBlank { "?" }}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (cross.notes.isNotBlank()) {
                                        Text(cross.notes, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(
                                    "Score ${cross.phenotypeScore.toInt()}/10",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent
                                )
                                IconButton(onClick = { onDeleteCross(cross) }) {
                                    Icon(Icons.Default.Delete, "Eliminar cruce")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onAddCross(project) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Registrar cruce")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDialog(
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (name: String, mother: String, father: String, generation: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mother by remember { mutableStateOf("") }
    var father by remember { mutableStateOf("") }
    var generation by remember { mutableStateOf("F1") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Proyecto de Cría") },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) nameError = true else onSave(name.trim(), mother.trim(), father.trim(), generation)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre del Proyecto *") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("El nombre no puede estar vacío") } } else null,
                    singleLine = true
                )
                OutlinedTextField(
                    value = mother,
                    onValueChange = { mother = it },
                    label = { Text("Planta Madre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = father,
                    onValueChange = { father = it },
                    label = { Text("Planta Padre") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("F1", "F2", "F3", "IBL", "Feminizada", "Retrocruz").forEach { g ->
                        FilterChip(
                            selected = generation == g,
                            onClick = { generation = g },
                            label = { Text(g) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CrossDialog(
    project: BreedingProject,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (parent1: String, parent2: String, score: Float, notes: String) -> Unit
) {
    var parent1 by remember { mutableStateOf("") }
    var parent2 by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(5f) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Cruce · ${project.name}") },
        confirmButton = {
            TextButton(onClick = { onSave(parent1.trim(), parent2.trim(), score, notes.trim()) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = parent1,
                    onValueChange = { parent1 = it },
                    label = { Text("Parental 1") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = parent2,
                    onValueChange = { parent2 = it },
                    label = { Text("Parental 2") },
                    singleLine = true
                )
                Text("Phenotype score: ${score.toInt()}/10", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = score,
                    onValueChange = { score = it },
                    valueRange = 0f..10f,
                    steps = 9
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    minLines = 2
                )
            }
        }
    )
}