# ZK & Shielded Pool Guide

## Overview

The Dilithia **shielded pool** is an on-chain privacy layer that lets users
deposit, transfer, and withdraw tokens without revealing amounts or
participants on the public ledger. Privacy is enforced through
**zero-knowledge proofs** -- specifically **STARKs** (Scalable Transparent
ARguments of Knowledge).

Why STARKs instead of SNARKs?

- **No trusted setup.** STARKs are transparent: there is no ceremony that could
  be compromised.
- **Post-quantum secure.** STARKs rely on hash functions (Poseidon) rather than
  elliptic-curve pairings, making them resistant to quantum attacks. This aligns
  with Dilithia's broader post-quantum cryptographic design (ML-DSA-65
  signatures, lattice-based key exchange).
- **Scalable verification.** Proof verification is polylogarithmic in the
  computation size.

The ZK adapter wraps the low-level proof system (winterfell) and exposes seven
methods for hashing, commitments, nullifiers, and proof generation/verification.
The `DilithiaClient` then provides five high-level methods for interacting with
the on-chain shielded contract.

For the full API reference, see [ZK Adapter API](../api/zk.md).

---

## Installation

The ZK bridge is packaged **separately** from the core SDK to keep the base
install lightweight (STARK proof generation pulls in native code).

=== "TypeScript / JavaScript"

    ```bash
    npm install @dilithia/sdk @dilithia/sdk-zk
    ```

=== "Python"

    ```bash
    pip install dilithia-sdk dilithia-sdk-zk
    ```

=== "Rust"

    ```toml
    [dependencies]
    dilithia-sdk = "0.2"
    dilithia-core = { version = "0.2", features = ["stark"] }
    ```

=== "Go"

    ```bash
    go get github.com/dilithia/languages-sdk/go/sdk
    ```

    Provide a `ZkAdapter` implementation backed by CGo or an external proof
    service.

=== "Java"

    ```xml
    <dependency>
      <groupId>org.dilithia</groupId>
      <artifactId>dilithia-sdk</artifactId>
      <version>0.3.0</version>
    </dependency>
    ```

    Provide a `DilithiaZkAdapter` implementation via JNI or a remote proof
    service.

---

## Loading the Adapter

=== "TypeScript"

    ```typescript
    import { loadZkAdapter, loadSyncZkAdapter } from "@dilithia/sdk";

    // Async adapter (recommended for servers / long-running processes)
    const zk = await loadZkAdapter();
    if (!zk) throw new Error("@dilithia/sdk-zk not installed");

    // Sync adapter (useful for scripts and CLI tools)
    const zkSync = loadSyncZkAdapter();
    if (!zkSync) throw new Error("@dilithia/sdk-zk not installed");
    ```

=== "Python"

    ```python
    from dilithia_sdk.zk import load_zk_adapter, load_async_zk_adapter

    # Synchronous adapter
    zk = load_zk_adapter()
    assert zk is not None, "dilithia-sdk-zk not installed"

    # Async adapter (wraps sync via asyncio.to_thread)
    async_zk = load_async_zk_adapter()
    assert async_zk is not None, "dilithia-sdk-zk not installed"
    ```

=== "Rust"

    ```rust
    use dilithia_sdk::DilithiaZkAdapter;

    // Implement the trait for your type. With the `stark` feature enabled,
    // dilithia-core provides a native implementation.
    struct MyZkAdapter;

    impl DilithiaZkAdapter for MyZkAdapter {
        fn poseidon_hash(&self, inputs: &[u64]) -> Result<String, String> {
            // ... native winterfell call
            todo!()
        }
        // ... remaining methods
    }
    ```

=== "Go"

    ```go
    import "github.com/dilithia/languages-sdk/go/sdk"

    // Implement the ZkAdapter interface.
    type myZkAdapter struct{}

    func (a *myZkAdapter) PoseidonHash(
        ctx context.Context, inputs []uint64,
    ) (string, error) {
        // ... CGo bridge or gRPC call
        return "", nil
    }
    // ... remaining methods
    ```

=== "Java"

    ```java
    import org.dilithia.sdk.DilithiaZkAdapter;

    // Implement the interface with JNI or a remote proof service.
    public class MyZkAdapter implements DilithiaZkAdapter {
        @Override
        public String poseidonHash(long[] inputs) {
            // ... JNI call
            return "";
        }
        // ... remaining methods
    }
    ```

---

## Use Case 1: Private Transfer

This end-to-end example demonstrates depositing tokens into the shielded pool
and later withdrawing them to a different address, all without revealing the
amount on chain.

### Step-by-step

