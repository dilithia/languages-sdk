//! STARK native bridge for Python via PyO3.

use pyo3::prelude::*;
use pyo3::types::PyDict;
use winter_math::{fields::f128::BaseElement, StarkField};

/// Convert a hex string to a u64 field element value.
fn hex_to_u64(hex_str: &str) -> PyResult<u64> {
    let hex_str = hex_str.strip_prefix("0x").unwrap_or(hex_str);
    let bytes = hex::decode(hex_str)
        .map_err(|e| pyo3::exceptions::PyValueError::new_err(format!("invalid hex: {e}")))?;
    if bytes.len() > 8 {
        return Err(pyo3::exceptions::PyValueError::new_err(
            "hex value too large for u64",
        ));
    }
    let mut buf = [0u8; 8];
    buf[8 - bytes.len()..].copy_from_slice(&bytes);
    Ok(u64::from_be_bytes(buf))
}

/// Format a BaseElement as a 0x-prefixed hex string of its u128 inner value.
fn element_to_hex(e: BaseElement) -> String {
    format!("0x{:032x}", e.as_int())
}

#[pyfunction]
fn poseidon_hash(inputs: Vec<u64>) -> PyResult<String> {
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
        _ => Err(pyo3::exceptions::PyValueError::new_err(
            "poseidon_hash expects 2 or 3 inputs",
        )),
    }
}

#[pyfunction]
fn compute_commitment(
    py: Python<'_>,
    value: u64,
    secret_hex: &str,
    nonce_hex: &str,
) -> PyResult<Py<PyAny>> {
    let secret = hex_to_u64(secret_hex)?;
    let nonce = hex_to_u64(nonce_hex)?;

    let v = BaseElement::new(value as u128);
    let s = BaseElement::new(secret as u128);
    let n = BaseElement::new(nonce as u128);

    let commitment = dilithia_stark::poseidon::hash3(v, s, n);

    let dict = PyDict::new(py);
    dict.set_item("commitment", element_to_hex(commitment))?;
    dict.set_item("nonce", nonce_hex)?;
    Ok(dict.into_any().unbind())
}

#[pyfunction]
fn compute_nullifier(secret_hex: &str, nonce_hex: &str) -> PyResult<String> {
    let secret = hex_to_u64(secret_hex)?;
    let nonce = hex_to_u64(nonce_hex)?;

    let s = BaseElement::new(secret as u128);
    let n = BaseElement::new(nonce as u128);

    Ok(element_to_hex(dilithia_stark::poseidon::hash2(s, n)))
}

#[pyfunction]
fn generate_preimage_proof(py: Python<'_>, values: Vec<u64>) -> PyResult<Py<PyAny>> {
    if values.len() != 3 {
        return Err(pyo3::exceptions::PyValueError::new_err(
            "generate_preimage_proof expects [value, secret, nonce]",
        ));
    }

    let (proof_bytes, commitment) =
        dilithia_stark::preimage::prove_preimage(values[0], values[1], values[2])
            .ok_or_else(|| {
                pyo3::exceptions::PyRuntimeError::new_err("preimage proof generation failed")
            })?;

    let commitment_u64 = commitment.as_int() as u64;

    let dict = PyDict::new(py);
    dict.set_item("proof", hex::encode(proof_bytes))?;
    dict.set_item("public_inputs", element_to_hex(commitment))?;
    dict.set_item(
        "verification_key",
        format!("{{\"type\":\"preimage\",\"commitment\":{commitment_u64}}}"),
    )?;
    Ok(dict.into_any().unbind())
}

#[pyfunction]
fn verify_preimage_proof(proof_hex: &str, _vk_json: &str, inputs_json: &str) -> PyResult<bool> {
    let proof_bytes = hex::decode(proof_hex)
        .map_err(|e| pyo3::exceptions::PyValueError::new_err(format!("invalid proof hex: {e}")))?;

    let commitment = parse_commitment(inputs_json)?;

    Ok(dilithia_stark::preimage::verify_preimage(
        &proof_bytes,
        commitment,
    ))
}

fn parse_commitment(inputs_json: &str) -> PyResult<BaseElement> {
    // Try parsing as a JSON string (hex) first, then as a JSON number
    if let Ok(hex_str) = serde_json::from_str::<String>(inputs_json) {
        let hex_str = hex_str.strip_prefix("0x").unwrap_or(&hex_str);
        let val = u128::from_str_radix(hex_str, 16).map_err(|e| {
            pyo3::exceptions::PyValueError::new_err(format!("invalid commitment hex: {e}"))
        })?;
        return Ok(BaseElement::new(val));
    }
    if let Ok(val) = serde_json::from_str::<u64>(inputs_json) {
        return Ok(BaseElement::new(val as u128));
    }
    Err(pyo3::exceptions::PyValueError::new_err(
        "inputs_json must be a hex string or u64 commitment value",
    ))
}

#[pyfunction]
fn generate_range_proof(py: Python<'_>, value: u64, min_val: u64, max_val: u64) -> PyResult<Py<PyAny>> {
    let (proof_bytes, min, max) =
        dilithia_stark::range::prove_range(value, min_val, max_val).ok_or_else(|| {
            pyo3::exceptions::PyRuntimeError::new_err("range proof generation failed")
        })?;

    let dict = PyDict::new(py);
    dict.set_item("proof", hex::encode(proof_bytes))?;
    dict.set_item("public_inputs", format!("[{min},{max}]"))?;
    dict.set_item(
        "verification_key",
        format!("{{\"type\":\"range\",\"min\":{min},\"max\":{max}}}"),
    )?;
    Ok(dict.into_any().unbind())
}

#[pyfunction]
fn verify_range_proof(proof_hex: &str, _vk_json: &str, inputs_json: &str) -> PyResult<bool> {
    let proof_bytes = hex::decode(proof_hex)
        .map_err(|e| pyo3::exceptions::PyValueError::new_err(format!("invalid proof hex: {e}")))?;

    let bounds: Vec<u64> = serde_json::from_str(inputs_json).map_err(|e| {
        pyo3::exceptions::PyValueError::new_err(format!("invalid inputs json: {e}"))
    })?;

    if bounds.len() != 2 {
        return Err(pyo3::exceptions::PyValueError::new_err(
            "inputs_json must be [min, max]",
        ));
    }

    Ok(dilithia_stark::range::verify_range(
        &proof_bytes,
        bounds[0],
        bounds[1],
    ))
}

#[pymodule]
fn dilithia_sdk_zk(_py: Python<'_>, module: &Bound<'_, PyModule>) -> PyResult<()> {
    module.add_function(wrap_pyfunction!(poseidon_hash, module)?)?;
    module.add_function(wrap_pyfunction!(compute_commitment, module)?)?;
    module.add_function(wrap_pyfunction!(compute_nullifier, module)?)?;
    module.add_function(wrap_pyfunction!(generate_preimage_proof, module)?)?;
    module.add_function(wrap_pyfunction!(verify_preimage_proof, module)?)?;
    module.add_function(wrap_pyfunction!(generate_range_proof, module)?)?;
    module.add_function(wrap_pyfunction!(verify_range_proof, module)?)?;
    Ok(())
}
