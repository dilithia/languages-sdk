# Smart Contracts on Dilithia

This guide walks through the full smart contract lifecycle on Dilithia: writing, compiling, deploying, calling, upgrading, and inspecting contracts.

---

## Overview

Smart contracts on Dilithia are written in **Rust** and compiled to **WebAssembly** (`wasm32-unknown-unknown`). The contract SDK (`qsc-sdk`) provides a `#[contract]` procedural macro that generates the necessary boilerplate for storage, ABI export, and host-function bindings.

Key properties of the Dilithia contract model:

- **No node recompilation** -- contracts are deployed via JSON-RPC as hex-encoded WASM bytecode.
- **Post-quantum signatures** -- every deploy and upgrade transaction is signed with **ML-DSA-65** (FIPS 204), the same lattice-based signature scheme used for all Dilithia transactions.
- **Deterministic addressing** -- a contract's on-chain address is derived from the deployer address and contract name.
- **Upgradeable by admin** -- the original deployer can push new bytecode to the same contract name, preserving state.

---

## Writing a Contract

### Minimal example -- counter contract

```rust
use qsc_sdk::prelude::*;

#[contract]
pub struct Counter;

#[contract]
impl Counter {
    /// Increment the counter by 1 and return the new value.
    pub fn increment(ctx: &mut Ctx) -> u64 {
        let current: u64 = ctx.storage_get("count").unwrap_or(0);
        let next = current + 1;
        ctx.storage_set("count", &next);
        ctx.emit("incremented", &serde_json::json!({ "value": next }));
        next
    }

    /// Read the current counter value (read-only).
    pub fn get(ctx: &Ctx) -> u64 {
        ctx.storage_get("count").unwrap_or(0)
    }
}
```

### Method signatures

| Receiver | Meaning | Use when... |
|---|---|---|
| `&mut Ctx` | **Mutable** -- may write storage, emit events, call other contracts | State-changing operations (transactions) |
| `&Ctx` | **Read-only** -- may read storage and query other contracts | Pure reads / queries |

### Available host functions

The `Ctx` object exposes these host functions:

| Function | Description |
|---|---|
| `ctx.storage_get::<T>(key)` | Deserialize a value from contract storage |
| `ctx.storage_set(key, &value)` | Serialize and persist a value to contract storage |
| `ctx.storage_delete(key)` | Remove a key from contract storage |
| `ctx.emit(event_name, &data)` | Emit a named event (included in the transaction receipt) |
| `ctx.call_contract(address, method, args)` | Call a method on another contract (state-changing) |
| `ctx.query_contract(address, method, args)` | Query a method on another contract (read-only) |
| `ctx.caller()` | Address of the account or contract that invoked this call |
| `ctx.self_address()` | This contract's own address |
| `ctx.block_height()` | Current block height |
| `ctx.block_timestamp()` | Current block timestamp (Unix seconds) |

---

## Compiling

You must produce a `.wasm` binary targeting `wasm32-unknown-unknown`. There are three ways to do this.

### Option 1 -- Manual `cargo build`

```bash
# One-time setup
rustup target add wasm32-unknown-unknown

# Build
cargo build --target wasm32-unknown-unknown --release
```

The output lands in `target/wasm32-unknown-unknown/release/<crate_name>.wasm`.

### Option 2 -- `dilithia-contract` CLI

```bash
dilithia-contract build --path .
```

This runs `cargo build` with the correct target and, by default, optimizes the output with `wasm-opt` (pass `--optimize false` to skip).

### Option 3 -- Docker

The official Docker image bundles Rust 1.88, the `wasm32-unknown-unknown` target, and `binaryen` (`wasm-opt`):

```bash
docker run --rm -v "$(pwd)":/workspace ghcr.io/dilithia/contract-builder:latest
```

The entrypoint is `cargo build --target wasm32-unknown-unknown --release`, so simply mount your project at `/workspace`.

### Optimizing with `wasm-opt`

Smaller binaries mean lower deployment gas costs. After building, run:

```bash
wasm-opt -Oz -o optimized.wasm \
  target/wasm32-unknown-unknown/release/my_contract.wasm
```

