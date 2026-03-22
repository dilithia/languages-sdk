# RPC Client API Reference

The `DilithiaClient` provides a complete interface for interacting with Dilithia blockchain nodes over JSON-RPC, REST, and WebSocket. Every language SDK exposes the same logical surface.

---

## Configuration

### Creating a Client

=== "TypeScript"

    ```typescript
    import { DilithiaClient, createClient } from "@dilithia/sdk";

    // Direct construction
    const client = new DilithiaClient({
      rpcUrl: "https://rpc.dilithia.network/rpc",
      timeoutMs: 15_000,
      jwt: "my-bearer-token",
    });

    // Factory function
    const client2 = createClient({ rpcUrl: "https://rpc.dilithia.network/rpc" });
    ```

=== "Python (sync)"

    ```python
    from dilithia_sdk import DilithiaClient

    client = DilithiaClient(
        "https://rpc.dilithia.network/rpc",
        timeout=15.0,
        jwt="my-bearer-token",
    )
    ```

=== "Python (async)"

    ```python
    from dilithia_sdk import AsyncDilithiaClient

    # Uses httpx for real async HTTP (not asyncio.to_thread on urllib)
    client = AsyncDilithiaClient(
        "https://rpc.dilithia.network/rpc",
        timeout=15.0,
        jwt="my-bearer-token",
    )
    ```

=== "Rust"

    ```rust
    use dilithia_sdk::{DilithiaClient, DilithiaClientConfig};

    // Simple construction
    let client = DilithiaClient::new("https://rpc.dilithia.network/rpc", None)?;

    // Full configuration
    let client = DilithiaClient::from_config(DilithiaClientConfig {
        rpc_url: "https://rpc.dilithia.network/rpc".to_string(),
        jwt: Some("my-bearer-token".to_string()),
        timeout_ms: Some(15_000),
        ..DilithiaClientConfig::default()
    })?;
    ```

=== "Go"

    ```go
    import sdk "github.com/dilithia/languages-sdk/go/sdk"

    // Functional options pattern
    client := sdk.NewClient(
        "https://rpc.dilithia.network/rpc",
        sdk.WithTimeout(15*time.Second),
        sdk.WithJWT("my-bearer-token"),
    )
    ```

=== "Java"

    ```java
    import org.dilithia.sdk.Dilithia;

    // Builder pattern
    var client = Dilithia.client("https://rpc.dilithia.network/rpc")
        .timeout(Duration.ofSeconds(15))
        .jwt("my-bearer-token")
        .build();
    ```

=== "C#"

    ```csharp
    using Dilithia.Sdk;

    // Builder pattern (IDisposable)
    using var client = DilithiaClient.Create("https://rpc.dilithia.network/rpc")
        .WithTimeout(TimeSpan.FromSeconds(15))
        .WithJwt("my-bearer-token")
        .Build();
    ```

