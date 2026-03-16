use pyo3::exceptions::PyRuntimeError;
use pyo3::prelude::*;
use pyo3::types::PyDict;
use dilithia_core::crypto;
use dilithia_core::wallet::{self, WalletFile};

fn wallet_file_to_dict(py: Python<'_>, wallet_file: WalletFile) -> PyResult<PyObject> {
    let wallet_dict = PyDict::new_bound(py);
    wallet_dict.set_item("version", wallet_file.version)?;
    wallet_dict.set_item("address", wallet_file.address)?;
    wallet_dict.set_item("public_key", wallet_file.public_key)?;
    wallet_dict.set_item("encrypted_sk", wallet_file.encrypted_sk)?;
    wallet_dict.set_item("nonce", wallet_file.nonce)?;
    wallet_dict.set_item("tag", wallet_file.tag)?;
    wallet_dict.set_item("account_index", wallet_file.account_index)?;
    Ok(wallet_dict.into())
}

#[pyfunction]
fn sdk_version() -> String {
    "0.3.0".to_string()
}

#[pyfunction]
fn rpc_line_version() -> String {
    "0.3.0".to_string()
}

#[pyfunction]
fn generate_mnemonic() -> PyResult<String> {
    wallet::generate_mnemonic().map_err(PyRuntimeError::new_err)
}

#[pyfunction]
fn validate_mnemonic(mnemonic: &str) -> PyResult<()> {
    wallet::validate_mnemonic(mnemonic).map_err(PyRuntimeError::new_err)
}

#[pyfunction]
fn create_wallet_file(py: Python<'_>, password: &str) -> PyResult<PyObject> {
    let (mnemonic, wallet_file) = wallet::create_wallet(password).map_err(PyRuntimeError::new_err)?;
    let result = PyDict::new_bound(py);
    result.set_item("mnemonic", mnemonic)?;
    result.set_item("wallet_file", wallet_file_to_dict(py, wallet_file)?)?;
    Ok(result.into())
}

#[pyfunction]
fn create_hd_wallet_account_from_mnemonic(
    py: Python<'_>,
    mnemonic: &str,
    password: &str,
    account_index: u32,
) -> PyResult<PyObject> {
    wallet::validate_mnemonic(mnemonic).map_err(PyRuntimeError::new_err)?;
    let normalized = mnemonic.trim().to_lowercase();
    let wallet_file =
        wallet::create_hd_wallet_account(&normalized, account_index, password).map_err(PyRuntimeError::new_err)?;
    let (secret_key, public_key, address) = wallet::recover_hd_account(&normalized, account_index);

    let result = PyDict::new_bound(py);
    result.set_item("address", address)?;
    result.set_item("public_key", hex::encode(public_key))?;
    result.set_item("secret_key", hex::encode(secret_key))?;
    result.set_item("account_index", account_index)?;
    result.set_item("wallet_file", wallet_file_to_dict(py, wallet_file)?)?;
    Ok(result.into())
}

#[pyfunction]
fn create_hd_wallet_file_from_mnemonic(
    py: Python<'_>,
    mnemonic: &str,
    password: &str,
) -> PyResult<PyObject> {
    create_hd_wallet_account_from_mnemonic(py, mnemonic, password, 0)
}

#[pyfunction]
#[pyo3(signature = (version, address, public_key, encrypted_sk, nonce, tag, mnemonic, password, account_index=None))]
fn recover_wallet_file(
    py: Python<'_>,
    version: u8,
    address: &str,
    public_key: &str,
    encrypted_sk: &str,
    nonce: &str,
    tag: &str,
    mnemonic: &str,
    password: &str,
    account_index: Option<u32>,
) -> PyResult<PyObject> {
    let wallet_file = WalletFile {
        version,
        address: address.to_string(),
        public_key: public_key.to_string(),
        encrypted_sk: encrypted_sk.to_string(),
        nonce: nonce.to_string(),
        tag: tag.to_string(),
        account_index,
    };
    let normalized = mnemonic.trim().to_lowercase();
    let (secret_key, recovered_address) = if wallet_file.account_index.is_some() {
        wallet::recover_hd_wallet_account(&wallet_file, &normalized, password)
            .map_err(PyRuntimeError::new_err)?
    } else {
        wallet::recover_wallet(&wallet_file, &normalized, password).map_err(PyRuntimeError::new_err)?
    };
    let result = PyDict::new_bound(py);
    result.set_item("address", recovered_address)?;
    result.set_item("public_key", wallet_file.public_key.clone())?;
    result.set_item("secret_key", hex::encode(secret_key))?;
    result.set_item("account_index", wallet_file.account_index.unwrap_or(0))?;
    result.set_item("wallet_file", wallet_file_to_dict(py, wallet_file)?)?;
    Ok(result.into())
}

