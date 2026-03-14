# Go Native Bridge Direction

The Go SDK should consume the shared `native-core/` C ABI bridge first.

That gives Go:

- mnemonic generation and validation
- deterministic HD account recovery
- address derivation
- sign / verify

without reimplementing `qsc-crypto` logic in Go.

Current integration path:

1. build `native-core` as a shared library
2. load it through `dlopen` from `go/sdk/crypto_cgo.go`
3. map JSON bridge payloads to idiomatic Go structs
4. keep the high-level SDK surface independent from the bridge transport

Current status:

- implemented for mnemonic generation/validation and root/HD account recovery
- implemented for HD wallet-file create/recover flows
- sign and verify are wired through the same bridge
