# Dilithia TypeScript SDK

TypeScript and Node.js SDK for the Dilithia RPC surface.

Current SDK line:

- `0.2.0`

## Scope

- RPC client construction
- raw JSON-RPC helpers
- raw REST helpers
- websocket URL derivation/configuration
- balance, nonce, receipt and address-summary reads
- gas estimate and base fee helpers
- name service helpers
- contract query and call helpers
- forwarder/meta-tx helpers
- gas sponsor connector
- messaging connector
- simulation helpers
- transaction submission helpers
- polling helpers
- crypto adapter interface aligned with `dilithia-core`

## Commands

```bash
npm install
npm run build
npm test
```

The test suite also validates:

- SDK version: `0.2.0`
- RPC line version: `0.2.0`
- minimum supported Node.js major: `22`

The package also exposes `loadNativeCryptoAdapter()` to attach the optional Rust-backed Node bridge when present.
