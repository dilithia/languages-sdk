# Getting Started

This guide covers installation and basic usage for every supported language.

## Installation

=== "TypeScript / Node.js"

    Requires Node.js 22 or later.

    ```bash
    npm install @dilithia/sdk-node
    ```

    For native crypto performance (recommended for production):

    ```bash
    npm install @dilithia/sdk-native
    ```

=== "Python"

    Requires Python 3.11 or later.

    ```bash
    pip install dilithia-sdk
    ```

    For native crypto performance (recommended for production):

    ```bash
    pip install dilithia-sdk-native
    ```

=== "Rust"

    Add to your `Cargo.toml`:

    ```toml
    [dependencies]
    dilithia-sdk-rust = "0.2.0"
    ```

=== "Go"

    Requires Go 1.21 or later.

    ```bash
    go get github.com/dilithia/languages-sdk/go
    ```

    For native crypto support, ensure the `native-core` shared library is available:

    ```bash
    export DILITHIUM_NATIVE_CORE_LIB=/path/to/libdilithia_core.so
    ```

=== "Java"

    Add to your `pom.xml`:

    ```xml
    <dependency>
      <groupId>org.dilithia</groupId>
      <artifactId>dilithia-sdk-java</artifactId>
      <version>0.2.0</version>
    </dependency>
    ```

    For native crypto support:

    ```xml
    <dependency>
      <groupId>org.dilithia</groupId>
      <artifactId>dilithia-sdk-native</artifactId>
      <version>0.2.0</version>
    </dependency>
    ```

## Quick Example

### Create a client and query a balance

=== "TypeScript"

    ```typescript
    import { DilithiaClient } from "@dilithia/sdk-node";

    const client = new DilithiaClient({
      rpcUrl: "https://rpc.dilithia.network/rpc",
    });

    const balance = await client.getBalance("dili1abc...");
    console.log(balance);
    ```

=== "Python"

    ```python
    from dilithia_sdk import DilithiaClient

    client = DilithiaClient("https://rpc.dilithia.network/rpc")
    balance = client.get_balance("dili1abc...")
    print(balance)
    ```

=== "Rust"

    ```rust
    use dilithia_sdk_rust::DilithiaClient;

    let client = DilithiaClient::new("https://rpc.dilithia.network/rpc", None)?;
    let request = client.get_balance_request("dili1abc...");
    // Execute the request with your preferred HTTP client
    ```

=== "Go"

    ```go
    import sdk "github.com/dilithia/languages-sdk/go/sdk"

    client := sdk.NewClient("https://rpc.dilithia.network/rpc", 0)
    balance, err := client.GetBalance(context.Background(), "dili1abc...")
    ```

=== "Java"

    ```java
    import org.dilithia.sdk.DilithiaClient;

    var client = new DilithiaClient("https://rpc.dilithia.network/rpc");
    var balance = client.getBalance("dili1abc...");
    ```

### Generate a wallet and sign a message

=== "TypeScript"

    ```typescript
    import { loadNativeCryptoAdapter } from "@dilithia/sdk-node";

    const crypto = await loadNativeCryptoAdapter();
    if (!crypto) throw new Error("Native bridge not available");

    const mnemonic = await crypto.generateMnemonic();
    const account = await crypto.recoverHdWallet(mnemonic);

    const signature = await crypto.signMessage(account.secretKey, "hello");
    const valid = await crypto.verifyMessage(
      account.publicKey,
      "hello",
      signature.signature
    );
    console.log("Valid:", valid); // true
    ```

=== "Python"

    ```python
    from dilithia_sdk import load_native_crypto_adapter

    crypto = load_native_crypto_adapter()
    assert crypto is not None, "Native bridge not available"

    mnemonic = crypto.generate_mnemonic()
    account = crypto.recover_hd_wallet(mnemonic)

    signature = crypto.sign_message(account.secret_key, "hello")
    valid = crypto.verify_message(
        account.public_key, "hello", signature.signature
    )
    print("Valid:", valid)  # True
    ```

=== "Rust"

    ```rust
    use dilithia_sdk_rust::{DilithiaCryptoAdapter, NativeCryptoAdapter};

    let adapter = NativeCryptoAdapter;

    let mnemonic = adapter.generate_mnemonic()?;
    let account = adapter.recover_hd_wallet(&mnemonic)?;

    let signature = adapter.sign_message(&account.secret_key, "hello")?;
    let valid = adapter.verify_message(
        &account.public_key, "hello", &signature.signature
    )?;
    println!("Valid: {}", valid); // true
    ```

## What Next?

- [Crypto Adapter API Reference](api/crypto.md) -- All 25 cryptographic methods documented
- [RPC Client Reference](api/client.md) -- Full client surface for queries and transactions
- [Signing Transactions Guide](guides/signing-transactions.md) -- End-to-end transaction walkthrough
- [Sync vs Async](guides/sync-async.md) -- Choosing the right adapter pattern