#[pyfunction]
fn address_from_public_key(public_key_hex: &str) -> PyResult<String> {
    let public_key = hex::decode(public_key_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    crypto::validate_pk(&public_key).map_err(PyRuntimeError::new_err)?;
    Ok(crypto::address_from_pk(&public_key))
}

#[pyfunction]
fn recover_hd_wallet(py: Python<'_>, mnemonic: &str) -> PyResult<PyObject> {
    recover_hd_wallet_account(py, mnemonic, 0)
}

#[pyfunction]
fn recover_hd_wallet_account(py: Python<'_>, mnemonic: &str, account_index: u32) -> PyResult<PyObject> {
    wallet::validate_mnemonic(mnemonic).map_err(PyRuntimeError::new_err)?;
    let normalized = mnemonic.trim().to_lowercase();
    let (secret_key, public_key, address) = wallet::recover_hd_account(&normalized, account_index);
    let result = PyDict::new_bound(py);
    result.set_item("address", address)?;
    result.set_item("public_key", hex::encode(public_key))?;
    result.set_item("secret_key", hex::encode(secret_key))?;
    result.set_item("account_index", account_index)?;
    Ok(result.into())
}

#[pyfunction]
fn sign_message(secret_key_hex: &str, message: &str) -> PyResult<PyObject> {
    let secret_key = hex::decode(secret_key_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    let signature =
        crypto::sign_mldsa65(message.as_bytes(), &secret_key).map_err(PyRuntimeError::new_err)?;
    Python::with_gil(|py| {
        let result = PyDict::new_bound(py);
        result.set_item("algorithm", "mldsa65")?;
        result.set_item("signature", hex::encode(signature))?;
        Ok(result.into())
    })
}

#[pyfunction]
fn verify_message(public_key_hex: &str, message: &str, signature_hex: &str) -> PyResult<bool> {
    let public_key = hex::decode(public_key_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    let signature = hex::decode(signature_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    Ok(crypto::verify_mldsa65(message.as_bytes(), &signature, &public_key))
}

#[pymodule]
fn dilithia_sdk_native(_py: Python<'_>, module: &Bound<'_, PyModule>) -> PyResult<()> {
    module.add_function(wrap_pyfunction!(sdk_version, module)?)?;
    module.add_function(wrap_pyfunction!(rpc_line_version, module)?)?;
    module.add_function(wrap_pyfunction!(generate_mnemonic, module)?)?;
    module.add_function(wrap_pyfunction!(validate_mnemonic, module)?)?;
    module.add_function(wrap_pyfunction!(create_wallet_file, module)?)?;
    module.add_function(wrap_pyfunction!(create_hd_wallet_file_from_mnemonic, module)?)?;
    module.add_function(wrap_pyfunction!(create_hd_wallet_account_from_mnemonic, module)?)?;
    module.add_function(wrap_pyfunction!(recover_wallet_file, module)?)?;
    module.add_function(wrap_pyfunction!(address_from_public_key, module)?)?;
    module.add_function(wrap_pyfunction!(recover_hd_wallet, module)?)?;
    module.add_function(wrap_pyfunction!(recover_hd_wallet_account, module)?)?;
    module.add_function(wrap_pyfunction!(sign_message, module)?)?;
    module.add_function(wrap_pyfunction!(verify_message, module)?)?;
    Ok(())
}
