#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="dilithia-sdk-node-crypto-builder"

docker build -f "$ROOT_DIR/typescript/native/Dockerfile" -t "$IMAGE_NAME" "$ROOT_DIR/typescript/native"

docker run --rm \
  -v "$(dirname "$ROOT_DIR"):/workspace-root" \
  -w /workspace-root/languages-sdk/typescript/native \
  -e HOST_UID="$(id -u)" \
  -e HOST_GID="$(id -g)" \
  "$IMAGE_NAME" \
  bash -c 'export PATH="/usr/local/cargo/bin:/usr/local/rustup/toolchains/stable-x86_64-unknown-linux-gnu/bin:/root/.cargo/bin:$PATH"; npm install && npm run build:native && NPM_CONFIG_CACHE=$PWD/.npm-cache npm pack && chown -R "$HOST_UID:$HOST_GID" /workspace-root/languages-sdk/typescript/native'
