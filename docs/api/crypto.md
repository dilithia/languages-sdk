# Crypto Adapter API Reference

The `DilithiaCryptoAdapter` is the core cryptographic interface shared across all Dilithia SDKs. It provides 25 methods covering mnemonic management, HD wallet derivation, signing, verification, address handling, key generation, and hashing -- all backed by `dilithia-core` using the ML-DSA-65 (Dilithium) post-quantum signature scheme.

!!! tip
    In TypeScript and Python, each method is available in both sync and async variants. See [Sync vs Async](../guides/sync-async.md) for details.

---

## Mnemonic Operations

### `generateMnemonic`

Generate a new BIP-39 mnemonic phrase for wallet creation.

=== "TypeScript"

    ```typescript
    generateMnemonic(): Promise<string>
    ```

=== "Python"

    ```python
    def generate_mnemonic(self) -> str
    ```

=== "Rust"

    ```rust
    fn generate_mnemonic(&self) -> Result<String, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) GenerateMnemonic(ctx context.Context) (string, error)
    ```

=== "Java"

    ```java
    String generateMnemonic()
    ```

**Returns:** A space-separated mnemonic phrase (typically 24 words).

---

### `validateMnemonic`

Validate that a mnemonic phrase is well-formed according to BIP-39 rules.

=== "TypeScript"

    ```typescript
    validateMnemonic(mnemonic: string): Promise<void>
    ```

=== "Python"

    ```python
    def validate_mnemonic(self, mnemonic: str) -> None
    ```

=== "Rust"

    ```rust
    fn validate_mnemonic(&self, mnemonic: &str) -> Result<(), String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) ValidateMnemonic(ctx context.Context, mnemonic string) error
    ```

=== "Java"

    ```java
    void validateMnemonic(String mnemonic)
    ```

| Parameter  | Type     | Description                  |
| ---------- | -------- | ---------------------------- |
| `mnemonic` | `string` | The mnemonic phrase to check |

**Returns:** Nothing on success. Throws/returns an error if the mnemonic is invalid.

---

### `seedFromMnemonic`

Derive a 32-byte seed from a mnemonic phrase. This seed can be used for deterministic key generation.

=== "TypeScript"

    ```typescript
    seedFromMnemonic(mnemonic: string): Promise<string>
    ```

=== "Python"

    ```python
    def seed_from_mnemonic(self, mnemonic: str) -> str
    ```

=== "Rust"

    ```rust
    fn seed_from_mnemonic(&self, mnemonic: &str) -> Result<String, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) SeedFromMnemonic(ctx context.Context, mnemonic string) (string, error)
    ```

=== "Java"

    ```java
    String seedFromMnemonic(String mnemonic)
    ```

| Parameter  | Type     | Description          |
| ---------- | -------- | -------------------- |
| `mnemonic` | `string` | A valid BIP-39 mnemonic |

**Returns:** Hex-encoded 32-byte seed.

---

## Wallet Operations

### `recoverHdWallet`

Recover the root HD wallet account (index 0) from a mnemonic.

=== "TypeScript"

    ```typescript
    recoverHdWallet(mnemonic: string): Promise<DilithiaAccount>
    ```

=== "Python"

    ```python
    def recover_hd_wallet(self, mnemonic: str) -> DilithiaAccount
    ```

=== "Rust"

    ```rust
    fn recover_hd_wallet(&self, mnemonic: &str) -> Result<DilithiaAccount, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) RecoverHDWallet(ctx context.Context, mnemonic string) (Account, error)
    ```

=== "Java"

    ```java
    DilithiaAccount recoverHdWallet(String mnemonic)
    ```

| Parameter  | Type     | Description              |
| ---------- | -------- | ------------------------ |
| `mnemonic` | `string` | A valid BIP-39 mnemonic  |

**Returns:** A `DilithiaAccount` with address, public key, secret key, and `accountIndex = 0`.

---

### `recoverHdWalletAccount`

