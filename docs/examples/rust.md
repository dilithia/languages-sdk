# Rust Examples

The Dilithia Rust SDK builds request objects (`DilithiaRequest::Get` / `DilithiaRequest::Post`) that describe the HTTP call to make. It does **not** include an HTTP runtime -- you execute the requests with your own HTTP client (reqwest, ureq, hyper, etc.). This keeps the SDK lightweight and compatible with any async runtime or blocking context.

## Prerequisites

Add to your `Cargo.toml`:

```toml
[dependencies]
dilithia-sdk-rust = "0.2.0"
serde_json = "1"
```

---

## Scenario 1: Balance Monitor Bot

A bot that recovers its wallet, checks its balance, and if the balance exceeds a threshold, sends tokens to a destination address via a contract call.

```rust
use dilithia_sdk_rust::{
    DilithiaClient, DilithiaCryptoAdapter, DilithiaRequest, NativeCryptoAdapter,
};
use serde_json::json;

const RPC_URL: &str = "https://rpc.dilithia.network/rpc";
const TOKEN_CONTRACT: &str = "dil1_token_main";
const DESTINATION: &str = "dil1_recipient_address";
const THRESHOLD: u64 = 500_000;
const SEND_AMOUNT: u64 = 100_000;

fn main() -> Result<(), String> {
    // 1. Initialize client and crypto adapter
    let client = DilithiaClient::new(RPC_URL, Some(15_000))
        .map_err(|e| e.to_string())?;
    let crypto = NativeCryptoAdapter;

    // 2. Recover wallet from saved mnemonic
    let mnemonic = std::env::var("BOT_MNEMONIC")
        .map_err(|_| "Set BOT_MNEMONIC env var".to_string())?;
    let account = crypto.recover_hd_wallet(&mnemonic)?;
    println!("Bot address: {}", account.address);

    // 3. Check current balance
    let balance_req = client.get_balance_request(&account.address);
    match &balance_req {
        DilithiaRequest::Get { path } => {
            println!("GET {path}");
            // execute with your HTTP client
            // let response = ureq::get(path).call()?.into_string()?;
            // let balance: u64 = parse_balance(&response);
        }
        _ => return Err("unexpected request type".into()),
    }

    // (Assuming we parsed the balance from the response)
    let balance: u64 = 600_000; // placeholder
    println!("Current balance: {balance}");

    if balance < THRESHOLD {
        println!("Balance {balance} below threshold {THRESHOLD}. Nothing to do.");
        return Ok(());
    }

    // 4. Build a contract call to transfer tokens
    let call = client.build_contract_call(
        TOKEN_CONTRACT,
        "transfer",
        json!({ "to": DESTINATION, "amount": SEND_AMOUNT }),
        None,
    );

    // 5. Simulate first to verify it would succeed
    let sim_req = client.simulate_request(call.clone());
    match &sim_req {
        DilithiaRequest::Post { path, body } => {
            println!("POST {path}");
            println!("Body: {body}");
            // execute with your HTTP client
        }
        _ => return Err("unexpected request type".into()),
    }

    // 6. Sign the call payload
    let call_json = call.to_string();
    let sig = crypto.sign_message(&account.secret_key, &call_json)?;
    println!("Signed with algorithm: {}", sig.algorithm);

    // 7. Submit the signed call
    let signed_call = json!({
        "call": call,
        "signature": sig.signature,
        "public_key": account.public_key,
    });
    let send_req = client.send_call_request(signed_call);
    match &send_req {
        DilithiaRequest::Post { path, body } => {
            println!("POST {path}");
            println!("Body: {body}");
            // execute with your HTTP client
            // let tx_hash = parse_tx_hash(&response);
        }
        _ => return Err("unexpected request type".into()),
    }

    // 8. Poll for receipt
    let tx_hash = "placeholder_tx_hash";
    let receipt_req = client.get_receipt_request(tx_hash);
    match &receipt_req {
        DilithiaRequest::Get { path } => {
            println!("GET {path}");
            // execute with your HTTP client, retry until receipt appears
        }
        _ => return Err("unexpected request type".into()),
    }

    println!("Transaction confirmed");
    Ok(())
}
```

---

## Scenario 2: Multi-Account Treasury Manager

Derives multiple accounts from a single mnemonic, checks each balance, and prints a treasury summary.

