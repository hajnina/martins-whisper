# x86_64 Linux build environment for the Android native library.
# Build on an x86_64 host, or on aarch64 with Docker binfmt/QEMU enabled.
FROM ubuntu:24.04

ARG GRADLE_VERSION=8.9
ARG NDK_VERSION=27.0.12077973
ARG CMDLINE_TOOLS=commandlinetools-linux-11076708_latest.zip

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl unzip git file \
        openjdk-17-jdk-headless ninja-build glslc \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
        -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=${ANDROID_HOME}
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/${CMDLINE_TOOLS}" -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/android-cmdline \
    && mv /tmp/android-cmdline/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline-tools.zip /tmp/android-cmdline

ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"
RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install \
        "platforms;android-35" \
        "build-tools;35.0.1" \
        "cmake;3.22.1" \
        "ndk;${NDK_VERSION}"

ENV ANDROID_NDK_HOME=${ANDROID_HOME}/ndk/${NDK_VERSION}
ENV PATH=${ANDROID_NDK_HOME}/shader-tools/linux-x86_64:${PATH}

WORKDIR /src
COPY . .
RUN scripts/fetch-whisper-cpp.sh

CMD ["gradle", "--no-daemon", ":core:test", ":app:testDebugUnitTest", ":app:assembleDebug"]
