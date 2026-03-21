# Getting Started

Install the SDK for your language, load the native crypto bridge, and run a complete sign-and-verify flow.

## Installation

### SDK packages

=== "TypeScript"

    Requires **Node.js 22** or later.

    ```bash
    npm install @dilithia/sdk-node
    ```

=== "Python"

    Requires **Python 3.11** or later.

    ```bash
    pip install dilithia-sdk
    ```

=== "Rust"

    Add to your `Cargo.toml`:

    ```toml
    [dependencies]
    dilithia-sdk-rust = "0.3.0"
    ```

=== "Go"

    Requires **Go 1.22** or later.

    ```bash
    go get github.com/dilithia/languages-sdk/go@latest
    ```

=== "Java"

    Requires **Java 17** or later. Add to your `pom.xml`:

    ```xml
    <dependency>
      <groupId>org.dilithia</groupId>
      <artifactId>dilithia-sdk-java</artifactId>
      <version>0.3.0</version>
    </dependency>
    ```

### Native crypto bridges

The native bridge gives you production-grade ML-DSA-65 performance by calling `dilithia-core` (Rust) directly.

=== "TypeScript"

    ```bash
    npm install @dilithia/sdk-native
    ```

    The SDK auto-discovers `@dilithia/sdk-native` at runtime -- no extra configuration needed.

=== "Python"

    ```bash
    pip install dilithia-sdk-native
    ```

    The SDK auto-discovers `dilithia_sdk_native` at runtime -- no extra configuration needed.

=== "Rust"

    The native adapter is built in. `dilithia-sdk-rust` depends on `dilithia-core` directly, so `NativeCryptoAdapter` is always available.

=== "Go"

    The Go SDK loads the native shared library via cgo + dlopen. Build with cgo enabled and point the environment variable to the compiled `dilithia-core` library:

    ```bash
    export DILITHIUM_NATIVE_CORE_LIB=/path/to/libdilithia_core.so
    ```

    Then call `sdk.LoadNativeCryptoAdapter()` in your code. Without this variable, `LoadNativeCryptoAdapter` returns `ErrNativeCryptoUnavailable`.

=== "Java"

    The Java SDK loads the native shared library via JNA. Point the environment variable to the compiled `dilithia-core` library:

    ```bash
    export DILITHIUM_NATIVE_CORE_LIB=/path/to/libdilithia_core.so
    ```

    Then call `NativeCryptoAdapters.load()` in your code. Without this variable, the method returns `Optional.empty()`.

---

## End-to-end example

The following example performs a complete flow in each language:

1. Create an RPC client
2. Load the native crypto adapter
3. Generate a mnemonic and recover a wallet
4. Sign a message
5. Verify the signature

=== "TypeScript"

    ```typescript
    import {
      DilithiaClient,
      loadNativeCryptoAdapter,
    } from "@dilithia/sdk-node";

    // 1. Create client
    const client = new DilithiaClient({
      rpcUrl: "https://rpc.dilithia.network/rpc",
    });

    // 2. Load native crypto adapter
    const crypto = await loadNativeCryptoAdapter();
    if (!crypto) throw new Error("Native bridge not available");

    // 3. Generate mnemonic + recover wallet
    const mnemonic = await crypto.generateMnemonic();
    const account = await crypto.recoverHdWallet(mnemonic);
    console.log("Address:", account.address);
    console.log("Public key:", account.publicKey);

    // 4. Sign a message
    const signature = await crypto.signMessage(account.secretKey, "hello dilithia");
    console.log("Algorithm:", signature.algorithm); // "mldsa65"
    console.log("Signature:", signature.signature);

    // 5. Verify the signature
    const valid = await crypto.verifyMessage(
      account.publicKey,
      "hello dilithia",
      signature.signature,
    );
    console.log("Valid:", valid); // true
    ```

=== "Python"

    ```python
    from dilithia_sdk import DilithiaClient, load_native_crypto_adapter

    # 1. Create client
    client = DilithiaClient("https://rpc.dilithia.network/rpc")

    # 2. Load native crypto adapter
    crypto = load_native_crypto_adapter()
    assert crypto is not None, "Native bridge not available"

    # 3. Generate mnemonic + recover wallet
    mnemonic = crypto.generate_mnemonic()
    account = crypto.recover_hd_wallet(mnemonic)
    print("Address:", account.address)
    print("Public key:", account.public_key)

    # 4. Sign a message
    signature = crypto.sign_message(account.secret_key, "hello dilithia")
    print("Algorithm:", signature.algorithm)  # "mldsa65"
    print("Signature:", signature.signature)

    # 5. Verify the signature
    valid = crypto.verify_message(
        account.public_key, "hello dilithia", signature.signature
    )
    print("Valid:", valid)  # True
    ```