```rust
use dilithia_sdk_rust::{
    DilithiaClient, DilithiaCryptoAdapter, DilithiaRequest, NativeCryptoAdapter,
};

const RPC_URL: &str = "https://rpc.dilithia.network/rpc";
const NUM_ACCOUNTS: u32 = 5;

fn main() -> Result<(), String> {
    let client = DilithiaClient::new(RPC_URL, Some(10_000))
        .map_err(|e| e.to_string())?;
    let crypto = NativeCryptoAdapter;

    // 1. Recover the root mnemonic
    let mnemonic = std::env::var("TREASURY_MNEMONIC")
        .map_err(|_| "Set TREASURY_MNEMONIC env var".to_string())?;
    crypto.validate_mnemonic(&mnemonic)?;

    // 2. Derive multiple HD accounts and query each balance
    println!("Treasury accounts:");
    println!("{:<8} {:<50} {}", "Index", "Address", "Balance Request");

    for index in 0..NUM_ACCOUNTS {
        let account = crypto.recover_hd_wallet_account(&mnemonic, index)?;
        let balance_req = client.get_balance_request(&account.address);

        match &balance_req {
            DilithiaRequest::Get { path } => {
                println!("{:<8} {:<50} GET {}", index, account.address, path);
                // execute with your HTTP client
                // let balance = fetch_balance(path)?;
            }
            _ => return Err("unexpected request type".into()),
        }
    }

    // 3. Check nonce on the root account for pending transactions
    let root_account = crypto.recover_hd_wallet(&mnemonic)?;
    let nonce_req = client.get_nonce_request(&root_account.address);
    match &nonce_req {
        DilithiaRequest::Get { path } => {
            println!("\nRoot account nonce request: GET {path}");
            // execute with your HTTP client
        }
        _ => return Err("unexpected request type".into()),
    }

    // 4. Get address summary via JSON-RPC for the root account
    let summary_req = client.get_address_summary_request(&root_account.address);
    match &summary_req {
        DilithiaRequest::Post { path, body } => {
            println!("Address summary: POST {path}");
            println!("Body: {body}");
            // execute with your HTTP client
        }
        _ => return Err("unexpected request type".into()),
    }

    println!("\nTreasury report complete.");
    Ok(())
}
```

---

## Scenario 3: Signature Verification Service

Generates a keypair, signs a message, then verifies the signature -- demonstrating the full cryptographic round-trip.

```rust
use dilithia_sdk_rust::{DilithiaCryptoAdapter, NativeCryptoAdapter};

fn main() -> Result<(), String> {
    let crypto = NativeCryptoAdapter;

    // 1. Generate a fresh keypair
    let keypair = crypto.keygen()?;
    println!("Address:    {}", keypair.address);
    println!("Public key: {} ({} hex chars)", &keypair.public_key[..32], keypair.public_key.len());

    // 2. Sign a message
    let message = "Authorize withdrawal of 1000 tokens";
    let sig = crypto.sign_message(&keypair.secret_key, message)?;
    println!("Algorithm:  {}", sig.algorithm);
    println!("Signature:  {}... ({} hex chars)", &sig.signature[..32], sig.signature.len());

    // 3. Verify with the correct public key
    let valid = crypto.verify_message(&keypair.public_key, message, &sig.signature)?;
    println!("Valid signature: {valid}");
    assert!(valid, "signature should be valid");

    // 4. Verify with a tampered message should fail
    let tampered = crypto.verify_message(&keypair.public_key, "tampered message", &sig.signature)?;
    println!("Tampered message valid: {tampered}");
    assert!(!tampered, "tampered message should fail verification");

    // 5. Validate individual components
    crypto.validate_public_key(&keypair.public_key)?;
    crypto.validate_secret_key(&keypair.secret_key)?;
    crypto.validate_signature(&sig.signature)?;
    println!("All key/signature validations passed.");

    // 6. Derive address from public key and confirm it matches
    let derived_addr = crypto.address_from_public_key(&keypair.public_key)?;
    assert_eq!(derived_addr, keypair.address, "derived address must match");
    println!("Address derivation confirmed: {derived_addr}");

    Ok(())
}
```

---

## Scenario 4: Wallet Backup and Recovery

Creates an encrypted wallet file from a mnemonic, then recovers the account from that file -- demonstrating the backup/restore workflow.

