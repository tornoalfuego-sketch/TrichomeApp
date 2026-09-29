package com.trichome.app.ui.screens.breeding

import com.trichome.app.data.entity.BreedingCross
import com.trichome.app.data.entity.BreedingProject

/** The project editor's state. */
data class ProjectForm(
    val id: Long = BreedingForm.NO_ROW,
    val name: String = "",
    val mother: String = "",
    val father: String = "",
    val generation: String = "F1",
    /**
     * Carried from the stored row and never edited.
     *
     * `@Update` writes whole entities, so a project rebuilt from the four
     * editable fields alone would take the entity defaults for the rest and
     * rewind `createdAt` to now while silently reopening an archived project.
     */
    val createdAt: Long = BreedingForm.NO_ROW,
    val status: String = "active"
)

/** The cross editor's state. */
data class CrossForm(
    val id: Long = BreedingForm.NO_ROW,
    val projectId: Long = 0L,
    val parent1: String = "",
    val parent2: String = "",
    val score: Float = 5f,
    val notes: String = ""
)

/**
 * The decision behind the breeding editors, free of Android and Compose so it
 * runs on the JVM under test.
 *
 * The bug this exists for: `BreedingDao` had **no** `@Update` at all, so both
 * dialogs could only insert. Opening `CrossDialog` on a cross that already
 * existed wrote a second, identical row next to it, and `status` and
 * `generation` were write-once because there was no other way to touch a project.
 *
 * [isNew] is the whole distinction: an id of [NO_ROW] means "insert, let Room
 * mint the id"; anything else means "update this exact row", which is why
 * [projectFrom] and [crossFrom] carry the id through from the form.
 */
object BreedingForm {

    /** Sentinel id meaning "this row does not exist yet". */
    const val NO_ROW: Long = 0L

    /** Generations offered by the project editor, matching the existing chips. */
    val GENERATIONS: List<String> = listOf("F1", "F2", "F3", "IBL", "Feminizada", "Retrocruz")

    private const val MAX_SCORE = 10f

    /**
     * Whether a save must insert rather than update.
     *
     * `null` means the dialog was opened for a new row. This is the check the
     * save button keys on, the same `isNew` distinction `ProtocolScreen` uses.
     */
    fun isNew(rowId: Long?): Boolean = rowId == null || rowId == NO_ROW

    /* ── Seeding ──────────────────────────────────────────────────────── */

    /** Reads [project] into the form, or a blank form when it is null. */
    fun projectOf(project: BreedingProject?): ProjectForm = if (project == null) {
        ProjectForm()
    } else {
        ProjectForm(
            id = project.id,
            name = project.name,
            mother = project.motherId,
            father = project.fatherId,
            generation = project.generation,
            createdAt = project.createdAt,
            status = project.status
        )
    }

    /**
     * Reads [existing] into the form.
     *
     * A cross is always bound to a project, so [projectId] is supplied even for
     * a new one; only the cross's own id stays at [NO_ROW].
     */
    fun crossOf(projectId: Long, existing: BreedingCross?): CrossForm = if (existing == null) {
        CrossForm(projectId = projectId)
    } else {
        CrossForm(
            id = existing.id,
            projectId = existing.projectId,
            parent1 = existing.parent1,
            parent2 = existing.parent2,
            score = existing.phenotypeScore,
            notes = existing.notes
        )
    }

    /* ── Writing back ─────────────────────────────────────────────────── */

    /**
     * The entity to write.
     *
     * A new row gets a zero id and a stamped `createdAt`, so Room inserts.
     * An existing row keeps its id, its `createdAt` and its `status` — none of
     * which are on the form, so reconstructing the entity from the form alone
     * would reset the creation instant and reopen an archived project.
     */
    fun projectFrom(form: ProjectForm, now: Long = System.currentTimeMillis()): BreedingProject =
        BreedingProject(
            // A new row carries no id, so Room inserts; an existing one keeps
            // its id, which is what makes `@Update` hit that row instead of
            // orphaning its crosses.
            id = form.id,
            name = form.name.trim(),
            motherId = form.mother.trim(),
            fatherId = form.father.trim(),
            generation = form.generation,
            createdAt = if (isNew(form.id)) now else form.createdAt,
            status = form.status
        )

    /**
     * The cross to write.
     *
     * An update rebuilds the entity from the form, so it must also carry the
     * fields the form does not expose — here only the id and the project
     * binding, which [crossOf] copied from the stored row for exactly this
     * reason.
     */
    fun crossFrom(form: CrossForm): BreedingCross = BreedingCross(
        id = form.id,
        projectId = form.projectId,
        parent1 = form.parent1.trim(),
        parent2 = form.parent2.trim(),
        phenotypeScore = form.score.coerceIn(0f, MAX_SCORE),
        notes = form.notes.trim()
    )

    /* ── Validation ───────────────────────────────────────────────────── */

    /** The Spanish reason [form] cannot be saved, or null when it can. */
    fun validate(form: ProjectForm): String? =
        if (form.name.isBlank()) "El nombre del proyecto no puede estar vacío." else null
}
