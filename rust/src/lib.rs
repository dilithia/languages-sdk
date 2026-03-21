use dilithia_core::hash;
use dilithia_core::hash::HashAlg;
use serde_json::{json, Value};
use std::fmt::{Display, Formatter};
use std::path::Path;

pub const SDK_VERSION: &str = "0.2.0";
pub const RPC_LINE_VERSION: &str = "0.2.0";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiaAccount {
    pub address: String,
    pub public_key: String,
    pub secret_key: String,
    pub account_index: u32,
    pub wallet_file: Option<Value>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiaKeypair {
    pub secret_key: String,
    pub public_key: String,
    pub address: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiaSignature {
    pub algorithm: String,
    pub signature: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DeployPayload {
    pub name: String,
    pub bytecode: String,
    pub from: String,
    pub alg: String,
    pub pk: String,
    pub sig: String,
    pub nonce: u64,
    pub chain_id: String,
    pub version: u8,
}

/// STARK proof bytes (serialized winterfell proof).
pub type StarkProof = Vec<u8>;

/// A commitment to a shielded value: Poseidon(value || secret || nonce).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Commitment {
    pub hash: String,          // hex-encoded commitment hash
    pub value: u64,            // deposited amount
    pub secret: String,        // hex-encoded 32-byte secret
    pub nonce: String,         // hex-encoded 32-byte nonce
}

/// A nullifier proving a commitment was spent: Poseidon(secret || nonce).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Nullifier {
    pub hash: String,          // hex-encoded nullifier hash
}

/// Result of a shielded deposit.
#[derive(Debug, Clone)]
pub struct ShieldedDepositResult {
    pub commitment: String,
    pub tx_hash: String,
}

/// Result of a shielded withdrawal.
#[derive(Debug, Clone)]
pub struct ShieldedWithdrawResult {
    pub nullifier: String,
    pub tx_hash: String,
}

/// Compliance proof type.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ComplianceType {
    NotOnSanctions,
    TaxPaid,
    BalanceRange,
}

pub trait DilithiaCryptoAdapter {
    fn generate_mnemonic(&self) -> Result<String, String>;
    fn validate_mnemonic(&self, mnemonic: &str) -> Result<(), String>;
    fn recover_hd_wallet(&self, mnemonic: &str) -> Result<DilithiaAccount, String>;
    fn recover_hd_wallet_account(
        &self,
        mnemonic: &str,
        account_index: u32,
    ) -> Result<DilithiaAccount, String>;
    fn create_hd_wallet_file_from_mnemonic(
        &self,
        mnemonic: &str,
        password: &str,
    ) -> Result<DilithiaAccount, String>;
    fn create_hd_wallet_account_from_mnemonic(
        &self,
        mnemonic: &str,
        password: &str,
        account_index: u32,
    ) -> Result<DilithiaAccount, String>;
    fn recover_wallet_file(
        &self,
        wallet_file: &dilithia_core::wallet::WalletFile,
        mnemonic: &str,
        password: &str,
    ) -> Result<DilithiaAccount, String>;
    fn address_from_public_key(&self, public_key_hex: &str) -> Result<String, String>;
    fn sign_message(&self, secret_key_hex: &str, message: &str) -> Result<DilithiaSignature, String>;
    fn verify_message(
        &self,
        public_key_hex: &str,
        message: &str,
        signature_hex: &str,
    ) -> Result<bool, String>;
    fn validate_address(&self, addr: &str) -> Result<String, String>;
    fn address_from_pk_checksummed(&self, public_key_hex: &str) -> Result<String, String>;
    fn address_with_checksum(&self, raw_addr: &str) -> Result<String, String>;
    fn validate_public_key(&self, public_key_hex: &str) -> Result<(), String>;
    fn validate_secret_key(&self, secret_key_hex: &str) -> Result<(), String>;
    fn validate_signature(&self, signature_hex: &str) -> Result<(), String>;
    fn keygen(&self) -> Result<DilithiaKeypair, String>;
    fn keygen_from_seed(&self, seed_hex: &str) -> Result<DilithiaKeypair, String>;
    fn seed_from_mnemonic(&self, mnemonic: &str) -> Result<String, String>;
    fn derive_child_seed(&self, parent_seed_hex: &str, index: u32) -> Result<String, String>;
    fn constant_time_eq(&self, a_hex: &str, b_hex: &str) -> Result<bool, String>;
    fn hash_hex(&self, data_hex: &str) -> Result<String, String>;
    fn set_hash_alg(&self, alg: &str) -> Result<(), String>;
    fn current_hash_alg(&self) -> String;
    fn hash_len_hex(&self) -> usize;
}

/// Post-quantum ZK adapter using STARKs (winterfell).
/// Requires `dilithia-core` with feature `stark`.
pub trait DilithiaZkAdapter {
    /// Compute a Poseidon hash over field elements.
    fn poseidon_hash(&self, inputs: &[u64]) -> Result<String, String>;