1. Generate a random **secret** and **nonce** (32 bytes each).
2. **Compute a commitment** -- `Poseidon(value || secret || nonce)`.
3. **Deposit** the commitment into the shielded pool.
4. When ready to withdraw: **compute the nullifier** --
   `Poseidon(secret || nonce)`.
5. **Generate a preimage proof** proving knowledge of the commitment's
   preimage.
6. **Withdraw** from the shielded pool using the nullifier and proof.

### TypeScript

```typescript
import { randomBytes } from "node:crypto";
import { DilithiaClient, loadZkAdapter } from "@dilithia/sdk";

const client = new DilithiaClient({ rpcUrl: "http://localhost:8000/rpc" });
const zk = await loadZkAdapter();
if (!zk) throw new Error("ZK bridge not available");

// 1. Generate secret and nonce
const secret = randomBytes(32).toString("hex");
const nonce = randomBytes(32).toString("hex");
const depositAmount = 1000;

// 2. Compute commitment
const commitment = await zk.computeCommitment(depositAmount, secret, nonce);
console.log("Commitment hash:", commitment.hash);

// 3. Generate a preimage proof for the deposit
const depositProof = await zk.generatePreimageProof([depositAmount]);

// 4. Deposit into the shielded pool
const depositTx = await client.shieldedDeposit(
  commitment.hash,
  depositAmount,
  depositProof.proof,
);
console.log("Deposit submitted:", depositTx);

// --- later, when you want to withdraw ---

// 5. Compute nullifier
const nullifier = await zk.computeNullifier(secret, nonce);

// 6. Generate withdrawal proof (preimage proof over the commitment inputs)
const withdrawProof = await zk.generatePreimageProof([depositAmount]);

// 7. Get the current commitment root
const rootResult = await client.getCommitmentRoot();
const commitmentRoot = rootResult.root as string;

// 8. Withdraw
const withdrawTx = await client.shieldedWithdraw(
  nullifier.hash,
  depositAmount,
  "dili1_recipient_address_here",
  withdrawProof.proof,
  commitmentRoot,
);
console.log("Withdrawal submitted:", withdrawTx);
```

### Python

```python
import os
from dilithia_sdk.client import DilithiaClient
from dilithia_sdk.zk import load_zk_adapter

client = DilithiaClient("http://localhost:8000/rpc")
zk = load_zk_adapter()
assert zk is not None, "ZK bridge not available"

# 1. Generate secret and nonce
secret = os.urandom(32).hex()
nonce = os.urandom(32).hex()
deposit_amount = 1000

# 2. Compute commitment
commitment = zk.compute_commitment(deposit_amount, secret, nonce)
print("Commitment hash:", commitment.hash)

# 3. Generate a preimage proof for the deposit
deposit_proof = zk.generate_preimage_proof([deposit_amount])

# 4. Deposit into the shielded pool
deposit_tx = client.shielded_deposit(
    commitment.hash,
    deposit_amount,
    deposit_proof.proof,
)
print("Deposit submitted:", deposit_tx)

# --- later, when you want to withdraw ---

# 5. Compute nullifier
nullifier = zk.compute_nullifier(secret, nonce)

# 6. Generate withdrawal proof
withdraw_proof = zk.generate_preimage_proof([deposit_amount])

# 7. Get the current commitment root
root_result = client.get_commitment_root()
commitment_root = root_result["root"]

# 8. Withdraw
withdraw_tx = client.shielded_withdraw(
    nullifier.hash,
    deposit_amount,
    "dili1_recipient_address_here",
    withdraw_proof.proof,
    commitment_root,
)
print("Withdrawal submitted:", withdraw_tx)
```

!!! warning "Store your secret and nonce safely"
    The `secret` and `nonce` are the only way to derive the nullifier and
    reclaim your funds. If you lose them, the deposited tokens are
    **permanently locked** in the shielded pool.

---

## Use Case 2: Compliance Proof (Tax Paid)

Regulatory bodies may require proof that taxes were paid on shielded
transactions. With a **range proof**, you can prove that the total tax paid
falls within an expected range without revealing the exact amounts of any
individual transaction.

### TypeScript

