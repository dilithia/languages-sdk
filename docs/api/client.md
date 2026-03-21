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

### Additional Name Service Methods (TypeScript / Python)

| Method                  | Description                                  |
| ----------------------- | -------------------------------------------- |
| `lookupName(name)`      | Look up full name record                     |
| `isNameAvailable(name)` | Check if a name is available for registration|
| `getNamesByOwner(addr)` | Get all names owned by an address            |

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
