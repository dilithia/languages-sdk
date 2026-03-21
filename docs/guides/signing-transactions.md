# Signing Transactions

This guide walks through the complete process of building, signing, and submitting a transaction to the Dilithia blockchain using the SDK in all five supported languages.

---

## Overview

The typical transaction flow is:

1. **Load the crypto adapter** for key management and signing
2. **Recover or generate a wallet** to obtain an account with keys
3. **Build the transaction payload** using the SDK's contract call helpers
4. **Sign the payload** with the account's secret key
5. **Submit the signed transaction** to the network
6. **Poll for the receipt** to confirm inclusion

---

## Full Example

=== "TypeScript"

    ```typescript
    import {
      DilithiaClient,
      loadNativeCryptoAdapter,
      type DilithiaCryptoAdapter,
      type DilithiaAccount,
      type SubmitResult,
      type Receipt,
      RpcError,
      TimeoutError,
    } from "@dilithia/sdk";

    async function main() {
      // 1. Load the crypto adapter
      const crypto = await loadNativeCryptoAdapter();
      if (!crypto) {
        throw new Error("Native crypto bridge not available");
      }

      // 2. Recover wallet from mnemonic
      const mnemonic = "your twenty four word mnemonic phrase ...";
      const account = await crypto.recoverHdWallet(mnemonic);
      console.log("Address:", account.address);

      // 3. Create client and build a contract call
      const client = new DilithiaClient({
        rpcUrl: "https://rpc.dilithia.network/rpc",
      });

      const call = client.buildContractCall("wasm:token", "transfer", {
        to: "dili1recipient...",
        amount: 1000,
      });

      // 4. Sign the payload
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

      // 5. Submit the signed transaction
      let result: SubmitResult;
      try {
        const simResult = await client.simulate(call);
        console.log("Simulation passed:", simResult);
        result = await client.sendSignedCall(call, signer);
        console.log("Submitted:", result.txHash);
      } catch (err) {
        if (err instanceof RpcError) {
          console.error("RPC error:", err.code, err.message);
        } else if (err instanceof TimeoutError) {
          console.error("Request timed out");
        }
        process.exit(1);
      }

      // 6. Poll for receipt
      const receipt: Receipt = await client.waitForReceipt(result.txHash);
      console.log("Receipt:", receipt);
    }

    main().catch(console.error);
    ```

=== "Python"

    ```python
    from dilithia_sdk import DilithiaClient
    from dilithia_sdk.crypto import load_native_crypto_adapter
    from dilithia_sdk.types import SubmitResult, Receipt
    from dilithia_sdk.errors import DilithiaError, RpcError, TimeoutError

    def main():
        # 1. Load the crypto adapter
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise RuntimeError("Native crypto bridge not available")

        # 2. Recover wallet from mnemonic
        mnemonic = "your twenty four word mnemonic phrase ..."
        account = crypto.recover_hd_wallet(mnemonic)
        print(f"Address: {account.address}")

        # 3. Create client and build a contract call
        client = DilithiaClient("https://rpc.dilithia.network/rpc")

        call = client.build_contract_call("wasm:token", "transfer", {
            "to": "dili1recipient...",
            "amount": 1000,
        })

        # 4. Sign the payload
        class Signer:
            def sign_canonical_payload(self, payload_json: str) -> dict:
                sig = crypto.sign_message(account.secret_key, payload_json)
                return {
                    "sender": account.address,
                    "public_key": account.public_key,
                    "algorithm": sig.algorithm,
                    "signature": sig.signature,
                }

        # 5. Submit the signed transaction
        try:
            sim_result = client.simulate(call)
            print(f"Simulation passed: {sim_result}")
            result: SubmitResult = client.send_signed_call(call, Signer())
            print(f"Submitted: {result.tx_hash}")
        except RpcError as exc:
            print(f"RPC error {exc.code}: {exc}")
            raise SystemExit(1)
        except DilithiaError as exc:
            print(f"Transaction failed: {exc}")
            raise SystemExit(1)

        # 6. Poll for receipt
        receipt: Receipt = client.wait_for_receipt(result.tx_hash)
        print(f"Receipt: {receipt}")

    if __name__ == "__main__":
        main()
    ```

