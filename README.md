# Dilithium Language SDKs

This repository is the monorepo for non-browser Dilithium SDKs.

Public rename direction:

- ecosystem public name: `Dilithia`
- Maven namespace: `org.dilithia`
- npm scope target: `@dilithia`

See:

- `docs/DILITHIA_RENAME_PLAN.md`

It is intended for:

- bots
- backend services
- automation
- infra tooling
- language-specific connectors

It is not the browser provider SDK. The browser-facing package remains separate in `dilithium-sdk/`.

## Versioning

Language SDKs in this repository are versioned against the current RPC/core line.

The initial aligned version is:

- `0.3.0`

## Layout

```text
typescript/
  docs/

python/
  docs/

go/
  docs/

rust/
  docs/

java/
  docs/
```

Current implementation status:

- `typescript/`: RPC client + optional native crypto bridge
- `python/`: RPC client + optional native crypto bridge
- `go/`: RPC client + optional native crypto bridge over `native-core`
- `rust/`: request builder core + direct `qsc-crypto` adapter
- `java/`: JVM client core + optional native crypto bridge over `native-core`
- `native-core/`: shared C ABI bridge used by Go and Java

Shared transport and domain surface now includes:

- raw JSON-RPC builders and dispatch
- raw REST access
- websocket URL/configuration
- contract query and call helpers
- gas estimate and base fee helpers
- name service helpers
- gas sponsor connectors
- cross-chain messaging connectors

## Version Smoke Tests

Each language SDK should own a small smoke test that verifies:

- the SDK package version
- the aligned RPC line version
- the minimum supported language/runtime version

This keeps version checks close to each language instead of hiding them behind a shared container setup.

## Shared Direction

Every language SDK should converge on the same logical surface:

- network and client configuration
- balance, nonce, receipt and address-summary reads
- canonical payload construction
- local signing
- simulation
- transaction submission
- polling helpers
- explorer URL helpers when useful

## Release Direction

Each language should get its own CI workflow for:

- lint
- test
- package build
- publish

The workflows can live together in this monorepo while the APIs are still stabilizing.

Current workflows already exist for:

- TypeScript
- Python
- Go
- Rust
- Java
- TypeScript native crypto
- Python native crypto
- native-core

Current native bridge scaffolds already exist for:

- `typescript/native/` via `napi-rs`
- `python/native/` via `pyo3`
- `native-core/` via C ABI for Go/Java

## Native Bridge Packaging

The first packaged native bridges are:

- `typescript/native/`
  Node package: `@dilithia/sdk-node-crypto`
- `python/native/`
  Python package: `dilithium-sdk-python-crypto`

These bridges are responsible for exposing the `qsc-crypto` surface to non-browser runtimes without reimplementing key derivation or signing logic.

Current CI coverage:

- `typescript-native.yml`
  - `cargo check`
  - native Node bridge build
  - npm tarball artifact
  - npm publish on GitHub release
- `python-native.yml`
  - `cargo check`
  - `maturin build`
  - wheel artifact
  - PyPI publish on GitHub release

Local containerized builds:

- `./scripts/build-typescript-native-docker.sh`
- `./scripts/build-python-native-docker.sh`

## Crypto Parity

The browser wallet already uses `qsc-crypto` through a WASM wrapper.

Language SDKs should align to the same primitive instead of reproducing key derivation or address logic independently.

See:

- `docs/CRYPTO_ALIGNMENT.md`
- `docs/CRYPTO_ADAPTER_SURFACE.md`
- `docs/CI_RELEASE_STRATEGY.md`
- `docs/REGISTRY_RELEASE_SETUP.md`
- `docs/ECOSYSTEM_SURFACE.md`
