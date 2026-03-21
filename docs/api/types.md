# Types Reference

This page documents the shared data types used across all Dilithia SDK methods. Each type has a canonical structure that is represented idiomatically in each language.

---

## Branded / Named Types

v0.3.0 introduces strongly typed wrappers for common primitives. These prevent accidentally passing an address where a transaction hash is expected, and carry domain-specific formatting/validation.

### Address

A Dilithia address string with checksum validation.

=== "TypeScript"

    ```typescript
    // Branded type -- a string at runtime, but distinct at compile time
    type Address = string & { readonly __brand: "Address" };

    const addr = Address("dili1abc...");  // validates + brands
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class Address:
        value: str

        @classmethod
        def of(cls, raw: str) -> "Address": ...
    ```

=== "Rust"

    ```rust
    // Rust SDK continues to use plain String for addresses
    ```

=== "Go"

    ```go
    type Address string

    func NewAddress(raw string) (Address, error)
    ```

=== "Java"

    ```java
    public record Address(String value) {
        public static Address of(String raw) { ... }
    }
    ```

=== "C#"

    ```csharp
    public sealed record Address(string Value)
    {
        public static Address Of(string raw) => ...;
    }
    ```

### TxHash

A transaction hash string.

=== "TypeScript"

    ```typescript
    type TxHash = string & { readonly __brand: "TxHash" };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class TxHash:
        value: str
    ```

=== "Go"

    ```go
    type TxHash string
    ```

=== "Java"

    ```java
    public record TxHash(String value) {
        public static TxHash of(String raw) { ... }
    }
    ```

=== "C#"

    ```csharp
    public sealed record TxHash(string Value)
    {
        public static TxHash Of(string raw) => ...;
    }
    ```

### PublicKey / SecretKey

Hex-encoded ML-DSA-65 key strings with length validation.

=== "TypeScript"

    ```typescript
    type PublicKey = string & { readonly __brand: "PublicKey" };
    type SecretKey = string & { readonly __brand: "SecretKey" };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class PublicKey:
        value: str

    @dataclass(frozen=True, slots=True)
    class SecretKey:
        value: str
    ```

=== "Go"

    ```go
    type PublicKey string
    type SecretKey string
    ```

=== "Java"

    ```java
    public record PublicKey(String value) { ... }
    public record SecretKey(String value) { ... }
    ```

=== "C#"

    ```csharp
    public sealed record PublicKey(string Value);
    public sealed record SecretKey(string Value);
    ```

### TokenAmount

Represents a token amount with full precision.

=== "TypeScript"

    ```typescript
    // BigInt-backed for lossless arithmetic
    type TokenAmount = bigint & { readonly __brand: "TokenAmount" };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class TokenAmount:
        value: Decimal  # decimal.Decimal for lossless arithmetic
    ```

=== "Go"

    ```go
    // Uses *big.Int for token amounts
    ```

=== "Java"

    ```java
    public record TokenAmount(BigDecimal value) {
        public static TokenAmount of(String raw) { ... }
        public static TokenAmount of(BigDecimal amount) { ... }
    }
    ```

=== "C#"

    ```csharp
    public sealed record TokenAmount(decimal Value)
    {
        public static TokenAmount Dili(string amount) => ...;
        public static TokenAmount Dili(long amount) => ...;
        public static TokenAmount FromRaw(string raw, int decimals) => ...;
    }
    ```

---

## Typed Response Objects

### Balance

Returned by `getBalance` / `get_balance`.

=== "TypeScript"

    ```typescript
    type Balance = {
      address: Address;
      available: TokenAmount;
      staked: TokenAmount;
      locked: TokenAmount;
    };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class Balance:
        address: Address
        available: TokenAmount
        staked: TokenAmount
        locked: TokenAmount
    ```

=== "Go"

    ```go
    type Balance struct {
        Address   Address  `json:"address"`
        Available *big.Int `json:"available"`
        Staked    *big.Int `json:"staked"`
        Locked    *big.Int `json:"locked"`
    }
    ```

=== "Java"

    ```java
    public record Balance(
        Address address,
        TokenAmount available,
        TokenAmount staked,
        TokenAmount locked
    ) {}
    ```

=== "C#"

    ```csharp
    public sealed record Balance(
        Address Address,
        TokenAmount Available,
        TokenAmount Staked,
        TokenAmount Locked
    );
    ```

### Nonce

Returned by `getNonce` / `get_nonce`.

=== "TypeScript"

    ```typescript
    type Nonce = { address: Address; value: number };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class Nonce:
        address: Address
        value: int
    ```