    /// Compute a shielded commitment: Poseidon(value || secret || nonce).
    fn compute_commitment(&self, value: u64, secret_hex: &str, nonce_hex: &str) -> Result<Commitment, String>;

    /// Compute a nullifier: Poseidon(secret || nonce).
    fn compute_nullifier(&self, secret_hex: &str, nonce_hex: &str) -> Result<Nullifier, String>;

    /// Generate a STARK preimage proof (prove knowledge of inputs that hash to output).
    fn generate_preimage_proof(&self, values: &[u64]) -> Result<(StarkProof, String, String), String>;

    /// Verify a STARK preimage proof.
    fn verify_preimage_proof(&self, proof: &[u8], vk_json: &str, inputs_json: &str) -> Result<bool, String>;

    /// Generate a STARK range proof (prove value ∈ [min, max] without revealing value).
    fn generate_range_proof(&self, value: u64, min: u64, max: u64) -> Result<(StarkProof, String, String), String>;

    /// Verify a STARK range proof.
    fn verify_range_proof(&self, proof: &[u8], vk_json: &str, inputs_json: &str) -> Result<bool, String>;
}

#[derive(Debug, Default, Clone, Copy)]
pub struct NativeCryptoAdapter;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiaClient {
    rpc_url: String,
    base_url: String,
    indexer_url: Option<String>,
    oracle_url: Option<String>,
    ws_url: Option<String>,
    jwt: Option<String>,
    headers: Vec<(String, String)>,
    timeout_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct DilithiaClientConfig {
    pub rpc_url: String,
    pub chain_base_url: Option<String>,
    pub indexer_url: Option<String>,
    pub oracle_url: Option<String>,
    pub ws_url: Option<String>,
    pub jwt: Option<String>,
    pub headers: Vec<(String, String)>,
    pub timeout_ms: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DilithiaRequest {
    Get { path: String },
    Post { path: String, body: Value },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DilithiaError {
    InvalidRpcUrl,
}

impl Display for DilithiaError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidRpcUrl => write!(f, "invalid RPC URL"),
        }
    }
}

impl std::error::Error for DilithiaError {}

fn derive_ws_url(explicit_base: &str, rpc_url: &str, base_url: &str) -> Option<String> {
    let source = if !explicit_base.is_empty() { explicit_base } else if !base_url.is_empty() { base_url } else { rpc_url };
    if let Some(rest) = source.strip_prefix("https://") {
        return Some(format!("wss://{}", rest.trim_end_matches('/')));
    }
    if let Some(rest) = source.strip_prefix("http://") {
        return Some(format!("ws://{}", rest.trim_end_matches('/')));
    }
    None
}

impl DilithiaClient {
    pub fn new(rpc_url: impl Into<String>, timeout_ms: Option<u64>) -> Result<Self, DilithiaError> {
        Self::from_config(DilithiaClientConfig {
            rpc_url: rpc_url.into(),
            timeout_ms,
            ..DilithiaClientConfig::default()
        })
    }

    pub fn from_config(config: DilithiaClientConfig) -> Result<Self, DilithiaError> {
        let DilithiaClientConfig {
            rpc_url,
            chain_base_url,
            indexer_url,
            oracle_url,
            ws_url,
            jwt,
            headers,
            timeout_ms,
        } = config;
        let rpc_url = rpc_url.trim_end_matches('/').to_string();
        if rpc_url.is_empty() {
            return Err(DilithiaError::InvalidRpcUrl);
        }
        let base_url = chain_base_url
            .clone()
            .unwrap_or_else(|| rpc_url.trim_end_matches("/rpc").to_string())
            .trim_end_matches('/')
            .to_string();
        let derived_ws_url = derive_ws_url(chain_base_url.as_deref().unwrap_or(""), &rpc_url, &base_url);
        Ok(Self {
            rpc_url,
            base_url,
            indexer_url: indexer_url.map(|v| v.trim_end_matches('/').to_string()),
            oracle_url: oracle_url.map(|v| v.trim_end_matches('/').to_string()),
            ws_url: ws_url.or(derived_ws_url),
            jwt,
            headers,
            timeout_ms: timeout_ms.unwrap_or(10_000),
        })
    }

    pub fn rpc_url(&self) -> &str {
        &self.rpc_url
    }

    pub fn timeout_ms(&self) -> u64 {
        self.timeout_ms
    }

    pub fn base_url(&self) -> &str {
        &self.base_url
    }

    pub fn indexer_url(&self) -> Option<&str> {
        self.indexer_url.as_deref()
    }

    pub fn oracle_url(&self) -> Option<&str> {
        self.oracle_url.as_deref()
    }

    pub fn ws_url(&self) -> Option<&str> {
        self.ws_url.as_deref()
    }

    pub fn build_auth_headers(&self, extra: Vec<(String, String)>) -> Vec<(String, String)> {
        let mut headers = Vec::new();
        if let Some(jwt) = &self.jwt {
            headers.push(("Authorization".to_string(), format!("Bearer {jwt}")));
        }
        headers.extend(self.headers.iter().cloned());
        headers.extend(extra);
        headers
    }

