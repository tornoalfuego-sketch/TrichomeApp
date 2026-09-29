package com.trichome.app.ui.screens.plant

import com.trichome.app.data.entity.Plant

/**
 * The plant editor's state, and nothing else.
 *
 * Deliberately not a [Plant]: the entity carries the identity (`id`, `tentId`,
 * `sortOrder`, `growStartTimestamp`, `createdAt`) that the grower never edits.
 * `AddPlantRow` in [com.trichome.app.ui.screens.tent.TentListScreen] creates
 * with `strain = ""`, `stage = "seedling"` and `growStart =
 * System.currentTimeMillis()`; reusing those create-time defaults for an edit
 * would reset a flowering plant to seedling on day one.
 */
data class PlantForm(
    val name: String = "",
    val strain: String = "",
    val stage: String = "seedling",
    val notes: String = ""
)

/**
 * The decision behind the plant editor, free of Android and Compose so it runs
 * on the JVM under test.
 */
object PlantEditForm {

    /** Growth stages the editor offers, in the order they are shown. */
    val STAGES: List<String> = listOf(
        "seedling", "vegetativo", "floracion", "lavado", "cosecha"
    )

    /** Reads the stored row into the form. Never returns a default for a set field. */
    fun formOf(plant: Plant): PlantForm = PlantForm(
        name = plant.name,
        strain = plant.strain,
        stage = plant.currentStage,
        notes = plant.notes
    )

    /**
     * Writes the form back onto the existing row.
     *
     * Everything the grower did not type is carried over from [existing]: the
     * id (an update that changed it would insert a second plant), the tent
     * binding, the position inside the tent and — most importantly — the grow
     * start, because "Día de Crecimiento" is derived from it and resetting it
     * rewinds the whole plant.
     */
    fun applyTo(form: PlantForm, existing: Plant): Plant = existing.copy(
        name = form.name.trim(),
        strain = form.strain.trim(),
        currentStage = form.stage,
        notes = form.notes.trim()
    )

    /**
     * The Spanish reason [form] cannot be saved, or null when it can.
     *
     * Returned rather than thrown or logged, because the dialog renders it
     * verbatim and a silent save is the failure this replaces.
     */
    fun validate(form: PlantForm): String? =
        if (form.name.isBlank()) "El nombre de la planta no puede estar vacío." else null
}

/**
 * What deleting a plant actually does, stated honestly.
 *
 * `grow_events` is the **only** table with a foreign key to `plants`; it
 * cascades. `protocols`, `super_cycle_configs`, `stage_entries` and `reminders`
 * carry a `plantId` with no foreign key at all, so those rows survive the delete
 * as orphans that no screen can reach again. Fixing that would need a schema
 * change and is out of scope, so the dialog says so instead of implying a clean
 * sweep.
 */
object PlantDeletionNotice {

    /** Body of the shared destructive confirmation, naming [plant]. */
    fun message(plant: Plant): String {
        val name = plant.name.trim().ifEmpty { "sin nombre" }
        return "Se eliminará la planta «$name» y todo su historial de la bitácora. " +
            "Sus protocolos, su configuración de SuperCycle, sus registros de etapa y " +
            "sus recordatorios NO se borrarán: quedarán guardados sin planta asociada. " +
            "Esta acción no se puede deshacer."
    }
}