=== "Go"

    ```go
    type Nonce struct {
        Address Address `json:"address"`
        Value   uint64  `json:"value"`
    }
    ```

=== "Java"

    ```java
    public record Nonce(Address address, long value) {}
    ```

=== "C#"

    ```csharp
    public sealed record Nonce(Address Address, long Value);
    ```

### Receipt

Returned by `getReceipt` / `get_receipt` and `waitForReceipt`.

=== "TypeScript"

    ```typescript
    type Receipt = {
      txHash: TxHash;
      status: "success" | "failure";
      blockHeight: number;
      gasUsed: number;
      events: unknown[];
    };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class Receipt:
        tx_hash: TxHash
        status: str
        block_height: int
        gas_used: int
        events: list[dict]
    ```

=== "Go"

    ```go
    type Receipt struct {
        TxHash      TxHash `json:"tx_hash"`
        Status      string `json:"status"`
        BlockHeight uint64 `json:"block_height"`
        GasUsed     uint64 `json:"gas_used"`
        Events      []any  `json:"events"`
    }
    ```

=== "Java"

    ```java
    public record Receipt(
        TxHash txHash,
        String status,
        long blockHeight,
        long gasUsed,
        List<Map<String, Object>> events
    ) {}
    ```

=== "C#"

    ```csharp
    public sealed record Receipt(
        TxHash TxHash,
        string Status,
        long BlockHeight,
        long GasUsed,
        IReadOnlyList<Dictionary<string, object>> Events
    );
    ```

### NetworkInfo

Returned by chain info queries.

=== "TypeScript"

    ```typescript
    type NetworkInfo = {
      chainId: string;
      blockHeight: number;
      tps: number;
    };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class NetworkInfo:
        chain_id: str
        block_height: int
        tps: float
    ```

=== "Go"

    ```go
    type NetworkInfo struct {
        ChainID     string `json:"chain_id"`
        BlockHeight uint64 `json:"block_height"`
        TPS         float64 `json:"tps"`
    }
    ```

=== "Java"

    ```java
    public record NetworkInfo(String chainId, long blockHeight, double tps) {}
    ```

=== "C#"

    ```csharp
    public sealed record NetworkInfo(string ChainId, long BlockHeight, double Tps);
    ```

### GasEstimate

Returned by `getGasEstimate` / `get_gas_estimate`.

=== "TypeScript"

    ```typescript
    type GasEstimate = {
      gasLimit: number;
      gasPrice: TokenAmount;
    };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class GasEstimate:
        gas_limit: int
        gas_price: TokenAmount
    ```

=== "Go"

    ```go
    type GasEstimate struct {
        GasLimit uint64   `json:"gas_limit"`
        GasPrice *big.Int `json:"gas_price"`
    }
    ```

=== "Java"

    ```java
    public record GasEstimate(long gasLimit, TokenAmount gasPrice) {}
    ```

=== "C#"

    ```csharp
    public sealed record GasEstimate(long GasLimit, TokenAmount GasPrice);
    ```

### SubmitResult

Returned by `sendCall` / `send_call` and deploy/upgrade operations.

=== "TypeScript"

    ```typescript
    type SubmitResult = {
      txHash: TxHash;
    };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class SubmitResult:
        tx_hash: TxHash
    ```

=== "Go"

    ```go
    type SubmitResult struct {
        TxHash TxHash `json:"tx_hash"`
    }
    ```

=== "Java"

    ```java
    public record SubmitResult(TxHash txHash) {}
    ```

=== "C#"

    ```csharp
    public sealed record SubmitResult(TxHash TxHash);
    ```

### QueryResult

Returned by `queryContract` / `query_contract`.

=== "TypeScript"

    ```typescript
    type QueryResult = {
      data: unknown;
      gasUsed: number;
    };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class QueryResult:
        data: Any
        gas_used: int
    ```

=== "Go"

    ```go
    type QueryResult struct {
        Data    any    `json:"data"`
        GasUsed uint64 `json:"gas_used"`
    }
    ```

=== "Java"

    ```java
    public record QueryResult(Object data, long gasUsed) {}
    ```

=== "C#"

    ```csharp
    public sealed record QueryResult(object Data, long GasUsed);
    ```

### NameRecord

Returned by `resolveName` / `resolve_name`.

=== "TypeScript"

    ```typescript
    type NameRecord = {
      name: string;
      address: Address;
      owner: Address;
      expiry: number;
    };
    ```

