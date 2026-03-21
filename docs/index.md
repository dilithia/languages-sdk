# Dilithia SDK

Multi-language SDK for interacting with the Dilithia post-quantum blockchain.

**Current version: 0.3.0**

!!! note "Post-quantum cryptography"
    All Dilithia SDKs use **ML-DSA-65** (FIPS 204, formerly known as Dilithium) for key generation, signing, and verification. This is a lattice-based digital signature scheme designed to be secure against both classical and quantum adversaries.

!!! info "Not the browser SDK"
    This monorepo contains server-side / CLI packages. The browser provider SDK is maintained in a separate repository.

## Packages

| Language       | Package name                               | Registry     |
| -------------- | ------------------------------------------ | ------------ |
| TypeScript     | `@dilithia/sdk-node`                       | npm          |
| Python         | `dilithia-sdk`                             | PyPI         |
| Rust           | `dilithia-sdk-rust`                        | crates.io    |
| Go             | `github.com/dilithia/languages-sdk/go`     | Go modules   |
| Java           | `org.dilithia:dilithia-sdk-java`           | Maven Central|

### Native crypto bridges

Each SDK can load a native bridge that links directly to `dilithia-core` (Rust) for production-grade performance. The bridge packages are:

| Language       | Native bridge package                      | Mechanism          |
| -------------- | ------------------------------------------ | ------------------ |
| TypeScript     | `@dilithia/sdk-native`                     | N-API addon        |
| Python         | `dilithia-sdk-native`                      | PyO3 / maturin     |
| Rust           | Built-in (`NativeCryptoAdapter`)           | Direct crate dep   |
| Go             | Built-in (cgo, set `DILITHIUM_NATIVE_CORE_LIB`) | dlopen via cgo |
| Java           | Built-in (JNA, set `DILITHIUM_NATIVE_CORE_LIB`) | JNA + dlopen   |

!!! abstract "v0.3.0 -- Typed SDKs"
    All SDKs now return strongly typed response objects (`Balance`, `Receipt`, `SubmitResult`, `Nonce`, `GasEstimate`, `QueryResult`, `NameRecord`) instead of raw dictionaries/maps. Branded/named types (`Address`, `TxHash`, `PublicKey`, `SecretKey`, `TokenAmount`) prevent mixing up string parameters. Java uses a builder pattern, Go uses functional options, and Python async uses httpx for real async HTTP. See the [Types Reference](api/types.md) for full details.

## Capabilities

Every language SDK provides the same logical surface:

- **RPC client** -- JSON-RPC, REST, and WebSocket access to any Dilithia node
- **HD wallet management** -- Generate BIP-39 mnemonics, derive accounts at arbitrary indices, create and recover encrypted wallet files
- **Post-quantum signing** -- Sign arbitrary messages with ML-DSA-65 and verify signatures
- **Key utilities** -- Keygen, seed derivation, address checksumming, key/signature validation, constant-time comparison
- **Contract interaction** -- Query contract state, simulate calls, build and submit signed contract calls
- **Gas sponsorship** -- `GasSponsorConnector` for building and submitting meta-transactions through a paymaster
- **Cross-chain messaging** -- `MessagingConnector` for sending and receiving cross-chain messages
- **Name service** -- Resolve, reverse-resolve, and look up `.dili` names
- **Configurable hashing** -- Switch between SHA3-512, BLAKE2b-512, and BLAKE3-256 at runtime

## Quick links

- [Getting Started](getting-started.md) -- Installation and end-to-end examples for all five languages
- [Crypto Adapter API](api/crypto.md) -- Full reference for all 25 cryptographic methods
- [Types Reference](api/types.md) -- Shared type definitions across languages
- [RPC Client API](api/client.md) -- Client configuration, queries, and transaction submission
- [Architecture](reference/architecture.md) -- How `dilithia-core` flows through native bridges to each SDK
