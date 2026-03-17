# Signing Transactions

This guide walks through the complete process of building, signing, and submitting a transaction to the Dilithia blockchain using the SDK.

---

## Overview

The typical transaction flow is:

1. **Create a client** connected to a Dilithia node
2. **Load the crypto adapter** for signing
3. **Recover or generate an account** with keys
4. **Build the transaction call** (contract call, transfer, etc.)
5. **Simulate** the call (optional but recommended)
6. **Sign and submit** the call
7. **Wait for the receipt**

---

## Full Example

=== "TypeScript"

    ```typescript
    import {
      DilithiaClient,
      loadNativeCryptoAdapter,
      type DilithiaCryptoAdapter,
      type DilithiaAccount,
    } from "@dilithia/sdk";

    // 1. Create the client
    const client = new DilithiaClient({
      rpcUrl: "https://rpc.dilithia.network/rpc",
    });

    // 2. Load the crypto adapter
    const crypto = await loadNativeCryptoAdapter();
    if (!crypto) throw new Error("Native crypto bridge not available");

    // 3. Recover the account from a mnemonic
    const mnemonic = "your twenty four word mnemonic phrase ...";
    const account = await crypto.recoverHdWallet(mnemonic);

    console.log("Address:", account.address);

    // 4. Build a contract call
    const call = client.buildContractCall("wasm:token", "transfer", {
      to: "dili1recipient...",
      amount: 1000,
    });

    // 5. Simulate first (optional but recommended)
    try {
      const simResult = await client.simulate(call);
      console.log("Simulation passed:", simResult);
    } catch (err) {
      console.error("Simulation failed:", err);
      process.exit(1);
    }

    // 6. Create a signer and submit the signed call
    const signer = {
      async signCanonicalPayload(payloadJson: string) {
        const sig = await crypto.signMessage(account.secretKey, payloadJson);
        return {
          sender: account.address,
          public_key: account.publicKey,
          algorithm: sig.algorithm,
          signature: sig.signature,
        };
      },
    };

    const result = await client.sendSignedCall(call, signer);
    console.log("Submitted:", result);

    // 7. Wait for the receipt
    const receipt = await client.waitForReceipt(result.tx_hash as string);
    console.log("Receipt:", receipt);
    ```

=== "Python"

    ```python
    from dilithia_sdk import DilithiaClient
    from dilithia_sdk.crypto import load_native_crypto_adapter

    # 1. Create the client
    client = DilithiaClient("https://rpc.dilithia.network/rpc")

    # 2. Load the crypto adapter
    crypto = load_native_crypto_adapter()
    assert crypto is not None, "Native crypto bridge not available"

    # 3. Recover the account from a mnemonic
    mnemonic = "your twenty four word mnemonic phrase ..."
    account = crypto.recover_hd_wallet(mnemonic)

    print(f"Address: {account.address}")

    # 4. Build a contract call
    call = client.build_contract_call("wasm:token", "transfer", {
        "to": "dili1recipient...",
        "amount": 1000,
    })

    # 5. Simulate first (optional but recommended)
    try:
        sim_result = client.simulate(call)
        print(f"Simulation passed: {sim_result}")
    except RuntimeError as exc:
        print(f"Simulation failed: {exc}")
        raise SystemExit(1)

    # 6. Create a signer and submit the signed call
    class Signer:
        def sign_canonical_payload(self, payload_json: str) -> dict:
            sig = crypto.sign_message(account.secret_key, payload_json)
            return {
                "sender": account.address,
                "public_key": account.public_key,
                "algorithm": sig.algorithm,
                "signature": sig.signature,
            }

    result = client.send_signed_call(call, Signer())
    print(f"Submitted: {result}")

    # 7. Wait for the receipt
    receipt = client.wait_for_receipt(result["tx_hash"])
    print(f"Receipt: {receipt}")
    ```

=== "Python (async)"

    ```python
    import asyncio
    from dilithia_sdk import AsyncDilithiaClient
    from dilithia_sdk.crypto import load_async_native_crypto_adapter

    async def main():
        # 1. Create the async client
        client = AsyncDilithiaClient("https://rpc.dilithia.network/rpc")

        # 2. Load the async crypto adapter
        crypto = load_async_native_crypto_adapter()
        assert crypto is not None, "Native crypto bridge not available"

        # 3. Recover the account
        mnemonic = "your twenty four word mnemonic phrase ..."
        account = await crypto.recover_hd_wallet(mnemonic)

        # 4. Build the call
        call = client.build_contract_call("wasm:token", "transfer", {
            "to": "dili1recipient...",
            "amount": 1000,
        })

        # 5. Simulate
        sim_result = await client.simulate(call)
        print(f"Simulation: {sim_result}")

        # 6. Sign and submit
        class AsyncSigner:
            def sign_canonical_payload(self, payload_json: str) -> dict:
                # Note: signer is called synchronously by the client
                sync_crypto = load_native_crypto_adapter()
                sig = sync_crypto.sign_message(account.secret_key, payload_json)
                return {
                    "sender": account.address,
                    "public_key": account.public_key,
                    "algorithm": sig.algorithm,
                    "signature": sig.signature,
                }

        result = await client.send_signed_call(call, AsyncSigner())
        print(f"Submitted: {result}")

        # 7. Wait for receipt
        receipt = await client.wait_for_receipt(result["tx_hash"])
        print(f"Receipt: {receipt}")

    asyncio.run(main())
    ```

