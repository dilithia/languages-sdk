use std::ffi::{c_char, CString};
use std::ptr;

use qsc_crypto::{crypto, wallet};
use qsc_crypto::wallet::WalletFile;
use serde::{Deserialize, Serialize};

#[derive(Serialize)]
struct WalletCreationResult {
    mnemonic: String,
    wallet_file: WalletFile,
}

#[derive(Serialize)]
struct WalletAccountResult {
    address: String,
    public_key: String,
    secret_key: String,
    account_index: u32,
    wallet_file: Option<WalletFile>,
}

#[derive(Deserialize)]
struct WalletFileInput {
    version: u8,
    address: String,
    public_key: String,
    encrypted_sk: String,
    nonce: String,
    tag: String,
    account_index: Option<u32>,
}

fn into_c_string(value: String) -> *mut c_char {
    CString::new(value)
        .map(CString::into_raw)
        .unwrap_or_else(|_| ptr::null_mut())
}

fn into_json_result<T: serde::Serialize>(result: Result<T, String>) -> *mut c_char {
    match result {
        Ok(value) => into_c_string(
            serde_json::json!({
                "ok": true,
                "value": value
            })
            .to_string(),
        ),
        Err(error) => into_c_string(
            serde_json::json!({
                "ok": false,
                "error": error
            })
            .to_string(),
        ),
    }
}

fn read_str(input: *const c_char) -> Result<String, String> {
    if input.is_null() {
        return Err("null pointer".to_string());
    }
    let cstr = unsafe { std::ffi::CStr::from_ptr(input) };
    cstr.to_str()
        .map(|value| value.to_string())
        .map_err(|e| format!("invalid utf-8: {e}"))
}

#[no_mangle]
pub extern "C" fn dilithium_generate_mnemonic() -> *mut c_char {
    into_json_result(wallet::generate_mnemonic())
}

#[no_mangle]
pub extern "C" fn dilithium_create_wallet_file(password: *const c_char) -> *mut c_char {
    let result = read_str(password).and_then(|password| {
        let (mnemonic, wallet_file) = wallet::create_wallet(&password)?;
        Ok(WalletCreationResult { mnemonic, wallet_file })
    });
    into_json_result(result)
}

#[no_mangle]
pub extern "C" fn dilithium_validate_mnemonic(mnemonic: *const c_char) -> *mut c_char {
    let result = read_str(mnemonic).and_then(|mnemonic| wallet::validate_mnemonic(&mnemonic));
    into_json_result(result.map(|_| serde_json::json!({ "valid": true })))
}

#[no_mangle]
pub extern "C" fn dilithium_create_hd_wallet_account_from_mnemonic(
    mnemonic: *const c_char,
    password: *const c_char,
    account_index: u32,
) -> *mut c_char {
    let result = read_str(mnemonic).and_then(|mnemonic| {
        let password = read_str(password)?;
        wallet::validate_mnemonic(&mnemonic)?;
        let normalized = mnemonic.trim().to_lowercase();
        let wallet_file = wallet::create_hd_wallet_account(&normalized, account_index, &password)?;
        let (secret_key, public_key, address) = wallet::recover_hd_account(&normalized, account_index);
        Ok(WalletAccountResult {
            address,
            public_key: hex::encode(public_key),
            secret_key: hex::encode(secret_key),
            account_index,
            wallet_file: Some(wallet_file),
        })
    });
    into_json_result(result)
}

#[no_mangle]
pub extern "C" fn dilithium_create_hd_wallet_file_from_mnemonic(
    mnemonic: *const c_char,
    password: *const c_char,
) -> *mut c_char {
    dilithium_create_hd_wallet_account_from_mnemonic(mnemonic, password, 0)
}