Recover a specific HD wallet account by index from a mnemonic.

=== "TypeScript"

    ```typescript
    recoverHdWalletAccount(mnemonic: string, accountIndex: number): Promise<DilithiaAccount>
    ```

=== "Python"

    ```python
    def recover_hd_wallet_account(self, mnemonic: str, account_index: int) -> DilithiaAccount
    ```

=== "Rust"

    ```rust
    fn recover_hd_wallet_account(&self, mnemonic: &str, account_index: u32) -> Result<DilithiaAccount, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) RecoverHDWalletAccount(ctx context.Context, mnemonic string, accountIndex int) (Account, error)
    ```

=== "Java"

    ```java
    DilithiaAccount recoverHdWalletAccount(String mnemonic, int accountIndex)
    ```

| Parameter      | Type     | Description                         |
| -------------- | -------- | ----------------------------------- |
| `mnemonic`     | `string` | A valid BIP-39 mnemonic             |
| `accountIndex` | `uint`   | The HD derivation index (0-based)   |

**Returns:** A `DilithiaAccount` for the specified derivation index.

---

### `createHdWalletFileFromMnemonic`

Create an encrypted wallet file for the root account (index 0) from a mnemonic and password.

=== "TypeScript"

    ```typescript
    createHdWalletFileFromMnemonic(mnemonic: string, password: string): Promise<DilithiaAccount>
    ```

=== "Python"

    ```python
    def create_hd_wallet_file_from_mnemonic(self, mnemonic: str, password: str) -> DilithiaAccount
    ```

=== "Rust"

    ```rust
    fn create_hd_wallet_file_from_mnemonic(&self, mnemonic: &str, password: &str) -> Result<DilithiaAccount, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) CreateHDWalletFileFromMnemonic(ctx context.Context, mnemonic, password string) (Account, error)
    ```

=== "Java"

    ```java
    DilithiaAccount createHdWalletFileFromMnemonic(String mnemonic, String password)
    ```

| Parameter  | Type     | Description                              |
| ---------- | -------- | ---------------------------------------- |
| `mnemonic` | `string` | A valid BIP-39 mnemonic                  |
| `password` | `string` | Encryption password for the wallet file  |

**Returns:** A `DilithiaAccount` whose `walletFile` field contains the encrypted wallet data.

---

### `createHdWalletAccountFromMnemonic`

Create an encrypted wallet file for a specific HD account index.

=== "TypeScript"

    ```typescript
    createHdWalletAccountFromMnemonic(mnemonic: string, password: string, accountIndex: number): Promise<DilithiaAccount>
    ```

=== "Python"

    ```python
    def create_hd_wallet_account_from_mnemonic(self, mnemonic: str, password: str, account_index: int) -> DilithiaAccount
    ```

=== "Rust"

    ```rust
    fn create_hd_wallet_account_from_mnemonic(&self, mnemonic: &str, password: &str, account_index: u32) -> Result<DilithiaAccount, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) CreateHDWalletAccountFromMnemonic(ctx context.Context, mnemonic, password string, accountIndex int) (Account, error)
    ```

=== "Java"

    ```java
    DilithiaAccount createHdWalletAccountFromMnemonic(String mnemonic, String password, int accountIndex)
    ```

| Parameter      | Type     | Description                              |
| -------------- | -------- | ---------------------------------------- |
| `mnemonic`     | `string` | A valid BIP-39 mnemonic                  |
| `password`     | `string` | Encryption password for the wallet file  |
| `accountIndex` | `uint`   | HD derivation index                      |

**Returns:** A `DilithiaAccount` with an encrypted `walletFile` for the given index.

---

### `recoverWalletFile`

Recover an account from a previously saved encrypted wallet file.

=== "TypeScript"

    ```typescript
    recoverWalletFile(walletFile: WalletFile, mnemonic: string, password: string): Promise<DilithiaAccount>
    ```

