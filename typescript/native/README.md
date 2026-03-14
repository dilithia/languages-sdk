# TypeScript Native Crypto Bridge

This package is the planned Rust-backed Node bridge for the TypeScript SDK.

## Purpose

Expose the same crypto semantics used by the browser wallet, but for Node.js runtimes.

The bridge should align with:

- `qsc-crypto`
- the browser wallet WASM wrapper
- `docs/CRYPTO_ADAPTER_SURFACE.md`

## Planned Runtime

- Node.js
- `napi-rs`

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

This bridge is packaged as a standalone npm package:

- `package.json`
- `index.js` loader for the generated `.node` binary
- `index.d.ts` surface for TS consumers

Typical local flow:

```bash
cd typescript/native
npm install
npm run build:native
```

Docker build flow:

```bash
./scripts/build-typescript-native-docker.sh
```