    pub fn ws_connection_info(&self) -> Value {
        json!({
            "url": self.ws_url,
            "headers": self.build_auth_headers(Vec::new())
                .into_iter()
                .map(|(k, v)| (k, Value::String(v)))
                .collect::<serde_json::Map<String, Value>>()
        })
    }

    pub fn get_balance_request(&self, address: &str) -> DilithiaRequest {
        DilithiaRequest::Get {
            path: format!("{}/balance/{}", self.rpc_url, address),
        }
    }

    pub fn get_nonce_request(&self, address: &str) -> DilithiaRequest {
        DilithiaRequest::Get {
            path: format!("{}/nonce/{}", self.rpc_url, address),
        }
    }

    pub fn get_receipt_request(&self, tx_hash: &str) -> DilithiaRequest {
        DilithiaRequest::Get {
            path: format!("{}/receipt/{}", self.rpc_url, tx_hash),
        }
    }

    pub fn get_address_summary_request(&self, address: &str) -> DilithiaRequest {
        DilithiaRequest::Post {
            path: self.rpc_url.clone(),
            body: json!({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_addressSummary",
                "params": { "address": address }
            }),
        }
    }

    pub fn get_gas_estimate_request(&self) -> DilithiaRequest {
        DilithiaRequest::Post {
            path: self.rpc_url.clone(),
            body: json!({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_gasEstimate",
                "params": {}
            }),
        }
    }

    pub fn get_base_fee_request(&self) -> DilithiaRequest {
        DilithiaRequest::Post {
            path: self.rpc_url.clone(),
            body: json!({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_baseFee",
                "params": {}
            }),
        }
    }

    pub fn resolve_name_request(&self, name: &str) -> DilithiaRequest {
        DilithiaRequest::Get {
            path: format!("{}/names/resolve/{}", self.base_url, name),
        }
    }

    pub fn reverse_resolve_name_request(&self, address: &str) -> DilithiaRequest {
        DilithiaRequest::Get {
            path: format!("{}/names/reverse/{}", self.base_url, address),
        }
    }

    pub fn query_contract_request(&self, contract: &str, method: &str, args: Value) -> DilithiaRequest {
        let args_json = args.to_string();
        let encoded_args = urlencoding::encode(&args_json);
        DilithiaRequest::Get {
            path: format!(
                "{}/query?contract={}&method={}&args={}",
                self.base_url,
                urlencoding::encode(contract),
                urlencoding::encode(method),
                encoded_args
            ),
        }
    }

    pub fn build_jsonrpc_request(&self, method: &str, params: Value, id: u64) -> Value {
        json!({
            "jsonrpc": "2.0",
            "id": if id == 0 { 1 } else { id },
            "method": method,
            "params": params,
        })
    }

    pub fn build_ws_request(&self, method: &str, params: Value, id: u64) -> Value {
        self.build_jsonrpc_request(method, params, id)
    }

    pub fn simulate_request(&self, call: Value) -> DilithiaRequest {
        DilithiaRequest::Post {
            path: format!("{}/simulate", self.rpc_url),
            body: call,
        }
    }

    pub fn send_call_request(&self, call: Value) -> DilithiaRequest {
        DilithiaRequest::Post {
            path: format!("{}/call", self.rpc_url),
            body: call,
        }
    }

    pub fn with_paymaster(&self, mut call: Value, paymaster: &str) -> Value {
        if let Some(object) = call.as_object_mut() {
            object.insert("paymaster".to_string(), Value::String(paymaster.to_string()));
        }
        call
    }

    pub fn build_contract_call(
        &self,
        contract: &str,
        method: &str,
        args: Value,
        paymaster: Option<&str>,
    ) -> Value {
        let call = json!({
            "contract": contract,
            "method": method,
            "args": args,
        });
        paymaster.map_or(call.clone(), |p| self.with_paymaster(call, p))
    }

    pub fn build_forwarder_call(&self, contract: &str, args: Value, paymaster: Option<&str>) -> Value {
        self.build_contract_call(contract, "forward", args, paymaster)
    }

    pub fn build_deploy_canonical_payload(
        from: &str,
        name: &str,
        bytecode_hex: &str,
        nonce: u64,
        chain_id: &str,
    ) -> Value {
        let bytecode_hash = hash::hash_hex(bytecode_hex.as_bytes());
        json!({
            "bytecode_hash": bytecode_hash,
            "chain_id": chain_id,
            "from": from,
            "name": name,
            "nonce": nonce,
        })
    }

    pub fn deploy_contract_request(&self, payload: &DeployPayload) -> DilithiaRequest {
        DilithiaRequest::Post {
            path: format!("{}/deploy", self.base_url),
            body: json!({
                "name": payload.name,
                "bytecode": payload.bytecode,
                "from": payload.from,
                "alg": payload.alg,
                "pk": payload.pk,
                "sig": payload.sig,
                "nonce": payload.nonce,
                "chain_id": payload.chain_id,
                "version": payload.version,
            }),
        }
    }

