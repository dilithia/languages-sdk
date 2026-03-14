# Dilithia Rename Plan

This document fixes the target public naming before the codebase-wide rename.

The current repositories and packages still contain `dilithium` in many places.
That is temporary. The public ecosystem target is `dilithia`.

## Public Naming Targets

### GitHub

- Organization: `dilithia`
- Monorepo: `languages-sdk`
- Browser provider SDK repo target: `dilithia-sdk`
- Wallet repo target: `dilithia-wallet-extension`
- MCP repo target: `dilithia-mcp-servers`

### npm

Use scoped packages under `@dilithia`.

- TypeScript SDK: `@dilithia/sdk-node`
- Browser/provider SDK: `@dilithia/sdk-browser`
- Node native crypto bridge: `@dilithia/sdk-node-crypto`

### PyPI

- Python SDK: `dilithia-sdk`
- Python native crypto bridge: `dilithia-sdk-python-crypto`

### crates.io

- Rust SDK: `dilithia-sdk-rust`
- Shared crypto/native crates should use the `dilithia-` prefix

Suggested placeholders to reserve early:

- `dilithia-sdk`
- `dilithia-crypto`

### Go

Use the GitHub import path under the new GitHub organization.

- module base: `github.com/dilithia/languages-sdk/go`

### Maven Central

Use the group namespace:

- `org.dilithia`

Initial Java coordinates:

- `org.dilithia:dilithia-sdk-java`

Target Java package namespace for the later code rename:

- `org.dilithia.sdk`

## Rename Policy

Do not perform a blind global search/replace.

Order:

1. Reserve or create public namespaces first.
2. Fix publication metadata.
3. Rename package names and import paths.
4. Rename visible product strings.
5. Add compatibility aliases only where already externally consumed.

Because these packages are not publicly released yet, compatibility shims are
generally optional. The main requirement is to do the rename once, cleanly.

## Current Decisions

- Maven namespace is fixed to `org.dilithia`.
- npm should prefer scoped packages under `@dilithia`.
- Go should follow the GitHub org path.
- Python and crates should use `dilithia-...` package names.
- Browser/provider SDK remains conceptually separate from `languages-sdk`.
