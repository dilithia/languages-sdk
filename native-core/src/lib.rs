use std::ffi::{c_char, CString};
use std::ptr;

use dilithia_core::{crypto, hash, wallet};
use dilithia_core::hash::HashAlg;
use dilithia_core::wallet::WalletFile;
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

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_generate_mnemonic() -> *mut c_char {
    into_json_result(wallet::generate_mnemonic())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_create_wallet_file(password: *const c_char) -> *mut c_char {
    let result = read_str(password).and_then(|password| {
        let (mnemonic, wallet_file) = wallet::create_wallet(&password)?;
        Ok(WalletCreationResult { mnemonic, wallet_file })
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_validate_mnemonic(mnemonic: *const c_char) -> *mut c_char {
    let result = read_str(mnemonic).and_then(|mnemonic| wallet::validate_mnemonic(&mnemonic));
    into_json_result(result.map(|_| serde_json::json!({ "valid": true })))
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_create_hd_wallet_account_from_mnemonic(
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

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_create_hd_wallet_file_from_mnemonic(
    mnemonic: *const c_char,
    password: *const c_char,
) -> *mut c_char {
    dilithia_create_hd_wallet_account_from_mnemonic(mnemonic, password, 0)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_recover_hd_account(
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

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_recover_wallet_file(
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

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_address_from_public_key(public_key_hex: *const c_char) -> *mut c_char {
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

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_sign_message(
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

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_verify_message(
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

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_validate_address(addr: *const c_char) -> *mut c_char {
    let result = read_str(addr).and_then(|addr| {
        let validated = crypto::validate_address(&addr)?;
        Ok(validated)
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_address_from_pk_checksummed(public_key_hex: *const c_char) -> *mut c_char {
    let result = read_str(public_key_hex).and_then(|public_key_hex| {
        let public_key =
            hex::decode(public_key_hex).map_err(|e| format!("invalid public key hex: {e}"))?;
        crypto::validate_pk(&public_key)?;
        Ok(crypto::address_from_pk_checksummed(&public_key))
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_address_with_checksum(raw_addr: *const c_char) -> *mut c_char {
    let result = read_str(raw_addr).and_then(|raw_addr| {
        Ok(crypto::address_with_checksum(&raw_addr))
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_validate_pk(public_key_hex: *const c_char) -> *mut c_char {
    let result = read_str(public_key_hex).and_then(|public_key_hex| {
        let public_key =
            hex::decode(public_key_hex).map_err(|e| format!("invalid public key hex: {e}"))?;
        crypto::validate_pk(&public_key)?;
        Ok(true)
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_validate_sk(secret_key_hex: *const c_char) -> *mut c_char {
    let result = read_str(secret_key_hex).and_then(|secret_key_hex| {
        let secret_key =
            hex::decode(secret_key_hex).map_err(|e| format!("invalid secret key hex: {e}"))?;
        crypto::validate_sk(&secret_key)?;
        Ok(true)
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_validate_sig(signature_hex: *const c_char) -> *mut c_char {
    let result = read_str(signature_hex).and_then(|signature_hex| {
        let signature =
            hex::decode(signature_hex).map_err(|e| format!("invalid signature hex: {e}"))?;
        crypto::validate_sig(&signature)?;
        Ok(true)
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_keygen_mldsa65() -> *mut c_char {
    let result = crypto::keygen_mldsa65_secure().map(|(sk, pk)| {
        let address = crypto::address_from_pk(&pk);
        serde_json::json!({
            "secret_key": hex::encode(&*sk),
            "public_key": hex::encode(pk),
            "address": address
        })
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_keygen_mldsa65_from_seed(seed_hex: *const c_char) -> *mut c_char {
    let result = read_str(seed_hex).and_then(|seed_hex| {
        let seed_bytes =
            hex::decode(seed_hex).map_err(|e| format!("invalid seed hex: {e}"))?;
        let seed: [u8; 32] = seed_bytes
            .try_into()
            .map_err(|_| "seed must be exactly 32 bytes".to_string())?;
        let (sk, pk) = crypto::keygen_mldsa65_from_seed(&seed);
        let address = crypto::address_from_pk(&pk);
        Ok(serde_json::json!({
            "secret_key": hex::encode(sk),
            "public_key": hex::encode(pk),
            "address": address
        }))
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_seed_from_mnemonic(mnemonic: *const c_char) -> *mut c_char {
    let result = read_str(mnemonic).map(|mnemonic| {
        let seed = crypto::seed_from_mnemonic(&mnemonic);
        hex::encode(seed)
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_derive_child_seed(
    parent_seed_hex: *const c_char,
    index: u32,
) -> *mut c_char {
    let result = read_str(parent_seed_hex).and_then(|parent_seed_hex| {
        let seed_bytes =
            hex::decode(parent_seed_hex).map_err(|e| format!("invalid seed hex: {e}"))?;
        let parent_seed: [u8; 32] = seed_bytes
            .try_into()
            .map_err(|_| "parent seed must be exactly 32 bytes".to_string())?;
        let child = crypto::derive_child_seed(&parent_seed, index);
        Ok(hex::encode(child))
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_constant_time_eq(
    a_hex: *const c_char,
    b_hex: *const c_char,
) -> *mut c_char {
    let result = read_str(a_hex).and_then(|a_hex| {
        let b_hex = read_str(b_hex)?;
        let a = hex::decode(a_hex).map_err(|e| format!("invalid hex (a): {e}"))?;
        let b = hex::decode(b_hex).map_err(|e| format!("invalid hex (b): {e}"))?;
        Ok(crypto::constant_time_eq(&a, &b))
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_hash_hex(data_hex: *const c_char) -> *mut c_char {
    let result = read_str(data_hex).and_then(|data_hex| {
        let data =
            hex::decode(data_hex).map_err(|e| format!("invalid data hex: {e}"))?;
        Ok(hash::hash_hex(&data))
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_set_hash_alg(alg: *const c_char) -> *mut c_char {
    let result = read_str(alg).and_then(|alg| {
        let hash_alg = match alg.as_str() {
            "sha3_512" => HashAlg::Sha3_512,
            "blake2b512" => HashAlg::Blake2b512,
            "blake3_256" => HashAlg::Blake3_256,
            other => return Err(format!("unknown hash algorithm: {other}")),
        };
        hash::set_hash_alg(hash_alg)?;
        Ok(true)
    });
    into_json_result(result)
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_current_hash_alg() -> *mut c_char {
    let alg = hash::current_hash_alg();
    let name = match alg {
        HashAlg::Sha3_512 => "sha3_512",
        HashAlg::Blake2b512 => "blake2b512",
        HashAlg::Blake3_256 => "blake3_256",
    };
    into_json_result(Ok::<_, String>(name))
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_hash_len_hex() -> *mut c_char {
    into_json_result(Ok::<_, String>(hash::hash_len_hex()))
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_string_free(ptr: *mut c_char) {
    if ptr.is_null() {
        return;
    }
    unsafe {
        drop(CString::from_raw(ptr));
    }
}
