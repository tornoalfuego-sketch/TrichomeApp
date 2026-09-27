package com.trichome.app.model

/**
 * The 16 event types supported by the grow journal.
 * UI labels are provided in Spanish by [labelResKey] (string resource).
 */
enum class EventType(val storageKey: String, val labelResKey: String, val icon: String) {
    IRRIGATION("IRRIGATION", "irrigation", "💧"),
    FERTILIZATION("FERTILIZATION", "fertilization", "🧪"),
    PRUNING("PRUNING", "pruning", "✂️"),
    TRANSPLANT("TRANSPLANT", "transplant", "🪴"),
    TRAINING("TRAINING", "training", "🎯"),
    PEST_CONTROL("PEST_CONTROL", "pest_control", "🛡️"),
    HEIGHT("HEIGHT", "height", "📏"),
    LAMP_DISTANCE("LAMP_DISTANCE", "lamp_distance", "💡"),
    FLUSHING("FLUSHING", "flushing", "🌊"),
    DEFOLIATION("DEFOLIATION", "defoliation", "🍃"),
    VPD("VPD", "vpd", "🌡️"),
    TRICHOME_CHECK("TRICHOME_CHECK", "trichome_check", "🔬"),
    TEMPERATURE_HUMIDITY("TEMPERATURE_HUMIDITY", "temperature_humidity", "🌤️"),
    HARVEST("HARVEST", "harvest_event", "🌿"),
    BREEDING("BREEDING", "breeding_event", "🧬"),
    DIAGNOSIS("DIAGNOSIS", "diagnosis_event", "🩺");

    companion object {
        private val byKey = entries.associateBy { it.storageKey }

        fun fromKey(key: String): EventType = byKey[key] ?: DIAGNOSIS

        fun fromIndex(index: Int): EventType = entries[index.coerceIn(0, entries.size - 1)]

        /** Types that record quantitative time series (charts). */
        val metricTypes: Set<EventType> = setOf(
            TEMPERATURE_HUMIDITY, HEIGHT, IRRIGATION, FERTILIZATION, VPD
        )
    }
}