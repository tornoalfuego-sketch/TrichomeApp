# 🌿 Trichome App

Gestión y diagnóstico inteligente de cultivos de cannabis — Android nativo en **Kotlin + Jetpack Compose**.

> UI en español · Código y commits en inglés · Offline-first (todo el procesamiento es local)

## ✨ Funcionalidades

- **Carpas y plantas**: alta de carpas y plantas desde la propia pantalla, reordenamiento dentro de la carpa, días de cultivo (hoy = **Día 1**) y métricas.
- **Protocolos por bloques**: editor de protocolos definidos como bloques de etapas ordenadas con fotoperiodo.
- **Motor SuperCycle**: cálculo de súper-días, fase luz/oscuridad, % restante y re-siembra con presets 18/6 · 12/12 · 24/0 · custom.
- **Bitácora (16 tipos de evento)**: riego, fertilización, poda, trasplante, entrenamiento, control de plagas, altura, distancia de lámpara, flush, defoliación, VPD, revisión de tricomas, temp/humedad, cosecha, cría y diagnóstico. Formularios dinámicos y registro multi-planta (grupo). Si no hay ninguna planta, el guardado se bloquea con un aviso explícito en lugar de descartarse en silencio.
- **Recordatorios recurrentes**: alarmas **reales del sistema** (`AlarmManager.setAlarmClock`), re-armado tras cada disparo, re-armado tras reiniciar el dispositivo y `setAndAllowWhileIdle` como fallback. WorkManager queda como barrido periódico de seguridad.
- **Calendario mensual**: marcadores por día, filtro por planta, detalle diario y **alta de eventos y recordatorios** desde la propia pantalla.
- **Biblia de terpenos**: **158 compuestos** con aroma, sabor, efectos, propiedades médicas, **mecanismo farmacológico, biosíntesis, toxicidad**, punto de ebullición, cepas, fuentes vegetales, nivel de acumulación y enlaces de sinergia (*entourage*). Incluye ficha de detalle, trivia con XP y gamificación de la enciclopedia (niveles, rangos e insignias).
- **Breeding**: generaciones (F1–F5, IBL, feminizada, retrocruce), técnicas y glosario; proyectos y cruces con scoring fenotípico.
- **Diagnóstico inteligente (49 condiciones)**: captura con CameraX o galería, análisis de imagen determinista que muestra las mediciones tomadas, motor de reglas local, selección de síntomas, **ficha de la enfermedad con causa, plan de acción y prevención**, y registro directo en la bitácora.
- **Gráficas nativas en Canvas**: pH, EC, temperatura, humedad y altura a lo largo del cultivo.
- **Gamificación de cultivo**: XP por evento, niveles, racha de registro diario y logros.
- **Apariencia con dos modos**: 4 temas **translúcidos** y sus 4 equivalentes **opacos de alto contraste** (Brote Verde Sólido, Cosecha Otoñal Sólida, Oscuro Extremo Sólido, Claro Solar Sólido), con un interruptor para elegir. Bordes de gradiente, orbes flotantes animados, tipografía configurable (familia, peso, escala), color de texto / fondo / borde y acento personalizables, todo persistido con DataStore. Sin desenfoque sobre el texto: el cristal se produce con superficie tintada y borde, porque difuminar el panel difumina su propio contenido.
- **Onboarding** que desaparece a la tercera apertura.
- **7 pestañas** en la barra inferior: Inicio, Carpas, Bitácora, Terpenos, Calendario, Diagnóstico, Ajustes.

## 🛠️ Tecnologías

| Capa | Elección |
| --- | --- |
| UI | Jetpack Compose (Material 3) + Navigation Compose |
| Persistencia | Room 2.6 (migraciones explícitas v1→v6, `exportSchema = true`, sin `fallbackToDestructiveMigration`) |
| Preferencias | DataStore Preferences (apariencia, progreso de terpenos, onboarding) |
| Tareas en segundo plano | WorkManager (factory propia) + `AlarmManager` para recordatorios |
| Imágenes | Coil, CameraX |
| Visión local | `PhotoAnalyzer`: extracción de features en HSV sobre el bitmap, sin ML |
| DI | Manual (`AppContainer` + `viewModelFactory { initializer { ... } }`) — sin Hilt/Koin |

