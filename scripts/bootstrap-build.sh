#!/usr/bin/env bash
# Install the Martins Whisper Android toolchain without root.
#
#   scripts/bootstrap-build.sh [--with-ndk] [--force]
#
# Configurable:
#   ANDROID_TOOLCHAIN_DIR   default /work/android-toolchain
#
# The layout is shared by every worktree on this host:
#   jdk/                     Temurin JDK 17
#   gradle/current -> ...    Gradle 8.9
#   sdk/platforms/...        Android platform 35
#   sdk/build-tools/...      build-tools 34.0.0 + 35.0.1 (aarch64 aapt2 on ARM)
#   sdk/platform-tools/      adb (aarch64 build on ARM)
#   gradle-home/             shared Gradle user home + aapt2 override
#   downloads/               cached archives (checksum-verified)
#   STATUS.md                last successful bootstrap summary
#
# The script is idempotent and does not delete anything. Pass --force to
# re-extract components that already exist.

set -euo pipefail

ROOT="${ANDROID_TOOLCHAIN_DIR:-/work/android-toolchain}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

JDK_VERSION="17.0.21+9"
GRADLE_VERSION="8.9"
PLATFORM_VERSION="35"
BUILD_TOOLS_VERSION="35.0.1"
AGP_DEFAULT_BUILD_TOOLS="34.0.0"
NDK_VERSION_AARCH64="30.0.16248370"
NDK_VERSION_X86_64="27.3.13750724"

DOWNLOAD_DIR="$ROOT/downloads"
WITH_NDK=0
FORCE=0
for arg in "$@"; do
  case "$arg" in
    --with-ndk) WITH_NDK=1 ;;
    --force) FORCE=1 ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

ARCH="$(uname -m)"
case "$ARCH" in
  aarch64|arm64) HOST_ARCH="aarch64" ;;
  x86_64|amd64) HOST_ARCH="x86_64" ;;
  *) echo "unsupported host architecture: $ARCH" >&2; exit 1 ;;
esac

mkdir -p "$ROOT"/{downloads,jdk,gradle,sdk/build-tools,sdk/platforms,sdk/platform-tools,gradle-home,scripts}

log() { printf '[bootstrap] %s\n' "$*"; }

download() {
  local url="$1" dest="$2" sha256="$3"
  if [[ -f "$dest" ]] && echo "$sha256  $dest" | sha256sum --check --status 2>/dev/null; then
    log "cached $(basename "$dest")"
    return 0
  fi
  log "downloading $(basename "$dest")"
  curl -fL --retry 5 --retry-delay 2 -C - -o "$dest" "$url"
  if ! echo "$sha256  $dest" | sha256sum --check --status; then
    log "checksum failed for $(basename "$dest"), retrying from scratch"
    rm -f "$dest"
    curl -fL --retry 5 --retry-delay 2 -o "$dest" "$url"
  fi
  if ! echo "$sha256  $dest" | sha256sum --check --status; then
    echo "checksum mismatch for $dest" >&2
    exit 1
  fi
}

if [[ "$HOST_ARCH" == "aarch64" ]]; then
  JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.21_9.tar.gz"
  JDK_SHA256="3e4eca7ead088f080af1476007decf010a240a1eb4e1e78c54fa32fbcc347084"
  JDK_ARCHIVE="$DOWNLOAD_DIR/jdk17-aarch64.tar.gz"
else
  JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz"
  JDK_SHA256="8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711"
  JDK_ARCHIVE="$DOWNLOAD_DIR/jdk17-x64.tar.gz"
fi

if [[ ! -x "$ROOT/jdk/bin/java" ]] || [[ "$FORCE" == "1" ]]; then
  download "$JDK_URL" "$JDK_ARCHIVE" "$JDK_SHA256"
  rm -rf "$ROOT/jdk.new"
  mkdir -p "$ROOT/jdk.new"
  tar -xzf "$JDK_ARCHIVE" -C "$ROOT/jdk.new" --strip-components=1
  rm -rf "$ROOT/jdk.old"
  [[ -d "$ROOT/jdk" ]] && mv "$ROOT/jdk" "$ROOT/jdk.old"
  mv "$ROOT/jdk.new" "$ROOT/jdk"
  rm -rf "$ROOT/jdk.old"
else
  log "JDK already installed ($("$ROOT/jdk/bin/java" -version 2>&1 | head -1))"
fi

GRADLE_ARCHIVE="$DOWNLOAD_DIR/gradle-$GRADLE_VERSION-bin.zip"
if [[ ! -x "$ROOT/gradle/gradle-$GRADLE_VERSION/bin/gradle" ]] || [[ "$FORCE" == "1" ]]; then
  download "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
    "$GRADLE_ARCHIVE" "d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
  unzip -qo "$GRADLE_ARCHIVE" -d "$ROOT/gradle"