=== "Python"

    ```python
    @dataclass(frozen=True, slots=True)
    class NameRecord:
        name: str
        address: Address
        owner: Address
        expiry: int
    ```

=== "Go"

    ```go
    type NameRecord struct {
        Name    string  `json:"name"`
        Address Address `json:"address"`
        Owner   Address `json:"owner"`
        Expiry  uint64  `json:"expiry"`
    }
    ```

=== "Java"

    ```java
    public record NameRecord(String name, Address address, Address owner, long expiry) {}
    ```

=== "C#"

    ```csharp
    public sealed record NameRecord(string Name, Address Address, Address Owner, long Expiry);
    ```

---

## Error Types

v0.3.0 introduces a structured exception/error hierarchy across all SDKs.

=== "TypeScript"

    ```typescript
    class DilithiaError extends Error { }
    class RpcError extends DilithiaError { code: number; data?: unknown; }
    class HttpError extends DilithiaError { status: number; }
    class TimeoutError extends DilithiaError { }
    ```

=== "Python"

    ```python
    class DilithiaError(Exception): ...
    class RpcError(DilithiaError): code: int; data: Any
    class HttpError(DilithiaError): status: int
    class TimeoutError(DilithiaError): ...
    ```

=== "Go"

    ```go
    // Error types support errors.Is and errors.As
    type DilithiaError struct { Message string }
    type RpcError struct { DilithiaError; Code int; Data any }
    type HttpError struct { DilithiaError; Status int }
    type TimeoutError struct { DilithiaError }
    ```

=== "Java"

    ```java
    public class DilithiaException extends RuntimeException { }
    public class RpcException extends DilithiaException { int code; }
    public class HttpException extends DilithiaException { int status; }
    public class TimeoutException extends DilithiaException { }
    ```

=== "C#"

    ```csharp
    public class DilithiaException : Exception { }
    public class HttpException : DilithiaException { public int StatusCode { get; } public string Body { get; } }
    public class RpcException : DilithiaException { public int Code { get; } public string RpcMessage { get; } }
    public class DilithiaTimeoutException : DilithiaException { public string Operation { get; } }
    ```

---

## DilithiaAccount

Represents a fully resolved account with keys, address, and optional wallet file.

=== "TypeScript"

    ```typescript
    type DilithiaAccount = {
      address: Address;
      publicKey: PublicKey;
      secretKey: SecretKey;
      accountIndex: number;
      walletFile?: WalletFile | null;
    };
    ```

=== "Python"

    ```python
    @dataclass(slots=True)
    class DilithiaAccount:
        address: Address
        public_key: PublicKey
        secret_key: SecretKey
        account_index: int
        wallet_file: WalletFile | None = None
    ```

=== "Rust"

    ```rust
    pub struct DilithiaAccount {
        pub address: String,
        pub public_key: String,
        pub secret_key: String,
        pub account_index: u32,
        pub wallet_file: Option<serde_json::Value>,
    }
    ```

=== "Go"

    ```go
    type DilithiaAccount struct {
        Address      Address                `json:"address"`
        PublicKey    PublicKey              `json:"public_key"`
        SecretKey    SecretKey              `json:"secret_key"`
        AccountIndex uint32                 `json:"account_index"`
        WalletFile   map[string]interface{} `json:"wallet_file,omitempty"`
    }
    ```

=== "Java"

    ```java
    public record DilithiaAccount(
        Address address,
        PublicKey publicKey,
        SecretKey secretKey,
        int accountIndex,
        Map<String, Object> walletFile
    ) {}
    ```

=== "C#"

    ```csharp
    public sealed record DilithiaAccount(
        Address Address,
        PublicKey PublicKey,
        SecretKey SecretKey,
        int AccountIndex,
        Dictionary<string, object>? WalletFile
    );
    ```

### Fields

| Field          | Type               | Description                                                              |
| -------------- | ------------------ | ------------------------------------------------------------------------ |
| `address`      | `string`           | The Dilithia address derived from the public key                         |
| `publicKey`    | `string`           | Hex-encoded ML-DSA-65 public key                                         |
| `secretKey`    | `string`           | Hex-encoded ML-DSA-65 secret key                                         |
| `accountIndex` | `uint`             | HD derivation index (0 for root account)                                 |
| `walletFile`   | `WalletFile?`      | Optional encrypted wallet file data, present when created with a password |

!!! warning
    The `secretKey` field contains sensitive key material. Never log, transmit, or persist it without encryption.

---

## DilithiaSignature

Represents a cryptographic signature produced by `signMessage`.

