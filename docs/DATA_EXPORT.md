# Exportación de datos

**Formato:** JSON. **Versión del esquema:** 1 (`schemaVersion`). **Identificador:** `trichome.export`.

Este documento describe el archivo que produce *Ajustes → Exportar Datos*. Es la
especificación: el renderizador y este documento cambian juntos, y `DataExport.SCHEMA_VERSION`
es lo que un lector futuro comprueba antes de confiar en un campo.

---

## 1. Qué exporta

Dos alcances, y **cada uno incluye lo suyo y nada más**:

| Alcance | `scope` | Incluye |
| --- | --- | --- |
| Una planta | `PLANT` | esa planta, su bitácora, sus cambios de etapa, sus protocolos y sus recordatorios |
| Una carpa | `TENT` | la carpa, todas sus plantas, y todo lo que cuelga de cada una |

Un alcance de planta **no** arrastra la fila de la carpa ni el superciclo de la carpa: esos
pertenecen a la carpa, y esta app ya tuvo dos충돌 de fuentes duplicadas (el punto de ebullición
de F1, los dos modelos de temperatura de F2). El nombre de la carpa sí aparece, como texto,
porque un lector humano necesita saber a qué carpa pertenece la planta.

---

## 2. Determinismo

Los mismos datos producen **bytes idénticos**. Tres reglas lo garantizan:

1. **Cada lista se ordena por su clave primaria** al construir el documento
   (`ExportDocument.of`), así que el orden de filas de SQLite no puede colarse.
2. **Cada objeto escribe sus claves en un orden declarado**, no el orden de iteración de un mapa.
3. **Los números se formatean en `Locale.US` con cuatro decimales fijos.** Un teléfono en
   español formatea `0.84` como `0,84`, y una coma dentro de un número JSON es un error de
   sintaxis, no un número.

El único campo que cambia entre dos exportaciones de datos sin cambios es `generatedAt`, que es
el instante que el cultivador pidió. El nombre del archivo lleva ese mismo instante, así que
dos exportaciones nunca se pisan y sí se pueden comparar byte a byte.

---

## 3. Estructura

```json
{
  "schema": "trichome.export",
  "schemaVersion": 1,
  "scope": "TENT",
  "scopeLabelEs": "Una carpa entera",
  "generatedAt": 1753000000000,
  "subjectName": "Carpa 4",
  "tents": [ ... ],
  "plants": [ ... ],
  "events": [ ... ],
  "stageEntries": [ ... ],
  "protocols": [ ... ],
  "protocolStages": [ ... ],
  "reminders": [ ... ],
  "superCycles": [ ... ],
  "achievements": [ ... ],
  "breedingProjects": [ ... ],
  "breedingCrosses": [ ... ]
}
```

**Las trece secciones están siempre presentes**, incluso vacías. Un lector que no encuentra
`plants` tiene que decidir si el cultivador no tenía plantas o si esta versión no escribe esa
sección; `[]` responde a la pregunta.

Un valor ausente es un `null` explícito, no una clave omitida. `"exitedAt": null` le dice a un
lector que esa entrada de etapa sigue abierta; una clave ausente lo deja adivinar entre "nunca
se estableció" y "esta versión no lo escribe".

### 3.1 Campos por sección

**`tents`** — `id`, `name`, `location`, `capacity`, `lightType`, `lightPowerWatts`, `isActive`

**`plants`** — `id`, `name`, `tentName` (null si la planta no está en ninguna carpa),
`sortOrder`, `growStartAt`, `currentStage`, `strain`, `notes`, `isActive`, `createdAt`

**`events`** — `id`, `plantName`, `eventType`, `timestamp`, `notes`, `temperature`, `humidity`,
`ph`, `ec`, `amount`, `height`, `lampDistance`, `trainingType`, `defoliationLevel`, `vpd`,
`vpdSource`, `vpdLeafOffset`, `trichomeMaturity`, `diagnosisResult`, `diagnosisCertainty`,
`isActive`, `photoName`

**`stageEntries`** — `id`, `plantName`, `stageName`, `enteredAt`, `exitedAt` (null = abierta),
`isOpen`

**`protocols`** — `id`, `plantName`, `name`, `lightHours`, `darkHours`, `presetType`,
`cycleStartAt`, `isActive`, `vpdBand`, `phRange`, `ecRange`, `lightTempCelsius`,
`lightHumidityPercent`, `darkTempCelsius`, `darkHumidityPercent`, `ppfd`, `dli`, `lightType`,
`lampPowerWatts`, `substrateType`, `wateringStrategy`, `observations`

**`protocolStages`** — `id`, `protocolName`, `stageName`, `durationDays`,
`recurrenceIntervalDays`, `sortOrder`

**`reminders`** — `id`, `plantName` (null si el recordatorio no pertenece a una planta),
`title`, `message`, `recurrenceType`, `recurrenceIntervalDays`, `reminderTime`, `isActive`

**`superCycles`** — `id`, `tentName`, `plantName`, `lightHours`, `darkHours`, `cycleStartAt`,
`presetType`

