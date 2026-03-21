# Crypto Adapter Surface

This document fixes the shared adapter surface that every non-browser SDK should consume.

The implementation behind it can vary by runtime, but the semantic surface should remain stable.

## Core Rule

SDKs should depend on a crypto adapter, not on direct ad hoc crypto helpers.

That adapter must remain aligned with `dilithia-core`.

## Shared Operations

The minimum shared surface is:

- `generate_mnemonic()`
- `validate_mnemonic(mnemonic)`
- `recover_hd_account(mnemonic, account_index=0)` or a root-account alias
- `recover_hd_wallet_account(mnemonic, account_index)`
- `create_hd_wallet_file_from_mnemonic(mnemonic, password)`
- `create_hd_wallet_account_from_mnemonic(mnemonic, password, account_index)`
- `recover_wallet_file(wallet_file, mnemonic, password)`
- `address_from_public_key(public_key_hex)`
- `sign_message(secret_key_hex, message)`
- `verify_message(public_key_hex, message, signature_hex)`

## Returned Data

Recovered or created accounts should expose:

- `address`
- `public_key`
- `secret_key`
- `account_index`
- `wallet_file` when applicable

Signed payloads should expose:

- `algorithm`
- `signature`

## Runtime Mapping

- TypeScript: adapter interface, later backed by a Node-native Rust bridge
- Python: adapter protocol, later backed by a Rust extension
- Rust: direct `dilithia-core`
- Go / Java: consume a native bridge that preserves the same semantics

## Why This Matters

This avoids:

- per-language drift
- incompatible account derivation
- mismatched address derivation
- inconsistent signature behavior
