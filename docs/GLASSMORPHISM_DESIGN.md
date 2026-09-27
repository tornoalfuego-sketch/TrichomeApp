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
| `GlassCard` | Superficie translúcida (`base.copy(alpha = glassOpacity)`), `blur` en el contenido del fondo, **borde de gradiente de 1dp** que simula el canto de un vidrio y esquinas redondeadas. |
| `FloatingOrbBackground` | Orbes de gradiente animados (flotación sinusoidal) detrás de todo; su color sigue al tema y al acento. |
| `GlassSlider` | Slider Material 3 con colores del acento para el motor de apariencia. |
| `GlassProgressIndicator` | Anillo de progreso con `Brush.sweepGradient` (glass ring). |
| `GlassChip` | Chips translúcidos seleccionables. |

## Temas (`ui/theme/TrichomeTheme.kt`)

- **🌿 Brote Verde** (default): verdes profundos + acento carpa.
- **🍂 Cosecha de Otoño**: ámbar/naranja cálidos.
- **🌙 Cuidado Nocturno**: azules/violetas oscuros.
- **☀️ Invernadero Soleado**: amarillos/verdes claros.

La paleta activa se persiste por índice en DataStore y se reconstruye con
`lightColorScheme` + `buildTypography(fontScale)`.

## Motor de apariencia (Ajustes)

- **Opacidad del vidrio**: slider 0.05–0.50 (pasos finos sobre toda la UI).
- **Intensidad de desenfoque**: slider 0–32dp.
- **Color de acento**: swatches (Verde, Ámbar, Naranja, Cian, Violeta, Rosa).
- **Escala de fuente**: slider 0.85–1.30 (accesibilidad).
- Persistencia inmediata vía `TrichomeThemeState` → DataStore; la actividad la
  re-colecta al iniciar con `LaunchedEffect { themeState.collectFromRepository() }`.

## Principios

1. **Legibilidad primero**: el fondo de la app es oscuro en todos los temas; los
   paneles suben contraste con su borde de gradiente y tipografía contrastada.
2. **Servicio gráfico**: los orbes y el blur son sutiles (opacidades bajas por defecto).
3. **Configurable**: cada parámetro del "vidrio" es ajustable en Ajustes, nunca
   forzado — requisito funcional cumplido con sliders, no con constantes.