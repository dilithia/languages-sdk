# Rust SDK Research

## Priority

Rust should come after the first bot-facing SDKs unless core reuse drives it earlier.

It is valuable for:

- high-performance services
- native tooling
- deep ecosystem integration

## Conventions to Follow

- builder-based client construction
- explicit transport and signer traits
- strong typed models
- feature flags for optional modules

## Target API Shape

Likely surface:

- `Client::builder()`
- `client.balance(address).await`
- `client.nonce(address).await`
- `client.receipt(tx_hash).await`
- `client.address_summary(address).await`
- `client.simulate(call).await`
- `client.send_call(call, signer).await`
- `client.wait_for_receipt(tx_hash, opts).await`

## Good Practices

- zero EVM assumptions
- serialization models isolated from transport
- async where network I/O exists
- cryptographic helpers split cleanly from RPC helpers

## CI / Publishing

Planned workflow:

- format check
- clippy
- tests
- crate publish on tagged release
