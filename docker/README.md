# Android sin Android Studio

## Compilar y ejecutar pruebas unitarias

```bash
docker compose run --rm --build android-build
```

El APK queda en `app/build/outputs/apk/debug/`.

## Emuladores (Linux o WSL2 con Docker Engine y `/dev/kvm`)

Arranca una versión por vez:

```bash
docker compose --profile api16 up -d emulator-api16
docker compose --profile api29 up -d emulator-api29
docker compose --profile api36 up -d emulator-api36
```

Comprueba el arranque e instala el APK:

```bash
docker compose exec emulator-api29 adb wait-for-device
docker compose exec emulator-api29 adb shell getprop sys.boot_completed
docker compose cp app/build/outputs/apk/debug/app-debug.apk emulator-api29:/tmp/app.apk
docker compose exec emulator-api29 adb install -r /tmp/app.apk
```

Los perfiles cubren Android 4.1 (API 16), Android 10 (API 29) y Android 16
(API 36). API 16 es la versión mínima compatible con la base AndroidX de la app.

La pantalla de cualquiera de los tres emuladores se publica mediante noVNC en
`http://localhost:6080/vnc.html?autoconnect=1&resize=scale`. El script
`scripts/emulator.ps1` abre esa dirección automáticamente.
Tras hacer clic en la pantalla noVNC, el teclado del equipo controla Android;
el contenedor fuerza `hw.keyboard=yes` incluso para los AVD ya persistidos.
