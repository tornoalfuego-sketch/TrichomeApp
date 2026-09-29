# Glassmorphism Design

El lenguaje visual de Trichome App ofrece dos modos: paneles translúcidos sobre
un fondo animado, o superficies opacas de alto contraste. **Ambos son
legibles por diseño**; el usuario elige en Ajustes → Apariencia.

## Tokens

```kotlin
data class GlassTokens(
    val glassOpacity: Float = 0.15f,   // opacidad del "vidrio"  (0.05–0.50)
    val blurRadius: Float = 12f,        // profundidad de mezcla   (0–32)
    val accentColor: Color              // acento configurable
)
```

## El cristal se lee del tema, no de los call sites

`GlassCard` **no puede adivinar** qué eligió el usuario. Por eso el tema publica
la configuración por `CompositionLocal` y los componentes la consultan:

```kotlin
data class GlassConfig(
    val enabled: Boolean,
    val glassOpacity: Float,
    val blurRadius: Float,
    val accentColor: Color,
)
val LocalGlassConfig = staticCompositionLocalOf { ... }
```

Los tres parámetros de `GlassCard` son **overrides anulables**:

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glassOpacity: Float? = null,    // null = usar LocalGlassConfig
    blurRadius: Float? = null,
    accentColor: Color? = null,
    contentColor: Color? = null,
    ...
)
```

El motivo es histórico: antes eran `Float` no nulos con defaults hardcodeados, y
como `GlassCard` solo veía `MaterialTheme.colorScheme`, unos **doce call sites
pasaban literales** que ignoraban la preferencia del usuario — `0.10f` en
calendario y gráficas, `0.12f` en bitácora, `0.18f` en inicio, un `+0.08f`
derivado en diagnóstico, y tres pantallas que no pasaban nada. Cualquier ajuste
de apariencia se ignoraba en media app.

`enabled` **no** es un override: es la decisión del usuario y no puede ser
anulada desde una pantalla.

> Verificación: `grep -E '(glassOpacity|blurRadius)\s*=\s*[0-9]' app/src/main`
> no debe devolver nada.

## No hay desenfoque, y es deliberado

**Cero llamadas a `Modifier.blur()` en `app/src/main`.** El cristal se produce
con una superficie tintada más un borde de gradiente de 1dp que simula el canto
de un vidrio.

La razón está documentada en `GlassmorphismComponents.kt`: aplicar `blur` al
contenedor de un panel difumina **su propio subárbol**, que es exactamente el
bug que hizo que en v1.0.0 "no se vieran los temas ni las fuentes". El
desenfoque solo produce efecto cuando hay algo **detrás** del cristal, y en
Compose eso significa aplicarlo a la capa de fondo, no al panel.

Por eso `blurRadius` es un **factor de mezcla de color** (`blurRadius / 32f`) y
no un radio de desenfoque. Si alguna vez se quiere un desenfoque real, es una
decisión deliberada aparte, no un slider.

## Temas (`ui/theme/TrichomeTheme.kt`)

| Índice | Con cristal | Opaco |
| --- | --- | --- |
| 0 | 🌿 Brote Verde | 🌿 Brote Verde Sólido |
| 1 | 🍂 Cosecha de Otoño | 🍂 Cosecha Otoñal Sólida |
| 2 | 🌙 Cuidado Nocturno | 🌙 Oscuro Extremo Sólido |
| 3 | ☀️ Invernadero Soleado | ☀️ Claro Solar Sólido |

`activeSchemeFor(theme, accent, glassEnabled)` es el **único** punto donde la
banderja elige el esquema, para que ninguna pantalla pueda seguir pintando la
paleta de cristal mientras el resto pinta la opaca.

### Contraste de los temas opacos

Un panel muy claro o muy opaco hace imposible leer el texto con los colores por
defecto, y el acento es un color **elegido por el usuario**, no algo que la app
pueda dar por sentado. Por eso las tintas se resuelven con
`readableOnStrict`, que elige entre negro y blanco **el que realmente contrasta
más**:

```kotlin
fun readableOnStrict(color: Color): Color =
    if (contrastRatio(SolidInk, color) >= contrastRatio(SolidIvory, color)) SolidInk else SolidIvory
