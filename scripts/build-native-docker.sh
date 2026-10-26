#!/usr/bin/env bash
# Builds the APK with NDK inside an x86_64 container. On an aarch64 host this needs binfmt/QEMU.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${IMAGE:-martins-whisper-native:local}"
docker buildx build \
    --platform linux/amd64 \
    --file "$ROOT/docker/native-build.Dockerfile" \
    --tag "$IMAGE" \
    "$ROOT"
echo "Built $IMAGE"