> **No hay modelo TFLite en este build.** No existe ningún `.tflite` en
> `assets`, así que el diagnóstico fotográfico es determinista: se extraen
> medidas del fotograma y se comparan contra los umbrales `photoEvidence` del
> catálogo. La pantalla muestra las mediciones, no solo el veredicto. Un modelo
> puede conectarse detrás de `PhotoAnalyzer` sin tocar el pipeline.

## 📦 Construcción

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --console=plain
```

> Ejecuta los tasks de Gradle de uno en uno: dos builds simultáneos sobre el
> mismo proyecto fallan con `Cannot access output property 'destinationDirectory'`.

Los cuatro tasks deben pasar antes de cerrar una entrega: **398 tests JVM**,
**0 errores de lint**, y ambos APKs generados.

APKs de salida: `app/build/outputs/apk/debug/app-debug.apk` y
`app/build/outputs/apk/release/app-release.apk`.

El release se firma con el `signingConfig` que lee `keystore.properties` (no
versionado, ver [docs/PUBLISHING.md](docs/PUBLISHING.md)). Sin ese archivo el
release sigue compilando pero produce un APK **sin firmar**, que Android rechaza
instalar.

### Catálogos de contenido

`app/src/main/assets/data/*.json` es la fuente de verdad que se distribuye. Se
regeneran con los scripts de `tools/`, que son re-ejecutables y se autovalidan:

```powershell
.\tools\convert_terpenes.ps1
.\tools\convert_diagnostics.ps1
```

Ambos scripts **fallan con error** si el JSON resultante no cumple el esquema
que la app deserializa, en vez de reportar éxito sobre un archivo roto.

## 🧪 Tests

398 tests JVM, todos ejecutables sin dispositivo:

`SuperCycleEngineTest` · `StageProgressEngineTest` · `DiagnosisEngineTest` · `EventTypeTest` · `GamificationTest` · `WorkManagerInitTest` · `PhotoAnalyzerTest` · `PhotoDiagnosisEngineTest` · `ReminderAlarmSchedulerTest` · `TerpeneProgressionTest` · `AssetCatalogTest` · `PlantDetailStateTest` · `TentNavigationTest` · `GlassConfigTest` · `DestructiveConfirmationTest` · `ProtocolBlocksTest` · `AboutInfoTest` · `CalendarWindowTest` · `ReminderCancellationTest` · `ReminderEditingTest` · `PlantEditFormTest` · `BreedingFormTest` · `TerpeneQuizTest` · `TerpeneXpTest` · `TerpeneBlenderTest` · `PunnettSquareTest` · `BreedingChapterTest` · `BreedingProgressTest` · `AccentPaletteTest` · `AppTopBarTest` · `HomeStatTileTest` · `AppNavigationRouteTest`

`AssetCatalogTest` deserializa ambos catálogos **con las mismas clases
`@Serializable` que usa la app**, de modo que un campo renombrado rompe el
build en lugar de publicar una enciclopedia que se renderiza vacía en silencio.

`GlassConfigTest` calcula la luminancia relativa desde los componentes ARGB en
vez de usar `Color.luminance()`, que bajo `isReturnDefaultValues = true` llegaría
a un método `android.graphics` sin mockear y compararía ceros: el test pasaría
sin comprobar nada. Los bordes tienen su propio listón de 3:1 (WCAG 1.4.11)
porque no llevan texto.

## 📚 Documentación

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — arquitectura, DI, workers, alarmas reales y pipeline de diagnóstico
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — modelo de datos Room, migración v1→v2 y esquemas de los catálogos
- [docs/FEATURES.md](docs/FEATURES.md) — catálogo de funcionalidades
- [docs/GLASSMORPHISM_DESIGN.md](docs/GLASSMORPHISM_DESIGN.md) — motor de apariencia
- [docs/ROADMAP.md](docs/ROADMAP.md) — roadmap
- [docs/PUBLISHING.md](docs/PUBLISHING.md) — publicación del APK como GitHub Release

## 🔒 Privacidad

La app funciona 100% offline: las fotos de diagnóstico se procesan localmente y no se envían a ningún servidor. No hay backend, ni analítica, ni cuenta de usuario.
