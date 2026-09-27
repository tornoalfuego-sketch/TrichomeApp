# 🌿 Trichome App

Gestión y diagnóstico inteligente de cultivos de cannabis — Android nativo en **Kotlin + Jetpack Compose**.

> UI en español · Código y commits en inglés · Offline-first (todo el procesamiento es local)

## ✨ Funcionalidades

- **Carpas y plantas**: CRUD de carpas, plantas por carpa con reordenamiento, días de cultivo (hoy = **Día 1**) y métricas.
- **Protocolos por bloques**: editor de protocolos definidos como bloques de etapas ordenadas con fotoperiodo.
- **Motor SuperCycle**: cálculo de súper-días, fase luz/oscuridad, % restante y re-siembra con presets 18/6 · 12/12 · 24/0 · custom.
- **Bitácora (16 tipos de evento)**: riego, fertilización, poda, trasplante, entrenamiento, control de plagas, altura, distancia de lámpara, flush, defoliación, VPD, revisión de tricomas, temp/humedad, cosecha, cría y diagnóstico. Formularios dinámicos y registro multi-planta (grupo).
- **Recordatorios recurrentes**: recordatorios semanales/cada N días re-encolados automáticamente por WorkManager.
- **Calendario mensual**: marcadores por día, filtro por planta y detalle diario.
- **Biblias**: terpenos (aroma, efectos, punto de ebullición, cepas) y breeding (generaciones, técnicas, glosario) cargadas desde `assets/data/*.json`.
- **Diagnóstico inteligente**: motor de reglas local (15 condiciones + saludable) con foto (cámara/galería), selección de síntomas, reporte glassmorphic y registro directo en la bitácora.
- **Gráficas nativas en Canvas**: pH, EC, temperatura, humedad y altura a lo largo del cultivo.
- **Gamificación**: XP por evento, niveles, racha de registro diario y logros.
- **Apariencia Glassmorphism**: 4 temas, paneles translúcidos con blur, bordes de gradiente, orbes flotantes animados, opacidad (0.05–0.50), blur (0–32dp), acento personalizable y escala de fuente (0.85–1.30), persistida con DataStore.

## 🛠️ Tecnologías

| Capa | Elección |
| --- | --- |
| UI | Jetpack Compose (Material 3) + Navigation Compose |
| Persistencia | Room 2.6 (migración explícita v1→v2, `exportSchema = true`) |
| Preferencias | DataStore Preferences |
| Tareas en segundo plano | WorkManager (factory propia, re-encolado de recordatorios) |
| Imágenes | Coil |
| Visión local | CameraX + MLKit + TensorFlow Lite (fallback a motor de reglas) |
| DI | Manual (`AppContainer` + `viewModelFactory { initializer { ... } }`) — sin Hilt/Koin |

## 📦 Construcción

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.20.101-hotspot"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest
```

APKs de salida: `app/build/outputs/apk/debug/` y `app/build/outputs/apk/release/`.

## 📚 Documentación

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — arquitectura, DI, workers y flujo de datos
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — modelo de datos Room y migración v1→v2
- [docs/FEATURES.md](docs/FEATURES.md) — catálogo de funcionalidades
- [docs/GLASSMORPHISM_DESIGN.md](docs/GLASSMORPHISM_DESIGN.md) — motor de apariencia
- [docs/ROADMAP.md](docs/ROADMAP.md) — roadmap post-1.0.0
- [docs/PUBLISHING.md](docs/PUBLISHING.md) — publicación del APK como GitHub Release

## 🔒 Privacidad

La app funciona 100% offline: las fotos de diagnóstico se procesan localmente (reglas + clasificador TFLite local). No se envían datos a ningún servidor.