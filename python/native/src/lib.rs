use pyo3::exceptions::PyRuntimeError;
use pyo3::prelude::*;
use pyo3::types::PyDict;
use dilithia_core::crypto;
use dilithia_core::hash;
use dilithia_core::hash::HashAlg;
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
    "0.2.0".to_string()
}

#[pyfunction]
fn rpc_line_version() -> String {
    "0.2.0".to_string()
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

#[pyfunction]
fn validate_address(addr: &str) -> PyResult<String> {
    crypto::validate_address(addr).map_err(PyRuntimeError::new_err)
}

#[pyfunction]
fn address_from_pk_checksummed(public_key_hex: &str) -> PyResult<String> {
    let public_key = hex::decode(public_key_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    crypto::validate_pk(&public_key).map_err(PyRuntimeError::new_err)?;
    Ok(crypto::address_from_pk_checksummed(&public_key))
}

#[pyfunction]
fn address_with_checksum(raw_addr: &str) -> PyResult<String> {
    crypto::address_with_checksum(raw_addr).map_err(PyRuntimeError::new_err)
}

#[pyfunction]
fn validate_public_key(public_key_hex: &str) -> PyResult<()> {
    let public_key = hex::decode(public_key_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    crypto::validate_pk(&public_key).map_err(PyRuntimeError::new_err)
}

#[pyfunction]
fn validate_secret_key(secret_key_hex: &str) -> PyResult<()> {
    let secret_key = hex::decode(secret_key_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    crypto::validate_sk(&secret_key).map_err(PyRuntimeError::new_err)
}

#[pyfunction]
fn validate_signature(signature_hex: &str) -> PyResult<()> {
    let signature = hex::decode(signature_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    crypto::validate_sig(&signature).map_err(PyRuntimeError::new_err)
}

#[pyfunction]
fn keygen(py: Python<'_>) -> PyResult<PyObject> {
    let (secret_key, public_key, address) =
        crypto::keygen_mldsa65_secure().map_err(PyRuntimeError::new_err)?;
    let result = PyDict::new_bound(py);
    result.set_item("secret_key", hex::encode(&*secret_key))?;
    result.set_item("public_key", hex::encode(public_key))?;
    result.set_item("address", address)?;
    Ok(result.into())
}

#[pyfunction]
fn keygen_from_seed(py: Python<'_>, seed_hex: &str) -> PyResult<PyObject> {
    let seed = hex::decode(seed_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    if seed.len() != 32 {
        return Err(PyRuntimeError::new_err("Seed must be exactly 32 bytes"));
    }
    let mut seed_array = [0u8; 32];
    seed_array.copy_from_slice(&seed);
    let (secret_key, public_key, address) =
        crypto::keygen_mldsa65_from_seed(&seed_array).map_err(PyRuntimeError::new_err)?;
    let result = PyDict::new_bound(py);
    result.set_item("secret_key", hex::encode(secret_key))?;
    result.set_item("public_key", hex::encode(public_key))?;
    result.set_item("address", address)?;
    Ok(result.into())
}

#[pyfunction]
fn seed_from_mnemonic(mnemonic: &str) -> PyResult<String> {
    let seed = crypto::seed_from_mnemonic(mnemonic).map_err(PyRuntimeError::new_err)?;
    Ok(hex::encode(seed))
}

#[pyfunction]
fn derive_child_seed(parent_seed_hex: &str, index: u32) -> PyResult<String> {
    let parent_seed =
        hex::decode(parent_seed_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    let child_seed = crypto::derive_child_seed(&parent_seed, index).map_err(PyRuntimeError::new_err)?;
    Ok(hex::encode(child_seed))
}

#[pyfunction]
fn constant_time_eq(a_hex: &str, b_hex: &str) -> PyResult<bool> {
    let a = hex::decode(a_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    let b = hex::decode(b_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    Ok(crypto::constant_time_eq(&a, &b))
}

#[pyfunction]
fn hash_hex(data_hex: &str) -> PyResult<String> {
    let data = hex::decode(data_hex).map_err(|error| PyRuntimeError::new_err(error.to_string()))?;
    Ok(hash::hash_hex(&data))
}

#[pyfunction]
fn set_hash_alg(alg: &str) -> PyResult<()> {
    let hash_alg = match alg.to_lowercase().as_str() {
        "blake3" => HashAlg::Blake3,
        "sha3_256" | "sha3-256" | "sha3256" => HashAlg::Sha3_256,
        "sha3_512" | "sha3-512" | "sha3512" => HashAlg::Sha3_512,
        _ => return Err(PyRuntimeError::new_err(format!("Unknown hash algorithm: {}", alg))),
    };
    hash::set_hash_alg(hash_alg);
    Ok(())
}

#[pyfunction]
fn current_hash_alg() -> String {
    format!("{:?}", hash::current_hash_alg())
}

#[pyfunction]
fn hash_len_hex() -> usize {
    hash::hash_len_hex()
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
    module.add_function(wrap_pyfunction!(validate_address, module)?)?;
    module.add_function(wrap_pyfunction!(address_from_pk_checksummed, module)?)?;
    module.add_function(wrap_pyfunction!(address_with_checksum, module)?)?;
    module.add_function(wrap_pyfunction!(validate_public_key, module)?)?;
    module.add_function(wrap_pyfunction!(validate_secret_key, module)?)?;
    module.add_function(wrap_pyfunction!(validate_signature, module)?)?;
    module.add_function(wrap_pyfunction!(keygen, module)?)?;
    module.add_function(wrap_pyfunction!(keygen_from_seed, module)?)?;
    module.add_function(wrap_pyfunction!(seed_from_mnemonic, module)?)?;
    module.add_function(wrap_pyfunction!(derive_child_seed, module)?)?;
    module.add_function(wrap_pyfunction!(constant_time_eq, module)?)?;
    module.add_function(wrap_pyfunction!(hash_hex, module)?)?;
    module.add_function(wrap_pyfunction!(set_hash_alg, module)?)?;
    module.add_function(wrap_pyfunction!(current_hash_alg, module)?)?;
    module.add_function(wrap_pyfunction!(hash_len_hex, module)?)?;
    Ok(())
}