#[no_mangle]
pub extern "C" fn dilithium_recover_hd_account(
    mnemonic: *const c_char,
    account_index: u32,
) -> *mut c_char {
    let result = read_str(mnemonic).and_then(|mnemonic| {
        wallet::validate_mnemonic(&mnemonic)?;
        let normalized = mnemonic.trim().to_lowercase();
        let (secret_key, public_key, address) = wallet::recover_hd_account(&normalized, account_index);
        Ok(WalletAccountResult {
            address,
            public_key: hex::encode(public_key),
            secret_key: hex::encode(secret_key),
            account_index,
            wallet_file: None,
        })
    });
    into_json_result(result)
}

#[no_mangle]
pub extern "C" fn dilithium_recover_wallet_file(
    wallet_file_json: *const c_char,
    mnemonic: *const c_char,
    password: *const c_char,
) -> *mut c_char {
    let result = read_str(wallet_file_json).and_then(|wallet_file_json| {
        let mnemonic = read_str(mnemonic)?;
        let password = read_str(password)?;
        let wallet_file_input: WalletFileInput =
            serde_json::from_str(&wallet_file_json).map_err(|e| format!("invalid wallet file JSON: {e}"))?;
        let wallet_file = WalletFile {
            version: wallet_file_input.version,
            address: wallet_file_input.address,
            public_key: wallet_file_input.public_key,
            encrypted_sk: wallet_file_input.encrypted_sk,
            nonce: wallet_file_input.nonce,
            tag: wallet_file_input.tag,
            account_index: wallet_file_input.account_index,
        };
        let normalized = mnemonic.trim().to_lowercase();
        let (secret_key, address) = if wallet_file.account_index.is_some() {
            wallet::recover_hd_wallet_account(&wallet_file, &normalized, &password)?
        } else {
            wallet::recover_wallet(&wallet_file, &normalized, &password)?
        };
        Ok(WalletAccountResult {
            address,
            public_key: wallet_file.public_key.clone(),
            secret_key: hex::encode(secret_key),
            account_index: wallet_file.account_index.unwrap_or(0),
            wallet_file: Some(wallet_file),
        })
    });
    into_json_result(result)
}

#[no_mangle]
pub extern "C" fn dilithium_address_from_public_key(public_key_hex: *const c_char) -> *mut c_char {
    let result = read_str(public_key_hex).and_then(|public_key_hex| {
        let public_key =
            hex::decode(public_key_hex).map_err(|e| format!("invalid public key hex: {e}"))?;
        crypto::validate_pk(&public_key)?;
        Ok(serde_json::json!({
            "address": crypto::address_from_pk(&public_key)
        }))
    });
    into_json_result(result)
}

#[no_mangle]
pub extern "C" fn dilithium_sign_message(
    secret_key_hex: *const c_char,
    message: *const c_char,
) -> *mut c_char {
    let result = read_str(secret_key_hex).and_then(|secret_key_hex| {
        let message = read_str(message)?;
        let secret_key =
            hex::decode(secret_key_hex).map_err(|e| format!("invalid secret key hex: {e}"))?;
        let signature = crypto::sign_mldsa65(message.as_bytes(), &secret_key)?;
        Ok(serde_json::json!({
            "algorithm": "mldsa65",
            "signature": hex::encode(signature)
        }))
    });
    into_json_result(result)
}

#[no_mangle]
pub extern "C" fn dilithium_verify_message(
    public_key_hex: *const c_char,
    message: *const c_char,
    signature_hex: *const c_char,
) -> *mut c_char {
    let result = read_str(public_key_hex).and_then(|public_key_hex| {
        let message = read_str(message)?;
        let signature_hex = read_str(signature_hex)?;
        let public_key =
            hex::decode(public_key_hex).map_err(|e| format!("invalid public key hex: {e}"))?;
        let signature =
            hex::decode(signature_hex).map_err(|e| format!("invalid signature hex: {e}"))?;
        Ok(serde_json::json!({
            "ok": crypto::verify_mldsa65(message.as_bytes(), &signature, &public_key)
        }))
    });
    into_json_result(result)
}

#[no_mangle]
pub extern "C" fn dilithium_string_free(ptr: *mut c_char) {
    if ptr.is_null() {
        return;
    }
    unsafe {
        drop(CString::from_raw(ptr));
    }
}