=== "Python (async)"

    ```python
    import asyncio
    from dilithia_sdk import AsyncDilithiaClient
    from dilithia_sdk.crypto import (
        load_native_crypto_adapter,
        load_async_native_crypto_adapter,
    )
    from dilithia_sdk.types import SubmitResult, Receipt
    from dilithia_sdk.errors import DilithiaError, RpcError

    async def main():
        # 1. Load the async crypto adapter
        crypto = load_async_native_crypto_adapter()
        if crypto is None:
            raise RuntimeError("Native crypto bridge not available")

        # 2. Recover wallet from mnemonic
        mnemonic = "your twenty four word mnemonic phrase ..."
        account = await crypto.recover_hd_wallet(mnemonic)
        print(f"Address: {account.address}")

        # 3. Create async client (uses httpx for real async HTTP)
        client = AsyncDilithiaClient("https://rpc.dilithia.network/rpc")

        call = client.build_contract_call("wasm:token", "transfer", {
            "to": "dili1recipient...",
            "amount": 1000,
        })

        # 4. Sign the payload (signer is called synchronously by the client)
        sync_crypto = load_native_crypto_adapter()

        class AsyncSigner:
            def sign_canonical_payload(self, payload_json: str) -> dict:
                sig = sync_crypto.sign_message(account.secret_key, payload_json)
                return {
                    "sender": account.address,
                    "public_key": account.public_key,
                    "algorithm": sig.algorithm,
                    "signature": sig.signature,
                }

        # 5. Submit the signed transaction
        try:
            sim_result = await client.simulate(call)
            print(f"Simulation passed: {sim_result}")
            result: SubmitResult = await client.send_signed_call(call, AsyncSigner())
            print(f"Submitted: {result.tx_hash}")
        except RpcError as exc:
            print(f"RPC error {exc.code}: {exc}")
            raise SystemExit(1)
        except DilithiaError as exc:
            print(f"Transaction failed: {exc}")
            raise SystemExit(1)

        # 6. Poll for receipt
        receipt: Receipt = await client.wait_for_receipt(result.tx_hash)
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
        // 1. Load the crypto adapter (native in Rust)
        let adapter = NativeCryptoAdapter;

        // 2. Recover wallet from mnemonic
        let mnemonic = "your twenty four word mnemonic phrase ...";
        let account = adapter.recover_hd_wallet(mnemonic)?;
        println!("Address: {}", account.address);

        // 3. Create client and build a contract call
        let client = DilithiaClient::new(
            "https://rpc.dilithia.network/rpc",
            None,
        )?;

        let call = client.build_contract_call(
            "wasm:token",
            "transfer",
            json!({"to": "dili1recipient...", "amount": 1000}),
            None, // no paymaster
        );

        // 4. Sign the payload
        let payload_json = serde_json::to_string(&call)?;
        let sig = adapter.sign_message(&account.secret_key, &payload_json)?;

        // 5. Build the signed request and submit
        let mut signed_call = call.clone();
        if let Some(obj) = signed_call.as_object_mut() {
            obj.insert("sender".into(), json!(account.address));
            obj.insert("public_key".into(), json!(account.public_key));
            obj.insert("algorithm".into(), json!(sig.algorithm));
            obj.insert("signature".into(), json!(sig.signature));
        }

        let request = client.send_call_request(signed_call);
        // Execute `request` with your preferred HTTP client (reqwest, ureq, etc.)
        // The request is a DilithiaRequest::Post { path, body } enum variant.
        println!("Request built: {:?}", request);

        // 6. Poll for receipt
        // Use client.get_receipt_request(tx_hash) in a retry loop:
        // loop {
        //     let receipt_req = client.get_receipt_request(&tx_hash);
        //     match execute(receipt_req) {
        //         Ok(receipt) => { println!("{:?}", receipt); break; }
        //         Err(_) => std::thread::sleep(std::time::Duration::from_secs(1)),
        //     }
        // }

        Ok(())
    }
    ```