!!! tip
    The `dilithia-contract build` command and the Docker image both run `wasm-opt` automatically.

---

## Deploying

Deployment follows the same five steps in every SDK:

1. **Read** the `.wasm` file and hex-encode it.
2. **Hash** the bytecode hex to produce `bytecode_hash`.
3. **Build the canonical payload** -- a JSON object with keys in alphabetical order: `bytecode_hash`, `chain_id`, `from`, `name`, `nonce`.
4. **Sign** the canonical payload with ML-DSA-65.
5. **POST** the full `DeployPayload` (bytecode + signature) to the `/deploy` endpoint.

=== "Rust"

    ```rust
    use std::path::Path;
    use dilithia_sdk::{
        DilithiaClient, DeployPayload, NativeCryptoAdapter,
        DilithiaCryptoAdapter, read_wasm_file_hex,
    };

    fn main() -> Result<(), Box<dyn std::error::Error>> {
        let client = DilithiaClient::new("http://localhost:8000/rpc", None)?;
        let crypto = NativeCryptoAdapter;

        // Recover account from mnemonic
        let account = crypto.recover_hd_wallet("your twelve word mnemonic ...")?;

        // 1. Read WASM and hex-encode
        let bytecode_hex = read_wasm_file_hex(Path::new("my_contract.wasm"))?;

        // 2. Hash the bytecode
        let bytecode_hash = crypto.hash_hex(
            &hex::encode(bytecode_hex.as_bytes())
        )?;

        // 3. Build canonical payload
        let canonical = DilithiaClient::build_deploy_canonical_payload(
            &account.address,
            "my_contract",
            &bytecode_hex,
            0,           // nonce
            "dilithia-1", // chain_id
        );

        // 4. Sign the canonical payload
        let canonical_json = serde_json::to_string(&canonical)?;
        let sig = crypto.sign_message(&account.secret_key, &canonical_json)?;

        // 5. Send deploy request
        let payload = DeployPayload {
            name: "my_contract".into(),
            bytecode: bytecode_hex,
            from: account.address.clone(),
            alg: sig.algorithm,
            pk: account.public_key.clone(),
            sig: sig.signature,
            nonce: 0,
            chain_id: "dilithia-1".into(),
            version: 1,
        };
        let request = client.deploy_contract_request(&payload);
        // Execute `request` with your HTTP client of choice
        println!("Deploy request: {:?}", request);
        Ok(())
    }
    ```

=== "TypeScript"

    ```typescript
    import {
      DilithiaClient,
      readWasmFileHex,
      loadNativeCryptoAdapter,
    } from "@dilithia/sdk";

    const client = new DilithiaClient({
      rpcUrl: "http://localhost:8000/rpc",
    });
    const crypto = await loadNativeCryptoAdapter();

    // Recover account from mnemonic
    const account = await crypto.recoverHDWallet(
      "your twelve word mnemonic ..."
    );

    // 1. Read WASM and hex-encode
    const bytecodeHex = readWasmFileHex("my_contract.wasm");

    // 2. Hash the bytecode
    const bytecodeHash = await crypto.hashHex(bytecodeHex);

    // 3. Build canonical payload
    const canonical = client.buildDeployCanonicalPayload(
      account.address,
      "my_contract",
      bytecodeHash,
      0,            // nonce
      "dilithia-1"  // chainId
    );

    // 4. Sign the canonical payload
    const canonicalJson = JSON.stringify(canonical);
    const sig = await crypto.signMessage(account.secretKey, canonicalJson);

    // 5. Send deploy request
    const { path, body } = client.deployContractRequest({
      name: "my_contract",
      bytecode: bytecodeHex,
      from: account.address,
      alg: sig.algorithm,
      pk: account.publicKey,
      sig: sig.signature,
      nonce: 0,
      chainId: "dilithia-1",
      version: 1,
    });

    const response = await fetch(`${client.baseUrl}${path}`, {
      method: "POST",
      headers: client.buildAuthHeaders({
        "content-type": "application/json",
      }),
      body: JSON.stringify(body),
    });
    console.log(await response.json());
    ```