=== "Python"

    ```python
    def recover_wallet_file(self, wallet_file: WalletFile, mnemonic: str, password: str) -> DilithiaAccount
    ```

=== "Rust"

    ```rust
    fn recover_wallet_file(&self, wallet_file: &WalletFile, mnemonic: &str, password: &str) -> Result<DilithiaAccount, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) RecoverWalletFile(ctx context.Context, walletFile WalletFile, mnemonic, password string) (Account, error)
    ```

=== "Java"

    ```java
    DilithiaAccount recoverWalletFile(Map<String, Object> walletFile, String mnemonic, String password)
    ```

| Parameter    | Type         | Description                                   |
| ------------ | ------------ | --------------------------------------------- |
| `walletFile` | `WalletFile` | The encrypted wallet file object               |
| `mnemonic`   | `string`     | The mnemonic used when creating the wallet     |
| `password`   | `string`     | The password used when creating the wallet     |

**Returns:** A `DilithiaAccount` with decrypted keys and the wallet file attached.

---

## HD Wallet Derivation

### `deriveChildSeed`

Derive a child seed from a parent seed at a given index. Used for hierarchical deterministic key derivation.

=== "TypeScript"

    ```typescript
    deriveChildSeed(parentSeedHex: string, index: number): Promise<string>
    ```

=== "Python"

    ```python
    def derive_child_seed(self, parent_seed_hex: str, index: int) -> str
    ```

=== "Rust"

    ```rust
    fn derive_child_seed(&self, parent_seed_hex: &str, index: u32) -> Result<String, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) DeriveChildSeed(ctx context.Context, parentSeedHex string, index int) (string, error)
    ```

=== "Java"

    ```java
    String deriveChildSeed(String parentSeedHex, int index)
    ```

| Parameter       | Type     | Description                              |
| --------------- | -------- | ---------------------------------------- |
| `parentSeedHex` | `string` | Hex-encoded 32-byte parent seed          |
| `index`         | `uint`   | Child derivation index                   |

**Returns:** Hex-encoded 32-byte child seed.

---

## Address Operations

### `addressFromPublicKey`

Derive a Dilithia address from a public key.

=== "TypeScript"

    ```typescript
    addressFromPublicKey(publicKeyHex: string): Promise<string>
    ```

=== "Python"

    ```python
    def address_from_public_key(self, public_key_hex: str) -> str
    ```

=== "Rust"

    ```rust
    fn address_from_public_key(&self, public_key_hex: &str) -> Result<String, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) AddressFromPublicKey(ctx context.Context, publicKeyHex string) (string, error)
    ```

=== "Java"

    ```java
    String addressFromPublicKey(String publicKeyHex)
    ```

| Parameter      | Type     | Description                          |
| -------------- | -------- | ------------------------------------ |
| `publicKeyHex` | `string` | Hex-encoded ML-DSA-65 public key     |

**Returns:** The derived Dilithia address string.

---

### `addressFromPkChecksummed`

Derive a checksummed Dilithia address from a public key.

=== "TypeScript"

    ```typescript
    addressFromPkChecksummed(publicKeyHex: string): Promise<string>
    ```

=== "Python"

    ```python
    def address_from_pk_checksummed(self, public_key_hex: str) -> str
    ```

=== "Rust"

    ```rust
    fn address_from_pk_checksummed(&self, public_key_hex: &str) -> Result<String, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) AddressFromPKChecksummed(ctx context.Context, publicKeyHex string) (string, error)
    ```

=== "Java"

    ```java
    String addressFromPkChecksummed(String publicKeyHex)
    ```

| Parameter      | Type     | Description                          |
| -------------- | -------- | ------------------------------------ |
| `publicKeyHex` | `string` | Hex-encoded ML-DSA-65 public key     |

**Returns:** Checksummed Dilithia address.

---

### `addressWithChecksum`

Add a checksum to a raw (un-checksummed) Dilithia address.