```typescript
import { DilithiaClient, loadZkAdapter } from "@dilithia/sdk";

const client = new DilithiaClient({ rpcUrl: "http://localhost:8000/rpc" });
const zk = await loadZkAdapter();
if (!zk) throw new Error("ZK bridge not available");

// Suppose you owe between 500 and 2000 in tax for the period, and you
// actually paid 1200. Prove it without revealing the exact figure.
const taxPaid = 1200;
const minExpected = 500;
const maxExpected = 2000;

// 1. Generate a range proof: taxPaid in [500, 2000]
const rangeProof = await zk.generateRangeProof(taxPaid, minExpected, maxExpected);

// 2. Submit compliance proof to the shielded contract
const call = client.buildContractCall("shielded", "compliance_proof", {
  proof_type: "tax_paid",
  proof: rangeProof.proof,
  inputs: rangeProof.inputs,
});
const result = await client.sendCall(call);
console.log("Compliance proof accepted:", result);
```

### Python

```python
from dilithia_sdk.client import DilithiaClient
from dilithia_sdk.zk import load_zk_adapter

client = DilithiaClient("http://localhost:8000/rpc")
zk = load_zk_adapter()
assert zk is not None

tax_paid = 1200
min_expected = 500
max_expected = 2000

# 1. Generate range proof
range_proof = zk.generate_range_proof(tax_paid, min_expected, max_expected)

# 2. Submit compliance proof
result = client.call_contract("shielded", "compliance_proof", {
    "proof_type": "tax_paid",
    "proof": range_proof.proof,
    "inputs": range_proof.inputs,
})
print("Compliance proof accepted:", result)
```

---

## Use Case 3: Sanctions Screening

Prove that your address is **not** on a sanctions list, without revealing which
addresses are on the list or any details about your transaction history.

This uses a **preimage proof**: the verifier publishes a Poseidon hash of the
sanctions list, and you prove that your address hashes to a value that is not in
the committed set.

### TypeScript

```typescript
import { DilithiaClient, loadZkAdapter } from "@dilithia/sdk";

const client = new DilithiaClient({ rpcUrl: "http://localhost:8000/rpc" });
const zk = await loadZkAdapter();
if (!zk) throw new Error("ZK bridge not available");

// Your address as a field element (simplified -- real implementation
// would encode the address into field elements).
const myAddressField = 0x1234abcd;

// 1. Generate a preimage proof demonstrating non-membership
const proof = await zk.generatePreimageProof([myAddressField]);

// 2. Submit a "not_on_sanctions" compliance proof
const call = client.buildContractCall("shielded", "compliance_proof", {
  proof_type: "not_on_sanctions",
  proof: proof.proof,
  inputs: proof.inputs,
});
const result = await client.sendCall(call);
console.log("Sanctions screening passed:", result);
```

---

## Sync vs Async

### When to use the async adapter

Use the **async** adapter when running inside an event loop or a server that
handles concurrent requests. STARK proof generation is CPU-intensive and can
take hundreds of milliseconds or more. The async adapter ensures this work does
not block other tasks.

- **TypeScript:** `loadZkAdapter()` returns a `DilithiaZkAdapter` whose methods
  return `Promise<T>`. The underlying native call is still synchronous, but
  wrapping it in `async` allows the event loop to schedule other microtasks.
- **Python:** `load_async_zk_adapter()` returns an `AsyncNativeZkAdapter` that
  delegates every call to `asyncio.to_thread`, running the CPU work on a
  thread-pool executor so the event loop stays responsive.

### When to use the sync adapter

Use the **sync** adapter for scripts, CLI tools, tests, or any context where
blocking is acceptable and you want simpler code without `await`.

- **TypeScript:** `loadSyncZkAdapter()` returns a `SyncDilithiaZkAdapter` whose
  methods return `T` directly.
- **Python:** `load_zk_adapter()` returns a `DilithiaZkAdapter` (sync protocol)
  with plain synchronous methods.

### Rust, Go, and Java

- **Rust:** The `DilithiaZkAdapter` trait is synchronous. Wrap calls in
  `tokio::task::spawn_blocking` (or equivalent) if you need async behavior.
- **Go:** The `ZkAdapter` interface accepts a `context.Context` for
  cancellation. Run proof generation in a goroutine if needed.
- **Java:** The `DilithiaZkAdapter` interface is synchronous. Use
  `CompletableFuture.supplyAsync()` or virtual threads (Java 21+) for
  non-blocking execution.

---

## Security Considerations

- **Secret management.** The 32-byte `secret` and `nonce` are the keys to your
  shielded funds. Store them with the same care as private keys.
- **Nullifier uniqueness.** Each commitment can only be spent once. The chain
  rejects any withdrawal whose nullifier has already been recorded.
- **Commitment root freshness.** Always query `getCommitmentRoot` immediately
  before generating a withdrawal proof. A stale root will cause the on-chain
  verification to fail.
- **Proof size.** STARK proofs are larger than SNARK proofs (tens of KB vs.
  hundreds of bytes). Plan for this in transaction size budgets.