fi
ln -sfn "$ROOT/gradle/gradle-$GRADLE_VERSION" "$ROOT/gradle/current"

SDK="$ROOT/sdk"
PLATFORM_SHA256="0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0"
if [[ ! -f "$SDK/platforms/android-$PLATFORM_VERSION/android.jar" ]] || [[ "$FORCE" == "1" ]]; then
  download "https://dl.google.com/android/repository/platform-${PLATFORM_VERSION}_r02.zip" \
    "$DOWNLOAD_DIR/platform-${PLATFORM_VERSION}_r02.zip" "$PLATFORM_SHA256"
  rm -rf "$SDK/platforms/android-$PLATFORM_VERSION"
  unzip -q "$DOWNLOAD_DIR/platform-${PLATFORM_VERSION}_r02.zip" -d "$SDK/platforms"
else
  log "platform $PLATFORM_VERSION already installed"
fi

BT_SHA256="5993499f3229a021b89f87088c57242aeefaa62316bf3d69da7de40bfd5350f1"
BT34_SHA256="e858c4b60069d0431051b225d384413b1643e1289b00a4825aed347f25bd510f"
TOOLS_AARCH64_SHA256="db1cea2c4454d5f9c5a802646b2d1cf560b4ee7badbe23e51ab8e1881bb50fc2"
TOOLS_AARCH64_ZIP="$DOWNLOAD_DIR/android-sdk-tools-static-aarch64.zip"

install_build_tools() {
  local version="$1" url="$2" sha="$3"
  local dst="$SDK/build-tools/$version"
  if [[ -f "$dst/aapt2" ]] && [[ "$FORCE" != "1" ]]; then
    log "build-tools $version already installed"
    return 0
  fi
  download "$url" "$DOWNLOAD_DIR/$(basename "$url")" "$sha"
  local tmp
  tmp="$(mktemp -d)"
  unzip -q "$DOWNLOAD_DIR/$(basename "$url")" -d "$tmp"
  local src
  src="$(find "$tmp" -mindepth 1 -maxdepth 1 -type d | head -1)"
  rm -rf "$dst"
  mkdir -p "$dst"
  cp -a "$src"/. "$dst"/
  rm -rf "$tmp"
  printf 'Pkg.Desc=Android SDK Build-Tools\nPkg.Revision=%s\n' "$version" > "$dst/source.properties"
  if [[ "$HOST_ARCH" == "aarch64" ]]; then
    download "https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip" \
      "$TOOLS_AARCH64_ZIP" "$TOOLS_AARCH64_SHA256"
    local t
    t="$(mktemp -d)"
    unzip -q "$TOOLS_AARCH64_ZIP" -d "$t"
    cp -f "$t"/build-tools/{aapt,aapt2,aidl,dexdump,split-select,zipalign} "$dst"/
    chmod +x "$dst"/{aapt,aapt2,aidl,dexdump,split-select,zipalign}
    rm -rf "$t"
  fi
}

install_build_tools "$AGP_DEFAULT_BUILD_TOOLS" \
  "https://dl.google.com/android/repository/build-tools_r${AGP_DEFAULT_BUILD_TOOLS}-linux.zip" "$BT34_SHA256"
install_build_tools "$BUILD_TOOLS_VERSION" \
  "https://dl.google.com/android/repository/build-tools_r${BUILD_TOOLS_VERSION}_linux.zip" "$BT_SHA256"

if [[ "$HOST_ARCH" == "aarch64" ]]; then
  if [[ ! -x "$SDK/platform-tools/adb" ]] || [[ "$FORCE" == "1" ]]; then
    download "https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip" \
      "$TOOLS_AARCH64_ZIP" "$TOOLS_AARCH64_SHA256"
    local_tmp="$(mktemp -d)"
    unzip -q "$TOOLS_AARCH64_ZIP" -d "$local_tmp"
    cp -a "$local_tmp"/platform-tools/. "$SDK/platform-tools/"
    chmod +x "$SDK/platform-tools/"*
    printf 'Pkg.Desc=Android SDK Platform-Tools\nPkg.Revision=35.0.2\n' > "$SDK/platform-tools/source.properties"
    rm -rf "$local_tmp"
  else
    log "platform-tools already installed"
  fi
fi

mkdir -p "$SDK/licenses"
cat > "$SDK/licenses/android-sdk-license" <<'EOF'

24333f8a63b6825ea9c5514f83c2829b004d1fee
d56f5187479451eabf01fb78af6dfcb131a6481e
EOF
cat > "$SDK/licenses/android-sdk-preview-license" <<'EOF'

33b6a2b64607f11b759f320ef9dff4ae5c47d97a
EOF

