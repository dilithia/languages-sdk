# Architecture

This page describes how the Dilithia SDK monorepo is structured and how `dilithia-core` flows through native bridges to each language SDK.

---

## Repository Layout

```
languages-sdk/
|
+-- rust/                   Rust SDK (direct dilithia-core integration)
|   +-- src/lib.rs          DilithiaCryptoAdapter trait, DilithiaClient, connectors
|
+-- typescript/             TypeScript/Node.js SDK
|   +-- src/index.ts        DilithiaClient class, connectors, exports
|   +-- src/crypto.ts       DilithiaCryptoAdapter interface, native bridge loader
|   +-- native/             NAPI-RS bridge (@dilithia/sdk-native)
|
+-- python/                 Python SDK
|   +-- src/dilithia_sdk/
|   |   +-- __init__.py     Package exports
|   |   +-- client.py       DilithiaClient, AsyncDilithiaClient, connectors
|   |   +-- crypto.py       DilithiaCryptoAdapter protocol, native bridge loader
|   +-- native/             PyO3 bridge (dilithia-sdk-native)
|
+-- go/                     Go SDK
|   +-- ...                 RPC client + native crypto via cgo/C ABI
|
+-- java/                   Java/JVM SDK
|   +-- ...                 RPC client + native crypto via JNI/C ABI
|
+-- native-core/            Shared C ABI library for Go and Java
|   +-- src/                Rust code exposing dilithia-core over C FFI
|
+-- docs/                   Documentation (this site)
+-- .github/workflows/      CI workflows for all languages
```

---

## Core Architecture Diagram

```
+------------------------------------------------------------------+
|                        dilithia-core (Rust)                       |
|                                                                    |
|  ML-DSA-65 keygen   BIP-39 mnemonics   HD derivation   Hashing  |
|  Signing/Verify      Address logic      Wallet encrypt/decrypt    |
+--------+------------------+------------------+--------------------+
         |                  |                  |
    Direct Rust API    NAPI-RS (N-API)    PyO3 (CPython ABI)
         |                  |                  |
         v                  v                  v
+----------------+  +------------------+  +-------------------+
|   Rust SDK     |  | @dilithia/       |  | dilithia-sdk-     |
|                |  | sdk-native       |  | native            |
| NativeCrypto-  |  | (.node addon)    |  | (.so/.pyd module) |
| Adapter        |  +--------+---------+  +---------+---------+
| DilithiaClient |           |                      |
+----------------+           v                      v
                   +------------------+   +-------------------+
                   | TypeScript SDK   |   |  Python SDK       |
                   |                  |   |                   |
                   | DilithiaClient   |   | DilithiaClient    |
                   | DilithiaCrypto-  |   | AsyncDilithia-    |
                   | Adapter          |   | Client            |
                   | SyncDilithia-    |   | DilithiaCrypto-   |
                   | CryptoAdapter    |   | Adapter           |
                   +------------------+   +-------------------+

+------------------------------------------------------------------+
|                    native-core (C ABI / FFI)                      |
|                                                                    |
|  Exposes dilithia-core functions as extern "C" symbols            |
+--------+---------------------------+-----------------------------+
         |                           |
    cgo (Go FFI)               JNI (Java FFI)
         |                           |
         v                           v
+------------------+       +-------------------+
|    Go SDK        |       |    Java SDK       |
|                  |       |                   |
| CryptoAdapter    |       | CryptoAdapter     |
| Client           |       | Client            |
+------------------+       +-------------------+
```

---

## Data Flow: Signing a Transaction

```
Application Code
       |
       |  1. Build contract call (JSON payload)
       v
  DilithiaClient.buildContractCall()
       |
       |  2. Serialize to canonical JSON string
       v
  JSON.stringify(call)  /  json.dumps(call)
       |
       |  3. Sign the canonical payload
       v
  CryptoAdapter.signMessage(secretKey, payloadJson)
       |
       |  (crosses language boundary via native bridge)
       v
  dilithia-core::crypto::sign_mldsa65(message, secret_key)
       |
       |  4. Return signature
       v
  DilithiaSignature { algorithm: "mldsa65", signature: "..." }
       |
       |  5. Merge signature fields into call
       v
  { ...call, sender, public_key, algorithm, signature }
       |
       |  6. Submit via HTTP POST
       v
  DilithiaClient.sendCall(signedCall)
       |
       v
  Dilithia Node (/rpc/call)
```

---

## Design Principles

### Single Source of Truth

All cryptographic operations use `dilithia-core`. No language SDK reimplements key derivation, address logic, or signing. This ensures:

- Consistent behavior across all languages
- Security fixes propagate to all SDKs via a single dependency update
- Post-quantum algorithm parameters are centralized

### Uniform API Surface

Every SDK exposes the same logical interface, adapted to each language's idioms:

| Concept                  | Rust             | TypeScript        | Python              | Go               | Java              |
| ------------------------ | ---------------- | ----------------- | ------------------- | ----------------- | ----------------- |
| Trait/Interface          | `trait`          | `interface`       | `Protocol`          | `interface`       | `interface`       |
| Error handling           | `Result<T, E>`   | `throw` / `Promise.reject` | `raise` / exception | `(T, error)` | `throws Exception` |
| Naming convention        | `snake_case`     | `camelCase`       | `snake_case`        | `PascalCase`      | `camelCase`       |
| Async pattern            | N/A (sync core)  | `Promise<T>`      | `async def` / `asyncio` | goroutines   | `CompletableFuture` |

### Optional Native Crypto

The native bridge is always optional. SDKs gracefully degrade when it is not installed:

- `loadNativeCryptoAdapter()` returns `null` / `None` instead of throwing
- The RPC client works independently of the crypto adapter
- Applications can check for native crypto at startup and decide how to proceed

### Request Builder Pattern (Rust)

The Rust SDK uses a request builder pattern rather than executing HTTP requests directly. This gives the caller full control over the HTTP client and runtime:

```rust
// Returns a DilithiaRequest enum (Get or Post), not a response
let request = client.get_balance_request("dili1abc...");
```

TypeScript and Python SDKs execute requests directly using built-in HTTP clients (`fetch` and `urllib`).

---

## CI Architecture

Each language has its own CI workflow:

```
.github/workflows/
|
+-- rust.yml                 cargo check, test, clippy
+-- typescript.yml           tsc, vitest, npm pack
+-- typescript-native.yml    NAPI-RS build, npm publish
+-- python.yml               pytest, mypy, build
+-- python-native.yml        maturin build, PyPI publish
+-- go.yml                   go test, go vet
+-- java.yml                 maven test, package
+-- native-core.yml          cargo check for C ABI bridge
```

Native bridge workflows additionally produce platform-specific artifacts and publish to registries on GitHub releases.
