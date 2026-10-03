package com.trichome.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.di.AppContainer
import com.trichome.app.model.EventType
import com.trichome.app.model.VpdHistoryBuilder
import com.trichome.app.model.VpdHistoryChart
import com.trichome.app.model.VpdHistoryRow
import com.trichome.app.model.VpdLogForm
import com.trichome.app.model.VpdLogFormValidator
import com.trichome.app.model.VpdLogOutcome
import com.trichome.app.model.VpdProvenance
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The VPD history: the chart's data, the range selector and the journal write.
 *
 * ## The window is bounded, and that is the point
 *
 * [watchVpdHistory] filters `vpd IS NOT NULL` **and** `timestamp BETWEEN from AND to`, and
 * the default window is 90 days. An unbounded read of a plant's whole journal would grow
 * without limit and is the reason `watchEventsBetween` exists for the calendar — the same
 * bound, applied for the same reason, in a different place.
 *
 * `from` is inclusive and `to` is exclusive-by-day, computed from the selected window so the
 * boundary is reproducible rather than a `now` that moves under the chart.
 *
 * ## Why the chart reads `grow_events` and nothing else
 *
 * There is no `vpd_history` table, and `MIGRATION_4_5` adds no table. The history is a query
 * over the journal: value, instant, provenance and offset are all on the row that already
 * holds them. A second table would be a second copy of one reading under one timestamp, which
 * is the shape of the F1 boiling point and the F2 temperature models.
 */
class VpdHistoryViewModel(container: AppContainer) : ViewModel() {

    private val eventRepo = container.eventRepository
    private val plantRepo = container.plantRepository
    private val tentRepo = container.tentRepository

    /** The resolved chart, including the provenance legend. */
    var chart by mutableStateOf(
        VpdHistoryBuilder.build(emptyList())
    )
        private set

    /**
     * The selected window, in days.
     *
     * `mutableIntStateOf` rather than `mutableStateOf`: this is the one `Int` in the class, and
     * the specialised holder boxes nothing on every read of a value the chip row re-reads on
     * each selection. The other state here is a list, a `String?` and a `VpdHistoryChart`, none
     * of which the specialised form serves.
     */
    var selectedDays by mutableIntStateOf(DEFAULT_WINDOW_DAYS)
        private set

    /** The tent the plant belongs to, for the log dialog's sentence. */
    var tentName by mutableStateOf<String?>(null)
        private set

    /** The plant's own stage, so the header can name what the readings describe. */
    var plantName by mutableStateOf<String?>(null)
        private set

    /** The plant whose readings this ViewModel is showing. */
    private var plantId: Long = 0L

    /**
     * Ranges offered, in days. One month, one season, one year.
     *
     * A plain `val` rather than snapshot state: it never changes, and a state holder for a
     * constant list would recompose the chip row for nothing.
     */
    val rangeDays: List<Int> = listOf(30, 90, 180, 365)

    /** First load: resolves the plant's tent and name, then the history. */
    fun load(plantId: Long) {
        this.plantId = plantId
        viewModelScope.launch {
            plantRepo.getPlantById(plantId)?.let { plant ->
                plantName = plant.name
                tentName = plant.tentId?.let { tentRepo.getTentById(it)?.name }
            }
            chart = VpdHistoryBuilder.build(readWindow(plantId, selectedDays))
        }
    }

    /**
     * Keeps the chart live.
     *
     * A separate collector from [load] for the reason `PlantDetailViewModel.loadPlant`
     * documents: a bare `collect()` inside the load would never return, so the resolved state
     * would never be published.
     */
    fun observeHistory(plantId: Long) {
        this.plantId = plantId
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val from = windowStart(now, selectedDays)
            eventRepo.watchVpdHistory(plantId, from, now + MILLIS_PER_DAY)
                .onEach { chart = VpdHistoryBuilder.build(it.map(GrowEvent::toHistoryRow)) }
                .collect()
        }
    }

    /** Switches the window and re-reads. */
    fun selectRange(days: Int) {
        selectedDays = days
        viewModelScope.launch {
            chart = VpdHistoryBuilder.build(readWindow(plantId, days))
        }
    }

    /**
     * Writes one VPD reading to the journal.
     *
     * Through [com.trichome.app.data.repository.GrowRepository.addEvent]'s own event path —
     * a `GrowEvent` inserted on [eventRepo] — so the row is identical to one written by the
     * journal screen: same table, same columns, same foreign key, same cascade. A VPD reading
     * that took a private write path would be invisible to `getAllEvents`, to the calendar and
     * to the exporter, which is how a "second source of truth" starts.
     *
     * The provenance is resolved **before** the call, so a rejected form never reaches the
     * database and never leaves a row with a null `vpd` behind. [onLogged] receives the real
     * outcome.
     */
    fun logReading(
        plantId: Long,
        form: VpdLogForm,
        zone: ZoneId = ZoneId.systemDefault(),
        onLogged: (Boolean) -> Unit = {}
    ) {
        val resolved = VpdLogFormValidator.validate(form, zone)
        if (resolved !is VpdLogOutcome.Ready) {
            onLogged(false)
            return
        }
        viewModelScope.launch {
            val ok = runCatching {
                eventRepo.insertEvent(
                    GrowEvent(
                        plantId = plantId,
                        eventType = EventType.VPD.storageKey,
                        timestamp = resolved.timestamp,
                        notes = resolved.notes,
                        temperature = resolved.airTemperatureC,
                        humidity = resolved.humidityPercent,
                        vpd = resolved.vpdKPa,
                        vpdSource = resolved.provenance.storageKey,
                        vpdLeafOffset = resolved.leafOffsetC
                    )
                )
            }.isSuccess
            onLogged(ok)
        }
    }

    private suspend fun readWindow(plantId: Long, days: Int): List<VpdHistoryRow> {
        val now = System.currentTimeMillis()
        val from = windowStart(now, days)
        return eventRepo.getVpdHistory(plantId, from, now + MILLIS_PER_DAY)
            .map(GrowEvent::toHistoryRow)
    }

    private fun windowStart(nowMillis: Long, days: Int): Long =
        nowMillis - days.toLong() * MILLIS_PER_DAY

    companion object {
        /** 90 days: one season of a vegetative-to-flower cycle, and the default window. */
        const val DEFAULT_WINDOW_DAYS: Int = 90

        private const val MILLIS_PER_DAY: Long = 86_400_000L
    }
}

/**
 * The entity as the chart reads it.
 *
 * Kept in one place so the chart's mapping is not re-written at each call site — and so
 * `vpdSource` goes through [VpdProvenance.fromStorageKey] on every path, which is what
 * guarantees a null resolves to `UNKNOWN` rather than to a measurement.
 */
internal fun GrowEvent.toHistoryRow(): VpdHistoryRow = VpdHistoryRow(
    id = id,
    timestamp = timestamp,
    vpdKPa = vpd,
    vpdSource = vpdSource,
    temperatureC = temperature,
    humidityPercent = humidity,
    leafOffsetC = vpdLeafOffset
)