=== "Python"

    ```python
    from dilithia_sdk import DilithiaClient, read_wasm_file_hex

    client = DilithiaClient("http://localhost:8000/rpc")

    # Assumes you have a crypto adapter instance
    # (see the Native Bridges guide for setup)
    account = crypto.recover_hd_wallet("your twelve word mnemonic ...")

    # 1. Read WASM and hex-encode
    bytecode_hex = read_wasm_file_hex("my_contract.wasm")

    # 2. Hash the bytecode
    bytecode_hash = crypto.hash_hex(bytecode_hex)

    # 3. Build canonical payload
    canonical = client.build_deploy_canonical_payload(
        from_addr=account.address,
        name="my_contract",
        bytecode_hash=bytecode_hash,
        nonce=0,
        chain_id="dilithia-1",
    )

    # 4. Sign the canonical payload
    import json
    canonical_json = json.dumps(canonical, separators=(",", ":"))
    sig = crypto.sign_message(account.secret_key, canonical_json)

    # 5. Send deploy request
    body = client.deploy_contract_body(
        name="my_contract",
        bytecode=bytecode_hex,
        from_addr=account.address,
        alg=sig.algorithm,
        pk=account.public_key,
        sig=sig.signature,
        nonce=0,
        chain_id="dilithia-1",
    )
    result = client.deploy_contract(body)
    print(result)
    ```

=== "Go"

    ```go
    package main

    import (
        "context"
        "encoding/json"
        "fmt"
        "time"

        sdk "github.com/dilithia/languages-sdk/go/sdk"
    )

    func main() {
        ctx := context.Background()
        client := sdk.NewClient("http://localhost:8000/rpc", sdk.WithTimeout(10*time.Second))

        // Assumes you have a CryptoAdapter instance
        account, _ := crypto.RecoverHDWallet(ctx, "your twelve word mnemonic ...")

        // 1. Read WASM and hex-encode
        bytecodeHex, _ := sdk.ReadWasmFileHex("my_contract.wasm")

        // 2. Hash the bytecode
        bytecodeHash, _ := crypto.HashHex(ctx, bytecodeHex)

        // 3. Build canonical payload
        canonical := client.BuildDeployCanonicalPayload(
            account.Address,
            "my_contract",
            bytecodeHash,
            0,            // nonce
            "dilithia-1", // chainID
        )

        // 4. Sign the canonical payload
        canonicalJSON, _ := json.Marshal(canonical)
        sig, _ := crypto.SignMessage(ctx, account.SecretKey, string(canonicalJSON))

        // 5. Build and POST the deploy body
        body := client.DeployContractBody(sdk.DeployPayload{
            Name:     "my_contract",
            Bytecode: bytecodeHex,
            From:     account.Address,
            Alg:      sig.Algorithm,
            PK:       account.PublicKey,
            Sig:      sig.Signature,
            Nonce:    0,
            ChainID:  "dilithia-1",
            Version:  1,
        })
        deployURL := client.DeployContractPath()

        // POST body to deployURL with your HTTP client
        fmt.Printf("POST %s\n%v\n", deployURL, body)
    }
    ```

=== "Java"

    ```java
    import org.dilithia.sdk.*;
    import org.dilithia.sdk.types.*;
    import java.time.Duration;

    public class Deploy {
        public static void main(String[] args) throws Exception {
            // Builder pattern for client creation
            var client = Dilithia.client("http://localhost:8000/rpc")
                .timeout(Duration.ofSeconds(10))
                .build();

            // Assumes you have a CryptoAdapter instance
            var account = crypto.recoverHdWallet("your twelve word mnemonic ...");

            // 1. Read WASM and hex-encode
            String bytecodeHex = Dilithia.readWasmFileHex("my_contract.wasm");

            // 2. Hash the bytecode
            String bytecodeHash = crypto.hashHex(bytecodeHex);

            // 3. Build canonical payload
            var canonical = client.buildDeployCanonicalPayload(
                Address.of(account.address().value()),
                "my_contract",
                bytecodeHash,
                0L,           // nonce
                "dilithia-1"  // chainId
            );

            // 4. Sign the canonical payload (Gson for sorted-key JSON)
            String canonicalJson = new com.google.gson.Gson().toJson(canonical);
            var sig = crypto.signMessage(account.secretKey().value(), canonicalJson);

            // 5. Build and submit the deploy payload
            var payload = new DeployPayload(
                "my_contract",
                bytecodeHex,
                account.address().value(),
                sig.algorithm(),
                account.publicKey().value(),
                sig.signature(),
                0L,
                "dilithia-1",
                1
            );
            SubmitResult result = client.deploy(payload).get();
            System.out.println("Deployed: " + result.txHash());
        }
    }
    ```