    pub fn upgrade_contract_request(&self, payload: &DeployPayload) -> DilithiaRequest {
        DilithiaRequest::Post {
            path: format!("{}/upgrade", self.base_url),
            body: json!({
                "name": payload.name,
                "bytecode": payload.bytecode,
                "from": payload.from,
                "alg": payload.alg,
                "pk": payload.pk,
                "sig": payload.sig,
                "nonce": payload.nonce,
                "chain_id": payload.chain_id,
                "version": payload.version,
            }),
        }
    }

    pub fn query_contract_abi_request(&self, contract: &str) -> DilithiaRequest {
        DilithiaRequest::Post {
            path: self.rpc_url.clone(),
            body: json!({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_getAbi",
                "params": { "contract": contract }
            }),
        }
    }

    // ── Shielded pool methods ────────────────────────────────────────

    pub fn shielded_deposit_request(&self, commitment: &str, value: u64, proof_hex: &str) -> DilithiaRequest {
        let call = self.build_contract_call(
            "shielded",
            "deposit",
            json!({
                "commitment": commitment,
                "value": value,
                "proof": proof_hex,
            }),
            None,
        );
        self.send_call_request(call)
    }

    pub fn shielded_withdraw_request(
        &self,
        nullifier: &str,
        amount: u64,
        recipient: &str,
        proof_hex: &str,
        commitment_root: &str,
    ) -> DilithiaRequest {
        let call = self.build_contract_call(
            "shielded",
            "withdraw",
            json!({
                "nullifier": nullifier,
                "amount": amount,
                "recipient": recipient,
                "proof": proof_hex,
                "commitment_root": commitment_root,
            }),
            None,
        );
        self.send_call_request(call)
    }

    pub fn get_commitment_root_request(&self) -> DilithiaRequest {
        let call = self.build_contract_call(
            "shielded",
            "commitment_root",
            json!({}),
            None,
        );
        self.send_call_request(call)
    }

    pub fn is_nullifier_spent_request(&self, nullifier: &str) -> DilithiaRequest {
        let call = self.build_contract_call(
            "shielded",
            "is_nullifier_spent",
            json!({ "nullifier": nullifier }),
            None,
        );
        self.send_call_request(call)
    }

    pub fn shielded_compliance_proof_request(
        &self,
        proof_type: &str,
        proof_hex: &str,
        inputs_hex: &str,
    ) -> DilithiaRequest {
        let call = self.build_contract_call(
            "shielded",
            "compliance_proof",
            json!({
                "proof_type": proof_type,
                "proof": proof_hex,
                "inputs": inputs_hex,
            }),
            None,
        );
        self.send_call_request(call)
    }
}

// ── WASM Bytecode Validation ────────────────────────────────────────────

/// WASM magic bytes: \0asm
const WASM_MAGIC: [u8; 4] = [0x00, 0x61, 0x73, 0x6D];

/// WASM version 1 bytes
const WASM_VERSION_1: [u8; 4] = [0x01, 0x00, 0x00, 0x00];

/// Maximum bytecode size (512 KB).
const MAX_BYTECODE_SIZE: usize = 512 * 1024;

/// Base gas cost for deploying a contract.
const BASE_DEPLOY_GAS: u64 = 500_000;

/// Gas cost per byte of bytecode.
const PER_BYTE_GAS: u64 = 50;

/// Result of validating WASM bytecode.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BytecodeValidation {
    /// Whether the bytecode passed all checks.
    pub valid: bool,
    /// List of validation error messages (empty when valid).
    pub errors: Vec<String>,
    /// Size of the input bytecode in bytes.
    pub size_bytes: usize,
}

/// Validate raw WASM bytecode.
///
/// Checks magic bytes, version header, and size constraints.
/// This is a lightweight client-side check — no WASM parsing or RPC required.
pub fn validate_bytecode(wasm_bytes: &[u8]) -> BytecodeValidation {
    let mut errors = Vec::new();
    let size_bytes = wasm_bytes.len();

    if size_bytes == 0 {
        errors.push("bytecode is empty".to_string());
        return BytecodeValidation { valid: false, errors, size_bytes };
    }

    if size_bytes < 8 {
        errors.push("bytecode too small: must be at least 8 bytes".to_string());
        return BytecodeValidation { valid: false, errors, size_bytes };
    }

    if size_bytes > MAX_BYTECODE_SIZE {
        errors.push(format!(
            "bytecode too large: {} bytes exceeds maximum of {} bytes",
            size_bytes, MAX_BYTECODE_SIZE,
        ));
    }

    if wasm_bytes[..4] != WASM_MAGIC {
        errors.push("invalid WASM magic bytes: expected \\0asm".to_string());
    }

    if wasm_bytes[4..8] != WASM_VERSION_1 {
        errors.push("unsupported WASM version: expected version 1".to_string());
    }

    BytecodeValidation {
        valid: errors.is_empty(),
        errors,
        size_bytes,
    }
}

