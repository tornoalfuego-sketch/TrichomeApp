package com.trichome.app.ui.screens.breeding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.trichome.app.data.prefs.BreedingProgressRepository
import com.trichome.app.model.BreedingChapter
import com.trichome.app.model.BreedingProgress
import com.trichome.app.ui.components.accentContentOn
import com.trichome.app.ui.components.accentTextButtonColors
import com.trichome.app.ui.components.AppTopBar
import com.trichome.app.ui.components.rememberDestructiveConfirmation
import com.trichome.app.ui.components.SolidPanel
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

    // Chapters and medals. Collected rather than sampled, so a medal earned in
    // this session shows up without leaving the tab.
    val theory = rememberTheoryChapters(
        contentRepository = container.breedingContentRepository,
        progressRepository = container.breedingProgress
    )

    // One slot per dialog, each carrying the row it was opened on. A null row
    // means "new"; a non-null one means the form is seeded from it and the save
    // updates it. `BreedingDao` had no `@Update` before, so every save here was
    // an insert and editing a cross produced a duplicate.
    var projectTarget by remember { mutableStateOf<BreedingProject?>(null) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var crossTarget by remember { mutableStateOf<Pair<BreedingProject, BreedingCross?>?>(null) }

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

    Scaffold(
        topBar = {
            AppTopBar(
                title = "🧬 Biblia de Breeding",
                onNavigateBack = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            if (tab == 1) {
                FloatingActionButton(
                    onClick = { showProjectDialog = true },
                    containerColor = accent,
                    contentColor = accentContentOn(accent)
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
                // An opaque scheme role, not a 20% black wash: a translucent bar
                // over the page is a panel whose contrast depends on what happens
                // to be behind it, which is the class of bug this refactor removed.
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("📖 Teoría") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("🌱 Proyectos") })
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                // Theory is three surfaces now: the chapters with their
                // quizzes, the Punnett square, and the raw library. The
                // chapter list replaces the old flat generation/technique
                // dump, and the library stays as the unedited reference.
                0 -> TheoryTab(
                    chapters = theory.chapters,
                    progress = theory.progress,
                    accent = accent,
                    generations = generations,
                    techniques = techniques,
                    glossary = glossary,
                    breedingProgressRepository = container.breedingProgress
                )
                else -> ProjectsTab(
                    projects = projects,
                    crosses = vm.crosses.groupBy { it.projectId },
                    accent = accent,
                    onDeleteProject = { deleteProjectConfirmation.request(it) },
                    onEditProject = { projectTarget = it },
                    onAddCross = { crossTarget = it to null },
                    onEditCross = { project, cross -> crossTarget = project to cross },
                    onDeleteCross = { deleteCrossConfirmation.request(it) }
                )
            }
        }
    }

    if (showProjectDialog || projectTarget != null) {
        ProjectDialog(
            project = projectTarget,
            accent = accent,
            onDismiss = { showProjectDialog = false; projectTarget = null },
            onSave = { saved ->
                scope.launch {
                    if (BreedingForm.isNew(saved.id)) {
                        vm.addProject(saved.name, saved.motherId, saved.fatherId, saved.generation)
                    } else {
                        vm.updateProject(saved)
                    }
                }
                showProjectDialog = false
                projectTarget = null
            }
        )
    }

    crossTarget?.let { (project, cross) ->
        CrossDialog(
            project = project,
            cross = cross,
            accent = accent,
            onDismiss = { crossTarget = null },
            onSave = { saved ->
                scope.launch {
                    if (BreedingForm.isNew(saved.id)) {
                        vm.addCross(project.id, saved.parent1, saved.parent2, saved.phenotypeScore, saved.notes)
                    } else {
                        vm.updateCross(saved)
                    }
                }
                crossTarget = null
            }
        )
    }
}

@Composable
private fun TheoryTab(
    chapters: List<BreedingChapter>,
    progress: BreedingProgress,
    accent: Color,
    generations: List<BreedingGeneration>,
    techniques: List<BreedingTechnique>,
    glossary: List<BreedingTerm>,
    breedingProgressRepository: BreedingProgressRepository
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 0 = capítulos con cuestionario, 1 = simulador, 2 = biblioteca en bruto.
        var section by rememberSaveable { mutableIntStateOf(0) }

        TabRow(
            selectedTabIndex = section,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Tab(selected = section == 0, onClick = { section = 0 }, text = { Text("Capítulos") })
            Tab(selected = section == 1, onClick = { section = 1 }, text = { Text("Simulador") })
            Tab(selected = section == 2, onClick = { section = 2 }, text = { Text("Biblioteca") })
        }
        Spacer(Modifier.height(8.dp))

        when (section) {
            0 -> TheoryChaptersTab(
                chapters = chapters,
                progress = progress,
                repository = breedingProgressRepository,
                accent = accent,
            )
            1 -> PunnettSquarePanel(accent = accent)
            else -> LibraryReference(
                generations = generations,
                techniques = techniques,
                glossary = glossary,
            )
        }
    }
}