!!! warning "Nonce management"
    The `nonce` must match the deployer account's current nonce on-chain. Fetch it first with `getNonce(address)` / `get_nonce(address)` or from `getAddressSummary`.

!!! info "Canonical payload field order"
    The canonical payload keys **must** be in alphabetical order (`bytecode_hash`, `chain_id`, `from`, `name`, `nonce`) so that every SDK produces an identical byte string for signing. All SDK methods already enforce this ordering.

---

## Calling a Contract

Once deployed, interact with a contract using `sendCall` / `send_call` (state-changing) or `queryContract` / `query_contract` (read-only). See the [Signing Transactions guide](signing-transactions.md) for full details on building and signing contract calls.

Quick example (TypeScript):

```typescript
// Read-only query
const value = await client.queryContract("wasm:my_contract", "get", {});

// State-changing call
const result = await client.callContract("wasm:my_contract", "increment", {});
```

---

## Upgrading

Upgrading a contract uses the **same payload format** as deployment, but posts to the `/upgrade` endpoint instead of `/deploy`.

**Rules:**

- Only the **original deployer** (admin) may upgrade a contract.
- The contract **name** must match an existing deployment.
- On-chain **state is preserved** -- only the WASM bytecode is replaced.

=== "Rust"

    ```rust
    let request = client.upgrade_contract_request(&payload);
    ```

=== "TypeScript"

    ```typescript
    const { path, body } = client.upgradeContractRequest(payload);
    // POST to `${client.baseUrl}${path}`
    ```

=== "Python"

    ```python
    result = client.upgrade_contract(body)
    ```

=== "Go"

    ```go
    body := client.UpgradeContractBody(payload)
    url := client.UpgradeContractPath()
    ```

=== "Java"

    ```java
    SubmitResult result = client.upgrade(payload).get();
    ```

!!! note
    Build the canonical payload and sign it exactly as you would for a deploy. The only difference is the HTTP endpoint.

---

## Querying ABI

Every deployed contract exposes its ABI (method names, parameter types, return types) via the `qsc_getAbi` JSON-RPC method.

=== "Rust"

    ```rust
    let request = client.query_contract_abi_request("wasm:my_contract");
    ```

=== "TypeScript"

    ```typescript
    const abiRequest = client.queryContractAbi("wasm:my_contract");
    const abi = await client.jsonRpc(abiRequest.method, abiRequest.params);
    ```

=== "Python"

    ```python
    abi = client.query_contract_abi("wasm:my_contract")
    ```

=== "Go"

    ```go
    body := client.QueryContractAbiBody("wasm:my_contract")
    ```

=== "Java"

    ```java
    var abi = client.queryContractAbi("wasm:my_contract").get();
    ```

---

## Scaffolding a New Project

The `dilithia-contract` CLI can generate a ready-to-build contract project:

```bash
dilithia-contract init --name my_contract
```

This creates a directory `my_contract/` containing:

```
my_contract/
  Cargo.toml          # wasm32 target, qsc-sdk dependency
  src/
    lib.rs            # Starter contract with #[contract] macro
  .cargo/
    config.toml       # Default build target set to wasm32-unknown-unknown
```

From there, build and deploy:

```bash
cd my_contract
dilithia-contract build
dilithia-contract deploy \
  --name my_contract \
  --wasm target/wasm32-unknown-unknown/release/my_contract.wasm \
  --rpc http://localhost:8000/rpc \
  --secret-key $DILITHIA_SECRET_KEY
```

!!! tip "Environment variable"
    The CLI reads `DILITHIA_SECRET_KEY` from the environment if `--secret-key` is not passed, so you can export it once in your shell session.
