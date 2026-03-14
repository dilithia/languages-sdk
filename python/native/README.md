# Python Native Crypto Bridge

This package is the planned Rust-backed Python bridge for the Python SDK.

## Purpose

Expose the same crypto semantics used by the browser wallet, but for Python runtimes.

The bridge should align with:

- `qsc-crypto`
- the browser wallet WASM wrapper
- `docs/CRYPTO_ADAPTER_SURFACE.md`

## Planned Runtime

- CPython
- `pyo3`

## Scope

The bridge should expose:

- mnemonic generation and validation
- HD wallet recovery
- indexed account derivation
- wallet-file recovery
- address derivation from public key
- sign / verify helpers

## Status

Wired against `qsc-crypto`.

Implemented:

- mnemonic generation and validation
- HD root-account recovery
- indexed HD account derivation
- HD wallet-file creation
- wallet-file recovery for HD and non-HD files
- address derivation from public key
- sign / verify

## Packaging

This bridge is packaged with `maturin`:

```bash
cd python/native
maturin build
```

Docker build flow:

```bash
./scripts/build-python-native-docker.sh
```