if [[ "$WITH_NDK" == "1" ]]; then
  if [[ "$HOST_ARCH" != "aarch64" ]]; then
    log "NDK: on x86_64 use sdkmanager \"ndk;$NDK_VERSION_X86_64\" (official x86_64 build)"
  elif [[ -f "$SDK/ndk/$NDK_VERSION_AARCH64/source.properties" ]] && [[ "$FORCE" != "1" ]]; then
    log "community NDK $NDK_VERSION_AARCH64 already installed"
  else
    NDK_SHA256="17ce5aa889ed26a2fcc15d1f1ddb1fbce43be736be2eb3fd49644bccae34aeea"
    download "https://github.com/kaspersigi/android-ndk-r30-linux-aarch64/releases/download/v1.0.1/android-ndk-r30-linux.zip" \
      "$DOWNLOAD_DIR/android-ndk-r30-aarch64.zip" "$NDK_SHA256"
    ndk_tmp="$(mktemp -d)"
    unzip -q "$DOWNLOAD_DIR/android-ndk-r30-aarch64.zip" -d "$ndk_tmp"
    ndk_src="$(find "$ndk_tmp" -mindepth 1 -maxdepth 1 -type d | head -1)"
    rm -rf "$SDK/ndk/$NDK_VERSION_AARCH64"
    mkdir -p "$SDK/ndk"
    mv "$ndk_src" "$SDK/ndk/$NDK_VERSION_AARCH64"
    rm -rf "$ndk_tmp"
  fi
else
  log "NDK not installed (pass --with-ndk for the community aarch64 NDK r30)"
fi

AAPT2_OVERRIDE=""
if [[ "$HOST_ARCH" == "aarch64" ]]; then
  AAPT2_OVERRIDE="$SDK/build-tools/$BUILD_TOOLS_VERSION/aapt2"
fi

{
  echo "# Shared Gradle user home for Martins Whisper."
  echo "# Generated by scripts/bootstrap-build.sh; safe to edit locally."
  if [[ -n "$AAPT2_OVERRIDE" ]]; then
    echo "# AGP's Maven aapt2 is x86_64-only; use the native aarch64 build-tools copy."
    echo "android.aapt2FromMavenOverride=$AAPT2_OVERRIDE"
  fi
  echo "org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8"
  echo "org.gradle.parallel=true"
  echo "org.gradle.caching=true"
} > "$ROOT/gradle-home/gradle.properties"

if [[ -f "$REPO_ROOT/settings.gradle.kts" ]]; then
  if [[ ! -f "$REPO_ROOT/local.properties" ]]; then
    printf 'sdk.dir=%s\n' "$SDK" > "$REPO_ROOT/local.properties"
    log "wrote $REPO_ROOT/local.properties"
  else
    log "kept existing $REPO_ROOT/local.properties"
  fi
fi

JAVA_VERSION="$("$ROOT/jdk/bin/java" -version 2>&1 | head -1)"
GRADLE_VERSION_OUT="$(JAVA_HOME="$ROOT/jdk" "$ROOT/gradle/current/bin/gradle" --version 2>/dev/null | awk '/^Gradle /{print $2}')"
AAPT2_VERSION="$("$SDK/build-tools/$BUILD_TOOLS_VERSION/aapt2" version 2>&1 | head -1)"
ADB_VERSION="$("$SDK/platform-tools/adb" version 2>&1 | head -1 || true)"

cat > "$ROOT/STATUS.md" <<EOF
# Android toolchain status

Last bootstrap: $(date -u '+%Y-%m-%dT%H:%M:%SZ')
Host: $HOST_ARCH Linux $(uname -r)

| Component | Version | Location |
|---|---|---|
| JDK (Temurin) | $JDK_VERSION | $ROOT/jdk |
| Gradle | $GRADLE_VERSION_OUT | $ROOT/gradle/current |
| Android platform | $PLATFORM_VERSION | $SDK/platforms/android-$PLATFORM_VERSION |
| Build-tools | $AGP_DEFAULT_BUILD_TOOLS, $BUILD_TOOLS_VERSION | $SDK/build-tools |
| Platform-tools | 35.0.2 ($HOST_ARCH on ARM) | $SDK/platform-tools |
| Gradle user home | shared | $ROOT/gradle-home |

- aapt2: $AAPT2_VERSION ($SDK/build-tools/$BUILD_TOOLS_VERSION/aapt2)
- adb: ${ADB_VERSION:-not installed}
- aapt2 override: ${AAPT2_OVERRIDE:-none (native x86_64 build-tools)}
- NDK: $([[ -d "$SDK/ndk/$NDK_VERSION_AARCH64" ]] && echo "$NDK_VERSION_AARCH64 at $SDK/ndk/$NDK_VERSION_AARCH64" || echo "not installed")

Use it from any worktree:

\`\`\`bash
source scripts/env.sh
./gradlew :core:test :app:assembleDebug
\`\`\`
EOF

log "done"
cat "$ROOT/STATUS.md"
