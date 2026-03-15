# Dilithia Python SDK

Python SDK for the Dilithia RPC surface.

Current SDK line:

- `0.3.0`

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
- crypto adapter protocol aligned with `dilithia-core`

## Commands

```bash
python -m unittest discover -s tests
python -m build
```

The test suite also validates:

- SDK version: `0.3.0`
- RPC line version: `0.3.0`
- minimum supported Python version: `3.11`

The package also exposes `load_native_crypto_adapter()` to attach the optional Rust-backed Python bridge when present.