```rust
use dilithia_sdk_rust::{DilithiaCryptoAdapter, NativeCryptoAdapter};

fn main() -> Result<(), String> {
    let crypto = NativeCryptoAdapter;

    // 1. Generate a fresh mnemonic
    let mnemonic = crypto.generate_mnemonic()?;
    println!("Generated mnemonic ({} words)", mnemonic.split_whitespace().count());
    crypto.validate_mnemonic(&mnemonic)?;

    // 2. Create an encrypted wallet file (account index 0)
    let password = "strong-passphrase-here";
    let account = crypto.create_hd_wallet_file_from_mnemonic(&mnemonic, password)?;
    println!("Account address: {}", account.address);
    println!("Account index:   {}", account.account_index);

    // The wallet_file field contains the encrypted JSON to persist
    let wallet_json = account.wallet_file
        .as_ref()
        .ok_or("wallet_file should be present")?;
    println!("Wallet file size: {} bytes", wallet_json.to_string().len());

    // 3. Simulate saving and loading the wallet file
    let wallet_file_str = wallet_json.to_string();
    println!("Wallet file saved to disk (simulated).");

    // 4. Create a second account at index 1 from the same mnemonic
    let account_1 = crypto.create_hd_wallet_account_from_mnemonic(&mnemonic, password, 1)?;
    println!("Account 1 address: {}", account_1.address);
    assert_ne!(account.address, account_1.address, "different indices yield different addresses");

    // 5. Recover the original account: verify the address matches
    let recovered = crypto.recover_hd_wallet(&mnemonic)?;
    assert_eq!(recovered.address, account.address, "recovered address must match");
    println!("Recovery verified: addresses match.");

    // 6. Derive a checksummed address
    let checksummed = crypto.address_from_pk_checksummed(&account.public_key)?;
    println!("Checksummed address: {checksummed}");

    // 7. Demonstrate seed-based key derivation
    let seed = crypto.seed_from_mnemonic(&mnemonic)?;
    let child_seed = crypto.derive_child_seed(&seed, 0)?;
    let child_keypair = crypto.keygen_from_seed(&child_seed)?;
    println!("Child keypair address: {}", child_keypair.address);

    println!("\nWallet backup and recovery complete.");
    Ok(())
}
```

---

## Scenario 5: Gas-Sponsored Meta-Transaction

Uses the `DilithiaGasSponsorConnector` to check if a user is eligible for gas sponsorship, then builds and submits a sponsored contract call.

```rust
use dilithia_sdk_rust::{
    DilithiaClient, DilithiaCryptoAdapter, DilithiaGasSponsorConnector,
    DilithiaRequest, NativeCryptoAdapter,
};
use serde_json::json;

const RPC_URL: &str = "https://rpc.dilithia.network/rpc";
const SPONSOR_CONTRACT: &str = "wasm:gas_sponsor";
const PAYMASTER: &str = "gas_sponsor";
const TARGET_CONTRACT: &str = "wasm:amm";

fn main() -> Result<(), String> {
    let client = DilithiaClient::new(RPC_URL, Some(10_000))
        .map_err(|e| e.to_string())?;
    let crypto = NativeCryptoAdapter;
    let sponsor = DilithiaGasSponsorConnector::new(SPONSOR_CONTRACT, Some(PAYMASTER.to_string()));

    // 1. Recover the user's wallet
    let mnemonic = std::env::var("USER_MNEMONIC")
        .map_err(|_| "Set USER_MNEMONIC env var".to_string())?;
    let account = crypto.recover_hd_wallet(&mnemonic)?;
    println!("User address: {}", account.address);

    // 2. Check remaining gas quota for the user
    let quota_query = sponsor.build_remaining_quota_query(&account.address);
    let quota_req = client.query_contract_request(
        &quota_query["contract"].as_str().unwrap_or(""),
        &quota_query["method"].as_str().unwrap_or(""),
        quota_query["args"].clone(),
    );
    match &quota_req {
        DilithiaRequest::Get { path } => {
            println!("Quota check: GET {path}");
            // execute with your HTTP client
            // let quota: u64 = parse_quota(&response);
        }
        _ => return Err("unexpected request type".into()),
    }

    // 3. Check if the sponsor accepts this call
    let accept_query = sponsor.build_accept_query(&account.address, TARGET_CONTRACT, "swap");
    let accept_req = client.query_contract_request(
        &accept_query["contract"].as_str().unwrap_or(""),
        &accept_query["method"].as_str().unwrap_or(""),
        accept_query["args"].clone(),
    );
    match &accept_req {
        DilithiaRequest::Get { path } => {
            println!("Accept check: GET {path}");
            // execute with your HTTP client
        }
        _ => return Err("unexpected request type".into()),
    }

    // 4. Build the contract call with gas sponsorship
    let call = client.build_contract_call(
        TARGET_CONTRACT,
        "swap",
        json!({ "token_in": "DAI", "token_out": "USDC", "amount": 1000 }),
        Some(PAYMASTER),
    );
    println!("Call with paymaster: {call}");

    // 5. Alternatively, build a call and apply the paymaster after
    let plain_call = client.build_contract_call(
        TARGET_CONTRACT,
        "swap",
        json!({ "token_in": "DAI", "token_out": "USDC", "amount": 1000 }),
        None,
    );
    let sponsored_call = sponsor.apply_paymaster(&client, plain_call);
    println!("Sponsored call: {sponsored_call}");

    // 6. Sign and submit
    let call_json = sponsored_call.to_string();
    let sig = crypto.sign_message(&account.secret_key, &call_json)?;
    let signed = json!({
        "call": sponsored_call,
        "signature": sig.signature,
        "public_key": account.public_key,
    });

    let send_req = client.send_call_request(signed);
    match &send_req {
        DilithiaRequest::Post { path, body } => {
            println!("Submit sponsored tx: POST {path}");
            println!("Body: {body}");
            // execute with your HTTP client
        }
        _ => return Err("unexpected request type".into()),
    }

    println!("Gas-sponsored transaction submitted.");
    Ok(())
}
```

