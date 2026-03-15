#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="dilithia-sdk-python-crypto-builder"

docker build -f "$ROOT_DIR/python/native/Dockerfile" -t "$IMAGE_NAME" "$ROOT_DIR/python/native"

docker run --rm \
  -v "$(dirname "$ROOT_DIR"):/workspace-root" \
  -w /workspace-root/languages-sdk/python/native \
  -e HOST_UID="$(id -u)" \
  -e HOST_GID="$(id -g)" \
  "$IMAGE_NAME" \
  bash -lc 'export PATH=/usr/local/cargo/bin:$PATH && python3 -m maturin build && chown -R "$HOST_UID:$HOST_GID" /workspace-root/languages-sdk/python/native'
