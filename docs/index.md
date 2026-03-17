# Dilithia SDK Documentation

Welcome to the official documentation for the **Dilithia Language SDKs** -- the monorepo providing non-browser SDK packages for the Dilithia blockchain ecosystem.

## What is this?

The Dilithia Language SDKs provide a unified interface to the Dilithia blockchain across multiple programming languages. Each SDK exposes the same logical surface: network configuration, account management, post-quantum cryptographic signing (ML-DSA-65 / Dilithium), transaction construction, and RPC interaction.

These SDKs are designed for:

- Backend services
- Bots and automation
- Infrastructure tooling
- Language-specific connectors and integrations

!!! note
    This is **not** the browser provider SDK. The browser-facing package is maintained in a separate repository.

## Supported Languages

| Language       | Package                        | Status     |
| -------------- | ------------------------------ | ---------- |
| **TypeScript** | `@dilithia/sdk-node`                       | Active     |
| **Python**     | `dilithia-sdk`                              | Active     |
| **Rust**       | `dilithia-sdk-rust`                         | Active     |
| **Go**         | `github.com/dilithia/languages-sdk/go`      | Active     |
| **Java**       | `org.dilithia:dilithia-sdk-java`            | Active     |

## SDK Surface

Every language SDK converges on the same logical capabilities:

- **RPC Client** -- JSON-RPC, REST, and WebSocket access to Dilithia nodes
- **Crypto Adapter** -- Mnemonic generation, HD wallet derivation, signing, and verification using `dilithia-core`
- **Contract Interaction** -- Query, simulate, and submit contract calls
- **Gas Sponsorship** -- Built-in gas sponsor connector for meta-transactions
- **Cross-chain Messaging** -- Send and receive cross-chain messages
- **Name Service** -- Resolve, lookup, and reverse-resolve `.dili` names

## Quick Links

- [Getting Started](getting-started.md) -- Installation and first steps for every language
- [Crypto Adapter API](api/crypto.md) -- Full reference for all 25 cryptographic methods
- [Types Reference](api/types.md) -- Shared type definitions across languages
- [RPC Client API](api/client.md) -- Client configuration, queries, and transaction submission
- [Architecture](reference/architecture.md) -- How `dilithia-core` flows through native bridges to each SDK

## Current Version

All language SDKs are versioned against the current RPC/core line:

```
0.2.0
```
