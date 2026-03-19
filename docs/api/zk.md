# ZK Adapter API Reference

The ZK adapter provides post-quantum zero-knowledge proof primitives built on
**STARKs** (via winterfell). It covers Poseidon hashing, shielded commitments,
nullifiers, preimage proofs, and range proofs. A separate set of **shielded pool
methods** on `DilithiaClient` lets you interact with the on-chain shielded
contract.

---

## Types

### Commitment

A commitment to a shielded value, computed as `Poseidon(value || secret || nonce)`.

=== "TypeScript"

    ```typescript
    type Commitment = {
      hash: string;   // hex-encoded commitment hash
      value: number;  // deposited amount
      secret: string; // hex-encoded 32-byte secret
      nonce: string;  // hex-encoded 32-byte nonce
    };
    ```

=== "Python"

    ```python
    @dataclass(slots=True)
    class Commitment:
        hash: str      # hex-encoded commitment hash
        value: int     # deposited amount
        secret: str    # hex-encoded 32-byte secret
        nonce: str     # hex-encoded 32-byte nonce
    ```

=== "Rust"

    ```rust
    pub struct Commitment {
        pub hash: String,    // hex-encoded commitment hash
        pub value: u64,      // deposited amount
        pub secret: String,  // hex-encoded 32-byte secret
        pub nonce: String,   // hex-encoded 32-byte nonce
    }
    ```

=== "Go"

    ```go
    type Commitment struct {
        Hash   string `json:"hash"`
        Value  uint64 `json:"value"`
        Secret string `json:"secret"`
        Nonce  string `json:"nonce"`
    }
    ```

=== "Java"

    ```java
    public record Commitment(String hash, long value, String secret, String nonce) {}
    ```

---

### Nullifier

A nullifier proving a commitment was spent, computed as `Poseidon(secret || nonce)`.

=== "TypeScript"

    ```typescript
    type Nullifier = {
      hash: string; // hex-encoded nullifier hash
    };
    ```

=== "Python"

    ```python
    @dataclass(slots=True)
    class Nullifier:
        hash: str  # hex-encoded nullifier hash
    ```

=== "Rust"

    ```rust
    pub struct Nullifier {
        pub hash: String, // hex-encoded nullifier hash
    }
    ```

=== "Go"

    ```go
    type Nullifier struct {
        Hash string `json:"hash"`
    }
    ```

=== "Java"

    ```java
    public record Nullifier(String hash) {}
    ```

---

### StarkProof / StarkProofResult

The result of generating a STARK proof. Contains the serialized proof bytes, the
verification key, and the public inputs.

=== "TypeScript"

    ```typescript
    type StarkProof = {
      proof: string;  // hex-encoded proof bytes
      vk: string;     // JSON-encoded verification key
      inputs: string; // JSON-encoded public inputs
    };
    ```

=== "Python"

    ```python
    @dataclass(slots=True)
    class StarkProofResult:
        proof: str   # hex-encoded proof bytes
        vk: str      # JSON-encoded verification key
        inputs: str  # JSON-encoded public inputs
    ```

=== "Rust"

    ```rust
    // Proof generation returns a tuple:
    //   (StarkProof, String, String)
    // where StarkProof = Vec<u8> (serialized winterfell proof),
    // the second element is the verification key JSON,
    // and the third is the public inputs JSON.
    pub type StarkProof = Vec<u8>;
    ```

=== "Go"

    ```go
    type StarkProofResult struct {
        Proof  string `json:"proof"`
        VK     string `json:"vk"`
        Inputs string `json:"inputs"`
    }
    ```

=== "Java"

    ```java
    public record StarkProofResult(String proof, String vk, String inputs) {}
    ```

---

### ComplianceType

The type of compliance proof being submitted to the shielded pool.

=== "TypeScript"

    ```typescript
    type ComplianceType = "not_on_sanctions" | "tax_paid" | "balance_range";
    ```

=== "Python"

    ComplianceType is passed as a plain `str` with values `"not_on_sanctions"`,
    `"tax_paid"`, or `"balance_range"`.

=== "Rust"

    ```rust
    pub enum ComplianceType {
        NotOnSanctions,
        TaxPaid,
        BalanceRange,
    }
    ```

=== "Go"

    ComplianceType is passed as a plain `string` with values `"not_on_sanctions"`,
    `"tax_paid"`, or `"balance_range"`.

=== "Java"

    ComplianceType is passed as a plain `String` with values `"not_on_sanctions"`,
    `"tax_paid"`, or `"balance_range"`.

---

## ZK Adapter Methods

