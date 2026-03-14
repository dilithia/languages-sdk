# Dilithium SDK Native Core

Shared C ABI bridge over `qsc-crypto`.

Purpose:

- expose one native crypto surface for runtimes that do not yet have a dedicated
  `napi-rs` or `pyo3` style bridge
- support Go and Java without duplicating cryptographic integration logic

Current exported functions:

- `dilithium_generate_mnemonic`
- `dilithium_create_wallet_file`
- `dilithium_validate_mnemonic`
- `dilithium_create_hd_wallet_file_from_mnemonic`
- `dilithium_create_hd_wallet_account_from_mnemonic`
- `dilithium_recover_hd_account`
- `dilithium_recover_wallet_file`
- `dilithium_address_from_public_key`
- `dilithium_sign_message`
- `dilithium_verify_message`
- `dilithium_string_free`

Return convention:

- every function returns a JSON string payload
- success: `{"ok":true,"value":...}`
- error: `{"ok":false,"error":"..."}`
