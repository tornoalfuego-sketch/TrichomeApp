package com.trichome.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trichome.app.data.export.ExportResult
import com.trichome.app.di.AppContainer
import com.trichome.app.model.ExportScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId

/**
 * The export screen's state: which plants and tents can be chosen, and the write.
 *
 * ## Why the subject name arrives as an argument
 *
 * [export] takes [subjectName] rather than resolving it. The picker already read the name to
 * render the row, and resolving it again would be a second read that can disagree — the row
 * would say "Planta 12" and the confirmation "Planta 14" if the plant was renamed in between.
 * The name that was shown to the grower is the name that names the file.
 *
 * ## The clock
 *
 * [export] takes `nowMillis`, defaulting to the system clock. Injectable so a test pins the
 * file name and the `generatedAt` field together, which is what makes the determinism
 * assertion in `DataExportTest` meaningful on the write path rather than only on the render
 * path.
 */
class DataExportViewModel(container: AppContainer) : ViewModel() {

    private val exportRepo = container.dataExportRepository
    private val plantRepo = container.plantRepository
    private val tentRepo = container.tentRepository

    /** Tents, for the picker. Observed. */
    var tents by androidx.compose.runtime.mutableStateOf<List<com.trichome.app.data.entity.GrowTent>>(emptyList())
        private set

    /** Plants, for the picker. Observed. */
    var plants by androidx.compose.runtime.mutableStateOf<List<com.trichome.app.data.entity.Plant>>(emptyList())
        private set

    /**
     * Whether the picker can be opened.
     *
     * Both lists are read once in the constructor rather than observed: the picker is a modal
     * that the grower opens, reads and dismisses, and a subscription that outlives it would
     * hold a Flow collector for a dialog that is already gone. The lists are re-read by
     * [loadTargets] on every open, which is when a new plant has to appear.
     */
    var isReady by mutableStateOf(false)
        private set

    /** Re-reads the exportable subjects. Called when the picker is about to open. */
    fun loadTargets() {
        viewModelScope.launch {
            tents = tentRepo.getAllTentsSnapshot()
            plants = plantRepo.getPlantsSnapshot()
            isReady = tents.isNotEmpty() || plants.isNotEmpty()
        }
    }

    /**
     * Runs the export.
     *
     * @return [ExportResult.Success] with the written file's absolute path, or
     *   [ExportResult.Failure] carrying a Spanish sentence the panel prints as-is. The
     *   failure case never leaves a partial file: `ExportFileWriter` renames a complete
     *   temporary file into place, so a `.json` on disk is always whole.
     */
    suspend fun export(
        scope: ExportScope,
        subjectId: Long,
        subjectName: String,
        directory: File,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ExportResult = exportRepo.export(scope, subjectId, subjectName, nowMillis, zone, directory)
}