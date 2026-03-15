# TypeScript SDK Research

## Priority

TypeScript is the first priority for Dilithia language SDKs.

It covers:

- Node.js bots
- backend automation
- shared tooling
- future browser-safe non-provider helpers

## Conventions to Follow

- ESM-first package layout
- top-level `createClient(...)`
- async methods returning typed objects
- explicit transport and signer separation
- tree-shakeable modules
- no hidden globals

## Target API Shape

Likely entry points:

- `createClient(config)`
- `client.getBalance(address)`
- `client.getNonce(address)`
- `client.getReceipt(txHash)`
- `client.getAddressSummary(address)`
- `client.simulate(call)`
- `client.sendCall(call, signer)`
- `client.waitForReceipt(txHash, options)`

## Web3 Design Notes

The SDK should feel familiar to users coming from:

- `ethers`
- `viem`
- `web3.js`

But it should avoid copying EVM assumptions:

- no `0x` address assumptions
- no EVM ABI dependency at the core
- long post-quantum keys and signatures are first-class

## Good Practices

- strong TypeScript types
- small pure helpers
- transport adapters isolated from high-level client logic
- browser provider logic kept out of this repo
- no implicit retries except in explicit polling helpers

## CI / Publishing

Planned workflow:

- install dependencies
- typecheck
- run tests
- build package
- publish to npm on tagged releases

## Native Crypto Plan

The TypeScript SDK should not reimplement crypto in JavaScript.

The intended path is:

- RPC client in TypeScript
- native crypto bridge in `typescript/native/`
- Rust bridge based on `napi-rs`
- crypto behavior sourced from `dilithia-core`
