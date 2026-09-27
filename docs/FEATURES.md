# Features

V1.0.0 — menu completo de funcionalidades, agrupado por fase de desarrollo.

## Fase 1 — Base y apariencia

- Tema Compose propia con 4 paletas: **Brote Verde** (default), **Cosecha de Otoño**, **Cuidado Nocturno**, **Invernadero Soleado**.
- Persistencia de apariencia en DataStore (opacidad, blur, tema, fuentes, acento).

## Fase 2 — Carpas y plantas

- CRUD de carpas (nombre, ubicación, capacidad, tipo/potencia de luz, activo).
- CRUD de plantas dentro de una carpa (cepa, etapa, fecha de inicio, activa).
- Reordenamiento manual dentro de la carpa y validación de nombres en blanco (no se pueden guardar nombres vacíos).
- `daysInGrow` corregido: **hoy = Día 1** (off-by-one resuelto y cubierto por tests).

## Fase 3 — Protocolos por bloques

- Editor de protocolos definidos como **bloques de etapas ordenados** (nombre + días por bloque), con presets de fotoperiodo 18/6 · 12/12 · 24/0 · custom.
- Registro de transiciones de etapa (bitácora de `stage_entries`).
- Progreso de etapa calculado por `StageProgressEngine` (días en etapa, % de avance global, días restantes).

## Fase 4 — Motor SuperCycle

- Cálculo matemático puro: súper-día, fase LUZ/OSCURIDAD, % restante de fase, horas restantes, días calendario.
- Presets de fotoperiodo y sliders en vivo; persistencia por planta.
- `CycleCheckWorker` para notificaciones de fases.

## Fase 5 — Bitácora (16 tipos de evento)

- Riego, Fertilización, Poda, Trasplante, Entrenamiento, Control de Plagas, Altura, Distancia de Lámpara, Flush, Defoliación, VPD, Revisión de Tricomas, Temp/Humedad, Cosecha, Cría y Diagnóstico.
- **Formularios dinámicos** por tipo con campos específicos (pH, EC, cantidad, altura, VPD…).
- Registro **multi-planta** (grupo `groupId`) desde la bitácora global.

## Fase 6 — Recordatorios recurrentes

- Recordatorios semanales o cada N días con hora configurable.
- WorkManager: `ReminderSchedulerWorker` dispara la notificación y **re-encola el siguiente ciclo** (`reminder_due_<id>`), con barrido periódico `ReminderReschedulerWorker`.

## Fase 7 — Calendario mensual

- Cuadrícula mensual con marcadores por día (eventos y tareas), filtro por planta y detalle diario.

## Fase 8 — Biblias y breeding

- **Terpenos**: 10 terpenos con aroma, efectos, punto de ebullición y cepas; buscador y favoritos (desde `terpenes.json`).
- **Breeding**: generaciones (F1–F5, IBL, feminizada, retrocruce), técnicas y glosario (desde `breeding.json`); proyectos y cruces persistidos con scoring fenotípico.

## Fase 9 — Gráficas y gamificación

- Gráficas **Canvas nativas** (sin librerías externas): pH, EC, temperatura, humedad y altura a lo largo del cultivo, con filtro por planta.
- **Gamificación**: XP por registro (10–50 según tipo), niveles, racha diaria y logros semilla.

## Fase 10 — Diagnóstico por foto

- Captura con cámara (`TakePicturePreview`) o galería (`GetContent`), previsualización con Coil.
- **Motor de reglas local** (`DiagnosisEngine`): 15 condiciones (deficiencias, excesos/estrés, plagas, hongos) + saludable, con certeza ponderada.
- TFLite presente con **fallback a reglas** si no hay modelo.
- Reporte glassmorphic con causa, plan de acción y prevención desde `diagnostics.json`.
- **Registrar en Bitácora** con un toque (evento `DIAGNOSIS`/`PEST_CONTROL` + imagen asociada).

## Transversales

- UI 100% en español; código y commits en inglés.
- WorkManager con `TrichomeWorkerFactory` (ningún worker crea su propia BD).
- Notificaciones con icono `ic_stat_leaf` propio.
- Manual DI con `AppContainer` + `viewModelFactory` (sin Hilt/Koin).