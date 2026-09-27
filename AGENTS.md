# 🤖 AGENTS.md — Estándares de Código y Protocolo de Desarrollo con Gentle-AI

Este documento define las reglas de arquitectura, convenciones de código, restricciones técnicas y flujos de trabajo autónomos para cualquier agente de Inteligencia Artificial (**Gentle-AI**) que interactúe con el repositorio de **Trichome App**.

---

## 📌 1. Visión General y Misión del Agente

**Trichome App** es una aplicación Android nativa offline-first para la gestión integral de cultivos, enciclopedia de terpenos, análisis por visión artificial de maduración de tricomas y monitoreo mediante fotoperiodos y ciclos lunares.

### Misión Principal del Agente:
1. Implementar código modular, escalable, libre de errores de compilación y altamente documentado.
2. Mantener la arquitectura estricta sin introducir dependencias innecesarias o patrones no autorizados.
3. Garantizar la persistencia y la no-destrucción de datos del usuario bajo cualquier actualización de software.

---

## 🛠️ 2. Stack Tecnológico y Reglas Estrictas

| Componente | Tecnología / Librería | Regla de Oro |
| :--- | :--- | :--- |
| **Lenguaje** | Kotlin 1.9+ | Corrutinas + Flow para reactividad. |
| **UI Framework** | Jetpack Compose + Material 3 | Interfaz con sistema **Glassmorphic** configurable. |
| **Package Name** | `com.trichome.app` | **ESTRICTO:** Prohibido cambiar o modificar el package ID. |
| **Inyección de Dependencias** | Manual vía `AppContainer` | **PROHIBIDO:** No usar Hilt, Koin ni Dagger. |
| **Base de Datos** | Room Database | Migraciones explícitas v1→v2. `exportSchema = true`. |
| **Persistencia KV** | DataStore Preferences | Para ajustes de UI, opacidad, contraste y temas. |
| **Procesamiento de Imagen** | OpenCV + TensorFlow Lite | Análisis local offline de tricomas y salud foliar. |
| **Hardware Externo** | USB Camera (UVC Driver Open-Source) | Soporte para endoscopios / lupas digitales USB. |
| **Tareas en Segundo Plano** | WorkManager | Inyección estricta de repositorios mediante `AppContainer`. |
| **Carga de Imágenes** | Coil | Renderizado eficiente de galerías y capturas. |

---

## 🌐 3. Convención de Idiomas e Identificadores

- **Interfaz de Usuario (UI / String Resources):** Todo en **Español** (`values/strings.xml`, labels, tooltips, notificaciones y diálogos).
- **Código Fuente:** Todo en **Inglés** (clases, interfaces, métodos, variables, atributos, enums, comentarios de código, nombres de tablas, commits y documentación técnica).

---

## 🏗️ 4. Arquitectura de Software y Capas

com.trichome.app/
├── data/
│   ├── local/          # Room DB, DAOs, Entities, DataStore
│   ├── model/          # Enums de negocio y data classes puras
│   └── repository/     # GrowRepository (Única fuente de verdad)
├── di/
│   └── AppContainer.kt # Contenedor manual de inyección de dependencias
├── domain/
│   ├── engine/         # Motor de Superciclo, Motor Lunar, Motor de Diagnóstico
│   └── usecase/        # Casos de uso específicos
├── ui/
│   ├── components/     # Tarjetas Glassmorphic, Modales, Canvas Nativos
│   ├── theme/          # Sistema de Color, Tipografía, Glassmorphism Tokens
│   ├── viewmodel/      # ViewModels e Inicializadores (viewModelFactory)
│   └── screens/        # Pantallas (Terpenes, Trichomes, Calendar, Log, etc.)
├── vision/             # Clasificador TFLite, Filtros OpenCV, Varianza Laplacian
└── worker/             # Workers de WorkManager


### Reglas de Arquitectura:
1. **Acceso Unificado a Datos:** Toda la interacción con la base de datos se realiza a través de `GrowRepository`.
2. **Prohibición de Instanciación en Workers:** Ningún `Worker` debe instanciar `AppDatabase` ni repositorios de forma independiente. Deben consumir las instancias de `AppContainer`.
3. **Desacoplamiento de Enums:** Los enums de negocio residen en `data/model/`. La representación visual (íconos, textos traducidos) reside únicamente en `EnumsUi`.

