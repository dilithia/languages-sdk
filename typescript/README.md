# Dilithia TypeScript SDK

TypeScript and Node.js SDK for the Dilithia RPC surface.

Current SDK line: `0.3.0`

## Installation

```bash
npm install @dilithia/sdk-node
```

Requires Node.js 22 or later.

## Quick start

```typescript
import { createClient, Address, TokenAmount } from "@dilithia/sdk-node";

const client = createClient({ rpcUrl: "http://localhost:9070/rpc" });

// Typed balance with BigInt-backed TokenAmount
const balance = await client.getBalance("dili1abc...");
console.log(balance.balance.formatted()); // "42.5"
console.log(balance.raw);                 // 42500000000000000000n

// Typed receipt
const receipt = await client.getReceipt("0xdeadbeef...");
console.log(receipt.status);      // "success"
console.log(receipt.blockHeight); // 1234
```

## Branded types

The SDK uses TypeScript branded types to prevent accidental mixing of
semantically different strings at compile time:

```typescript
import { Address, TxHash, PublicKey, SecretKey } from "@dilithia/sdk-node";

const addr: Address = Address.of("dili1abc...");
const hash: TxHash  = TxHash.of("0xdeadbeef...");
const pk: PublicKey  = PublicKey.of("abcdef...");
const sk: SecretKey  = SecretKey.of("secret...");
```

All client methods accept both branded types and plain strings.

## TokenAmount

JavaScript `number` loses precision beyond 2^53, which is well below the
10^18 range used by Dilithia. `TokenAmount` uses `BigInt` internally:

```typescript
import { TokenAmount } from "@dilithia/sdk-node";

const amount = TokenAmount.dili("1.5");   // from human-readable
const raw    = TokenAmount.fromRaw(1_500_000_000_000_000_000n);

console.log(amount.toRaw());     // 1500000000000000000n
console.log(raw.formatted());    // "1.5"
```

## Error handling

All SDK errors extend `DilithiaError` for easy catching:

```typescript
import {
  DilithiaError,
  RpcError,
  HttpError,
  TimeoutError,
} from "@dilithia/sdk-node";

try {
  await client.getBalance("dili1...");
} catch (err) {
  if (err instanceof HttpError) {
    console.error(`HTTP ${err.statusCode}: ${err.body}`);
  } else if (err instanceof RpcError) {
    console.error(`RPC error ${err.code}: ${err.message}`);
  } else if (err instanceof TimeoutError) {
    console.error(`Timed out after ${err.timeoutMs}ms`);
  } else if (err instanceof DilithiaError) {
    console.error(`SDK error: ${err.message}`);
  }
}
```

## Contract calls and signing

```typescript
const call = client.buildContractCall("wasm:token", "transfer", {
  to: "dili1recipient...",
  amount: 100,
});

const result = await client.sendSignedCall(call, signer);
console.log(result.txHash); // branded TxHash

const receipt = await client.waitForReceipt(result.txHash);
```

## Gas sponsorship

```typescript
import { createGasSponsorConnector } from "@dilithia/sdk-node";

const sponsor = createGasSponsorConnector({
  client,
  sponsorContract: "wasm:gas_sponsor",
  paymaster: "gas_sponsor",
});

const result = await sponsor.sendSponsoredCall(call, signer);
```

## Deploy and upgrade

```typescript
const result = await client.deployContract({
  name: "my_contract",
  bytecode: wasmHex,
  from: "dili1deployer...",
  alg: "mldsa65",
  pk: publicKeyHex,
  sig: signatureHex,
  nonce: 0,
  chainId: "dilithia-devnet",
});
console.log(result.txHash);
```

## Scope

- RPC client construction with typed responses
- Branded types: Address, TxHash, PublicKey, SecretKey
- BigInt-backed TokenAmount for precision-safe token amounts
- Custom error hierarchy: DilithiaError, RpcError, HttpError, TimeoutError
- Balance, nonce, receipt, and address-summary reads
- Gas estimate, base fee, and network info
- Name service resolution and reverse-resolution
- Contract query, call, deploy, and upgrade
- Forwarder/meta-tx helpers
- Gas sponsor connector
- Messaging connector
- Shielded pool operations
- Transaction polling with waitForReceipt
- Crypto adapter interface aligned with `dilithia-core`
- ZK adapter interface for STARK proofs

## Commands

```bash
npm install
npm run build
npm test
```

The test suite validates:

- SDK version: `0.3.0`
- RPC line version: `0.3.0`
- Minimum supported Node.js major: `22`
- Branded types (Address, TxHash, PublicKey, SecretKey)
- TokenAmount precision and formatting
- Error class hierarchy
- Typed response mapping (Balance, Receipt, Nonce)

The package also exposes `loadNativeCryptoAdapter()` to attach the optional
Rust-backed Node bridge when present.