---

## Scenario 6: Cross-Chain Message Sender

Uses the `DilithiaMessagingConnector` to send a cross-chain message and build a receive handler for incoming messages.

```rust
use dilithia_sdk_rust::{
    DilithiaClient, DilithiaCryptoAdapter, DilithiaMessagingConnector,
    DilithiaRequest, NativeCryptoAdapter,
};
use serde_json::json;

const RPC_URL: &str = "https://rpc.dilithia.network/rpc";
const MESSAGING_CONTRACT: &str = "wasm:messaging";
const PAYMASTER: &str = "gas_sponsor";

fn main() -> Result<(), String> {
    let client = DilithiaClient::new(RPC_URL, Some(10_000))
        .map_err(|e| e.to_string())?;
    let crypto = NativeCryptoAdapter;
    let messaging = DilithiaMessagingConnector::new(
        MESSAGING_CONTRACT,
        Some(PAYMASTER.to_string()),
    );

    // 1. Recover the sender's wallet
    let mnemonic = std::env::var("SENDER_MNEMONIC")
        .map_err(|_| "Set SENDER_MNEMONIC env var".to_string())?;
    let account = crypto.recover_hd_wallet(&mnemonic)?;
    println!("Sender address: {}", account.address);

    // 2. Build an outbound cross-chain message
    let payload = json!({
        "action": "bridge_transfer",
        "token": "USDC",
        "amount": 5000,
        "recipient": "0xRecipientOnEthereum",
    });
    let outbound_call = messaging.build_send_message_call(&client, "ethereum", payload);
    println!("Outbound call: {outbound_call}");

    // 3. Sign and submit the outbound message
    let call_json = outbound_call.to_string();
    let sig = crypto.sign_message(&account.secret_key, &call_json)?;
    let signed_outbound = json!({
        "call": outbound_call,
        "signature": sig.signature,
        "public_key": account.public_key,
    });

    let send_req = client.send_call_request(signed_outbound);
    match &send_req {
        DilithiaRequest::Post { path, body } => {
            println!("Send cross-chain message: POST {path}");
            println!("Body: {body}");
            // execute with your HTTP client
        }
        _ => return Err("unexpected request type".into()),
    }

    // 4. Build a receive handler for an incoming cross-chain message
    let incoming_payload = json!({
        "action": "bridge_confirmation",
        "tx_hash": "0xabc123",
        "status": "confirmed",
    });
    let inbound_call = messaging.build_receive_message_call(
        &client,
        "ethereum",
        "0xBridgeContract",
        incoming_payload,
    );
    println!("Inbound call: {inbound_call}");

    // 5. Simulate the inbound call before submitting
    let sim_req = client.simulate_request(inbound_call.clone());
    match &sim_req {
        DilithiaRequest::Post { path, body } => {
            println!("Simulate inbound: POST {path}");
            println!("Body: {body}");
            // execute with your HTTP client
        }
        _ => return Err("unexpected request type".into()),
    }

    // 6. Submit the inbound call
    let inbound_json = inbound_call.to_string();
    let inbound_sig = crypto.sign_message(&account.secret_key, &inbound_json)?;
    let signed_inbound = json!({
        "call": inbound_call,
        "signature": inbound_sig.signature,
        "public_key": account.public_key,
    });

    let submit_req = client.send_call_request(signed_inbound);
    match &submit_req {
        DilithiaRequest::Post { path, body } => {
            println!("Submit inbound: POST {path}");
            println!("Body: {body}");
            // execute with your HTTP client
        }
        _ => return Err("unexpected request type".into()),
    }

    println!("Cross-chain messaging complete.");
    Ok(())
}
```