---

## 🎛️ 5. Sistema de Diseño Glassmorphic y Contraste Dinámico

Para resolver problemas de legibilidad, el sistema Glassmorphism debe respetar el siguiente contrato:

```kotlin
// Contrato de Configuración Glassmorphic
data class GlassThemeConfig(
    val panelOpacity: Float = 0.15f,      // Ajustable por el usuario (0.05f a 0.50f)
    val blurRadius: Dp = 16.dp,           // Ajustable por el usuario (0.dp a 32.dp)
    val customTextColor: Color,           // Personalizable para garantizar contraste
    val customBackgroundColor: Color,     // Personalizable
    val customBorderColor: Color,         // Personalizable para reflejo de vidrio
    val accentColor: Color                // Color de acento para botones e indicadores
)
Legibilidad Estricta: El texto debe calcular dinámicamente su contraste contra el fondo. Si el vidrio es muy claro u opaco, la UI debe ofrecer la opción de elegir colores personalizados para texto, fondo y bordes.

Grids / Cuadrículas: Las Acciones Rápidas y los Tipos de Eventos deben maquetarse en cuadrículas de tarjetas (LazyVerticalGrid).

Navegación Inferior (BottomBar): Debe mantenerse fija, flotante y traslúcida, con íconos de alto contraste.

💾 6. Persistencia, Migraciones y Retención de Datos
Migración No Destructiva:

La base de datos NUNCA debe destruirse (fallbackToDestructiveMigration() está estrictamente prohibido en release).

Toda alteración en esquemas requiere una clase Migration(from, to) explícita y su test unitario correspondiente.

Respaldo de Actualización:

Al actualizar la app, la estructura de la base de datos Room y las preferencias de DataStore deben preservarse intactas.

🔬 7. Módulos Especializados
7.1. Visor y Análisis de Tricomas (Open Code / Vision)
TFLite Model: Integrar modelos cuantitativos locales para clasificar tricomas: Clear (Transparente), Milky (Lechoso), Amber (Ámbar).

OpenCV processing: Usar algoritmos locales para calcular varianza de Laplacian (asistente de enfoque) y segmentación de color en espacio HSV.

UVC Support: Integrar drivers universales para microscopios y lupas USB externos.

7.2. Biblia de Terpenos
Carga de datos local/offline desde assets/data/terpenes.json.

Integración de Rueda interactiva, Calculadora de Sinergia y filtro de Alérgenos.

7.3. Motor de Superciclo y Ciclo Lunar
Cálculo puramente matemático e independiente del tiempo del sistema local para determinar fase LUZ/OSCURIDAD.

Sincronización astronómica diaria para fases lunares e indicadores de eventos lunares en el calendario.

🧪 8. Protocolo de Calidad y Pruebas Obligatorias
Antes de dar por completada cualquier tarea o generar un commit, Gentle-AI debe ejecutar y validar los siguientes comandos:

Bash
# 1. Verificación de formato y sintaxis Kotlin
./gradlew lintDebug

# 2. Compilación en modo Debug
./gradlew assembleDebug

# 3. Ejecución de Pruebas Unitarias
./gradlew test
Tests Unitarios Requeridos:
SupercycleEngineTest: Validación de fases fotoperiódicas y superdías.

RoomMigrationTest: Validación de migración de esquema v1 a v2.

TrichomeClassifierTest: Validación de inferencia TFLite y rangos de salida.

LunarEngineTest: Cálculo correcto de fase lunar e imágenes asociadas.

📦 9. Convención de Git y Commits
Gentle-AI debe estructurar sus entregas con commits atómicos siguiendo el estándar Conventional Commits:

feat(terpenes): add interactive terpene wheel and synergy calculator

fix(plant-detail): correct off-by-one error in daysInGrow calculation

refactor(workers): unify database access using AppContainer

feat(vision): integrate OpenCV Laplacian focus assistant for USB microscopes

docs(publishing): update store release preparation guidelines

📄 10. Mantenimiento de Fuentes de Verdad (Documentación)
Tras cada cambio arquitectónico, actualización de esquemas o nueva característica, Gentle-AI debe mantener actualizados los siguientes archivos de documentación:

docs/ARCHITECTURE.md

docs/DATA_MODEL.md

docs/FEATURES.md

docs/GLASSMORPHISM_DESIGN.md

README.md