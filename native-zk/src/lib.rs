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

// ── Commitment with domain tag (new in 0.5.0) ────────────────────────

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_generate_commitment_proof(
    value: u64,
    blinding: u64,
    domain_tag: u64,
) -> *mut c_char {
    into_json_result((|| {
        let tag = BaseElement::new(domain_tag as u128);
        let (proof_bytes, inputs) =
            dilithia_stark::commitment::prove_commitment(value, blinding, tag)
                .ok_or_else(|| "commitment proof generation failed".to_string())?;

        Ok(ProofResult {
            proof: hex::encode(proof_bytes),
            public_inputs: serde_json::json!({
                "commitment": element_to_hex(inputs.commitment),
                "domain_tag": domain_tag
            }).to_string(),
            verification_key: format!("{{\"type\":\"commitment\",\"domain_tag\":{domain_tag}}}"),
        })
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_verify_commitment_proof(
    proof_hex: *const c_char,
    _vk_json: *const c_char,
    inputs_json: *const c_char,
) -> *mut c_char {
    into_json_result((|| {
        let proof_str = read_str(proof_hex)?;
        let inputs_str = read_str(inputs_json)?;

        let proof_bytes = hex::decode(&proof_str).map_err(|e| format!("invalid proof hex: {e}"))?;
        let parsed: serde_json::Value = serde_json::from_str(&inputs_str)
            .map_err(|e| format!("invalid inputs json: {e}"))?;

        let commitment_hex = parsed["commitment"].as_str().ok_or("missing commitment")?;
        let domain_tag = parsed["domain_tag"].as_u64().ok_or("missing domain_tag")?;

        let commitment_hex = commitment_hex.strip_prefix("0x").unwrap_or(commitment_hex);
        let commitment_val = u128::from_str_radix(commitment_hex, 16)
            .map_err(|e| format!("invalid commitment hex: {e}"))?;

        let inputs = dilithia_stark::commitment::CommitmentInputs {
            commitment: BaseElement::new(commitment_val),
            domain_tag: BaseElement::new(domain_tag as u128),
        };

        Ok(dilithia_stark::commitment::verify_commitment(&proof_bytes, &inputs))
    })())
}

// ── Predicate proofs (age, balance — new in 0.5.0) ───────────────────

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_prove_predicate(
    value: u64,
    blinding: u64,
    domain_tag: u64,
    min_val: u64,
    max_val: u64,
) -> *mut c_char {
    into_json_result((|| {
        let (proof_bytes, commitment, actual_min, actual_max, tag) =
            dilithia_stark::predicate::prove_predicate(value, blinding, domain_tag, min_val, max_val)
                .ok_or_else(|| "predicate proof generation failed".to_string())?;

        Ok(serde_json::json!({
            "proof": hex::encode(proof_bytes),
            "commitment": format!("0x{:032x}", commitment),
            "min": actual_min,
            "max": actual_max,
            "domain_tag": tag,
        }))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_prove_age_over(
    birth_year: u64,
    current_year: u64,
    min_age: u64,
    blinding: u64,
) -> *mut c_char {
    into_json_result((|| {
        let (proof_bytes, commitment, min, max, tag) =
            dilithia_stark::predicate::prove_age_over(birth_year, current_year, min_age, blinding)
                .ok_or_else(|| "age proof generation failed".to_string())?;

        Ok(serde_json::json!({
            "proof": hex::encode(proof_bytes),
            "commitment": format!("0x{:032x}", commitment),
            "min": min,
            "max": max,
            "domain_tag": tag,
        }))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_verify_age_over(
    proof_hex: *const c_char,
    commitment_hex: *const c_char,
    min_age: u64,
) -> *mut c_char {
    into_json_result((|| {
        let proof_str = read_str(proof_hex)?;
        let commit_str = read_str(commitment_hex)?;
        let proof_bytes = hex::decode(&proof_str).map_err(|e| format!("invalid proof hex: {e}"))?;
        let commit_str = commit_str.strip_prefix("0x").unwrap_or(&commit_str);
        let commitment = u128::from_str_radix(commit_str, 16)
            .map_err(|e| format!("invalid commitment: {e}"))?;

        Ok(dilithia_stark::predicate::verify_age_over(&proof_bytes, commitment, min_age))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_prove_balance_above(
    balance: u64,
    blinding: u64,
    min_balance: u64,
    max_balance: u64,
) -> *mut c_char {
    into_json_result((|| {
        let (proof_bytes, commitment, min, max, tag) =
            dilithia_stark::predicate::prove_balance_above(balance, blinding, min_balance, max_balance)
                .ok_or_else(|| "balance proof generation failed".to_string())?;

        Ok(serde_json::json!({
            "proof": hex::encode(proof_bytes),
            "commitment": format!("0x{:032x}", commitment),
            "min": min,
            "max": max,
            "domain_tag": tag,
        }))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_verify_balance_above(
    proof_hex: *const c_char,
    commitment_hex: *const c_char,
    min_balance: u64,
    max_balance: u64,
) -> *mut c_char {
    into_json_result((|| {
        let proof_str = read_str(proof_hex)?;
        let commit_str = read_str(commitment_hex)?;
        let proof_bytes = hex::decode(&proof_str).map_err(|e| format!("invalid proof hex: {e}"))?;
        let commit_str = commit_str.strip_prefix("0x").unwrap_or(&commit_str);
        let commitment = u128::from_str_radix(commit_str, 16)
            .map_err(|e| format!("invalid commitment: {e}"))?;

        Ok(dilithia_stark::predicate::verify_balance_above(&proof_bytes, commitment, min_balance, max_balance))
    })())
}

// ── Transfer proof (new in 0.5.0) ────────────────────────────────────

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_prove_transfer(
    sender_pre: u64,
    receiver_pre: u64,
    amount: u64,
) -> *mut c_char {
    into_json_result((|| {
        let (proof_bytes, inputs) =
            dilithia_stark::transfer::prove_transfer(sender_pre, receiver_pre, amount)
                .ok_or_else(|| "transfer proof generation failed".to_string())?;

        Ok(serde_json::json!({
            "proof": hex::encode(proof_bytes),
            "sender_pre": inputs.sender_pre.as_int() as u64,
            "receiver_pre": inputs.receiver_pre.as_int() as u64,
            "sender_post": inputs.sender_post.as_int() as u64,
            "receiver_post": inputs.receiver_post.as_int() as u64,
        }))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_verify_transfer(
    proof_hex: *const c_char,
    inputs_json: *const c_char,
) -> *mut c_char {
    into_json_result((|| {
        let proof_str = read_str(proof_hex)?;
        let inputs_str = read_str(inputs_json)?;
        let proof_bytes = hex::decode(&proof_str).map_err(|e| format!("invalid proof hex: {e}"))?;
        let parsed: serde_json::Value = serde_json::from_str(&inputs_str)
            .map_err(|e| format!("invalid inputs json: {e}"))?;

        let inputs = dilithia_stark::transfer::TransferInputs {
            sender_pre: BaseElement::new(parsed["sender_pre"].as_u64().ok_or("missing sender_pre")? as u128),
            receiver_pre: BaseElement::new(parsed["receiver_pre"].as_u64().ok_or("missing receiver_pre")? as u128),
            sender_post: BaseElement::new(parsed["sender_post"].as_u64().ok_or("missing sender_post")? as u128),
            receiver_post: BaseElement::new(parsed["receiver_post"].as_u64().ok_or("missing receiver_post")? as u128),
        };

        Ok(dilithia_stark::transfer::verify_transfer(&proof_bytes, &inputs))
    })())
}

// ── Merkle verification (new in 0.5.0) ───────────────────────────────

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_prove_merkle_verify(
    leaf_hash_hex: *const c_char,
    path_json: *const c_char,
) -> *mut c_char {
    into_json_result((|| {
        let leaf_str = read_str(leaf_hash_hex)?;
        let path_str = read_str(path_json)?;

        let leaf_hex = leaf_str.strip_prefix("0x").unwrap_or(&leaf_str);
        let leaf_val = u128::from_str_radix(leaf_hex, 16)
            .map_err(|e| format!("invalid leaf hash: {e}"))?;
        let leaf_hash = BaseElement::new(leaf_val);

        let path_data: Vec<serde_json::Value> = serde_json::from_str(&path_str)
            .map_err(|e| format!("invalid path json: {e}"))?;

        let path: Vec<dilithia_stark::merkle_verify::MerkleLevel> = path_data.iter().map(|level| {
            let sibling_hex = level["sibling"].as_str().unwrap_or("0");
            let sibling_hex = sibling_hex.strip_prefix("0x").unwrap_or(sibling_hex);
            let sibling_val = u128::from_str_radix(sibling_hex, 16).unwrap_or(0);
            let is_left = level["is_left"].as_bool().unwrap_or(false);
            dilithia_stark::merkle_verify::MerkleLevel {
                sibling: BaseElement::new(sibling_val),
                is_left,
            }
        }).collect();

        let (proof_bytes, inputs) =
            dilithia_stark::merkle_verify::prove_merkle_verify(leaf_hash, &path)
                .ok_or_else(|| "merkle proof generation failed".to_string())?;

        Ok(serde_json::json!({
            "proof": hex::encode(proof_bytes),
            "leaf_hash": element_to_hex(inputs.leaf_hash),
            "root": element_to_hex(inputs.root),
            "depth": inputs.depth,
        }))
    })())
}

#[unsafe(no_mangle)]
pub extern "C" fn dilithia_verify_merkle_proof(
    proof_hex: *const c_char,
    inputs_json: *const c_char,
) -> *mut c_char {
    into_json_result((|| {
        let proof_str = read_str(proof_hex)?;
        let inputs_str = read_str(inputs_json)?;
        let proof_bytes = hex::decode(&proof_str).map_err(|e| format!("invalid proof hex: {e}"))?;
        let parsed: serde_json::Value = serde_json::from_str(&inputs_str)
            .map_err(|e| format!("invalid inputs json: {e}"))?;

        let leaf_hex = parsed["leaf_hash"].as_str().ok_or("missing leaf_hash")?;
        let root_hex = parsed["root"].as_str().ok_or("missing root")?;
        let depth = parsed["depth"].as_u64().ok_or("missing depth")? as usize;

        let leaf_hex = leaf_hex.strip_prefix("0x").unwrap_or(leaf_hex);
        let root_hex = root_hex.strip_prefix("0x").unwrap_or(root_hex);

        let inputs = dilithia_stark::merkle_verify::MerkleVerifyInputs {
            leaf_hash: BaseElement::new(u128::from_str_radix(leaf_hex, 16).map_err(|e| format!("invalid leaf: {e}"))?),
            root: BaseElement::new(u128::from_str_radix(root_hex, 16).map_err(|e| format!("invalid root: {e}"))?),
            depth,
        };

        Ok(dilithia_stark::merkle_verify::verify_merkle_verify(&proof_bytes, &inputs))
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