=== "Go"

    ```go
    package main

    import (
        "context"
        "encoding/json"
        "errors"
        "fmt"
        "log"
        "time"

        sdk "github.com/dilithia/languages-sdk/go/sdk"
    )

    func main() {
        ctx := context.Background()

        // 1. Load the crypto adapter
        // The CryptoAdapter interface is implemented by your chosen bridge.
        var crypto sdk.CryptoAdapter // = your_bridge.New()

        // 2. Recover wallet from mnemonic
        mnemonic := "your twenty four word mnemonic phrase ..."
        account, err := crypto.RecoverHDWallet(ctx, mnemonic)
        if err != nil {
            log.Fatalf("failed to recover wallet: %v", err)
        }
        fmt.Println("Address:", account.Address)

        // 3. Create client (functional options) and build a contract call
        client := sdk.NewClient(
            "https://rpc.dilithia.network/rpc",
            sdk.WithTimeout(10*time.Second),
        )

        call := client.BuildContractCall("wasm:token", "transfer", map[string]any{
            "to":     "dili1recipient...",
            "amount": 1000,
        }, "" /* no paymaster */)

        // 4. Sign the payload
        callJSON, err := json.Marshal(call)
        if err != nil {
            log.Fatalf("failed to marshal call: %v", err)
        }

        sig, err := crypto.SignMessage(ctx, account.SecretKey, string(callJSON))
        if err != nil {
            log.Fatalf("failed to sign: %v", err)
        }

        // 5. Submit the signed transaction
        call["sender"] = account.Address
        call["public_key"] = account.PublicKey
        call["algorithm"] = sig.Algorithm
        call["signature"] = sig.Signature

        result, err := client.SendCall(ctx, call)
        if err != nil {
            // Use errors.Is/errors.As with typed errors
            var rpcErr *sdk.RpcError
            if errors.As(err, &rpcErr) {
                log.Fatalf("RPC error %d: %v", rpcErr.Code, rpcErr)
            }
            log.Fatalf("failed to submit transaction: %v", err)
        }
        fmt.Println("Submitted:", result.TxHash)

        // 6. Poll for receipt (returns typed *Receipt)
        receipt, err := client.WaitForReceipt(ctx, result.TxHash, 12, time.Second)
        if err != nil {
            log.Fatalf("receipt not available: %v", err)
        }
        fmt.Println("Receipt:", receipt.Status, "block:", receipt.BlockHeight)
    }
    ```

=== "Java"

    ```java
    import org.dilithia.sdk.*;
    import org.dilithia.sdk.crypto.*;
    import org.dilithia.sdk.types.*;
    import java.time.Duration;
    import java.util.*;

    public class SigningExample {
        public static void main(String[] args) throws Exception {
            // 1. Load the crypto adapter
            DilithiaCryptoAdapter crypto = NativeCryptoAdapters.load()
                .orElseThrow(() -> new RuntimeException(
                    "Native crypto bridge not available"
                ));

            // 2. Recover wallet from mnemonic
            String mnemonic = "your twenty four word mnemonic phrase ...";
            DilithiaAccount account = crypto.recoverHdWallet(mnemonic);
            System.out.println("Address: " + account.address());

            // 3. Create client (builder pattern) and build a contract call
            var client = Dilithia.client("https://rpc.dilithia.network/rpc")
                .timeout(Duration.ofSeconds(10))
                .build();

            var call = client.buildContractCall(
                "wasm:token",
                "transfer",
                Map.of("to", "dili1recipient...", "amount", 1000),
                null // no paymaster
            );

            // 4. Sign the payload via the DilithiaSigner interface
            DilithiaSigner signer = canonicalPayload -> {
                String payloadJson = new com.google.gson.Gson().toJson(canonicalPayload);
                DilithiaSignature sig = crypto.signMessage(
                    account.secretKey().value(), payloadJson
                );
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("sender", account.address().value());
                fields.put("public_key", account.publicKey().value());
                fields.put("algorithm", sig.algorithm());
                fields.put("signature", sig.signature());
                return fields;
            };

            // 5. Submit the signed transaction
            SubmitResult result = client.sendSignedCall(call, signer).get();
            System.out.println("Submitted: " + result.txHash());

            // 6. Poll for receipt (returns typed Receipt)
            Receipt receipt = client.waitForReceipt(result.txHash(), 12, Duration.ofSeconds(1)).get();
            System.out.println("Receipt: " + receipt.status() + " block: " + receipt.blockHeight());
        }
    }
    ```