/// Estimate the gas cost for deploying WASM bytecode.
///
/// Uses a simple heuristic: `BASE_DEPLOY_GAS + len(wasm_bytes) * PER_BYTE_GAS`.
pub fn estimate_deploy_gas(wasm_bytes: &[u8]) -> u64 {
    BASE_DEPLOY_GAS + (wasm_bytes.len() as u64) * PER_BYTE_GAS
}

pub fn read_wasm_file_hex(path: &Path) -> Result<String, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("failed to read wasm file: {e}"))?;
    Ok(hex::encode(bytes))
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiaGasSponsorConnector {
    sponsor_contract: String,
    paymaster: Option<String>,
}

impl DilithiaGasSponsorConnector {
    pub fn new(sponsor_contract: impl Into<String>, paymaster: Option<String>) -> Self {
        Self {
            sponsor_contract: sponsor_contract.into(),
            paymaster,
        }
    }

    pub fn build_accept_query(&self, user: &str, contract: &str, method: &str) -> Value {
        json!({
            "contract": self.sponsor_contract,
            "method": "accept",
            "args": { "user": user, "contract": contract, "method": method }
        })
    }

    pub fn build_remaining_quota_query(&self, user: &str) -> Value {
        json!({
            "contract": self.sponsor_contract,
            "method": "remaining_quota",
            "args": { "user": user }
        })
    }

