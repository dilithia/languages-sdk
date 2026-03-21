# Ecosystem Surface

`languages-sdk` is the client base for the Dilithia ecosystem.

It is not only for bots. It is intended to be the shared integration layer for:

- oracle services
- bridge / relayer services
- DEX backends and frontends
- vault services
- DiliScan and explorer-facing services
- indexers
- protocol automation and ops

## Shared Layers

Every language SDK should converge on the same conceptual layers:

- `transport`
  - raw JSON-RPC
  - raw REST
  - websocket URL/configuration
- `chain`
  - balance
  - nonce
  - receipt
  - address summary
  - gas estimate
  - base fee
- `contracts`
  - build contract calls
  - query contracts
  - call contracts
  - simulate calls
- `signing`
  - mnemonic and account recovery
  - signing
  - verification
- `sponsor`
  - paymaster helpers
  - sponsor contract connectors
- `messaging`
  - send message
  - receive message
  - inbox / outbox queries
- `nameservice`
  - resolve
  - reverse resolve
  - availability and ownership helpers

## Design Rule

High-level ecosystem services should prefer these SDKs instead of inventing one-off clients.

That keeps:

- transport handling consistent
- signing behavior aligned with `dilithia-core`
- contract calling conventions stable
- sponsor and messaging semantics shared
- future protocol features easier to roll out
