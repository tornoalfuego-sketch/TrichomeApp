# Publishing

Guía para publicar la Trichome App como **GitHub Release v1.0.0** con sus APKs.

## Requisitos

- JDK 17 instalado (Adoptium): `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- `gh` autenticado como `tornoalfuego-sketch` (token con permisos `repo` + `workflow`).

## 1. Keystore de release

El APK de release **debe ir firmado**: Android rechaza instalar un APK sin
firma (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`). El keystore nunca se sube a git
(`.gitignore` ya cubre `*.jks` y `keystore.properties`).

Generar uno nuevo (o recrear el existente con estos mismos datos para conservar
la firma y poder actualizar la app ya instalada):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore trichome-release.jks -alias trichome `
  -keyalg RSA -keysize 2048 -validity 10950 `
  -storepass trichome2024 -keypass trichome2024 `
  -dname "CN=Trichome App, OU=Mobile, O=Trichome, L=Madrid, ST=Madrid, C=ES"
```

`keystore.properties` (no versionado):

```properties
storeFile=trichome-release.jks
storePassword=trichome2024
keyAlias=trichome
keyPassword=trichome2024
```

`app/build.gradle.kts` lee ese archivo y aplica el `signingConfig` al build type
`release`. Si el archivo no existe el release sigue compilando, pero produce un
APK **sin firmar** e ininstalable.

> Esta clave es de distribución community. Para Play Store hay que generar una
> clave propia y guardarla fuera del repositorio.

## 2. Construir los APKs

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"

.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain
```

> Ejecutar los tasks de Gradle de uno en uno: dos builds simultáneos sobre el
> mismo proyecto fallan con `Cannot access output property 'destinationDirectory'`.

Salidas:
- `app/build/outputs/apk/debug/app-debug.apk` (firmado con la clave de debug)
- `app/build/outputs/apk/release/app-release.apk` (firmado, con `signingConfig`)

Verificar la firma antes de publicar:

```powershell
$bt = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory | Sort-Object Name -Descending)[0]
& "$($bt.FullName)\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

## 3. Confirmar la release

```powershell
gh repo view tornoalfuego-sketch/TrichomeApp
```

Si el repositorio no existe:

```powershell
gh repo create tornoalfuego-sketch/TrichomeApp --public --source . --push
```

## 4. Subir tags y creación de Release

```powershell
git tag v1.0.0
git push origin v1.0.0

gh release create v1.0.0 `
  "app/build/outputs/apk/debug/app-debug.apk#Trichome-App-debug-1.0.0.apk" `
  "app/build/outputs/apk/release/app-release.apk#Trichome-App-release-1.0.0.apk" `
  --title "Trichome App v1.0.0" `
  --notes "Primera versión: gestión de carpas, bitácora de 16 tipos de evento, SuperCycle, calendario, biblias de terpenos/breeding, gráficas, gamificación y diagnóstico por foto."
```

> `gh release create ruta#label` solo fija la *etiqueta* visible del asset, no el
> nombre del fichero. Para renombrar de verdad el asset hay que hacer
> `PATCH repos/{owner}/{repo}/releases/assets/{id}` con `name`.

## 5. Verificar

```powershell
gh release view v1.0.0
```

Comprueba que los dos APKs aparecen como assets descargables.

## Checklist de calidad antes de publicar

- [ ] `assembleDebug` y `assembleRelease` en verde
- [ ] `apksigner verify` confirma que `app-release.apk` está firmado
- [ ] `testDebugUnitTest` en verde (superciclo, días de etapa, diagnóstico, gamificación, EventType, init de WorkManager)
- [ ] Migración Room v1→v2 cubierta por test de instrumentación (`MigrationTest`)
- [ ] Nombre de paquete `com.trichome.app`, `versionCode=1`, `versionName=1.0.0`
- [ ] Documentación en `docs/` actualizada
- [ ] Repositorio público y Release con APK(s) adjuntos

## Notas de arranque

`TrichomeApp` usa la **inicialización bajo demanda** de WorkManager: implementa
`Configuration.Provider` y el `androidx.work.WorkManagerInitializer` por defecto
se elimina del manifest con `tools:node="remove"`.

Es obligatorio. `androidx.startup.InitializationProvider` es un
`ContentProvider`, así que se ejecuta **antes** de `Application.onCreate()`: si
además se llama a `WorkManager.initialize()` desde `onCreate()`, WorkManager
lanza `IllegalStateException: WorkManager is already initialized` y el proceso
muere sin llegar a pintar ninguna pantalla (la app "no ejecuta").

`WorkManagerInitTest` vigila las tres partes de ese contrato.