```

Eso garantiza ≥4.58:1 para **cualquier** entrada. El `readableOn` anterior usa un
umbral de luminancia `> 0.45f` y **no garantiza ninguna ratio**: de ahí venían
los emparejamientos ilegibles.

Los bordes llevan su propio listón, porque un borde no lleva texto:

| Rol | Listón | WCAG |
| --- | --- | --- |
| `onSurface`, `onBackground`, `onPrimary` | 4.5:1 | 1.4.3 texto |
| `outline`, `outlineVariant` | 3:1 | 1.4.11 no textual |

La primera tanda de paletas opacas pasó **todas** las aserciones de texto con
bordes en 2.55:1 (Nocturno), 2.65:1 (Otoñal) y 3.45:1 (Brote), y divisores
internos en 1.39–1.95:1. Nada miraba un rol no textual, así que "bordes de alto
contraste" estaba verificado solo para las palabras dibujadas encima. Corregido
y ahora cubierto por `GlassConfigTest`.

> `ColorScheme` de Material 3 **no sobrescribe `equals`**: cada rol es un
> `mutableStateOf` y la clase hereda la igualdad por referencia de `Any`.
> Comparar dos esquemas construidos por separado con `assertEquals` falla
> siempre, y con `assertNotEquals` pasa siempre. Los esquemas se comparan rol por
> rol, donde `Color` sí es una value class con igualdad real.

## Componentes (`ui/components/`)

| Componente | Descripción |
| --- | --- |
| `GlassCard` | Superficie translúcida con borde de gradiente de 1dp, o `Surface` opaca con elevación y borde sólido cuando el cristal está desactivado. Mismo padding en ambos modos, para que nada salte al cambiar. |
| `GlassChip` | Chips translúcidos seleccionables. Sigue el acento (antes quedaba fijo en verde). |
| `GlassSlider` | Slider Material 3 con colores del acento. |
| `GlassProgressIndicator` | Anillo de progreso con `Brush.sweepGradient`. |
| `GlassmorphicBottomBar` | Barra inferior fija, flotante y traslúcida, con iconos de alto contraste. Presente en las 7 pestañas. |
| `FloatingOrbBackground` | Orbes de gradiente animados detrás de todo. |
| `ConfirmDestructiveDialog` | Confirmación compartida para acciones irreversibles. El botón de confirmar usa `colorScheme.error`. |

## Legibilidad dinámica

| Ajuste | Efecto |
| --- | --- |
| **Efecto de cristal** | Activa o desactiva el translúcido en toda la app |
| **Familia de fuente** | Systema, serif, monoespaciada o script |
| **Peso de fuente** | Normal a negrita |
| **Escala de fuente** | 0.85–1.30 (accesibilidad) |
| **Color de texto** | Se aplica sobre el panel tal como se ve |
| **Color de fondo del panel** | Para oscurecer o aclarar el cristal |
| **Color de borde** | Reflejo del canto del vidrio |
| **Color de acento** | Botones, indicadores y elementos activos |

## Rangos en un solo sitio

`GlassRanges` (en `TrichomeTheme.kt`) es la **única** definición de los límites:

```kotlin
object GlassRanges {
    const val OPACITY_MIN = 0.05f; const val OPACITY_MAX = 0.50f
    const val BLUR_MIN = 0f;       const val BLUR_MAX = 32f
    fun clampOpacity(v: Float): Float
    fun clampBlur(v: Float): Float
}
```

Antes el repositorio limitaba la opacidad a `0.50f` mientras el estado y el
slider ofrecían `0.55f`: los valores entre medio se perdían en silencio al
reiniciar. Los tres sitios usan ahora la misma constante.

## Principios

1. **El tema manda.** Ningún componente adivina: la configuración viaja por
   `CompositionLocal` y los overrides son explícitos.
2. **Legibilidad primero.** `readableOnStrict` garantiza contraste real, y los
   bordes tienen su propio listón de 3:1 porque no llevan texto.
3. **El cristal se ve limpio.** Sin `blur` sobre el contenido; el efecto se
   produce con superficie tintada y borde.
4. **Configurable de punta a punta.** Cada parámetro es ajustable y persiste.
5. **Cambiar de modo no mueve nada.** Mismo padding, mismo layout.
