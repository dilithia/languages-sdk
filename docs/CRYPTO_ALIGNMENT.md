# Crypto Alignment

## Goal

All non-browser language SDKs must generate keypairs, addresses and signatures with the same primitive used by the browser wallet.

That primitive is:

- `qsc-crypto`

The browser wallet currently reaches it through the WASM wrapper in `dilithium-wallet-extension/crypto-wasm`.

## Rule

Language SDKs should not invent their own key derivation or address formatting rules.

They must align with:

- mnemonic validation
- mnemonic-to-seed derivation
- account derivation
- ML-DSA key generation
- address derivation from public key
- signature generation and verification

## Shared Operations

The current browser-aligned operations are:

- `generate_mnemonic`
- `validate_mnemonic`
- `seed_from_mnemonic`
- `create_hd_wallet_file_from_mnemonic`
- `create_hd_wallet_account_from_mnemonic`
- `recover_hd_wallet`
- `recover_wallet_file`
- `address_from_public_key`
- `sign_message`
- `verify_message`

## Clean Strategy

The cleanest strategy for multi-language parity is:

1. keep `qsc-crypto` as the single source of truth
2. expose narrow wrappers per runtime
3. make SDKs consume those wrappers instead of reimplementing the primitive

That means:

- TypeScript can use either a Rust-backed native module or a small FFI/CLI bridge, depending on deployment target
- Python should prefer a Rust extension or a small native bridge instead of reimplementing crypto in Python
- Go, Rust and Java should also bind to the same core behavior rather than translating the algorithm independently

## What To Avoid

- reimplementing mnemonic derivation separately in each language
- reimplementing address derivation separately in each language
- diverging account-index rules
- language-specific checksum logic

## Next Step

Before implementing local signing in each SDK, define the shared adapter surface exported from the Rust side of the ecosystem.