=== "Rust"

    ```rust
    use dilithia_sdk::{
        DilithiaClient, DilithiaCryptoAdapter, NativeCryptoAdapter,
    };
    use serde_json::json;

    fn main() -> Result<(), Box<dyn std::error::Error>> {
        // 1. Create the client
        let client = DilithiaClient::new("https://rpc.dilithia.network/rpc", None)?;

        // 2. The adapter is available directly in Rust
        let adapter = NativeCryptoAdapter;

        // 3. Recover the account
        let mnemonic = "your twenty four word mnemonic phrase ...";
        let account = adapter.recover_hd_wallet(mnemonic)?;

        println!("Address: {}", account.address);

        // 4. Build the call
        let call = client.build_contract_call(
            "wasm:token",
            "transfer",
            json!({"to": "dili1recipient...", "amount": 1000}),
            None,
        );

        // 5. Sign the canonical payload
        let payload_json = serde_json::to_string(&call)?;
        let sig = adapter.sign_message(&account.secret_key, &payload_json)?;

        // 6. Build the signed request
        let mut signed_call = call.clone();
        if let Some(obj) = signed_call.as_object_mut() {
            obj.insert("sender".to_string(), json!(account.address));
            obj.insert("public_key".to_string(), json!(account.public_key));
            obj.insert("algorithm".to_string(), json!(sig.algorithm));
            obj.insert("signature".to_string(), json!(sig.signature));
        }

        let request = client.send_call_request(signed_call);
        // Execute the request with your preferred HTTP client

        Ok(())
    }
    ```

---

## The Signer Interface

The `sendSignedCall` method expects a signer object that implements a single method:

=== "TypeScript"

    ```typescript
    interface Signer {
      signCanonicalPayload(payloadJson: string): Promise<Record<string, unknown>>;
    }
    ```

=== "Python"

    ```python
    class Signer:
        def sign_canonical_payload(self, payload_json: str) -> dict[str, Any]:
            ...
    ```

The signer receives the JSON-serialized call payload and must return a dictionary containing at minimum:

| Field        | Description                                   |
| ------------ | --------------------------------------------- |
| `sender`     | The sender's Dilithia address                 |
| `public_key` | Hex-encoded public key                        |
| `algorithm`  | Signature algorithm (e.g. `"mldsa65"`)        |
| `signature`  | Hex-encoded signature of the payload          |

These fields are merged into the call before submission.

---

## Using a Paymaster (Gas Sponsorship)

To have a gas sponsor pay for the transaction:

=== "TypeScript"

    ```typescript
    // Option 1: Attach paymaster when building the call
    const call = client.buildContractCall("wasm:token", "transfer", args, {
      paymaster: "gas_sponsor",
    });
    const result = await client.sendSignedCall(call, signer);

    // Option 2: Use sendSponsoredCall
    const call = client.buildContractCall("wasm:token", "transfer", args);
    const result = await client.sendSponsoredCall(call, "gas_sponsor", signer);

    // Option 3: Use the GasSponsorConnector
    const sponsor = new DilithiaGasSponsorConnector({
      client,
      sponsorContract: "wasm:gas_sponsor",
      paymaster: "gas_sponsor",
    });
    const result = await sponsor.sendSponsoredCall(call, signer);
    ```

=== "Python"

    ```python
    # Option 1: Attach paymaster when building the call
    call = client.build_contract_call(
        "wasm:token", "transfer", args, paymaster="gas_sponsor"
    )
    result = client.send_signed_call(call, signer)

    # Option 2: Use send_sponsored_call
    call = client.build_contract_call("wasm:token", "transfer", args)
    result = client.send_sponsored_call(call, "gas_sponsor", signer)

    # Option 3: Use the GasSponsorConnector
    sponsor = DilithiaGasSponsorConnector(
        client, "wasm:gas_sponsor", paymaster="gas_sponsor"
    )
    result = sponsor.send_sponsored_call(call, signer)
    ```

!!! tip
    Before submitting a sponsored call, use `buildAcceptQuery` on the sponsor connector to verify the sponsor will accept the call for the given user and method.

---

## HD Wallet Account Derivation

For applications managing multiple accounts from a single mnemonic:

```typescript
// Derive multiple accounts
const account0 = await crypto.recoverHdWalletAccount(mnemonic, 0);
const account1 = await crypto.recoverHdWalletAccount(mnemonic, 1);
const account2 = await crypto.recoverHdWalletAccount(mnemonic, 2);

// Or use seed-based derivation for more control
const seed = await crypto.seedFromMnemonic(mnemonic);
const childSeed0 = await crypto.deriveChildSeed(seed, 0);
const keypair0 = await crypto.keygenFromSeed(childSeed0);
```

---

## Verifying a Signature

To verify that a message was signed by a particular account:

```typescript
const isValid = await crypto.verifyMessage(
  account.publicKey,
  "the original message",
  signature.signature
);

if (!isValid) {
  throw new Error("Signature verification failed");
}
```

!!! warning
    Always verify signatures before trusting signed data, especially when received from external sources.