These methods are exposed by the ZK adapter interface. In TypeScript and Python
there are both **async** and **sync** variants of the adapter (see notes below).

### `poseidonHash`

Compute a Poseidon hash over an array of field elements.

**Parameters:**

| Name     | Type          | Description                       |
|----------|---------------|-----------------------------------|
| `inputs` | array of integers | Field elements to hash        |

**Returns:** hex-encoded hash string.

=== "TypeScript (async)"

    ```typescript
    // DilithiaZkAdapter
    poseidonHash(inputs: number[]): Promise<string>;
    ```

=== "TypeScript (sync)"

    ```typescript
    // SyncDilithiaZkAdapter
    poseidonHash(inputs: number[]): string;
    ```

=== "Python (sync)"

    ```python
    # DilithiaZkAdapter (Protocol)
    def poseidon_hash(self, inputs: list[int]) -> str: ...
    ```

=== "Python (async)"

    ```python
    # AsyncDilithiaZkAdapter (Protocol)
    async def poseidon_hash(self, inputs: list[int]) -> str: ...
    ```

=== "Rust"

    ```rust
    fn poseidon_hash(&self, inputs: &[u64]) -> Result<String, String>;
    ```

=== "Go"

    ```go
    PoseidonHash(ctx context.Context, inputs []uint64) (string, error)
    ```

=== "Java"

    ```java
    String poseidonHash(long[] inputs);
    ```

---

### `computeCommitment`

Create a shielded commitment: `Poseidon(value || secret || nonce)`.

**Parameters:**

| Name        | Type    | Description                         |
|-------------|---------|-------------------------------------|
| `value`     | integer | Amount to commit                    |
| `secretHex` | string  | Hex-encoded 32-byte secret          |
| `nonceHex`  | string  | Hex-encoded 32-byte nonce           |

