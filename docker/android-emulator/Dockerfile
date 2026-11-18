FROM eclipse-temurin:17-jdk-jammy

# CVE-2026-114938: disable vulnerable Base64 intrinsic in Java 17 build/tooling JVMs.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"

ARG ANDROID_API
ARG SYSTEM_IMAGE_TAG=google_apis
ARG SYSTEM_IMAGE_ARCH=x86_64
ARG ANDROID_COMMAND_LINE_TOOLS=15859902
ARG ANDROID_COMMAND_LINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_API=${ANDROID_API} \
    SYSTEM_IMAGE_TAG=${SYSTEM_IMAGE_TAG} \
    SYSTEM_IMAGE_ARCH=${SYSTEM_IMAGE_ARCH} \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/emulator:/opt/android-sdk/platform-tools:${PATH}

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      curl unzip socat xvfb x11vnc novnc websockify \
      libgl1 libpulse0 libnss3 libx11-6 libxcb1 libxcomposite1 \
      libxcursor1 libxi6 libxdamage1 libxext6 libxfixes3 libxrandr2 \
      libxkbcommon-x11-0 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL \
      "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMAND_LINE_TOOLS}_latest.zip" \
      -o /tmp/android-tools.zip \
    && echo "${ANDROID_COMMAND_LINE_TOOLS_SHA256}  /tmp/android-tools.zip" | sha256sum -c - \
    && unzip -q /tmp/android-tools.zip -d /tmp/android-tools \
    && mv /tmp/android-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/android-tools /tmp/android-tools.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager \
      "platform-tools" \
      "emulator" \
      "system-images;android-${ANDROID_API};${SYSTEM_IMAGE_TAG};${SYSTEM_IMAGE_ARCH}"

COPY entrypoint.sh /usr/local/bin/android-emulator-entrypoint
RUN chmod +x /usr/local/bin/android-emulator-entrypoint

EXPOSE 5555 6080
ENTRYPOINT ["android-emulator-entrypoint"]
