package com.trichome.app.ui.screens.journal

import com.trichome.app.model.EventType

object EventTypeUi {
    val ALL: List<EventType> = EventType.entries

    fun labelResolved(storageKey: String): String = when (storageKey) {
        "IRRIGATION" -> "💧 Riego"
        "FERTILIZATION" -> "🧪 Fertilización"
        "PRUNING" -> "✂️ Poda"
        "TRANSPLANT" -> "🪴 Trasplante"
        "TRAINING" -> "🎯 Entrenamiento"
        "PEST_CONTROL" -> "🛡️ Control de Plagas"
        "HEIGHT" -> "📏 Altura"
        "LAMP_DISTANCE" -> "💡 Distancia Lámpara"
        "FLUSHING" -> "🌊 Flush"
        "DEFOLIATION" -> "🍃 Defoliación"
        "VPD" -> "🌡️ VPD"
        "TRICHOME_CHECK" -> "🔬 Revisión Tricomas"
        "TEMPERATURE_HUMIDITY" -> "🌤️ Temp/Humedad"
        "HARVEST" -> "🌿 Cosecha"
        "BREEDING" -> "🧬 Cría"
        "DIAGNOSIS" -> "🩺 Diagnóstico"
        else -> storageKey
    }

    fun label(type: EventType): String = labelResolved(type.storageKey)
}