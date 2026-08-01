# Ejecución completa sin Android Studio

Todo Android, la API, PostgreSQL, el correo de desarrollo y la administración
web se gestionan con Docker dentro de Ubuntu/WSL2. iOS está deliberadamente
separado porque Xcode solo funciona en macOS.

## 1. Preparación única

Abre PowerShell en la raíz del repositorio y genera secretos locales:

```powershell
.\scripts\dev-setup.ps1
```

El script no sobrescribe un `.env` existente. Conserva la contraseña inicial de
administración que muestra y no incorpores `.env` a Git.

Comprueba el motor Docker de Ubuntu y KVM:

```powershell
wsl -d Ubuntu -- bash -lc "/usr/bin/docker version && /usr/bin/docker compose version"
wsl -d Ubuntu -- bash -lc "test -c /dev/kvm && test -r /dev/kvm && test -w /dev/kvm && echo KVM_AVAILABLE"
```

Si aparece `KVM_AVAILABLE`, la BIOS ya está correctamente configurada. El error
`KVM_UNAVAILABLE` se producía al usar el motor de Docker Desktop, que no expone
`/dev/kvm`. Los scripts de este repo usan explícitamente `/usr/bin/docker`
dentro de Ubuntu/WSL2. Si no aparece:

```powershell
wsl --update
wsl --shutdown
```

Después vuelve a abrir Ubuntu, comprueba que tu usuario pertenece a `kvm` y
`docker` (`groups`) y reinicia Windows si acabas de añadir esos grupos. No
desactives Hyper-V/Virtual Machine Platform: WSL2 los necesita.

## 2. API, base de datos, email y administración

```powershell
.\scripts\backend.ps1 Start
.\scripts\backend.ps1 Status
```

Servicios:

- API y estado: `http://localhost:8080/health`
- administración: `http://localhost:3000`
- buzón SMTP de desarrollo: `http://localhost:8025`

Al registrar una cuenta, el email de confirmación aparecerá en Mailpit. El
inicio de sesión queda bloqueado hasta abrir su enlace.

Para detener estos servicios:

```powershell
.\scripts\backend.ps1 Stop
```

## 3. Compilar Android en Docker

No hace falta Android Studio, SDK ni Gradle instalados en Windows:

```powershell
wsl -d Ubuntu -- bash -lc "cd '/mnt/c/Users/rodri/OneDrive/Documentos/GitHub/HotelDumbria3' && /usr/bin/docker compose run --rm --build android-build"
```

Esto ejecuta pruebas unitarias y genera
`app/build/outputs/apk/debug/app-debug.apk`.

La comprobación completa (debug, unitarios, lint y release con R8) es:

```powershell
wsl -d Ubuntu -- bash -lc "cd '/mnt/c/Users/rodri/OneDrive/Documentos/GitHub/HotelDumbria3' && /usr/bin/docker compose run --rm android-build ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease -PHOTEL_API_BASE_URL=http://10.0.2.2:8080/api/v1 -PHOTEL_RELEASE_API_BASE_URL=https://api.hotel.example/api/v1"
```

La URL de release del ejemplo solo demuestra la compilación. Sustitúyela por
el endpoint HTTPS real antes de distribuir. `verifyReleaseApiUrl` rechaza HTTP,
localhost, `10.0.2.2` y dominios `.invalid` si se intenta una release publicable.

## 4. Emuladores: siempre uno solo

Los scripts rechazan el arranque si detectan otro emulador del proyecto. No
ejecutes nunca API 16, 29 y 36 a la vez. El backend sí puede permanecer activo.
Al terminar el arranque se abre automáticamente una pantalla interactiva en
`http://localhost:6080/vnc.html?autoconnect=1&resize=scale`. Si el navegador no
se abre, visita esa dirección manualmente. Usa `-NoBrowser` para no abrirla.
Haz clic dentro de la pantalla noVNC para darle el foco: el AVD está configurado
con teclado físico y acepta directamente el teclado del equipo. El teclado
virtual de Android sigue disponible cuando se necesite.

### Android 4.1, API 16

```powershell
.\scripts\emulator.ps1 -Api 16
```

Al terminar la prueba:

```powershell
.\scripts\stop-emulator.ps1
```

### Android 10, API 29

```powershell
.\scripts\emulator.ps1 -Api 29
```

Prueba especialmente seleccionar una foto desde DocumentProvider, previsualizar
y subirla sin permiso de almacenamiento. Después:

```powershell
.\scripts\stop-emulator.ps1
```

### Android actual, API 36

```powershell
.\scripts\emulator.ps1 -Api 36
```

Después:

```powershell
.\scripts\stop-emulator.ps1
```

`-NoInstall` arranca el AVD sin reinstalar el APK. API 16 es la mínima soportada
por esta modernización; no es técnicamente viable ejecutar la aplicación
AndroidX actual en Android 1.0. API 36 representa Android 16; no existe una
imagen estable de Android 17 en la configuración actual.

Para ejecutar instrumentación en el emulador activo, genera los dos APK y
cópialos al mismo contenedor; no arranques otro AVD:

```powershell
wsl -d Ubuntu -- bash -lc "cd '/mnt/c/Users/rodri/OneDrive/Documentos/GitHub/HotelDumbria3' && /usr/bin/docker compose run --rm android-build ./gradlew --no-daemon assembleDebug assembleDebugAndroidTest"
# Cambia 29 por el único perfil que esté activo: 16, 29 o 36.
wsl -d Ubuntu -- bash -lc "cd '/mnt/c/Users/rodri/OneDrive/Documentos/GitHub/HotelDumbria3' && /usr/bin/docker compose cp app/build/outputs/apk/debug/app-debug.apk emulator-api29:/tmp/app.apk && /usr/bin/docker compose cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk emulator-api29:/tmp/tests.apk && /usr/bin/docker compose exec -T emulator-api29 adb install -r /tmp/app.apk && /usr/bin/docker compose exec -T emulator-api29 adb install -r /tmp/tests.apk && /usr/bin/docker compose exec -T emulator-api29 adb shell am instrument -w org.hoteldumbria.test/androidx.test.runner.AndroidJUnitRunner"
```

La prueba de conectividad requiere salida real a Internet; si la red corporativa
bloquea UDP/53, debe pasar mediante la fase HTTPS de respaldo.

## 5. Migrar una copia del SQL histórico

Los dumps originales contienen credenciales y datos personales y no se
versionan. Copia uno manualmente como
`legacy-import/input/hotel_dumbria.sql`, revisa y anonimiza sus datos, y ejecuta:

```powershell
wsl -d Ubuntu -- bash -lc "cd '/mnt/c/Users/rodri/OneDrive/Documentos/GitHub/HotelDumbria3' && /usr/bin/docker compose --profile backend --profile migration up --build --abort-on-container-exit legacy-import"
```

Las contraseñas SHA históricas no se reutilizan. Los usuarios importados deben
establecer una nueva contraseña Argon2id mediante un flujo de recuperación.

## 6. iOS, fuera de Docker

En un Mac:

```bash
cd ios
brew install xcodegen
xcodegen generate
open HotelDumbria.xcodeproj
```

Consulta `ios/README.md` para configurar el endpoint HTTPS y la firma.

## 7. Apagado total

```powershell
.\scripts\stop-wsl-docker.ps1
```

Los datos PostgreSQL, avatares y AVD se conservan en volúmenes Docker.
