# Dilithia SDK for Java

**`org.dilithia:dilithia-sdk-java`** | Version **0.3.0** | Java 17+

Type-safe, fluent JVM SDK for the Dilithia post-quantum blockchain -- query balances, call contracts, deploy code, and sign transactions with ML-DSA-65 cryptography.

[Full documentation](https://docs.dilithia.network) | [Javadoc](https://docs.dilithia.network/javadoc) | [GitHub](https://github.com/dilithia/languages-sdk)

---

## Installation

### Maven

```xml
<dependency>
    <groupId>org.dilithia</groupId>
    <artifactId>dilithia-sdk-java</artifactId>
    <version>0.3.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("org.dilithia:dilithia-sdk-java:0.3.0")
```

### Gradle (Groovy DSL)

```groovy
implementation 'org.dilithia:dilithia-sdk-java:0.3.0'
```

---

## Quick Start

```java
import org.dilithia.sdk.Dilithia;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.model.*;

import java.time.Duration;
import java.util.Map;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        // 1. Create a client
        try (var client = Dilithia.client("http://localhost:8000/rpc")
                .timeout(Duration.ofSeconds(15))
                .jwt("your-auth-token")
                .build()) {

            var alice = Address.of("dili1alice");
            var bob   = Address.of("dili1bob");

            // 2. Check balance
            Balance balance = client.balance(alice).get();
            System.out.println("Balance: " + balance.asDili().formatted() + " DILI");

            // 3. Transfer tokens
            Receipt receipt = client.contract("token")
                    .call("transfer", Map.of("to", bob.value(), "amount", 1_000))
                    .send(signer);

            System.out.println("Tx: " + receipt.txHash());
            System.out.println("Status: " + receipt.status());

            // 4. Wait for a receipt (if you only have a tx hash)
            Receipt confirmed = client.receipt(receipt.txHash())
                    .waitFor(30, Duration.ofSeconds(2));

            System.out.println("Confirmed at block " + confirmed.blockHeight());
        }
    }
}
```

---

## Client Builder

`DilithiaClient` is created through a fluent builder obtained from `Dilithia.client(rpcUrl)`. The resulting client is **immutable** and **thread-safe**.

```java
DilithiaClient client = Dilithia.client("http://localhost:8000/rpc")
        .timeout(Duration.ofSeconds(15))          // HTTP request timeout (default: 10s)
        .timeoutMs(15_000)                         // Alternative: timeout in milliseconds
        .jwt("eyJhbG...")                          // Bearer token for authentication
        .header("X-Request-Id", "abc-123")         // Custom header on every request
        .headers(Map.of("X-Org", "acme"))          // Bulk custom headers
        .chainBaseUrl("http://node:9000")           // Override REST base URL
        .indexerUrl("http://indexer:7000")           // Indexer service URL
        .oracleUrl("http://oracle:6000")            // Oracle service URL
        .wsUrl("ws://node:9000")                    // Override WebSocket URL
        .httpClient(myHttpClient)                   // Supply a pre-configured HttpClient
        .build();
```

| Method | Default | Description |
|---|---|---|
| `timeout(Duration)` | 10 seconds | HTTP request timeout |
| `timeoutMs(long)` | 10000 | Timeout in milliseconds (convenience) |
| `jwt(String)` | none | Sets the `Authorization: Bearer` header |
| `header(name, value)` | none | Adds a single custom HTTP header |
| `headers(Map)` | none | Adds multiple custom HTTP headers |
| `chainBaseUrl(String)` | derived from RPC URL | Override the REST endpoint base |
| `indexerUrl(String)` | none | Indexer service URL |
| `oracleUrl(String)` | none | Oracle service URL |
| `wsUrl(String)` | derived from base URL | Override the WebSocket URL |
| `httpClient(HttpClient)` | internally created | Supply your own `java.net.http.HttpClient` |

`DilithiaClient` implements `AutoCloseable`. Use try-with-resources to release the underlying HTTP transport when done.

---

## Fluent API Reference

Every operation begins from a `DilithiaClient` instance and returns a request object with terminal methods like `.get()`, `.getAsync()`, or `.send(signer)`.

### Balance

```java
// Synchronous
Balance balance = client.balance("dili1alice").get();
System.out.println(balance.asDili().formatted() + " DILI");

// Asynchronous
CompletableFuture<Balance> future = client.balance("dili1alice").getAsync();
```

Accepts `Address` or `String`. Returns a `Balance` record with `address()`, `balance()`, `asDili()`, and `asTokenAmount(decimals)`.

### Nonce

```java
Nonce nonce = client.nonce("dili1alice").get();

// Async
CompletableFuture<Nonce> future = client.nonce("dili1alice").getAsync();
```

### Receipt

```java
// Fetch immediately
Receipt receipt = client.receipt("0xabc123...").get();

// Async
CompletableFuture<Receipt> future = client.receipt("0xabc123...").getAsync();

// Poll until available (up to 30 attempts, 2s apart)
Receipt receipt = client.receipt("0xabc123...").waitFor(30, Duration.ofSeconds(2));
```

The `waitFor` method retries on HTTP 404 (receipt not yet indexed) and throws `DilithiaException` if all attempts are exhausted.

### Network

```java
NetworkInfo head    = client.network().head();
GasEstimate gas     = client.network().gasEstimate();
long        baseFee = client.network().baseFee();

// Each has an async variant
CompletableFuture<NetworkInfo> headFuture = client.network().headAsync();
CompletableFuture<GasEstimate> gasFuture  = client.network().gasEstimateAsync();
CompletableFuture<Long>        feeFuture  = client.network().baseFeeAsync();
```

### Contract Call (Mutating)

```java
Receipt receipt = client.contract("token")
        .call("transfer", Map.of("to", "dili1bob", "amount", 100))
        .withPaymaster("dili1sponsor")   // optional gas sponsorship
        .send(signer);

// Async
CompletableFuture<Receipt> future = client.contract("token")
        .call("transfer", Map.of("to", "dili1bob", "amount", 100))
        .sendAsync(signer);
```

The `.call()` method returns a `ContractCallBuilder`. Calling `.send(signer)` builds a canonical payload, signs it with the provided `DilithiaSigner`, and POSTs the signed transaction.

### Contract Query (Read-Only)

```java
QueryResult result = client.contract("token").query("totalSupply").get();

// With arguments
QueryResult result = client.contract("token")
        .query("balanceOf", Map.of("account", "dili1alice"))
        .get();

// Async
CompletableFuture<QueryResult> future = client.contract("token")
        .query("totalSupply")
        .getAsync();
```

### Contract ABI

```java
ContractAbi abi = client.contract("token").abi().get();

CompletableFuture<ContractAbi> future = client.contract("token").abi().getAsync();
```

### Name Service

```java
// Forward resolve: name -> address
NameRecord record = client.names().resolve("alice.dili").get();

// Reverse resolve: address -> name
NameRecord reverse = client.names().reverseResolve("dili1abc...").get();

// Async variants
CompletableFuture<NameRecord> future = client.names().resolve("alice.dili").getAsync();
```

### Deploy

```java
var payload = DeployPayload.builder()
        .name("my_token")
        .bytecodeFile(Path.of("target/my_token.wasm"))
        .chainId("dilithia")
        .build();

Receipt receipt = client.deploy(payload).send(signer);

// Async
CompletableFuture<Receipt> future = client.deploy(payload).sendAsync(signer);
```

### Upgrade

```java
Receipt receipt = client.upgrade(payload).send(signer);
```

### Shielded Transactions

```java
// Deposit into the shielded pool
Receipt deposit = client.shielded()
        .deposit(commitment, value, proof)
        .send(signer);

// Withdraw from the shielded pool
Receipt withdraw = client.shielded()
        .withdraw(nullifier, amount, recipient, proof, root)
        .send(signer);

// Query the current commitment root
QueryResult root = client.shielded().commitmentRoot().get();

// Check if a nullifier has been spent
QueryResult spent = client.shielded().isNullifierSpent(nullifier).get();
```

All shielded mutating operations also have `.sendAsync(signer)` variants, and all queries have `.getAsync()`.

### Generic RPC

```java
// Call any JSON-RPC method directly
RpcResponse resp = client.rpc("qsc_customMethod", Map.of("key", "value")).get();

// Async
CompletableFuture<RpcResponse> future = client.rpc("qsc_customMethod", params).getAsync();
```

### Address Summary

```java
AddressSummary summary = client.addressSummary("dili1alice").get();
```

---

## Typed Domain Objects

The SDK uses Java records to enforce type safety and prevent the accidental misuse of plain strings.

| Type | Description | Factory |
|---|---|---|
| `Address` | Account address (typically `dili1...`) | `Address.of("dili1alice")` |
| `TxHash` | Transaction hash (hex string) | `TxHash.of("0xabc...")` |
| `PublicKey` | ML-DSA-65 public key (hex-encoded) | `PublicKey.of("04ab...")` |
| `SecretKey` | ML-DSA-65 secret key (hex-encoded, redacted `toString`) | `SecretKey.of("...")` |

All domain objects validate that their value is non-null and non-blank at construction time. `SecretKey.toString()` always returns `"SecretKey[REDACTED]"` to prevent accidental exposure in logs.

### TokenAmount

`TokenAmount` handles the conversion between human-readable decimal values and raw on-chain integers without precision loss.

```java
// Create from a decimal string (18 decimals for DILI)
TokenAmount five = TokenAmount.dili("5.0");

// Create from whole tokens
TokenAmount ten = TokenAmount.dili(10);

// Convert to raw on-chain integer
BigInteger raw = five.toRaw();  // 5_000_000_000_000_000_000

// Round-trip from raw
TokenAmount back = TokenAmount.fromRaw(raw, 18);

// Human-readable formatting (trailing zeros stripped)
String display = five.formatted();  // "5"
```

### Token

The `Token` record describes a token's metadata and provides a convenient factory for creating amounts.

```java
// The native DILI token constant
Token dili = Token.DILI;  // address="token", name="Dilithia", symbol="DILI", decimals=18

// Create a TokenAmount from the token definition
TokenAmount amount = Token.DILI.amount("2.5");

// Create from raw on-chain value
TokenAmount raw = Token.DILI.amountRaw(1_000_000_000_000_000_000L);
```

---

## Async Support

Every request object provides both synchronous and asynchronous terminal methods. Async methods return `CompletableFuture` and integrate naturally with Java's concurrency primitives.

```java
// Fire multiple queries in parallel
var balanceFuture = client.balance("dili1alice").getAsync();
var nonceFuture   = client.nonce("dili1alice").getAsync();
var headFuture    = client.network().headAsync();

// Compose results
CompletableFuture.allOf(balanceFuture, nonceFuture, headFuture).join();

Balance     balance = balanceFuture.get();
Nonce       nonce   = nonceFuture.get();
NetworkInfo head    = headFuture.get();

System.out.printf("Balance: %s DILI, Nonce: %s, Block: %d%n",
        balance.asDili().formatted(), nonce, head);
```

```java
// Chain an async contract call with receipt polling
client.contract("token")
        .call("transfer", Map.of("to", "dili1bob", "amount", 500))
        .sendAsync(signer)
        .thenApply(receipt -> {
            System.out.println("Tx submitted: " + receipt.txHash());
            return receipt;
        })
        .exceptionally(ex -> {
            System.err.println("Transfer failed: " + ex.getMessage());
            return null;
        });
```

---

## Gas Sponsorship

Use `GasSponsorConnector` to let a third party cover gas fees for your users.

```java
import org.dilithia.sdk.connector.GasSponsorConnector;

var sponsor = new GasSponsorConnector(client, "wasm:gas_sponsor", "gas_sponsor");

// Check if the sponsor will cover a specific call
boolean accepted = sponsor.acceptsCall("dili1alice", "token", "transfer");

// Query remaining gas quota for a user
long quota = sponsor.remainingQuota("dili1alice");

// Apply the paymaster to a contract call
var call = client.contract("token")
        .call("transfer", Map.of("to", "dili1bob", "amount", 100));
// The GasSponsorConnector is used alongside ContractCallBuilder from
// org.dilithia.sdk.ContractCallBuilder for legacy call construction:
sponsor.applyPaymaster(legacyCallBuilder);

// Or use the fluent API directly with withPaymaster:
Receipt receipt = client.contract("token")
        .call("transfer", Map.of("to", "dili1bob", "amount", 100))
        .withPaymaster("gas_sponsor")
        .send(signer);
```

---

## Crypto Adapter

The `DilithiaCryptoAdapter` interface bridges to the native Dilithia post-quantum crypto library (ML-DSA-65) via JNA. It provides key generation, HD wallet derivation, signing, and verification.

### Loading the Native Bridge

The SDK uses [JNA](https://github.com/java-native-access/jna) (`net.java.dev.jna:jna:5.14.0`) to call the native Dilithia crypto library. Ensure the shared library (`libdilithia_crypto`) is available on `java.library.path` or in the standard system library directories.

### Key Operations

```java
DilithiaCryptoAdapter crypto = /* obtain implementation */;

// Generate a new mnemonic
String mnemonic = crypto.generateMnemonic();

// Validate a mnemonic
crypto.validateMnemonic(mnemonic);  // throws ValidationException if invalid

// Recover an HD wallet
DilithiaAccount account = crypto.recoverHdWallet(mnemonic);

// Derive a specific account index
DilithiaAccount child = crypto.recoverHdWalletAccount(mnemonic, 1);

// Generate a new keypair
DilithiaKeypair keypair = crypto.keygen();
```

### Signing and Verification

```java
// Sign a message
DilithiaSignature sig = crypto.signMessage(secretKey, "Hello Dilithia");

// Verify a signature
boolean valid = crypto.verifyMessage(publicKey, "Hello Dilithia", sig.hex());
```

### Address Derivation

```java
Address addr = crypto.addressFromPublicKey(publicKey);
Address checksummed = crypto.addressFromPkChecksummed(publicKey);
Address validated = crypto.validateAddress("dili1abc...");
```

### Building a Signer

`DilithiaSigner` is a functional interface. Implement it to bridge any cryptographic backend:

```java
DilithiaSigner signer = payload -> {
    byte[] sig = myKeyPair.sign(payload.canonicalBytes());
    return new SignedPayload("dilithium", myKeyPair.publicKeyHex(), sig);
};

// Use with any mutating operation
Receipt receipt = client.contract("token")
        .call("transfer", Map.of("to", "dili1bob", "amount", 100))
        .send(signer);
```

---

## Exception Handling

All SDK exceptions extend the checked `DilithiaException` base class, enabling both fine-grained and catch-all error handling.

```
DilithiaException (checked)
 +-- RpcException              JSON-RPC error (carries code() and rpcMessage())
 +-- HttpException             Non-2xx HTTP response (carries statusCode() and body())
 +-- TimeoutException          HTTP request timeout
 +-- CryptoException           Native crypto bridge failure
 +-- ValidationException       Invalid input (address, key, mnemonic, config)
 +-- SerializationException    JSON serialization/deserialization failure
```

### Catching Specific Errors

```java
try {
    Balance b = client.balance("dili1alice").get();
} catch (HttpException e) {
    System.err.println("HTTP " + e.statusCode() + ": " + e.body());
} catch (TimeoutException e) {
    System.err.println("Request timed out");
} catch (DilithiaException e) {
    System.err.println("SDK error: " + e.getMessage());
}
```

### RPC Error Inspection

```java
try {
    client.rpc("qsc_unknownMethod", null).get();
} catch (RpcException e) {
    System.err.println("RPC code: " + e.code());
    System.err.println("RPC message: " + e.rpcMessage());
}
```

---

## Build Configuration

### Maven (`pom.xml`)

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.dilithia</groupId>
    <artifactId>dilithia-sdk-java</artifactId>
    <version>0.3.0</version>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.11.0</version>
        </dependency>
        <dependency>
            <groupId>net.java.dev.jna</groupId>
            <artifactId>jna</artifactId>
            <version>5.14.0</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### Gradle (Kotlin DSL, `build.gradle.kts`)

```kotlin
plugins {
    java
    `maven-publish`
    signing
}

group = "org.dilithia"
version = "0.3.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("net.java.dev.jna:jna:5.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
```

---

## Requirements

- **Java 21** or later (uses records, pattern matching, and `java.net.http.HttpClient`)
- **Native library** (`libdilithia_crypto`) on the library path for cryptographic operations

---

## License

Licensed under MIT OR Apache-2.0. See [LICENSE](LICENSE) for details.
