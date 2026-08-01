#!/usr/bin/env bash
set -euo pipefail

AVD_NAME="${AVD_NAME:-hotel_api${ANDROID_API}}"
EMULATOR_MEMORY="${EMULATOR_MEMORY:-2048}"
IMAGE_PACKAGE="system-images;android-${ANDROID_API};${SYSTEM_IMAGE_TAG};${SYSTEM_IMAGE_ARCH}"
DISPLAY_WIDTH="${DISPLAY_WIDTH:-1280}"
DISPLAY_HEIGHT="${DISPLAY_HEIGHT:-800}"

if [[ ! -f "/root/.android/avd/${AVD_NAME}.ini" ]]; then
  mkdir -p /root/.android/avd
  echo "no" | avdmanager create avd \
    --force \
    --name "${AVD_NAME}" \
    --package "${IMAGE_PACKAGE}" \
    --device "pixel"
fi

# noVNC/x11vnc sends the host's key events to the emulator window. Ensure the
# AVD exposes a hardware keyboard so Android consumes those events instead of
# requiring the on-screen keyboard. Apply it on every start as existing AVDs
# are persisted in a Docker volume.
AVD_CONFIG="/root/.android/avd/${AVD_NAME}.avd/config.ini"
sed -i '/^hw\.keyboard[[:space:]]*=/d' "${AVD_CONFIG}"
printf '\nhw.keyboard = yes\n' >> "${AVD_CONFIG}"

# El invitado Android ve el loopback del contenedor como 10.0.2.2. Este proxy
# permite que la app llegue al backend publicado por el mismo Docker Engine.
socat TCP-LISTEN:8080,fork,reuseaddr TCP:host.docker.internal:8080 &

# Pantalla X virtual y cliente web interactivo. Compose publica noVNC solo en
# localhost, por lo que no queda expuesto a la red local.
export DISPLAY=:0
export QT_X11_NO_MITSHM=1
Xvfb :0 -screen 0 "${DISPLAY_WIDTH}x${DISPLAY_HEIGHT}x24" -nolisten tcp &
x11vnc -display :0 -forever -shared -localhost -nopw -rfbport 5900 -quiet &
websockify --web=/usr/share/novnc/ 6080 localhost:5900 &

exec emulator "@${AVD_NAME}" \
  -no-audio \
  -no-boot-anim \
  -gpu swiftshader_indirect \
  -accel on \
  -memory "${EMULATOR_MEMORY}" \
  -port 5554 \
  -grpc 8554 \
  -writable-system