=== "C#"

    ```csharp
    using Dilithia.Sdk;
    using Dilithia.Sdk.Crypto;
    using System.Text.Json;

    // 1. Load the crypto adapter
    var crypto = new NativeCryptoBridge();

    // 2. Recover wallet from mnemonic
    var mnemonic = "your twenty four word mnemonic phrase ...";
    var account = crypto.RecoverHdWallet(mnemonic);
    Console.WriteLine($"Address: {account.Address}");

    // 3. Create client and build a contract call
    using var client = DilithiaClient.Create("https://rpc.dilithia.network/rpc").Build();

    var call = client.BuildContractCall("wasm:token", "transfer", new
    {
        to = "dili1recipient...",
        amount = 1000,
    });

    // 4. Sign the payload
    var nonce = await client.GetNonceAsync(Address.Of(account.Address));
    var canonical = DilithiaClient.BuildDeployCanonicalPayload(
        account.Address, "wasm:token", call, nonce, "dilithia-1"
    );
    var canonicalJson = JsonSerializer.Serialize(canonical);
    var sig = crypto.SignMessage(account.SecretKey, canonicalJson);

    // 5. Submit the signed transaction
    try
    {
        var simResult = await client.SimulateAsync(call);
        Console.WriteLine($"Simulation passed: {simResult}");
        var result = await client.SendSignedCallAsync(call, new DilithiaSigner
        {
            Sender = account.Address,
            PublicKey = account.PublicKey,
            Algorithm = sig.Algorithm,
            Signature = sig.Signature,
        });
        Console.WriteLine($"Submitted: {result.TxHash}");

        // 6. Poll for receipt
        var receipt = await client.WaitForReceiptAsync(result.TxHash);
        Console.WriteLine($"Receipt: {receipt.Status} block: {receipt.BlockHeight}");
    }
    catch (RpcException ex)
    {
        Console.Error.WriteLine($"RPC error {ex.Code}: {ex.Message}");
        Environment.Exit(1);
    }
    catch (TimeoutException ex)
    {
        Console.Error.WriteLine("Request timed out");
        Environment.Exit(1);
    }
    ```

---

## The Signer Interface

The signer is responsible for producing the cryptographic fields that authenticate a transaction. Each language implements it slightly differently:

=== "TypeScript"

    ```typescript
    interface Signer {
      signCanonicalPayload(payloadJson: string): Promise<{
        sender: Address;
        public_key: PublicKey;
        algorithm: string;
        signature: string;
      }>;
    }
    ```

=== "Python"

    ```python
    class Signer:
        def sign_canonical_payload(self, payload_json: str) -> dict[str, Any]:
            ...
    ```

=== "Rust"

    In Rust, there is no signer trait. You sign the payload manually and merge the
    fields into the call object before passing it to `send_call_request`.

=== "Go"

    In Go, there is no formal signer interface. You sign the JSON-serialized call
    with `crypto.SignMessage` and add the authentication fields to the call map
    before calling `client.SendCall`.

=== "Java"

    ```java
    @FunctionalInterface
    public interface DilithiaSigner {
        Map<String, Object> signCanonicalPayload(
            Map<String, Object> canonicalPayload
        );
    }
    ```

    Java's `DilithiaClient.sendSignedCallBody` accepts a `DilithiaSigner` and
    merges the returned fields into the call automatically.