=== "TypeScript"

    ```typescript
    type DilithiaSignature = {
      algorithm: string;
      signature: string;
    };
    ```

=== "Python"

    ```python
    @dataclass(slots=True)
    class DilithiaSignature:
        algorithm: str
        signature: str
    ```

=== "Rust"

    ```rust
    pub struct DilithiaSignature {
        pub algorithm: String,
        pub signature: String,
    }
    ```

=== "Go"

    ```go
    type DilithiaSignature struct {
        Algorithm string `json:"algorithm"`
        Signature string `json:"signature"`
    }
    ```

=== "Java"

    ```java
    public record DilithiaSignature(
        String algorithm,
        String signature
    ) {}
    ```

=== "C#"

    ```csharp
    public sealed record DilithiaSignature(string Algorithm, string Signature);
    ```

### Fields

| Field       | Type     | Description                                              |
| ----------- | -------- | -------------------------------------------------------- |
| `algorithm` | `string` | The signing algorithm identifier, always `"mldsa65"`     |
| `signature` | `string` | Hex-encoded signature bytes                              |

---

## DilithiaKeypair

Represents a keypair generated by `keygen` or `keygenFromSeed`.

=== "TypeScript"

    ```typescript
    type DilithiaKeypair = {
      secretKey: SecretKey;
      publicKey: PublicKey;
      address: Address;
    };
    ```

=== "Python"

    ```python
    @dataclass(slots=True)
    class DilithiaKeypair:
        secret_key: SecretKey
        public_key: PublicKey
        address: Address
    ```

=== "Rust"

    ```rust
    pub struct DilithiaKeypair {
        pub secret_key: String,
        pub public_key: String,
        pub address: String,
    }
    ```

=== "Go"

    ```go
    type DilithiaKeypair struct {
        SecretKey SecretKey `json:"secret_key"`
        PublicKey PublicKey `json:"public_key"`
        Address   Address  `json:"address"`
    }
    ```

=== "Java"

    ```java
    public record DilithiaKeypair(
        SecretKey secretKey,
        PublicKey publicKey,
        Address address
    ) {}
    ```

=== "C#"

    ```csharp
    public sealed record DilithiaKeypair(SecretKey SecretKey, PublicKey PublicKey, Address Address);
    ```

### Fields

| Field       | Type     | Description                                      |
| ----------- | -------- | ------------------------------------------------ |
| `secretKey` | `string` | Hex-encoded ML-DSA-65 secret key                  |
| `publicKey` | `string` | Hex-encoded ML-DSA-65 public key                  |
| `address`   | `string` | Dilithia address derived from the public key       |

---

## WalletFile

An encrypted wallet file containing a protected secret key. This is a generic dictionary/map -- the structure is defined by `dilithia-core` and may vary by version.

=== "TypeScript"

    ```typescript
    type WalletFile = Record<string, unknown>;
    ```

=== "Python"

    ```python
    WalletFile = dict[str, Any]
    ```

=== "Rust"

    ```rust
    // Represented as serde_json::Value or dilithia_core::wallet::WalletFile
    ```

=== "Go"

    ```go
    type WalletFile map[string]interface{}
    ```

=== "Java"

    ```java
    // Represented as Map<String, Object>
    ```

=== "C#"

    ```csharp
    // Represented as Dictionary<string, object>
    ```

### Known Fields

The wallet file typically contains these fields, though implementations should treat it as opaque when possible:

| Field          | Type     | Description                                       |
| -------------- | -------- | ------------------------------------------------- |
| `version`      | `int`    | Wallet format version (currently `1`)             |
| `address`      | `string` | The account address                               |
| `public_key`   | `string` | Hex-encoded public key                            |
| `encrypted_sk` | `string` | Encrypted secret key (hex-encoded ciphertext)     |
| `nonce`        | `string` | Encryption nonce (hex)                            |
| `tag`          | `string` | Authentication tag (hex)                          |
| `account_index`| `int?`   | HD derivation index, if applicable                |

!!! note
    The wallet file is designed to be serialized as JSON and stored on disk. It can be recovered later using `recoverWalletFile` with the original mnemonic and password.

---

## DeployPayload

Represents the full payload for deploying or upgrading a WASM smart contract. Includes the bytecode, deployer identity, cryptographic signature, and chain metadata.

=== "TypeScript"

    ```typescript
    type DeployPayload = {
      name: string;
      bytecode: string;
      from: string;
      alg: string;
      pk: string;
      sig: string;
      nonce: number;
      chainId: string;
      version?: number;
    };
    ```

