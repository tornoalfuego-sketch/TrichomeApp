package com.trichome.app.ui.screens.tent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.trichome.app.data.entity.GrowTent
import com.trichome.app.data.entity.Plant
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.components.accentContentOn
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.rememberDestructiveConfirmation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import com.trichome.app.ui.components.accentTextButtonColors
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.screens.plant.PlantDeletionNotice
import com.trichome.app.ui.screens.plant.PlantEditDialog
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.TentViewModel
import com.trichome.app.viewmodel.appViewModel

@Composable
fun TentListScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { TentViewModel(it) }
    val scheme = themeState.colorScheme()
    val accent = scheme.primary
    // Collected as state: reading `.value` in composition would freeze the
    // tent and plant lists at their first value and never update.
    val tents by vm.tents.collectAsState()
    val unassigned by vm.unassignedPlants.collectAsState()
    val plants by vm.plants.collectAsState()
    var showAddTent by remember { mutableStateOf(false) }
    var editingTent by remember { mutableStateOf<GrowTent?>(null) }
    var editingPlant by remember { mutableStateOf<Plant?>(null) }

    // A tent that still has plants in it is refused, not emptied.
    //
    // It used to be emptied, and the dialog said so: "sus plantas no se borrarán,
    // quedarán sin asignar a ninguna carpa". The first half was true and the
    // second half was a promise the app could not keep -- `tentId` became null,
    // every query filtered on `tentId = :tentId`, and there was no query anywhere
    // that returned them. The plants survived, were counted in the totals, and
    // could not be seen, opened, edited or deleted from any screen.
    //
    // So the delete is now a decision the grower makes twice: first where the
    // plants go, then whether the tent itself goes.
    var tentToClear by remember { mutableStateOf<GrowTent?>(null) }
    val deleteTentConfirmation = rememberDestructiveConfirmation<GrowTent>(
        title = { "Eliminar carpa" },
        message = { tent ->
            "Se eliminará la carpa «${tent.name}». No tiene plantas dentro, así que no " +
                "se queda nada sin asignar. Esta acción no se puede deshacer."
        },
        confirmLabel = { "Eliminar" },
        onConfirmed = { tent ->
            // The plant list is re-read here rather than trusted from the message:
            // it can change between the tap and the confirm, and deleting a tent
            // that gained a plant in between is the exact case this guard is for.
            val hasPlants = vm.plants.value.any { it.tentId == tent.id }
            if (hasPlants) tentToClear = tent else vm.deleteTent(tent)
        }
    )
    val deletePlantConfirmation = rememberDestructiveConfirmation<Plant>(
        title = { "Eliminar planta" },
        // One copy for both delete sites, so the app cannot tell the user two
        // different things about what a plant delete removes.
        message = { plant -> PlantDeletionNotice.message(plant) },
        confirmLabel = { "Eliminar" },
        onConfirmed = { plant -> vm.deletePlant(plant) }
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingTent = null; showAddTent = true },
                containerColor = accent,
                contentColor = accentContentOn(accent)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir Carpa")
            }
        },
        bottomBar = {
            MainBottomBar("tents", { navController.navigate(it) }, themeState)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("🏕️ Carpas de Cultivo", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                tents.count { it.isActive }.let { "$it carpa(s) activa(s) · ${plants.size} planta(s)" },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            if (tents.isEmpty()) {
                SolidPanel {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏕️", fontSize = 40.sp)
                        Text("No hay carpas registradas", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Toca + para crear tu primera carpa y agrupar tus plantas",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tents, key = { it.id }) { tent ->
                        TentCard(
                            tent = tent,
                            plants = plants.filter { it.tentId == tent.id }.sortedBy { it.sortOrder },
                            accent = accent,
                            onOpen = { tapped ->
                                // A tent with no plants renders no row to
                                // tap; if one is ever reached with nothing
                                // selected the tap is ignored rather than
                                // navigating to a sentinel id.
                                TentNavigation.targetForTap(tapped)?.let { route ->
                                    navController.navigate(route)
                                }
                            },
                            onEdit = { editingTent = tent },
                            onDelete = { deleteTentConfirmation.request(tent) },
                            onMoveUp = { p -> vm.moveUp(p) },
                            onMoveDown = { p -> vm.moveDown(p) },
                            onEditPlant = { p -> editingPlant = p },
                            onDeletePlant = { p -> deletePlantConfirmation.request(p) },
                            onAddPlant = { name ->
                                vm.addPlant(name, tent.id, "", "seedling", System.currentTimeMillis())
                            }
                        )
                    }

                    // Plants left behind by a deleted tent.
                    //
                    // They used to exist with no way to be seen: `tentId` was null
                    // and every query filtered on `tentId = :tentId`. Shown here,
                    // they are reachable again -- open one, move it into a tent, or
                    // delete it. Rendered as a card rather than as a row inside a
                    // tent, because a tent is exactly what they no longer have.
                    if (unassigned.isNotEmpty()) {
                        item(key = "unassigned") {
                            SolidPanel(
                                contentColor = scheme.onSurface
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "🧺 Sin carpa",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "Estas plantas estaban en una carpa que ya no existe. " +
                                            "Asignales una carpa o eliminalas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    unassigned.forEach { plant ->
                                        UnassignedPlantRow(
                                            plant = plant,
                                            tentOptions = tents.filter { it.id != tentToClear?.id },
                                            accent = accent,
                                            onOpen = { target ->
                                                TentNavigation.targetForTap(target)
                                                    ?.let { navController.navigate(it) }
                                            },
                                            onAssign = { destination ->
                                                vm.assignPlantToTent(plant.id, destination.id)
                                            },
                                            onDelete = { deletePlantConfirmation.request(plant) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // The refusal. It replaces the confirm rather than stacking on top of it, so
    // the grower reads one explanation instead of two dialogs about the same tap.
    tentToClear?.let { tent ->
        val occupants = plants.filter { it.tentId == tent.id }.sortedBy { it.sortOrder }
        val destinations = tents.filter { it.id != tent.id }
        AlertDialog(
            onDismissRequest = { tentToClear = null },
            title = { Text("La carpa tiene plantas") },
            text = {
                Column {
                    Text(
                        "«${tent.name}» tiene ${occupants.size} planta(s). No se puede " +
                            "eliminar hasta que estén en otra carpa: si se borrara, se " +
                            "quedarían sin asignar y no se podrían abrir ni editar desde " +
                            "ningún lado.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    occupants.forEach { plant ->
                        Text(
                            "· ${plant.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { tentToClear = null },
                    colors = accentTextButtonColors(scheme, accent)
                ) { Text("Entendido") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Move every plant, then delete: the tent is only empty
                        // once they have all landed, and doing it in one step is
                        // what makes the second tap unnecessary.
                        val fallback = destinations.firstOrNull() ?: run {
                            tentToClear = null
                            return@TextButton
                        }
                        occupants.forEach { vm.assignPlantToTent(it.id, fallback.id) }
                        vm.deleteTent(tent)
                        tentToClear = null
                    },
                    enabled = destinations.isNotEmpty(),
                    colors = accentTextButtonColors(scheme, accent)
                ) {
                    Text(
                        if (destinations.isEmpty()) {
                            "Sin carpa destino"
                        } else {
                            "Mover a ${destinations.first().name} y eliminar"
                        }
                    )
                }
            }
        )
    }

    if (showAddTent || editingTent != null) {
        TentDialog(
            tent = editingTent,
            onDismiss = { showAddTent = false; editingTent = null },
            onSave = { name, location, capacity, lightType, watts, active ->
                if (editingTent == null) {
                    vm.addTent(name, location, capacity, lightType, watts, active)
                } else {
                    vm.updateTent(editingTent!!.copy(name = name, location = location, capacity = capacity, lightType = lightType, lightPowerWatts = watts, isActive = active))
                }
                showAddTent = false
                editingTent = null
            }
        )
    }

    editingPlant?.let { plant ->
        PlantEditDialog(
            plant = plant,
            accent = accent,
            onDismiss = { editingPlant = null },
            onSave = { updated ->
                vm.updatePlant(updated)
                editingPlant = null
            }
        )
    }
}

@Composable
private fun TentCard(
    tent: GrowTent,
    plants: List<Plant>,
    accent: Color,
    onOpen: (Plant) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (Plant) -> Unit,
    onMoveDown: (Plant) -> Unit,
    onEditPlant: (Plant) -> Unit,
    onDeletePlant: (Plant) -> Unit,
    onAddPlant: (String) -> Unit
) {
    SolidPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tent.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        // Location, lamp type, wattage and occupancy in one
                        // sentence. Left in the prose face on purpose: the numbers
                        // are welded to their unit words (`W`, `plantas`) and to the
                        // location before them, so instrumenting them means
                        // splitting one `Text` into three and re-laying-out a row
                        // that already carries two icon buttons. That is a layout
                        // change wearing a typography costume.
                        "${tent.location.ifBlank { "Sin ubicación" }} · ${tent.lightType} ${tent.lightPowerWatts}W · ${plants.size}/${tent.capacity} plantas",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Named, because an unnamed icon button is a button TalkBack
                // announces as just a button: two of them in a row, one editing and
                // one destroying, with nothing to tell them apart. It also gave the
                // automated checks nothing to target, which is how the delete path
                // stayed untested until now.
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar carpa") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar carpa") }
            }

            Spacer(Modifier.height(8.dp))

            if (plants.isEmpty()) {
                Text("Sin plantas en esta carpa", style = MaterialTheme.typography.bodyMedium)
            } else {
                plants.forEachIndexed { index, plant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(plant) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plant.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                plant.strain.ifBlank { plant.currentStage },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // Reorder arrows
                        IconButton(
                            onClick = { onMoveUp(plant) },
                            enabled = index > 0
                        ) { Icon(Icons.Default.KeyboardArrowUp, "Subir") }
                        IconButton(
                            onClick = { onMoveDown(plant) },
                            enabled = index < plants.size - 1
                        ) { Icon(Icons.Default.KeyboardArrowDown, "Bajar") }
                        // Opens the same editor the plant detail screen uses, so
                        // the row is seeded from the stored plant instead of the
                        // create-time defaults this list used to hardcode.
                        IconButton(onClick = { onEditPlant(plant) }) {
                            Icon(Icons.Default.Edit, "Editar planta")
                        }
                        IconButton(onClick = { onDeletePlant(plant) }) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            AddPlantRow(onAdd = onAddPlant, accent = accent)
        }
    }
}

@Composable
private fun AddPlantRow(onAdd: (String) -> Unit, accent: Color) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Nueva planta…") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            },
            colors = accentButtonColors(accent)
        ) {
            Icon(Icons.Default.Add, "Añadir")
        }
    }
}

@Composable
private fun TentDialog(
    tent: GrowTent?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, Int, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(tent?.name.orEmpty()) }
    var location by remember { mutableStateOf(tent?.location.orEmpty()) }
    var capacity by remember { mutableStateOf(tent?.capacity ?: 1) }
    var lightType by remember { mutableStateOf(tent?.lightType ?: "LED") }
    var watts by remember { mutableStateOf(tent?.lightPowerWatts ?: 200) }
    var active by remember { mutableStateOf(tent?.isActive ?: true) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tent == null) "Añadir Carpa" else "Editar Carpa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre de la Carpa *") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("El nombre no puede estar vacío") }
                    } else null,
                    singleLine = true
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Ubicación") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = capacity.toString(),
                    onValueChange = { capacity = it.toIntOrNull() ?: 1 },
                    label = { Text("Capacidad (plantas)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = lightType,
                    onValueChange = { lightType = it },
                    label = { Text("Tipo de Luz") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = watts.toString(),
                    onValueChange = { watts = it.toIntOrNull() ?: 0 },
                    label = { Text("Potencia (W)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("Carpa activa")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = true
                } else {
                    onSave(name.trim(), location.trim(), capacity, lightType, watts, active)
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
/**
 * One plant with no tent, and the three things that can be done about it.
 *
 * A dropdown rather than a second dialog: the destination is usually obvious
 * (there is one other tent) and a row that needs its own modal to be repaired is
 * a row nobody repairs. [tentOptions] may be empty, and then the assign control
 * says so instead of opening onto nothing.
 */
@Composable
private fun UnassignedPlantRow(
    plant: Plant,
    tentOptions: List<GrowTent>,
    accent: Color,
    onOpen: (Plant) -> Unit,
    onAssign: (GrowTent) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(plant) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(plant.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    plant.strain.ifBlank { "sin cepa" },
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    "Eliminar planta",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    enabled = tentOptions.isNotEmpty(),
                    colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
                ) {
                    Text(
                        if (tentOptions.isEmpty()) {
                            "No hay carpa destino"
                        } else {
                            "Mover a..."
                        }
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    tentOptions.forEach { tent ->
                        DropdownMenuItem(
                            text = { Text(tent.name) },
                            onClick = {
                                menuOpen = false
                                onAssign(tent)
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { onOpen(plant) }) {
                Icon(Icons.Default.ChevronRight, "Abrir planta")
            }
        }
    }
}