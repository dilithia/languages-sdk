//! STARK native bridge — requires dilithia-core with feature "stark".
//! This package will not compile until dilithia-core publishes the stark feature.
//! See: https://github.com/dilithia/languages-sdk/issues/TBD

use pyo3::prelude::*;
use pyo3::types::PyDict;

#[pyfunction]
fn poseidon_hash(_inputs: Vec<u64>) -> PyResult<String> {
    todo!()
}

#[pyfunction]
fn compute_commitment(_py: Python<'_>, _value: u64, _secret_hex: &str, _nonce_hex: &str) -> PyResult<PyObject> {
    todo!()
}

#[pyfunction]
fn compute_nullifier(_secret_hex: &str, _nonce_hex: &str) -> PyResult<String> {
    todo!()
}

#[pyfunction]
fn generate_preimage_proof(_py: Python<'_>, _values: Vec<u64>) -> PyResult<PyObject> {
    todo!()
}

#[pyfunction]
fn verify_preimage_proof(_proof_hex: &str, _vk_json: &str, _inputs_json: &str) -> PyResult<bool> {
    todo!()
}

#[pyfunction]
fn generate_range_proof(_py: Python<'_>, _value: u64, _min_val: u64, _max_val: u64) -> PyResult<PyObject> {
    todo!()
}

#[pyfunction]
fn verify_range_proof(_proof_hex: &str, _vk_json: &str, _inputs_json: &str) -> PyResult<bool> {
    todo!()
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