=== "C#"

    ```csharp
    public record DilithiaSigner
    {
        public required string Sender { get; init; }
        public required string PublicKey { get; init; }
        public required string Algorithm { get; init; }
        public required string Signature { get; init; }
    }
    ```

    In C#, you build a `DilithiaSigner` record with the authentication fields
    and pass it to `SendSignedCallAsync`. The client merges the fields into
    the call automatically.

The signer must return (or merge) the following fields:

| Field        | Description                                   |
| ------------ | --------------------------------------------- |
| `sender`     | The sender's Dilithia address                 |
| `public_key` | Hex-encoded public key                        |
| `algorithm`  | Signature algorithm (e.g. `"mldsa65"`)        |
| `signature`  | Hex-encoded signature of the payload          |

---

## Using a Paymaster (Gas Sponsorship)

To have a gas sponsor pay for the transaction, attach a paymaster when building the call.

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

=== "Rust"

    ```rust
    // Option 1: Pass paymaster when building the call
    let call = client.build_contract_call(
        "wasm:token", "transfer", args, Some("gas_sponsor"),
    );

    // Option 2: Use with_paymaster on an existing call
    let call = client.with_paymaster(call, "gas_sponsor");

    // Option 3: Use the GasSponsorConnector
    let sponsor = DilithiaGasSponsorConnector::new(
        "wasm:gas_sponsor",
        Some("gas_sponsor".to_string()),
    );
    let call = sponsor.apply_paymaster(&client, call);
    ```

=== "Go"

    ```go
    // Option 1: Pass paymaster when building the call
    call := client.BuildContractCall(
        "wasm:token", "transfer", args, "gas_sponsor",
    )

    // Option 2: Use WithPaymaster on an existing call
    call = client.WithPaymaster(call, "gas_sponsor")

    // Option 3: Use the GasSponsorConnector
    sponsor := sdk.NewGasSponsorConnector(
        client, "wasm:gas_sponsor", "gas_sponsor",
    )
    call = sponsor.ApplyPaymaster(call)
    ```

=== "Java"

    ```java
    // Option 1: Pass paymaster when building the call
    var call = client.buildContractCall(
        "wasm:token", "transfer", args, "gas_sponsor"
    );

    // Option 2: Use withPaymaster on an existing call
    call = client.withPaymaster(call, "gas_sponsor");

    // Option 3: Use the GasSponsorConnector (not shown: import the class)
    // Builds accept queries and applies the paymaster address.
    ```

=== "C#"

    ```csharp
    // Option 1: Pass paymaster when building the call
    var call = client.BuildContractCall(
        "wasm:token", "transfer", args, paymaster: "gas_sponsor"
    );

    // Option 2: Use WithPaymaster on an existing call
    call = client.WithPaymaster(call, "gas_sponsor");

    // Option 3: Use the GasSponsorConnector
    var sponsor = new DilithiaGasSponsorConnector(
        client, "wasm:gas_sponsor", "gas_sponsor"
    );
    call = sponsor.ApplyPaymaster(call);
    ```

!!! tip
    Before submitting a sponsored call, use the sponsor connector's `buildAcceptQuery` to verify the sponsor will accept the call for the given user and method.

---

## HD Wallet Account Derivation

For applications managing multiple accounts from a single mnemonic:

=== "TypeScript"

    ```typescript
    // Derive multiple accounts by index
    const account0 = await crypto.recoverHdWalletAccount(mnemonic, 0);
    const account1 = await crypto.recoverHdWalletAccount(mnemonic, 1);
    const account2 = await crypto.recoverHdWalletAccount(mnemonic, 2);

    // Or use seed-based derivation for more control
    const seed = await crypto.seedFromMnemonic(mnemonic);
    const childSeed0 = await crypto.deriveChildSeed(seed, 0);
    const keypair0 = await crypto.keygenFromSeed(childSeed0);
    ```

=== "Python"

    ```python
    # Derive multiple accounts by index
    account0 = crypto.recover_hd_wallet_account(mnemonic, 0)
    account1 = crypto.recover_hd_wallet_account(mnemonic, 1)
    account2 = crypto.recover_hd_wallet_account(mnemonic, 2)

    # Or use seed-based derivation
    seed = crypto.seed_from_mnemonic(mnemonic)
    child_seed = crypto.derive_child_seed(seed, 0)
    keypair = crypto.keygen_from_seed(child_seed)
    ```

