# Glassmorphism Design

El lenguaje visual de Trichome App simula paneles de vidrio esmerilado flotando
sobre un fondo animado, sin romper legibilidad ni accesibilidad.

## Tokens

```kotlin
data class GlassTokens(
    val glassOpacity: Float = 0.15f,   // opacidad del "vidrio"  (0.05–0.50)
    val blurRadius: Float = 12f,       // desenfoque del fondo    (0–32dp)
    val accentColor: Color             // acento configurable
)
```

## Componentes (`ui/components/GlassmorphismComponents.kt`)

| Componente | Descripción |
| --- | --- |
| `GlassCard` | Superficie translúcida (`base.copy(alpha = glassOpacity)`), **borde de gradiente de 1dp** que simula el canto de un vidrio y esquinas redondeadas. |
| `FloatingOrbBackground` | Orbes de gradiente animados (flotación sinusoidal) detrás de todo; su color sigue al tema y al acento. |
| `GlassSlider` | Slider Material 3 con colores del acento para el motor de apariencia. |
| `GlassProgressIndicator` | Anillo de progreso con `Brush.sweepGradient` (glass ring). |
| `GlassChip` | Chips translúcidos seleccionables. |
| `GlassmorphicBottomBar` | Barra inferior fija, flotante y traslúcida, con iconos de alto contraste. Presente en las 7 pestañas. |

## El desenfoque va en el fondo, nunca en la tarjeta

`GlassCard` **no** aplica `Modifier.blur()` sobre su propio contenedor.

```kotlin
// Correcto: el desenfoque pertenece al fondo, debajo del panel.
Modifier.blur(themeState.glassTokens.blurRadius)   // en FloatingOrbBackground
Box(Modifier.background(base.copy(alpha = glassOpacity)))   // el panel, nítido
```

Aplicar `blur` al contenedor del panel difuminaba **su subárbol entero**: los
textos, las fuentes y los temas se veían borrosos y el usuario reportó
justamente que "no se ven los temas ni las fuentes". El desenfoque produce
efecto únicamente cuando algo hay *detrás* del cristal.

La barra inferior tenía el mismo problema por otra causa: forzaba
`Color.Black` como color de contenido, ignorando el tema activo. Ahora deriva
de `colorScheme.surface`, así que acompaña al tema y al acento.

## Temas (`ui/theme/TrichomeTheme.kt`)

- **🌿 Brote Verde** (default): verdes profundos + acento carpa.
- **🍂 Cosecha de Otoño**: ámbar/naranja cálidos.
- **🌙 Cuidado Nocturno**: azules/violetas oscuros.
- **☀️ Invernadero Soleado**: amarillos/verdes claros.

La paleta activa se persiste por índice en DataStore y se reconstruye con
`schemeFor(theme)` + `buildTypography(family, weight, scale)`.

## Legibilidad dinámica

Un panel de vidrio muy claro o muy opaco hace imposible leer el texto con los
colores por defecto. Por eso los tres colores del panel son configurables por
separado:

|Ajuste | Efecto |
| --- | --- |
| **Familia de fuente** | Systema, serif o monoespaciada |
| **Peso de fuente** | Normal a negrita |
| **Escala de fuente** | 0.85–1.30 (accesibilidad) |
| **Color de texto** | Se aplica sobre el panel tal como se ve |
| **Color de fondo del panel** | Para oscurecer o aclarar el cristal |
| **Color de borde** | Reflejo del canto del vidrio |
| **Color de acento** | Botones, indicadores y elementos activos |

`readableOn(background)` calcula el color de texto que mantiene contraste
sobre el fondo realmente elegido, en vez de asumir que siempre será oscuro.

## Motor de apariencia (Ajustes)

- **Opacidad del vidrio**: slider 0.05–0.50 (pasos finos sobre toda la UI).
- **Intensidad de desenfoque**: slider 0–32dp.
- **Color de acento**: swatches (Verde, Ámbar, Naranja, Cian, Violeta, Rosa) y
  selector libre.
- **Tipografía**: familia, peso y escala.
- Persistencia inmediata vía `TrichomeThemeState` → DataStore; la actividad la
  re-colecta al iniciar con `LaunchedEffect { themeState.collectFromRepository() }`.

## Principios

1. **Legibilidad primero**: los colores del panel son configurables y
   `readableOn` garantiza que el texto tenga contraste contra el fondo elegido,
   sea claro u opaco.
2. **El cristal se ve limpio**: nada de desenfoque sobre el contenido; el
   efecto va en la capa de fondo.
3. **Servicio gráfico**: los orbes y el blur son sutiles (opacidades bajas por
   defecto) y el desenfoque se puede desactivar a 0.
4. **Configurable**: cada parámetro del "vidrio" es ajustable en Ajustes, nunca
   forzado — requisito funcional cumplido con sliders, no con constantes.
5. **Consistencia**: las 7 pestañas comparten la misma barra inferior, de modo
   que la navegación no cambia de forma ni de posición entre pantallas.