/**
 * The asset as it ships, unedited.
 *
 * Kept alongside the chapters on purpose: the chapters organise and (where the
 * asset has gaps) extend the library, and a reader who wants the source text has
 * to be able to find it without trusting the rewrite.
 */
@Composable
private fun LibraryReference(
    generations: List<BreedingGeneration>,
    techniques: List<BreedingTechnique>,
    glossary: List<BreedingTerm>,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Generaciones", style = MaterialTheme.typography.titleMedium)
        generations.forEach { g ->
            SolidPanel {
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
            SolidPanel {
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
            SolidPanel {
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
    onDeleteProject: (BreedingProject) -> Unit,
    onEditProject: (BreedingProject) -> Unit,
    onAddCross: (BreedingProject) -> Unit,
    onEditCross: (BreedingProject, BreedingCross) -> Unit,
    onDeleteCross: (BreedingCross) -> Unit
) {
    if (projects.isEmpty()) {
        SolidPanel {
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
            SolidPanel {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Madre: ${project.motherId.ifBlank { "—" }} · Padre: ${project.fatherId.ifBlank { "—" }} · ${project.generation}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { onEditProject(project) }) {
                            Icon(Icons.Default.Edit, "Editar proyecto")
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
                                IconButton(onClick = { onEditCross(project, cross) }) {
                                    Icon(Icons.Default.Edit, "Editar cruce")
                                }
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

/**
 * Project editor.
 *
 * One dialog for both jobs: [project] null means "new", a non-null project
 * means the form is seeded from the row and the save updates it. The state is
 * keyed on [ProjectForm.id] so reusing this dialog across projects cannot keep
 * the previous one's values — the `remember`-seeding bug from v1.1.0.
 */
@Composable
private fun ProjectDialog(
    project: BreedingProject?,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (BreedingProject) -> Unit
) {
    val isNew = BreedingForm.isNew(project?.id)
    var form by remember(project?.id) { mutableStateOf(BreedingForm.projectOf(project)) }
    var nameError by remember(project?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuevo Proyecto de Cría" else "Editar Proyecto de Cría") },
        confirmButton = {
            TextButton(
                onClick = {
                    val rejection = BreedingForm.validate(form)
                    if (rejection != null) {
                        nameError = true
                    } else {
                        onSave(BreedingForm.projectFrom(form))
                    }
                },
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it); nameError = false },
                    label = { Text("Nombre del Proyecto *") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("El nombre no puede estar vacío") } } else null,
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.mother,
                    onValueChange = { form = form.copy(mother = it) },
                    label = { Text("Planta Madre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.father,
                    onValueChange = { form = form.copy(father = it) },
                    label = { Text("Planta Padre") },
                    singleLine = true
                )
                BreedingForm.GENERATIONS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { g ->
                            FilterChip(
                                selected = form.generation == g,
                                onClick = { form = form.copy(generation = g) },
                                label = { Text(g) }
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Cross editor.
 *
 * [cross] null registers a new cross; a non-null one is seeded from the row and
 * saved with an `@Update`. Before `BreedingDao` grew an `@Update` this dialog
 * always inserted, so editing a cross produced a duplicate next to it.
 */
@Composable
private fun CrossDialog(
    project: BreedingProject,
    cross: BreedingCross?,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (BreedingCross) -> Unit
) {
    val isNew = BreedingForm.isNew(cross?.id)
    var form by remember(cross?.id, project.id) {
        mutableStateOf(BreedingForm.crossOf(project.id, cross))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                (if (isNew) "Registrar Cruce · " else "Editar Cruce · ") + project.name
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(BreedingForm.crossFrom(form)) },
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.parent1,
                    onValueChange = { form = form.copy(parent1 = it) },
                    label = { Text("Parental 1") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.parent2,
                    onValueChange = { form = form.copy(parent2 = it) },
                    label = { Text("Parental 2") },
                    singleLine = true
                )
                Text("Phenotype score: ${form.score.toInt()}/10", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = form.score,
                    onValueChange = { form = form.copy(score = it) },
                    valueRange = 0f..10f,
                    steps = 9
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { form = form.copy(notes = it) },
                    label = { Text("Notas") },
                    minLines = 2
                )
            }
        }
    )
}