=== "Rust"

    ```rust
    // Derive multiple accounts by index
    let account0 = adapter.recover_hd_wallet_account(mnemonic, 0)?;
    let account1 = adapter.recover_hd_wallet_account(mnemonic, 1)?;
    let account2 = adapter.recover_hd_wallet_account(mnemonic, 2)?;

    // Or use seed-based derivation
    let seed = adapter.seed_from_mnemonic(mnemonic)?;
    let child_seed = adapter.derive_child_seed(&seed, 0)?;
    let keypair = adapter.keygen_from_seed(&child_seed)?;
    ```

=== "Go"

    ```go
    // Derive multiple accounts by index
    account0, err := crypto.RecoverHDWalletAccount(ctx, mnemonic, 0)
    account1, err := crypto.RecoverHDWalletAccount(ctx, mnemonic, 1)
    account2, err := crypto.RecoverHDWalletAccount(ctx, mnemonic, 2)

    // Or use seed-based derivation
    seed, err := crypto.SeedFromMnemonic(ctx, mnemonic)
    childSeed, err := crypto.DeriveChildSeed(ctx, seed, 0)
    keypair, err := crypto.KeygenFromSeed(ctx, childSeed)
    ```

=== "Java"

    ```java
    // Derive multiple accounts by index
    DilithiaAccount account0 = crypto.recoverHdWalletAccount(mnemonic, 0);
    DilithiaAccount account1 = crypto.recoverHdWalletAccount(mnemonic, 1);
    DilithiaAccount account2 = crypto.recoverHdWalletAccount(mnemonic, 2);

    // Or use seed-based derivation
    String seed = crypto.seedFromMnemonic(mnemonic);
    String childSeed = crypto.deriveChildSeed(seed, 0);
    DilithiaKeypair keypair = crypto.keygenFromSeed(childSeed);
    ```

=== "C#"

    ```csharp
    // Derive multiple accounts by index
    var account0 = crypto.RecoverHdWalletAccount(mnemonic, 0);
    var account1 = crypto.RecoverHdWalletAccount(mnemonic, 1);
    var account2 = crypto.RecoverHdWalletAccount(mnemonic, 2);

    // Or use seed-based derivation
    var seed = crypto.SeedFromMnemonic(mnemonic);
    var childSeed = crypto.DeriveChildSeed(seed, 0);
    var keypair = crypto.KeygenFromSeed(childSeed);
    ```

---

## Verifying a Signature

To verify that a message was signed by a particular account:

=== "TypeScript"

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

=== "Python"

    ```python
    is_valid = crypto.verify_message(
        account.public_key, "the original message", signature.signature
    )
    if not is_valid:
        raise ValueError("Signature verification failed")
    ```

=== "Rust"

    ```rust
    let is_valid = adapter.verify_message(
        &account.public_key,
        "the original message",
        &sig.signature,
    )?;

    if !is_valid {
        return Err("Signature verification failed".into());
    }
    ```

=== "Go"

    ```go
    isValid, err := crypto.VerifyMessage(
        ctx, account.PublicKey,
        "the original message", sig.Signature,
    )
    if err != nil {
        log.Fatalf("verify error: %v", err)
    }
    if !isValid {
        log.Fatal("Signature verification failed")
    }
    ```

=== "Java"

    ```java
    boolean isValid = crypto.verifyMessage(
        account.publicKey(),
        "the original message",
        sig.signature()
    );
    if (!isValid) {
        throw new RuntimeException("Signature verification failed");
    }
    ```

=== "C#"

    ```csharp
    var isValid = crypto.VerifyMessage(
        account.PublicKey,
        "the original message",
        sig.Signature
    );
    if (!isValid)
    {
        throw new InvalidOperationException("Signature verification failed");
    }
    ```

!!! warning
    Always verify signatures before trusting signed data, especially when received from external sources.