=== "Rust"

    ```rust
    use dilithia_sdk_rust::{
        DilithiaClient, DilithiaCryptoAdapter, NativeCryptoAdapter,
    };

    fn main() -> Result<(), Box<dyn std::error::Error>> {
        // 1. Create client
        let client = DilithiaClient::new("https://rpc.dilithia.network/rpc", None)?;

        // 2. Load native crypto adapter (built in)
        let crypto = NativeCryptoAdapter;

        // 3. Generate mnemonic + recover wallet
        let mnemonic = crypto.generate_mnemonic()?;
        let account = crypto.recover_hd_wallet(&mnemonic)?;
        println!("Address: {}", account.address);
        println!("Public key: {}", account.public_key);

        // 4. Sign a message
        let signature = crypto.sign_message(&account.secret_key, "hello dilithia")?;
        println!("Algorithm: {}", signature.algorithm); // "mldsa65"
        println!("Signature: {}", signature.signature);

        // 5. Verify the signature
        let valid = crypto.verify_message(
            &account.public_key,
            "hello dilithia",
            &signature.signature,
        )?;
        println!("Valid: {}", valid); // true

        Ok(())
    }
    ```

=== "Go"

    ```go
    package main

    import (
        "context"
        "fmt"
        "log"
        "time"

        sdk "github.com/dilithia/languages-sdk/go/sdk"
    )

    func main() {
        ctx := context.Background()

        // 1. Create client (functional options)
        client := sdk.NewClient("https://rpc.dilithia.network/rpc", sdk.WithTimeout(10*time.Second))
        _ = client // use client for RPC calls

        // 2. Load native crypto adapter
        //    Requires: export DILITHIUM_NATIVE_CORE_LIB=/path/to/libdilithia_core.so
        crypto, err := sdk.LoadNativeCryptoAdapter()
        if err != nil {
            log.Fatal("Native bridge not available:", err)
        }

        // 3. Generate mnemonic + recover wallet
        mnemonic, err := crypto.GenerateMnemonic(ctx)
        if err != nil {
            log.Fatal(err)
        }
        account, err := crypto.RecoverHDWallet(ctx, mnemonic)
        if err != nil {
            log.Fatal(err)
        }
        fmt.Println("Address:", account.Address)
        fmt.Println("Public key:", account.PublicKey)

        // 4. Sign a message
        signature, err := crypto.SignMessage(ctx, account.SecretKey, "hello dilithia")
        if err != nil {
            log.Fatal(err)
        }
        fmt.Println("Algorithm:", signature.Algorithm) // "mldsa65"
        fmt.Println("Signature:", signature.Signature)

        // 5. Verify the signature
        valid, err := crypto.VerifyMessage(ctx, account.PublicKey, "hello dilithia", signature.Signature)
        if err != nil {
            log.Fatal(err)
        }
        fmt.Println("Valid:", valid) // true
    }
    ```

=== "Java"

    ```java
    import org.dilithia.sdk.*;
    import org.dilithia.sdk.crypto.*;
    import org.dilithia.sdk.types.*;
    import java.time.Duration;

    public class Main {
        public static void main(String[] args) {
            // 1. Create client (builder pattern)
            var client = Dilithia.client("https://rpc.dilithia.network/rpc")
                .timeout(Duration.ofSeconds(10))
                .build();

            // 2. Load native crypto adapter
            //    Requires: export DILITHIUM_NATIVE_CORE_LIB=/path/to/libdilithia_core.so
            DilithiaCryptoAdapter crypto = NativeCryptoAdapters.load()
                    .orElseThrow(() -> new RuntimeException("Native bridge not available"));

            // 3. Generate mnemonic + recover wallet
            String mnemonic = crypto.generateMnemonic();
            DilithiaAccount account = crypto.recoverHdWallet(mnemonic);
            System.out.println("Address: " + account.address());
            System.out.println("Public key: " + account.publicKey());

            // 4. Sign a message
            DilithiaSignature signature = crypto.signMessage(account.secretKey(), "hello dilithia");
            System.out.println("Algorithm: " + signature.algorithm()); // "mldsa65"
            System.out.println("Signature: " + signature.signature());

            // 5. Verify the signature
            boolean valid = crypto.verifyMessage(
                    account.publicKey(), "hello dilithia", signature.signature()
            );
            System.out.println("Valid: " + valid); // true
        }
    }
    ```

---

## What next?

- [Crypto Adapter API Reference](api/crypto.md) -- All 25 cryptographic methods documented
- [RPC Client Reference](api/client.md) -- Full client surface for queries and transactions
- [Signing Transactions Guide](guides/signing-transactions.md) -- End-to-end transaction walkthrough
- [Sync vs Async](guides/sync-async.md) -- Choosing the right adapter pattern (TypeScript and Python offer both)