=== "TypeScript"

    ```typescript
    addressWithChecksum(rawAddr: string): Promise<string>
    ```

=== "Python"

    ```python
    def address_with_checksum(self, raw_addr: str) -> str
    ```

=== "Rust"

    ```rust
    fn address_with_checksum(&self, raw_addr: &str) -> Result<String, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) AddressWithChecksum(ctx context.Context, rawAddr string) (string, error)
    ```

=== "Java"

    ```java
    String addressWithChecksum(String rawAddr)
    ```

| Parameter | Type     | Description                 |
| --------- | -------- | --------------------------- |
| `rawAddr` | `string` | Raw Dilithia address        |

**Returns:** Address with an embedded checksum.

---

### `validateAddress`

Validate a Dilithia address (including checksum verification).

=== "TypeScript"

    ```typescript
    validateAddress(addr: string): Promise<string>
    ```

=== "Python"

    ```python
    def validate_address(self, addr: str) -> str
    ```

=== "Rust"

    ```rust
    fn validate_address(&self, addr: &str) -> Result<String, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) ValidateAddress(ctx context.Context, addr string) (string, error)
    ```

=== "Java"

    ```java
    String validateAddress(String addr)
    ```

| Parameter | Type     | Description                |
| --------- | -------- | -------------------------- |
| `addr`    | `string` | Address to validate        |

**Returns:** The normalized address on success. Throws/returns error if invalid.

---

## Signing Operations

### `signMessage`

Sign a message using an ML-DSA-65 secret key.

=== "TypeScript"

    ```typescript
    signMessage(secretKeyHex: string, message: string): Promise<DilithiaSignature>
    ```

=== "Python"

    ```python
    def sign_message(self, secret_key_hex: str, message: str) -> DilithiaSignature
    ```

=== "Rust"

    ```rust
    fn sign_message(&self, secret_key_hex: &str, message: &str) -> Result<DilithiaSignature, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) SignMessage(ctx context.Context, secretKeyHex, message string) (Signature, error)
    ```

=== "Java"

    ```java
    DilithiaSignature signMessage(String secretKeyHex, String message)
    ```

| Parameter      | Type     | Description                          |
| -------------- | -------- | ------------------------------------ |
| `secretKeyHex` | `string` | Hex-encoded ML-DSA-65 secret key     |
| `message`      | `string` | The message to sign                  |

**Returns:** A `DilithiaSignature` containing the algorithm identifier (`"mldsa65"`) and the hex-encoded signature.

---

### `verifyMessage`

Verify a signature against a public key and message.

=== "TypeScript"

    ```typescript
    verifyMessage(publicKeyHex: string, message: string, signatureHex: string): Promise<boolean>
    ```

=== "Python"

    ```python
    def verify_message(self, public_key_hex: str, message: str, signature_hex: str) -> bool
    ```

=== "Rust"

    ```rust
    fn verify_message(&self, public_key_hex: &str, message: &str, signature_hex: &str) -> Result<bool, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) VerifyMessage(ctx context.Context, publicKeyHex, message, signatureHex string) (bool, error)
    ```

=== "Java"

    ```java
    boolean verifyMessage(String publicKeyHex, String message, String signatureHex)
    ```

| Parameter      | Type     | Description                          |
| -------------- | -------- | ------------------------------------ |
| `publicKeyHex` | `string` | Hex-encoded ML-DSA-65 public key     |
| `message`      | `string` | The original message                 |
| `signatureHex` | `string` | Hex-encoded signature to verify      |

**Returns:** `true` if the signature is valid, `false` otherwise.

---

## Validation Operations

### `validatePublicKey`

Validate that a hex string represents a well-formed ML-DSA-65 public key.

=== "TypeScript"

    ```typescript
    validatePublicKey(publicKeyHex: string): Promise<void>
    ```

=== "Python"

    ```python
    def validate_public_key(self, public_key_hex: str) -> None
    ```

