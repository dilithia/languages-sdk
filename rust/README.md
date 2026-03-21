# dilithia-sdk-rust

Rust client for the Dilithia blockchain. Builds typed RPC requests, canonical payloads for signing, and deploy/upgrade operations.

## Install

```toml
[dependencies]
dilithia-sdk-rust = "0.3"
```

## Usage

```rust
use dilithia_sdk_rust::{DilithiaClient, DilithiaCryptoAdapter, NativeCryptoAdapter};

// Client builds requests — you execute them with your HTTP client
let client = DilithiaClient::new("http://localhost:8000/rpc", Some(15_000))?;

// Balance
let req = client.get_balance_request("dili1alice");
// req is DilithiaRequest::Get { path: "http://..." }

// Contract call
let call = client.build_contract_call("token", "transfer",
    serde_json::json!({"to": "dili1bob", "amount": 100}), None);

// Deploy
use dilithia_sdk_rust::{DeployPayload, read_wasm_file_hex};
let bytecode = read_wasm_file_hex(std::path::Path::new("my_contract.wasm"))?;
let payload = DeployPayload { name: "my_contract".into(), bytecode, /* ... */ };
let req = client.deploy_contract_request(&payload);

// Crypto (post-quantum ML-DSA-65)
let adapter = NativeCryptoAdapter;
let mnemonic = adapter.generate_mnemonic()?;
let account = adapter.recover_hd_wallet(&mnemonic)?;
let sig = adapter.sign_message(&account.secret_key, "hello")?;
let valid = adapter.verify_message(&account.public_key, "hello", &sig.signature)?;
```

## What it provides

- **RPC request builders** — balance, nonce, receipt, address summary, gas estimate, base fee
- **Contract interaction** — call, query, simulate, ABI, forwarder
- **Deploy and upgrade** — canonical payload construction, bytecode hashing
- **Name service** — resolve, reverse resolve, lookup
- **Shielded pool** — deposit, withdraw, commitment root, nullifier check
- **Gas sponsorship** — paymaster attachment, sponsor connector
- **Cross-chain messaging** — send/receive message builders
- **Crypto adapter** — ML-DSA-65 signing, HD wallets, address derivation, key validation, hashing
- **ZK adapter trait** — Poseidon hash, preimage proofs, range proofs (requires `dilithia-stark`)

## Architecture

This crate builds `DilithiaRequest` values (GET or POST with path and body). You execute them with your preferred HTTP client (reqwest, ureq, hyper, etc.). This keeps the SDK dependency-free beyond `dilithia-core`.

## License

MIT OR Apache-2.0
