# Publishing

Guía para publicar la Trichome App como **GitHub Release v1.0.0** con sus APKs.

## Requisitos

- JDK 17 instalado (Adoptium): `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- `gh` autenticado como `tornoalfuego-sketch` (token con permisos `repo` + `workflow`).

## 1. Construir los APKs

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"

# Limpieza + builds
.\gradlew.bat :app:clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest

# Firmar el release firmado (si se cuenta con keystore):
#  - Colocar `trichome.jks` y `keystore.properties` (no se suben a git)
#  - Añadir signingConfig en app/build.gradle.kts y reconstruir
```

Salidas:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`
  (firmado si se configura `signingConfig`)

> Nota: para un release instalable real (Play/u otros) el APK debe ir **firmado**.
> Sin keystore, se puede publicar el `app-debug.apk` como artefacto funcional
> o firmar con un keystore propio.

## 2. Confirmar la release

```powershell
gh repo view tornoalfuego-sketch/TrichomeApp
```

Si el repositorio no existe:

```powershell
gh repo create tornoalfuego-sketch/TrichomeApp --public --source . --push
```

## 3. Subir tags y creación de Release

```powershell
git tag v1.0.0
git push origin v1.0.0

gh release create v1.0.0 `
  "app/build/outputs/apk/debug/app-debug.apk#Trichome-App-debug-1.0.0.apk" `
  "app/build/outputs/apk/release/app-release.apk#Trichome-App-release-1.0.0.apk" `
  --title "Trichome App v1.0.0" `
  --notes "Primera versión: gestión de carpas, bitácora de 16 tipos de evento, SuperCycle, calendario, biblias de terpenos/breeding, gráficas, gamificación y diagnóstico por foto."
```

## 4. Verificar

```powershell
gh release view v1.0.0
```

Comprueba que los dos APKs aparecen como assets descargables.

## Checklist de calidad antes de publicar

- [ ] `assembleDebug` y `assembleRelease` en verde
- [ ] `testDebugUnitTest` en verde (superciclo, días de etapa, diagnóstico, gamificación, EventType)
- [ ] Migración Room v1→v2 cubierta por test de instrumentación (`MigrationTest`)
- [ ] Nombre de paquete `com.trichome.app`, `versionCode=1`, `versionName=1.0.0`
- [ ] Documentación en `docs/` actualizada
- [ ] Repositorio público y Release con APK(s) adjuntos