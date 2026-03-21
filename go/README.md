# Dilithia SDK for Go

Go client library for the Dilithia/QSC blockchain. Provides typed responses,
custom error types compatible with `errors.Is`/`errors.As`, and idiomatic
functional options for configuration.

## Installation

```bash
go get github.com/dilithia/languages-sdk/go@latest
```

Requires Go 1.22 or later.

## Quick start

```go
package main

import (
    "context"
    "fmt"
    "log"
    "time"

    sdk "github.com/dilithia/languages-sdk/go/sdk"
)

func main() {
    client := sdk.NewClient("http://localhost:9070/rpc",
        sdk.WithTimeout(5*time.Second),
    )

    ctx := context.Background()

    // Typed balance response with math/big.Int
    bal, err := client.GetBalance(ctx, sdk.Address("dili1alice"))
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Balance: %s (raw: %s)\n", bal.Value, bal.RawValue)

    // Typed nonce response
    nonce, err := client.GetNonce(ctx, sdk.Address("dili1alice"))
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Next nonce: %d\n", nonce.NextNonce)
}
```

## Configuration

Use functional options to configure the client:

```go
client := sdk.NewClient("http://localhost:9070/rpc",
    sdk.WithTimeout(5*time.Second),
    sdk.WithJWT("my-auth-token"),
    sdk.WithHeader("x-network", "devnet"),
    sdk.WithChainBaseURL("http://chain.local"),
    sdk.WithIndexerURL("http://indexer.local"),
    sdk.WithOracleURL("http://oracle.local"),
    sdk.WithWSURL("wss://ws.local"),
    sdk.WithHTTPClient(customHTTPClient),
)
```

## Error handling

All errors are wrapped in `*sdk.DilithiaError` so you can inspect the chain:

```go
bal, err := client.GetBalance(ctx, sdk.Address("dili1alice"))
if err != nil {
    var httpErr *sdk.HttpError
    if errors.As(err, &httpErr) {
        fmt.Printf("HTTP %d: %s\n", httpErr.StatusCode, httpErr.Body)
    }

    var rpcErr *sdk.RpcError
    if errors.As(err, &rpcErr) {
        fmt.Printf("RPC error %d: %s\n", rpcErr.Code, rpcErr.RpcMessage)
    }

    var timeoutErr *sdk.TimeoutError
    if errors.As(err, &timeoutErr) {
        fmt.Printf("Timed out: %s\n", timeoutErr.Operation)
    }
}
```

## Contract calls

```go
// Read-only query
result, err := client.QueryContract(ctx, "wasm:amm", "get_reserves", map[string]any{})

// Submit a call
submitResult, err := client.SendCall(ctx, client.BuildContractCall(
    "wasm:amm", "swap",
    map[string]any{"amount": 100},
    "", // no paymaster
))
```

## Deploy / upgrade

```go
bytecodeHex, err := sdk.ReadWasmFileHex("contract.wasm")
if err != nil {
    log.Fatal(err)
}

result, err := client.DeployContract(ctx, sdk.DeployPayload{
    Name:     "my_contract",
    Bytecode: bytecodeHex,
    From:     "dili1alice",
    Alg:      "mldsa65",
    PK:       publicKeyHex,
    Sig:      signatureHex,
    Nonce:    1,
    ChainID:  "dilithia-1",
    Version:  1,
})
```

## Gas sponsorship

```go
sponsor := sdk.NewGasSponsorConnector(client, "wasm:gas_sponsor", "gas_sponsor")

// Check eligibility
acceptQuery := sponsor.BuildAcceptQuery("dili1alice", "wasm:amm", "swap")

// Wrap a call with paymaster
call := client.BuildContractCall("wasm:amm", "swap", args, "")
sponsored := sponsor.ApplyPaymaster(call)
```

## Shielded pool

```go
// Deposit
result, err := client.ShieldedDeposit(ctx, commitmentHash, 1000, proofHex)

// Withdraw
result, err := client.ShieldedWithdraw(ctx, nullifier, 500, "dili1bob", proofHex, commitmentRoot)

// Query state
root, err := client.GetCommitmentRoot(ctx)
spent, err := client.IsNullifierSpent(ctx, nullifierHash)
```

## Name service

```go
record, err := client.ResolveName(ctx, "alice.dili")
fmt.Printf("%s -> %s\n", record.Name, record.Address)

reverse, err := client.ReverseResolveName(ctx, "dili1alice")
fmt.Printf("%s -> %s\n", reverse.Address, reverse.Name)
```

## Wait for receipt

```go
receipt, err := client.WaitForReceipt(ctx, sdk.TxHash("abc123"), 12, time.Second)
if err != nil {
    log.Fatal(err)
}
fmt.Printf("Status: %s, Gas: %d\n", receipt.Status, receipt.GasUsed)
```

## File structure

```
sdk/
  types.go         -- Typed structs (Balance, Receipt, Address, TokenAmount, etc.)
  errors.go        -- Error types (DilithiaError, RpcError, HttpError, TimeoutError)
  client.go        -- Client, functional options, core HTTP methods
  rpc.go           -- JSON-RPC helpers
  contracts.go     -- Contract call/query/ABI
  deploy.go        -- Deploy/upgrade
  shielded.go      -- Shielded pool operations
  names.go         -- Name service
  connectors.go    -- GasSponsorConnector, MessagingConnector
  crypto_types.go  -- CryptoAdapter, ZkAdapter interfaces
  crypto_cgo.go    -- Native bridge (CGo)
  crypto.go        -- Non-CGo stub
  client_test.go   -- Tests
```
