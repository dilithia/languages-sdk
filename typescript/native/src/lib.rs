use napi::bindgen_prelude::*;
use napi_derive::napi;
use dilithia_core::crypto;
use dilithia_core::wallet::{self, WalletFile};

#[napi(object)]
pub struct WalletFileData {
    pub version: u8,
    pub address: String,
    pub public_key: String,
    pub encrypted_sk: String,
    pub nonce: String,
    pub tag: String,
    pub account_index: Option<u32>,
}

#[napi(object)]
pub struct WalletAccount {
    pub address: String,
    pub public_key: String,
    pub secret_key: String,
    pub account_index: u32,
    pub wallet_file: Option<WalletFileData>,
}

#[napi(object)]
pub struct SignatureResult {
    pub algorithm: String,
    pub signature: String,
}

#[napi(object)]
pub struct WalletCreationResult {
    pub mnemonic: String,
    pub wallet_file: WalletFileData,
}

fn into_wallet_file_data(wallet_file: WalletFile) -> WalletFileData {
    WalletFileData {
        version: wallet_file.version,
        address: wallet_file.address,
        public_key: wallet_file.public_key,
        encrypted_sk: wallet_file.encrypted_sk,
        nonce: wallet_file.nonce,
        tag: wallet_file.tag,
        account_index: wallet_file.account_index,
    }
}

fn into_wallet_account(
    address: String,
    public_key: String,
    secret_key: Vec<u8>,
    account_index: u32,
    wallet_file: Option<WalletFileData>,
) -> WalletAccount {
    WalletAccount {
        address,
        public_key,
        secret_key: hex::encode(secret_key),
        account_index,
        wallet_file,
    }
}

#[napi]
pub fn sdk_version() -> String {
    "0.3.0".to_string()
}

#[napi]
pub fn rpc_line_version() -> String {
    "0.3.0".to_string()
}

#[napi]
pub fn generate_mnemonic() -> Result<String> {
    wallet::generate_mnemonic().map_err(Error::from_reason)
}

#[napi]
pub fn validate_mnemonic(mnemonic: String) -> Result<()> {
    wallet::validate_mnemonic(&mnemonic).map_err(Error::from_reason)
}

#[napi]
pub fn create_wallet_file(password: String) -> Result<WalletCreationResult> {
    let (mnemonic, wallet_file) = wallet::create_wallet(&password).map_err(Error::from_reason)?;
    Ok(WalletCreationResult {
        mnemonic,
        wallet_file: into_wallet_file_data(wallet_file),
    })
}

#[napi]
pub fn create_hd_wallet_account_from_mnemonic(
    mnemonic: String,
    password: String,
    account_index: u32,
) -> Result<WalletAccount> {
    wallet::validate_mnemonic(&mnemonic).map_err(Error::from_reason)?;
    let normalized = mnemonic.trim().to_lowercase();
    let wallet_file =
        wallet::create_hd_wallet_account(&normalized, account_index, &password).map_err(Error::from_reason)?;
    let (secret_key, public_key, address) = wallet::recover_hd_account(&normalized, account_index);
    Ok(into_wallet_account(
        address,
        hex::encode(public_key),
        secret_key,
        account_index,
        Some(into_wallet_file_data(wallet_file)),
    ))
}

#[napi]
pub fn create_hd_wallet_file_from_mnemonic(
    mnemonic: String,
    password: String,
) -> Result<WalletAccount> {
    create_hd_wallet_account_from_mnemonic(mnemonic, password, 0)
}

#[napi]
pub fn recover_wallet_file(
    version: u8,
    address: String,
    public_key: String,
    encrypted_sk: String,
    nonce: String,
    tag: String,
    account_index: Option<u32>,
    mnemonic: String,
    password: String,
) -> Result<WalletAccount> {
    let wallet_file = WalletFile {
        version,
        address,
        public_key,
        encrypted_sk,
        nonce,
        tag,
        account_index,
    };

    let normalized = mnemonic.trim().to_lowercase();
    let (secret_key, address) = if wallet_file.account_index.is_some() {
        wallet::recover_hd_wallet_account(&wallet_file, &normalized, &password)
            .map_err(Error::from_reason)?
    } else {
        wallet::recover_wallet(&wallet_file, &normalized, &password).map_err(Error::from_reason)?
    };

    let account_index = wallet_file.account_index.unwrap_or(0);
    Ok(into_wallet_account(
        address,
        wallet_file.public_key.clone(),
        secret_key,
        account_index,
        Some(into_wallet_file_data(wallet_file)),
    ))
}

#[napi]
pub fn address_from_public_key(public_key_hex: String) -> Result<String> {
    let pk_bytes = hex::decode(&public_key_hex).map_err(|error| Error::from_reason(error.to_string()))?;
    crypto::validate_pk(&pk_bytes).map_err(Error::from_reason)?;
    Ok(crypto::address_from_pk(&pk_bytes))
}

#[napi]
pub fn recover_hd_wallet(mnemonic: String) -> Result<WalletAccount> {
    recover_hd_wallet_account(mnemonic, 0)
}

#[napi]
pub fn recover_hd_wallet_account(mnemonic: String, account_index: u32) -> Result<WalletAccount> {
    wallet::validate_mnemonic(&mnemonic).map_err(Error::from_reason)?;
    let normalized = mnemonic.trim().to_lowercase();
    let (secret_key, public_key, address) = wallet::recover_hd_account(&normalized, account_index);
    Ok(into_wallet_account(
        address,
        hex::encode(public_key),
        secret_key,
        account_index,
        None,
    ))
}

#[napi]
pub fn sign_message(secret_key_hex: String, message: String) -> Result<SignatureResult> {
    let secret_key = hex::decode(&secret_key_hex).map_err(|error| Error::from_reason(error.to_string()))?;
    let signature = crypto::sign_mldsa65(message.as_bytes(), &secret_key).map_err(Error::from_reason)?;
    Ok(SignatureResult {
        algorithm: "mldsa65".to_string(),
        signature: hex::encode(signature),
    })
}

#[napi]
pub fn verify_message(public_key_hex: String, message: String, signature_hex: String) -> Result<bool> {
    let public_key = hex::decode(&public_key_hex).map_err(|error| Error::from_reason(error.to_string()))?;
    let signature = hex::decode(&signature_hex).map_err(|error| Error::from_reason(error.to_string()))?;
    Ok(crypto::verify_mldsa65(message.as_bytes(), &signature, &public_key))
}