**`achievements`** — `id`, `name`, `description`, `icon`, `xpReward`, `isUnlocked`

**`breedingProjects`** — `id`, `name`, `motherId`, `fatherId`, `generation`, `createdAt`, `status`

**`breedingCrosses`** — `id`, `projectName`, `parent1`, `parent2`, `phenotypeScore`, `notes`

---

## 4. `vpdSource`: por qué el origen viaja con el número

`vpdSource` toma uno de tres valores:

| Valor | Significado |
| --- | --- |
| `MEASURED` | Lectura del instrumental del propio cultivador, tal como la anotó |
| `CALCULATED` | Calculado por la app con la temperatura y la humedad que anotó |
| `UNKNOWN` | Anotado antes de que la app guardara el origen; no se puede decir cuál era |

`UNKNOWN` **no** significa "medido". Las filas anteriores al esquema v5 tienen `vpdSource` en
NULL, y algunas de Those numbers las escribió el cultivador mientras otras las derivó una
versión anterior; la columna no puede distinguirlas. Exportarlas como `MEASURED` publicaría cada
número derivado como si fuera una lectura de sensor, que es exactamente el defecto que
`EstimatedClimate` ya previene en la tarjeta de clima.

Un valor `CALCULATED` **debe** traer `vpdLeafOffset`, que es la diferencia hoja-aire con la que
se obtuvo. Sin él el número es un huérfano: no se puede reproducir, solo creer. Un valor
`MEASURED` no lleva desplazamiento, porque una lectura de instrumental no lo tiene detrás.

---

## 5. Qué NO contiene

El archivo se escribe en el almacenamiento externo de la app, así que cualquier cosa que pueda
leer ese directorio puede leerlo. La regla es: **lo que el cultivador registró, y nada sobre el
dispositivo ni sobre el almacenamiento de la app.**

- **`photoName` es solo el nombre del archivo.** El valor almacenado en `grow_events.imagePath`
  es una ruta absoluta dentro del almacenamiento privado de la app, y esa ruta revela la
  estructura de directorios de la app y las carpetas visibles del dispositivo. El nombre de la
  foto sí es del cultivador — él lo nombró — así que el nombre se queda y la ruta no.
- **No hay `room_master_table`.** El hash de identidad de Room no es del cultivador ni le
  pertenece publicarlo.
- **No hay identificadores del dispositivo**, ni huella de compilación, ni configuración regional
  ni zona horaria. El único instante del archivo es `generatedAt`, que el cultivador pidió.

Todo lo demás se exporta literal, incluidas las columnas que parecen internas: `isActive`,
`sortOrder`, `defoliationLevel`, `diagnosisCertainty`. Omitirlas produciría un archivo que no
describe los datos que el cultivador tiene.

---

## 6. Atomicidad de la escritura

**Un archivo terminado en `.json` está completo. Siempre.**

Los bytes se escriben en `trichome-export-<alcance>-<instante>.json.part`, se vacían con
`flush()` y `fsync()`, y solo entonces sustituyen al `.json` mediante un renombrado. Un fallo, un
disco lleno o una excepción dejan el `.part` y **nunca tocan el destino**. El `.part` se borra en
la ruta de fallo, y si ese borrado fallara, el nombre sigue diciendo `.part`.

La razón: un exportador a medio escribir que parece completo es peor que no exportar nada,
porque el siguiente paso del cultivador sería borrar los datos de la app creyendo que la copia
de seguridad sirve.

El archivo se escribe en el directorio de documentos externo de la app. No requiere permiso en
ningún nivel de API de 26 a 36. La ruta se muestra al cultivador antes y después de exportar.

---

## 7. Por qué no hay PDF

Solo se exporta en JSON. No hay ninguna librería de PDF en el grafo de dependencias de este
proyecto, y Android no tiene un generador de PDF para contenido arbitrario. Un PDF requeriría
**añadir una dependencia**, y eso es una decisión del propietario de la app, no de esta fase
(`AGENTS.md` §11). El JSON contiene todos los mismos datos.
---

## 8. Verificacion

- `DataExportTest` (JVM): determinismo byte a byte, orden de claves, las tres procedencias de
  VPD, la reduccion de la ruta de la foto a su nombre, la ausencia de internals de Room, el
  escapado de comillas y saltos de linea, y los acentos espanoles.
- `ExportFileWriterTest` (JVM): la escritura real sobre archivos reales, incluido el camino de
  fallo, que no deja ningun `.json` y borra el `.part`.
- `VpdHistoryStructureTest` (JVM): el panel de exportacion escribe unicamente a traves de
  `ExportFileWriter`, y el dialogo limita su desplazamiento.

Lo que **no** esta verificado: `DataExportRepository` necesita una base de datos real, asi que
el camino desde las filas hasta el JSON esta cubierto por pruebas de modelo y de escritura pero
**no se ha ejecutado nunca**. No hay dispositivo conectado (`adb devices` devuelve vacio).