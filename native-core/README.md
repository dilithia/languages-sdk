# Dilithia SDK Native Core

Shared C ABI bridge over `dilithia-core`.

Purpose:

- expose one native crypto surface for runtimes that do not yet have a dedicated
  `napi-rs` or `pyo3` style bridge
- support Go and Java without duplicating cryptographic integration logic

Current exported functions:

- `dilithia_generate_mnemonic`
- `dilithia_create_wallet_file`
- `dilithia_validate_mnemonic`
- `dilithia_create_hd_wallet_file_from_mnemonic`
- `dilithia_create_hd_wallet_account_from_mnemonic`
- `dilithia_recover_hd_account`
- `dilithia_recover_wallet_file`
- `dilithia_address_from_public_key`
- `dilithia_sign_message`
- `dilithia_verify_message`
- `dilithia_string_free`

Return convention:

- every function returns a JSON string payload
- success: `{"ok":true,"value":...}`
- error: `{"ok":false,"error":"..."}`
