//! STARK native bridge — requires dilithia-core with feature "stark".
//! This package will not compile until dilithia-core publishes the stark feature.
//! See: https://github.com/dilithia/languages-sdk/issues/TBD

use std::ffi::{c_char, CString};
use std::ptr;

use serde::Serialize;

#[derive(Serialize)]
struct CommitmentResult {
    commitment: String,
    nonce: String,
}

#[derive(Serialize)]
struct ProofResult {
    proof: String,
    public_inputs: String,
    verification_key: String,
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
pub extern "C" fn dilithia_poseidon_hash(inputs_json: *const c_char) -> *mut c_char {
    let _result = read_str(inputs_json);
    todo!()
}

#[no_mangle]
pub extern "C" fn dilithia_compute_commitment(
    value: u64,
    secret_hex: *const c_char,
    nonce_hex: *const c_char,
) -> *mut c_char {
    let _secret = read_str(secret_hex);
    let _nonce = read_str(nonce_hex);
    let _ = value;
    todo!()
}

#[no_mangle]
pub extern "C" fn dilithia_compute_nullifier(
    secret_hex: *const c_char,
    nonce_hex: *const c_char,
) -> *mut c_char {
    let _secret = read_str(secret_hex);
    let _nonce = read_str(nonce_hex);
    todo!()
}

#[no_mangle]
pub extern "C" fn dilithia_generate_preimage_proof(inputs_json: *const c_char) -> *mut c_char {
    let _result = read_str(inputs_json);
    todo!()
}

#[no_mangle]
pub extern "C" fn dilithia_verify_preimage_proof(
    proof_hex: *const c_char,
    vk_json: *const c_char,
    inputs_json: *const c_char,
) -> *mut c_char {
    let _proof = read_str(proof_hex);
    let _vk = read_str(vk_json);
    let _inputs = read_str(inputs_json);
    todo!()
}

#[no_mangle]
pub extern "C" fn dilithia_generate_range_proof(
    value: u64,
    min_val: u64,
    max_val: u64,
) -> *mut c_char {
    let _ = (value, min_val, max_val);
    todo!()
}

#[no_mangle]
pub extern "C" fn dilithia_verify_range_proof(
    proof_hex: *const c_char,
    vk_json: *const c_char,
    inputs_json: *const c_char,
) -> *mut c_char {
    let _proof = read_str(proof_hex);
    let _vk = read_str(vk_json);
    let _inputs = read_str(inputs_json);
    todo!()
}

#[no_mangle]
pub extern "C" fn dilithia_stark_string_free(ptr: *mut c_char) {
    if ptr.is_null() {
        return;
    }
    unsafe {
        drop(CString::from_raw(ptr));
    }
}
