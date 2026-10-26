#!/usr/bin/env bash
# Shared build environment for Martins Whisper on this host.
# Usage: source scripts/env.sh
#
# Override the toolchain location with ANDROID_TOOLCHAIN_DIR if you installed it
# elsewhere. This file only exports variables; it does not modify the repo.

MW_TOOLCHAIN_DIR="${ANDROID_TOOLCHAIN_DIR:-/work/android-toolchain}"

if [[ ! -x "$MW_TOOLCHAIN_DIR/jdk/bin/java" ]]; then
  echo "error: JDK not found under $MW_TOOLCHAIN_DIR/jdk (run scripts/bootstrap-build.sh)" >&2
  return 1 2>/dev/null || exit 1
fi

export ANDROID_TOOLCHAIN_DIR="$MW_TOOLCHAIN_DIR"
export JAVA_HOME="$MW_TOOLCHAIN_DIR/jdk"
export ANDROID_HOME="$MW_TOOLCHAIN_DIR/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$MW_TOOLCHAIN_DIR/gradle-home"
export PATH="$JAVA_HOME/bin:$MW_TOOLCHAIN_DIR/gradle/current/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.1:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8"

unset ANDROID_NDK_HOME ANDROID_NDK_ROOT 2>/dev/null || true
if [[ -d "$ANDROID_HOME/ndk" ]]; then
  MW_NDK="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
  if [[ -n "$MW_NDK" ]]; then
    export ANDROID_NDK_HOME="$MW_NDK"
    export ANDROID_NDK_ROOT="$MW_NDK"
  fi
fi

echo "Martins Whisper toolchain:"
echo "  JAVA_HOME=$JAVA_HOME ($("$JAVA_HOME/bin/java" -version 2>&1 | head -1))"
echo "  ANDROID_HOME=$ANDROID_HOME"
echo "  GRADLE_USER_HOME=$GRADLE_USER_HOME"