**Returns:** a [`Commitment`](#commitment) object.

=== "TypeScript (async)"

    ```typescript
    computeCommitment(value: number, secretHex: string, nonceHex: string): Promise<Commitment>;
    ```

=== "TypeScript (sync)"

    ```typescript
    computeCommitment(value: number, secretHex: string, nonceHex: string): Commitment;
    ```

=== "Python (sync)"

    ```python
    def compute_commitment(self, value: int, secret_hex: str, nonce_hex: str) -> Commitment: ...
    ```

=== "Python (async)"

    ```python
    async def compute_commitment(self, value: int, secret_hex: str, nonce_hex: str) -> Commitment: ...
    ```

=== "Rust"

    ```rust
    fn compute_commitment(&self, value: u64, secret_hex: &str, nonce_hex: &str) -> Result<Commitment, String>;
    ```

=== "Go"

    ```go
    ComputeCommitment(ctx context.Context, value uint64, secretHex, nonceHex string) (Commitment, error)
    ```

=== "Java"

    ```java
    Commitment computeCommitment(long value, String secretHex, String nonceHex);
    ```

---

### `computeNullifier`

Create a nullifier: `Poseidon(secret || nonce)`. The nullifier uniquely
identifies a commitment without revealing its value.

**Parameters:**

| Name        | Type   | Description                |
|-------------|--------|----------------------------|
| `secretHex` | string | Hex-encoded 32-byte secret |
| `nonceHex`  | string | Hex-encoded 32-byte nonce  |

**Returns:** a [`Nullifier`](#nullifier) object.

=== "TypeScript (async)"

    ```typescript
    computeNullifier(secretHex: string, nonceHex: string): Promise<Nullifier>;
    ```

=== "TypeScript (sync)"

    ```typescript
    computeNullifier(secretHex: string, nonceHex: string): Nullifier;
    ```

=== "Python (sync)"

    ```python
    def compute_nullifier(self, secret_hex: str, nonce_hex: str) -> Nullifier: ...
    ```

=== "Python (async)"

    ```python
    async def compute_nullifier(self, secret_hex: str, nonce_hex: str) -> Nullifier: ...
    ```

=== "Rust"

    ```rust
    fn compute_nullifier(&self, secret_hex: &str, nonce_hex: &str) -> Result<Nullifier, String>;
    ```

=== "Go"

    ```go
    ComputeNullifier(ctx context.Context, secretHex, nonceHex string) (Nullifier, error)
    ```

=== "Java"

    ```java
    Nullifier computeNullifier(String secretHex, String nonceHex);
    ```

---

### `generatePreimageProof`

Generate a STARK proof of knowledge of a hash preimage. Proves that the prover
knows field elements whose Poseidon hash equals a public output, without
revealing the elements themselves.

**Parameters:**

| Name     | Type              | Description               |
|----------|-------------------|---------------------------|
| `values` | array of integers | Preimage field elements   |

**Returns:** a [`StarkProof` / `StarkProofResult`](#starkproof-starkproofresult).

=== "TypeScript (async)"

    ```typescript
    generatePreimageProof(values: number[]): Promise<StarkProof>;
    ```

=== "TypeScript (sync)"

    ```typescript
    generatePreimageProof(values: number[]): StarkProof;
    ```

=== "Python (sync)"

    ```python
    def generate_preimage_proof(self, values: list[int]) -> StarkProofResult: ...
    ```

=== "Python (async)"

    ```python
    async def generate_preimage_proof(self, values: list[int]) -> StarkProofResult: ...
    ```

=== "Rust"

    ```rust
    fn generate_preimage_proof(&self, values: &[u64]) -> Result<(StarkProof, String, String), String>;
    ```

=== "Go"

    ```go
    GeneratePreimageProof(ctx context.Context, values []uint64) (StarkProofResult, error)
    ```

=== "Java"

    ```java
    StarkProofResult generatePreimageProof(long[] values);
    ```

---

### `verifyPreimageProof`

Verify a STARK preimage proof.

**Parameters:**

| Name         | Type   | Description                             |
|--------------|--------|-----------------------------------------|
| `proofHex`   | string | Hex-encoded proof bytes                 |
| `vkJson`     | string | JSON-encoded verification key           |
| `inputsJson` | string | JSON-encoded public inputs              |

**Returns:** `boolean` -- `true` if the proof is valid.

=== "TypeScript (async)"

    ```typescript
    verifyPreimageProof(proofHex: string, vkJson: string, inputsJson: string): Promise<boolean>;
    ```

=== "TypeScript (sync)"

    ```typescript
    verifyPreimageProof(proofHex: string, vkJson: string, inputsJson: string): boolean;
    ```

=== "Python (sync)"

    ```python
    def verify_preimage_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...
    ```

=== "Python (async)"

    ```python
    async def verify_preimage_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...
    ```

=== "Rust"

    ```rust
    fn verify_preimage_proof(&self, proof: &[u8], vk_json: &str, inputs_json: &str) -> Result<bool, String>;
    ```

=== "Go"

    ```go
    VerifyPreimageProof(ctx context.Context, proofHex, vkJSON, inputsJSON string) (bool, error)
    ```

=== "Java"

    ```java
    boolean verifyPreimageProof(String proofHex, String vkJson, String inputsJson);
    ```

---

### `generateRangeProof`

Generate a STARK range proof proving that `value` is in the range `[min, max]`
without revealing the actual value.

**Parameters:**

| Name    | Type    | Description          |
|---------|---------|----------------------|
| `value` | integer | The secret value     |
| `min`   | integer | Range lower bound    |
| `max`   | integer | Range upper bound    |

**Returns:** a [`StarkProof` / `StarkProofResult`](#starkproof-starkproofresult).

=== "TypeScript (async)"

    ```typescript
    generateRangeProof(value: number, min: number, max: number): Promise<StarkProof>;
    ```

=== "TypeScript (sync)"

    ```typescript
    generateRangeProof(value: number, min: number, max: number): StarkProof;
    ```

=== "Python (sync)"

    ```python
    def generate_range_proof(self, value: int, min_val: int, max_val: int) -> StarkProofResult: ...
    ```

=== "Python (async)"

    ```python
    async def generate_range_proof(self, value: int, min_val: int, max_val: int) -> StarkProofResult: ...
    ```

=== "Rust"

    ```rust
    fn generate_range_proof(&self, value: u64, min: u64, max: u64) -> Result<(StarkProof, String, String), String>;
    ```

=== "Go"

    ```go
    GenerateRangeProof(ctx context.Context, value, min, max uint64) (StarkProofResult, error)
    ```

=== "Java"

    ```java
    StarkProofResult generateRangeProof(long value, long min, long max);
    ```

---

### `verifyRangeProof`

Verify a STARK range proof.

**Parameters:**

| Name         | Type   | Description                             |
|--------------|--------|-----------------------------------------|
| `proofHex`   | string | Hex-encoded proof bytes                 |
| `vkJson`     | string | JSON-encoded verification key           |
| `inputsJson` | string | JSON-encoded public inputs              |

**Returns:** `boolean` -- `true` if the proof is valid.

=== "TypeScript (async)"

    ```typescript
    verifyRangeProof(proofHex: string, vkJson: string, inputsJson: string): Promise<boolean>;
    ```

=== "TypeScript (sync)"

    ```typescript
    verifyRangeProof(proofHex: string, vkJson: string, inputsJson: string): boolean;
    ```

=== "Python (sync)"

    ```python
    def verify_range_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...
    ```

=== "Python (async)"

    ```python
    async def verify_range_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...
    ```

=== "Rust"

    ```rust
    fn verify_range_proof(&self, proof: &[u8], vk_json: &str, inputs_json: &str) -> Result<bool, String>;
    ```

=== "Go"

    ```go
    VerifyRangeProof(ctx context.Context, proofHex, vkJSON, inputsJSON string) (bool, error)
    ```

=== "Java"

    ```java
    boolean verifyRangeProof(String proofHex, String vkJson, String inputsJson);
    ```

---

## Shielded Pool Client Methods

These methods live on `DilithiaClient` and interact with the on-chain
`shielded` contract. They are available in all five SDKs.

### `shieldedDeposit`

Deposit funds into the shielded pool by publishing a commitment.

**Parameters:**

| Name         | Type    | Description                          |
|--------------|---------|--------------------------------------|
| `commitment` | string  | Hex-encoded commitment hash          |
| `value`      | integer | Amount to deposit                    |
| `proofHex`   | string  | Hex-encoded STARK proof              |

**Returns:** a submitted call / transaction result.

=== "TypeScript"

    ```typescript
    async shieldedDeposit(
      commitment: string,
      value: number,
      proofHex: string,
    ): Promise<SubmittedCall>
    ```

=== "Python (sync)"

    ```python
    def shielded_deposit(self, commitment: str, value: int, proof_hex: str) -> dict[str, Any]: ...
    ```

=== "Python (async)"

    ```python
    async def shielded_deposit(self, commitment: str, value: int, proof_hex: str) -> dict[str, Any]: ...
    ```

=== "Rust"

    ```rust
    pub fn shielded_deposit_request(
        &self,
        commitment: &str,
        value: u64,
        proof_hex: &str,
    ) -> DilithiaRequest
    ```

=== "Go"

    ```go
    func (c *Client) ShieldedDepositBody(
        commitment string,
        value uint64,
        proofHex string,
    ) map[string]interface{}
    ```

=== "Java"

    ```java
    public Map<String, Object> shieldedDepositBody(
        String commitment, long value, String proofHex)
    ```

---

### `shieldedWithdraw`

Withdraw funds from the shielded pool by revealing a nullifier and providing a
ZK proof of commitment ownership.

**Parameters:**

| Name             | Type    | Description                                   |
|------------------|---------|-----------------------------------------------|
| `nullifier`      | string  | Hex-encoded nullifier hash                    |
| `amount`         | integer | Amount to withdraw                            |
| `recipient`      | string  | Destination address                           |
| `proofHex`       | string  | Hex-encoded STARK proof                       |
| `commitmentRoot` | string  | Current Merkle root of the commitment tree    |

**Returns:** a submitted call / transaction result.

=== "TypeScript"

    ```typescript
    async shieldedWithdraw(
      nullifier: string,
      amount: number,
      recipient: string,
      proofHex: string,
      commitmentRoot: string,
    ): Promise<SubmittedCall>
    ```

=== "Python (sync)"

    ```python
    def shielded_withdraw(
        self,
        nullifier: str,
        amount: int,
        recipient: str,
        proof_hex: str,
        commitment_root: str,
    ) -> dict[str, Any]: ...
    ```

=== "Python (async)"

    ```python
    async def shielded_withdraw(
        self,
        nullifier: str,
        amount: int,
        recipient: str,
        proof_hex: str,
        commitment_root: str,
    ) -> dict[str, Any]: ...
    ```

=== "Rust"

    ```rust
    pub fn shielded_withdraw_request(
        &self,
        nullifier: &str,
        amount: u64,
        recipient: &str,
        proof_hex: &str,
        commitment_root: &str,
    ) -> DilithiaRequest
    ```

=== "Go"

    ```go
    func (c *Client) ShieldedWithdrawBody(
        nullifier string,
        amount uint64,
        recipient, proofHex, commitmentRoot string,
    ) map[string]interface{}
    ```

=== "Java"

    ```java
    public Map<String, Object> shieldedWithdrawBody(
        String nullifier, long amount, String recipient,
        String proofHex, String commitmentRoot)
    ```

---

### `getCommitmentRoot`

Query the current Merkle root of the shielded pool's commitment tree. This root
is required when generating withdrawal proofs.

**Parameters:** none.

**Returns:** the current commitment root hash.

=== "TypeScript"

    ```typescript
    async getCommitmentRoot(): Promise<Record<string, unknown>>
    ```

=== "Python (sync)"

    ```python
    def get_commitment_root(self) -> dict[str, Any]: ...
    ```

=== "Python (async)"

    ```python
    async def get_commitment_root(self) -> dict[str, Any]: ...
    ```

=== "Rust"

    ```rust
    pub fn get_commitment_root_request(&self) -> DilithiaRequest
    ```

=== "Go"

    ```go
    func (c *Client) GetCommitmentRootBody() map[string]interface{}
    ```

=== "Java"

    ```java
    public Map<String, Object> getCommitmentRootBody()
    ```

---

### `isNullifierSpent`

Check whether a nullifier has already been used (i.e., the corresponding
commitment has been withdrawn).

**Parameters:**

| Name        | Type   | Description                    |
|-------------|--------|--------------------------------|
| `nullifier` | string | Hex-encoded nullifier hash     |

**Returns:** whether the nullifier has been spent.

=== "TypeScript"

    ```typescript
    async isNullifierSpent(nullifier: string): Promise<Record<string, unknown>>
    ```

=== "Python (sync)"

    ```python
    def is_nullifier_spent(self, nullifier: str) -> dict[str, Any]: ...
    ```

=== "Python (async)"

    ```python
    async def is_nullifier_spent(self, nullifier: str) -> dict[str, Any]: ...
    ```

=== "Rust"

    ```rust
    pub fn is_nullifier_spent_request(&self, nullifier: &str) -> DilithiaRequest
    ```

=== "Go"

    ```go
    func (c *Client) IsNullifierSpentBody(nullifier string) map[string]interface{}
    ```

=== "Java"

    ```java
    public Map<String, Object> isNullifierSpentBody(String nullifier)
    ```

---

### `shieldedComplianceProof`

Submit a compliance proof to the shielded pool. This allows a user to prove
regulatory compliance (e.g., tax paid, not on sanctions list, balance in range)
without revealing the underlying transaction data.

!!! note
    This method is currently available in the **Rust** SDK only. Other SDKs
    can achieve the same result by calling `buildContractCall` / `callContract`
    with the `"shielded"` contract and `"compliance_proof"` method directly.

**Parameters:**

| Name        | Type   | Description                                            |
|-------------|--------|--------------------------------------------------------|
| `proofType` | string | One of `"not_on_sanctions"`, `"tax_paid"`, `"balance_range"` |
| `proofHex`  | string | Hex-encoded STARK proof                                |
| `inputsHex` | string | Hex-encoded public inputs                              |

**Returns:** a submitted call / transaction result.

=== "Rust"

    ```rust
    pub fn shielded_compliance_proof_request(
        &self,
        proof_type: &str,
        proof_hex: &str,
        inputs_hex: &str,
    ) -> DilithiaRequest
    ```

=== "TypeScript (via buildContractCall)"

    ```typescript
    const call = client.buildContractCall("shielded", "compliance_proof", {
      proof_type: "tax_paid",
      proof: proofHex,
      inputs: inputsHex,
    });
    await client.sendCall(call);
    ```

---

## Adapter Variants

### TypeScript

TypeScript provides two adapter interfaces:

- **`DilithiaZkAdapter`** -- all methods return `Promise<T>`. Loaded via
  `loadZkAdapter()`, which dynamically imports `@dilithia/sdk-zk`.
- **`SyncDilithiaZkAdapter`** -- all methods return `T` synchronously. Loaded
  via `loadSyncZkAdapter()`, which uses `createRequire` to synchronously require
  the native bridge.

Both loaders return `null` if the `@dilithia/sdk-zk` package is not installed.

### Python

Python provides two adapter protocols:

- **`DilithiaZkAdapter`** (Protocol) -- synchronous methods. The concrete
  implementation is `NativeZkAdapter`. Loaded via `load_zk_adapter()`.
- **`AsyncDilithiaZkAdapter`** (Protocol) -- async methods. The concrete
  implementation is `AsyncNativeZkAdapter`, which wraps a sync adapter using
  `asyncio.to_thread` so CPU-intensive STARK work does not block the event loop.
  Loaded via `load_async_zk_adapter()`.

Both loaders return `None` if `dilithia_sdk_zk` is not importable.

### Rust

Rust defines a `DilithiaZkAdapter` **trait**. Enable it with the `stark` feature
on the `dilithia-core` crate. Implement the trait for your own type or use the
provided native implementation.

### Go

Go defines a `ZkAdapter` **interface** in `sdk.ZkAdapter`. Provide your own
implementation backed by a CGo bridge or a gRPC call to a proof service.

### Java

Java defines a `DilithiaZkAdapter` **interface** in
`org.dilithia.sdk.DilithiaZkAdapter`. Implement it with JNI bindings or a
remote proof service.
