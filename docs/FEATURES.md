# Features

V1.1.0 — menu completo de funcionalidades, agrupado por fase de desarrollo.

## Fase 1 — Base y apariencia

- **Dos modos de appearance**, con interruptor en Ajustes → Apariencia:
  - **Translúcido** (4 paletas): **Brote Verde**, **Cosecha de Otoño**, **Cuidado Nocturno**, **Invernadero Soleado**.
  - **Opaco de alto contraste** (4 paletas): **Brote Verde Sólido**, **Cosecha Otoñal Sólida**, **Oscuro Extremo Sólido**, **Claro Solar Sólido**. Con el cristal desactivado cada panel se dibuja como superficie opaca con elevación y borde sólido, al mismo padding para que nada salte al cambiar.
  - El modo por defecto es el **opaco**: quien nunca eligió un aspecto obtiene el más legible.
- Los componentes de cristal **leen la configuración del tema** por `CompositionLocal`, con overrides anulables. Antes unas doce pantallas pasaban opacidades fijas que ignoraban la preferencia del usuario.
- **Tipografía configurable**: familia (systema / serif / monoespaciada / script), peso y escala, aplicadas en vivo y persistidas en DataStore.
- **Contraste verificado, no estimado**: las tintas se resuelven con `readableOnStrict`, que elige entre negro y blanco el que realmente contrasta más y garantiza ≥4.58:1 con cualquier color de acento. Los **bordos** tienen su propio listón de 3:1 (WCAG 1.4.11) porque no llevan texto, y los tests lo comprueban.
- **Barra inferior glassmorphic fija, flotante y traslúcida**, presente en las 7 pestañas.
- **Sin desenfoque en el contenido**: hay cero llamadas a `Modifier.blur()`. El cristal se produce con superficie tintada y borde de gradiente, porque difuminar el contenedor de una tarjeta difumina su propio subárbol — el bug que hizo que en v1.0.0 "no se vieran los temas ni las fuentes".
- Los límites de opacidad y profundidad viven en `GlassRanges`, el único sitio que los define.

## Fase 2 — Carpas y plantas

- CRUD de carpas (nombre, ubicación, capacidad, tipo/potencia de luz, activo) **con alta desde la propia pantalla**.
- CRUD de plantas dentro de una carpa (cepa, etapa, fecha de inicio, activa).
- Reordenamiento manual dentro de la carpa y validación de nombres en blanco (no se pueden guardar nombres vacíos).
- `daysInGrow` corregido: **hoy = Día 1** (off-by-one resuelto y cubierto por tests).
- **Tocar una planta abre esa planta.** Antes el `onOpen` de la tarjeta de carpa resolvía siempre la *primera* planta de la carpa, así que tocar la fila 2 abría la 1, y una carpa vacía navegaba a `plant_detail/-1`.
- **El detalle de planta tiene estados terminales** (`Loading` / `Success` / `Error`): búsqueda acotada, mensaje en español y botón de vuelta. Antes un id inexistente dejaba la pantalla en "Cargando planta…" **para siempre**.
- El progreso del protocolo se resuelve **después** de que la planta exista, no leyendo el campo desde otra corrutina: en frío nunca se veía la tarjeta de etapa.

## Fase 3 — Protocolos por bloques

- Editor de protocolos definidos como **bloques de etapas ordenados** (nombre + días por bloque), con presets de fotoperiodo 18/6 · 12/12 · 24/0 · custom.
- **Editar un protocolo ya no borra sus etapas.** El editor sembraba siempre un bloque por defecto de tres etapas y nunca leía las filas reales, así que guardar una edición sobrescribía el calendario completo.
- Registro de transiciones de etapa (bitácora de `stage_entries`).
- Progreso de etapa calculado por `StageProgressEngine` (días en etapa, % de avance global, días restantes).
- **Objetivo de VPD por etapa** (`protocol_stages.vpdTarget`, esquema v6). Es la banda que el
  cultivador apunta a **mientras la planta está en esa etapa**, y no una medición: no hay ningún
  sensor detrás de ningún VPD de esta app, así que la columna se llama `vpdTarget` y no `vpd`.
  Una etapa sin objetivo lee **"Sin definir"** en la tarjeta y sale como `null` en la
  exportación, nunca como `0,00 – 0,00`. La banda general del protocolo sigue en
  `protocols.vpdBand`: una es el rango del cultivo entero y la otra, la fase.
- **Los quince objetivos ya se pueden rellenar.** Hasta ahora se dibujaban, se exportaban y no se
  escribían: quince columnas de almacenamiento sin ninguna forma de llenarlas. Ahora cada grupo
  de la tarjeta (Ambiente · Intensidad de luz · Instalación y manejo) se toca y abre su propia
  pantalla, y cada fila de "Objetivo por etapa" abre la banda de esa etapa. Son dos superficies
  distintas porque son dos entidades distintas: la banda se pone en la etapa, no en el protocolo.
  Una banda son **dos casillas y un valor**: no se guarda hasta que las dos se rellenan y el mínimo
  no supera al máximo; una caja vacía se guarda como `null`, o sea "Sin definir", nunca como un `0`
  inventado en la misma tipografía métrica que un valor elegido. Y guardar el calendario de un
  protocolo **ya no destruye sus etapas**: la edición se reconoce por el id de la etapa, no por su
  nombre ni por su posición, así que solo desaparece la etapa que el cultivador borró de verdad.

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

## Seguridad — acciones irreversibles

- **Toda eliminación pide confirmación.** Carpas, plantas, eventos de bitácora,
  protocolos, proyectos de cría y cruces se borraban con un solo toque. No
  existía ningún patrón de confirmación: los ocho `AlertDialog` anteriores eran
  formularios de alta o edición.
- `ConfirmDestructiveDialog` es compartido y `DestructiveConfirmation` es el
  estado detrás de él. `request()` solo arma; `confirm()` es el único que
  escribe, así que el borrado solo es alcanzable desde el botón de confirmar.
  Una segunda pulsación **reemplaza** a la primera en vez de apilarse, para que
  el diálogo nunca nombre una fila que no sea la última tocada.
- Cada mensaje nombra la entidad y explica qué pasa con sus hijos, porque **no es
  uniforme**: borrar una carpa deja sus plantas (`Plant.tentId` es `SET_NULL`),
  mientras borrar una planta se lleva su historial de bitácora
  (`GrowEvent.plantId` es `CASCADE`).
- El botón de confirmar usa `colorScheme.error`, para que nunca parezca tan
  seguro como "Cancelar".