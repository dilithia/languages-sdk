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
    await client.getBalance("dili1abc...");
    ```

=== "Python"

    ```python
    client.get_balance("dili1abc...")
    ```

=== "Rust"

    ```rust
    let request = client.get_balance_request("dili1abc...");
    ```

---

### `getNonce`

Fetch the current nonce (transaction count) of an address.

=== "TypeScript"

    ```typescript
    await client.getNonce("dili1abc...");
    ```

=== "Python"

    ```python
    client.get_nonce("dili1abc...")
    ```

=== "Rust"

    ```rust
    let request = client.get_nonce_request("dili1abc...");
    ```

---

### `getReceipt`

Fetch a transaction receipt by hash.

=== "TypeScript"

    ```typescript
    await client.getReceipt("0xabc123...");
    ```

=== "Python"

    ```python
    client.get_receipt("0xabc123...")
    ```

=== "Rust"

    ```rust
    let request = client.get_receipt_request("0xabc123...");
    ```

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

---

## Gas and Fee Estimation

### `getGasEstimate`

Get the current gas estimate via JSON-RPC (`qsc_gasEstimate`).

=== "TypeScript"

    ```typescript
    const estimate = await client.getGasEstimate();
    ```

=== "Python"

    ```python
    estimate = client.get_gas_estimate()
    ```

=== "Rust"

    ```rust
    let request = client.get_gas_estimate_request();
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

## Contract Interaction

### `queryContract`

Query a smart contract's read-only method (no transaction, no gas).

=== "TypeScript"

    ```typescript
    const result = await client.queryContract("wasm:amm", "get_reserves", {});
    ```

=== "Python"

    ```python
    result = client.query_contract("wasm:amm", "get_reserves", {})
    ```

=== "Rust"

    ```rust
    let request = client.query_contract_request("wasm:amm", "get_reserves", json!({}));
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
    const result = await client.sendCall(call);
    ```

=== "Python"

    ```python
    result = client.send_call(call)
    ```

=== "Rust"

    ```rust
    let request = client.send_call_request(call);
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
    const receipt = await client.waitForReceipt("0xabc...", 12, 1000);
    ```

=== "Python"

    ```python
    receipt = client.wait_for_receipt("0xabc...", max_attempts=12, delay_seconds=1.0)
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
    const record = await client.resolveName("alice.dili");
    ```

=== "Python"

    ```python
    record = client.resolve_name("alice.dili")
    ```

=== "Rust"

    ```rust
    let request = client.resolve_name_request("alice.dili");
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
