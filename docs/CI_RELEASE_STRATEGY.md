# CI And Release Strategy

This document fixes the release direction for every language in `languages-sdk`.

## TypeScript SDK

- CI matrix:
  - Linux
  - macOS
  - Windows
  - Node 20 and 22
- artifacts:
  - npm tarball
- publish target:
  - npm

## Python SDK

- CI matrix:
  - Linux
  - macOS
  - Windows
  - Python 3.11 and 3.12
- artifacts:
  - sdist
  - wheel
- publish target:
  - PyPI

## TypeScript Native Crypto Bridge

- CI matrix:
  - Linux
  - macOS
  - Windows
- artifacts:
  - platform-specific `.node` binary
  - npm tarball
- publish target:
  - npm

## Python Native Crypto Bridge

- CI matrix:
  - Linux
  - macOS
  - Windows
  - Python 3.11 and 3.12
- artifacts:
  - platform-specific wheels
- publish target:
  - PyPI

## Go / Rust / Java

- Go
  - OS matrix tests
  - source artifact on release
  - publication by Git tag / GitHub release
- Rust
  - cargo package on CI
  - crates.io publish on GitHub release
- Java
  - JVM matrix tests
  - Maven package artifacts
  - Maven Central publish on GitHub release

See:

- `docs/REGISTRY_RELEASE_SETUP.md`
