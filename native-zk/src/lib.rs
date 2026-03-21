//! STARK native bridge — shared C ABI over dilithia-stark.

use std::ffi::{c_char, CString};
use std::ptr;

use serde::Serialize;
use winter_math::{fields::f128::BaseElement, StarkField};

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

/// Convert a hex string to a u64 field element value.
fn hex_to_u64(hex_str: &str) -> Result<u64, String> {
    let hex_str = hex_str.strip_prefix("0x").unwrap_or(hex_str);
    let bytes =
        hex::decode(hex_str).map_err(|e| format!("invalid hex: {e}"))?;
    if bytes.len() > 8 {
        return Err("hex value too large for u64".to_string());
    }
    let mut buf = [0u8; 8];
    buf[8 - bytes.len()..].copy_from_slice(&bytes);
    Ok(u64::from_be_bytes(buf))
}

/// Format a BaseElement as a 0x-prefixed hex string.
fn element_to_hex(e: BaseElement) -> String {
    format!("0x{:032x}", e.as_int())
}

fn parse_commitment(inputs_json: &str) -> Result<BaseElement, String> {
    if let Ok(hex_str) = serde_json::from_str::<String>(inputs_json) {
        let hex_str = hex_str.strip_prefix("0x").unwrap_or(&hex_str);
        let val = u128::from_str_radix(hex_str, 16)
            .map_err(|e| format!("invalid commitment hex: {e}"))?;
        return Ok(BaseElement::new(val));
    }
    if let Ok(val) = serde_json::from_str::<u64>(inputs_json) {
        return Ok(BaseElement::new(val as u128));
    }
    Err("inputs_json must be a hex string or u64 commitment value".to_string())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_poseidon_hash(inputs_json: *const c_char) -> *mut c_char {
    into_json_result((|| {
        let json_str = read_str(inputs_json)?;
        let inputs: Vec<u64> = serde_json::from_str(&json_str)
            .map_err(|e| format!("invalid inputs json: {e}"))?;

        match inputs.len() {
            2 => {
                let a = BaseElement::new(inputs[0] as u128);
                let b = BaseElement::new(inputs[1] as u128);
                Ok(element_to_hex(dilithia_stark::poseidon::hash2(a, b)))
            }
            3 => {
                let a = BaseElement::new(inputs[0] as u128);
                let b = BaseElement::new(inputs[1] as u128);
                let c = BaseElement::new(inputs[2] as u128);
                Ok(element_to_hex(dilithia_stark::poseidon::hash3(a, b, c)))
            }
            _ => Err("poseidon_hash expects 2 or 3 inputs".to_string()),
        }
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_compute_commitment(
    value: u64,
    secret_hex: *const c_char,
    nonce_hex: *const c_char,
) -> *mut c_char {
    into_json_result((|| {
        let secret_str = read_str(secret_hex)?;
        let nonce_str = read_str(nonce_hex)?;
        let secret = hex_to_u64(&secret_str)?;
        let nonce = hex_to_u64(&nonce_str)?;

        let v = BaseElement::new(value as u128);
        let s = BaseElement::new(secret as u128);
        let n = BaseElement::new(nonce as u128);

        let commitment = dilithia_stark::poseidon::hash3(v, s, n);
        Ok(CommitmentResult {
            commitment: element_to_hex(commitment),
            nonce: nonce_str,
        })
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_compute_nullifier(
    secret_hex: *const c_char,
    nonce_hex: *const c_char,
) -> *mut c_char {
    into_json_result((|| {
        let secret_str = read_str(secret_hex)?;
        let nonce_str = read_str(nonce_hex)?;
        let secret = hex_to_u64(&secret_str)?;
        let nonce = hex_to_u64(&nonce_str)?;

        let s = BaseElement::new(secret as u128);
        let n = BaseElement::new(nonce as u128);

        Ok(element_to_hex(dilithia_stark::poseidon::hash2(s, n)))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_generate_preimage_proof(inputs_json: *const c_char) -> *mut c_char {
    into_json_result((|| {
        let json_str = read_str(inputs_json)?;
        let values: Vec<u64> = serde_json::from_str(&json_str)
            .map_err(|e| format!("invalid inputs json: {e}"))?;

        if values.len() != 3 {
            return Err("expected [value, secret, nonce]".to_string());
        }

        let (proof_bytes, commitment) =
            dilithia_stark::preimage::prove_preimage(values[0], values[1], values[2])
                .ok_or_else(|| "preimage proof generation failed".to_string())?;

        let commitment_u64 = commitment.as_int() as u64;

        Ok(ProofResult {
            proof: hex::encode(proof_bytes),
            public_inputs: element_to_hex(commitment),
            verification_key: format!(
                "{{\"type\":\"preimage\",\"commitment\":{commitment_u64}}}"
            ),
        })
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_verify_preimage_proof(
    proof_hex: *const c_char,
    vk_json: *const c_char,
    inputs_json: *const c_char,
) -> *mut c_char {
    let _ = vk_json; // VK is implicit for the preimage AIR
    into_json_result((|| {
        let proof_str = read_str(proof_hex)?;
        let inputs_str = read_str(inputs_json)?;

        let proof_bytes =
            hex::decode(&proof_str).map_err(|e| format!("invalid proof hex: {e}"))?;
        let commitment = parse_commitment(&inputs_str)?;

        Ok(dilithia_stark::preimage::verify_preimage(
            &proof_bytes,
            commitment,
        ))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_generate_range_proof(
    value: u64,
    min_val: u64,
    max_val: u64,
) -> *mut c_char {
    into_json_result((|| {
        let (proof_bytes, min, max) =
            dilithia_stark::range::prove_range(value, min_val, max_val)
                .ok_or_else(|| "range proof generation failed".to_string())?;

        Ok(ProofResult {
            proof: hex::encode(proof_bytes),
            public_inputs: format!("[{min},{max}]"),
            verification_key: format!("{{\"type\":\"range\",\"min\":{min},\"max\":{max}}}"),
        })
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_verify_range_proof(
    proof_hex: *const c_char,
    vk_json: *const c_char,
    inputs_json: *const c_char,
) -> *mut c_char {
    let _ = vk_json; // VK is implicit for the range AIR
    into_json_result((|| {
        let proof_str = read_str(proof_hex)?;
        let inputs_str = read_str(inputs_json)?;

        let proof_bytes =
            hex::decode(&proof_str).map_err(|e| format!("invalid proof hex: {e}"))?;

        let bounds: Vec<u64> = serde_json::from_str(&inputs_str)
            .map_err(|e| format!("invalid inputs json: {e}"))?;

        if bounds.len() != 2 {
            return Err("inputs_json must be [min, max]".to_string());
        }

        Ok(dilithia_stark::range::verify_range(
            &proof_bytes,
            bounds[0],
            bounds[1],
        ))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_stark_string_free(ptr: *mut c_char) {
    if ptr.is_null() {
        return;
    }
    unsafe {
        drop(CString::from_raw(ptr));
    }
}