=== "Rust"

    ```rust
    fn validate_public_key(&self, public_key_hex: &str) -> Result<(), String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) ValidatePublicKey(ctx context.Context, publicKeyHex string) error
    ```

=== "Java"

    ```java
    void validatePublicKey(String publicKeyHex)
    ```

| Parameter      | Type     | Description                      |
| -------------- | -------- | -------------------------------- |
| `publicKeyHex` | `string` | Hex-encoded public key to check  |

**Returns:** Nothing on success. Throws/returns error if invalid.

---

### `validateSecretKey`

Validate that a hex string represents a well-formed ML-DSA-65 secret key.

=== "TypeScript"

    ```typescript
    validateSecretKey(secretKeyHex: string): Promise<void>
    ```

=== "Python"

    ```python
    def validate_secret_key(self, secret_key_hex: str) -> None
    ```

=== "Rust"

    ```rust
    fn validate_secret_key(&self, secret_key_hex: &str) -> Result<(), String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) ValidateSecretKey(ctx context.Context, secretKeyHex string) error
    ```

=== "Java"

    ```java
    void validateSecretKey(String secretKeyHex)
    ```

| Parameter      | Type     | Description                      |
| -------------- | -------- | -------------------------------- |
| `secretKeyHex` | `string` | Hex-encoded secret key to check  |

**Returns:** Nothing on success. Throws/returns error if invalid.

---

### `validateSignature`

Validate that a hex string represents a well-formed ML-DSA-65 signature (structural check only -- does not verify against a message).

=== "TypeScript"

    ```typescript
    validateSignature(signatureHex: string): Promise<void>
    ```

=== "Python"

    ```python
    def validate_signature(self, signature_hex: str) -> None
    ```

=== "Rust"

    ```rust
    fn validate_signature(&self, signature_hex: &str) -> Result<(), String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) ValidateSignature(ctx context.Context, signatureHex string) error
    ```

=== "Java"

    ```java
    void validateSignature(String signatureHex)
    ```

| Parameter      | Type     | Description                        |
| -------------- | -------- | ---------------------------------- |
| `signatureHex` | `string` | Hex-encoded signature to validate  |

**Returns:** Nothing on success. Throws/returns error if structurally invalid.

---

## Key Generation

### `keygen`

Generate a new random ML-DSA-65 keypair using a secure random source.

=== "TypeScript"

    ```typescript
    keygen(): Promise<DilithiaKeypair>
    ```

=== "Python"

    ```python
    def keygen(self) -> DilithiaKeypair
    ```

=== "Rust"

    ```rust
    fn keygen(&self) -> Result<DilithiaKeypair, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) Keygen(ctx context.Context) (Keypair, error)
    ```

=== "Java"

    ```java
    DilithiaKeypair keygen()
    ```

**Returns:** A `DilithiaKeypair` containing the secret key, public key, and derived address.

---

### `keygenFromSeed`

Generate a deterministic ML-DSA-65 keypair from a 32-byte seed.

=== "TypeScript"

    ```typescript
    keygenFromSeed(seedHex: string): Promise<DilithiaKeypair>
    ```

=== "Python"

    ```python
    def keygen_from_seed(self, seed_hex: str) -> DilithiaKeypair
    ```

=== "Rust"

    ```rust
    fn keygen_from_seed(&self, seed_hex: &str) -> Result<DilithiaKeypair, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) KeygenFromSeed(ctx context.Context, seedHex string) (Keypair, error)
    ```

=== "Java"

    ```java
    DilithiaKeypair keygenFromSeed(String seedHex)
    ```

| Parameter | Type     | Description                      |
| --------- | -------- | -------------------------------- |
| `seedHex` | `string` | Hex-encoded 32-byte seed         |

**Returns:** A deterministic `DilithiaKeypair`. The same seed always produces the same keypair.

!!! warning
    The seed must be exactly 32 bytes (64 hex characters). Using a shorter or longer value will produce an error.

---

