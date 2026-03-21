//! STARK native bridge for TypeScript/Node.js via NAPI.

use napi::bindgen_prelude::*;
use napi_derive::napi;
use winter_math::{fields::f128::BaseElement, StarkField};

#[napi(object)]
pub struct CommitmentResult {
    pub commitment: String,
    pub nonce: String,
}

#[napi(object)]
pub struct ProofResult {
    pub proof: String,
    pub public_inputs: String,
    pub verification_key: String,
}

/// Convert a hex string to a u64 field element value.
/// Parses up to 8 bytes of hex, interpreting as big-endian u64.
fn hex_to_u64(hex_str: &str) -> napi::Result<u64> {
    let hex_str = hex_str.strip_prefix("0x").unwrap_or(hex_str);
    let bytes = hex::decode(hex_str)
        .map_err(|e| napi::Error::from_reason(format!("invalid hex: {e}")))?;
    if bytes.len() > 8 {
        return Err(napi::Error::from_reason("hex value too large for u64"));
    }
    let mut buf = [0u8; 8];
    buf[8 - bytes.len()..].copy_from_slice(&bytes);
    Ok(u64::from_be_bytes(buf))
}

/// Format a BaseElement as a 0x-prefixed hex string of its u128 inner value.
fn element_to_hex(e: BaseElement) -> String {
    format!("0x{:032x}", e.as_int())
}

#[napi]
pub fn poseidon_hash(inputs: Vec<i64>) -> Result<String> {
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
        _ => Err(napi::Error::from_reason(
            "poseidon_hash expects 2 or 3 inputs",
        )),
    }
}

#[napi]
pub fn compute_commitment(
    value: i64,
    secret_hex: String,
    nonce_hex: String,
) -> Result<CommitmentResult> {
    let secret = hex_to_u64(&secret_hex)?;
    let nonce = hex_to_u64(&nonce_hex)?;

    let v = BaseElement::new(value as u128);
    let s = BaseElement::new(secret as u128);
    let n = BaseElement::new(nonce as u128);

    let commitment = dilithia_stark::poseidon::hash3(v, s, n);
    Ok(CommitmentResult {
        commitment: element_to_hex(commitment),
        nonce: nonce_hex,
    })
}

#[napi]
pub fn compute_nullifier(secret_hex: String, nonce_hex: String) -> Result<String> {
    let secret = hex_to_u64(&secret_hex)?;
    let nonce = hex_to_u64(&nonce_hex)?;

    let s = BaseElement::new(secret as u128);
    let n = BaseElement::new(nonce as u128);

    Ok(element_to_hex(dilithia_stark::poseidon::hash2(s, n)))
}

#[napi]
pub fn generate_preimage_proof(values: Vec<i64>) -> Result<ProofResult> {
    if values.len() != 3 {
        return Err(napi::Error::from_reason(
            "generate_preimage_proof expects [value, secret, nonce]",
        ));
    }
    let value = values[0] as u64;
    let secret = values[1] as u64;
    let nonce = values[2] as u64;

    let (proof_bytes, commitment) =
        dilithia_stark::preimage::prove_preimage(value, secret, nonce)
            .ok_or_else(|| napi::Error::from_reason("preimage proof generation failed"))?;

    let commitment_u64 = commitment.as_int() as u64;

    Ok(ProofResult {
        proof: hex::encode(proof_bytes),
        public_inputs: serde_json::to_string(&element_to_hex(commitment))
            .map_err(|e| napi::Error::from_reason(format!("json error: {e}")))?,
        verification_key: format!("{{\"type\":\"preimage\",\"commitment\":{commitment_u64}}}"),
    })
}

#[napi]
pub fn verify_preimage_proof(
    proof_hex: String,
    _vk_json: String,
    inputs_json: String,
) -> Result<bool> {
    let proof_bytes = hex::decode(&proof_hex)
        .map_err(|e| napi::Error::from_reason(format!("invalid proof hex: {e}")))?;

    // inputs_json is the commitment as a hex string or u64
    let commitment = parse_commitment(&inputs_json)?;

    Ok(dilithia_stark::preimage::verify_preimage(
        &proof_bytes,
        commitment,
    ))
}

fn parse_commitment(inputs_json: &str) -> napi::Result<BaseElement> {
    // Try parsing as a JSON string (hex) first, then as a JSON number
    if let Ok(hex_str) = serde_json::from_str::<String>(inputs_json) {
        let hex_str = hex_str.strip_prefix("0x").unwrap_or(&hex_str);
        let val = u128::from_str_radix(hex_str, 16)
            .map_err(|e| napi::Error::from_reason(format!("invalid commitment hex: {e}")))?;
        return Ok(BaseElement::new(val));
    }
    if let Ok(val) = serde_json::from_str::<u64>(inputs_json) {
        return Ok(BaseElement::new(val as u128));
    }
    Err(napi::Error::from_reason(
        "inputs_json must be a hex string or u64 commitment value",
    ))
}

#[napi]
pub fn generate_range_proof(
    value: i64,
    min_val: i64,
    max_val: i64,
) -> Result<ProofResult> {
    let (proof_bytes, min, max) =
        dilithia_stark::range::prove_range(value as u64, min_val as u64, max_val as u64)
            .ok_or_else(|| napi::Error::from_reason("range proof generation failed"))?;

    Ok(ProofResult {
        proof: hex::encode(proof_bytes),
        public_inputs: format!("[{min},{max}]"),
        verification_key: format!("{{\"type\":\"range\",\"min\":{min},\"max\":{max}}}"),
    })
}

#[napi]
pub fn verify_range_proof(
    proof_hex: String,
    _vk_json: String,
    inputs_json: String,
) -> Result<bool> {
    let proof_bytes = hex::decode(&proof_hex)
        .map_err(|e| napi::Error::from_reason(format!("invalid proof hex: {e}")))?;

    let bounds: Vec<u64> = serde_json::from_str(&inputs_json)
        .map_err(|e| napi::Error::from_reason(format!("invalid inputs json: {e}")))?;

    if bounds.len() != 2 {
        return Err(napi::Error::from_reason(
            "inputs_json must be [min, max]",
        ));
    }

    Ok(dilithia_stark::range::verify_range(
        &proof_bytes,
        bounds[0],
        bounds[1],
    ))
}
