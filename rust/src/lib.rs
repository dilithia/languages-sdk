use serde_json::{json, Value};
use std::fmt::{Display, Formatter};

pub const SDK_VERSION: &str = "0.3.0";
pub const RPC_LINE_VERSION: &str = "0.3.0";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiumAccount {
    pub address: String,
    pub public_key: String,
    pub secret_key: String,
    pub account_index: u32,
    pub wallet_file: Option<Value>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiumSignature {
    pub algorithm: String,
    pub signature: String,
}

pub trait DilithiumCryptoAdapter {
    fn generate_mnemonic(&self) -> Result<String, String>;
    fn validate_mnemonic(&self, mnemonic: &str) -> Result<(), String>;
    fn recover_hd_wallet(&self, mnemonic: &str) -> Result<DilithiumAccount, String>;
    fn recover_hd_wallet_account(
        &self,
        mnemonic: &str,
        account_index: u32,
    ) -> Result<DilithiumAccount, String>;
    fn create_hd_wallet_file_from_mnemonic(
        &self,
        mnemonic: &str,
        password: &str,
    ) -> Result<DilithiumAccount, String>;
    fn create_hd_wallet_account_from_mnemonic(
        &self,
        mnemonic: &str,
        password: &str,
        account_index: u32,
    ) -> Result<DilithiumAccount, String>;
    fn recover_wallet_file(
        &self,
        wallet_file: &qsc_crypto::wallet::WalletFile,
        mnemonic: &str,
        password: &str,
    ) -> Result<DilithiumAccount, String>;
    fn address_from_public_key(&self, public_key_hex: &str) -> Result<String, String>;
    fn sign_message(&self, secret_key_hex: &str, message: &str) -> Result<DilithiumSignature, String>;
    fn verify_message(
        &self,
        public_key_hex: &str,
        message: &str,
        signature_hex: &str,
    ) -> Result<bool, String>;
}

#[derive(Debug, Default, Clone, Copy)]
pub struct NativeCryptoAdapter;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiumClient {
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
pub struct DilithiumClientConfig {
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
pub enum DilithiumRequest {
    Get { path: String },
    Post { path: String, body: Value },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DilithiumError {
    InvalidRpcUrl,
}

impl Display for DilithiumError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidRpcUrl => write!(f, "invalid RPC URL"),
        }
    }
}

impl std::error::Error for DilithiumError {}

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

impl DilithiumClient {
    pub fn new(rpc_url: impl Into<String>, timeout_ms: Option<u64>) -> Result<Self, DilithiumError> {
        Self::from_config(DilithiumClientConfig {
            rpc_url: rpc_url.into(),
            timeout_ms,
            ..DilithiumClientConfig::default()
        })
    }

    pub fn from_config(config: DilithiumClientConfig) -> Result<Self, DilithiumError> {
        let DilithiumClientConfig {
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
            return Err(DilithiumError::InvalidRpcUrl);
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

    pub fn get_balance_request(&self, address: &str) -> DilithiumRequest {
        DilithiumRequest::Get {
            path: format!("{}/balance/{}", self.rpc_url, address),
        }
    }

    pub fn get_nonce_request(&self, address: &str) -> DilithiumRequest {
        DilithiumRequest::Get {
            path: format!("{}/nonce/{}", self.rpc_url, address),
        }
    }

    pub fn get_receipt_request(&self, tx_hash: &str) -> DilithiumRequest {
        DilithiumRequest::Get {
            path: format!("{}/receipt/{}", self.rpc_url, tx_hash),
        }
    }

    pub fn get_address_summary_request(&self, address: &str) -> DilithiumRequest {
        DilithiumRequest::Post {
            path: self.rpc_url.clone(),
            body: json!({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_addressSummary",
                "params": { "address": address }
            }),
        }
    }

    pub fn get_gas_estimate_request(&self) -> DilithiumRequest {
        DilithiumRequest::Post {
            path: self.rpc_url.clone(),
            body: json!({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_gasEstimate",
                "params": {}
            }),
        }
    }

    pub fn get_base_fee_request(&self) -> DilithiumRequest {
        DilithiumRequest::Post {
            path: self.rpc_url.clone(),
            body: json!({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_baseFee",
                "params": {}
            }),
        }
    }

    pub fn resolve_name_request(&self, name: &str) -> DilithiumRequest {
        DilithiumRequest::Get {
            path: format!("{}/names/resolve/{}", self.base_url, name),
        }
    }

    pub fn reverse_resolve_name_request(&self, address: &str) -> DilithiumRequest {
        DilithiumRequest::Get {
            path: format!("{}/names/reverse/{}", self.base_url, address),
        }
    }

    pub fn query_contract_request(&self, contract: &str, method: &str, args: Value) -> DilithiumRequest {
        let args_json = args.to_string();
        let encoded_args = urlencoding::encode(&args_json);
        DilithiumRequest::Get {
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

    pub fn simulate_request(&self, call: Value) -> DilithiumRequest {
        DilithiumRequest::Post {
            path: format!("{}/simulate", self.rpc_url),
            body: call,
        }
    }

    pub fn send_call_request(&self, call: Value) -> DilithiumRequest {
        DilithiumRequest::Post {
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
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiumGasSponsorConnector {
    sponsor_contract: String,
    paymaster: Option<String>,
}

impl DilithiumGasSponsorConnector {
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

    pub fn apply_paymaster(&self, client: &DilithiumClient, call: Value) -> Value {
        self.paymaster
            .as_deref()
            .map_or(call.clone(), |paymaster| client.with_paymaster(call, paymaster))
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DilithiumMessagingConnector {
    messaging_contract: String,
    paymaster: Option<String>,
}

impl DilithiumMessagingConnector {
    pub fn new(messaging_contract: impl Into<String>, paymaster: Option<String>) -> Self {
        Self {
            messaging_contract: messaging_contract.into(),
            paymaster,
        }
    }

    pub fn build_send_message_call(&self, client: &DilithiumClient, dest_chain: &str, payload: Value) -> Value {
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
        client: &DilithiumClient,
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
        wallet_file: Option<qsc_crypto::wallet::WalletFile>,
    ) -> Result<DilithiumAccount, String> {
        let wallet_file = wallet_file
            .map(serde_json::to_value)
            .transpose()
            .map_err(|e| format!("wallet file serialization failed: {e}"))?;
        Ok(DilithiumAccount {
            address,
            public_key: hex::encode(public_key),
            secret_key: hex::encode(secret_key),
            account_index,
            wallet_file,
        })
    }
}

impl DilithiumCryptoAdapter for NativeCryptoAdapter {
    fn generate_mnemonic(&self) -> Result<String, String> {
        qsc_crypto::wallet::generate_mnemonic()
    }

    fn validate_mnemonic(&self, mnemonic: &str) -> Result<(), String> {
        qsc_crypto::wallet::validate_mnemonic(mnemonic)
    }

    fn recover_hd_wallet(&self, mnemonic: &str) -> Result<DilithiumAccount, String> {
        self.recover_hd_wallet_account(mnemonic, 0)
    }

    fn recover_hd_wallet_account(
        &self,
        mnemonic: &str,
        account_index: u32,
    ) -> Result<DilithiumAccount, String> {
        qsc_crypto::wallet::validate_mnemonic(mnemonic)?;
        let normalized = mnemonic.trim().to_lowercase();
        let (secret_key, public_key, address) =
            qsc_crypto::wallet::recover_hd_account(&normalized, account_index);
        Self::account_from_parts(secret_key, public_key, address, account_index, None)
    }

    fn create_hd_wallet_file_from_mnemonic(
        &self,
        mnemonic: &str,
        password: &str,
    ) -> Result<DilithiumAccount, String> {
        self.create_hd_wallet_account_from_mnemonic(mnemonic, password, 0)
    }

    fn create_hd_wallet_account_from_mnemonic(
        &self,
        mnemonic: &str,
        password: &str,
        account_index: u32,
    ) -> Result<DilithiumAccount, String> {
        qsc_crypto::wallet::validate_mnemonic(mnemonic)?;
        let normalized = mnemonic.trim().to_lowercase();
        let wallet_file =
            qsc_crypto::wallet::create_hd_wallet_account(&normalized, account_index, password)?;
        let (secret_key, public_key, address) =
            qsc_crypto::wallet::recover_hd_account(&normalized, account_index);
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
        wallet_file: &qsc_crypto::wallet::WalletFile,
        mnemonic: &str,
        password: &str,
    ) -> Result<DilithiumAccount, String> {
        qsc_crypto::wallet::validate_mnemonic(mnemonic)?;
        let normalized = mnemonic.trim().to_lowercase();
        let (secret_key, address) =
            qsc_crypto::wallet::recover_wallet(wallet_file, &normalized, password)?;
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
        qsc_crypto::crypto::validate_pk(&public_key)?;
        Ok(qsc_crypto::crypto::address_from_pk(&public_key))
    }

    fn sign_message(&self, secret_key_hex: &str, message: &str) -> Result<DilithiumSignature, String> {
        let secret_key = hex::decode(secret_key_hex).map_err(|e| format!("invalid secret key hex: {e}"))?;
        let signature = qsc_crypto::crypto::sign_mldsa65(message.as_bytes(), &secret_key)?;
        Ok(DilithiumSignature {
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
        Ok(qsc_crypto::crypto::verify_mldsa65(
            message.as_bytes(),
            &signature,
            &public_key,
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn versions_match_rpc_line() {
        assert_eq!(SDK_VERSION, "0.3.0");
        assert_eq!(RPC_LINE_VERSION, "0.3.0");
    }

    #[test]
    fn client_builds_requests() {
        let client = DilithiumClient::new("http://localhost:8000/rpc/", None).unwrap();
        assert_eq!(client.rpc_url(), "http://localhost:8000/rpc");
        assert_eq!(client.base_url(), "http://localhost:8000");
        assert_eq!(client.ws_url(), Some("ws://localhost:8000"));
        assert_eq!(client.timeout_ms(), 10_000);

        assert_eq!(
            client.get_balance_request("user1"),
            DilithiumRequest::Get {
                path: "http://localhost:8000/rpc/balance/user1".to_string()
            }
        );

        match client.get_address_summary_request("user1") {
            DilithiumRequest::Post { path, body } => {
                assert_eq!(path, "http://localhost:8000/rpc");
                assert_eq!(body["method"], "qsc_addressSummary");
                assert_eq!(body["params"]["address"], "user1");
            }
            _ => panic!("expected POST request"),
        }
    }

    #[test]
    fn configurable_urls_and_contract_queries_are_supported() {
        let client = DilithiumClient::from_config(DilithiumClientConfig {
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
            DilithiumRequest::Get {
                path: "http://localhost:8000/chain/names/resolve/alice.dili".to_string()
            }
        );
        assert_eq!(
            client.query_contract_request("wasm:amm", "get_reserves", json!({})),
            DilithiumRequest::Get {
                path: "http://localhost:8000/chain/query?contract=wasm%3Aamm&method=get_reserves&args=%7B%7D".to_string()
            }
        );
    }

    #[test]
    fn generic_rpc_and_ws_builders_are_available() {
        let client = DilithiumClient::new("http://localhost:8000/rpc", None).unwrap();
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
        let client = DilithiumClient::new("http://localhost:8000/rpc", None).unwrap();
        let sponsor = DilithiumGasSponsorConnector::new("wasm:gas_sponsor", Some("gas_sponsor".to_string()));
        let applied = sponsor.apply_paymaster(&client, json!({"contract":"wasm:amm","method":"swap","args":{}}));
        assert_eq!(applied["paymaster"], "gas_sponsor");

        let messaging = DilithiumMessagingConnector::new("wasm:messaging", Some("gas_sponsor".to_string()));
        let outbound = messaging.build_send_message_call(&client, "ethereum", json!({"amount": 1}));
        assert_eq!(outbound["method"], "send_message");
        assert_eq!(outbound["paymaster"], "gas_sponsor");
        let inbound = messaging.build_receive_message_call(&client, "ethereum", "bridge", json!({"tx":"0xabc"}));
        assert_eq!(inbound["args"]["source_chain"], "ethereum");
        assert_eq!(inbound["args"]["source_contract"], "bridge");
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