## Hash Operations

### `hashHex`

Hash hex-encoded data using the currently configured hash algorithm.

=== "TypeScript"

    ```typescript
    hashHex(dataHex: string): Promise<string>
    ```

=== "Python"

    ```python
    def hash_hex(self, data_hex: str) -> str
    ```

=== "Rust"

    ```rust
    fn hash_hex(&self, data_hex: &str) -> Result<String, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) HashHex(ctx context.Context, dataHex string) (string, error)
    ```

=== "Java"

    ```java
    String hashHex(String dataHex)
    ```

| Parameter | Type     | Description                 |
| --------- | -------- | --------------------------- |
| `dataHex` | `string` | Hex-encoded data to hash    |

**Returns:** Hex-encoded hash digest.

---

### `setHashAlg`

Set the hash algorithm used by `hashHex` and related operations.

=== "TypeScript"

    ```typescript
    setHashAlg(alg: string): Promise<void>
    ```

=== "Python"

    ```python
    def set_hash_alg(self, alg: str) -> None
    ```

=== "Rust"

    ```rust
    fn set_hash_alg(&self, alg: &str) -> Result<(), String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) SetHashAlg(ctx context.Context, alg string) error
    ```

=== "Java"

    ```java
    void setHashAlg(String alg)
    ```

| Parameter | Type     | Description                                                                  |
| --------- | -------- | ---------------------------------------------------------------------------- |
| `alg`     | `string` | One of: `"sha3_512"`, `"blake2b512"`, `"blake3_256"`                        |

**Returns:** Nothing on success. Throws/returns error for unknown algorithms.

---

### `currentHashAlg`

Return the name of the currently active hash algorithm.

=== "TypeScript"

    ```typescript
    currentHashAlg(): Promise<string>
    ```

=== "Python"

    ```python
    def current_hash_alg(self) -> str
    ```

=== "Rust"

    ```rust
    fn current_hash_alg(&self) -> String
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) CurrentHashAlg(ctx context.Context) (string, error)
    ```

=== "Java"

    ```java
    String currentHashAlg()
    ```

**Returns:** The active algorithm name (e.g. `"sha3_512"`).

---

### `hashLenHex`

Return the output length (in hex characters) of the current hash algorithm.

=== "TypeScript"

    ```typescript
    hashLenHex(): Promise<number>
    ```

=== "Python"

    ```python
    def hash_len_hex(self) -> int
    ```

=== "Rust"

    ```rust
    fn hash_len_hex(&self) -> usize
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) HashLenHex(ctx context.Context) (int, error)
    ```

=== "Java"

    ```java
    int hashLenHex()
    ```

**Returns:** The number of hex characters in a hash digest for the current algorithm.

---

### `constantTimeEq`

Compare two hex-encoded byte strings in constant time, preventing timing side-channel attacks.

=== "TypeScript"

    ```typescript
    constantTimeEq(aHex: string, bHex: string): Promise<boolean>
    ```

=== "Python"

    ```python
    def constant_time_eq(self, a_hex: str, b_hex: str) -> bool
    ```

=== "Rust"

    ```rust
    fn constant_time_eq(&self, a_hex: &str, b_hex: &str) -> Result<bool, String>
    ```

=== "Go"

    ```go
    func (a *CryptoAdapter) ConstantTimeEq(ctx context.Context, aHex, bHex string) (bool, error)
    ```

=== "Java"

    ```java
    boolean constantTimeEq(String aHex, String bHex)
    ```

| Parameter | Type     | Description                |
| --------- | -------- | -------------------------- |
| `aHex`    | `string` | First hex-encoded value    |
| `bHex`    | `string` | Second hex-encoded value   |

**Returns:** `true` if the decoded byte sequences are equal, `false` otherwise. The comparison runs in constant time regardless of where the values differ.

!!! tip
    Always use `constantTimeEq` when comparing secrets, signatures, or hashes to avoid leaking information through timing.