See [DilithiaClientConfig](types.md#dilithiaclientconfig) for all available configuration options.

### URL Derivation

The client automatically derives related URLs from the `rpcUrl`:

- **`baseUrl`** -- Strips `/rpc` suffix from `rpcUrl` (e.g. `https://rpc.dilithia.network/rpc` becomes `https://rpc.dilithia.network`). Override with `chainBaseUrl`.
- **`wsUrl`** -- Converts `baseUrl` to WebSocket protocol (e.g. `https://` becomes `wss://`). Override with `wsUrl`.

### Authentication

If a `jwt` is configured, all requests include an `Authorization: Bearer <token>` header. Additional headers can be set via the `headers` configuration.

```typescript
const headers = client.buildAuthHeaders({ "x-custom": "value" });
// { Authorization: "Bearer ...", ...configuredHeaders, "x-custom": "value" }
```

---

## Balance, Nonce, and Account Queries

### `getBalance`

Fetch the balance of an address.

=== "TypeScript"

    ```typescript
    const balance: Balance = await client.getBalance("dili1abc...");
    // balance.available  -- TokenAmount (BigInt)
    // balance.staked     -- TokenAmount (BigInt)
    ```

=== "Python"

    ```python
    balance: Balance = client.get_balance("dili1abc...")
    # balance.available  -- TokenAmount (Decimal)
    # balance.staked     -- TokenAmount (Decimal)
    ```

=== "Rust"

    ```rust
    let request = client.get_balance_request("dili1abc...");
    ```

=== "Go"

    ```go
    balance, err := client.GetBalance(ctx, "dili1abc...")
    // balance.Available  -- *big.Int
    // balance.Staked     -- *big.Int
    ```

=== "Java"

    ```java
    Balance balance = client.balance(Address.of("dili1abc...")).get();
    // balance.available()  -- TokenAmount (BigDecimal)
    // balance.staked()     -- TokenAmount (BigDecimal)
    ```

=== "C#"

    ```csharp
    Balance balance = await client.GetBalanceAsync("dili1abc...");
    // balance.Available  -- TokenAmount (decimal)
    // balance.Staked     -- TokenAmount (decimal)
    ```

**Returns:** A typed `Balance` object containing account balance fields.

---

### `getNonce`

Fetch the current nonce (transaction count) of an address.

=== "TypeScript"

    ```typescript
    const nonce: Nonce = await client.getNonce("dili1abc...");
    ```

=== "Python"

    ```python
    nonce: Nonce = client.get_nonce("dili1abc...")
    ```

=== "Rust"

    ```rust
    let request = client.get_nonce_request("dili1abc...");
    ```

=== "Go"

    ```go
    nonce, err := client.GetNonce(ctx, "dili1abc...")
    // nonce.Value -- uint64
    ```

=== "Java"

    ```java
    Nonce nonce = client.nonce(Address.of("dili1abc...")).get();
    ```

=== "C#"

    ```csharp
    Nonce nonce = await client.GetNonceAsync("dili1abc...");
    ```

**Returns:** A typed `Nonce` object.

---

### `getReceipt`

Fetch a transaction receipt by hash.

=== "TypeScript"

    ```typescript
    const receipt: Receipt = await client.getReceipt("0xabc123...");
    // receipt.status, receipt.gasUsed, receipt.blockHeight, ...
    ```

=== "Python"

    ```python
    receipt: Receipt = client.get_receipt("0xabc123...")
    # receipt.status, receipt.gas_used, receipt.block_height, ...
    ```

=== "Rust"

    ```rust
    let request = client.get_receipt_request("0xabc123...");
    ```

=== "Go"

    ```go
    receipt, err := client.GetReceipt(ctx, "0xabc123...")
    // receipt.Status, receipt.GasUsed, receipt.BlockHeight, ...
    ```

=== "Java"

    ```java
    Receipt receipt = client.receipt(TxHash.of("0xabc123...")).get();
    ```

=== "C#"

    ```csharp
    Receipt receipt = await client.GetReceiptAsync("0xabc123...");
    // receipt.Status, receipt.GasUsed, receipt.BlockHeight, ...
    ```

**Returns:** A typed `Receipt` object with transaction execution details.

---

### `getAddressSummary`

Get a comprehensive summary of an address via JSON-RPC (`qsc_addressSummary`).

=== "TypeScript"

    ```typescript
    await client.getAddressSummary("dili1abc...");
    ```

=== "Python"

    ```python
    client.get_address_summary("dili1abc...")
    ```

=== "Rust"

    ```rust
    let request = client.get_address_summary_request("dili1abc...");
    ```

=== "C#"

    ```csharp
    var summary = await client.GetAddressSummaryAsync("dili1abc...");
    ```

---

## Gas and Fee Estimation

### `getGasEstimate`

Get the current gas estimate via JSON-RPC (`qsc_gasEstimate`).

=== "TypeScript"

    ```typescript
    const estimate: GasEstimate = await client.getGasEstimate();
    ```

=== "Python"

    ```python
    estimate: GasEstimate = client.get_gas_estimate()
    ```

=== "Rust"

    ```rust
    let request = client.get_gas_estimate_request();
    ```

=== "Go"

    ```go
    estimate, err := client.GetGasEstimate(ctx)
    ```

=== "Java"

    ```java
    GasEstimate estimate = client.gasEstimate().get();
    ```

=== "C#"

    ```csharp
    GasEstimate estimate = await client.GetGasEstimateAsync();
    ```

---

### `getBaseFee`

Get the current base fee via JSON-RPC (`qsc_baseFee`).

=== "TypeScript"

    ```typescript
    const fee = await client.getBaseFee();
    ```

=== "Python"

    ```python
    fee = client.get_base_fee()
    ```

=== "Rust"

    ```rust
    let request = client.get_base_fee_request();
    ```

=== "C#"

    ```csharp
    var fee = await client.GetBaseFeeAsync();
    ```

---

## Chain State Queries (Python)

The Python client includes additional chain state query methods:

| Method              | RPC Method         | Description                         |
| ------------------- | ------------------ | ----------------------------------- |
| `get_head()`        | `qsc_head`         | Current chain head                  |
| `get_chain()`       | `qsc_chain`        | Chain metadata                      |
| `get_state_root()`  | `qsc_stateRoot`    | Current state root hash             |
| `get_tps()`         | `qsc_tps`          | Current transactions per second     |
| `get_block(height)` | `qsc_getBlock`     | Block at a specific height          |
| `get_blocks(from, to)` | `qsc_getBlocks` | Range of blocks                     |
| `get_tx_block(hash)`   | `qsc_getTxBlock`| Block containing a transaction      |
| `get_internal_txs(hash)` | `qsc_internalTxs` | Internal transactions for a tx |
| `get_address_txs(addr)` | `qsc_getAddressTxs` | Transactions for an address    |
| `search_hash(hash)` | `qsc_search`       | Search by hash                      |

---

## JSON-RPC

### `jsonRpc` / `rawRpc`

Send an arbitrary JSON-RPC request.

=== "TypeScript"

    ```typescript
    const result = await client.jsonRpc("qsc_head", { full: true });
    ```

=== "Python"

    ```python
    result = client.json_rpc("qsc_head", {"full": True})
    ```

---

### `buildJsonRpcRequest`

Build a JSON-RPC request object without sending it.

=== "TypeScript"

    ```typescript
    const request = client.buildJsonRpcRequest("qsc_head", { full: true }, 1);
    // { jsonrpc: "2.0", id: 1, method: "qsc_head", params: { full: true } }
    ```

=== "Python"

    ```python
    request = client.build_json_rpc_request("qsc_head", {"full": True})
    ```

=== "Rust"

    ```rust
    let request = client.build_jsonrpc_request("qsc_head", json!({"full": true}), 1);
    ```

---

### Batch Requests (Python)

The Python client supports JSON-RPC batch requests:

```python
# Custom batch
results = client.json_rpc_batch([
    ("qsc_head", {}),
    ("qsc_gasEstimate", {}),
])

# Pre-built batches
overview = client.json_rpc_batch(client.build_network_overview_batch())
tx_details = client.json_rpc_batch(client.build_transaction_details_batch("0xabc..."))
addr_details = client.json_rpc_batch(client.build_address_details_batch("dili1abc..."))
```

---

## REST Access

### `rawGet` / `rawPost`

Send raw HTTP requests through the client (inheriting auth headers and timeout).

=== "TypeScript"

    ```typescript
    // GET against RPC base
    const data = await client.rawGet("/custom/endpoint");

    // GET against chain base URL
    const data2 = await client.rawGet("/custom/endpoint", true);

    // POST
    const result = await client.rawPost("/custom", { key: "value" });
    ```

=== "Python"

    ```python
    data = client.raw_get("/custom/endpoint")
    data2 = client.raw_get("/custom/endpoint", use_chain_base=True)
    result = client.raw_post("/custom", {"key": "value"})
    ```

---

## WebSocket

### `getWsConnectionInfo`

Get WebSocket connection details including URL and authentication headers.

=== "TypeScript"

    ```typescript
    const info = client.getWsConnectionInfo();
    // { url: "wss://rpc.dilithia.network", headers: { Authorization: "Bearer ..." } }
    ```

=== "Python"

    ```python
    info = client.get_ws_connection_info()
    ```

=== "Rust"

    ```rust
    let info = client.ws_connection_info();
    ```

---

### `buildWsRequest`

Build a JSON-RPC request suitable for sending over WebSocket.

=== "TypeScript"

    ```typescript
    const msg = client.buildWsRequest("subscribe_heads", { full: true }, 1);
    ws.send(JSON.stringify(msg));
    ```

=== "Python"

    ```python
    msg = client.build_ws_request("subscribe_heads", {"full": True})
    ```

=== "Rust"

    ```rust
    let msg = client.build_ws_request("subscribe_heads", json!({"full": true}), 1);
    ```

---

## Contract Deployment

Methods for deploying and upgrading WASM smart contracts. The workflow is: read the WASM file, hash the bytecode, build a canonical payload for signing, sign it, assemble the full `DeployPayload`, and send the deploy (or upgrade) request.

### `readWasmFileHex`

Read a `.wasm` file from disk and return its contents as a hex-encoded string. This is a standalone utility function, not a method on the client.

=== "TypeScript"

    ```typescript
    import { readWasmFileHex } from "@dilithia/sdk-node";

    const bytecodeHex = readWasmFileHex("./my_contract.wasm");
    ```

=== "Python"

    ```python
    from dilithia_sdk import read_wasm_file_hex

    bytecode_hex = read_wasm_file_hex("./my_contract.wasm")
    ```

=== "Rust"

    ```rust
    use dilithia_sdk_rust::read_wasm_file_hex;
    use std::path::Path;

    let bytecode_hex = read_wasm_file_hex(Path::new("./my_contract.wasm"))?;
    ```

=== "Go"

    ```go
    bytecodeHex, err := sdk.ReadWasmFileHex("./my_contract.wasm")
    ```

=== "Java"

    ```java
    String bytecodeHex = Dilithia.readWasmFileHex("./my_contract.wasm");
    ```

=== "C#"

    ```csharp
    string bytecodeHex = DilithiaClient.ReadWasmFileHex("./my_contract.wasm");
    ```

| Parameter  | Type     | Description                        |
| ---------- | -------- | ---------------------------------- |
| `filePath` | `string` | Path to the `.wasm` binary file    |

**Returns:** `string` -- hex-encoded bytes of the WASM file.

---

### `buildDeployCanonicalPayload`

Build the canonical payload for a deploy or upgrade request. Keys are sorted alphabetically for deterministic signing. The payload is signed before being included in the `DeployPayload`.

=== "TypeScript"

    ```typescript
    const canonical = client.buildDeployCanonicalPayload(
      account.address, "my_contract", bytecodeHash, nonce, "dilithia-mainnet"
    );
    ```

=== "Python"

    ```python
    canonical = client.build_deploy_canonical_payload(
        account.address, "my_contract", bytecode_hash, nonce, "dilithia-mainnet"
    )
    ```

=== "Rust"

    ```rust
    // Note: Rust version takes raw bytecode_hex and hashes internally
    let canonical = DilithiaClient::build_deploy_canonical_payload(
        &account.address, "my_contract", &bytecode_hex, nonce, "dilithia-mainnet"
    );
    ```

=== "Go"

    ```go
    canonical := client.BuildDeployCanonicalPayload(
        account.Address, "my_contract", bytecodeHash, nonce, "dilithia-mainnet",
    )
    ```

=== "Java"

    ```java
    var canonical = client.buildDeployCanonicalPayload(
        Address.of(account.address()), "my_contract", bytecodeHash, nonce, "dilithia-mainnet"
    );
    ```

=== "C#"

    ```csharp
    var canonical = DilithiaClient.BuildDeployCanonicalPayload(
        account.Address, "my_contract", bytecodeHash, nonce, "dilithia-mainnet"
    );
    ```

| Parameter      | Type     | Description                                                |
| -------------- | -------- | ---------------------------------------------------------- |
| `from`         | `string` | Deployer address                                           |
| `name`         | `string` | Contract name                                              |
| `bytecodeHash` | `string` | Hash of the bytecode hex (Rust hashes internally from raw hex) |
| `nonce`        | `uint64` | Current account nonce                                      |
| `chainId`      | `string` | Target chain identifier                                    |

**Returns:** A sorted dictionary/map with keys `bytecode_hash`, `chain_id`, `from`, `name`, `nonce`.

---

### `deployContractRequest` / `deploy_contract`

Build or send the HTTP request for deploying a new contract. TypeScript and Rust return a request descriptor; Python and Go send the request directly.

=== "TypeScript"

    ```typescript
    const { path, body } = client.deployContractRequest(deployPayload);
    // POST to chainBaseUrl + path with body
    ```

=== "Python"

    ```python
    # Build body only (low-level)
    body = client.deploy_contract_body(name, bytecode, from_addr, alg, pk, sig, nonce, chain_id)

    # Or send directly (high-level)
    result = client.deploy_contract(body)
    ```

=== "Rust"

    ```rust
    let request = client.deploy_contract_request(&deploy_payload);
    // Returns DilithiaRequest::Post { path, body }
    ```

=== "Go"

    ```go
    path := client.DeployContractPath()
    body := client.DeployContractBody(deployPayload)
    // POST body to path
    ```

=== "Java"

    ```java
    SubmitResult result = client.deploy(deployPayload).get();
    ```

=== "C#"

    ```csharp
    SubmitResult result = await client.DeployContractAsync(deployPayload);
    ```

| Parameter | Type            | Description                                             |
| --------- | --------------- | ------------------------------------------------------- |
| `payload` | `DeployPayload` | The fully assembled deploy payload including signature   |

**Returns:** Request descriptor (TypeScript/Rust) or the response from the deploy endpoint (Python).

---

### `upgradeContractRequest` / `upgrade_contract`

Build or send the HTTP request for upgrading an existing contract. Same interface as deploy but targets the `/upgrade` endpoint.

=== "TypeScript"

    ```typescript
    const { path, body } = client.upgradeContractRequest(deployPayload);
    ```

=== "Python"

    ```python
    result = client.upgrade_contract(body)
    ```

=== "Rust"

    ```rust
    let request = client.upgrade_contract_request(&deploy_payload);
    ```

=== "Go"

    ```go
    path := client.UpgradeContractPath()
    body := client.UpgradeContractBody(deployPayload)
    ```

=== "Java"

    ```java
    SubmitResult result = client.upgrade(deployPayload).get();
    ```

=== "C#"

    ```csharp
    SubmitResult result = await client.UpgradeContractAsync(deployPayload);
    ```

| Parameter | Type            | Description                                             |
| --------- | --------------- | ------------------------------------------------------- |
| `payload` | `DeployPayload` | The fully assembled deploy payload including signature   |

**Returns:** Request descriptor (TypeScript/Rust) or the response from the upgrade endpoint (Python).

---

### `queryContractAbi`

Query a contract's ABI definition via JSON-RPC (`qsc_getAbi`).

=== "TypeScript"

    ```typescript
    const rpcBody = client.queryContractAbi("my_contract");
    // Send as JSON-RPC request
    ```

=== "Python"

    ```python
    abi = client.query_contract_abi("my_contract")
    ```

=== "Rust"

    ```rust
    let request = client.query_contract_abi_request("my_contract");
    ```

=== "Go"

    ```go
    body := client.QueryContractAbiBody("my_contract")
    ```

=== "Java"

    ```java
    var abi = client.queryContractAbi("my_contract").get();
    ```

=== "C#"

    ```csharp
    ContractAbi abi = await client.QueryContractAbiAsync("my_contract");
    ```

| Parameter  | Type     | Description                     |
| ---------- | -------- | ------------------------------- |
| `contract` | `string` | Contract name or address        |

**Returns:** The contract's ABI definition (Python sends and returns the result; others return a request to send).

---

## Contract Interaction

### `queryContract`

Query a smart contract's read-only method (no transaction, no gas).

=== "TypeScript"

    ```typescript
    const result: QueryResult = await client.queryContract("wasm:amm", "get_reserves", {});
    ```

=== "Python"

    ```python
    result: QueryResult = client.query_contract("wasm:amm", "get_reserves", {})
    ```

=== "Rust"

    ```rust
    let request = client.query_contract_request("wasm:amm", "get_reserves", json!({}));
    ```

=== "Go"

    ```go
    result, err := client.QueryContract(ctx, "wasm:amm", "get_reserves", nil)
    ```

=== "Java"

    ```java
    QueryResult result = client.queryContract("wasm:amm", "get_reserves", Map.of()).get();
    ```

=== "C#"

    ```csharp
    QueryResult result = await client.QueryContractAsync("wasm:amm", "get_reserves", new {});
    ```

---

### `buildContractCall`

Build a contract call payload for submission.

=== "TypeScript"

    ```typescript
    const call = client.buildContractCall("wasm:token", "transfer", {
      to: "dili1xyz...",
      amount: 1000,
    });
    ```

=== "Python"

    ```python
    call = client.build_contract_call("wasm:token", "transfer", {
        "to": "dili1xyz...",
        "amount": 1000,
    })
    ```

=== "Rust"

    ```rust
    let call = client.build_contract_call(
        "wasm:token", "transfer",
        json!({"to": "dili1xyz...", "amount": 1000}),
        None,
    );
    ```

=== "C#"

    ```csharp
    var call = DilithiaClient.BuildContractCall("wasm:token", "transfer", new
    {
        to = "dili1xyz...",
        amount = 1000,
    });
    ```

---

### `simulate`

Simulate a call without committing it to the chain.

=== "TypeScript"

    ```typescript
    const result = await client.simulate(call);
    ```

=== "Python"

    ```python
    result = client.simulate(call)
    ```

=== "Rust"

    ```rust
    let request = client.simulate_request(call);
    ```

---

### `sendCall`

Submit a call to the chain.

=== "TypeScript"

    ```typescript
    const result: SubmitResult = await client.sendCall(call);
    // result.txHash -- TxHash (branded string)
    ```

=== "Python"

    ```python
    result: SubmitResult = client.send_call(call)
    # result.tx_hash -- TxHash
    ```

=== "Rust"

    ```rust
    let request = client.send_call_request(call);
    ```

=== "Go"

    ```go
    result, err := client.SendCall(ctx, call)
    // result.TxHash -- TxHash
    ```

=== "Java"

    ```java
    SubmitResult result = client.sendCall(call).get();
    // result.txHash() -- TxHash
    ```

=== "C#"

    ```csharp
    SubmitResult result = await client.SendCallAsync(call);
    // result.TxHash -- TxHash
    ```

---

### `sendSignedCall`

Sign a canonical call payload and submit it. Requires a signer object.

=== "TypeScript"

    ```typescript
    const result = await client.sendSignedCall(call, signer);
    ```

=== "Python"

    ```python
    result = client.send_signed_call(call, signer)
    ```

The signer must implement `signCanonicalPayload(payloadJson: string)` (TypeScript) or `sign_canonical_payload(payload_json: str)` (Python), returning a dictionary with signature fields.

---

### `waitForReceipt`

Poll for a transaction receipt until it becomes available.

=== "TypeScript"

    ```typescript
    const receipt: Receipt = await client.waitForReceipt("0xabc...", 12, 1000);
    ```

=== "Python"

    ```python
    receipt: Receipt = client.wait_for_receipt("0xabc...", max_attempts=12, delay_seconds=1.0)
    ```

=== "C#"

    ```csharp
    Receipt receipt = await client.WaitForReceiptAsync("0xabc...", maxAttempts: 12, delayMs: 1000);
    ```

| Parameter     | Type     | Default | Description                             |
| ------------- | -------- | ------- | --------------------------------------- |
| `txHash`      | `string` | --      | Transaction hash to poll for            |
| `maxAttempts` | `int`    | `12`    | Maximum number of polling attempts      |
| `delayMs`     | `int`    | `1000`  | Delay between attempts (milliseconds)   |

---

## Name Service

### `resolveName`

Resolve a `.dili` name to an address.

=== "TypeScript"

    ```typescript
    const record: NameRecord = await client.resolveName("alice.dili");
    ```

=== "Python"

    ```python
    record: NameRecord = client.resolve_name("alice.dili")
    ```

=== "Rust"

    ```rust
    let request = client.resolve_name_request("alice.dili");
    ```

=== "C#"

    ```csharp
    NameRecord record = await client.ResolveNameAsync("alice.dili");
    ```

---

### `reverseResolveName`

Reverse-resolve an address to its registered `.dili` name.

=== "TypeScript"

    ```typescript
    const record = await client.reverseResolveName("dili1abc...");
    ```

=== "Python"

    ```python
    record = client.reverse_resolve_name("dili1abc...")
    ```

=== "Rust"

    ```rust
    let request = client.reverse_resolve_name_request("dili1abc...");
    ```

=== "C#"

    ```csharp
    NameRecord record = await client.ReverseResolveAsync("dili1abc...");
    ```

---

### `registerName`

Register a `.dili` name. Cost depends on name length and is burned on-chain.

=== "TypeScript"

    ```typescript
    const result = await client.registerName("alice");
    ```

=== "Python"

    ```python
    result = client.register_name("alice")
    ```

=== "Rust"

    ```rust
    let request = client.register_name_request("alice");
    ```

=== "Go"

    ```go
    result, err := client.RegisterName(ctx, "alice")
    ```

=== "Java"

    ```java
    var result = client.names().registerName("alice").send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.RegisterNameAsync("alice");
    ```

| Parameter | Type     | Description                          |
| --------- | -------- | ------------------------------------ |
| `name`    | `string` | The name to register (without `.dili` suffix) |

**Returns:** A `NameRegistration` receipt containing the registered name, expiry block, and transaction hash.

---

### `renewName`

Renew an existing `.dili` name registration before it expires.

=== "TypeScript"

    ```typescript
    const result = await client.renewName("alice", 365);
    ```

=== "Python"

    ```python
    result = client.renew_name("alice", duration_days=365)
    ```

=== "Rust"

    ```rust
    let request = client.renew_name_request("alice", 365);
    ```

=== "Go"

    ```go
    result, err := client.RenewName(ctx, "alice", 365)
    ```

=== "Java"

    ```java
    var result = client.names().renewName("alice", 365).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.RenewNameAsync("alice", durationDays: 365);
    ```

| Parameter      | Type     | Description                                    |
| -------------- | -------- | ---------------------------------------------- |
| `name`         | `string` | The name to renew (without `.dili` suffix)     |
| `durationDays` | `int`    | Number of days to extend the registration      |

**Returns:** A `NameRenewal` receipt containing the new expiry block and transaction hash.

---

### `transferName`

Transfer ownership of a `.dili` name to another address.

=== "TypeScript"

    ```typescript
    const result = await client.transferName("alice", "dili1qpz9ckg7r4n6eftm...");
    ```

=== "Python"

    ```python
    result = client.transfer_name("alice", "dili1qpz9ckg7r4n6eftm...")
    ```

=== "Rust"

    ```rust
    let request = client.transfer_name_request("alice", "dili1qpz9ckg7r4n6eftm...");
    ```

=== "Go"

    ```go
    result, err := client.TransferName(ctx, "alice", "dili1qpz9ckg7r4n6eftm...")
    ```

=== "Java"

    ```java
    var result = client.names().transferName("alice", Address.of("dili1qpz9ckg7r4n6eftm...")).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.TransferNameAsync("alice", "dili1qpz9ckg7r4n6eftm...");
    ```

| Parameter    | Type     | Description                              |
| ------------ | -------- | ---------------------------------------- |
| `name`       | `string` | The name to transfer                     |
| `newOwner`   | `string` | Destination address for the transfer     |

**Returns:** A `NameTransfer` receipt containing the transaction hash and new owner address.

---

### `setNameTarget`

Set the default resolution target address for a `.dili` name.

=== "TypeScript"

    ```typescript
    const result = await client.setNameTarget("alice", "dili1w7ek3fhz9acq0mp...");
    ```

=== "Python"

    ```python
    result = client.set_name_target("alice", "dili1w7ek3fhz9acq0mp...")
    ```

=== "Rust"

    ```rust
    let request = client.set_name_target_request("alice", "dili1w7ek3fhz9acq0mp...");
    ```

=== "Go"

    ```go
    result, err := client.SetNameTarget(ctx, "alice", "dili1w7ek3fhz9acq0mp...")
    ```

=== "Java"

    ```java
    var result = client.names().setNameTarget("alice", Address.of("dili1w7ek3fhz9acq0mp...")).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.SetNameTargetAsync("alice", "dili1w7ek3fhz9acq0mp...");
    ```

| Parameter | Type     | Description                                         |
| --------- | -------- | --------------------------------------------------- |
| `name`    | `string` | The name to update                                  |
| `target`  | `string` | The address that this name should resolve to        |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `setNameRecord`

Set an arbitrary key-value record on a `.dili` name (e.g., avatar URL, social handle, content hash).

=== "TypeScript"

    ```typescript
    const result = await client.setNameRecord("alice", "avatar", "https://cdn.dilithia.network/avatars/alice.png");
    ```

=== "Python"

    ```python
    result = client.set_name_record("alice", "avatar", "https://cdn.dilithia.network/avatars/alice.png")
    ```

=== "Rust"

    ```rust
    let request = client.set_name_record_request("alice", "avatar", "https://cdn.dilithia.network/avatars/alice.png");
    ```

=== "Go"

    ```go
    result, err := client.SetNameRecord(ctx, "alice", "avatar", "https://cdn.dilithia.network/avatars/alice.png")
    ```

=== "Java"

    ```java
    var result = client.names().setNameRecord("alice", "avatar", "https://cdn.dilithia.network/avatars/alice.png").send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.SetNameRecordAsync("alice", "avatar", "https://cdn.dilithia.network/avatars/alice.png");
    ```

| Parameter | Type     | Description                                    |
| --------- | -------- | ---------------------------------------------- |
| `name`    | `string` | The name to update                             |
| `key`     | `string` | Record key (e.g., `avatar`, `url`, `github`)   |
| `value`   | `string` | Record value                                   |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `releaseName`

Release a `.dili` name, removing it from your ownership and making it available for registration by others.

=== "TypeScript"

    ```typescript
    const result = await client.releaseName("alice");
    ```

=== "Python"

    ```python
    result = client.release_name("alice")
    ```

=== "Rust"

    ```rust
    let request = client.release_name_request("alice");
    ```

=== "Go"

    ```go
    result, err := client.ReleaseName(ctx, "alice")
    ```

=== "Java"

    ```java
    var result = client.names().releaseName("alice").send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.ReleaseNameAsync("alice");
    ```

| Parameter | Type     | Description                  |
| --------- | -------- | ---------------------------- |
| `name`    | `string` | The name to release          |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `isNameAvailable`

Check whether a `.dili` name is available for registration.

=== "TypeScript"

    ```typescript
    const available: boolean = await client.isNameAvailable("alice");
    ```

=== "Python"

    ```python
    available: bool = client.is_name_available("alice")
    ```

=== "Rust"

    ```rust
    let request = client.is_name_available_request("alice");
    ```

=== "Go"

    ```go
    available, err := client.IsNameAvailable(ctx, "alice")
    ```

=== "Java"

    ```java
    boolean available = client.names().isNameAvailable("alice").get();
    ```

=== "C#"

    ```csharp
    bool available = await client.IsNameAvailableAsync("alice");
    ```

| Parameter | Type     | Description                                        |
| --------- | -------- | -------------------------------------------------- |
| `name`    | `string` | The name to check (without `.dili` suffix)         |

**Returns:** `boolean` -- `true` if the name is not currently registered.

---

### `lookupName`

Look up the full name record for a `.dili` name, including owner, target, expiry, and all custom records.

=== "TypeScript"

    ```typescript
    const record: FullNameRecord = await client.lookupName("alice");
    ```

=== "Python"

    ```python
    record: FullNameRecord = client.lookup_name("alice")
    ```

=== "Rust"

    ```rust
    let request = client.lookup_name_request("alice");
    ```

=== "Go"

    ```go
    record, err := client.LookupName(ctx, "alice")
    ```

=== "Java"

    ```java
    FullNameRecord record = client.names().lookupName("alice").get();
    ```

=== "C#"

    ```csharp
    FullNameRecord record = await client.LookupNameAsync("alice");
    ```

| Parameter | Type     | Description                                |
| --------- | -------- | ------------------------------------------ |
| `name`    | `string` | The name to look up (without `.dili` suffix) |

**Returns:** A `FullNameRecord` containing `owner`, `target`, `expiryBlock`, and a map of custom records.

---

### `getNameRecords`

Fetch all custom key-value records associated with a `.dili` name.

=== "TypeScript"

    ```typescript
    const records: Record<string, string> = await client.getNameRecords("alice");
    // { avatar: "https://...", github: "alice-dev", url: "https://alice.dev" }
    ```

=== "Python"

    ```python
    records: dict[str, str] = client.get_name_records("alice")
    # {"avatar": "https://...", "github": "alice-dev", "url": "https://alice.dev"}
    ```

=== "Rust"

    ```rust
    let request = client.get_name_records_request("alice");
    ```

=== "Go"

    ```go
    records, err := client.GetNameRecords(ctx, "alice")
    // records is map[string]string
    ```

=== "Java"

    ```java
    Map<String, String> records = client.names().getNameRecords("alice").get();
    ```

=== "C#"

    ```csharp
    Dictionary<string, string> records = await client.GetNameRecordsAsync("alice");
    ```

| Parameter | Type     | Description                                |
| --------- | -------- | ------------------------------------------ |
| `name`    | `string` | The name to query (without `.dili` suffix) |

**Returns:** A dictionary/map of all custom records set on the name.

---

### `getNamesByOwner`

Get all `.dili` names owned by an address.

=== "TypeScript"

    ```typescript
    const names: string[] = await client.getNamesByOwner("dili1qpz9ckg7r4n6eftm...");
    // ["alice", "myproject"]
    ```

=== "Python"

    ```python
    names: list[str] = client.get_names_by_owner("dili1qpz9ckg7r4n6eftm...")
    # ["alice", "myproject"]
    ```

=== "Rust"

    ```rust
    let request = client.get_names_by_owner_request("dili1qpz9ckg7r4n6eftm...");
    ```

=== "Go"

    ```go
    names, err := client.GetNamesByOwner(ctx, "dili1qpz9ckg7r4n6eftm...")
    // names is []string
    ```

=== "Java"

    ```java
    List<String> names = client.names().getNamesByOwner(Address.of("dili1qpz9ckg7r4n6eftm...")).get();
    ```

=== "C#"

    ```csharp
    string[] names = await client.GetNamesByOwnerAsync("dili1qpz9ckg7r4n6eftm...");
    ```

| Parameter | Type     | Description                            |
| --------- | -------- | -------------------------------------- |
| `address` | `string` | The owner address to query             |

**Returns:** A list of name strings owned by the address.

---

### `getRegistrationCost`

Query the registration cost for a name before registering. Cost varies by name length -- shorter names are more expensive.

=== "TypeScript"

    ```typescript
    // Check cost, availability, then register
    const cost = await client.getRegistrationCost("alice");
    console.log(`Registration costs ${cost.amount} DILI`);

    const available = await client.isNameAvailable("alice");
    if (available) {
      const result = await client.registerName("alice");
      console.log(`Registered! TX: ${result.txHash}`);
    }
    ```

=== "Python"

    ```python
    # Check cost, availability, then register
    cost = client.get_registration_cost("alice")
    print(f"Registration costs {cost.amount} DILI")

    available = client.is_name_available("alice")
    if available:
        result = client.register_name("alice")
        print(f"Registered! TX: {result.tx_hash}")
    ```

=== "Rust"

    ```rust
    // Query cost first, then check availability and register
    let cost_req = client.get_registration_cost_request("alice");
    let avail_req = client.is_name_available_request("alice");
    let register_req = client.register_name_request("alice");
    ```

=== "Go"

    ```go
    // Check cost, availability, then register
    cost, err := client.GetRegistrationCost(ctx, "alice")
    fmt.Printf("Registration costs %s DILI\n", cost.Amount)

    available, err := client.IsNameAvailable(ctx, "alice")
    if available {
        result, err := client.RegisterName(ctx, "alice")
        fmt.Printf("Registered! TX: %s\n", result.TxHash)
    }
    ```

=== "Java"

    ```java
    // Check cost, availability, then register
    RegistrationCost cost = client.names().getRegistrationCost("alice").get();
    System.out.printf("Registration costs %s DILI%n", cost.amount());

    boolean available = client.names().isNameAvailable("alice").get();
    if (available) {
        var result = client.names().registerName("alice").send(signer);
        System.out.printf("Registered! TX: %s%n", result.txHash());
    }
    ```

=== "C#"

    ```csharp
    // Check cost, availability, then register
    RegistrationCost cost = await client.GetRegistrationCostAsync("alice");
    Console.WriteLine($"Registration costs {cost.Amount} DILI");

    bool available = await client.IsNameAvailableAsync("alice");
    if (available)
    {
        var result = await client.RegisterNameAsync("alice");
        Console.WriteLine($"Registered! TX: {result.TxHash}");
    }
    ```

| Parameter | Type     | Description                                    |
| --------- | -------- | ---------------------------------------------- |
| `name`    | `string` | The name to query cost for (without `.dili` suffix) |

**Returns:** A `RegistrationCost` object containing `amount` (in DILI tokens) and `durationDays`.

---

## Credentials

Methods for managing verifiable credentials on-chain. Credentials use a schema-based model: issuers define schemas, issue credentials as commitments, and holders can produce selective disclosure proofs that verifiers check on-chain.

### `registerSchema`

Register a credential schema with typed attributes. Schemas define the structure of credentials that can be issued against them.

=== "TypeScript"

    ```typescript
    const result = await client.registerSchema({
      name: "KYCLevel2",
      attributes: [
        { name: "full_name", type: "string" },
        { name: "date_of_birth", type: "date" },
        { name: "country_code", type: "string" },
        { name: "verification_level", type: "uint8" },
      ],
    });
    // result.schemaHash -- the on-chain identifier for this schema
    ```

=== "Python"

    ```python
    result = client.register_schema(
        name="KYCLevel2",
        attributes=[
            {"name": "full_name", "type": "string"},
            {"name": "date_of_birth", "type": "date"},
            {"name": "country_code", "type": "string"},
            {"name": "verification_level", "type": "uint8"},
        ],
    )
    # result.schema_hash -- the on-chain identifier for this schema
    ```

=== "Rust"

    ```rust
    let request = client.register_schema_request(SchemaDefinition {
        name: "KYCLevel2".to_string(),
        attributes: vec![
            SchemaAttribute::new("full_name", AttributeType::String),
            SchemaAttribute::new("date_of_birth", AttributeType::Date),
            SchemaAttribute::new("country_code", AttributeType::String),
            SchemaAttribute::new("verification_level", AttributeType::Uint8),
        ],
    });
    ```

=== "Go"

    ```go
    result, err := client.RegisterSchema(ctx, &sdk.SchemaDefinition{
        Name: "KYCLevel2",
        Attributes: []sdk.SchemaAttribute{
            {Name: "full_name", Type: sdk.AttrString},
            {Name: "date_of_birth", Type: sdk.AttrDate},
            {Name: "country_code", Type: sdk.AttrString},
            {Name: "verification_level", Type: sdk.AttrUint8},
        },
    })
    // result.SchemaHash -- the on-chain identifier for this schema
    ```

=== "Java"

    ```java
    var result = client.credentials().registerSchema(SchemaDefinition.builder()
        .name("KYCLevel2")
        .attribute("full_name", AttributeType.STRING)
        .attribute("date_of_birth", AttributeType.DATE)
        .attribute("country_code", AttributeType.STRING)
        .attribute("verification_level", AttributeType.UINT8)
        .build()
    ).send(signer);
    // result.schemaHash() -- the on-chain identifier for this schema
    ```

=== "C#"

    ```csharp
    var result = await client.RegisterSchemaAsync(new SchemaDefinition
    {
        Name = "KYCLevel2",
        Attributes = new[]
        {
            new SchemaAttribute("full_name", AttributeType.String),
            new SchemaAttribute("date_of_birth", AttributeType.Date),
            new SchemaAttribute("country_code", AttributeType.String),
            new SchemaAttribute("verification_level", AttributeType.Uint8),
        },
    });
    // result.SchemaHash -- the on-chain identifier for this schema
    ```

| Parameter    | Type               | Description                                      |
| ------------ | ------------------ | ------------------------------------------------ |
| `name`       | `string`           | Human-readable schema name                       |
| `attributes` | `SchemaAttribute[]` | List of attribute definitions with name and type |

**Returns:** A `SchemaRegistration` containing the `schemaHash` identifier and transaction hash.

---

### `issueCredential`

Issue a credential to a holder address. The credential is stored as a commitment hash on-chain, preserving holder privacy.

=== "TypeScript"

    ```typescript
    const result = await client.issueCredential({
      schemaHash: "0x8f3a1b2c4d5e6f...",
      holder: "dili1v8njkg4e5xr2mt...",
      commitment: "0xc4a9e7d1f2b385...",
    });
    ```

=== "Python"

    ```python
    result = client.issue_credential(
        schema_hash="0x8f3a1b2c4d5e6f...",
        holder="dili1v8njkg4e5xr2mt...",
        commitment="0xc4a9e7d1f2b385...",
    )
    ```

=== "Rust"

    ```rust
    let request = client.issue_credential_request(
        "0x8f3a1b2c4d5e6f...",
        "dili1v8njkg4e5xr2mt...",
        "0xc4a9e7d1f2b385...",
    );
    ```

=== "Go"

    ```go
    result, err := client.IssueCredential(ctx, &sdk.IssueCredentialParams{
        SchemaHash: "0x8f3a1b2c4d5e6f...",
        Holder:     "dili1v8njkg4e5xr2mt...",
        Commitment: "0xc4a9e7d1f2b385...",
    })
    ```

=== "Java"

    ```java
    var result = client.credentials().issueCredential(
        SchemaHash.of("0x8f3a1b2c4d5e6f..."),
        Address.of("dili1v8njkg4e5xr2mt..."),
        Commitment.of("0xc4a9e7d1f2b385...")
    ).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.IssueCredentialAsync(
        schemaHash: "0x8f3a1b2c4d5e6f...",
        holder: "dili1v8njkg4e5xr2mt...",
        commitment: "0xc4a9e7d1f2b385..."
    );
    ```

| Parameter    | Type     | Description                                                  |
| ------------ | -------- | ------------------------------------------------------------ |
| `schemaHash` | `string` | Hash of the schema this credential conforms to               |
| `holder`     | `string` | Address of the credential holder                             |
| `commitment` | `string` | Commitment hash of the credential attributes                 |

**Returns:** A `CredentialIssuance` containing the credential identifier and transaction hash.

---

### `revokeCredential`

Revoke a credential by commitment hash. Only callable by the original issuer.

=== "TypeScript"

    ```typescript
    const result = await client.revokeCredential("0xc4a9e7d1f2b385...");
    ```

=== "Python"

    ```python
    result = client.revoke_credential("0xc4a9e7d1f2b385...")
    ```

=== "Rust"

    ```rust
    let request = client.revoke_credential_request("0xc4a9e7d1f2b385...");
    ```

=== "Go"

    ```go
    result, err := client.RevokeCredential(ctx, "0xc4a9e7d1f2b385...")
    ```

=== "Java"

    ```java
    var result = client.credentials().revokeCredential(
        Commitment.of("0xc4a9e7d1f2b385...")
    ).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.RevokeCredentialAsync("0xc4a9e7d1f2b385...");
    ```

| Parameter    | Type     | Description                                    |
| ------------ | -------- | ---------------------------------------------- |
| `commitment` | `string` | Commitment hash of the credential to revoke    |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `verifyCredential`

Verify a selective disclosure proof against the credential contract. The proof reveals only the attributes the holder chose to disclose.

=== "TypeScript"

    ```typescript
    const valid: boolean = await client.verifyCredential({
      commitment: "0xc4a9e7d1f2b385...",
      proof: "0x7e2f91a4d6c8b3...",
      disclosedAttributes: ["country_code", "verification_level"],
    });
    ```

=== "Python"

    ```python
    valid: bool = client.verify_credential(
        commitment="0xc4a9e7d1f2b385...",
        proof="0x7e2f91a4d6c8b3...",
        disclosed_attributes=["country_code", "verification_level"],
    )
    ```

=== "Rust"

    ```rust
    let request = client.verify_credential_request(VerifyCredentialParams {
        commitment: "0xc4a9e7d1f2b385...".to_string(),
        proof: "0x7e2f91a4d6c8b3...".to_string(),
        disclosed_attributes: vec!["country_code".to_string(), "verification_level".to_string()],
    });
    ```

=== "Go"

    ```go
    valid, err := client.VerifyCredential(ctx, &sdk.VerifyCredentialParams{
        Commitment:           "0xc4a9e7d1f2b385...",
        Proof:                "0x7e2f91a4d6c8b3...",
        DisclosedAttributes:  []string{"country_code", "verification_level"},
    })
    ```

=== "Java"

    ```java
    boolean valid = client.credentials().verifyCredential(
        Commitment.of("0xc4a9e7d1f2b385..."),
        Proof.of("0x7e2f91a4d6c8b3..."),
        List.of("country_code", "verification_level")
    ).get();
    ```

=== "C#"

    ```csharp
    bool valid = await client.VerifyCredentialAsync(
        commitment: "0xc4a9e7d1f2b385...",
        proof: "0x7e2f91a4d6c8b3...",
        disclosedAttributes: new[] { "country_code", "verification_level" }
    );
    ```

| Parameter              | Type       | Description                                                |
| ---------------------- | ---------- | ---------------------------------------------------------- |
| `commitment`           | `string`   | Commitment hash of the credential                          |
| `proof`                | `string`   | Selective disclosure proof generated by the holder          |
| `disclosedAttributes`  | `string[]` | List of attribute names included in the proof              |

**Returns:** `boolean` -- `true` if the proof is valid against the on-chain commitment.

---

### `getCredential`

Fetch a credential by commitment hash, including its current status.

=== "TypeScript"

    ```typescript
    const credential: Credential = await client.getCredential("0xc4a9e7d1f2b385...");
    // credential.issuer, credential.holder, credential.schemaHash, credential.status
    ```

=== "Python"

    ```python
    credential: Credential = client.get_credential("0xc4a9e7d1f2b385...")
    # credential.issuer, credential.holder, credential.schema_hash, credential.status
    ```

=== "Rust"

    ```rust
    let request = client.get_credential_request("0xc4a9e7d1f2b385...");
    ```

=== "Go"

    ```go
    credential, err := client.GetCredential(ctx, "0xc4a9e7d1f2b385...")
    // credential.Issuer, credential.Holder, credential.SchemaHash, credential.Status
    ```

=== "Java"

    ```java
    Credential credential = client.credentials().getCredential(
        Commitment.of("0xc4a9e7d1f2b385...")
    ).get();
    // credential.issuer(), credential.holder(), credential.schemaHash(), credential.status()
    ```

=== "C#"

    ```csharp
    Credential credential = await client.GetCredentialAsync("0xc4a9e7d1f2b385...");
    // credential.Issuer, credential.Holder, credential.SchemaHash, credential.Status
    ```

| Parameter    | Type     | Description                                |
| ------------ | -------- | ------------------------------------------ |
| `commitment` | `string` | Commitment hash of the credential          |

**Returns:** A `Credential` object containing `issuer`, `holder`, `schemaHash`, `status` (`active` or `revoked`), and `issuedAtBlock`.

---

### `getSchema`

Fetch a credential schema by hash.

=== "TypeScript"

    ```typescript
    const schema: CredentialSchema = await client.getSchema("0x8f3a1b2c4d5e6f...");
    // schema.name, schema.attributes, schema.issuer
    ```

=== "Python"

    ```python
    schema: CredentialSchema = client.get_schema("0x8f3a1b2c4d5e6f...")
    # schema.name, schema.attributes, schema.issuer
    ```

=== "Rust"

    ```rust
    let request = client.get_schema_request("0x8f3a1b2c4d5e6f...");
    ```

=== "Go"

    ```go
    schema, err := client.GetSchema(ctx, "0x8f3a1b2c4d5e6f...")
    // schema.Name, schema.Attributes, schema.Issuer
    ```

=== "Java"

    ```java
    CredentialSchema schema = client.credentials().getSchema(
        SchemaHash.of("0x8f3a1b2c4d5e6f...")
    ).get();
    // schema.name(), schema.attributes(), schema.issuer()
    ```

=== "C#"

    ```csharp
    CredentialSchema schema = await client.GetSchemaAsync("0x8f3a1b2c4d5e6f...");
    // schema.Name, schema.Attributes, schema.Issuer
    ```

| Parameter    | Type     | Description                      |
| ------------ | -------- | -------------------------------- |
| `schemaHash` | `string` | Hash of the schema to fetch      |

**Returns:** A `CredentialSchema` containing `name`, `attributes` (list of name/type pairs), `issuer`, and `registeredAtBlock`.

---

### `listCredentialsByHolder`

List all credentials issued to a holder address.

=== "TypeScript"

    ```typescript
    const credentials: Credential[] = await client.listCredentialsByHolder("dili1v8njkg4e5xr2mt...");
    ```

=== "Python"

    ```python
    credentials: list[Credential] = client.list_credentials_by_holder("dili1v8njkg4e5xr2mt...")
    ```

=== "Rust"

    ```rust
    let request = client.list_credentials_by_holder_request("dili1v8njkg4e5xr2mt...");
    ```

=== "Go"

    ```go
    credentials, err := client.ListCredentialsByHolder(ctx, "dili1v8njkg4e5xr2mt...")
    ```

=== "Java"

    ```java
    List<Credential> credentials = client.credentials().listByHolder(
        Address.of("dili1v8njkg4e5xr2mt...")
    ).get();
    ```

=== "C#"

    ```csharp
    Credential[] credentials = await client.ListCredentialsByHolderAsync("dili1v8njkg4e5xr2mt...");
    ```

| Parameter | Type     | Description                          |
| --------- | -------- | ------------------------------------ |
| `holder`  | `string` | Address of the credential holder     |

**Returns:** A list of `Credential` objects held by the address.

---

### `listCredentialsByIssuer`

List all credentials issued by an address.

=== "TypeScript"

    ```typescript
    const credentials: Credential[] = await client.listCredentialsByIssuer("dili1qpz9ckg7r4n6eftm...");
    ```

=== "Python"

    ```python
    credentials: list[Credential] = client.list_credentials_by_issuer("dili1qpz9ckg7r4n6eftm...")
    ```

=== "Rust"

    ```rust
    let request = client.list_credentials_by_issuer_request("dili1qpz9ckg7r4n6eftm...");
    ```

=== "Go"

    ```go
    credentials, err := client.ListCredentialsByIssuer(ctx, "dili1qpz9ckg7r4n6eftm...")
    ```

=== "Java"

    ```java
    List<Credential> credentials = client.credentials().listByIssuer(
        Address.of("dili1qpz9ckg7r4n6eftm...")
    ).get();
    ```

=== "C#"

    ```csharp
    Credential[] credentials = await client.ListCredentialsByIssuerAsync("dili1qpz9ckg7r4n6eftm...");
    ```

| Parameter | Type     | Description                          |
| --------- | -------- | ------------------------------------ |
| `issuer`  | `string` | Address of the credential issuer     |

**Returns:** A list of `Credential` objects issued by the address.

---

## Shielded Pool

Methods for interacting with the shielded pool, which provides privacy-preserving token transfers using zero-knowledge proofs. Deposits move tokens from a public balance into the shielded pool; withdrawals extract them back. The pool maintains a Merkle tree of commitments and a set of spent nullifiers.

### `shieldedDeposit`

Deposit tokens into the shielded pool.

=== "TypeScript"

    ```typescript
    const result: SubmitResult = await client.shieldedDeposit(
      "0xa1b2c3d4e5f6...",  // commitment
      1000n,                // value
      "0x7e2f91a4d6c8..."   // proofHex
    );
    // result.txHash -- TxHash
    ```

=== "Python"

    ```python
    result: SubmitResult = client.shielded_deposit(
        commitment="0xa1b2c3d4e5f6...",
        value=1000,
        proof_hex="0x7e2f91a4d6c8...",
    )
    # result.tx_hash -- TxHash
    ```

=== "Rust"

    ```rust
    let request = client.shielded_deposit_request(
        "0xa1b2c3d4e5f6...",
        1000,
        "0x7e2f91a4d6c8...",
    );
    ```

=== "Go"

    ```go
    result, err := client.ShieldedDeposit(ctx,
        "0xa1b2c3d4e5f6...",  // commitment
        1000,                 // value
        "0x7e2f91a4d6c8...",  // proofHex
    )
    // result.TxHash -- TxHash
    ```

=== "Java"

    ```java
    SubmitResult result = client.shielded().deposit(
        Commitment.of("0xa1b2c3d4e5f6..."),
        BigInteger.valueOf(1000),
        Proof.of("0x7e2f91a4d6c8...")
    ).send(signer);
    // result.txHash() -- TxHash
    ```

=== "C#"

    ```csharp
    SubmitResult result = await client.ShieldedDepositAsync(
        commitment: "0xa1b2c3d4e5f6...",
        value: 1000,
        proofHex: "0x7e2f91a4d6c8..."
    );
    // result.TxHash -- TxHash
    ```

| Parameter    | Type     | Description                                          |
| ------------ | -------- | ---------------------------------------------------- |
| `commitment` | `string` | Pedersen commitment for the deposited value           |
| `value`      | `uint64` | Amount of tokens to deposit into the shielded pool    |
| `proofHex`   | `string` | Hex-encoded zero-knowledge proof of valid commitment  |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `shieldedWithdraw`

Withdraw tokens from the shielded pool back to a public address.

=== "TypeScript"

    ```typescript
    const result: SubmitResult = await client.shieldedWithdraw(
      "0xd4e5f6a1b2c3...",  // nullifier
      "dili1abc...",         // recipient
      1000n,                // value
      "0x91a4d6c8b37e..."   // proofHex
    );
    ```

=== "Python"

    ```python
    result: SubmitResult = client.shielded_withdraw(
        nullifier="0xd4e5f6a1b2c3...",
        recipient="dili1abc...",
        value=1000,
        proof_hex="0x91a4d6c8b37e...",
    )
    ```

=== "Rust"

    ```rust
    let request = client.shielded_withdraw_request(
        "0xd4e5f6a1b2c3...",
        "dili1abc...",
        1000,
        "0x91a4d6c8b37e...",
    );
    ```

=== "Go"

    ```go
    result, err := client.ShieldedWithdraw(ctx,
        "0xd4e5f6a1b2c3...",  // nullifier
        "dili1abc...",         // recipient
        1000,                 // value
        "0x91a4d6c8b37e...",  // proofHex
    )
    ```

=== "Java"

    ```java
    SubmitResult result = client.shielded().withdraw(
        Nullifier.of("0xd4e5f6a1b2c3..."),
        Address.of("dili1abc..."),
        BigInteger.valueOf(1000),
        Proof.of("0x91a4d6c8b37e...")
    ).send(signer);
    ```

=== "C#"

    ```csharp
    SubmitResult result = await client.ShieldedWithdrawAsync(
        nullifier: "0xd4e5f6a1b2c3...",
        recipient: "dili1abc...",
        value: 1000,
        proofHex: "0x91a4d6c8b37e..."
    );
    ```

| Parameter   | Type     | Description                                            |
| ----------- | -------- | ------------------------------------------------------ |
| `nullifier` | `string` | Nullifier hash to prevent double-spending              |
| `recipient` | `string` | Public address to receive the withdrawn tokens         |
| `value`     | `uint64` | Amount of tokens to withdraw                           |
| `proofHex`  | `string` | Hex-encoded zero-knowledge proof of valid withdrawal   |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `getCommitmentRoot`

Get the current Merkle root of the shielded pool commitment tree.

=== "TypeScript"

    ```typescript
    const root: string = await client.getCommitmentRoot();
    // "0x3f8a9b1c2d4e..."
    ```

=== "Python"

    ```python
    root: str = client.get_commitment_root()
    # "0x3f8a9b1c2d4e..."
    ```

=== "Rust"

    ```rust
    let request = client.get_commitment_root_request();
    ```

=== "Go"

    ```go
    root, err := client.GetCommitmentRoot(ctx)
    // root is string
    ```

=== "Java"

    ```java
    String root = client.shielded().getCommitmentRoot().get();
    ```

=== "C#"

    ```csharp
    string root = await client.GetCommitmentRootAsync();
    ```

**Returns:** `string` -- the current Merkle root hash of the shielded pool commitment tree.

---

### `isNullifierSpent`

Check whether a nullifier has already been spent in the shielded pool.

=== "TypeScript"

    ```typescript
    const spent: boolean = await client.isNullifierSpent("0xd4e5f6a1b2c3...");
    ```

=== "Python"

    ```python
    spent: bool = client.is_nullifier_spent("0xd4e5f6a1b2c3...")
    ```

=== "Rust"

    ```rust
    let request = client.is_nullifier_spent_request("0xd4e5f6a1b2c3...");
    ```

=== "Go"

    ```go
    spent, err := client.IsNullifierSpent(ctx, "0xd4e5f6a1b2c3...")
    ```

=== "Java"

    ```java
    boolean spent = client.shielded().isNullifierSpent(
        Nullifier.of("0xd4e5f6a1b2c3...")
    ).get();
    ```

=== "C#"

    ```csharp
    bool spent = await client.IsNullifierSpentAsync("0xd4e5f6a1b2c3...");
    ```

| Parameter   | Type     | Description                          |
| ----------- | -------- | ------------------------------------ |
| `nullifier` | `string` | Nullifier hash to check              |

**Returns:** `boolean` -- `true` if the nullifier has already been used in a withdrawal.

---

## Multisig

Methods for creating and managing multisignature wallets. Multisig wallets require a configurable threshold of signer approvals before a transaction can be executed. Common use cases include treasury management and DAO governance.

### `createMultisig`

Create a new multisig wallet with a set of signers and an approval threshold.

=== "TypeScript"

    ```typescript
    const result = await client.createMultisig({
      name: "treasury",
      signers: ["dili1alice...", "dili1bob...", "dili1carol..."],
      threshold: 2,
    });
    // result.walletAddress -- the on-chain multisig address
    ```

=== "Python"

    ```python
    result = client.create_multisig(
        name="treasury",
        signers=["dili1alice...", "dili1bob...", "dili1carol..."],
        threshold=2,
    )
    # result.wallet_address -- the on-chain multisig address
    ```

=== "Rust"

    ```rust
    let request = client.create_multisig_request(CreateMultisigParams {
        name: "treasury".to_string(),
        signers: vec![
            "dili1alice...".to_string(),
            "dili1bob...".to_string(),
            "dili1carol...".to_string(),
        ],
        threshold: 2,
    });
    ```

=== "Go"

    ```go
    result, err := client.CreateMultisig(ctx, &sdk.CreateMultisigParams{
        Name:      "treasury",
        Signers:   []string{"dili1alice...", "dili1bob...", "dili1carol..."},
        Threshold: 2,
    })
    // result.WalletAddress -- the on-chain multisig address
    ```

=== "Java"

    ```java
    var result = client.multisig().create(MultisigParams.builder()
        .name("treasury")
        .signer(Address.of("dili1alice..."))
        .signer(Address.of("dili1bob..."))
        .signer(Address.of("dili1carol..."))
        .threshold(2)
        .build()
    ).send(signer);
    // result.walletAddress() -- the on-chain multisig address
    ```

=== "C#"

    ```csharp
    var result = await client.CreateMultisigAsync(new CreateMultisigParams
    {
        Name = "treasury",
        Signers = new[] { "dili1alice...", "dili1bob...", "dili1carol..." },
        Threshold = 2,
    });
    // result.WalletAddress -- the on-chain multisig address
    ```

| Parameter   | Type       | Description                                              |
| ----------- | ---------- | -------------------------------------------------------- |
| `name`      | `string`   | Human-readable name for the multisig wallet              |
| `signers`   | `string[]` | List of signer addresses                                 |
| `threshold` | `uint32`   | Number of approvals required to execute a transaction    |

**Returns:** A `MultisigCreation` containing the `walletAddress` and transaction hash.

---

### `proposeTx`

Propose a new transaction from a multisig wallet. The proposer must be one of the signers.

=== "TypeScript"

    ```typescript
    const result = await client.proposeTx({
      wallet: "dili1msig_treasury...",
      to: "dili1vendor...",
      value: 50_000n,
      data: "0x",
    });
    // result.txId -- the multisig-internal transaction ID
    ```

=== "Python"

    ```python
    result = client.propose_tx(
        wallet="dili1msig_treasury...",
        to="dili1vendor...",
        value=50_000,
        data="0x",
    )
    # result.tx_id -- the multisig-internal transaction ID
    ```

=== "Rust"

    ```rust
    let request = client.propose_tx_request(ProposeTxParams {
        wallet: "dili1msig_treasury...".to_string(),
        to: "dili1vendor...".to_string(),
        value: 50_000,
        data: "0x".to_string(),
    });
    ```

=== "Go"

    ```go
    result, err := client.ProposeTx(ctx, &sdk.ProposeTxParams{
        Wallet: "dili1msig_treasury...",
        To:     "dili1vendor...",
        Value:  50_000,
        Data:   "0x",
    })
    // result.TxID -- the multisig-internal transaction ID
    ```

=== "Java"

    ```java
    var result = client.multisig().proposeTx(
        Address.of("dili1msig_treasury..."),
        Address.of("dili1vendor..."),
        BigInteger.valueOf(50_000),
        "0x"
    ).send(signer);
    // result.txId() -- the multisig-internal transaction ID
    ```

=== "C#"

    ```csharp
    var result = await client.ProposeTxAsync(new ProposeTxParams
    {
        Wallet = "dili1msig_treasury...",
        To = "dili1vendor...",
        Value = 50_000,
        Data = "0x",
    });
    // result.TxId -- the multisig-internal transaction ID
    ```

| Parameter | Type     | Description                                    |
| --------- | -------- | ---------------------------------------------- |
| `wallet`  | `string` | Multisig wallet address                        |
| `to`      | `string` | Destination address for the proposed transfer  |
| `value`   | `uint64` | Amount of tokens to transfer                   |
| `data`    | `string` | Hex-encoded call data (use `"0x"` for simple transfers) |

**Returns:** A `MultisigProposal` containing the `txId` and transaction hash.

---

### `approveMultisigTx`

Approve a pending multisig transaction. The caller must be one of the wallet signers.

=== "TypeScript"

    ```typescript
    const result = await client.approveMultisigTx("dili1msig_treasury...", 1);
    ```

=== "Python"

    ```python
    result = client.approve_multisig_tx(
        wallet="dili1msig_treasury...",
        tx_id=1,
    )
    ```

=== "Rust"

    ```rust
    let request = client.approve_multisig_tx_request("dili1msig_treasury...", 1);
    ```

=== "Go"

    ```go
    result, err := client.ApproveMultisigTx(ctx, "dili1msig_treasury...", 1)
    ```

=== "Java"

    ```java
    var result = client.multisig().approve(
        Address.of("dili1msig_treasury..."), 1
    ).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.ApproveMultisigTxAsync("dili1msig_treasury...", txId: 1);
    ```

| Parameter | Type     | Description                        |
| --------- | -------- | ---------------------------------- |
| `wallet`  | `string` | Multisig wallet address            |
| `txId`    | `uint64` | Multisig-internal transaction ID   |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `executeMultisigTx`

Execute a multisig transaction that has reached the required approval threshold.

=== "TypeScript"

    ```typescript
    const result = await client.executeMultisigTx("dili1msig_treasury...", 1);
    ```

=== "Python"

    ```python
    result = client.execute_multisig_tx(
        wallet="dili1msig_treasury...",
        tx_id=1,
    )
    ```

=== "Rust"

    ```rust
    let request = client.execute_multisig_tx_request("dili1msig_treasury...", 1);
    ```

=== "Go"

    ```go
    result, err := client.ExecuteMultisigTx(ctx, "dili1msig_treasury...", 1)
    ```

=== "Java"

    ```java
    var result = client.multisig().execute(
        Address.of("dili1msig_treasury..."), 1
    ).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.ExecuteMultisigTxAsync("dili1msig_treasury...", txId: 1);
    ```

| Parameter | Type     | Description                        |
| --------- | -------- | ---------------------------------- |
| `wallet`  | `string` | Multisig wallet address            |
| `txId`    | `uint64` | Multisig-internal transaction ID   |

**Returns:** A `SubmitResult` with the transaction hash of the executed underlying transaction.

---

### `revokeMultisigApproval`

Revoke a previously given approval on a pending multisig transaction.

=== "TypeScript"

    ```typescript
    const result = await client.revokeMultisigApproval("dili1msig_treasury...", 1);
    ```

=== "Python"

    ```python
    result = client.revoke_multisig_approval(
        wallet="dili1msig_treasury...",
        tx_id=1,
    )
    ```

=== "Rust"

    ```rust
    let request = client.revoke_multisig_approval_request("dili1msig_treasury...", 1);
    ```

=== "Go"

    ```go
    result, err := client.RevokeMultisigApproval(ctx, "dili1msig_treasury...", 1)
    ```

=== "Java"

    ```java
    var result = client.multisig().revokeApproval(
        Address.of("dili1msig_treasury..."), 1
    ).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.RevokeMultisigApprovalAsync("dili1msig_treasury...", txId: 1);
    ```

| Parameter | Type     | Description                        |
| --------- | -------- | ---------------------------------- |
| `wallet`  | `string` | Multisig wallet address            |
| `txId`    | `uint64` | Multisig-internal transaction ID   |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `addMultisigSigner`

Add a new signer to a multisig wallet. This operation itself requires threshold approval.

=== "TypeScript"

    ```typescript
    const result = await client.addMultisigSigner("dili1msig_treasury...", "dili1dave...");
    ```

=== "Python"

    ```python
    result = client.add_multisig_signer(
        wallet="dili1msig_treasury...",
        signer="dili1dave...",
    )
    ```

=== "Rust"

    ```rust
    let request = client.add_multisig_signer_request("dili1msig_treasury...", "dili1dave...");
    ```

=== "Go"

    ```go
    result, err := client.AddMultisigSigner(ctx, "dili1msig_treasury...", "dili1dave...")
    ```

=== "Java"

    ```java
    var result = client.multisig().addSigner(
        Address.of("dili1msig_treasury..."),
        Address.of("dili1dave...")
    ).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.AddMultisigSignerAsync("dili1msig_treasury...", "dili1dave...");
    ```

| Parameter | Type     | Description                          |
| --------- | -------- | ------------------------------------ |
| `wallet`  | `string` | Multisig wallet address              |
| `signer`  | `string` | Address of the new signer to add     |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `removeMultisigSigner`

Remove a signer from a multisig wallet. This operation itself requires threshold approval. The threshold must remain achievable with the remaining signers.

=== "TypeScript"

    ```typescript
    const result = await client.removeMultisigSigner("dili1msig_treasury...", "dili1carol...");
    ```

=== "Python"

    ```python
    result = client.remove_multisig_signer(
        wallet="dili1msig_treasury...",
        signer="dili1carol...",
    )
    ```

=== "Rust"

    ```rust
    let request = client.remove_multisig_signer_request("dili1msig_treasury...", "dili1carol...");
    ```

=== "Go"

    ```go
    result, err := client.RemoveMultisigSigner(ctx, "dili1msig_treasury...", "dili1carol...")
    ```

=== "Java"

    ```java
    var result = client.multisig().removeSigner(
        Address.of("dili1msig_treasury..."),
        Address.of("dili1carol...")
    ).send(signer);
    ```

=== "C#"

    ```csharp
    var result = await client.RemoveMultisigSignerAsync("dili1msig_treasury...", "dili1carol...");
    ```

| Parameter | Type     | Description                             |
| --------- | -------- | --------------------------------------- |
| `wallet`  | `string` | Multisig wallet address                 |
| `signer`  | `string` | Address of the signer to remove         |

**Returns:** A `SubmitResult` with the transaction hash.

---

### `getMultisigWallet`

Fetch the configuration and state of a multisig wallet.

=== "TypeScript"

    ```typescript
    const wallet: MultisigWallet = await client.getMultisigWallet("dili1msig_treasury...");
    // wallet.name, wallet.signers, wallet.threshold, wallet.balance
    ```

=== "Python"

    ```python
    wallet: MultisigWallet = client.get_multisig_wallet("dili1msig_treasury...")
    # wallet.name, wallet.signers, wallet.threshold, wallet.balance
    ```

=== "Rust"

    ```rust
    let request = client.get_multisig_wallet_request("dili1msig_treasury...");
    ```

=== "Go"

    ```go
    wallet, err := client.GetMultisigWallet(ctx, "dili1msig_treasury...")
    // wallet.Name, wallet.Signers, wallet.Threshold, wallet.Balance
    ```

=== "Java"

    ```java
    MultisigWallet wallet = client.multisig().getWallet(
        Address.of("dili1msig_treasury...")
    ).get();
    // wallet.name(), wallet.signers(), wallet.threshold(), wallet.balance()
    ```

=== "C#"

    ```csharp
    MultisigWallet wallet = await client.GetMultisigWalletAsync("dili1msig_treasury...");
    // wallet.Name, wallet.Signers, wallet.Threshold, wallet.Balance
    ```

| Parameter | Type     | Description               |
| --------- | -------- | ------------------------- |
| `wallet`  | `string` | Multisig wallet address   |

**Returns:** A `MultisigWallet` containing `name`, `signers` (list of addresses), `threshold`, `balance`, and `pendingTxCount`.

---

### `getMultisigTx`

Fetch the details of a specific multisig transaction by ID.

=== "TypeScript"

    ```typescript
    const tx: MultisigTx = await client.getMultisigTx("dili1msig_treasury...", 1);
    // tx.to, tx.value, tx.approvals, tx.executed
    ```

=== "Python"

    ```python
    tx: MultisigTx = client.get_multisig_tx(
        wallet="dili1msig_treasury...",
        tx_id=1,
    )
    # tx.to, tx.value, tx.approvals, tx.executed
    ```

=== "Rust"

    ```rust
    let request = client.get_multisig_tx_request("dili1msig_treasury...", 1);
    ```

=== "Go"

    ```go
    tx, err := client.GetMultisigTx(ctx, "dili1msig_treasury...", 1)
    // tx.To, tx.Value, tx.Approvals, tx.Executed
    ```

=== "Java"

    ```java
    MultisigTx tx = client.multisig().getTx(
        Address.of("dili1msig_treasury..."), 1
    ).get();
    // tx.to(), tx.value(), tx.approvals(), tx.executed()
    ```

=== "C#"

    ```csharp
    MultisigTx tx = await client.GetMultisigTxAsync("dili1msig_treasury...", txId: 1);
    // tx.To, tx.Value, tx.Approvals, tx.Executed
    ```

| Parameter | Type     | Description                        |
| --------- | -------- | ---------------------------------- |
| `wallet`  | `string` | Multisig wallet address            |
| `txId`    | `uint64` | Multisig-internal transaction ID   |

**Returns:** A `MultisigTx` containing `to`, `value`, `data`, `approvals` (list of signer addresses), `executed` (boolean), and `proposedAtBlock`.

---

### `listMultisigPendingTxs`

List all pending (not yet executed) transactions for a multisig wallet.

=== "TypeScript"

    ```typescript
    const pending: MultisigTx[] = await client.listMultisigPendingTxs("dili1msig_treasury...");
    // pending.length, pending[0].txId, pending[0].approvals.length
    ```

=== "Python"

    ```python
    pending: list[MultisigTx] = client.list_multisig_pending_txs("dili1msig_treasury...")
    # len(pending), pending[0].tx_id, len(pending[0].approvals)
    ```

=== "Rust"

    ```rust
    let request = client.list_multisig_pending_txs_request("dili1msig_treasury...");
    ```

=== "Go"

    ```go
    pending, err := client.ListMultisigPendingTxs(ctx, "dili1msig_treasury...")
    // len(pending), pending[0].TxID, len(pending[0].Approvals)
    ```

=== "Java"

    ```java
    List<MultisigTx> pending = client.multisig().listPendingTxs(
        Address.of("dili1msig_treasury...")
    ).get();
    ```

=== "C#"

    ```csharp
    MultisigTx[] pending = await client.ListMultisigPendingTxsAsync("dili1msig_treasury...");
    ```

| Parameter | Type     | Description               |
| --------- | -------- | ------------------------- |
| `wallet`  | `string` | Multisig wallet address   |

**Returns:** A list of `MultisigTx` objects that have not yet been executed.

---

## Gas Sponsor Connector

The `DilithiaGasSponsorConnector` simplifies working with gas sponsor contracts for meta-transactions.

### Creating a Sponsor Connector

=== "TypeScript"

    ```typescript
    import { DilithiaGasSponsorConnector } from "@dilithia/sdk";

    const sponsor = new DilithiaGasSponsorConnector({
      client,
      sponsorContract: "wasm:gas_sponsor",
      paymaster: "gas_sponsor",
    });
    ```

=== "Python"

    ```python
    from dilithia_sdk import DilithiaGasSponsorConnector

    sponsor = DilithiaGasSponsorConnector(
        client, "wasm:gas_sponsor", paymaster="gas_sponsor"
    )
    ```

=== "Rust"

    ```rust
    let sponsor = DilithiaGasSponsorConnector::new("wasm:gas_sponsor", Some("gas_sponsor".to_string()));
    ```

### Sponsor Methods

| Method                          | Description                                         |
| ------------------------------- | --------------------------------------------------- |
| `buildAcceptQuery(user, contract, method)` | Check if the sponsor will accept a call   |
| `buildRemainingQuotaQuery(user)` | Query remaining gas quota for a user               |
| `buildMaxGasPerUserQuery()`      | Query the maximum gas per user                     |
| `buildFundCall(amount)`          | Build a call to fund the sponsor contract          |
| `applyPaymaster(call)`          | Attach the paymaster to a call                      |
| `sendSponsoredCall(call, signer)` | Sign and submit a sponsored call                  |

---

## Messaging Connector

The `DilithiaMessagingConnector` provides cross-chain messaging functionality.

### Creating a Messaging Connector

=== "TypeScript"

    ```typescript
    import { DilithiaMessagingConnector } from "@dilithia/sdk";

    const messaging = new DilithiaMessagingConnector({
      client,
      messagingContract: "wasm:messaging",
      paymaster: "gas_sponsor",
    });
    ```

=== "Python"

    ```python
    from dilithia_sdk import DilithiaMessagingConnector

    messaging = DilithiaMessagingConnector(
        client, "wasm:messaging", paymaster="gas_sponsor"
    )
    ```

=== "Rust"

    ```rust
    let messaging = DilithiaMessagingConnector::new("wasm:messaging", Some("gas_sponsor".to_string()));
    ```

### Messaging Methods

| Method                                              | Description                                  |
| --------------------------------------------------- | -------------------------------------------- |
| `buildSendMessageCall(destChain, payload)`          | Build an outbound cross-chain message call   |
| `buildReceiveMessageCall(sourceChain, sourceContract, payload)` | Build an inbound message call   |
| `sendMessage(destChain, payload, signer)`           | Sign and send a cross-chain message          |
| `queryOutbox()`                                     | Query the messaging outbox                   |
| `queryInbox()`                                      | Query the messaging inbox                    |