=== "Python"

    ```python
    # Represented as a plain dict built by deploy_contract_body()
    deploy_body: dict[str, Any] = {
        "name": str,
        "bytecode": str,
        "from": str,
        "alg": str,
        "pk": str,
        "sig": str,
        "nonce": int,
        "chain_id": str,
        "version": int,  # default 1
    }
    ```

=== "Rust"

    ```rust
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
    ```

=== "Go"

    ```go
    type DeployPayload struct {
        Name     string `json:"name"`
        Bytecode string `json:"bytecode"`
        From     string `json:"from"`
        Alg      string `json:"alg"`
        PK       string `json:"pk"`
        Sig      string `json:"sig"`
        Nonce    uint64 `json:"nonce"`
        ChainID  string `json:"chain_id"`
        Version  uint8  `json:"version"`
    }
    ```

=== "Java"

    ```java
    public record DeployPayload(
        String name,
        String bytecode,
        String from,
        String alg,
        String pk,
        String sig,
        long nonce,
        String chainId,
        int version
    ) {}
    ```

=== "C#"

    ```csharp
    public sealed record DeployPayload(
        string Name,
        string Bytecode,
        string From,
        string Alg,
        string Pk,
        string Sig,
        long Nonce,
        string ChainId,
        int Version = 1
    );
    ```

### Fields

| Field      | Type     | Description                                                  |
| ---------- | -------- | ------------------------------------------------------------ |
| `name`     | `string` | Contract name (used as the on-chain identifier)              |
| `bytecode` | `string` | Hex-encoded WASM bytecode                                    |
| `from`     | `string` | Deployer's Dilithia address                                  |
| `alg`      | `string` | Signing algorithm identifier (e.g. `"mldsa65"`)             |
| `pk`       | `string` | Hex-encoded public key of the deployer                       |
| `sig`      | `string` | Hex-encoded signature over the canonical deploy payload      |
| `nonce`    | `uint64` | Account nonce at time of deployment                          |
| `chainId`  | `string` | Target chain identifier (e.g. `"dilithia-mainnet"`)          |
| `version`  | `uint8`  | Contract version number (default `1`)                        |

!!! note
    The `sig` field is produced by signing the canonical payload returned by `buildDeployCanonicalPayload`. The canonical payload contains a hash of the bytecode (not the bytecode itself) to keep the signed message small.

---

## DilithiaClientConfig

Configuration for creating a `DilithiaClient` instance.

=== "TypeScript"

    ```typescript
    type DilithiaClientConfig = {
      rpcUrl: string;
      timeoutMs?: number;
      chainBaseUrl?: string;
      indexerUrl?: string;
      oracleUrl?: string;
      wsUrl?: string;
      jwt?: string;
      headers?: Record<string, string>;
    };
    ```

=== "Python"

    ```python
    # Passed as keyword arguments to DilithiaClient.__init__
    DilithiaClient(
        rpc_url: str,
        timeout: float = 10.0,
        *,
        chain_base_url: str | None = None,
        indexer_url: str | None = None,
        oracle_url: str | None = None,
        ws_url: str | None = None,
        jwt: str | None = None,
        headers: dict[str, str] | None = None,
    )
    ```

=== "Rust"

    ```rust
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
    ```

=== "C#"

    ```csharp
    // Configured via the builder pattern
    using var client = DilithiaClient.Create("https://rpc.dilithia.network/rpc")
        .WithTimeout(TimeSpan.FromSeconds(15))
        .WithJwt("my-bearer-token")
        .WithHeader("x-custom", "value")
        .Build();
    ```

### Fields

| Field           | Type               | Default        | Description                                              |
| --------------- | ------------------ | -------------- | -------------------------------------------------------- |
| `rpcUrl`        | `string`           | *required*     | Base URL of the Dilithia RPC endpoint                    |
| `timeoutMs`     | `number`           | `10000`        | HTTP request timeout in milliseconds                     |
| `chainBaseUrl`  | `string?`          | derived        | Base URL for REST endpoints; derived from `rpcUrl` if omitted |
| `indexerUrl`    | `string?`          | `null`         | URL of the indexer service                               |
| `oracleUrl`     | `string?`          | `null`         | URL of the oracle service                                |
| `wsUrl`         | `string?`          | derived        | WebSocket URL; auto-derived from `chainBaseUrl` if omitted |
| `jwt`           | `string?`          | `null`         | Bearer token for authenticated requests                  |
| `headers`       | `Record<string,string>` | `{}`    | Additional headers to include in every request           |