    pub fn apply_paymaster(&self, client: &DilithiaClient, call: Value) -> Value {
        self.paymaster
            .as_deref()
            .map_or(call.clone(), |paymaster| client.with_paymaster(call, paymaster))
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiaMessagingConnector {
    messaging_contract: String,
    paymaster: Option<String>,
}

impl DilithiaMessagingConnector {
    pub fn new(messaging_contract: impl Into<String>, paymaster: Option<String>) -> Self {
        Self {
            messaging_contract: messaging_contract.into(),
            paymaster,
        }
    }

    pub fn build_send_message_call(&self, client: &DilithiaClient, dest_chain: &str, payload: Value) -> Value {
        let call = json!({
            "contract": self.messaging_contract,
            "method": "send_message",
            "args": { "dest_chain": dest_chain, "payload": payload }
        });
        self.paymaster
            .as_deref()
            .map_or(call.clone(), |paymaster| client.with_paymaster(call, paymaster))
    }

    pub fn build_receive_message_call(
        &self,
        client: &DilithiaClient,
        source_chain: &str,
        source_contract: &str,
        payload: Value,
    ) -> Value {
        let call = json!({
            "contract": self.messaging_contract,
            "method": "receive_message",
            "args": {
                "source_chain": source_chain,
                "source_contract": source_contract,
                "payload": payload
            }
        });
        self.paymaster
            .as_deref()
            .map_or(call.clone(), |paymaster| client.with_paymaster(call, paymaster))
    }
}

impl NativeCryptoAdapter {
    fn account_from_parts(
        secret_key: Vec<u8>,
        public_key: Vec<u8>,
        address: String,
        account_index: u32,
        wallet_file: Option<dilithia_core::wallet::WalletFile>,
    ) -> Result<DilithiaAccount, String> {
        let wallet_file = wallet_file
            .map(serde_json::to_value)
            .transpose()
            .map_err(|e| format!("wallet file serialization failed: {e}"))?;
        Ok(DilithiaAccount {
            address,
            public_key: hex::encode(public_key),
            secret_key: hex::encode(secret_key),
            account_index,
            wallet_file,
        })
    }
}

impl DilithiaCryptoAdapter for NativeCryptoAdapter {
    fn generate_mnemonic(&self) -> Result<String, String> {
        dilithia_core::wallet::generate_mnemonic()
    }

    fn validate_mnemonic(&self, mnemonic: &str) -> Result<(), String> {
        dilithia_core::wallet::validate_mnemonic(mnemonic)
    }

    fn recover_hd_wallet(&self, mnemonic: &str) -> Result<DilithiaAccount, String> {
        self.recover_hd_wallet_account(mnemonic, 0)
    }

    fn recover_hd_wallet_account(
        &self,
        mnemonic: &str,
        account_index: u32,
    ) -> Result<DilithiaAccount, String> {
        dilithia_core::wallet::validate_mnemonic(mnemonic)?;
        let normalized = mnemonic.trim().to_lowercase();
        let (secret_key, public_key, address) =
            dilithia_core::wallet::recover_hd_account(&normalized, account_index);
        Self::account_from_parts(secret_key, public_key, address, account_index, None)
    }

    fn create_hd_wallet_file_from_mnemonic(
        &self,
        mnemonic: &str,
        password: &str,
    ) -> Result<DilithiaAccount, String> {
        self.create_hd_wallet_account_from_mnemonic(mnemonic, password, 0)
    }

    fn create_hd_wallet_account_from_mnemonic(
        &self,
        mnemonic: &str,
        password: &str,
        account_index: u32,
    ) -> Result<DilithiaAccount, String> {
        dilithia_core::wallet::validate_mnemonic(mnemonic)?;
        let normalized = mnemonic.trim().to_lowercase();
        let wallet_file =
            dilithia_core::wallet::create_hd_wallet_account(&normalized, account_index, password)?;
        let (secret_key, public_key, address) =
            dilithia_core::wallet::recover_hd_account(&normalized, account_index);
        Self::account_from_parts(
            secret_key,
            public_key,
            address,
            account_index,
            Some(wallet_file),
        )
    }

    fn recover_wallet_file(
        &self,
        wallet_file: &dilithia_core::wallet::WalletFile,
        mnemonic: &str,
        password: &str,
    ) -> Result<DilithiaAccount, String> {
        dilithia_core::wallet::validate_mnemonic(mnemonic)?;
        let normalized = mnemonic.trim().to_lowercase();
        let (secret_key, address) =
            dilithia_core::wallet::recover_wallet(wallet_file, &normalized, password)?;
        let public_key = hex::decode(&wallet_file.public_key)
            .map_err(|e| format!("invalid public_key hex: {e}"))?;
        Self::account_from_parts(
            secret_key,
            public_key,
            address,
            wallet_file.account_index.unwrap_or(0),
            Some(wallet_file.clone()),
        )
    }

    fn address_from_public_key(&self, public_key_hex: &str) -> Result<String, String> {
        let public_key = hex::decode(public_key_hex).map_err(|e| format!("invalid public key hex: {e}"))?;
        dilithia_core::crypto::validate_pk(&public_key)?;
        Ok(dilithia_core::crypto::address_from_pk(&public_key))
    }

    fn sign_message(&self, secret_key_hex: &str, message: &str) -> Result<DilithiaSignature, String> {
        let secret_key = hex::decode(secret_key_hex).map_err(|e| format!("invalid secret key hex: {e}"))?;
        let signature = dilithia_core::crypto::sign_mldsa65(message.as_bytes(), &secret_key)?;
        Ok(DilithiaSignature {
            algorithm: "mldsa65".to_string(),
            signature: hex::encode(signature),
        })
    }

    fn verify_message(
        &self,
        public_key_hex: &str,
        message: &str,
        signature_hex: &str,
    ) -> Result<bool, String> {
        let public_key = hex::decode(public_key_hex).map_err(|e| format!("invalid public key hex: {e}"))?;
        let signature = hex::decode(signature_hex).map_err(|e| format!("invalid signature hex: {e}"))?;
        Ok(dilithia_core::crypto::verify_mldsa65(
            message.as_bytes(),
            &signature,
            &public_key,
        ))
    }

    fn validate_address(&self, addr: &str) -> Result<String, String> {
        dilithia_core::crypto::validate_address(addr)
    }

    fn address_from_pk_checksummed(&self, public_key_hex: &str) -> Result<String, String> {
        let public_key = hex::decode(public_key_hex).map_err(|e| format!("invalid public key hex: {e}"))?;
        dilithia_core::crypto::validate_pk(&public_key)?;
        Ok(dilithia_core::crypto::address_from_pk_checksummed(&public_key))
    }

    fn address_with_checksum(&self, raw_addr: &str) -> Result<String, String> {
        Ok(dilithia_core::crypto::address_with_checksum(raw_addr))
    }

    fn validate_public_key(&self, public_key_hex: &str) -> Result<(), String> {
        let public_key = hex::decode(public_key_hex).map_err(|e| format!("invalid public key hex: {e}"))?;
        dilithia_core::crypto::validate_pk(&public_key)
    }

    fn validate_secret_key(&self, secret_key_hex: &str) -> Result<(), String> {
        let secret_key = hex::decode(secret_key_hex).map_err(|e| format!("invalid secret key hex: {e}"))?;
        dilithia_core::crypto::validate_sk(&secret_key)
    }

    fn validate_signature(&self, signature_hex: &str) -> Result<(), String> {
        let signature = hex::decode(signature_hex).map_err(|e| format!("invalid signature hex: {e}"))?;
        dilithia_core::crypto::validate_sig(&signature)
    }

    fn keygen(&self) -> Result<DilithiaKeypair, String> {
        let (sk, pk) = dilithia_core::crypto::keygen_mldsa65_secure()?;
        let address = dilithia_core::crypto::address_from_pk(&pk);
        Ok(DilithiaKeypair {
            secret_key: hex::encode(&*sk),
            public_key: hex::encode(&pk),
            address,
        })
    }

    fn keygen_from_seed(&self, seed_hex: &str) -> Result<DilithiaKeypair, String> {
        let seed_bytes = hex::decode(seed_hex).map_err(|e| format!("invalid seed hex: {e}"))?;
        let seed: [u8; 32] = seed_bytes
            .try_into()
            .map_err(|_| "seed must be exactly 32 bytes".to_string())?;
        let (sk, pk) = dilithia_core::crypto::keygen_mldsa65_from_seed(&seed);
        let address = dilithia_core::crypto::address_from_pk(&pk);
        Ok(DilithiaKeypair {
            secret_key: hex::encode(sk),
            public_key: hex::encode(pk),
            address,
        })
    }

    fn seed_from_mnemonic(&self, mnemonic: &str) -> Result<String, String> {
        Ok(hex::encode(dilithia_core::crypto::seed_from_mnemonic(mnemonic)))
    }

    fn derive_child_seed(&self, parent_seed_hex: &str, index: u32) -> Result<String, String> {
        let parent_bytes =
            hex::decode(parent_seed_hex).map_err(|e| format!("invalid parent seed hex: {e}"))?;
        let parent: [u8; 32] = parent_bytes
            .try_into()
            .map_err(|_| "parent seed must be exactly 32 bytes".to_string())?;
        let child = dilithia_core::crypto::derive_child_seed(&parent, index);
        Ok(hex::encode(child))
    }

    fn constant_time_eq(&self, a_hex: &str, b_hex: &str) -> Result<bool, String> {
        let a = hex::decode(a_hex).map_err(|e| format!("invalid a hex: {e}"))?;
        let b = hex::decode(b_hex).map_err(|e| format!("invalid b hex: {e}"))?;
        Ok(dilithia_core::crypto::constant_time_eq(&a, &b))
    }

    fn hash_hex(&self, data_hex: &str) -> Result<String, String> {
        let data = hex::decode(data_hex).map_err(|e| format!("invalid data hex: {e}"))?;
        Ok(hash::hash_hex(&data))
    }

    fn set_hash_alg(&self, alg: &str) -> Result<(), String> {
        let hash_alg = match alg {
            "sha3_512" => HashAlg::Sha3_512,
            "blake2b512" => HashAlg::Blake2b512,
            "blake3_256" => HashAlg::Blake3_256,
            other => return Err(format!("unknown hash algorithm: {other}")),
        };
        hash::set_hash_alg(hash_alg)
    }

    fn current_hash_alg(&self) -> String {
        let alg = hash::current_hash_alg();
        match alg {
            HashAlg::Sha3_512 => "sha3_512".to_string(),
            HashAlg::Blake2b512 => "blake2b512".to_string(),
            HashAlg::Blake3_256 => "blake3_256".to_string(),
        }
    }

    fn hash_len_hex(&self) -> usize {
        hash::hash_len_hex()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn versions_match_rpc_line() {
        assert_eq!(SDK_VERSION, "0.2.0");
        assert_eq!(RPC_LINE_VERSION, "0.2.0");
    }

    #[test]
    fn client_builds_requests() {
        let client = DilithiaClient::new("http://localhost:8000/rpc/", None).unwrap();
        assert_eq!(client.rpc_url(), "http://localhost:8000/rpc");
        assert_eq!(client.base_url(), "http://localhost:8000");
        assert_eq!(client.ws_url(), Some("ws://localhost:8000"));
        assert_eq!(client.timeout_ms(), 10_000);

        assert_eq!(
            client.get_balance_request("user1"),
            DilithiaRequest::Get {
                path: "http://localhost:8000/rpc/balance/user1".to_string()
            }
        );

        match client.get_address_summary_request("user1") {
            DilithiaRequest::Post { path, body } => {
                assert_eq!(path, "http://localhost:8000/rpc");
                assert_eq!(body["method"], "qsc_addressSummary");
                assert_eq!(body["params"]["address"], "user1");
            }
            _ => panic!("expected POST request"),
        }
    }

    #[test]
    fn configurable_urls_and_contract_queries_are_supported() {
        let client = DilithiaClient::from_config(DilithiaClientConfig {
            rpc_url: "http://localhost:8000/rpc".to_string(),
            chain_base_url: Some("http://localhost:8000/chain/".to_string()),
            indexer_url: Some("http://localhost:8011/api".to_string()),
            oracle_url: Some("http://localhost:8020".to_string()),
            ws_url: None,
            jwt: Some("secret-token".to_string()),
            headers: vec![("x-network".to_string(), "devnet".to_string())],
            timeout_ms: Some(5_000),
        })
        .unwrap();
        assert_eq!(client.base_url(), "http://localhost:8000/chain");
        assert_eq!(client.indexer_url(), Some("http://localhost:8011/api"));
        assert_eq!(client.oracle_url(), Some("http://localhost:8020"));
        assert_eq!(client.ws_url(), Some("ws://localhost:8000/chain"));
        assert_eq!(
            client.build_auth_headers(vec![("accept".to_string(), "application/json".to_string())]),
            vec![
                ("Authorization".to_string(), "Bearer secret-token".to_string()),
                ("x-network".to_string(), "devnet".to_string()),
                ("accept".to_string(), "application/json".to_string()),
            ]
        );

        assert_eq!(
            client.resolve_name_request("alice.dili"),
            DilithiaRequest::Get {
                path: "http://localhost:8000/chain/names/resolve/alice.dili".to_string()
            }
        );
        assert_eq!(
            client.query_contract_request("wasm:amm", "get_reserves", json!({})),
            DilithiaRequest::Get {
                path: "http://localhost:8000/chain/query?contract=wasm%3Aamm&method=get_reserves&args=%7B%7D".to_string()
            }
        );
    }

    #[test]
    fn generic_rpc_and_ws_builders_are_available() {
        let client = DilithiaClient::new("http://localhost:8000/rpc", None).unwrap();
        assert_eq!(
            client.build_jsonrpc_request("qsc_head", json!({"full": true}), 1),
            json!({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_head",
                "params": {"full": true}
            })
        );
        assert_eq!(
            client.build_ws_request("subscribe_heads", json!({"full": true}), 2),
            json!({
                "jsonrpc": "2.0",
                "id": 2,
                "method": "subscribe_heads",
                "params": {"full": true}
            })
        );
        assert_eq!(
            client.ws_connection_info(),
            json!({
                "url": "ws://localhost:8000",
                "headers": {}
            })
        );
    }

    #[test]
    fn sponsor_and_messaging_connectors_shape_calls() {
        let client = DilithiaClient::new("http://localhost:8000/rpc", None).unwrap();
        let sponsor = DilithiaGasSponsorConnector::new("wasm:gas_sponsor", Some("gas_sponsor".to_string()));
        let applied = sponsor.apply_paymaster(&client, json!({"contract":"wasm:amm","method":"swap","args":{}}));
        assert_eq!(applied["paymaster"], "gas_sponsor");

        let messaging = DilithiaMessagingConnector::new("wasm:messaging", Some("gas_sponsor".to_string()));
        let outbound = messaging.build_send_message_call(&client, "ethereum", json!({"amount": 1}));
        assert_eq!(outbound["method"], "send_message");
        assert_eq!(outbound["paymaster"], "gas_sponsor");
        let inbound = messaging.build_receive_message_call(&client, "ethereum", "bridge", json!({"tx":"0xabc"}));
        assert_eq!(inbound["args"]["source_chain"], "ethereum");
        assert_eq!(inbound["args"]["source_contract"], "bridge");
    }

    // ── Bytecode validation tests ─────────────────────────────────────

    fn make_valid_wasm(extra: usize) -> Vec<u8> {
        let mut bytes = vec![0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00];
        bytes.extend(std::iter::repeat(0u8).take(extra));
        bytes
    }

    #[test]
    fn validate_bytecode_valid() {
        let wasm = make_valid_wasm(100);
        let result = validate_bytecode(&wasm);
        assert!(result.valid);
        assert!(result.errors.is_empty());
        assert_eq!(result.size_bytes, 108);
    }

    #[test]
    fn validate_bytecode_empty() {
        let result = validate_bytecode(&[]);
        assert!(!result.valid);
        assert_eq!(result.errors.len(), 1);
        assert!(result.errors[0].contains("empty"));
    }

    #[test]
    fn validate_bytecode_too_small() {
        let result = validate_bytecode(&[0x00, 0x61, 0x73]);
        assert!(!result.valid);
        assert!(result.errors[0].contains("too small"));
    }

    #[test]
    fn validate_bytecode_too_large() {
        let wasm = make_valid_wasm(512 * 1024);
        let result = validate_bytecode(&wasm);
        assert!(!result.valid);
        assert!(result.errors[0].contains("too large"));
    }

    #[test]
    fn validate_bytecode_invalid_magic() {
        let bytes = vec![0xFF, 0xFF, 0xFF, 0xFF, 0x01, 0x00, 0x00, 0x00, 0x00];
        let result = validate_bytecode(&bytes);
        assert!(!result.valid);
        assert!(result.errors.iter().any(|e| e.contains("magic")));
    }

    #[test]
    fn validate_bytecode_invalid_version() {
        let bytes = vec![0x00, 0x61, 0x73, 0x6D, 0x02, 0x00, 0x00, 0x00, 0x00];
        let result = validate_bytecode(&bytes);
        assert!(!result.valid);
        assert!(result.errors.iter().any(|e| e.contains("version")));
    }

    #[test]
    fn estimate_deploy_gas_known_size() {
        let wasm = make_valid_wasm(0); // 8 bytes
        assert_eq!(estimate_deploy_gas(&wasm), 500_000 + 8 * 50);

        let wasm2 = make_valid_wasm(992); // 1000 bytes
        assert_eq!(estimate_deploy_gas(&wasm2), 500_000 + 1000 * 50);
    }

    #[test]
    fn native_crypto_adapter_recovers_root_account() {
        let adapter = NativeCryptoAdapter;
        let mnemonic = adapter.generate_mnemonic().unwrap();
        let account = adapter.recover_hd_wallet(&mnemonic).unwrap();
        assert!(!account.address.is_empty());
        assert!(!account.public_key.is_empty());
        assert!(!account.secret_key.is_empty());
        assert_eq!(account.account_index, 0);
    }

    #[test]
    fn native_crypto_adapter_signs_messages() {
        let adapter = NativeCryptoAdapter;
        let mnemonic = adapter.generate_mnemonic().unwrap();
        let account = adapter.recover_hd_wallet(&mnemonic).unwrap();
        let signature = adapter.sign_message(&account.secret_key, "hello").unwrap();
        let ok = adapter
            .verify_message(&account.public_key, "hello", &signature.signature)
            .unwrap();
        assert!(ok);
    }
}
