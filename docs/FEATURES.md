# Features

V1.0.1 — menu completo de funcionalidades, agrupado por fase de desarrollo.

## Fase 1 — Base y apariencia

- Tema Compose propia con 4 paletas: **Brote Verde** (default), **Cosecha de Otoño**, **Cuidado Nocturno**, **Invernadero Soleado**.
- **Tipografía configurable**: familia (systema / serif / monoespaciada), peso y escala, aplicadas en vivo y persistidas en DataStore.
- **Contraste dinámico**: color de texto, fondo y borde del vidrio configurables de forma independiente, con cálculo de legibilidad (`readableOn`) para que el texto nunca se pierda sobre un panel claro u opaco.
- **Barra inferior glassmorphic fija, flotante y traslúcida**, presente en las 7 pestañas.
- El desenfoque (`Modifier.blur`) se aplica al fondo, **nunca** al contenedor de la tarjeta: difuminar el propio contenedor difuminaba su subárbol entero y por eso temas y fuentes no se veían.

## Fase 2 — Carpas y plantas

- CRUD de carpas (nombre, ubicación, capacidad, tipo/potencia de luz, activo) **con alta desde la propia pantalla**.
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
- **Guardado bloqueado y explícito** cuando no hay ninguna planta: antes el registro se descartaba en silencio y el usuario creía que se había guardado.

## Fase 6 — Recordatorios recurrentes

- Recordatorios semanales o cada N días con hora configurable.
- **Alarmas reales del sistema** (`AlarmManager.setAlarmClock`) con re-armado tras cada disparo, `setAndAllowWhileIdle` como fallback, y re-armado automático tras reiniciar el dispositivo (`RECEIVE_BOOT_COMPLETED`).
- WorkManager se mantiene como barrido periódico de seguridad (`ReminderReschedulerWorker`).

## Fase 7 — Calendario mensual

- Cuadrícula mensual con marcadores por día (eventos y tareas), filtro por planta y detalle diario.
- **Alta de eventos y recordatorios desde la propia pantalla** (FAB + hoja de captura, con validación de planta, tipo de evento y descripción). Antes el calendario era de solo lectura.

## Fase 8 — Biblias y breeding

- **Terpenos**: **158 compuestos** desde `terpenes.json`, cada uno con aroma, sabor, efectos, propiedades médicas, **mecanismo farmacológico, biosíntesis, toxicidad**, punto de ebullición, cepas, fuentes vegetales, nível de acumulación y enlaces de sinergia (*entourage*).
- **Ficha de detalle** por terpeno y **trivia** con XP.
- **Gamificación de la enciclopedia**: XP por descubrimiento, por familia completada y por trivia correcta; niveles, títulos de rango e insignias. Progreso persistido en DataStore.
- **Breeding**: generaciones (F1–F5, IBL, feminizada, retrocruce), técnicas y glosario (desde `breeding.json`); proyectos y cruces persistidos con scoring fenotípico.

## Fase 9 — Gráficas y gamificación

- Gráficas **Canvas nativas** (sin librerías externas): pH, EC, temperatura, humedad y altura a lo largo del cultivo, con filtro por planta.
- **Gamificación**: XP por registro (10–50 según tipo), niveles, racha diaria y logros semilla.

## Fase 10 — Diagnóstico por foto

- Captura **en proceso** con CameraX (`CameraCaptureActivity`) o galería (`GetContent` + copia a caché), previsualización con Coil. La ruta anterior con `TakePicturePreview` y `ACTION_IMAGE_CAPTURE` crasheaba al pulsar la cámara.
- **Análisis de imagen determinista**: `PhotoAnalyzer` extrae clorosis, necrosis, densidad de manchas, tricomas, telarañas y salud verde del fotograma; la pantalla muestra las mediciones, no solo el veredicto.
- **Motor de reglas local** (`DiagnosisEngine`): 49 condiciones (deficiencias, excesos/estrés, plagas, hongos) + saludable, con certeza ponderada.
- `PhotoDiagnosisEngine` puntúa cada foto contra los umbrales `photoEvidence` del catálogo y combina ambos motores: los síntomas que el usuario marcó prevalence sobre la foto, y la foto decide cuando los síntomas dicen "sano".
- Reporte glassmorphic con **causa, plan de acción y prevención** desde `diagnostics.json`, y **ficha de la enfermedad y su solución** al seleccionarla.
- **Registrar en Bitácora** con un toque (evento `DIAGNOSIS`/`PEST_CONTROL` + imagen asociada).

## Onboarding

- Tour de bienvenida que **desaparece a la tercera apertura** (contador en DataStore), sin dejar de ejecutarse para siempre ni desaparecer antes de tiempo.

## Navegación

- **7 pestañas** en la barra inferior: Inicio, Carpas, Bitácora, Terpenos, Calendario, Diagnóstico, Ajustes. Terpenos y Calendario antes solo eran accesibles por enlace profundo.

## Transversales

- UI 100% en español; código y commits en inglés.
- WorkManager con `TrichomeWorkerFactory` (ningún worker crea su propia BD).
- Notificaciones con icono `ic_stat_leaf` propio.
- Manual DI con `AppContainer` + `viewModelFactory` (sin Hilt/Koin).
- **Estado reactivo real**: las pantallas recogen sus `StateFlow` con `collectAsState()`. Leer `.value` durante la composición congelaba la lista en su primer valor y la UI nunca se actualizaba al cambiar la base de datos.
